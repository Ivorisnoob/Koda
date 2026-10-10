package com.ivor.ivormusic.data.youtube

import com.ivor.ivormusic.data.ChannelBell
import com.ivor.ivormusic.data.ChannelBellParser
import com.ivor.ivormusic.data.LikeStatus
import com.ivor.ivormusic.data.VideoChapter
import com.ivor.ivormusic.data.VideoEngagement
import com.ivor.ivormusic.data.VideoItem
import com.ivor.ivormusic.data.cleanChannelName
import com.ivor.ivormusic.data.parseRichText
import com.ivor.ivormusic.util.KLog
import org.json.JSONObject

// One /next response feeds the whole watch page: title and description,
// likes and subscription state, chapters, related videos and the live chat
// token. Each function reads one of those out of the same root. Pure.

/**
 * The column of a watch page that describes the video itself: its title and
 * actions, its owner and Subscribe button, and its comments section.
 * [verified October 2026, signed out: `contents.twoColumnWatchNextResults
 * .results.results`] Related videos, the player overlay and the engagement
 * panels sit elsewhere and repeat some of the same renderers.
 */
private fun watchColumn(root: JSONObject): JSONObject? =
    root.optJSONObject("contents")
        ?.optJSONObject("twoColumnWatchNextResults")
        ?.optJSONObject("results")
        ?.optJSONObject("results")

/**
 * Enriched video metadata from a watch-next response, layered over
 * [baseVideo] (feed items lack description, channel avatar and subscriber
 * count). Shapes verified against the live API July 2026:
 * videoPrimaryInfoRenderer (title, viewCount.videoViewCountRenderer,
 * relativeDateText) and videoSecondaryInfoRenderer (owner.videoOwnerRenderer,
 * attributedDescription.content).
 */
internal fun parseVideoMetadataFromWatchNext(
    videoId: String,
    root: JSONObject,
    baseVideo: VideoItem?
): VideoItem? {
    return try {
        val column = watchColumn(root)
        val primary = firstObjectByKey(column, root, "videoPrimaryInfoRenderer")
        val secondaryInfo = firstObjectByKey(column, root, "videoSecondaryInfoRenderer")

        if (primary == null && secondaryInfo == null) return baseVideo

        val viewCountRenderer = primary?.optJSONObject("viewCount")
            ?.optJSONObject("videoViewCountRenderer")
        val viewCount = getRunText(viewCountRenderer?.optJSONObject("shortViewCount"))
            ?.takeIf { it.isNotBlank() }
            ?: getRunText(viewCountRenderer?.optJSONObject("viewCount"))?.takeIf { it.isNotBlank() }
        val uploadedDate = getRunText(primary?.optJSONObject("relativeDateText"))
            ?.takeIf { it.isNotBlank() }
            ?: getRunText(primary?.optJSONObject("dateText"))?.takeIf { it.isNotBlank() }

        val owner = secondaryInfo?.optJSONObject("owner")?.optJSONObject("videoOwnerRenderer")
        val channelId = owner?.optJSONObject("navigationEndpoint")
            ?.optJSONObject("browseEndpoint")?.optString("browseId")
            ?.takeIf { it.isNotBlank() }
        val avatarThumbs = owner?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
        val channelIconUrl = avatarThumbs
            ?.optJSONObject(avatarThumbs.length() - 1)?.optString("url")
            ?.takeIf { it.isNotBlank() }
            // A collab video has no owner thumbnail at all, only a stack of
            // every collaborator's; the first of them is the uploader.
            ?: owner?.optJSONObject("avatarStack")
                ?.optJSONObject("avatarStackViewModel")
                ?.optJSONArray("avatars")
                ?.optJSONObject(0)
                ?.optJSONObject("avatarViewModel")
                ?.optJSONObject("image")
                ?.optJSONArray("sources")
                ?.optJSONObject(0)
                ?.optString("url")
                ?.takeIf { it.isNotBlank() }
        val subscriberCount = getRunText(owner?.optJSONObject("subscriberCountText"))
            ?.takeIf { it.isNotBlank() }
        // attributedDescription carries commandRuns marking every link,
        // hashtag and timestamp with exact UTF-16 offsets, so the
        // description arrives already linkified (see parseRichText).
        val richDescription = parseRichText(
            secondaryInfo?.optJSONObject("attributedDescription")
        )
        val description = richDescription.text.takeIf { it.isNotBlank() }

        VideoItem(
            videoId = videoId,
            title = getRunText(primary?.optJSONObject("title"))?.takeIf { it.isNotBlank() }
                ?: baseVideo?.title.orEmpty(),
            channelName = getRunText(owner?.optJSONObject("title"))?.takeIf { it.isNotBlank() }
                // A collab video names no owner; its byline ("KSI and 2
                // more") is attributed text, already localized by YouTube,
                // which is why it is used rather than assembled here.
                ?: owner?.optJSONObject("attributedTitle")?.optString("content")
                    ?.takeIf { it.isNotBlank() }
                ?: cleanChannelName(baseVideo?.channelName),
            channelId = channelId ?: baseVideo?.channelId,
            channelIconUrl = channelIconUrl ?: baseVideo?.channelIconUrl,
            // Carried on the item as well as on the engagement so that a
            // surface holding only the VideoItem - the queue, the mini
            // player, a related card - can still resolve the creator.
            collaborators = parseCollaborators(owner)
                .ifEmpty { baseVideo?.collaborators.orEmpty() },
            thumbnailUrl = baseVideo?.thumbnailUrl
                ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
            duration = baseVideo?.duration ?: 0L,
            viewCount = viewCount ?: baseVideo?.viewCount ?: "",
            uploadedDate = uploadedDate ?: baseVideo?.uploadedDate,
            isLive = baseVideo?.isLive ?: false,
            description = description ?: baseVideo?.description,
            subscriberCount = subscriberCount ?: baseVideo?.subscriberCount,
            // Only valid alongside the description they were measured
            // against; a fallback description has no matching offsets.
            descriptionLinks = if (description != null) richDescription.links else emptyList()
        )
    } catch (e: Exception) {
        KLog.w(YOUTUBE_TAG, "watch-next metadata parse failed for $videoId", e)
        baseVideo
    }
}

