package com.ivor.ivormusic

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.ivor.ivormusic.data.AppPreferences
import com.ivor.ivormusic.data.SessionManager
import com.ivor.ivormusic.ui.downloads.DownloadsScreen
import com.ivor.ivormusic.ui.home.HomeScreen
import com.ivor.ivormusic.ui.home.HomeViewModel
import com.ivor.ivormusic.ui.player.PlayerViewModel
import com.ivor.ivormusic.ui.settings.SettingsScreen
import com.ivor.ivormusic.ui.theme.ThemeMode

private enum class Screen { HOME, SETTINGS, DOWNLOADS }

@Composable
fun KodaDesktopApp(
    appPreferences: AppPreferences,
    homeViewModel: HomeViewModel,
    playerViewModel: PlayerViewModel,
    sessionManager: SessionManager
) {
    val loadLocalSongs      by appPreferences.loadLocalSongs.collectAsState()
    val excludedFolders     by appPreferences.excludedFolders.collectAsState()
    val ambientBackground   by appPreferences.ambientBackground.collectAsState()
    val videoMode           by appPreferences.videoMode.collectAsState()
    val playerStyle         by appPreferences.playerStyle.collectAsState()
    val manualScanEnabled   by appPreferences.manualScanEnabled.collectAsState()
    val themeMode           by appPreferences.themeMode.collectAsState()
    val saveVideoHistory    by appPreferences.saveVideoHistory.collectAsState()
    val cacheEnabled        by appPreferences.cacheEnabled.collectAsState()
    val maxCacheSizeMb      by appPreferences.maxCacheSizeMb.collectAsState()
    val crossfadeEnabled    by appPreferences.crossfadeEnabled.collectAsState()
    val crossfadeDurationMs by appPreferences.crossfadeDurationMs.collectAsState()
    val oemFixEnabled       by appPreferences.oemFixEnabled.collectAsState()

    val downloadedSongs  by playerViewModel.downloadedSongs.collectAsState()
    val downloadingIds   by playerViewModel.downloadingIds.collectAsState()
    val downloadProgress by playerViewModel.downloadProgress.collectAsState()

    val isDark = themeMode == ThemeMode.DARK

    var currentScreen by remember { mutableStateOf(Screen.HOME) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (currentScreen) {
            Screen.HOME -> HomeScreen(
                onSongClick = { song -> playerViewModel.playSong(song) },
                playerViewModel = playerViewModel,
                viewModel = homeViewModel,
                isDarkMode = isDark,
                onThemeToggle = { dark ->
                    appPreferences.setThemeMode(if (dark) ThemeMode.DARK else ThemeMode.LIGHT)
                },
                onNavigateToSettings  = { currentScreen = Screen.SETTINGS },
                onNavigateToDownloads = { currentScreen = Screen.DOWNLOADS },
                onNavigateToStats     = {},
                onNavigateToUpdate    = {},
                onNavigateToVideoPlayer = {},
                loadLocalSongs    = loadLocalSongs,
                excludedFolders   = excludedFolders,
                ambientBackground = ambientBackground,
                videoMode         = videoMode,
                playerStyle       = playerStyle,
                manualScan        = manualScanEnabled
            )

            Screen.SETTINGS -> SettingsScreen(
                currentThemeMode          = themeMode,
                onThemeModeChange         = appPreferences::setThemeMode,
                loadLocalSongs            = loadLocalSongs,
                onLoadLocalSongsToggle    = appPreferences::setLoadLocalSongs,
                ambientBackground         = ambientBackground,
                onAmbientBackgroundToggle = appPreferences::setAmbientBackground,
                videoMode                 = videoMode,
                onVideoModeToggle         = appPreferences::setVideoMode,
                playerStyle               = playerStyle,
                onPlayerStyleChange       = appPreferences::setPlayerStyle,
                saveVideoHistory          = saveVideoHistory,
                onSaveVideoHistoryToggle  = appPreferences::setSaveVideoHistory,
                excludedFolders           = excludedFolders,
                onAddExcludedFolder       = appPreferences::addExcludedFolder,
                onRemoveExcludedFolder    = appPreferences::removeExcludedFolder,
                homeViewModel             = homeViewModel,
                onLogoutClick             = { homeViewModel.logout() },
                onBackClick               = { currentScreen = Screen.HOME },
                cacheEnabled              = cacheEnabled,
                onCacheEnabledToggle      = appPreferences::setCacheEnabled,
                maxCacheSizeMb            = maxCacheSizeMb,
                onMaxCacheSizeMbChange    = appPreferences::setMaxCacheSizeMb,
                currentCacheSize          = 0L,
                onClearCacheClick         = {},
                crossfadeEnabled          = crossfadeEnabled,
                onCrossfadeEnabledToggle  = appPreferences::setCrossfadeEnabled,
                crossfadeDurationMs       = crossfadeDurationMs,
                onCrossfadeDurationChange = appPreferences::setCrossfadeDuration,
                oemFixEnabled             = oemFixEnabled,
                onOemFixEnabledToggle     = appPreferences::setOemFixEnabled,
                manualScanEnabled         = manualScanEnabled,
                onManualScanEnabledToggle = appPreferences::setManualScanEnabled,
                onNavigateToUpdate        = {},
                sessionManager            = sessionManager
            )

            Screen.DOWNLOADS -> DownloadsScreen(
                downloadedSongs  = downloadedSongs,
                downloadingIds   = downloadingIds,
                downloadProgress = downloadProgress,
                onBack           = { currentScreen = Screen.HOME },
                onPlaySong       = { song -> playerViewModel.playSong(song) },
                onPlayQueue      = { songs, song -> playerViewModel.playQueue(songs, song) },
                onDeleteDownload = playerViewModel::deleteDownload,
                onCancelDownload = playerViewModel::cancelDownload,
                onRetryDownload  = playerViewModel::toggleDownload
            )
        }
    }
}
