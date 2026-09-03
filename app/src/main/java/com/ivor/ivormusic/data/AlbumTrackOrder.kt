package com.ivor.ivormusic.data

import java.util.Locale

internal data class AlbumPosition(
    val track: Int?,
    val disc: Int?
)

/**
 * Decode album position from both generations of Android audio metadata.
 *
 * MediaStore's legacy TRACK integer encodes disc 2 track 3 as 2003, while
 * CD_TRACK_NUMBER and DISC_NUMBER retain tags such as `3/12` and `2/2`.
 * Prefer the explicit tags and keep the encoded value as the compatibility
 * path for providers which expose only the original column.
 */
internal fun albumPosition(
    encodedTrack: Int?,
    cdTrackNumber: String?,
    discNumber: String?
): AlbumPosition {
    val encoded = encodedTrack?.takeIf { it > 0 }
    val encodedDisc = encoded?.takeIf { it >= 1000 }?.div(1000)?.takeIf { it > 0 }
    val encodedTrackNumber = encoded
        ?.let { if (it >= 1000) it % 1000 else it }
        ?.takeIf { it > 0 }

    return AlbumPosition(
        track = positiveOrdinal(cdTrackNumber) ?: encodedTrackNumber,
        disc = positiveOrdinal(discNumber) ?: encodedDisc
    )
}

private fun positiveOrdinal(value: String?): Int? = value
    ?.trim()
    ?.takeWhile { it.isDigit() }
    ?.toIntOrNull()
    ?.takeIf { it > 0 }

/** Album playback order: tagged tracks first, then a stable title/id fallback. */
fun List<Song>.sortedInAlbumOrder(): List<Song> = sortedWith(
    compareBy<Song>(
        { if (it.trackNumber == null) 1 else 0 },
        { it.discNumber ?: 1 },
        { it.trackNumber ?: Int.MAX_VALUE },
        { it.title.lowercase(Locale.ROOT) },
        { it.id }
    )
)
