package com.example.data.repository

import com.example.data.ActivityChartMetric
import com.example.data.ActivityDataPoint
import com.example.data.DayActivity
import com.example.data.LibraryStatusCounts
import com.example.data.Manga
import com.example.data.PeriodComparison
import com.example.data.ReadingHabits
import com.example.data.SeriesStatistics
import com.example.data.SummaryStatistics
import com.example.data.TimePeriod
import com.example.data.db.ChapterProgressEntity
import com.example.data.db.MangaDao
import com.example.data.db.ReadingSessionEntity
import com.example.data.db.StatisticsDao
import com.example.data.preferences.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

class StatisticsRepository(
    private val statisticsDao: StatisticsDao,
    private val mangaDao: MangaDao,
    private val preferencesManager: PreferencesManager
) {
    val allSessions: Flow<List<ReadingSessionEntity>> = statisticsDao.getAllSessions()

    /**
     * Persist a completed reading session to the database if statistics tracking is enabled.
     */
    suspend fun recordSession(
        mangaId: String,
        chapterId: String,
        chapterTitle: String,
        startedAt: Long,
        endedAt: Long,
        startPage: Int,
        endPage: Int,
        pagesProgressed: Int,
        completedChapter: Boolean
    ): Long = withContext(Dispatchers.IO) {
        if (!preferencesManager.trackStatistics.value) {
            return@withContext -1L
        }

        val duration = (endedAt - startedAt).coerceAtLeast(0L)
        // Discard trivial empty sessions (< 2 seconds with 0 pages and not completed)
        if (duration < 2000L && pagesProgressed <= 0 && !completedChapter) {
            return@withContext -1L
        }

        val session = ReadingSessionEntity(
            mangaId = mangaId,
            chapterId = chapterId,
            chapterTitle = chapterTitle,
            startedAt = startedAt,
            endedAt = endedAt,
            durationMillis = duration,
            startPage = startPage,
            endPage = endPage,
            pagesProgressed = pagesProgressed.coerceAtLeast(0),
            completedChapter = completedChapter
        )

        statisticsDao.insertSession(session)
    }

    /**
     * Clear all recorded reading session statistics. Does not delete library or saved covers.
     */
    suspend fun clearStatistics() = withContext(Dispatchers.IO) {
        statisticsDao.clearAllSessions()
    }

    /**
     * Calculate summary statistics for the selected time period.
     */
    fun computeSummary(
        sessions: List<ReadingSessionEntity>,
        period: TimePeriod,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): SummaryStatistics {
        val (currentRange, prevRange) = getTimePeriodRanges(period, zoneId)
        val periodSessions = sessions.filter { it.startedAt in currentRange.first..currentRange.second }
        val prevSessions = if (prevRange != null) {
            sessions.filter { it.startedAt in prevRange.first..prevRange.second }
        } else emptyList()

        val totalTime = periodSessions.sumOf { it.durationMillis }
        val prevTotalTime = prevSessions.sumOf { it.durationMillis }

        val chaptersCompleted = periodSessions.count { it.completedChapter }
        val prevChaptersCompleted = prevSessions.count { it.completedChapter }

        val pagesProgressed = periodSessions.sumOf { it.pagesProgressed }
        val prevPagesProgressed = prevSessions.sumOf { it.pagesProgressed }

        // Qualifying active days (at least 20s or 1 page or completed chapter)
        val activeDaysSet = periodSessions
            .filter { isQualifyingSession(it) }
            .map { toLocalDate(it.startedAt, zoneId) }
            .toSet()

        val activeDaysCount = activeDaysSet.size
        val prevActiveDaysCount = prevSessions
            .filter { isQualifyingSession(it) }
            .map { toLocalDate(it.startedAt, zoneId) }
            .toSet().size

        val distinctSeriesCount = periodSessions.map { it.mangaId }.distinct().size

        val avgTimePerActiveDay = if (activeDaysCount > 0) totalTime / activeDaysCount else 0L
        val avgTimePerSession = if (periodSessions.isNotEmpty()) totalTime / periodSessions.size else 0L
        val avgPagesPerActiveDay = if (activeDaysCount > 0) pagesProgressed.toFloat() / activeDaysCount else 0f

        val totalPossibleDays = calculateTotalDaysInPeriod(period, zoneId)
        val activeDaysPercentage = if (totalPossibleDays > 0) {
            ((activeDaysCount.toFloat() / totalPossibleDays) * 100f).coerceIn(0f, 100f)
        } else 0f

        // Differences
        val chaptersDiff = if (prevRange != null) chaptersCompleted - prevChaptersCompleted else null
        val chaptersPercentDiff = calculatePercentChange(prevChaptersCompleted.toFloat(), chaptersCompleted.toFloat())

        val pagesDiff = if (prevRange != null) pagesProgressed - prevPagesProgressed else null
        val pagesPercentDiff = calculatePercentChange(prevPagesProgressed.toFloat(), pagesProgressed.toFloat())

        val (currentStreak, longestStreak) = computeStreaks(sessions, zoneId)

        return SummaryStatistics(
            totalReadingTimeMillis = totalTime,
            avgReadingTimePerActiveDayMillis = avgTimePerActiveDay,
            avgReadingTimePerSessionMillis = avgTimePerSession,
            chaptersCompleted = chaptersCompleted,
            chaptersCompletedDiff = chaptersDiff,
            chaptersCompletedPercentDiff = chaptersPercentDiff,
            pagesProgressed = pagesProgressed,
            avgPagesPerActiveDay = avgPagesPerActiveDay,
            pagesProgressedDiff = pagesDiff,
            pagesProgressedPercentDiff = pagesPercentDiff,
            activeDaysCount = activeDaysCount,
            activeDaysPercentage = activeDaysPercentage,
            distinctSeriesCount = distinctSeriesCount,
            currentStreakDays = currentStreak,
            longestStreakDays = longestStreak
        )
    }

    /**
     * Compute activity chart points over time according to the period and metric.
     */
    fun computeChartPoints(
        sessions: List<ReadingSessionEntity>,
        period: TimePeriod,
        metric: ActivityChartMetric,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<ActivityDataPoint> {
        val (currentRange, _) = getTimePeriodRanges(period, zoneId)
        val periodSessions = sessions.filter { it.startedAt in currentRange.first..currentRange.second }

        val today = LocalDate.now(zoneId)

        return when (period) {
            TimePeriod.TODAY -> {
                // 24 hourly buckets
                val hourlyGroups = periodSessions.groupBy {
                    Instant.ofEpochMilli(it.startedAt).atZone(zoneId).hour
                }
                (0..23).map { hour ->
                    val bucketSessions = hourlyGroups[hour] ?: emptyList()
                    val value = extractMetricValue(bucketSessions, metric)
                    val label = "%02d:00".format(hour)
                    ActivityDataPoint(
                        label = label,
                        timestamp = today.atTime(hour, 0).atZone(zoneId).toInstant().toEpochMilli(),
                        value = value,
                        formattedValue = formatMetricValue(value, metric),
                        secondaryInfo = "${bucketSessions.size} sessions"
                    )
                }
            }
            TimePeriod.THIS_WEEK -> {
                // 7 days of this week (Monday through Sunday)
                val startOfWeek = today.with(DayOfWeek.MONDAY)
                (0..6).map { dayOffset ->
                    val date = startOfWeek.plusDays(dayOffset.toLong())
                    val dateSessions = periodSessions.filter { toLocalDate(it.startedAt, zoneId) == date }
                    val value = extractMetricValue(dateSessions, metric)
                    val dayLabel = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                    ActivityDataPoint(
                        label = dayLabel,
                        timestamp = date.atStartOfDay(zoneId).toInstant().toEpochMilli(),
                        value = value,
                        formattedValue = formatMetricValue(value, metric),
                        secondaryInfo = date.format(DateTimeFormatter.ofPattern("MMM d"))
                    )
                }
            }
            TimePeriod.THIS_MONTH -> {
                // All days of current month
                val yearMonth = YearMonth.from(today)
                val daysInMonth = yearMonth.lengthOfMonth()
                (1..daysInMonth).map { dayOfMonth ->
                    val date = yearMonth.atDay(dayOfMonth)
                    val dateSessions = periodSessions.filter { toLocalDate(it.startedAt, zoneId) == date }
                    val value = extractMetricValue(dateSessions, metric)
                    ActivityDataPoint(
                        label = "$dayOfMonth",
                        timestamp = date.atStartOfDay(zoneId).toInstant().toEpochMilli(),
                        value = value,
                        formattedValue = formatMetricValue(value, metric),
                        secondaryInfo = date.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
                    )
                }
            }
            TimePeriod.THIS_YEAR -> {
                // 12 months of current year
                val currentYear = today.year
                (1..12).map { month ->
                    val ym = YearMonth.of(currentYear, month)
                    val monthSessions = periodSessions.filter {
                        val sessionDate = toLocalDate(it.startedAt, zoneId)
                        sessionDate.year == currentYear && sessionDate.monthValue == month
                    }
                    val value = extractMetricValue(monthSessions, metric)
                    val label = ym.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                    ActivityDataPoint(
                        label = label,
                        timestamp = ym.atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli(),
                        value = value,
                        formattedValue = formatMetricValue(value, metric),
                        secondaryInfo = "${ym.month.name} $currentYear"
                    )
                }
            }
            TimePeriod.ALL_TIME -> {
                // Monthly buckets for the past 12 months or span of history
                val startMonth = if (sessions.isNotEmpty()) {
                    val earliest = sessions.minOf { it.startedAt }
                    YearMonth.from(toLocalDate(earliest, zoneId))
                } else YearMonth.from(today).minusMonths(11)

                val endMonth = YearMonth.from(today)
                val monthsBetween = ChronoUnit.MONTHS.between(startMonth, endMonth).coerceIn(0, 23).toInt()

                (0..monthsBetween).map { offset ->
                    val ym = startMonth.plusMonths(offset.toLong())
                    val monthSessions = periodSessions.filter {
                        val sessionDate = toLocalDate(it.startedAt, zoneId)
                        sessionDate.year == ym.year && sessionDate.monthValue == ym.monthValue
                    }
                    val value = extractMetricValue(monthSessions, metric)
                    val label = ym.format(DateTimeFormatter.ofPattern("MMM yy"))
                    ActivityDataPoint(
                        label = label,
                        timestamp = ym.atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli(),
                        value = value,
                        formattedValue = formatMetricValue(value, metric),
                        secondaryInfo = ym.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
                    )
                }
            }
        }
    }

    /**
     * Compute GitHub-style activity calendar days for a specific month offset (0 = current month, -1 = last month).
     */
    fun computeCalendarDays(
        sessions: List<ReadingSessionEntity>,
        monthOffset: Int = 0,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Pair<String, List<DayActivity>> {
        val targetMonth = YearMonth.now(zoneId).plusMonths(monthOffset.toLong())
        val daysInMonth = targetMonth.lengthOfMonth()

        // Daily sessions grouped by LocalDate
        val sessionsByDate = sessions.groupBy { toLocalDate(it.startedAt, zoneId) }

        // Determine max reading time to compute relative intensity
        val maxDurationDay = sessionsByDate.values.maxOfOrNull { list -> list.sumOf { it.durationMillis } } ?: 1L
        val maxPagesDay = sessionsByDate.values.maxOfOrNull { list -> list.sumOf { it.pagesProgressed } } ?: 1

        val calendarDays = (1..daysInMonth).map { day ->
            val date = targetMonth.atDay(day)
            val dateSessions = sessionsByDate[date] ?: emptyList()
            val readingTime = dateSessions.sumOf { it.durationMillis }
            val chapters = dateSessions.count { it.completedChapter }
            val pages = dateSessions.sumOf { it.pagesProgressed }

            val intensity = when {
                readingTime <= 0L && pages <= 0 && chapters <= 0 -> 0
                readingTime >= 3600000L || pages >= 60 || chapters >= 3 -> 4
                readingTime >= 1800000L || pages >= 30 || chapters >= 2 -> 3
                readingTime >= 600000L || pages >= 12 || chapters >= 1 -> 2
                else -> 1
            }

            DayActivity(
                dateString = date.toString(),
                epochDay = date.toEpochDay(),
                dayOfMonth = day,
                monthOfYear = targetMonth.monthValue,
                year = targetMonth.year,
                readingTimeMillis = readingTime,
                chaptersCompleted = chapters,
                pagesProgressed = pages,
                sessionCount = dateSessions.size,
                intensity = intensity
            )
        }

        val monthLabel = targetMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
        return Pair(monthLabel, calendarDays)
    }

    /**
     * Calculate comparison between current period and previous equivalent period.
     */
    fun computeComparison(
        sessions: List<ReadingSessionEntity>,
        period: TimePeriod,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): PeriodComparison? {
        val (currentRange, prevRange) = getTimePeriodRanges(period, zoneId)
        if (prevRange == null) return null

        val currentSessions = sessions.filter { it.startedAt in currentRange.first..currentRange.second }
        val prevSessions = sessions.filter { it.startedAt in prevRange.first..prevRange.second }

        val currentTime = currentSessions.sumOf { it.durationMillis }
        val prevTime = prevSessions.sumOf { it.durationMillis }

        val currentChapters = currentSessions.count { it.completedChapter }
        val prevChapters = prevSessions.count { it.completedChapter }

        val currentPages = currentSessions.sumOf { it.pagesProgressed }
        val prevPages = prevSessions.sumOf { it.pagesProgressed }

        val currentActiveDays = currentSessions.filter { isQualifyingSession(it) }.map { toLocalDate(it.startedAt, zoneId) }.toSet().size
        val prevActiveDays = prevSessions.filter { isQualifyingSession(it) }.map { toLocalDate(it.startedAt, zoneId) }.toSet().size

        val prevLabel = when (period) {
            TimePeriod.TODAY -> "Yesterday"
            TimePeriod.THIS_WEEK -> "Last Week"
            TimePeriod.THIS_MONTH -> "Last Month"
            TimePeriod.THIS_YEAR -> "Last Year"
            TimePeriod.ALL_TIME -> "Prior Period"
        }

        return PeriodComparison(
            previousPeriodLabel = prevLabel,
            timeDiffMillis = currentTime - prevTime,
            timePercentDiff = calculatePercentChange(prevTime.toFloat(), currentTime.toFloat()),
            chaptersDiff = currentChapters - prevChapters,
            chaptersPercentDiff = calculatePercentChange(prevChapters.toFloat(), currentChapters.toFloat()),
            pagesDiff = currentPages - prevPages,
            pagesPercentDiff = calculatePercentChange(prevPages.toFloat(), currentPages.toFloat()),
            activeDaysDiff = currentActiveDays - prevActiveDays
        )
    }

    /**
     * Calculate habits, streaks, and personal records across all history.
     */
    fun computeHabits(
        sessions: List<ReadingSessionEntity>,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): ReadingHabits {
        if (sessions.isEmpty()) {
            return ReadingHabits()
        }

        val (currentStreak, longestStreak) = computeStreaks(sessions, zoneId)

        // Qualifying sessions
        val qualifyingSessions = sessions.filter { isQualifyingSession(it) }

        // Most active weekday
        val weekdayCounts = qualifyingSessions.groupBy {
            toLocalDate(it.startedAt, zoneId).dayOfWeek
        }.mapValues { it.value.sumOf { s -> s.durationMillis } }

        val mostActiveWeekday = weekdayCounts.maxByOrNull { it.value }?.key?.getDisplayName(
            TextStyle.FULL, Locale.getDefault()
        ) ?: "N/A"

        // Active days
        val sessionsByDay = qualifyingSessions.groupBy { toLocalDate(it.startedAt, zoneId) }
        val distinctDaysCount = sessionsByDay.size.coerceAtLeast(1)

        val totalDuration = qualifyingSessions.sumOf { it.durationMillis }
        val totalChapters = qualifyingSessions.count { it.completedChapter }
        val totalPages = qualifyingSessions.sumOf { it.pagesProgressed }

        val avgTimePerDay = totalDuration / distinctDaysCount
        val avgChaptersPerDay = totalChapters.toFloat() / distinctDaysCount

        // Longest session
        val longestSession = sessions.maxByOrNull { it.durationMillis }
        val longestDuration = longestSession?.durationMillis ?: 0L

        // Record pages in a day
        val recordPagesEntry = sessionsByDay.maxByOrNull { it.value.sumOf { s -> s.pagesProgressed } }
        val recordPages = recordPagesEntry?.value?.sumOf { it.pagesProgressed } ?: 0
        val recordPagesDate = recordPagesEntry?.key?.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))

        // Record chapters in a day
        val recordChaptersEntry = sessionsByDay.maxByOrNull { it.value.count { s -> s.completedChapter } }
        val recordChapters = recordChaptersEntry?.value?.count { it.completedChapter } ?: 0
        val recordChaptersDate = recordChaptersEntry?.key?.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))

        // Estimated reading speed (pages per minute from sessions with > 0 pages and > 30s)
        val validSpeedSessions = sessions.filter { it.durationMillis >= 30000L && it.pagesProgressed > 0 }
        val totalSpeedPages = validSpeedSessions.sumOf { it.pagesProgressed }
        val totalSpeedMinutes = validSpeedSessions.sumOf { it.durationMillis } / 60000f
        val readingSpeed = if (totalSpeedMinutes > 0f) totalSpeedPages / totalSpeedMinutes else 0f

        return ReadingHabits(
            currentStreak = currentStreak,
            longestStreak = longestStreak,
            mostActiveWeekday = mostActiveWeekday,
            avgReadingTimePerDayMillis = avgTimePerDay,
            avgChaptersPerDay = avgChaptersPerDay,
            longestSessionDurationMillis = longestDuration,
            longestSessionMangaTitle = longestSession?.chapterTitle,
            recordPagesInDay = recordPages,
            recordPagesDate = recordPagesDate,
            recordChaptersInDay = recordChapters,
            recordChaptersDate = recordChaptersDate,
            readingSpeedPagesPerMinute = (readingSpeed * 10f).roundToInt() / 10f
        )
    }

    /**
     * Compute per-series statistics.
     */
    fun computeSeriesStatistics(
        sessions: List<ReadingSessionEntity>,
        mangaList: List<Manga>,
        allChapterProgress: List<ChapterProgressEntity>
    ): List<SeriesStatistics> {
        val mangaMap = mangaList.associateBy { it.id }
        val sessionsByManga = sessions.groupBy { it.mangaId }
        val progressByManga = allChapterProgress.groupBy { it.mangaId }

        return mangaList.map { manga ->
            val mangaSessions = sessionsByManga[manga.id] ?: emptyList()
            val mangaProgress = progressByManga[manga.id] ?: emptyList()

            val readingTime = mangaSessions.sumOf { it.durationMillis }
            val pagesProgressed = mangaSessions.sumOf { it.pagesProgressed }
            val chaptersCompleted = mangaProgress.count { it.isRead }

            SeriesStatistics(
                mangaId = manga.id,
                title = manga.title,
                coverPath = manga.coverPath,
                totalChapters = manga.chapterCount,
                chaptersCompleted = chaptersCompleted,
                pagesProgressed = pagesProgressed,
                readingTimeMillis = readingTime,
                lastReadTime = manga.lastReadTime,
                lastReadChapterTitle = manga.lastReadChapterTitle
            )
        }.sortedByDescending { it.readingTimeMillis }
    }

    /**
     * Compute library status counts.
     */
    fun computeLibraryCounts(
        mangaList: List<Manga>,
        allChapterProgress: List<ChapterProgressEntity>
    ): LibraryStatusCounts {
        val progressByManga = allChapterProgress.groupBy { it.mangaId }

        var currentlyReading = 0
        var completed = 0
        var unread = 0
        var favorites = 0

        for (manga in mangaList) {
            if (manga.isFavorite) favorites++

            val progressList = progressByManga[manga.id] ?: emptyList()
            val readCount = progressList.count { it.isRead }

            when {
                readCount > 0 && manga.chapterCount > 0 && readCount >= manga.chapterCount -> completed++
                manga.lastReadTime > 0L -> currentlyReading++
                else -> unread++
            }
        }

        return LibraryStatusCounts(
            currentlyReading = currentlyReading,
            completed = completed,
            unread = unread,
            favorites = favorites
        )
    }

    // ==========================================
    // Internal Helper Functions
    // ==========================================

    private fun isQualifyingSession(session: ReadingSessionEntity): Boolean {
        // Qualifying day requires at least 20 seconds of reading OR >= 1 page forward progress OR a completed chapter
        return session.durationMillis >= 20000L || session.pagesProgressed >= 1 || session.completedChapter
    }

    private fun toLocalDate(timestamp: Long, zoneId: ZoneId): LocalDate {
        return Instant.ofEpochMilli(timestamp).atZone(zoneId).toLocalDate()
    }

    private fun computeStreaks(
        sessions: List<ReadingSessionEntity>,
        zoneId: ZoneId
    ): Pair<Int, Int> {
        val qualifyingDates = sessions
            .filter { isQualifyingSession(it) }
            .map { toLocalDate(it.startedAt, zoneId) }
            .distinct()
            .sorted()

        if (qualifyingDates.isEmpty()) {
            return Pair(0, 0)
        }

        val today = LocalDate.now(zoneId)
        val yesterday = today.minusDays(1)

        // 1. Longest streak
        var longestStreak = 0
        var tempStreak = 0
        var prevDate: LocalDate? = null

        for (date in qualifyingDates) {
            if (prevDate == null || date == prevDate.plusDays(1)) {
                tempStreak++
            } else if (date != prevDate) {
                tempStreak = 1
            }
            if (tempStreak > longestStreak) {
                longestStreak = tempStreak
            }
            prevDate = date
        }

        // 2. Current streak
        // A streak is active if the user read today OR yesterday
        val lastDate = qualifyingDates.last()
        var currentStreak = 0

        if (lastDate == today || lastDate == yesterday) {
            var checkDate = lastDate
            val dateSet = qualifyingDates.toSet()

            while (dateSet.contains(checkDate)) {
                currentStreak++
                checkDate = checkDate.minusDays(1)
            }
        }

        return Pair(currentStreak, longestStreak)
    }

    private fun getTimePeriodRanges(
        period: TimePeriod,
        zoneId: ZoneId
    ): Pair<Pair<Long, Long>, Pair<Long, Long>?> {
        val today = LocalDate.now(zoneId)

        return when (period) {
            TimePeriod.TODAY -> {
                val start = today.atStartOfDay(zoneId).toInstant().toEpochMilli()
                val end = today.atTime(23, 59, 59, 999_000_000).atZone(zoneId).toInstant().toEpochMilli()

                val yDate = today.minusDays(1)
                val prevStart = yDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
                val prevEnd = yDate.atTime(23, 59, 59, 999_000_000).atZone(zoneId).toInstant().toEpochMilli()

                Pair(Pair(start, end), Pair(prevStart, prevEnd))
            }
            TimePeriod.THIS_WEEK -> {
                val startOfWeek = today.with(DayOfWeek.MONDAY)
                val endOfWeek = today.with(DayOfWeek.SUNDAY)
                val start = startOfWeek.atStartOfDay(zoneId).toInstant().toEpochMilli()
                val end = endOfWeek.atTime(23, 59, 59, 999_000_000).atZone(zoneId).toInstant().toEpochMilli()

                val prevStartOfWeek = startOfWeek.minusWeeks(1)
                val prevEndOfWeek = endOfWeek.minusWeeks(1)
                val prevStart = prevStartOfWeek.atStartOfDay(zoneId).toInstant().toEpochMilli()
                val prevEnd = prevEndOfWeek.atTime(23, 59, 59, 999_000_000).atZone(zoneId).toInstant().toEpochMilli()

                Pair(Pair(start, end), Pair(prevStart, prevEnd))
            }
            TimePeriod.THIS_MONTH -> {
                val ym = YearMonth.from(today)
                val start = ym.atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
                val end = ym.atEndOfMonth().atTime(23, 59, 59, 999_000_000).atZone(zoneId).toInstant().toEpochMilli()

                val prevYm = ym.minusMonths(1)
                val prevStart = prevYm.atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
                val prevEnd = prevYm.atEndOfMonth().atTime(23, 59, 59, 999_000_000).atZone(zoneId).toInstant().toEpochMilli()

                Pair(Pair(start, end), Pair(prevStart, prevEnd))
            }
            TimePeriod.THIS_YEAR -> {
                val start = LocalDate.of(today.year, 1, 1).atStartOfDay(zoneId).toInstant().toEpochMilli()
                val end = LocalDate.of(today.year, 12, 31).atTime(23, 59, 59, 999_000_000).atZone(zoneId).toInstant().toEpochMilli()

                val prevStart = LocalDate.of(today.year - 1, 1, 1).atStartOfDay(zoneId).toInstant().toEpochMilli()
                val prevEnd = LocalDate.of(today.year - 1, 12, 31).atTime(23, 59, 59, 999_000_000).atZone(zoneId).toInstant().toEpochMilli()

                Pair(Pair(start, end), Pair(prevStart, prevEnd))
            }
            TimePeriod.ALL_TIME -> {
                Pair(Pair(0L, Long.MAX_VALUE), null)
            }
        }
    }

    private fun calculateTotalDaysInPeriod(period: TimePeriod, zoneId: ZoneId): Int {
        val today = LocalDate.now(zoneId)
        return when (period) {
            TimePeriod.TODAY -> 1
            TimePeriod.THIS_WEEK -> today.dayOfWeek.value // 1 (Mon) to 7 (Sun) elapsed so far
            TimePeriod.THIS_MONTH -> today.dayOfMonth
            TimePeriod.THIS_YEAR -> today.dayOfYear
            TimePeriod.ALL_TIME -> 365
        }
    }

    private fun extractMetricValue(sessions: List<ReadingSessionEntity>, metric: ActivityChartMetric): Float {
        return when (metric) {
            ActivityChartMetric.READING_TIME -> (sessions.sumOf { it.durationMillis } / 60000f).roundToOneDecimal()
            ActivityChartMetric.CHAPTERS -> sessions.count { it.completedChapter }.toFloat()
            ActivityChartMetric.PAGES -> sessions.sumOf { it.pagesProgressed }.toFloat()
        }
    }

    private fun formatMetricValue(value: Float, metric: ActivityChartMetric): String {
        return when (metric) {
            ActivityChartMetric.READING_TIME -> {
                if (value >= 60f) {
                    val hrs = (value / 60f).toInt()
                    val mins = (value % 60f).roundToInt()
                    if (mins > 0) "${hrs}h ${mins}m" else "${hrs}h"
                } else {
                    "${value.roundToInt()}m"
                }
            }
            ActivityChartMetric.CHAPTERS -> "${value.toInt()} ch"
            ActivityChartMetric.PAGES -> "${value.toInt()} p"
        }
    }

    private fun calculatePercentChange(prev: Float, current: Float): Float? {
        if (prev <= 0f) return null
        return (((current - prev) / prev) * 100f).roundToOneDecimal()
    }

    private fun Float.roundToOneDecimal(): Float {
        return (this * 10f).roundToInt() / 10f
    }
}
