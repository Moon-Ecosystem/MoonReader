package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.preferences.PreferencesManager
import com.example.data.storage.NaturalOrderComparator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("MangaReader", appName)
  }

  @Test
  fun `natural order comparator sorts chapters correctly`() {
    val list = listOf("Chapter 10", "Chapter 2", "Chapter 1", "Chapter 20")
    val sorted = list.sortedWith { a, b -> NaturalOrderComparator.compare(a, b) }
    assertEquals(listOf("Chapter 1", "Chapter 2", "Chapter 10", "Chapter 20"), sorted)
  }

  @Test
  fun `extracts chapter number accurately`() {
    val num = NaturalOrderComparator.extractChapterNumber("Ch. 12.5 - Bonus Chapter.cbz")
    assertEquals(12.5f, num, 0.001f)
  }

  @Test
  fun `app lock pin verification and clearing works`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = PreferencesManager(context)
    prefs.setPin("1234")
    prefs.setAppLockEnabled(true)

    assertTrue(prefs.isAppLockEnabled.value)
    assertTrue(prefs.verifyPin("1234"))
    assertFalse(prefs.verifyPin("0000"))
    assertFalse(prefs.verifyPin("123"))

    prefs.clearPin()
    assertFalse(prefs.isAppLockEnabled.value)
    assertFalse(prefs.hasPin())
  }

  @Test
  fun `cbz extractor extracts pages and handles nested paths and metadata`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val testZip = java.io.File(context.cacheDir, "test_manga.cbz")

    // Create a zip with nested directories, out of order entries, and macOS metadata
    java.util.zip.ZipOutputStream(java.io.FileOutputStream(testZip)).use { zos ->
      // Entry 1: page 2
      zos.putNextEntry(java.util.zip.ZipEntry("Vol_01/page_02.jpg"))
      zos.write("page 2 content".toByteArray())
      zos.closeEntry()

      // Entry 2: macOS metadata
      zos.putNextEntry(java.util.zip.ZipEntry("__MACOSX/._page_01.jpg"))
      zos.write("garbage".toByteArray())
      zos.closeEntry()

      // Entry 3: page 1
      zos.putNextEntry(java.util.zip.ZipEntry("Vol_01/page_01.jpg"))
      zos.write("page 1 content".toByteArray())
      zos.closeEntry()

      // Entry 4: non-image
      zos.putNextEntry(java.util.zip.ZipEntry("ComicInfo.xml"))
      zos.write("<xml></xml>".toByteArray())
      zos.closeEntry()
    }

    val extracted = com.example.data.storage.CbzExtractor.extractPages(context, testZip.absolutePath, "test_chapter_id")
    assertEquals(2, extracted.size)
    // Verify natural ordering
    assertTrue(extracted[0].name.startsWith("page_0001"))
    assertTrue(extracted[1].name.startsWith("page_0002"))
    assertTrue(extracted[0].length() > 0)
    assertTrue(extracted[1].length() > 0)
  }
}
