package com.example.data.storage

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

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

    /** Whether a ZIP entry name is a readable page (an image, not macOS metadata or a hidden file). */
    internal fun isPageName(name: String): Boolean {
        val normalized = name.replace('\\', '/')
        val fileName = normalized.substringAfterLast('/')
        return !normalized.contains("__MACOSX") && !fileName.startsWith(".") && isImageFile(fileName)
    }

    /**
     * Copies a chapter to a temp file. Only for providers whose descriptor can't seek (some cloud storage);
     * normal storage is read in place through its descriptor.
     */
    internal fun copyToTemp(context: Context, chapterUriString: String): File? {
        return try {
            val temp = File(context.cacheDir, "$TEMP_PREFIX${System.nanoTime()}.cbz")
            openChapterInputStream(context, chapterUriString)?.use { input ->
                FileOutputStream(temp).use { output -> input.copyTo(output, COPY_BUFFER) }
            }
            if (temp.length() > 0L) temp else null.also { temp.delete() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy $chapterUriString to a temp file", e)
            null
        }
    }

    /** Where the cover generated from a manga's first page is stored. */
    fun coverFileFor(context: Context, mangaId: String): File =
        File(File(context.cacheDir, "covers"), "cover_${getStableHash(mangaId)}.jpg")

    /**
     * Writes a downscaled cover from the chapter's first page and returns its path.
     */
    fun extractCover(context: Context, chapterUriString: String, mangaId: String): String? {
        val coverFile = coverFileFor(context, mangaId)
        coverFile.parentFile?.mkdirs()

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

    /** Deletes chapters extracted by older versions of the app and leftover temp copies. */
    fun clearChapterCache(context: Context) {
        File(context.cacheDir, "chapters").deleteRecursively()
        context.cacheDir.listFiles { f -> f.name.startsWith(TEMP_PREFIX) }?.forEach { it.delete() }
    }
}
