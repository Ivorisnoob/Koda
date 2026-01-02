package com.ivor.ivormusic.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages app preferences (theme, local songs toggle, etc.).
 */
class ThemePreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )

    private val _isDarkMode = MutableStateFlow(getDarkModePreference())
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val _loadLocalSongs = MutableStateFlow(getLoadLocalSongsPreference())
    val loadLocalSongs: StateFlow<Boolean> = _loadLocalSongs.asStateFlow()

    companion object {
        private const val PREFS_NAME = "ivor_music_theme_prefs"
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_LOAD_LOCAL_SONGS = "load_local_songs"
    }

    /**
     * Get the stored dark mode preference. Defaults to true (dark mode).
     */
    private fun getDarkModePreference(): Boolean {
        return prefs.getBoolean(KEY_DARK_MODE, true)
    }

    /**
     * Get the stored load local songs preference. Defaults to true.
     */
    private fun getLoadLocalSongsPreference(): Boolean {
        return prefs.getBoolean(KEY_LOAD_LOCAL_SONGS, true)
    }

    /**
     * Save dark mode preference and update the flow.
     */
    fun setDarkMode(isDark: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_MODE, isDark).apply()
        _isDarkMode.value = isDark
    }

    /**
     * Toggle between dark and light mode.
     */
    fun toggleDarkMode() {
        setDarkMode(!_isDarkMode.value)
    }

    /**
     * Save load local songs preference and update the flow.
     */
    fun setLoadLocalSongs(load: Boolean) {
        prefs.edit().putBoolean(KEY_LOAD_LOCAL_SONGS, load).apply()
        _loadLocalSongs.value = load
    }

    /**
     * Toggle load local songs setting.
     */
    fun toggleLoadLocalSongs() {
        setLoadLocalSongs(!_loadLocalSongs.value)
    }
}
