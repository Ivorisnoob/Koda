package com.ivor.ivormusic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ivor.ivormusic.ui.home.HomeScreen
import com.ivor.ivormusic.ui.home.HomeViewModel
import com.ivor.ivormusic.ui.player.PlayerViewModel
import com.ivor.ivormusic.ui.theme.IvorMusicTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            IvorMusicTheme {
                MusicApp()
            }
        }
    }
}

@Composable
fun MusicApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val navController = rememberNavController()
    val playerViewModel: PlayerViewModel = remember { PlayerViewModel(context) }
    val homeViewModel: HomeViewModel = viewModel()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onSongClick = { song ->
                    playerViewModel.playSong(song)
                },
                playerViewModel = playerViewModel,
                viewModel = homeViewModel
            )
        }
        // Player is now handled via ModalBottomSheet inside HomeScreen
        // Navigation routes for other screens can be added here
    }
}