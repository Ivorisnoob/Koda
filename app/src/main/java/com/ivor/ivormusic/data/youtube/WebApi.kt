package com.ivor.ivormusic.data.youtube

import com.ivor.ivormusic.data.SessionManager
import com.ivor.ivormusic.data.YouTubeRateLimit
import com.ivor.ivormusic.data.YouTubeSession
import com.ivor.ivormusic.util.KLog
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * InnerTube on www.youtube.com as the WEB client: browse, next, search,
 * engagement and the account writes of video mode.
 *
 * Every call carries Koda's visitorData and the client headers a real WEB
 * client sends, and is signed for the www origin when there is a session. The
 * functions answer with the raw body and leave the shape to the parsers.
 */
internal class WebApi(
    private val http: YouTubeHttp,
    private val sessionManager: SessionManager,
) {
    /**
     * The WEB client context for www.youtube.com calls.
     *
     * Carries [VisitorIdentity.cachedOrNull] when there is one. The app mints a
     * visitorData, persists it, TTLs it and re-mints it when the bot check
     * flags it - and for a long time used it on exactly one endpoint family
     * (/player). Every browse, next, search and engagement call went out with
     * no visitor identity at all, so from YouTube's side each was a brand new
     * anonymous client, hundreds per session from one address. That is a large
     * part of what makes a device's standing degrade over a session rather than
     * all at once.
     */
    fun webContext(): JSONObject =
        JSONObject().put(
            "client",
            JSONObject()
                .put("clientName", "WEB")
                .put("clientVersion", WEB_VERSION)
                .put("hl", "en")
                .put("gl", http.contentRegion())
                .apply {
                    http.visitorIdentity.cachedOrNull()?.let { put("visitorData", it) }
                }
        )

    /**
     * POST to an InnerTube endpoint on www.youtube.com, attaching cookies and
     * SAPISIDHASH when logged in. Returns the raw response body or null on failure.
     */
    fun postWatchApi(
        endpoint: String,
        body: JSONObject,
        session: YouTubeSession? = sessionManager.captureSession()
    ): String? {
        val currentSession = session?.let { sessionManager.currentSession(it) ?: return null }
        val builder = Request.Builder()
            .url("https://www.youtube.com/youtubei/v1/$endpoint?prettyPrint=false")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("User-Agent", BROWSER_USER_AGENT)
            .addHeader("Origin", "https://www.youtube.com")
            .addHeader("X-Origin", "https://www.youtube.com")
            // A real WEB client always sends these; their absence alongside a
            // missing visitor id is most of what makes this traffic look
            // synthetic. Client name 1 is WEB.
            .addHeader("X-YouTube-Client-Name", "1")
            .addHeader("X-YouTube-Client-Version", WEB_VERSION)

        http.visitorIdentity.cachedOrNull()?.let { builder.addHeader("X-Goog-Visitor-Id", it) }

        builder.authenticate(currentSession, "https://www.youtube.com")

        return try {
            http.okHttpClient.newCall(builder.build()).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.string()?.also { http.noteSessionState(it, currentSession) }
                } else {
                    YouTubeRateLimit.note(
                        response.code,
                        "watch api $endpoint",
                        response.header("Retry-After"),
                    )
                    KLog.w(YOUTUBE_TAG, "watch api $endpoint HTTP ${response.code}")
                    null
                }
            }
        } catch (e: Exception) {
            KLog.e(YOUTUBE_TAG, "watch api $endpoint failed", e)
            null
        }
    }

    /**
     * WEB `/browse` on www.youtube.com, signed when there is a session and
     * anonymous when there is not.
     *
     * **It used to return an empty string the moment cookies were missing**,
     * which made every public read built on it look like empty content rather
     * than a missing session: that is what left video-mode playlists reading
     * "No videos in this playlist" for signed-out users. Public browse ids
     * (`VL<playlistId>`, a channel id) answer 200 anonymously - verified
     * August 2026 - so the account headers are what is conditional, not the
     * call. Anything account-scoped (`FEplaylist_aggregation`, `FEhistory`)
     * must gate on [SessionManager.isLoggedIn] at its own call site instead,
     * because signed out this returns a perfectly valid shell with nothing in
     * it rather than an error.
     */
    fun fetchYouTubeBrowse(browseId: String): String {
        val session = sessionManager.captureSession()
        val url = "https://www.youtube.com/youtubei/v1/browse?key=$INNER_TUBE_API_KEY"

        val visitorData = http.visitorIdentity.cachedOrNull()

        // Built through JSONObject rather than string interpolation so the
        // optional visitorData cannot produce malformed JSON.
        val jsonBody = JSONObject()
            .put(
                "context",
                JSONObject().put(
                    "client",
                    JSONObject()
                        .put("clientName", "WEB")
                        .put("clientVersion", WEB_VERSION)
                        .put("hl", "en")
                        .put("gl", http.contentRegion())
                        .apply { visitorData?.let { put("visitorData", it) } }
                )
            )
            .put("browseId", browseId)
            .toString()

        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .addHeader("User-Agent", BROWSER_USER_AGENT)
            .addHeader("Origin", "https://www.youtube.com")
            .addHeader("X-YouTube-Client-Name", "1")
            .addHeader("X-YouTube-Client-Version", WEB_VERSION)
            .apply {
                visitorData?.let { addHeader("X-Goog-Visitor-Id", it) }
            }
            // Signed out this attaches nothing, which is the difference between
            // a public read and a malformed one: an empty Cookie or
            // Authorization header is worse than no header at all.
            .authenticate(session, "https://www.youtube.com")
            .build()

        return try {
            http.okHttpClient.newCall(request).execute().use { response ->
                // This used to return the body whatever the status, so a 429
                // was handed to the parsers as a string, parsed to nothing, and
                // surfaced as empty content - indistinguishable from a real
                // empty result, and invisible to everything upstream.
                if (!response.isSuccessful) {
                    YouTubeRateLimit.note(
                        response.code,
                        "browse $browseId",
                        response.header("Retry-After"),
                    )
                    KLog.w(YOUTUBE_TAG, "browse $browseId HTTP ${response.code}")
                    return ""
                }
                response.body?.string() ?: ""
            }
        } catch (e: Exception) {
            KLog.e(YOUTUBE_TAG, "Error in fetchYouTubeBrowse", e)
            ""
        }
    }

    fun fetchYouTubeBrowseContinuation(token: String): String =
        postWatchApi(
            "browse",
            JSONObject()
                .put("context", webContext())
                .put("continuation", token)
        ).orEmpty()
}
