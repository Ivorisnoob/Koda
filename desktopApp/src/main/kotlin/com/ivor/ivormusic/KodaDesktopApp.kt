package com.ivor.ivormusic

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ivor.ivormusic.data.AppPreferences
import com.ivor.ivormusic.data.DownloadRepository
import com.ivor.ivormusic.data.LocalSongRepository
import com.ivor.ivormusic.data.PlaylistRepository
import com.ivor.ivormusic.data.SessionManager
import com.ivor.ivormusic.data.StatsRepository
import com.ivor.ivormusic.media.PlayerController
import com.ivor.ivormusic.network.YouTubeRepository

@Composable
fun KodaDesktopApp(
    appPreferences: AppPreferences,
    sessionManager: SessionManager,
    youtubeRepository: YouTubeRepository,
    playerController: PlayerController,
    downloadRepository: DownloadRepository,
    localSongRepository: LocalSongRepository,
    playlistRepository: PlaylistRepository,
    statsRepository: StatsRepository
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Koda — Desktop",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}
