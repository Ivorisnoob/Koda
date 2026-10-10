package com.ivor.ivormusic.data.youtube

import com.ivor.ivormusic.data.PlaylistPageInfo
import com.ivor.ivormusic.data.VideoItem
import com.ivor.ivormusic.data.VideoPlaylist
import org.json.JSONObject

// A video playlist page on www.youtube.com: its header in both shapes, its
// rows signed in (playlistVideoRenderer) and signed out (lockupViewModel),
// and the playlist cards of a library or channel. Pure.

internal fun parseVideoPlaylistRows(root: JSONObject): List<VideoItem> {
    val renderers = mutableListOf<JSONObject>()
    findObjectsByKey(root, "playlistVideoRenderer", renderers)
    if (renderers.isNotEmpty()) return renderers.mapNotNull { parsePlaylistVideoRenderer(it) }
    val lockups = mutableListOf<JSONObject>()
    findObjectsByKey(root, "lockupViewModel", lockups)
    return lockups.mapNotNull { parseLockupViewModel(it) }
}

internal fun extractVideoPlaylistContinuationToken(root: JSONObject): String? {
    val scopes = mutableListOf<JSONObject>()
    findObjectsByKey(root, "itemSectionRenderer", scopes)
    findObjectsByKey(root, "playlistVideoListRenderer", scopes)
    findObjectsByKey(root, "appendContinuationItemsAction", scopes)
    findObjectsByKey(root, "reloadContinuationItemsCommand", scopes)
    return scopes.asSequence().mapNotNull { scope ->
        val tokens = mutableListOf<String>()
        findContinuationTokens(scope, tokens)
        tokens.firstOrNull()
    }.firstOrNull()
}

/**
 * playlistVideoRenderer: videoId/title/shortBylineText/lengthSeconds plus
 * a combined videoInfo line ("376K views • 2 days ago"). Unavailable
 * entries (deleted/private) come with isPlayable=false and are skipped.
 */
private fun parsePlaylistVideoRenderer(renderer: JSONObject): VideoItem? {
    val videoId = renderer.optString("videoId").takeIf { it.length == 11 } ?: return null
    if (!renderer.optBoolean("isPlayable", true)) return null
    val title = getRunText(renderer.optJSONObject("title"))
        ?.takeIf { it.isNotBlank() } ?: return null

    val byline = renderer.optJSONObject("shortBylineText")
    val channelName = getRunText(byline)?.takeIf { it.isNotBlank() } ?: "Unknown Channel"
    val channelId = byline?.optJSONArray("runs")?.optJSONObject(0)
        ?.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")
        ?.optString("browseId")?.takeIf { it.isNotBlank() }

    val info = getRunText(renderer.optJSONObject("videoInfo")).orEmpty()
    val infoParts = info.split("•").map { it.trim() }.filter { it.isNotBlank() }
    val viewCount = infoParts.firstOrNull { it.contains("view", ignoreCase = true) } ?: ""
    val uploadedDate = infoParts.firstOrNull { !it.contains("view", ignoreCase = true) }

    val thumbs = renderer.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
    val thumbnailUrl = thumbs?.optJSONObject(thumbs.length() - 1)?.optString("url")
        ?.takeIf { it.isNotBlank() } ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

    return VideoItem(
        videoId = videoId,
        title = title,
        channelName = channelName,
        channelId = channelId,
        thumbnailUrl = thumbnailUrl,
        duration = renderer.optString("lengthSeconds").toLongOrNull() ?: 0L,
        viewCount = viewCount,
        uploadedDate = uploadedDate
    )
}

/**
 * Playlist lockup (LOCKUP_CONTENT_TYPE_PLAYLIST / PODCAST): contentId is
 * the playlist id, title lives in lockupMetadataViewModel, the video count
 * is a thumbnailBadgeViewModel text ("28 videos") and the first metadata
 * row carries privacy/type parts ("Private", "Playlist").
 */
internal fun parsePlaylistLockup(lockup: JSONObject): VideoPlaylist? {
    val contentType = lockup.optString("contentType")
    if (contentType != "LOCKUP_CONTENT_TYPE_PLAYLIST" &&
        contentType != "LOCKUP_CONTENT_TYPE_PODCAST"
    ) return null
    val playlistId = lockup.optString("contentId").takeIf { it.isNotBlank() } ?: return null
    val metadata = lockup.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel")
    val title = metadata?.optJSONObject("title")?.optString("content")
        ?.takeIf { it.isNotBlank() } ?: return null

    val contentImage = lockup.optJSONObject("contentImage")
    val thumbnailViewModel = contentImage?.optJSONObject("collectionThumbnailViewModel")
        ?.optJSONObject("primaryThumbnail")?.optJSONObject("thumbnailViewModel")
        ?: contentImage?.optJSONObject("thumbnailViewModel")
    val sources = thumbnailViewModel?.optJSONObject("image")?.optJSONArray("sources")
    var thumbnailUrl: String? = null
    var maxWidth = -1
    if (sources != null) {
        for (i in 0 until sources.length()) {
            val source = sources.optJSONObject(i)
            val width = source?.optInt("width", 0) ?: 0
            if (width >= maxWidth) {
                maxWidth = width
                thumbnailUrl = source?.optString("url")
            }
        }
    }

    val badges = mutableListOf<JSONObject>()
    findObjectsByKey(lockup, "thumbnailBadgeViewModel", badges)
    val videoCountText = badges.firstNotNullOfOrNull { badge ->
        badge.optString("text").takeIf { it.isNotBlank() }
    }

    val firstRowParts = metadata.optJSONObject("metadata")
        ?.optJSONObject("contentMetadataViewModel")
        ?.optJSONArray("metadataRows")?.optJSONObject(0)
        ?.optJSONArray("metadataParts")
    val subtitle = firstRowParts?.let { parts ->
        (0 until parts.length())
            .mapNotNull { parts.optJSONObject(it)?.optJSONObject("text")?.optString("content") }
            .filter { it.isNotBlank() }
            .joinToString(" • ")
            .takeIf { it.isNotBlank() }
    }

    return VideoPlaylist(
        playlistId = playlistId,
        title = title,
        thumbnailUrl = thumbnailUrl?.takeIf { it.isNotBlank() },
        videoCountText = videoCountText,
        subtitle = subtitle
    )
}

