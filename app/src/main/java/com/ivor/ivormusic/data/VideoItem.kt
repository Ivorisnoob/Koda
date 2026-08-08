package com.ivor.ivormusic.data

/**
 * YouTube's own dismissal tokens for one feed item, when it carried any.
 *
 * These only exist on a signed-in InnerTube response - there is no account to
 * record a preference against otherwise - so every field is null for RSS,
 * NewPipe and signed-out items, and telling the account is skipped rather than
 * failed. Tokens are single-use and tied to the response that carried them,
 * which is why they ride along on the item instead of being fetched later.
 *
 * The undo tokens are pre-baked by YouTube into the same response, so taking
 * back a dismissal costs no extra round trip either. Verified August 2026.
 */
data class DismissalTokens(
    /** Hides this one video from the account's recommendations. */
    val notInterested: String? = null,
    val notInterestedUndo: String? = null,
    /** Stops the account being recommended this video's channel at all. */
    val blockChannel: String? = null,
    val blockChannelUndo: String? = null
)

/**
 * Represents a YouTube video item for Video Mode.
 * This is distinct from Song as it contains video-specific metadata.
 */
data class VideoItem(
    val videoId: String,
    val title: String,
    val channelName: String,
    val channelId: String? = null,
    val channelIconUrl: String? = null,
    val thumbnailUrl: String?,
    val duration: Long, // Duration in seconds
    val viewCount: String, // Formatted view count like "1.2M views"
    val uploadedDate: String? = null, // e.g., "2 days ago"
    val isLive: Boolean = false,
    val description: String? = null,
    val subscriberCount: String? = null,
    // Clickable spans inside [description] (links, hashtags, timestamps), as
    // marked by YouTube. Empty when the description is plain text or when the
    // item came from a feed rather than a watch-next response.
    val descriptionLinks: List<RichLink> = emptyList(),
    /**
     * Exact upload time in epoch millis, when the source knew it.
     *
     * InnerTube only ever gives [uploadedDate] as prose ("3 days ago"), which
     * is useless for ordering: merging fifteen channels' uploads into one
     * chronological feed needs a real timestamp, and "1 day ago" covers a
     * 24-hour spread that would shuffle the top of the list arbitrarily. The
     * channel RSS feed carries a proper ISO timestamp, so the local
     * subscriptions feed is built from that and sorts on this field.
     */
    val publishedAtMs: Long? = null,
    /**
     * YouTube's dismissal tokens for this item, when the response carried
     * them. Null everywhere except signed-in InnerTube feeds.
     */
    val dismissal: DismissalTokens? = null
) {
    /**
     * High-resolution thumbnail URL.
     */
    val highResThumbnailUrl: String?
        get() = thumbnailUrl?.let { url ->
            when {
                url.contains("ytimg.com") || url.contains("youtube.com") -> {
                    url.replace("mqdefault", "maxresdefault")
                       .replace("hqdefault", "maxresdefault")
                       .replace("sddefault", "maxresdefault")
                }
                else -> url
            }
        }
    
    /**
     * Get channel icon or default fallback URL.
     */
    val channelIconUrlOrDefault: String
        get() = channelIconUrl ?: "https://www.gstatic.com/youtube/img/creator/no_channel_image_hh.png"

    /**
     * Formatted duration string (e.g., "3:45" or "1:23:45").
     */
    val formattedDuration: String
        get() {
            if (isLive) return "LIVE"
            if (duration <= 0) return ""
            val hours = duration / 3600
            val minutes = (duration % 3600) / 60
            val seconds = duration % 60
            return if (hours > 0) {
                String.format("%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%d:%02d", minutes, seconds)
            }
        }

    companion object {
        /**
         * Creates a VideoItem from NewPipe StreamInfoItem data.
         */
        fun fromStreamInfoItem(
            videoId: String,
            title: String,
            channelName: String,
            channelId: String? = null,
            channelIconUrl: String? = null,
            thumbnailUrl: String?,
            durationSeconds: Long,
            viewCount: Long?,
            uploadedDate: String? = null,
            isLive: Boolean = false,
            description: String? = null,
            subscriberCount: String? = null
        ): VideoItem = VideoItem(
            videoId = videoId,
            title = title,
            channelName = channelName,
            channelId = channelId,
            channelIconUrl = channelIconUrl,
            thumbnailUrl = thumbnailUrl,
            duration = durationSeconds,
            viewCount = formatViewCount(viewCount),
            uploadedDate = uploadedDate,
            isLive = isLive,
            description = description,
            subscriberCount = subscriberCount
        )

        /**
         * "3 days ago" from an absolute timestamp, matching the phrasing
         * InnerTube uses so RSS-sourced cards read the same as feed cards.
         */
        fun formatRelativeTime(publishedAtMs: Long, nowMs: Long = System.currentTimeMillis()): String {
            val seconds = ((nowMs - publishedAtMs) / 1000).coerceAtLeast(0)
            val units = listOf(
                31_536_000L to "year",
                2_592_000L to "month",
                604_800L to "week",
                86_400L to "day",
                3_600L to "hour",
                60L to "minute"
            )
            for ((size, label) in units) {
                if (seconds >= size) {
                    val value = seconds / size
                    return "$value $label${if (value == 1L) "" else "s"} ago"
                }
            }
            return "just now"
        }

        /**
         * The inverse of [formatRelativeTime], for ordering feed items whose
         * source only gave prose. Deliberately coarse - "3 weeks ago" covers a
         * seven-day spread - so it is only ever used as a fallback when no
         * exact timestamp is available.
         */
        fun parseRelativeTime(text: String?, nowMs: Long = System.currentTimeMillis()): Long? {
            if (text.isNullOrBlank()) return null
            val match = RELATIVE_TIME.find(text) ?: return null
            val value = match.groupValues[1].toLongOrNull() ?: return null
            val unitSeconds = when (match.groupValues[2].lowercase()) {
                "second" -> 1L
                "minute" -> 60L
                "hour" -> 3_600L
                "day" -> 86_400L
                "week" -> 604_800L
                "month" -> 2_592_000L
                "year" -> 31_536_000L
                else -> return null
            }
            return nowMs - value * unitSeconds * 1000
        }

        private val RELATIVE_TIME =
            Regex("""(\d+)\s+(second|minute|hour|day|week|month|year)s?\s+ago""", RegexOption.IGNORE_CASE)

        /**
         * Formats view count to human-readable format.
         */
        fun formatViewCount(count: Long?): String {
            if (count == null || count < 0) return ""
            return when {
                count >= 1_000_000_000 -> String.format("%.1fB views", count / 1_000_000_000.0)
                count >= 1_000_000 -> String.format("%.1fM views", count / 1_000_000.0)
                count >= 1_000 -> String.format("%.1fK views", count / 1_000.0)
                else -> "$count views"
            }
        }
    }
}

