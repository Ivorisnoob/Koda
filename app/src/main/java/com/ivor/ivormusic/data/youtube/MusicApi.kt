package com.ivor.ivormusic.data.youtube

import com.ivor.ivormusic.data.SessionManager
import com.ivor.ivormusic.data.YouTubeAuthUtils
import com.ivor.ivormusic.data.YouTubeRateLimit
import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.CancellationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * InnerTube on music.youtube.com as the WEB_REMIX client.
 *
 * Three families that differ in what they need from the session:
 * [postMusicMetadata] and [browseMusic] read public pages and attach the
 * account only when there is one, [fetchInternalApi] reads the account's own
 * library and answers nothing signed out, and [postMusicApi] is an account
 * write that must be signed. The SAPISIDHASH is per origin, so a session
 * signed for www.youtube.com is refused here.
 */
internal class MusicApi(
    private val http: YouTubeHttp,
    private val sessionManager: SessionManager,
) {
    fun musicContext(): JSONObject =
        JSONObject().put(
            "client",
            JSONObject()
                .put("clientName", "WEB_REMIX")
                .put("clientVersion", WEB_REMIX_VERSION)
                .put("hl", "en")
                .put("gl", http.contentRegion())
        )

    fun postMusicMetadata(endpoint: String, payload: JSONObject): JSONObject? {
        return try {
            val session = sessionManager.captureSession()
            val client = JSONObject().put("clientName", "WEB_REMIX")
                .put("clientVersion", WEB_REMIX_VERSION).put("hl", "en").put("gl", http.contentRegion())
            http.visitorIdentity.cachedOrNull()?.let { client.put("visitorData", it) }
            payload.put("context", JSONObject().put("client", client))
            val builder = Request.Builder()
                .url("https://music.youtube.com/youtubei/v1/$endpoint")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .addHeader("User-Agent", BROWSER_USER_AGENT)
                .addHeader("Origin", "https://music.youtube.com")
                .addHeader("X-YouTube-Client-Name", "67")
                .addHeader("X-YouTube-Client-Version", WEB_REMIX_VERSION)
            http.visitorIdentity.cachedOrNull()?.let { builder.addHeader("X-Goog-Visitor-Id", it) }
            builder.authenticate(session)
            http.okHttpClient.newCall(builder.build()).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful || body.isBlank()) {
                    KLog.w(YOUTUBE_TAG, "Music $endpoint HTTP ${response.code}: ${body.take(200)}")
                    null
                } else {
                    http.noteSessionState(body, session)
                    JSONObject(body).takeUnless { it.has("error") }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            KLog.e(YOUTUBE_TAG, "Music $endpoint metadata request failed", e)
            null
        }
    }

    /**
     * POST to InnerTube /browse with the WEB_REMIX client. Works anonymously;
     * cookies are attached when logged in so results are personalized.
     * Unlike [fetchInternalApi], this does NOT require a login.
     */
    fun browseMusic(browseId: String, params: String? = null): String? =
        postMusicMetadata("browse", JSONObject().put("browseId", browseId).apply {
            if (params != null) put("params", params)
        })?.toString()

    fun fetchInternalApi(endpoint: String): String {
        val session = sessionManager.captureSession() ?: return ""
        val isBrowse = !endpoint.contains("/") // simple check: browseId vs endpoint path
        
        val url = if (isBrowse) {
            "https://music.youtube.com/youtubei/v1/browse"
        } else {
            "https://music.youtube.com/youtubei/v1/$endpoint"
        }
        
        // Construct complete JSON body for WEB_REMIX client
        val jsonBody = if (isBrowse) {
            """
                {
                    "context": {
                        "client": {
                            "clientName": "WEB_REMIX",
                            "clientVersion": "$WEB_REMIX_VERSION",
                            "hl": "en",
                            "gl": "${http.contentRegion()}"
                        }
                    },
                    "browseId": "$endpoint"
                }
            """.trimIndent()
        } else {
             """
                {
                    "context": {
                        "client": {
                            "clientName": "WEB_REMIX",
                            "clientVersion": "$WEB_REMIX_VERSION",
                            "hl": "en",
                            "gl": "${http.contentRegion()}"
                        }
                    }
                }
            """.trimIndent()
        }

        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .authenticate(session)
            .addHeader("User-Agent", BROWSER_USER_AGENT)
            .addHeader("Origin", "https://music.youtube.com")
            // Client name 67 is WEB_REMIX. Sent for the same reason the WEB
            // calls now send theirs: a client that never identifies itself is
            // the shape anti-abuse looks for. The visitor id rides as a header
            // rather than in the body because this endpoint's context is built
            // from raw JSON strings in five places; the header carries the same
            // identity without touching any of them.
            .addHeader("X-YouTube-Client-Name", "67")
            .addHeader("X-YouTube-Client-Version", WEB_REMIX_VERSION)
            .apply {
                http.visitorIdentity.cachedOrNull()?.let { addHeader("X-Goog-Visitor-Id", it) }
            }
            .build()

        return try {
            http.okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    YouTubeRateLimit.note(
                        response.code,
                        "music browse $endpoint",
                        response.header("Retry-After"),
                    )
                    KLog.w(YOUTUBE_TAG, "music browse $endpoint HTTP ${response.code}")
                    return ""
                }
                (response.body?.string() ?: "").also { http.noteSessionState(it, session) }
            }
        } catch (e: Exception) {
            KLog.e(YOUTUBE_TAG, "Music browse request failed", e)
            ""
        }
    }

    /**
     * Fetch continuation page using continuation token.
     */
    fun fetchContinuation(continuationToken: String): String {
        // Account headers are conditional, the call is not. [verified August
        // 2026: a public playlist's continuation chain walks to the end with no
        // cookies, no auth header and no visitorData.] Returning "" without a
        // session made every signed-out continuation look like a failed fetch,
        // which capped public playlists at their first page just as the parser
        // gap did for signed-in ones - the same symptom from a second cause.
        val session = sessionManager.captureSession()

        val jsonBody = """
            {
                "context": {
                    "client": {
                        "clientName": "WEB_REMIX",
                        "clientVersion": "$WEB_REMIX_VERSION",
                        "hl": "en",
                        "gl": "${http.contentRegion()}"
                    }
                },
                "continuation": "$continuationToken"
            }
        """.trimIndent()

        val request = Request.Builder()
            .url("https://music.youtube.com/youtubei/v1/browse")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .addHeader("User-Agent", BROWSER_USER_AGENT)
            .addHeader("Origin", "https://music.youtube.com")
            .authenticate(session)
            .build()

        return try {
            http.okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    YouTubeRateLimit.note(response.code, request.url.toString(), response.header("Retry-After"))
                    KLog.w(YOUTUBE_TAG, "Music continuation failed: HTTP ${response.code}")
                    return ""
                }
                val body = response.body?.string().orEmpty()
                if (body.isBlank() || JSONObject(body).has("error")) return ""
                body.also { http.noteSessionState(it, session) }
            }
        } catch (e: Exception) {
            KLog.e(YOUTUBE_TAG, "Music continuation request failed", e)
            ""
        }
    }

    /**
     * POST to an InnerTube endpoint on music.youtube.com with cookies and a
     * music-origin SAPISIDHASH (the hash is per-origin — a www.youtube.com
     * hash is rejected here). Returns the raw body or null on failure.
     */
    fun postMusicApi(endpoint: String, body: JSONObject): String? {
        val session = sessionManager.captureSession() ?: return null
        // Signing is not optional here: these are account writes, and an
        // unsigned one answers 200 having done nothing.
        if (YouTubeAuthUtils.getSapisid(session.cookies) == null) return null
        val request = Request.Builder()
            .url("https://music.youtube.com/youtubei/v1/$endpoint?prettyPrint=false")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .authenticate(session)
            .addHeader("User-Agent", BROWSER_USER_AGENT)
            .addHeader("Origin", "https://music.youtube.com")
            .addHeader("X-Origin", "https://music.youtube.com")
            .build()
        return try {
            http.okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.string()
                } else {
                    KLog.w(YOUTUBE_TAG, "music api $endpoint HTTP ${response.code}")
                    null
                }
            }
        } catch (e: Exception) {
            KLog.e(YOUTUBE_TAG, "music api $endpoint failed", e)
            null
        }
    }
}
