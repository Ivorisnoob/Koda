package com.ivor.ivormusic.data

import android.util.Log
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

/**
 * Sits between Media3's segment loader and [SabrSession].
 *
 * Media3 asks for a specific segment - "number 41 of itag 137" - and SABR has no
 * way to answer that directly: it is told a player time and replies with
 * whatever segments it chooses. This turns one into the other. A wanted segment
 * is looked up in the cache; if it is not there, a request is issued at that
 * segment's start time and everything the server sends back is cached, so the
 * segments delivered around the wanted one serve the reads that follow.
 *
 * **A request in flight is reported as pending rather than waited on.** Media3
 * loads on its own threads and blocking one behind another thread's request is
 * how a seek turns into a freeze; [SabrSegmentPendingException] tells the load
 * error policy to retry shortly instead, and the request already running will
 * usually have filled the cache by then.
 */
class SabrMediaBridge(
    private val session: SabrSession,
    private val videoFormat: SabrProtocol.FormatId,
    private val audioFormat: SabrProtocol.FormatId,
) {

    /** Segment bytes, keyed by itag and sequence. Init segments use sequence 0. */
    private val segments = ConcurrentHashMap<Long, ByteArray>()

    /** Per-track timelines, parsed from each init segment's `sidx`. */
    private val timelines = ConcurrentHashMap<Int, SabrProtocol.SegmentIndex>()

    /** Highest sequence Media3 has asked for, per track: the buffered frontier. */
    private val wanted = ConcurrentHashMap<Int, Int>()

    private val requestLock = ReentrantLock()

    val videoTimeline: SabrProtocol.SegmentIndex? get() = timelines[videoFormat.itag]
    val audioTimeline: SabrProtocol.SegmentIndex? get() = timelines[audioFormat.itag]

    fun hasTimelines(): Boolean = videoTimeline != null && audioTimeline != null

    /** Duration reported by the stream itself, or the longer of the two timelines. */
    fun durationMs(): Long = maxOf(
        session.totalDurationMs,
        videoTimeline?.durationMs ?: 0L,
        audioTimeline?.durationMs ?: 0L,
    )

    fun initData(itag: Int): ByteArray? = segments[key(itag, 0)]

    /**
     * Fetch the init segments and parse their indexes.
     *
     * Must succeed before a manifest can be written: without a timeline there is
     * no way to state what segments exist, and therefore no way to seek.
     */
    fun prepareTimelines(initialPositionMs: Long) {
        if (hasTimelines()) return
        requestLock.lock()
        try {
            if (hasTimelines()) return
            runBlocking {
                session.requestOnce(
                    playerTimeMs = maxOf(0L, initialPositionMs),
                    tracks = listOf(
                        SabrSession.TrackRequest(audioFormat, null, 0),
                        SabrSession.TrackRequest(videoFormat, null, 0),
                    ),
                    onSegment = ::accept,
                )
            }
        } finally {
            requestLock.unlock()
        }
        Log.i(
            TAG,
            "SABR timelines: video=${videoTimeline?.size} audio=${audioTimeline?.size} " +
                "duration=${durationMs()}ms",
        )
    }

    /**
     * Return segment [sequence] of [itag], fetching it if it is not held.
     *
     * @throws SabrSegmentPendingException when another thread is already
     *   fetching, or when the request completed without delivering this exact
     *   segment - both are "ask again shortly", not failures.
     */
    fun segment(itag: Int, sequence: Int): ByteArray {
        segments[key(itag, sequence)]?.let { return it }
        wanted[itag] = maxOf(wanted[itag] ?: 0, sequence)

        if (!requestLock.tryLock()) {
            segments[key(itag, sequence)]?.let { return it }
            throw SabrSegmentPendingException("request in progress: itag=$itag seq=$sequence")
        }
        try {
            segments[key(itag, sequence)]?.let { return it }
            fetchAround(itag, sequence)
        } finally {
            requestLock.unlock()
        }
        return segments[key(itag, sequence)]
            ?: throw SabrSegmentPendingException("not delivered yet: itag=$itag seq=$sequence")
    }

    /**
     * Ask for media at the wanted segment's own start time.
     *
     * The player time is the segment's position rather than the playhead: that
     * is what makes a seek fetch the segment it landed on instead of resuming
     * from wherever playback happened to be.
     */
    private fun fetchAround(itag: Int, sequence: Int) {
        val timeline = timelines[itag]
        val playerTimeMs = timeline?.startMs(sequence)?.takeIf { it >= 0 } ?: 0L
        runBlocking {
            session.requestOnce(
                playerTimeMs = playerTimeMs,
                tracks = listOf(track(audioFormat), track(videoFormat)),
                onSegment = ::accept,
            )
        }
    }

    /**
     * What the caller holds for a track. `bufferedThrough` is one below the
     * lowest segment still wanted, so the server resumes at the wanted one
     * rather than re-sending what is already cached.
     */
    private fun track(format: SabrProtocol.FormatId): SabrSession.TrackRequest {
        val next = wanted[format.itag] ?: 0
        return SabrSession.TrackRequest(
            format = format,
            index = timelines[format.itag],
            bufferedThrough = maxOf(0, next - 1),
        )
    }

    private fun accept(segment: SabrSession.Segment) {
        if (segment.isInit) {
            segments[key(segment.itag, 0)] = segment.data
            // The `sidx` in an init segment covers the whole file, so this one
            // parse is what every later seek is answered from.
            SabrProtocol.parseSegmentIndex(segment.data)?.let {
                timelines[segment.itag] = it
            }
        } else if (segment.sequence > 0) {
            segments[key(segment.itag, segment.sequence)] = segment.data
        }
    }

    fun release() {
        session.release()
        segments.clear()
        timelines.clear()
    }

    private fun key(itag: Int, sequence: Int): Long =
        (itag.toLong() shl 32) or (sequence.toLong() and 0xFFFFFFFFL)

    companion object {
        private const val TAG = "SabrMediaBridge"
    }
}

/** Signals that another request may shortly deliver the segment Media3 asked for. */
class SabrSegmentPendingException(message: String) : IOException(message) {
    // The stack is never useful (it is always the loader) and these are thrown
    // routinely while buffering, so building one is pure cost.
    override fun fillInStackTrace(): Throwable = this
}
