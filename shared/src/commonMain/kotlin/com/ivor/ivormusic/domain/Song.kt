package com.ivor.ivormusic.domain

import kotlinx.serialization.Serializable

@Serializable
enum class SongSource {
    LOCAL,
    YOUTUBE
}

@Serializable
data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val uri: String? = null,
    val albumArtUri: String? = null,
    val thumbnailUrl: String? = null,
    val source: SongSource = SongSource.LOCAL,
    val filePath: String? = null
) {
    val highResThumbnailUrl: String?
        get() = thumbnailUrl?.let { url ->
            when {
                url.contains("googleusercontent.com") ->
                    url.replace(Regex("w\\d+-h\\d+"), "w1080-h1080")
                       .replace(Regex("s\\d+"), "s1080")
                url.contains("ytimg.com") || url.contains("youtube.com") ->
                    url.replace("mqdefault", "maxresdefault")
                       .replace("hqdefault", "maxresdefault")
                       .replace("sddefault", "maxresdefault")
                else -> url
            }
        }

    val artworkUrl: String?
        get() = highResThumbnailUrl ?: thumbnailUrl ?: albumArtUri

    companion object {
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

        fun fromLocal(
            id: Long,
            title: String,
            artist: String,
            album: String,
            duration: Long,
            uri: String,
            albumArtUri: String?,
            filePath: String? = null
        ): Song = Song(
            id = id.toString(),
            title = title,
            artist = artist,
            album = album,
            duration = duration,
            uri = uri,
            albumArtUri = albumArtUri,
            source = SongSource.LOCAL,
            filePath = filePath
        )
    }
}
