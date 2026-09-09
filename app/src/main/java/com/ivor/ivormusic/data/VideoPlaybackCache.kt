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
