package com.ivor.ivormusic.ui.theme

import androidx.lifecycle.ViewModel
import com.ivor.ivormusic.data.AppPreferences
import com.ivor.ivormusic.domain.PlayerStyle
import kotlinx.coroutines.flow.StateFlow

class ThemeViewModel(private val prefs: AppPreferences) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = prefs.themeMode
    val loadLocalSongs: StateFlow<Boolean> = prefs.loadLocalSongs
    val ambientBackground: StateFlow<Boolean> = prefs.ambientBackground
    val videoMode: StateFlow<Boolean> = prefs.videoMode
    val playerStyle: StateFlow<PlayerStyle> = prefs.playerStyle
    val saveVideoHistory: StateFlow<Boolean> = prefs.saveVideoHistory
    val excludedFolders: StateFlow<Set<String>> = prefs.excludedFolders
    val cacheEnabled: StateFlow<Boolean> = prefs.cacheEnabled
    val maxCacheSizeMb: StateFlow<Long> = prefs.maxCacheSizeMb
    val crossfadeEnabled: StateFlow<Boolean> = prefs.crossfadeEnabled
    val crossfadeDurationMs: StateFlow<Int> = prefs.crossfadeDurationMs
    val oemFixEnabled: StateFlow<Boolean> = prefs.oemFixEnabled
    val manualScanEnabled: StateFlow<Boolean> = prefs.manualScanEnabled

    fun setThemeMode(mode: ThemeMode) = prefs.setThemeMode(mode)
    fun toggleLoadLocalSongs() = prefs.toggleLoadLocalSongs()
    fun toggleAmbientBackground() = prefs.toggleAmbientBackground()
    fun toggleVideoMode() = prefs.toggleVideoMode()
    fun setPlayerStyle(style: PlayerStyle) = prefs.setPlayerStyle(style)
    fun setSaveVideoHistory(enabled: Boolean) = prefs.setSaveVideoHistory(enabled)
    fun addExcludedFolder(path: String) = prefs.addExcludedFolder(path)
    fun removeExcludedFolder(path: String) = prefs.removeExcludedFolder(path)
    fun setCacheEnabled(enabled: Boolean) = prefs.setCacheEnabled(enabled)
    fun setMaxCacheSizeMb(mb: Long) = prefs.setMaxCacheSizeMb(mb)
    fun toggleCrossfadeEnabled() = prefs.toggleCrossfadeEnabled()
    fun setCrossfadeDuration(ms: Int) = prefs.setCrossfadeDuration(ms)
    fun setOemFixEnabled(enabled: Boolean) = prefs.setOemFixEnabled(enabled)
    fun setManualScanEnabled(enabled: Boolean) = prefs.setManualScanEnabled(enabled)
}
