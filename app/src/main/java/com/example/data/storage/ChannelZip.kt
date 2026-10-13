package com.example.data.storage

import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.SequenceInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream
import java.util.zip.ZipException

/**
 * Minimal read-only ZIP reader over a [FileChannel].
 *
 * java.util.zip.ZipFile needs a file path, and for a SAF document the only path is /proc/self/fd/N, which
 * Android 11+ refuses to reopen for shared-storage files the app has no direct access to; the old code then
 * copied the whole archive (hundreds of MB) to a temp file on every chapter open. Reading through the
 * descriptor's channel needs no path. Positional reads keep concurrent page loads thread-safe.
 *
 * Supports stored and deflated entries and ZIP64; encrypted entries are skipped.
 */
class ChannelZip(private val channel: FileChannel, private val onClose: () -> Unit = {}) : Closeable {

    class Entry internal constructor(
        val name: String,
        val isDirectory: Boolean,
        internal val method: Int,
        internal val compressedSize: Long,
        internal val localHeaderOffset: Long
    )

    val entries: List<Entry> = readCentralDirectory()

    fun open(entry: Entry): InputStream {
        val local = readAt(entry.localHeaderOffset, LOCAL_HEADER_SIZE)
        if (local.getInt(0) != LOCAL_HEADER_SIG) throw ZipException("Bad local header for ${entry.name}")
        val dataStart = entry.localHeaderOffset + LOCAL_HEADER_SIZE + u16(local, 26) + u16(local, 28)
        val raw = RangeStream(dataStart, entry.compressedSize)
        return when (entry.method) {
            METHOD_STORED -> raw
            METHOD_DEFLATED -> {
                val inflater = Inflater(true)
                // nowrap inflation needs one extra dummy byte after the data (as java.util.zip.ZipFile adds)
                object : InflaterInputStream(SequenceInputStream(raw, ByteArrayInputStream(ByteArray(1))), inflater, 64 * 1024) {
                    override fun close() {
                        super.close()
                        inflater.end()
                    }
                }
            }
            else -> throw ZipException("Unsupported compression method ${entry.method} for ${entry.name}")
        }
    }

    override fun close() {
        try {
            channel.close()
        } catch (_: IOException) {}
        onClose()
    }

    private fun readCentralDirectory(): List<Entry> {
        val size = channel.size()
        if (size < EOCD_SIZE) throw ZipException("Not a ZIP archive")

        // End of central directory record: at the end, before an optional comment of up to 64 KB
        val tailLength = minOf(size, (EOCD_SIZE + 0xFFFF).toLong()).toInt()
        val tailStart = size - tailLength
        val tail = readAt(tailStart, tailLength)
        val eocd = (tailLength - EOCD_SIZE downTo 0).firstOrNull { tail.getInt(it) == EOCD_SIG }
            ?: throw ZipException("No ZIP central directory")

        var count = u16(tail, eocd + 10).toLong()
        var cdSize = u32(tail, eocd + 12)
        var cdOffset = u32(tail, eocd + 16)
        val locator = eocd - ZIP64_LOCATOR_SIZE
        if ((count == 0xFFFFL || cdSize == 0xFFFFFFFFL || cdOffset == 0xFFFFFFFFL) &&
            locator >= 0 && tail.getInt(locator) == ZIP64_LOCATOR_SIG
        ) {
            val zip64 = readAt(tail.getLong(locator + 8), ZIP64_EOCD_SIZE)
            if (zip64.getInt(0) != ZIP64_EOCD_SIG) throw ZipException("Bad ZIP64 end record")
            count = zip64.getLong(32)
            cdSize = zip64.getLong(40)
            cdOffset = zip64.getLong(48)
        }
        if (cdSize > MAX_CENTRAL_DIRECTORY) throw ZipException("Central directory too large: $cdSize")

        val cd = readAt(cdOffset, cdSize.toInt())
        val result = ArrayList<Entry>(count.coerceAtMost(100_000).toInt())
        var p = 0
        while (p + CD_HEADER_SIZE <= cd.limit() && cd.getInt(p) == CD_HEADER_SIG) {
            val flags = u16(cd, p + 8)
            val method = u16(cd, p + 10)
            var compressed = u32(cd, p + 20)
            val uncompressed = u32(cd, p + 24)
            val nameLength = u16(cd, p + 28)
            val extraLength = u16(cd, p + 30)
            val commentLength = u16(cd, p + 32)
            var offset = u32(cd, p + 42)

            val nameBytes = ByteArray(nameLength).also { cd.position(p + CD_HEADER_SIZE); cd.get(it) }
            val name = decodeName(nameBytes, utf8 = flags and FLAG_UTF8 != 0)

            // ZIP64 extra field holds the 64-bit values for whichever fields are 0xFFFFFFFF, in this order
            var e = p + CD_HEADER_SIZE + nameLength
            val extraEnd = e + extraLength
            while (e + 4 <= extraEnd) {
                val id = u16(cd, e)
                val dataSize = u16(cd, e + 2)
                if (id == ZIP64_EXTRA_ID) {
                    var q = e + 4
                    if (uncompressed == 0xFFFFFFFFL) q += 8
                    if (compressed == 0xFFFFFFFFL) { compressed = cd.getLong(q); q += 8 }
                    if (offset == 0xFFFFFFFFL) offset = cd.getLong(q)
                }
                e += 4 + dataSize
            }

            if (flags and FLAG_ENCRYPTED == 0) {
                result += Entry(name, name.endsWith('/') || name.endsWith('\\'), method, compressed, offset)
            }
            p += CD_HEADER_SIZE + nameLength + extraLength + commentLength
        }
        return result
    }

