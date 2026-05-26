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

private fun trace(msg: String) {
    val line = "[KODA] $msg"
    System.err.println(line)
    try {
        File(System.getProperty("user.home"), "koda-startup.log")
            .appendText("[${LocalDateTime.now()}] $line\n")
    } catch (_: Exception) {}
}

fun main() {
    trace("main() entered")
    Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
        trace("UNCAUGHT in thread: ${throwable.javaClass.name}: ${throwable.message}")
        throwable.printStackTrace(System.err)
        writeCrashLog(throwable)
    }

    try {
        trace("building dependencies")
        val settings = PreferencesSettings(Preferences.userRoot().node("com/ivor/koda"))
        trace("settings ok")
        val sessionManager = DesktopSessionManager(); trace("sessionManager ok")
        val youtubeRepository = DesktopYouTubeRepository(sessionManager); trace("youtubeRepository ok")
        val appPreferences = DesktopAppPreferences(); trace("appPreferences ok")
        val playerController = DesktopPlayerController(youtubeRepository); trace("playerController ok")
        val playlistRepository = DesktopPlaylistRepository(); trace("playlistRepository ok")
        val downloadRepository = DesktopDownloadRepository(youtubeRepository); trace("downloadRepository ok")
        val localSongRepository = DesktopLocalSongRepository(); trace("localSongRepository ok")
        val statsRepository = DesktopStatsRepository(); trace("statsRepository ok")
        val likedSongsRepository = LikedSongsRepository(settings); trace("likedSongsRepository ok")
        val searchHistoryRepository = SearchHistoryRepository(settings); trace("searchHistoryRepository ok")
        val httpClient = HttpClient(Java); trace("httpClient ok")
        val lyricsRepository = LyricsRepository(httpClient); trace("lyricsRepository ok")

        trace("creating HomeViewModel")
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
        trace("HomeViewModel ok; creating PlayerViewModel")
        val playerViewModel = PlayerViewModel(
            playerController = playerController,
            likedSongsRepository = likedSongsRepository,
            lyricsRepository = lyricsRepository,
            downloadRepository = downloadRepository,
            statsRepository = statsRepository,
            playlistRepository = playlistRepository,
            prefs = appPreferences
        )

        trace("PlayerViewModel ok; entering application{}")
        application {
            trace("inside application{} composable")
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
        trace("application{} returned normally")
    } catch (t: Throwable) {
        trace("CAUGHT in main: ${t.javaClass.name}: ${t.message}")
        t.printStackTrace(System.err)
        writeCrashLog(t)
    }
    trace("main() exiting")
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
