package com.ivor.ivormusic.data

import android.net.Uri

/**
 * Represents the source of the song.
 */
enum class SongSource {
    LOCAL,
    YOUTUBE
}

/**
 * A unified Song model that supports both local and YouTube Music sources.
 */
data class Song(
    val id: String, // Changed from Long to String for YouTube video IDs
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long, // Duration in milliseconds
    val uri: Uri? = null, // Local content URI (null for YouTube songs until resolved)
    val albumArtUri: Uri? = null, // Album art URI
    val thumbnailUrl: String? = null, // YouTube thumbnail URL
    val source: SongSource = SongSource.LOCAL
) {
    val highResThumbnailUrl: String?
        get() = thumbnailUrl?.let { url ->
            when {
                url.contains("googleusercontent.com") -> {
                    // Replace size params like w120-h120 with w1080-h1080
                    url.replace(Regex("w\\d+-h\\d+"), "w1080-h1080")
                        .replace(Regex("s\\d+"), "s1080")
                }
                url.contains("ytimg.com") || url.contains("youtube.com") -> {
                    // Replace low res filenames with max res
                    url.replace("mqdefault", "maxresdefault")
                       .replace("hqdefault", "maxresdefault")
                       .replace("sddefault", "maxresdefault")
                }
                else -> url
            }
        }

    companion object {
        /**
         * Creates a Song from local MediaStore data.
         */
        fun fromLocal(
            id: Long,
            title: String,
            artist: String,
            album: String,
            duration: Long,
            uri: Uri,
            albumArtUri: Uri?
        ): Song = Song(
            id = id.toString(),
            title = title,
            artist = artist,
            album = album,
            duration = duration,
            uri = uri,
            albumArtUri = albumArtUri,
            source = SongSource.LOCAL
        )

        /**
         * Creates a Song from YouTube Music data.
         */
        fun fromYouTube(
            videoId: String,
            title: String,
            artist: String,
            album: String,
            duration: Long,
            thumbnailUrl: String?
        ): Song = Song(
            id = videoId,
            title = title,
            artist = artist,
            album = album,
            duration = duration,
            thumbnailUrl = thumbnailUrl,
            source = SongSource.YOUTUBE
        )
    }
}
