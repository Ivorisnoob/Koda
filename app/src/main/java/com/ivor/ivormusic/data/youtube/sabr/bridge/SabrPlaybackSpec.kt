/*
 * Fixed playback selection for one SABR source: single audio plus optional
 * single video (stage 6 is fixed quality - no codec groups or ABR), keyed as
 * a0/v0 exactly like the manifest addresses them. Design adapted from PipePipe
 * (GPL-3.0), commit 08b277619ac05a5b227ca53a7fe4cb1958663c4d
 * (SabrSourceSpec, SabrSegmentKey). Copyright the PipePipe contributors.
 * See THIRD_PARTY_NOTICES.md.
 * Koda differences: no format groups (fixed selection), init holders instead
 * of atomic init slots, cache keys reuse SabrFormatId (length-prefixed, null
 * safe), URI parsing stays on java.net so it remains JVM-testable.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.ivor.ivormusic.data.youtube.sabr.bridge

import com.ivor.ivormusic.data.youtube.sabr.model.SabrFormat
import java.net.URI
import java.util.concurrent.atomic.AtomicReference

/** One initialization (`sequence` null) or media segment demand. */
internal data class SabrSegmentRef(val format: SabrFormat, val sequence: Int?) {
    init {
        require(sequence == null || sequence > 0) { "SABR media sequences start at one" }
    }

    fun cacheKey(videoId: String): String = format.id.cacheKey(videoId, sequence)
}

internal class SabrPlaybackSpec(
    val videoId: String,
    val audio: SabrFormat,
    val video: SabrFormat? = null,
) {
    init {
        require(videoId.isNotBlank()) { "SABR spec needs a video id" }
        require(audio.isAudio) { "SABR spec audio track is not audio" }
        if (video != null) require(video.isVideo) { "SABR spec video track is not video" }
    }

    private val audioInit = AtomicReference<ByteArray?>()
    private val videoInit = AtomicReference<ByteArray?>()

    fun durationMs(): Long = maxOf(audio.durationMs, video?.durationMs ?: 0)

    fun formatFor(itag: Int, xtags: String?): SabrFormat? =
        listOfNotNull(audio, video).firstOrNull {
            it.id.itag == itag && (xtags == null || it.id.xtags == xtags)
        }

    fun initData(format: SabrFormat): ByteArray? = when {
        format.isAudio -> audioInit.get()?.copyOf()
        format.isVideo -> videoInit.get()?.copyOf()
        else -> null
    }

    /** First initialization wins; later ones belong to a stale request. */
    fun putInitData(format: SabrFormat, bytes: ByteArray): Boolean = when {
        format.isAudio -> audioInit.compareAndSet(null, bytes.copyOf())
        format.isVideo -> videoInit.compareAndSet(null, bytes.copyOf())
        else -> throw IllegalArgumentException("SABR format has no track type")
    }

    /** Resolves a `sabrseg://<a0|v0>/<init|sequence>` URI from the manifest. */
    fun refFor(uri: String): SabrSegmentRef {
        val parsed = try {
            URI(uri)
        } catch (error: Exception) {
            throw IllegalArgumentException("Bad SABR segment URI: $uri", error)
        }
        require(parsed.scheme == SABR_SEGMENT_SCHEME) { "Bad SABR segment URI: $uri" }
        val format = when (parsed.host) {
            SABR_AUDIO_KEY -> audio
            SABR_VIDEO_KEY -> video
                ?: throw IllegalArgumentException("Audio-only SABR source has no video: $uri")
            else -> throw IllegalArgumentException("Unknown SABR format in URI: $uri")
        }
        val segment = parsed.path?.trim('/')?.takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("Bad SABR segment URI: $uri")
        if (segment == "init") return SabrSegmentRef(format, null)
        return SabrSegmentRef(format, segment.toIntOrNull()?.takeIf { it > 0 }
            ?: throw IllegalArgumentException("Bad SABR sequence in URI: $uri"))
    }
}
