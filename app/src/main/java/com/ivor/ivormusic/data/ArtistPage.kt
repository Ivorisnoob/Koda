package com.ivor.ivormusic.data

/**
 * An artist page as YouTube Music describes it: identity, popularity, the
 * discography split the way the shelves arrive, and the discovery shelves
 * around it.
 *
 * Parsed from a single InnerTube artist browse (`musicImmersiveHeaderRenderer`
 * plus the carousels), with the Singles discography More endpoint followed for
 * the complete list. Verified September 2026.
 */
data class ArtistPage(
    val id: String,
    val name: String,
    /** Full biography from the immersive header, null when absent. */
    val bio: String? = null,
    /** e.g. "371M monthly audience", null when absent. */
    val monthlyAudience: String? = null,
    /** Widest banner thumbnail, null when the artist has none. */
    val bannerUrl: String? = null,
    /** Full song list (top shelf plus the followed "More" playlist). */
    val songs: List<Song> = emptyList(),
    /** Shelf order of the top songs, so the popular-five survive the merge. */
    val topSongIds: List<String> = emptyList(),
    /** "Albums" shelf, complete as served (no More endpoint). */
    val albums: List<PlaylistDisplayItem> = emptyList(),
    /** "Singles & EPs" shelf plus the followed discography pages. */
    val singles: List<PlaylistDisplayItem> = emptyList(),
    /** "Fans might also like" shelf. */
    val similarArtists: List<SimilarArtist> = emptyList(),
    /** "Featured on" shelf (playlists carrying this artist). */
    val featuredOn: List<FeaturedPlaylist> = emptyList(),
    /** The top-songs shelf's full-list playlist, for callers paging further. */
    val songsPlaylistBrowseId: String? = null,
) {
    /** Top songs in shelf order (roughly popularity). */
    val topSongs: List<Song>
        get() {
            if (topSongIds.isEmpty()) return songs.take(5)
            val byId = songs.associateBy { it.id }
            val ordered = topSongIds.mapNotNull { byId[it] }
            return (ordered + songs.filter { it.id !in topSongIds.toSet() }).take(5)
        }
}

/** Identity block parsed from `musicImmersiveHeaderRenderer`. */
internal data class ArtistHeader(
    val name: String,
    val bio: String?,
    val monthlyAudience: String?,
    val bannerUrl: String?,
)

/** One entry of the "Fans might also like" shelf. */
data class SimilarArtist(
    val id: String,
    val name: String,
    /** e.g. "46.3M monthly audience", null when absent. */
    val audienceText: String? = null,
    val thumbnailUrl: String? = null,
)

/** One entry of the "Featured on" shelf. */
data class FeaturedPlaylist(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val thumbnailUrl: String? = null,
)

/**
 * Where a song lives: resolved from the first `playlistPanelVideoRenderer`
 * of a music `/next` response, whose `longBylineText` runs are exactly
 * artist link, album link, year. Verified September 2026.
 */
data class SongAlbumRef(
    val albumId: String,
    val albumTitle: String? = null,
    val artistId: String? = null,
    val year: Int? = null,
)
