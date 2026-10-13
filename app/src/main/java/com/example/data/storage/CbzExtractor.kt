package com.example.data.storage

import android.content.Context
import android.net.Uri
import android.util.Log
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

/**
 * Archive helpers shared by [CbzArchive] (pages are read straight from the archive, never extracted)
 * and cover generation.
 */
object CbzExtractor {
    private const val TAG = "CbzExtractor"
    private const val COPY_BUFFER = 64 * 1024
    private const val TEMP_PREFIX = "temp_extract_"

    private val IMAGE_EXTENSIONS = setOf(
        "jpg", "jpeg", "png", "webp", "gif", "avif", "bmp", "heic", "heif"
    )

    internal val SUPPORTED_CHARSETS = listOf(
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

    internal fun isPageEntry(entry: ZipEntry): Boolean {
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
    internal fun stageArchive(context: Context, chapterUriString: String): Pair<File, () -> Unit>? {
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
            // Non-seekable provider (e.g. some cloud storage): copy once to a temp file for random access
            val temp = File(context.cacheDir, "$TEMP_PREFIX${System.nanoTime()}.cbz")
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
     * Writes a downscaled cover from the chapter's first page and returns its path.
     */
    fun extractCover(context: Context, chapterUriString: String, mangaId: String): String? {
        val mangaHash = getStableHash(mangaId)
        val coversDir = File(context.cacheDir, "covers")
        coversDir.mkdirs()
        val coverFile = File(coversDir, "cover_$mangaHash.jpg")

        // Full-size covers from before downscaling are regenerated
        if (CoverImages.isScaledCover(coverFile)) {
            return coverFile.absolutePath
        }

        // Scaled JPEG normally; raw copy if this device can't decode the format (a big cover beats none)
        fun writeCover(open: () -> InputStream?): Boolean =
            CoverImages.saveScaled(open, coverFile) || try {
                open()?.use { input -> FileOutputStream(coverFile).use { input.copyTo(it, COPY_BUFFER) } }
                coverFile.length() > 0L
            } catch (_: Exception) {
                false
            }

        try {
            CbzArchive.open(context, chapterUriString)?.use { archive ->
                val first = archive.pages.firstOrNull()
                if (first != null && writeCover { archive.openPage(first) }) return coverFile.absolutePath
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting cover thumbnail for $mangaId", e)
        }
        coverFile.delete() // don't leave a partial cover that the cache check above would trust
        return null
    }

    /**
     * Size of reclaimable cache: chapters extracted by older versions of the app and leftover temp copies.
     */
    fun getCacheSize(context: Context): Long {
        return calculateDirSize(File(context.cacheDir, "chapters")) +
                (tempFiles(context).sumOf { it.length() })
    }

    private fun tempFiles(context: Context): List<File> =
        context.cacheDir.listFiles { f -> f.name.startsWith(TEMP_PREFIX) }?.toList().orEmpty()

    private fun calculateDirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        var total = 0L
        dir.listFiles()?.forEach { file ->
            total += if (file.isDirectory) calculateDirSize(file) else file.length()
        }
        return total
    }

    /**
     * Deletes the reclaimable cache (see [getCacheSize]) and returns the bytes freed.
     */
    fun clearChapterCache(context: Context): Long {
        val size = getCacheSize(context)
        File(context.cacheDir, "chapters").deleteRecursively()
        tempFiles(context).forEach { it.delete() }
        return size
    }
}
