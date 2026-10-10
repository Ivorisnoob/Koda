package com.ivor.ivormusic.data.youtube

import android.content.Context
import com.ivor.ivormusic.data.SessionManager
import com.ivor.ivormusic.data.ShortsFeedPage
import com.ivor.ivormusic.data.ShortsItem
import com.ivor.ivormusic.data.VideoFeedPage
import com.ivor.ivormusic.data.VideoHistoryRepository
import com.ivor.ivormusic.data.VideoItem
import com.ivor.ivormusic.data.YouTubeSession
import com.ivor.ivormusic.data.items
import com.ivor.ivormusic.data.parseShortsSeed
import com.ivor.ivormusic.data.parseShortsSequence
import com.ivor.ivormusic.data.videoListContinuationToken
import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * The lists of videos YouTube assembles for an account: Home, Subscriptions,
 * watch history and Shorts, and what Home falls back to signed out.
 *
 * Every feed is read a page at a time and hands its continuation back to the
 * caller. Nothing here walks an account to its end on its own.
 */
internal class VideoFeeds(
    private val context: Context,
    private val http: YouTubeHttp,
    private val webApi: WebApi,
    private val videoSearch: VideoSearch,
    private val watchPage: WatchPage,
    private val sessionManager: SessionManager,
) {
    private val videoHistoryRepository by lazy { VideoHistoryRepository(context) }

    /**
     * Get recommended videos for the video mode home screen.
     * 1. Logged in: personalized YouTube home feed (with a browse continuation
     *    token for endless scrolling — see [getVideoFeedContinuation]).
     * 2. Otherwise: taste-based mix built from the local watch history (pages
     *    by seed offset instead of a token — see [getTasteBasedVideos]).
     * 3. Cold start: generic popular search.
     * (YouTube removed the public Trending page in mid-2025 — the InnerTube
     * FEtrending browseId now returns HTTP 400, so no trending fallback.)
     */
    suspend fun getTrendingVideos(): VideoFeedPage = withContext(Dispatchers.IO) {
        val isLoggedIn = sessionManager.isLoggedIn()
        KLog.d("YouTubeRepo", "getTrendingVideos - isLoggedIn: $isLoggedIn")

        if (isLoggedIn) {
            try {
                val page = getPersonalizedVideoRecommendations()
                if (page.videos.isNotEmpty()) {
                    KLog.d("YouTubeRepo", "Got ${page.videos.size} personalized videos (continuation=${page.continuation != null})")
                    return@withContext page
                }
                KLog.w("YouTubeRepo", "Personalized recommendations empty, using taste-based feed")
            } catch (e: Exception) {
                KLog.e("YouTubeRepo", "Error fetching personalized videos", e)
            }
        }

        try {
            val tasteFeed = getTasteBasedVideos()
            if (tasteFeed.isNotEmpty()) {
                KLog.d("YouTubeRepo", "Got ${tasteFeed.size} taste-based videos")
                return@withContext VideoFeedPage(tasteFeed)
            }
        } catch (e: Exception) {
            KLog.e("YouTubeRepo", "Error building taste-based feed", e)
        }

        // Cold start: nothing watched yet and not logged in
        try {
            VideoFeedPage(videoSearch.searchVideos("trending videos ${java.time.Year.now().value}"))
        } catch (e: Exception) {
            KLog.e("YouTubeRepo", "Cold-start search failed", e)
            VideoFeedPage(emptyList())
        }
    }

    /**
     * Get personalized video recommendations from YouTube (requires login).
     * Uses the YouTube homepage API to get personalized suggestions. The
     * returned page carries the rich-grid continuation token so the home feed
     * can keep loading (feed shape verified July 2026).
     */
    private suspend fun getPersonalizedVideoRecommendations(): VideoFeedPage = withContext(Dispatchers.IO) {
        val empty = VideoFeedPage(emptyList())
        val session = sessionManager.captureSession() ?: return@withContext empty
        val origin = "https://www.youtube.com"

        // Use YouTube browse endpoint for "What to Watch" (home page recommendations)
        val url = "https://www.youtube.com/youtubei/v1/browse?key=AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8&prettyPrint=false"
        
        val jsonBody = """
            {
                "context": {
                    "client": {
                        "clientName": "WEB",
                        "clientVersion": "$WEB_VERSION",
                        "hl": "en",
                        "gl": "${http.contentRegion()}",
                        "originalUrl": "https://www.youtube.com/",
                        "platform": "DESKTOP"
                    },
                    "user": {
                        "lockedSafetyMode": false
                    }
                },
                "browseId": "FEwhat_to_watch"
            }
        """.trimIndent()

        val request = okhttp3.Request.Builder()
            .url(url)
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .authenticate(session, origin)
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .addHeader("Origin", origin)
            .addHeader("Referer", "$origin/")
            .addHeader("X-Origin", origin)
            .addHeader("Accept", "*/*")
            .addHeader("Accept-Language", "en-US,en;q=0.9")
            .build()

        try {
            // Never place Authorization material or response bodies in KLog:
            // users can deliberately attach its release ring buffer to a bug
            // report, and these values may carry account/feed information.
            KLog.d("YouTubeRepo", "Making personalized video request")
            val response = http.okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: return@withContext empty
            response.close()

            KLog.d("YouTubeRepo", "Personalized response received")
            val root = org.json.JSONObject(responseBody)
            val videos = parseVideosFromYouTubeJson(responseBody)
            KLog.d("YouTubeRepo", "Parsed ${videos.size} personalized videos")
            VideoFeedPage(videos, extractRichGridContinuation(root))
        } catch (e: Exception) {
            KLog.e("YouTubeRepo", "Error in getPersonalizedVideoRecommendations", e)
            empty
        }
    }

    /**
     * Build a feed from the related videos of recently watched ones.
     * Interleaves per-seed results for variety; drops watched videos and dupes.
     * [seedOffset] pages through the watch history 6 seeds at a time so the
     * home feed can load more logged out: offset 0 seeds from the 6 most
     * recent videos, offset 6 from the next 6, and so on. Returns empty once
     * the history runs out of seeds.
     */
    suspend fun getTasteBasedVideos(seedOffset: Int = 0): List<VideoItem> = kotlinx.coroutines.coroutineScope {
        val history = videoHistoryRepository.getHistory()
        if (history.isEmpty()) return@coroutineScope emptyList()

        val seeds = history.drop(seedOffset).take(6)
        if (seeds.isEmpty()) return@coroutineScope emptyList()
        val historyIds = history.mapTo(HashSet()) { it.videoId }
        val perSeed = seeds.map { seed ->
            async(Dispatchers.IO) { getRelatedVideosLight(seed.videoId) }
        }.map { it.await() }

        val mixed = mutableListOf<VideoItem>()
        val seen = HashSet<String>()
        val longest = perSeed.maxOfOrNull { it.size } ?: 0
        for (i in 0 until longest) {
            for (list in perSeed) {
                val video = list.getOrNull(i) ?: continue
                if (video.videoId in historyIds || !seen.add(video.videoId)) continue
                mixed.add(video)
            }
        }
        mixed
    }

    /**
     * Related videos for a seed video from the watch-next endpoint.
     * Far lighter than a full NewPipe StreamExtractor fetch (one JSON call,
     * no stream resolution). Related items are lockupViewModels since 2025.
     */
    private fun getRelatedVideosLight(videoId: String): List<VideoItem> {
        return try {
            val root = watchPage.fetchWatchNextRoot(videoId) ?: return emptyList()
            parseRelatedFromWatchNext(root)
        } catch (e: Exception) {
            KLog.w("YouTubeRepo", "getRelatedVideosLight failed for $videoId", e)
            emptyList()
        }
    }

    /**
     * Next page of the personalized home feed from a browse continuation
     * token. The response carries appendContinuationItemsAction with ~23 more
     * richItemRenderers (lockupViewModel contents) plus the next page's
     * continuationItemRenderer. Requires login (the token comes from a signed
     * FEwhat_to_watch response). Shape verified July 2026.
     */
    suspend fun getVideoFeedContinuation(continuation: String): VideoFeedPage = withContext(Dispatchers.IO) {
        try {
            val body = org.json.JSONObject()
                .put("context", webApi.webContext())
                .put("continuation", continuation)
            val raw = webApi.postWatchApi("browse", body) ?: return@withContext VideoFeedPage(emptyList())
            val root = org.json.JSONObject(raw)

            val videos = mutableListOf<VideoItem>()
            var nextToken: String? = null
            val actions = root.optJSONArray("onResponseReceivedActions") ?: org.json.JSONArray()
            for (i in 0 until actions.length()) {
                val items = actions.optJSONObject(i)
                    ?.optJSONObject("appendContinuationItemsAction")
                    ?.optJSONArray("continuationItems")
                    ?: continue
                for (j in 0 until items.length()) {
                    val item = items.optJSONObject(j) ?: continue
                    val content = item.optJSONObject("richItemRenderer")?.optJSONObject("content")
                    content?.optJSONObject("lockupViewModel")?.let {
                        parseLockupViewModel(it)?.let { v -> videos.add(v) }
                    }
                    content?.optJSONObject("videoRenderer")?.let {
                        parseVideoRenderer(it)?.let { v -> videos.add(v) }
                    }
                    item.optJSONObject("continuationItemRenderer")
                        ?.optJSONObject("continuationEndpoint")
                        ?.optJSONObject("continuationCommand")
                        ?.optString("token")
                        ?.takeIf { it.isNotBlank() }
                        ?.let { nextToken = it }
                }
            }
            KLog.d("YouTubeRepo", "Feed continuation: ${videos.size} videos, next=${nextToken != null}")
            VideoFeedPage(videos.distinctBy { it.videoId }, nextToken)
        } catch (e: Exception) {
            KLog.e("YouTubeRepo", "Feed continuation failed", e)
            VideoFeedPage(emptyList())
        }
    }

    /**
     * The user's subscriptions feed (FEsubscriptions): latest uploads from
     * all subscribed channels, newest first. Requires login.
     *
     * The first page is everything YouTube sent, not the first thirty: the
     * token it carries continues from the page's real end, so a truncated page
     * followed by its continuation would skip whatever was cut. Later pages come
     * from [getVideoFeedContinuation], whose `appendContinuationItemsAction`
     * shape this feed shares with Home. [verified October 2026, signed in: page
     * one about a hundred lockups, the continuation 95 more and a next token]
     */
    suspend fun getSubscriptionsFeedPage(): VideoFeedPage = withContext(Dispatchers.IO) {
        if (!sessionManager.isLoggedIn()) return@withContext VideoFeedPage(emptyList())
        try {
            val raw = webApi.postWatchApi(
                "browse",
                org.json.JSONObject().put("context", webApi.webContext()).put("browseId", "FEsubscriptions")
            ) ?: return@withContext VideoFeedPage(emptyList())
            val root = org.json.JSONObject(raw)
            VideoFeedPage(
                videos = parseVideosFromYouTubeJson(raw, limit = Int.MAX_VALUE, parsedRoot = root),
                continuation = extractRichGridContinuation(root)
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            KLog.e("YouTubeRepo", "getSubscriptionsFeedPage failed", e)
            VideoFeedPage(emptyList())
        }
    }

    /** One history page; Library must never walk an account's entire history. */
    internal suspend fun getWatchHistoryPage(
        continuation: String? = null,
        session: YouTubeSession? = sessionManager.captureSession()
    ): VideoFeedPage? = withContext(Dispatchers.IO) {
        if (session == null) return@withContext null
        try {
            val body = org.json.JSONObject().put("context", webApi.webContext())
            if (continuation == null) body.put("browseId", "FEhistory")
            else body.put("continuation", continuation)
            val raw = webApi.postWatchApi("browse", body, session)
                ?.takeIf { it.isNotBlank() } ?: return@withContext null
            val root = org.json.JSONObject(raw)
            if (root.has("error")) return@withContext null
            val videos = parseVideosFromYouTubeJson(raw, limit = Int.MAX_VALUE)
            KLog.d("YouTubeRepo", "History ${if (continuation == null) "first" else "next"} page: ${videos.size} videos")
            VideoFeedPage(videos, videoListContinuationToken(root))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            KLog.e("YouTubeRepo", "Error fetching watch history page", e)
            null
        }
    }

    /**
     * Signed-in Shorts use YouTube's seedless Shorts navigation, not a search
     * query. FEwhat_to_watch can contain no Shorts even with logged_in=1.
     * Seed and continuation shapes verified live September 2026.
     * Signed-out profiles retain the local-history search fallback.
     */
    suspend fun getShortsFeed(): List<ShortsItem> = withContext(Dispatchers.IO) {
        val session = sessionManager.captureSession()
        if (session != null) {
            val body = org.json.JSONObject()
                .put("context", webApi.webContext())
                .put("params", "CA8%3D")
                .put("inputType", "REEL_WATCH_INPUT_TYPE_SEEDLESS")
                .put("disablePlayerResponse", true)
            val raw = webApi.postWatchApi("reel/reel_item_watch", body, session)
                ?: throw java.io.IOException("Shorts recommendations request failed")
            val root = org.json.JSONObject(raw)
            if (LOGGED_IN_TRACKING_PARAM.find(raw)?.groupValues?.get(1) == "0") {
                throw java.io.IOException("YouTube rejected the Shorts session")
            }
            val seed = parseShortsSeed(root)
            if (sessionManager.currentSession(session) == null) {
                throw kotlinx.coroutines.CancellationException("Shorts account changed")
            }
            val page = seed.continuation?.let { requestShortsSequence(it, session) }
            val continuation = if (page != null) page.continuation else seed.continuation
            // Every shelf tap continues after the loaded shelf, without replaying
            // a search candidate list or dropping the account's sequence token.
            return@withContext (seed.items + page?.items.orEmpty())
                .distinctBy { it.videoId }
                .map { it.copy(sequenceParams = continuation) }
        }
        val seedChannel = videoHistoryRepository.getHistory()
            .firstOrNull { it.channelName.isNotBlank() && it.channelName != "Unknown Channel" }
            ?.channelName
        val body = org.json.JSONObject()
            .put("context", webApi.webContext())
            .put("query", seedChannel?.let { "$it shorts" } ?: "trending shorts")
        val raw = webApi.postWatchApi("search", body)
            ?: throw java.io.IOException("Shorts search request failed")
        parseShortsLockups(org.json.JSONObject(raw))
    }

    /**
     * One account-aware reel_watch_sequence page. Errors must propagate so the
     * pager keeps its token for retry instead of treating a failure as exhaustion.
     * Seed params and continuation tokens use the same field (verified September 2026).
     */
    suspend fun getShortsSequence(sequenceParams: String): ShortsFeedPage = withContext(Dispatchers.IO) {
        requestShortsSequence(sequenceParams, sessionManager.captureSession())
    }

    private fun requestShortsSequence(sequenceParams: String, session: YouTubeSession?): ShortsFeedPage {
        val body = org.json.JSONObject()
            .put("context", webApi.webContext())
            .put("sequenceParams", sequenceParams)
        val raw = webApi.postWatchApi("reel/reel_watch_sequence", body, session)
            ?: throw java.io.IOException("Shorts sequence request failed")
        if (session != null &&
            LOGGED_IN_TRACKING_PARAM.find(raw)?.groupValues?.get(1) == "0") {
            throw java.io.IOException("YouTube rejected the Shorts session")
        }
        return parseShortsSequence(org.json.JSONObject(raw))
    }

    /**
     * Tell the signed-in account about a dismissal, so the choice also cleans
     * up recommendations on youtube.com and in the official apps.
     *
     * This is the *bonus* half of "don't recommend this", never the mechanism:
     * the local hide in [NotInterestedRepository] has already happened by the
     * time this runs, and a failure here must not undo it. YouTube's own
     * feedback is advisory and takes days to visibly change a feed, whereas
     * the local filter takes effect on the next frame - so this returning
     * false is not something the user should ever be told about.
     *
     * Signed out there is nothing to call: no response carries a token, so
     * [token] is null and this is skipped. Search results carry no tokens even
     * when signed in, which is consistent with search never being filtered.
     *
     * The same endpoint reverses a dismissal - pass the undo token. Success is
     * `feedbackResponses[0].isProcessed`, not the HTTP code: like
     * `subscription/subscribe`, this endpoint answers 200 to requests it did
     * not actually act on. Verified against the live endpoint, August 2026.
     */
    suspend fun sendDismissalFeedback(token: String?): Boolean = withContext(Dispatchers.IO) {
        if (token.isNullOrBlank()) return@withContext false
        if (!sessionManager.isLoggedIn()) return@withContext false
        try {
            val body = org.json.JSONObject()
                .put("context", webApi.webContext())
                .put("feedbackTokens", org.json.JSONArray().put(token))
                .put("isFeedbackTokenUnencrypted", false)
                .put("shouldMerge", false)
            val raw = webApi.postWatchApi("feedback", body) ?: return@withContext false
            val processed = org.json.JSONObject(raw)
                .optJSONArray("feedbackResponses")
                ?.optJSONObject(0)
                ?.optBoolean("isProcessed", false) ?: false
            if (!processed) {
                KLog.w("YouTubeRepo", "feedback token not processed")
            }
            processed
        } catch (e: Exception) {
            KLog.e("YouTubeRepo", "feedback failed", e)
            false
        }
    }
}
