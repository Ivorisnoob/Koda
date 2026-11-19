package com.ivor.ivormusic.data

/**
 * The playlist list as it should be drawn without a connection.
 *
 * A downloaded playlist is remembered by [DownloadedPlaylistStore], separately
 * from the lists the Library merges: the account's own playlists come from a
 * live fetch, so offline the one place a downloaded YouTube playlist was still
 * recorded was the Downloads tab. The Playlists tab and Home's playlist rows
 * were empty for exactly the people who had prepared for being offline.
 *
 * Offline, every downloaded playlist is listed first - those are the ones that
 * will play - counted by the songs actually on the device rather than by the
 * playlist's full length, followed by everything else in its usual order. A
 * playlist already in [playlists] keeps its own card and moves up rather than
 * appearing twice. One with nothing left on the device is not added: an entry
 * that opens on an empty list is the bug this replaces.
 *
 * Online the list is returned untouched. A download is not a Save (settled in
 * CLAUDE.md), so a playlist downloaded from search does not join the Library
 * grid just because its files are here.
 *
 * [hiddenIds] is applied to the downloaded ones too, so hiding a playlist
 * still hides it offline.
 */
fun playlistsForOffline(
    playlists: List<PlaylistDisplayItem>,
    online: Boolean,
    downloaded: List<DownloadedPlaylist>,
    downloads: List<Song>,
    hiddenIds: Set<String>,
    downloadedLabel: String,
): List<PlaylistDisplayItem> {
    if (online || downloaded.isEmpty()) return playlists
    val listed = playlists.associateBy { it.id }
    val ready = downloaded.mapNotNull { snapshot ->
        if (snapshot.id in hiddenIds) return@mapNotNull null
        val here = snapshot.offlineSongs(downloads).size
        if (here == 0) return@mapNotNull null
        (listed[snapshot.id] ?: PlaylistDisplayItem(
            name = snapshot.title,
            url = snapshot.id,
            uploaderName = downloadedLabel,
            thumbnailUrl = snapshot.artworkUrl,
        )).copy(itemCount = here)
    }
    if (ready.isEmpty()) return playlists
    val readyIds = ready.mapTo(hashSetOf()) { it.id }
    return ready + playlists.filterNot { it.id in readyIds }
}
