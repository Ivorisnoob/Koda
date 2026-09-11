package com.ivor.ivormusic.data

import java.security.MessageDigest

/** The independently cached parts of a progressive video source. */
internal enum class VideoPlaybackCacheStream(val keyPart: String) {
    MUXED("muxed"),
    VIDEO("video"),
    AUDIO("audio"),
}

private const val VIDEO_PLAYBACK_CACHE_PREFIX = "video-playback:"
private const val OPAQUE_PLAYBACK_CACHE_PREFIX = "playback-uri:"
private val ITAG_QUERY_PARAMETER = Regex("(?:[?&])itag=([^&#]+)", RegexOption.IGNORE_CASE)
private val LAST_MODIFIED_QUERY_PARAMETER = Regex("(?:[?&])lmt=([^&#]+)", RegexOption.IGNORE_CASE)
private val CONTENT_LENGTH_QUERY_PARAMETER = Regex("(?:[?&])clen=([^&#]+)", RegexOption.IGNORE_CASE)
private val AUDIO_TRACK_QUERY_PARAMETER = Regex("(?:[?&])xtags=([^&#]+)", RegexOption.IGNORE_CASE)
private val NON_KEY_CHARACTER = Regex("[^a-z0-9._-]+")

/**
 * Stable cache key for one byte-addressable video stream.
 *
 * googlevideo URLs expire and are re-signed every few hours, so using the URL
 * itself as Media3's key strands bytes that are still valid behind a dead URL.
 * The itag identifies the encoded rendition across those refreshes. The stream
 * role is part of the key because a split quality has independent video and
 * audio files. Providers that omit itag fall back to a URL digest: they lose
 * cross-signature reuse, but can never mix two same-label files into one cache
 * resource.
 */
internal fun videoPlaybackCacheKey(
    videoId: String,
    stream: VideoPlaybackCacheStream,
    sourceUrl: String,
    fallbackVariant: String,
): String {
    val itag = ITAG_QUERY_PARAMETER.valueIn(sourceUrl)
    val rendition = itag
        ?.let {
            buildString {
                append("itag-").append(it)
                LAST_MODIFIED_QUERY_PARAMETER.valueIn(sourceUrl)?.let { value ->
                    append("-lmt-").append(value)
                }
                CONTENT_LENGTH_QUERY_PARAMETER.valueIn(sourceUrl)?.let { value ->
                    append("-clen-").append(value)
                }
                // Alternate audio tracks may share the same itag. The player
                // currently asks for the original track, but keeping YouTube's
                // discriminator in the key makes that invariant explicit.
                AUDIO_TRACK_QUERY_PARAMETER.valueIn(sourceUrl)?.let { value ->
                    append("-track-").append(value.sha256Prefix())
                }
            }
        }
        ?: run {
            val variant = fallbackVariant
                .lowercase()
                .replace(NON_KEY_CHARACTER, "-")
                .trim('-')
                .takeIf { it.isNotBlank() }
                ?: "default"
            "$variant-url-${sourceUrl.sha256Prefix()}"
        }
    return "$VIDEO_PLAYBACK_CACHE_PREFIX$videoId:${stream.keyPart}:$rendition"
}

private fun Regex.valueIn(url: String): String? =
    find(url)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }

private fun String.sha256Prefix(): String =
    MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))
        .take(8)
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

internal fun isVideoPlaybackCacheKey(key: String): Boolean =
    key.startsWith(VIDEO_PLAYBACK_CACHE_PREFIX)

/** Cache identity for adaptive manifests/segments that provide no media key. */
internal fun opaquePlaybackCacheKey(uri: String): String =
    "$OPAQUE_PLAYBACK_CACHE_PREFIX$uri"

/** Keys that belong in the shared byte cache but are not song ids. */
internal fun isNonMusicPlaybackCacheKey(key: String): Boolean =
    isVideoPlaybackCacheKey(key) || key.startsWith(OPAQUE_PLAYBACK_CACHE_PREFIX)

/** Shorts and watch playback can be cleared independently, even for the same URL. */
internal fun playbackCacheCategoryKey(key: String, shorts: Boolean): String =
    if (shorts) "shorts:$key" else key

internal fun isShortsCacheKey(key: String): Boolean = key.startsWith("shorts:")

/**
 * Whether [url] addresses YouTube's adaptive pipeline - an HLS/DASH manifest,
 * an HLS media playlist, or a live segment - rather than a progressive media
 * file worth keeping on disk.
 *
 * [scar] None of these may be served from the playback cache. An HLS media
 * playlist is re-fetched from the *same* URL every target duration, and that
 * reload is the only way the player learns a broadcast has produced new
 * segments. Media3 asks for it with an ordinary cacheable DataSpec
 * (FLAG_ALLOW_GZIP, no key) and CacheDataSource resolves the unset length from
 * the stored content metadata and reads the first copy straight back off disk
 * [verified September 2026 against Media3 1.11's DefaultHlsPlaylistTracker and
 * CacheDataSource bytecode] - so the segment list froze at whatever the first
 * fetch held. A live stream played out the handful of segments it opened with
 * and then stalled at the live edge, while seeking back into the DVR window
 * went on working, because those segments were already listed in the frozen
 * copy. That asymmetry is the whole signature of this bug.
 *
 * The googlevideo half of the rule is the discriminator [ChunkedStreamDataSource]
 * already uses: a progressive URL carries a query string
 * (`?expire=...&itag=...`), while every URL in the live pipeline - the variant
 * and media playlists on manifest.googlevideo.com and the segments they point
 * at - is path-style with no query at all [verified August 2026]. Excluding
 * the segments as well as the playlists is deliberate: the video cache has no
 * byte ceiling, and an hour of a broadcast is an hour of media nobody replays.
 */
internal fun isUncacheablePlaybackUrl(url: String): Boolean {
    val afterScheme = url.substringAfter("://", missingDelimiterValue = "")
    if (afterScheme.isEmpty()) return false
    val authorityEnd = afterScheme.indexOfFirst { it == '/' || it == '?' || it == '#' }
    val authority = if (authorityEnd < 0) afterScheme else afterScheme.take(authorityEnd)
    val rest = if (authorityEnd < 0) "" else afterScheme.substring(authorityEnd)
    val host = authority.substringAfterLast('@').substringBefore(':').lowercase()
    val path = rest.substringBefore('?').substringBefore('#')
    if (path.endsWith(".m3u8", ignoreCase = true) || path.endsWith(".mpd", ignoreCase = true)) {
        return true
    }
    val googleVideo = host == "googlevideo.com" || host.endsWith(".googlevideo.com")
    return googleVideo && path.isNotEmpty() && !rest.contains('?')
}
