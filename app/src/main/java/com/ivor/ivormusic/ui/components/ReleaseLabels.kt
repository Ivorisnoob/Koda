package com.ivor.ivormusic.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ivor.ivormusic.data.MusicReleaseType
import com.ivor.ivormusic.data.PlaylistDisplayItem

/** How every release caption in the app joins its parts. */
private const val SEPARATOR = " • "

/**
 * The visible caption for a release's kind and year, e.g. "EP • 2019".
 *
 * This lives in the UI layer rather than on [PlaylistDisplayItem] because a
 * release type's caption is a string resource, and only a composable can
 * resolve one. The data class carried the English word itself for a while,
 * which put an untranslated "Single" on the album header of an app whose other
 * nouns are translated into twenty-five languages.
 *
 * Returns an empty string when the source supplied neither, which is normal:
 * only album endpoints carry a type and a year.
 */
@Composable
fun releaseCaption(type: MusicReleaseType?, year: Int?): String = listOfNotNull(
    type?.let { stringResource(it.labelRes) },
    year?.toString()
).joinToString(SEPARATOR)

/**
 * The card subtitle for a playlist or release: its author, then whatever
 * release metadata exists. Blank parts drop out rather than leaving a stray
 * separator, and the whole thing can be blank - callers supply their own
 * fallback, because "Playlist" is right on a library card and wrong on a
 * search result that already says so.
 */
@Composable
fun PlaylistDisplayItem.displaySubtitle(): String {
    val caption = releaseCaption(releaseType, releaseYear)
    return listOf(uploaderName, caption).filter { it.isNotBlank() }.joinToString(SEPARATOR)
}

/** True when this item has release metadata worth showing at all. */
val PlaylistDisplayItem.hasReleaseMetadata: Boolean
    get() = releaseType != null || releaseYear != null
