package com.ivor.ivormusic.data

import android.content.Context
import android.content.SharedPreferences
import com.ivor.ivormusic.ui.theme.ThemeMode
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

    private val _themeMode = MutableStateFlow(getThemeModePreference())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _loadLocalSongs = MutableStateFlow(getLoadLocalSongsPreference())
    val loadLocalSongs: StateFlow<Boolean> = _loadLocalSongs.asStateFlow()
    
    private val _ambientBackground = MutableStateFlow(getAmbientBackgroundPreference())
    val ambientBackground: StateFlow<Boolean> = _ambientBackground.asStateFlow()
    
    private val _videoMode = MutableStateFlow(getVideoModePreference())
    val videoMode: StateFlow<Boolean> = _videoMode.asStateFlow()
    
    private val _playerStyle = MutableStateFlow(getPlayerStylePreference())
    val playerStyle: StateFlow<PlayerStyle> = _playerStyle.asStateFlow()
    
    private val _saveVideoHistory = MutableStateFlow(getSaveVideoHistoryPreference())
    val saveVideoHistory: StateFlow<Boolean> = _saveVideoHistory.asStateFlow()
    
    private val _excludedFolders = MutableStateFlow(getExcludedFoldersPreference())
    val excludedFolders: StateFlow<Set<String>> = _excludedFolders.asStateFlow()
    
    // Cache Settings
    private val _cacheEnabled = MutableStateFlow(getCacheEnabledPreference())
    val cacheEnabled: StateFlow<Boolean> = _cacheEnabled.asStateFlow()
    
    private val _maxCacheSizeMb = MutableStateFlow(getMaxCacheSizeMbPreference())
    val maxCacheSizeMb: StateFlow<Long> = _maxCacheSizeMb.asStateFlow()
    
    // Crossfade Settings
    private val _crossfadeEnabled = MutableStateFlow(getCrossfadeEnabledPreference())
    val crossfadeEnabled: StateFlow<Boolean> = _crossfadeEnabled.asStateFlow()
    
    private val _crossfadeDurationMs = MutableStateFlow(getCrossfadeDurationPreference())
    val crossfadeDurationMs: StateFlow<Int> = _crossfadeDurationMs.asStateFlow()

    private val _oemFixEnabled = MutableStateFlow(getOemFixEnabledPreference())
    val oemFixEnabled: StateFlow<Boolean> = _oemFixEnabled.asStateFlow()

    private val _manualScanEnabled = MutableStateFlow(getManualScanEnabledPreference())
    val manualScanEnabled: StateFlow<Boolean> = _manualScanEnabled.asStateFlow()

    companion object {
        private const val PREFS_NAME = "ivor_music_theme_prefs"
        private const val KEY_THEME_MODE = "theme_mode_enum"
        private const val KEY_OLD_DARK_MODE = "dark_mode" // For migration
        private const val KEY_LOAD_LOCAL_SONGS = "load_local_songs"
        private const val KEY_AMBIENT_BACKGROUND = "ambient_background"
        private const val KEY_VIDEO_MODE = "video_mode"
        private const val KEY_PLAYER_STYLE = "player_style"
        private const val KEY_SAVE_VIDEO_HISTORY = "save_video_history"
        private const val KEY_EXCLUDED_FOLDERS = "excluded_folders"
        private const val KEY_CACHE_ENABLED = "cache_enabled"
        private const val KEY_MAX_CACHE_SIZE_MB = "max_cache_size_mb"
        private const val KEY_CROSSFADE_ENABLED = "crossfade_enabled"
        private const val KEY_CROSSFADE_DURATION = "crossfade_duration"
        private const val KEY_OEM_FIX_ENABLED = "oem_fix_enabled"
        private const val KEY_MANUAL_SCAN_ENABLED = "manual_scan_enabled"
        private const val KEY_LAST_SONG_ID = "last_song_id"
        private const val KEY_LAST_SONG_TITLE = "last_song_title"
        private const val KEY_LAST_SONG_ARTIST = "last_song_artist"
        private const val KEY_LAST_SONG_ALBUM = "last_song_album"
        private const val KEY_LAST_SONG_ARTWORK = "last_song_artwork"
        private const val KEY_LAST_SONG_DURATION = "last_song_duration"
    }
    
    // --- Last Played Song ---
    
    /**
     * Save the last played song for restoration.
     */
    fun saveLastPlayedSong(song: Song) {
        prefs.edit()
            .putString(KEY_LAST_SONG_ID, song.id)
            .putString(KEY_LAST_SONG_TITLE, song.title)
            .putString(KEY_LAST_SONG_ARTIST, song.artist)
            .putString(KEY_LAST_SONG_ALBUM, song.album)
            .putString(KEY_LAST_SONG_ARTWORK, song.thumbnailUrl ?: song.albumArtUri?.toString() ?: "")
            .putLong(KEY_LAST_SONG_DURATION, song.duration)
            .apply()
    }
    
    /**
     * Get the last played song, or null if none.
     */
    fun getLastPlayedSong(): Song? {
        val id = prefs.getString(KEY_LAST_SONG_ID, null) ?: return null
        val artwork = prefs.getString(KEY_LAST_SONG_ARTWORK, "") ?: ""
        return Song(
            id = id,
            title = prefs.getString(KEY_LAST_SONG_TITLE, "Unknown") ?: "Unknown",
            artist = prefs.getString(KEY_LAST_SONG_ARTIST, "Unknown Artist") ?: "Unknown Artist",
            album = prefs.getString(KEY_LAST_SONG_ALBUM, "") ?: "",
            thumbnailUrl = artwork.ifEmpty { null },
            duration = prefs.getLong(KEY_LAST_SONG_DURATION, 0L),
            source = SongSource.YOUTUBE
        )
    }
    
    /**
     * Clear the last played song.
     */
    fun clearLastPlayedSong() {
        prefs.edit()
            .remove(KEY_LAST_SONG_ID)
            .remove(KEY_LAST_SONG_TITLE)
            .remove(KEY_LAST_SONG_ARTIST)
            .remove(KEY_LAST_SONG_ALBUM)
            .remove(KEY_LAST_SONG_ARTWORK)
            .remove(KEY_LAST_SONG_DURATION)
            .apply()
    }

    private fun getThemeModePreference(): ThemeMode {
        if (prefs.contains(KEY_THEME_MODE)) {
            val modeName = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)
            return try {
                ThemeMode.valueOf(modeName ?: ThemeMode.SYSTEM.name)
            } catch (e: IllegalArgumentException) {
                ThemeMode.SYSTEM
            }
        }
        
        if (prefs.contains(KEY_OLD_DARK_MODE)) {
            val oldDarkMode = prefs.getBoolean(KEY_OLD_DARK_MODE, true)
            return if (oldDarkMode) ThemeMode.DARK else ThemeMode.LIGHT
        }
        
        return ThemeMode.SYSTEM
    }

    /**
     * Get the stored load local songs preference. Defaults to true.
     */
    private fun getLoadLocalSongsPreference(): Boolean {
        return prefs.getBoolean(KEY_LOAD_LOCAL_SONGS, true)
    }
    
    /**
     * Get the stored ambient background preference. Defaults to true.
     */
    private fun getAmbientBackgroundPreference(): Boolean {
        return prefs.getBoolean(KEY_AMBIENT_BACKGROUND, true)
    }

    /**
     * Save theme mode preference and update the flow.
     */
    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _themeMode.value = mode
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
    
    /**
     * Save ambient background preference and update the flow.
     */
    fun setAmbientBackground(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AMBIENT_BACKGROUND, enabled).apply()
        _ambientBackground.value = enabled
    }
    
    /**
     * Toggle ambient background setting.
     */
    fun toggleAmbientBackground() {
        setAmbientBackground(!_ambientBackground.value)
    }
    
    /**
     * Get the stored video mode preference. Defaults to false (Music mode).
     */
    private fun getVideoModePreference(): Boolean {
        return prefs.getBoolean(KEY_VIDEO_MODE, false)
    }
    
    /**
     * Save video mode preference and update the flow.
     */
    fun setVideoMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VIDEO_MODE, enabled).apply()
        _videoMode.value = enabled
    }
    
    /**
     * Toggle video mode setting.
     */
    fun toggleVideoMode() {
        setVideoMode(!_videoMode.value)
    }
    
    /**
     * Get the stored player style preference. Defaults to CLASSIC.
     */
    private fun getPlayerStylePreference(): PlayerStyle {
        val styleName = prefs.getString(KEY_PLAYER_STYLE, PlayerStyle.CLASSIC.name)
        return try {
            PlayerStyle.valueOf(styleName ?: PlayerStyle.CLASSIC.name)
        } catch (e: IllegalArgumentException) {
            PlayerStyle.CLASSIC
        }
    }
    
    /**
     * Save player style preference and update the flow.
     */
    fun setPlayerStyle(style: PlayerStyle) {
        prefs.edit().putString(KEY_PLAYER_STYLE, style.name).apply()
        _playerStyle.value = style
    }
    
    /**
     * Get the stored save video history preference. Defaults to true (save history).
     */
    private fun getSaveVideoHistoryPreference(): Boolean {
        return prefs.getBoolean(KEY_SAVE_VIDEO_HISTORY, true)
    }
    
    /**
     * Save video history preference and update the flow.
     */
    fun setSaveVideoHistory(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SAVE_VIDEO_HISTORY, enabled).apply()
        _saveVideoHistory.value = enabled
    }
    
    /**
     * Toggle save video history setting.
     */
    fun toggleSaveVideoHistory() {
        setSaveVideoHistory(!_saveVideoHistory.value)
    }
    
    /**
     * Get the stored excluded folders preference. Defaults to empty set.
     */
    private fun getExcludedFoldersPreference(): Set<String> {
        return prefs.getStringSet(KEY_EXCLUDED_FOLDERS, emptySet()) ?: emptySet()
    }
    
    /**
     * Save excluded folders preference and update the flow.
     */
    fun setExcludedFolders(folders: Set<String>) {
        prefs.edit().putStringSet(KEY_EXCLUDED_FOLDERS, folders).apply()
        _excludedFolders.value = folders
    }
    
    /**
     * Add a folder to the excluded list.
     */
    fun addExcludedFolder(folderPath: String) {
        val current = _excludedFolders.value.toMutableSet()
        current.add(folderPath)
        setExcludedFolders(current)
    }
    
    fun removeExcludedFolder(folderPath: String) {
        val current = _excludedFolders.value.toMutableSet()
        current.remove(folderPath)
        setExcludedFolders(current)
    }
    
    // --- Cache Settings ---
    
    private fun getCacheEnabledPreference(): Boolean {
        return prefs.getBoolean(KEY_CACHE_ENABLED, true)
    }
    
    fun setCacheEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CACHE_ENABLED, enabled).apply()
        _cacheEnabled.value = enabled
    }
    
    private fun getMaxCacheSizeMbPreference(): Long {
        return prefs.getLong(KEY_MAX_CACHE_SIZE_MB, 512L) // Default 512MB
    }
    
    fun setMaxCacheSizeMb(sizeMb: Long) {
        prefs.edit().putLong(KEY_MAX_CACHE_SIZE_MB, sizeMb).apply()
        _maxCacheSizeMb.value = sizeMb
    }
    
    // --- Crossfade Settings ---
    
    private fun getCrossfadeEnabledPreference(): Boolean {
        return prefs.getBoolean(KEY_CROSSFADE_ENABLED, true)
    }
    
    fun setCrossfadeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CROSSFADE_ENABLED, enabled).apply()
        _crossfadeEnabled.value = enabled
    }
    
    fun toggleCrossfadeEnabled() {
        setCrossfadeEnabled(!_crossfadeEnabled.value)
    }
    
    private fun getCrossfadeDurationPreference(): Int {
        return prefs.getInt(KEY_CROSSFADE_DURATION, 3000) // Default 3000ms
    }
    
    fun setCrossfadeDuration(durationMs: Int) {
        prefs.edit().putInt(KEY_CROSSFADE_DURATION, durationMs).apply()
        _crossfadeDurationMs.value = durationMs
    }

    private fun getOemFixEnabledPreference(): Boolean {
        return prefs.getBoolean(KEY_OEM_FIX_ENABLED, false)
    }

    fun setOemFixEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_OEM_FIX_ENABLED, enabled).apply()
        _oemFixEnabled.value = enabled
    }

    private fun getManualScanEnabledPreference(): Boolean {
        return prefs.getBoolean(KEY_MANUAL_SCAN_ENABLED, false)
    }

    fun setManualScanEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MANUAL_SCAN_ENABLED, enabled).apply()
        _manualScanEnabled.value = enabled
    }
}

/**
 * Player UI Style options
 */
enum class PlayerStyle {
    /** Classic button-based player with play/pause/next/previous controls */
    CLASSIC,
    /** Gesture-based carousel player with swipe navigation */
    GESTURE
}
