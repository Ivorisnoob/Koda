/*
 * Request field numbers, client identity and URL parameters adapted from
 * PipePipeExtractor's YoutubeSabrRequestHelper at c0cd0d61863f430af86475aaac968fbef245f507
 * (GPL-3.0). Copyright the upstream contributors. See THIRD_PARTY_NOTICES.md.
 * Koda changes: audio-only state, explicit buffered ranges, strict URL checks.
 */
package com.ivor.ivormusic.data.youtube.sabr.session

import com.ivor.ivormusic.data.youtube.sabr.exception.SabrProtocolException
import com.ivor.ivormusic.data.youtube.sabr.model.SabrDescriptor
import com.ivor.ivormusic.data.youtube.sabr.model.SabrFormat
import com.ivor.ivormusic.data.youtube.sabr.model.validateSabrStreamingUrl
import com.ivor.ivormusic.data.youtube.sabr.protocol.SabrProto
import java.util.Base64

/** Mutable per-session inputs, snapshotted by [SabrSession] under its transaction lock. */
internal class SabrStreamerState(
    val playbackCookie: ByteArray?,
    /** Contexts to send, in first-seen order. */
    val activeContexts: List<Pair<Int, ByteArray>>,
    /** Known context types the client holds but is not sending. */
    val unsentContextTypes: List<Int>,
    val bandwidthEstimateBps: Long,
)

internal object SabrRequestEncoder {
    /**
     * [judgement] Upstream's MWEB identity. The descriptor's streaming URL must be
     * minted for MWEB (`c=MWEB`) or googlevideo rejects the mismatch (invariant 2).
     * Stage 5 must verify this pairing against a live player response.
     */
    const val MWEB_USER_AGENT = "Mozilla/5.0 (iPad; CPU OS 16_7_10 like Mac OS X) AppleWebKit/605.1.15 " +
        "(KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1,gzip(gfe)"
    private const val MWEB_CLIENT_ID = 2
    private const val MIN_WIDTH = 640
    private const val MIN_HEIGHT = 360

    fun headers(): Map<String, String> = mapOf(
        "Content-Type" to "application/x-protobuf",
        "Accept" to "application/vnd.yt-ump",
        "Accept-Encoding" to "identity",
        "User-Agent" to MWEB_USER_AGENT,
    )

    /** Rejects anything but https googlevideo, and a URL minted for another client. */
    fun validateStreamingUrl(url: String) = validateSabrStreamingUrl(url)

    /** `alr`/`cpn` are kept if the server already set them; `rn` always reflects this request. */
    fun url(base: String, cpn: String, requestNumber: Int): String {
        require(requestNumber >= 0)
        val fragment = base.indexOf('#').let { if (it < 0) "" else base.substring(it) }
        val withoutFragment = base.removeSuffix(fragment)
        val queryStart = withoutFragment.indexOf('?')
        val path = if (queryStart < 0) withoutFragment else withoutFragment.substring(0, queryStart)
        val parameters = (if (queryStart < 0) "" else withoutFragment.substring(queryStart + 1))
            .split('&').filter { it.isNotEmpty() && it.substringBefore('=') != "rn" }.toMutableList()
        val names = parameters.map { it.substringBefore('=') }.toSet()
        if ("alr" !in names) parameters += "alr=yes"
        if ("cpn" !in names) parameters += "cpn=" + java.net.URLEncoder.encode(cpn, "UTF-8")
        parameters += "rn=$requestNumber"
        return path + "?" + parameters.joinToString("&") + fragment
    }

    fun body(descriptor: SabrDescriptor, request: SabrRequest, state: SabrStreamerState, followUp: Boolean): ByteArray {
        val audio = request.audio?.format
        val video = request.video?.format
        val ranges = if (request.selectsTracks) request.tracks.mapNotNull(::bufferedRange) else emptyList()
        val includePlaybackState = followUp || request.playerTimeMs > 0 || ranges.isNotEmpty()
        val writer = SabrProto.Writer()
        writer.writeMessage(1, clientAbrState(request, audio, video, includePlaybackState, state.bandwidthEstimateBps))
        if (includePlaybackState) {
            if (request.selectsTracks) request.tracks.forEach { writer.writeMessage(2, SabrProto.formatId(it.format.id)) }
            ranges.forEach { writer.writeMessage(3, it) }
            writer.writeUInt64(4, request.playerTimeMs)
        }
        writer.writeBytes(5, decodeBase64(descriptor.ustreamerConfig))
        audio?.let { writer.writeMessage(16, SabrProto.formatId(it.id)) }
        video?.let { writer.writeMessage(17, SabrProto.formatId(it.id)) }
        writer.writeMessage(19, streamerContext(descriptor, state))
        return writer.toByteArray()
    }

