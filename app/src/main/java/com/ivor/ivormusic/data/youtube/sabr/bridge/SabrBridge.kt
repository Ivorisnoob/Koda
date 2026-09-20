/*
 * Bridges Media3's segment demand to serialized SABR session requests. Demand
 * loop adapted from PipePipe (GPL-3.0), commit 08b277619ac05a5b227ca53a7fe4cb1958663c4d
 * (SabrMediaBridge): preparation until timelines parse, honest buffered ranges,
 * bounded ahead-cache, stop/discard lifecycle.
 * Copyright the PipePipe contributors. See THIRD_PARTY_NOTICES.md.
 * Koda differences: fixed single audio plus optional video (no selection
 * machinery); no bridge-level lock or pending exception - SabrSession already
 * serializes transactions, and undelivered segments surface as IOException so
 * Media3's standard retry policy applies; spool files are the transient store
 * (durable caching is stage 7); session and spool lifecycles stay with the
 * assembly that created them.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.ivor.ivormusic.data.youtube.sabr.bridge

import com.ivor.ivormusic.data.youtube.sabr.SabrFormatTimeline
import com.ivor.ivormusic.data.youtube.sabr.exception.SabrProtocolException
import com.ivor.ivormusic.data.youtube.sabr.exception.SabrStaleDescriptorException
import com.ivor.ivormusic.data.youtube.sabr.media.SabrSegment
import com.ivor.ivormusic.data.youtube.sabr.model.SabrFormat
import com.ivor.ivormusic.data.youtube.sabr.session.SabrRequest
import com.ivor.ivormusic.data.youtube.sabr.session.SabrSession
import com.ivor.ivormusic.data.youtube.sabr.session.SabrTrack
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

internal class SabrBridge(
    private val session: SabrSession,
    val spec: SabrPlaybackSpec,
    private val playbackRate: () -> Float = { 1f },
    /**
     * True when the descriptor behind this bridge stopped being usable -
     * token invalidation, profile switch or expiry - while work was in flight.
     * Upstream applies initialization still arriving after invalidation;
     * Koda rejects those stale completions by identity generation instead, so a
     * rotation can never arm timelines from the previous attestation.
     */
    private val staleCheck: () -> Boolean = { false },
) {
    @Volatile private var audioTimeline: SabrFormatTimeline? = null
    @Volatile private var videoTimeline: SabrFormatTimeline? = null
    @Volatile private var stopped = false
    private val ahead = LinkedHashMap<SabrSegmentRef, SabrSegment>()
    private val nextSequences = ConcurrentHashMap<SabrFormat, Int>()

    fun hasTimelines(): Boolean {
        if (audioTimeline == null) return false
        return spec.video == null || videoTimeline != null
    }

    fun timeline(format: SabrFormat): SabrFormatTimeline =
        (if (format.isAudio) audioTimeline else videoTimeline)
            ?: throw IllegalStateException("SABR timeline is not ready: itag=${format.id.itag}")

    /** Pulls initialization until timelines parse; retains media around position. */
    fun prepareTimelines(positionMs: Long) {
        if (staleCheck()) throw SabrStaleDescriptorException()
        session.request(
            SabrRequest.preparation(maxOf(0, positionMs), listOfNotNull(spec.audio, spec.video)),
            ::accept,
        ) { hasTimelines() }
    }

    /**
     * Returns the demanded media segment, requesting it when absent. Blocks the
     * loader thread; the session serializes concurrent demands internally.
     * Throws [IOException] when the server does not deliver (Media3 retries
     * with its standard policy) and [IllegalStateException] past the timeline.
     */
    fun awaitSegment(ref: SabrSegmentRef): SabrSegment {
        if (staleCheck()) throw SabrStaleDescriptorException()
        val sequence = requireNotNull(ref.sequence) { "SABR initialization is served from the spec" }
        nextSequences[ref.format] = sequence
        val end = timeline(ref.format).endSequence
        if (sequence > end) {
            throw IllegalStateException(
                "SABR segment beyond timeline: itag=${ref.format.id.itag}, seq=$sequence, end=$end")
        }
        synchronized(ahead) { ahead[ref]?.let { return it } }
        val at = maxOf(0, timeline(ref.format).getStartMs(sequence))
        session.request(playbackFor(at), ::accept)
        return synchronized(ahead) { ahead[ref] }
            ?: throw IOException("SABR segment not delivered: itag=${ref.format.id.itag}, seq=$sequence")
    }

    fun discard(ref: SabrSegmentRef) {
        val removed = synchronized(ahead) { ahead.remove(ref) }
        removed?.close()
    }

    fun stop() {
        stopped = true
        val pending = synchronized(ahead) {
            ahead.values.toList().also { ahead.clear() }
        }
        pending.forEach { it.close() }
    }

    private fun playbackFor(playerTimeMs: Long): SabrRequest {
        val tracks = listOfNotNull(spec.audio, spec.video).map { format ->
            SabrTrack(format, timeline(format), honestBuffered(format))
        }
        return SabrRequest.playback(playerTimeMs, playbackRate(), tracks)
    }

    private fun honestBuffered(format: SabrFormat): IntRange? {
        val through = (nextSequences[format] ?: 1) - 1
        val cached = synchronized(ahead) {
            ahead.keys.filter { it.format === format }.mapNotNullTo(mutableSetOf()) { it.sequence }
        }
        return contiguousBuffered(cached, through)
    }

    private fun accept(segment: SabrSegment) {
        if (stopped) {
            segment.close()
            return
        }
        if (staleCheck()) {
            segment.close()
            throw SabrStaleDescriptorException()
        }
        val header = segment.header
        val format = spec.formatFor(header.itag, header.xtags)
        if (format == null || (!header.initialization && header.sequence <= 0)) {
            segment.close()
            return
        }
        if (header.initialization) {
            val bytes = try {
                segment.open().use { it.readBytes() }
            } finally {
                segment.close()
            }
            if (!spec.putInitData(format, bytes)) return
            try {
                val parsed = SabrFormatTimeline.parse(format, bytes)
                if (format.isAudio) audioTimeline = parsed else videoTimeline = parsed
            } catch (error: SabrProtocolException) {
                throw IllegalStateException(
                    "Invalid SABR initialization: itag=${format.id.itag}", error)
            }
            return
        }
        val key = SabrSegmentRef(format, header.sequence)
        synchronized(ahead) {
            if (ahead.containsKey(key)) {
                segment.close()
                return
            }
            if (stopped) {
                segment.close()
                return
            }
            ahead[key] = segment
            while (ahead.size > MAX_AHEAD_SEGMENTS) {
                val oldest = ahead.keys.first()
                ahead.remove(oldest)?.close()
            }
        }
    }

    private companion object {
        const val MAX_AHEAD_SEGMENTS = 64
    }
}

/**
 * The contiguous cached run ending at [through], or null when [through] itself
 * is not held. The server is told exactly this range - never an optimistic
 * "everything before the playhead".
 */
internal fun contiguousBuffered(cached: Set<Int>, through: Int): IntRange? {
    if (through < 1 || through !in cached) return null
    var start = through
    while (start - 1 >= 1 && start - 1 in cached) start--
    return start..through
}
