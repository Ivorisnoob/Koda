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

    companion object {
        private const val PREFS_NAME = "ivor_music_theme_prefs"
        private const val KEY_THEME_MODE = "theme_mode_enum"
        private const val KEY_OLD_DARK_MODE = "dark_mode" // For migration
        private const val KEY_LOAD_LOCAL_SONGS = "load_local_songs"
        private const val KEY_AMBIENT_BACKGROUND = "ambient_background"
        private const val KEY_VIDEO_MODE = "video_mode"
        private const val KEY_PLAYER_STYLE = "player_style"
    }

    /**
     * Get the stored theme mode preference. Defaults to SYSTEM.
     * Migrates from old boolean preference if needed.
     */
    private fun getThemeModePreference(): ThemeMode {
        // Check if new key exists
        if (prefs.contains(KEY_THEME_MODE)) {
            val modeName = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)
            return try {
                ThemeMode.valueOf(modeName ?: ThemeMode.SYSTEM.name)
            } catch (e: IllegalArgumentException) {
                ThemeMode.SYSTEM
            }
        }
        
        // Callback to old preference for migration
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
