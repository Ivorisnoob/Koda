package com.ivor.ivormusic.data.stream

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Asks googlevideo whether it will serve a resolved stream to the end, before
 * the player is given it.
 *
 * A refused visitorData is invisible to resolution: `/player` answers OK and
 * googlevideo serves the opening of the stream, then answers 403 for every
 * range after it. [verified October 2026, `.probe/visionos_wall_probe.py`:
 * about one fresh WEB-minted token in ten through visionOS; the wall began
 * 250 KiB to 1.1 MiB in depending on the format, about a minute of audio, and
 * held for every song, every audio itag and a re-resolution under that token.
 * The same wall on ANDROID_VR and IOS was measured in August 2026.] Unprobed,
 * a song played for a minute and then stalled while playback found out.
 *
 * The last byte is refused under such a token and served under a good one
 * whatever the format, so one single-byte ranged request settles it.
 *
 * @param http must carry a hard `callTimeout`: this sits in front of a song
 * starting.
 */
internal class StreamProbe(
    private val http: OkHttpClient,
    private val browserUserAgent: String,
) {
    /**
     * True or false when googlevideo said so (206 or 403); null when it could
     * not be asked or the answer means nothing - see [lastByteOffset] - or the
     * request failed, or came back with any other status.
     */
    suspend fun servesLastByte(url: String): Boolean? = withContext(Dispatchers.IO) {
        val offset = lastByteOffset(url) ?: return@withContext null
        try {
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", userAgentForStreamUrl(url, browserUserAgent))
                .addHeader("Range", "bytes=$offset-$offset")
                .build()
            http.newCall(request).execute().use { response ->
                when (response.code) {
                    206 -> true
                    403 -> false
                    else -> null
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * The shortest stream whose last byte says anything. The refused window is
 * about the first minute of media, so a clip shorter than this could be
 * served whole under a refused token and read as a pass.
 */
private const val MIN_PROBED_DURATION_S = 120.0

/**
 * The offset of [url]'s last byte, or null when probing it would prove
 * nothing: the URL does not state its length (`clen`) or duration (`dur`), or
 * the stream is short enough to sit inside the opening window.
 */
internal fun lastByteOffset(url: String): Long? {
    val parsed = url.toHttpUrlOrNull() ?: return null
    val length = parsed.queryParameter("clen")?.toLongOrNull()?.takeIf { it > 0L } ?: return null
    val durationS = parsed.queryParameter("dur")?.toDoubleOrNull() ?: return null
    if (durationS < MIN_PROBED_DURATION_S) return null
    return length - 1
}
