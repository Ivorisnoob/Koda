package com.ivor.ivormusic.data

import com.ivor.ivormusic.domain.PlayerStyle
import com.ivor.ivormusic.domain.Song
import com.ivor.ivormusic.domain.SongSource
import com.ivor.ivormusic.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.prefs.Preferences

class DesktopAppPreferences : AppPreferences {

    private val prefs: Preferences = Preferences.userRoot().node("com/ivor/koda/prefs")

    private val _themeMode = MutableStateFlow(loadThemeMode())
    override val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _loadLocalSongs = MutableStateFlow(prefs.getBoolean("load_local_songs", true))
    override val loadLocalSongs: StateFlow<Boolean> = _loadLocalSongs.asStateFlow()

    private val _ambientBackground = MutableStateFlow(prefs.getBoolean("ambient_background", true))
    override val ambientBackground: StateFlow<Boolean> = _ambientBackground.asStateFlow()

    private val _videoMode = MutableStateFlow(prefs.getBoolean("video_mode", false))
    override val videoMode: StateFlow<Boolean> = _videoMode.asStateFlow()

    private val _playerStyle = MutableStateFlow(loadPlayerStyle())
    override val playerStyle: StateFlow<PlayerStyle> = _playerStyle.asStateFlow()

    private val _saveVideoHistory = MutableStateFlow(prefs.getBoolean("save_video_history", true))
    override val saveVideoHistory: StateFlow<Boolean> = _saveVideoHistory.asStateFlow()

    private val _excludedFolders = MutableStateFlow(
        prefs.get("excluded_folders", "").split("|").filter { it.isNotEmpty() }.toSet()
    )
    override val excludedFolders: StateFlow<Set<String>> = _excludedFolders.asStateFlow()

    private val _cacheEnabled = MutableStateFlow(prefs.getBoolean("cache_enabled", true))
    override val cacheEnabled: StateFlow<Boolean> = _cacheEnabled.asStateFlow()

    private val _maxCacheSizeMb = MutableStateFlow(prefs.getLong("max_cache_size_mb", 512L))
    override val maxCacheSizeMb: StateFlow<Long> = _maxCacheSizeMb.asStateFlow()

    private val _crossfadeEnabled = MutableStateFlow(prefs.getBoolean("crossfade_enabled", true))
    override val crossfadeEnabled: StateFlow<Boolean> = _crossfadeEnabled.asStateFlow()

    private val _crossfadeDurationMs = MutableStateFlow(prefs.getInt("crossfade_duration", 3000))
    override val crossfadeDurationMs: StateFlow<Int> = _crossfadeDurationMs.asStateFlow()

    private val _oemFixEnabled = MutableStateFlow(prefs.getBoolean("oem_fix_enabled", false))
    override val oemFixEnabled: StateFlow<Boolean> = _oemFixEnabled.asStateFlow()

    private val _manualScanEnabled = MutableStateFlow(prefs.getBoolean("manual_scan_enabled", false))
    override val manualScanEnabled: StateFlow<Boolean> = _manualScanEnabled.asStateFlow()

    private fun loadThemeMode(): ThemeMode {
        val name = prefs.get("theme_mode", ThemeMode.SYSTEM.name)
        return try { ThemeMode.valueOf(name) } catch (_: Exception) { ThemeMode.SYSTEM }
    }

    private fun loadPlayerStyle(): PlayerStyle {
        val name = prefs.get("player_style", PlayerStyle.CLASSIC.name)
        return try { PlayerStyle.valueOf(name) } catch (_: Exception) { PlayerStyle.CLASSIC }
    }

    override fun setThemeMode(mode: ThemeMode) { prefs.put("theme_mode", mode.name); _themeMode.value = mode }
    override fun setLoadLocalSongs(load: Boolean) { prefs.putBoolean("load_local_songs", load); _loadLocalSongs.value = load }
    override fun toggleLoadLocalSongs() = setLoadLocalSongs(!_loadLocalSongs.value)
    override fun setAmbientBackground(enabled: Boolean) { prefs.putBoolean("ambient_background", enabled); _ambientBackground.value = enabled }
    override fun toggleAmbientBackground() = setAmbientBackground(!_ambientBackground.value)
    override fun setVideoMode(enabled: Boolean) { prefs.putBoolean("video_mode", enabled); _videoMode.value = enabled }
    override fun toggleVideoMode() = setVideoMode(!_videoMode.value)
    override fun setPlayerStyle(style: PlayerStyle) { prefs.put("player_style", style.name); _playerStyle.value = style }
    override fun setSaveVideoHistory(enabled: Boolean) { prefs.putBoolean("save_video_history", enabled); _saveVideoHistory.value = enabled }
    override fun setExcludedFolders(folders: Set<String>) {
        prefs.put("excluded_folders", folders.joinToString("|"))
        _excludedFolders.value = folders
    }
    override fun addExcludedFolder(folderPath: String) = setExcludedFolders(_excludedFolders.value + folderPath)
    override fun removeExcludedFolder(folderPath: String) = setExcludedFolders(_excludedFolders.value - folderPath)
    override fun setCacheEnabled(enabled: Boolean) { prefs.putBoolean("cache_enabled", enabled); _cacheEnabled.value = enabled }
    override fun setMaxCacheSizeMb(sizeMb: Long) { prefs.putLong("max_cache_size_mb", sizeMb); _maxCacheSizeMb.value = sizeMb }
    override fun setCrossfadeEnabled(enabled: Boolean) { prefs.putBoolean("crossfade_enabled", enabled); _crossfadeEnabled.value = enabled }
    override fun toggleCrossfadeEnabled() = setCrossfadeEnabled(!_crossfadeEnabled.value)
    override fun setCrossfadeDuration(durationMs: Int) { prefs.putInt("crossfade_duration", durationMs); _crossfadeDurationMs.value = durationMs }
    override fun setOemFixEnabled(enabled: Boolean) { prefs.putBoolean("oem_fix_enabled", enabled); _oemFixEnabled.value = enabled }
    override fun setManualScanEnabled(enabled: Boolean) { prefs.putBoolean("manual_scan_enabled", enabled); _manualScanEnabled.value = enabled }

    override fun saveLastPlayedSong(song: Song) {
        prefs.put("last_song_id", song.id)
        prefs.put("last_song_title", song.title)
        prefs.put("last_song_artist", song.artist)
        prefs.put("last_song_album", song.album)
        prefs.put("last_song_artwork", song.artworkUrl ?: "")
        prefs.putLong("last_song_duration", song.duration)
    }

    override fun getLastPlayedSong(): Song? {
        val id = prefs.get("last_song_id", null) ?: return null
        return Song(
            id = id,
            title = prefs.get("last_song_title", "Unknown"),
            artist = prefs.get("last_song_artist", "Unknown Artist"),
            album = prefs.get("last_song_album", ""),
            thumbnailUrl = prefs.get("last_song_artwork", "").ifEmpty { null },
            duration = prefs.getLong("last_song_duration", 0L),
            source = SongSource.YOUTUBE
        )
    }

    override fun clearLastPlayedSong() {
        listOf("last_song_id", "last_song_title", "last_song_artist",
            "last_song_album", "last_song_artwork", "last_song_duration"
        ).forEach { prefs.remove(it) }
    }
}