    /** UTF-8 when flagged or valid, otherwise the legacy single-byte encoding of old ZIP tools. */
    private fun decodeName(bytes: ByteArray, utf8: Boolean): String {
        if (utf8) return String(bytes, StandardCharsets.UTF_8)
        return try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: CharacterCodingException) {
            String(bytes, java.nio.charset.Charset.forName("Cp437"))
        }
    }

    private fun readAt(position: Long, length: Int): ByteBuffer {
        val buffer = ByteBuffer.allocate(length).order(ByteOrder.LITTLE_ENDIAN)
        var p = position
        while (buffer.hasRemaining()) {
            val n = channel.read(buffer, p)
            if (n < 0) throw EOFException("ZIP truncated at $p")
            p += n
        }
        buffer.flip()
        return buffer
    }

    /** The compressed bytes of one entry, read by position so several entries can be read at once. */
    private inner class RangeStream(private var position: Long, private var remaining: Long) : InputStream() {
        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) <= 0) -1 else one[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (remaining <= 0L) return -1
            if (len == 0) return 0
            val n = channel.read(ByteBuffer.wrap(b, off, minOf(len.toLong(), remaining).toInt()), position)
            if (n < 0) throw EOFException("ZIP entry truncated")
            position += n
            remaining -= n
            return n
        }

        override fun available(): Int = remaining.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    private companion object {
        const val LOCAL_HEADER_SIG = 0x04034b50
        const val CD_HEADER_SIG = 0x02014b50
        const val EOCD_SIG = 0x06054b50
        const val ZIP64_LOCATOR_SIG = 0x07064b50
        const val ZIP64_EOCD_SIG = 0x06064b50
        const val LOCAL_HEADER_SIZE = 30
        const val CD_HEADER_SIZE = 46
        const val EOCD_SIZE = 22
        const val ZIP64_LOCATOR_SIZE = 20
        const val ZIP64_EOCD_SIZE = 56
        const val ZIP64_EXTRA_ID = 0x0001
        const val FLAG_ENCRYPTED = 0x1
        const val FLAG_UTF8 = 0x800
        const val METHOD_STORED = 0
        const val METHOD_DEFLATED = 8
        const val MAX_CENTRAL_DIRECTORY = 64L * 1024 * 1024

        fun u16(b: ByteBuffer, i: Int) = b.getShort(i).toInt() and 0xFFFF
        fun u32(b: ByteBuffer, i: Int) = b.getInt(i).toLong() and 0xFFFFFFFFL
    }
}
