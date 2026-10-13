package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StatisticsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ReadingSessionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessions(sessions: List<ReadingSessionEntity>)

    @Query("SELECT * FROM reading_sessions ORDER BY startedAt DESC")
    fun getAllSessions(): Flow<List<ReadingSessionEntity>>

    @Query("SELECT * FROM reading_sessions ORDER BY startedAt DESC")
    suspend fun getAllSessionsOnce(): List<ReadingSessionEntity>

    @Query("SELECT * FROM reading_sessions ORDER BY endedAt DESC LIMIT 1")
    suspend fun getLatestSession(): ReadingSessionEntity?

    @Query("SELECT * FROM reading_sessions WHERE startedAt >= :startTime AND startedAt <= :endTime ORDER BY startedAt ASC")
    fun getSessionsBetween(startTime: Long, endTime: Long): Flow<List<ReadingSessionEntity>>

    @Query("SELECT * FROM reading_sessions WHERE startedAt >= :startTime AND startedAt <= :endTime ORDER BY startedAt ASC")
    suspend fun getSessionsBetweenOnce(startTime: Long, endTime: Long): List<ReadingSessionEntity>

    @Query("SELECT * FROM reading_sessions WHERE mangaId = :mangaId ORDER BY startedAt DESC")
    fun getSessionsForManga(mangaId: String): Flow<List<ReadingSessionEntity>>

    @Query("SELECT COALESCE(SUM(durationMillis), 0) FROM reading_sessions WHERE startedAt >= :startTime AND startedAt <= :endTime")
    fun getTotalDurationBetween(startTime: Long, endTime: Long): Flow<Long>

    @Query("SELECT COUNT(*) FROM reading_sessions WHERE completedChapter = 1 AND startedAt >= :startTime AND startedAt <= :endTime")
    fun getCompletedChaptersCountBetween(startTime: Long, endTime: Long): Flow<Int>

    @Query("SELECT COALESCE(SUM(pagesProgressed), 0) FROM reading_sessions WHERE startedAt >= :startTime AND startedAt <= :endTime")
    fun getPagesProgressedBetween(startTime: Long, endTime: Long): Flow<Int>

    @Query("SELECT * FROM reading_sessions ORDER BY durationMillis DESC LIMIT 1")
    fun getLongestSession(): Flow<ReadingSessionEntity?>

    @Query("SELECT * FROM reading_sessions ORDER BY durationMillis DESC LIMIT 1")
    suspend fun getLongestSessionOnce(): ReadingSessionEntity?

    @Query("DELETE FROM reading_sessions")
    suspend fun clearAllSessions()
}
