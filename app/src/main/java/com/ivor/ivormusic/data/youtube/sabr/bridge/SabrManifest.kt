/*
 * Synthetic static MPD over `sabrseg://` URIs, served by SabrSegmentDataSource.
 * Shape adapted from PipePipe (GPL-3.0), commit 08b277619ac05a5b227ca53a7fe4cb1958663c4d
 * (SabrDashMediaSource): isoff-on-demand profile, one AdaptationSet per track,
 * BaseURL per representation, explicit SegmentTimeline in milliseconds, segment
 * count bounded. Copyright the PipePipe contributors. See THIRD_PARTY_NOTICES.md.
 * Koda differences: fixed single audio plus optional single video (no codec
 * groups or ABR - stage 6 is fixed quality), audio-only manifests for music,
 * Role always main, pure String builder (parsing stays in the Android assembly).
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.ivor.ivormusic.data.youtube.sabr.bridge

import com.ivor.ivormusic.data.youtube.sabr.SabrFormatTimeline
import com.ivor.ivormusic.data.youtube.sabr.model.SabrFormat

/**
 * Builds the manifest text. Timelines must already be prepared: the segment
 * counts and boundaries come from parsed initialization data, never guessed.
 * Throws [IllegalStateException] on an unusable timeline.
 */
internal fun buildSabrManifest(
    audio: SabrFormat,
    audioTimeline: SabrFormatTimeline,
    video: SabrFormat? = null,
    videoTimeline: SabrFormatTimeline? = null,
    durationMs: Long,
): String {
    require(audio.isAudio) { "SABR manifest audio track is not audio" }
    val videoWithTimeline = if (video != null) {
        require(video.isVideo) { "SABR manifest video track is not video" }
        video to requireNotNull(videoTimeline) { "SABR video needs its timeline" }
    } else {
        require(videoTimeline == null) { "SABR video timeline without video" }
        null
    }
    return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
        "<MPD xmlns=\"urn:mpeg:dash:schema:mpd:2011\" type=\"static\" " +
        "profiles=\"urn:mpeg:dash:profile:isoff-on-demand:2011\" " +
        "minBufferTime=\"PT1.5S\" mediaPresentationDuration=\"${mpdDuration(durationMs)}\">" +
        "<Period id=\"0\" start=\"PT0S\">" +
        (videoWithTimeline?.let { (format, timeline) -> videoAdaptationSet(format, timeline) } ?: "") +
        audioAdaptationSet(audio, audioTimeline) +
        "</Period></MPD>"
}

internal const val SABR_AUDIO_KEY = "a0"
internal const val SABR_VIDEO_KEY = "v0"
internal const val SABR_SEGMENT_SCHEME = "sabrseg"

private fun audioAdaptationSet(audio: SabrFormat, timeline: SabrFormatTimeline): String {
    val label = audio.audioTrackDisplayName
    val language = audio.id.audioTrackId?.split('.', '_', '-')?.firstOrNull()
    val builder = StringBuilder()
        .append("<AdaptationSet id=\"1\" contentType=\"audio\" mimeType=\"")
        .append(xml(containerMime(audio))).append("\" segmentAlignment=\"true\" startWithSAP=\"1\"")
    if (language != null) builder.append(" lang=\"").append(xml(language)).append("\"")
    builder.append('>')
    if (!label.isNullOrEmpty()) builder.append("<Label>").append(xml(label)).append("</Label>")
    builder.append("<Role schemeIdUri=\"urn:mpeg:dash:role:2011\" value=\"main\"/>")
    builder.append(representation(audio, SABR_AUDIO_KEY, timeline))
    return builder.append("</AdaptationSet>").toString()
}

private fun videoAdaptationSet(video: SabrFormat, timeline: SabrFormatTimeline): String =
    "<AdaptationSet id=\"0\" contentType=\"video\" mimeType=\"" +
        xml(containerMime(video)) + "\" segmentAlignment=\"true\" startWithSAP=\"1\">" +
        representation(video, SABR_VIDEO_KEY, timeline) +
        "</AdaptationSet>"

private fun representation(format: SabrFormat, key: String, timeline: SabrFormatTimeline?): String {
    val builder = StringBuilder()
        .append("<Representation id=\"").append(key)
        .append("\" bandwidth=\"").append(maxOf(1, format.bitrate)).append("\"")
    val codecs = codecs(format.mimeType)
    if (codecs.isNotEmpty()) builder.append(" codecs=\"").append(xml(codecs)).append("\"")
    if (format.isVideo) {
        builder.append(" width=\"").append(maxOf(1, format.width))
            .append("\" height=\"").append(maxOf(1, format.height)).append("\"")
    } else {
        builder.append(" audioSamplingRate=\"48000\"")
    }
    builder.append("><BaseURL>").append(SABR_SEGMENT_SCHEME).append("://").append(key).append("/</BaseURL>")
    if (timeline != null) builder.append(segmentTemplate(timeline, format.id.itag))
    return builder.append("</Representation>").toString()
}

private fun segmentTemplate(timeline: SabrFormatTimeline, itag: Int): String {
    val end = timeline.endSequence
    check(end in 1..10_000) { "Invalid SABR segment count: itag=$itag, count=$end" }
    val builder = StringBuilder()
        .append("<SegmentTemplate timescale=\"1000\" startNumber=\"1\" ")
        .append("initialization=\"init\" media=\"\$Number\$\">")
        .append("<SegmentTimeline>")
    for (sequence in 1..end) {
        val start = maxOf(0, timeline.getStartMs(sequence))
        val duration = maxOf(1, timeline.getEndMs(sequence) - timeline.getStartMs(sequence))
        builder.append("<S t=\"").append(start).append("\" d=\"").append(duration).append("\"/>")
    }
    return builder.append("</SegmentTimeline></SegmentTemplate>").toString()
}

private fun mpdDuration(durationMs: Long): String {
    val safe = maxOf(1, durationMs)
    return "PT${safe / 1000}.${(safe % 1000).toString().padStart(3, '0')}S"
}

private fun containerMime(format: SabrFormat): String {
    val mime = format.mimeType.substringBefore(';').trim()
    return mime.ifEmpty { if (format.isAudio) "audio/mp4" else "video/mp4" }
}

private fun codecs(mimeType: String): String {
    val start = mimeType.indexOf("codecs=")
    if (start < 0) return ""
    return mimeType.substring(start + "codecs=".length).replace("\"", "").trim()
}

private fun xml(value: String): String = value
    .replace("&", "&amp;")
    .replace("\"", "&quot;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