internal fun parseEngagementFromWatchNext(
    videoId: String,
    root: JSONObject
): VideoEngagement {
        val column = watchColumn(root)
        // The like count rides inside the video's own like button.
        var likeCount = firstObjectByKey(column, root, "likeCountEntity")
            ?.optJSONObject("likeCountIfIndifferent")?.optString("content")
            ?.takeIf { it.isNotBlank() }
        if (likeCount == null) {
            // Fallback: the visible title on the like toggle button ("19M")
            val title = firstObjectByKey(column, root, "segmentedLikeDislikeButtonViewModel")
                ?.optJSONObject("likeButtonViewModel")?.optJSONObject("likeButtonViewModel")
                ?.optJSONObject("toggleButtonViewModel")?.optJSONObject("toggleButtonViewModel")
                ?.optJSONObject("defaultButtonViewModel")?.optJSONObject("buttonViewModel")
                ?.optString("title")
            // Ignore non-numeric titles like "Like" (count hidden by creator)
            likeCount = title?.takeIf { it.isNotBlank() && it.any { c -> c.isDigit() } }
        }

        // The response holds a like status per like button drawn, and they
        // all sit in one list: take the one whose key names this video.
        val likeStatuses = mutableListOf<JSONObject>()
        findObjectsByKey(root, "likeStatusEntity", likeStatuses)
        val ownLikeStatus = likeStatuses.firstOrNull { entityKeyNames(it.optString("key"), videoId) }
            ?: likeStatuses.firstOrNull()
        val likeStatus = when (ownLikeStatus?.optString("likeStatus")) {
            "LIKE" -> LikeStatus.LIKE
            "DISLIKE" -> LikeStatus.DISLIKE
            else -> LikeStatus.INDIFFERENT
        }

        val subButton = firstObjectByKey(column, root, "subscribeButtonRenderer")
        val isSubscribed = subButton?.optBoolean("subscribed", false) ?: false

        val owner = firstObjectByKey(column, root, "videoOwnerRenderer")
        val channelId = subButton?.optString("channelId")?.takeIf { it.isNotBlank() }
            ?: owner?.optJSONObject("navigationEndpoint")
                ?.optJSONObject("browseEndpoint")?.optString("browseId")
                ?.takeIf { it.isNotBlank() }
        val subscriberCountText = getRunText(owner?.optJSONObject("subscriberCountText"))

        // Comments entry token: the itemSectionRenderer tagged comment-item-section.
        // Two tokens usually appear; the longest is the full comments panel.
        var commentsToken: String? = null
        val sections = mutableListOf<JSONObject>()
        findObjectsByKey(root, "itemSectionRenderer", sections)
        for (section in sections) {
            if (section.optString("sectionIdentifier") == "comment-item-section") {
                val tokens = mutableListOf<String>()
                findContinuationTokens(section, tokens)
                val best = tokens.maxByOrNull { it.length }
                if (best != null && best.length > (commentsToken?.length ?: 0)) {
                    commentsToken = best
                }
            }
        }

        return VideoEngagement(
            videoId = videoId,
            likeCount = likeCount,
            likeStatus = likeStatus,
            channelId = channelId,
            isSubscribed = isSubscribed,
            subscriberCountText = subscriberCountText,
            commentsToken = commentsToken,
            collaborators = parseCollaborators(owner),
            bells = ChannelBellParser.fromSubscribeButtons(root).let { bells ->
                // Not every watch page has moved to the view-model button.
                val legacy = channelId?.takeIf { it !in bells }?.let { legacyBell(subButton, it) }
                if (legacy != null) bells + (legacy.channelId to legacy) else bells
            }
        )
}

