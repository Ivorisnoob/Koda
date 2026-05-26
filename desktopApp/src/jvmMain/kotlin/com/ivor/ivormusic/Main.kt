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
import com.ivor.ivormusic.data.LikedSongsRepository
import com.ivor.ivormusic.data.SearchHistoryRepository
import com.ivor.ivormusic.media.DesktopPlayerController
import com.ivor.ivormusic.network.DesktopYouTubeRepository
import com.ivor.ivormusic.network.LyricsRepository
import com.ivor.ivormusic.ui.home.HomeViewModel
import com.ivor.ivormusic.ui.player.PlayerViewModel
import com.ivor.ivormusic.ui.theme.KodaTheme
import com.russhwolf.settings.PreferencesSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
import java.io.File
import java.time.LocalDateTime
import java.util.prefs.Preferences

fun main() {
    Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
        writeCrashLog(throwable)
    }

    try {
        // Build all dependencies BEFORE application{} so the window
        // opens instantly and macOS doesn't kill the process during init.
        val settings = PreferencesSettings(Preferences.userRoot().node("com/ivor/koda"))
        val sessionManager = DesktopSessionManager()
        val youtubeRepository = DesktopYouTubeRepository(sessionManager)
        val appPreferences = DesktopAppPreferences()
        val playerController = DesktopPlayerController(youtubeRepository)
        val playlistRepository = DesktopPlaylistRepository()
        val downloadRepository = DesktopDownloadRepository(youtubeRepository)
        val localSongRepository = DesktopLocalSongRepository()
        val statsRepository = DesktopStatsRepository()
        val likedSongsRepository = LikedSongsRepository(settings)
        val searchHistoryRepository = SearchHistoryRepository(settings)
        val httpClient = HttpClient(Java)
        val lyricsRepository = LyricsRepository(httpClient)

        val homeViewModel = HomeViewModel(
            localRepository = localSongRepository,
            youtubeRepository = youtubeRepository,
            playlistRepository = playlistRepository,
            sessionManager = sessionManager,
            searchHistoryRepository = searchHistoryRepository,
            likedSongsRepository = likedSongsRepository,
            downloadRepository = downloadRepository,
            statsRepository = statsRepository
        )
        val playerViewModel = PlayerViewModel(
            playerController = playerController,
            likedSongsRepository = likedSongsRepository,
            lyricsRepository = lyricsRepository,
            downloadRepository = downloadRepository,
            statsRepository = statsRepository,
            playlistRepository = playlistRepository,
            prefs = appPreferences
        )

        application {
            val windowState = rememberWindowState(width = 1200.dp, height = 800.dp)

            Window(
                onCloseRequest = {
                    playerController.release()
                    httpClient.close()
                    exitApplication()
                },
                title = "Koda",
                state = windowState
            ) {
                KodaTheme {
                    KodaDesktopApp(
                        appPreferences = appPreferences,
                        homeViewModel = homeViewModel,
                        playerViewModel = playerViewModel
                    )
                }
            }
        }
    } catch (t: Throwable) {
        writeCrashLog(t)
    }
}

private fun writeCrashLog(t: Throwable) {
    val text = "[${LocalDateTime.now()}]\n${t.stackTraceToString()}\n\n"
    val candidates = listOf(
        File(System.getProperty("user.home"), "koda-crash.log"),
        File(System.getProperty("user.home"), "Desktop/koda-crash.log"),
        File(System.getProperty("java.io.tmpdir"), "koda-crash.log"),
        File(".").absoluteFile.let { File(it, "koda-crash.log") }
    )
    for (f in candidates) {
        try { f.parentFile?.mkdirs(); f.appendText(text) } catch (_: Exception) {}
    }
}
