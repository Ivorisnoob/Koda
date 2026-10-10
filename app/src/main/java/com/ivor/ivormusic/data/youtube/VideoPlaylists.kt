package com.ivor.ivormusic.data.youtube

import android.content.Context
import com.ivor.ivormusic.data.PlaylistPageInfo
import com.ivor.ivormusic.data.SessionManager
import com.ivor.ivormusic.data.VideoItem
import com.ivor.ivormusic.data.VideoPlaylist
import com.ivor.ivormusic.data.VideoPlaylistCursor
import com.ivor.ivormusic.data.VideoPlaylistPage
import com.ivor.ivormusic.data.YouTubeSession
import com.ivor.ivormusic.data.items
import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Video playlists on www.youtube.com: the account's own list, a playlist's
 * header and its videos.
 *
 * A playlist screen reads one page per call and keeps the cursor; only an
 * explicit whole-playlist operation (a download) uses the complete loader.
 * Signed out the rows are lockups, which NewPipe's playlist extractor does
 * not collect, so the browse path is the normal one and NewPipe the fallback.
 */
internal class VideoPlaylists(
    private val context: Context,
    private val webApi: WebApi,
    private val sessionManager: SessionManager,
) {
    /**
     * The user's playlists from FEplaylist_aggregation (requires login).
     * Watch Later never appears here and Liked videos ("LL") sometimes does;
     * the Library UI pins both as fixed entries, so they are filtered out.
     */
    suspend fun getVideoPlaylists(): List<VideoPlaylist> = withContext(Dispatchers.IO) {
        // This one really is account-only: the browse below runs anonymously
        // now, and signed out it would spend a request to be handed a
        // signed-out shell with no playlists in it.
        if (!sessionManager.isLoggedIn()) return@withContext emptyList()
        try {
            val json = webApi.fetchYouTubeBrowse("FEplaylist_aggregation")
                .takeIf { it.isNotEmpty() } ?: return@withContext emptyList()
            val root = JSONObject(json)
            val lockups = mutableListOf<JSONObject>()
            findObjectsByKey(root, "lockupViewModel", lockups)
            lockups.mapNotNull { parsePlaylistLockup(it) }
                .filter { it.playlistId != "LL" && it.playlistId != "WL" }
                .distinctBy { it.playlistId }
        } catch (e: Exception) {
            KLog.e(YOUTUBE_TAG, "getVideoPlaylists failed", e)
            emptyList()
        }
    }

    /**
     * What a playlist says it is - title, author, cover, length - without its
     * contents.
     *
     * Exists for links. Everywhere else a playlist arrives already described,
     * from a search result or a lockup or the account's own list, but a shared
     * URL is an id and nothing more, and a page opened on an id alone has an
     * empty header. [getPlaylistVideos] answers the other half of the same
     * response and throws this half away, so the two are deliberately separate
     * calls: the pages fetch their own items, and asking for both here would
     * fetch every item twice.
     *
     * Anonymous when there is no session - `fetchYouTubeBrowse` only signs when
     * one exists, and a public playlist answers without it (verified August
     * 2026) - which is what a shared link needs, since most arrive with the app
     * signed out.
     *
     * Null means "no page behind this id": a generated mix answers "This
     * playlist type is unviewable", and private or deleted lists answer with an
     * alert and no header at all. That is the caller's signal to fall back to
     * playing the id rather than to open a blank page.
     */
    suspend fun getPlaylistHeader(playlistId: String): PlaylistPageInfo? =
        withContext(Dispatchers.IO) {
            val listId = playlistId.removePrefix("VL").takeIf { it.isNotBlank() }
                ?: return@withContext null
            try {
                val json = webApi.fetchYouTubeBrowse("VL$listId").takeIf { it.isNotEmpty() }
                    ?: return@withContext null
                val root = JSONObject(json)
                parseModernPlaylistHeader(root, listId)
                    ?: parseLegacyPlaylistHeader(root, listId)
            } catch (e: Exception) {
                KLog.e(YOUTUBE_TAG, "getPlaylistHeader failed for $playlistId", e)
                null
            }
        }

    /** Bounded preview for link resolution. Detail screens retain the page cursor. */
    suspend fun getPlaylistVideos(playlistId: String): List<VideoItem> =
        getPlaylistVideosPage(playlistId)?.videos.orEmpty()

    /**
     * Fetch exactly one playlist page. WEB and NewPipe keep separate cursors;
     * a failed continuation stays retryable and never switches source mid-list.
     * Renderer and continuation scopes verified August/September 2026.
     */
    internal suspend fun getPlaylistVideosPage(
        playlistId: String,
        continuation: VideoPlaylistCursor? = null,
        session: YouTubeSession? = sessionManager.captureSession()
    ): VideoPlaylistPage? = withContext(Dispatchers.IO) {
        try {
            if (continuation is VideoPlaylistCursor.NewPipe) {
                val page = continuation.extractor.getPage(continuation.page)
                return@withContext VideoPlaylistPage(page.items.toVideoItems(context),
                    page.nextPage?.takeIf { page.hasNextPage() }
                        ?.let { VideoPlaylistCursor.NewPipe(continuation.extractor, it) })
            }
            val body = JSONObject().put("context", webApi.webContext())
            if (continuation is VideoPlaylistCursor.Browse) body.put("continuation", continuation.token)
            else body.put("browseId", if (playlistId.startsWith("VL")) playlistId else "VL$playlistId")
            val raw = webApi.postWatchApi("browse", body, session)
            val root = raw?.takeIf { it.isNotBlank() }?.let { JSONObject(it) }
                ?.takeUnless { it.has("error") }
            if (root != null) {
                val videos = parseVideoPlaylistRows(root)
                val token = extractVideoPlaylistContinuationToken(root)
                if (videos.isNotEmpty() || token != null || continuation != null ||
                    playlistId.removePrefix("VL") in setOf("WL", "LL", "LM")) {
                    KLog.d(YOUTUBE_TAG, "Playlist ${if (continuation == null) "first" else "next"} page: ${videos.size} videos")
                    return@withContext VideoPlaylistPage(videos, token?.let { VideoPlaylistCursor.Browse(it) })
                }
            }
            if (continuation != null || playlistId.removePrefix("VL") in setOf("WL", "LL", "LM")) {
                return@withContext null
            }
            val extractor = youtubeService.getPlaylistExtractor(
                "https://www.youtube.com/playlist?list=${playlistId.removePrefix("VL")}")
            extractor.fetchPage()
            val page = extractor.initialPage
            VideoPlaylistPage(page.items.toVideoItems(context),
                page.nextPage?.takeIf { page.hasNextPage() }
                    ?.let { VideoPlaylistCursor.NewPipe(extractor, it) })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            KLog.e(YOUTUBE_TAG, "Error fetching video playlist page", e)
            null
        }
    }

    /**
     * Resolve a playlist only if one of the two independent paths reaches its
     * real end. Used by whole-playlist download; returning null on an incomplete
     * chain prevents a button labelled "full playlist" from silently queuing
     * only the first exact page boundary.
     *
     * The WEB renderer differs per session: an authenticated response can carry
     * `playlistVideoRenderer`s, while an anonymous public playlist uses
     * `lockupViewModel`s. Verified August 2026: page one puts its playlist token
     * inside `itemSectionRenderer`; later pages arrive under
     * `appendContinuationItemsAction.continuationItems`.
     */
    suspend fun getCompletePlaylistVideos(playlistId: String): List<VideoItem>? =
        withContext(Dispatchers.IO) {
            val browseResult = getPlaylistVideosFromBrowse(playlistId)
            if (browseResult.complete && browseResult.videos.isNotEmpty()) {
                return@withContext browseResult.videos
            }

            val newPipeResult = getCompletePlaylistVideosFromNewPipe(playlistId)
            if (newPipeResult.complete && newPipeResult.videos.isNotEmpty()) {
                return@withContext newPipeResult.videos
            }

            val partialSize = maxOf(browseResult.videos.size, newPipeResult.videos.size)
            if (partialSize > 0) {
                KLog.w(
                    YOUTUBE_TAG,
                    "Refusing incomplete full-playlist load for $playlistId ($partialSize videos resolved)"
                )
            }
            null
        }

    private data class VideoPlaylistLoadResult(
        val videos: List<VideoItem>,
        val complete: Boolean
    )

    private fun getPlaylistVideosFromBrowse(playlistId: String): VideoPlaylistLoadResult {
        val browseId = if (playlistId.startsWith("VL")) playlistId else "VL$playlistId"
        val videos = mutableListOf<VideoItem>()
        val seenTokens = mutableSetOf<String>()
        var json = webApi.fetchYouTubeBrowse(browseId)
        if (json.isEmpty()) return VideoPlaylistLoadResult(emptyList(), complete = false)

        return try {
            while (true) {
                val root = JSONObject(json)
                videos += parseVideoPlaylistRows(root)

                val token = extractVideoPlaylistContinuationToken(root) ?: break
                if (!seenTokens.add(token)) {
                    KLog.w(YOUTUBE_TAG, "Repeated video playlist continuation for $playlistId")
                    return VideoPlaylistLoadResult(videos, complete = false)
                }
                json = webApi.fetchYouTubeBrowseContinuation(token)
                if (json.isEmpty()) {
                    KLog.w(
                        YOUTUBE_TAG,
                        "Video playlist continuation failed for $playlistId after ${videos.size} videos"
                    )
                    return VideoPlaylistLoadResult(videos, complete = false)
                }
            }
            VideoPlaylistLoadResult(videos, complete = true)
        } catch (e: Exception) {
            KLog.e(YOUTUBE_TAG, "Video playlist browse failed for $playlistId", e)
            VideoPlaylistLoadResult(videos, complete = false)
        }
    }

    /**
     * A playlist's videos through NewPipe's playlist page, as the last resort
     * when the browse above parsed to nothing.
     *
     * **Not the primary signed-out path**: page-one browsing uses WEB lockups,
     * while this is the independent full-load fallback. NewPipe's playlist
     * extractor collects `playlistVideoRenderer`s, and a signed-out browse can
     * therefore come back with zero items and *no exception* when that shape
     * changes. It remains valuable here precisely because its page tokens are
     * independent from WEB's.
     *
     * Every NewPipe continuation is followed. If one fails, the rows already
     * resolved are returned as an explicitly incomplete result so the caller
     * can reject the batch instead of presenting a partial one as complete.
     */
    private suspend fun getCompletePlaylistVideosFromNewPipe(
        playlistId: String
    ): VideoPlaylistLoadResult =
        withContext(Dispatchers.IO) {
            val listId = playlistId.removePrefix("VL")
            // The account's own feeds have no public page to fetch: asking for
            // one anonymously is a guaranteed miss, so skip the request.
            if (listId == "WL" || listId == "LL" || listId == "LM") {
                return@withContext VideoPlaylistLoadResult(emptyList(), complete = false)
            }
            try {
                val extractor = youtubeService.getPlaylistExtractor(
                    "https://www.youtube.com/playlist?list=$listId"
                )
                extractor.fetchPage()
                val videos = mutableListOf<VideoItem>()
                var page = extractor.initialPage
                videos += page.items.toVideoItems(context)
                while (page.hasNextPage()) {
                    try {
                        page = extractor.getPage(page.nextPage)
                        videos += page.items.toVideoItems(context)
                    } catch (e: Exception) {
                        KLog.w(
                            YOUTUBE_TAG,
                            "Anonymous video playlist continuation failed for $listId after ${videos.size} videos",
                            e
                        )
                        return@withContext VideoPlaylistLoadResult(videos, complete = false)
                    }
                }
                VideoPlaylistLoadResult(videos, complete = true)
            } catch (e: Exception) {
                KLog.e(YOUTUBE_TAG, "Anonymous playlist fetch failed for $listId", e)
                VideoPlaylistLoadResult(emptyList(), complete = false)
            }
        }
}
