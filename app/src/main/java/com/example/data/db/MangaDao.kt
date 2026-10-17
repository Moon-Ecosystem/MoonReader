package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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
    suspend fun updateCoverPath(id: String, coverPath: String)

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

    @Query("UPDATE chapter_progress SET isBookmarked = :isBookmarked, lastUpdatedTime = :time WHERE chapterId = :chapterId")
    suspend fun updateChapterBookmark(chapterId: String, isBookmarked: Boolean, time: Long)

    @Query("SELECT * FROM chapter_progress")
    fun getAllChapterProgress(): Flow<List<ChapterProgressEntity>>

    @Query("SELECT * FROM chapter_progress")
    suspend fun getAllChapterProgressOnce(): List<ChapterProgressEntity>

    @Query("DELETE FROM manga_library WHERE id = :id")
    suspend fun deleteManga(id: String)
}
