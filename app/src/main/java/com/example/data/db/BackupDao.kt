package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

/** Every table's rows, as written to and read from a backup. */
data class BackupData(
    val manga: List<MangaEntity>,
    val chapterProgress: List<ChapterProgressEntity>,
    val readingSessions: List<ReadingSessionEntity>,
    val collections: List<CollectionEntity>,
    val collectionManga: List<CollectionMangaEntity>,
    val tracks: List<MangaTrackEntity>
)

@Dao
interface BackupDao {
    @Query("SELECT * FROM manga_library") suspend fun allManga(): List<MangaEntity>
    @Query("SELECT * FROM chapter_progress") suspend fun allProgress(): List<ChapterProgressEntity>
    @Query("SELECT * FROM reading_sessions") suspend fun allSessions(): List<ReadingSessionEntity>
    @Query("SELECT * FROM collections") suspend fun allCollections(): List<CollectionEntity>
    @Query("SELECT * FROM collection_manga") suspend fun allCollectionManga(): List<CollectionMangaEntity>
    @Query("SELECT * FROM manga_tracks") suspend fun allTracks(): List<MangaTrackEntity>

    @Query("DELETE FROM manga_library") suspend fun clearManga()
    @Query("DELETE FROM chapter_progress") suspend fun clearProgress()
    @Query("DELETE FROM reading_sessions") suspend fun clearSessions()
    @Query("DELETE FROM collections") suspend fun clearCollections()
    @Query("DELETE FROM collection_manga") suspend fun clearCollectionManga()
    @Query("DELETE FROM manga_tracks") suspend fun clearTracks()

    @Insert suspend fun insertManga(rows: List<MangaEntity>)
    @Insert suspend fun insertProgress(rows: List<ChapterProgressEntity>)
    @Insert suspend fun insertSessions(rows: List<ReadingSessionEntity>)
    @Insert suspend fun insertCollections(rows: List<CollectionEntity>)
    @Insert suspend fun insertCollectionManga(rows: List<CollectionMangaEntity>)
    @Insert suspend fun insertTracks(rows: List<MangaTrackEntity>)

    /** All tables read in one transaction, so the backup is a consistent snapshot. */
    @Transaction
    suspend fun snapshot() = BackupData(
        allManga(), allProgress(), allSessions(), allCollections(), allCollectionManga(), allTracks()
    )

    /** Replaces every table with [data]; a failure rolls back and leaves the current data untouched. */
    @Transaction
    suspend fun replaceAll(data: BackupData) {
        clearManga(); clearProgress(); clearSessions(); clearCollections(); clearCollectionManga(); clearTracks()
        insertManga(data.manga)
        insertProgress(data.chapterProgress)
        insertSessions(data.readingSessions)
        insertCollections(data.collections)
        insertCollectionManga(data.collectionManga)
        insertTracks(data.tracks)
    }
}
