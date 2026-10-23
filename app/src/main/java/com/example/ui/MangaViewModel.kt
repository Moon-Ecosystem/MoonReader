package com.example.ui

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AccentPreset
import com.example.data.ActivityChartMetric
import com.example.data.Chapter
import com.example.data.ChapterFilter
import com.example.data.DayActivity
import com.example.data.LibrarySort
import com.example.data.LibraryStatus
import com.example.data.Manga
import com.example.data.PageRef
import com.example.data.ReaderFitMode
import com.example.data.ReaderTheme
import com.example.data.ReadingDirection
import com.example.data.StatisticsUiState
import com.example.data.TimePeriod
import com.example.data.db.AppDatabase
import com.example.data.preferences.PreferencesManager
import com.example.data.repository.MangaRepository
import com.example.data.repository.StatisticsRepository
import com.example.data.storage.ComicInfo
import com.example.data.storage.OpenArchives
import com.example.data.storage.TitleCleaner
import com.example.data.db.MangaTrackEntity
import com.example.data.db.CollectionEntity
import com.example.data.db.CollectionMangaEntity
import com.example.data.tracking.AniListMedia
import com.example.data.tracking.TrackingRepository
import com.example.data.tracking.trackNumbers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.time.LocalDate

enum class NavTab {
    HOME, STATISTICS, SEARCH, SETTINGS
}

/**
 * Keeps the series matching [status] and, if [favoritesOnly], only favorites. A series counts as started once
 * it was opened or any chapter was marked read, so one marked read from the library isn't "Not started".
 */
internal fun filterLibrary(list: List<Manga>, status: LibraryStatus, favoritesOnly: Boolean): List<Manga> =
    list.filter { manga ->
        val started = manga.lastReadTime > 0L || manga.readChapterCount > 0
        val statusMatches = when (status) {
            LibraryStatus.ALL -> true
            LibraryStatus.READING -> started && !manga.isCompleted
            LibraryStatus.NOT_STARTED -> !started
            LibraryStatus.COMPLETED -> manga.isCompleted
        }
        statusMatches && (!favoritesOnly || manga.isFavorite)
    }

/** Orders the library by [sort]; ties (e.g. never-read manga under "Last read") fall back to title A–Z. */
internal fun sortLibrary(list: List<Manga>, sort: LibrarySort, ascending: Boolean): List<Manga> {
    val primary: Comparator<Manga> = when (sort) {
        LibrarySort.LAST_READ -> compareBy { it.lastReadTime }
        LibrarySort.TITLE -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
        LibrarySort.TOTAL_CHAPTERS -> compareBy { it.chapterCount }
        LibrarySort.UNREAD -> compareBy { (it.chapterCount - it.readChapterCount).coerceAtLeast(0) }
    }
    val directed = if (ascending) primary else primary.reversed()
    return list.sortedWith(directed.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title })
}

/**
 * The chapters a series page shows. Unread includes chapters started but not finished. With [byLastRead] the
 * most recently read come first (never-read ones keep their order at the end). Only the list changes: the
 * reader's next-chapter order stays [MangaViewModel.currentChapters].
 */
internal fun visibleChapters(
    chapters: List<Chapter>,
    filter: ChapterFilter,
    bookmarkedOnly: Boolean,
    byLastRead: Boolean
): List<Chapter> {
    val kept = chapters.filter { chapter ->
        val matches = when (filter) {
            ChapterFilter.ALL -> true
            ChapterFilter.UNREAD -> !chapter.isRead
            ChapterFilter.IN_PROGRESS -> !chapter.isRead && chapter.lastReadPage > 0
            ChapterFilter.READ -> chapter.isRead
        }
        matches && (!bookmarkedOnly || chapter.isBookmarked)
    }
    return if (byLastRead) kept.sortedByDescending { it.lastReadTime } else kept
}