/**
 * Represents a video stream quality option.
 */
data class VideoQuality(
    val resolution: String, // e.g. "1080p", "720p"
    val url: String,
    val format: String? = null, // e.g. "mp4", "webm"
    val isDASH: Boolean = false,
    val audioUrl: String? = null, // For non-DASH adaptive streams
    /**
     * Set on entries resolved from a live broadcast. Live streams only ever
     * play through the adaptive manifest - their progressive URLs are segment
     * endpoints, not byte-addressable files - so this doubles as the player's
     * "this video is live" signal.
     */
    val isLive: Boolean = false,
    /**
     * Width over height of the source frame, read off the stream dimensions at
     * parse time. Null when no format declared both dimensions.
     *
     * The player also learns this from ExoPlayer's onVideoSizeChanged, but only
     * once the first frame has decoded - too late for any layout that sizes
     * itself from the shape of the video, which would otherwise compose as a
     * 16:9 box and visibly snap a moment after playback starts. That is the
     * vertical live player, and it is also the watch page, whose video box
     * follows the source aspect ratio rather than assuming 16:9.
     *
     * The exact ratio matters and a portrait flag is not enough: "vertical"
     * covers 9:16, 4:5 and 1:1, and a box sized for one of those is wrong for
     * the other two.
     */
    val sourceAspectRatio: Float? = null
) {
    /** Taller than it is wide. Unknown dimensions read as landscape. */
    val isPortrait: Boolean get() = sourceAspectRatio?.let { it > 0f && it < 1f } ?: false
}

