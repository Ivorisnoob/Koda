package com.ivor.ivormusic.service

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.ivor.ivormusic.data.MusicQueueItem
import com.ivor.ivormusic.data.SongSource

/** Stable identity for one occurrence of a song in the playback queue. */
internal const val EXTRA_QUEUE_ITEM_ID = "com.ivor.ivormusic.QUEUE_ITEM_ID"

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
