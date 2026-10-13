package com.example.data

import android.net.Uri

/**
 * Reading direction modes supported by the reader.
 */
enum class ReadingDirection(val displayName: String) {
    RTL("Right to Left (Manga)"),
    LTR("Left to Right"),
    WEBTOON("Vertical Continuous (Webtoon)")
}

/**
 * Reader background theme.
 */
enum class ReaderTheme(val displayName: String) {
    BLACK("Pure Black"),
    DARK_GRAY("Dark Gray"),
    LIGHT("Light")
}

/**
 * Reader image fit modes.
 */
enum class ReaderFitMode(val displayName: String) {
    FIT_SCREEN("Fit Screen"),
    FIT_WIDTH("Fit Width"),
    FIT_HEIGHT("Fit Height")
}

/**
 * Library sort orders. [ascendingByDefault] is the direction used when an option is first picked
 * (A–Z for titles; newest / largest first for the rest).
 */
enum class LibrarySort(val displayName: String, val ascendingByDefault: Boolean) {
    LAST_READ("Last read", false),
    TITLE("Title", true),
    TOTAL_CHAPTERS("Total chapters", false),
    UNREAD("Unread chapters", false)
}

/**
 * 5 Accent color presets matching the project specifications.
 */
enum class AccentPreset(val displayName: String, val hex: Long, val darkHex: Long) {
    NIGHT_BLUE("Night Blue", 0xFF2563EBL, 0xFF60A5FAL),
    CRIMSON_RED("Crimson Red", 0xFFDC2626L, 0xFFF87171L),
    CYBER_PURPLE("Cyber Purple", 0xFF7C4DFFL, 0xFFB388FFL),
    EMERALD("Emerald", 0xFF059669L, 0xFF34D399L),
    WARM_RUST("Warm Rust", 0xFF8A4B2FL, 0xFFE0A97AL),
    CORAL_PINK("Coral Pink", 0xFFE11D48L, 0xFFFB7185L),
    SYSTEM("System Dynamic", 0L, 0L)
}

/**
 * Domain representation of a Manga title.
 */
data class Manga(
    val id: String,
    val title: String,
    val folderUriString: String,
    val chapterCount: Int,
    val coverPath: String? = null,
    val hasExplicitFolderCover: Boolean = false,
    val isFavorite: Boolean = false,
    val lastReadChapterId: String? = null,
    val lastReadChapterTitle: String? = null,
    val lastReadPage: Int = 0,
    val lastReadTime: Long = 0L,
    val readChapterCount: Int = 0
) {
    val isCompleted: Boolean get() = chapterCount > 0 && readChapterCount >= chapterCount
}

/**
 * Domain representation of a Chapter in a Manga.
 */
data class Chapter(
    val id: String,
    val mangaId: String,
    val title: String,
    val fileName: String,
    val fileUriString: String,
    val chapterNumber: Float = 0f,
    val lastReadPage: Int = 0,
    val totalPages: Int = 0,
    val isRead: Boolean = false,
    val isBookmarked: Boolean = false,
    val lastReadTime: Long = 0L
)

/**
 * A page the reader can show. The reader and image loading only see this type, so new page sources
 * (e.g. an online source serving page URLs) can be added without touching the reader.
 */
sealed interface PageRef {
    val index: Int
    val displayName: String
}

/** A page read directly from inside a CBZ/ZIP chapter archive; nothing is extracted to disk. */
data class CbzPage(
    val archiveUri: String,
    val entryName: String,
    override val index: Int
) : PageRef {
    override val displayName: String get() = entryName.replace('\\', '/').substringAfterLast('/')
}
