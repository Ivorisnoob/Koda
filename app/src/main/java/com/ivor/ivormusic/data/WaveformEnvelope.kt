package com.ivor.ivormusic.data

import java.util.Base64
import kotlin.math.sqrt

/**
 * One song's amplitude envelope: [BUCKETS] peak levels spread across its duration,
 * which is what the waveform seek bar draws instead of a plain track.
 *
 * Levels are measured rather than synthesised, by either of two paths. [WaveformAnalyzer]
 * decodes the whole track at load and publishes a complete envelope through
 * [Companion.measured], which is what lets the bar show the loud and quiet parts of a
 * song *before* they arrive. [com.ivor.ivormusic.service.WaveformTap] folds Koda's own
 * playing PCM in through [record], and is the fallback for the songs that pass cannot
 * reach - an unsupported container, a stream on mobile data - so a song still gains its
 * shape by being listened to.
 *
 * A bucket holds the loudest sample seen in it, never an average of the visits, so
 * replaying a song cannot erode its own peaks. Byte 0 means "never measured" and is
 * distinct from a measured silence, which encodes as 1.
 */
class WaveformEnvelope internal constructor(internal val levels: ByteArray) {

    /**
     * How many buckets hold a measurement, maintained rather than counted.
     *
     * [coverage] is read on every draw decision and [isComplete] on every recorded sample, so
     * a 512-byte scan at either of those rates is work for an answer that changes at most once
     * per sample.
     */
    @Volatile
    private var measuredBuckets: Int = levels.count { it != UNMEASURED }

    /** Fraction of the song that has been measured at least once. */
    val coverage: Float
        get() = measuredBuckets.toFloat() / BUCKETS

    /**
     * Every bucket holds a measurement, so the drawn bar is the whole song rather than the part
     * of it somebody has heard.
     *
     * This is what [WaveformStore] gates its live recording on: once a song has been measured
     * end to end there is nothing left for a listening sample to add, and letting one in anyway
     * would mix two measurements taken through different paths - the file's own PCM against
     * whatever the sink was fed after gain and normalisation - into one envelope.
     */
    val isComplete: Boolean
        get() = measuredBuckets == BUCKETS

    /**
     * Fold one measured peak into the bucket holding [fraction].
     * Returns whether this actually changed the envelope, so callers can avoid
     * republishing and rewriting an envelope that has stopped growing.
     */
    fun record(fraction: Float, peak: Float): Boolean {
        if (!fraction.isFinite() || fraction < 0f || fraction > 1f) return false
        if (!peak.isFinite() || peak < 0f) return false
        val bucket = (fraction * BUCKETS).toInt().coerceIn(0, BUCKETS - 1)
        val encoded = encode(peak)
        synchronized(levels) {
            val existing = levels[bucket].toInt() and 0xFF
            if (encoded <= existing) return false
            if (existing == 0) measuredBuckets++
            levels[bucket] = encoded.toByte()
        }
        return true
    }

    /**
     * Resample to [count] drawable bars in 0..1.
     *
     * Unmeasured stretches are interpolated from their nearest measured neighbours rather than
     * drawn empty, so a song part-way through its first listen still reads as one continuous
     * waveform instead of a half-built one.
     *
     * [scar] Two things here used to flatten the drawn bar until it read as a rectangle, and
     * both were smoothing dressed up as correctness.
     *
     * Buckets inside one bar were reduced by *arithmetic mean*, a low-pass filter over exactly
     * the detail the bar exists to show. They are reduced by quadratic mean now - which, since a
     * bucket already holds an RMS, is an average of energy rather than of amplitude, so a loud
     * moment lifts its bar instead of being diluted by its quiet neighbours. A plain maximum was
     * tried in between and is worse than either: at roughly four buckets to a bar it picks the
     * local loudest every time, so every bar converges on the same value and the whole point is
     * lost. It preserves an isolated transient and destroys general contrast.
     *
     * And levels were divided by the loudest bucket, which only guarantees that *one* bar reaches
     * the top: a song whose quietest passage is already half its loudest still drew between 0.5
     * and 1.0, a band far too narrow to see. They are contrast-stretched between two percentiles
     * of this song's own distribution instead, so every song uses the full height whatever its
     * mastering. Percentiles are taken over the bars, after reduction, since those are what is
     * actually drawn.
     */
    fun bars(count: Int): FloatArray {
        if (count <= 0) return FloatArray(0)
        val snapshot = synchronized(levels) { levels.copyOf() }
        val raw = FloatArray(count) { Float.NaN }
        var anyMeasured = false
        for (bar in 0 until count) {
            val from = bar * BUCKETS / count
            val until = maxOf(((bar + 1) * BUCKETS / count), from + 1).coerceAtMost(BUCKETS)
            var energy = 0.0
            var measured = 0
            for (bucket in from until until) {
                val value = snapshot[bucket]
                if (value == UNMEASURED) continue
                val level = decode(value)
                energy += level.toDouble() * level
                measured++
            }
            raw[bar] = if (measured == 0) Float.NaN else sqrt(energy / measured).toFloat()
            if (measured > 0) anyMeasured = true
        }
        if (!anyMeasured) return FloatArray(count) { RESTING }
        interpolateGaps(raw)

        val sorted = raw.copyOf()
        sorted.sort()
        var floor = percentile(sorted, FLOOR_PERCENTILE)
        var ceiling = percentile(sorted, CEILING_PERCENTILE)
        if (ceiling - floor <= MIN_SPAN) {
            // The trimmed window collapsed, which happens when a song is level almost throughout
            // and its few loud moments are the very outliers the trim discards. Fall back to the
            // whole range so those moments are exactly what stands out.
            floor = sorted.first()
            ceiling = sorted.last()
        }
        val span = ceiling - floor
        // Genuinely uniform: there is no contrast to recover, only measurement noise to amplify
        // into dynamics the song does not have. Draw it as the solid band it honestly is.
        if (span <= MIN_SPAN) return FloatArray(count) { UNIFORM_LEVEL }
        return FloatArray(count) {
            ((raw[it] - floor) / span).coerceIn(0f, 1f).coerceAtLeast(RESTING)
        }
    }

