package com.ivor.ivormusic.data

/**
 * A second-chance lyrics query for YouTube-shaped metadata, or null when
 * cleaning changes nothing.
 *
 * Upload titles carry decoration no lyrics catalogue indexes: "(Official
 * Video)", "[Lyrics]", "| 4K", "feat." credits, and "Artist - Title" titles
 * under a "Artist - Topic" or "ArtistVEVO" channel name. Version markers that
 * change the lyric or its timing (Remix, Live, Acoustic) are kept.
 */
internal fun LyricsRequest.cleanedForSearch(): LyricsRequest? {
    var cleanTitle = title
    var cleanArtist = artist
        .replace(TOPIC_SUFFIX, "")
        .replace(VEVO_SUFFIX, "")
        .trim()

    // "Artist - Title" under the artist's own channel.
    val dash = cleanTitle.indexOf(" - ")
    if (dash > 0) {
        val left = cleanTitle.substring(0, dash).trim()
        if (cleanArtist.isBlank() || left.contains(cleanArtist, ignoreCase = true) ||
            cleanArtist.contains(left, ignoreCase = true)
        ) {
            if (cleanArtist.isBlank()) cleanArtist = left
            cleanTitle = cleanTitle.substring(dash + 3)
        }
    }

    cleanTitle = cleanTitle
        .replace(DECORATION_BRACKETS, "")
        .replace(FEATURING, "")
        .substringBefore(" | ")
        .replace(Regex("\\s{2,}"), " ")
        .trim(' ', '-', '|', '"', '\'')
    cleanArtist = cleanArtist
        .replace(FEATURING, "")
        .split(ARTIST_SEPARATORS)
        .first()
        .trim()

    if (cleanTitle.isBlank()) return null
    if (cleanTitle == title && cleanArtist == artist) return null
    return copy(title = cleanTitle, artist = cleanArtist)
}

private val TOPIC_SUFFIX = Regex("\\s*-\\s*Topic$", RegexOption.IGNORE_CASE)
private val VEVO_SUFFIX = Regex("VEVO$")
private val FEATURING = Regex(
    "\\s*[(\\[]?\\s*\\b(feat\\.?|ft\\.?|featuring)\\s+[^)\\]]*[)\\]]?",
    RegexOption.IGNORE_CASE,
)
private val DECORATION_BRACKETS = Regex(
    "\\s*[(\\[][^)\\]]*\\b(official|video|audio|lyrics?|visuali[sz]er|m/?v|hd|4k|explicit|clean|remaster(ed)?)\\b[^)\\]]*[)\\]]",
    RegexOption.IGNORE_CASE,
)
private val ARTIST_SEPARATORS = Regex("\\s*(,|&| x )\\s*", RegexOption.IGNORE_CASE)
