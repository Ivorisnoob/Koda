package com.ivor.ivormusic.data

import android.net.Uri
import kotlinx.serialization.Serializable

/**
 * Represents the source of the song.
 */
@Serializable
enum class SongSource {
    LOCAL,
    YOUTUBE
}

/**
 * A unified Song model that supports both local and YouTube Music sources.
 */
@Serializable
data class Song(
    val id: String, // Changed from Long to String for YouTube video IDs
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long, // Duration in milliseconds
    @Serializable(with = UriAsStringSerializer::class)
    val uri: Uri? = null, // Local content URI (null for YouTube songs until resolved)
    @Serializable(with = UriAsStringSerializer::class)
    val albumArtUri: Uri? = null, // Album art URI
    val thumbnailUrl: String? = null, // YouTube thumbnail URL
    val source: SongSource = SongSource.LOCAL,
    val filePath: String? = null, // Local file path for folder filtering and embedded lyrics
    @Serializable(with = UriAsStringSerializer::class)
    val lyricsUri: Uri? = null, // Downloaded LRC companion in shared storage
    // When this song entered the user's library, epoch millis. Each source
    // stamps its own notion of "added": MediaStore DATE_ADDED for device
    // files, download completion time for downloads, like time for likes.
    // Null means unknown (sorts last), never zero.
    val dateAdded: Long? = null
) {
    val highResThumbnailUrl: String?
        get() = thumbnailUrl?.let { url ->
            when {
                url.contains("googleusercontent.com") -> {
                    // Size directives (w120-h120, s120) only live after the '='
                    // separator; the opaque image token before it can itself
                    // contain "s<digits>" runs, so an unanchored replace over
                    // the whole URL corrupts it into a permanent 404.
                    val sep = url.lastIndexOf('=')
                    if (sep >= 0) {
                        url.substring(0, sep) + url.substring(sep)
                            .replace(Regex("w\\d+-h\\d+"), "w1080-h1080")
                            .replace(Regex("s\\d+"), "s1080")
                    } else url
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
            albumArtUri: Uri?,
            filePath: String? = null,
            dateAdded: Long? = null
        ): Song = Song(
            id = id.toString(),
            title = title,
            artist = artist,
            album = album,
            duration = duration,
            uri = uri,
            albumArtUri = albumArtUri,
            source = SongSource.LOCAL,
            filePath = filePath,
            dateAdded = dateAdded
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
