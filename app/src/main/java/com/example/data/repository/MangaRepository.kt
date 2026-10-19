package com.example.data.repository

import android.content.Context
import com.example.data.CbzPage
import com.example.data.Chapter
import com.example.data.Manga
import com.example.data.PageRef
import com.example.data.db.ChapterOverrideEntity
import com.example.data.db.ChapterProgressEntity
import com.example.data.db.MangaDao
import com.example.data.db.MangaEntity
import com.example.data.preferences.PreferencesManager
import com.example.data.storage.CbzArchive
import com.example.data.storage.CbzExtractor
import com.example.data.storage.ComicInfo
import com.example.data.storage.ComicInfoParser
import com.example.data.storage.MangaScanner
import com.example.data.storage.OpenArchives
import com.example.data.storage.SampleMangaGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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

    // null until the first scan of this process finishes
    private val _scannedManga = MutableStateFlow<List<Manga>?>(null)

    // Combined library flow: merges scanned filesystem structure with Room database progress & favorites
    val libraryManga: Flow<List<Manga>> = combine(
        _scannedManga,
        _scannedChapters,
        mangaDao.getAllManga(),
        mangaDao.getAllChapterProgress(),
        mangaDao.getAllChapterOverrides()
    ) { scannedList, chaptersById, dbEntities, progress, overrides ->
        val hiddenChapterIds = overrides.filter { it.isHidden }.map { it.chapterId }.toSet()
        val hiddenByManga = overrides.filter { it.isHidden }.groupBy({ it.mangaId }, { it.chapterId })
        val readCounts = progress.filter { it.isRead && it.chapterId !in hiddenChapterIds }
            .groupingBy { it.mangaId }.eachCount()
        // Chapter changes are counted against the chapters the scan actually found, so an override left
        // over from a file that is no longer on disk can't make the count go negative
        fun visibleChapterCount(mangaId: String, scanned: Int): Int {
            val foundIds = chaptersById[mangaId]?.map { it.id }?.toSet()
            val hidden = hiddenByManga[mangaId].orEmpty().count { foundIds == null || it in foundIds }
            return (scanned - hidden).coerceAtLeast(0)
        }
        // Show the library saved by the last scan immediately instead of an empty home screen
        // while the launch-time rescan runs; the scan result replaces it when ready.
        if (scannedList == null) {
            return@combine dbEntities.map { e ->
                Manga(
                    id = e.id,
                    title = e.title,
                    folderUriString = e.folderUriString,
                    chapterCount = visibleChapterCount(e.id, e.totalChapters),
                    coverPath = e.coverPath,
                    isFavorite = e.isFavorite,
                    lastReadChapterId = e.lastReadChapterId,
                    lastReadChapterTitle = e.lastReadChapterTitle,
                    lastReadPage = e.lastReadPage,
                    lastReadTime = e.lastReadTime,
                    readChapterCount = readCounts[e.id] ?: 0
                )
            }.sortedBy { it.title }
        }
        val entityMap = dbEntities.associateBy { it.id }

        scannedList.map { manga ->
            val entity = entityMap[manga.id]
            val chapterCount = visibleChapterCount(manga.id, manga.chapterCount)
            if (entity != null) {
                manga.copy(
                    chapterCount = chapterCount,
                    isFavorite = entity.isFavorite,
                    lastReadChapterId = entity.lastReadChapterId,
                    lastReadChapterTitle = entity.lastReadChapterTitle,
                    lastReadPage = entity.lastReadPage,
                    lastReadTime = entity.lastReadTime,
                    coverPath = entity.coverPath ?: manga.coverPath,
                    hasExplicitFolderCover = manga.hasExplicitFolderCover,
                    readChapterCount = readCounts[manga.id] ?: 0
                )
            } else {
                manga.copy(chapterCount = chapterCount, readChapterCount = readCounts[manga.id] ?: 0)
            }
        }
    }

    /**
     * Get chapters for a specific manga, merged with their read progress, renames and deletes from Room.
     */
    fun getChaptersForManga(mangaId: String): Flow<List<Chapter>> {
        return combine(
            _scannedChapters,
            mangaDao.getProgressForManga(mangaId),
            mangaDao.getOverridesForManga(mangaId)
        ) { chaptersMap, progressList, overrides ->
            mergeChapters(chaptersMap[mangaId] ?: emptyList(), progressList, overrides)
        }
    }

    /** Chapters removed from [mangaId] but still in the library folder; shown so they can be restored. */
    fun getRemovedChaptersForManga(mangaId: String): Flow<List<Chapter>> {
        return combine(
            _scannedChapters,
            mangaDao.getProgressForManga(mangaId),
            mangaDao.getOverridesForManga(mangaId)
        ) { chaptersMap, progressList, overrides ->
            mergeChapters(chaptersMap[mangaId] ?: emptyList(), progressList, overrides, includeRemoved = true)
                .filter { it.isRemoved }
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
        comicInfoCache.clear()

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

        // The library is already published above; generate missing first-page covers now, a few at a time.
        // Each lands via the Room flow, so cards fill in as they finish instead of the scan waiting for all.
        val coverSlots = Semaphore(3)
        coroutineScope {
            for (entity in entities) {
                val chapterUri = result.pendingCovers[entity.id] ?: continue
                // Only fill missing or auto-generated covers; keep folder covers and user-chosen ones
                val autoCover = CbzExtractor.coverFileFor(context, entity.id).absolutePath
                if (entity.coverPath != null && entity.coverPath != autoCover) continue
                launch {
                    coverSlots.withPermit {
                        CbzExtractor.extractCover(context, chapterUri, entity.id)
                            ?.let { mangaDao.updateCoverPath(entity.id, it) }
                    }
                }
            }
        }
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
            val scanned = _scannedManga.value?.find { it.id == mangaId }
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
        _scannedManga.value = _scannedManga.value?.map {
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
        if (!isRead) mangaDao.resetLastReadPage(mangaId, chapterId)
    }

    /** Marks every visible chapter of [mangaId] read or unread; returns those chapters. */
    suspend fun markMangaRead(mangaId: String, isRead: Boolean): List<Chapter> {
        // Hidden chapters are deleted as far as the library is concerned, so they are not touched
        val chapters = getChaptersForManga(mangaId).first()
        chapters.forEach { markChapterRead(it.id, mangaId, isRead) }
        // Fully unread means never started: leaves the Reading tab and Continue Reading, shows under Unread
        if (!isRead) mangaDao.clearLastRead(mangaId)
        return chapters
    }

    // chapter URI -> parsed ComicInfo (null = archive has none); cleared on rescan in case files changed
    private val comicInfoCache = java.util.concurrent.ConcurrentHashMap<String, java.util.Optional<ComicInfo>>()

    /** Metadata from the chapter archive's ComicInfo.xml, or null if it has none or can't be read. */
    suspend fun readComicInfo(chapterUriString: String): ComicInfo? = withContext(Dispatchers.IO) {
        comicInfoCache.getOrPut(chapterUriString) {
            val info = try {
                CbzArchive.open(context, chapterUriString)?.use { archive ->
                    archive.openComicInfo()?.use { ComicInfoParser.parse(it) }
                }
            } catch (e: Exception) {
                null
            }
            java.util.Optional.ofNullable(info)
        }.orElse(null)
    }

    /** Opens the chapter archive (kept open for reading) and returns its pages; empty if it can't be read. */
    suspend fun openChapterPages(chapterUriString: String): List<PageRef> = withContext(Dispatchers.IO) {
        OpenArchives.acquire(context, chapterUriString)?.pages.orEmpty()
    }

    /** Closes open chapter archives other than [chapterUris]. */
    suspend fun retainOpenChapters(chapterUris: Set<String>) = withContext(Dispatchers.IO) {
        OpenArchives.retainOnly(chapterUris)
    }

    suspend fun readPageBytes(page: PageRef): ByteArray? = withContext(Dispatchers.IO) {
        try {
            when (page) {
                is CbzPage -> OpenArchives.readPage(context, page) { it.readBytes() }
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun clearCache() = withContext(Dispatchers.IO) {
        CbzExtractor.clearChapterCache(context)
    }

    /**
     * Renames a chapter in the list. The archive keeps its name on disk: the new name is stored and
     * shown instead. A blank [title] drops the change and brings the file's name back.
     */
    suspend fun renameChapter(chapterId: String, mangaId: String, title: String) = withContext(Dispatchers.IO) {
        val existing = mangaDao.getChapterOverrideOnce(chapterId)
        val customTitle = title.trim().takeIf { it.isNotEmpty() }
        when {
            customTitle == null && existing?.isHidden != true -> mangaDao.deleteChapterOverride(chapterId)
            customTitle == null -> mangaDao.saveChapterOverride(existing!!.copy(customTitle = null))
            else -> mangaDao.saveChapterOverride(
                ChapterOverrideEntity(chapterId, mangaId, customTitle, existing?.isHidden ?: false)
            )
        }
        // Continue Reading and the widget show this saved name, not the chapter list's
        if (mangaDao.getMangaByIdOnce(mangaId)?.lastReadChapterId == chapterId) {
            val original = _scannedChapters.value[mangaId]?.firstOrNull { it.id == chapterId }?.title
            val shown = customTitle ?: original ?: return@withContext
            mangaDao.updateLastReadChapterTitle(mangaId, shown)
        }
    }

    /**
     * Removes a chapter from its series. The archive stays where it is; the chapter is remembered as
     * hidden so a later scan doesn't bring it back, and its saved progress goes with it.
     */
    suspend fun deleteChapter(chapterId: String, mangaId: String) = withContext(Dispatchers.IO) {
        val existing = mangaDao.getChapterOverrideOnce(chapterId)
        mangaDao.saveChapterOverride(
            ChapterOverrideEntity(chapterId, mangaId, existing?.customTitle, isHidden = true)
        )
        mangaDao.deleteChapterProgress(chapterId)
        if (mangaDao.getMangaByIdOnce(mangaId)?.lastReadChapterId == chapterId) mangaDao.clearLastRead(mangaId)
    }

    /** Puts a removed chapter back in the series, keeping any rename. It comes back unread. */
    suspend fun restoreChapter(chapterId: String) = withContext(Dispatchers.IO) {
        val existing = mangaDao.getChapterOverrideOnce(chapterId) ?: return@withContext
        if (existing.customTitle == null) mangaDao.deleteChapterOverride(chapterId)
        else mangaDao.saveChapterOverride(existing.copy(isHidden = false))
    }
}

/**
 * The chapters a series shows: the ones found by the scan, with saved progress and the user's changes
 * applied. Chapters marked hidden in [overrides] are dropped unless [includeRemoved] is set, in which
 * case they are kept and flagged [Chapter.isRemoved]; a custom title replaces the name from the file.
 */
internal fun mergeChapters(
    chapters: List<Chapter>,
    progress: List<ChapterProgressEntity>,
    overrides: List<ChapterOverrideEntity>,
    includeRemoved: Boolean = false
): List<Chapter> {
    val progressMap = progress.associateBy { it.chapterId }
    val overrideMap = overrides.associateBy { it.chapterId }
    return chapters.mapNotNull { chapter ->
        val override = overrideMap[chapter.id]
        val removed = override?.isHidden == true
        if (removed && !includeRemoved) return@mapNotNull null
        val named = override?.customTitle?.let { chapter.copy(title = it) } ?: chapter
        val saved = progressMap[chapter.id]
        val merged = if (saved == null) named else named.copy(
            lastReadPage = saved.lastReadPage,
            totalPages = if (saved.totalPages > 0) saved.totalPages else named.totalPages,
            isRead = saved.isRead,
            isBookmarked = saved.isBookmarked,
            lastReadTime = saved.lastUpdatedTime
        )
        if (removed) merged.copy(isRemoved = true) else merged
    }
}
