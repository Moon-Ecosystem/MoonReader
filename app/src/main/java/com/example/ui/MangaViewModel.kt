package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AccentPreset
import com.example.data.Chapter
import com.example.data.Manga
import com.example.data.ReaderFitMode
import com.example.data.ReaderTheme
import com.example.data.ReadingDirection
import com.example.data.db.AppDatabase
import com.example.data.preferences.PreferencesManager
import com.example.data.repository.MangaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

enum class NavTab {
    HOME, SEARCH, SETTINGS
}

enum class LibraryFilter {
    ALL, IN_PROGRESS, UNREAD, FAVORITES
}

class MangaViewModel(application: Application) : AndroidViewModel(application) {
    val preferencesManager = PreferencesManager(application)
    private val database = AppDatabase.getDatabase(application)
    val repository = MangaRepository(application, database.mangaDao(), preferencesManager)

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

    fun selectChapter(chapter: Chapter) {
        _selectedChapter.value = chapter
        loadChapterPages(chapter)
    }

    fun closeChapter() {
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
            val pages = repository.getChapterPages(chapter.id, chapter.fileUriString)
            _readerPages.value = pages
            // Resume from lastReadPage if valid
            val startPage = if (chapter.lastReadPage in pages.indices) chapter.lastReadPage else 0
            _currentReaderPage.value = startPage
            _isLoadingPages.value = false
        }
    }

    fun onPageChanged(pageIndex: Int) {
        _currentReaderPage.value = pageIndex
        val chapter = _selectedChapter.value ?: return
        val manga = _selectedManga.value ?: return
        val total = _readerPages.value.size
        val isCompleted = pageIndex >= total - 1 && total > 0

        viewModelScope.launch {
            repository.saveReadingProgress(
                mangaId = manga.id,
                chapterId = chapter.id,
                chapterTitle = chapter.title,
                page = pageIndex,
                totalPages = total,
                isCompleted = isCompleted
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
                context.contentResolver.openInputStream(imageUri)?.use { input ->
                    java.io.FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
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
                repository.updateMangaCover(mangaId, coverFile.absolutePath)
                _selectedManga.value?.let { current ->
                    if (current.id == mangaId) {
                        _selectedManga.value = current.copy(coverPath = coverFile.absolutePath)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MangaViewModel", "Failed to set custom cover from file", e)
            }
        }
    }
}
