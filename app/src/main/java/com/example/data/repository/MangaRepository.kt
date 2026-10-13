package com.example.data.repository

import android.content.Context
import com.example.data.Chapter
import com.example.data.Manga
import com.example.data.db.ChapterProgressEntity
import com.example.data.db.MangaDao
import com.example.data.db.MangaEntity
import com.example.data.preferences.PreferencesManager
import com.example.data.storage.CbzExtractor
import com.example.data.storage.MangaScanner
import com.example.data.storage.SampleMangaGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

class MangaRepository(
    private val context: Context,
    private val mangaDao: MangaDao,
    private val preferencesManager: PreferencesManager
) {
    // In-memory cache of scanned chapters per manga
    private val _scannedChapters = MutableStateFlow<Map<String, List<Chapter>>>(emptyMap())
    val scannedChapters: Flow<Map<String, List<Chapter>>> = _scannedChapters.asStateFlow()

    private val _scannedManga = MutableStateFlow<List<Manga>>(emptyList())

    // Combined library flow: merges scanned filesystem structure with Room database progress & favorites
    val libraryManga: Flow<List<Manga>> = combine(
        _scannedManga,
        mangaDao.getAllManga()
    ) { scannedList, dbEntities ->
        val entityMap = dbEntities.associateBy { it.id }

        scannedList.map { manga ->
            val entity = entityMap[manga.id]
            if (entity != null) {
                manga.copy(
                    isFavorite = entity.isFavorite,
                    lastReadChapterId = entity.lastReadChapterId,
                    lastReadChapterTitle = entity.lastReadChapterTitle,
                    lastReadPage = entity.lastReadPage,
                    lastReadTime = entity.lastReadTime,
                    coverPath = entity.coverPath ?: manga.coverPath,
                    hasExplicitFolderCover = manga.hasExplicitFolderCover
                )
            } else {
                manga
            }
        }
    }

    /**
     * Get chapters for a specific manga, merged with their read progress from Room.
     */
    fun getChaptersForManga(mangaId: String): Flow<List<Chapter>> {
        return combine(
            _scannedChapters,
            mangaDao.getProgressForManga(mangaId)
        ) { chaptersMap, progressList ->
            val chapters = chaptersMap[mangaId] ?: emptyList()
            val progressMap = progressList.associateBy { it.chapterId }

            chapters.map { ch ->
                val progress = progressMap[ch.id]
                if (progress != null) {
                    ch.copy(
                        lastReadPage = progress.lastReadPage,
                        totalPages = if (progress.totalPages > 0) progress.totalPages else ch.totalPages,
                        isRead = progress.isRead,
                        isBookmarked = progress.isBookmarked,
                        lastReadTime = progress.lastUpdatedTime
                    )
                } else {
                    ch
                }
            }
        }
    }

    suspend fun scanCurrentLibrary(): Boolean = withContext(Dispatchers.IO) {
        val folderUri = preferencesManager.folderUri.value
        if (folderUri.isNullOrBlank()) {
            return@withContext false
        }

        val result = MangaScanner.scanFolder(context, folderUri)
        _scannedManga.value = result.mangaList
        _scannedChapters.value = result.chaptersByMangaId

        // Persist scanned manga headers to Room so metadata is retained
        val existingById = mangaDao.getAllManga().first().associateBy { it.id }
        val entities = result.mangaList.map { manga ->
            val existing = existingById[manga.id]

            // Resolve cover path with proper priority:
            // 1. If an explicit cover image file (cover.jpg/png) was found in the manga folder, use it!
            // 2. If the user previously chose a custom cover or has one saved in DB that exists on disk, keep it!
            // 3. Fallback to freshly scanned default chapter cover.
            val finalCoverPath = when {
                manga.hasExplicitFolderCover && !manga.coverPath.isNullOrBlank() -> {
                    manga.coverPath
                }
                existing?.coverPath != null && File(existing.coverPath).exists() -> {
                    existing.coverPath
                }
                else -> {
                    manga.coverPath
                }
            }

            MangaEntity(
                id = manga.id,
                title = manga.title,
                folderUriString = manga.folderUriString,
                coverPath = finalCoverPath,
                isFavorite = existing?.isFavorite ?: false,
                lastReadChapterId = existing?.lastReadChapterId,
                lastReadChapterTitle = existing?.lastReadChapterTitle,
                lastReadPage = existing?.lastReadPage ?: 0,
                lastReadTime = existing?.lastReadTime ?: 0L,
                totalChapters = manga.chapterCount
            )
        }
        mangaDao.insertOrUpdateMangaList(entities)
        true
    }

    suspend fun setLibraryFolder(uriString: String) = withContext(Dispatchers.IO) {
        preferencesManager.setFolderUri(uriString)
        scanCurrentLibrary()
    }

    suspend fun loadSampleManga(): String = withContext(Dispatchers.IO) {
        val samplePath = SampleMangaGenerator.ensureSampleMangaExists(context)
        preferencesManager.setFolderUri(samplePath)
        scanCurrentLibrary()
        samplePath
    }

    suspend fun toggleFavorite(mangaId: String, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        mangaDao.updateFavorite(mangaId, isFavorite)
    }

    suspend fun updateMangaCover(mangaId: String, newCoverPath: String) = withContext(Dispatchers.IO) {
        val existing = mangaDao.getMangaByIdOnce(mangaId)
        if (existing != null) {
            mangaDao.updateCoverPath(mangaId, newCoverPath)
        } else {
            val scanned = _scannedManga.value.find { it.id == mangaId }
            if (scanned != null) {
                mangaDao.insertOrUpdateManga(
                    MangaEntity(
                        id = scanned.id,
                        title = scanned.title,
                        folderUriString = scanned.folderUriString,
                        coverPath = newCoverPath,
                        totalChapters = scanned.chapterCount
                    )
                )
            }
        }
        // Update in-memory state so UI updates instantaneously
        _scannedManga.value = _scannedManga.value.map {
            if (it.id == mangaId) it.copy(coverPath = newCoverPath) else it
        }
    }

    suspend fun saveReadingProgress(
        mangaId: String,
        chapterId: String,
        chapterTitle: String,
        page: Int,
        totalPages: Int,
        isCompleted: Boolean
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val existing = mangaDao.getProgressForChapterOnce(chapterId)
        val progress = ChapterProgressEntity(
            chapterId = chapterId,
            mangaId = mangaId,
            chapterTitle = chapterTitle,
            lastReadPage = page,
            totalPages = totalPages,
            isRead = isCompleted || (existing?.isRead == true),
            isBookmarked = existing?.isBookmarked ?: false,
            lastUpdatedTime = now
        )
        mangaDao.saveChapterProgress(progress)

        // Also update parent manga's last read pointer
        mangaDao.updateLastRead(mangaId, chapterId, chapterTitle, page, now)
    }

    suspend fun toggleChapterBookmark(chapterId: String, mangaId: String) = withContext(Dispatchers.IO) {
        val existing = mangaDao.getProgressForChapterOnce(chapterId)
        val newBookmark = !(existing?.isBookmarked ?: false)
        val now = System.currentTimeMillis()
        if (existing == null) {
            val progress = ChapterProgressEntity(
                chapterId = chapterId,
                mangaId = mangaId,
                chapterTitle = "",
                lastReadPage = 0,
                totalPages = 0,
                isRead = false,
                isBookmarked = newBookmark,
                lastUpdatedTime = now
            )
            mangaDao.saveChapterProgress(progress)
        } else {
            mangaDao.updateChapterBookmark(chapterId, newBookmark, now)
        }
    }

    suspend fun markChapterRead(chapterId: String, mangaId: String, isRead: Boolean) = withContext(Dispatchers.IO) {
        val existing = mangaDao.getProgressForChapterOnce(chapterId)
        val now = System.currentTimeMillis()
        if (existing == null) {
            val progress = ChapterProgressEntity(
                chapterId = chapterId,
                mangaId = mangaId,
                chapterTitle = "",
                lastReadPage = 0,
                totalPages = 0,
                isRead = isRead,
                isBookmarked = false,
                lastUpdatedTime = now
            )
            mangaDao.saveChapterProgress(progress)
        } else {
            mangaDao.updateChapterRead(chapterId, isRead, now)
        }
    }

    suspend fun getChapterPages(chapterId: String, chapterUriString: String): List<File> = withContext(Dispatchers.IO) {
        CbzExtractor.extractPages(context, chapterUriString, chapterId)
    }

    suspend fun clearCache(): Long = withContext(Dispatchers.IO) {
        CbzExtractor.clearChapterCache(context)
    }

    suspend fun getCacheSizeBytes(): Long = withContext(Dispatchers.IO) {
        CbzExtractor.getCacheSize(context)
    }
}
