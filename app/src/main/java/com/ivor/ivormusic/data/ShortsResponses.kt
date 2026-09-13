package com.ivor.ivormusic.data

import org.json.JSONObject

/** WEB Shorts navigation and sequence responses, verified live September 2026. */
internal fun parseShortsSeed(root: JSONObject): ShortsFeedPage {
    if (root.optString("status") != "REEL_ITEM_WATCH_STATUS_SUCCEEDED") {
        throw java.io.IOException("YouTube did not return a Shorts seed")
    }
    val continuation = root.optString("sequenceContinuation").takeIf { it.isNotBlank() }
    val reel = root.optJSONObject("replacementEndpoint")?.optJSONObject("reelWatchEndpoint")
    val item = reel?.let(::parseShortsEndpoint)
        ?: throw java.io.IOException("YouTube returned no Shorts replacement endpoint")
    val header = shortsDescriptionHeader(root)
    return ShortsFeedPage(listOf(item.copy(
        title = shortsText(header?.optJSONObject("title")),
        viewCount = shortsText(header?.optJSONObject("views")),
        sequenceParams = continuation
    )), continuation)
}

/** Only top-level entries belong to this page; nested endpoints include unrelated UI actions. */
internal fun parseShortsSequence(root: JSONObject): ShortsFeedPage {
    val entries = root.optJSONArray("entries")
        ?: throw java.io.IOException("YouTube returned no Shorts sequence entries")
    val items = buildList {
        for (index in 0 until entries.length()) {
            val reel = entries.optJSONObject(index)?.optJSONObject("command")
                ?.optJSONObject("reelWatchEndpoint") ?: continue
            parseShortsEndpoint(reel)?.let { add(it) }
        }
    }.distinctBy { it.videoId }
    val continuation = root.optJSONObject("continuationEndpoint")
        ?.optJSONObject("continuationCommand")?.optString("token")?.takeIf { it.isNotBlank() }
    return ShortsFeedPage(items, continuation)
}

internal fun parseShortsEndpoint(reel: JSONObject): ShortsItem? {
    val videoId = reel.optString("videoId").takeIf { it.length == 11 } ?: return null
    val thumbnail = reel.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
        ?.optJSONObject(0)?.optString("url")?.takeIf { it.isNotBlank() }
    // Some sequence entries include metadata for free. Never use their WEB stream URLs.
    val details = reel.optJSONObject("unserializedPrefetchData")
        ?.optJSONObject("playerResponse")?.optJSONObject("videoDetails")
        ?.takeIf { it.optString("videoId") == videoId }
    return ShortsItem(
        videoId = videoId,
        title = details?.optString("title").orEmpty(),
        thumbnailUrl = thumbnail,
        sequenceParams = reel.optString("sequenceParams").takeIf { it.isNotBlank() }
    )
}

private fun shortsDescriptionHeader(root: JSONObject): JSONObject? {
    val panels = root.optJSONArray("engagementPanels") ?: return null
    for (index in 0 until panels.length()) {
        val items = panels.optJSONObject(index)?.optJSONObject("engagementPanelSectionListRenderer")
            ?.optJSONObject("content")?.optJSONObject("structuredDescriptionContentRenderer")
            ?.optJSONArray("items") ?: continue
        for (item in 0 until items.length()) {
            items.optJSONObject(item)?.optJSONObject("videoDescriptionHeaderRenderer")?.let { return it }
        }
    }
    return null
}

private fun shortsText(text: JSONObject?): String {
    text ?: return ""
    text.optString("simpleText").takeIf { it.isNotBlank() }?.let { return it }
    val runs = text.optJSONArray("runs") ?: return ""
    return buildString {
        for (index in 0 until runs.length()) append(runs.optJSONObject(index)?.optString("text").orEmpty())
    }
}
