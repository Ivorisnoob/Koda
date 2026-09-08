package com.ivor.ivormusic.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Owned by the player host, not process-global. No account, third-party proxy or listening-history store. */
internal class MotionArtworkRepository(context: Context) {
    private val tokenFile = File(context.cacheDir, "motion-artwork-web-token")
    private val lock = Mutex()
    private val client = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS).callTimeout(12, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()
    private data class CachedArtwork(val url: String?, val until: Long)
    private val results = object : LinkedHashMap<MotionArtworkResolver.Track, CachedArtwork>(24, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<MotionArtworkResolver.Track, CachedArtwork>?) = size > 24
    }
    private var token: String? = null
    private var retryAfter = 0L

    suspend fun resolve(song: Song): String? = withContext(Dispatchers.IO) {
        lock.withLock {
            val track = MotionArtworkResolver.Track(song.title, song.artist, song.album, song.duration)
            val now = System.currentTimeMillis()
            results[track]?.takeIf { it.until > now }?.let { return@withLock it.url }
            if (now < retryAfter || track.title.isBlank() || track.artist.isBlank()) return@withLock null
            val url = try {
                withTimeoutOrNull(25_000) { lookup(track) }
            } catch (_: IOException) {
                // Provider failures must never turn every skip into another request storm.
                retryAfter = now + 60_000
                null
            } catch (_: org.json.JSONException) { null }
            results[track] = CachedArtwork(url, now + if (url == null) 15 * 60_000 else 6 * 60 * 60_000)
            url
        }
    }

    private suspend fun lookup(track: MotionArtworkResolver.Track): String? {
        val url = "https://amp-api-edge.music.apple.com/v1/catalog/us/search".toHttpUrl().newBuilder()
            .addQueryParameter("term", "${track.title} ${track.artist}")
            .addQueryParameter("types", "songs").addQueryParameter("include[songs]", "albums")
            .addQueryParameter("format[resources]", "map").addQueryParameter("extend", "editorialVideo")
            .addQueryParameter("l", "en-US").addQueryParameter("limit", "10")
            .addQueryParameter("platform", "web").build()
        repeat(2) { attempt ->
            val bearer = webToken() ?: return null
            val request = Request.Builder().url(url).header("Authorization", "Bearer $bearer")
                .header("Origin", HOME).header("Referer", "$HOME/").build()
            val response = get(request, 2_000_000)
            if (response.code == 401 || response.code == 403) {
                token = null
                tokenFile.delete()
                if (attempt == 0) return@repeat
            }
            if (response.code !in 200..299) throw IOException("Artwork catalog unavailable")
            val master = MotionArtworkResolver.masterUrl(JSONObject(response.text), track) ?: return null
            val playlist = get(Request.Builder().url(master).build(), 256_000)
            if (playlist.code !in 200..299) throw IOException("Artwork playlist unavailable")
            return MotionArtworkResolver.rendition(master, playlist.text)
        }
        return null
    }

    private suspend fun webToken(): String? {
        val now = System.currentTimeMillis() / 1_000
        token?.takeIf { MotionArtworkResolver.usableWebToken(it, now) }?.let { return it }
        if (tokenFile.isFile && tokenFile.length() < 8_192) {
            tokenFile.readText().takeIf { MotionArtworkResolver.usableWebToken(it, now) }?.let {
                token = it
                return it
            }
        }
        val home = get(Request.Builder().url(HOME).build(), 2_000_000)
        if (home.code !in 200..299) throw IOException("Artwork web player unavailable")
        val asset = Regex("""/assets/[^"'<>\s]*index[^"'<>\s]*\.js""").find(home.text)?.value ?: return null
        val bundle = get(Request.Builder().url(HOME + asset).build(), 12_000_000)
        if (bundle.code !in 200..299) throw IOException("Artwork web bundle unavailable")
        val found = Regex("""eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+""")
            .findAll(bundle.text).map { it.value }
            .firstOrNull { MotionArtworkResolver.usableWebToken(it, now) } ?: return null
        token = found
        // Cache only public authentication material, never song names or search responses on disk.
        try { tokenFile.writeText(found) } catch (_: IOException) { /* Memory cache still works. */ }
        return found
    }

    private data class Reply(val code: Int, val text: String)

    private suspend fun get(request: Request, limit: Long): Reply = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { continuation.resumeWithException(e) }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val reply = response.use {
                        val source = it.body?.source() ?: throw IOException("Missing artwork response")
                        if (source.request(limit + 1)) throw IOException("Artwork response too large")
                        Reply(it.code, source.readUtf8())
                    }
                    continuation.resume(reply)
                } catch (e: IOException) { continuation.resumeWithException(e) }
            }
        })
    }

    companion object { private const val HOME = "https://music.apple.com" }
}
