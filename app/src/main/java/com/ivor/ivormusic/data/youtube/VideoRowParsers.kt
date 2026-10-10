package com.ivor.ivormusic.data.youtube

import com.ivor.ivormusic.data.ShortsItem
import com.ivor.ivormusic.data.VideoFeedPage
import com.ivor.ivormusic.data.VideoItem
import com.ivor.ivormusic.data.continuationItemsOrNull
import com.ivor.ivormusic.data.parseShortsEndpoint
import com.ivor.ivormusic.data.videoListContinuationToken
import com.ivor.ivormusic.util.KLog
import org.json.JSONArray
import org.json.JSONObject

// Lists of videos on www.youtube.com: the feed and search pages, the legacy
// videoRenderer rows that still appear beside lockups, and Shorts shelves.
// Pure.

/**
 * Parse video items from YouTube homepage JSON response.
 * Use optimized path traversal instead of recursive findAllObjects.
 */
internal fun parseVideosFromYouTubeJson(
    json: String,
    limit: Int = 30,
    /** The same response already parsed, so a caller that needs the tree does not pay for it twice. */
    parsedRoot: JSONObject? = null
): List<VideoItem> {
    val videos = mutableListOf<VideoItem>()
    try {
        val root = parsedRoot ?: JSONObject(json)

        // Locate content array
        // Normal Home: contents -> singleColumnBrowseResultsRenderer -> tabs[0] -> tabRenderer -> content -> richGridRenderer -> contents
        // Or sectionListRenderer -> contents

        var contents: JSONArray? = null

        // Desktop WEB responses (FEwhat_to_watch) use twoColumnBrowseResultsRenderer;
        // singleColumn is the mobile/YTM shape. Check both.
        val browseContents = root.optJSONObject("contents")
        val tabs = browseContents
            ?.optJSONObject("twoColumnBrowseResultsRenderer")
            ?.optJSONArray("tabs")
            ?: browseContents
                ?.optJSONObject("singleColumnBrowseResultsRenderer")
                ?.optJSONArray("tabs")

        if (tabs != null && tabs.length() > 0) {
             val contentObj = tabs.optJSONObject(0)?.optJSONObject("tabRenderer")?.optJSONObject("content")

             // Try RichGrid (modern Home)
             contents = contentObj?.optJSONObject("richGridRenderer")?.optJSONArray("contents")

             // Try SectionList (old Home or other views)
             if (contents == null) {
                 contents = contentObj?.optJSONObject("sectionListRenderer")?.optJSONArray("contents")
             }
        }

        // History continuation pages retain the same date-grouped rows.
        if (contents == null) contents = continuationItemsOrNull(root)

        // If we found contents, iterate them
        if (contents != null) {
            for (i in 0 until contents.length()) {
                val item = contents.optJSONObject(i) ?: continue

                // 1. RichItemRenderer (Home Grid)
                val richItem = item.optJSONObject("richItemRenderer")
                if (richItem != null) {
                    val content = richItem.optJSONObject("content")

                    // Handler for VideoRenderer (Old UI)
                    content?.optJSONObject("videoRenderer")?.let { 
                        parseVideoRenderer(it)?.let { v -> videos.add(v) } 
                    }

                    // Handler for LockupViewModel (New UI)
                    content?.optJSONObject("lockupViewModel")?.let {
                        parseLockupViewModel(it)?.let { v -> videos.add(v) }
                    }
                }

                // 2. ItemSectionRenderer (flat lists, e.g. FEhistory's date-grouped sections)
                val itemSection = item.optJSONObject("itemSectionRenderer")?.optJSONArray("contents")
                if (itemSection != null) {
                    for (j in 0 until itemSection.length()) {
                        val sectionItem = itemSection.optJSONObject(j) ?: continue
                        sectionItem.optJSONObject("videoRenderer")?.let {
                            parseVideoRenderer(it)?.let { v -> videos.add(v) }
                        }
                        sectionItem.optJSONObject("lockupViewModel")?.let {
                            parseLockupViewModel(it)?.let { v -> videos.add(v) }
                        }
                    }
                }

                // 3. RichSectionRenderer (Shelves within Grid)
                val richSection = item.optJSONObject("richSectionRenderer")?.optJSONObject("content")
                if (richSection != null) {
                    val shelfItems = parseItemsFromShelf(richSection)
                    shelfItems.forEach { shelfItem ->
                         // Check for LockupViewModel in shelf
                         if (shelfItem.has("lockupViewModel")) {
                              parseLockupViewModel(shelfItem.optJSONObject("lockupViewModel"))?.let { v -> videos.add(v) }
                         } else if (shelfItem.has("videoRenderer")) {
                              parseVideoRenderer(shelfItem.optJSONObject("videoRenderer"))?.let { v -> videos.add(v) }
                         } else if (shelfItem.has("gridVideoRenderer")) { // Search results often use this
                              parseVideoRenderer(shelfItem.optJSONObject("gridVideoRenderer"))?.let { v -> videos.add(v) }
                         }
                    }
                }
            }
        }

    } catch (e: Exception) {
        KLog.e(YOUTUBE_TAG, "Could not parse watch history", e)
    }
    return videos.distinctBy { it.videoId }.take(limit)
}

