package com.example.data.storage

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

object CbzExtractor {
    private const val TAG = "CbzExtractor"
    private const val COPY_BUFFER = 64 * 1024
    private const val COMPLETE_MARKER = ".complete"
    private val extractionLocks = ConcurrentHashMap<String, Any>()

    private val IMAGE_EXTENSIONS = setOf(
        "jpg", "jpeg", "png", "webp", "gif", "avif", "bmp", "heic", "heif"
    )

    private val SUPPORTED_CHARSETS = listOf(
        StandardCharsets.UTF_8,
        Charset.forName("Cp437"),
        Charset.forName("Shift_JIS"),
        StandardCharsets.ISO_8859_1
    )

    fun isImageFile(fileName: String): Boolean {
        val cleanName = fileName.replace('\\', '/').substringAfterLast('/')
        if (cleanName.startsWith(".") || cleanName.startsWith("._")) return false
        val ext = cleanName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return ext in IMAGE_EXTENSIONS
    }

    fun getStableHash(key: String): String {
        return try {
            val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray(StandardCharsets.UTF_8))
            bytes.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            key.replace(Regex("[^a-zA-Z0-9_]"), "_").take(48)
        }
    }

    private fun getChapterCacheDir(context: Context, chapterUriString: String): File {
        val hash = getStableHash(chapterUriString)
        return File(context.cacheDir, "chapters/$hash")
    }

    private fun openChapterInputStream(context: Context, fileUriString: String): InputStream? {
        return try {
            if (fileUriString.startsWith("content://")) {
                context.contentResolver.openInputStream(Uri.parse(fileUriString))
            } else {
                val file = File(fileUriString)
                if (file.exists()) FileInputStream(file) else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open input stream for: $fileUriString", e)
            null
        }
    }

    private fun isPageEntry(entry: ZipEntry): Boolean {
        val normalized = entry.name.replace('\\', '/')
        val fileName = normalized.substringAfterLast('/')
        return !entry.isDirectory &&
                !normalized.contains("__MACOSX") &&
                !fileName.startsWith(".") &&
                isImageFile(fileName)
    }

    /**
     * Returns a random-access File for the archive plus a cleanup callback.
     * content:// URIs are read in place through /proc/self/fd when the provider hands out a seekable
     * file descriptor, avoiding a full copy of the archive; otherwise they are copied to a temp file.
     */
    private fun stageArchive(context: Context, chapterUriString: String): Pair<File, () -> Unit>? {
        if (!chapterUriString.startsWith("content://")) {
            val local = File(chapterUriString)
            return if (local.exists() && local.isFile) local to {} else null
        }
        try {
            val pfd = context.contentResolver.openFileDescriptor(Uri.parse(chapterUriString), "r")
            if (pfd != null) {
                val fdFile = File("/proc/self/fd/${pfd.fd}")
                val seekable = try {
                    ZipFile(fdFile, StandardCharsets.ISO_8859_1).close() // any byte sequence is a valid name
                    true
                } catch (_: Exception) {
                    false
                }
                if (seekable) return fdFile to { try { pfd.close() } catch (_: Exception) {} }
                pfd.close()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Direct descriptor access failed for $chapterUriString: ${e.message}")
        }
        return try {
            // Copy stream to temp cache file so we have a random-access seekable File
            val temp = File(context.cacheDir, "temp_extract_${System.nanoTime()}.cbz")
            openChapterInputStream(context, chapterUriString)?.use { input ->
                FileOutputStream(temp).use { output -> input.copyTo(output, COPY_BUFFER) }
            }
            if (temp.exists() && temp.length() > 0L) {
                temp to { temp.delete(); Unit }
            } else {
                temp.delete()
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stage archive file for $chapterUriString", e)
            null
        }
    }

    /**
     * Extracts all image pages from a .cbz (ZIP) archive into a cache folder.
     * If the chapter has already been extracted, returns the cached files directly.
     */
    /**
     * Receives progress of a running extraction so the reader can open before the whole archive is unpacked.
     * [onPlanned] delivers the final ordered page files (not yet written); [onPageReady] fires per page index once
     * its file is complete. Called on the extracting thread.
     */
    class ExtractionListener(
        val onPlanned: (List<File>) -> Unit,
        val onPageReady: (Int) -> Unit
    )

    private class Progress {
        var planned: List<File>? = null
        val ready = HashSet<Int>()
        val listeners = mutableListOf<ExtractionListener>()

        fun plan(files: List<File>) = synchronized(this) {
            planned = files
            ready.clear()
            listeners.forEach { it.onPlanned(files) }
        }

        fun pageReady(index: Int) = synchronized(this) {
            ready.add(index)
            listeners.forEach { it.onPageReady(index) }
        }

        fun subscribe(listener: ExtractionListener) = synchronized(this) {
            planned?.let { files ->
                listener.onPlanned(files)
                ready.forEach(listener.onPageReady)
            }
            listeners.add(listener)
        }
    }

    private val inFlight = ConcurrentHashMap<String, Progress>()

    fun extractPages(
        context: Context,
        chapterUriString: String,
        chapterId: String,
        startIndex: Int = 0,
        listener: ExtractionListener? = null
    ): List<File> {
        val chapterCacheDir = getChapterCacheDir(context, chapterUriString)
        val key = chapterCacheDir.path
        // Another caller (e.g. next-chapter prefetch) may already be unpacking this chapter: follow its progress
        if (listener != null) inFlight[key]?.subscribe(listener)
        // Serialise per chapter so a background prefetch and the reader never race on the same cache dir
        synchronized(extractionLocks.getOrPut(key) { Any() }) {
            val progress = Progress()
            if (listener != null) progress.listeners.add(listener)
            inFlight[key] = progress
            try {
                return extractPagesLocked(context, chapterUriString, chapterCacheDir, startIndex, progress)
            } finally {
                inFlight.remove(key, progress)
            }
        }
    }

    /** Page indices ordered outward from [start]: the page the reader opens on is written first. */
    internal fun priorityOrder(count: Int, start: Int): List<Int> {
        val s = start.coerceIn(0, (count - 1).coerceAtLeast(0))
        return (s until count) + (s - 1 downTo 0)
    }

    private fun extractPagesLocked(
        context: Context,
        chapterUriString: String,
        chapterCacheDir: File,
        startIndex: Int,
        progress: Progress
    ): List<File> {
        // 1. Verify if valid cached pages exist (marker guards against a cache left half-written by a killed process)
        if (chapterCacheDir.exists() && !File(chapterCacheDir, COMPLETE_MARKER).exists()) {
            chapterCacheDir.deleteRecursively()
        }
        if (chapterCacheDir.exists() && chapterCacheDir.isDirectory) {
            val existing = chapterCacheDir.listFiles()
                ?.filter { it.isFile && it.length() > 0L && isImageFile(it.name) }
                ?.sortedWith { a, b -> NaturalOrderComparator.compare(a.name, b.name) }

            if (!existing.isNullOrEmpty()) {
                Log.d(TAG, "Found ${existing.size} cached pages for $chapterUriString")
                return existing
            } else {
                // Stale or empty directory, wipe it
                chapterCacheDir.deleteRecursively()
            }
        }

        chapterCacheDir.mkdirs()

        // 2. Prepare local archive file for ZipFile inspection
        val staged = stageArchive(context, chapterUriString)
        val archiveFile = staged?.first

        val extractedPages = mutableListOf<File>()

        // 3. Extract using ZipFile (handles central directory, compression types, arbitrary entry ordering)
        if (archiveFile != null && archiveFile.exists()) {
            for (charset in SUPPORTED_CHARSETS) {
                var zipFile: ZipFile? = null
                try {
                    zipFile = ZipFile(archiveFile, charset)
                    val entries = zipFile.entries().toList()

                    // Filter valid image entries
                    val imageEntries = entries.filter { isPageEntry(it) }.sortedWith { a, b ->
                        NaturalOrderComparator.compare(a.name.replace('\\', '/'), b.name.replace('\\', '/'))
                    }

                    if (imageEntries.isNotEmpty()) {
                        Log.d(TAG, "Found ${imageEntries.size} image entries using charset ${charset.name()}")
                        // The central directory gives the full page list up front, so the reader can open now
                        val targets = imageEntries.mapIndexed { i, entry ->
                            val ext = entry.name.substringAfterLast('.', "jpg").lowercase(Locale.ROOT)
                            File(chapterCacheDir, String.format(Locale.US, "page_%04d.%s", i + 1, ext))
                        }
                        progress.plan(targets)

                        // shortcut: fixed nearest-first order from the opening page; seeking far ahead waits
                        // for that page's turn. Reprioritise on seek if large archives make this noticeable.
                        for (i in priorityOrder(targets.size, startIndex)) {
                            val outFile = targets[i]
                            zipFile.getInputStream(imageEntries[i]).use { inStream ->
                                FileOutputStream(outFile).use { outStream ->
                                    inStream.copyTo(outStream, COPY_BUFFER)
                                }
                            }
                            if (outFile.length() > 0L) progress.pageReady(i) else outFile.delete()
                        }
                        extractedPages.addAll(targets.filter { it.length() > 0L })
                        // Successfully extracted all pages!
                        break
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed extraction with charset ${charset.name()}: ${e.message}")
                    // Clean up any partial files from this attempt
                    chapterCacheDir.listFiles()?.forEach { it.delete() }
                    extractedPages.clear()
                } finally {
                    try {
                        zipFile?.close()
                    } catch (_: Exception) {}
                }
            }
        }

        // 4. Fallback: Stream-based extraction with ZipInputStream if ZipFile could not be used
        if (extractedPages.isEmpty()) {
            Log.w(TAG, "ZipFile extraction yielded 0 pages, attempting ZipInputStream stream fallback...")
            val stream = openChapterInputStream(context, chapterUriString)
            if (stream != null) {
                try {
                    ZipInputStream(BufferedInputStream(stream)).use { zis ->
                        var entry: ZipEntry? = zis.nextEntry
                        val rawTempFiles = mutableListOf<Pair<String, File>>()
                        var tempCounter = 0

                        while (entry != null) {
                            val normalized = entry.name.replace('\\', '/')
                            val fileName = normalized.substringAfterLast('/')

                            if (!entry.isDirectory && !normalized.contains("__MACOSX") && !fileName.startsWith(".") && isImageFile(fileName)) {
                                val tempOut = File(chapterCacheDir, "raw_${tempCounter++}_$fileName")
                                FileOutputStream(tempOut).use { fos ->
                                    zis.copyTo(fos)
                                }
                                if (tempOut.exists() && tempOut.length() > 0L) {
                                    rawTempFiles.add(normalized to tempOut)
                                } else {
                                    tempOut.delete()
                                }
                            }
                            zis.closeEntry()
                            entry = zis.nextEntry
                        }

                        // Sort the raw temp files by their original entry names
                        rawTempFiles.sortWith { a, b -> NaturalOrderComparator.compare(a.first, b.first) }

                        var finalIndex = 1
                        for ((_, rawFile) in rawTempFiles) {
                            val ext = rawFile.name.substringAfterLast('.', "jpg")
                            val finalFile = File(chapterCacheDir, String.format(Locale.US, "page_%04d.%s", finalIndex++, ext))
                            if (rawFile.renameTo(finalFile)) {
                                extractedPages.add(finalFile)
                            } else {
                                extractedPages.add(rawFile)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Fallback stream extraction failed for $chapterUriString", e)
                }
            }
        }

        // Clean up temporary staged archive file / descriptor
        staged?.second?.invoke()

        // If extraction completely failed, delete cache dir
        if (extractedPages.isEmpty()) {
            chapterCacheDir.deleteRecursively()
            Log.e(TAG, "No valid image pages could be extracted from: $chapterUriString")
        } else {
            File(chapterCacheDir, COMPLETE_MARKER).createNewFile()
            Log.i(TAG, "Successfully extracted ${extractedPages.size} pages to ${chapterCacheDir.name}")
        }

        val result = extractedPages.sortedWith { a, b -> NaturalOrderComparator.compare(a.name, b.name) }
        if (result != progress.planned) {
            progress.plan(result)
            result.indices.forEach { progress.pageReady(it) }
        }
        return result
    }

    /**
     * Extracts only the first image from a chapter to use as a cover thumbnail.
     */
    fun extractCover(context: Context, chapterUriString: String, mangaId: String): String? {
        val mangaHash = getStableHash(mangaId)
        val coversDir = File(context.cacheDir, "covers")
        coversDir.mkdirs()
        val coverFile = File(coversDir, "cover_$mangaHash.jpg")

        if (coverFile.exists() && coverFile.length() > 0L) {
            return coverFile.absolutePath
        }

        // Check if chapter pages are already extracted in cache
        val chapterCache = getChapterCacheDir(context, chapterUriString)
        if (chapterCache.exists()) {
            val firstPage = chapterCache.listFiles()
                ?.filter { it.isFile && it.length() > 0L && isImageFile(it.name) }
                ?.minWithOrNull { a, b -> NaturalOrderComparator.compare(a.name, b.name) }
            if (firstPage != null) {
                try {
                    firstPage.copyTo(coverFile, overwrite = true)
                    return coverFile.absolutePath
                } catch (_: Exception) {}
            }
        }

        // Otherwise extract just the first page from the chapter archive
        val staged = stageArchive(context, chapterUriString)
        if (staged != null) {
            try {
                for (charset in SUPPORTED_CHARSETS) {
                    val written = try {
                        ZipFile(staged.first, charset).use { zip ->
                            val first = zip.entries().asSequence().filter { isPageEntry(it) }
                                .minWithOrNull { a, b ->
                                    NaturalOrderComparator.compare(a.name.replace('\\', '/'), b.name.replace('\\', '/'))
                                }
                            first != null && zip.getInputStream(first).use { input ->
                                FileOutputStream(coverFile).use { output -> input.copyTo(output, COPY_BUFFER) }
                                true
                            }
                        }
                    } catch (_: Exception) {
                        false
                    }
                    if (written && coverFile.length() > 0L) return coverFile.absolutePath
                }
                coverFile.delete() // don't leave a partial cover that the cache check above would trust
            } finally {
                staged.second()
            }
        }

        // Fallback: full extraction (handles archives ZipFile cannot read)
        try {
            val firstPage = extractPages(context, chapterUriString, mangaId).firstOrNull()
            if (firstPage != null && firstPage.exists() && firstPage.length() > 0L) {
                firstPage.copyTo(coverFile, overwrite = true)
                return coverFile.absolutePath
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting cover thumbnail for $mangaId", e)
        }

        return null
    }

    /**
     * Returns total size in bytes of the extracted chapters cache.
     */
    fun getCacheSize(context: Context): Long {
        val chaptersDir = File(context.cacheDir, "chapters")
        return calculateDirSize(chaptersDir)
    }

    private fun calculateDirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        var total = 0L
        dir.listFiles()?.forEach { file ->
            total += if (file.isDirectory) calculateDirSize(file) else file.length()
        }
        return total
    }

    /**
     * Clears all unzipped chapter cache files.
     */
    fun clearChapterCache(context: Context): Long {
        val chaptersDir = File(context.cacheDir, "chapters")
        val size = calculateDirSize(chaptersDir)
        chaptersDir.deleteRecursively()
        return size
    }
}
