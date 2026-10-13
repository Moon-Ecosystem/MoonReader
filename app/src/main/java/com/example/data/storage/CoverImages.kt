package com.example.data.storage

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Covers are only ever shown as thumbnails (largest: the 155dp hero cover), so they are stored downscaled
 * instead of as full-size pages, which can be several MB each and are decoded for every library card.
 */
object CoverImages {
    private const val TAG = "CoverImages"
    private const val MAX_SIDE = 600

    /** A stored cover bigger than this predates downscaling (or failed it) and should be regenerated. */
    const val MAX_COVER_BYTES = 400 * 1024L

    /** True when [file] is an existing, already-downscaled cover. */
    fun isScaledCover(file: File): Boolean = file.length() in 1..MAX_COVER_BYTES

    /**
     * Decodes the image from [open] (called twice: bounds, then pixels), scales it to at most [MAX_SIDE]px
     * on the long side and writes it to [dest] as JPEG. Returns false if the image can't be decoded.
     */
    fun saveScaled(open: () -> InputStream?, dest: File): Boolean {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            // decodeStream returns null in bounds-only mode by design; the result is in bounds.out*
            (open() ?: return false).use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false

            // Power-of-two subsampling while decoding keeps memory low for huge pages
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
            val decoded = open()?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return false

            val scale = MAX_SIDE.toFloat() / max(decoded.width, decoded.height)
            val scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    decoded,
                    (decoded.width * scale).roundToInt().coerceAtLeast(1),
                    (decoded.height * scale).roundToInt().coerceAtLeast(1),
                    true
                )
            } else decoded

            dest.parentFile?.mkdirs()
            val tmp = File(dest.path + ".tmp")
            FileOutputStream(tmp).use { scaled.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            if (scaled !== decoded) scaled.recycle()
            decoded.recycle()
            tmp.length() > 0L && tmp.renameTo(dest)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save scaled cover to ${dest.name}", e)
            false
        }
    }
}
