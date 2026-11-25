package com.ivor.ivormusic.data.lossless

import android.content.Context
import com.ivor.ivormusic.data.ThemePreferences
import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Where a lossless track's audio is, once the server has said. */
sealed interface LosslessSource {
    val url: String

    /** A DASH manifest: ExoPlayer plays it as is, a download joins its segments. */
    data class Dash(override val url: String, val manifest: String) : LosslessSource

    /** One file (FLAC, or an MP4 holding FLAC/ALAC). */
    data class File(override val url: String) : LosslessSource
}

/** The server answered but has nothing to play for this song. */
class LosslessUnavailableException(message: String) : IOException(message)

/**
 * Talks to the listener's lossless server, the same protocol the Windows app
 * speaks [verified against koda-windows LosslessServer.cs, November 2026]:
 *
 *  - `GET {base}/search?q=..&quality=..&atmos=none` -> `{tracks:[{id,title,artist,album,duration,...}]}`
 *  - `GET {base}/stream/{id}?quality=..&atmos=none` -> `{url | streamUrl | directUrl}`, which is a
 *    DASH manifest or the file itself.
 *
 * `base` is the address the listener typed, `https://host/a/TOKEN`. With a
 * secret, every call carries `X-LW-TS`, `X-LW-Sign` and `X-LW-Intent`; the
 * signature is HMAC-SHA256 over `"{ts}\n{METHOD}\n{path}\n{token}"` (path
 * without the query, token from the address), hex, lower case. The header
 * names are the server's, so they stay as it spells them. Requests to the
 * media itself (the manifest's segments, the file) are not signed.
 */
