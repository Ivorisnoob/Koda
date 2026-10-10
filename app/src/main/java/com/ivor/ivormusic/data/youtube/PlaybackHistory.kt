package com.ivor.ivormusic.data.youtube

import android.content.Context
import com.ivor.ivormusic.data.HistoryPingResult
import com.ivor.ivormusic.data.IncognitoMode
import com.ivor.ivormusic.data.PlayerSignatureTimestamp
import com.ivor.ivormusic.data.SessionManager
import com.ivor.ivormusic.data.ThemePreferences
import com.ivor.ivormusic.data.VideoHistorySession
import com.ivor.ivormusic.data.YouTubeSession
import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Telling YouTube what was played, so the account's history and
 * recommendations follow Koda: one report per song, and a session of pings
 * per video.
 *
 * Signed in only, and never while history is paused or incognito is on; the
 * video path rechecks before every ping because the setting can change
 * mid-video.
 */
internal class PlaybackHistory(
    private val context: Context,
    private val http: YouTubeHttp,
    private val webApi: WebApi,
    private val sessionManager: SessionManager,
) {
    /** What the history /player calls must carry; see [PlayerSignatureTimestamp]. */
    private val playerSignatureTimestamp = PlayerSignatureTimestamp(context, http.okHttpClient)

    private val videoHistoryPreferences by lazy { ThemePreferences(context) }

    /**
     * Reports playback to YouTube Music history.
     * This mimics the web player's behavior to ensure the song appears in history.
     * 
     * The flow is:
     * 1. Call /player endpoint to get playback tracking URLs
     * 2. Call the videostatsPlaybackUrl to register the play in history
     */
    suspend fun reportPlayback(videoId: String) = withContext(Dispatchers.IO) {
        if (!sessionManager.isLoggedIn()) return@withContext
        // Incognito covers the account's own history too, not only Koda's.
        // Gated here rather than at the call sites so nothing that starts
        // playback later has to remember. The music history switch does not:
        // it is the on-device log, and its setting says it is separate from
        // the account's history (si_music_history).
        if (IncognitoMode.isEnabled(context)) return@withContext

        try {
            val session = sessionManager.captureSession() ?: return@withContext
            val cpn = generateCpn()

            // This install's own token, which every other InnerTube call rides
            // on, minted here if none is cached yet (a fresh install's first
            // play). Never a fallback literal: a hardcoded visitor id is a
            // stranger's session, and without a token the play is skipped
            // rather than filed under one.
            val visitorData = http.visitorIdentity.current().ifEmpty {
                KLog.w(YOUTUBE_TAG, "History sync: no visitorData, skipped $videoId")
                return@withContext
            }

            // Client constants - using WEB_REMIX (web player)
            val clientName = "WEB_REMIX"
            val clientVersion = WEB_REMIX_VERSION

            // Step 1: Call player endpoint to get tracking URLs. It must carry
            // the player's signatureTimestamp: without one, every video answers
            // "Video unavailable" with no playbackTracking (October 2026).
            fun postPlayer(signatureTimestamp: Int): String? {
                val jsonBody = JSONObject()
                    .put("context", JSONObject().put("client", JSONObject()
                        .put("clientName", clientName)
                        .put("clientVersion", clientVersion)
                        .put("hl", "en")
                        .put("gl", http.contentRegion())
                        .put("visitorData", visitorData)))
                    .put("videoId", videoId)
                    .put("cpn", cpn)
                    .put("playbackContext", JSONObject().put("contentPlaybackContext",
                        JSONObject().put("signatureTimestamp", signatureTimestamp)))
                    .toString()
                val playerRequest = Request.Builder()
                    .url("https://music.youtube.com/youtubei/v1/player")
                    .post(jsonBody.toRequestBody("application/json".toMediaType()))
                    .authenticate(session)
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .addHeader("Origin", "https://music.youtube.com")
                    .addHeader("Referer", "https://music.youtube.com/")
                    .addHeader("X-Goog-Api-Format-Version", "1")
                    .addHeader("X-YouTube-Client-Name", "67") // WEB_REMIX numeric ID
                    .addHeader("X-YouTube-Client-Version", clientVersion)
                    .addHeader("X-Goog-Visitor-Id", visitorData)
                    .build()
                return http.okHttpClient.newCall(playerRequest).execute().use { response ->
                    response.body?.string().also {
                        if (it.isNullOrEmpty()) {
                            KLog.e(YOUTUBE_TAG, "History sync: /player HTTP ${response.code} with an empty body for $videoId")
                        }
                    }
                }
            }

            val sentTimestamp = playerSignatureTimestamp.current()
            var playerResponseBody = postPlayer(sentTimestamp)
                ?.takeIf { it.isNotEmpty() } ?: return@withContext
            if (historyPlayerSignedOut(playerResponseBody, session, "Music")) return@withContext
            var playerJson = JSONObject(playerResponseBody)
            if (playerJson.optJSONObject("playbackTracking") == null) {
                // A signatureTimestamp YouTube no longer accepts looks exactly
                // like this; retry once if a fresh read gives another value.
                val retry = playerSignatureTimestamp.refreshAfterRejection(sentTimestamp)
                if (retry != null) {
                    KLog.w(YOUTUBE_TAG, "History sync: no playbackTracking with sts $sentTimestamp, retrying with $retry")
                    playerResponseBody = postPlayer(retry)
                        ?.takeIf { it.isNotEmpty() } ?: return@withContext
                    if (historyPlayerSignedOut(playerResponseBody, session, "Music")) return@withContext
                    playerJson = JSONObject(playerResponseBody)
                }
            }

            // Parse response to extract playback tracking URL
            val playbackTracking = playerJson.optJSONObject("playbackTracking")

            if (playbackTracking == null) {
                // Log more details about the error
                val playabilityStatus = playerJson.optJSONObject("playabilityStatus")
                val status = playabilityStatus?.optString("status")
                val reason = playabilityStatus?.optString("reason")
                KLog.e(YOUTUBE_TAG, "No playbackTracking. Status: $status, Reason: $reason")
                return@withContext
            }
            
            val videostatsPlaybackUrl = playbackTracking
                .optJSONObject("videostatsPlaybackUrl")
                ?.optString("baseUrl")

            if (videostatsPlaybackUrl.isNullOrEmpty()) {
                KLog.e(YOUTUBE_TAG, "No playback tracking URL found for $videoId")
                return@withContext
            }

            // Step 3: Call the tracking URL to register the play. Credentials
            // only go to the YouTube tracking hosts /player returns, and each
            // parameter is set rather than appended, so one the URL already
            // carries is replaced instead of sent twice.
            val baseTrackingUrl = videostatsPlaybackUrl.toHttpUrlOrNull()
            if (baseTrackingUrl == null || baseTrackingUrl.scheme != "https" ||
                baseTrackingUrl.host !in MUSIC_HISTORY_TRACKING_HOSTS
            ) {
                KLog.w(YOUTUBE_TAG, "History sync: refused a tracking URL on ${baseTrackingUrl?.host}")
                return@withContext
            }
            val trackingUrl = baseTrackingUrl.newBuilder()
                .setQueryParameter("cpn", cpn)
                .setQueryParameter("ver", "2")
                .setQueryParameter("c", clientName)
                .build()

            // The /player call above blocks, and the switch can be flipped or
            // the account changed while it does. This ping is the write that
            // reaches the account, so both are rechecked against live state
            // here rather than only at the top. currentSession also hands back
            // the cookies as they are now, which /player itself may have
            // rotated on the way through.
            if (IncognitoMode.isEnabled(context)) return@withContext
            val live = sessionManager.currentSession(session) ?: run {
                KLog.w(YOUTUBE_TAG, "History sync: login changed before the ping for $videoId")
                return@withContext
            }

            val trackingRequest = Request.Builder()
                .url(trackingUrl)
                .get()
                .authenticate(live)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .addHeader("Origin", "https://music.youtube.com")
                .addHeader("Referer", "https://music.youtube.com/watch?v=$videoId")
                .build()

            val trackingResponse = http.okHttpClient.newCall(trackingRequest).execute()
            if (trackingResponse.isSuccessful) {
                KLog.d(YOUTUBE_TAG, "History sync: ping accepted for $videoId")
            } else {
                KLog.e(YOUTUBE_TAG, "History sync: ping HTTP ${trackingResponse.code} for $videoId")
            }
            trackingResponse.close()

        } catch (e: CancellationException) {
            // The token mint above suspends; never swallow a cancellation.
            throw e
        } catch (e: Exception) {
            KLog.e(YOUTUBE_TAG, "Error in reportPlayback", e)
        }
    }

    internal suspend fun beginVideoHistorySession(
        videoId: String,
        positionMs: Long,
    ): VideoHistorySession? = withContext(Dispatchers.IO) {
        val session = sessionManager.captureSession() ?: return@withContext null
        if (!mayWriteVideoHistory()) return@withContext null
        try {
            val cpn = generateCpn()
            // signatureTimestamp is required: without one, WEB /player answers
            // every video "Video unavailable" with no playbackTracking
            // (verified October 2026; see PlayerSignatureTimestamp).
            fun postPlayer(signatureTimestamp: Int): String? =
                // postWatchApi has already logged the HTTP failure or the changed login.
                webApi.postWatchApi("player", JSONObject()
                    .put("context", webApi.webContext()).put("videoId", videoId).put("cpn", cpn)
                    .put("playbackContext", JSONObject().put("contentPlaybackContext",
                        JSONObject().put("signatureTimestamp", signatureTimestamp))), session)
                    ?: run {
                        KLog.w(YOUTUBE_TAG, "Video history: no /player response for $videoId")
                        null
                    }
            val sentTimestamp = playerSignatureTimestamp.current()
            var raw = postPlayer(sentTimestamp) ?: return@withContext null
            if (historyPlayerSignedOut(raw, session = null, "Video")) return@withContext null
            var json = JSONObject(raw)
            if (json.optJSONObject("playbackTracking") == null) {
                // A signatureTimestamp YouTube no longer accepts looks exactly
                // like this; retry once if a fresh read gives another value.
                val retry = playerSignatureTimestamp.refreshAfterRejection(sentTimestamp)
                if (retry != null) {
                    KLog.w(YOUTUBE_TAG, "Video history: no playbackTracking with sts $sentTimestamp, retrying with $retry")
                    raw = postPlayer(retry) ?: return@withContext null
                    if (historyPlayerSignedOut(raw, session = null, "Video")) return@withContext null
                    json = JSONObject(raw)
                }
            }
            val tracking = json.optJSONObject("playbackTracking")
            val playback = tracking?.optJSONObject("videostatsPlaybackUrl")?.optString("baseUrl")
            val watchtime = tracking?.optJSONObject("videostatsWatchtimeUrl")?.optString("baseUrl")
            if (playback.isNullOrBlank() || watchtime.isNullOrBlank()) {
                val status = json.optJSONObject("playabilityStatus")
                KLog.w(
                    YOUTUBE_TAG,
                    "Video history: no tracking URLs for $videoId " +
                        "(playback=${!playback.isNullOrBlank()} watchtime=${!watchtime.isNullOrBlank()} " +
                        "status=${status?.optString("status")} reason=${status?.optString("reason")})"
                )
                return@withContext null
            }
            val history = VideoHistorySession(videoId, session, cpn, playback, watchtime)
            val first = sendVideoHistoryPing(history, playback, positionMs, positionMs, false)
            if (first != HistoryPingResult.SENT) {
                KLog.w(YOUTUBE_TAG, "Video history: first ping for $videoId ended $first")
                return@withContext null
            }
            history
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            KLog.w(YOUTUBE_TAG, "Could not start video history reporting", e)
            null
        }
    }

    /** WEB watchtime and FEhistory readback verified September 2026 (60s of a 634s VOD -> 10%). */
    internal suspend fun reportVideoWatchProgress(
        session: VideoHistorySession,
        startMs: Long,
        positionMs: Long,
        final: Boolean,
    ): HistoryPingResult = withContext(Dispatchers.IO) {
        try {
            sendVideoHistoryPing(session, session.watchtimeUrl, startMs, positionMs, final)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            KLog.w(YOUTUBE_TAG, "Could not report video watch progress", e)
            HistoryPingResult.FAILED
        }
    }

    private fun sendVideoHistoryPing(
        session: VideoHistorySession,
        baseUrl: String,
        startMs: Long,
        positionMs: Long,
        final: Boolean,
    ): HistoryPingResult {
        if (!mayWriteVideoHistory()) {
            KLog.d(YOUTUBE_TAG, "Video history: not sent for ${session.videoId}, history off or incognito")
            return HistoryPingResult.FAILED
        }
        // Deliberately not a comparison of cookie strings. Google rotates the
        // session cookies mid-video, and a string compare read every rotation
        // as a different login and silently stopped reporting for the rest of
        // the video on a perfectly valid account. This asks the question that
        // was meant - same profile, still active, still the same login - and
        // hands back the refreshed cookies to sign this ping with.
        val live = sessionManager.currentSession(session.login) ?: run {
            KLog.w(YOUTUBE_TAG, "Video history: login changed, reporting stops for ${session.videoId}")
            return HistoryPingResult.SESSION_ENDED
        }
        val url = baseUrl.toHttpUrlOrNull() ?: return HistoryPingResult.FAILED
        // Credentials only go to the YouTube tracking hosts returned by /player.
        if (url.scheme != "https" || url.host !in setOf("s.youtube.com", "www.youtube.com")) {
            KLog.w(YOUTUBE_TAG, "Video history: refused a tracking URL on ${url.host}")
            return HistoryPingResult.FAILED
        }
        val position = (positionMs.coerceAtLeast(0L) / 1000.0).toString()
        val trackingUrl = url.newBuilder()
            .setQueryParameter("cpn", session.cpn)
            .setQueryParameter("ver", "2").setQueryParameter("c", "WEB")
            .setQueryParameter("cver", WEB_VERSION)
            .setQueryParameter("cmt", position)
            .setQueryParameter("st", (startMs.coerceIn(0L, positionMs.coerceAtLeast(0L)) / 1000.0).toString())
            .setQueryParameter("et", position)
            .setQueryParameter("state", if (final) "paused" else "playing")
            .setQueryParameter("final", if (final) "1" else "0")
            .build()
        val request = Request.Builder().url(trackingUrl)
            .header("User-Agent", BROWSER_USER_AGENT)
            .header("Origin", "https://www.youtube.com")
            .header("Referer", "https://www.youtube.com/watch?v=${session.videoId}")
            .authenticate(live, "https://www.youtube.com")
            .build()
        // "playback" is the one that files the video in history; "watchtime"
        // pings carry how far it was watched.
        val kind = url.pathSegments.lastOrNull() ?: url.encodedPath
        return http.okHttpClient.newCall(request).execute().use { response ->
            val detail = "$kind ping for ${session.videoId} at ${position}s " +
                "(from ${trackingUrl.queryParameter("st")}s, final=$final): HTTP ${response.code}"
            if (response.isSuccessful) {
                KLog.d(YOUTUBE_TAG, "Video history: $detail")
            } else {
                KLog.w(YOUTUBE_TAG, "Video history ping failed: $detail")
            }
            if (response.isSuccessful) HistoryPingResult.SENT else HistoryPingResult.FAILED
        }
    }

    /**
     * True, with a log line saying so, when YouTube answered a history /player
     * call as signed out.
     *
     * A session YouTube no longer accepts still gets `status: OK`, full
     * tracking URLs and a 204 on every ping - and the play is recorded nowhere.
     * `logged_in` in the responseContext is the only thing that tells the two
     * apart, so the pings are skipped rather than reported as a sync that did
     * nothing. Probed September 2026 against FEhistory and FEmusic_history.
     *
     * [session] is noted for the expired badge where the caller's request did
     * not already do it (postWatchApi notes its own responses).
     */
    private fun historyPlayerSignedOut(raw: String, session: YouTubeSession?, surface: String): Boolean {
        if (session != null) http.noteSessionState(raw, session)
        if (LOGGED_IN_TRACKING_PARAM.find(raw)?.groupValues?.get(1) != "0") return false
        KLog.w(YOUTUBE_TAG, "$surface history: YouTube treated the session as signed out, nothing recorded")
        return true
    }

    /** The switches, as opposed to the session. Both have to hold at ping time. */
    private fun mayWriteVideoHistory(): Boolean =
        !IncognitoMode.isEnabled(context) && videoHistoryPreferences.isSaveVideoHistoryEnabled()

    private fun generateCpn(): String {
        val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_"
        return (1..16).map { chars.random() }.joinToString("")
    }

    private companion object {
        // Where a music history ping may carry the account's cookies. WEB_REMIX
        // /player returns its tracking URLs on s.youtube.com, with no cpn, c or
        // ver of their own [verified September 2026, signed-in probe]; the
        // video path's www.youtube.com is allowed as well.
        private val MUSIC_HISTORY_TRACKING_HOSTS = setOf("s.youtube.com", "www.youtube.com")
    }
}
