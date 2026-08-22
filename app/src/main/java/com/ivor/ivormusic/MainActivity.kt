package com.ivor.ivormusic

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import com.ivor.ivormusic.data.VideoItem
import com.ivor.ivormusic.ui.home.HomeScreen
import com.ivor.ivormusic.ui.home.HomeViewModel
import com.ivor.ivormusic.ui.player.PlayerViewModel
import com.ivor.ivormusic.ui.theme.IvorMusicTheme
import com.ivor.ivormusic.ui.theme.ThemeViewModel
import com.ivor.ivormusic.data.PlayerStyle
import androidx.compose.ui.unit.dp


import androidx.compose.foundation.isSystemInDarkTheme
import com.ivor.ivormusic.ui.theme.ThemeMode

import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.ivor.ivormusic.ui.onboarding.OnboardingScreen
import com.ivor.ivormusic.ui.video.enterPipMode
import com.ivor.ivormusic.ui.share.PendingSharedLink
import com.ivor.ivormusic.ui.share.SharedLinkHandler
import com.ivor.ivormusic.ui.share.sharedLinkText

/**
 * Height the floating navigation bar occupies at the bottom of the Home
 * screen, above the system navigation inset: the toolbar itself plus the 20dp
 * it is padded away from the inset.
 *
 * Here rather than in `HomeScreen` because the thing that needs it is the
 * video overlay, which is drawn above the NavHost and cannot see inside the
 * screen that draws the bar.
 */
private val EXPRESSIVE_NAV_BAR_RESERVE = 84.dp

/** Material 3's standard navigation container height, excluding system insets. */
private val NON_EXPRESSIVE_NAV_BAR_RESERVE = 80.dp

/** Height the collapsed music player occupies above the navigation bar. */
private val MUSIC_PILL_RESERVE = 88.dp

class MainActivity : ComponentActivity() {

    // A YouTube link shared or opened into Koda, picked up by SharedLinkHandler
    // inside the composition. Snapshot state so a link arriving while the app is
    // already running reaches the UI without restarting anything.
    private var pendingSharedLink by androidx.compose.runtime.mutableStateOf<PendingSharedLink?>(null)
    private var sharedLinkCounter = 0L

    // True while the app is in system Picture-in-Picture. Held here rather than
    // inside the video overlay because the whole app has to stand down in PiP:
    // the NavHost used to keep composing and animating behind the window, and
    // any gap around the video showed app chrome instead of black.
    private var isInPipMode by androidx.compose.runtime.mutableStateOf(false)

    // Set by the video player so onUserLeaveHint can enter PiP with the right
    // window shape on Android 11, where setAutoEnterEnabled does not exist.
    private var pipVideoAspectRatio: Float? = null
    private var pipVideoBounds: android.graphics.Rect? = null
    private var pipEligible = false

    /**
     * The daily time limit's lock state, evaluated by [tickAppTime] every 30
     * seconds while foregrounded (and once at start). Snapshot state so the
     * overlay in MusicApp reacts without any flow plumbing; the underlying
     * decision always fresh-reads preferences, so budget changes apply on the
     * next tick.
     */
    private var appTimeLocked by androidx.compose.runtime.mutableStateOf(false)
    private var foregroundedAtMs = 0L
    private var appTimeTicker: kotlinx.coroutines.Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        takeSharedLink(intent)

        // Remove splash instantly when ready — the AVD entrance animation is the show
        splashScreen.setOnExitAnimationListener { it.remove() }

        // The app is portrait-only, like YouTube: rotating the device must not
        // rotate the app UI. The only exception is fullscreen video playback,
        // which temporarily requests landscape from VideoPlayerContent and
        // restores portrait when it exits.
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        enableEdgeToEdge()