class LosslessClient(
    private val context: Context,
    private val config: LosslessConfig,
) {
    private val base = config.serverUrl.trim().trimEnd('/')
    private val token = TOKEN.find(base)?.groupValues?.get(1).orEmpty()

    /** Search, then let [LosslessMatcher] decide. Null when the server has no convincing copy. */
    suspend fun find(
        title: String,
        artist: String,
        durationMs: Long,
        quality: LosslessQuality,
    ): LosslessTrack? {
        val results = search(LosslessMatcher.query(title, artist), quality)
        return LosslessMatcher.pick(title, artist, durationMs, results)
    }

    suspend fun search(query: String, quality: LosslessQuality): List<LosslessTrack> {
        val body = api("/search?q=${encode(query)}&quality=${quality.apiValue}&atmos=none")
        return parseTracks(body)
    }

    /** The address the server hands out for [trackId], before it is looked at. */
    suspend fun streamUrl(trackId: String, quality: LosslessQuality): String {
        val body = api("/stream/${encode(trackId)}?quality=${quality.apiValue}&atmos=none")
        val json = runCatching { JSONObject(body) }.getOrNull()
            ?: throw LosslessUnavailableException("Stream answer is not JSON")
        return listOf("url", "streamUrl", "directUrl")
            .firstNotNullOfOrNull { key -> json.optString(key).takeIf { it.startsWith("http") } }
            ?: throw LosslessUnavailableException("Stream answer has no address")
    }

    /**
     * What [url] is. The server does not say, so the answer is looked at: a
     * manifest is read whole (it is a few kilobytes), a file only as far as its
     * headers. Either way the request is bounded.
     */
    suspend fun source(url: String): LosslessSource {
        if (url.substringBefore('?').endsWith(".mpd", ignoreCase = true)) {
            return LosslessSource.Dash(url, text(url))
        }
        val request = Request.Builder().url(url)
            .header("User-Agent", USER_AGENT)
            .header("Range", "bytes=0-$PROBE_BYTES")
            .build()
        return http.newCall(request).await().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} for the stream")
            val type = response.header("Content-Type").orEmpty().lowercase()
            val head = response.body?.source()?.let { source ->
                source.request(PROBE_BYTES.toLong())
                source.buffer.snapshot().utf8().take(PROBE_BYTES)
            }.orEmpty()
            val looksLikeXml = head.trimStart().let { it.startsWith("<?xml") || it.startsWith("<MPD") }
            when {
                // Only the first bytes were read; a manifest is small, so it
                // is read again whole.
                "dash+xml" in type || looksLikeXml -> LosslessSource.Dash(url, text(url))
                else -> LosslessSource.File(url)
            }
        }
    }

    /** One resolved source for a song, or null when the server has none. */
    suspend fun resolve(
        title: String,
        artist: String,
        durationMs: Long,
        quality: LosslessQuality,
    ): Pair<LosslessTrack, LosslessSource>? {
        val track = find(title, artist, durationMs, quality) ?: return null
        val url = streamUrl(track.id, quality)
        return track to source(url)
    }

    private suspend fun api(pathAndQuery: String): String {
        val url = (base + pathAndQuery).toHttpUrlOrNull()
            ?: throw IOException("The lossless server address is not a valid URL")
        val builder = Request.Builder().url(url).header("User-Agent", USER_AGENT)
        if (config.secret.isNotBlank()) {
            val ts = (System.currentTimeMillis() / 1000L).toString()
            builder.header("X-LW-TS", ts)
                .header("X-LW-Sign", sign(config.secret, ts, "GET", url.encodedPath, token))
                .header("X-LW-Intent", "stream")
        }
        return http.newCall(builder.build()).await().use { response ->
            when {
                response.code == 401 || response.code == 403 ->
                    throw LosslessAuthException(response.code)
                response.code == 404 -> throw LosslessUnavailableException("Not found on the server")
                !response.isSuccessful -> throw IOException("HTTP ${response.code} from the lossless server")
            }
            response.body?.string().orEmpty()
        }
    }

    private suspend fun text(url: String): String =
        http.newCall(Request.Builder().url(url).header("User-Agent", USER_AGENT).build())
            .await().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code} for the manifest")
                response.body?.string().orEmpty()
            }

    private val http: OkHttpClient get() = clientFor(context)

    companion object {
        private const val TAG = "LosslessClient"
        const val USER_AGENT = "Koda/1.0"
        private const val PROBE_BYTES = 2048
        private val TOKEN = Regex("""/a/([^/?#]+)""")

        @Volatile
        private var client: OkHttpClient? = null

        private fun clientFor(context: Context): OkHttpClient =
            client ?: synchronized(this) {
                client ?: OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        if (ThemePreferences.isLocalOnly(context.applicationContext)) {
                            throw IOException("Local only mode is on: network disabled")
                        }
                        chain.proceed(chain.request())
                    }
                    .connectTimeout(8, TimeUnit.SECONDS)
                    .readTimeout(12, TimeUnit.SECONDS)
                    .callTimeout(20, TimeUnit.SECONDS)
                    .build()
                    .also { client = it }
            }

        internal fun sign(secret: String, ts: String, method: String, path: String, token: String): String {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
            val digest = mac.doFinal("$ts\n${method.uppercase()}\n$path\n$token".toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
        }

        private fun encode(text: String): String =
            java.net.URLEncoder.encode(text, "UTF-8").replace("+", "%20")

        /**
         * Tracks out of a search answer. The server writes ids as strings or
         * numbers and the duration in seconds; anything without an id or a
         * title is skipped rather than guessed at.
         */
        internal fun parseTracks(body: String): List<LosslessTrack> {
            val array: JSONArray = runCatching {
                val trimmed = body.trimStart()
                if (trimmed.startsWith("[")) JSONArray(trimmed)
                else JSONObject(trimmed).let { root ->
                    root.optJSONArray("tracks") ?: root.optJSONArray("items") ?: JSONArray()
                }
            }.getOrElse {
                KLog.w(TAG, "Search answer is not JSON")
                JSONArray()
            }
            return (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val id = item.opt("id")?.toString()?.takeIf { it.isNotBlank() && it != "null" }
                    ?: return@mapNotNull null
                val title = item.optString("title").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val artist = when (val raw = item.opt("artist")) {
                    is JSONObject -> raw.optString("name")
                    is String -> raw
                    else -> ""
                }
                LosslessTrack(
                    id = id,
                    title = title,
                    artist = artist,
                    album = when (val raw = item.opt("album")) {
                        is JSONObject -> raw.optString("title")
                        is String -> raw
                        else -> ""
                    },
                    durationSec = item.optInt("duration", 0).coerceAtLeast(0),
                    format = item.optString("format", "flac"),
                    quality = item.optString("quality"),
                )
            }
        }
    }
}

/** The server refused the address or the secret. */
class LosslessAuthException(val code: Int) : IOException("The lossless server refused access (HTTP $code)")

/** Enqueued rather than executed, so cancelling the coroutine really ends the call. */
internal suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { runCatching { cancel() } }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isActive) continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            if (continuation.isActive) continuation.resume(response) else response.close()
        }
    })
}