    /**
     * An independent copy, taken under the lock.
     *
     * What the seek bar draws has to be frozen: the same object is still being folded into by
     * the live sampler on another thread, and a bar whose levels change under it is exactly the
     * moving waveform this exists to stop. A snapshot also lets the drawing side memoize its
     * resampled bars, since nothing behind them can change.
     */
    fun snapshot(): WaveformEnvelope =
        WaveformEnvelope(synchronized(levels) { levels.copyOf() })

    /**
     * [java.util.Base64] rather than `android.util.Base64` on purpose: the android.jar the
     * JVM tests run against is stubbed, so the platform one would encode every envelope to
     * nothing in a test that still passed. Real on API 26 and up; minSdk here is 30.
     */
    fun encodeToString(): String =
        Base64.getEncoder().encodeToString(synchronized(levels) { levels.copyOf() })

    companion object {

        /** Fill NaN runs by walking in from whichever measured neighbours exist. */
        private fun interpolateGaps(values: FloatArray) {
            var index = 0
            while (index < values.size) {
                if (!values[index].isNaN()) {
                    index++
                    continue
                }
                var end = index
                while (end < values.size && values[end].isNaN()) end++
                val before = if (index > 0) values[index - 1] else Float.NaN
                val after = if (end < values.size) values[end] else Float.NaN
                for (gap in index until end) {
                    values[gap] = when {
                        before.isNaN() && after.isNaN() -> RESTING
                        before.isNaN() -> after
                        after.isNaN() -> before
                        else -> {
                            val step = (gap - index + 1).toFloat() / (end - index + 1)
                            before + (after - before) * step
                        }
                    }
                }
                index = end
            }
        }

        /**
         * Roughly four buckets per drawn bar on a phone-width track, so resampling reduces real
         * measurements rather than stretching a handful of them. Half a kilobyte per song.
         *
         * Changing this invalidates every stored envelope by design: [decodeFromString] refuses
         * an array of the wrong length, so a build with a different count remeasures rather than
         * reading an old one at the wrong scale.
         */
        const val BUCKETS = 512

        /** What an unmeasured or silent stretch draws as, so the bar always reads as a track. */
        const val RESTING = 0.06f

        private const val UNMEASURED: Byte = 0

        /**
         * The window the contrast stretch maps onto the full height. Trimmed at both ends so one
         * clipped transient or one silent gap cannot set the scale for the whole song.
         */
        private const val FLOOR_PERCENTILE = 0.10f
        private const val CEILING_PERCENTILE = 0.98f

        /** Below this the song has no dynamics worth stretching, only noise to amplify. */
        private const val MIN_SPAN = 0.02f

        /** What a genuinely level song draws as: a solid band, neither a line nor a full block. */
        private const val UNIFORM_LEVEL = 0.7f

        private fun percentile(sorted: FloatArray, fraction: Float): Float {
            if (sorted.isEmpty()) return 0f
            val index = ((sorted.size - 1) * fraction).toInt().coerceIn(0, sorted.size - 1)
            return sorted[index]
        }

        fun empty() = WaveformEnvelope(ByteArray(BUCKETS))

        /**
         * A whole-song envelope from an offline measurement pass, or null when the pass reached
         * nothing usable.
         *
         * [levels] is one RMS per bucket in 0..1 with `NaN` where the decoder produced no audio
         * for that stretch. Those gaps are interpolated *here* rather than at draw time, so the
         * envelope this returns is genuinely complete: [isComplete] holds, which is the signal
         * the seek bar draws on and the signal [WaveformStore] stops recording live samples on.
         * A decoder that skipped a bucket at the very end of a track must not leave the song
         * looking part-heard forever.
         */
        fun measured(levels: FloatArray): WaveformEnvelope? {
            if (levels.size != BUCKETS) return null
            if (levels.none { !it.isNaN() }) return null
            val filled = levels.copyOf()
            interpolateGaps(filled)
            return WaveformEnvelope(ByteArray(BUCKETS) { encode(filled[it]).toByte() })
        }

        fun decodeFromString(value: String?): WaveformEnvelope? {
            if (value.isNullOrBlank()) return null
            return try {
                Base64.getDecoder().decode(value)
                    .takeIf { it.size == BUCKETS }
                    ?.let { WaveformEnvelope(it) }
            } catch (_: IllegalArgumentException) {
                null
            }
        }

        /** 0 is reserved for "never measured", so a measured silence still encodes as 1. */
        internal fun encode(peak: Float): Int =
            1 + (peak.coerceIn(0f, 1f) * 254f).toInt().coerceIn(0, 254)

        internal fun decode(value: Byte): Float = ((value.toInt() and 0xFF) - 1) / 254f
    }
}
