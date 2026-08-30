package com.ivor.ivormusic.ui.theme

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import com.ivor.ivormusic.data.AppMode
import com.ivor.ivormusic.data.ThemePreferences
import com.ivor.ivormusic.data.PlayerStyle
import kotlinx.coroutines.flow.StateFlow

/**
 * ViewModel for managing theme and app settings state across the app.
 */
class ThemeViewModel(application: Application) : AndroidViewModel(application) {

    private val themePreferences = ThemePreferences(application)

    val themeMode: StateFlow<ThemeMode> = themePreferences.themeMode
    val amoledTheme: StateFlow<Boolean> = themePreferences.amoledTheme
    val colorPalette: StateFlow<String> = themePreferences.colorPalette
    val loadLocalSongs: StateFlow<Boolean> = themePreferences.loadLocalSongs
    val ambientBackground: StateFlow<Boolean> = themePreferences.ambientBackground
    val playerArtworkColors: StateFlow<Boolean> = themePreferences.playerArtworkColors
    val appMode: StateFlow<AppMode> = themePreferences.appMode
    val homeModeToggleEnabled: StateFlow<Boolean> = themePreferences.homeModeToggleEnabled
    val playerStyle: StateFlow<PlayerStyle> = themePreferences.playerStyle
    val saveVideoHistory: StateFlow<Boolean> = themePreferences.saveVideoHistory
    val saveMusicHistory: StateFlow<Boolean> = themePreferences.saveMusicHistory

    val liveDownloadUpdates: StateFlow<Boolean> = themePreferences.liveDownloadUpdates
    val livePlaybackUpdates: StateFlow<Boolean> = themePreferences.livePlaybackUpdates
    val timedCommentsEnabled: StateFlow<Boolean> = themePreferences.timedCommentsEnabled
    val shortsEnabled: StateFlow<Boolean> = themePreferences.shortsEnabled
    val shortsHiddenActions: StateFlow<Set<String>> = themePreferences.shortsHiddenActions
    val videoQualityWifi: StateFlow<String> = themePreferences.videoQualityWifi
    val videoQualityMobile: StateFlow<String> = themePreferences.videoQualityMobile
    val musicQualityWifi: StateFlow<String> = themePreferences.musicQualityWifi
    val musicQualityMobile: StateFlow<String> = themePreferences.musicQualityMobile
    val spotlightHome: StateFlow<Boolean> = themePreferences.spotlightHome
    val uiScale: StateFlow<Float> = themePreferences.uiScale
    val nonExpressiveNavigationBar: StateFlow<Boolean> =
        themePreferences.nonExpressiveNavigationBar
    val subscriptionSource: StateFlow<String> = themePreferences.subscriptionSource
    val subscribeTarget: StateFlow<String> = themePreferences.subscribeTarget
    val fastSubscriptionFeed: StateFlow<Boolean> = themePreferences.fastSubscriptionFeed
    val excludedFolders: StateFlow<Set<String>> = themePreferences.excludedFolders
    
    val autoLoadQueue: StateFlow<Boolean> = themePreferences.autoLoadQueue

    // Cache & Crossfade
    val cacheEnabled: StateFlow<Boolean> = themePreferences.cacheEnabled
    val maxCacheSizeMb: StateFlow<Long> = themePreferences.maxCacheSizeMb
    val crossfadeEnabled: StateFlow<Boolean> = themePreferences.crossfadeEnabled
    val crossfadeAuto: StateFlow<Boolean> = themePreferences.crossfadeAuto
    val crossfadeDurationMs: StateFlow<Int> = themePreferences.crossfadeDurationMs
    val normalizeVolume: StateFlow<Boolean> = themePreferences.normalizeVolume
    val rememberVideoBrightness: StateFlow<Boolean> = themePreferences.rememberVideoBrightness
    val hapticsLevel: StateFlow<String> = themePreferences.hapticsLevel
    val uploadNotificationsEnabled: StateFlow<Boolean> = themePreferences.uploadNotificationsEnabled

