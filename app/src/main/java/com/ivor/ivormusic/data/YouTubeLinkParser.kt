package com.ivor.ivormusic.data

import android.net.Uri

/**
 * A YouTube URL pasted into the search bar or shared into the app, reduced to
 * something the app can act on. Exactly one of [videoId], [playlistId] or
 * [channelRef] is always present.
 */
data class ParsedYouTubeLink(
    val videoId: String? = null,
    val playlistId: String? = null,
    /**
     * A channel, as whatever the link named it: a canonical `UC…` id, an
     * `@handle`, or a legacy `/c/` or `/user/` vanity name.
     *
     * Deliberately not resolved here. `resolveChannelId` is a network call and
     * this parser is called on every keystroke in the search field, so
     * resolution belongs to whoever actually opens the channel.
     */
    val channelRef: String? = null,
    /** True for music.youtube.com links. */
    val isMusicLink: Boolean = false
)

/**
 * Detects YouTube URLs pasted into the search bar and extracts video, playlist
 * or channel references from them. Supported forms: youtu.be short links, watch
 * links (www / m / music subdomains), shorts, live, embed and playlist links,
 * and every channel form - `/channel/UC…`, `/@handle`, and the legacy `/c/` and
 * `/user/` paths - with or without an explicit scheme.
 *
 * Channels matter more than they look. The manifest already claims every
 * `youtube.com` host, so Koda appears in the share sheet for a channel link
 * whether or not it can do anything with one; before these forms were parsed it
 * accepted the tap and then silently did nothing, which is worse than not being
 * offered at all.
 */
object YouTubeLinkParser {

    private val VIDEO_ID = Regex("[A-Za-z0-9_-]{11}")
    private val PLAYLIST_ID = Regex("[A-Za-z0-9_-]{2,}")

    /** Path roots whose second segment is the video id. */
    private val VIDEO_PATH_ROOTS = setOf("shorts", "live", "embed", "v")

    /** Path roots whose second segment names a channel. */
    private val CHANNEL_PATH_ROOTS = setOf("channel", "c", "user")

    /**
     * The channel a path names, in whatever form it used.
     *
     * Trailing segments are ignored on purpose: `/@handle/videos` and
     * `/channel/UC…/playlists` are links to a channel, and dropping the tab is
     * the right reading - the screen opens on the channel's own default tab,
     * which is where the sender's link would have landed anyway.
     */
    private fun parseChannelRef(segments: List<String>): String? {
        val first = segments.firstOrNull()?.takeIf { it.isNotBlank() } ?: return null
        if (first.startsWith("@") && first.length > 1) return first
        if (first.lowercase() in CHANNEL_PATH_ROOTS) {
            return segments.getOrNull(1)?.takeIf { it.isNotBlank() }
        }
        return null
    }

    /**
     * Parse the first YouTube link found anywhere in [text].
     *
     * Unlike [parse], which expects the whole string to be the URL, this
     * tolerates the surrounding prose that share sheets attach - YouTube's own
     * share action sends "Video title\nhttps://youtu.be/id", and other apps add
     * their own wrappers. Returns null when no YouTube link is present.
     */
    fun parseFromSharedText(text: String): ParsedYouTubeLink? =
        text.split(' ', '\n', '\r', '\t', '<', '>', '"')
            .asSequence()
            .mapNotNull { token -> parse(token.trim().trimEnd(*TRAILING_PUNCTUATION)) }
            .firstOrNull()

    /** Sentence punctuation that ends up glued to a URL in shared prose. */
    private val TRAILING_PUNCTUATION = charArrayOf('.', ',', ';', ':', '!', '?', ')', ']', '}', '\'')

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

        // A channel link never carries a video or a list, so it is only
        // considered once those have come back empty - a watch link that
        // happens to sit under /c/ is still a watch link.
        val channelRef = if (videoId == null && playlistId == null) {
            parseChannelRef(segments)
        } else {
            null
        }

        if (videoId == null && playlistId == null && channelRef == null) return null
        return ParsedYouTubeLink(
            videoId = videoId,
            playlistId = playlistId,
            channelRef = channelRef,
            isMusicLink = host == "music.youtube.com"
        )
    }
}
