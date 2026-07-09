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
    val homeModeToggleEnabled: StateFlow<Boolean> = themePreferences.homeModeToggleEnabled
    val playerStyle: StateFlow<PlayerStyle> = themePreferences.playerStyle
    val saveVideoHistory: StateFlow<Boolean> = themePreferences.saveVideoHistory
    val timedCommentsEnabled: StateFlow<Boolean> = themePreferences.timedCommentsEnabled
    val defaultVideoQuality: StateFlow<String> = themePreferences.defaultVideoQuality
    val excludedFolders: StateFlow<Set<String>> = themePreferences.excludedFolders
    
    val autoLoadQueue: StateFlow<Boolean> = themePreferences.autoLoadQueue

    // Cache & Crossfade
    val cacheEnabled: StateFlow<Boolean> = themePreferences.cacheEnabled
    val maxCacheSizeMb: StateFlow<Long> = themePreferences.maxCacheSizeMb
    val crossfadeEnabled: StateFlow<Boolean> = themePreferences.crossfadeEnabled
    val crossfadeDurationMs: StateFlow<Int> = themePreferences.crossfadeDurationMs
    
    val oemFixEnabled: StateFlow<Boolean> = themePreferences.oemFixEnabled
    val manualScanEnabled: StateFlow<Boolean> = themePreferences.manualScanEnabled
    val onboardingCompleted: StateFlow<Boolean> = themePreferences.onboardingCompleted
    
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

    fun setHomeModeToggleEnabled(enabled: Boolean) {
        themePreferences.setHomeModeToggleEnabled(enabled)
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

    fun setTimedCommentsEnabled(enabled: Boolean) {
        themePreferences.setTimedCommentsEnabled(enabled)
    }

    fun setDefaultVideoQuality(quality: String) {
        themePreferences.setDefaultVideoQuality(quality)
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

    fun setAutoLoadQueue(enabled: Boolean) {
        themePreferences.setAutoLoadQueue(enabled)
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

    fun setOnboardingCompleted(completed: Boolean) {
        themePreferences.setOnboardingCompleted(completed)
    }
}
