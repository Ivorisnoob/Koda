package com.ivor.ivormusic.ui.theme

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
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
    val saveVideoHistory: StateFlow<Boolean> = themePreferences.saveVideoHistory
    val excludedFolders: StateFlow<Set<String>> = themePreferences.excludedFolders
    
    // Cache & Crossfade
    val cacheEnabled: StateFlow<Boolean> = themePreferences.cacheEnabled
    val maxCacheSizeMb: StateFlow<Long> = themePreferences.maxCacheSizeMb
    val crossfadeEnabled: StateFlow<Boolean> = themePreferences.crossfadeEnabled
    val crossfadeDurationMs: StateFlow<Int> = themePreferences.crossfadeDurationMs
    
    val oemFixEnabled: StateFlow<Boolean> = themePreferences.oemFixEnabled
    val manualScanEnabled: StateFlow<Boolean> = themePreferences.manualScanEnabled
    
    val currentCacheSizeBytes: StateFlow<Long> = com.ivor.ivormusic.data.CacheManager.currentCacheSizeBytes

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
    
    fun setSaveVideoHistory(enabled: Boolean) {
        themePreferences.setSaveVideoHistory(enabled)
    }
    
    fun toggleSaveVideoHistory() {
        themePreferences.toggleSaveVideoHistory()
    }
    
    fun addExcludedFolder(folderPath: String) {
        themePreferences.addExcludedFolder(folderPath)
    }
    
    fun removeExcludedFolder(folderPath: String) {
        themePreferences.removeExcludedFolder(folderPath)
    }
    
    fun setExcludedFolders(folders: Set<String>) {
        themePreferences.setExcludedFolders(folders)
    }
    
    // --- Cache Settings ---
    fun setCacheEnabled(enabled: Boolean) {
        themePreferences.setCacheEnabled(enabled)
    }
    
    fun setMaxCacheSizeMb(sizeMb: Long) {
        themePreferences.setMaxCacheSizeMb(sizeMb)
    }
    
    fun clearCacheAction() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            com.ivor.ivormusic.data.CacheManager.clearCache()
        }
    }

    // --- Crossfade Settings ---
    fun setCrossfadeEnabled(enabled: Boolean) {
        themePreferences.setCrossfadeEnabled(enabled)
    }
    
    fun toggleCrossfadeEnabled() {
        themePreferences.toggleCrossfadeEnabled()
    }
    
    fun setCrossfadeDuration(durationMs: Int) {
        themePreferences.setCrossfadeDuration(durationMs)
    }

    fun setOemFixEnabled(enabled: Boolean) {
        themePreferences.setOemFixEnabled(enabled)
    }

    fun setManualScanEnabled(enabled: Boolean) {
        themePreferences.setManualScanEnabled(enabled)
    }
}
