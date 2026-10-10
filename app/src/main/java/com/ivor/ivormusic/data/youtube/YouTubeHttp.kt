package com.ivor.ivormusic.data.youtube

import android.content.Context
import com.ivor.ivormusic.data.SessionManager
import com.ivor.ivormusic.data.SessionRefreshInterceptor
import com.ivor.ivormusic.data.ThemePreferences
import com.ivor.ivormusic.data.YouTubeAuthUtils
import com.ivor.ivormusic.data.YouTubeRequestLedger
import com.ivor.ivormusic.data.YouTubeSession
import com.ivor.ivormusic.data.stream.VisitorIdentity
import com.ivor.ivormusic.util.KLog
import java.util.concurrent.TimeUnit
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient

/**
 * What every call to YouTube is made with: the HTTP clients, the visitor
 * identity, the content region and the session verdict.
 *
 * One per [com.ivor.ivormusic.data.YouTubeRepository]. The clients are built
 * per instance because their interceptors read this instance's
 * [SessionManager]; the sockets, dispatcher threads and disk cache under them
 * are shared by the whole process.
 */
internal class YouTubeHttp(
    private val context: Context,
    private val sessionManager: SessionManager,
) {
    // Local-only kill-switch: checked per request so flipping the setting
    // needs no restart. newBuilder() copies interceptors, so this also guards
    // streamResolveClient and the NewPipe downloader (same client instance).
    val okHttpClient = OkHttpClient.Builder()
        .dispatcher(sharedHttpDispatcher)
        .connectionPool(sharedConnectionPool)
        .addInterceptor { chain ->
            if (ThemePreferences.isLocalOnly(context)) {
                throw java.io.IOException("Local only mode is on: network disabled")
            }
            chain.proceed(chain.request())
        }
        // After the kill-switch, so a request local-only mode refused is not
        // counted as one YouTube saw.
        .addInterceptor(YouTubeRequestLedger)
        // Folds Google's rotated session cookies back into storage. Without it
        // the login snapshot goes stale on its own and every authenticated
        // endpoint quietly answers as signed out. A *network* interceptor
        // because the rotation often rides on a 302 inside the chain, which an
        // application interceptor never sees. See SessionRefreshInterceptor.
        .addNetworkInterceptor(SessionRefreshInterceptor(sessionManager))
        .apply { httpCache(context)?.let { cache(it) } }
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // Dedicated client for stream resolution. callTimeout is a hard wall-clock
    // cap enforced by OkHttp itself — unlike withTimeoutOrNull, it cannot be
    // defeated by a thread blocked inside execute().
    val streamResolveClient = okHttpClient.newBuilder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .build()

    // The client NewPipe's downloader runs on. It needs its own callTimeout for
    // the same reason streamResolveClient has one, and the reason is sharper
    // here: NewPipe's Downloader.execute() is a *blocking* call, and one
    // extraction is many of them in sequence. [verified September 2026: a
    // single fetchPage() of an ordinary track made eight requests — an
    // ANDROID visitor_id mint, reel/reel_item_watch, a visionOS visitor_id and
    // /player, sw.js, a WEB visitor_id, the WEB metadata /player and /next.]
    // On the bare 30s connect/read budget of okHttpClient that is minutes of
    // worst case, and because the thread is blocked inside execute() no
    // coroutine timeout above it can cut it short. A per-request wall-clock cap
    // is the only thing that bounds one of those requests at all; what bounds
    // the *wait* is NewPipeAudioSource.withinBudget, which is where playback
    // responsiveness actually comes from.
    val newPipeClient = okHttpClient.newBuilder()
        .callTimeout(NEWPIPE_REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    /** Koda's own visitorData, sent on every InnerTube call and minted in data/stream. */
    val visitorIdentity = VisitorIdentity(
        context = context,
        quickHttp = streamResolveClient,
        pageHttp = okHttpClient,
        webClientVersion = WEB_VERSION,
        browserUserAgent = BROWSER_USER_AGENT,
        region = ::contentRegion,
    )

    /** The country every InnerTube context and NewPipe search ranks for. */
    fun contentRegion(): String = ThemePreferences.resolveContentRegion(context)

    /**
     * Read YouTube's own verdict on the session out of a response.
     *
     * Every InnerTube response reports `logged_in` in its responseContext
     * tracking params. When the app sent cookies and a SAPISIDHASH and still
     * gets `0` back, the stored session is dead - which used to surface only as
     * an empty subscriptions tab and a blank account name, with no hint that
     * signing in again was what was needed. A `1` clears the flag, so a session
     * revived by a cookie rotation heals itself without a round trip through
     * the login screen.
     */
    fun noteSessionState(body: String, session: YouTubeSession?) {
        if (session == null) return
        val match = LOGGED_IN_TRACKING_PARAM.find(body) ?: return
        // Against the session the request went out with, not whoever is active
        // now: a response that outlives an account switch used to badge the
        // profile the user had just switched to as expired.
        sessionManager.noteSessionExpired(session, match.groupValues[1] == "0")
    }

    private companion object {
        // A backstop on one NewPipe request, and generous on purpose: this
        // downloader serves search, playlists and channel pages, whose
        // responses are far larger than a /player call and which had no
        // wall-clock cap at all before. What playback feels is the separate
        // wait budget in data/stream; tightening this to match it would turn a
        // slow connection into failed searches to fix a playback problem the
        // budget already fixes.
        private const val NEWPIPE_REQUEST_TIMEOUT_SECONDS = 20L

        /**
         * Shared HTTP cache for the plain GETs this app makes - the channel
         * Atom feeds above all, which carry ETag/Last-Modified and are
         * re-fetched in full on every subscriptions refresh and by the
         * six-hourly upload check. With a cache those become 304s.
         *
         * **Companion-level because it must be, not merely because it is
         * cheaper.** OkHttp's Cache is a DiskLruCache holding an exclusive
         * lock on its directory, and this app builds a YouTubeRepository per
         * ViewModel. A per-instance cache would mean several Cache objects on
         * one directory, which is corruption, not contention. One instance,
         * shared by every client built from it.
         *
         * InnerTube calls are POSTs and are never cached by OkHttp, so this
         * cannot serve a stale feed or a stale playlist.
         */
        @Volatile private var sharedHttpCache: okhttp3.Cache? = null
        private const val HTTP_CACHE_DIR_NAME = "yt_http_cache"
        private const val HTTP_CACHE_BYTES = 10L * 1024 * 1024
        private val httpCacheLock = Any()

        // Repository instances retain their own cookie jars and Local Only
        // interceptors, but sockets and dispatcher threads are transport
        // resources rather than account state. Sharing both avoids building a
        // fresh connection pool and executor for every ViewModel.
        private val sharedHttpDispatcher = Dispatcher().apply {
            maxRequests = 32
            maxRequestsPerHost = 8
        }
        private val sharedConnectionPool = ConnectionPool(
            maxIdleConnections = 8,
            keepAliveDuration = 5,
            timeUnit = TimeUnit.MINUTES,
        )

        private fun httpCache(context: Context): okhttp3.Cache? {
            sharedHttpCache?.let { return it }
            return synchronized(httpCacheLock) {
                sharedHttpCache ?: try {
                    okhttp3.Cache(
                        java.io.File(
                            context.applicationContext.cacheDir,
                            HTTP_CACHE_DIR_NAME,
                        ),
                        HTTP_CACHE_BYTES,
                    ).also { sharedHttpCache = it }
                } catch (e: Exception) {
                    // A cache is an optimisation; losing it must not stop the
                    // app making requests.
                    KLog.w("YouTubeRepository", "HTTP cache unavailable: ${e.message}")
                    null
                }
            }
        }
    }
}

// YouTube's own account verdict, in the responseContext tracking params
// of every InnerTube response: {"key":"logged_in","value":"0"|"1"}.
// Tolerant of the pretty-printed spacing so it matches either form.
internal val LOGGED_IN_TRACKING_PARAM =
    Regex("\"logged_in\"\\s*,\\s*\"value\"\\s*:\\s*\"([01])\"")

/**
 * Bind a request to the login it is being sent for.
 *
 * The `Cookie` header and the per-origin SAPISIDHASH both come from the
 * same [YouTubeSession], and the session rides along as a request tag so
 * [SessionRefreshInterceptor] folds Google's rotated cookies back into the
 * profile that made the call rather than into whichever profile happens to
 * be active when the response lands.
 *
 * A null session sends the request signed out, which is deliberate for the
 * browse ids that read fine anonymously - an empty Cookie or Authorization
 * header is worse than no header at all.
 */
internal fun okhttp3.Request.Builder.authenticate(
    session: YouTubeSession?,
    origin: String = "https://music.youtube.com",
): okhttp3.Request.Builder {
    if (session == null) return this
    addHeader("Cookie", session.cookies)
    YouTubeAuthUtils.getAuthorizationHeader(session.cookies, origin)?.let {
        addHeader("Authorization", it)
        addHeader("X-Goog-AuthUser", "0")
    }
    return tag(YouTubeSession::class.java, session)
}
