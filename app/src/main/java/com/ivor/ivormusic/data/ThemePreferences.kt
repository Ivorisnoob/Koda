package com.ivor.ivormusic.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages theme preferences (light/dark mode).
 */
class ThemePreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )

    private val _isDarkMode = MutableStateFlow(getDarkModePreference())
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    companion object {
        private const val PREFS_NAME = "ivor_music_theme_prefs"
        private const val KEY_DARK_MODE = "dark_mode"
    }

    /**
     * Get the stored dark mode preference. Defaults to true (dark mode).
     */
    private fun getDarkModePreference(): Boolean {
        return prefs.getBoolean(KEY_DARK_MODE, true)
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
}
