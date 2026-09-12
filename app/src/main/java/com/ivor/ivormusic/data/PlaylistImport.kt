package com.ivor.ivormusic.data

/**
 * Parses M3U/M3U8 playlist format into a list of [Song]s.
 */
object PlaylistImport {

    /**
     * Extracts the playlist title from a `#PLAYLIST:` header directive in M3U content, if present.
     */
    fun parseM3uHeaderName(content: String): String? {
        for (rawLine in content.lines()) {
            val line = rawLine.trim()
            if (line.startsWith("#PLAYLIST:", ignoreCase = true)) {
                val name = line.substring("#PLAYLIST:".length).trim()
                if (name.isNotBlank()) {
                    return name
                }
            }
        }
        return null
    }

    /**
     * Parses M3U or M3U8 string content and extracts [Song] entries.
     */
    fun parseM3u(content: String): List<Song> {
        val songs = mutableListOf<Song>()
        val lines = content.lines()

        var currentDurationMs = 0L
        var currentTitle = ""
        var currentArtist = ""

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isBlank()) continue

            if (line.startsWith("#EXTINF:")) {
                val info = line.removePrefix("#EXTINF:").trim()
                val commaIndex = info.indexOf(',')
                if (commaIndex != -1) {
                    val durationStr = info.substring(0, commaIndex).trim()
                    val titleArtist = info.substring(commaIndex + 1).trim()

                    val firstSpaceIndex = durationStr.indexOf(' ')
                    val numericDuration = if (firstSpaceIndex != -1) {
                        durationStr.substring(0, firstSpaceIndex).trim()
                    } else {
                        durationStr
                    }
                    val seconds = numericDuration.toLongOrNull() ?: -1L
                    currentDurationMs = if (seconds > 0) seconds * 1000L else 0L

                    if (titleArtist.contains(" - ")) {
                        val parts = titleArtist.split(" - ", limit = 2)
                        currentArtist = parts[0].trim()
                        currentTitle = parts[1].trim()
                    } else {
                        currentArtist = ""
                        currentTitle = titleArtist
                    }
                }
            } else if (!line.startsWith("#")) {
                // Line is a URI, file path, or YouTube link
                val song = parseLineToSong(
                    location = line,
                    fallbackTitle = currentTitle,
                    fallbackArtist = currentArtist,
                    durationMs = currentDurationMs
                )
                if (song != null) {
                    songs.add(song)
                }

                // Reset per-track state
                currentDurationMs = 0L
                currentTitle = ""
                currentArtist = ""
            }
        }

        return songs
    }

    private fun parseLineToSong(
        location: String,
        fallbackTitle: String,
        fallbackArtist: String,
        durationMs: Long
    ): Song? {
        val trimmed = location.trim()
        if (trimmed.isBlank()) return null

        val videoId = extractYouTubeVideoId(trimmed)
        val isDeviceFile = trimmed.startsWith("content://") || trimmed.startsWith("file://") || trimmed.startsWith("/")
        val songId = if (videoId != null) {
            videoId
        } else if (isDeviceFile) {
            if (trimmed.startsWith("file://") || trimmed.startsWith("/")) {
                "device:${trimmed.removePrefix("file://")}"
            } else {
                trimmed
            }
        } else {
            trimmed
        }

        val title = if (fallbackTitle.isNotBlank()) fallbackTitle else "Imported Song"
        val artist = if (fallbackArtist.isNotBlank()) fallbackArtist else "Unknown Artist"

        return Song(
            id = songId,
            title = title,
            artist = artist,
            album = "",
            duration = durationMs,
            thumbnailUrl = if (videoId != null) "https://i.ytimg.com/vi/$videoId/hqdefault.jpg" else null,
            source = if (videoId != null) SongSource.YOUTUBE else SongSource.LOCAL
        )
    }

    private fun extractYouTubeVideoId(urlOrId: String): String? {
        if (urlOrId.matches(Regex("^[a-zA-Z0-9_-]{11}$"))) {
            return urlOrId
        }

        val vParamRegex = Regex("[?&]v=([a-zA-Z0-9_-]{11})")
        val matchV = vParamRegex.find(urlOrId)
        if (matchV != null) {
            return matchV.groupValues[1]
        }

        val shortUrlRegex = Regex("youtu\\.be/([a-zA-Z0-9_-]{11})")
        val matchShort = shortUrlRegex.find(urlOrId)
        if (matchShort != null) {
            return matchShort.groupValues[1]
        }

        return null
    }
}
