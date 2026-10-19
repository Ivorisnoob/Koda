package com.ivor.ivormusic.data.stream

import android.content.Context
import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Koda's anonymous identity towards YouTube: the `visitorData` token.
 *
 * YouTube's bot check keys on it. A request without one is refused outright
 * [verified September 2026: ANDROID_VR and VISIONOS answer a token-less
 * `/player` with LOGIN_REQUIRED on most videos], a token shared between
 * installs is flagged, and a token can be refused mid-life. So there is one
 * token per install: minted here, kept for [TTL_MS], persisted so a cold
 * start does not pay for a mint, and replaced the moment YouTube refuses it -
 * otherwise every resolution fails for hours, which users experience as
 * "music suddenly stops playing".
 *
 * The token itself is process-wide (the companion): every surface builds its
 * own repository, and they must all present the same identity. An instance
 * only adds the clients it mints through.
 *
 * @param quickHttp hard-capped client for the small `visitor_id` call, so a
 * dead network cannot stall a mint for 30 seconds.
 * @param pageHttp ordinary client for the ~1.5 MB bootstrap page fallback,
 * which the hard cap kills on a slow connection.
 * @param region the content region the mint is made for.
 */
internal class VisitorIdentity(
    context: Context,
    private val quickHttp: OkHttpClient,
    private val pageHttp: OkHttpClient,
    private val webClientVersion: String,
    private val browserUserAgent: String,
    private val region: () -> String,
) {
    private val prefs by lazy {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    /**
     * Warm the token off the critical path, so the first playback of a
     * session does not pay for the mint before its `/player` call can go out.
     */
    suspend fun prefetch() {
        try {
            current()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            KLog.w(STREAM_TAG, "visitorData prefetch failed: ${e.message}")
        }
    }

    /**
     * The token to send, minting one when there is none or it has aged out.
     *
     * Blank is a real answer, not a soft one: the mint failed and nothing was
     * ever persisted. A caller about to make a `/player` call must treat that
     * as a mint that has to succeed first ([replace] with a blank token),
     * because the call would only be refused.
     */
    suspend fun current(): String {
        freshCached(System.currentTimeMillis())?.let { return it }
        return mutex.withLock {
            val now = System.currentTimeMillis()
            freshCached(now)?.let { return@withLock it }
            // A token persisted by an earlier process stays valid for the
            // full TTL: adopt it rather than mint on every app start. A
            // negative age means the clock moved backwards since the mint;
            // that is treated as expired, not as "forever fresh".
            val persistedAt = prefs.getLong(KEY_TOKEN_AT, 0L)
            if (now - persistedAt in 0 until TTL_MS) {
                persisted()?.let {
                    cached = it
                    cachedAt = persistedAt
                    return@withLock it
                }
            }
            val fresh = mint()
            if (!fresh.isNullOrEmpty()) {
                adopt(fresh, now)
                KLog.i(STREAM_TAG, "visitorData refreshed (len=${fresh.length})")
                fresh
            } else {
                // Reuse a previously good value: this session's, else the
                // last one this install minted. Never one shared across
                // installs - the bot check flags a shared visitorData, which
                // kills all stream resolution.
                cached ?: persisted() ?: ""
            }
        }
    }

    /**
     * The token this install already has, without minting one.
     *
     * For the browse, next and search helpers, which are not suspending and
     * must not block on a network round trip. A token a little past its TTL
     * is still a far better identity for a browse call than none, and
     * `/player`'s own path replaces it on the bot-check signal regardless.
     */
    fun cachedOrNull(): String? = cached ?: persisted()

    /**
     * Drop [refused] and mint a new token now, because YouTube refused it.
     *
     * Returns the new token, or null when the mint failed. A resolution that
     * waited on the lock while another one reminted gets that one's token
     * instead of minting a second.
     */
    suspend fun replace(refused: String): String? = mutex.withLock {
        cached?.takeIf { it != refused }?.let { return@withLock it }
        cached = null
        cachedAt = 0L
        if (prefs.getString(KEY_TOKEN, null) == refused) {
            prefs.edit().remove(KEY_TOKEN).remove(KEY_TOKEN_AT).apply()
        }
        val fresh = mint()
        if (!fresh.isNullOrEmpty()) {
            adopt(fresh, System.currentTimeMillis())
            KLog.i(STREAM_TAG, "visitorData replaced after a refusal")
            fresh
        } else {
            KLog.w(STREAM_TAG, "visitorData replacement failed (mint)")
            null
        }
    }

    /**
     * Whether googlevideo refusing [failedUrl] says nothing about the token
     * Koda holds now, because the URL was signed under one already replaced.
     *
     * A refusal reaches playback well after resolution: a queue, a prefetch
     * or a quality ladder holds URLs signed under the old token, and each of
     * them fails in turn after the first one replaced it. Replacing the
     * current token for those threw away a good one each time - another new
     * visitor from one address, and another roll of the dice for a refused
     * token. False when the URL's token is unknown (a NewPipe URL, or one
     * resolved before this process): the caller then replaces as before.
     */
    fun signedBySupersededToken(failedUrl: String?): Boolean {
        val signer = failedUrl?.let(::signerOf) ?: return false
        if (signer == cachedOrNull()) return false
        KLog.i(STREAM_TAG, "Playback refused a URL signed under a replaced visitorData; keeping the current one")
        return true
    }

    /**
     * [replace] for a caller that only knows playback failed: a `/player`
     * that answered OK with URLs googlevideo then refused. Replaces the token
     * that signed [failedUrl] when that is known, so two refusals arriving
     * together mint once, else the current one. A no-op when there is no
     * token to replace.
     */
    suspend fun replaceAfterPlaybackFailure(failedUrl: String?) {
        val refused = failedUrl?.let(::signerOf) ?: cachedOrNull() ?: return
        try {
            replace(refused)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            KLog.w(STREAM_TAG, "visitorData replacement after a playback failure failed: ${e.message}")
        }
    }

    /**
     * Remember that the stream URLs in [streamingData] were signed under
     * [token], so a refusal of one of them can be traced back to it; see
     * [signedBySupersededToken].
     */
    fun noteSigned(streamingData: JSONObject, token: String) {
        if (token.isBlank()) return
        val key = streamUrlsOf(streamingData).firstNotNullOfOrNull(::responseKeyOf) ?: return
        synchronized(signers) { signers[key] = token }
    }

    private fun signerOf(url: String): String? {
        val key = responseKeyOf(url) ?: return null
        return synchronized(signers) { signers[key] }
    }

    /** Whether googlevideo has been seen serving a whole stream under [token]; see [StreamProbe]. */
    fun isVetted(token: String): Boolean {
        if (vetted == token) return true
        if (prefs.getString(KEY_VETTED, null) != token) return false
        vetted = token
        return true
    }

    fun markVetted(token: String) {
        vetted = token
        prefs.edit().putString(KEY_VETTED, token).apply()
    }

    private fun freshCached(now: Long): String? = cached?.takeIf { now - cachedAt < TTL_MS }

    private fun persisted(): String? = prefs.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() }

    private fun adopt(token: String, now: Long) {
        cached = token
        cachedAt = now
        prefs.edit().putString(KEY_TOKEN, token).putLong(KEY_TOKEN_AT, now).apply()
    }

    /**
     * Mint a token. The dedicated `visitor_id` endpoint first - a few hundred
     * bytes and one round trip [verified July 2026] - then the bootstrap page.
     */
    private suspend fun mint(): String? = withContext(Dispatchers.IO) {
        mintFromApi() ?: mintFromBootstrapPage()
    }

    private fun mintFromApi(): String? = try {
        val body = JSONObject().put(
            "context",
            JSONObject().put(
                "client",
                JSONObject()
                    .put("clientName", "WEB")
                    .put("clientVersion", webClientVersion)
                    .put("hl", "en")
                    .put("gl", region()),
            ),
        ).toString()
        val request = Request.Builder()
            .url("https://www.youtube.com/youtubei/v1/visitor_id?prettyPrint=false")
            .post(body.toRequestBody("application/json".toMediaType()))
            .addHeader("User-Agent", browserUserAgent)
            .build()
        val json = quickHttp.newCall(request).execute().use { it.body?.string().orEmpty() }
        // The response URL-encodes the token's base64 padding; /player wants
        // the raw token.
        JSONObject(json)
            .optJSONObject("responseContext")
            ?.optString("visitorData")
            ?.replace("%3D", "=")
            ?.replace("%3d", "=")
            ?.takeIf { it.isNotEmpty() }
    } catch (e: Exception) {
        KLog.w(STREAM_TAG, "visitor_id mint failed: ${e.message}")
        null
    }

    /**
     * The token as the youtube.com page embeds it. It is JSON-escaped there,
     * so the two escapes that occur in a base64url token are undone.
     */
    private fun mintFromBootstrapPage(): String? = try {
        val request = Request.Builder()
            .url("https://www.youtube.com/")
            .addHeader("User-Agent", browserUserAgent)
            .addHeader("Accept-Language", "en-US,en;q=0.9")
            .build()
        val html = pageHttp.newCall(request).execute().use { it.body?.string().orEmpty() }
        BOOTSTRAP_TOKEN.find(html)
            ?.groupValues?.get(1)
            ?.replace("\\u003d", "=")
            ?.replace("\\u0026", "&")
            ?.takeIf { it.isNotEmpty() }
    } catch (e: Exception) {
        KLog.w(STREAM_TAG, "bootstrap visitorData scrape failed: ${e.message}")
        null
    }

    companion object {
        private const val PREFS = "ivor_visitor_data"
        private const val KEY_TOKEN = "visitor_data"
        private const val KEY_TOKEN_AT = "visitor_data_at"
        private const val KEY_VETTED = "visitor_data_vetted"
        private const val TTL_MS = 6 * 60 * 60 * 1000L
        private val BOOTSTRAP_TOKEN = Regex("\"visitorData\":\"(.*?)\"")

        @Volatile private var cached: String? = null
        @Volatile private var cachedAt: Long = 0L
        @Volatile private var vetted: String? = null
        private val mutex = Mutex()

        /**
         * Which token signed each recent `/player` response, keyed by
         * [responseKeyOf]. Memory only: stream URLs do not outlive the process
         * that resolved them. Bounded, oldest first out; a key that has aged
         * out reads as unknown, which falls back to replacing the current token.
         */
        private val signers = object : LinkedHashMap<String, String>() {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) =
                size > MAX_SIGNERS
        }
        private const val MAX_SIGNERS = 512

        /**
         * Forget the identity, in memory and on disk, to be minted again on
         * the next call. A profile switch needs this: replaying one account's
         * token under another is the "shared value gets flagged" case.
         *
         * @param commitNow force the erase to disk before returning. Only a
         * restore needs it: it kills the process on purpose, and a queued
         * `apply()` dying with it would leave the previous identity persisted.
         */
        fun forget(context: Context, commitNow: Boolean = false) {
            cached = null
            cachedAt = 0L
            vetted = null
            val editor = context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().remove(KEY_TOKEN).remove(KEY_TOKEN_AT).remove(KEY_VETTED)
            if (commitNow) editor.commit() else editor.apply()
        }
    }
}

