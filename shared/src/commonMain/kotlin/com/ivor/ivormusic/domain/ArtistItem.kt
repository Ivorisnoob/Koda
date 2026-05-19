package com.ivor.ivormusic.domain

data class ArtistItem(
    val id: String,
    val name: String,
    val thumbnailUrl: String? = null,
    val subscriberCount: String? = null,
    val description: String? = null,
    val isVerified: Boolean = false
)
