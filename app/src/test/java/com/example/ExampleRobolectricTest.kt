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
  fun `cbz archive lists pages in order, skipping metadata, without extracting`() {
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

    val archive = com.example.data.storage.CbzArchive.open(context, testZip.absolutePath)!!
    archive.use {
      // Metadata and non-images skipped, natural order applied, nothing written to disk
      assertEquals(listOf("page_01.jpg", "page_02.jpg"), it.pages.map { p -> p.displayName })
      assertEquals("page 1 content", it.openPage(it.pages[0]).use { s -> s.readBytes().decodeToString() })
      assertEquals("page 2 content", it.openPage(it.pages[1]).use { s -> s.readBytes().decodeToString() })
    }
    assertFalse(java.io.File(context.cacheDir, "chapters").exists())
  }

  @Test
  fun `cover uses only the first page and nothing is extracted`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val cbz = java.io.File(context.cacheDir, "cover_test.cbz")
    java.util.zip.ZipOutputStream(java.io.FileOutputStream(cbz)).use { zos ->
      for (name in listOf("p10.jpg", "p2.jpg", "p1.jpg")) {
        zos.putNextEntry(java.util.zip.ZipEntry(name))
        zos.write(name.toByteArray())
        zos.closeEntry()
      }
    }
    // Fake image bytes can't be decoded, so this also exercises the raw-copy cover fallback
    val cover = com.example.data.storage.CbzExtractor.extractCover(context, cbz.absolutePath, "cover_test_manga")
    assertEquals("p1.jpg", java.io.File(cover!!).readText())
    assertFalse(java.io.File(context.cacheDir, "chapters").exists())
  }

  @Test
  fun `coil fetcher reads a page straight from the archive, held open or not`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val cbz = java.io.File(context.cacheDir, "fetch_test.cbz")
    java.util.zip.ZipOutputStream(java.io.FileOutputStream(cbz)).use { zos ->
      for (i in 1..3) {
        zos.putNextEntry(java.util.zip.ZipEntry("p$i.jpg"))
        zos.write("page $i".toByteArray())
        zos.closeEntry()
      }
    }
    val archives = com.example.data.storage.OpenArchives
    suspend fun fetch(page: com.example.data.CbzPage): String {
      val result = com.example.data.storage.CbzPageFetcher(page, coil.request.Options(context)).fetch()
      return (result as coil.fetch.SourceResult).source.source().readUtf8()
    }

    val pages = archives.acquire(context, cbz.absolutePath)!!.pages
    assertEquals("page 2", fetch(pages[1]))

    // After the reader releases the chapter, a late request still works by opening the archive briefly
    archives.retainOnly(emptySet())
    assertEquals("page 3", fetch(pages[2]))
    assertEquals(
      "cbz:${cbz.absolutePath}#p3.jpg",
      com.example.data.storage.CbzPageKeyer().key(pages[2], coil.request.Options(context))
    )
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
