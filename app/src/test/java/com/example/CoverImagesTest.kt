package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.storage.CoverImages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CoverImagesTest {
  @Test
  fun `large page is stored as a small scaled jpeg keeping aspect ratio`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val page = java.io.File(context.cacheDir, "big_page.png")
    val bmp = Bitmap.createBitmap(2000, 3000, Bitmap.Config.ARGB_8888)
    val rnd = java.util.Random(1)
    val pixels = IntArray(2000) { rnd.nextInt() or (0xFF shl 24) }
    for (y in 0 until 3000) bmp.setPixels(pixels, 0, 2000, 0, y, 2000, 1) // noisy, so the PNG is genuinely large
    page.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }

    val cover = java.io.File(context.cacheDir, "covers/scaled.jpg")
    assertTrue(CoverImages.saveScaled({ page.inputStream() }, cover))

    val out = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(cover.path, out)
    assertEquals(400, out.outWidth)
    assertEquals(600, out.outHeight)
    assertTrue("cover ${cover.length()} bytes", CoverImages.isScaledCover(cover))
    assertFalse(java.io.File(cover.path + ".tmp").exists())
  }

  @Test
  fun `undecodable data is rejected so callers can fall back`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val dest = java.io.File(context.cacheDir, "covers/bad.jpg")
    assertFalse(CoverImages.saveScaled({ "not an image".byteInputStream() }, dest))
    assertFalse(dest.exists())
  }
}
