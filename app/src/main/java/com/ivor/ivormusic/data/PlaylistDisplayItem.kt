package com.ivor.ivormusic.data

data class PlaylistDisplayItem(
    val name: String,
    val url: String,
    val uploaderName: String,
    val itemCount: Int = -1,
    val thumbnailUrl: String? = null,
    val description: String? = null
) {
    val id: String
        get() = when {
            url.contains("list=") -> url.substringAfter("list=").substringBefore("&")
            // Album pages use browse ids (MPREb…) instead of playlist ids
            url.contains("/browse/") -> url.substringAfter("/browse/").substringBefore("?")
            else -> url
        }
}
