package com.example.data.storage

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.example.data.CbzPage
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap

/**
 * An open chapter archive. Opening reads only the ZIP central directory (a few KB at the end of the file);
 * each page is then read on demand straight from its entry, so pages are never extracted to disk.
 */
class CbzArchive private constructor(
    val uri: String,
    private val zip: ChannelZip,
    private val entries: List<ChannelZip.Entry>
) : Closeable {
    val pages: List<CbzPage> = entries.mapIndexed { i, entry -> CbzPage(uri, entry.name, i) }

    fun openPage(page: CbzPage): InputStream {
        require(page.archiveUri == uri) { "Page belongs to ${page.archiveUri}, not $uri" }
        return zip.open(entries[page.index])
    }

    /** The archive's ComicInfo.xml (preferring the top-level one), or null if it has none. */
    fun openComicInfo(): InputStream? {
        val entry = zip.entries
            .filter { !it.isDirectory && it.name.replace('\\', '/').substringAfterLast('/').equals("ComicInfo.xml", true) }
            .minByOrNull { it.name.count { c -> c == '/' || c == '\\' } }
            ?: return null
        return zip.open(entry)
    }

    override fun close() = zip.close()

    companion object {
        private const val TAG = "CbzArchive"

        /** Opens [uri] and lists its pages in natural order, or returns null if it holds no readable pages. */
        fun open(context: Context, uri: String): CbzArchive? {
            val zip = try {
                openZip(context, uri)
            } catch (e: Exception) {
                Log.w(TAG, "Cannot open $uri as ZIP: ${e.message}")
                null
            } ?: return null
            val pageEntries = zip.entries
                .filter { !it.isDirectory && CbzExtractor.isPageName(it.name) }
                .sortedWith { a, b -> NaturalOrderComparator.compare(a.name.replace('\\', '/'), b.name.replace('\\', '/')) }
            if (pageEntries.isEmpty()) {
                zip.close()
                return null
            }
            return CbzArchive(uri, zip, pageEntries)
        }

        private fun openZip(context: Context, uri: String): ChannelZip? {
            if (!uri.startsWith("content://")) {
                val file = File(uri)
                if (!file.isFile) return null
                return zipOrClose(RandomAccessFile(file, "r"))
            }
            // Read through the provider's descriptor: no path needed, so nothing is copied for local storage
            val pfd = context.contentResolver.openFileDescriptor(Uri.parse(uri), "r") ?: return null
            if (pfd.statSize >= 0) {
                // AutoCloseInputStream owns the descriptor, so it is closed exactly once along with the channel
                return zipOrClose(ParcelFileDescriptor.AutoCloseInputStream(pfd))
            }
            // Pipe-backed provider (some cloud storage) can't seek: copy once to a temp file, unlinked right away
            pfd.close()
            val temp = CbzExtractor.copyToTemp(context, uri) ?: return null
            return try {
                zipOrClose(RandomAccessFile(temp, "r"))
            } finally {
                temp.delete()
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
