package com.ivor.ivormusic.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.media3.datasource.HttpDataSource

/**
 * Why YouTube refused a playback, when the refusal is about the connection
 * rather than the video.
 *
 * These are the verdicts nothing inside the app can fix. A fresh visitorData
 * has already been tried by the time one reaches the screen (the bot-check
 * verdict is only armed after a remint was refused, and a googlevideo 403 has
 * already been through one re-resolve); what fixes them is a different address,
 * which is the user's to get. So they get advice about their network rather
 * than a message and a Retry that fails the same way.
 */
enum class ConnectionRefusal {
    /** `/player` refused a fresh identity: "Sign in to confirm you're not a bot". */
    BOT_CHECK,

    /** HTTP 429, or a playback failure while [YouTubeRateLimit] is holding. */
    RATE_LIMITED,

    /** googlevideo answered 403 to URLs re-resolved under a fresh identity. */
    STREAM_REFUSED,
}

/** The network the advice is for; the fix differs for each. */
enum class NetworkKind { MOBILE, WIFI, VPN, OTHER }

data class ConnectionAdvice(val refusal: ConnectionRefusal, val network: NetworkKind)

/**
 * Advice for [error], or null when it is an ordinary failure that must keep its
 * own message - an unavailable or private video, a decoder error, or being
 * offline, where "change network" would be wrong.
 *
 * Classified from what reached the screen plus the process-wide verdicts at
 * that moment, rather than at each of the many places a ViewModel sets an
 * error: the verdicts are armed during the resolution that produced it.
 */
fun connectionAdviceFor(context: Context, error: Throwable): ConnectionAdvice? {
    val network = currentNetworkKind(context) ?: return null
    val status = httpStatusOf(error)
    val refusal = when {
        status == 429 || YouTubeRateLimit.isHeld() -> ConnectionRefusal.RATE_LIMITED
        status == 403 -> ConnectionRefusal.STREAM_REFUSED
        YouTubeRepository.isBotCheckVerdictActive() -> ConnectionRefusal.BOT_CHECK
        else -> return null
    }
    return ConnectionAdvice(refusal, network)
}

/** The HTTP status behind a Media3 source failure, if there was one. */
private fun httpStatusOf(error: Throwable): Int? =
    generateSequence(error) { it.cause }
        .take(8)
        .filterIsInstance<HttpDataSource.InvalidResponseCodeException>()
        .firstOrNull()
        ?.responseCode

/**
 * The kind of the default network, or null when there is none with internet.
 * A VPN is checked first: it rides on Wi-Fi or mobile data, but its address is
 * the one YouTube sees, and the fix is the VPN's.
 */
fun currentNetworkKind(context: Context): NetworkKind? {
    val manager = context.getSystemService(ConnectivityManager::class.java) ?: return null
    val capabilities = manager.getNetworkCapabilities(manager.activeNetwork ?: return null)
        ?: return null
    if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return null
    return when {
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> NetworkKind.VPN
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkKind.MOBILE
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkKind.WIFI
        else -> NetworkKind.OTHER
    }
}

/**
 * Calls [onChanged] once for each new validated default network after [start].
 *
 * This is what makes the advice worth following: a user who toggles airplane
 * mode or switches to mobile data comes back to a video that is already
 * retrying, not to a Retry button. Every one of those paths ends in a new
 * default network - airplane mode and a Wi-Fi rejoin included, since Android
 * gives a reconnected network a new id - so a changed [Network] is the signal,
 * and waiting for VALIDATED keeps the retry off a network that cannot reach
 * anything yet. The callback registered on start reports the current network
 * first; that is the baseline and is ignored.
 *
 * [onChanged] runs on ConnectivityManager's thread.
 */
class NetworkChangeWatcher(context: Context, private val onChanged: () -> Unit) {
    private val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    private var callback: ConnectivityManager.NetworkCallback? = null

    @Volatile
    private var current: Network? = null

    fun start() {
        if (callback != null || manager == null) return
        current = manager.activeNetwork
        val watch = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                if (network == current ||
                    !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                ) return
                current = network
                onChanged()
            }
        }
        // A registration can throw at the per-app callback limit; losing the
        // auto-retry must not lose the advice.
        if (runCatching { manager.registerDefaultNetworkCallback(watch) }.isSuccess) callback = watch
    }

    fun stop() {
        val watch = callback ?: return
        callback = null
        runCatching { manager?.unregisterNetworkCallback(watch) }
    }
}
