package com.ivor.ivormusic

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

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
            val timedCommentsEnabled by themeViewModel.timedCommentsEnabled.collectAsState()
            val shortsEnabled by themeViewModel.shortsEnabled.collectAsState()
            val shortsHiddenActions by themeViewModel.shortsHiddenActions.collectAsState()
            val defaultVideoQuality by themeViewModel.defaultVideoQuality.collectAsState()
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
                        timedCommentsEnabled = timedCommentsEnabled,
                        onTimedCommentsToggle = { themeViewModel.setTimedCommentsEnabled(it) },
                        shortsEnabled = shortsEnabled,
                        onShortsEnabledToggle = { themeViewModel.setShortsEnabled(it) },
                        shortsHiddenActions = shortsHiddenActions,
                        onShortsHiddenActionsChange = { themeViewModel.setShortsHiddenActions(it) },
                        defaultVideoQuality = defaultVideoQuality,
                        onDefaultVideoQualityChange = { themeViewModel.setDefaultVideoQuality(it) },
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
}

@Composable
fun MusicApp(
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
    timedCommentsEnabled: Boolean,
    onTimedCommentsToggle: (Boolean) -> Unit,
    shortsEnabled: Boolean,
    onShortsEnabledToggle: (Boolean) -> Unit,
    shortsHiddenActions: Set<String>,
    onShortsHiddenActionsChange: (Set<String>) -> Unit,
    defaultVideoQuality: String,
    onDefaultVideoQualityChange: (String) -> Unit,
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
                    timedCommentsEnabled = timedCommentsEnabled,
                    onTimedCommentsToggle = onTimedCommentsToggle,
                    shortsEnabled = shortsEnabled,
                    onShortsEnabledToggle = onShortsEnabledToggle,
                    shortsHiddenActions = shortsHiddenActions,
                    onShortsHiddenActionsChange = onShortsHiddenActionsChange,
                    defaultVideoQuality = defaultVideoQuality,
                    onDefaultVideoQualityChange = onDefaultVideoQualityChange,
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
                    onLocalOnlyModeToggle = onLocalOnlyModeToggle,
                    onNavigateToEqualizer = { navController.navigate("equalizer") }
                )
            }
            composable(
                route = "equalizer",
                enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn() },
                exitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() },
                popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 3 }) + fadeIn() },
                popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() }
            ) {
                com.ivor.ivormusic.ui.settings.EqualizerScreen(
                    onBack = { navController.popBackStack() }
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
                val downloadProgress by playerViewModel.downloadProgress.collectAsState()
                
                com.ivor.ivormusic.ui.downloads.DownloadsScreen(
                    downloadedSongs = downloadedSongs,
                    activeDownloads = downloadProgress,
                    onBack = { navController.popBackStack() },
                    onPlaySong = { song -> 
                        playerViewModel.playSong(song)
                    },
                    onPlayQueue = { songs, song ->
                        playerViewModel.playQueue(songs, song)
                    },
                    onDeleteDownload = { songId -> 
                        playerViewModel.deleteDownload(songId)
                    },
                    onCancelDownload = { songId -> 
                        playerViewModel.cancelDownload(songId)
                    },
                    onRetryDownload = { song -> 
                        playerViewModel.toggleDownload(song)
                    }
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
    }
}

