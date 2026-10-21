package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import com.example.ui.components.stableStatusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.data.ActivityChartMetric
import com.example.data.ActivityDataPoint
import com.example.data.DayActivity
import com.example.data.Manga
import com.example.data.PeriodComparison
import com.example.data.ReadingHabits
import com.example.data.SeriesStatistics
import com.example.data.SummaryStatistics
import com.example.data.TimePeriod
import com.example.ui.MangaViewModel
import com.example.ui.NavTab
import java.io.File
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun StatisticsScreen(
    viewModel: MangaViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.statisticsUiState.collectAsStateWithLifecycle()
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.stableStatusBars)
                        .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Your",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                fontSize = 20.sp,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = (-0.5).sp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Reading",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                fontSize = 20.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                letterSpacing = (-0.5).sp
                            )
                        }
                        Text(
                            text = "Analytics, habits & milestones",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }

                    if (uiState.hasSufficientHistory) {
                        IconButton(
                            onClick = { showClearConfirmDialog = true },
                            modifier = Modifier.testTag("clear_statistics_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = "Clear Statistics",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Time Period Selector
                item {
                    TimePeriodSelector(
                        selectedPeriod = uiState.selectedPeriod,
                        onSelectPeriod = { viewModel.setTimePeriod(it) }
                    )
                }

                // 2. Overview Summary Cards
                item {
                    SummaryCardsSection(
                        summary = uiState.summary,
                        period = uiState.selectedPeriod
                    )
                }

                // 3. Reading Activity Charts (Activity Over Time + Calendar + Comparison)
                item {
                    ReadingActivitySection(
                        chartPoints = uiState.chartDataPoints,
                        selectedMetric = uiState.selectedMetric,
                        onSelectMetric = { viewModel.setChartMetric(it) },
                        calendarDays = uiState.calendarDays,
                        calendarMonthLabel = uiState.calendarMonthLabel,
                        calendarMonthOffset = uiState.calendarMonthOffset,
                        onMonthOffsetChange = { viewModel.setCalendarMonthOffset(it) },
                        selectedCalendarDay = uiState.selectedCalendarDay,
                        onSelectCalendarDay = { viewModel.selectCalendarDay(it) },
                        comparison = uiState.comparison,
                        hasHistory = uiState.hasSufficientHistory
                    )
                }

                // 4. Library Insights Section
                item {
                    LibraryInsightsSection(
                        libraryCounts = uiState.libraryCounts,
                        mostReadSeries = uiState.mostReadSeries,
                        recentSeries = uiState.recentSeries,
                        onSelectManga = { viewModel.selectManga(it) },
                        onResumeManga = { viewModel.resumeReading(it) }
                    )
                }

                // 5. Reading Habits & Personal Records Section
                item {
                    ReadingHabitsSection(habits = uiState.habits)
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Clear Reading Statistics?") },
            text = {
                Text("This will reset all recorded reading sessions, streaks, and activity history. Your manga library, covers, and chapter bookmarks will NOT be affected.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearStatistics()
                        showClearConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// ==========================================
// 1. Time Period Selector
// ==========================================
@Composable
private fun TimePeriodSelector(
    selectedPeriod: TimePeriod,
    onSelectPeriod: (TimePeriod) -> Unit
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TimePeriod.values().forEach { period ->
            val isSelected = period == selectedPeriod
            FilterChip(
                selected = isSelected,
                onClick = { onSelectPeriod(period) },
                label = {
                    Text(
                        text = period.displayName,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                shape = RoundedCornerShape(20.dp),
                border = null,
                modifier = Modifier.testTag("period_chip_${period.name.lowercase()}")
            )
        }
    }
}

// ==========================================
// 2. Summary Cards Section
// ==========================================
@Composable
private fun SummaryCardsSection(
    summary: SummaryStatistics,
    period: TimePeriod
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // First Row: Reading Time & Reading Streak
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SummaryCard(
                title = "Reading Time",
                value = formatDuration(summary.totalReadingTimeMillis),
                subInfo = if (summary.activeDaysCount > 0) "${formatDuration(summary.avgReadingTimePerActiveDayMillis)} / active day" else "No active time",
                secondaryNote = if (summary.avgReadingTimePerSessionMillis > 0) "Avg session: ${formatDuration(summary.avgReadingTimePerSessionMillis)}" else null,
                icon = Icons.Default.Timer,
                iconTint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )

            SummaryCard(
                title = "Reading Streak",
                value = "${summary.currentStreakDays} ${if (summary.currentStreakDays == 1) "day" else "days"}",
                subInfo = "Longest: ${summary.longestStreakDays} days",
                secondaryNote = if (summary.currentStreakDays > 0) "Active streak" else "Read today to start",
                icon = Icons.Default.LocalFireDepartment,
                iconTint = Color(0xFFFF6D00),
                modifier = Modifier.weight(1f)
            )
        }

        // Second Row: Chapters Read & Pages Read
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SummaryCard(
                title = "Chapters Read",
                value = "${summary.chaptersCompleted}",
                subInfo = formatDiff(summary.chaptersCompletedDiff, summary.chaptersCompletedPercentDiff, "ch"),
                secondaryNote = "Completed in ${period.displayName.lowercase()}",
                icon = Icons.Default.MenuBook,
                iconTint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f)
            )

            SummaryCard(
                title = "Pages Read",
                value = "${summary.pagesProgressed}",
                subInfo = if (summary.activeDaysCount > 0) String.format(Locale.US, "%.1f pages / day", summary.avgPagesPerActiveDay) else "0 pages / day",
                secondaryNote = formatDiff(summary.pagesProgressedDiff, summary.pagesProgressedPercentDiff, "pages"),
                icon = Icons.Default.AutoStories,
                iconTint = Color(0xFF00B0FF),
                modifier = Modifier.weight(1f)
            )
        }

        // Third Row: Active Days & Series Explored
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SummaryCard(
                title = "Active Days",
                value = "${summary.activeDaysCount} ${if (summary.activeDaysCount == 1) "day" else "days"}",
                subInfo = if (summary.activeDaysPercentage > 0f) String.format(Locale.US, "%.0f%% of period", summary.activeDaysPercentage) else "0% active",
                secondaryNote = "Qualifying reading days",
                icon = Icons.Default.CalendarMonth,
                iconTint = Color(0xFF00E676),
                modifier = Modifier.weight(1f)
            )

            SummaryCard(
                title = "Series Read",
                value = "${summary.distinctSeriesCount}",
                subInfo = if (summary.distinctSeriesCount == 1) "1 distinct title" else "${summary.distinctSeriesCount} distinct titles",
                secondaryNote = "Progressed in ${period.displayName.lowercase()}",
                icon = Icons.Default.Bookmark,
                iconTint = Color(0xFFAA00FF),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SummaryCard(
    title: String,
    value: String,
    subInfo: String,
    secondaryNote: String?,
    icon: ImageVector,
    iconTint: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp
                )
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .background(iconTint.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 20.sp
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = subInfo,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (secondaryNote != null) {
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = secondaryNote,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ==========================================
// 3. Reading Activity Section (Charts + Calendar)
// ==========================================
@Composable
private fun ReadingActivitySection(
    chartPoints: List<ActivityDataPoint>,
    selectedMetric: ActivityChartMetric,
    onSelectMetric: (ActivityChartMetric) -> Unit,
    calendarDays: List<DayActivity>,
    calendarMonthLabel: String,
    calendarMonthOffset: Int,
    onMonthOffsetChange: (Int) -> Unit,
    selectedCalendarDay: DayActivity?,
    onSelectCalendarDay: (DayActivity) -> Unit,
    comparison: PeriodComparison?,
    hasHistory: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.BarChart,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Reading Activity",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Metric Toggle Chips on their own row: sharing the title's row squeezed "Pages" into a
            // one-letter-wide column on narrow screens
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ActivityChartMetric.values().forEach { metric ->
                    val isSelected = metric == selectedMetric
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                            .clickable { onSelectMetric(metric) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = metric.displayName,
                            fontSize = 12.sp,
                            maxLines = 1,
                            softWrap = false,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // A. Activity Over Time Bar Chart
            ActivityBarChart(
                points = chartPoints,
                metric = selectedMetric
            )

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(16.dp))

            // B. Reading Calendar (GitHub contribution style)
            ReadingActivityCalendar(
                days = calendarDays,
                monthLabel = calendarMonthLabel,
                monthOffset = calendarMonthOffset,
                onMonthOffsetChange = onMonthOffsetChange,
                selectedDay = selectedCalendarDay,
                onSelectDay = onSelectCalendarDay
            )

            // C. Period Comparison
            if (comparison != null && hasHistory) {
                Spacer(modifier = Modifier.height(18.dp))
                PeriodComparisonCard(comparison = comparison)
            }
        }
    }
}

@Composable
private fun ActivityBarChart(
    points: List<ActivityDataPoint>,
    metric: ActivityChartMetric
) {
    if (points.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No activity records for this period",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    var selectedPointIndex by remember(points) { mutableStateOf<Int?>(null) }
    val maxValue = (points.maxOfOrNull { it.value } ?: 1f).coerceAtLeast(1f)

    Column(modifier = Modifier.fillMaxWidth()) {
        // Selected data point detail tooltip badge
        val displayIndex = selectedPointIndex ?: points.indices.lastOrNull { points[it].value > 0 } ?: (points.size - 1)
        val highlighted = points.getOrNull(displayIndex)

        if (highlighted != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${highlighted.secondaryInfo.ifBlank { highlighted.label }}:",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = highlighted.formattedValue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Horizontal Bars Row
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                points.forEachIndexed { index, point ->
                    val fraction = (point.value / maxValue).coerceIn(0.04f, 1f)
                    val isSelected = index == selectedPointIndex
                    val hasValue = point.value > 0f

                    val barColor = when {
                        isSelected -> MaterialTheme.colorScheme.primary
                        hasValue -> MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clickable { selectedPointIndex = index },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(if (points.size > 14) 0.85f else 0.65f)
                                .fillMaxSize(fraction)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(barColor)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Axis Labels (Show spaced labels to avoid overlapping)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val step = if (points.size > 20) 4 else if (points.size > 10) 2 else 1
            points.forEachIndexed { index, point ->
                if (index % step == 0 || index == points.size - 1) {
                    Text(
                        text = point.label,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

// ==========================================
// Reading Calendar (GitHub Contribution Style)
// ==========================================
@Composable
private fun ReadingActivityCalendar(
    days: List<DayActivity>,
    monthLabel: String,
    monthOffset: Int,
    onMonthOffsetChange: (Int) -> Unit,
    selectedDay: DayActivity?,
    onSelectDay: (DayActivity) -> Unit
) {
    val todayString = LocalDate.now().toString()

    Column(modifier = Modifier.fillMaxWidth()) {
        // Month Navigation Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = monthLabel,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { onMonthOffsetChange(monthOffset - 1) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.NavigateBefore,
                        contentDescription = "Previous Month",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (monthOffset != 0) {
                    TextButton(
                        onClick = { onMonthOffsetChange(0) },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("Current", fontSize = 11.sp)
                    }
                }

                IconButton(
                    onClick = { onMonthOffsetChange(monthOffset + 1) },
                    enabled = monthOffset < 0,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.NavigateNext,
                        contentDescription = "Next Month",
                        tint = if (monthOffset < 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Days Grid (7 columns for days of week)
        val chunkedDays = days.chunked(7)
        chunkedDays.forEach { week ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                week.forEach { day ->
                    val isToday = day.dateString == todayString
                    val isSelected = selectedDay?.dateString == day.dateString

                    val intensityColor = when (day.intensity) {
                        4 -> MaterialTheme.colorScheme.primary
                        3 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
                        2 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.50f)
                        1 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f)
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(intensityColor)
                            .then(
                                if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(6.dp))
                                else if (isToday) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                                else Modifier
                            )
                            .clickable { onSelectDay(day) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${day.dayOfMonth}",
                            fontSize = 10.sp,
                            fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (day.intensity >= 3) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                // Pad empty slots if week is less than 7 days
                val remaining = 7 - week.size
                repeat(remaining) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Legend row (Less -> More)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Less",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.width(4.dp))
            listOf(0, 1, 2, 3, 4).forEach { level ->
                val col = when (level) {
                    4 -> MaterialTheme.colorScheme.primary
                    3 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
                    2 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.50f)
                    1 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f)
                }
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(col)
                )
                Spacer(modifier = Modifier.width(2.dp))
            }
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = "More",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }

        // Selected Day Details Card
        if (selectedDay != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = selectedDay.dateString + if (selectedDay.dateString == todayString) " (Today)" else "",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${formatDuration(selectedDay.readingTimeMillis)} reading · ${selectedDay.chaptersCompleted} ch · ${selectedDay.pagesProgressed} pages",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Text(
                        text = "${selectedDay.sessionCount} ${if (selectedDay.sessionCount == 1) "session" else "sessions"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ==========================================
// Period Comparison Card
// ==========================================
@Composable
private fun PeriodComparisonCard(comparison: PeriodComparison) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Comparison vs ${comparison.previousPeriodLabel}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ComparisonMetricPill(
                    label = "Time",
                    diff = formatDurationDiff(comparison.timeDiffMillis),
                    isPositive = comparison.timeDiffMillis >= 0L
                )
                ComparisonMetricPill(
                    label = "Chapters",
                    diff = "${if (comparison.chaptersDiff >= 0) "+" else ""}${comparison.chaptersDiff} ch",
                    isPositive = comparison.chaptersDiff >= 0
                )
                ComparisonMetricPill(
                    label = "Pages",
                    diff = "${if (comparison.pagesDiff >= 0) "+" else ""}${comparison.pagesDiff}",
                    isPositive = comparison.pagesDiff >= 0
                )
                ComparisonMetricPill(
                    label = "Active Days",
                    diff = "${if (comparison.activeDaysDiff >= 0) "+" else ""}${comparison.activeDaysDiff} d",
                    isPositive = comparison.activeDaysDiff >= 0
                )
            }
        }
    }
}

@Composable
private fun ComparisonMetricPill(
    label: String,
    diff: String,
    isPositive: Boolean
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (isPositive) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                contentDescription = null,
                tint = if (isPositive) Color(0xFF00E676) else Color(0xFFFF5252),
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = diff,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isPositive) Color(0xFF00E676) else Color(0xFFFF5252)
            )
        }
    }
}

// ==========================================
// 4. Library Insights Section
// ==========================================
@Composable
private fun LibraryInsightsSection(
    libraryCounts: com.example.data.LibraryStatusCounts,
    mostReadSeries: List<SeriesStatistics>,
    recentSeries: List<Manga>,
    onSelectManga: (Manga) -> Unit,
    onResumeManga: (Manga) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Leaderboard,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Library Insights",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Reading Status Pills Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusCountCard(
                    title = "Reading",
                    count = libraryCounts.currentlyReading,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                StatusCountCard(
                    title = "Completed",
                    count = libraryCounts.completed,
                    color = Color(0xFF00E676),
                    modifier = Modifier.weight(1f)
                )
                StatusCountCard(
                    title = "Unread",
                    count = libraryCounts.unread,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                StatusCountCard(
                    title = "Favorites",
                    count = libraryCounts.favorites,
                    color = Color(0xFFFF4081),
                    modifier = Modifier.weight(1f)
                )
            }

            // Most-Read Series
            if (mostReadSeries.isNotEmpty()) {
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = "Most-Read Series",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                mostReadSeries.take(4).forEachIndexed { index, series ->
                    MostReadSeriesItem(
                        rank = index + 1,
                        series = series
                    )
                    if (index < 3 && index < mostReadSeries.size - 1) {
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }

            // Continue Reading / Recent series
            if (recentSeries.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Continue Reading",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(recentSeries) { manga ->
                        RecentSeriesCard(
                            manga = manga,
                            onResume = { onResumeManga(manga) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusCountCard(
    title: String,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.12f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "$count",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = title,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun MostReadSeriesItem(
    rank: Int,
    series: SeriesStatistics
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rank badge
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(
                        when (rank) {
                            1 -> Color(0xFFFFD700)
                            2 -> Color(0xFFC0C0C0)
                            3 -> Color(0xFFCD7F32)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "#$rank",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (rank <= 3) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Cover thumbnail
            Box(
                modifier = Modifier
                    .size(width = 36.dp, height = 48.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (series.coverPath != null) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(File(series.coverPath))
                            .crossfade(true)
                            .build(),
                        contentDescription = series.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.MenuBook,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = series.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${series.chaptersCompleted}/${series.totalChapters} chapters completed · ${series.pagesProgressed} pages",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatDuration(series.readingTimeMillis),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp
                )
                Text(
                    text = "read",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun RecentSeriesCard(
    manga: Manga,
    onResume: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(130.dp)
            .clickable { onResume() },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (manga.coverPath != null) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(File(manga.coverPath))
                            .crossfade(true)
                            .build(),
                        contentDescription = manga.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.MenuBook,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(32.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .size(24.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Resume",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = manga.title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = manga.lastReadChapterTitle ?: "Page ${manga.lastReadPage + 1}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ==========================================
// 5. Reading Habits & Personal Records Section
// ==========================================
@Composable
private fun ReadingHabitsSection(habits: ReadingHabits) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Habits & Personal Records",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            HabitRecordRow(
                icon = Icons.Default.CalendarMonth,
                title = "Most Active Day",
                value = habits.mostActiveWeekday,
                subtitle = "Day of the week with the most reading time"
            )

            HabitRecordRow(
                icon = Icons.Default.Timer,
                title = "Longest Single Session",
                value = if (habits.longestSessionDurationMillis > 0) formatDuration(habits.longestSessionDurationMillis) else "No sessions yet",
                subtitle = habits.longestSessionMangaTitle ?: "Personal duration record"
            )

            HabitRecordRow(
                icon = Icons.Default.AutoStories,
                title = "Single-Day Page Record",
                value = "${habits.recordPagesInDay} pages",
                subtitle = habits.recordPagesDate ?: "Highest page count in 24 hours"
            )

            HabitRecordRow(
                icon = Icons.Default.MenuBook,
                title = "Single-Day Chapter Record",
                value = "${habits.recordChaptersInDay} chapters",
                subtitle = habits.recordChaptersDate ?: "Most chapters completed in one day"
            )

            HabitRecordRow(
                icon = Icons.Default.Speed,
                title = "Average Reading Speed",
                value = if (habits.readingSpeedPagesPerMinute > 0f) String.format(Locale.US, "%.1f pages / min", habits.readingSpeedPagesPerMinute) else "Not enough data",
                subtitle = if (habits.readingSpeedPagesPerMinute > 0f) "Pages turned forward per minute of reading"
                else "Shows once you've read for a minute"
            )
        }
    }
}

@Composable
private fun HabitRecordRow(
    icon: ImageVector,
    title: String,
    value: String,
    subtitle: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

// ==========================================
// Formatting Helpers
// ==========================================
private fun formatDuration(millis: Long): String {
    if (millis <= 0L) return "0m"
    val totalMinutes = millis / 60000L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return when {
        hours > 0L && minutes > 0L -> "${hours}h ${minutes}m"
        hours > 0L -> "${hours}h"
        else -> "${minutes.coerceAtLeast(1L)}m"
    }
}

private fun formatDurationDiff(millis: Long): String {
    val sign = if (millis >= 0L) "+" else "-"
    val absMillis = kotlin.math.abs(millis)
    val totalMinutes = absMillis / 60000L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return when {
        hours > 0L -> "$sign${hours}h ${minutes}m"
        else -> "$sign${minutes}m"
    }
}

private fun formatDiff(diff: Int?, percentDiff: Float?, unit: String): String {
    if (diff == null) return "No previous data"
    val sign = if (diff >= 0) "+" else ""
    return if (percentDiff != null) {
        val pctSign = if (percentDiff >= 0f) "+" else ""
        "$sign$diff $unit ($pctSign${percentDiff.roundToInt()}%)"
    } else {
        "$sign$diff $unit"
    }
}
