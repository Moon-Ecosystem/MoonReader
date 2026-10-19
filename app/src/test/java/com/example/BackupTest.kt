package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.backup.BackupException
import com.example.data.backup.BackupRepository
import com.example.data.backup.remapLibraryId
import com.example.data.db.AppDatabase
import com.example.data.db.BackupData
import com.example.data.db.ChapterOverrideEntity
import com.example.data.db.ChapterProgressEntity
import com.example.data.db.CollectionEntity
import com.example.data.db.CollectionMangaEntity
import com.example.data.db.MangaEntity
import com.example.data.db.MangaTrackEntity
import com.example.data.db.ReadingSessionEntity
import com.example.data.preferences.PreferencesManager
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupTest {
  private val context = ApplicationProvider.getApplicationContext<Context>()
  private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
  private val dao = db.backupDao()
  private val backups = BackupRepository(context, dao)
  private val prefs = context.getSharedPreferences(PreferencesManager.PREFS_NAME, Context.MODE_PRIVATE)

  private val oldRoot = "content://com.android.externalstorage.documents/tree/primary%3AManga"
  private val newRoot = "content://com.android.externalstorage.documents/tree/primary%3AComics"
  private fun doc(root: String, path: String) = "$root/document/${root.substringAfter("/tree/")}$path"

  @After fun close() = db.close()

  @Test
  fun `ids move to the new folder only when they are inside the old one`() {
    assertEquals(doc(newRoot, "%2FBerserk%2FCh1.cbz"), remapLibraryId(doc(oldRoot, "%2FBerserk%2FCh1.cbz"), oldRoot, newRoot))
    assertEquals(doc(newRoot, "_root"), remapLibraryId(doc(oldRoot, "_root"), oldRoot, newRoot))
    // "Manga2" is a different folder that merely starts with "Manga"
    val sibling = "$oldRoot/document/primary%3AManga2%2FCh1.cbz"
    assertEquals(sibling, remapLibraryId(sibling, oldRoot, newRoot))
    assertEquals("/data/new/Berserk", remapLibraryId("/data/old/Berserk", "/data/old", "/data/new"))
    assertEquals("/elsewhere/x", remapLibraryId("/elsewhere/x", "/data/old", "/data/new"))
  }

  @Test
  fun `backup round trip restores everything onto the new folder`() = runBlocking {
    val series = doc(oldRoot, "%2FBerserk")
    val chapter = doc(oldRoot, "%2FBerserk%2FCh1.cbz")
    val cover = File(context.filesDir, "custom_covers/cover_1.jpg").apply { parentFile!!.mkdirs(); writeText("cover") }
    dao.replaceAll(
      BackupData(
        manga = listOf(MangaEntity(series, "Berserk", series, cover.absolutePath, isFavorite = true, lastReadChapterId = chapter, lastReadPage = 7)),
        chapterProgress = listOf(ChapterProgressEntity(chapter, series, "Ch1", lastReadPage = 7, totalPages = 20, isRead = true)),
        readingSessions = listOf(ReadingSessionEntity(0, series, chapter, "Ch1", 1000L, 61_000L, 60_000L, 0, 7, 7, false)),
        collections = listOf(CollectionEntity(1, "Seinen", 0)),
        collectionManga = listOf(CollectionMangaEntity(1, series)),
        tracks = listOf(MangaTrackEntity(series, remoteId = 30002, title = "Berserk", progress = 1, pendingSync = true)),
        chapterOverrides = listOf(ChapterOverrideEntity(chapter, series, "Chapter One", isHidden = true))
      )
    )
    prefs.edit().putString("pref_reading_direction", "LTR").putString("pref_pin_hash", "old-pin").putFloat("f", 1.5f).commit()

    val backup = ByteArrayOutputStream().also { backups.export(it, oldRoot) }.toByteArray()

    // A different install: other folder, own PIN, empty library and no cover files
    dao.replaceAll(BackupData(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList()))
    cover.delete()
    prefs.edit().clear().putString("pref_pin_hash", "new-pin").commit()

    backups.restore(backup.inputStream(), newRoot)

    val newSeries = doc(newRoot, "%2FBerserk")
    val newChapter = doc(newRoot, "%2FBerserk%2FCh1.cbz")
    val manga = dao.allManga().single()
    assertEquals(newSeries, manga.id)
    assertEquals(newSeries, manga.folderUriString)
    assertEquals(newChapter, manga.lastReadChapterId)
    assertTrue(manga.isFavorite)
    assertEquals("cover", File(manga.coverPath!!).readText())
    assertEquals(newChapter, dao.allProgress().single().chapterId)
    assertEquals(newChapter, dao.allSessions().single().chapterId)
    assertEquals(newSeries, dao.allCollectionManga().single().mangaId)
    assertEquals("Seinen", dao.allCollections().single().name)
    val override = dao.allChapterOverrides().single()
    assertEquals(newChapter, override.chapterId)
    assertEquals(newSeries, override.mangaId)
    assertEquals("Chapter One", override.customTitle)
    assertTrue(override.isHidden)
    val track = dao.allTracks().single()
    assertEquals(newSeries, track.mangaId)
    assertFalse(track.pendingSync)
    assertEquals("LTR", prefs.getString("pref_reading_direction", null))
    assertEquals(1.5f, prefs.getFloat("f", 0f))
    assertEquals("new-pin", prefs.getString("pref_pin_hash", null)) // the PIN never comes from a backup
  }

  @Test
  fun `a backup made before chapter changes existed still restores`() = runBlocking {
    // Version 1 backups have no "chapterOverrides" array at all
    val backup = ByteArrayOutputStream().also { out ->
      ZipOutputStream(out).use { zip ->
        fun write(name: String, text: String) {
          zip.putNextEntry(ZipEntry(name))
          zip.write(text.toByteArray())
          zip.closeEntry()
        }
        write("manifest.json", JSONObject().put("format", "moonreader-backup").put("version", 1).put("libraryRoot", oldRoot).toString())
        write("library.json", JSONObject()
          .put("manga", JSONArray())
          .put("chapterProgress", JSONArray())
          .put("readingSessions", JSONArray())
          .put("collections", JSONArray())
          .put("collectionManga", JSONArray())
          .put("tracks", JSONArray())
          .toString())
      }
    }.toByteArray()

    backups.restore(backup.inputStream(), newRoot)

    assertEquals(emptyList<ChapterOverrideEntity>(), dao.allChapterOverrides())
  }

  @Test
  fun `invalid backups are rejected without touching the library`() = runBlocking {
    val keep = MangaEntity("id", "Keep me", "id")
    dao.replaceAll(BackupData(listOf(keep), emptyList(), emptyList(), emptyList(), emptyList(), emptyList()))

    val notZip = "hello".toByteArray()
    val damaged = ByteArrayOutputStream().also { backups.export(it, oldRoot) }.toByteArray().let { it.copyOf(it.size / 2) }
    for (bytes in listOf(notZip, damaged)) {
      try {
        backups.restore(bytes.inputStream(), newRoot)
        fail("restore should have failed")
      } catch (_: BackupException) {
      }
      assertEquals(listOf(keep), dao.allManga())
    }
  }
}
