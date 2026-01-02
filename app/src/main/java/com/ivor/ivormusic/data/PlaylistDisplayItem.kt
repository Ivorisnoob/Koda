package com.ivor.ivormusic.data

data class PlaylistDisplayItem(
    val name: String,
    val url: String,
    val uploaderName: String,
    val itemCount: Int = -1,
    val thumbnailUrl: String? = null
)
