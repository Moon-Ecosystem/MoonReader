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
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

object CbzExtractor {
    private const val TAG = "CbzExtractor"

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

    /**
     * Extracts all image pages from a .cbz (ZIP) archive into a cache folder.
     * If the chapter has already been extracted, returns the cached files directly.
     */
    fun extractPages(context: Context, chapterUriString: String, chapterId: String): List<File> {
        val chapterCacheDir = getChapterCacheDir(context, chapterUriString)

        // 1. Verify if valid cached pages exist
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
        var tempArchiveFile: File? = null
        val archiveFile: File? = try {
            if (chapterUriString.startsWith("content://")) {
                // Copy stream to temp cache file so we have a random-access seekable File
                val temp = File(context.cacheDir, "temp_extract_${System.currentTimeMillis()}.cbz")
                val stream = openChapterInputStream(context, chapterUriString)
                if (stream != null) {
                    stream.use { input ->
                        FileOutputStream(temp).use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (temp.exists() && temp.length() > 0L) {
                        tempArchiveFile = temp
                        temp
                    } else {
                        temp.delete()
                        null
                    }
                } else null
            } else {
                val local = File(chapterUriString)
                if (local.exists() && local.isFile) local else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stage archive file for $chapterUriString", e)
            null
        }

        val extractedPages = mutableListOf<File>()

        // 3. Extract using ZipFile (handles central directory, compression types, arbitrary entry ordering)
        if (archiveFile != null && archiveFile.exists()) {
            for (charset in SUPPORTED_CHARSETS) {
                var zipFile: ZipFile? = null
                try {
                    zipFile = ZipFile(archiveFile, charset)
                    val entries = zipFile.entries().toList()

                    // Filter valid image entries
                    val imageEntries = entries.filter { entry ->
                        val normalized = entry.name.replace('\\', '/')
                        val fileName = normalized.substringAfterLast('/')
                        !entry.isDirectory &&
                                !normalized.contains("__MACOSX") &&
                                !fileName.startsWith(".") &&
                                isImageFile(fileName)
                    }.sortedWith { a, b ->
                        NaturalOrderComparator.compare(a.name.replace('\\', '/'), b.name.replace('\\', '/'))
                    }

                    if (imageEntries.isNotEmpty()) {
                        Log.d(TAG, "Found ${imageEntries.size} image entries using charset ${charset.name()}")
                        var pageIndex = 1
                        for (entry in imageEntries) {
                            val ext = entry.name.substringAfterLast('.', "jpg").lowercase(Locale.ROOT)
                            val outFile = File(chapterCacheDir, String.format(Locale.US, "page_%04d.%s", pageIndex++, ext))

                            zipFile.getInputStream(entry).use { inStream ->
                                FileOutputStream(outFile).use { outStream ->
                                    inStream.copyTo(outStream)
                                }
                            }

                            if (outFile.exists() && outFile.length() > 0L) {
                                extractedPages.add(outFile)
                            } else {
                                outFile.delete()
                            }
                        }
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

        // Clean up temporary staged archive file
        tempArchiveFile?.delete()

        // If extraction completely failed, delete cache dir
        if (extractedPages.isEmpty()) {
            chapterCacheDir.deleteRecursively()
            Log.e(TAG, "No valid image pages could be extracted from: $chapterUriString")
        } else {
            Log.i(TAG, "Successfully extracted ${extractedPages.size} pages to ${chapterCacheDir.name}")
        }

        return extractedPages.sortedWith { a, b -> NaturalOrderComparator.compare(a.name, b.name) }
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

        // Otherwise extract the first page from the chapter
        try {
            val pages = extractPages(context, chapterUriString, mangaId)
            val firstPage = pages.firstOrNull()
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
