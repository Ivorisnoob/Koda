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
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
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
import com.ivor.ivormusic.ui.onboarding.OnboardingScreen
import com.ivor.ivormusic.ui.video.enterPipMode
import com.ivor.ivormusic.ui.share.PendingSharedLink
import com.ivor.ivormusic.ui.share.SharedLinkHandler
import com.ivor.ivormusic.ui.share.sharedLinkText

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
            val liveDownloadUpdates by themeViewModel.liveDownloadUpdates.collectAsState()
            val livePlaybackUpdates by themeViewModel.livePlaybackUpdates.collectAsState()
            val timedCommentsEnabled by themeViewModel.timedCommentsEnabled.collectAsState()
            val shortsEnabled by themeViewModel.shortsEnabled.collectAsState()
            val shortsHiddenActions by themeViewModel.shortsHiddenActions.collectAsState()
            val videoQualityWifi by themeViewModel.videoQualityWifi.collectAsState()
            val videoQualityMobile by themeViewModel.videoQualityMobile.collectAsState()
            val musicQualityWifi by themeViewModel.musicQualityWifi.collectAsState()
            val musicQualityMobile by themeViewModel.musicQualityMobile.collectAsState()
            val preferHdr by themeViewModel.preferHdr.collectAsState()
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
            val crossfadeDurationMs by themeViewModel.crossfadeDurationMs.collectAsState()
            
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
                        playerStyle = playerStyle,
                        onPlayerStyleChange = { themeViewModel.setPlayerStyle(it) },
                        saveVideoHistory = saveVideoHistory,
                        onSaveVideoHistoryToggle = { themeViewModel.setSaveVideoHistory(it) },
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
                        preferHdr = preferHdr,
                        onPreferHdrToggle = { themeViewModel.setPreferHdr(it) },
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
                        onCrossfadeEnabledToggle = { themeViewModel.toggleCrossfadeEnabled() },
                        crossfadeDurationMs = crossfadeDurationMs,
                        onCrossfadeDurationChange = { themeViewModel.setCrossfadeDuration(it) },
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
    playerStyle: PlayerStyle,
    onPlayerStyleChange: (PlayerStyle) -> Unit,
    saveVideoHistory: Boolean,
    onSaveVideoHistoryToggle: (Boolean) -> Unit,
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
    preferHdr: Boolean,
    onPreferHdrToggle: (Boolean) -> Unit,
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
    crossfadeDurationMs: Int,
    onCrossfadeDurationChange: (Int) -> Unit,
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

    // Surface music playback failures. Before this, a song that could not be
    // resolved failed silently and the player looked stuck on loading forever.
    val playbackError by playerViewModel.playbackError.collectAsState()
    androidx.compose.runtime.LaunchedEffect(playbackError) {
        playbackError?.let { message ->
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
            playerViewModel.clearPlaybackError()
        }
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
                    onNavigateToUpdate = { navController.navigate("update") },
                    onNavigateToVideoPlayer = { video ->
                        videoPlayerViewModel.playVideo(video)
                    },
                    onOpenShorts = { shorts, index ->
                        // Shorts take over the screen; pause the video player
                        // so the two ExoPlayers don't fight for audio focus
                        videoPlayerViewModel.exoPlayer?.pause()
                        shortsPlayerViewModel.open(shorts, index)
                    },
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
                    hasVideoMiniPlayer = hasVideoMiniPlayer
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
                    playerStyle = playerStyle,
                    onPlayerStyleChange = onPlayerStyleChange,
                    saveVideoHistory = saveVideoHistory,
                    onSaveVideoHistoryToggle = onSaveVideoHistoryToggle,
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
                    preferHdr = preferHdr,
                    onPreferHdrToggle = onPreferHdrToggle,
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
                    crossfadeDurationMs = crossfadeDurationMs,
                    onCrossfadeDurationChange = onCrossfadeDurationChange,
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
        }
        
        com.ivor.ivormusic.ui.video.VideoPlayerOverlay(
            viewModel = videoPlayerViewModel,
            timedCommentsEnabled = timedCommentsEnabled,
            // Stack the minimized video player above the music pill instead of
            // on top of it when both are alive at once
            miniPlayerExtraBottomPadding = if (musicPillVisible) 88.dp else 0.dp
        )

        // Shorts sit above everything, including the video player overlay
        com.ivor.ivormusic.ui.shorts.ShortsPlayerOverlay(
            viewModel = shortsPlayerViewModel,
            hiddenActions = shortsHiddenActions
        )

        // Opens YouTube links shared into the app. Held back until onboarding
        // is done, so a share cannot jump the first-run flow.
        SharedLinkHandler(
            pendingLink = pendingSharedLink,
            enabled = onboardingCompleted,
            localOnlyMode = localOnlyMode,
            homeViewModel = homeViewModel,
            playerViewModel = playerViewModel,
            videoPlayerViewModel = videoPlayerViewModel,
            onNavigateHome = {
                navController.navigate("home") {
                    popUpTo("home") { inclusive = false }
                    launchSingleTop = true
                }
            }
        )
    }
}

