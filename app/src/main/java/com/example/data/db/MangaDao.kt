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

    @Query("UPDATE chapter_progress SET isRead = :isRead, lastUpdatedTime = :time WHERE chapterId = :chapterId")
    suspend fun updateChapterRead(chapterId: String, isRead: Boolean, time: Long)

    @Query("UPDATE chapter_progress SET isBookmarked = :isBookmarked, lastUpdatedTime = :time WHERE chapterId = :chapterId")
    suspend fun updateChapterBookmark(chapterId: String, isBookmarked: Boolean, time: Long)

    @Query("DELETE FROM manga_library WHERE id = :id")
    suspend fun deleteManga(id: String)
}
