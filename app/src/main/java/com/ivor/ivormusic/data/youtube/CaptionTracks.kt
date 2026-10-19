package com.ivor.ivormusic.data.youtube

import com.ivor.ivormusic.data.CaptionTrack
import com.ivor.ivormusic.data.VttCue
import com.ivor.ivormusic.data.WebVttParser
import com.ivor.ivormusic.data.stream.PlayerClient
import com.ivor.ivormusic.data.stream.PlayerClients
import com.ivor.ivormusic.data.stream.PlayerSession
import com.ivor.ivormusic.data.stream.okRoot
import com.ivor.ivormusic.util.KLog
import java.util.Collections
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

/**
 * A video's caption tracks and their text.
 *
 * The tracklist rides on every `/player` response, so the one fetched to
 * start playback is kept and a CC tap normally costs no request. The cache is
 * process-wide for the reason visitorData is: the player and the repository
 * that resolved the stream can be different instances.
 */
internal class CaptionTracks(
    private val playerSession: PlayerSession,
    private val http: YouTubeHttp,
) {
    /**
     * Caption/subtitle tracks for a video.
     *
     * Resolved with the ANDROID_VR client (IOS as fallback) — the same chain
     * used for streams, and deliberately *not* WEB: a WEB /player call without
     * account cookies comes back UNPLAYABLE ("Video unavailable") with no
     * captions block at all, so every signed-out user saw an empty CC menu.
     * The native clients answer with the full tracklist either way.
     *
     * Normally free: the tracklist is cached from the /player response
     * fetched to start playback, so this only hits the network when that
     * cache missed or went stale.
     */
    suspend fun getCaptionTracks(videoId: String): List<CaptionTrack> = withContext(Dispatchers.IO) {
        cachedCaptionTracks(videoId)?.let { return@withContext it }
        try {
            suspend fun via(client: PlayerClient): List<CaptionTrack> =
                playerSession.single(videoId, client).okRoot()?.let(::parseCaptionTracks).orEmpty()
            val tracks = via(PlayerClients.ANDROID_VR).ifEmpty { via(PlayerClients.IOS) }
            cacheCaptionTracks(videoId, tracks)
            tracks
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            KLog.e(YOUTUBE_TAG, "getCaptionTracks failed for $videoId", e)
            emptyList()
        }
    }

    private fun cachedCaptionTracks(videoId: String): List<CaptionTrack>? {
        val entry = captionCache[videoId] ?: return null
        if (System.currentTimeMillis() - entry.fetchedAt > CAPTION_CACHE_TTL_MS) {
            captionCache.remove(videoId)
            return null
        }
        return entry.tracks
    }

    fun cacheCaptionTracks(videoId: String, tracks: List<CaptionTrack>) {
        if (tracks.isEmpty()) return
        captionCache[videoId] = CachedCaptions(tracks, System.currentTimeMillis())
    }

    /**
     * Download and parse one caption track into cues the player overlay can
     * render itself.
     *
     * Captions deliberately do not travel through ExoPlayer as a sideloaded
     * text track: that made them part of the media source, so turning captions
     * on or off rebuilt the source and discarded the entire video buffer. The
     * timedtext endpoint lives on www.youtube.com rather than googlevideo, so a
     * plain browser User-Agent is enough and no ranged chunking is needed - the
     * payload is a few tens of KB.
     *
     * Returns an empty list on any failure; captions are best-effort and must
     * never take playback down with them.
     */
    suspend fun getCaptionCues(track: CaptionTrack): List<VttCue> =
        getCaptionVtt(track)?.let { WebVttParser.parse(it) }.orEmpty()

    /**
     * One caption track as the WebVTT document timedtext serves, unparsed, or
     * null on any failure. The player parses it straight away; a video download
     * keeps it as it is, to be parsed when the file is watched offline.
     */
    suspend fun getCaptionVtt(track: CaptionTrack): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(track.vttUrl)
                .addHeader("User-Agent", BROWSER_USER_AGENT)
                .build()
            http.okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    KLog.w(
                        YOUTUBE_TAG,
                        "Caption fetch failed for ${track.languageCode}: HTTP ${response.code}"
                    )
                    return@withContext null
                }
                response.body?.string()?.takeIf { it.isNotBlank() }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            KLog.e(YOUTUBE_TAG, "Caption fetch failed for ${track.languageCode}", e)
            null
        }
    }

    private companion object {
        private class CachedCaptions(val tracks: List<CaptionTrack>, val fetchedAt: Long)

        // Caption tracklists harvested from the /player response already made to
        // start playback, so tapping CC costs no extra request. Companion-level
        // for the same reason visitorData is: the player VM and the repository
        // that resolved the stream can be different instances. Timedtext URLs
        // are signed with a ~6h expiry, so entries are dropped well before that.
        private const val CAPTION_CACHE_TTL_MS = 30 * 60 * 1000L // 30 minutes

        private const val CAPTION_CACHE_MAX_ENTRIES = 16

        private val captionCache = Collections.synchronizedMap(
            object : LinkedHashMap<String, CachedCaptions>(CAPTION_CACHE_MAX_ENTRIES, 0.75f, true) {
                override fun removeEldestEntry(
                    eldest: MutableMap.MutableEntry<String, CachedCaptions>,
                ): Boolean = size > CAPTION_CACHE_MAX_ENTRIES
            }
        )
    }
}

/**
 * Parse captions.playerCaptionsTracklistRenderer.captionTracks out of a
 * /player response. Each entry carries a signed timedtext baseUrl, a
 * languageCode, a display name (runs on the native clients, simpleText on
 * WEB) and, for auto-captions, kind == "asr" / a vssId prefixed "a.".
 * Manually authored tracks are listed before auto-generated ones.
 * Verified against the live /player API July 2026.
 */
internal fun parseCaptionTracks(root: JSONObject): List<CaptionTrack> {
    return try {
        val tracks = root.optJSONObject("captions")
            ?.optJSONObject("playerCaptionsTracklistRenderer")
            ?.optJSONArray("captionTracks")
            ?: return emptyList()

        (0 until tracks.length()).mapNotNull { i ->
            val t = tracks.optJSONObject(i) ?: return@mapNotNull null
            val baseUrl = t.optString("baseUrl").takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val languageCode = t.optString("languageCode").takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val name = getRunText(t.optJSONObject("name"))?.takeIf { it.isNotBlank() }
                ?: languageCode
            CaptionTrack(
                languageCode = languageCode,
                name = name,
                baseUrl = baseUrl,
                // vssId is the more reliable marker: the native clients
                // sometimes omit "kind" while still prefixing vssId "a.".
                isAutoGenerated = t.optString("kind") == "asr" ||
                    t.optString("vssId").startsWith("a."),
            )
        }
            .distinctBy { it.languageCode to it.isAutoGenerated }
            .sortedBy { it.isAutoGenerated }
    } catch (e: Exception) {
        KLog.e(YOUTUBE_TAG, "parseCaptionTracks failed", e)
        emptyList()
    }
}
