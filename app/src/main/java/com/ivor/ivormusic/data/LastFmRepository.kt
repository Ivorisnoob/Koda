package com.ivor.ivormusic.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.ivor.ivormusic.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class LastFmState(
    val enabled: Boolean = false,
    val user: String = "",
    val connected: Boolean = false,
    val hasCredentials: Boolean = false,
    val pendingAuth: Boolean = false,
    val busy: Boolean = false,
    val queued: Int = 0,
    val playCount: String = "",
    val profile: LastFmProfile = LastFmProfile(),
    val recent: List<LastFmTrack> = emptyList(),
    val refreshedAt: Long = 0,
    val error: String? = null
)

/** One coordinator for Settings and the background service: disabling cancels both surfaces' calls. */
class LastFmRepository private constructor(private val context: Context) {
    @Suppress("DEPRECATION")
    private val prefs = EncryptedSharedPreferences.create(context, "lastfm_private",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = Mutex()
    private var generation = 0
    internal val recordingGeneration get() = generation
    private var operation: Job? = null
    private var retryAfter = 0L
    private val client = OkHttpClient.Builder().callTimeout(25, TimeUnit.SECONDS).build()
    private val mutable = MutableStateFlow(snapshot())
    val state = mutable.asStateFlow()
    private val privacyPreferences = ThemePreferences(context)

    init {
        scope.launch {
            combine(IncognitoMode.enabled(context), privacyPreferences.localOnlyMode) { incognito, local -> incognito || local }
                .collect { paused ->
                    if (paused) {
                        generation++
                        operation?.cancel()
                        client.dispatcher.cancelAll()
                        mutable.value = state.value.copy(busy = false)
                    }
                }
        }
    }
    private fun value(key: String) = prefs.getString(key, "").orEmpty()
    private fun queue() = JSONArray(value("queue").ifEmpty { "[]" })
    private fun snapshot() = LastFmState(enabled = prefs.getBoolean("enabled", false),
        user = value("user"), connected = value("session").isNotEmpty(),
        hasCredentials = value("key").isNotEmpty() || value("session").isNotEmpty(),
        pendingAuth = value("token").isNotEmpty(), queued = queue().length(),
        playCount = value("playcount"), refreshedAt = prefs.getLong("refreshed", 0),
        profile = runCatching { LastFmProtocol.profile(JSONObject(value("profile"))) }.getOrDefault(LastFmProfile()),
        recent = runCatching { LastFmProtocol.recent(JSONObject(value("recent"))) }.getOrDefault(emptyList()))
    fun allowed() = state.value.enabled && !IncognitoMode.isEnabled(context) && !ThemePreferences.isLocalOnly(context)
    fun canScrobble() = allowed() && state.value.connected

    fun setEnabled(enabled: Boolean) {
        generation++
        retryAfter = 0
        operation?.cancel()
        client.dispatcher.cancelAll()
        prefs.edit().putBoolean("enabled", enabled).remove("token").remove("queue").apply()
        mutable.value = snapshot()
    }

    fun disconnect() {
        setEnabled(false)
        prefs.edit().clear().apply()
        mutable.value = snapshot()
    }

    fun reportBrowserFailure() { mutable.value = state.value.copy(error = context.getString(R.string.lastfm_error_browser)) }

    fun beginLogin(key: String, secret: String, openBrowser: (String) -> Unit) {
        if (!allowed()) return
        val apiKey = key.trim()
        val apiSecret = secret.trim()
        if (!apiKey.matches(Regex("[a-fA-F0-9]{32}")) || !apiSecret.matches(Regex("[a-fA-F0-9]{32}"))) {
            mutable.value = state.value.copy(error = context.getString(R.string.lastfm_error_credentials))
            return
        }
        generation++
        operation?.cancel()
        client.dispatcher.cancelAll()
        prefs.edit().remove("session").remove("token")
            .putString("key", apiKey).putString("secret", apiSecret).apply()
        mutable.value = snapshot()
        work {
            val token = request("auth.getToken", signed = true).getString("token")
            prefs.edit().putString("token", token).putLong("token_at", System.currentTimeMillis()).apply()
            mutable.value = state.value.copy(pendingAuth = true)
            openBrowser("https://www.last.fm/api/auth/".toHttpUrl().newBuilder()
                .addQueryParameter("api_key", apiKey).addQueryParameter("token", token).build().toString())
        }
    }

    fun finishLogin() = work {
        check(value("token").isNotBlank()) { context.getString(R.string.lastfm_error_start) }
        check(System.currentTimeMillis() - prefs.getLong("token_at", 0) < 3_600_000) { context.getString(R.string.lastfm_error_expired) }
        val session = request("auth.getSession", mapOf("token" to value("token")), signed = true).getJSONObject("session")
        val name = session.getString("name")
        val key = session.getString("key")
        check(name.isNotBlank() && key.isNotBlank()) { context.getString(R.string.lastfm_error_session) }
        if (!name.equals(value("user"), ignoreCase = true)) {
            prefs.edit().remove("queue").remove("playcount").remove("recent").remove("refreshed").remove("profile").apply()
        }
        prefs.edit().putString("session", key).putString("user", name).remove("token").apply()
        mutable.value = snapshot().copy(busy = true)
        flush()
        refreshData()
    }

    fun restartLogin(openBrowser: (String) -> Unit) = beginLogin(value("key"), value("secret"), openBrowser)

    fun refresh() = work { flush(); refreshData() }

    private suspend fun refreshData() {
        if (!state.value.connected) return
        val params = mapOf("user" to value("user"))
        val user = request("user.getInfo", params).getJSONObject("user")
        val response = request("user.getRecentTracks", params + ("limit" to "20"))
        val recent = LastFmProtocol.recent(response)
        prefs.edit().putString("playcount", user.getString("playcount")).putString("recent", response.toString())
            .putString("profile", user.toString())
            .putLong("refreshed", System.currentTimeMillis()).apply()
        mutable.value = state.value.copy(playCount = user.getString("playcount"), recent = recent, profile = LastFmProtocol.profile(user),
            refreshedAt = System.currentTimeMillis())
    }

    fun nowPlaying(artist: String, track: String, album: String, duration: Long) {
        if (!canScrobble() || operation?.isActive == true) return
        work { request("track.updateNowPlaying", trackParams(artist, track, album, duration), authenticated = true) }
    }

    fun enqueue(artist: String, track: String, album: String, duration: Long, started: Long) {
        if (!canScrobble()) return
        val entries = queue()
        if (entries.length() >= 1000) {
            mutable.value = state.value.copy(error = context.getString(R.string.lastfm_error_full))
            return
        }
        entries.put(JSONObject(trackParams(artist, track, album, duration) + ("timestamp" to started.toString())))
        prefs.edit().putString("queue", entries.toString()).apply()
        mutable.value = state.value.copy(queued = entries.length())
        syncPending()
    }

    fun syncPending() {
        if (canScrobble() && state.value.queued > 0 && operation?.isActive != true) work { flush() }
    }

    private suspend fun flush() {
        if (!canScrobble()) return
        // Persist after each acknowledgement; retries retain the original start timestamp.
        while (queue().length() > 0 && canScrobble()) {
            val entry = queue().getJSONObject(0)
            val params = entry.keys().asSequence().associateWith { entry.getString(it) }
            val ignored = LastFmProtocol.scrobbleCode(request("track.scrobble", params, authenticated = true))
            if (ignored == 5) {
                retryAfter = System.currentTimeMillis() + 300_000
                error(context.getString(R.string.lastfm_error_daily))
            }
            check(ignored in 0..4) { context.getString(R.string.lastfm_error_result) }
            val current = queue()
            current.remove(0)
            prefs.edit().putString("queue", current.toString()).apply()
            mutable.value = state.value.copy(queued = current.length(), error =
                if (ignored != 0) context.getString(R.string.lastfm_error_ignored, ignored) else state.value.error)
        }
    }

    private fun trackParams(artist: String, track: String, album: String, duration: Long) = buildMap {
        put("artist", artist); put("track", track)
        if (album.isNotBlank()) put("album", album)
        if (duration > 0) put("duration", (duration / 1000).toString())
    }

    private fun work(block: suspend () -> Unit) {
        if (!allowed() || operation?.isActive == true) return
        if (System.currentTimeMillis() < retryAfter) {
            mutable.value = state.value.copy(error = context.getString(R.string.lastfm_error_wait))
            return
        }
        val expected = generation
        operation = scope.launch {
            mutex.withLock {
                mutable.value = state.value.copy(busy = true, error = null)
                try { block() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (e: Exception) {
                    if (e is IOException) retryAfter = System.currentTimeMillis() + 300_000
                    if (expected == generation) mutable.value = state.value.copy(error =
                        if (e is IOException) context.getString(R.string.lastfm_error_network) else if (e is IllegalStateException) e.message ?: context.getString(R.string.lastfm_error_generic) else context.getString(R.string.lastfm_error_generic))
                } finally {
                    if (expected == generation) mutable.value = state.value.copy(busy = false)
                }
            }
        }
    }

    private suspend fun request(method: String, params: Map<String, String> = emptyMap(), signed: Boolean = false, authenticated: Boolean = false): JSONObject {
        check(allowed()) { context.getString(R.string.lastfm_error_paused) }
        val expected = generation
        val all = (params + mapOf("method" to method, "api_key" to value("key"))).toMutableMap()
        if (authenticated) {
            check(state.value.connected) { context.getString(R.string.lastfm_error_connect) }
            all["sk"] = value("session")
        }
        if (signed || authenticated) all["api_sig"] = LastFmProtocol.signature(all, value("secret"))
        all["format"] = "json"
        val builder = Request.Builder()
        if (authenticated) builder.url("https://ws.audioscrobbler.com/2.0/")
            .post(FormBody.Builder().apply { all.forEach { (k, v) -> add(k, v) } }.build())
        else builder.url("https://ws.audioscrobbler.com/2.0/".toHttpUrl().newBuilder()
            .apply { all.forEach { (k, v) -> addQueryParameter(k, v) } }.build())
        val call = client.newCall(builder.build())
        val body = suspendCancellableCoroutine<String> { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(e) }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val text = response.use { it.body?.string() ?: throw IOException("Empty response") }
                        if (continuation.isActive) continuation.resume(text)
                    } catch (e: Exception) { if (continuation.isActive) continuation.resumeWithException(e) }
                }
            })
        }
        ensureActiveRequest(expected)
        val json = JSONObject(body)
        if (json.has("error")) {
            val code = json.getInt("error")
            if (code in setOf(8, 11, 16, 29)) retryAfter = System.currentTimeMillis() + 300_000
            if (code == 9) {
                prefs.edit().remove("session").apply()
                mutable.value = state.value.copy(connected = false)
            }
            error(when (code) {
                9 -> context.getString(R.string.lastfm_error_revoked)
                14 -> context.getString(R.string.lastfm_error_approve)
                15 -> context.getString(R.string.lastfm_error_expired)
                10, 13, 26 -> context.getString(R.string.lastfm_error_api, code)
                29 -> context.getString(R.string.lastfm_error_limit)
                else -> context.getString(R.string.lastfm_error_code, code)
            })
        }
        return json
    }

    private fun ensureActiveRequest(expected: Int) {
        if (generation != expected || !allowed()) throw CancellationException("Last.fm disabled or identity changed")
    }

    companion object {
        @Volatile private var instance: LastFmRepository? = null
        fun get(context: Context): LastFmRepository = instance ?: synchronized(this) {
            instance ?: LastFmRepository(context.applicationContext).also { instance = it }
        }
    }
}