        setContent {
            val themeViewModel: ThemeViewModel = viewModel()
            val themeMode by themeViewModel.themeMode.collectAsState()
            val amoledTheme by themeViewModel.amoledTheme.collectAsState()
            val colorPalette by themeViewModel.colorPalette.collectAsState()
            val loadLocalSongs by themeViewModel.loadLocalSongs.collectAsState()
            val ambientBackground by themeViewModel.ambientBackground.collectAsState()
            val playerArtworkColors by themeViewModel.playerArtworkColors.collectAsState()
            val videoMode by themeViewModel.videoMode.collectAsState()
            val homeModeToggleEnabled by themeViewModel.homeModeToggleEnabled.collectAsState()
            val playerStyle by themeViewModel.playerStyle.collectAsState()
            val saveVideoHistory by themeViewModel.saveVideoHistory.collectAsState()
            val saveMusicHistory by themeViewModel.saveMusicHistory.collectAsState()
            val liveDownloadUpdates by themeViewModel.liveDownloadUpdates.collectAsState()
            val livePlaybackUpdates by themeViewModel.livePlaybackUpdates.collectAsState()
            val timedCommentsEnabled by themeViewModel.timedCommentsEnabled.collectAsState()
            val shortsEnabled by themeViewModel.shortsEnabled.collectAsState()
            val shortsHiddenActions by themeViewModel.shortsHiddenActions.collectAsState()
            val videoQualityWifi by themeViewModel.videoQualityWifi.collectAsState()
            val videoQualityMobile by themeViewModel.videoQualityMobile.collectAsState()
            val musicQualityWifi by themeViewModel.musicQualityWifi.collectAsState()
            val musicQualityMobile by themeViewModel.musicQualityMobile.collectAsState()
            val spotlightHome by themeViewModel.spotlightHome.collectAsState()
            val nonExpressiveNavigationBar by
                themeViewModel.nonExpressiveNavigationBar.collectAsState()
            val subscriptionSource by themeViewModel.subscriptionSource.collectAsState()
            val subscribeTarget by themeViewModel.subscribeTarget.collectAsState()
            val fastSubscriptionFeed by themeViewModel.fastSubscriptionFeed.collectAsState()
            val excludedFolders by themeViewModel.excludedFolders.collectAsState()
            val oemFixEnabled by themeViewModel.oemFixEnabled.collectAsState()
            val manualScanEnabled by themeViewModel.manualScanEnabled.collectAsState()
            val onboardingCompleted by themeViewModel.onboardingCompleted.collectAsState()
            val localOnlyMode by themeViewModel.localOnlyMode.collectAsState()
            
            val cacheEnabled by themeViewModel.cacheEnabled.collectAsState()
            val maxCacheSizeMb by themeViewModel.maxCacheSizeMb.collectAsState()
            val currentCacheSize by themeViewModel.currentCacheSizeBytes.collectAsState()
            val autoLoadQueue by themeViewModel.autoLoadQueue.collectAsState()
            val crossfadeEnabled by themeViewModel.crossfadeEnabled.collectAsState()
            val crossfadeAuto by themeViewModel.crossfadeAuto.collectAsState()
            val crossfadeDurationMs by themeViewModel.crossfadeDurationMs.collectAsState()
            val normalizeVolume by themeViewModel.normalizeVolume.collectAsState()
            
            val isSystemDark = isSystemInDarkTheme()
            val isDarkTheme = remember(themeMode, isSystemDark) {
                when (themeMode) {
                    ThemeMode.SYSTEM -> isSystemDark
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                }
            }
            
            IvorMusicTheme(darkTheme = isDarkTheme, colorPalette = colorPalette, amoledDark = amoledTheme) {
                Box(modifier = Modifier.fillMaxSize()) {
                    MusicApp(
                        pendingSharedLink = pendingSharedLink,
                        isInPipMode = isInPipMode,
                        onPipStateChanged = { eligible, aspectRatio, bounds ->
                            pipEligible = eligible
                            pipVideoAspectRatio = aspectRatio
                            pipVideoBounds = bounds
                        },
                        currentThemeMode = themeMode,
                        onThemeModeChange = { themeViewModel.setThemeMode(it) },
                        amoledTheme = amoledTheme,
                        onAmoledThemeToggle = { themeViewModel.setAmoledTheme(it) },
                        colorPalette = colorPalette,
                        onColorPaletteChange = { themeViewModel.setColorPalette(it) },
                        isDarkMode = isDarkTheme, // Derived for compatibility
                        onThemeToggle = { isDark ->
                            themeViewModel.setThemeMode(if (isDark) ThemeMode.DARK else ThemeMode.LIGHT)
                        },
                        loadLocalSongs = loadLocalSongs,
                        onLoadLocalSongsToggle = { themeViewModel.setLoadLocalSongs(it) },
                        ambientBackground = ambientBackground,
                        onAmbientBackgroundToggle = { themeViewModel.setAmbientBackground(it) },
                        playerArtworkColors = playerArtworkColors,
                        onPlayerArtworkColorsToggle = { themeViewModel.setPlayerArtworkColors(it) },
                        videoMode = videoMode,
                        onVideoModeToggle = { themeViewModel.setVideoMode(it) },
                        homeModeToggleEnabled = homeModeToggleEnabled,
                        onHomeModeToggleEnabledChange = { themeViewModel.setHomeModeToggleEnabled(it) },
                        spotlightHome = spotlightHome,
                        onSpotlightHomeToggle = { themeViewModel.setSpotlightHome(it) },
                        nonExpressiveNavigationBar = nonExpressiveNavigationBar,
                        onNonExpressiveNavigationBarToggle = {
                            themeViewModel.setNonExpressiveNavigationBar(it)
                        },
                        playerStyle = playerStyle,
                        onPlayerStyleChange = { themeViewModel.setPlayerStyle(it) },
                        saveVideoHistory = saveVideoHistory,
                        onSaveVideoHistoryToggle = { themeViewModel.setSaveVideoHistory(it) },
                        saveMusicHistory = saveMusicHistory,
                        onSaveMusicHistoryToggle = { themeViewModel.setSaveMusicHistory(it) },
                        liveDownloadUpdates = liveDownloadUpdates,
                        onLiveDownloadUpdatesToggle = { themeViewModel.setLiveDownloadUpdates(it) },
                        livePlaybackUpdates = livePlaybackUpdates,
                        onLivePlaybackUpdatesToggle = { themeViewModel.setLivePlaybackUpdates(it) },
                        timedCommentsEnabled = timedCommentsEnabled,
                        onTimedCommentsToggle = { themeViewModel.setTimedCommentsEnabled(it) },
                        shortsEnabled = shortsEnabled,
                        onShortsEnabledToggle = { themeViewModel.setShortsEnabled(it) },
                        shortsHiddenActions = shortsHiddenActions,
                        onShortsHiddenActionsChange = { themeViewModel.setShortsHiddenActions(it) },
                        videoQualityWifi = videoQualityWifi,
                        onVideoQualityWifiChange = { themeViewModel.setVideoQualityWifi(it) },
                        videoQualityMobile = videoQualityMobile,
                        onVideoQualityMobileChange = { themeViewModel.setVideoQualityMobile(it) },
                        musicQualityWifi = musicQualityWifi,
                        onMusicQualityWifiChange = { themeViewModel.setMusicQualityWifi(it) },
                        musicQualityMobile = musicQualityMobile,
                        onMusicQualityMobileChange = { themeViewModel.setMusicQualityMobile(it) },
                        subscriptionSource = subscriptionSource,
                        onSubscriptionSourceChange = { themeViewModel.setSubscriptionSource(it) },
                        subscribeTarget = subscribeTarget,
                        onSubscribeTargetChange = { themeViewModel.setSubscribeTarget(it) },
                        fastSubscriptionFeed = fastSubscriptionFeed,
                        onFastSubscriptionFeedToggle = { themeViewModel.setFastSubscriptionFeed(it) },
                        excludedFolders = excludedFolders,
                        onAddExcludedFolder = { themeViewModel.addExcludedFolder(it) },
                        onRemoveExcludedFolder = { themeViewModel.removeExcludedFolder(it) },
                        oemFixEnabled = oemFixEnabled,
                        onOemFixEnabledToggle = { themeViewModel.setOemFixEnabled(it) },
                        manualScanEnabled = manualScanEnabled,
                        onManualScanEnabledToggle = { themeViewModel.setManualScanEnabled(it) },
                        cacheEnabled = cacheEnabled,
                        onCacheEnabledToggle = { themeViewModel.setCacheEnabled(it) },
                        maxCacheSizeMb = maxCacheSizeMb,
                        onMaxCacheSizeMbChange = { themeViewModel.setMaxCacheSizeMb(it) },
                        currentCacheSize = currentCacheSize,
                        onClearCacheClick = { themeViewModel.clearCacheAction() },
                        autoLoadQueue = autoLoadQueue,
                        onAutoLoadQueueToggle = { themeViewModel.setAutoLoadQueue(it) },
                        crossfadeEnabled = crossfadeEnabled,
                        onCrossfadeEnabledToggle = { themeViewModel.setCrossfadeEnabled(it) },
                        crossfadeAuto = crossfadeAuto,
                        onCrossfadeAutoChange = { themeViewModel.setCrossfadeAuto(it) },
                        crossfadeDurationMs = crossfadeDurationMs,
                        onCrossfadeDurationChange = { themeViewModel.setCrossfadeDuration(it) },
                        normalizeVolume = normalizeVolume,
                        onNormalizeVolumeToggle = { themeViewModel.setNormalizeVolume(it) },
                        onboardingCompleted = onboardingCompleted,
                        onOnboardingCompleted = { themeViewModel.setOnboardingCompleted(it) },
                        localOnlyMode = localOnlyMode,
                        onLocalOnlyModeToggle = { themeViewModel.setLocalOnlyMode(it) }
                    )
                }
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: android.content.res.Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipMode = isInPictureInPictureMode
    }

    /**
     * Daily time limit bookkeeping. STARTED is the whole definition of
     * "using Koda" here: foregrounded, on screen, in front of the user.
     * PiP counts as background - the video is a floating window, not app use.
     */
    override fun onStart() {
        super.onStart()
        foregroundedAtMs = System.currentTimeMillis()
        tickAppTime()
        appTimeTicker = lifecycleScope.launch {
            while (isActive) {
                delay(30_000L)
                chargeForegroundTime()
                tickAppTime()
            }
        }
    }

    override fun onStop() {
        appTimeTicker?.cancel()
        appTimeTicker = null
        chargeForegroundTime()
        super.onStop()
    }

    /** Flush the time since the last marker into today's total. */
    private fun chargeForegroundTime() {
        val now = System.currentTimeMillis()
        if (foregroundedAtMs == 0L || appTimeLocked) {
            // Locked sessions are deliberately uncounted: sitting on the lock
            // screen must not eat the budget, and the marker still moves so a
            // midnight rollover or budget raise resumes cleanly.
            foregroundedAtMs = now
            return
        }
        com.ivor.ivormusic.data.AppTimeLimit.addForegroundMillis(this, now - foregroundedAtMs)
        foregroundedAtMs = now
    }

    private fun tickAppTime() {
        val prefs = com.ivor.ivormusic.data.ThemePreferences(applicationContext)
        appTimeLocked = com.ivor.ivormusic.data.AppTimeLimit.isLocked(
            this,
            prefs.isTimeLimitEnabled(),
            prefs.getTimeLimitBudgets()
        )
    }

    /**
     * Entering PiP on the way out of the app.
     *
     * On API 31+ the system does this itself from setAutoEnterEnabled, which
     * handles the gesture-nav swipe up as well and animates better, so this
     * only covers Android 11 and 12 where that flag does not exist.
     */
    @Deprecated("Deprecated in Java")
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return
        if (!pipEligible || isInPipMode) return
        enterPipMode(this, pipVideoAspectRatio, pipVideoBounds)
    }