/** Chapter order used by the series page and the reader: number first, then title; [ascending] flips both. */
internal fun sortChaptersByNumber(chapters: List<Chapter>, ascending: Boolean): List<Chapter> =
    chapters.sortedWith { a, b ->
        val byNumber = if (ascending) {
            a.chapterNumber.compareTo(b.chapterNumber)
        } else {
            b.chapterNumber.compareTo(a.chapterNumber)
        }
        if (byNumber != 0) byNumber
        else if (ascending) a.title.compareTo(b.title) else b.title.compareTo(a.title)
    }

/** Id prefix for a file opened from another app rather than from the library. */
private const val EXTERNAL_PREFIX = "external:"

class MangaViewModel(application: Application) : AndroidViewModel(application) {
    val preferencesManager = PreferencesManager(application)
    private val database = AppDatabase.getDatabase(application)
    val repository = MangaRepository(application, database.mangaDao(), preferencesManager)
    val statisticsRepository = StatisticsRepository(database.statisticsDao(), database.mangaDao(), preferencesManager)
    private val trackingRepository = TrackingRepository(database.trackDao(), preferencesManager)
    private val backupRepository = com.example.data.backup.BackupRepository(application, database.backupDao())

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
    // Follows the live library entry: the tapped Manga is a snapshot, so without this the chapter list's
    // "Continue" kept pointing at the chapter that was current when the manga was opened
    val selectedManga: StateFlow<Manga?> = combine(_selectedManga, repository.libraryManga) { selected, library ->
        selected?.let { s -> library.find { it.id == s.id } ?: s }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // Metadata for the open manga from its first chapter's ComicInfo.xml (null when the archive has none);
    // read on demand rather than during the library scan
    val selectedMangaInfo: StateFlow<ComicInfo?> = combine(_selectedManga, repository.scannedChapters) { manga, chaptersById ->
        manga?.let { chaptersById[it.id]?.firstOrNull()?.fileUriString }
    }.distinctUntilChanged().flatMapLatest { firstChapterUri ->
        if (firstChapterUri == null) flowOf(null)
        else flow<ComicInfo?> {
            emit(null)
            emit(repository.readComicInfo(firstChapterUri))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** The open manga's AniList link, or null when it isn't tracked. */
    val selectedTrack: StateFlow<MangaTrackEntity?> = _selectedManga.flatMapLatest { manga ->
        manga?.let { trackingRepository.observe(it.id) } ?: flowOf(null)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

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

    private val collectionDao = database.collectionDao()

    val collections: StateFlow<List<CollectionEntity>> = collectionDao.observeCollections()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Collection id -> ids of the series in it. */
    val collectionMembers: StateFlow<Map<Long, Set<String>>> = collectionDao.observeMembers()
        .map { members -> members.groupBy({ it.collectionId }, { it.mangaId }).mapValues { it.value.toSet() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** The selected home screen tab; falls back to All once its collection is deleted. */
    val selectedCollection: StateFlow<Long> = combine(preferencesManager.libraryCollection, collections) { id, list ->
        if (list.any { it.id == id }) id else PreferencesManager.ALL_COLLECTIONS
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PreferencesManager.ALL_COLLECTIONS)

    private val libraryFilterFlow = combine(
        preferencesManager.libraryStatus,
        preferencesManager.libraryFavoritesOnly
    ) { status, favoritesOnly -> status to favoritesOnly }

    private val librarySortFlow = combine(
        preferencesManager.librarySort,
        preferencesManager.librarySortAscending
    ) { sort, ascending -> sort to ascending }

    /**
     * Each home screen tab's series, filtered and sorted: [PreferencesManager.ALL_COLLECTIONS] and every collection
     * id. Every tab is ready so swiping shows the next tab's grid while it slides in; the sizes are the tab counts.
     */
    val homeLibrary: StateFlow<Map<Long, List<Manga>>> = combine(
        libraryManga,
        libraryFilterFlow,
        collectionMembers,
        collections,
        librarySortFlow
    ) { library, (status, favoritesOnly), members, list, (sort, ascending) ->
        val visible = sortLibrary(filterLibrary(library, status, favoritesOnly), sort, ascending)
        mapOf(PreferencesManager.ALL_COLLECTIONS to visible) +
            list.associate { c -> c.id to members[c.id].orEmpty().let { ids -> visible.filter { it.id in ids } } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** Search results: the whole library matching the query, sorted; collections and filters only narrow Home. */
    val filteredManga: StateFlow<List<Manga>> = combine(
        libraryManga,
        _searchQuery,
        librarySortFlow
    ) { mangaList, query, (sort, ascending) ->
        val list = if (query.isBlank()) mangaList else mangaList.filter { it.title.contains(query, ignoreCase = true) }
        sortLibrary(list, sort, ascending)
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
        sortChaptersByNumber(chapters, asc)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    /** Chapters taken out of the series; the list offers them back for restoring. */
    val removedChapters: StateFlow<List<Chapter>> = _selectedManga.flatMapLatest { manga ->
        if (manga == null) flowOf(emptyList())
        else repository.getRemovedChaptersForManga(manga.id)
    }.combine(_sortChaptersAscending) { chapters, asc ->
        sortChaptersByNumber(chapters, asc)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Reader state
    private val _readerPages = MutableStateFlow<List<PageRef>>(emptyList())
    val readerPages: StateFlow<List<PageRef>> = _readerPages.asStateFlow()

    private val _isLoadingPages = MutableStateFlow(false)
    val isLoadingPages: StateFlow<Boolean> = _isLoadingPages.asStateFlow()

    private val _currentReaderPage = MutableStateFlow(0)
    val currentReaderPage: StateFlow<Int> = _currentReaderPage.asStateFlow()

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
            // Pages are read straight from archives: drop chapters extracted by older versions and any
            // leftover temp copy from a non-seekable provider
            repository.clearCache()
        }
        // Progress read while offline last time
        viewModelScope.launch { trackingRepository.syncPending() }
    }

    override fun onCleared() {
        OpenArchives.retainOnly(emptySet())
    }

    fun selectTab(tab: NavTab) {
        _currentTab.value = tab
        _selectedManga.value = null
        _selectedChapter.value = null
    }

    fun setLibraryStatus(status: LibraryStatus) =
        preferencesManager.setLibraryFilter(status, preferencesManager.libraryFavoritesOnly.value)

    fun setLibraryFavoritesOnly(favoritesOnly: Boolean) =
        preferencesManager.setLibraryFilter(preferencesManager.libraryStatus.value, favoritesOnly)

    fun resetLibraryFilter() = preferencesManager.setLibraryFilter(LibraryStatus.ALL, false)

    // --- Collections ---

    fun selectCollection(id: Long) = preferencesManager.setLibraryCollection(id)

    /** Creates a collection named [name] (ignored when blank), adding [mangaId] to it if given. */
    fun createCollection(name: String, mangaId: String? = null) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val id = collectionDao.create(trimmed)
            if (mangaId != null) collectionDao.addMember(CollectionMangaEntity(id, mangaId))
        }
    }

    fun renameCollection(id: Long, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { collectionDao.rename(id, trimmed) }
    }

    fun deleteCollection(id: Long) {
        viewModelScope.launch { collectionDao.delete(id) }
    }

    fun setInCollection(collectionId: Long, mangaId: String, member: Boolean) {
        viewModelScope.launch {
            if (member) collectionDao.addMember(CollectionMangaEntity(collectionId, mangaId))
            else collectionDao.removeMember(collectionId, mangaId)
        }
    }

    /** Picking the active sort flips its direction; a new one starts in its natural direction. */
    fun onLibrarySortSelected(sort: LibrarySort) {
        val ascending = if (sort == preferencesManager.librarySort.value) {
            !preferencesManager.librarySortAscending.value
        } else {
            sort.ascendingByDefault
        }
        preferencesManager.setLibrarySort(sort, ascending)
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
            // On a cold start (e.g. from the widget) the chapter list fills in when the launch scan finishes
            val chapters = withTimeoutOrNull(10_000) {
                repository.getChaptersForManga(manga.id).first { it.isNotEmpty() }
            } ?: emptyList()
            val targetChapter = chapters.find { it.id == manga.lastReadChapterId }
                ?: chapters.firstOrNull { !it.isRead }
                ?: chapters.firstOrNull()
            if (targetChapter != null) {
                selectChapter(targetChapter)
            }
        }
    }

    /** Resumes a series picked on the home screen widget. */
    fun resumeReading(mangaId: String) {
        viewModelScope.launch {
            repository.libraryManga.first().find { it.id == mangaId }?.let { resumeReading(it) }
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
        if (activeSessionChapterId == null) {
            // The session was saved when the app went to the background: start a new one from this page
            val chapter = _selectedChapter.value?.takeUnless { it.isExternal } ?: return
            val manga = _selectedManga.value ?: return
            startReadingSession(manga.id, chapter.id, chapter.title, _currentReaderPage.value)
            return
        }
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
                endedAt = now,
                durationMillis = totalDuration,
                startPage = startPage,
                endPage = maxPage,
                pagesProgressed = pagesProgressed,
                completedChapter = chapterCompleted
            )
        }
    }

    /** Opens a CBZ handed over by another app ("Open with"). It isn't in the library, so no progress or stats are saved. */
    fun openExternalFile(uri: String, name: String) {
        val id = EXTERNAL_PREFIX + uri
        _selectedManga.value = Manga(id = id, title = name, folderUriString = "", chapterCount = 1)
        selectChapter(Chapter(id = id, mangaId = id, title = name, fileName = name, fileUriString = uri))
    }

    private val Chapter.isExternal get() = id.startsWith(EXTERNAL_PREFIX)

    fun selectChapter(chapter: Chapter) {
        _selectedChapter.value = chapter
        val manga = _selectedManga.value
        if (manga != null && !chapter.isExternal) {
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
        // An opened file has no chapter list to return to
        if (_selectedChapter.value?.isExternal == true) _selectedManga.value = null
        _selectedChapter.value = null
        _readerPages.value = emptyList()
        viewModelScope.launch { repository.retainOpenChapters(emptySet()) }
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
            val list = currentChapters.value
            val index = list.indexOfFirst { it.id == chapter.id }
            val next = list.getOrNull(index + 1)?.takeIf { index != -1 }
            // Keep only this chapter and the next one open; opening reads just the archive's table of contents
            repository.retainOpenChapters(setOfNotNull(chapter.fileUriString, next?.fileUriString))
            val pages = repository.openChapterPages(chapter.fileUriString)
            if (_selectedChapter.value?.id != chapter.id) return@launch
            _readerPages.value = pages
            // Resume from lastReadPage if valid
            _currentReaderPage.value = if (chapter.lastReadPage in pages.indices) chapter.lastReadPage else 0
            _isLoadingPages.value = false

            // Open the next chapter now so goToNextChapter is instant
            next?.let { repository.openChapterPages(it.fileUriString) }
        }
    }

    private val progressWrites = Mutex()

    fun onPageChanged(pageIndex: Int) {
        _currentReaderPage.value = pageIndex
        val chapter = _selectedChapter.value?.takeUnless { it.isExternal } ?: return
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
            // Repeats on later page turns are no-ops: tracked progress only goes up
            onChapterFinished(manga.id, chapter)
        }

        // Saves run one at a time in page-turn order (launch on Main + fair Mutex); in parallel, a slower
        // save of an earlier page could land last and undo the last page / completed state
        viewModelScope.launch {
            progressWrites.withLock {
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

    /** Renames a chapter in the list; the archive keeps its own name on disk. A blank title restores it. */
    fun renameChapter(chapter: Chapter, newTitle: String) {
        viewModelScope.launch {
            val mangaId = _selectedManga.value?.id ?: chapter.mangaId
            repository.renameChapter(chapter.id, mangaId, newTitle)
        }
    }

    /** Removes a chapter from its series and forgets its progress. The archive file itself is not deleted. */
    fun deleteChapter(chapter: Chapter) {
        viewModelScope.launch {
            val mangaId = _selectedManga.value?.id ?: chapter.mangaId
            repository.deleteChapter(chapter.id, mangaId)
        }
    }

    /** Puts a removed chapter back in the series; it comes back unread. */
    fun restoreChapter(chapter: Chapter) {
        viewModelScope.launch { repository.restoreChapter(chapter.id) }
    }

    fun markChapterRead(chapter: Chapter, isRead: Boolean) {
        viewModelScope.launch {
            val mangaId = _selectedManga.value?.id ?: chapter.mangaId
            repository.markChapterRead(chapter.id, mangaId, isRead)
        }
        if (isRead) onChapterFinished(_selectedManga.value?.id ?: chapter.mangaId, chapter)
    }

    fun markMangaRead(manga: Manga, isRead: Boolean) {
        viewModelScope.launch {
            val chapters = repository.markMangaRead(manga.id, isRead)
            // Tracking only ever raises progress, so reporting the last chapter covers them all
            if (isRead) chapters.lastOrNull()?.let { onChapterFinished(manga.id, it) }
        }
    }

    // --- AniList tracking ---

    private fun onChapterFinished(mangaId: String, chapter: Chapter) {
        val (chapterNumber, volumeNumber) = trackNumbers(chapter)
        viewModelScope.launch { trackingRepository.onChapterRead(mangaId, chapterNumber, volumeNumber) }
    }

    private fun isoDate(millis: Long) =
        java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()

    private fun toast(message: String) =
        Toast.makeText(getApplication(), message, Toast.LENGTH_SHORT).show()

    /** Finishes the browser login with the token from the redirect (null when AniList didn't send one). */
    fun completeAniListLogin(token: String?) {
        if (token.isNullOrBlank()) {
            toast("AniList login failed")
            return
        }
        viewModelScope.launch {
            runCatching { trackingRepository.login(token) }
                .onSuccess { toast("Logged in to AniList as $it") }
                .onFailure { toast("AniList login failed: ${it.message}") }
            trackingRepository.syncPending()
        }
    }

    fun logoutAniList() = trackingRepository.logout()

    fun setTrackAutoUpdate(enabled: Boolean) = preferencesManager.setTrackAutoUpdate(enabled)

    /** Search text for linking [manga]: ComicInfo's series name when present, else the cleaned-up folder name. */
    fun trackSearchQuery(manga: Manga): String =
        selectedMangaInfo.value?.series ?: TitleCleaner.clean(manga.title)

    /** AniList results for [query]; those starting in the year from the file name come first. */
    suspend fun searchAniList(query: String, manga: Manga): List<AniListMedia> {
        val year = selectedMangaInfo.value?.year ?: TitleCleaner.year(manga.title)
        return trackingRepository.search(query).sortedByDescending { year != null && it.year == year }
    }

    fun linkTrack(manga: Manga, media: AniListMedia) {
        // Bring over what was already read here when linking
        val chapters = currentChapters.value
        val read = chapters.filter { it.isRead }.map { trackNumbers(it) }
        val chapter = read.mapNotNull { it.first }.maxOrNull()
        val volume = read.mapNotNull { it.second }.maxOrNull()
        val touched = chapters.map { it.lastReadTime }.filter { it > 0 }
        viewModelScope.launch {
            // Start = first reading session here (chapter progress times as a fallback); finish = last read
            val firstRead = database.statisticsDao().getFirstSessionStart(manga.id)?.takeIf { it > 0 } ?: touched.minOrNull()
            val lastRead = touched.maxOrNull()
            runCatching {
                trackingRepository.link(manga.id, media, chapter, volume, firstRead?.let(::isoDate), lastRead?.let(::isoDate))
            }.onFailure { toast("Couldn't link AniList: ${it.message}") }
        }
    }

    fun updateTrack(track: MangaTrackEntity) {
        viewModelScope.launch {
            runCatching { trackingRepository.update(track) }
                .onFailure { toast("Couldn't update AniList: ${it.message}") }
        }
    }

    fun refreshTrack(mangaId: String) {
        viewModelScope.launch { runCatching { trackingRepository.refresh(mangaId) } }
    }

    fun unlinkTrack(mangaId: String) {
        viewModelScope.launch { trackingRepository.unlink(mangaId) }
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

    fun setShowPageNumber(enabled: Boolean) {
        preferencesManager.setShowPageNumber(enabled)
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

    fun setCustomCoverFromPage(mangaId: String, page: PageRef) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val bytes = repository.readPageBytes(page) ?: return@launch
                val destFile = File(
                    File(getApplication<Application>().filesDir, "custom_covers"),
                    "cover_${mangaId.hashCode()}_${System.currentTimeMillis()}.jpg"
                )
                // Scaled JPEG normally; the original bytes if this device can't decode the format
                if (!com.example.data.storage.CoverImages.saveScaled({ bytes.inputStream() }, destFile)) {
                    destFile.parentFile?.mkdirs()
                    destFile.writeBytes(bytes)
                }
                repository.updateMangaCover(mangaId, destFile.absolutePath)
                _selectedManga.value?.let { current ->
                    if (current.id == mangaId) {
                        _selectedManga.value = current.copy(coverPath = destFile.absolutePath)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MangaViewModel", "Failed to set custom cover from page", e)
            }
        }
    }

    /** Page bytes for saving to the gallery; null if the page can't be read. */
    suspend fun readPageBytes(page: PageRef): ByteArray? = repository.readPageBytes(page)

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

    // --- Backup ---

    /** Writes a backup to the document at [uri] and returns the message to show. */
    suspend fun exportBackup(uri: android.net.Uri): String = withContext(Dispatchers.IO) {
        val app = getApplication<Application>()
        try {
            val out = app.contentResolver.openOutputStream(uri, "wt") ?: error("Can't write to the chosen file")
            out.use { backupRepository.export(it, preferencesManager.folderUri.value) }
            val size = androidx.documentfile.provider.DocumentFile.fromSingleUri(app, uri)?.length() ?: 0L
            "Backup saved (${android.text.format.Formatter.formatShortFileSize(app, size)})"
        } catch (e: Exception) {
            android.util.Log.e("MangaViewModel", "Backup failed", e)
            // Don't leave a partial file that looks like a finished backup
            try { android.provider.DocumentsContract.deleteDocument(app.contentResolver, uri) } catch (_: Exception) {}
            "Backup failed: ${e.message}"
        }
    }

    /**
     * Replaces the library data with the backup at [uri], then restarts the app so every screen and setting
     * reloads. Returns an error message if the backup couldn't be restored (nothing is changed then).
     */
    suspend fun restoreBackup(uri: android.net.Uri): String {
        val app = getApplication<Application>()
        try {
            withContext(Dispatchers.IO) {
                val input = app.contentResolver.openInputStream(uri) ?: error("Can't read the chosen file")
                input.use { backupRepository.restore(it, preferencesManager.folderUri.value) }
            }
        } catch (e: com.example.data.backup.BackupException) {
            return e.message ?: "Restore failed"
        } catch (e: Exception) {
            android.util.Log.e("MangaViewModel", "Restore failed", e)
            return "Restore failed: ${e.message}"
        }
        val launch = app.packageManager.getLaunchIntentForPackage(app.packageName)!!
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
        app.startActivity(launch)
        Runtime.getRuntime().exit(0)
        return ""
    }

    fun setTrackStatistics(enabled: Boolean) {
        preferencesManager.setTrackStatistics(enabled)
    }

    fun setInactivityThresholdSeconds(seconds: Int) {
        preferencesManager.setInactivityThresholdSeconds(seconds)
    }
}
