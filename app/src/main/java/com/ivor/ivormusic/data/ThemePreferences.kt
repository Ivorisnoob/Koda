package com.ivor.ivormusic.data

import android.content.Context
import android.content.SharedPreferences
import com.ivor.ivormusic.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Stable destinations in Video mode's bottom navigation. Persist [storageId], never the ordinal. */
enum class VideoHomeDestination(val tabId: Int, val storageId: String) {
    HOME(0, "home"),
    SEARCH(1, "search"),
    SUBSCRIPTIONS(2, "subscriptions"),
    LIBRARY(3, "library");

    companion object {
        val defaultOrder: List<VideoHomeDestination> = entries.toList()

        fun fromStorageId(value: String): VideoHomeDestination? =
            entries.firstOrNull { it.storageId == value }
    }
}

/** One atomic snapshot so navigation never observes a new order with an old visibility set. */
data class VideoHomeConfiguration(
    val recommendationsEnabled: Boolean = true,
    val destinationOrder: List<VideoHomeDestination> = VideoHomeDestination.defaultOrder,
    val visibleDestinations: Set<VideoHomeDestination> = VideoHomeDestination.entries.toSet(),
) {
    val orderedVisibleDestinations: List<VideoHomeDestination>
        get() = destinationOrder.filter { it in visibleDestinations }
}

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

    private val _videoHomeConfiguration = MutableStateFlow(getVideoHomeConfiguration())
    val videoHomeConfiguration: StateFlow<VideoHomeConfiguration> =
        _videoHomeConfiguration.asStateFlow()

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

    private val _videoQualityWifi = MutableStateFlow(getVideoQualityWifiPreference())
    val videoQualityWifi: StateFlow<String> = _videoQualityWifi.asStateFlow()

    private val _videoQualityMobile = MutableStateFlow(getVideoQualityMobilePreference())
    val videoQualityMobile: StateFlow<String> = _videoQualityMobile.asStateFlow()

    private val _preferHdr = MutableStateFlow(getPreferHdrPreference())
    val preferHdr: StateFlow<Boolean> = _preferHdr.asStateFlow()

    private val _captionTextSize = MutableStateFlow(getCaptionTextSizePreference())
    val captionTextSize: StateFlow<Float> = _captionTextSize.asStateFlow()

    private val _captionTextColor = MutableStateFlow(getCaptionTextColorPreference())
    val captionTextColor: StateFlow<CaptionTextColor> = _captionTextColor.asStateFlow()

    private val _captionBackground = MutableStateFlow(getCaptionBackgroundPreference())
    val captionBackground: StateFlow<CaptionBackground> = _captionBackground.asStateFlow()

    // Captions on/off and the chosen language ride the same persistence as the
    // style above, so a user who watches everything subtitled does not have to
    // tap CC again on every video.
    private val _captionsEnabled = MutableStateFlow(prefs.getBoolean(KEY_CAPTIONS_ENABLED, false))
    val captionsEnabled: StateFlow<Boolean> = _captionsEnabled.asStateFlow()

    private val _captionLanguageCode =
        MutableStateFlow(prefs.getString(KEY_CAPTION_LANGUAGE_CODE, null))
    val captionLanguageCode: StateFlow<String?> = _captionLanguageCode.asStateFlow()

    private val _musicQualityWifi = MutableStateFlow(getMusicQualityWifiPreference())
    val musicQualityWifi: StateFlow<String> = _musicQualityWifi.asStateFlow()

    private val _musicQualityMobile = MutableStateFlow(getMusicQualityMobilePreference())
    val musicQualityMobile: StateFlow<String> = _musicQualityMobile.asStateFlow()

    private val _spotlightHome = MutableStateFlow(getSpotlightHomePreference())
    val spotlightHome: StateFlow<Boolean> = _spotlightHome.asStateFlow()

    private val _uiScale = MutableStateFlow(getUiScalePreference())
    val uiScale: StateFlow<Float> = _uiScale.asStateFlow()

    private val _sponsorBlockEnabled = MutableStateFlow(getSponsorBlockEnabledPreference())
    val sponsorBlockEnabled: StateFlow<Boolean> = _sponsorBlockEnabled.asStateFlow()

    private val _sponsorBlockActions = MutableStateFlow(getSponsorBlockActionsPreference())
    val sponsorBlockActions: StateFlow<Map<SponsorCategory, SegmentAction>> =
        _sponsorBlockActions.asStateFlow()

    private val _sponsorBlockShowOnSeekBar =
        MutableStateFlow(getSponsorBlockShowOnSeekBarPreference())
    val sponsorBlockShowOnSeekBar: StateFlow<Boolean> =
        _sponsorBlockShowOnSeekBar.asStateFlow()

    private val _sponsorBlockNotice = MutableStateFlow(getSponsorBlockNoticePreference())
    val sponsorBlockNotice: StateFlow<Boolean> = _sponsorBlockNotice.asStateFlow()

    private val _sponsorBlockMinDurationMs =
        MutableStateFlow(getSponsorBlockMinDurationPreference())
    val sponsorBlockMinDurationMs: StateFlow<Long> = _sponsorBlockMinDurationMs.asStateFlow()

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

    private val _rememberVideoBrightness =
        MutableStateFlow(getRememberVideoBrightness())
    val rememberVideoBrightness: StateFlow<Boolean> = _rememberVideoBrightness.asStateFlow()

    private val _hapticsLevel = MutableStateFlow(getHapticsLevelPreference())
    val hapticsLevel: StateFlow<String> = _hapticsLevel.asStateFlow()

    private val _uploadNotificationsEnabled =
        MutableStateFlow(getUploadNotificationsEnabledPreference())
    val uploadNotificationsEnabled: StateFlow<Boolean> = _uploadNotificationsEnabled.asStateFlow()

    private val _oemFixEnabled = MutableStateFlow(getOemFixEnabledPreference())
    val oemFixEnabled: StateFlow<Boolean> = _oemFixEnabled.asStateFlow()

    private val _manualScanEnabled = MutableStateFlow(getManualScanEnabledPreference())
    val manualScanEnabled: StateFlow<Boolean> = _manualScanEnabled.asStateFlow()

    private val _privateDownloadsEnabled =
        MutableStateFlow(getPrivateDownloadsEnabledPreference())
    val privateDownloadsEnabled: StateFlow<Boolean> =
        _privateDownloadsEnabled.asStateFlow()

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
            KEY_VIDEO_RECOMMENDATIONS_ENABLED,
            KEY_VIDEO_HOME_DESTINATION_ORDER,
            KEY_VIDEO_HOME_VISIBLE_DESTINATIONS ->
                _videoHomeConfiguration.value = getVideoHomeConfiguration()
            KEY_PLAYER_STYLE -> _playerStyle.value = getPlayerStylePreference()
            KEY_SAVE_VIDEO_HISTORY -> _saveVideoHistory.value = getSaveVideoHistoryPreference()
            KEY_SAVE_MUSIC_HISTORY -> _saveMusicHistory.value = getSaveMusicHistoryPreference()
            KEY_LIVE_DOWNLOAD_UPDATES -> _liveDownloadUpdates.value = getLiveDownloadUpdatesPreference()
            KEY_LIVE_PLAYBACK_UPDATES -> _livePlaybackUpdates.value = getLivePlaybackUpdatesPreference()
            KEY_TIMED_COMMENTS_ENABLED -> _timedCommentsEnabled.value = getTimedCommentsEnabledPreference()
            KEY_SHORTS_ENABLED -> _shortsEnabled.value = getShortsEnabledPreference()
            KEY_SHORTS_HIDDEN_ACTIONS -> _shortsHiddenActions.value = getShortsHiddenActionsPreference()
            KEY_VIDEO_QUALITY_WIFI -> _videoQualityWifi.value = getVideoQualityWifiPreference()
            KEY_VIDEO_QUALITY_MOBILE -> _videoQualityMobile.value = getVideoQualityMobilePreference()
            KEY_PREFER_HDR -> _preferHdr.value = getPreferHdrPreference()
            KEY_CAPTION_TEXT_SIZE -> _captionTextSize.value = getCaptionTextSizePreference()
            KEY_CAPTION_TEXT_COLOR -> _captionTextColor.value = getCaptionTextColorPreference()
            KEY_CAPTION_BACKGROUND -> _captionBackground.value = getCaptionBackgroundPreference()
            KEY_MUSIC_QUALITY_WIFI -> _musicQualityWifi.value = getMusicQualityWifiPreference()
            KEY_MUSIC_QUALITY_MOBILE -> _musicQualityMobile.value = getMusicQualityMobilePreference()
            KEY_SPOTLIGHT_HOME -> _spotlightHome.value = getSpotlightHomePreference()
            KEY_UI_SCALE -> _uiScale.value = getUiScalePreference()
            KEY_SPONSORBLOCK_ENABLED ->
                _sponsorBlockEnabled.value = getSponsorBlockEnabledPreference()
            KEY_SPONSORBLOCK_ACTIONS ->
                _sponsorBlockActions.value = getSponsorBlockActionsPreference()
            KEY_SPONSORBLOCK_SEEKBAR ->
                _sponsorBlockShowOnSeekBar.value = getSponsorBlockShowOnSeekBarPreference()
            KEY_SPONSORBLOCK_NOTICE ->
                _sponsorBlockNotice.value = getSponsorBlockNoticePreference()
            KEY_SPONSORBLOCK_MIN_DURATION ->
                _sponsorBlockMinDurationMs.value = getSponsorBlockMinDurationPreference()
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
            KEY_REMEMBER_VIDEO_BRIGHTNESS ->
                _rememberVideoBrightness.value = getRememberVideoBrightness()
            KEY_HAPTICS_LEVEL -> _hapticsLevel.value = getHapticsLevelPreference()
            KEY_UPLOAD_NOTIFICATIONS_ENABLED ->
                _uploadNotificationsEnabled.value = getUploadNotificationsEnabledPreference()
            KEY_OEM_FIX_ENABLED -> _oemFixEnabled.value = getOemFixEnabledPreference()
            KEY_MANUAL_SCAN_ENABLED -> _manualScanEnabled.value = getManualScanEnabledPreference()
            KEY_PRIVATE_DOWNLOADS ->
                _privateDownloadsEnabled.value = getPrivateDownloadsEnabledPreference()
            KEY_ONBOARDING_COMPLETED -> _onboardingCompleted.value = getOnboardingCompletedPreference()
            KEY_LOCAL_ONLY_MODE -> _localOnlyMode.value = getLocalOnlyModePreference()
            KEY_TIME_LIMIT_ENABLED -> _timeLimitEnabled.value = getTimeLimitEnabledPreference()
            KEY_TIME_LIMIT_BUDGETS -> _timeLimitBudgets.value = getTimeLimitBudgetsPreference()
            KEY_LIBRARY_SORT_OPTION -> _librarySortOption.value = getLibrarySortOptionPreference()
        }
    }

    init {
        migrateLoadLocalSongsDefault()
        prefs.registerOnSharedPreferenceChangeListener(prefChangeListener)
    }

    /**
     * Preserve the old `load_local_songs = true` default for installs that
     * predate it becoming false.
     *
     * The Settings toggle is the *only* writer of that key - onboarding never
     * touches it - so an existing user who simply never opened that screen has
     * no stored value and has been running on the old default. Flipping the
     * default alone would empty their library on update with no explanation.
     *
     * `onboarding_completed` is the signal for "this install existed before
     * now", and it cannot be read lazily inside the getter: a fresh user who
     * finishes onboarding would then start reporting true, which is exactly
     * the behaviour this change exists to remove. So it is resolved once,
     * behind its own marker, and written down.
     */
    private fun migrateLoadLocalSongsDefault() {
        if (prefs.contains(KEY_LOAD_LOCAL_SONGS_DEFAULT_MIGRATED)) return
        val isExistingInstall = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
        prefs.edit().apply {
            if (isExistingInstall && !prefs.contains(KEY_LOAD_LOCAL_SONGS)) {
                putBoolean(KEY_LOAD_LOCAL_SONGS, true)
            }
            putBoolean(KEY_LOAD_LOCAL_SONGS_DEFAULT_MIGRATED, true)
        }.apply()
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
        /** One-shot marker for [migrateLoadLocalSongsDefault]. */
        private const val KEY_LOAD_LOCAL_SONGS_DEFAULT_MIGRATED =
            "load_local_songs_default_migrated"
        private const val KEY_AMBIENT_BACKGROUND = "ambient_background"
        private const val KEY_PLAYER_ARTWORK_COLORS = "player_artwork_colors"
        private const val KEY_VIDEO_MODE = "video_mode"
        private const val KEY_LAST_MUSIC_TAB = "last_music_tab"
        private const val KEY_LAST_VIDEO_TAB = "last_video_tab"
        private const val KEY_HOME_MODE_TOGGLE_ENABLED = "home_mode_toggle_enabled"
        private const val KEY_VIDEO_RECOMMENDATIONS_ENABLED =
            "video_recommendations_enabled"
        private const val KEY_VIDEO_HOME_DESTINATION_ORDER =
            "video_home_destination_order"
        private const val KEY_VIDEO_HOME_VISIBLE_DESTINATIONS =
            "video_home_visible_destinations"
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
        private const val KEY_PREFER_HDR = "prefer_hdr_video"
        private const val KEY_CAPTION_TEXT_SIZE = "caption_text_size"
        private const val KEY_CAPTION_TEXT_COLOR = "caption_text_color"
        private const val KEY_CAPTION_BACKGROUND = "caption_background"
        private const val KEY_CAPTIONS_ENABLED = "captions_enabled"
        private const val KEY_CAPTION_LANGUAGE_CODE = "caption_language_code"

        /** Sentinel meaning "highest available quality". */
        const val VIDEO_QUALITY_AUTO = "auto"

        /** Quality labels offered in Settings, best first. */
        val VIDEO_QUALITY_OPTIONS = listOf(
            VIDEO_QUALITY_AUTO, "2160p", "1440p", "1080p", "720p", "480p", "360p", "144p"
        )
        private const val DEFAULT_VIDEO_QUALITY_WIFI = "1080p"
        private const val DEFAULT_VIDEO_QUALITY_MOBILE = "720p"

        /** Fresh read used by both video players at stream-resolution time. */
        fun isPreferHdrEnabled(context: Context): Boolean =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_PREFER_HDR, false)

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
        private const val KEY_UI_SCALE = "ui_scale"
        private const val KEY_SPONSORBLOCK_ENABLED = "sponsorblock_enabled"
        private const val KEY_SPONSORBLOCK_ACTIONS = "sponsorblock_actions"
        private const val KEY_SPONSORBLOCK_SEEKBAR = "sponsorblock_seekbar"
        private const val KEY_SPONSORBLOCK_NOTICE = "sponsorblock_notice"
        private const val KEY_SPONSORBLOCK_MIN_DURATION = "sponsorblock_min_duration"
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
         * App-only download storage. Off preserves Koda's existing public
         * Downloads/Koda behavior; callers fresh-read this when a transfer
         * starts so separate ThemePreferences instances cannot go stale.
         */
        private const val KEY_PRIVATE_DOWNLOADS = "private_downloads"

        fun usePrivateDownloadStorage(context: Context): Boolean =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_PRIVATE_DOWNLOADS, false)

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
        private const val KEY_REMEMBER_VIDEO_BRIGHTNESS = "remember_video_brightness"
        private const val KEY_HAPTICS_LEVEL = "haptics_level"
        private const val KEY_UPLOAD_NOTIFICATIONS_ENABLED = "upload_notifications_enabled"
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
     * Get the stored load local songs preference. Defaults to **false**.
     *
     * Off by default because turning it on is what makes `HomeScreen` ask for
     * the audio permission, and it asked on first render - so someone who
     * skipped onboarding entirely was met by a system permission dialog before
     * they had done anything, for a feature they had never asked for. Koda is
     * a YouTube client first; the device library is opt-in from
     * Settings, and turning it on is what should trigger the request.
     *
     * Only the default moved, and [migrateLoadLocalSongsDefault] keeps
     * existing installs on the old `true`: the Settings toggle is the only
     * writer of this key, so an upgrading user who never opened that screen
     * has nothing stored and would otherwise find their library emptied.
     */
    private fun getLoadLocalSongsPreference(): Boolean {
        return prefs.getBoolean(KEY_LOAD_LOCAL_SONGS, false)
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

    private fun getVideoHomeConfiguration(): VideoHomeConfiguration {
        val defaults = VideoHomeDestination.defaultOrder
        val storedOrder = prefs.getString(KEY_VIDEO_HOME_DESTINATION_ORDER, null)
            ?.split(',')
            .orEmpty()
            .mapNotNull(VideoHomeDestination::fromStorageId)
            .distinct()
        val order = storedOrder + defaults.filterNot { it in storedOrder }

        val visible = prefs.getStringSet(KEY_VIDEO_HOME_VISIBLE_DESTINATIONS, null)
            ?.mapNotNull(VideoHomeDestination::fromStorageId)
            ?.toSet()
            ?.takeIf { it.isNotEmpty() }
            ?: defaults.toSet()

        return VideoHomeConfiguration(
            recommendationsEnabled = prefs.getBoolean(KEY_VIDEO_RECOMMENDATIONS_ENABLED, true),
            destinationOrder = order,
            visibleDestinations = visible,
        )
    }

    /** Hide the network recommendation feed without removing Home itself. */
    fun setVideoRecommendationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VIDEO_RECOMMENDATIONS_ENABLED, enabled).apply()
        _videoHomeConfiguration.value = getVideoHomeConfiguration()
    }

    /** Fresh read for HomeViewModel's fetch and pagination gates. */
    fun areVideoRecommendationsEnabled(): Boolean =
        getVideoHomeConfiguration().recommendationsEnabled

    /** Keep at least one destination visible so the navigation shell remains usable. */
    fun setVideoHomeDestinationVisible(destination: VideoHomeDestination, visible: Boolean) {
        val current = getVideoHomeConfiguration().visibleDestinations
        val updated = if (visible) current + destination else current - destination
        if (updated.isEmpty()) return
        prefs.edit().putStringSet(
            KEY_VIDEO_HOME_VISIBLE_DESTINATIONS,
            updated.mapTo(mutableSetOf()) { it.storageId }
        ).apply()
        _videoHomeConfiguration.value = getVideoHomeConfiguration()
    }

    /** Move one destination by one place; hidden destinations keep their relative order. */
    fun moveVideoHomeDestination(destination: VideoHomeDestination, delta: Int) {
        if (delta == 0) return
        val order = getVideoHomeConfiguration().destinationOrder.toMutableList()
        val from = order.indexOf(destination)
        if (from < 0) return
        val to = (from + delta).coerceIn(0, order.lastIndex)
        if (to == from) return
        order.add(to, order.removeAt(from))
        prefs.edit().putString(
            KEY_VIDEO_HOME_DESTINATION_ORDER,
            order.joinToString(",") { it.storageId }
        ).apply()
        _videoHomeConfiguration.value = getVideoHomeConfiguration()
    }

    /**
     * Get the stored player style preference. Defaults to EDITORIAL.
     *
     * The fallback in the catch is the same constant as the default on purpose:
     * an unreadable stored value should land where a fresh install does, not on
     * a different style than the one someone who never chose would get.
     */
    private fun getPlayerStylePreference(): PlayerStyle {
        val styleName = prefs.getString(KEY_PLAYER_STYLE, PlayerStyle.EDITORIAL.name)
        return try {
            PlayerStyle.valueOf(styleName ?: PlayerStyle.EDITORIAL.name)
        } catch (e: IllegalArgumentException) {
            PlayerStyle.EDITORIAL
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
     * Get the stored Shorts experience preference. Defaults to false: the Home
     * shelf stays hidden and Shorts found elsewhere use the ordinary video
     * player until the user deliberately opts into the endless swipe player.
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
     * Save the Home shelf / swipe-player preference and update the flow.
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

    private fun getPreferHdrPreference(): Boolean =
        prefs.getBoolean(KEY_PREFER_HDR, false)

    fun isPreferHdrEnabled(): Boolean = getPreferHdrPreference()

    fun setPreferHdr(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PREFER_HDR, enabled).apply()
        _preferHdr.value = enabled
    }

    private fun getCaptionTextSizePreference(): Float =
        captionTextScaleFromStored(prefs.all[KEY_CAPTION_TEXT_SIZE])

    private fun getCaptionTextColorPreference(): CaptionTextColor =
        prefs.getString(KEY_CAPTION_TEXT_COLOR, null)
            ?.let { stored -> CaptionTextColor.entries.firstOrNull { it.name == stored } }
            ?: CaptionTextColor.WHITE

    private fun getCaptionBackgroundPreference(): CaptionBackground =
        prefs.getString(KEY_CAPTION_BACKGROUND, null)
            ?.let { stored -> CaptionBackground.entries.firstOrNull { it.name == stored } }
            ?: CaptionBackground.TRANSLUCENT

    fun setCaptionTextSize(size: Float) {
        val safeSize = size.coerceIn(CAPTION_TEXT_SCALE_MIN, CAPTION_TEXT_SCALE_MAX)
        prefs.edit().putFloat(KEY_CAPTION_TEXT_SIZE, safeSize).apply()
        _captionTextSize.value = safeSize
    }

    fun setCaptionTextColor(color: CaptionTextColor) {
        prefs.edit().putString(KEY_CAPTION_TEXT_COLOR, color.name).apply()
        _captionTextColor.value = color
    }

    fun setCaptionBackground(background: CaptionBackground) {
        prefs.edit().putString(KEY_CAPTION_BACKGROUND, background.name).apply()
        _captionBackground.value = background
    }

    fun setCaptionsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CAPTIONS_ENABLED, enabled).apply()
        _captionsEnabled.value = enabled
    }

    /** Fresh pref read for non-composable decision points (ViewModel workers). */
    fun isCaptionsEnabled(): Boolean = prefs.getBoolean(KEY_CAPTIONS_ENABLED, false)

    fun getCaptionLanguageCode(): String? = prefs.getString(KEY_CAPTION_LANGUAGE_CODE, null)

    fun setCaptionLanguageCode(languageCode: String?) {
        if (languageCode == null) {
            prefs.edit().remove(KEY_CAPTION_LANGUAGE_CODE).apply()
        } else {
            prefs.edit().putString(KEY_CAPTION_LANGUAGE_CODE, languageCode).apply()
        }
        _captionLanguageCode.value = languageCode
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

    private fun getUiScalePreference(): Float =
        uiScaleFromStored(prefs.all[KEY_UI_SCALE])

    /**
     * Off until asked for. This is the only third-party service the app
     * contacts, so it is not something to switch on for someone.
     */
    private fun getSponsorBlockEnabledPreference(): Boolean =
        prefs.getBoolean(KEY_SPONSORBLOCK_ENABLED, false)

    private fun getSponsorBlockActionsPreference(): Map<SponsorCategory, SegmentAction> =
        decodeSegmentActions(prefs.getString(KEY_SPONSORBLOCK_ACTIONS, null))

    private fun getSponsorBlockShowOnSeekBarPreference(): Boolean =
        prefs.getBoolean(KEY_SPONSORBLOCK_SEEKBAR, true)

    private fun getSponsorBlockNoticePreference(): Boolean =
        prefs.getBoolean(KEY_SPONSORBLOCK_NOTICE, true)

    private fun getSponsorBlockMinDurationPreference(): Long =
        prefs.getLong(KEY_SPONSORBLOCK_MIN_DURATION, 0L)
            .coerceIn(0L, SPONSORBLOCK_MAX_MIN_DURATION_MS)

    fun setSponsorBlockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SPONSORBLOCK_ENABLED, enabled).apply()
        _sponsorBlockEnabled.value = enabled
    }

    fun setSponsorBlockAction(category: SponsorCategory, action: SegmentAction) {
        val updated = _sponsorBlockActions.value.toMutableMap().apply { put(category, action) }
        prefs.edit().putString(KEY_SPONSORBLOCK_ACTIONS, encodeSegmentActions(updated)).apply()
        _sponsorBlockActions.value = updated
    }

    fun resetSponsorBlockActions() {
        val defaults = SponsorCategory.defaultActions()
        prefs.edit().putString(KEY_SPONSORBLOCK_ACTIONS, encodeSegmentActions(defaults)).apply()
        _sponsorBlockActions.value = defaults
    }

    fun setSponsorBlockShowOnSeekBar(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SPONSORBLOCK_SEEKBAR, enabled).apply()
        _sponsorBlockShowOnSeekBar.value = enabled
    }

    fun setSponsorBlockNotice(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SPONSORBLOCK_NOTICE, enabled).apply()
        _sponsorBlockNotice.value = enabled
    }

    fun setSponsorBlockMinDurationMs(durationMs: Long) {
        val safe = durationMs.coerceIn(0L, SPONSORBLOCK_MAX_MIN_DURATION_MS)
        prefs.edit().putLong(KEY_SPONSORBLOCK_MIN_DURATION, safe).apply()
        _sponsorBlockMinDurationMs.value = safe
    }

    /** Fresh read for the player, which must not use this instance's stale flow. */
    fun isSponsorBlockEnabled(): Boolean = getSponsorBlockEnabledPreference()

    fun sponsorBlockActionsNow(): Map<SponsorCategory, SegmentAction> =
        getSponsorBlockActionsPreference()

    fun sponsorBlockMinDurationNow(): Long = getSponsorBlockMinDurationPreference()

    /**
     * Save the interface scale and update the flow.
     *
     * Coerced on the way in as well as on the way out: the slider is the only
     * writer today, but a restored backup carries whatever the device that
     * wrote it allowed, and a value outside the range would reach
     * [androidx.compose.ui.unit.Density] unchecked.
     */
    fun setUiScale(scale: Float) {
        val safeScale = scale.coerceIn(UI_SCALE_MIN, UI_SCALE_MAX)
        prefs.edit().putFloat(KEY_UI_SCALE, safeScale).apply()
        _uiScale.value = safeScale
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

    private fun getPrivateDownloadsEnabledPreference(): Boolean =
        prefs.getBoolean(KEY_PRIVATE_DOWNLOADS, false)

    fun setPrivateDownloadsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PRIVATE_DOWNLOADS, enabled).apply()
        _privateDownloadsEnabled.value = enabled
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
     * Whether the fullscreen brightness drag should carry over to the next
     * fullscreen video (default). Off, every video reopens at the system
     * brightness and the gesture's level dies with the surface. The stored
     * level itself is kept either way so turning the setting back on restores
     * what the user last dialed in.
     */
    fun getRememberVideoBrightness(): Boolean =
        prefs.getBoolean(KEY_REMEMBER_VIDEO_BRIGHTNESS, true)

    fun setRememberVideoBrightness(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_REMEMBER_VIDEO_BRIGHTNESS, enabled).apply()
        _rememberVideoBrightness.value = enabled
    }

    /**
     * Touch feedback intensity for the whole app - one of the values
     * [com.ivor.ivormusic.util.HapticsLevel] writes via [toPref]. Every
     * haptic in the app routes through it, so this is the single switch.
     */
    private fun getHapticsLevelPreference(): String =
        prefs.getString(KEY_HAPTICS_LEVEL, com.ivor.ivormusic.util.HapticsLevel.DEFAULT)
            ?: com.ivor.ivormusic.util.HapticsLevel.DEFAULT

    fun setHapticsLevel(value: String) {
        prefs.edit().putString(KEY_HAPTICS_LEVEL, value).apply()
        _hapticsLevel.value = value
    }

    /**
     * Whether the background check may notify about new uploads from channels
     * followed on this device. Off by default: it is a battery-and-attention
     * commitment, and its periodic work is canceled while this is false.
     */
    fun getUploadNotificationsEnabled(): Boolean =
        prefs.getBoolean(KEY_UPLOAD_NOTIFICATIONS_ENABLED, false)

    private fun getUploadNotificationsEnabledPreference(): Boolean =
        prefs.getBoolean(KEY_UPLOAD_NOTIFICATIONS_ENABLED, false)

    fun setUploadNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_UPLOAD_NOTIFICATIONS_ENABLED, enabled).apply()
        _uploadNotificationsEnabled.value = enabled
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
        return prefs.getLong(KEY_MAX_CACHE_SIZE_MB, CacheManager.DEFAULT_CACHE_SIZE_MB)
            .coerceIn(CacheManager.MIN_CACHE_SIZE_MB, CacheManager.MAX_CACHE_SIZE_MB)
    }
    
    fun setMaxCacheSizeMb(sizeMb: Long) {
        // Clamped here rather than at the settings slider, because a restored
        // backup and a build with a different ceiling both write through this
        // one setter and neither passes a slider stop.
        val clamped = sizeMb.coerceIn(
            CacheManager.MIN_CACHE_SIZE_MB,
            CacheManager.MAX_CACHE_SIZE_MB
        )
        prefs.edit().putLong(KEY_MAX_CACHE_SIZE_MB, clamped).apply()
        _maxCacheSizeMb.value = clamped
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
 * Ceiling for the "ignore very short segments" filter. Two seconds is already
 * past the point where a skip is less disruptive than the segment, and a
 * larger value would start hiding the sponsor reads the feature exists for.
 */
const val SPONSORBLOCK_MAX_MIN_DURATION_MS = 2_000L

/* ------------------------------------------------------------------ */
/* Interface scale                                                     */
/* ------------------------------------------------------------------ */

/**
 * How far the whole interface may shrink, matching the smallest stop on the
 * platform's own Display size control. It is a floor rather than a preference:
 * the scale multiplies every dp in the app, so 0.85 already takes a 48dp touch
 * target down to the physical size of 40.8dp, and going further would put the
 * app's controls under the minimum a finger can reliably hit. Someone who
 * needs smaller still can stack the system Display size setting underneath,
 * which is their explicit choice rather than one Koda made for them.
 */
const val UI_SCALE_MIN = 0.85f

const val UI_SCALE_DEFAULT = 1f

/**
 * The ceiling is deliberately nearer the default than the floor is. Growing
 * the interface is what the system font and display size settings already do
 * well, and Koda's layouts have far less slack upward - see the bottom-sheet
 * ceiling in VideoOptionsSheet, which scaling up eats into.
 */
const val UI_SCALE_MAX = 1.15f

/** The stops the slider snaps to, and the only values ever written. */
val UI_SCALE_STEPS: List<Float> =
    listOf(0.85f, 0.90f, 0.95f, 1.00f, 1.05f, 1.10f, 1.15f)

/**
 * Read the stored scale, tolerating anything. Kept outside SharedPreferences
 * so it is deterministic and unit-testable without an Android Context, the
 * same way [captionTextScaleFromStored] is.
 */
internal fun uiScaleFromStored(stored: Any?): Float = when (stored) {
    is Number -> stored.toFloat()
    else -> UI_SCALE_DEFAULT
}.coerceIn(UI_SCALE_MIN, UI_SCALE_MAX)

/** Snaps an arbitrary slider position onto the nearest stop. */
fun nearestUiScaleStep(value: Float): Float =
    UI_SCALE_STEPS.minByOrNull { kotlin.math.abs(it - value) } ?: UI_SCALE_DEFAULT

const val CAPTION_TEXT_SCALE_MIN = 0.75f
const val CAPTION_TEXT_SCALE_DEFAULT = 1f

/** Twice the former Large preset (1.25x), for high-density displays. */
const val CAPTION_TEXT_SCALE_MAX = 2.5f

/**
 * Read both the new float and the three names written by Koda 4.6. Keeping the
 * conversion outside SharedPreferences makes the migration deterministic and
 * unit-testable without an Android Context.
 */
internal fun captionTextScaleFromStored(stored: Any?): Float = when (stored) {
    is Number -> stored.toFloat()
    "SMALL" -> CAPTION_TEXT_SCALE_MIN
    "MEDIUM" -> CAPTION_TEXT_SCALE_DEFAULT
    "LARGE" -> 1.25f
    else -> CAPTION_TEXT_SCALE_DEFAULT
}.coerceIn(CAPTION_TEXT_SCALE_MIN, CAPTION_TEXT_SCALE_MAX)

/** Video-safe foreground choices, paired with the caption background. */
enum class CaptionTextColor {
    WHITE,
    YELLOW
}

/** Strength of the black caption plate drawn over the video frame. */
enum class CaptionBackground {
    NONE,
    TRANSLUCENT,
    SOLID
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
