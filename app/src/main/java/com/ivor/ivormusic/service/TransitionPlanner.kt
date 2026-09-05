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
        TEMPO_MISMATCH,
        PRESERVE_ALBUM,
        SHORT_TRACK,
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
        preserveAlbumSequence: Boolean = false,
        incomingDurationMs: Long = incoming?.durationMs ?: 0L,
    ): TransitionPlan {
        val fallback = fallbackOverlapMs.coerceIn(MIN_OVERLAP_MS, MAX_OVERLAP_MS)
        var maximum = maximumOverlapMs.coerceIn(fallback, MAX_OVERLAP_MS)
        // Do not consume most of an interlude or a short incoming track.
        if (outgoingDurationMs > 0L) maximum = minOf(maximum, outgoingDurationMs / 4L)
        if (incomingDurationMs > 0L) maximum = minOf(maximum, incomingDurationMs / 4L)
        if (maximum < MIN_OVERLAP_MS) {
            return TransitionPlan(0L, 0L, TransitionPlan.Reason.SHORT_TRACK)
        }
        if (preserveAlbumSequence) {
            return TransitionPlan(0L, 0L, TransitionPlan.Reason.PRESERVE_ALBUM)
        }
        val silenceStart = (incoming?.leadInSilenceMs
            ?.minus(ATTACK_GUARD_MS)
            ?.coerceIn(0L, MAX_LEAD_IN_SKIP_MS)
            ?: 0L).coerceAtMost(
                if (incomingDurationMs > 0L) incomingDurationMs / 4L else MAX_LEAD_IN_SKIP_MS
            )
        val tempo = tempoMatch(outgoing, incoming)
        val harmonic = harmonicMatch(outgoing, incoming)
        val alignBars = (outgoing?.outroDownbeatConfidence ?: 0f) in MIN_GRID_CONFIDENCE..1f &&
            (incoming?.downbeatConfidence ?: 0f) in MIN_GRID_CONFIDENCE..1f
        val filterStrength = if (!tempo.compatible) 0f else when (harmonic) {
            TransitionPlan.HarmonicMatch.COMPATIBLE -> 0.3f
            TransitionPlan.HarmonicMatch.NEUTRAL -> 0.45f
            TransitionPlan.HarmonicMatch.CLASH -> 0.8f
            TransitionPlan.HarmonicMatch.UNKNOWN -> 0f
        }

        fun result(overlap: Long, reason: TransitionPlan.Reason): TransitionPlan {
            val harmonicallySafe = minOf(maximum, if (harmonic == TransitionPlan.HarmonicMatch.CLASH) {
                minOf(overlap, CLASH_MAX_OVERLAP_MS)
            } else overlap)
            // Beat snapping must never undo a reason-specific safety cap. A
            // natural fade stays at one second and a key clash stays at three.
            val ruleMaximum = minOf(maximum, harmonicallySafe)
            val canProcess = tempo.compatible && harmonicallySafe > NATURAL_FADE_OVERLAP_MS
            val cue = incomingCue(incoming, silenceStart, if (canProcess) tempo.speed else 1f, alignBars)
            val beatAligned = if (canProcess) alignOutgoingBoundary(
                requestedLeadMs = harmonicallySafe,
                maximumLeadMs = ruleMaximum,
                durationMs = outgoingDurationMs,
                profile = outgoing,
                incomingDownbeatDelayMs = cue.downbeatDelayMs,
                alignBars = alignBars,
            ) else harmonicallySafe
            return TransitionPlan(
                overlapMs = beatAligned,
                incomingStartMs = cue.startMs,
                reason = reason,
                incomingSpeed = if (canProcess) tempo.speed else 1f,
                filterSweepStrength = if (canProcess) filterStrength else 0f,
                harmonicMatch = harmonic,
                incomingDownbeatDelayMs = if (canProcess) cue.downbeatDelayMs else 0L,
            )
        }

        if (outgoing == null) {
            return result(minOf(fallback, maximum), TransitionPlan.Reason.FALLBACK)
        }

        // A loud final frame is commonly a deliberate cut into the next album
        // track. Overlap would duplicate that boundary; let Media3 advance
        // gaplessly instead.
        if (outgoing.endsAbruptly && preserveAbruptEnding) {
            return TransitionPlan(0L, 0L, TransitionPlan.Reason.PRESERVE_ABRUPT_END)
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
                overlapMs = minOf(maximum, maxOf(minOf(fallback, NATURAL_FADE_OVERLAP_MS), MIN_OVERLAP_MS)),
                incomingStartMs = silenceStart,
                reason = TransitionPlan.Reason.SILENCE_SKIP,
                incomingSpeed = 1f,
                filterSweepStrength = 0f,
                harmonicMatch = harmonic,
                incomingDownbeatDelayMs = 0L,
                prepareLeadMs = if (outgoingDurationMs > 0L) {
                    minOf(lead, outgoingDurationMs * 2L / 3L)
                } else lead,
            )
        }

        if (tempo.confident && !tempo.compatible) {
            return result(minOf(fallback, 1_500L), TransitionPlan.Reason.TEMPO_MISMATCH)
        }

        // Long overlaps require a reliable matching pulse on both sides.
        // An energy dip alone can be a breath between lines, not an outro.
        if (!tempo.compatible) {
            return result(minOf(fallback, maximum), TransitionPlan.Reason.FALLBACK)
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

    private data class TempoMatch(
        val speed: Float = 1f,
        val confident: Boolean = false,
        val compatible: Boolean = false,
    )

    private fun tempoMatch(outgoing: AudioProfile?, incoming: AudioProfile?): TempoMatch {
        val out = outgoing?.outroBpm ?: return TempoMatch()
        val rawIn = incoming?.bpm ?: return TempoMatch()
        if (!out.isFinite() || out <= 0f || !rawIn.isFinite() || rawIn <= 0f ||
            outgoing.outroTempoConfidence !in MIN_TEMPO_CONFIDENCE..1f ||
            incoming.tempoConfidence !in MIN_TEMPO_CONFIDENCE..1f
        ) return TempoMatch()

        val equivalent = listOf(rawIn * 0.5f, rawIn, rawIn * 2f)
            .minByOrNull { kotlin.math.abs(out / it - 1f) } ?: rawIn
        val ratio = out / equivalent
        return if (ratio in MIN_TEMPO_SPEED..MAX_TEMPO_SPEED) {
            TempoMatch(ratio, confident = true, compatible = true)
        } else TempoMatch(confident = true)
    }

    private data class IncomingCue(val startMs: Long, val downbeatDelayMs: Long)

    private fun incomingCue(
        incoming: AudioProfile?,
        minimumStartMs: Long,
        speed: Float,
        alignBars: Boolean,
    ): IncomingCue {
        val bpm = incoming?.bpm ?: return IncomingCue(minimumStartMs, 0L)
        val offsetMs = if (alignBars) incoming.downbeatOffsetMs else incoming.beatOffsetMs
        if (!bpm.isFinite() || bpm <= 0f ||
            incoming.tempoConfidence !in MIN_GRID_CONFIDENCE..1f ||
            offsetMs !in 0L..MAX_LEAD_IN_SKIP_MS
        ) {
            return IncomingCue(minimumStartMs, 0L)
        }
        // Seek positions are expressed on the source timeline. Playback speed
        // changes how long the bar takes after starting, not where it lives.
        val beatsPerGrid = if (alignBars) 4f else 1f
        val barMsOnSource = (beatsPerGrid * 60_000f / bpm).toLong().coerceAtLeast(1L)
        var downbeat = offsetMs
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
        alignBars: Boolean,
    ): Long {
        val bpm = profile?.outroBpm ?: return requestedLeadMs
        if (durationMs <= 0 || !bpm.isFinite() || bpm <= 0f ||
            profile.outroTempoConfidence !in MIN_GRID_CONFIDENCE..1f
        ) {
            return requestedLeadMs
        }
        val barMs = (if (alignBars) 4.0 else 1.0) * 60_000.0 / bpm
        // Align the incoming downbeat, not necessarily the beginning of its
        // audio. A pickup can now play during the first part of the overlap.
        val desiredDownbeatPosition = durationMs - requestedLeadMs + incomingDownbeatDelayMs
        val lastDownbeat = durationMs -
            if (alignBars) profile.outroDownbeatLeadMs else profile.outroBeatLeadMs
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
        if (outgoingConfidence !in MIN_KEY_CONFIDENCE..1f ||
            incoming.keyConfidence !in MIN_KEY_CONFIDENCE..1f ||
            a !in 0..11 || b !in 0..11 ||
            outgoingMode !in listOf("major", "minor") ||
            incoming.keyMode !in listOf("major", "minor")
        ) return TransitionPlan.HarmonicMatch.UNKNOWN
        val interval = ((b - a) % 12 + 12) % 12
        val sameMode = outgoingMode == incoming.keyMode
        return when {
            interval == 0 && sameMode -> TransitionPlan.HarmonicMatch.COMPATIBLE
            sameMode && (interval == 5 || interval == 7) -> TransitionPlan.HarmonicMatch.COMPATIBLE
            !sameMode && ((outgoingMode == "major" && interval == 9) ||
                (outgoingMode == "minor" && interval == 3)) -> TransitionPlan.HarmonicMatch.COMPATIBLE
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
