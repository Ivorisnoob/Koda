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
    /** Wall-clock delay from overlap start to the incoming downbeat. */
    val incomingDownbeatDelayMs: Long = 0L,
    /**
     * How far before the track's end the transition may be prepared and held
     * ready, when that distance is larger than [overlapMs]. Only the silence
     * skip sets this: the engine waits out the dead air, then runs a normal
     * length fade at the point the music actually stopped.
     */
    val prepareLeadMs: Long = 0L,
) {
    val shouldOverlap: Boolean get() = overlapMs > 0L

    val effectivePrepareLeadMs: Long
        get() = if (prepareLeadMs > overlapMs) prepareLeadMs else overlapMs

    enum class Reason {
        FALLBACK,
        OUTRO_BOUNDARY,
        PHRASE_BOUNDARY,
        NATURAL_FADE,
        ABRUPT_END,
        PRESERVE_ABRUPT_END,
        SILENCE_SKIP,
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
        preserveAbruptEnding: Boolean = false,
    ): TransitionPlan {
        val fallback = fallbackOverlapMs.coerceIn(MIN_OVERLAP_MS, MAX_OVERLAP_MS)
        val maximum = maximumOverlapMs.coerceIn(fallback, MAX_OVERLAP_MS)
        val silenceStart = incoming?.leadInSilenceMs
            ?.minus(ATTACK_GUARD_MS)
            ?.coerceIn(0L, MAX_LEAD_IN_SKIP_MS)
            ?: 0L
        val tempo = tempoMatch(outgoing, incoming)
        val harmonic = harmonicMatch(outgoing, incoming)
        val cue = incomingCue(incoming, silenceStart, tempo.speed)
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
            // Beat snapping must never undo a reason-specific safety cap. A
            // natural fade stays at one second and a key clash stays at three.
            val ruleMaximum = minOf(maximum, harmonicallySafe)
            val beatAligned = alignOutgoingBoundary(
                requestedLeadMs = harmonicallySafe,
                maximumLeadMs = ruleMaximum,
                durationMs = outgoingDurationMs,
                profile = outgoing,
                incomingDownbeatDelayMs = cue.downbeatDelayMs,
            )
            return TransitionPlan(
                overlapMs = beatAligned,
                incomingStartMs = cue.startMs,
                reason = reason,
                incomingSpeed = tempo.speed,
                filterSweepStrength = filterStrength,
                harmonicMatch = harmonic,
                incomingDownbeatDelayMs = cue.downbeatDelayMs,
            )
        }

        if (outgoing == null) {
            return result(fallback, TransitionPlan.Reason.FALLBACK)
        }

        // A loud final frame is commonly a deliberate cut into the next album
        // track. Overlap would duplicate that boundary; let Media3 advance
        // gaplessly instead.
        if (outgoing.endsAbruptly && preserveAbruptEnding) {
            return TransitionPlan(0L, cue.startMs, TransitionPlan.Reason.PRESERVE_ABRUPT_END)
        }
        if (outgoing.endsAbruptly) {
            return result(
                minOf(fallback, ABRUPT_END_OVERLAP_MS),
                TransitionPlan.Reason.ABRUPT_END,
            )
        }

        // Do not lay a long synthetic fade over a track already fading itself.
        // A short overlap keeps the queue moving without making the tail limp.
        if (outgoing.tailFadeMs >= NATURAL_FADE_THRESHOLD_MS) {
            val overlap = minOf(fallback, NATURAL_FADE_OVERLAP_MS)
            return result(overlap, TransitionPlan.Reason.NATURAL_FADE)
        }

        // Dead air. The music has stopped and what follows is measured
        // silence, so the transition belongs at the point the music actually
        // ended - up to a minute before the track's nominal end - with the
        // fade running its usual short length once there. This is AutoMix's
        // alone: manual skips keep their own timing and never wait out dead
        // air they chose to skip through.
        if (outgoing.trailingSilenceMs >= MIN_SILENCE_SKIP_MS) {
            val lead = minOf(outgoing.trailingSilenceMs, MAX_SILENCE_SKIP_MS)
            return TransitionPlan(
                overlapMs = maxOf(minOf(fallback, NATURAL_FADE_OVERLAP_MS), MIN_OVERLAP_MS),
                incomingStartMs = cue.startMs,
                reason = TransitionPlan.Reason.SILENCE_SKIP,
                incomingSpeed = tempo.speed,
                filterSweepStrength = filterStrength,
                harmonicMatch = harmonic,
                incomingDownbeatDelayMs = cue.downbeatDelayMs,
                prepareLeadMs = lead,
            )
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

    private data class IncomingCue(val startMs: Long, val downbeatDelayMs: Long)

    private fun incomingCue(
        incoming: AudioProfile?,
        minimumStartMs: Long,
        speed: Float,
    ): IncomingCue {
        val bpm = incoming?.bpm ?: return IncomingCue(minimumStartMs, 0L)
        if (incoming.tempoConfidence < MIN_GRID_CONFIDENCE) {
            return IncomingCue(minimumStartMs, 0L)
        }
        // Seek positions are expressed on the source timeline. Playback speed
        // changes how long the bar takes after starting, not where it lives.
        val barMsOnSource = (4f * 60_000f / bpm).toLong().coerceAtLeast(1L)
        var downbeat = incoming.downbeatOffsetMs
        while (downbeat < minimumStartMs) downbeat += barMsOnSource
        // Clamping a later downbeat to exactly 15 seconds creates a timestamp
        // that is not on the grid. Keep the audible cue and decline alignment.
        if (downbeat > MAX_LEAD_IN_SKIP_MS) return IncomingCue(minimumStartMs, 0L)
        val sourceDelay = (downbeat - minimumStartMs).coerceAtLeast(0L)
        val playbackDelay = (sourceDelay / speed.coerceAtLeast(0.01f)).toLong()
        return IncomingCue(minimumStartMs, playbackDelay)
    }

    private fun alignOutgoingBoundary(
        requestedLeadMs: Long,
        maximumLeadMs: Long,
        durationMs: Long,
        profile: AudioProfile?,
        incomingDownbeatDelayMs: Long,
    ): Long {
        val bpm = profile?.outroBpm ?: return requestedLeadMs
        if (durationMs <= 0 || profile.outroTempoConfidence < MIN_GRID_CONFIDENCE) {
            return requestedLeadMs
        }
        val barMs = 4.0 * 60_000.0 / bpm
        // Align the incoming downbeat, not necessarily the beginning of its
        // audio. A pickup can now play during the first part of the overlap.
        val desiredDownbeatPosition = durationMs - requestedLeadMs + incomingDownbeatDelayMs
        val lastDownbeat = durationMs - profile.outroDownbeatLeadMs
        val nearestBar = kotlin.math.round((desiredDownbeatPosition - lastDownbeat) / barMs).toLong()
        return ((nearestBar - 2)..(nearestBar + 2))
            .map { bar ->
                val alignedDownbeat = lastDownbeat + (bar * barMs).toLong()
                val transitionStart = alignedDownbeat - incomingDownbeatDelayMs
                durationMs - transitionStart
            }
            .filter {
                it in maxOf(MIN_OVERLAP_MS, incomingDownbeatDelayMs)..maximumLeadMs
            }
            .minByOrNull { kotlin.math.abs(it - requestedLeadMs) }
            ?: requestedLeadMs
    }

    private fun harmonicMatch(
        outgoing: AudioProfile?,
        incoming: AudioProfile?,
    ): TransitionPlan.HarmonicMatch {
        val outgoingProfile = outgoing ?: return TransitionPlan.HarmonicMatch.UNKNOWN
        val a = outgoingProfile.outroKeyPitchClass ?: outgoingProfile.keyPitchClass
            ?: return TransitionPlan.HarmonicMatch.UNKNOWN
        val b = incoming?.keyPitchClass ?: return TransitionPlan.HarmonicMatch.UNKNOWN
        val outgoingConfidence = if (outgoingProfile.outroKeyPitchClass != null) {
            outgoingProfile.outroKeyConfidence
        } else {
            outgoingProfile.keyConfidence
        }
        val outgoingMode = outgoingProfile.outroKeyMode ?: outgoingProfile.keyMode
        if (outgoingConfidence < MIN_KEY_CONFIDENCE ||
            incoming.keyConfidence < MIN_KEY_CONFIDENCE
        ) return TransitionPlan.HarmonicMatch.UNKNOWN
        val interval = ((b - a) % 12 + 12) % 12
        val sameMode = outgoingMode == incoming.keyMode
        return when {
            interval == 0 && sameMode -> TransitionPlan.HarmonicMatch.COMPATIBLE
            interval == 5 || interval == 7 -> TransitionPlan.HarmonicMatch.COMPATIBLE
            interval == 0 || interval == 3 || interval == 9 -> TransitionPlan.HarmonicMatch.NEUTRAL
            else -> TransitionPlan.HarmonicMatch.CLASH
        }
    }

    private const val MIN_OVERLAP_MS = 750L
    private const val MAX_OVERLAP_MS = 15_000L
    private const val MIN_OUTRO_BOUNDARY_MS = 750L
    private const val MAX_OUTRO_BOUNDARY_MS = 15_000L
    private const val NATURAL_FADE_THRESHOLD_MS = 900L
    private const val NATURAL_FADE_OVERLAP_MS = 1_000L
    private const val ABRUPT_END_OVERLAP_MS = 750L
    private const val MAX_LEAD_IN_SKIP_MS = 15_000L
    private const val ATTACK_GUARD_MS = 60L
    private const val MIN_TEMPO_CONFIDENCE = 0.35f
    private const val MIN_GRID_CONFIDENCE = 0.4f
    private const val MIN_PHRASE_CONFIDENCE = 0.4f
    private const val MIN_KEY_CONFIDENCE = 0.08f
    private const val MIN_TEMPO_SPEED = 0.96f
    private const val MAX_TEMPO_SPEED = 1.04f
    private const val CLASH_MAX_OVERLAP_MS = 3_000L

    /** Dead air worth special handling starts where the ordinary outro
     *  boundary search gives up; it may extend to a full minute. */
    private const val MIN_SILENCE_SKIP_MS = 16_000L
    private const val MAX_SILENCE_SKIP_MS = 60_000L
}
