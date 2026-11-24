package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.CbzPage
import com.example.data.backup.BackupRepository
import com.example.data.db.AppDatabase
import com.example.data.preferences.PreferencesManager
import com.example.data.repository.MangaRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CoverPersistenceTest {
    @Test
    fun `manual covers survive folder scans concurrent defaults and backup restore`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = File(context.cacheDir, "cover_persistence_library").apply { mkdirs() }
        val series = File(root, "Series").apply { mkdirs() }
        val archive = File(series, "Chapter 01.cbz")
        ZipOutputStream(archive.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("001.png"))
            zip.write(coverImage(Color.BLUE))
            zip.closeEntry()
        }
        File(series, "cover.jpg").writeBytes(coverImage(Color.RED))
        val image = File(context.cacheDir, "gallery.png").apply { writeBytes(coverImage(Color.GREEN)) }
        val customDir = File(context.filesDir, "custom_covers").apply { mkdirs() }
        val legacyCover = File(customDir, "cover_123_1600000000000.jpg").apply { writeBytes(image.readBytes()) }
        val id = series.absolutePath
        val prefs = PreferencesManager(context).apply { setFolderUri(root.absolutePath) }
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = db.mangaDao()
            val repository = MangaRepository(context, dao, prefs)
            suspend fun verifyReloaded(expected: String) {
                val restarted = MangaRepository(context, dao, prefs)
                assertEquals(expected, restarted.libraryManga.first().single().coverPath)
                assertTrue(restarted.scanCurrentLibrary())
                assertEquals(expected, restarted.libraryManga.first().single().coverPath)
            }

            repository.scanCurrentLibrary()
            val scannerCover = dao.getMangaByIdOnce(id)!!.coverPath!!
            val backups = BackupRepository(context, db.backupDao())
            suspend fun verifyBackupRestored(expected: String) {
                val saved = ByteArrayOutputStream().also { backups.export(it, root.absolutePath) }.toByteArray()
                check(File(expected).delete())
                repository.scanCurrentLibrary()
                assertEquals(scannerCover, dao.getMangaByIdOnce(id)!!.coverPath)
                backups.restore(saved.inputStream(), root.absolutePath)
                assertTrue(File(expected).isFile)
                verifyReloaded(expected)
            }
            dao.updateCoverPath(id, legacyCover.absolutePath)
            verifyReloaded(legacyCover.absolutePath)

            val gallery = Uri.fromFile(image)
            assertTrue(repository.saveCustomCover(id) { context.contentResolver.openInputStream(gallery) })
            val galleryCover = dao.getMangaByIdOnce(id)!!.coverPath!!
            assertNotEquals(legacyCover.absolutePath, galleryCover)
            verifyReloaded(galleryCover)
            verifyBackupRestored(galleryCover)

            val page = CbzPage(archive.absolutePath, "001.png", 0)
            val pageBytes = repository.readPageBytes(page)!!
            assertTrue(repository.saveCustomCover(id) { pageBytes.inputStream() })
            val pageCover = dao.getMangaByIdOnce(id)!!.coverPath!!
            assertNotEquals(galleryCover, pageCover)

            // Both an older scan's header write and its asynchronous thumbnail can finish after a user save.
            dao.updateFavorite(id, true)
            dao.updateLastRead(id, archive.absolutePath, "Chapter 01", 2, 123L)
            val staleHeader = dao.getMangaByIdOnce(id)!!.copy(coverPath = scannerCover, isFavorite = false, lastReadTime = 0)
            dao.saveScannedManga(listOf(staleHeader), mapOf(id to galleryCover))
            assertEquals(0, dao.updateScannedCover(id, galleryCover, scannerCover))
            assertTrue(dao.getMangaByIdOnce(id)!!.isFavorite)
            assertEquals(123L, dao.getMangaByIdOnce(id)!!.lastReadTime)
            assertFalse(repository.saveCustomCover(id) { null })
            verifyReloaded(pageCover)

            verifyBackupRestored(pageCover)
        } finally {
            db.close()
        }
    }

    private fun coverImage(color: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(24, 32, Bitmap.Config.ARGB_8888)
        return try {
            bitmap.eraseColor(color)
            ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        } finally {
            bitmap.recycle()
        }
    }
}
