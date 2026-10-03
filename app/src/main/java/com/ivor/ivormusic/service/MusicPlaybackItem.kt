package com.ivor.ivormusic.service

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.ivor.ivormusic.data.MusicQueueItem
import com.ivor.ivormusic.data.MusicReleaseType
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.data.SongSource

/** Stable identity for one occurrence of a song in the playback queue. */
internal const val EXTRA_QUEUE_ITEM_ID = "com.ivor.ivormusic.QUEUE_ITEM_ID"
internal const val EXTRA_MUSIC_ALBUM_ID = "com.ivor.ivormusic.MUSIC_ALBUM_ID"
internal const val EXTRA_MUSIC_RELEASE_TYPE = "com.ivor.ivormusic.MUSIC_RELEASE_TYPE"
internal const val EXTRA_MUSIC_IS_UPLOAD = "com.ivor.ivormusic.MUSIC_IS_UPLOAD"

/**
 * Song fields the player itself has no slot for, carried so the service can
 * write a queue back out as the songs it was built from (see
 * [toMusicQueueItem]). The thumbnail is the original URL: the artwork URI is
 * the high-resolution variant, which YouTube does not serve for every video.
 */
internal const val EXTRA_SONG_THUMBNAIL_URL = "com.ivor.ivormusic.SONG_THUMBNAIL_URL"
internal const val EXTRA_SONG_FILE_PATH = "com.ivor.ivormusic.SONG_FILE_PATH"
internal const val EXTRA_SONG_LYRICS_URI = "com.ivor.ivormusic.SONG_LYRICS_URI"

/** A plain YouTube upload played as a song; see [com.ivor.ivormusic.data.Song.isUpload]. */
internal val MediaItem.isUpload: Boolean
    get() = mediaMetadata.extras?.getBoolean(EXTRA_MUSIC_IS_UPLOAD, false) == true

/** The identity of this exact queue occurrence, when Koda created the item. */
internal val MediaItem.queueItemId: String?
    get() = mediaMetadata.extras?.getString(EXTRA_QUEUE_ITEM_ID)

/** Preserve an identifiable album sequence; matching titles alone are not enough. */
internal fun MediaItem.isAlbumSuccessorOf(previous: MediaItem): Boolean {
    val before = previous.mediaMetadata
    val after = mediaMetadata
    val album = before.albumTitle?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: return false
    val artist = (before.albumArtist ?: before.artist)?.toString()?.trim()
        ?.takeIf { it.isNotEmpty() } ?: return false
    val track = before.trackNumber?.takeIf { it > 0 } ?: return false
    return album == after.albumTitle?.toString()?.trim() &&
        artist == (after.albumArtist ?: after.artist)?.toString()?.trim() &&
        (before.discNumber ?: 1) == (after.discNumber ?: 1) &&
        after.trackNumber == track + 1
}

/**
 * Whether two media items describe the same occurrence in the same queue.
 *
 * A media id is only a track identity. Playlists may contain that track more
 * than once, and replacing a queue rebuilds every occurrence with a fresh id.
 * Fall back to the media id only when neither item has Koda's occurrence id,
 * for legacy/raw items. Controller ingress assigns an id before insertion,
 * so external duplicate rows do not use that fallback. If only one has it,
 * treating them as equal would let an external/rebuilt item impersonate a
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
        song.albumId?.let { putString(EXTRA_MUSIC_ALBUM_ID, it) }
        song.releaseType?.let { putString(EXTRA_MUSIC_RELEASE_TYPE, it.name) }
        if (song.isUpload) putBoolean(EXTRA_MUSIC_IS_UPLOAD, true)
        song.thumbnailUrl?.let { putString(EXTRA_SONG_THUMBNAIL_URL, it) }
        song.filePath?.let { putString(EXTRA_SONG_FILE_PATH, it) }
        song.lyricsUri?.let { putString(EXTRA_SONG_LYRICS_URI, it.toString()) }
    }
    val metadata = MediaMetadata.Builder()
        .setTitle(song.title)
        .setArtist(song.artist)
        .setAlbumTitle(song.album.takeIf { it.isNotBlank() })
        .setTrackNumber(song.trackNumber)
        .setDiscNumber(song.discNumber)
        .setReleaseYear(song.releaseYear)
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

/**
 * The song a queue row was built from, read back off the row itself.
 *
 * The inverse of [toPlaybackMediaItem], so the service - which only ever holds
 * `MediaItem`s - can save the queue without the app's screen being alive. A
 * row from an external controller carries none of Koda's extras and falls back
 * to what the metadata says. Null only for a row with no media id.
 */
internal fun MediaItem.toQueueSong(): Song? {
    if (mediaId.isEmpty()) return null
    val metadata = mediaMetadata
    val extras = metadata.extras
    val localUri = localConfiguration?.uri?.takeIf { it.scheme == "content" || it.scheme == "file" }
    val source = extras?.getString(MusicService.EXTRA_SONG_SOURCE)
        ?.let { name -> SongSource.entries.firstOrNull { it.name == name } }
        ?: if (localUri != null) SongSource.LOCAL else SongSource.YOUTUBE
    val isLocal = source == SongSource.LOCAL
    return Song(
        id = mediaId,
        title = metadata.title?.toString() ?: "Unknown",
        artist = metadata.artist?.toString() ?: "Unknown Artist",
        album = metadata.albumTitle?.toString() ?: "",
        duration = metadata.durationMs ?: 0L,
        uri = if (isLocal) localUri else null,
        albumArtUri = if (isLocal) metadata.artworkUri else null,
        thumbnailUrl = if (isLocal) null else {
            extras?.getString(EXTRA_SONG_THUMBNAIL_URL) ?: metadata.artworkUri?.toString()
        },
        source = source,
        filePath = extras?.getString(EXTRA_SONG_FILE_PATH),
        lyricsUri = extras?.getString(EXTRA_SONG_LYRICS_URI)?.let(Uri::parse),
        trackNumber = metadata.trackNumber,
        discNumber = metadata.discNumber,
        albumId = extras?.getString(EXTRA_MUSIC_ALBUM_ID),
        releaseYear = metadata.releaseYear,
        releaseType = extras?.getString(EXTRA_MUSIC_RELEASE_TYPE)
            ?.let { name -> MusicReleaseType.entries.firstOrNull { it.name == name } },
        isUpload = extras?.getBoolean(EXTRA_MUSIC_IS_UPLOAD, false) == true,
    )
}

/** This row as a saved queue entry, keeping its occurrence id. */
internal fun MediaItem.toMusicQueueItem(): MusicQueueItem? {
    val song = toQueueSong() ?: return null
    return queueItemId?.let { MusicQueueItem(id = it, song = song) } ?: MusicQueueItem(song = song)
}
