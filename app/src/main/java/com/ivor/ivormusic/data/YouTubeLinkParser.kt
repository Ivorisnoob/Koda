package com.ivor.ivormusic.data

import android.net.Uri

/**
 * A YouTube URL pasted into the search bar, reduced to the ids the app can
 * act on. At least one of [videoId] or [playlistId] is always non-null.
 */
data class ParsedYouTubeLink(
    val videoId: String? = null,
    val playlistId: String? = null,
    /** True for music.youtube.com links. */
    val isMusicLink: Boolean = false
)

/**
 * Detects YouTube URLs pasted into the search bar and extracts video or
 * playlist ids from them. Supported forms: youtu.be short links, watch links
 * (www / m / music subdomains), shorts, live, embed and playlist links, with
 * or without an explicit scheme.
 */
object YouTubeLinkParser {

    private val VIDEO_ID = Regex("[A-Za-z0-9_-]{11}")
    private val PLAYLIST_ID = Regex("[A-Za-z0-9_-]{2,}")

    /** Path roots whose second segment is the video id. */
    private val VIDEO_PATH_ROOTS = setOf("shorts", "live", "embed", "v")

    /**
     * Parse [input] as a YouTube link. Returns null when the text is not a
     * YouTube URL, i.e. it should be treated as a normal search query.
     */
    fun parse(input: String): ParsedYouTubeLink? {
        val text = input.trim()
        if (text.isEmpty() || text.any { it.isWhitespace() }) return null

        val candidate = when {
            text.startsWith("http://", ignoreCase = true) ||
                text.startsWith("https://", ignoreCase = true) -> text
            text.contains("youtube.com/", ignoreCase = true) ||
                text.contains("youtu.be/", ignoreCase = true) -> "https://$text"
            else -> return null
        }

        val uri = try {
            Uri.parse(candidate)
        } catch (e: Exception) {
            return null
        }
        val host = uri.host?.lowercase() ?: return null
        val isYouTubeHost = host == "youtu.be" ||
            host == "youtube.com" || host.endsWith(".youtube.com") ||
            host == "youtube-nocookie.com" || host.endsWith(".youtube-nocookie.com")
        if (!isYouTubeHost) return null

        val segments = try {
            uri.pathSegments
        } catch (e: Exception) {
            emptyList<String>()
        }

        val videoId = when {
            host == "youtu.be" -> segments.firstOrNull()
            segments.firstOrNull() == "watch" -> uri.getQueryParameter("v")
            segments.firstOrNull() in VIDEO_PATH_ROOTS -> segments.getOrNull(1)
            else -> null
        }?.takeIf { it != "videoseries" && VIDEO_ID.matches(it) }

        val playlistId = try {
            uri.getQueryParameter("list")?.takeIf { PLAYLIST_ID.matches(it) }
        } catch (e: Exception) {
            null
        }

        if (videoId == null && playlistId == null) return null
        return ParsedYouTubeLink(
            videoId = videoId,
            playlistId = playlistId,
            isMusicLink = host == "music.youtube.com"
        )
    }
}
