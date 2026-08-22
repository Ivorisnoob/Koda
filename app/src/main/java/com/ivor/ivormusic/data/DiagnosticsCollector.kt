package com.ivor.ivormusic.data

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import com.ivor.ivormusic.BuildConfig
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Builds the fixed-format device/app block that heads every bug report.
 *
 * **Allowlist, not denylist.** Only the fields below ever reach a report;
 * there is no code path that dumps cookies, session material, profile
 * contents, history or playlist names. "Signed in" is a boolean, not an
 * identity. If a new field seems useful, add it here explicitly - never by
 * widening what gets read.
 *
 * Constructed fresh at report time with its own [ThemePreferences] instance,
 * so every value is a live read rather than a stale flow from some ViewModel.
 */
object DiagnosticsCollector {

    fun collect(context: Context): String {
        val appContext = context.applicationContext
        val prefs = ThemePreferences(appContext)
        val metered = try {
            @Suppress("DEPRECATION")
            (appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager)
                ?.isActiveNetworkMetered
        } catch (_: Throwable) {
            null
        }
        val signedIn = try {
            SessionManager(appContext).isLoggedIn()
        } catch (_: Throwable) {
            false
        }
        val abis = Build.SUPPORTED_ABIS?.takeIf { it.isNotEmpty() }?.joinToString(", ")
        val uptimeMinutes = try {
            TimeUnit.MILLISECONDS.toMinutes(android.os.SystemClock.elapsedRealtime())
        } catch (_: Throwable) {
            -1L
        }

        return buildString {
            appendLine("[Device]")
            appendLine("Model: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            if (abis != null) appendLine("ABIs: $abis")
            appendLine("Uptime: ${if (uptimeMinutes >= 0) "$uptimeMinutes min" else "unknown"}")
            metered?.let { appendLine("Network: ${if (it) "metered (mobile data)" else "unmetered (Wi-Fi)"}") }
            appendLine()
            appendLine("[App]")
            appendLine("Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Build type: ${if (BuildConfig.DEBUG) "debug" else "release"}")
            appendLine("Signed in: ${if (signedIn) "yes" else "no"}")
            appendLine()
            appendLine("[Settings]")
            appendLine("Video mode: ${prefs.videoMode.value}")
            appendLine("Home style: ${if (prefs.spotlightHome.value) "Spotlight" else "Classic"}")
            appendLine("Player style: ${prefs.playerStyle.value.name.lowercase(Locale.US)}")
            appendLine("Local only mode: ${prefs.localOnlyMode.value}")
            appendLine(
                "Music quality: wifi=${prefs.musicQualityWifi.value}, " +
                    "mobile=${prefs.musicQualityMobile.value}"
            )
            appendLine(
                "Video quality: wifi=${prefs.videoQualityWifi.value}, " +
                    "mobile=${prefs.videoQualityMobile.value}"
            )
            appendLine("Subscribe target: ${prefs.subscribeTarget.value}")
            appendLine("Cache enabled: ${prefs.cacheEnabled.value}")
        }
    }
}
