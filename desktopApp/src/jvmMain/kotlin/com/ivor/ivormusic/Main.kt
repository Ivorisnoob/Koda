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
import java.io.File
import java.time.LocalDateTime

fun main() {
    // Catch any crash before the window even opens and write it to a log
    // so "nothing happens" silent failures become diagnosable.
    Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
        writeCrashLog(throwable)
    }

    try {
        application {
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
    } catch (t: Throwable) {
        writeCrashLog(t)
    }
}

private fun writeCrashLog(t: Throwable) {
    val text = "[${LocalDateTime.now()}]\n${t.stackTraceToString()}\n\n"
    // Write to several locations so it's easy to find regardless of OS
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
