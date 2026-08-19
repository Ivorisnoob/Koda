package com.ivor.ivormusic.service

import com.ivor.ivormusic.data.AudioProfile

/** A conservative, explainable decision for one automatic track change. */
data class TransitionPlan(
    val overlapMs: Long,
    val incomingStartMs: Long,
    val reason: Reason,
    val incomingSpeed: Float = 1f,
    val filterSweepStrength: Float = 0f,
    val harmonicMatch: HarmonicMatch = HarmonicMatch.UNKNOWN,
) {
    val shouldOverlap: Boolean get() = overlapMs > 0L

    enum class Reason {
        FALLBACK,
        OUTRO_BOUNDARY,
        PHRASE_BOUNDARY,
        NATURAL_FADE,
        PRESERVE_ABRUPT_END,
    }

    enum class HarmonicMatch { COMPATIBLE, NEUTRAL, CLASH, UNKNOWN }
}

/**
 * Turns cached audio measurements into safe transition parameters.
 *
 * This intentionally refuses to be clever when a measurement is absent or
 * ambiguous. A plain equal-power fade is already good; analysis only gets to
 * replace it when the signal describes an obvious musical boundary.
 */
object TransitionPlanner {

    fun plan(
        fallbackOverlapMs: Long,
        maximumOverlapMs: Long = fallbackOverlapMs,
        outgoing: AudioProfile?,
        incoming: AudioProfile?,
        outgoingDurationMs: Long = 0L,
    ): TransitionPlan {
        val fallback = fallbackOverlapMs.coerceIn(MIN_OVERLAP_MS, MAX_OVERLAP_MS)
        val maximum = maximumOverlapMs.coerceIn(fallback, MAX_OVERLAP_MS)
        val silenceStart = incoming?.leadInSilenceMs
            ?.minus(ATTACK_GUARD_MS)
            ?.coerceIn(0L, MAX_LEAD_IN_SKIP_MS)
            ?: 0L
        val tempo = tempoMatch(outgoing, incoming)
        val incomingStart = alignedIncomingStart(incoming, silenceStart)
        val harmonic = harmonicMatch(outgoing, incoming)
        val filterStrength = when (harmonic) {
            TransitionPlan.HarmonicMatch.COMPATIBLE -> 0.3f
            TransitionPlan.HarmonicMatch.NEUTRAL -> 0.45f
            TransitionPlan.HarmonicMatch.CLASH -> 0.8f
            TransitionPlan.HarmonicMatch.UNKNOWN -> 0.5f
        }

        fun result(overlap: Long, reason: TransitionPlan.Reason): TransitionPlan {
            val harmonicallySafe = if (harmonic == TransitionPlan.HarmonicMatch.CLASH) {
                minOf(overlap, CLASH_MAX_OVERLAP_MS)
            } else overlap
            val beatAligned = alignOutgoingBoundary(
                requestedLeadMs = harmonicallySafe,
                maximumLeadMs = maximum,
                durationMs = outgoingDurationMs,
                profile = outgoing,
            )
            return TransitionPlan(
                overlapMs = beatAligned,
                incomingStartMs = incomingStart,
                reason = reason,
                incomingSpeed = tempo.speed,
                filterSweepStrength = filterStrength,
                harmonicMatch = harmonic,
            )
        }

        if (outgoing == null) {
            return result(fallback, TransitionPlan.Reason.FALLBACK)
        }

        // A loud final frame is commonly a deliberate cut into the next album
        // track. Overlap would duplicate that boundary; let Media3 advance
        // gaplessly instead.
        if (outgoing.endsAbruptly) {
            return TransitionPlan(0L, incomingStart, TransitionPlan.Reason.PRESERVE_ABRUPT_END)
        }

        // Do not lay a long synthetic fade over a track already fading itself.
        // A short overlap keeps the queue moving without making the tail limp.
        if (outgoing.tailFadeMs >= NATURAL_FADE_THRESHOLD_MS) {
            val overlap = minOf(fallback, NATURAL_FADE_OVERLAP_MS)
            return result(overlap, TransitionPlan.Reason.NATURAL_FADE)
        }

        val phrase = outgoing.phraseOutroLeadMs
        if (outgoing.phraseConfidence >= MIN_PHRASE_CONFIDENCE &&
            phrase in MIN_OUTRO_BOUNDARY_MS..MAX_OUTRO_BOUNDARY_MS
        ) {
            return result(minOf(maximum, phrase), TransitionPlan.Reason.PHRASE_BOUNDARY)
        }

        // A measured outro is more useful than an arbitrary duration: begin
        // where the energy actually falls, up to AutoMix's conservative cap.
        val outro = outgoing.outroLeadMs
        if (outro in MIN_OUTRO_BOUNDARY_MS..MAX_OUTRO_BOUNDARY_MS) {
            return result(
                minOf(maximum, outro).coerceAtLeast(MIN_OVERLAP_MS),
                TransitionPlan.Reason.OUTRO_BOUNDARY,
            )
        }

        return result(fallback, TransitionPlan.Reason.FALLBACK)
    }

