package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AccentPreset
import com.example.data.ActivityChartMetric
import com.example.data.Chapter
import com.example.data.DayActivity
import com.example.data.Manga
import com.example.data.ReaderFitMode
import com.example.data.ReaderTheme
import com.example.data.ReadingDirection
import com.example.data.StatisticsUiState
import com.example.data.TimePeriod
import com.example.data.db.AppDatabase
import com.example.data.preferences.PreferencesManager
import com.example.data.repository.MangaRepository
import com.example.data.repository.StatisticsRepository
import com.example.data.storage.CbzExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

enum class NavTab {
    HOME, STATISTICS, SEARCH, SETTINGS
}

enum class LibraryFilter {
    ALL, IN_PROGRESS, UNREAD, FAVORITES
}

class MangaViewModel(application: Application) : AndroidViewModel(application) {
    val preferencesManager = PreferencesManager(application)
    private val database = AppDatabase.getDatabase(application)
    val repository = MangaRepository(application, database.mangaDao(), preferencesManager)
    val statisticsRepository = StatisticsRepository(database.statisticsDao(), database.mangaDao(), preferencesManager)

    // Statistics UI filter state
    private val _selectedTimePeriod = MutableStateFlow(TimePeriod.THIS_WEEK)
    val selectedTimePeriod: StateFlow<TimePeriod> = _selectedTimePeriod.asStateFlow()

    private val _selectedChartMetric = MutableStateFlow(ActivityChartMetric.READING_TIME)
    val selectedChartMetric: StateFlow<ActivityChartMetric> = _selectedChartMetric.asStateFlow()

    private val _calendarMonthOffset = MutableStateFlow(0)
    val calendarMonthOffset: StateFlow<Int> = _calendarMonthOffset.asStateFlow()

    private val _selectedCalendarDay = MutableStateFlow<DayActivity?>(null)
    val selectedCalendarDay: StateFlow<DayActivity?> = _selectedCalendarDay.asStateFlow()

    private data class StatsFilterBundle(
        val period: TimePeriod,
        val metric: ActivityChartMetric,
        val monthOffset: Int,
        val selectedDay: DayActivity?
    )

    private val statsFilterFlow = combine(
        _selectedTimePeriod,
        _selectedChartMetric,
        _calendarMonthOffset,
        _selectedCalendarDay
    ) { period, metric, monthOffset, selectedDay ->
        StatsFilterBundle(period, metric, monthOffset, selectedDay)
    }

