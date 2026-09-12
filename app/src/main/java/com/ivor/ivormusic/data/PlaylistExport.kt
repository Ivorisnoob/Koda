package com.ivor.ivormusic.data

/**
 * Serializes local playlists ([UserPlaylist]) to M3U/M3U8 playlist format.
 */
object PlaylistExport {

    /**
     * Converts a [UserPlaylist] to an M3U/M3U8 formatted string.
     */
    fun toM3u(playlist: UserPlaylist): String {
        val sb = StringBuilder()
        sb.append("#EXTM3U\n")
        sb.append("#PLAYLIST:").append(playlist.name).append("\n")

        for (song in playlist.songs) {
            val durationInSeconds = if (song.duration > 0) song.duration / 1000 else -1
            val titleAndArtist = if (song.artist.isNotBlank()) {
                "${song.artist} - ${song.title}"
            } else {
                song.title
            }
            sb.append("#EXTINF:").append(durationInSeconds).append(",").append(titleAndArtist).append("\n")

            if (song.id.startsWith("device:")) {
                sb.append(song.id.removePrefix("device:")).append("\n")
            } else if (song.id.startsWith("http://") || song.id.startsWith("https://") || song.id.startsWith("content://") || song.id.startsWith("file://")) {
                sb.append(song.id).append("\n")
            } else {
                sb.append("https://www.youtube.com/watch?v=").append(song.id).append("\n")
            }
        }

        return sb.toString()
    }
}
