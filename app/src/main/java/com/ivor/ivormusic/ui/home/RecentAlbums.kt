package com.ivor.ivormusic.ui.home

import com.ivor.ivormusic.data.PlayHistoryEntry
import com.ivor.ivormusic.data.SongSource
import com.ivor.ivormusic.data.isUnknownAlbum
import com.ivor.ivormusic.data.isUnknownArtist

/**
 * An album the user has been listening to, recovered from the play history.
 *
 * [albumId] is the streaming release (`MPREb...`) when the play recorded one;
 * older plays predate that field, so a streaming album without it is resolved
 * from [songId] when opened. A device album has no id at all and opens as the
 * library's tracks under the same album name.
 */
data class RecentAlbum(
    val title: String,
    val artist: String,
    val artwork: String?,
    val albumId: String?,
    val songId: String,
    val source: SongSource,
    /** One play of every distinct song heard from it, newest first: the fallback queue. */
    val tracks: List<PlayHistoryEntry>,
)

/**
 * The albums behind [history] (newest first), in the order they were last
 * played. One card per album, keyed by release id when there is one and by
 * album plus artist otherwise, so the same record heard across old and new
 * plays still collapses into one card once any play carries its id.
 *
 * A play with no real album - a plain upload, an untagged file - contributes
 * nothing: an "album" named after nothing is not something to go back to.
 */
fun recentAlbumsFrom(history: List<PlayHistoryEntry>, limit: Int = 12): List<RecentAlbum> {
    // First pass: which name+artist pairs have a release id anywhere, so an
    // old id-less play folds into the card its newer plays created.
    val idByName = HashMap<String, String>()
    for (entry in history) {
        val id = entry.albumId?.takeIf { it.isNotBlank() } ?: continue
        idByName.putIfAbsent(nameKey(entry), id)
    }
    val albums = LinkedHashMap<String, RecentAlbum>()
    for (entry in history) {
        if (isUnknownAlbum(entry.album)) continue
        val id = entry.albumId?.takeIf { it.isNotBlank() } ?: idByName[nameKey(entry)]
        val key = id ?: nameKey(entry)
        val existing = albums[key]
        if (existing == null) {
            if (albums.size >= limit) continue
            albums[key] = RecentAlbum(
                title = entry.album.trim(),
                artist = entry.artist.takeIf { !isUnknownArtist(it) }.orEmpty(),
                artwork = entry.thumbnailUrl,
                albumId = id,
                songId = entry.songId,
                source = entry.source,
                tracks = listOf(entry),
            )
        } else if (existing.tracks.none { it.songId == entry.songId }) {
            albums[key] = existing.copy(tracks = existing.tracks + entry)
        }
    }
    return albums.values.toList()
}

private fun nameKey(entry: PlayHistoryEntry): String =
    "${entry.source}|${entry.album.trim().lowercase()}|${entry.artist.trim().lowercase()}"
