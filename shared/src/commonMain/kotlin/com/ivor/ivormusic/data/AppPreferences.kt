package com.ivor.ivormusic.data

import com.ivor.ivormusic.domain.PlayerStyle
import com.ivor.ivormusic.domain.Song
import com.ivor.ivormusic.ui.theme.ThemeMode
import kotlinx.coroutines.flow.StateFlow

/**
 * Platform-independent preferences interface.
 * Android: backed by EncryptedSharedPreferences.
 * iOS: backed by NSUserDefaults / Keychain.
 */
interface AppPreferences {
    val themeMode: StateFlow<ThemeMode>
    val loadLocalSongs: StateFlow<Boolean>
    val ambientBackground: StateFlow<Boolean>
    val videoMode: StateFlow<Boolean>
    val playerStyle: StateFlow<PlayerStyle>
    val saveVideoHistory: StateFlow<Boolean>
    val excludedFolders: StateFlow<Set<String>>
    val cacheEnabled: StateFlow<Boolean>
    val maxCacheSizeMb: StateFlow<Long>
    val crossfadeEnabled: StateFlow<Boolean>
    val crossfadeDurationMs: StateFlow<Int>
    val oemFixEnabled: StateFlow<Boolean>
    val manualScanEnabled: StateFlow<Boolean>

    fun setThemeMode(mode: ThemeMode)
    fun setLoadLocalSongs(load: Boolean)
    fun toggleLoadLocalSongs()
    fun setAmbientBackground(enabled: Boolean)
    fun toggleAmbientBackground()
    fun setVideoMode(enabled: Boolean)
    fun toggleVideoMode()
    fun setPlayerStyle(style: PlayerStyle)
    fun setSaveVideoHistory(enabled: Boolean)
    fun setExcludedFolders(folders: Set<String>)
    fun addExcludedFolder(folderPath: String)
    fun removeExcludedFolder(folderPath: String)
    fun setCacheEnabled(enabled: Boolean)
    fun setMaxCacheSizeMb(sizeMb: Long)
    fun setCrossfadeEnabled(enabled: Boolean)
    fun toggleCrossfadeEnabled()
    fun setCrossfadeDuration(durationMs: Int)
    fun setOemFixEnabled(enabled: Boolean)
    fun setManualScanEnabled(enabled: Boolean)
    fun saveLastPlayedSong(song: Song)
    fun getLastPlayedSong(): Song?
    fun clearLastPlayedSong()
}