    /**
     * A link shared into Koda while it was already running is delivered here
     * rather than through a fresh onCreate, thanks to singleTop.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        takeSharedLink(intent)
    }

    /**
     * Pick up the YouTube link an intent carries, if it has one, and neutralize
     * the intent so it cannot fire twice - the activity is recreated on theme
     * and locale changes, and would otherwise replay the same link each time.
     */
    private fun takeSharedLink(intent: Intent?) {
        val text = intent?.sharedLinkText() ?: return
        intent.action = Intent.ACTION_MAIN
        intent.data = null
        intent.removeExtra(Intent.EXTRA_TEXT)
        pendingSharedLink = PendingSharedLink(text, ++sharedLinkCounter)
    }
}

@Composable
fun MusicApp(
    pendingSharedLink: PendingSharedLink?,
    isInPipMode: Boolean,

    /**
     * The daily time limit's lock. Owned by the activity's ticker rather
     * than a flow: the decision fresh-reads preferences every tick, and the
     * only consumer is this overlay.
     */
    appTimeLocked: Boolean = false,
    onPipStateChanged: (eligible: Boolean, aspectRatio: Float?, bounds: android.graphics.Rect?) -> Unit,
    currentThemeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    amoledTheme: Boolean,
    onAmoledThemeToggle: (Boolean) -> Unit,
    colorPalette: String,
    onColorPaletteChange: (String) -> Unit,
    isDarkMode: Boolean,
    onThemeToggle: (Boolean) -> Unit,
    loadLocalSongs: Boolean,
    onLoadLocalSongsToggle: (Boolean) -> Unit,
    ambientBackground: Boolean,
    onAmbientBackgroundToggle: (Boolean) -> Unit,
    playerArtworkColors: Boolean,
    onPlayerArtworkColorsToggle: (Boolean) -> Unit,
    videoMode: Boolean,
    onVideoModeToggle: (Boolean) -> Unit,
    homeModeToggleEnabled: Boolean,
    onHomeModeToggleEnabledChange: (Boolean) -> Unit,
    spotlightHome: Boolean,
    onSpotlightHomeToggle: (Boolean) -> Unit,
    nonExpressiveNavigationBar: Boolean,
    onNonExpressiveNavigationBarToggle: (Boolean) -> Unit,
    playerStyle: PlayerStyle,
    onPlayerStyleChange: (PlayerStyle) -> Unit,
    saveVideoHistory: Boolean,
    onSaveVideoHistoryToggle: (Boolean) -> Unit,
    saveMusicHistory: Boolean,
    onSaveMusicHistoryToggle: (Boolean) -> Unit,
    liveDownloadUpdates: Boolean,
    onLiveDownloadUpdatesToggle: (Boolean) -> Unit,
    livePlaybackUpdates: Boolean,
    onLivePlaybackUpdatesToggle: (Boolean) -> Unit,
    timedCommentsEnabled: Boolean,
    onTimedCommentsToggle: (Boolean) -> Unit,
    shortsEnabled: Boolean,
    onShortsEnabledToggle: (Boolean) -> Unit,
    shortsHiddenActions: Set<String>,
    onShortsHiddenActionsChange: (Set<String>) -> Unit,
    videoQualityWifi: String,
    onVideoQualityWifiChange: (String) -> Unit,
    videoQualityMobile: String,
    onVideoQualityMobileChange: (String) -> Unit,
    musicQualityWifi: String,
    onMusicQualityWifiChange: (String) -> Unit,
    musicQualityMobile: String,
    onMusicQualityMobileChange: (String) -> Unit,
    subscriptionSource: String,
    onSubscriptionSourceChange: (String) -> Unit,
    subscribeTarget: String,
    onSubscribeTargetChange: (String) -> Unit,
    fastSubscriptionFeed: Boolean,
    onFastSubscriptionFeedToggle: (Boolean) -> Unit,
    excludedFolders: Set<String>,
    onAddExcludedFolder: (String) -> Unit,
    onRemoveExcludedFolder: (String) -> Unit,
    cacheEnabled: Boolean,
    onCacheEnabledToggle: (Boolean) -> Unit,
    maxCacheSizeMb: Long,
    onMaxCacheSizeMbChange: (Long) -> Unit,
    currentCacheSize: Long,
    onClearCacheClick: () -> Unit,
    autoLoadQueue: Boolean,
    onAutoLoadQueueToggle: (Boolean) -> Unit,
    crossfadeEnabled: Boolean,
    onCrossfadeEnabledToggle: (Boolean) -> Unit,
    crossfadeAuto: Boolean,
    onCrossfadeAutoChange: (Boolean) -> Unit,
    crossfadeDurationMs: Int,
    onCrossfadeDurationChange: (Int) -> Unit,
    normalizeVolume: Boolean,
    onNormalizeVolumeToggle: (Boolean) -> Unit,
    oemFixEnabled: Boolean,
    onOemFixEnabledToggle: (Boolean) -> Unit,
    manualScanEnabled: Boolean,
    onManualScanEnabledToggle: (Boolean) -> Unit,
    onboardingCompleted: Boolean,
    onOnboardingCompleted: (Boolean) -> Unit,
    localOnlyMode: Boolean,
    onLocalOnlyModeToggle: (Boolean) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val navController = rememberNavController()
    // Scope the player VM to the ViewModelStore so it survives configuration
    // changes and onCleared() actually runs (releasing the MediaController).
    // Application context is used so the Activity isn't retained.
    val playerViewModel: PlayerViewModel = viewModel {
        PlayerViewModel(context.applicationContext)
    }
    val homeViewModel: HomeViewModel = viewModel()

    val videoPlayerViewModel: com.ivor.ivormusic.ui.video.VideoPlayerViewModel = viewModel()
    val shortsPlayerViewModel: com.ivor.ivormusic.ui.shorts.ShortsPlayerViewModel = viewModel()

    // A live broadcast that turned up in the Shorts feed. The Shorts player
    // cannot present one honestly (no chat, and a seek bar for a duration that
    // does not exist), so it closes itself and the stream reopens here, where
    // the vertical live layout lives.
    androidx.compose.runtime.LaunchedEffect(shortsPlayerViewModel) {
        shortsPlayerViewModel.liveHandoff.collect { liveVideo ->
            videoPlayerViewModel.playVideo(liveVideo)
        }
    }

    /**
     * Opens a creator's page from anywhere: a feed card, a search result, the
     * player's channel row, the Subscriptions tab, or a shared link.
     *
     * **The two overlays step out of the way rather than being drawn over.**
     * The channel screen is a NavHost destination, and both players live above
     * the NavHost, so an expanded video player would simply cover it. Dropping
     * the video to its mini bar is also the behaviour worth having on its own
     * merits: the video keeps playing while its creator's page is read, which
     * is exactly what someone tapping a channel name mid-video wants. Shorts
     * close outright, because that overlay is full-bleed with no minimised form
     * to fall back to.
     *
     * `launchSingleTop` is deliberately absent: opening channel B from channel
     * A's "Featured channels" shelf has to push a second entry, or back from B
     * would leave the app rather than return to A.
     */
    val openChannel: (String) -> Unit = openChannel@{ channelId ->
        if (channelId.isBlank()) return@openChannel
        videoPlayerViewModel.setExpanded(false)
        shortsPlayerViewModel.close()
        navController.navigate("channel/${android.net.Uri.encode(channelId)}")
    }

    // Music, video and Shorts are mutually exclusive: whichever pipeline
    // starts playing pauses the other two. System audio focus alone is not
    // reliable between players inside the same app, so this is enforced
    // explicitly. Each effect only fires on a transition to playing, so
    // pausing one player never re-triggers the others.
    val isMusicPlaying by playerViewModel.isPlaying.collectAsState()
    val isVideoPlaying by videoPlayerViewModel.isPlaying.collectAsState()
    val isShortsPlaying by shortsPlayerViewModel.isPlaying.collectAsState()
    androidx.compose.runtime.LaunchedEffect(isMusicPlaying) {
        if (isMusicPlaying) {
            videoPlayerViewModel.pause()
            shortsPlayerViewModel.pause()
        }
    }
    androidx.compose.runtime.LaunchedEffect(isVideoPlaying) {
        if (isVideoPlaying) {
            playerViewModel.pause()
            shortsPlayerViewModel.pause()
        }
    }
    androidx.compose.runtime.LaunchedEffect(isShortsPlaying) {
        if (isShortsPlaying) {
            playerViewModel.pause()
            videoPlayerViewModel.pause()
        }
    }

    // Video overlay state, needed by HomeScreen so bottom-anchored UI (FABs)
    // and the music mini player can stay clear of the video mini player.
    val overlayVideo by videoPlayerViewModel.currentVideo.collectAsState()
    val isVideoOverlayExpanded by videoPlayerViewModel.isExpanded.collectAsState()
    val hasVideoMiniPlayer = overlayVideo != null && !isVideoOverlayExpanded
    val musicPillVisible = playerViewModel.currentSong.collectAsState().value != null

    // The floating nav bar and the music pill both live inside HomeScreen, so
    // they exist on the "home" route and nowhere else. The video overlay is
    // drawn above the NavHost and therefore renders on every route, so it has
    // to be told what is actually underneath it rather than assuming: reserving
    // their height unconditionally is what left the video mini bar hovering in
    // empty space over Settings, Downloads, Stats and channel pages.
    val currentRoute = navController.currentBackStackEntryAsState()
        .value?.destination?.route
    val onHomeRoute = currentRoute == "home"
    val navBarReserve = if (nonExpressiveNavigationBar) {
        NON_EXPRESSIVE_NAV_BAR_RESERVE
    } else {
        EXPRESSIVE_NAV_BAR_RESERVE
    }
    val videoMiniBottomChrome = when {
        !onHomeRoute -> 0.dp
        // Stacked above the music pill rather than on top of it, when both
        // players are alive at once.
        musicPillVisible -> navBarReserve + MUSIC_PILL_RESERVE
        else -> navBarReserve
    }

    // Keep the Activity's PiP inputs current. It needs them outside the
    // composition, in onUserLeaveHint, where there is no way to read state.
    val pipAspectRatio by videoPlayerViewModel.videoAspectRatio.collectAsState()
    val pipBounds by videoPlayerViewModel.videoSurfaceBounds.collectAsState()
    androidx.compose.runtime.LaunchedEffect(
        overlayVideo, isVideoOverlayExpanded, pipAspectRatio, pipBounds
    ) {
        onPipStateChanged(
            overlayVideo != null && isVideoOverlayExpanded,
            pipAspectRatio,
            pipBounds
        )
    }

    // Keep the ViewModel's PiP flag current so it can suppress auto-play
    // (advancing to the next video while in PiP means the user returns to
    // a video they did not put there).
    androidx.compose.runtime.LaunchedEffect(isInPipMode) {
        videoPlayerViewModel.setInPipMode(isInPipMode)
    }

    // Opens YouTube links shared into the app. Composed above the PiP
    // early return so its remembered deduplication token survives PiP
    // transitions; disabled while in PiP so it does not try to navigate
    // or start a new video inside the tiny window.
    SharedLinkHandler(
        pendingLink = pendingSharedLink,
        enabled = onboardingCompleted && !isInPipMode,
        localOnlyMode = localOnlyMode,
        homeViewModel = homeViewModel,
        playerViewModel = playerViewModel,
        videoPlayerViewModel = videoPlayerViewModel,
        onNavigateHome = {
            navController.navigate("home") {
                popUpTo("home") { inclusive = false }
                launchSingleTop = true
            }
        },
        onOpenChannel = openChannel
    )

    // Drives the PiP window's shape and its transport controls. Composed above
    // the early return on purpose: everything below it is torn out of the
    // composition the moment PiP starts, which is what used to unregister the
    // receiver behind the PiP play/pause button and leave it inert.
    com.ivor.ivormusic.ui.video.VideoPipController(viewModel = videoPlayerViewModel)

    // In system PiP the app is just a video surface. Returning here keeps the
    // NavHost, both players and every overlay out of the composition entirely,
    // rather than letting them draw and animate behind a window nobody can see
    // them in.
    if (isInPipMode) {
        com.ivor.ivormusic.ui.video.PipVideoSurface(viewModel = videoPlayerViewModel)
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        NavHost(
            navController = navController,
            startDestination = if (onboardingCompleted) "home" else "onboarding"
        ) {
            composable("onboarding") {
                OnboardingScreen(
                    currentThemeMode = currentThemeMode,
                    onThemeModeChange = onThemeModeChange,
                    loadLocalSongs = loadLocalSongs,
                    onLoadLocalSongsToggle = onLoadLocalSongsToggle,
                    ambientBackground = ambientBackground,
                    onAmbientBackgroundToggle = onAmbientBackgroundToggle,
                    videoMode = videoMode,
                    onVideoModeToggle = onVideoModeToggle,
                    homeModeToggleEnabled = homeModeToggleEnabled,
                    onHomeModeToggleEnabledChange = onHomeModeToggleEnabledChange,
                    shortsEnabled = shortsEnabled,
                    onShortsEnabledToggle = onShortsEnabledToggle,
                    spotlightHome = spotlightHome,
                    onSpotlightHomeChange = onSpotlightHomeToggle,
                    playerStyle = playerStyle,
                    onPlayerStyleChange = onPlayerStyleChange,
                    crossfadeEnabled = crossfadeEnabled,
                    onCrossfadeEnabledToggle = onCrossfadeEnabledToggle,
                    manualScanEnabled = manualScanEnabled,
                    onManualScanEnabledToggle = onManualScanEnabledToggle,
                    onFinish = {
                        onOnboardingCompleted(true)
                        navController.navigate("home") {
                            popUpTo("onboarding") { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable("home") {
                HomeScreen(
                    onSongClick = { song ->
                        playerViewModel.playSong(song)
                    },
                    playerViewModel = playerViewModel,
                    viewModel = homeViewModel,
                    isDarkMode = isDarkMode,
                    onThemeToggle = onThemeToggle,
                    onNavigateToSettings = { navController.navigate("settings") },
                    onNavigateToDownloads = { navController.navigate("downloads") },
                    onNavigateToStats = { navController.navigate("stats") },
                    onNavigateToSubscriptions = { navController.navigate("subscriptions") },
                    onNavigateToUpdate = { navController.navigate("update") },
                    onNavigateToVideoPlayer = { video ->
                        videoPlayerViewModel.playVideo(video)
                    },
                    onPlayVideoQueue = { queue ->
                        videoPlayerViewModel.playQueue(queue)
                    },
                    onEnqueueVideo = { video, playNext ->
                        videoPlayerViewModel.enqueueVideo(video, playNext)
                    },
                    onOpenShorts = { shorts, index ->
                        // Shorts take over the screen; pause the video player
                        // so the two ExoPlayers don't fight for audio focus
                        videoPlayerViewModel.exoPlayer?.pause()
                        shortsPlayerViewModel.open(shorts, index)
                    },
                    onOpenChannel = openChannel,
                    shortsEnabled = shortsEnabled,
                    loadLocalSongs = loadLocalSongs,
                    excludedFolders = excludedFolders,
                    ambientBackground = ambientBackground,
                    playerArtworkColors = playerArtworkColors,
                    videoMode = videoMode,
                    onVideoModeToggle = onVideoModeToggle,
                    showModeToggle = homeModeToggleEnabled,
                    playerStyle = playerStyle,
                    onPlayerStyleChange = onPlayerStyleChange,
                    manualScan = manualScanEnabled,
                    localOnly = localOnlyMode,
                    hasVideoMiniPlayer = hasVideoMiniPlayer,
                    spotlightHome = spotlightHome,
                    nonExpressiveNavigationBar = nonExpressiveNavigationBar
                )
            }
            composable(
                route = "settings",
                enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn() },
                exitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() },
                popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 3 }) + fadeIn() },
                popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() }
            ) {
                com.ivor.ivormusic.ui.settings.SettingsScreen(
                    currentThemeMode = currentThemeMode,
                    onThemeModeChange = onThemeModeChange,
                    amoledTheme = amoledTheme,
                    onAmoledThemeToggle = onAmoledThemeToggle,
                    colorPalette = colorPalette,
                    onNavigateToColorPalette = { navController.navigate("color_palette") },
                    onNavigateToSubscriptions = { navController.navigate("subscriptions") },
                    onNavigateToNotInterested = { navController.navigate("not_interested") },
                    onNavigateToBackup = { navController.navigate("backup") },
                    onNavigateToReportBug = { navController.navigate("report") },
                    onNavigateToTimeLimit = { navController.navigate("app_time_limit") },
                    loadLocalSongs = loadLocalSongs,
                    onLoadLocalSongsToggle = onLoadLocalSongsToggle,
                    ambientBackground = ambientBackground,
                    onAmbientBackgroundToggle = onAmbientBackgroundToggle,
                    playerArtworkColors = playerArtworkColors,
                    onPlayerArtworkColorsToggle = onPlayerArtworkColorsToggle,
                    videoMode = videoMode,
                    onVideoModeToggle = onVideoModeToggle,
                    homeModeToggleEnabled = homeModeToggleEnabled,
                    onHomeModeToggleChange = onHomeModeToggleEnabledChange,
                    spotlightHome = spotlightHome,
                    onSpotlightHomeToggle = onSpotlightHomeToggle,
                    nonExpressiveNavigationBar = nonExpressiveNavigationBar,
                    onNonExpressiveNavigationBarToggle =
                        onNonExpressiveNavigationBarToggle,
                    playerStyle = playerStyle,
                    onPlayerStyleChange = onPlayerStyleChange,
                    saveVideoHistory = saveVideoHistory,
                    onSaveVideoHistoryToggle = onSaveVideoHistoryToggle,
                    saveMusicHistory = saveMusicHistory,
                    onSaveMusicHistoryToggle = onSaveMusicHistoryToggle,
                    liveDownloadUpdates = liveDownloadUpdates,
                    onLiveDownloadUpdatesToggle = onLiveDownloadUpdatesToggle,
                    livePlaybackUpdates = livePlaybackUpdates,
                    onLivePlaybackUpdatesToggle = onLivePlaybackUpdatesToggle,
                    timedCommentsEnabled = timedCommentsEnabled,
                    onTimedCommentsToggle = onTimedCommentsToggle,
                    shortsEnabled = shortsEnabled,
                    onShortsEnabledToggle = onShortsEnabledToggle,
                    shortsHiddenActions = shortsHiddenActions,
                    onShortsHiddenActionsChange = onShortsHiddenActionsChange,
                    videoQualityWifi = videoQualityWifi,
                    onVideoQualityWifiChange = onVideoQualityWifiChange,
                    videoQualityMobile = videoQualityMobile,
                    onVideoQualityMobileChange = onVideoQualityMobileChange,
                    musicQualityWifi = musicQualityWifi,
                    onMusicQualityWifiChange = onMusicQualityWifiChange,
                    musicQualityMobile = musicQualityMobile,
                    onMusicQualityMobileChange = onMusicQualityMobileChange,
                    subscriptionSource = subscriptionSource,
                    onSubscriptionSourceChange = onSubscriptionSourceChange,
                    subscribeTarget = subscribeTarget,
                    onSubscribeTargetChange = onSubscribeTargetChange,
                    fastSubscriptionFeed = fastSubscriptionFeed,
                    onFastSubscriptionFeedToggle = onFastSubscriptionFeedToggle,
                    excludedFolders = excludedFolders,
                    onAddExcludedFolder = onAddExcludedFolder,
                    onRemoveExcludedFolder = onRemoveExcludedFolder,
                    homeViewModel = homeViewModel,
                    onLogoutClick = { 
                        homeViewModel.logout()
                    },
                    onBackClick = { navController.popBackStack() },
                    cacheEnabled = cacheEnabled,
                    onCacheEnabledToggle = onCacheEnabledToggle,
                    maxCacheSizeMb = maxCacheSizeMb,
                    onMaxCacheSizeMbChange = onMaxCacheSizeMbChange,
                    currentCacheSize = currentCacheSize,
                    onClearCacheClick = onClearCacheClick,
                    autoLoadQueue = autoLoadQueue,
                    onAutoLoadQueueToggle = onAutoLoadQueueToggle,
                    crossfadeEnabled = crossfadeEnabled,
                    onCrossfadeEnabledToggle = onCrossfadeEnabledToggle,
                    crossfadeAuto = crossfadeAuto,
                    onCrossfadeAutoChange = onCrossfadeAutoChange,
                    crossfadeDurationMs = crossfadeDurationMs,
                    onCrossfadeDurationChange = onCrossfadeDurationChange,
                    normalizeVolume = normalizeVolume,
                    onNormalizeVolumeToggle = onNormalizeVolumeToggle,
                    oemFixEnabled = oemFixEnabled,
                    onOemFixEnabledToggle = onOemFixEnabledToggle,
                    manualScanEnabled = manualScanEnabled,
                    onManualScanEnabledToggle = onManualScanEnabledToggle,
                    onNavigateToUpdate = { navController.navigate("update") },
                    localOnlyMode = localOnlyMode,
                    onLocalOnlyModeToggle = onLocalOnlyModeToggle
                )
            }
            composable(
                route = "color_palette",
                enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn() },
                exitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() },
                popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 3 }) + fadeIn() },
                popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() }
            ) {
                com.ivor.ivormusic.ui.theme.ColorPaletteScreen(
                    currentPalette = colorPalette,
                    onPaletteSelected = onColorPaletteChange,
                    isDarkMode = isDarkMode,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = "subscriptions",
                enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn() },
                exitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() },
                popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 3 }) + fadeIn() },
                popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() }
            ) {
                com.ivor.ivormusic.ui.video.SubscriptionsManagerScreen(
                    viewModel = homeViewModel,
                    onBack = { navController.popBackStack() },
                    // The sign-in dialog lives on the home screen, so a login
                    // ask from here has to go back for it rather than opening a
                    // second WebView on top of a settings sub-screen.
                    onLoginClick = { navController.popBackStack("home", inclusive = false) }
                )
            }
            composable(
                route = "backup",
                enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn() },
                exitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() },
                popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 3 }) + fadeIn() },
                popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() }
            ) {
                com.ivor.ivormusic.ui.settings.BackupScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = "not_interested",
                enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn() },
                exitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() },
                popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 3 }) + fadeIn() },
                popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() }
            ) {
                com.ivor.ivormusic.ui.video.NotInterestedScreen(
                    viewModel = homeViewModel,
                    onBack = { navController.popBackStack() }
                )
            }
            // A creator's page. The argument is normally a UC id, may be an
            // @handle/full URL from a shared link, or a video:<id> fallback
            // when a modern feed card omitted its creator endpoint. The screen
            // resolves whichever it was given without starting that video.
            composable(
                route = "channel/{channelId}",
                arguments = listOf(
                    androidx.navigation.navArgument("channelId") {
                        type = androidx.navigation.NavType.StringType
                    }
                ),
                enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn() },
                exitTransition = { slideOutHorizontally(targetOffsetX = { -it / 3 }) + fadeOut() },
                popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 3 }) + fadeIn() },
                popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() }
            ) { entry ->
                val channelArg = entry.arguments?.getString("channelId").orEmpty()
                com.ivor.ivormusic.ui.channel.ChannelScreen(
                    channelId = channelArg,
                    homeViewModel = homeViewModel,
                    onBack = { navController.popBackStack() },
                    onPlayVideo = { video -> videoPlayerViewModel.playVideo(video) },
                    onPlayQueue = { queue -> videoPlayerViewModel.playQueue(queue) },
                    onOpenShorts = { shorts, index ->
                        videoPlayerViewModel.exoPlayer?.pause()
                        shortsPlayerViewModel.open(shorts, index)
                    },
                    // A channel opened from inside a channel is a new entry, so
                    // back walks the trail of creators the user actually followed.
                    onOpenChannel = openChannel,
                    onEnqueueVideo = { video, playNext ->
                        videoPlayerViewModel.enqueueVideo(video, playNext)
                    },
                    // The sign-in dialog lives on the home screen, so a login ask
                    // from here goes back for it rather than opening a second
                    // WebView on top of a detail screen - same rule as the
                    // subscriptions manager above.
                    onLoginClick = { navController.popBackStack("home", inclusive = false) },
                    // The music artist page lives inside the Library tab rather
                    // than on a route of its own, so the cross-link goes home
                    // and asks for it; HomeScreen routes to the tab and clears
                    // the request as it renders.
                    onOpenMusicArtist = { _, name ->
                        homeViewModel.requestArtistPage(name)
                        navController.popBackStack("home", inclusive = false)
                    }
                )
            }
            composable(
                route = "downloads",
                enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn() },
                exitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() },
                popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 3 }) + fadeIn() },
                popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() }
            ) {
                val downloadedSongs by playerViewModel.downloadedSongs.collectAsState()
                val downloadedVideos by playerViewModel.downloadedVideos.collectAsState()
                val downloadProgress by playerViewModel.downloadProgress.collectAsState()
                val downloadsContext = LocalContext.current

                com.ivor.ivormusic.ui.downloads.DownloadsScreen(
                    downloadedSongs = downloadedSongs,
                    downloadedVideos = downloadedVideos,
                    activeDownloads = downloadProgress,
                    onBack = { navController.popBackStack() },
                    onPlaySong = { song ->
                        playerViewModel.playSong(song)
                    },
                    onPlayQueue = { songs, song ->
                        playerViewModel.playQueue(songs, song)
                    },
                    onPlayVideo = { video ->
                        // Handed to the system player rather than the in-app one:
                        // VideoPlayerViewModel.playVideo drives the two-phase
                        // InnerTube resolution, and a downloaded file has no
                        // stream to resolve. Local playback in the app player is
                        // a separate piece of work.
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
                            .setDataAndType(video.uri, "video/*")
                            .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        runCatching { downloadsContext.startActivity(intent) }
                    },
                    onDeleteDownload = { songId ->
                        playerViewModel.deleteDownload(songId)
                    },
                    onDeleteVideo = { videoId ->
                        playerViewModel.deleteVideoDownload(videoId)
                    },
                    onCancelDownload = { songId ->
                        playerViewModel.cancelDownload(songId)
                    },
                    onRetryDownload = { request ->
                        playerViewModel.retryDownload(request)
                    },
                    onCancelAll = { playerViewModel.cancelAllDownloads() }
                )
            }
            composable(
                route = "stats",
                enterTransition = { 
                    slideInHorizontally(
                        initialOffsetX = { it },
                        animationSpec = androidx.compose.animation.core.tween(500, easing = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0f, 0f, 1f))
                    ) + fadeIn(animationSpec = androidx.compose.animation.core.tween(400))
                },
                exitTransition = { 
                    slideOutHorizontally(
                        targetOffsetX = { it },
                        animationSpec = androidx.compose.animation.core.tween(500, easing = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0f, 0f, 1f))
                    ) + fadeOut(animationSpec = androidx.compose.animation.core.tween(400))
                },
                popEnterTransition = { 
                    slideInHorizontally(
                        initialOffsetX = { -it / 3 },
                        animationSpec = androidx.compose.animation.core.tween(500, easing = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0f, 0f, 1f))
                    ) + fadeIn(animationSpec = androidx.compose.animation.core.tween(400))
                },
                popExitTransition = { 
                    slideOutHorizontally(
                        targetOffsetX = { it },
                        animationSpec = androidx.compose.animation.core.tween(500, easing = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0f, 0f, 1f))
                    ) + fadeOut(animationSpec = androidx.compose.animation.core.tween(400))
                }
            ) {
                com.ivor.ivormusic.ui.library.StatsScreen(
                    onBack = { navController.popBackStack() },
                    viewModel = homeViewModel,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 160.dp)
                )
            }
            composable(
                route = "update",
                enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn() },
                exitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() },
                popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 3 }) + fadeIn() },
                popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() }
            ) {
                if (localOnlyMode) {
                    com.ivor.ivormusic.ui.components.LocalOnlyNotice(
                        subtitle = "Update checks need the internet. Turn off Local only in Settings to check for updates."
                    )
                } else {
                    com.ivor.ivormusic.ui.settings.UpdateScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
            }
            composable(
                route = "report",
                enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn() },
                exitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() },
                popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 3 }) + fadeIn() },
                popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() }
            ) {
                com.ivor.ivormusic.ui.report.ReportBugScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = "app_time_limit",
                enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn() },
                exitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() },
                popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 3 }) + fadeIn() },
                popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() }
            ) {
                com.ivor.ivormusic.ui.applock.AppTimeLimitScreen(
                    onBack = { navController.popBackStack() }
                )
            }
        }
        
        com.ivor.ivormusic.ui.video.VideoPlayerOverlay(
            viewModel = videoPlayerViewModel,
            timedCommentsEnabled = timedCommentsEnabled,
            onOpenChannel = openChannel,
            hostBottomChrome = videoMiniBottomChrome
        )

        // Shorts sit above everything, including the video player overlay
        com.ivor.ivormusic.ui.shorts.ShortsPlayerOverlay(
            viewModel = shortsPlayerViewModel,
            hiddenActions = shortsHiddenActions,
            onOpenChannel = openChannel
        )

        // Undo for "don't recommend", app-wide and last in the stack.
        //
        // One host for the whole app rather than one per screen: the action can
        // be taken from the home grid, the subscriptions feed, search, the
        // player's Up Next list and Shorts, and two of those are overlays
        // drawn above the NavHost. A per-screen snackbar would be hidden behind
        // the Shorts overlay exactly when it is needed most, and could show
        // twice when a screen and an overlay are both alive.
        NotInterestedUndoHost(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = if (musicPillVisible) 96.dp else 16.dp)
        )

        // Offer to report the crash from the previous run. Read once per
        // composition of MusicApp (an activity recreation re-reads the file,
        // but either answer has deleted it by then). Only after onboarding -
        // a crash during first-run setup would otherwise interrupt it again.
        if (onboardingCompleted) {
            var showCrashPrompt by remember {
                mutableStateOf(
                    com.ivor.ivormusic.data.CrashReporter.readPendingCrash(context) != null
                )
            }
            if (showCrashPrompt) {
                CrashReportPrompt(
                    onViewReport = {
                        showCrashPrompt = false
                        navController.navigate("report")
                    },
                    onDismiss = {
                        showCrashPrompt = false
                        com.ivor.ivormusic.data.CrashReporter.clearPendingCrash(context)
                    }
                )
            }
        }

        // The daily time limit's enforcement surface, last in the stack so it
        // covers the NavHost, both player overlays and every prompt. Locking
        // also stands playback down: a limiter that locks the screen while
        // music keeps playing would be counting nothing and stopping nothing.
        androidx.compose.runtime.LaunchedEffect(appTimeLocked) {
            if (appTimeLocked) {
                playerViewModel.pause()
                videoPlayerViewModel.pause()
                shortsPlayerViewModel.pause()
            }
        }
        if (appTimeLocked && !isInPipMode) {
            val limitPrefs = remember { com.ivor.ivormusic.data.ThemePreferences(context) }
            val usedSeconds = remember(appTimeLocked) {
                com.ivor.ivormusic.data.AppTimeLimit.usedSecondsToday(context)
            }
            val budgetMinutes = remember(appTimeLocked) {
                com.ivor.ivormusic.data.AppTimeLimit.budgetMinutesForToday(
                    com.ivor.ivormusic.data.AppTimeLimit.parseBudgets(
                        limitPrefs.getTimeLimitBudgets()
                    )
                )
            }
            com.ivor.ivormusic.ui.applock.AppLockOverlay(
                usedSecondsToday = usedSeconds,
                budgetMinutes = budgetMinutes
            )
        }
    }
}

