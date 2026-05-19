package com.ivor.ivormusic.data

import android.content.Context
import android.content.SharedPreferences
import com.ivor.ivormusic.domain.PlayerStyle
import com.ivor.ivormusic.domain.Song
import com.ivor.ivormusic.domain.SongSource
import com.ivor.ivormusic.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AndroidAppPreferences(context: Context) : AppPreferences {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(loadThemeMode())
    override val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _loadLocalSongs = MutableStateFlow(prefs.getBoolean(KEY_LOAD_LOCAL_SONGS, true))
    override val loadLocalSongs: StateFlow<Boolean> = _loadLocalSongs.asStateFlow()

    private val _ambientBackground = MutableStateFlow(prefs.getBoolean(KEY_AMBIENT_BACKGROUND, true))
    override val ambientBackground: StateFlow<Boolean> = _ambientBackground.asStateFlow()

    private val _videoMode = MutableStateFlow(prefs.getBoolean(KEY_VIDEO_MODE, false))
    override val videoMode: StateFlow<Boolean> = _videoMode.asStateFlow()

    private val _playerStyle = MutableStateFlow(loadPlayerStyle())
    override val playerStyle: StateFlow<PlayerStyle> = _playerStyle.asStateFlow()

    private val _saveVideoHistory = MutableStateFlow(prefs.getBoolean(KEY_SAVE_VIDEO_HISTORY, true))
    override val saveVideoHistory: StateFlow<Boolean> = _saveVideoHistory.asStateFlow()

    private val _excludedFolders = MutableStateFlow(prefs.getStringSet(KEY_EXCLUDED_FOLDERS, emptySet()) ?: emptySet())
    override val excludedFolders: StateFlow<Set<String>> = _excludedFolders.asStateFlow()

    private val _cacheEnabled = MutableStateFlow(prefs.getBoolean(KEY_CACHE_ENABLED, true))
    override val cacheEnabled: StateFlow<Boolean> = _cacheEnabled.asStateFlow()

    private val _maxCacheSizeMb = MutableStateFlow(prefs.getLong(KEY_MAX_CACHE_SIZE_MB, 512L))
    override val maxCacheSizeMb: StateFlow<Long> = _maxCacheSizeMb.asStateFlow()

    private val _crossfadeEnabled = MutableStateFlow(prefs.getBoolean(KEY_CROSSFADE_ENABLED, true))
    override val crossfadeEnabled: StateFlow<Boolean> = _crossfadeEnabled.asStateFlow()

    private val _crossfadeDurationMs = MutableStateFlow(prefs.getInt(KEY_CROSSFADE_DURATION, 3000))
    override val crossfadeDurationMs: StateFlow<Int> = _crossfadeDurationMs.asStateFlow()

    private val _oemFixEnabled = MutableStateFlow(prefs.getBoolean(KEY_OEM_FIX_ENABLED, false))
    override val oemFixEnabled: StateFlow<Boolean> = _oemFixEnabled.asStateFlow()

    private val _manualScanEnabled = MutableStateFlow(prefs.getBoolean(KEY_MANUAL_SCAN_ENABLED, false))
    override val manualScanEnabled: StateFlow<Boolean> = _manualScanEnabled.asStateFlow()

    private fun loadThemeMode(): ThemeMode {
        val name = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)
        return try { ThemeMode.valueOf(name ?: ThemeMode.SYSTEM.name) } catch (_: Exception) { ThemeMode.SYSTEM }
    }

    private fun loadPlayerStyle(): PlayerStyle {
        val name = prefs.getString(KEY_PLAYER_STYLE, PlayerStyle.CLASSIC.name)
        return try { PlayerStyle.valueOf(name ?: PlayerStyle.CLASSIC.name) } catch (_: Exception) { PlayerStyle.CLASSIC }
    }

    override fun setThemeMode(mode: ThemeMode) { prefs.edit().putString(KEY_THEME_MODE, mode.name).apply(); _themeMode.value = mode }
    override fun setLoadLocalSongs(load: Boolean) { prefs.edit().putBoolean(KEY_LOAD_LOCAL_SONGS, load).apply(); _loadLocalSongs.value = load }
    override fun toggleLoadLocalSongs() = setLoadLocalSongs(!_loadLocalSongs.value)
    override fun setAmbientBackground(enabled: Boolean) { prefs.edit().putBoolean(KEY_AMBIENT_BACKGROUND, enabled).apply(); _ambientBackground.value = enabled }
    override fun toggleAmbientBackground() = setAmbientBackground(!_ambientBackground.value)
    override fun setVideoMode(enabled: Boolean) { prefs.edit().putBoolean(KEY_VIDEO_MODE, enabled).apply(); _videoMode.value = enabled }
    override fun toggleVideoMode() = setVideoMode(!_videoMode.value)
    override fun setPlayerStyle(style: PlayerStyle) { prefs.edit().putString(KEY_PLAYER_STYLE, style.name).apply(); _playerStyle.value = style }
    override fun setSaveVideoHistory(enabled: Boolean) { prefs.edit().putBoolean(KEY_SAVE_VIDEO_HISTORY, enabled).apply(); _saveVideoHistory.value = enabled }
    override fun setExcludedFolders(folders: Set<String>) { prefs.edit().putStringSet(KEY_EXCLUDED_FOLDERS, folders).apply(); _excludedFolders.value = folders }
    override fun addExcludedFolder(folderPath: String) { setExcludedFolders(_excludedFolders.value + folderPath) }
    override fun removeExcludedFolder(folderPath: String) { setExcludedFolders(_excludedFolders.value - folderPath) }
    override fun setCacheEnabled(enabled: Boolean) { prefs.edit().putBoolean(KEY_CACHE_ENABLED, enabled).apply(); _cacheEnabled.value = enabled }
    override fun setMaxCacheSizeMb(sizeMb: Long) { prefs.edit().putLong(KEY_MAX_CACHE_SIZE_MB, sizeMb).apply(); _maxCacheSizeMb.value = sizeMb }
    override fun setCrossfadeEnabled(enabled: Boolean) { prefs.edit().putBoolean(KEY_CROSSFADE_ENABLED, enabled).apply(); _crossfadeEnabled.value = enabled }
    override fun toggleCrossfadeEnabled() = setCrossfadeEnabled(!_crossfadeEnabled.value)
    override fun setCrossfadeDuration(durationMs: Int) { prefs.edit().putInt(KEY_CROSSFADE_DURATION, durationMs).apply(); _crossfadeDurationMs.value = durationMs }
    override fun setOemFixEnabled(enabled: Boolean) { prefs.edit().putBoolean(KEY_OEM_FIX_ENABLED, enabled).apply(); _oemFixEnabled.value = enabled }
    override fun setManualScanEnabled(enabled: Boolean) { prefs.edit().putBoolean(KEY_MANUAL_SCAN_ENABLED, enabled).apply(); _manualScanEnabled.value = enabled }

    override fun saveLastPlayedSong(song: Song) {
        prefs.edit()
            .putString(KEY_LAST_SONG_ID, song.id)
            .putString(KEY_LAST_SONG_TITLE, song.title)
            .putString(KEY_LAST_SONG_ARTIST, song.artist)
            .putString(KEY_LAST_SONG_ALBUM, song.album)
            .putString(KEY_LAST_SONG_ARTWORK, song.artworkUrl ?: "")
            .putLong(KEY_LAST_SONG_DURATION, song.duration)
            .apply()
    }

    override fun getLastPlayedSong(): Song? {
        val id = prefs.getString(KEY_LAST_SONG_ID, null) ?: return null
        return Song(
            id = id,
            title = prefs.getString(KEY_LAST_SONG_TITLE, "Unknown") ?: "Unknown",
            artist = prefs.getString(KEY_LAST_SONG_ARTIST, "Unknown Artist") ?: "Unknown Artist",
            album = prefs.getString(KEY_LAST_SONG_ALBUM, "") ?: "",
            thumbnailUrl = prefs.getString(KEY_LAST_SONG_ARTWORK, "")?.ifEmpty { null },
            duration = prefs.getLong(KEY_LAST_SONG_DURATION, 0L),
            source = SongSource.YOUTUBE
        )
    }

    override fun clearLastPlayedSong() {
        prefs.edit().remove(KEY_LAST_SONG_ID).remove(KEY_LAST_SONG_TITLE)
            .remove(KEY_LAST_SONG_ARTIST).remove(KEY_LAST_SONG_ALBUM)
            .remove(KEY_LAST_SONG_ARTWORK).remove(KEY_LAST_SONG_DURATION).apply()
    }

    companion object {
        private const val PREFS_NAME = "ivor_music_theme_prefs"
        private const val KEY_THEME_MODE = "theme_mode_enum"
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
}
