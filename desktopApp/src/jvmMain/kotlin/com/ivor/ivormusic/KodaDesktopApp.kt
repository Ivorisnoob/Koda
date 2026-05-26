package com.ivor.ivormusic

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import com.ivor.ivormusic.data.AppPreferences
import com.ivor.ivormusic.ui.home.HomeScreen
import com.ivor.ivormusic.ui.home.HomeViewModel
import com.ivor.ivormusic.ui.player.PlayerViewModel

@Composable
fun KodaDesktopApp(
    appPreferences: AppPreferences,
    homeViewModel: HomeViewModel,
    playerViewModel: PlayerViewModel
) {
    val loadLocalSongs by appPreferences.loadLocalSongs.collectAsState()
    val excludedFolders by appPreferences.excludedFolders.collectAsState()
    val ambientBackground by appPreferences.ambientBackground.collectAsState()
    val videoMode by appPreferences.videoMode.collectAsState()
    val playerStyle by appPreferences.playerStyle.collectAsState()
    val manualScanEnabled by appPreferences.manualScanEnabled.collectAsState()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            HomeScreen(
                onSongClick = { song -> playerViewModel.playSong(song) },
                playerViewModel = playerViewModel,
                viewModel = homeViewModel,
                isDarkMode = true,
                onThemeToggle = {},
                onNavigateToSettings = {},
                onNavigateToDownloads = {},
                onNavigateToStats = {},
                onNavigateToUpdate = {},
                onNavigateToVideoPlayer = {},
                loadLocalSongs = loadLocalSongs,
                excludedFolders = excludedFolders,
                ambientBackground = ambientBackground,
                videoMode = videoMode,
                playerStyle = playerStyle,
                manualScan = manualScanEnabled
            )
        }
    }
}
