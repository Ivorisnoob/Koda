package com.ivor.ivormusic.data.youtube

import android.content.Context
import com.ivor.ivormusic.data.SubscribedChannel
import com.ivor.ivormusic.data.ThemePreferences
import com.ivor.ivormusic.data.VideoFeedPage
import com.ivor.ivormusic.data.VideoItem
import com.ivor.ivormusic.data.VideoPlaylist
import com.ivor.ivormusic.data.VideoSearchDateFilter
import com.ivor.ivormusic.data.VideoSearchSort
import com.ivor.ivormusic.data.items
import com.ivor.ivormusic.util.KLog
import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.search.SearchExtractor

/**
 * Search on www.youtube.com: videos, playlists and channels.
 *
 * Every search goes to InnerTube, with NewPipe behind it only as
 * [searchFallbackAllowed] permits. A cursor belongs to its query and sort, and
 * an exhausted InnerTube cursor never resumes a NewPipe one.
 */
internal class VideoSearch(
    private val context: Context,
    private val webApi: WebApi,
    private val newPipeGateway: NewPipeGateway,
) {
    private data class VideoSearchKey(val query: String, val sort: VideoSearchSort)

    private val videoSearchExtractorCache =
        mutableMapOf<VideoSearchKey, SearchExtractor>()

    private val videoSearchNextPageCache = mutableMapOf<VideoSearchKey, Page?>()

    // Present null means InnerTube exhausted; it must never resume NewPipe.
    private val videoSearchContinuations = mutableMapOf<VideoSearchKey, String?>()

    /** Forget every cursor: they belong to the profile that searched. */
    fun clearCaches() {
        videoSearchExtractorCache.clear()
        videoSearchNextPageCache.clear()
        videoSearchContinuations.clear()
    }

    /**
     * Search for videos on YouTube (not YouTube Music).
     * Returns VideoItem objects with view counts, channel info, etc.
     * [dateFilter] restricts results by upload date via the `after:` search operator.
     * [sort] picks the result order; NewPipe cannot sort, so its fallback comes
     * back in relevance order.
     */
    suspend fun searchVideos(
        query: String,
        dateFilter: VideoSearchDateFilter = VideoSearchDateFilter.ANY,
        sort: VideoSearchSort = VideoSearchSort.RELEVANCE
    ): List<VideoItem> = withContext(Dispatchers.IO) {
        val effectiveQuery = dateFilter.applyTo(query)
        val key = VideoSearchKey(effectiveQuery, sort)
        videoSearchExtractorCache.remove(key)
        videoSearchNextPageCache.remove(key)
        videoSearchContinuations.remove(key)

        val direct = searchVideosInnerTube(effectiveQuery, sort)
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        if (direct != null) {
            videoSearchContinuations[key] = direct.continuation
            return@withContext direct.videos
        }
        if (!searchFallbackAllowed(noResults = false)) return@withContext emptyList()
        KLog.w(YOUTUBE_TAG, "Direct video search failed, falling back to NewPipe")

        try {
            // Use YouTube videos filter (not music_videos)
            val searchExtractor = newPipeGateway.regionalSearchExtractor(effectiveQuery, listOf(FILTER_YOUTUBE_VIDEOS), "")
            searchExtractor.fetchPage()

            // Cache for pagination (see searchVideosNext)
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            videoSearchExtractorCache[key] = searchExtractor
            videoSearchNextPageCache[key] =
                if (searchExtractor.initialPage.hasNextPage()) searchExtractor.initialPage.nextPage else null

            searchExtractor.initialPage.items.toVideoItems(context)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            KLog.e(YOUTUBE_TAG, "Error searching videos", e)
            emptyList()
        }
    }

    /** Continue exactly the query, date window and sort used for page one. */
    suspend fun searchVideosNext(
        query: String,
        dateFilter: VideoSearchDateFilter = VideoSearchDateFilter.ANY,
        sort: VideoSearchSort = VideoSearchSort.RELEVANCE
    ): List<VideoItem> = withContext(Dispatchers.IO) {
        try {
            val key = VideoSearchKey(dateFilter.applyTo(query), sort)
            if (videoSearchContinuations.containsKey(key)) {
                val token = videoSearchContinuations[key] ?: return@withContext emptyList()
                val response = webApi.postWatchApi("search", JSONObject()
                    .put("context", webApi.webContext()).put("continuation", token))
                    ?: return@withContext emptyList()
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                val page = parseVideoSearchPage(response, dropShorts())
                videoSearchContinuations[key] = page.continuation?.takeUnless { it == token }
                return@withContext page.videos
            }
            val extractor = videoSearchExtractorCache[key] ?: return@withContext emptyList()
            val pageInfo = videoSearchNextPageCache[key] ?: return@withContext emptyList()
            val nextPage = extractor.getPage(pageInfo)
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            videoSearchNextPageCache[key] =
                if (nextPage.hasNextPage()) nextPage.nextPage else null
            nextPage.items.toVideoItems(context)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            KLog.e(YOUTUBE_TAG, "Error loading more video results", e)
            emptyList()
        }
    }

    /**
     * Video search through a direct InnerTube /search call. Results arrive as
     * videoRenderers (legacy shape, still what /search returns signed out) or
     * lockupViewModels; both parsers already exist for the feed. Verified
     * November 2026. Null when YouTube did not answer the search, which is not
     * the same as a search that found nothing.
     */
    private fun searchVideosInnerTube(query: String, sort: VideoSearchSort): VideoFeedPage? {
        return try {
            val body = JSONObject()
                .put("context", webApi.webContext())
                .put("query", query)
                .put("params", buildVideoSearchParams(sort))
            val response = webApi.postWatchApi("search", body) ?: return null
            parseVideoSearchPage(response, dropShorts()).takeIf {
                it.videos.isNotEmpty() || isEmptyVideoSearch(JSONObject(response))
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            KLog.e(YOUTUBE_TAG, "InnerTube video search failed", e)
            null
        }
    }

    private fun dropShorts(): Boolean = ThemePreferences.isShortsHardBlocked(context)

    /** One filtered /search, parsed; null when the fallback should be asked. */
    private fun <T> searchFiltered(
        query: String,
        params: String,
        parse: (JSONObject) -> List<T>,
    ): List<T>? {
        val body = JSONObject()
            .put("context", webApi.webContext())
            .put("query", query)
            .put("params", params)
        return try {
            val root = webApi.postWatchApi("search", body)?.let(::JSONObject)
            val results = root?.let(parse).orEmpty()
            results.takeIf { it.isNotEmpty() || (root != null && isEmptyVideoSearch(root)) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            KLog.e(YOUTUBE_TAG, "Direct filtered search failed", e)
            null
        }
    }

    /**
     * Base64url protobuf for the /search `params` field: sort order (field 1)
     * plus a filter block (field 2) pinning the result type to videos, the same
     * values the youtube.com filter sheet puts in the `sp` URL param.
     * Verified July 2026.
     */
    private fun buildVideoSearchParams(sort: VideoSearchSort): String {
        val bytes = byteArrayOf(0x08, sort.code.toByte(), 0x12, 0x02, 0x10, 0x01)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /**
     * Search for playlists on regular YouTube (video mode search). Mapped to
     * [VideoPlaylist] so results plug straight into the same playlist detail
     * page and models the video Library tab uses.
     */
    suspend fun searchVideoPlaylists(query: String): List<VideoPlaylist> = withContext(Dispatchers.IO) {
        searchFiltered(query, PARAMS_PLAYLISTS, ::parsePlaylistSearchResults)
            ?.let { return@withContext it }
        if (!searchFallbackAllowed(noResults = false)) return@withContext emptyList()
        try {
            val searchExtractor = newPipeGateway.regionalSearchExtractor(query, listOf(FILTER_YOUTUBE_PLAYLISTS), "")
            searchExtractor.fetchPage()

            searchExtractor.initialPage.items.filterIsInstance<PlaylistInfoItem>().mapNotNull { item ->
                val playlistId = item.url?.substringAfter("list=", "")
                    ?.substringBefore("&")
                    ?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                VideoPlaylist(
                    playlistId = playlistId,
                    title = item.name.orEmpty(),
                    thumbnailUrl = item.thumbnails?.maxByOrNull { it.width }?.url
                        ?: item.thumbnails?.firstOrNull()?.url,
                    videoCountText = item.streamCount.takeIf { it > 0 }?.let { "$it videos" },
                    subtitle = item.uploaderName?.takeIf { it.isNotBlank() }
                )
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            KLog.e(YOUTUBE_TAG, "Error searching video playlists", e)
            emptyList()
        }
    }

    /**
     * Channels matching [query], for video mode's Channels search filter.
     */
    suspend fun searchChannels(query: String): List<SubscribedChannel> =
        withContext(Dispatchers.IO) {
            searchFiltered(query, PARAMS_CHANNELS, ::parseChannelSearchResults)
                ?.let { return@withContext it }
            if (!searchFallbackAllowed(noResults = false)) return@withContext emptyList()
            try {
                val searchExtractor =
                    newPipeGateway.regionalSearchExtractor(query, listOf(FILTER_YOUTUBE_CHANNELS), "")
                searchExtractor.fetchPage()

                searchExtractor.initialPage.items
                    .filterIsInstance<ChannelInfoItem>()
                    .mapNotNull { item ->
                        // Every other call in the app keys off the canonical id,
                        // so a result whose URL is a handle rather than /channel/
                        // is dropped here instead of failing later on the page.
                        val channelId = item.url?.substringAfterLast('/')
                            ?.takeIf { it.startsWith("UC") } ?: return@mapNotNull null
                        SubscribedChannel(
                            channelId = channelId,
                            name = item.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null,
                            avatarUrl = item.thumbnails?.maxByOrNull { it.width }?.url
                                ?: item.thumbnails?.firstOrNull()?.url,
                            subscriberCountText = item.subscriberCount.takeIf { it >= 0 }
                                ?.let { "${VideoItem.formatViewCount(it)} subscribers" }
                        )
                    }
                    .distinctBy { it.channelId }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                KLog.e(YOUTUBE_TAG, "Error searching channels", e)
                emptyList()
            }
        }
}

// The filter sheet's `params` on www.youtube.com. Verified November 2026.
private const val PARAMS_PLAYLISTS = "EgIQAw%3D%3D"
private const val PARAMS_CHANNELS = "EgIQAg%3D%3D"

// NewPipe's content filters for a search on www.youtube.com.
private const val FILTER_YOUTUBE_VIDEOS = "videos"
private const val FILTER_YOUTUBE_PLAYLISTS = "playlists"
private const val FILTER_YOUTUBE_CHANNELS = "channels"
