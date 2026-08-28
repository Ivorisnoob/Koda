package com.ivor.ivormusic.ui.library

import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.ui.components.queueRowKeys

/**
 * One concrete occurrence in a playlist.
 *
 * A video id is not row identity: YouTube and local playlists may contain the
 * same song more than once. Keeping the generated key with the row means it
 * survives drag reordering and lets Compose, local edits and InnerTube's
 * per-row setVideoId all address the same occurrence.
 */
internal data class PlaylistSongRow(
    val key: String,
    val song: Song,
    val setVideoId: String? = null
)

internal fun playlistSongRows(
    songs: List<Song>,
    keyPrefix: String
): List<PlaylistSongRow> {
    val keys = queueRowKeys(songs.map { it.id }, keyPrefix)
    return songs.mapIndexed { index, song -> PlaylistSongRow(keys[index], song) }
}

/** Attach occurrence-ordered InnerTube row ids without collapsing duplicates. */
internal fun attachPlaylistSetVideoIds(
    rows: List<PlaylistSongRow>,
    idsByVideo: Map<String, List<String>>
): List<PlaylistSongRow> {
    val occurrences = mutableMapOf<String, Int>()
    return rows.map { row ->
        val videoId = row.song.id
        val occurrence = occurrences.getOrDefault(videoId, 0)
        occurrences[videoId] = occurrence + 1
        row.copy(setVideoId = idsByVideo[videoId]?.getOrNull(occurrence))
    }
}