    val oemFixEnabled: StateFlow<Boolean> = themePreferences.oemFixEnabled
    val manualScanEnabled: StateFlow<Boolean> = themePreferences.manualScanEnabled
    val privateDownloadsEnabled: StateFlow<Boolean> = themePreferences.privateDownloadsEnabled
    val onboardingCompleted: StateFlow<Boolean> = themePreferences.onboardingCompleted
    val localOnlyMode: StateFlow<Boolean> = themePreferences.localOnlyMode
    
    val currentCacheSizeBytes: StateFlow<Long> = com.ivor.ivormusic.data.CacheManager.currentCacheSizeBytes

    fun setThemeMode(mode: ThemeMode) {
        themePreferences.setThemeMode(mode)
    }

    fun setAmoledTheme(enabled: Boolean) {
        themePreferences.setAmoledTheme(enabled)
    }

    fun setColorPalette(paletteId: String) {
        themePreferences.setColorPalette(paletteId)
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

    fun setPlayerArtworkColors(enabled: Boolean) {
        themePreferences.setPlayerArtworkColors(enabled)
    }
    
    fun toggleAmbientBackground() {
        themePreferences.toggleAmbientBackground()
    }
    
    fun setAppMode(mode: AppMode) {
        themePreferences.setAppMode(mode)
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

    fun setSaveMusicHistory(enabled: Boolean) {
        themePreferences.setSaveMusicHistory(enabled)
    }

    fun setLiveDownloadUpdates(enabled: Boolean) {
        themePreferences.setLiveDownloadUpdates(enabled)
    }

    fun setLivePlaybackUpdates(enabled: Boolean) {
        themePreferences.setLivePlaybackUpdates(enabled)
    }

    fun setTimedCommentsEnabled(enabled: Boolean) {
        themePreferences.setTimedCommentsEnabled(enabled)
    }

    fun setShortsEnabled(enabled: Boolean) {
        themePreferences.setShortsEnabled(enabled)
    }

    fun setShortsHiddenActions(hidden: Set<String>) {
        themePreferences.setShortsHiddenActions(hidden)
    }

    fun setVideoQualityWifi(quality: String) {
        themePreferences.setVideoQualityWifi(quality)
    }

    fun setVideoQualityMobile(quality: String) {
        themePreferences.setVideoQualityMobile(quality)
    }

    fun setMusicQualityWifi(quality: String) {
        themePreferences.setMusicQualityWifi(quality)
    }

    fun setMusicQualityMobile(quality: String) {
        themePreferences.setMusicQualityMobile(quality)
    }

    fun setSpotlightHome(enabled: Boolean) {
        themePreferences.setSpotlightHome(enabled)
    }

    fun setUiScale(scale: Float) {
        themePreferences.setUiScale(scale)
    }

    fun setNonExpressiveNavigationBar(enabled: Boolean) {
        themePreferences.setNonExpressiveNavigationBar(enabled)
    }

    fun setSubscriptionSource(source: String) {
        themePreferences.setSubscriptionSource(source)
    }

    fun setSubscribeTarget(target: String) {
        themePreferences.setSubscribeTarget(target)
    }

    fun setFastSubscriptionFeed(enabled: Boolean) {
        themePreferences.setFastSubscriptionFeed(enabled)
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

    fun setCrossfadeAuto(enabled: Boolean) {
        themePreferences.setCrossfadeAuto(enabled)
    }

    fun setNormalizeVolume(enabled: Boolean) {
        themePreferences.setNormalizeVolume(enabled)
    }

    fun setRememberVideoBrightness(enabled: Boolean) {
        themePreferences.setRememberVideoBrightness(enabled)
    }

    fun setHapticsLevel(value: String) {
        themePreferences.setHapticsLevel(value)
    }

    fun setUploadNotificationsEnabled(enabled: Boolean) {
        themePreferences.setUploadNotificationsEnabled(enabled)
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

    fun setPrivateDownloadsEnabled(enabled: Boolean) {
        themePreferences.setPrivateDownloadsEnabled(enabled)
    }

    fun setOnboardingCompleted(completed: Boolean) {
        themePreferences.setOnboardingCompleted(completed)
    }

    fun setLocalOnlyMode(enabled: Boolean) {
        themePreferences.setLocalOnlyMode(enabled)
    }
}
