package com.ivor.ivormusic.ui.theme

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.ivor.ivormusic.data.ThemePreferences
import com.ivor.ivormusic.data.PlayerStyle
import kotlinx.coroutines.flow.StateFlow

/**
 * ViewModel for managing theme and app settings state across the app.
 */
class ThemeViewModel(application: Application) : AndroidViewModel(application) {

    private val themePreferences = ThemePreferences(application)

    val themeMode: StateFlow<ThemeMode> = themePreferences.themeMode
    val loadLocalSongs: StateFlow<Boolean> = themePreferences.loadLocalSongs
    val ambientBackground: StateFlow<Boolean> = themePreferences.ambientBackground
    val videoMode: StateFlow<Boolean> = themePreferences.videoMode
    val playerStyle: StateFlow<PlayerStyle> = themePreferences.playerStyle

    fun setThemeMode(mode: ThemeMode) {
        themePreferences.setThemeMode(mode)
    }

    fun setLoadLocalSongs(load: Boolean) {
        themePreferences.setLoadLocalSongs(load)
    }

    fun toggleLoadLocalSongs() {
        themePreferences.toggleLoadLocalSongs()
    }
    
    fun setAmbientBackground(enabled: Boolean) {
        themePreferences.setAmbientBackground(enabled)
    }
    
    fun toggleAmbientBackground() {
        themePreferences.toggleAmbientBackground()
    }
    
    fun setVideoMode(enabled: Boolean) {
        themePreferences.setVideoMode(enabled)
    }
    
    fun toggleVideoMode() {
        themePreferences.toggleVideoMode()
    }
    
    fun setPlayerStyle(style: PlayerStyle) {
        themePreferences.setPlayerStyle(style)
    }
}
