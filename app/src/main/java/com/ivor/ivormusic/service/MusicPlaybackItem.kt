package com.ivor.ivormusic.service

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.ivor.ivormusic.data.MusicQueueItem
import com.ivor.ivormusic.data.SongSource

/** Stable identity for one occurrence of a song in the playback queue. */
internal const val EXTRA_QUEUE_ITEM_ID = "com.ivor.ivormusic.QUEUE_ITEM_ID"

/** The identity of this exact queue occurrence, when Koda created the item. */
internal val MediaItem.queueItemId: String?
    get() = mediaMetadata.extras?.getString(EXTRA_QUEUE_ITEM_ID)

/**
 * Whether two media items describe the same occurrence in the same queue.
 *
 * A media id is only a track identity. Playlists may contain that track more
 * than once, and replacing a queue rebuilds every occurrence with a fresh id.
 * Fall back to the media id only when neither item has Koda's occurrence id,
 * as happens for a pair supplied by an external controller. If only one has
 * it, treating them as equal would let an external/rebuilt item impersonate a
 * Koda queue occurrence merely because it names the same track.
 */
internal fun MediaItem.isSameQueueItemAs(other: MediaItem): Boolean {
    return isSameQueueOccurrence(
        firstQueueId = queueItemId,
        firstMediaId = mediaId,
        secondQueueId = other.queueItemId,
        secondMediaId = other.mediaId,
    )
}

/** Pure identity rule kept separate so its duplicate-entry cases stay tested. */
internal fun isSameQueueOccurrence(
    firstQueueId: String?,
    firstMediaId: String,
    secondQueueId: String?,
    secondMediaId: String,
): Boolean {
    return when {
        firstQueueId != null || secondQueueId != null ->
            firstQueueId != null && firstQueueId == secondQueueId
        else -> firstMediaId == secondMediaId
    }
}

/**
 * Build the canonical Media3 item used by both the app and service-side
 * playback resumption. Keeping this in one place prevents a restored queue
 * from losing local URIs, occurrence IDs, or artwork metadata.
 */
internal fun MusicQueueItem.toPlaybackMediaItem(): MediaItem {
    val extras = Bundle().apply {
        putString(EXTRA_QUEUE_ITEM_ID, id)
        putString(MusicService.EXTRA_SONG_SOURCE, song.source.name)
    }
    val metadata = MediaMetadata.Builder()
        .setTitle(song.title)
        .setArtist(song.artist)
        .setAlbumTitle(song.album.takeIf { it.isNotBlank() })
        .setDurationMs(song.duration.takeIf { it > 0L })
        .setArtworkUri(
            if (song.source == SongSource.LOCAL) {
                song.albumArtUri
            } else {
                (song.highResThumbnailUrl ?: song.thumbnailUrl)
                    ?.takeIf { it.isNotBlank() }
                    ?.let(android.net.Uri::parse)
            }
        )
        .setExtras(extras)
        .build()

    val builder = MediaItem.Builder()
        .setMediaId(song.id)
        .setMediaMetadata(metadata)
    if (song.source == SongSource.LOCAL && song.uri != null) {
        builder.setUri(song.uri)
    } else {
        builder.setUri("https://placeholder.ivormusic/${song.id}")
    }
    return builder.build()
}
