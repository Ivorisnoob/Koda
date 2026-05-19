package com.ivor.ivormusic.domain

data class VideoItem(
    val videoId: String,
    val title: String,
    val channelName: String,
    val channelId: String? = null,
    val channelIconUrl: String? = null,
    val thumbnailUrl: String?,
    val duration: Long,
    val viewCount: String,
    val uploadedDate: String? = null,
    val isLive: Boolean = false,
    val description: String? = null,
    val subscriberCount: String? = null
) {
    val highResThumbnailUrl: String?
        get() = thumbnailUrl?.let { url ->
            when {
                url.contains("ytimg.com") || url.contains("youtube.com") ->
                    url.replace("mqdefault", "maxresdefault")
                       .replace("hqdefault", "maxresdefault")
                       .replace("sddefault", "maxresdefault")
                else -> url
            }
        }

    val channelIconUrlOrDefault: String
        get() = channelIconUrl ?: "https://www.gstatic.com/youtube/img/creator/no_channel_image_hh.png"

    val formattedDuration: String
        get() {
            if (isLive) return "LIVE"
            if (duration <= 0) return ""
            val hours = duration / 3600
            val minutes = (duration % 3600) / 60
            val seconds = duration % 60
            return if (hours > 0) "$hours:${minutes.toString().padStart(2,'0')}:${seconds.toString().padStart(2,'0')}"
            else "$minutes:${seconds.toString().padStart(2,'0')}"
        }

    companion object {
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

        fun formatViewCount(count: Long?): String {
            if (count == null || count < 0) return ""
            fun fmt1(d: Double): String {
                val i = (d * 10).toLong()
                return "${i / 10}.${i % 10}"
            }
            return when {
                count >= 1_000_000_000 -> "${fmt1(count / 1_000_000_000.0)}B views"
                count >= 1_000_000 -> "${fmt1(count / 1_000_000.0)}M views"
                count >= 1_000 -> "${fmt1(count / 1_000.0)}K views"
                else -> "$count views"
            }
        }
    }
}

data class VideoQuality(
    val resolution: String,
    val url: String,
    val format: String? = null,
    val isDASH: Boolean = false,
    val audioUrl: String? = null
)

data class VideoDetails(
    val qualities: List<VideoQuality>,
    val relatedVideos: List<VideoItem>,
    val updatedVideoItem: VideoItem? = null
)
