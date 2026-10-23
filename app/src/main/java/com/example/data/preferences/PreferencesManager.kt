package com.example.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.example.data.AccentPreset
import com.example.data.LibrarySort
import com.example.data.LibraryStatus
import com.example.data.ReaderFitMode
import com.example.data.ReaderTheme
import com.example.data.ReadingDirection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Own file so backup rules can exclude the AniList token
    private val authPrefs: SharedPreferences =
        context.getSharedPreferences("anilist_auth", Context.MODE_PRIVATE)

    val aniListToken: String? get() = authPrefs.getString(KEY_ANILIST_TOKEN, null)
    val aniListUserId: Long get() = authPrefs.getLong(KEY_ANILIST_USER_ID, 0L)

    private val _aniListUserName = MutableStateFlow(
        authPrefs.getString(KEY_ANILIST_USER_NAME, null)?.takeIf { aniListToken != null }
    )
    /** The logged-in AniList user's name, or null when logged out. */
    val aniListUserName: StateFlow<String?> = _aniListUserName.asStateFlow()

    private val _aniListScoreFormat = MutableStateFlow(authPrefs.getString(KEY_ANILIST_SCORE_FORMAT, null))
    /** AniList ScoreFormat name (e.g. "POINT_10_DECIMAL"), or null until fetched. */
    val aniListScoreFormat: StateFlow<String?> = _aniListScoreFormat.asStateFlow()

    private val _trackAutoUpdate =MutableStateFlow(prefs.getBoolean(KEY_TRACK_AUTO_UPDATE, true))
    val trackAutoUpdate: StateFlow<Boolean> = _trackAutoUpdate.asStateFlow()

    private val _folderUri = MutableStateFlow(prefs.getString(KEY_FOLDER_URI, null))
    val folderUri: StateFlow<String?> = _folderUri.asStateFlow()

    private val _accentPreset = MutableStateFlow(
        try {
            AccentPreset.valueOf(prefs.getString(KEY_ACCENT, AccentPreset.NIGHT_BLUE.name) ?: AccentPreset.NIGHT_BLUE.name)
        } catch (_: Exception) {
            AccentPreset.NIGHT_BLUE
        }
    )
    val accentPreset: StateFlow<AccentPreset> = _accentPreset.asStateFlow()

    private val _readingDirection = MutableStateFlow(
        try {
            ReadingDirection.valueOf(prefs.getString(KEY_READING_DIR, ReadingDirection.RTL.name) ?: ReadingDirection.RTL.name)
        } catch (_: Exception) {
            ReadingDirection.RTL
        }
    )
    val readingDirection: StateFlow<ReadingDirection> = _readingDirection.asStateFlow()

    private val _readerTheme = MutableStateFlow(
        try {
            ReaderTheme.valueOf(prefs.getString(KEY_READER_THEME, ReaderTheme.BLACK.name) ?: ReaderTheme.BLACK.name)
        } catch (_: Exception) {
            ReaderTheme.BLACK
        }
    )
    val readerTheme: StateFlow<ReaderTheme> = _readerTheme.asStateFlow()

    private val _readerFitMode = MutableStateFlow(
        try {
            ReaderFitMode.valueOf(prefs.getString(KEY_READER_FIT, ReaderFitMode.FIT_SCREEN.name) ?: ReaderFitMode.FIT_SCREEN.name)
        } catch (_: Exception) {
            ReaderFitMode.FIT_SCREEN
        }
    )
    val readerFitMode: StateFlow<ReaderFitMode> = _readerFitMode.asStateFlow()

    private val _librarySort = MutableStateFlow(
        try {
            LibrarySort.valueOf(prefs.getString(KEY_LIBRARY_SORT, LibrarySort.LAST_READ.name) ?: LibrarySort.LAST_READ.name)
        } catch (_: Exception) {
            LibrarySort.LAST_READ
        }
    )
    val librarySort: StateFlow<LibrarySort> = _librarySort.asStateFlow()

    private val _librarySortAscending = MutableStateFlow(
        prefs.getBoolean(KEY_LIBRARY_SORT_ASCENDING, _librarySort.value.ascendingByDefault)
    )
    val librarySortAscending: StateFlow<Boolean> = _librarySortAscending.asStateFlow()

    private val _libraryStatus = MutableStateFlow(
        LibraryStatus.entries.find { it.name == prefs.getString(KEY_LIBRARY_STATUS, null) } ?: LibraryStatus.ALL
    )
    val libraryStatus: StateFlow<LibraryStatus> = _libraryStatus.asStateFlow()

    private val _libraryFavoritesOnly = MutableStateFlow(prefs.getBoolean(KEY_LIBRARY_FAVORITES_ONLY, false))
    val libraryFavoritesOnly: StateFlow<Boolean> = _libraryFavoritesOnly.asStateFlow()

    // Home screen tab: a collection id, or ALL_COLLECTIONS
    private val _libraryCollection = MutableStateFlow(prefs.getLong(KEY_LIBRARY_COLLECTION, ALL_COLLECTIONS))
    val libraryCollection: StateFlow<Long> = _libraryCollection.asStateFlow()

    fun setLibraryCollection(id: Long) {
        prefs.edit().putLong(KEY_LIBRARY_COLLECTION, id).apply()
        _libraryCollection.value = id
    }

    private val _volumeKeysNavigation = MutableStateFlow(prefs.getBoolean(KEY_VOLUME_KEYS, true))
    val volumeKeysNavigation: StateFlow<Boolean> = _volumeKeysNavigation.asStateFlow()

    private val _keepScreenOn = MutableStateFlow(prefs.getBoolean(KEY_KEEP_SCREEN_ON, true))
    val keepScreenOn: StateFlow<Boolean> = _keepScreenOn.asStateFlow()

    private val _showPageNumber = MutableStateFlow(prefs.getBoolean(KEY_SHOW_PAGE_NUMBER, true))
    val showPageNumber: StateFlow<Boolean> = _showPageNumber.asStateFlow()

    // App Lock security preferences
    private val _isAppLockEnabled = MutableStateFlow(prefs.getBoolean(KEY_APP_LOCK_ENABLED, false))
    val isAppLockEnabled: StateFlow<Boolean> = _isAppLockEnabled.asStateFlow()

    fun setFolderUri(uriString: String?) {
        prefs.edit().putString(KEY_FOLDER_URI, uriString).apply()
        _folderUri.value = uriString
    }

    fun setAccentPreset(preset: AccentPreset) {
        prefs.edit().putString(KEY_ACCENT, preset.name).apply()
        _accentPreset.value = preset
    }

    fun setReadingDirection(direction: ReadingDirection) {
        prefs.edit().putString(KEY_READING_DIR, direction.name).apply()
        _readingDirection.value = direction
    }

    fun setReaderTheme(theme: ReaderTheme) {
        prefs.edit().putString(KEY_READER_THEME, theme.name).apply()
        _readerTheme.value = theme
    }

    fun setReaderFitMode(fitMode: ReaderFitMode) {
        prefs.edit().putString(KEY_READER_FIT, fitMode.name).apply()
        _readerFitMode.value = fitMode
    }

    fun setLibrarySort(sort: LibrarySort, ascending: Boolean) {
        prefs.edit()
            .putString(KEY_LIBRARY_SORT, sort.name)
            .putBoolean(KEY_LIBRARY_SORT_ASCENDING, ascending)
            .apply()
        _librarySort.value = sort
        _librarySortAscending.value = ascending
    }

    fun setLibraryFilter(status: LibraryStatus, favoritesOnly: Boolean) {
        prefs.edit()
            .putString(KEY_LIBRARY_STATUS, status.name)
            .putBoolean(KEY_LIBRARY_FAVORITES_ONLY, favoritesOnly)
            .apply()
        _libraryStatus.value = status
        _libraryFavoritesOnly.value = favoritesOnly
    }

    fun setVolumeKeysNavigation(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VOLUME_KEYS, enabled).apply()
        _volumeKeysNavigation.value = enabled
    }

    fun setAniListLogin(token: String, userId: Long, userName: String, scoreFormat: String) {
        authPrefs.edit()
            .putString(KEY_ANILIST_TOKEN, token)
            .putLong(KEY_ANILIST_USER_ID, userId)
            .putString(KEY_ANILIST_USER_NAME, userName)
            .apply()
        _aniListUserName.value = userName
        setAniListScoreFormat(scoreFormat)
    }

    fun setAniListScoreFormat(format: String) {
        authPrefs.edit().putString(KEY_ANILIST_SCORE_FORMAT, format).apply()
        _aniListScoreFormat.value = format
    }

    fun clearAniListLogin() {
        authPrefs.edit().clear().apply()
        _aniListUserName.value = null
        _aniListScoreFormat.value = null
    }

    fun setTrackAutoUpdate(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TRACK_AUTO_UPDATE, enabled).apply()
        _trackAutoUpdate.value = enabled
    }

    fun setKeepScreenOn(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON, enabled).apply()
        _keepScreenOn.value = enabled
    }

    fun setShowPageNumber(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_PAGE_NUMBER, enabled).apply()
        _showPageNumber.value = enabled
    }

    fun setAppLockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_APP_LOCK_ENABLED, enabled).apply()
        _isAppLockEnabled.value = enabled
    }

    fun setPin(pin: String) {
        val hash = hashPin(pin)
        prefs.edit().putString(KEY_PIN_HASH, hash).apply()
    }

    fun verifyPin(pin: String): Boolean {
        val savedHash = prefs.getString(KEY_PIN_HASH, null) ?: return false
        return savedHash == hashPin(pin)
    }

    fun hasPin(): Boolean {
        return !prefs.getString(KEY_PIN_HASH, null).isNullOrBlank()
    }

    fun clearPin() {
        prefs.edit().remove(KEY_PIN_HASH).putBoolean(KEY_APP_LOCK_ENABLED, false).apply()
        _isAppLockEnabled.value = false
    }

    private fun hashPin(pin: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // Statistics preferences
    private val _trackStatistics = MutableStateFlow(prefs.getBoolean(KEY_TRACK_STATISTICS, true))
    val trackStatistics: StateFlow<Boolean> = _trackStatistics.asStateFlow()

    private val _inactivityThresholdSeconds = MutableStateFlow(prefs.getInt(KEY_INACTIVITY_THRESHOLD, 120))
    val inactivityThresholdSeconds: StateFlow<Int> = _inactivityThresholdSeconds.asStateFlow()

    fun setTrackStatistics(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TRACK_STATISTICS, enabled).apply()
        _trackStatistics.value = enabled
    }

    fun setInactivityThresholdSeconds(seconds: Int) {
        prefs.edit().putInt(KEY_INACTIVITY_THRESHOLD, seconds).apply()
        _inactivityThresholdSeconds.value = seconds
    }

    companion object {
        const val PREFS_NAME = "manga_reader_prefs"
        private const val KEY_FOLDER_URI = "pref_folder_uri"
        private const val KEY_ACCENT = "pref_accent_preset"
        private const val KEY_READING_DIR = "pref_reading_direction"
        private const val KEY_READER_THEME = "pref_reader_theme"
        private const val KEY_READER_FIT = "pref_reader_fit_mode"
        private const val KEY_LIBRARY_SORT = "pref_library_sort"
        private const val KEY_LIBRARY_SORT_ASCENDING = "pref_library_sort_ascending"
        private const val KEY_LIBRARY_STATUS = "pref_library_status"
        private const val KEY_LIBRARY_FAVORITES_ONLY = "pref_library_favorites_only"
        private const val KEY_LIBRARY_COLLECTION = "pref_library_collection"

        /** The "All" tab; collection ids start at 1. */
        const val ALL_COLLECTIONS = 0L
        private const val KEY_VOLUME_KEYS = "pref_volume_keys"
        private const val KEY_KEEP_SCREEN_ON = "pref_keep_screen_on"
        private const val KEY_SHOW_PAGE_NUMBER = "pref_show_page_number"
        private const val KEY_APP_LOCK_ENABLED = "pref_app_lock_enabled"
        private const val KEY_PIN_HASH = "pref_pin_hash"
        private const val KEY_TRACK_STATISTICS = "pref_track_statistics"
        private const val KEY_INACTIVITY_THRESHOLD = "pref_inactivity_threshold_seconds"
        private const val KEY_TRACK_AUTO_UPDATE = "pref_track_auto_update"
        private const val KEY_ANILIST_TOKEN = "anilist_token"
        private const val KEY_ANILIST_USER_ID = "anilist_user_id"
        private const val KEY_ANILIST_USER_NAME = "anilist_user_name"
        private const val KEY_ANILIST_SCORE_FORMAT = "anilist_score_format"

        /**
         * Settings a backup leaves out: the folder permission can't move to another install, and a restored
         * PIN could lock the user out.
         */
        val BACKUP_EXCLUDED_KEYS = setOf(KEY_FOLDER_URI, KEY_APP_LOCK_ENABLED, KEY_PIN_HASH)
    }
}
