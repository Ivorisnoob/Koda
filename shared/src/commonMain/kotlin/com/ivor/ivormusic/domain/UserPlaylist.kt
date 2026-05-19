package com.ivor.ivormusic.domain

import kotlinx.serialization.Serializable

@Serializable
data class UserPlaylist(
    val id: String,
    val name: String,
    val description: String? = null,
    val coverUri: String? = null,
    val createdAt: Long = 0L,
    val songs: List<Song> = emptyList()
) {
    fun toDisplayItem(): PlaylistDisplayItem = PlaylistDisplayItem(
        name = name,
        url = id,
        uploaderName = "You",
        itemCount = songs.size,
        thumbnailUrl = coverUri ?: songs.firstOrNull()?.artworkUrl,
        description = description
    )
}
