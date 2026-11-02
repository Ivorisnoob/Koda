package com.ivor.ivormusic.data.youtube

import com.ivor.ivormusic.data.SubscribedChannel
import com.ivor.ivormusic.data.VideoItem
import com.ivor.ivormusic.data.VideoPlaylist
import com.ivor.ivormusic.data.YouTubeRateLimit
import com.ivor.ivormusic.data.stream.BotCheckVerdict
import com.ivor.ivormusic.util.KLog
import org.json.JSONObject

/**
 * Whether a failed direct search may try NewPipe. [noResults] is an answer, not
 * a failure, so it never does. Nor does anything during a 429 hold or a
 * bot-check verdict: both are about this device, so NewPipe would be refused
 * too, at the cost of more requests to the server that just said stop.
 */
internal fun searchFallbackAllowed(
    noResults: Boolean,
    isRateLimited: Boolean = YouTubeRateLimit.isHeld(),
    isBotChecked: Boolean = BotCheckVerdict.isActive(),
): Boolean {
    if (noResults) return false
    if (isRateLimited || isBotChecked) {
        KLog.w(YOUTUBE_TAG, "Search standing down: limited=$isRateLimited botCheck=$isBotChecked")
        return false
    }
    return true
}

/**
 * www.youtube.com's own "No results found": `estimatedResults` of 0 and a
 * `backgroundPromoRenderer` where the list would be. Verified November 2026.
 */
internal fun isEmptyVideoSearch(root: JSONObject): Boolean {
    val promos = mutableListOf<JSONObject>()
    findObjectsByKey(root, "backgroundPromoRenderer", promos)
    return promos.isNotEmpty()
}

/**
 * YouTube Music's "No results for ...": a `messageRenderer` and no shelf.
 * Verified November 2026.
 */
internal fun isEmptyMusicSearch(root: JSONObject): Boolean {
    val shelves = mutableListOf<JSONObject>()
    findObjectsByKey(root, "musicShelfRenderer", shelves)
    if (shelves.isNotEmpty()) return false
    val messages = mutableListOf<JSONObject>()
    findObjectsByKey(root, "messageRenderer", messages)
    return messages.isNotEmpty()
}

/**
 * One row of a filtered video search. The feed parser under it reads a missing
 * length as live, and a live row's count arrives as runs, so both are settled
 * here: live comes from the row's own badge, and the count from the short text
 * ("137M views", "9.1K watching"). Verified November 2026.
 */
internal fun parseVideoSearchRow(renderer: JSONObject): VideoItem? {
    val video = parseVideoRenderer(renderer) ?: return null
    return video.copy(
        viewCount = getRunText(renderer.optJSONObject("shortViewCountText"))
            ?.takeIf { it.isNotBlank() }
            ?: video.viewCount,
        isLive = hasLiveBadge(renderer),
    )
}

private fun hasLiveBadge(renderer: JSONObject): Boolean {
    val badges = renderer.optJSONArray("badges") ?: return false
    return (0 until badges.length()).any { index ->
        badges.optJSONObject(index)?.optJSONObject("metadataBadgeRenderer")
            ?.optString("style") == "BADGE_STYLE_TYPE_LIVE_NOW"
    }
}

/**
 * Whether a `videoRenderer` is a Short: a reel endpoint or a SHORTS length
 * overlay. A filtered search rarely has one, and "Fully block Shorts" still has
 * to hold when it does.
 */
internal fun isShortVideoRenderer(renderer: JSONObject): Boolean {
    if (renderer.optJSONObject("navigationEndpoint")?.has("reelWatchEndpoint") == true) return true
    val overlays = mutableListOf<JSONObject>()
    findObjectsByKey(renderer, "thumbnailOverlayTimeStatusRenderer", overlays)
    return overlays.any { it.optString("style") == "SHORTS" }
}

/**
 * Playlists of a filtered www.youtube.com search, which arrive as lockups. The
 * lockup's first metadata row reads "Owner • Playlist"; only the owner is worth
 * drawing. Verified November 2026.
 */
internal fun parsePlaylistSearchResults(root: JSONObject): List<VideoPlaylist> {
    val lockups = mutableListOf<JSONObject>()
    findObjectsByKey(root, "lockupViewModel", lockups)
    return lockups.mapNotNull(::parsePlaylistLockup)
        .map { it.copy(subtitle = it.subtitle?.substringBefore(" • ")?.takeIf(String::isNotBlank)) }
        .distinctBy { it.playlistId }
}

/**
 * Channels of a filtered www.youtube.com search. Where the subscriber count
 * sits moves between `videoCountText` and `subscriberCountText`, the other one
 * holding the handle, and a channel can carry neither, so each is recognised by
 * what it says. Verified November 2026.
 */
internal fun parseChannelSearchResults(root: JSONObject): List<SubscribedChannel> {
    val renderers = mutableListOf<JSONObject>()
    findObjectsByKey(root, "channelRenderer", renderers)
    return renderers.mapNotNull { renderer ->
        val channelId = renderer.optString("channelId").takeIf { it.startsWith("UC") }
            ?: return@mapNotNull null
        val name = getRunText(renderer.optJSONObject("title"))?.takeIf { it.isNotBlank() }
            ?: return@mapNotNull null
        val counts = listOf("videoCountText", "subscriberCountText")
            .mapNotNull { getRunText(renderer.optJSONObject(it)) }
        val thumbnails = renderer.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
        val avatarUrl = thumbnails?.optJSONObject(thumbnails.length() - 1)?.optString("url")
            ?.takeIf { it.isNotBlank() }
            ?.let { if (it.startsWith("//")) "https:$it" else it }
        SubscribedChannel(
            channelId,
            name,
            avatarUrl,
            counts.firstOrNull { it.contains("subscriber", ignoreCase = true) },
            counts.firstOrNull { it.startsWith("@") },
        )
    }.distinctBy { it.channelId }
}
