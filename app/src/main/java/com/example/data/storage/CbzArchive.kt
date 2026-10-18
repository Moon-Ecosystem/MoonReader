package com.example.data.storage

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import android.util.Log
import com.example.data.CbzPage
import me.zhanghai.android.libarchive.Archive
import me.zhanghai.android.libarchive.ArchiveEntry
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap

/**
 * An open chapter archive. A ZIP (.cbz) is opened by reading only its central directory (a few KB at the end
 * of the file); each page is then read on demand straight from its entry, so pages are never extracted to disk.
 * Anything else (.cbr/.rar including RAR5, .7z, …) is read through libarchive.
 */
class CbzArchive private constructor(
    val uri: String,
    private val source: EntrySource,
    /** Indices into [EntrySource.names], in page order. */
    private val pageEntries: List<Int>
) : Closeable {
    val pages: List<CbzPage> = pageEntries.mapIndexed { i, entry -> CbzPage(uri, source.names[entry], i) }

    fun openPage(page: CbzPage): InputStream {
        require(page.archiveUri == uri) { "Page belongs to ${page.archiveUri}, not $uri" }
        return source.open(pageEntries[page.index])
    }

    /** The archive's ComicInfo.xml (preferring the top-level one), or null if it has none. */
    fun openComicInfo(): InputStream? {
        val entry = source.names.indices
            .filter { source.names[it].replace('\\', '/').substringAfterLast('/').equals("ComicInfo.xml", true) }
            .minByOrNull { source.names[it].count { c -> c == '/' || c == '\\' } }
            ?: return null
        return source.open(entry)
    }

    override fun close() = source.close()

    companion object {
        private const val TAG = "CbzArchive"

        /** Opens [uri] and lists its pages in natural order, or returns null if it holds no readable pages. */
        fun open(context: Context, uri: String): CbzArchive? {
            val source = try {
                openSource(context, uri)
            } catch (e: Exception) {
                Log.w(TAG, "Cannot open $uri: ${e.message}")
                null
            } ?: return null
            val names = source.names
            val pageEntries = names.indices
                .filter { CbzExtractor.isPageName(names[it]) }
                .sortedWith { a, b -> NaturalOrderComparator.compare(names[a].replace('\\', '/'), names[b].replace('\\', '/')) }
            if (pageEntries.isEmpty()) {
                source.close()
                return null
            }
            return CbzArchive(uri, source, pageEntries)
        }

        private fun openSource(context: Context, uri: String): EntrySource? {
            if (!uri.startsWith("content://")) {
                val file = File(uri)
                if (!file.isFile) return null
                return zipOrLibArchive({ zipOrClose(RandomAccessFile(file, "r")) }) {
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                }
            }
            // Read through the provider's descriptor: no path needed, so nothing is copied for local storage
            val reopen = { context.contentResolver.openFileDescriptor(Uri.parse(uri), "r") }
            val pfd = reopen() ?: return null
            if (pfd.statSize >= 0) {
                // AutoCloseInputStream owns the descriptor, so it is closed exactly once along with the channel
                return zipOrLibArchive({ zipOrClose(ParcelFileDescriptor.AutoCloseInputStream(pfd)) }, reopen)
            }
            // Pipe-backed provider (some cloud storage) can't seek: copy once to a temp file, unlinked right away
            pfd.close()
            val temp = CbzExtractor.copyToTemp(context, uri) ?: return null
            return try {
                zipOrLibArchive({ zipOrClose(RandomAccessFile(temp, "r")) }) {
                    ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY)
                }
            } finally {
                temp.delete()
            }
        }

        /** Reads the file as a ZIP, or, when it isn't one, through libarchive on a fresh descriptor from [reopen]. */
        private fun zipOrLibArchive(zip: () -> ChannelZip, reopen: () -> ParcelFileDescriptor?): EntrySource =
            try {
                ZipSource(zip())
            } catch (e: Exception) {
                val pfd = reopen() ?: throw e
                try {
                    LibArchiveSource(pfd)
                } catch (t: Throwable) { // LinkageError too: JVM unit tests have no native libarchive
                    pfd.close()
                    throw IOException("Not a ZIP (${e.message}) and libarchive can't read it: ${t.message}", t)
                }
            }

        /** Builds the reader over the stream's channel, closing the stream if the archive is unreadable. */
        private fun zipOrClose(source: Closeable): ChannelZip {
            val channel = when (source) {
                is RandomAccessFile -> source.channel
                is java.io.FileInputStream -> source.channel
                else -> error("Unsupported source $source")
            }
            return try {
                ChannelZip(channel) { try { source.close() } catch (_: Exception) {} }
            } catch (e: Exception) {
                try { source.close() } catch (_: Exception) {}
                throw e
            }
        }
    }
}

