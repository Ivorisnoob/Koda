package com.ivor.ivormusic.ui.theme

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.ivor.ivormusic.data.ThemePreferences
import kotlinx.coroutines.flow.StateFlow

/**
 * ViewModel for managing theme state across the app.
 */
class ThemeViewModel(application: Application) : AndroidViewModel(application) {

    private val themePreferences = ThemePreferences(application)

    val isDarkMode: StateFlow<Boolean> = themePreferences.isDarkMode

    fun setDarkMode(isDark: Boolean) {
        themePreferences.setDarkMode(isDark)
    }

    fun toggleDarkMode() {
        themePreferences.toggleDarkMode()
    }
}