/**
 * The bell inside a legacy `subscribeButtonRenderer`, at
 * `notificationPreferenceButton.subscriptionNotificationToggleButtonRenderer`.
 * [verified September 2026, signed in] Some watch pages still draw their
 * Subscribe button this way (`videoSecondaryInfoRenderer.subscribeButton`)
 * while others use `subscribeButtonViewModel`, so both are read.
 */
internal fun legacyBell(subscribeButton: JSONObject?, channelId: String): ChannelBell? {
    if (subscribeButton == null) return null
    val toggles = mutableListOf<JSONObject>()
    findObjectsByKey(subscribeButton, "subscriptionNotificationToggleButtonRenderer", toggles)
    return ChannelBellParser.fromToggle(toggles.firstOrNull(), channelId)
}

/**
 * Parse chapter markers from a watch-next response. Chapters live in the
 * decorated player bar: multiMarkersPlayerBarRenderer.markersMap, keyed
 * DESCRIPTION_CHAPTERS (creator) or AUTO_CHAPTERS, each with a list of
 * chapterRenderer { title, timeRangeStartMillis, thumbnail }. Collecting
 * chapterRenderer by key returns them in document (chronological) order;
 * we sort defensively and drop anything without a title. Verified against
 * the live /next API July 2026.
 */
internal fun parseChaptersFromWatchNext(root: JSONObject): List<VideoChapter> {
    val renderers = mutableListOf<JSONObject>()
    findObjectsByKey(root, "chapterRenderer", renderers)
    if (renderers.isEmpty()) return emptyList()

    val chapters = renderers.mapNotNull { r ->
        val title = getRunText(r.optJSONObject("title"))?.takeIf { it.isNotBlank() }
            ?: return@mapNotNull null
        val startMs = r.optLong("timeRangeStartMillis", -1L)
        if (startMs < 0L) return@mapNotNull null
        val thumbs = r.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
        val thumbnailUrl = thumbs?.optJSONObject(thumbs.length() - 1)
            ?.optString("url")?.takeIf { it.isNotBlank() }
        VideoChapter(title = title, startMs = startMs, thumbnailUrl = thumbnailUrl)
    }

    // De-duplicate by start time (the same chapter list can appear twice in
    // the tree on some responses) and keep chronological order.
    return chapters
        .distinctBy { it.startMs }
        .sortedBy { it.startMs }
}

/**
 * Related videos from a watch-next response: lockupViewModels under
 * secondaryResults (the modern shape since 2025).
 */
internal fun parseRelatedFromWatchNext(root: JSONObject): List<VideoItem> {
    val secondary = root.optJSONObject("contents")
        ?.optJSONObject("twoColumnWatchNextResults")
        ?.optJSONObject("secondaryResults") ?: return emptyList()
    val lockups = mutableListOf<JSONObject>()
    findObjectsByKey(secondary, "lockupViewModel", lockups)
    return lockups.mapNotNull { parseLockupViewModel(it) }
}

/**
 * Pull the chat start token out of an already-fetched watch-next response.
 *
 * conversationBar is absent entirely when the video is not live or the
 * creator disabled chat, which is the "no chat" signal - not an error.
 *
 * A finished broadcast that kept its chat exposes the *replay* under this
 * same key. Nothing consumes that today - the panel is gated on isLive -
 * but it is the hook if replay chat is ever wanted.
 */
internal fun parseLiveChatContinuation(root: JSONObject): String? {
    val renderer = root.optJSONObject("contents")
        ?.optJSONObject("twoColumnWatchNextResults")
        ?.optJSONObject("conversationBar")
        ?.optJSONObject("liveChatRenderer")
        ?: return null

    // Sub-menu entry 0 is "Top chat" and entry 1 "Live chat"; the top-level
    // continuation matches whichever the creator defaulted to, and is the
    // one the web player opens with.
    return renderer.optJSONArray("continuations")
        ?.optJSONObject(0)
        ?.optJSONObject("reloadContinuationData")
        ?.optString("continuation")
        ?.takeIf { it.isNotBlank() }
}
