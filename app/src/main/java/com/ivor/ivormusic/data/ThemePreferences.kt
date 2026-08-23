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

    private val appContext: Context = context.applicationContext

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )

    private val _themeMode = MutableStateFlow(getThemeModePreference())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _amoledTheme = MutableStateFlow(getAmoledThemePreference())
    val amoledTheme: StateFlow<Boolean> = _amoledTheme.asStateFlow()

    private val _colorPalette = MutableStateFlow(getColorPalettePreference())
    val colorPalette: StateFlow<String> = _colorPalette.asStateFlow()

    private val _loadLocalSongs = MutableStateFlow(getLoadLocalSongsPreference())
    val loadLocalSongs: StateFlow<Boolean> = _loadLocalSongs.asStateFlow()
    
    private val _ambientBackground = MutableStateFlow(getAmbientBackgroundPreference())
    val ambientBackground: StateFlow<Boolean> = _ambientBackground.asStateFlow()

    private val _playerArtworkColors = MutableStateFlow(getPlayerArtworkColorsPreference())
    val playerArtworkColors: StateFlow<Boolean> = _playerArtworkColors.asStateFlow()
    
    private val _videoMode = MutableStateFlow(getVideoModePreference())
    val videoMode: StateFlow<Boolean> = _videoMode.asStateFlow()

    private val _homeModeToggleEnabled = MutableStateFlow(getHomeModeToggleEnabledPreference())
    val homeModeToggleEnabled: StateFlow<Boolean> = _homeModeToggleEnabled.asStateFlow()

    private val _playerStyle = MutableStateFlow(getPlayerStylePreference())
    val playerStyle: StateFlow<PlayerStyle> = _playerStyle.asStateFlow()
    
    private val _saveVideoHistory = MutableStateFlow(getSaveVideoHistoryPreference())
    val saveVideoHistory: StateFlow<Boolean> = _saveVideoHistory.asStateFlow()

    private val _saveMusicHistory = MutableStateFlow(getSaveMusicHistoryPreference())
    val saveMusicHistory: StateFlow<Boolean> = _saveMusicHistory.asStateFlow()

    private val _liveDownloadUpdates = MutableStateFlow(getLiveDownloadUpdatesPreference())
    val liveDownloadUpdates: StateFlow<Boolean> = _liveDownloadUpdates.asStateFlow()

    private val _livePlaybackUpdates = MutableStateFlow(getLivePlaybackUpdatesPreference())
    val livePlaybackUpdates: StateFlow<Boolean> = _livePlaybackUpdates.asStateFlow()

    private val _timedCommentsEnabled = MutableStateFlow(getTimedCommentsEnabledPreference())
    val timedCommentsEnabled: StateFlow<Boolean> = _timedCommentsEnabled.asStateFlow()

    private val _shortsEnabled = MutableStateFlow(getShortsEnabledPreference())
    val shortsEnabled: StateFlow<Boolean> = _shortsEnabled.asStateFlow()

    private val _shortsHiddenActions = MutableStateFlow(getShortsHiddenActionsPreference())
    val shortsHiddenActions: StateFlow<Set<String>> = _shortsHiddenActions.asStateFlow()

    private val _videoWavySeekBar = MutableStateFlow(getVideoWavySeekBarPreference())
    val videoWavySeekBar: StateFlow<Boolean> = _videoWavySeekBar.asStateFlow()

    private val _videoQualityWifi = MutableStateFlow(getVideoQualityWifiPreference())
    val videoQualityWifi: StateFlow<String> = _videoQualityWifi.asStateFlow()

    private val _videoQualityMobile = MutableStateFlow(getVideoQualityMobilePreference())
    val videoQualityMobile: StateFlow<String> = _videoQualityMobile.asStateFlow()

    private val _musicQualityWifi = MutableStateFlow(getMusicQualityWifiPreference())
    val musicQualityWifi: StateFlow<String> = _musicQualityWifi.asStateFlow()

    private val _musicQualityMobile = MutableStateFlow(getMusicQualityMobilePreference())
    val musicQualityMobile: StateFlow<String> = _musicQualityMobile.asStateFlow()

    private val _spotlightHome = MutableStateFlow(getSpotlightHomePreference())
    val spotlightHome: StateFlow<Boolean> = _spotlightHome.asStateFlow()

    private val _nonExpressiveNavigationBar =
        MutableStateFlow(getNonExpressiveNavigationBarPreference())
    val nonExpressiveNavigationBar: StateFlow<Boolean> =
        _nonExpressiveNavigationBar.asStateFlow()


    private val _subscriptionSource = MutableStateFlow(getSubscriptionSourcePreference())
    val subscriptionSource: StateFlow<String> = _subscriptionSource.asStateFlow()

    private val _subscribeTarget = MutableStateFlow(getSubscribeTargetPreference())
    val subscribeTarget: StateFlow<String> = _subscribeTarget.asStateFlow()

    private val _fastSubscriptionFeed = MutableStateFlow(getFastSubscriptionFeedPreference())
    val fastSubscriptionFeed: StateFlow<Boolean> = _fastSubscriptionFeed.asStateFlow()
    
    private val _excludedFolders = MutableStateFlow(getExcludedFoldersPreference())
    val excludedFolders: StateFlow<Set<String>> = _excludedFolders.asStateFlow()
    
    // Cache Settings
    private val _cacheEnabled = MutableStateFlow(getCacheEnabledPreference())
    val cacheEnabled: StateFlow<Boolean> = _cacheEnabled.asStateFlow()
    
    private val _maxCacheSizeMb = MutableStateFlow(getMaxCacheSizeMbPreference())
    val maxCacheSizeMb: StateFlow<Long> = _maxCacheSizeMb.asStateFlow()
    
    // Queue Settings
    private val _autoLoadQueue = MutableStateFlow(getAutoLoadQueuePreference())
    val autoLoadQueue: StateFlow<Boolean> = _autoLoadQueue.asStateFlow()

    // Crossfade Settings
    private val _crossfadeEnabled = MutableStateFlow(getCrossfadeEnabledPreference())
    val crossfadeEnabled: StateFlow<Boolean> = _crossfadeEnabled.asStateFlow()

    private val _crossfadeAuto = MutableStateFlow(getCrossfadeAutoPreference())
    val crossfadeAuto: StateFlow<Boolean> = _crossfadeAuto.asStateFlow()
    
    private val _crossfadeDurationMs = MutableStateFlow(getCrossfadeDurationPreference())
    val crossfadeDurationMs: StateFlow<Int> = _crossfadeDurationMs.asStateFlow()

    private val _normalizeVolume = MutableStateFlow(getNormalizeVolumePreference())
    val normalizeVolume: StateFlow<Boolean> = _normalizeVolume.asStateFlow()

    private val _oemFixEnabled = MutableStateFlow(getOemFixEnabledPreference())
    val oemFixEnabled: StateFlow<Boolean> = _oemFixEnabled.asStateFlow()

    private val _manualScanEnabled = MutableStateFlow(getManualScanEnabledPreference())
    val manualScanEnabled: StateFlow<Boolean> = _manualScanEnabled.asStateFlow()

    private val _onboardingCompleted = MutableStateFlow(getOnboardingCompletedPreference())
    val onboardingCompleted: StateFlow<Boolean> = _onboardingCompleted.asStateFlow()

    private val _localOnlyMode = MutableStateFlow(getLocalOnlyModePreference())
    val localOnlyMode: StateFlow<Boolean> = _localOnlyMode.asStateFlow()

    private val _timeLimitEnabled = MutableStateFlow(getTimeLimitEnabledPreference())
    val timeLimitEnabled: StateFlow<Boolean> = _timeLimitEnabled.asStateFlow()

    private val _timeLimitBudgets = MutableStateFlow(getTimeLimitBudgetsPreference())
    val timeLimitBudgets: StateFlow<Set<String>> = _timeLimitBudgets.asStateFlow()

    private val _librarySortOption = MutableStateFlow(getLibrarySortOptionPreference())
    val librarySortOption: StateFlow<String> = _librarySortOption.asStateFlow()

    // Every screen/service news up its own ThemePreferences (no DI), so a setter
    // called on one instance must still reach the flows of every other instance.
    // All instances share the same process-wide SharedPreferences object, so a
    // change listener gives us that propagation. Must be a field: SharedPreferences
    // only holds listeners weakly and would otherwise garbage-collect it.
    private val prefChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            KEY_THEME_MODE -> _themeMode.value = getThemeModePreference()
            KEY_AMOLED_THEME -> _amoledTheme.value = getAmoledThemePreference()
            KEY_COLOR_PALETTE -> _colorPalette.value = getColorPalettePreference()
            KEY_LOAD_LOCAL_SONGS -> _loadLocalSongs.value = getLoadLocalSongsPreference()
            KEY_AMBIENT_BACKGROUND -> _ambientBackground.value = getAmbientBackgroundPreference()
            KEY_PLAYER_ARTWORK_COLORS -> _playerArtworkColors.value = getPlayerArtworkColorsPreference()
            KEY_VIDEO_MODE -> _videoMode.value = getVideoModePreference()
            KEY_HOME_MODE_TOGGLE_ENABLED -> _homeModeToggleEnabled.value = getHomeModeToggleEnabledPreference()
            KEY_PLAYER_STYLE -> _playerStyle.value = getPlayerStylePreference()
            KEY_SAVE_VIDEO_HISTORY -> _saveVideoHistory.value = getSaveVideoHistoryPreference()
            KEY_SAVE_MUSIC_HISTORY -> _saveMusicHistory.value = getSaveMusicHistoryPreference()
            KEY_LIVE_DOWNLOAD_UPDATES -> _liveDownloadUpdates.value = getLiveDownloadUpdatesPreference()
            KEY_LIVE_PLAYBACK_UPDATES -> _livePlaybackUpdates.value = getLivePlaybackUpdatesPreference()
            KEY_TIMED_COMMENTS_ENABLED -> _timedCommentsEnabled.value = getTimedCommentsEnabledPreference()
            KEY_SHORTS_ENABLED -> _shortsEnabled.value = getShortsEnabledPreference()
            KEY_SHORTS_HIDDEN_ACTIONS -> _shortsHiddenActions.value = getShortsHiddenActionsPreference()
            KEY_VIDEO_WAVY_SEEKBAR -> _videoWavySeekBar.value = getVideoWavySeekBarPreference()
            KEY_VIDEO_QUALITY_WIFI -> _videoQualityWifi.value = getVideoQualityWifiPreference()
            KEY_VIDEO_QUALITY_MOBILE -> _videoQualityMobile.value = getVideoQualityMobilePreference()
            KEY_MUSIC_QUALITY_WIFI -> _musicQualityWifi.value = getMusicQualityWifiPreference()
            KEY_MUSIC_QUALITY_MOBILE -> _musicQualityMobile.value = getMusicQualityMobilePreference()
            KEY_SPOTLIGHT_HOME -> _spotlightHome.value = getSpotlightHomePreference()
            KEY_NON_EXPRESSIVE_NAVIGATION_BAR ->
                _nonExpressiveNavigationBar.value = getNonExpressiveNavigationBarPreference()
            KEY_SUBSCRIPTION_SOURCE -> _subscriptionSource.value = getSubscriptionSourcePreference()
            KEY_SUBSCRIBE_TARGET -> _subscribeTarget.value = getSubscribeTargetPreference()
            KEY_FAST_SUBSCRIPTION_FEED -> _fastSubscriptionFeed.value = getFastSubscriptionFeedPreference()
            KEY_EXCLUDED_FOLDERS -> _excludedFolders.value = getExcludedFoldersPreference()
            KEY_CACHE_ENABLED -> _cacheEnabled.value = getCacheEnabledPreference()
            KEY_MAX_CACHE_SIZE_MB -> _maxCacheSizeMb.value = getMaxCacheSizeMbPreference()
            KEY_AUTO_LOAD_QUEUE -> _autoLoadQueue.value = getAutoLoadQueuePreference()
            KEY_CROSSFADE_ENABLED -> _crossfadeEnabled.value = getCrossfadeEnabledPreference()
            KEY_CROSSFADE_AUTO -> _crossfadeAuto.value = getCrossfadeAutoPreference()
            KEY_CROSSFADE_DURATION -> _crossfadeDurationMs.value = getCrossfadeDurationPreference()
            KEY_NORMALIZE_VOLUME -> _normalizeVolume.value = getNormalizeVolumePreference()
            KEY_OEM_FIX_ENABLED -> _oemFixEnabled.value = getOemFixEnabledPreference()
            KEY_MANUAL_SCAN_ENABLED -> _manualScanEnabled.value = getManualScanEnabledPreference()
            KEY_ONBOARDING_COMPLETED -> _onboardingCompleted.value = getOnboardingCompletedPreference()
            KEY_LOCAL_ONLY_MODE -> _localOnlyMode.value = getLocalOnlyModePreference()
            KEY_TIME_LIMIT_ENABLED -> _timeLimitEnabled.value = getTimeLimitEnabledPreference()
            KEY_TIME_LIMIT_BUDGETS -> _timeLimitBudgets.value = getTimeLimitBudgetsPreference()
            KEY_LIBRARY_SORT_OPTION -> _librarySortOption.value = getLibrarySortOptionPreference()
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(prefChangeListener)
    }

    companion object {
        private const val PREFS_NAME = "ivor_music_theme_prefs"
        private const val KEY_THEME_MODE = "theme_mode_enum"
        private const val KEY_OLD_DARK_MODE = "dark_mode" // For migration
        private const val KEY_AMOLED_THEME = "amoled_theme"
        private const val KEY_COLOR_PALETTE = "color_palette"
        /** Default palette id: wallpaper-based dynamic color (Android 12+). */
        const val DEFAULT_COLOR_PALETTE = "dynamic"
        private const val KEY_LOAD_LOCAL_SONGS = "load_local_songs"
        private const val KEY_AMBIENT_BACKGROUND = "ambient_background"
        private const val KEY_PLAYER_ARTWORK_COLORS = "player_artwork_colors"
        private const val KEY_VIDEO_MODE = "video_mode"
        private const val KEY_LAST_MUSIC_TAB = "last_music_tab"
        private const val KEY_LAST_VIDEO_TAB = "last_video_tab"
        private const val KEY_HOME_MODE_TOGGLE_ENABLED = "home_mode_toggle_enabled"
        private const val KEY_PLAYER_STYLE = "player_style"
        private const val KEY_SAVE_VIDEO_HISTORY = "save_video_history"
        private const val KEY_SAVE_MUSIC_HISTORY = "save_music_history"
        private const val KEY_LIVE_DOWNLOAD_UPDATES = "live_download_updates"

        /**
         * Live Updates (promoted ongoing notifications) are an Android 16 / API 36
         * feature. Below that the setting is meaningless and should not be shown.
         */
        val SUPPORTS_LIVE_UPDATES: Boolean =
            android.os.Build.VERSION.SDK_INT >= 36

        /**
         * Fresh read for the notification helper, which runs off a plain Context
         * rather than holding a ThemePreferences instance. Always false where the
         * platform cannot promote, so callers need only check this one thing.
         */
        fun isLiveDownloadUpdatesEnabled(context: Context): Boolean =
            SUPPORTS_LIVE_UPDATES &&
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .getBoolean(KEY_LIVE_DOWNLOAD_UPDATES, true)

        private const val KEY_LIVE_PLAYBACK_UPDATES = "live_playback_updates"

        /**
         * Fresh read for the playback service and its notification provider,
         * which run off a plain Context. Unlike downloads this defaults to
         * OFF: a Live Update for playback is a persistent status bar chip for
         * the whole song, which is a lot of chrome to hand someone who did not
         * ask for it.
         */
        fun isLivePlaybackUpdatesEnabled(context: Context): Boolean =
            SUPPORTS_LIVE_UPDATES &&
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .getBoolean(KEY_LIVE_PLAYBACK_UPDATES, false)
        private const val KEY_TIMED_COMMENTS_ENABLED = "timed_comments_enabled"
        private const val KEY_SHORTS_ENABLED = "shorts_enabled"
        private const val KEY_SHORTS_HIDDEN_ACTIONS = "shorts_hidden_actions"
        private const val KEY_VIDEO_WAVY_SEEKBAR = "video_wavy_seekbar"

        /** Ids for the Shorts action-rail buttons that can be hidden. */
        const val SHORTS_ACTION_LIKE = "like"
        const val SHORTS_ACTION_DISLIKE = "dislike"
        const val SHORTS_ACTION_COMMENTS = "comments"
        const val SHORTS_ACTION_SHARE = "share"
        const val SHORTS_ACTION_NOT_INTERESTED = "not_interested"

        /**
         * Legacy single video-quality key, superseded by the per-network pair
         * below. Kept only as a migration source: a value stored here by an
         * older version seeds both network variants on first read.
         */
        private const val KEY_DEFAULT_VIDEO_QUALITY = "default_video_quality"

        private const val KEY_VIDEO_QUALITY_WIFI = "video_quality_wifi"
        private const val KEY_VIDEO_QUALITY_MOBILE = "video_quality_mobile"

        /** Sentinel meaning "highest available quality". */
        const val VIDEO_QUALITY_AUTO = "auto"

        /** Quality labels offered in Settings, best first. */
        val VIDEO_QUALITY_OPTIONS = listOf(
            VIDEO_QUALITY_AUTO, "2160p", "1440p", "1080p", "720p", "480p", "360p", "144p"
        )
        private const val DEFAULT_VIDEO_QUALITY_WIFI = "1080p"
        private const val DEFAULT_VIDEO_QUALITY_MOBILE = "720p"

        // ---------------- Subscriptions ----------------

        private const val KEY_SUBSCRIPTION_SOURCE = "subscription_source"
        private const val KEY_SUBSCRIBE_TARGET = "subscribe_target"
        private const val KEY_FAST_SUBSCRIPTION_FEED = "fast_subscription_feed"

        /**
         * Show whichever subscriptions exist - the device's, the account's, or
         * both merged. The default, because a user who has imported a list
         * *and* signed in wants both, and picking one for them silently hides
         * channels they explicitly added.
         */
        const val SUBSCRIPTIONS_AUTO = "auto"

        /** Device-local subscriptions only, even when signed in. */
        const val SUBSCRIPTIONS_LOCAL = "local"

        /** The signed-in YouTube account's subscriptions only. */
        const val SUBSCRIPTIONS_YOUTUBE = "youtube"

        /** Subscribe writes to both stores at once (subscribe target only). */
        const val SUBSCRIPTIONS_BOTH = "both"

        val SUBSCRIPTION_SOURCE_OPTIONS = listOf(
            SUBSCRIPTIONS_AUTO, SUBSCRIPTIONS_LOCAL, SUBSCRIPTIONS_YOUTUBE
        )

        val SUBSCRIBE_TARGET_OPTIONS = listOf(
            SUBSCRIPTIONS_AUTO, SUBSCRIPTIONS_LOCAL, SUBSCRIPTIONS_YOUTUBE, SUBSCRIPTIONS_BOTH
        )

        private const val KEY_MUSIC_QUALITY_WIFI = "music_quality_wifi"
        private const val KEY_MUSIC_QUALITY_MOBILE = "music_quality_mobile"

        /** Music stream quality values: best available bitrate. */
        const val MUSIC_QUALITY_HIGH = "high"

        /** Music stream quality values: balanced, around 128 kbps. */
        const val MUSIC_QUALITY_NORMAL = "normal"

        /** Music stream quality values: smallest available stream. */
        const val MUSIC_QUALITY_LOW = "low"

        /** Music quality values offered in Settings, best first. */
        val MUSIC_QUALITY_OPTIONS = listOf(
            MUSIC_QUALITY_HIGH, MUSIC_QUALITY_NORMAL, MUSIC_QUALITY_LOW
        )

        /**
         * Highest bitrate matches the app's historical pick, so both network
         * defaults preserve existing behavior until the user chooses otherwise.
         */
        private const val DEFAULT_MUSIC_QUALITY = MUSIC_QUALITY_HIGH

        /**
         * Whether the active network is metered (mobile data, metered
         * hotspots). Drives which half of every per-network quality pair
         * applies. Unknown network state reads as unmetered so quality
         * degrades gracefully to the Wi-Fi choice rather than silently
         * capping streams.
         */
        fun isNetworkMetered(context: Context): Boolean {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE)
                as? android.net.ConnectivityManager ?: return false
            return cm.isActiveNetworkMetered
        }

        /**
         * Static fresh read of the music quality for the current network, for
         * the stream-resolution layer which only holds a Context (same
         * pattern as [isLocalOnly]).
         */
        fun currentMusicQuality(context: Context): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val key = if (isNetworkMetered(context)) KEY_MUSIC_QUALITY_MOBILE else KEY_MUSIC_QUALITY_WIFI
            return prefs.getString(key, DEFAULT_MUSIC_QUALITY) ?: DEFAULT_MUSIC_QUALITY
        }

        /**
         * Spotlight: the alternative music Home, built from a shortcut grid,
         * paged quick picks and artwork shelves (see
         * ui/home/SpotlightHomeContent.kt). A boolean rather than a home-style
         * enum on purpose - PlayerStyle's constants are persisted by name and are
         * therefore frozen forever, and there is no third Home planned.
         */
        private const val KEY_SPOTLIGHT_HOME = "spotlight_home"
        private const val KEY_NON_EXPRESSIVE_NAVIGATION_BAR =
            "non_expressive_navigation_bar"

        /**
         * Default quality for video downloads, one of [VIDEO_QUALITY_OPTIONS].
         * Set from the download sheet's "remember" toggle rather than the
         * Settings screen, so like the video session state below it has no
         * StateFlow — the sheet and the download worker both read it fresh.
         */
        private const val KEY_DOWNLOAD_VIDEO_QUALITY = "download_video_quality"

        /**
         * Static fresh read of the download quality for DownloadRepository,
         * which runs off a plain Context (same pattern as [isLocalOnly]).
         * Defaults to [VIDEO_QUALITY_AUTO]: best available, the historical
         * download behavior.
         */
        fun currentDownloadVideoQuality(context: Context): String =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_DOWNLOAD_VIDEO_QUALITY, VIDEO_QUALITY_AUTO)
                ?: VIDEO_QUALITY_AUTO

        /**
         * Static fresh read of the active palette id, for the playlist cover
         * generator. It runs when a playlist is created or renamed, long after
         * the Settings screen changed the palette through its own
         * ThemePreferences instance, and instance StateFlows do not cross.
         */
        fun currentColorPalette(context: Context): String =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_COLOR_PALETTE, DEFAULT_COLOR_PALETTE)
                ?: DEFAULT_COLOR_PALETTE

        /**
         * Video player session state that outlives a single video. Unlike the
         * settings above these have no Settings UI and no StateFlow: nothing
         * observes them reactively, they are read once when the player starts
         * and written when the user changes them in the player itself.
         */
        private const val KEY_VIDEO_REPEAT = "video_repeat"
        private const val KEY_VIDEO_AUTOPLAY = "video_autoplay"
        private const val KEY_VIDEO_BRIGHTNESS = "video_brightness"

        /** Stored brightness sentinel meaning "never set, follow the system". */
        const val VIDEO_BRIGHTNESS_UNSET = -1f

        private const val KEY_EXCLUDED_FOLDERS = "excluded_folders"
        private const val KEY_CACHE_ENABLED = "cache_enabled"
        private const val KEY_MAX_CACHE_SIZE_MB = "max_cache_size_mb"
        private const val KEY_AUTO_LOAD_QUEUE = "auto_load_queue"
        private const val KEY_CROSSFADE_ENABLED = "crossfade_enabled"
        private const val KEY_CROSSFADE_AUTO = "crossfade_auto"
        private const val KEY_CROSSFADE_DURATION = "crossfade_duration"
        private const val KEY_PLAYBACK_SHUFFLE = "playback_shuffle"
        private const val KEY_PLAYBACK_REPEAT_MODE = "playback_repeat_mode"
        private const val KEY_PLAYBACK_SHUFFLE_SEED = "playback_shuffle_seed"
        private const val KEY_SLEEP_TIMER_ENDS_AT = "sleep_timer_ends_at"
        private const val KEY_SLEEP_TIMER_END_OF_TRACK = "sleep_timer_end_of_track"
        private const val MIN_CROSSFADE_DURATION_MS = 1_000
        private const val MAX_CROSSFADE_DURATION_MS = 15_000
        private const val KEY_NORMALIZE_VOLUME = "normalize_volume"
        private const val KEY_OEM_FIX_ENABLED = "oem_fix_enabled"
        private const val KEY_MANUAL_SCAN_ENABLED = "manual_scan_enabled"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_LOCAL_ONLY_MODE = "local_only_mode"

        private const val KEY_TIME_LIMIT_ENABLED = "time_limit_enabled"
        private const val KEY_TIME_LIMIT_BUDGETS = "time_limit_budgets"

        /** 5 hours a day, every day - the seed when the limiter is first enabled. */
        val DEFAULT_TIME_LIMIT_BUDGETS: Set<String> =
            (0..6).map { "$it=${AppTimeLimit.DEFAULT_DAILY_MINUTES}" }.toSet()

        private const val KEY_REPORT_VERBOSE_LOGS = "report_verbose_logs"

        private const val KEY_LIBRARY_SORT_OPTION = "library_sort_option"

        /**
         * Fallback sort order for the Library's All tab. Mirrors the name of
         * LibrarySortOption.Title, which lives in the UI layer because it
         * carries an icon.
         */
        private const val LIBRARY_SORT_DEFAULT = "Title"

        /**
         * Static fresh read of the local-only preference for network layers
         * that only hold a Context (OkHttp interceptors, repositories) — no
         * ThemePreferences instance or flow subscription needed.
         */
        fun isLocalOnly(context: Context): Boolean =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_LOCAL_ONLY_MODE, false)

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
     * Get the stored AMOLED theme preference. Defaults to false.
     */
    private fun getAmoledThemePreference(): Boolean {
        return prefs.getBoolean(KEY_AMOLED_THEME, false)
    }

    /**
     * Save AMOLED theme preference and update the flow.
     */
    fun setAmoledTheme(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AMOLED_THEME, enabled).apply()
        _amoledTheme.value = enabled
    }

    /**
     * Get the stored color palette id. Defaults to "dynamic" (wallpaper color)
     * to preserve the app's historical dynamic-color behavior.
     */
    private fun getColorPalettePreference(): String {
        return prefs.getString(KEY_COLOR_PALETTE, DEFAULT_COLOR_PALETTE) ?: DEFAULT_COLOR_PALETTE
    }

    /**
     * Save color palette preference and update the flow.
     */
    fun setColorPalette(paletteId: String) {
        prefs.edit().putString(KEY_COLOR_PALETTE, paletteId).apply()
        _colorPalette.value = paletteId
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
     * Get the stored album-art player colors preference. Defaults to true:
     * expanded player buttons take their colors from the current cover.
     */
    private fun getPlayerArtworkColorsPreference(): Boolean {
        return prefs.getBoolean(KEY_PLAYER_ARTWORK_COLORS, true)
    }

    /**
     * Save the album-art player colors preference and update the flow.
     */
    fun setPlayerArtworkColors(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PLAYER_ARTWORK_COLORS, enabled).apply()
        _playerArtworkColors.value = enabled
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

    /** Root Home destination restored after Koda is recreated. */
    fun getLastHomeTab(videoMode: Boolean): Int {
        val key = if (videoMode) KEY_LAST_VIDEO_TAB else KEY_LAST_MUSIC_TAB
        val lastValidTab = if (videoMode) 3 else 2
        return prefs.getInt(key, 0).coerceIn(0, lastValidTab)
    }

    /** Music and video keep separate positions because their tab sets differ. */
    fun setLastHomeTab(videoMode: Boolean, tab: Int) {
        val key = if (videoMode) KEY_LAST_VIDEO_TAB else KEY_LAST_MUSIC_TAB
        val lastValidTab = if (videoMode) 3 else 2
        prefs.edit().putInt(key, tab.coerceIn(0, lastValidTab)).apply()
    }

    /**
     * Get the stored home mode toggle preference. Defaults to true (the
     * music/video switch is shown in the Home screen top bar).
     */
    private fun getHomeModeToggleEnabledPreference(): Boolean {
        return prefs.getBoolean(KEY_HOME_MODE_TOGGLE_ENABLED, true)
    }

    /**
     * Save home mode toggle preference and update the flow.
     */
    fun setHomeModeToggleEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HOME_MODE_TOGGLE_ENABLED, enabled).apply()
        _homeModeToggleEnabled.value = enabled
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
     * Fresh read of the save-history preference straight from SharedPreferences.
     * ViewModels hold their own ThemePreferences instances, so their StateFlow
     * copy goes stale when the toggle is flipped through another instance
     * (e.g. the settings screen) — use this at decision time instead.
     */
    fun isSaveVideoHistoryEnabled(): Boolean = getSaveVideoHistoryPreference()

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
     * Whether songs that play are written to the local listening history.
     *
     * Separate from [getSaveVideoHistoryPreference], which governs the YouTube
     * account's watch history: this one is device-local, works signed out, and
     * feeds the listening history screen, the recently-played rail, the Library
     * "Most played" sort and the taste profile behind recommendations. Turning
     * it off stops all of those growing; it does not erase what is already
     * recorded, which is what Clear history is for.
     */
    private fun getSaveMusicHistoryPreference(): Boolean {
        return prefs.getBoolean(KEY_SAVE_MUSIC_HISTORY, true)
    }

    /**
     * Fresh read of the listening-history preference. Same reasoning as
     * [isSaveVideoHistoryEnabled]: PlayerViewModel holds its own
     * ThemePreferences and decides at playback time, long after the settings
     * screen wrote through a different instance.
     */
    fun isSaveMusicHistoryEnabled(): Boolean = getSaveMusicHistoryPreference()

    fun setSaveMusicHistory(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SAVE_MUSIC_HISTORY, enabled).apply()
        _saveMusicHistory.value = enabled
    }

    /**
     * Whether download progress may ask to be promoted to a Live Update.
     * Defaults on, but is inert below API 36 (see [SUPPORTS_LIVE_UPDATES]).
     */
    private fun getLiveDownloadUpdatesPreference(): Boolean {
        return SUPPORTS_LIVE_UPDATES && prefs.getBoolean(KEY_LIVE_DOWNLOAD_UPDATES, true)
    }

    fun setLiveDownloadUpdates(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LIVE_DOWNLOAD_UPDATES, enabled).apply()
        _liveDownloadUpdates.value = enabled && SUPPORTS_LIVE_UPDATES
    }

    /**
     * Whether music playback may ask to be promoted to a Live Update. Off by
     * default - see [isLivePlaybackUpdatesEnabled] - and inert below API 36.
     */
    private fun getLivePlaybackUpdatesPreference(): Boolean {
        return SUPPORTS_LIVE_UPDATES && prefs.getBoolean(KEY_LIVE_PLAYBACK_UPDATES, false)
    }

    fun setLivePlaybackUpdates(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LIVE_PLAYBACK_UPDATES, enabled).apply()
        _livePlaybackUpdates.value = enabled && SUPPORTS_LIVE_UPDATES
    }

    /**
     * Get the stored Shorts preference. Defaults to false: short-form feeds
     * are engineered to be compulsive, so Shorts stay hidden until the user
     * deliberately opts in.
     */
    private fun getShortsEnabledPreference(): Boolean {
        return prefs.getBoolean(KEY_SHORTS_ENABLED, false)
    }

    /**
     * Fresh read of the Shorts preference straight from SharedPreferences —
     * ViewModels hold their own ThemePreferences instances, so their StateFlow
     * copy goes stale when the toggle flips through the settings screen.
     */
    fun isShortsEnabled(): Boolean = getShortsEnabledPreference()

    /**
     * Save Shorts preference and update the flow.
     */
    fun setShortsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHORTS_ENABLED, enabled).apply()
        _shortsEnabled.value = enabled
    }

    /**
     * Get the Shorts action buttons the user chose to hide (ids from
     * SHORTS_ACTION_OPTIONS). Defaults to empty: all buttons visible.
     */
    private fun getShortsHiddenActionsPreference(): Set<String> {
        return prefs.getStringSet(KEY_SHORTS_HIDDEN_ACTIONS, emptySet()) ?: emptySet()
    }

    /**
     * Save which Shorts action buttons are hidden and update the flow.
     */
    fun setShortsHiddenActions(hidden: Set<String>) {
        prefs.edit().putStringSet(KEY_SHORTS_HIDDEN_ACTIONS, hidden).apply()
        _shortsHiddenActions.value = hidden
    }

    /**
     * Get the stored timed comments preference. Defaults to false (off).
     */
    private fun getTimedCommentsEnabledPreference(): Boolean {
        return prefs.getBoolean(KEY_TIMED_COMMENTS_ENABLED, false)
    }

    /**
     * Save timed comments preference and update the flow.
     */
    fun setTimedCommentsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TIMED_COMMENTS_ENABLED, enabled).apply()
        _timedCommentsEnabled.value = enabled
    }

    /**
     * Get the stored video wavy seekbar preference. Defaults to true (wavy).
     */
    fun getVideoWavySeekBarPreference(): Boolean {
        return prefs.getBoolean(KEY_VIDEO_WAVY_SEEKBAR, true)
    }

    /**
     * Save video wavy seekbar preference and update the flow.
     */
    fun setVideoWavySeekBar(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VIDEO_WAVY_SEEKBAR, enabled).apply()
        _videoWavySeekBar.value = enabled
    }
    
    /**
     * Get the stored Wi-Fi (unmetered) video quality. Falls back to the
     * legacy single-quality key so an upgrade keeps the user's old choice,
     * then to 1080p, matching the player's historical hardcoded pick.
     */
    private fun getVideoQualityWifiPreference(): String {
        return prefs.getString(KEY_VIDEO_QUALITY_WIFI, null)
            ?: prefs.getString(KEY_DEFAULT_VIDEO_QUALITY, null)
            ?: DEFAULT_VIDEO_QUALITY_WIFI
    }

    /**
     * Get the stored mobile-data (metered) video quality. A legacy
     * single-quality choice seeds this too — the split must not silently
     * change what an existing user sees — and only fresh installs get the
     * data-friendlier 720p default.
     */
    private fun getVideoQualityMobilePreference(): String {
        return prefs.getString(KEY_VIDEO_QUALITY_MOBILE, null)
            ?: prefs.getString(KEY_DEFAULT_VIDEO_QUALITY, null)
            ?: DEFAULT_VIDEO_QUALITY_MOBILE
    }

    /**
     * Fresh read of the default video quality for the current network,
     * straight from SharedPreferences. The video player VMs hold their own
     * ThemePreferences instances, so their StateFlow copies go stale when
     * Settings changes a value — use this at playback time instead.
     */
    fun getDefaultVideoQuality(): String =
        if (isNetworkMetered(appContext)) getVideoQualityMobilePreference()
        else getVideoQualityWifiPreference()

    /**
     * Save the Wi-Fi video quality preference and update the flow.
     */
    fun setVideoQualityWifi(quality: String) {
        prefs.edit().putString(KEY_VIDEO_QUALITY_WIFI, quality).apply()
        _videoQualityWifi.value = quality
    }

    /**
     * Save the mobile-data video quality preference and update the flow.
     */
    fun setVideoQualityMobile(quality: String) {
        prefs.edit().putString(KEY_VIDEO_QUALITY_MOBILE, quality).apply()
        _videoQualityMobile.value = quality
    }

    // ---------------- Subscriptions ----------------

    private fun getSubscriptionSourcePreference(): String =
        prefs.getString(KEY_SUBSCRIPTION_SOURCE, SUBSCRIPTIONS_AUTO) ?: SUBSCRIPTIONS_AUTO

    private fun getSubscribeTargetPreference(): String =
        prefs.getString(KEY_SUBSCRIBE_TARGET, SUBSCRIPTIONS_AUTO) ?: SUBSCRIPTIONS_AUTO

    private fun getFastSubscriptionFeedPreference(): Boolean =
        prefs.getBoolean(KEY_FAST_SUBSCRIPTION_FEED, true)

    /** Which subscription lists the Subscriptions tab shows. */
    fun setSubscriptionSource(source: String) {
        prefs.edit().putString(KEY_SUBSCRIPTION_SOURCE, source).apply()
        _subscriptionSource.value = source
    }

    /** Where a Subscribe tap writes: device, YouTube account, or both. */
    fun setSubscribeTarget(target: String) {
        prefs.edit().putString(KEY_SUBSCRIBE_TARGET, target).apply()
        _subscribeTarget.value = target
    }

    /**
     * Fast refresh builds the local feed from each channel's Atom feed:
     * roughly a twentieth of the data, exact upload times, but no duration or
     * live badge. Off means a full channel fetch per channel, which restores
     * those at a real cost on a large subscription list.
     */
    fun setFastSubscriptionFeed(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FAST_SUBSCRIPTION_FEED, enabled).apply()
        _fastSubscriptionFeed.value = enabled
    }

    /** Fresh read for ViewModels deciding at tap time. */
    fun currentSubscribeTarget(): String = getSubscribeTargetPreference()

    /** Fresh read for ViewModels deciding at refresh time. */
    fun currentSubscriptionSource(): String = getSubscriptionSourcePreference()

    /** Fresh read for ViewModels deciding at refresh time. */
    fun isFastSubscriptionFeedEnabled(): Boolean = getFastSubscriptionFeedPreference()

    // ---------------- Bug reporting ----------------

    /**
     * Whether the bug reporter's log filter offers "everything" (debug-level
     * lines) or stops at warnings. Sheet-persisted like download video quality
     * rather than a settings page row: it belongs to the report flow, and the
     * choice only ever matters inside it.
     */
    fun getReportVerboseLogs(): Boolean = prefs.getBoolean(KEY_REPORT_VERBOSE_LOGS, false)

    fun setReportVerboseLogs(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_REPORT_VERBOSE_LOGS, enabled).apply()
    }

    private fun getMusicQualityWifiPreference(): String {
        return prefs.getString(KEY_MUSIC_QUALITY_WIFI, DEFAULT_MUSIC_QUALITY)
            ?: DEFAULT_MUSIC_QUALITY
    }

    private fun getMusicQualityMobilePreference(): String {
        return prefs.getString(KEY_MUSIC_QUALITY_MOBILE, DEFAULT_MUSIC_QUALITY)
            ?: DEFAULT_MUSIC_QUALITY
    }

    /**
     * Save the Wi-Fi music quality preference and update the flow.
     */
    fun setMusicQualityWifi(quality: String) {
        prefs.edit().putString(KEY_MUSIC_QUALITY_WIFI, quality).apply()
        _musicQualityWifi.value = quality
    }

    /**
     * Save the mobile-data music quality preference and update the flow.
     */
    fun setMusicQualityMobile(quality: String) {
        prefs.edit().putString(KEY_MUSIC_QUALITY_MOBILE, quality).apply()
        _musicQualityMobile.value = quality
    }

    private fun getSpotlightHomePreference(): Boolean {
        return prefs.getBoolean(KEY_SPOTLIGHT_HOME, false)
    }

    /**
     * Save the Spotlight home opt-in and update the flow. Off by default: the
     * classic Home stays what an upgrading user and a first run both get.
     */
    fun setSpotlightHome(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SPOTLIGHT_HOME, enabled).apply()
        _spotlightHome.value = enabled
    }

    private fun getNonExpressiveNavigationBarPreference(): Boolean {
        return prefs.getBoolean(KEY_NON_EXPRESSIVE_NAVIGATION_BAR, false)
    }

    /**
     * Opt into Material 3's standard non-expressive navigation bar. The existing
     * expressive floating toolbar remains the default for new and upgrading
     * users, and this preference intentionally is not part of onboarding.
     */
    fun setNonExpressiveNavigationBar(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NON_EXPRESSIVE_NAVIGATION_BAR, enabled).apply()
        _nonExpressiveNavigationBar.value = enabled
    }

    /**
     * Default quality for video downloads. See [currentDownloadVideoQuality]
     * for the semantics; this instance pair serves the download sheet, which
     * preselects from it and writes it back through its "remember" toggle.
     */
    fun getDownloadVideoQuality(): String =
        prefs.getString(KEY_DOWNLOAD_VIDEO_QUALITY, VIDEO_QUALITY_AUTO) ?: VIDEO_QUALITY_AUTO

    fun setDownloadVideoQuality(quality: String) {
        prefs.edit().putString(KEY_DOWNLOAD_VIDEO_QUALITY, quality).apply()
    }

    /**
     * Whether the video player loops the current video (repeat) instead of
     * auto-playing the next related one. Sticks across videos and app
     * restarts. Defaults to off, i.e. auto-play.
     */
    fun isVideoRepeatEnabled(): Boolean = prefs.getBoolean(KEY_VIDEO_REPEAT, false)

    fun setVideoRepeatEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VIDEO_REPEAT, enabled).apply()
    }

    /**
     * Whether a finished video may advance to the next playlist or related
     * item. Defaults to on to preserve the player's historical behaviour.
     * Loop is governed by the same end-of-video master switch in the player,
     * so disabling autoplay also disables repeat there.
     */
    fun isVideoAutoplayEnabled(): Boolean = prefs.getBoolean(KEY_VIDEO_AUTOPLAY, true)

    fun setVideoAutoplayEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VIDEO_AUTOPLAY, enabled).apply()
    }

    /**
     * Screen brightness (0..1) the user last dialed in with the fullscreen
     * brightness drag, re-applied the next time a video goes fullscreen.
     * [VIDEO_BRIGHTNESS_UNSET] means it was never set, so the window should
     * keep following the system brightness.
     */
    fun getVideoBrightness(): Float =
        prefs.getFloat(KEY_VIDEO_BRIGHTNESS, VIDEO_BRIGHTNESS_UNSET)

    fun setVideoBrightness(value: Float) {
        prefs.edit().putFloat(KEY_VIDEO_BRIGHTNESS, value.coerceIn(0f, 1f)).apply()
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
    
    // --- Queue Settings ---

    private fun getAutoLoadQueuePreference(): Boolean {
        return prefs.getBoolean(KEY_AUTO_LOAD_QUEUE, true)
    }

    /**
     * Fresh read of the auto-load-queue preference straight from
     * SharedPreferences — the player VM's StateFlow copy goes stale when the
     * toggle is flipped through the settings screen's own instance.
     */
    fun isAutoLoadQueueEnabled(): Boolean = getAutoLoadQueuePreference()

    fun setAutoLoadQueue(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_LOAD_QUEUE, enabled).apply()
        _autoLoadQueue.value = enabled
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

    private fun getCrossfadeAutoPreference(): Boolean {
        return prefs.getBoolean(KEY_CROSSFADE_AUTO, true)
    }

    fun setCrossfadeAuto(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CROSSFADE_AUTO, enabled).apply()
        _crossfadeAuto.value = enabled
    }
    
    private fun getCrossfadeDurationPreference(): Int {
        return prefs.getInt(KEY_CROSSFADE_DURATION, 3000)
            .coerceIn(MIN_CROSSFADE_DURATION_MS, MAX_CROSSFADE_DURATION_MS)
    }
    
    fun setCrossfadeDuration(durationMs: Int) {
        val bounded = durationMs.coerceIn(MIN_CROSSFADE_DURATION_MS, MAX_CROSSFADE_DURATION_MS)
        prefs.edit().putInt(KEY_CROSSFADE_DURATION, bounded).apply()
        _crossfadeDurationMs.value = bounded
    }

    // --- Durable playback modes ---

    fun isPlaybackShuffleEnabled(): Boolean = prefs.getBoolean(KEY_PLAYBACK_SHUFFLE, false)

    fun setPlaybackShuffle(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PLAYBACK_SHUFFLE, enabled).apply()
    }

    fun getPlaybackRepeatMode(): Int = prefs.getInt(
        KEY_PLAYBACK_REPEAT_MODE,
        androidx.media3.common.Player.REPEAT_MODE_OFF
    ).takeIf {
        it == androidx.media3.common.Player.REPEAT_MODE_OFF ||
            it == androidx.media3.common.Player.REPEAT_MODE_ONE ||
            it == androidx.media3.common.Player.REPEAT_MODE_ALL
    } ?: androidx.media3.common.Player.REPEAT_MODE_OFF

    fun setPlaybackRepeatMode(mode: Int) {
        prefs.edit().putInt(KEY_PLAYBACK_REPEAT_MODE, mode).apply()
    }

    fun getPlaybackShuffleSeed(): Long = prefs.getLong(KEY_PLAYBACK_SHUFFLE_SEED, 0L)

    fun setPlaybackShuffleSeed(seed: Long) {
        prefs.edit().putLong(KEY_PLAYBACK_SHUFFLE_SEED, seed).apply()
    }

    fun getSleepTimerEndsAt(): Long = prefs.getLong(KEY_SLEEP_TIMER_ENDS_AT, 0L)

    fun isSleepTimerEndOfTrack(): Boolean =
        prefs.getBoolean(KEY_SLEEP_TIMER_END_OF_TRACK, false)

    fun saveSleepTimer(endsAt: Long, endOfTrack: Boolean) {
        prefs.edit()
            .putLong(KEY_SLEEP_TIMER_ENDS_AT, endsAt)
            .putBoolean(KEY_SLEEP_TIMER_END_OF_TRACK, endOfTrack)
            .apply()
    }

    fun clearSleepTimer() {
        prefs.edit()
            .remove(KEY_SLEEP_TIMER_ENDS_AT)
            .remove(KEY_SLEEP_TIMER_END_OF_TRACK)
            .apply()
    }

    /**
     * Even out the volume between tracks, using the loudness YouTube already
     * measured for each one (see `TrackLoudnessStore`).
     *
     * On by default, as it is in every player that offers it. The correction
     * only ever attenuates, so the effect on an existing install is that the
     * loudest masters stop jumping out - not that anything gets louder than it
     * was.
     */
    private fun getNormalizeVolumePreference(): Boolean {
        return prefs.getBoolean(KEY_NORMALIZE_VOLUME, true)
    }

    fun setNormalizeVolume(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NORMALIZE_VOLUME, enabled).apply()
        _normalizeVolume.value = enabled
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

    /**
     * Get the stored local-only preference. Defaults to false: YouTube
     * features work normally until the user opts into offline-only use.
     */
    private fun getLocalOnlyModePreference(): Boolean {
        return prefs.getBoolean(KEY_LOCAL_ONLY_MODE, false)
    }

    /**
     * Fresh read of the local-only preference straight from SharedPreferences —
     * ViewModels hold their own ThemePreferences instances, so their StateFlow
     * copy goes stale when the toggle flips through the settings screen.
     */
    fun isLocalOnlyModeEnabled(): Boolean = getLocalOnlyModePreference()

    /**
     * Save local-only preference and update the flow.
     */
    fun setLocalOnlyMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LOCAL_ONLY_MODE, enabled).apply()
        _localOnlyMode.value = enabled
    }

    // ---------------- Daily time limit ----------------
    //
    // The enforcement side (usage accrual, lock evaluation) lives in
    // [AppTimeLimit]; this is only where the user's choices are kept.

    private fun getTimeLimitEnabledPreference(): Boolean =
        prefs.getBoolean(KEY_TIME_LIMIT_ENABLED, false)

    /** Fresh read for the activity's lock ticker deciding at tick time. */
    fun isTimeLimitEnabled(): Boolean = getTimeLimitEnabledPreference()

    fun setTimeLimitEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TIME_LIMIT_ENABLED, enabled).apply()
        _timeLimitEnabled.value = enabled
    }

    /**
     * Per-weekday budgets as "day=minutes" entries, day 0 = Monday .. 6 =
     * Sunday. A day absent from the set, or stored 0, means unlimited.
     */
    private fun getTimeLimitBudgetsPreference(): Set<String> {
        val stored = prefs.getStringSet(KEY_TIME_LIMIT_BUDGETS, null)
        return stored ?: DEFAULT_TIME_LIMIT_BUDGETS
    }

    fun getTimeLimitBudgets(): Set<String> = getTimeLimitBudgetsPreference()

    fun setTimeLimitBudgets(budgets: Set<String>) {
        val canonical = AppTimeLimit.parseBudgets(budgets)
            .map { (day, minutes) -> "$day=$minutes" }
            .toSet()
        prefs.edit().putStringSet(KEY_TIME_LIMIT_BUDGETS, canonical).apply()
        _timeLimitBudgets.value = canonical
    }

    /** Replace one weekday's value without leaving an ambiguous duplicate entry. */
    fun setTimeLimitBudget(day: Int, minutes: Int) {
        require(day in 0..6) { "Weekday must be in 0..6" }
        require(minutes >= 0) { "Budget cannot be negative" }
        val updated = AppTimeLimit.parseBudgets(getTimeLimitBudgetsPreference()).toMutableMap()
        updated[day] = minutes
        setTimeLimitBudgets(updated.map { (storedDay, storedMinutes) ->
            "$storedDay=$storedMinutes"
        }.toSet())
    }

    /** One budget applied to all seven days - the onboarding preset path. */
    fun setAllTimeLimitBudgets(minutesPerDay: Int) {
        setTimeLimitBudgets((0..6).map { "$it=$minutesPerDay" }.toSet())
    }

    /**
     * Get the stored Library sort order, held as a LibrarySortOption name.
     * The caller maps it back to the enum and is responsible for the unknown
     * case, so a sort option dropped in a later version degrades to the
     * default instead of throwing on launch.
     */
    private fun getLibrarySortOptionPreference(): String {
        return prefs.getString(KEY_LIBRARY_SORT_OPTION, LIBRARY_SORT_DEFAULT)
            ?: LIBRARY_SORT_DEFAULT
    }

    /**
     * Save the Library sort order and update the flow. Pass a
     * LibrarySortOption name; existing constants are frozen, since renaming
     * one would silently reset every user's stored choice.
     */
    fun setLibrarySortOption(optionName: String) {
        prefs.edit().putString(KEY_LIBRARY_SORT_OPTION, optionName).apply()
        _librarySortOption.value = optionName
    }

    private fun getOnboardingCompletedPreference(): Boolean {
        return prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
    }

    fun setOnboardingCompleted(completed: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply()
        _onboardingCompleted.value = completed
    }
}

/**
 * Player UI Style options
 */
enum class PlayerStyle {
    /** Classic button-based player with play/pause/next/previous controls */
    CLASSIC,
    /** Gesture-based carousel player with swipe navigation */
    GESTURE,
    /** Two-tone magazine player with die-cut art and a word-pill transport */
    EDITORIAL,
    /** Kinetic type player where the title itself is the progress display */
    POSTER,
    /** Squish grid of flat tonal tiles with press physics */
    BENTO,
    /** Die-cut sticker with drag, peel and squash-and-stretch physics */
    STICKER,
    /** Living hero shape that cycles organic cuts while playing */
    MORPH,
    /** Rotary instrument: a tick-ring dial spun to scrub */
    DIAL
}
