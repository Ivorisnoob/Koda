package com.ivor.ivormusic.data.youtube

import com.ivor.ivormusic.data.ChannelAbout
import com.ivor.ivormusic.data.ChannelBellParser
import com.ivor.ivormusic.data.ChannelHeader
import com.ivor.ivormusic.data.ChannelMixPool
import com.ivor.ivormusic.data.ChannelPage
import com.ivor.ivormusic.data.ChannelPost
import com.ivor.ivormusic.data.ChannelSortChips
import com.ivor.ivormusic.data.ChannelTabKind
import com.ivor.ivormusic.data.ChannelTabPage
import com.ivor.ivormusic.data.SessionManager
import com.ivor.ivormusic.data.SubscribedChannel
import com.ivor.ivormusic.data.VideoItem
import com.ivor.ivormusic.data.YouTubeRateLimit
import com.ivor.ivormusic.data.YouTubeRateLimitedException
import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Channel identity and display metadata, from one channel browse.
 *
 * Everything comes out of `metadata.channelMetadataRenderer`, which has
 * outlived several redesigns of the visible header (`c4TabbedHeaderRenderer`
 * is gone entirely as of 2026; the header is a `pageHeaderViewModel` now).
 * The subscriber count only exists in the header, so it is read from there
 * and is the one field allowed to come back null on a shape change.
 * Verified August 2026.
 */
data class ChannelProfile(
    val channelId: String,
    val name: String,
    val avatarUrl: String?,
    val handle: String?,
    val subscriberCountText: String?
)

/**
 * The channel page, and the account's list of channels. Verified against live
 * responses, signed out, August 2026.
 *
 * **A channel page describes itself, and this class is written to let it.**
 * The first browse returns the tab list with each tab's own `params`, every
 * sort order with its own continuation token, and every next page as another
 * token. So there are two hardcoded browse parameters in here
 * (`CHANNEL_VIDEOS_TAB_PARAMS`, the fallback for a response whose tab list
 * failed to parse, and `CHANNEL_POSTS_TAB_PARAMS`, for feeds that sample a
 * channel whose page was never opened), and no fixed set of tabs.
 *
 * That matters beyond tidiness. Tab sets genuinely differ per channel: a
 * musician has "Releases" where a teacher has "Courses" and a big tech channel
 * has "Podcasts" and "Store". Hardcoding the six tabs YouTube shows one
 * channel would have meant drawing empty tabs on channels that lack them and
 * hiding real ones on channels that have more - and both failures are silent,
 * which is the worst kind.
 *
 * Everything but [getSubscribedChannels] works signed out, which is the whole
 * point: deciding whether a creator is worth following is exactly the thing a
 * signed-out user does most.
 */
