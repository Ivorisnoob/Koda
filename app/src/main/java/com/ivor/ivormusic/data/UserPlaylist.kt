package com.ivor.ivormusic.data

import kotlinx.serialization.Serializable

@Serializable
data class UserPlaylist(
    val id: String,
    val name: String,
    val description: String? = null,
    val coverUri: String? = null, // Custom cover art URI (file:// or content://)
    val createdAt: Long = System.currentTimeMillis(),
    val songs: List<Song> = emptyList()
) {
    // Helper to convert to PlaylistDisplayItem for UI consistency
    fun toDisplayItem(): PlaylistDisplayItem {
        return PlaylistDisplayItem(
            name = name,
            // PlaylistDisplayItem.url is the identity field throughout the UI,
            // and for a local playlist the identity is this id. It is not a URI
            // and nothing parses it as one - remote playlists put a real URL
            // here, local ones put a bare id, and every consumer treats the
            // field as an opaque key.
            url = id,
            uploaderName = "You",
            itemCount = songs.size,
            thumbnailUrl = coverUri ?: songs.firstOrNull()?.let { 
                it.highResThumbnailUrl ?: it.thumbnailUrl ?: it.albumArtUri?.toString() 
            },
            description = description
        )
    }
}
