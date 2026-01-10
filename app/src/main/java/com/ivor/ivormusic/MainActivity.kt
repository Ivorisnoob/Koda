package com.ivor.ivormusic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ivor.ivormusic.data.VideoItem
import com.ivor.ivormusic.ui.home.HomeScreen
import com.ivor.ivormusic.ui.home.HomeViewModel
import com.ivor.ivormusic.ui.player.PlayerViewModel
import com.ivor.ivormusic.ui.theme.IvorMusicTheme
import com.ivor.ivormusic.ui.theme.ThemeViewModel
import com.ivor.ivormusic.ui.video.VideoPlayerScreen

import androidx.compose.foundation.isSystemInDarkTheme
import com.ivor.ivormusic.ui.theme.ThemeMode

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeViewModel: ThemeViewModel = viewModel()
            val themeMode by themeViewModel.themeMode.collectAsState()
            val loadLocalSongs by themeViewModel.loadLocalSongs.collectAsState()
            val ambientBackground by themeViewModel.ambientBackground.collectAsState()
            val videoMode by themeViewModel.videoMode.collectAsState()
            
            val isSystemDark = isSystemInDarkTheme()
            val isDarkTheme = remember(themeMode, isSystemDark) {
                when (themeMode) {
                    ThemeMode.SYSTEM -> isSystemDark
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                }
            }
            
            IvorMusicTheme(darkTheme = isDarkTheme) {
                MusicApp(
                    currentThemeMode = themeMode,
                    onThemeModeChange = { themeViewModel.setThemeMode(it) },
                    isDarkMode = isDarkTheme, // Derived for compatibility
                    onThemeToggle = { isDark ->
                        // Fallback toggle for legacy consumers
                        themeViewModel.setThemeMode(if (isDark) ThemeMode.DARK else ThemeMode.LIGHT)
                    },
                    loadLocalSongs = loadLocalSongs,
                    onLoadLocalSongsToggle = { themeViewModel.setLoadLocalSongs(it) },
                    ambientBackground = ambientBackground,
                    onAmbientBackgroundToggle = { themeViewModel.setAmbientBackground(it) },
                    videoMode = videoMode,
                    onVideoModeToggle = { themeViewModel.setVideoMode(it) }
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
    onVideoModeToggle: (Boolean) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val navController = rememberNavController()
    val playerViewModel: PlayerViewModel = remember { PlayerViewModel(context) }
    val homeViewModel: HomeViewModel = viewModel()
    
    // State to hold the current video to play
    var currentVideoToPlay by remember { mutableStateOf<VideoItem?>(null) }

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
                onNavigateToVideoPlayer = { video ->
                    currentVideoToPlay = video
                    navController.navigate("video_player")
                },
                loadLocalSongs = loadLocalSongs,
                ambientBackground = ambientBackground,
                videoMode = videoMode
            )
        }
        composable("settings") {
            com.ivor.ivormusic.ui.settings.SettingsScreen(
                currentThemeMode = currentThemeMode,
                onThemeModeChange = onThemeModeChange,
                loadLocalSongs = loadLocalSongs,
                onLoadLocalSongsToggle = onLoadLocalSongsToggle,
                ambientBackground = ambientBackground,
                onAmbientBackgroundToggle = onAmbientBackgroundToggle,
                videoMode = videoMode,
                onVideoModeToggle = onVideoModeToggle,
                onLogoutClick = { 
                    homeViewModel.logout()
                },
                onBackClick = { navController.popBackStack() }
            )
        }
        composable("downloads") {
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
        composable("video_player") {
            currentVideoToPlay?.let { video ->
                VideoPlayerScreen(
                    video = video,
                    onBackClick = { navController.popBackStack() }
                )
            }
        }
    }
}

