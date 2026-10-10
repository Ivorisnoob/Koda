package com.ivor.ivormusic.data.youtube

import com.ivor.ivormusic.data.BellLevel
import com.ivor.ivormusic.data.ChannelBell
import com.ivor.ivormusic.data.ChannelBellChange
import com.ivor.ivormusic.data.ChannelBellParser
import com.ivor.ivormusic.data.LikeStatus
import com.ivor.ivormusic.data.SessionManager
import com.ivor.ivormusic.data.VideoEngagement
import com.ivor.ivormusic.data.VideoItem
import com.ivor.ivormusic.data.WatchNextData
import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Everything around a playing video that one `/next` carries, and the writes
 * made from that page: like, subscribe and the notification bell.
 *
 * One `/next` feeds title, description, likes, subscription state, chapters,
 * related videos and the comments and live chat tokens, so a feature reads
 * what is already there rather than asking again.
 */
internal class WatchPage(
    private val webApi: WebApi,
    private val sessionManager: SessionManager,
) {
    /**
     * Everything the video player needs from one watch-next call: engagement,
     * enriched metadata and related videos, all parsed from a single /next
     * response. Replaces the previous pair of an engagement call plus a full
     * NewPipe StreamExtractor fetch, halving the network work per video open
     * and freeing bandwidth for the player's initial buffer.
     */
    suspend fun getWatchNextData(
        videoId: String,
        baseVideo: VideoItem? = null
    ): WatchNextData = withContext(Dispatchers.IO) {
        try {
            val root = fetchWatchNextRoot(videoId)
                ?: return@withContext WatchNextData(null, null, emptyList())
            WatchNextData(
                engagement = try {
                    parseEngagementFromWatchNext(videoId, root)
                } catch (e: Exception) {
                    KLog.w("YouTubeRepo", "engagement parse failed for $videoId", e)
                    null
                },
                updatedVideoItem = parseVideoMetadataFromWatchNext(videoId, root, baseVideo),
                relatedVideos = parseRelatedFromWatchNext(root),
                chapters = try {
                    parseChaptersFromWatchNext(root)
                } catch (e: Exception) {
                    KLog.w("YouTubeRepo", "chapters parse failed for $videoId", e)
                    emptyList()
                },
                liveChatContinuation = parseLiveChatContinuation(root)
            )
        } catch (e: Exception) {
            KLog.e("YouTubeRepo", "getWatchNextData failed", e)
            WatchNextData(null, null, emptyList())
        }
    }

    /**
     * Fetch like count/status, subscription state and the comments entry token
     * for a video. likeStatus/isSubscribed are only meaningful when logged in.
     */
    suspend fun getVideoEngagement(videoId: String): VideoEngagement? = withContext(Dispatchers.IO) {
        try {
            val root = fetchWatchNextRoot(videoId) ?: return@withContext null
            parseEngagementFromWatchNext(videoId, root)
        } catch (e: Exception) {
            KLog.e("YouTubeRepo", "getVideoEngagement failed", e)
            null
        }
    }

    /**
     * Resolve the creator of a feed video without resolving streams or touching
     * either player. Used only when a feed lockup supplied an avatar/name but
     * omitted its channel browse endpoint.
     */
    suspend fun getVideoChannelId(videoId: String): String? = withContext(Dispatchers.IO) {
        if (videoId.length != 11) return@withContext null
        try {
            val root = fetchWatchNextRoot(videoId) ?: return@withContext null
            val engagement = runCatching { parseEngagementFromWatchNext(videoId, root) }.getOrNull()
            engagement?.channelId
                ?: parseVideoMetadataFromWatchNext(videoId, root, null)?.channelId
                // A collab upload names no owner at all - no title, no
                // thumbnail, no browse endpoint - so both of the above are null
                // and this used to answer "no such channel", which is what a
                // feed card whose lockup carried no creator command (a channel
                // tab's, for one) surfaced as a channel page that failed to
                // load. The collaborators are listed uploader first, so the
                // first of them is the channel the byline leads with.
                ?: engagement?.collaborators?.firstOrNull()?.channelId
        } catch (e: Exception) {
            KLog.w("YouTubeRepo", "Channel lookup failed for $videoId", e)
            null
        }
    }

    fun fetchWatchNextRoot(videoId: String): org.json.JSONObject? {
        val body = org.json.JSONObject()
            .put("context", webApi.webContext())
            .put("videoId", videoId)
        val raw = webApi.postWatchApi("next", body) ?: return null
        return org.json.JSONObject(raw)
    }

    /**
     * Rate a video: LIKE, DISLIKE, or INDIFFERENT (removes existing rating).
     * Requires login. Returns true on success.
     */
    suspend fun rateVideo(videoId: String, status: LikeStatus): Boolean = withContext(Dispatchers.IO) {
        if (!sessionManager.isLoggedIn()) return@withContext false
        val endpoint = when (status) {
            LikeStatus.LIKE -> "like/like"
            LikeStatus.DISLIKE -> "like/dislike"
            LikeStatus.INDIFFERENT -> "like/removelike"
        }
        val body = org.json.JSONObject()
            .put("context", webApi.webContext())
            .put("target", org.json.JSONObject().put("videoId", videoId))
        webApi.postWatchApi(endpoint, body) != null
    }

    /**
     * Subscribe to / unsubscribe from a channel. Requires login and a
     * canonical UC... channelId (from getVideoEngagement).
     */
    suspend fun setSubscribed(channelId: String, subscribe: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (!sessionManager.isLoggedIn()) return@withContext false
        val endpoint = if (subscribe) "subscription/subscribe" else "subscription/unsubscribe"
        val body = org.json.JSONObject()
            .put("context", webApi.webContext())
            .put("channelIds", org.json.JSONArray().put(channelId))
        webApi.postWatchApi(endpoint, body) != null
    }

    /**
     * Move [bell]'s channel to [level] on the account, with the params YouTube
     * served for that level.
     *
     * [verified September 2026, signed in] `notification/modify_channel_preference`
     * answers with the channel's fresh bell (`newNotificationButton`, the toggle
     * shape) and a toast (`notificationActionRenderer.responseText`, "You'll get
     * personalized notifications"). Success is that bell standing at [level], not
     * the HTTP code: account writes on this API answer 200 to requests they did
     * not act on. Null means the level did not change; the caller keeps what it
     * had.
     */
    suspend fun setChannelBell(bell: ChannelBell, level: BellLevel): ChannelBellChange? =
        withContext(Dispatchers.IO) {
            if (!sessionManager.isLoggedIn()) return@withContext null
            val params = bell.choices[level] ?: return@withContext null
            try {
                val raw = webApi.postWatchApi(
                    "notification/modify_channel_preference",
                    org.json.JSONObject().put("context", webApi.webContext()).put("params", params)
                ) ?: return@withContext null
                val root = org.json.JSONObject(raw)
                val updated = ChannelBellParser.fromToggle(
                    root.optJSONObject("newNotificationButton"), bell.channelId
                )
                if (updated?.level != level) {
                    KLog.w("YouTubeRepo", "Bell for ${bell.channelId} did not move to $level (reply: ${updated?.level})")
                    return@withContext null
                }
                val toasts = mutableListOf<org.json.JSONObject>()
                findObjectsByKey(root, "notificationActionRenderer", toasts)
                ChannelBellChange(
                    // The level is the reply's; the params stay the ones the
                    // calling surface was served, which were minted for it.
                    bell = bell.withLevel(level),
                    message = getRunText(toasts.firstOrNull()?.optJSONObject("responseText"))
                        ?.takeIf { it.isNotBlank() }
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                KLog.w("YouTubeRepo", "setChannelBell failed", e)
                null
            }
        }
}