    val statisticsUiState: StateFlow<StatisticsUiState> = combine(
        statisticsRepository.allSessions,
        statsFilterFlow,
        repository.libraryManga,
        database.mangaDao().getAllChapterProgress()
    ) { sessions, filter, mangaList, chapterProgressList ->
        val summary = statisticsRepository.computeSummary(sessions, filter.period)
        val chartPoints = statisticsRepository.computeChartPoints(sessions, filter.period, filter.metric)
        val (monthLabel, days) = statisticsRepository.computeCalendarDays(sessions, filter.monthOffset)
        val comparison = statisticsRepository.computeComparison(sessions, filter.period)
        val habits = statisticsRepository.computeHabits(sessions)
        val seriesStats = statisticsRepository.computeSeriesStatistics(sessions, mangaList, chapterProgressList)
        val libraryCounts = statisticsRepository.computeLibraryCounts(mangaList, chapterProgressList)
        val recent = mangaList.filter { it.lastReadTime > 0L }.sortedByDescending { it.lastReadTime }.take(6)

        StatisticsUiState(
            selectedPeriod = filter.period,
            selectedMetric = filter.metric,
            isLoading = false,
            summary = summary,
            chartDataPoints = chartPoints,
            calendarDays = days,
            calendarMonthLabel = monthLabel,
            calendarMonthOffset = filter.monthOffset,
            selectedCalendarDay = filter.selectedDay ?: days.find { it.dateString == LocalDate.now().toString() },
            comparison = comparison,
            libraryCounts = libraryCounts,
            mostReadSeries = seriesStats,
            recentSeries = recent,
            habits = habits,
            hasSufficientHistory = sessions.isNotEmpty()
        )
    }.flowOn(Dispatchers.Default).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StatisticsUiState(isLoading = true)
    )

    // App Lock state
    private val _isAppLocked = MutableStateFlow(
        preferencesManager.isAppLockEnabled.value && preferencesManager.hasPin()
    )
    val isAppLocked: StateFlow<Boolean> = _isAppLocked.asStateFlow()

    // Navigation state
    private val _currentTab = MutableStateFlow(NavTab.HOME)
    val currentTab: StateFlow<NavTab> = _currentTab.asStateFlow()

    private val _selectedManga = MutableStateFlow<Manga?>(null)
    val selectedManga: StateFlow<Manga?> = _selectedManga.asStateFlow()

    private val _selectedChapter = MutableStateFlow<Chapter?>(null)
    val selectedChapter: StateFlow<Chapter?> = _selectedChapter.asStateFlow()

    // Library state
    val libraryManga: StateFlow<List<Manga>> = repository.libraryManga.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _libraryFilter = MutableStateFlow(LibraryFilter.ALL)
    val libraryFilter: StateFlow<LibraryFilter> = _libraryFilter.asStateFlow()

    // Filtered manga for Home/Search
    val filteredManga: StateFlow<List<Manga>> = combine(
        libraryManga,
        _searchQuery,
        _libraryFilter,
        _currentTab
    ) { mangaList, query, filter, tab ->
        var list = mangaList
        if (query.isNotBlank()) {
            list = list.filter { it.title.contains(query, ignoreCase = true) }
        }

        if (tab == NavTab.HOME) {
            list = when (filter) {
                LibraryFilter.ALL -> list
                LibraryFilter.IN_PROGRESS -> list.filter { it.lastReadTime > 0L }
                LibraryFilter.UNREAD -> list.filter { it.lastReadTime == 0L }
                LibraryFilter.FAVORITES -> list.filter { it.isFavorite }
            }
        }
        list
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // In-progress item for continue reading hero card
    val continueReadingManga: StateFlow<Manga?> = libraryManga.combine(_selectedManga) { list, _ ->
        list.filter { it.lastReadTime > 0L && it.lastReadChapterTitle != null }
            .maxByOrNull { it.lastReadTime }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    // Chapter list for selected manga
    private val _sortChaptersAscending = MutableStateFlow(true)
    val sortChaptersAscending: StateFlow<Boolean> = _sortChaptersAscending.asStateFlow()

    val currentChapters: StateFlow<List<Chapter>> = _selectedManga.flatMapLatest { manga ->
        if (manga == null) flowOf(emptyList())
        else repository.getChaptersForManga(manga.id)
    }.combine(_sortChaptersAscending) { chapters, asc ->
        if (asc) {
            chapters.sortedWith { a, b ->
                val diff = a.chapterNumber.compareTo(b.chapterNumber)
                if (diff != 0) diff else a.title.compareTo(b.title)
            }
        } else {
            chapters.sortedWith { a, b ->
                val diff = b.chapterNumber.compareTo(a.chapterNumber)
                if (diff != 0) diff else b.title.compareTo(a.title)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Reader state
    private val _readerPages = MutableStateFlow<List<File>>(emptyList())
    val readerPages: StateFlow<List<File>> = _readerPages.asStateFlow()

    private val _isLoadingPages = MutableStateFlow(false)
    val isLoadingPages: StateFlow<Boolean> = _isLoadingPages.asStateFlow()

    // Indices of reader pages whose files are fully written; the rest are still being unpacked
    private val _readyPages = MutableStateFlow<Set<Int>>(emptySet())
    val readyPages: StateFlow<Set<Int>> = _readyPages.asStateFlow()

    private val _currentReaderPage = MutableStateFlow(0)
    val currentReaderPage: StateFlow<Int> = _currentReaderPage.asStateFlow()

    // Cache size info for settings
    private val _cacheSizeBytes = MutableStateFlow(0L)
    val cacheSizeBytes: StateFlow<Long> = _cacheSizeBytes.asStateFlow()

    init {
        // Automatically check if library needs initial scanning
        viewModelScope.launch {
            val uri = preferencesManager.folderUri.value
            if (uri.isNullOrBlank()) {
                // Pre-generate sample manga on first launch for immediate reading demo
                repository.loadSampleManga()
            } else {
                scanLibrary()
            }
            refreshCacheSize()
        }
    }

    fun selectTab(tab: NavTab) {
        _currentTab.value = tab
        _selectedManga.value = null
        _selectedChapter.value = null
    }

    fun setLibraryFilter(filter: LibraryFilter) {
        _libraryFilter.value = filter
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectManga(manga: Manga) {
        _selectedManga.value = manga
    }

    fun resumeReading(manga: Manga) {
        selectManga(manga)
        viewModelScope.launch {
            val chapters = repository.getChaptersForManga(manga.id).firstOrNull() ?: emptyList()
            val targetChapter = chapters.find { it.id == manga.lastReadChapterId }
                ?: chapters.firstOrNull { !it.isRead }
                ?: chapters.firstOrNull()
            if (targetChapter != null) {
                selectChapter(targetChapter)
            }
        }
    }

    fun closeManga() {
        _selectedManga.value = null
    }

    fun toggleSortOrder() {
        _sortChaptersAscending.value = !_sortChaptersAscending.value
    }

    // ==========================================
    // Reading Session Tracking
    // ==========================================
    private var activeSessionMangaId: String? = null
    private var activeSessionChapterId: String? = null
    private var activeSessionChapterTitle: String = ""
    private var activeSessionStartedAt: Long = 0L
    private var activeSessionStartPage: Int = 0
    private var activeSessionMaxPageReached: Int = 0
    private var activeSessionPagesProgressed: Int = 0
    private var activeSessionChapterCompleted: Boolean = false
    private var activeSessionLastActivityTimestamp: Long = 0L
    private var activeSessionAccumulatedDuration: Long = 0L
    private var activeSessionIsPaused: Boolean = false

    fun startReadingSession(
        mangaId: String,
        chapterId: String,
        chapterTitle: String,
        startPage: Int,
        alreadyCompleted: Boolean = false
    ) {
        if (activeSessionChapterId != null) {
            endReadingSession()
        }

        val now = System.currentTimeMillis()
        activeSessionMangaId = mangaId
        activeSessionChapterId = chapterId
        activeSessionChapterTitle = chapterTitle
        activeSessionStartedAt = now
        activeSessionLastActivityTimestamp = now
        activeSessionStartPage = startPage
        activeSessionMaxPageReached = startPage
        activeSessionPagesProgressed = 0
        activeSessionChapterCompleted = false
        activeSessionAccumulatedDuration = 0L
        activeSessionIsPaused = false
    }

    fun onReaderUserInteraction() {
        if (activeSessionChapterId == null) return
        val now = System.currentTimeMillis()
        val inactivityTimeoutMillis = (preferencesManager.inactivityThresholdSeconds.value * 1000L).coerceAtLeast(30000L)
        val elapsed = (now - activeSessionLastActivityTimestamp).coerceAtLeast(0L)

        if (!activeSessionIsPaused) {
            if (elapsed <= inactivityTimeoutMillis) {
                activeSessionAccumulatedDuration += elapsed
            }
        }
        activeSessionLastActivityTimestamp = now
        activeSessionIsPaused = false
    }

    fun pauseReadingSession() {
        if (activeSessionChapterId == null || activeSessionIsPaused) return
        val now = System.currentTimeMillis()
        val inactivityTimeoutMillis = (preferencesManager.inactivityThresholdSeconds.value * 1000L).coerceAtLeast(30000L)
        val elapsed = (now - activeSessionLastActivityTimestamp).coerceAtLeast(0L)
        if (elapsed <= inactivityTimeoutMillis) {
            activeSessionAccumulatedDuration += elapsed
        }
        activeSessionIsPaused = true
    }

    fun resumeReadingSession() {
        if (activeSessionChapterId == null) return
        activeSessionLastActivityTimestamp = System.currentTimeMillis()
        activeSessionIsPaused = false
    }

    fun endReadingSession() {
        val chapterId = activeSessionChapterId ?: return
        val mangaId = activeSessionMangaId ?: return
        val chapterTitle = activeSessionChapterTitle
        val startedAt = activeSessionStartedAt
        val startPage = activeSessionStartPage
        val maxPage = activeSessionMaxPageReached
        val pagesProgressed = activeSessionPagesProgressed
        val chapterCompleted = activeSessionChapterCompleted

        val now = System.currentTimeMillis()
        if (!activeSessionIsPaused) {
            val inactivityTimeoutMillis = (preferencesManager.inactivityThresholdSeconds.value * 1000L).coerceAtLeast(30000L)
            val elapsed = (now - activeSessionLastActivityTimestamp).coerceAtLeast(0L)
            if (elapsed <= inactivityTimeoutMillis) {
                activeSessionAccumulatedDuration += elapsed
            }
        }

        val totalDuration = activeSessionAccumulatedDuration

        activeSessionMangaId = null
        activeSessionChapterId = null
        activeSessionChapterTitle = ""
        activeSessionStartedAt = 0L
        activeSessionLastActivityTimestamp = 0L
        activeSessionAccumulatedDuration = 0L
        activeSessionIsPaused = false

        viewModelScope.launch {
            statisticsRepository.recordSession(
                mangaId = mangaId,
                chapterId = chapterId,
                chapterTitle = chapterTitle,
                startedAt = startedAt,
                endedAt = startedAt + totalDuration,
                startPage = startPage,
                endPage = maxPage,
                pagesProgressed = pagesProgressed,
                completedChapter = chapterCompleted
            )
        }
    }

    fun selectChapter(chapter: Chapter) {
        _selectedChapter.value = chapter
        val manga = _selectedManga.value
        if (manga != null) {
            startReadingSession(
                mangaId = manga.id,
                chapterId = chapter.id,
                chapterTitle = chapter.title,
                startPage = chapter.lastReadPage,
                alreadyCompleted = chapter.isRead
            )
        }
        loadChapterPages(chapter)
    }

    fun closeChapter() {
        endReadingSession()
        _selectedChapter.value = null
        _readerPages.value = emptyList()
    }

    fun handleBackPress(): Boolean {
        if (_selectedChapter.value != null) {
            closeChapter()
            return true
        }
        if (_selectedManga.value != null) {
            closeManga()
            return true
        }
        if (_currentTab.value != NavTab.HOME) {
            _currentTab.value = NavTab.HOME
            return true
        }
        return false
    }

    private fun loadChapterPages(chapter: Chapter) {
        viewModelScope.launch {
            _isLoadingPages.value = true
            _readyPages.value = emptySet()
            // Extraction can outlive this chapter; drop its callbacks once the user has moved on
            fun isCurrent() = _selectedChapter.value?.id == chapter.id
            var openedEarly = false
            val listener = CbzExtractor.ExtractionListener(
                onPlanned = { planned ->
                    if (isCurrent()) {
                        _readyPages.value = emptySet()
                        _readerPages.value = planned
                        _currentReaderPage.value = if (chapter.lastReadPage in planned.indices) chapter.lastReadPage else 0
                        _isLoadingPages.value = false // open the reader now; pages fill in as they are written
                        openedEarly = true
                    }
                },
                onPageReady = { index -> if (isCurrent()) _readyPages.update { it + index } }
            )
            val pages = repository.getChapterPages(chapter.id, chapter.fileUriString, chapter.lastReadPage, listener)
            if (!isCurrent()) return@launch
            _readerPages.value = pages
            _readyPages.value = pages.indices.toSet()
            if (!openedEarly) {
                // Resume from lastReadPage if valid (when opened early the user may already have turned pages)
                _currentReaderPage.value = if (chapter.lastReadPage in pages.indices) chapter.lastReadPage else 0
            }
            _isLoadingPages.value = false

            // Warm the next chapter's page cache so goToNextChapter opens instantly
            val list = currentChapters.value
            val index = list.indexOfFirst { it.id == chapter.id }
            list.getOrNull(index + 1)?.takeIf { index != -1 }?.let { next ->
                repository.getChapterPages(next.id, next.fileUriString)
            }
        }
    }

    fun onPageChanged(pageIndex: Int) {
        _currentReaderPage.value = pageIndex
        val chapter = _selectedChapter.value ?: return
        val manga = _selectedManga.value ?: return
        val total = _readerPages.value.size

        onReaderUserInteraction()
        if (pageIndex > activeSessionMaxPageReached) {
            val delta = pageIndex - activeSessionMaxPageReached
            activeSessionPagesProgressed += delta
            activeSessionMaxPageReached = pageIndex
        }

        val completionThreshold = if (total > 0) ((total - 1) * 0.9f).toInt().coerceAtLeast(total - 1) else 0
        val isCompleted = (pageIndex >= completionThreshold && total > 0) || (pageIndex >= total - 1 && total > 0)
        if (isCompleted && !chapter.isRead) {
            activeSessionChapterCompleted = true
        }

        viewModelScope.launch {
            repository.saveReadingProgress(
                mangaId = manga.id,
                chapterId = chapter.id,
                chapterTitle = chapter.title,
                page = pageIndex,
                totalPages = total,
                isCompleted = isCompleted || chapter.isRead
            )
        }
    }

    fun goToNextChapter() {
        val current = _selectedChapter.value ?: return
        val list = currentChapters.value
        val currentIndex = list.indexOfFirst { it.id == current.id }
        if (currentIndex != -1 && currentIndex + 1 < list.size) {
            selectChapter(list[currentIndex + 1])
        }
    }

    fun goToPreviousChapter() {
        val current = _selectedChapter.value ?: return
        val list = currentChapters.value
        val currentIndex = list.indexOfFirst { it.id == current.id }
        if (currentIndex > 0) {
            selectChapter(list[currentIndex - 1])
        }
    }

    fun continueReading(manga: Manga) {
        viewModelScope.launch {
            _selectedManga.value = manga
            val chapters = repository.getChaptersForManga(manga.id).firstOrNull() ?: emptyList()
            val targetChapter = if (manga.lastReadChapterId != null) {
                chapters.find { it.id == manga.lastReadChapterId } ?: chapters.firstOrNull()
            } else {
                chapters.firstOrNull()
            }
            if (targetChapter != null) {
                selectChapter(targetChapter)
            }
        }
    }

    fun scanLibrary() {
        viewModelScope.launch {
            _isScanning.value = true
            repository.scanCurrentLibrary()
            _isScanning.value = false
        }
    }

    fun setFolderUri(uriString: String) {
        viewModelScope.launch {
            _isScanning.value = true
            repository.setLibraryFolder(uriString)
            _isScanning.value = false
        }
    }

    fun loadSampleManga() {
        viewModelScope.launch {
            _isScanning.value = true
            repository.loadSampleManga()
            _isScanning.value = false
        }
    }

    fun toggleFavorite(manga: Manga) {
        viewModelScope.launch {
            repository.toggleFavorite(manga.id, !manga.isFavorite)
        }
    }

    fun toggleBookmark(chapter: Chapter) {
        viewModelScope.launch {
            val mangaId = _selectedManga.value?.id ?: chapter.mangaId
            repository.toggleChapterBookmark(chapter.id, mangaId)
        }
    }

    fun markChapterRead(chapter: Chapter, isRead: Boolean) {
        viewModelScope.launch {
            val mangaId = _selectedManga.value?.id ?: chapter.mangaId
            repository.markChapterRead(chapter.id, mangaId, isRead)
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            repository.clearCache()
            refreshCacheSize()
        }
    }

    fun refreshCacheSize() {
        viewModelScope.launch {
            _cacheSizeBytes.value = repository.getCacheSizeBytes()
        }
    }

    fun setAccentPreset(preset: AccentPreset) {
        preferencesManager.setAccentPreset(preset)
    }

    fun setReadingDirection(direction: ReadingDirection) {
        preferencesManager.setReadingDirection(direction)
    }

    fun setReaderTheme(theme: ReaderTheme) {
        preferencesManager.setReaderTheme(theme)
    }

    fun setReaderFitMode(fitMode: ReaderFitMode) {
        preferencesManager.setReaderFitMode(fitMode)
    }

    fun setVolumeKeysNavigation(enabled: Boolean) {
        preferencesManager.setVolumeKeysNavigation(enabled)
    }

    fun setKeepScreenOn(enabled: Boolean) {
        preferencesManager.setKeepScreenOn(enabled)
    }

    fun unlockApp(pin: String): Boolean {
        val valid = preferencesManager.verifyPin(pin)
        if (valid) {
            _isAppLocked.value = false
        }
        return valid
    }

    fun lockApp() {
        pauseReadingSession()
        if (preferencesManager.isAppLockEnabled.value && preferencesManager.hasPin()) {
            _isAppLocked.value = true
        }
    }

    fun setNewPin(pin: String) {
        preferencesManager.setPin(pin)
        preferencesManager.setAppLockEnabled(true)
        _isAppLocked.value = false
    }

    fun verifyPin(pin: String): Boolean {
        return preferencesManager.verifyPin(pin)
    }

    fun disableAppLock(currentPin: String): Boolean {
        if (preferencesManager.verifyPin(currentPin)) {
            preferencesManager.clearPin()
            preferencesManager.setAppLockEnabled(false)
            _isAppLocked.value = false
            return true
        }
        return false
    }

    fun disableAppLockDirect() {
        preferencesManager.clearPin()
        preferencesManager.setAppLockEnabled(false)
        _isAppLocked.value = false
    }

    fun changePin(oldPin: String, newPin: String): Boolean {
        if (preferencesManager.verifyPin(oldPin)) {
            preferencesManager.setPin(newPin)
            return true
        }
        return false
    }

    fun hasPin(): Boolean = preferencesManager.hasPin()

    fun setCustomCoverFromUri(context: android.content.Context, mangaId: String, imageUri: android.net.Uri) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val customCoversDir = File(context.filesDir, "custom_covers")
                customCoversDir.mkdirs()
                val destFile = File(customCoversDir, "cover_${mangaId.hashCode()}_${System.currentTimeMillis()}.jpg")
                val scaled = com.example.data.storage.CoverImages.saveScaled(
                    { context.contentResolver.openInputStream(imageUri) }, destFile
                )
                if (!scaled) {
                    context.contentResolver.openInputStream(imageUri)?.use { input ->
                        java.io.FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                if (destFile.exists() && destFile.length() > 0) {
                    repository.updateMangaCover(mangaId, destFile.absolutePath)
                    _selectedManga.value?.let { current ->
                        if (current.id == mangaId) {
                            _selectedManga.value = current.copy(coverPath = destFile.absolutePath)
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MangaViewModel", "Failed to save custom cover from URI", e)
            }
        }
    }

    fun setCustomCoverFromFile(mangaId: String, coverFile: File) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // Save a scaled copy: coverFile is an extracted page in the chapter cache, which "clear cache" deletes
                val destFile = File(
                    File(getApplication<Application>().filesDir, "custom_covers"),
                    "cover_${mangaId.hashCode()}_${System.currentTimeMillis()}.jpg"
                )
                val path = if (com.example.data.storage.CoverImages.saveScaled({ coverFile.inputStream() }, destFile)) {
                    destFile.absolutePath
                } else {
                    coverFile.absolutePath
                }
                repository.updateMangaCover(mangaId, path)
                _selectedManga.value?.let { current ->
                    if (current.id == mangaId) {
                        _selectedManga.value = current.copy(coverPath = path)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MangaViewModel", "Failed to set custom cover from file", e)
            }
        }
    }

    // ==========================================
    // Statistics Actions
    // ==========================================
    fun setTimePeriod(period: TimePeriod) {
        _selectedTimePeriod.value = period
    }

    fun setChartMetric(metric: ActivityChartMetric) {
        _selectedChartMetric.value = metric
    }

    fun setCalendarMonthOffset(offset: Int) {
        _calendarMonthOffset.value = offset
    }

    fun selectCalendarDay(day: DayActivity?) {
        _selectedCalendarDay.value = day
    }

    fun clearStatistics() {
        viewModelScope.launch {
            statisticsRepository.clearStatistics()
        }
    }

    fun setTrackStatistics(enabled: Boolean) {
        preferencesManager.setTrackStatistics(enabled)
    }

    fun setInactivityThresholdSeconds(seconds: Int) {
        preferencesManager.setInactivityThresholdSeconds(seconds)
    }
}