    private fun clientAbrState(request: SabrRequest, audio: SabrFormat?, video: SabrFormat?,
                               followUp: Boolean, bandwidthEstimateBps: Long): ByteArray {
        val state = SabrProto.Writer()
        if (followUp && video != null) {
            state.writeInt32(18, maxOf(video.width, MIN_WIDTH))
            state.writeInt32(19, maxOf(video.height, MIN_HEIGHT))
        }
        if (video != null) state.writeInt32(21, maxOf(video.height, MIN_HEIGHT))
        if (followUp) {
            val estimate = if (bandwidthEstimateBps > 0) bandwidthEstimateBps else bitrateEstimate(audio, video)
            if (estimate > 0) state.writeUInt64(23, estimate)
        }
        state.writeInt32(34, 1)
        state.writeFloat(35, request.playbackRate)
        // Enabled track types: 1 audio only, 2 video only, absent for both.
        when {
            audio != null && video == null -> state.writeInt32(40, 1)
            video != null && audio == null -> state.writeInt32(40, 2)
        }
        if (audio?.isDrc == true) state.writeBool(46, true)
        state.writeUInt64(28, request.playerTimeMs)
        state.writeStringIfNotEmpty(69, audio?.id?.audioTrackId)
        return state.toByteArray()
    }

    private fun bitrateEstimate(audio: SabrFormat?, video: SabrFormat?): Long {
        val formats = listOfNotNull(audio, video)
        if (formats.any { it.bitrate <= 0 }) return -1
        return formats.sumOf { it.bitrate.toLong() } * 2
    }

    private fun bufferedRange(track: SabrTrack): ByteArray? {
        val range = track.buffered ?: return null
        val timeline = track.timeline ?: return null
        val startMs = timeline.getStartMs(range.first)
        val endMs = timeline.getEndMs(range.last)
        if (startMs < 0 || endMs < startMs) throw SabrProtocolException("Invalid SABR buffered range")
        val durationMs = endMs - startMs
        val timeRange = SabrProto.Writer().apply {
            writeUInt64(1, startMs); writeUInt64(2, durationMs); writeInt32(3, 1000)
        }
        return SabrProto.Writer().apply {
            writeMessage(1, SabrProto.formatId(track.format.id))
            writeUInt64(2, startMs)
            writeUInt64(3, durationMs)
            writeInt32(4, range.first)
            writeInt32(5, range.last)
            writeMessage(6, timeRange.toByteArray())
        }.toByteArray()
    }

    private fun streamerContext(descriptor: SabrDescriptor, state: SabrStreamerState): ByteArray {
        val client = SabrProto.Writer().apply {
            writeInt32(16, MWEB_CLIENT_ID)
            writeStringIfNotEmpty(17, descriptor.clientVersion)
            // [judgement] Upstream's fixed locale; the server does not localize media.
            writeStringIfNotEmpty(21, "en-US")
            writeStringIfNotEmpty(22, "US")
        }
        val context = SabrProto.Writer()
        context.writeMessage(1, client.toByteArray())
        context.writeBytes(2, descriptor.poToken)
        state.playbackCookie?.takeIf { it.isNotEmpty() }?.let { context.writeBytes(3, it) }
        state.activeContexts.forEach { (type, value) ->
            context.writeMessage(5, SabrProto.Writer().apply { writeInt32(1, type); writeBytes(2, value) }.toByteArray())
        }
        state.unsentContextTypes.forEach { context.writeInt32(6, it) }
        return context.toByteArray()
    }

    private fun decodeBase64(value: String): ByteArray {
        val padded = value.trim().let { it + "=".repeat((4 - it.length % 4) % 4) }
        return try {
            Base64.getDecoder().decode(padded)
        } catch (_: IllegalArgumentException) {
            try { Base64.getUrlDecoder().decode(padded) } catch (error: IllegalArgumentException) {
                throw SabrProtocolException("Invalid SABR ustreamer config", error)
            }
        }
    }
}
