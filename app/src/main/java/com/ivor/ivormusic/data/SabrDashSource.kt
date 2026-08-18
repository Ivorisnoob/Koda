package com.ivor.ivormusic.data

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.dash.DefaultDashChunkSource
import androidx.media3.exoplayer.dash.manifest.DashManifestParser
import androidx.media3.exoplayer.source.MediaSource
import java.io.ByteArrayInputStream
import java.io.IOException

/**
 * Builds a `DashMediaSource` over a SABR session.
 *
 * **The manifest is synthesised, not fetched.** SABR has no manifest; what it
 * has is an initialization segment per track whose `sidx` box indexes every
 * segment in the file. Writing that index out as a DASH `SegmentTimeline` gives
 * Media3 exactly what it needs to turn a seek position into a segment number,
 * which it then asks [SabrDataSource] for. The alternative - spooling segments
 * into a file and playing it progressively - can only be read forward from the
 * start, which is why seeking never worked before this existed.
 *
 * The MPD is deliberately minimal: one video representation and one audio
 * representation, matching the single pair `SabrInfo` resolves. Quality
 * switching still goes through re-resolving the video, as it did before.
 */
@UnstableApi
object SabrDashSource {

    /**
     * @param initialPositionMs where playback will start, so the preparation
     *   request fetches initialization near it rather than at zero.
     */
    fun create(
        mediaItem: MediaItem,
        bridge: SabrMediaBridge,
        videoItag: Int,
        audioItag: Int,
        videoCodec: String?,
        audioCodec: String?,
        width: Int,
        height: Int,
        videoBitrate: Int,
        audioBitrate: Int,
        initialPositionMs: Long,
    ): MediaSource {
        bridge.prepareTimelines(initialPositionMs)
        val videoTimeline = bridge.videoTimeline
            ?: throw IOException("SABR video timeline unavailable")
        val audioTimeline = bridge.audioTimeline
            ?: throw IOException("SABR audio timeline unavailable")

        val durationMs = bridge.durationMs().takeIf { it > 0 }
            ?: maxOf(videoTimeline.durationMs, audioTimeline.durationMs)

        val mpd = buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
            append("<MPD xmlns=\"urn:mpeg:dash:schema:mpd:2011\" type=\"static\" ")
            append("profiles=\"urn:mpeg:dash:profile:isoff-on-demand:2011\" ")
            append("minBufferTime=\"PT1.5S\" mediaPresentationDuration=\"")
            append(formatDuration(durationMs)).append("\">")
            append("<Period id=\"0\" start=\"PT0S\">")
            append(
                adaptationSet(
                    id = "0",
                    contentType = "video",
                    mimeType = "video/mp4",
                    itag = videoItag,
                    codecs = videoCodec,
                    bitrate = videoBitrate,
                    width = width,
                    height = height,
                    timeline = videoTimeline,
                )
            )
            append(
                adaptationSet(
                    id = "1",
                    contentType = "audio",
                    mimeType = "audio/mp4",
                    itag = audioItag,
                    codecs = audioCodec,
                    bitrate = audioBitrate,
                    width = 0,
                    height = 0,
                    timeline = audioTimeline,
                )
            )
            append("</Period></MPD>")
        }

        val manifest = try {
            DashManifestParser().parse(
                Uri.parse("sabr://manifest"),
                ByteArrayInputStream(mpd.toByteArray(Charsets.UTF_8)),
            )
        } catch (e: IOException) {
            throw IOException("Could not parse the generated SABR DASH manifest", e)
        }

        return DashMediaSource.Factory(
            DefaultDashChunkSource.Factory(SabrDataSource.Factory(bridge)),
            /* manifestDataSourceFactory = */ null,
        )
            .setLoadErrorHandlingPolicy(SabrLoadErrorHandlingPolicy())
            .createMediaSource(manifest, mediaItem)
    }

    private fun adaptationSet(
        id: String,
        contentType: String,
        mimeType: String,
        itag: Int,
        codecs: String?,
        bitrate: Int,
        width: Int,
        height: Int,
        timeline: SabrProtocol.SegmentIndex,
    ): String = buildString {
        append("<AdaptationSet id=\"").append(id)
        append("\" contentType=\"").append(contentType)
        append("\" mimeType=\"").append(mimeType)
        append("\" segmentAlignment=\"true\" startWithSAP=\"1\">")
        append("<Representation id=\"").append(itag)
        append("\" bandwidth=\"").append(maxOf(1, bitrate)).append("\"")
        if (!codecs.isNullOrBlank()) {
            append(" codecs=\"").append(xml(codecs)).append("\"")
        }
        if (contentType == "video") {
            append(" width=\"").append(maxOf(1, width))
            append("\" height=\"").append(maxOf(1, height)).append("\"")
        } else {
            append(" audioSamplingRate=\"48000\"")
        }
        append("><BaseURL>").append(SabrDataSource.baseUrlFor(itag)).append("</BaseURL>")
        append(segmentTemplate(timeline))
        append("</Representation></AdaptationSet>")
    }

    /**
     * Every segment written out explicitly, because SABR's segments are not a
     * fixed duration and a `duration=` template would drift from the real
     * timeline - which is exactly the sort of drift that makes a seek land in
     * the wrong place.
     */
    private fun segmentTemplate(timeline: SabrProtocol.SegmentIndex): String = buildString {
        append("<SegmentTemplate timescale=\"1000\" startNumber=\"1\" ")
        append("initialization=\"init\" media=\"\$Number\$\">")
        append("<SegmentTimeline>")
        for (entry in timeline.entries) {
            val duration = maxOf(1, entry.endMs - entry.startMs)
            append("<S t=\"").append(maxOf(0, entry.startMs))
            append("\" d=\"").append(duration).append("\"/>")
        }
        append("</SegmentTimeline></SegmentTemplate>")
    }

    private fun formatDuration(durationMs: Long): String =
        "PT" + (durationMs.coerceAtLeast(0) / 1000.0) + "S"

    private fun xml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
