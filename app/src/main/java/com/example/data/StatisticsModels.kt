package com.example.data

/**
 * Filter time period for the statistics dashboard.
 */
enum class TimePeriod(val displayName: String) {
    TODAY("Today"),
    THIS_WEEK("This Week"),
    THIS_MONTH("This Month"),
    THIS_YEAR("This Year"),
    ALL_TIME("All Time")
}

/**
 * Metrics that can be toggled in the activity-over-time chart.
 */
enum class ActivityChartMetric(val displayName: String, val unit: String) {
    READING_TIME("Reading Time", "min"),
    CHAPTERS("Chapters", "ch"),
    PAGES("Pages", "pages")
}

/**
 * Calculated summary metrics for the selected time period.
 */
data class SummaryStatistics(
    val totalReadingTimeMillis: Long = 0L,
    val avgReadingTimePerActiveDayMillis: Long = 0L,
    val avgReadingTimePerSessionMillis: Long = 0L,
    val chaptersCompleted: Int = 0,
    val chaptersCompletedDiff: Int? = null,
    val chaptersCompletedPercentDiff: Float? = null,
    val pagesProgressed: Int = 0,
    val avgPagesPerActiveDay: Float = 0f,
    val pagesProgressedDiff: Int? = null,
    val pagesProgressedPercentDiff: Float? = null,
    val activeDaysCount: Int = 0,
    val activeDaysPercentage: Float = 0f,
    val distinctSeriesCount: Int = 0,
    val currentStreakDays: Int = 0,
    val longestStreakDays: Int = 0
)

/**
 * A single data point on the activity chart.
 */
data class ActivityDataPoint(
    val label: String,
    val timestamp: Long,
    val value: Float,
    val formattedValue: String,
    val secondaryInfo: String = ""
)

/**
 * Reading activity summary for a single local calendar date (GitHub-style calendar).
 */
data class DayActivity(
    val dateString: String, // "yyyy-MM-dd"
    val epochDay: Long,
    val dayOfMonth: Int,
    val monthOfYear: Int,
    val year: Int,
    val readingTimeMillis: Long,
    val chaptersCompleted: Int,
    val pagesProgressed: Int,
    val sessionCount: Int,
    val intensity: Int // 0 (none) to 4 (heavy)
)

/**
 * Statistics aggregated per manga series.
 */
data class SeriesStatistics(
    val mangaId: String,
    val title: String,
    val coverPath: String?,
    val totalChapters: Int,
    val chaptersCompleted: Int,
    val pagesProgressed: Int,
    val readingTimeMillis: Long,
    val lastReadTime: Long,
    val lastReadChapterTitle: String?
)

/**
 * Personal records and long-term reading habit insights.
 */
data class ReadingHabits(
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val mostActiveWeekday: String = "N/A",
    val avgReadingTimePerDayMillis: Long = 0L,
    val avgChaptersPerDay: Float = 0f,
    val longestSessionDurationMillis: Long = 0L,
    val longestSessionMangaTitle: String? = null,
    val recordPagesInDay: Int = 0,
    val recordPagesDate: String? = null,
    val recordChaptersInDay: Int = 0,
    val recordChaptersDate: String? = null,
    val readingSpeedPagesPerMinute: Float = 0f
)

/**
 * High-level counts of manga in the user's library.
 */
data class LibraryStatusCounts(
    val currentlyReading: Int = 0,
    val completed: Int = 0,
    val unread: Int = 0,
    val favorites: Int = 0
)

/**
 * Comparison details between the selected period and previous equivalent period.
 */
data class PeriodComparison(
    val previousPeriodLabel: String,
    val timeDiffMillis: Long,
    val timePercentDiff: Float?,
    val chaptersDiff: Int,
    val chaptersPercentDiff: Float?,
    val pagesDiff: Int,
    val pagesPercentDiff: Float?,
    val activeDaysDiff: Int
)

/**
 * Full state bundle passed to the Statistics Screen UI.
 */
data class StatisticsUiState(
    val selectedPeriod: TimePeriod = TimePeriod.THIS_WEEK,
    val selectedMetric: ActivityChartMetric = ActivityChartMetric.READING_TIME,
    val isLoading: Boolean = true,
    val summary: SummaryStatistics = SummaryStatistics(),
    val chartDataPoints: List<ActivityDataPoint> = emptyList(),
    val calendarDays: List<DayActivity> = emptyList(),
    val calendarMonthLabel: String = "",
    val calendarMonthOffset: Int = 0, // 0 = current month, -1 = previous month
    val selectedCalendarDay: DayActivity? = null,
    val comparison: PeriodComparison? = null,
    val libraryCounts: LibraryStatusCounts = LibraryStatusCounts(),
    val mostReadSeries: List<SeriesStatistics> = emptyList(),
    val recentSeries: List<Manga> = emptyList(),
    val habits: ReadingHabits = ReadingHabits(),
    val hasSufficientHistory: Boolean = false
)
