package com.ivor.ivormusic.data.lossless

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.ivor.ivormusic.data.VolatileSharedPreferences
import com.ivor.ivormusic.util.KLog

/**
 * How hard to ask the lossless server for quality. The ids are stored, so they
 * are frozen; [apiValue] is the server's own name for the tier.
 */
enum class LosslessQuality(val id: String, val apiValue: String) {
    /** Up to 24-bit / 192 kHz where the server has it, CD quality otherwise. */
    HI_RES("hi_res", "hi_res_lossless"),

    /** 16-bit / 44.1 kHz: a third of the size, and what most ears and earbuds take. */
    CD("cd", "lossless");

    companion object {
        fun fromId(id: String?): LosslessQuality = entries.firstOrNull { it.id == id } ?: HI_RES
    }
}

/**
 * Everything about the listener's lossless server, read as one snapshot.
 *
 * Koda has no server built in. The address (which carries the server's token,
 * as `https://host/a/TOKEN/`) and the optional signing secret are typed in by
 * the listener under Settings > Advanced > Lossless audio.
 */
data class LosslessConfig(
    val serverUrl: String = "",
    val secret: String = "",
    /** Play songs from the server instead of YouTube while online. */
    val streaming: Boolean = false,
    /** Only on unmetered networks: a hi-res stream is 3-10 MB a minute. */
    val streamingUnmeteredOnly: Boolean = true,
    val streamingQuality: LosslessQuality = LosslessQuality.HI_RES,
    /** Offer "Lossless" in the download sheets. */
    val downloads: Boolean = false,
    /** The download sheets open on Lossless rather than the AAC default. */
    val downloadByDefault: Boolean = false,
    val downloadQuality: LosslessQuality = LosslessQuality.HI_RES,
    /** A song the server does not have is saved from YouTube instead of failing. */
    val downloadFallback: Boolean = true,
) {
    val isConfigured: Boolean get() = serverUrl.isNotBlank()
    val streamsLossless: Boolean get() = isConfigured && streaming
    val offersDownloads: Boolean get() = isConfigured && downloads

    /** The host alone, for a row that must never show the token. */
    val serverHost: String?
        get() = runCatching { android.net.Uri.parse(serverUrl).host }.getOrNull()?.takeIf { it.isNotBlank() }
}

/**
 * The lossless server's settings, kept in their own encrypted store.
 *
 * Encrypted because the address carries an access token and the secret signs
 * every request; separate from `ThemePreferences` so neither ever rides along
 * in a backup file (BackupRepository copies an allowlist, and this file is
 * deliberately not on it). Every read is fresh: Settings writes here while
 * MusicService and the download sheets hold no copy of their own.
 *
 * The first open is a keystore round trip, so call from a background thread.
 */
object LosslessSettings {
    private const val TAG = "LosslessSettings"
    private const val PREFS = "koda_lossless_server"

    private const val KEY_URL = "server_url"
    private const val KEY_SECRET = "server_secret"
    private const val KEY_STREAMING = "streaming"
    private const val KEY_STREAMING_UNMETERED = "streaming_unmetered_only"
    private const val KEY_STREAMING_QUALITY = "streaming_quality"
    private const val KEY_DOWNLOADS = "downloads"
    private const val KEY_DOWNLOAD_DEFAULT = "download_by_default"
    private const val KEY_DOWNLOAD_QUALITY = "download_quality"
    private const val KEY_DOWNLOAD_FALLBACK = "download_fallback"

    @Volatile
    private var handle: SharedPreferences? = null

    private fun prefs(context: Context): SharedPreferences =
        handle ?: synchronized(this) {
            handle ?: open(context.applicationContext).also { handle = it }
        }

    fun read(context: Context): LosslessConfig {
        // Every song resolution asks; someone who never set up a server should
        // not pay a keystore round trip, or get an empty encrypted file, for it.
        if (handle == null &&
            !java.io.File(context.applicationContext.dataDir, "shared_prefs/$PREFS.xml").exists()
        ) {
            return LosslessConfig()
        }
        val p = prefs(context)
        return LosslessConfig(
            serverUrl = p.getString(KEY_URL, null).orEmpty(),
            secret = p.getString(KEY_SECRET, null).orEmpty(),
            streaming = p.getBoolean(KEY_STREAMING, false),
            streamingUnmeteredOnly = p.getBoolean(KEY_STREAMING_UNMETERED, true),
            streamingQuality = LosslessQuality.fromId(p.getString(KEY_STREAMING_QUALITY, null)),
            downloads = p.getBoolean(KEY_DOWNLOADS, false),
            downloadByDefault = p.getBoolean(KEY_DOWNLOAD_DEFAULT, false),
            downloadQuality = LosslessQuality.fromId(p.getString(KEY_DOWNLOAD_QUALITY, null)),
            downloadFallback = p.getBoolean(KEY_DOWNLOAD_FALLBACK, true),
        )
    }

    fun write(context: Context, config: LosslessConfig) {
        prefs(context).edit()
            .putString(KEY_URL, config.serverUrl.trim())
            .putString(KEY_SECRET, config.secret.trim())
            .putBoolean(KEY_STREAMING, config.streaming)
            .putBoolean(KEY_STREAMING_UNMETERED, config.streamingUnmeteredOnly)
            .putString(KEY_STREAMING_QUALITY, config.streamingQuality.id)
            .putBoolean(KEY_DOWNLOADS, config.downloads)
            .putBoolean(KEY_DOWNLOAD_DEFAULT, config.downloadByDefault)
            .putString(KEY_DOWNLOAD_QUALITY, config.downloadQuality.id)
            .putBoolean(KEY_DOWNLOAD_FALLBACK, config.downloadFallback)
            .apply()
    }

    fun update(context: Context, transform: (LosslessConfig) -> LosslessConfig): LosslessConfig =
        synchronized(this) {
            transform(read(context)).also { write(context, it) }
        }

    /** Forget the server and turn everything that needed it off. */
    fun forget(context: Context) {
        prefs(context).edit().clear().apply()
    }

    @Suppress("DEPRECATION")
    private fun build(context: Context): SharedPreferences =
        EncryptedSharedPreferences.create(
            context, PREFS,
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

    /**
     * Last.fm's recovery: an unreadable file is dropped and rebuilt, never the
     * master key, which the YouTube session store shares. If even that fails
     * the server is forgotten for this process rather than taking it down.
     */
    private fun open(context: Context): SharedPreferences = try {
        build(context)
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        KLog.e(TAG, "Encrypted lossless store unreadable, resetting", e)
        try {
            context.deleteSharedPreferences(PREFS)
            build(context)
        } catch (retry: kotlinx.coroutines.CancellationException) {
            throw retry
        } catch (retry: Exception) {
            KLog.e(TAG, "Encrypted lossless store unavailable; using volatile store", retry)
            VolatileSharedPreferences()
        }
    }
}