/** An archive's entry names, and a way to read the entry at an index of [names]. */
private interface EntrySource : Closeable {
    val names: List<String>
    fun open(index: Int): InputStream
}

private class ZipSource(private val zip: ChannelZip) : EntrySource {
    override val names = zip.entries.map { it.name }
    override fun open(index: Int): InputStream = zip.open(zip.entries[index])
    override fun close() = zip.close()
}

/** RAR, 7z and the other formats libarchive reads. It can only read forward from the start of the file. */
private class LibArchiveSource(private val pfd: ParcelFileDescriptor) : EntrySource {
    override val names: List<String> = withArchive { archive ->
        generateSequence { Archive.readNextHeader(archive).takeIf { it != 0L } }.map(::entryName).toList()
    }

    // shortcut: each page read skips past the entries before it, which in a solid RAR/7z means decompressing
    // them again; cache decoded pages if big solid archives turn out slow
    override fun open(index: Int): InputStream = withArchive { archive ->
        repeat(index) { Archive.readNextHeader(archive) }
        if (Archive.readNextHeader(archive) == 0L) throw IOException("Archive has no entry $index")
        val out = ByteArrayOutputStream()
        val buffer = ByteBuffer.allocate(BUFFER_SIZE)
        while (true) {
            buffer.clear()
            Archive.readData(archive, buffer)
            if (buffer.position() == 0) break
            out.write(buffer.array(), 0, buffer.position())
        }
        ByteArrayInputStream(out.toByteArray())
    }

    /** One read at a time: every read rewinds the shared descriptor and starts over from the archive's start. */
    @Synchronized
    private fun <T> withArchive(block: (Long) -> T): T {
        Os.lseek(pfd.fileDescriptor, 0L, OsConstants.SEEK_SET)
        val archive = Archive.readNew()
        try {
            Archive.readSupportFilterAll(archive)
            Archive.readSupportFormatAll(archive)
            Archive.readOpenFd(archive, pfd.fd, BUFFER_SIZE.toLong())
            return block(archive)
        } finally {
            Archive.readFree(archive)
        }
    }

    private fun entryName(entry: Long): String =
        ArchiveEntry.pathnameUtf8(entry) ?: ArchiveEntry.pathname(entry)?.let { String(it) } ?: ""

    override fun close() = pfd.close()

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
    }
}

/**
 * Archives kept open for reading (the current chapter and the prefetched next one), keyed by chapter URI.
 */
object OpenArchives {
    private val open = ConcurrentHashMap<String, CbzArchive>()

    /** Opens [uri], or reuses it if already open, and keeps it open until dropped by [retainOnly]. */
    fun acquire(context: Context, uri: String): CbzArchive? = synchronized(this) {
        open[uri] ?: CbzArchive.open(context, uri)?.also { open[uri] = it }
    }

    /** Closes every open archive whose URI is not in [uris]. */
    fun retainOnly(uris: Set<String>) = synchronized(this) {
        open.keys.filter { it !in uris }.forEach { open.remove(it)?.close() }
    }

    /** Runs [block] on the page's bytes, opening the archive just for this read if it isn't held open. */
    fun <T> readPage(context: Context, page: CbzPage, block: (InputStream) -> T): T? {
        open[page.archiveUri]?.let { archive -> return archive.openPage(page).use(block) }
        return CbzArchive.open(context, page.archiveUri)?.use { archive -> archive.openPage(page).use(block) }
    }
}
