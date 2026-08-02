package com.ivor.ivormusic.data

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
    val descriptionLinks: List<RichLink> = emptyList()
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
    val isLive: Boolean = false
)

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
