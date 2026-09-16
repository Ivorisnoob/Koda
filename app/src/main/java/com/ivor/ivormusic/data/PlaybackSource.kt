package com.ivor.ivormusic.data

import com.ivor.ivormusic.data.youtube.sabr.model.SabrDescriptor
import com.ivor.ivormusic.data.youtube.sabr.model.SabrFormatId

/**
 * Resolution metadata, not a MediaSource or a mutable playback session.
 * URL-backed consumers accept [UrlBacked] explicitly until they support SABR.
 * Do not serialize this type: URLs and attestation material expire independently
 * of the user's saved queue. Local media still uses its existing URI path.
 */
internal sealed interface PlaybackSource {
    sealed interface UrlBacked : PlaybackSource {
        val urls: List<String>
    }

    class Progressive(val url: String) : UrlBacked {
        override val urls: List<String> get() = listOf(url)
        override fun toString() = "PlaybackSource.Progressive(redacted)"
    }

    class Split(val videoUrl: String, val audioUrl: String) : UrlBacked {
        override val urls: List<String> get() = listOf(videoUrl, audioUrl)
        override fun toString() = "PlaybackSource.Split(redacted)"
    }

    class Manifest(val url: String, val type: ManifestType) : UrlBacked {
        override val urls: List<String> get() = listOf(url)
        override fun toString() = "PlaybackSource.Manifest(type=$type, redacted)"
    }

    class Sabr(
        val descriptor: SabrDescriptor,
        val audio: SabrFormatId?,
        val video: SabrFormatId?,
    ) : PlaybackSource {
        init {
            require(audio != null || video != null) { "SABR requires a selected track" }
            require(audio == null || descriptor.formats.any { it.id == audio && it.isAudio }) {
                "Unknown SABR audio format"
            }
            require(video == null || descriptor.formats.any { it.id == video && it.isVideo }) {
                "Unknown SABR video format"
            }
        }

        override fun toString() = "PlaybackSource.Sabr(audio=${audio != null}, video=${video != null})"
    }

    enum class ManifestType { DASH, HLS }
}

/** Compatibility boundary while VideoQuality's producers still provide URLs. */
internal val VideoQuality.playbackSource: PlaybackSource.UrlBacked
    get() = when {
        isDASH -> PlaybackSource.Manifest(
            url,
            if (format.equals("HLS", ignoreCase = true)) PlaybackSource.ManifestType.HLS
            else PlaybackSource.ManifestType.DASH,
        )
        audioUrl != null -> PlaybackSource.Split(url, audioUrl)
        else -> PlaybackSource.Progressive(url)
    }
