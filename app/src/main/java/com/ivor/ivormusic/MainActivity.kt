package com.ivor.ivormusic

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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

class MainActivity : ComponentActivity() {
    
    companion object {
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 1001
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        
        // Remove splash instantly when ready — the AVD entrance animation is the show
        splashScreen.setOnExitAnimationListener { it.remove() }

        enableEdgeToEdge()
        
        requestNotificationPermission()
        
        setContent {
            val themeViewModel: ThemeViewModel = viewModel()
            val themeMode by themeViewModel.themeMode.collectAsState()
            val loadLocalSongs by themeViewModel.loadLocalSongs.collectAsState()
            val ambientBackground by themeViewModel.ambientBackground.collectAsState()
            val videoMode by themeViewModel.videoMode.collectAsState()
            val playerStyle by themeViewModel.playerStyle.collectAsState()
            val saveVideoHistory by themeViewModel.saveVideoHistory.collectAsState()
            val excludedFolders by themeViewModel.excludedFolders.collectAsState()
            val oemFixEnabled by themeViewModel.oemFixEnabled.collectAsState()
            val manualScanEnabled by themeViewModel.manualScanEnabled.collectAsState()
            
            val cacheEnabled by themeViewModel.cacheEnabled.collectAsState()
            val maxCacheSizeMb by themeViewModel.maxCacheSizeMb.collectAsState()
            val currentCacheSize by themeViewModel.currentCacheSizeBytes.collectAsState()
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
            
            IvorMusicTheme(darkTheme = isDarkTheme) {
                Box(modifier = Modifier.fillMaxSize()) {
                    MusicApp(
                        currentThemeMode = themeMode,
                        onThemeModeChange = { themeViewModel.setThemeMode(it) },
                        isDarkMode = isDarkTheme, // Derived for compatibility
                        onThemeToggle = { isDark ->
                            themeViewModel.setThemeMode(if (isDark) ThemeMode.DARK else ThemeMode.LIGHT)
                        },
                        loadLocalSongs = loadLocalSongs,
                        onLoadLocalSongsToggle = { themeViewModel.setLoadLocalSongs(it) },
                        ambientBackground = ambientBackground,
                        onAmbientBackgroundToggle = { themeViewModel.setAmbientBackground(it) },
                        videoMode = videoMode,
                        onVideoModeToggle = { themeViewModel.setVideoMode(it) },
                        playerStyle = playerStyle,
                        onPlayerStyleChange = { themeViewModel.setPlayerStyle(it) },
                        saveVideoHistory = saveVideoHistory,
                        onSaveVideoHistoryToggle = { themeViewModel.setSaveVideoHistory(it) },
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
                        crossfadeEnabled = crossfadeEnabled,
                        onCrossfadeEnabledToggle = { themeViewModel.toggleCrossfadeEnabled() },
                        crossfadeDurationMs = crossfadeDurationMs,
                        onCrossfadeDurationChange = { themeViewModel.setCrossfadeDuration(it) }
                    )
                }
            }
        }
    }
    
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_REQUEST_CODE
                )
            }
        }
    }
}

@Composable
fun MusicApp(
    currentThemeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    isDarkMode: Boolean,
    onThemeToggle: (Boolean) -> Unit,
    loadLocalSongs: Boolean,
    onLoadLocalSongsToggle: (Boolean) -> Unit,
    ambientBackground: Boolean,
    onAmbientBackgroundToggle: (Boolean) -> Unit,
    videoMode: Boolean,
    onVideoModeToggle: (Boolean) -> Unit,
    playerStyle: PlayerStyle,
    onPlayerStyleChange: (PlayerStyle) -> Unit,
    saveVideoHistory: Boolean,
    onSaveVideoHistoryToggle: (Boolean) -> Unit,
    excludedFolders: Set<String>,
    onAddExcludedFolder: (String) -> Unit,
    onRemoveExcludedFolder: (String) -> Unit,
    cacheEnabled: Boolean,
    onCacheEnabledToggle: (Boolean) -> Unit,
    maxCacheSizeMb: Long,
    onMaxCacheSizeMbChange: (Long) -> Unit,
    currentCacheSize: Long,
    onClearCacheClick: () -> Unit,
    crossfadeEnabled: Boolean,
    onCrossfadeEnabledToggle: (Boolean) -> Unit,
    crossfadeDurationMs: Int,
    onCrossfadeDurationChange: (Int) -> Unit,
    oemFixEnabled: Boolean,
    onOemFixEnabledToggle: (Boolean) -> Unit,
    manualScanEnabled: Boolean,
    onManualScanEnabledToggle: (Boolean) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val navController = rememberNavController()
    val playerViewModel: PlayerViewModel = remember { PlayerViewModel(context) }
    val homeViewModel: HomeViewModel = viewModel()
    
    val videoPlayerViewModel: com.ivor.ivormusic.ui.video.VideoPlayerViewModel = viewModel()
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        NavHost(navController = navController, startDestination = "home") {

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
                    onNavigateToVideoPlayer = { video ->
                        videoPlayerViewModel.playVideo(video)
                    },
                    loadLocalSongs = loadLocalSongs,
                    excludedFolders = excludedFolders,
                    ambientBackground = ambientBackground,
                    videoMode = videoMode,
                    playerStyle = playerStyle,
                    manualScan = manualScanEnabled
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
                    loadLocalSongs = loadLocalSongs,
                    onLoadLocalSongsToggle = onLoadLocalSongsToggle,
                    ambientBackground = ambientBackground,
                    onAmbientBackgroundToggle = onAmbientBackgroundToggle,
                    videoMode = videoMode,
                    onVideoModeToggle = onVideoModeToggle,
                    playerStyle = playerStyle,
                    onPlayerStyleChange = onPlayerStyleChange,
                    saveVideoHistory = saveVideoHistory,
                    onSaveVideoHistoryToggle = onSaveVideoHistoryToggle,
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
                    crossfadeEnabled = crossfadeEnabled,
                    onCrossfadeEnabledToggle = onCrossfadeEnabledToggle,
                    crossfadeDurationMs = crossfadeDurationMs,
                    onCrossfadeDurationChange = onCrossfadeDurationChange,
                    oemFixEnabled = oemFixEnabled,
                    onOemFixEnabledToggle = onOemFixEnabledToggle,
                    manualScanEnabled = manualScanEnabled,
                    onManualScanEnabledToggle = onManualScanEnabledToggle
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
        }
        
        com.ivor.ivormusic.ui.video.VideoPlayerOverlay(
            viewModel = videoPlayerViewModel
        )
    }
}