    private data class TempoMatch(val speed: Float)

    private fun tempoMatch(outgoing: AudioProfile?, incoming: AudioProfile?): TempoMatch {
        val out = outgoing?.outroBpm ?: return TempoMatch(1f)
        val rawIn = incoming?.bpm ?: return TempoMatch(1f)
        if (outgoing.outroTempoConfidence < MIN_TEMPO_CONFIDENCE ||
            incoming.tempoConfidence < MIN_TEMPO_CONFIDENCE
        ) return TempoMatch(1f)

        val equivalent = listOf(rawIn * 0.5f, rawIn, rawIn * 2f)
            .minByOrNull { kotlin.math.abs(out / it - 1f) } ?: rawIn
        val ratio = out / equivalent
        return if (ratio in MIN_TEMPO_SPEED..MAX_TEMPO_SPEED) TempoMatch(ratio)
        else TempoMatch(1f)
    }

    private fun alignedIncomingStart(
        incoming: AudioProfile?,
        minimumStartMs: Long,
    ): Long {
        val bpm = incoming?.bpm ?: return minimumStartMs
        if (incoming.tempoConfidence < MIN_GRID_CONFIDENCE) return minimumStartMs
        // Seek positions are expressed on the source timeline. Playback speed
        // changes how long the bar takes after starting, not where it lives.
        val barMsAtPlayback = (4f * 60_000f / bpm).toLong().coerceAtLeast(1L)
        var downbeat = incoming.downbeatOffsetMs
        while (downbeat < minimumStartMs) downbeat += barMsAtPlayback
        return downbeat.coerceIn(minimumStartMs, MAX_LEAD_IN_SKIP_MS)
    }

    private fun alignOutgoingBoundary(
        requestedLeadMs: Long,
        maximumLeadMs: Long,
        durationMs: Long,
        profile: AudioProfile?,
    ): Long {
        val bpm = profile?.outroBpm ?: return requestedLeadMs
        if (durationMs <= 0 || profile.outroTempoConfidence < MIN_GRID_CONFIDENCE) {
            return requestedLeadMs
        }
        val barMs = 4.0 * 60_000.0 / bpm
        val desiredPosition = durationMs - requestedLeadMs
        val lastDownbeat = durationMs - profile.outroDownbeatLeadMs
        val bar = kotlin.math.round((desiredPosition - lastDownbeat) / barMs)
        val boundary = lastDownbeat + (bar * barMs).toLong()
        val lead = durationMs - boundary
        return if (lead in MIN_OVERLAP_MS..maximumLeadMs) lead else requestedLeadMs
    }

    private fun harmonicMatch(
        outgoing: AudioProfile?,
        incoming: AudioProfile?,
    ): TransitionPlan.HarmonicMatch {
        val a = outgoing?.keyPitchClass ?: return TransitionPlan.HarmonicMatch.UNKNOWN
        val b = incoming?.keyPitchClass ?: return TransitionPlan.HarmonicMatch.UNKNOWN
        if (outgoing.keyConfidence < MIN_KEY_CONFIDENCE ||
            incoming.keyConfidence < MIN_KEY_CONFIDENCE
        ) return TransitionPlan.HarmonicMatch.UNKNOWN
        val interval = ((b - a) % 12 + 12) % 12
        val sameMode = outgoing.keyMode == incoming.keyMode
        return when {
            interval == 0 && sameMode -> TransitionPlan.HarmonicMatch.COMPATIBLE
            interval == 5 || interval == 7 -> TransitionPlan.HarmonicMatch.COMPATIBLE
            interval == 0 || interval == 3 || interval == 9 -> TransitionPlan.HarmonicMatch.NEUTRAL
            else -> TransitionPlan.HarmonicMatch.CLASH
        }
    }

    private const val MIN_OVERLAP_MS = 750L
    private const val MAX_OVERLAP_MS = 12_000L
    private const val MIN_OUTRO_BOUNDARY_MS = 750L
    private const val MAX_OUTRO_BOUNDARY_MS = 12_000L
    private const val NATURAL_FADE_THRESHOLD_MS = 900L
    private const val NATURAL_FADE_OVERLAP_MS = 1_000L
    private const val MAX_LEAD_IN_SKIP_MS = 7_500L
    private const val ATTACK_GUARD_MS = 60L
    private const val MIN_TEMPO_CONFIDENCE = 0.35f
    private const val MIN_GRID_CONFIDENCE = 0.4f
    private const val MIN_PHRASE_CONFIDENCE = 0.4f
    private const val MIN_KEY_CONFIDENCE = 0.08f
    private const val MIN_TEMPO_SPEED = 0.96f
    private const val MAX_TEMPO_SPEED = 1.04f
    private const val CLASH_MAX_OVERLAP_MS = 3_000L
}
