package com.example.data.storage

import android.content.Context
import android.util.Log
import com.example.data.CbzPage
import java.io.Closeable
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * An open chapter archive. Opening reads only the ZIP central directory (a few KB at the end of the file);
 * each page is then read on demand straight from its entry, so pages are never extracted to disk.
 */
class CbzArchive private constructor(
    val uri: String,
    private val zip: ZipFile,
    private val entries: List<ZipEntry>
) : Closeable {
    val pages: List<CbzPage> = entries.mapIndexed { i, entry -> CbzPage(uri, entry.name, i) }

    fun openPage(page: CbzPage): InputStream {
        require(page.archiveUri == uri) { "Page belongs to ${page.archiveUri}, not $uri" }
        return zip.getInputStream(entries[page.index])
    }

    override fun close() {
        try {
            zip.close()
        } catch (_: Exception) {}
    }

    companion object {
        private const val TAG = "CbzArchive"

        /** Opens [uri] and lists its pages in natural order, or returns null if it holds no readable pages. */
        fun open(context: Context, uri: String): CbzArchive? {
            val staged = CbzExtractor.stageArchive(context, uri) ?: return null
            try {
                // Entry names are decoded with the charset; fall back through common encodings for older archives
                for (charset in CbzExtractor.SUPPORTED_CHARSETS) {
                    val zip = try {
                        ZipFile(staged.first, charset)
                    } catch (e: Exception) {
                        Log.w(TAG, "Cannot open $uri as ZIP with ${charset.name()}: ${e.message}")
                        continue
                    }
                    val pageEntries = try {
                        zip.entries().asSequence().filter { CbzExtractor.isPageEntry(it) }.toList()
                            .sortedWith { a, b ->
                                NaturalOrderComparator.compare(a.name.replace('\\', '/'), b.name.replace('\\', '/'))
                            }
                    } catch (e: Exception) {
                        emptyList()
                    }
                    if (pageEntries.isNotEmpty()) return CbzArchive(uri, zip, pageEntries)
                    zip.close()
                }
                return null
            } finally {
                // ZipFile holds its own descriptor now, so the staging fd can be closed and a temp copy unlinked
                staged.second()
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