/**
 * Shows "Video hidden - Undo" whenever something is dismissed.
 *
 * Keyed on the action's id rather than the action itself so two identical
 * dismissals in a row still re-show the snackbar instead of the second one
 * silently doing nothing.
 */
@Composable
private fun NotInterestedUndoHost(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repository = remember(context) {
        com.ivor.ivormusic.data.NotInterestedRepository(context)
    }
    // Undo also takes back the account-side dismissal when there was one, so it
    // goes through the actions layer rather than straight to the local store.
    //
    // Built lazily: this host is composed for the whole life of the app, while
    // a YouTubeRepository carries its own OkHttp pool and opens
    // EncryptedSharedPreferences. Nobody should pay that at startup for a path
    // that only runs when someone actually taps Undo.
    val actions = remember(context) {
        lazy {
            com.ivor.ivormusic.data.NotInterestedActions(
                repository,
                com.ivor.ivormusic.data.YouTubeRepository(context)
            )
        }
    }
    // Deliberately not the LaunchedEffect's own scope: that is cancelled the
    // moment another dismissal replaces this one, which would drop the undo
    // request mid-flight exactly when the user is undoing in a hurry.
    val undoScope = androidx.compose.runtime.rememberCoroutineScope()
    val lastAction by repository.lastAction.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(lastAction?.id) {
        val action = lastAction ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = action.message,
            actionLabel = "Undo",
            withDismissAction = false,
            duration = SnackbarDuration.Short
        )
        if (result == SnackbarResult.ActionPerformed) {
            actions.value.undo(action, undoScope)
        } else {
            // Timed out or was replaced. The hide stands; just stop offering
            // an undo for something the user has moved on from.
            repository.clearLastAction()
        }
    }

    SnackbarHost(hostState = snackbarHostState, modifier = modifier)
}

/**
 * One-time offer to report the crash the app died with on its previous run.
 *
 * A dialog at the MusicApp root rather than a card inside Home: it must be
 * answerable before any of the tab content, overlays or mini players settle,
 * and both answers are one tap - "Report" opens the reporter route (which
 * carries the crash file's contents), "Not now" deletes the file. Either way
 * it never appears twice for the same crash.
 */
@Composable
private fun CrashReportPrompt(
    onViewReport: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(32.dp),
        icon = {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.BugReport,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        },
        title = {
            Text(
                text = "Koda crashed last time",
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Text(
                text = "Something went wrong on your previous session. " +
                    "You can send a bug report with the details - nothing " +
                    "leaves your device unless you choose to share it.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Button(onClick = onViewReport) {
                Text("View & report")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Not now")
            }
        }
    )
}
