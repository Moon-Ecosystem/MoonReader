package com.example.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

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
