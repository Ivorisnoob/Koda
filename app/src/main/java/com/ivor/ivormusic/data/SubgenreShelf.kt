package com.ivor.ivormusic.data

/**
 * An Explore-tab shelf: popular songs from a subgenre the listener's liked
 * artists point to (e.g. "Conscious Rap" for Kendrick / J. Cole fans).
 */
data class SubgenreShelf(
    val subgenre: String,
    val basedOn: List<String>,
    val songs: List<Song>,
)
