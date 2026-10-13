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
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE) // real image decoding, so fake page bytes exercise the cover fallback
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

  @Test
  fun `cover extracts only first page and incomplete page cache is rebuilt`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val cbz = java.io.File(context.cacheDir, "cover_test.cbz")
    java.util.zip.ZipOutputStream(java.io.FileOutputStream(cbz)).use { zos ->
      for (name in listOf("p10.jpg", "p2.jpg", "p1.jpg")) {
        zos.putNextEntry(java.util.zip.ZipEntry(name))
        zos.write(name.toByteArray())
        zos.closeEntry()
      }
    }
    val extractor = com.example.data.storage.CbzExtractor
    val chapterDir = java.io.File(context.cacheDir, "chapters/" + extractor.getStableHash(cbz.absolutePath))

    val cover = extractor.extractCover(context, cbz.absolutePath, "cover_test_manga")
    assertEquals("p1.jpg", java.io.File(cover!!).readText())
    assertFalse("cover must not unpack the whole chapter", chapterDir.exists())

    val pages = extractor.extractPages(context, cbz.absolutePath, "ch")
    assertEquals(listOf("p1.jpg", "p2.jpg", "p10.jpg"), pages.map { it.readText() })
    assertTrue(java.io.File(chapterDir, ".complete").exists())

    // Simulate a process killed mid-extraction: marker gone, a page missing
    java.io.File(chapterDir, ".complete").delete()
    pages.last().delete()
    assertEquals(3, extractor.extractPages(context, cbz.absolutePath, "ch").size)
  }

  @Test
  fun `progressive extraction plans pages first and writes from the resume page outward`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val cbz = java.io.File(context.cacheDir, "progressive_test.cbz")
    java.util.zip.ZipOutputStream(java.io.FileOutputStream(cbz)).use { zos ->
      for (i in 1..6) {
        zos.putNextEntry(java.util.zip.ZipEntry("p$i.jpg"))
        zos.write("page $i".toByteArray())
        zos.closeEntry()
      }
    }
    val extractor = com.example.data.storage.CbzExtractor
    var planned: List<java.io.File>? = null
    var anyWrittenAtPlan = true
    val readyOrder = mutableListOf<Int>()
    val listener = com.example.data.storage.CbzExtractor.ExtractionListener(
      onPlanned = { files -> planned = files; anyWrittenAtPlan = files.any { it.exists() } },
      onPageReady = { i -> assertTrue("page $i reported before written", planned!![i].length() > 0); readyOrder += i }
    )

    val pages = extractor.extractPages(context, cbz.absolutePath, "ch", startIndex = 3, listener = listener)

    assertEquals(6, planned!!.size)
    assertFalse("plan must arrive before any page is written", anyWrittenAtPlan)
    assertEquals(listOf(3, 4, 5, 2, 1, 0), readyOrder)
    assertEquals(planned, pages)
    assertEquals(listOf(0, 1, 2, 3, 4, 5).map { "page ${it + 1}" }, pages.map { it.readText() })
  }

  @Test
  fun `statistics summary and streaks calculation`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = com.example.data.db.AppDatabase.getDatabase(context)
    val prefs = PreferencesManager(context)
    val repo = com.example.data.repository.StatisticsRepository(db.statisticsDao(), db.mangaDao(), prefs)

    repo.clearStatistics()

    val now = System.currentTimeMillis()
    // Session 1: Today, 30 minutes, 25 pages, chapter completed
    repo.recordSession(
        mangaId = "manga_1",
        chapterId = "ch_1",
        chapterTitle = "Chapter 1",
        startedAt = now - 1800000L,
        endedAt = now,
        startPage = 0,
        endPage = 25,
        pagesProgressed = 25,
        completedChapter = true
    )

    // Session 2: Today, 15 minutes, 10 pages
    repo.recordSession(
        mangaId = "manga_1",
        chapterId = "ch_2",
        chapterTitle = "Chapter 2",
        startedAt = now - 900000L,
        endedAt = now,
        startPage = 0,
        endPage = 10,
        pagesProgressed = 10,
        completedChapter = false
    )

    val sessions = db.statisticsDao().getAllSessionsOnce()
    assertEquals(2, sessions.size)

    val summary = repo.computeSummary(sessions, com.example.data.TimePeriod.TODAY)
    assertEquals(1, summary.chaptersCompleted)
    assertEquals(35, summary.pagesProgressed)
    assertEquals(1, summary.activeDaysCount)
    assertEquals(1, summary.distinctSeriesCount)
    assertTrue(summary.totalReadingTimeMillis >= 2700000L)
    assertEquals(1, summary.currentStreakDays)

    // Chart points verification
    val chartPoints = repo.computeChartPoints(sessions, com.example.data.TimePeriod.THIS_WEEK, com.example.data.ActivityChartMetric.PAGES)
    assertEquals(7, chartPoints.size)

    // Calendar verification
    val (_, calendarDays) = repo.computeCalendarDays(sessions, 0)
    assertTrue(calendarDays.isNotEmpty())
    val todayActivity = calendarDays.find { it.epochDay == java.time.LocalDate.now().toEpochDay() }
    assertTrue(todayActivity != null)
    assertEquals(35, todayActivity!!.pagesProgressed)
    assertEquals(1, todayActivity.chaptersCompleted)
    assertTrue(todayActivity.intensity >= 2)
  }
}
