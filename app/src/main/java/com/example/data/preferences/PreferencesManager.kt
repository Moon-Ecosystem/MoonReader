package com.example.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.example.data.AccentPreset
import com.example.data.ReaderFitMode
import com.example.data.ReaderTheme
import com.example.data.ReadingDirection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("manga_reader_prefs", Context.MODE_PRIVATE)

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

    private val _volumeKeysNavigation = MutableStateFlow(prefs.getBoolean(KEY_VOLUME_KEYS, true))
    val volumeKeysNavigation: StateFlow<Boolean> = _volumeKeysNavigation.asStateFlow()

    private val _keepScreenOn = MutableStateFlow(prefs.getBoolean(KEY_KEEP_SCREEN_ON, true))
    val keepScreenOn: StateFlow<Boolean> = _keepScreenOn.asStateFlow()

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

    fun setVolumeKeysNavigation(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VOLUME_KEYS, enabled).apply()
        _volumeKeysNavigation.value = enabled
    }

    fun setKeepScreenOn(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON, enabled).apply()
        _keepScreenOn.value = enabled
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
        private const val KEY_FOLDER_URI = "pref_folder_uri"
        private const val KEY_ACCENT = "pref_accent_preset"
        private const val KEY_READING_DIR = "pref_reading_direction"
        private const val KEY_READER_THEME = "pref_reader_theme"
        private const val KEY_READER_FIT = "pref_reader_fit_mode"
        private const val KEY_VOLUME_KEYS = "pref_volume_keys"
        private const val KEY_KEEP_SCREEN_ON = "pref_keep_screen_on"
        private const val KEY_APP_LOCK_ENABLED = "pref_app_lock_enabled"
        private const val KEY_PIN_HASH = "pref_pin_hash"
        private const val KEY_TRACK_STATISTICS = "pref_track_statistics"
        private const val KEY_INACTIVITY_THRESHOLD = "pref_inactivity_threshold_seconds"
    }
}
