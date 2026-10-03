package com.ivor.ivormusic.data

import com.ivor.ivormusic.util.KLog
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import okio.Buffer

/**
 * Counts what Koda asks YouTube for, per thing the user opened.
 *
 * Throttling and the bot check are verdicts on a device's traffic as a whole,
 * and before this there was no way to see that traffic: a tester could say
 * "videos stopped loading" but not whether one open had cost two requests or
 * twelve, or how many fresh anonymous identities it had minted. The counts are
 * the evidence any request-reduction change has to be judged by.
 *
 * Installed as an application interceptor on `YouTubeRepository`'s base client,
 * which `newBuilder()` copies into the stream-resolution client and the client
 * NewPipe's downloader runs on - so NewPipe's own requests are counted too,
 * which is the point. Media bytes go through the playback data sources and are
 * deliberately not counted here.
 *
 * A *segment* is everything between two [begin] calls, labelled with what the
 * user opened. It is not attribution: a music resolution or a feed refresh that
 * happens while a video is open is counted in that video's segment, and the
 * log line says "since" for that reason. Always on, in release too, because
 * [KLog] is what the bug reporter sends and release is where testers hit
 * throttling; the cost is a map increment per request and one line per open.
 *
 * Only endpoint names, client names and counts are recorded - never a URL,
 * query, header value or body.
 */
object YouTubeRequestLedger : Interceptor {

    private const val TAG = "YTRequests"
    private const val MINT_ENDPOINT = "visitor_id"
    private const val MAX_BODY_PEEK_BYTES = 64L * 1024
    private val CLIENT_NAME = Regex("\"clientName\"\\s*:\\s*\"([A-Za-z0-9_]+)\"")

    private class Tally(val label: String, val startedAtMs: Long) {
        val byEndpoint = LinkedHashMap<String, Int>()
        val failures = LinkedHashMap<String, Int>()
        var total = 0
        var mints = 0
    }

    private val lock = Any()
    private var segment = Tally("app start", System.currentTimeMillis())
    private val session = Tally("session", System.currentTimeMillis())

    /** Close the current segment (logging it) and start one for [label]. */
    fun begin(label: String) {
        val finished = synchronized(lock) {
            val previous = segment
            segment = Tally(label, System.currentTimeMillis())
            previous
        }
        if (finished.total > 0) KLog.i(TAG, render(finished, System.currentTimeMillis()))
    }

    /** The open segment and the session totals, for the bug report header. */
    fun describe(): String = synchronized(lock) {
        val now = System.currentTimeMillis()
        render(segment, now) + "\n" + render(session, now)
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val endpoint = endpointOf(request.url)
            ?: return chain.proceed(request)
        val key = clientOf(request)?.let { "$endpoint($it)" } ?: endpoint
        val response = try {
            chain.proceed(request)
        } catch (e: java.io.IOException) {
            record(key, failure = "io")
            throw e
        }
        // A response served entirely from OkHttp's disk cache never reached
        // YouTube, so it is not traffic YouTube can hold against us.
        if (response.networkResponse == null) return response
        record(key, failure = response.code.takeIf { it !in 200..399 }?.toString())
        return response
    }

    private fun record(key: String, failure: String?) {
        synchronized(lock) {
            for (tally in arrayOf(segment, session)) {
                tally.total++
                tally.byEndpoint[key] = (tally.byEndpoint[key] ?: 0) + 1
                if (key.startsWith(MINT_ENDPOINT)) tally.mints++
                if (failure != null) {
                    val failureKey = "$key $failure"
                    tally.failures[failureKey] = (tally.failures[failureKey] ?: 0) + 1
                }
            }
        }
    }

    private fun render(tally: Tally, nowMs: Long): String = buildString {
        append("[").append(tally.label).append("] ")
        append(tally.total).append(" requests, ")
        append(tally.mints).append(" new visitor ids, ")
        append((nowMs - tally.startedAtMs) / 1000).append("s since: ")
        append(tally.byEndpoint.entries.joinToString(", ") { (k, v) -> if (v == 1) k else "$k x$v" })
        if (tally.failures.isNotEmpty()) {
            append(" | failed: ")
            append(tally.failures.entries.joinToString(", ") { (k, v) -> if (v == 1) k else "$k x$v" })
        }
    }

    /**
     * The endpoint a YouTube request addresses, or null for anything that is
     * not YouTube's. InnerTube calls are named by their method (`player`,
     * `next`, `reel/reel_item_watch`); page and asset fetches by at most their
     * first two path segments, which keeps video and player-script ids out.
     */
    internal fun endpointOf(url: HttpUrl): String? {
        val host = url.host
        val path = url.encodedPath
        if ("/youtubei/v1/" in path) return path.substringAfter("/youtubei/v1/").trimEnd('/')
        val youtube = host == "youtube.com" || host.endsWith(".youtube.com") ||
            host == "youtubei.googleapis.com" || host == "youtube.googleapis.com"
        if (!youtube) return null
        val segments = url.pathSegments.filter(String::isNotEmpty).take(2)
        val prefix = if (host.startsWith("music.")) "music" else "web"
        return "$prefix/" + segments.joinToString("/")
    }

    /**
     * The InnerTube client a request claims to be. The header is what a real
     * client sends; bodies are peeked only when small, because NewPipe does not
     * always send the header and the client is the whole question for `player`.
     */
    private fun clientOf(request: okhttp3.Request): String? {
        request.header("X-YouTube-Client-Name")?.let { return clientNameForId(it) ?: it }
        val body = request.body ?: return null
        val length = body.contentLength()
        if (length !in 1..MAX_BODY_PEEK_BYTES || body.isOneShot()) return null
        return try {
            val buffer = Buffer()
            body.writeTo(buffer)
            clientNameIn(buffer.readUtf8())
        } catch (_: Exception) {
            null
        }
    }

    internal fun clientNameIn(json: String): String? = CLIENT_NAME.find(json)?.groupValues?.get(1)

    private fun clientNameForId(id: String): String? = when (id) {
        "1" -> "WEB"
        "2" -> "MWEB"
        "3" -> "ANDROID"
        "5" -> "IOS"
        "28" -> "ANDROID_VR"
        "67" -> "WEB_REMIX"
        "101" -> "VISIONOS"
        else -> null
    }
}