/**
 * The key a googlevideo URL shares with every other URL of the `/player`
 * response it came from, and with no other response: its `ei`. [verified
 * October 2026, visionOS, `.probe/visionos_url_provenance_probe.py`: one value
 * across every format of a response, eight different values over eight calls,
 * two tokens and two songs; the token itself appears nowhere in the URL.] A
 * manifest URL carries it as a path segment (`/ei/<value>/`) instead of a query
 * parameter. Null for anything else.
 */
internal fun responseKeyOf(url: String): String? {
    val parsed = url.toHttpUrlOrNull() ?: return null
    parsed.queryParameter("ei")?.takeIf { it.isNotBlank() }?.let { return it }
    val segments = parsed.pathSegments
    val at = segments.indexOf("ei")
    if (at < 0) return null
    return segments.getOrNull(at + 1)?.takeIf { it.isNotBlank() }
}

/** Every stream URL a `/player` response's `streamingData` hands out. */
private fun streamUrlsOf(streamingData: JSONObject): Sequence<String> = sequence {
    for (key in listOf("adaptiveFormats", "formats")) {
        val formats = streamingData.optJSONArray(key) ?: continue
        for (i in 0 until formats.length()) {
            formats.optJSONObject(i)?.optString("url")?.takeIf { it.isNotBlank() }?.let { yield(it) }
        }
    }
    for (key in listOf("hlsManifestUrl", "dashManifestUrl")) {
        streamingData.optString(key).takeIf { it.isNotBlank() }?.let { yield(it) }
    }
}
