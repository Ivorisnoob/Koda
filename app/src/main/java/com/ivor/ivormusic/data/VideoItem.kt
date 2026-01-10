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
    val subscriberCount: String? = null
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
        private fun formatViewCount(count: Long?): String {
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
    val audioUrl: String? = null // For non-DASH adaptive streams
)

/**
 * Complete video details including qualities and related videos.
 */
data class VideoDetails(
    val qualities: List<VideoQuality>,
    val relatedVideos: List<VideoItem>
)
