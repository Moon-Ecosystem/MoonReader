package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT * FROM manga_tracks WHERE mangaId = :mangaId LIMIT 1")
    fun observe(mangaId: String): Flow<MangaTrackEntity?>

    @Query("SELECT * FROM manga_tracks WHERE mangaId = :mangaId LIMIT 1")
    suspend fun get(mangaId: String): MangaTrackEntity?

    @Query("SELECT * FROM manga_tracks WHERE pendingSync = 1")
    suspend fun getPending(): List<MangaTrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(track: MangaTrackEntity)

    @Query("DELETE FROM manga_tracks WHERE mangaId = :mangaId")
    suspend fun delete(mangaId: String)
}