/**
 * Complete video details including qualities and related videos.
 */
data class VideoDetails(
    val qualities: List<VideoQuality>,
    val relatedVideos: List<VideoItem>,
    val updatedVideoItem: VideoItem? = null
)

/**
 * A single chapter marker in a video, parsed from the watch-next player bar
 * (multiMarkersPlayerBarRenderer -> markersMap -> chapterRenderer).
 */
data class VideoChapter(
    val title: String,
    val startMs: Long,
    val thumbnailUrl: String? = null
)

/**
 * Everything the video player needs from a single watch-next (/next) call:
 * engagement state, enriched metadata, related videos and chapters.
 */
data class WatchNextData(
    val engagement: VideoEngagement?,
    val updatedVideoItem: VideoItem?,
    val relatedVideos: List<VideoItem>,
    val chapters: List<VideoChapter> = emptyList(),
    /**
     * Live chat start token, when the video is a broadcast with chat enabled.
     * It rides this response so opening the chat panel costs no extra /next -
     * the watch-next tree is a multi-megabyte parse and re-fetching it was the
     * single biggest cost of opening chat.
     */
    val liveChatContinuation: String? = null
)

/**
 * A caption/subtitle track for a video, parsed from the /player response
 * (captions.playerCaptionsTracklistRenderer.captionTracks). [baseUrl] is the
 * timedtext endpoint, which serves whichever format its "fmt" parameter asks
 * for; [vttUrl] pins it to WebVTT.
 */
data class CaptionTrack(
    val languageCode: String,
    val name: String,
    val baseUrl: String,
    val isAutoGenerated: Boolean = false
) {
    /**
     * WebVTT variant of [baseUrl], which ExoPlayer can parse directly.
     *
     * The existing "fmt" must be *replaced*, not appended to: the ANDROID_VR
     * client hands back baseUrls that already carry "&fmt=srv3", and timedtext
     * honors the first occurrence, so appending "&fmt=vtt" silently returns
     * srv3 XML under a text/vtt MIME type and the subtitle load fails. Same
     * normalization NewPipe's YoutubeStreamExtractor applies (it also drops
     * "&tlang=", which would otherwise force a machine translation).
     */
    val vttUrl: String
        get() = baseUrl
            .replace(FMT_PARAM, "")
            .replace(TLANG_PARAM, "") + "&fmt=vtt"

    private companion object {
        val FMT_PARAM = Regex("&fmt=[^&]*")
        val TLANG_PARAM = Regex("&tlang=[^&]*")
    }
}

/**
 * One page of the video home feed. [continuation] is the InnerTube token for
 * the next page (browse continuation), or null when the source can't page
 * that way (taste-based and cold-start feeds page by seed offset instead).
 */
data class VideoFeedPage(
    val videos: List<VideoItem>,
    val continuation: String? = null
)

/**
 * A YouTube playlist shown in the video Library tab. Watch Later ("WL") and
 * Liked videos ("LL") are pinned entries built locally, not parsed.
 */
data class VideoPlaylist(
    val playlistId: String,
    val title: String,
    val thumbnailUrl: String? = null,
    val videoCountText: String? = null, // e.g. "28 videos"
    val subtitle: String? = null // e.g. "Private"
)
