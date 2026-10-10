package com.ivor.ivormusic.data.youtube

import com.ivor.ivormusic.data.ChannelPollChoice
import com.ivor.ivormusic.data.ChannelPost
import com.ivor.ivormusic.data.RichLink
import com.ivor.ivormusic.data.RichLinkTarget
import com.ivor.ivormusic.data.RichText
import org.json.JSONObject

// Community posts (backstagePostRenderer) and the rich text inside them. Pure.

/** The browse id of a community post's own page. */
internal const val POST_DETAIL_BROWSE_ID = "FEpost_detail"

/**
 * A community post, with whichever single attachment it carried.
 *
 * `contentText` is a legacy run list rather than the attributed-text shape
 * [parseRichText] takes, so the runs are folded into the same [RichText]
 * here - a post is full of links to videos and channels, and flattening it
 * to a String would throw all of them away.
 */
internal fun parseBackstagePost(renderer: JSONObject): ChannelPost? {
    val postId = renderer.optString("postId").takeIf { it.isNotBlank() } ?: return null
    val authorName = getRunText(renderer.optJSONObject("authorText")).orEmpty()
    val authorAvatarUrl = bestThumbnail(
        renderer.optJSONObject("authorThumbnail")?.optJSONArray("thumbnails")
    )

    val text = parseRunListAsRichText(renderer.optJSONObject("contentText"))

    val attachment = renderer.optJSONObject("backstageAttachment")
    val images = mutableListOf<String>()
    attachment?.optJSONObject("backstageImageRenderer")
        ?.optJSONObject("image")?.optJSONArray("thumbnails")
        ?.let { bestThumbnail(it) }
        ?.let { images.add(it) }
    attachment?.optJSONObject("postMultiImageRenderer")?.optJSONArray("images")
        ?.let { array ->
            for (i in 0 until array.length()) {
                array.optJSONObject(i)?.optJSONObject("backstageImageRenderer")
                    ?.optJSONObject("image")?.optJSONArray("thumbnails")
                    ?.let { bestThumbnail(it) }
                    ?.let { images.add(it) }
            }
        }

    val video = attachment?.optJSONObject("videoRenderer")?.let { parseVideoRenderer(it) }

    val poll = attachment?.optJSONObject("pollRenderer")
    val pollChoices = poll?.optJSONArray("choices")?.let { array ->
        (0 until array.length()).mapNotNull { i ->
            val choice = array.optJSONObject(i) ?: return@mapNotNull null
            val label = getRunText(choice.optJSONObject("text"))
                ?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            ChannelPollChoice(
                text = label,
                imageUrl = bestThumbnail(
                    choice.optJSONObject("image")?.optJSONArray("thumbnails")
                )
            )
        }
    }.orEmpty()

    // The reply button and the timestamp both link to the post's own page
    // [verified September 2026]; the reply button is the one a comments-off
    // post may lack, so the timestamp is the backstop.
    val replyButton = renderer.optJSONObject("actionButtons")
        ?.optJSONObject("commentActionButtonsRenderer")
        ?.optJSONObject("replyButton")
        ?.optJSONObject("buttonRenderer")
    val detailParams = listOfNotNull(
        replyButton?.optJSONObject("navigationEndpoint"),
        renderer.optJSONObject("publishedTimeText")?.optJSONArray("runs")
            ?.optJSONObject(0)?.optJSONObject("navigationEndpoint")
    ).firstNotNullOfOrNull { endpoint ->
        endpoint.optJSONObject("browseEndpoint")
            ?.takeIf { it.optString("browseId") == POST_DETAIL_BROWSE_ID }
            ?.optString("params")?.takeIf { it.isNotBlank() }
    }

    return ChannelPost(
        postId = postId,
        authorName = authorName,
        authorAvatarUrl = authorAvatarUrl,
        text = text,
        detailParams = detailParams,
        publishedText = getRunText(renderer.optJSONObject("publishedTimeText")),
        voteCountText = renderer.optJSONObject("voteCount")?.optString("simpleText")
            ?.takeIf { it.isNotBlank() },
        replyCountText = replyButton?.let { getRunText(it.optJSONObject("text")) },
        images = images,
        video = video,
        pollChoices = pollChoices,
        pollTotalText = getRunText(poll?.optJSONObject("totalVotes"))
    )
}

/**
 * A legacy `{runs: [...]}` text object as [RichText], keeping the links.
 *
 * Offsets are accumulated as the runs are concatenated, which is the same
 * UTF-16 indexing [parseRichText] documents - Kotlin's String indices are
 * UTF-16 code units, so an emoji in a post does not shift the spans.
 */
private fun parseRunListAsRichText(node: JSONObject?): RichText {
    val runs = node?.optJSONArray("runs")
        ?: return RichText(node?.optString("simpleText").orEmpty())
    val builder = StringBuilder()
    val links = mutableListOf<RichLink>()
    for (i in 0 until runs.length()) {
        val run = runs.optJSONObject(i) ?: continue
        val text = run.optString("text")
        if (text.isEmpty()) continue
        val start = builder.length
        builder.append(text)
        val command = run.optJSONObject("navigationEndpoint") ?: continue
        val target = parseRunLinkTarget(command) ?: continue
        var end = builder.length
        while (end > start && builder[end - 1].isWhitespace()) end--
        if (end > start) links.add(RichLink(start, end, target))
    }
    return RichText(builder.toString(), links)
}

private fun parseRunLinkTarget(command: JSONObject): RichLinkTarget? {
    command.optJSONObject("watchEndpoint")?.optString("videoId")
        ?.takeIf { it.isNotBlank() }
        ?.let { return RichLinkTarget.Url("https://www.youtube.com/watch?v=$it") }
    command.optJSONObject("urlEndpoint")?.optString("url")
        ?.takeIf { it.isNotBlank() }
        ?.let { return RichLinkTarget.Url(unwrapYouTubeRedirect(it)) }
    command.optJSONObject("browseEndpoint")?.optString("browseId")
        ?.takeIf { it.isNotBlank() }
        ?.let { return RichLinkTarget.Browse(it) }
    return null
}