internal class ChannelPages(
    private val webApi: WebApi,
    private val sessionManager: SessionManager,
) {
    /**
     * Identity, tab list, and the contents of whichever tab YouTube had already
     * selected - all from one browse.
     *
     * The selected tab's items ride along rather than being fetched again,
     * because they arrived in this same response. Asking for them a second time
     * would be a request for bytes already in hand.
     *
     * Returns null only when the channel could not be identified at all, which
     * for the caller means "this is not a channel" rather than "try again".
     */
    suspend fun getChannelPage(channelId: String): ChannelPage? = withContext(Dispatchers.IO) {
        try {
            val raw = webApi.postWatchApi(
                "browse",
                JSONObject().put("context", webApi.webContext()).put("browseId", channelId)
            ) ?: return@withContext null
            val root = JSONObject(raw)
            val header = parseChannelHeader(root, channelId) ?: return@withContext null
            val tabs = parseChannelTabs(root)
            val selected = parseSelectedTab(root)
            val selectedKind = selected?.first ?: ChannelTabKind.HOME
            val content = selected?.second?.let { parseChannelTabPage(it, header) }
                ?: ChannelTabPage()
            ChannelPage(
                header = header,
                tabs = tabs,
                selectedTab = selectedKind,
                selectedContent = content
            )
        } catch (e: Exception) {
            KLog.e(YOUTUBE_TAG, "getChannelPage failed for $channelId", e)
            null
        }
    }

    /** One tab's first page, by the `params` the page handed out for it. */
    suspend fun getChannelTab(
        channelId: String,
        params: String,
        header: ChannelHeader? = null
    ): ChannelTabPage = withContext(Dispatchers.IO) {
        try {
            val raw = webApi.postWatchApi(
                "browse",
                JSONObject()
                    .put("context", webApi.webContext())
                    .put("browseId", channelId)
                    .put("params", params)
            ) ?: return@withContext ChannelTabPage()
            val root = JSONObject(raw)
            val scope = parseSelectedTab(root)?.second ?: root
            parseChannelTabPage(scope, header)
        } catch (e: Exception) {
            KLog.e(YOUTUBE_TAG, "getChannelTab failed for $channelId", e)
            ChannelTabPage()
        }
    }

    /**
     * The next page of a tab, or the same tab re-sorted - the two are the same
     * call, because YouTube expresses both as a browse continuation. The
     * response differs only in whether it says append or reload, and since the
     * caller already knows which it asked for, that distinction stays with the
     * caller rather than being guessed here.
     */
    suspend fun getChannelContinuation(
        token: String,
        header: ChannelHeader? = null
    ): ChannelTabPage = withContext(Dispatchers.IO) {
        try {
            val raw = webApi.postWatchApi(
                "browse",
                JSONObject().put("context", webApi.webContext()).put("continuation", token)
            ) ?: return@withContext ChannelTabPage()
            parseChannelTabPage(JSONObject(raw), header)
        } catch (e: Exception) {
            KLog.e(YOUTUBE_TAG, "getChannelContinuation failed", e)
            ChannelTabPage()
        }
    }

    /**
     * Search a single channel's back catalogue.
     *
     * The one tab whose results come back as legacy `videoRenderer`s rather
     * than lockups (verified August 2026), which the generic page parser
     * already handles, so this is a browse with a query bolted on and nothing
     * more.
     */
    suspend fun searchWithinChannel(
        channelId: String,
        params: String,
        query: String,
        header: ChannelHeader? = null
    ): ChannelTabPage = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext ChannelTabPage()
        try {
            val raw = webApi.postWatchApi(
                "browse",
                JSONObject()
                    .put("context", webApi.webContext())
                    .put("browseId", channelId)
                    .put("params", params)
                    .put("query", query)
            ) ?: return@withContext ChannelTabPage()
            val root = JSONObject(raw)
            parseChannelTabPage(parseSelectedTab(root)?.second ?: root, header)
        } catch (e: Exception) {
            KLog.e(YOUTUBE_TAG, "searchWithinChannel failed for $channelId", e)
            ChannelTabPage()
        }
    }

    /**
     * The About panel, behind the token the header carried.
     *
     * YouTube does not put the full description, the links, the join date or
     * the lifetime view count in the channel response at all - they live in an
     * engagement panel fetched by continuation - so About costs one request and
     * only when someone opens it.
     */
    suspend fun getChannelAbout(token: String): ChannelAbout? = withContext(Dispatchers.IO) {
        try {
            val raw = webApi.postWatchApi(
                "browse",
                JSONObject().put("context", webApi.webContext()).put("continuation", token)
            ) ?: return@withContext null
            val root = JSONObject(raw)
            val about = mutableListOf<JSONObject>()
            findObjectsByKey(root, "aboutChannelViewModel", about)
            val view = about.firstOrNull() ?: return@withContext null

            val links = mutableListOf<JSONObject>()
            findObjectsByKey(view, "channelExternalLinkViewModel", links)

            ChannelAbout(
                description = view.optString("description").takeIf { it.isNotBlank() },
                links = links.mapNotNull { parseChannelExternalLink(it) },
                joinedDateText = view.optJSONObject("joinedDateText")
                    ?.optString("content")?.takeIf { it.isNotBlank() },
                viewCountText = view.optString("viewCountText").takeIf { it.isNotBlank() },
                subscriberCountText = view.optString("subscriberCountText")
                    .takeIf { it.isNotBlank() },
                videoCountText = view.optString("videoCountText").takeIf { it.isNotBlank() },
                country = view.optString("country").takeIf { it.isNotBlank() },
                canonicalUrl = view.optString("canonicalChannelUrl").takeIf { it.isNotBlank() }
            )
        } catch (e: Exception) {
            KLog.e(YOUTUBE_TAG, "getChannelAbout failed", e)
            null
        }
    }

    /**
     * A channel's newest community posts, for the posts the feeds scatter
     * between videos. One browse straight at the Posts tab, each post stamped
     * with the channel it was asked of. Empty for a channel with no Posts tab
     * and on any failure: a missing post is not worth an error anywhere.
     */
    suspend fun getChannelPosts(channelId: String): List<ChannelPost> = withContext(Dispatchers.IO) {
        try {
            val raw = webApi.postWatchApi(
                "browse",
                JSONObject()
                    .put("context", webApi.webContext())
                    .put("browseId", channelId)
                    .put("params", CHANNEL_POSTS_TAB_PARAMS)
            ) ?: return@withContext emptyList()
            val postRenderers = mutableListOf<JSONObject>()
            findObjectsByKey(JSONObject(raw), "backstagePostRenderer", postRenderers)
            postRenderers.mapNotNull { parseBackstagePost(it) }
                .distinctBy { it.postId }
                .map { it.copy(channelId = channelId) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            KLog.w(YOUTUBE_TAG, "getChannelPosts failed for $channelId", e)
            emptyList()
        }
    }

    /**
     * Latest uploads of a channel (Videos tab browse). Channel-page lockups
     * omit the channel row, so the caller's channel identity is stitched in.
     */
    suspend fun getChannelVideos(channel: SubscribedChannel): List<VideoItem> = withContext(Dispatchers.IO) {
        try {
            val raw = webApi.postWatchApi(
                "browse",
                JSONObject()
                    .put("context", webApi.webContext())
                    .put("browseId", channel.channelId)
                    .put("params", CHANNEL_VIDEOS_TAB_PARAMS)
            ) ?: return@withContext emptyList()
            val root = JSONObject(raw)
            val richItems = mutableListOf<JSONObject>()
            findObjectsByKey(root, "richItemRenderer", richItems)
            richItems.mapNotNull { item ->
                val content = item.optJSONObject("content") ?: return@mapNotNull null
                val parsed = parseLockupViewModel(content.optJSONObject("lockupViewModel"))
                    ?: parseVideoRenderer(content.optJSONObject("videoRenderer"))
                    ?: return@mapNotNull null
                withSubscribedChannelIdentity(parsed, channel)
            }.distinctBy { it.videoId }
        } catch (e: Exception) {
            KLog.e(YOUTUBE_TAG, "getChannelVideos failed", e)
            emptyList()
        }
    }

    /**
     * One followed channel's contribution to the shuffled Home feed: its latest
     * uploads and its all-time Popular order. Two requests - the Videos tab,
     * which also carries the sort chips, then the Popular continuation - and
     * nothing when the Popular order is not offered beyond the first.
     *
     * Only finished uploads are kept. A live stream or an upcoming premiere has
     * no duration on these cards, and a shuffled list of videos from any point
     * in a channel's history is not where either belongs.
     *
     * Throws [YouTubeRateLimitedException] rather than returning empty during a
     * hold, so the caller can stop the whole batch instead of recording every
     * channel as having nothing.
     */
    suspend fun getChannelMixPool(channel: SubscribedChannel): ChannelMixPool =
        withContext(Dispatchers.IO) {
            if (YouTubeRateLimit.isHeld()) {
                throw YouTubeRateLimitedException(YouTubeRateLimit.remainingMs())
            }
            val tab = getChannelTab(channel.channelId, CHANNEL_VIDEOS_TAB_PARAMS)
            val recent = tab.videos
                .map { withSubscribedChannelIdentity(it, channel) }
                .filter(::isFinishedUpload)
            val popularToken = ChannelSortChips.popularToken(tab.sortOptions)
            val catalogue = if (popularToken != null && !YouTubeRateLimit.isHeld()) {
                getChannelContinuation(popularToken).videos
                    .map { withSubscribedChannelIdentity(it, channel) }
                    .filter(::isFinishedUpload)
            } else emptyList()
            if (YouTubeRateLimit.isHeld()) {
                throw YouTubeRateLimitedException(YouTubeRateLimit.remainingMs())
            }
            ChannelMixPool(channelId = channel.channelId, recent = recent, catalogue = catalogue)
        }

    /**
     * A card for a finished upload: not live, and either carrying a duration or
     * an upload date. An upcoming premiere has neither, and the date half keeps
     * a card whose duration badge simply failed to parse.
     */
    private fun isFinishedUpload(video: VideoItem): Boolean =
        !video.isLive && (video.duration > 0 || !video.uploadedDate.isNullOrBlank())

    /**
     * A channel-page card with the followed channel's identity put back.
     * Channel-page lockups omit the channel row, and the generic parser then
     * reads the first metadata row - "N views • date" - as the channel name.
     */
    private fun withSubscribedChannelIdentity(parsed: VideoItem, channel: SubscribedChannel): VideoItem {
        val viewCount = if (parsed.viewCount.isBlank() &&
            (parsed.channelName.contains("view", ignoreCase = true) ||
                parsed.channelName.contains("watching", ignoreCase = true))
        ) parsed.channelName else parsed.viewCount
        return parsed.copy(
            channelName = channel.name,
            channelId = channel.channelId,
            channelIconUrl = channel.avatarUrl ?: parsed.channelIconUrl,
            viewCount = viewCount
        )
    }

    /**
     * All channels the user is subscribed to, from the FEchannels browse
     * feed (channelRenderer items), following continuations. Requires login.
     */
    suspend fun getSubscribedChannels(): List<SubscribedChannel> = withContext(Dispatchers.IO) {
        if (!sessionManager.isLoggedIn()) return@withContext emptyList()
        try {
            val channels = mutableListOf<SubscribedChannel>()
            var response = webApi.postWatchApi(
                "browse",
                JSONObject().put("context", webApi.webContext()).put("browseId", "FEchannels")
            )
            var pages = 0
            while (response != null && pages < 10) {
                val root = JSONObject(response)
                val renderers = mutableListOf<JSONObject>()
                findObjectsByKey(root, "channelRenderer", renderers)
                for (renderer in renderers) {
                    val channelId = renderer.optString("channelId").takeIf { it.isNotBlank() } ?: continue
                    val name = renderer.optJSONObject("title")?.optString("simpleText")
                        ?.takeIf { it.isNotBlank() } ?: continue
                    val thumbs = renderer.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                    var avatarUrl = thumbs?.optJSONObject((thumbs.length() - 1).coerceAtLeast(0))
                        ?.optString("url")?.takeIf { it.isNotBlank() }
                    if (avatarUrl?.startsWith("//") == true) avatarUrl = "https:$avatarUrl"
                    // InnerTube quirk: on FEchannels the subscriber count arrives in
                    // videoCountText; subscriberCountText carries the @handle.
                    // Verified again August 2026 - both fields are present on every
                    // renderer, so the handle is free here and account channels are
                    // searchable by it exactly like device-local ones.
                    val subscriberCount = getRunText(renderer.optJSONObject("videoCountText"))
                        ?.takeIf { it.isNotBlank() }
                    val handle = getRunText(renderer.optJSONObject("subscriberCountText"))
                        ?.takeIf { it.startsWith("@") }
                    // Every row carries its bell, so the list knows each level
                    // without a request per channel (ChannelBellParser).
                    val toggles = mutableListOf<JSONObject>()
                    findObjectsByKey(renderer, "subscriptionNotificationToggleButtonRenderer", toggles)
                    val bell = ChannelBellParser.fromToggle(toggles.firstOrNull(), channelId)
                    channels.add(
                        SubscribedChannel(channelId, name, avatarUrl, subscriberCount, handle, bell)
                    )
                }
                val token = if (renderers.isNotEmpty()) extractContinuationToken(response) else null
                response = token?.let {
                    webApi.postWatchApi(
                        "browse",
                        JSONObject().put("context", webApi.webContext()).put("continuation", it)
                    )
                }
                pages++
            }
            channels.distinctBy { it.channelId }
        } catch (e: Exception) {
            KLog.e(YOUTUBE_TAG, "getSubscribedChannels failed", e)
            emptyList()
        }
    }

    /**
     * Turns a handle, vanity URL or legacy user URL into a canonical UC id
     * via `navigation/resolve_url`. Works signed out. Verified August 2026.
     *
     * Import files are full of these - a Takeout CSV is all UC ids, but an
     * OPML from an RSS reader or a hand-written list is usually @handles, and
     * every other call in the app needs the UC id.
     */
    suspend fun resolveChannelId(urlOrHandle: String): String? = withContext(Dispatchers.IO) {
        val raw = urlOrHandle.trim()
        if (raw.isBlank()) return@withContext null
        if (raw.startsWith("UC") && raw.length >= 24) return@withContext raw
        val url = when {
            raw.startsWith("http://") || raw.startsWith("https://") -> raw
            raw.startsWith("@") -> "https://www.youtube.com/$raw"
            else -> "https://www.youtube.com/${raw.trimStart('/')}"
        }
        try {
            val response = webApi.postWatchApi(
                "navigation/resolve_url",
                JSONObject().put("context", webApi.webContext()).put("url", url)
            ) ?: return@withContext null
            JSONObject(response)
                .optJSONObject("endpoint")
                ?.optJSONObject("browseEndpoint")
                ?.optString("browseId")
                ?.takeIf { it.startsWith("UC") }
        } catch (e: Exception) {
            KLog.w(YOUTUBE_TAG, "resolveChannelId failed for $url", e)
            null
        }
    }

    /**
     * Name, avatar and handle for a channel. Used to fill in imported entries,
     * which arrive carrying a name at best and never an avatar.
     */
    suspend fun getChannelProfile(channelId: String): ChannelProfile? = withContext(Dispatchers.IO) {
        try {
            val raw = webApi.postWatchApi(
                "browse",
                JSONObject().put("context", webApi.webContext()).put("browseId", channelId)
            ) ?: return@withContext null
            val root = JSONObject(raw)
            val metadata = root.optJSONObject("metadata")?.optJSONObject("channelMetadataRenderer")
            val id = metadata?.optString("externalId")?.takeIf { it.isNotBlank() } ?: channelId
            val name = metadata?.optString("title")?.takeIf { it.isNotBlank() }
                ?: return@withContext null
            val thumbs = metadata.optJSONObject("avatar")?.optJSONArray("thumbnails")
            val avatarUrl = thumbs?.optJSONObject((thumbs.length() - 1).coerceAtLeast(0))
                ?.optString("url")?.takeIf { it.isNotBlank() }
                ?.let { if (it.startsWith("//")) "https:$it" else it }
            val handle = metadata.optString("vanityChannelUrl")
                .substringAfterLast('/')
                .takeIf { it.startsWith("@") }

            // Subscriber count lives only in the visible header. The search is
            // scoped to the header subtree on purpose: a whole channel page
            // carries ~95 contentMetadataViewModels, all but one of them a
            // video card, so a document-wide key search would be a coin flip.
            // A miss here is not worth failing the whole profile over.
            val subscriberCountText = runCatching {
                val header = root.optJSONObject("header") ?: return@runCatching null
                val texts = mutableListOf<JSONObject>()
                findObjectsByKey(header, "text", texts)
                texts.mapNotNull { it.optString("content").takeIf { c -> c.isNotBlank() } }
                    .firstOrNull { it.contains("subscriber", ignoreCase = true) }
            }.getOrNull()

            ChannelProfile(id, name, avatarUrl, handle, subscriberCountText)
        } catch (e: Exception) {
            KLog.w(YOUTUBE_TAG, "getChannelProfile failed for $channelId", e)
            null
        }
    }

    private companion object {
        // browse params selecting a channel's Videos tab (protobuf: "videos")
        private const val CHANNEL_VIDEOS_TAB_PARAMS = "EgZ2aWRlb3PyBgQKAjoA"

        // browse params selecting a channel's Posts tab (protobuf: "posts").
        // The second and last hardcoded tab: the feeds sample posts from
        // channels whose page was never opened, so there is no tab list to
        // read it from. Identical on all five channels that had the tab, and a
        // channel without one answers with another tab and no posts.
        // [verified October 2026, signed in, WEB]
        private const val CHANNEL_POSTS_TAB_PARAMS = "EgVwb3N0c_IGBAoCSgA%3D"
    }
}
