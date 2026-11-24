package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface MangaDao {
    @Query("SELECT * FROM manga_library ORDER BY lastReadTime DESC, title ASC")
    fun getAllManga(): Flow<List<MangaEntity>>

    /** Started but not finished, most recently read first (the Reading tab's rule, for the home screen widget). */
    @Query(
        """SELECT * FROM manga_library WHERE lastReadTime > 0 AND lastReadChapterId IS NOT NULL
        AND totalChapters > (SELECT COUNT(*) FROM chapter_progress p WHERE p.mangaId = manga_library.id AND p.isRead)
        ORDER BY lastReadTime DESC LIMIT :limit"""
    )
    suspend fun getInProgressManga(limit: Int): List<MangaEntity>

    @Query("SELECT * FROM manga_library WHERE id = :id LIMIT 1")
    fun getMangaById(id: String): Flow<MangaEntity?>

    @Query("SELECT * FROM manga_library WHERE id = :id LIMIT 1")
    suspend fun getMangaByIdOnce(id: String): MangaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateManga(manga: MangaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateMangaList(mangaList: List<MangaEntity>)

    @Query("UPDATE manga_library SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: String, isFavorite: Boolean)

    @Query("UPDATE manga_library SET coverPath = :coverPath WHERE id = :id")
    suspend fun updateCoverPath(id: String, coverPath: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNewManga(manga: List<MangaEntity>)

    @Query("""UPDATE manga_library SET title = :title, folderUriString = :folderUri,
        totalChapters = :chapters, archiveModifiedTime = :modified WHERE id = :id""")
    suspend fun updateScannedMetadata(id: String, title: String, folderUri: String, chapters: Int, modified: Long)

    @Query("UPDATE manga_library SET coverPath = :coverPath WHERE id = :id AND coverPath IS :expectedPath")
    suspend fun updateScannedCover(id: String, expectedPath: String?, coverPath: String?): Int

    @Transaction
    suspend fun saveScannedManga(manga: List<MangaEntity>, previousCovers: Map<String, String?>) {
        insertNewManga(manga)
        for (entry in manga) {
            updateScannedMetadata(entry.id, entry.title, entry.folderUriString, entry.totalChapters, entry.archiveModifiedTime)
            val previousPath = previousCovers[entry.id]
            if (previousPath != entry.coverPath) updateScannedCover(entry.id, previousPath, entry.coverPath)
        }
    }

    @Query("UPDATE manga_library SET lastReadChapterId = :chapterId, lastReadChapterTitle = :chapterTitle, lastReadPage = :page, lastReadTime = :time WHERE id = :mangaId")
    suspend fun updateLastRead(mangaId: String, chapterId: String, chapterTitle: String, page: Int, time: Long)

    @Query("SELECT * FROM chapter_progress WHERE mangaId = :mangaId")
    fun getProgressForManga(mangaId: String): Flow<List<ChapterProgressEntity>>

    @Query("SELECT * FROM chapter_progress WHERE chapterId = :chapterId LIMIT 1")
    fun getProgressForChapter(chapterId: String): Flow<ChapterProgressEntity?>

    @Query("SELECT * FROM chapter_progress WHERE chapterId = :chapterId LIMIT 1")
    suspend fun getProgressForChapterOnce(chapterId: String): ChapterProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveChapterProgress(progress: ChapterProgressEntity)

    // Marking unread also starts the chapter over from the first page
    @Query("UPDATE chapter_progress SET isRead = :isRead, lastReadPage = CASE WHEN :isRead THEN lastReadPage ELSE 0 END, lastUpdatedTime = :time WHERE chapterId = :chapterId")
    suspend fun updateChapterRead(chapterId: String, isRead: Boolean, time: Long)

    @Query("UPDATE manga_library SET lastReadChapterId = NULL, lastReadChapterTitle = NULL, lastReadPage = 0, lastReadTime = 0 WHERE id = :mangaId")
    suspend fun clearLastRead(mangaId: String)

    @Query("UPDATE manga_library SET lastReadPage = 0 WHERE id = :mangaId AND lastReadChapterId = :chapterId")
    suspend fun resetLastReadPage(mangaId: String, chapterId: String)

    @Query("UPDATE manga_library SET lastReadChapterTitle = :chapterTitle WHERE id = :mangaId")
    suspend fun updateLastReadChapterTitle(mangaId: String, chapterTitle: String)

    @Query("UPDATE chapter_progress SET isBookmarked = :isBookmarked, lastUpdatedTime = :time WHERE chapterId = :chapterId")
    suspend fun updateChapterBookmark(chapterId: String, isBookmarked: Boolean, time: Long)

    @Query("SELECT * FROM chapter_progress")
    fun getAllChapterProgress(): Flow<List<ChapterProgressEntity>>

    @Query("SELECT * FROM chapter_progress")
    suspend fun getAllChapterProgressOnce(): List<ChapterProgressEntity>

    @Query("SELECT * FROM chapter_overrides WHERE mangaId = :mangaId")
    fun getOverridesForManga(mangaId: String): Flow<List<ChapterOverrideEntity>>

    @Query("SELECT * FROM chapter_overrides")
    fun getAllChapterOverrides(): Flow<List<ChapterOverrideEntity>>

    @Query("SELECT * FROM chapter_overrides WHERE chapterId = :chapterId LIMIT 1")
    suspend fun getChapterOverrideOnce(chapterId: String): ChapterOverrideEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveChapterOverride(override: ChapterOverrideEntity)

    @Query("DELETE FROM chapter_overrides WHERE chapterId = :chapterId")
    suspend fun deleteChapterOverride(chapterId: String)

    @Query("DELETE FROM chapter_progress WHERE chapterId = :chapterId")
    suspend fun deleteChapterProgress(chapterId: String)

    @Query("DELETE FROM manga_library WHERE id = :id")
    suspend fun deleteManga(id: String)
}