/**
 * `pageHeaderViewModel`, which is what an ordinary `PL…` playlist returns
 * (verified August 2026, both signed in and signed out).
 *
 * The header's metadata is positional prose - "by the bootleg boy",
 * "Playlist", "960 videos", "26,738,861 views" - so nothing here reads a
 * row by index. The author comes off the avatar stack, which is a
 * structurally distinct part rather than a position, and the count is
 * whichever part talks about videos. That word is localized, so a miss
 * reports -1 ("the page did not say") rather than a number invented from
 * the wrong row.
 */
internal fun parseModernPlaylistHeader(
    root: JSONObject,
    listId: String
): PlaylistPageInfo? {
    val headers = mutableListOf<JSONObject>()
    findObjectsByKey(root, "pageHeaderViewModel", headers)
    val header = headers.firstOrNull() ?: return null

    val title = header.optJSONObject("title")
        ?.optJSONObject("dynamicTextViewModel")
        ?.optJSONObject("text")
        ?.optString("content")
        ?.takeIf { it.isNotBlank() }
        ?: return null

    val rows = header.optJSONObject("metadata")
        ?.optJSONObject("contentMetadataViewModel")
        ?.optJSONArray("metadataRows")

    var author = ""
    var itemCount = -1
    if (rows != null) {
        for (rowIndex in 0 until rows.length()) {
            val parts = rows.optJSONObject(rowIndex)?.optJSONArray("metadataParts") ?: continue
            for (partIndex in 0 until parts.length()) {
                val part = parts.optJSONObject(partIndex) ?: continue
                val avatarText = part.optJSONObject("avatarStack")
                    ?.optJSONObject("avatarStackViewModel")
                    ?.optJSONObject("text")
                    ?.optString("content")
                    ?.takeIf { it.isNotBlank() }
                if (avatarText != null && author.isBlank()) {
                    author = avatarText.removePrefix("by ").trim()
                    continue
                }
                val text = part.optJSONObject("text")?.optString("content").orEmpty()
                if (itemCount < 0 && text.contains("video", ignoreCase = true)) {
                    itemCount = countFromText(text)
                }
            }
        }
    }

    return PlaylistPageInfo(
        playlistId = listId,
        title = title,
        author = author,
        thumbnailUrl = header.optJSONObject("heroImage")
            ?.optJSONObject("contentPreviewImageViewModel")
            ?.optJSONObject("image")
            ?.let { bestImageSource(it.optJSONArray("sources")) },
        itemCount = itemCount
    )
}

/**
 * `playlistHeaderRenderer`, the legacy shape, still what an album playlist
 * (`OLAK5uy_…`) returns (verified August 2026).
 *
 * Its subtitle is "Daft Punk • Album", so the author is the part before the
 * separator; the type half is dropped rather than shown, because the page
 * this feeds already says what it is.
 */
internal fun parseLegacyPlaylistHeader(
    root: JSONObject,
    listId: String
): PlaylistPageInfo? {
    val headers = mutableListOf<JSONObject>()
    findObjectsByKey(root, "playlistHeaderRenderer", headers)
    val header = headers.firstOrNull() ?: return null

    val title = getRunText(header.optJSONObject("title"))?.takeIf { it.isNotBlank() }
        ?: return null
    val author = getRunText(header.optJSONObject("ownerText"))
        ?.takeIf { it.isNotBlank() }
        ?: getRunText(header.optJSONObject("subtitle"))
            ?.substringBefore("•")
            ?.trim()
            .orEmpty()

    return PlaylistPageInfo(
        playlistId = listId,
        title = title,
        author = author,
        thumbnailUrl = header.optJSONObject("playlistHeaderBanner")
            ?.optJSONObject("heroPlaylistThumbnailRenderer")
            ?.optJSONObject("thumbnail")
            ?.let { bestThumbnail(it.optJSONArray("thumbnails")) },
        itemCount = countFromText(getRunText(header.optJSONObject("numVideosText")))
    )
}
