package com.example.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.tracking.TrackStatus

@Entity(tableName = "manga_library")
data class MangaEntity(
    @PrimaryKey val id: String,
    val title: String,
    val folderUriString: String,
    val coverPath: String? = null,
    val isFavorite: Boolean = false,
    val lastReadChapterId: String? = null,
    val lastReadChapterTitle: String? = null,
    val lastReadPage: Int = 0,
    val lastReadTime: Long = 0L,
    val totalChapters: Int = 0
)

@Entity(tableName = "chapter_progress")
data class ChapterProgressEntity(
    @PrimaryKey val chapterId: String,
    val mangaId: String,
    val chapterTitle: String,
    val lastReadPage: Int = 0,
    val totalPages: Int = 0,
    val isRead: Boolean = false,
    val isBookmarked: Boolean = false,
    val lastUpdatedTime: Long = System.currentTimeMillis()
)

/**
 * What the user changed about a chapter in the chapter list. Chapters come from scanning the library
 * folder, so they are not rows of their own: a rename or a delete is kept here and re-applied on every
 * scan. [customTitle] replaces the name taken from the file; [isHidden] keeps a chapter out of the
 * series without deleting the archive.
 */
@Entity(tableName = "chapter_overrides")
data class ChapterOverrideEntity(
    @PrimaryKey val chapterId: String,
    val mangaId: String,
    val customTitle: String? = null,
    val isHidden: Boolean = false
)

/**
 * Persisted record of an actual reading session.
 * Tracks reading duration, pages progressed, and chapter completion with exact timestamps.
 */
@Entity(
    tableName = "reading_sessions",
    indices = [
        Index("mangaId"),
        Index("chapterId"),
        Index("startedAt")
    ]
)
data class ReadingSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mangaId: String,
    val chapterId: String,
    val chapterTitle: String,
    val startedAt: Long,
    val endedAt: Long,
    val durationMillis: Long,
    val startPage: Int,
    val endPage: Int,
    val pagesProgressed: Int,
    val completedChapter: Boolean
)

/** A user-made library collection; [position] is its place in the home screen tabs. */
@Entity(tableName = "collections")
data class CollectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val position: Int
)

/** A series in a collection; a series can be in any number of collections. */
@Entity(
    tableName = "collection_manga",
    primaryKeys = ["collectionId", "mangaId"],
    indices = [Index("mangaId")]
)
data class CollectionMangaEntity(
    val collectionId: Long,
    val mangaId: String
)

/**
 * A local series linked to its AniList entry, mirroring the remote list entry.
 * [pendingSync] marks progress read offline that still has to reach AniList.
 */
@Entity(tableName = "manga_tracks")
data class MangaTrackEntity(
    @PrimaryKey val mangaId: String,
    val remoteId: Long,
    val libraryId: Long? = null,
    val title: String,
    val coverUrl: String? = null,
    val status: TrackStatus? = null,
    val progress: Int = 0,
    val progressVolumes: Int = 0,
    val totalChapters: Int = 0,
    val totalVolumes: Int = 0,
    val score: Int = 0, // 0–100, AniList's raw score whatever format the user displays
    val startedAt: String? = null, // ISO date (yyyy-MM-dd)
    val completedAt: String? = null,
    val pendingSync: Boolean = false
)
