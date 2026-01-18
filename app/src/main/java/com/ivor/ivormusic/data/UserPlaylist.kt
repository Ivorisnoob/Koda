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
            url = id, // ID is used as 'url' which acts as ID in the app logic cause i dont wanna do the URI shi rn 
            uploaderName = "You",
            itemCount = songs.size,
            thumbnailUrl = coverUri ?: songs.firstOrNull()?.let { 
                it.highResThumbnailUrl ?: it.thumbnailUrl ?: it.albumArtUri?.toString() 
            }
        )
    }
}