/** Verified September 2026: sorted pages retain a list-scoped search token. */
internal fun parseVideoSearchPage(response: String): VideoFeedPage {
    val root = JSONObject(response)
    check(!root.has("error")) { "Search returned an error body" }
    val renderers = mutableListOf<JSONObject>()
    findObjectsByKey(root, "videoRenderer", renderers)
    findObjectsByKey(root, "lockupViewModel", renderers)
    val videos = renderers.mapNotNull { renderer ->
        if (renderer.has("videoId")) parseVideoRenderer(renderer)
        else parseLockupViewModel(renderer)
    }.distinctBy { it.videoId }
    return VideoFeedPage(videos, videoListContinuationToken(root, search = true))
}

internal fun parseVideoRenderer(videoRenderer: JSONObject?): VideoItem? {
    if (videoRenderer == null) return null
    try {
        val videoId = videoRenderer.optString("videoId")
            .takeIf { it.isNotBlank() }
            ?: videoRenderer.optString("contentId")

        if (videoId.isNullOrBlank()) {
            return null
        }

        // Extract title
        val titleObj = videoRenderer.optJSONObject("title")
        val title = titleObj?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
            ?: titleObj?.optString("simpleText")
            ?: titleObj?.optJSONObject("accessibility")?.optJSONObject("accessibilityData")?.optString("label")
            ?: "Unknown Title"

        // Extract channel name
        val channelObj = videoRenderer.optJSONObject("ownerText")
            ?: videoRenderer.optJSONObject("shortBylineText")
            ?: videoRenderer.optJSONObject("longBylineText")
        val channelName = channelObj?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
            ?: "Unknown Channel"

        // Extract view count
        val viewCountText = videoRenderer.optJSONObject("viewCountText")?.optString("simpleText")
            ?: videoRenderer.optJSONObject("shortViewCountText")?.optString("simpleText")
            ?: videoRenderer.optJSONObject("shortViewCountText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
            ?: ""

        // Extract duration
        val durationText = videoRenderer.optJSONObject("lengthText")?.optString("simpleText") 
            ?: videoRenderer.optJSONObject("lengthText")?.optJSONObject("accessibility")?.optJSONObject("accessibilityData")?.optString("label")?.let { 
                // Convert "3 minutes, 45 seconds" to "3:45"
                val mins = Regex("(\\d+) minute").find(it)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                val secs = Regex("(\\d+) second").find(it)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                String.format("%d:%02d", mins, secs)
            }
            ?: "0:00"
        val durationSeconds = parseDurationToSeconds(durationText)

        // Extract thumbnail
        val thumbnails = videoRenderer.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
        val thumbnailUrl = thumbnails?.let {
            // Get highest quality thumbnail
            var bestUrl: String? = null
            var maxWidth = 0
            for (i in 0 until it.length()) {
                val thumb = it.optJSONObject(i)
                val width = thumb?.optInt("width", 0) ?: 0
                if (width >= maxWidth) {
                    maxWidth = width
                    bestUrl = thumb?.optString("url")
                }
            }
            bestUrl ?: it.optJSONObject(it.length() - 1)?.optString("url")
        }

        // Extract channel icon
        var channelId: String? = null

        // 1. Try directly from channelThumbnailSupportedRenderers
        val channelThumbnails = videoRenderer.optJSONObject("channelThumbnailSupportedRenderers")
            ?.optJSONObject("channelThumbnailWithLinkRenderer")
            ?.optJSONObject("thumbnail")
            ?.optJSONArray("thumbnails")

        var channelIconUrl = channelThumbnails?.let {
            it.optJSONObject(it.length() - 1)?.optString("url")
        }

        // 2. Try to extract channelId and icon from channelObj navigationEndpoint
        try {
            val runs = channelObj?.optJSONArray("runs")
            if (runs != null && runs.length() > 0) {
                val browseEndpoint = runs.optJSONObject(0)?.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")
                channelId = browseEndpoint?.optString("browseId")
            }
        } catch (e: Exception) {}

        // 3. Fallback search for avatar in the whole renderer if missing
        if (channelIconUrl == null) {
            // We use the light-weight finder here since we are inside a single renderer, so recursion is shallow
            val avatarList = mutableListOf<JSONObject>()
            findAllObjects(videoRenderer, "avatar", avatarList, 0)
            for (avatar in avatarList) {
                val thumbs = avatar.optJSONArray("thumbnails")
                if (thumbs != null && thumbs.length() > 0) {
                    channelIconUrl = thumbs.optJSONObject(thumbs.length() - 1)?.optString("url")
                    break
                }
            }
        }
        // Extract upload date
        val publishedText = videoRenderer.optJSONObject("publishedTimeText")?.optString("simpleText")

        return VideoItem(
            videoId = videoId,
            title = title,
            channelName = channelName,
            channelId = channelId,
            channelIconUrl = channelIconUrl,
            thumbnailUrl = thumbnailUrl,
            duration = durationSeconds,
            viewCount = viewCountText,
            uploadedDate = publishedText,
            isLive = durationSeconds <= 0L
        )
    } catch (e: Exception) {
         return null
    }
}

/**
 * Continuation token of a FEwhat_to_watch rich-grid browse response:
 * tabs[0].tabRenderer.content.richGridRenderer.contents holds a trailing
 * continuationItemRenderer whose continuationEndpoint.continuationCommand
 * carries the token for the next /browse page. Verified July 2026.
 */
internal fun extractRichGridContinuation(root: JSONObject): String? {
    val tabs = root.optJSONObject("contents")
        ?.optJSONObject("twoColumnBrowseResultsRenderer")
        ?.optJSONArray("tabs")
        ?: root.optJSONObject("contents")
            ?.optJSONObject("singleColumnBrowseResultsRenderer")
            ?.optJSONArray("tabs")
    val contents = tabs?.optJSONObject(0)
        ?.optJSONObject("tabRenderer")
        ?.optJSONObject("content")
        ?.optJSONObject("richGridRenderer")
        ?.optJSONArray("contents")
        ?: return null
    for (i in 0 until contents.length()) {
        val token = contents.optJSONObject(i)
            ?.optJSONObject("continuationItemRenderer")
            ?.optJSONObject("continuationEndpoint")
            ?.optJSONObject("continuationCommand")
            ?.optString("token")
            ?.takeIf { it.isNotBlank() }
        if (token != null) return token
    }
    return null
}

/** All shortsLockupViewModels of a response, deduped, in shelf order. */
internal fun parseShortsLockups(root: JSONObject): List<ShortsItem> {
    val lockups = mutableListOf<JSONObject>()
    findObjectsByKey(root, "shortsLockupViewModel", lockups)
    return lockups.mapNotNull { parseShortsLockup(it) }
        .distinctBy { it.videoId }
        .take(30)
}

/**
 * shortsLockupViewModel: videoId + sequenceParams live in
 * onTap.innertubeCommand.reelWatchEndpoint, title/views in
 * overlayMetadata.primaryText/secondaryText, portrait thumbnail in the
 * reelWatchEndpoint (1080x1920 frame0).
 */
internal fun parseShortsLockup(lockup: JSONObject): ShortsItem? {
    return try {
        val reel = lockup.optJSONObject("onTap")
            ?.optJSONObject("innertubeCommand")
            ?.optJSONObject("reelWatchEndpoint") ?: return null
        val base = parseReelWatchEndpoint(reel) ?: return null

        val overlay = lockup.optJSONObject("overlayMetadata")
        val title = overlay?.optJSONObject("primaryText")?.optString("content")
            ?.takeIf { it.isNotBlank() } ?: ""
        val viewCount = overlay?.optJSONObject("secondaryText")?.optString("content")
            ?.takeIf { it.isNotBlank() } ?: ""

        // Prefer the lockup's own thumbnailViewModel (sized variants) over
        // the reel endpoint's single 1080x1920 frame
        val sources = lockup.optJSONObject("thumbnailViewModel")
            ?.optJSONObject("image")?.optJSONArray("sources")
        val lockupThumb = sources?.optJSONObject(sources.length() - 1)
            ?.optString("url")?.takeIf { it.isNotBlank() }

        base.copy(
            title = title,
            viewCount = viewCount,
            thumbnailUrl = lockupThumb ?: base.thumbnailUrl
        )
    } catch (e: Exception) {
        KLog.w(YOUTUBE_TAG, "parseShortsLockup failed", e)
        null
    }
}

/** reelWatchEndpoint: videoId, portrait thumbnail and sequence seed. */
private fun parseReelWatchEndpoint(reel: JSONObject): ShortsItem? =
    parseShortsEndpoint(reel)
