package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.preferences.PreferencesManager
import com.example.data.storage.NaturalOrderComparator
import com.example.ui.screens.readableFolderPath
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    assertEquals("Moon Reader", appName)
  }

  @Test
  fun `library folder uri is shown as a readable path`() {
    val tree = "content://com.android.externalstorage.documents/tree/"
    assertEquals("Internal storage/Manga/local", readableFolderPath(tree + "primary%3AManga%2Flocal"))
    assertEquals("Internal storage", readableFolderPath(tree + "primary%3A"))
    assertEquals("1A2B-3C4D/Comics", readableFolderPath(tree + "1A2B-3C4D%3AComics"))
    assertEquals("/data/user/0/sample_manga", readableFolderPath("/data/user/0/sample_manga"))
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
  fun `non-ZIP archive falls back to libarchive and fails softly without it`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    // A RAR5 signature: not a ZIP, so the libarchive path runs, and the JVM has no native libarchive
    val cbr = java.io.File(context.cacheDir, "fake.cbr").apply { writeBytes(byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x01, 0x00)) }
    org.junit.Assert.assertNull(com.example.data.storage.CbzArchive.open(context, cbr.absolutePath))
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
  fun `marking chapters read or unread persists, unread restarts the chapter and library progress follows`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val dao = com.example.data.db.AppDatabase.getDatabase(context).mangaDao()
    val repo = com.example.data.repository.MangaRepository(context, dao, PreferencesManager(context))
    val mangaId = "mark_read_manga"
    dao.insertOrUpdateManga(com.example.data.db.MangaEntity(id = mangaId, title = "M", folderUriString = mangaId, totalChapters = 3))

    // Chapter 1 partially read, then marked read and unread again from the chapter list
    repo.saveReadingProgress(mangaId, "c1", "Chapter 1", page = 12, totalPages = 30, isCompleted = false)
    repo.markChapterRead("c1", mangaId, true)
    val read = dao.getProgressForChapterOnce("c1")!!
    assertTrue(read.isRead)
    assertEquals("marking read keeps the position", 12, read.lastReadPage)
    repo.markChapterRead("c1", mangaId, false)
    val c1 = dao.getProgressForChapterOnce("c1")!!
    assertFalse(c1.isRead)
    assertEquals("unread chapters start from the first page (#30)", 0, c1.lastReadPage)
    assertEquals("continue reading card resets too", 0, dao.getMangaByIdOnce(mangaId)!!.lastReadPage)

    // Never-opened chapters can be marked read; library progress follows
    repo.markChapterRead("c2", mangaId, true)
    repo.markChapterRead("c3", mangaId, true)
    fun libraryEntry() = kotlinx.coroutines.runBlocking {
      repo.libraryManga.first { list -> list.any { it.id == mangaId } }.first { it.id == mangaId }
    }
    assertEquals(2, libraryEntry().readChapterCount)
    assertFalse(libraryEntry().isCompleted)
    repo.markChapterRead("c1", mangaId, true)
    assertTrue(libraryEntry().isCompleted)
  }

  @Test
  fun `marking a whole manga read or unread from the library covers every chapter`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val root = java.io.File(context.cacheDir, "mark_manga_lib").apply { deleteRecursively(); mkdirs() }
    val dir = java.io.File(root, "Book").apply { mkdirs() }
    for (ch in 1..3) {
      java.util.zip.ZipOutputStream(java.io.FileOutputStream(java.io.File(dir, "Ch $ch.cbz"))).use { zos ->
        zos.putNextEntry(java.util.zip.ZipEntry("p1.jpg"))
        zos.write("page".toByteArray())
        zos.closeEntry()
      }
    }
    val dao = com.example.data.db.AppDatabase.getDatabase(context).mangaDao()
    val repo = com.example.data.repository.MangaRepository(
      context, dao, PreferencesManager(context).apply { setFolderUri(root.absolutePath) }
    )
    repo.scanCurrentLibrary()
    val mangaId = repo.libraryManga.first { it.isNotEmpty() }.single().id
    fun entry() = kotlinx.coroutines.runBlocking {
      repo.libraryManga.first { list -> list.any { it.id == mangaId } }.first { it.id == mangaId }
    }

    val chapters = repo.markMangaRead(mangaId, true)
    assertEquals(3, chapters.size)
    assertTrue(entry().isCompleted)
    repo.saveReadingProgress(mangaId, chapters[0].id, "Ch 1", page = 3, totalPages = 10, isCompleted = false)
    repo.markMangaRead(mangaId, false)
    assertEquals(0, entry().readChapterCount)
    assertEquals("no longer counts as being read", 0L, entry().lastReadTime)
    assertEquals(null, entry().lastReadChapterId)
  }

  @Test
  fun `migration 3 to 5 adds collections and chapter changes without touching existing data`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val name = "migration_3_4_test.db"
    context.deleteDatabase(name)
    fun open() = androidx.room.Room.databaseBuilder(context, com.example.data.db.AppDatabase::class.java, name)
      .addMigrations(
        com.example.data.db.AppDatabase.MIGRATION_1_2,
        com.example.data.db.AppDatabase.MIGRATION_2_3,
        com.example.data.db.AppDatabase.MIGRATION_3_4,
        com.example.data.db.AppDatabase.MIGRATION_4_5
      )
      .allowMainThreadQueries()
      .build() // no destructive fallback: a schema mismatch must fail here, not wipe data

    // A version 3 database: today's schema minus the collection and chapter override tables, with a library row in it
    open().apply {
      mangaDao().insertOrUpdateManga(com.example.data.db.MangaEntity(id = "m1", title = "Kept", folderUriString = "m1"))
      openHelper.writableDatabase.apply {
        execSQL("DROP TABLE collection_manga")
        execSQL("DROP TABLE collections")
        execSQL("DROP TABLE chapter_overrides")
        execSQL("PRAGMA user_version = 3")
      }
      close()
    }

    // Reopening runs MIGRATION_3_4 and MIGRATION_4_5, and Room validates the tables they create
    val db = open()
    assertEquals("Kept", db.mangaDao().getMangaByIdOnce("m1")!!.title)
    db.mangaDao().saveChapterOverride(com.example.data.db.ChapterOverrideEntity("ch1", "m1", "Renamed", isHidden = true))
    assertEquals(
      "Renamed" to true,
      db.mangaDao().getChapterOverrideOnce("ch1")!!.let { it.customTitle to it.isHidden }
    )
    val dao = db.collectionDao()
    val horror = dao.create("Horror")
    val seinen = dao.create("Seinen")
    assertEquals(listOf("Horror" to 0, "Seinen" to 1), dao.observeCollections().first().map { it.name to it.position })

    dao.addMember(com.example.data.db.CollectionMangaEntity(horror, "m1"))
    dao.addMember(com.example.data.db.CollectionMangaEntity(horror, "m1")) // adding twice is ignored
    dao.addMember(com.example.data.db.CollectionMangaEntity(seinen, "m1"))
    assertEquals(2, dao.observeMembers().first().size)

    // Deleting a collection drops its memberships only; the series stays
    dao.delete(horror)
    assertEquals(listOf(seinen), dao.observeMembers().first().map { it.collectionId })
    assertEquals(listOf("Seinen"), dao.observeCollections().first().map { it.name })
    assertEquals("Kept", db.mangaDao().getMangaByIdOnce("m1")!!.title)
    db.close()
  }

  @Test
  fun `renaming the last read chapter updates the continue reading title`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val root = java.io.File(context.cacheDir, "rename_lib").apply { deleteRecursively(); mkdirs() }
    val dir = java.io.File(root, "Book").apply { mkdirs() }
    for (name in listOf("Ch 1.cbz", "Ch 2.cbz")) {
      java.util.zip.ZipOutputStream(java.io.FileOutputStream(java.io.File(dir, name))).use { zos ->
        zos.putNextEntry(java.util.zip.ZipEntry("p1.jpg"))
        zos.write("page".toByteArray())
        zos.closeEntry()
      }
    }
    val dao = com.example.data.db.AppDatabase.getDatabase(context).mangaDao()
    val repo = com.example.data.repository.MangaRepository(
      context, dao, PreferencesManager(context).apply { setFolderUri(root.absolutePath) }
    )
    repo.scanCurrentLibrary()
    val mangaId = repo.libraryManga.first { it.isNotEmpty() }.single().id
    val chapters = repo.getChaptersForManga(mangaId).first()
    val second = chapters.first { it.fileName == "Ch 2.cbz" }
    fun lastReadTitle() = kotlinx.coroutines.runBlocking { dao.getMangaByIdOnce(mangaId)!!.lastReadChapterTitle }

    repo.saveReadingProgress(mangaId, second.id, second.title, page = 3, totalPages = 10, isCompleted = false)
    assertEquals("Ch 2", lastReadTitle())

    repo.renameChapter(second.id, mangaId, "The Duel")
    assertEquals("The Duel", lastReadTitle())
    assertEquals("The Duel", repo.getChaptersForManga(mangaId).first().first { it.id == second.id }.title)

    // Renaming another chapter leaves the continue reading title alone
    repo.renameChapter(chapters.first { it.fileName == "Ch 1.cbz" }.id, mangaId, "The Beginning")
    assertEquals("The Duel", lastReadTitle())

    // A blank name clears the change: the file's own name comes back everywhere
    repo.renameChapter(second.id, mangaId, "  ")
    assertEquals("Ch 2", lastReadTitle())
  }

  @Test
  fun `a removed chapter leaves the series and can be restored`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val root = java.io.File(context.cacheDir, "remove_lib").apply { deleteRecursively(); mkdirs() }
    val dir = java.io.File(root, "Book").apply { mkdirs() }
    for (name in listOf("Ch 1.cbz", "Ch 2.cbz")) {
      java.util.zip.ZipOutputStream(java.io.FileOutputStream(java.io.File(dir, name))).use { zos ->
        zos.putNextEntry(java.util.zip.ZipEntry("p1.jpg"))
        zos.write("page".toByteArray())
        zos.closeEntry()
      }
    }
    val dao = com.example.data.db.AppDatabase.getDatabase(context).mangaDao()
    val repo = com.example.data.repository.MangaRepository(
      context, dao, PreferencesManager(context).apply { setFolderUri(root.absolutePath) }
    )
    repo.scanCurrentLibrary()
    val mangaId = repo.libraryManga.first { it.isNotEmpty() }.single().id
    val chapters = repo.getChaptersForManga(mangaId).first()
    val second = chapters.first { it.fileName == "Ch 2.cbz" }
    repo.saveReadingProgress(mangaId, second.id, second.title, page = 3, totalPages = 10, isCompleted = false)

    repo.deleteChapter(second.id, mangaId)
    assertEquals(listOf("Ch 1.cbz"), repo.getChaptersForManga(mangaId).first().map { it.fileName })
    assertEquals(listOf(second.id), repo.getRemovedChaptersForManga(mangaId).first().map { it.id })
    assertEquals("removed chapters no longer count", 1, repo.libraryManga.first { it.any { m -> m.id == mangaId } }.single { it.id == mangaId }.chapterCount)
    org.junit.Assert.assertNull("its progress goes too", dao.getProgressForChapterOnce(second.id))
    assertNull("continue reading no longer points at it", dao.getMangaByIdOnce(mangaId)!!.lastReadChapterId)

    repo.restoreChapter(second.id)
    assertTrue(repo.getRemovedChaptersForManga(mangaId).first().isEmpty())
    assertEquals(listOf("Ch 1.cbz", "Ch 2.cbz"), repo.getChaptersForManga(mangaId).first().map { it.fileName })
    assertEquals(2, repo.libraryManga.first { it.any { m -> m.id == mangaId } }.single { it.id == mangaId }.chapterCount)
  }

  @Test
  fun `widget lists started unfinished series, most recent first`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val dao = com.example.data.db.AppDatabase.getDatabase(context).mangaDao()
    fun manga(id: String, lastRead: Long, chapters: Int = 2) = com.example.data.db.MangaEntity(
      id = id, title = id, folderUriString = id, totalChapters = chapters,
      lastReadChapterId = if (lastRead > 0) "$id-c1" else null, lastReadTime = lastRead
    )
    // Times far in the future so other tests' rows in the shared database sort after these
    val base = 4_000_000_000_000L
    dao.insertOrUpdateMangaList(
      listOf(manga("w_old", base + 1), manga("w_new", base + 3), manga("w_done", base + 2, chapters = 1), manga("w_never", 0))
    )
    dao.saveChapterProgress(com.example.data.db.ChapterProgressEntity(chapterId = "w_done-c1", mangaId = "w_done", chapterTitle = "", isRead = true))

    val ids = dao.getInProgressManga(10).map { it.id }
    assertEquals(listOf("w_new", "w_old"), ids.filter { it.startsWith("w_") })
    val slots = com.example.widget.ContinueReadingWidget::slotCount
    // Default 4x2 widget: covers about 80 dp wide
    assertEquals(1, slots(70, 150))
    assertEquals(3, slots(250, 150))
    assertEquals(4, slots(600, 150))
    // Resized tall (the reported bug): one big cover instead of four thin strips
    assertEquals(1, slots(300, 430))
    // Wide and tall: as many full-size covers as fit
    assertEquals(2, slots(600, 430))
  }

  @Test
  fun `scan lists manga without generating covers, then covers are filled in`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val root = java.io.File(context.cacheDir, "cover_scan_lib").apply { deleteRecursively(); mkdirs() }
    for (book in listOf("Book A", "Book B")) {
      val dir = java.io.File(root, book).apply { mkdirs() }
      java.util.zip.ZipOutputStream(java.io.FileOutputStream(java.io.File(dir, "$book.cbz"))).use { zos ->
        zos.putNextEntry(java.util.zip.ZipEntry("p1.jpg"))
        zos.write("$book page 1".toByteArray())
        zos.closeEntry()
      }
    }
    val extractor = com.example.data.storage.CbzExtractor
    root.listFiles()!!.forEach { extractor.coverFileFor(context, it.absolutePath).delete() }

    // The scanner itself must not decode covers: it only queues them
    val scan = com.example.data.storage.MangaScanner.scanFolder(context, root.absolutePath)
    assertEquals(2, scan.mangaList.size)
    assertTrue(scan.mangaList.all { it.coverPath == null })
    assertEquals(scan.mangaList.map { it.id }.toSet(), scan.pendingCovers.keys)

    // The repository generates them after publishing the library and stores them in Room
    val prefs = PreferencesManager(context).apply { setFolderUri(root.absolutePath) }
    val dao = com.example.data.db.AppDatabase.getDatabase(context).mangaDao()
    com.example.data.repository.MangaRepository(context, dao, prefs).scanCurrentLibrary()
    for (manga in scan.mangaList) {
      val cover = dao.getMangaByIdOnce(manga.id)!!.coverPath
      assertEquals(extractor.coverFileFor(context, manga.id).absolutePath, cover)
      assertEquals("${manga.title} page 1", java.io.File(cover!!).readText()) // raw-copy fallback for fake bytes
    }
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

  @Test
  fun `returning to the same chapter within 5 minutes continues the session`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = com.example.data.db.AppDatabase.getDatabase(context)
    val repo = com.example.data.repository.StatisticsRepository(db.statisticsDao(), db.mangaDao(), PreferencesManager(context))
    repo.clearStatistics()

    val t = 1_000_000_000L
    val min = 60_000L
    suspend fun read(ch: String, start: Long, end: Long, pages: Int) = repo.recordSession(
      mangaId = "m", chapterId = ch, chapterTitle = ch, startedAt = start, endedAt = end,
      startPage = 0, endPage = pages, pagesProgressed = pages, completedChapter = false
    )

    read("ch1", t, t + 10 * min, 5)
    read("ch1", t + 14 * min, t + 20 * min, 3) // 4 min gap: merged
    var sessions = db.statisticsDao().getAllSessionsOnce()
    assertEquals(1, sessions.size)
    assertEquals(8, sessions[0].pagesProgressed)
    assertEquals(16 * min, sessions[0].durationMillis) // active time only, not the gap
    assertEquals(t + 20 * min, sessions[0].endedAt)

    read("ch1", t + 26 * min, t + 30 * min, 2) // 6 min gap: new session
    read("ch2", t + 31 * min, t + 35 * min, 2) // other chapter: new session
    sessions = db.statisticsDao().getAllSessionsOnce()
    assertEquals(3, sessions.size)
  }
}
