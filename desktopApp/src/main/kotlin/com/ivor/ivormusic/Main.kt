package com.ivor.ivormusic

import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.ivor.ivormusic.data.DesktopAppPreferences
import com.ivor.ivormusic.data.DesktopDownloadRepository
import com.ivor.ivormusic.data.DesktopLocalSongRepository
import com.ivor.ivormusic.data.DesktopPlaylistRepository
import com.ivor.ivormusic.data.DesktopSessionManager
import com.ivor.ivormusic.data.DesktopStatsRepository
import com.ivor.ivormusic.media.DesktopPlayerController
import com.ivor.ivormusic.network.DesktopYouTubeRepository
import com.ivor.ivormusic.ui.theme.KodaTheme

fun main() = application {
    val windowState = rememberWindowState(width = 1200.dp, height = 800.dp)

    val sessionManager = remember { DesktopSessionManager() }
    val youtubeRepository = remember { DesktopYouTubeRepository(sessionManager) }
    val appPreferences = remember { DesktopAppPreferences() }
    val playerController = remember { DesktopPlayerController(youtubeRepository) }
    val downloadRepository = remember { DesktopDownloadRepository(youtubeRepository) }
    val localSongRepository = remember { DesktopLocalSongRepository() }
    val playlistRepository = remember { DesktopPlaylistRepository() }
    val statsRepository = remember { DesktopStatsRepository() }

    Window(
        onCloseRequest = {
            playerController.release()
            exitApplication()
        },
        title = "Koda",
        state = windowState
    ) {
        KodaTheme {
            KodaDesktopApp(
                appPreferences = appPreferences,
                sessionManager = sessionManager,
                youtubeRepository = youtubeRepository,
                playerController = playerController,
                downloadRepository = downloadRepository,
                localSongRepository = localSongRepository,
                playlistRepository = playlistRepository,
                statsRepository = statsRepository
            )
        }
    }
}
