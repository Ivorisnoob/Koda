package com.ivor.ivormusic.data

/**
 * Represents an Artist or Channel from YouTube Music search results.
 */
data class ArtistItem(
    val id: String, // Channel ID / Browse ID
    val name: String,
    val thumbnailUrl: String? = null,
    val subscriberCount: String? = null,
    val description: String? = null,
    val isVerified: Boolean = false
)
