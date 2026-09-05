package com.ivor.ivormusic.data

import kotlinx.serialization.Serializable

/**
 * What the shape of a track's audio says about how to transition into and out
 * of it.
 *
 * **This is the half a crossfade cannot guess.** A fixed "overlap the last
 * three seconds" is why crossfades sound wrong: it cuts into final choruses,
 * lays a fade over tracks that already fade themselves, fades into three
 * seconds of lead-in silence, and destroys album transitions that were
 * deliberately continuous. Every field here exists to answer one of those, and
 * they come from short PCM windows at the head and tail: an RMS envelope,
 * onset autocorrelation for rhythm, and FFT chroma for harmonic content.
 *
 * Absent for a track never profiled, and everything downstream has to work
 * without one: the fallback is a plain equal-power fade at the user's chosen
 * duration, which is what the engine did before profiles existed.
 */
@Serializable
data class AudioProfile(
    val songId: String,

    /** Duration reported by the analysed source, used to bound short-track mixes. */
    val durationMs: Long = 0L,

    /**
     * Silence at the very start, in milliseconds.
     *
     * Fading *into* silence wastes the overlap and reads as a gap, so the
     * incoming track starts here rather than at zero.
     */
    val leadInSilenceMs: Long = 0,

    /**
     * How long the track spends fading itself out at the end, or zero.
     *
     * A track that already decays to silence needs little or no help; laying a
     * second fade over the first makes the ending limp rather than smooth.
     */
    val tailFadeMs: Long = 0,

    /**
     * True when the track is still at real energy in its final moments.
     *
     * It can signal a deliberate segue when the queue also supplies album
     * continuity. Numbered album sequences are preserved independently;
     * an unrelated loud ending gets only a brief overlap.
     */
    val endsAbruptly: Boolean = false,

    /**
     * Where the outro starts, as milliseconds before the end of the track.
     *
     * The last point at which the energy drops and stays down, so an overlap
     * anchored here begins at a musical boundary instead of over the middle of
     * a phrase. Zero when no such point was found, which means "use the
     * duration the user asked for".
     */
    val outroLeadMs: Long = 0,

    /**
     * Detected intro tempo, for beat-matched transitions. Null when the estimate was
     * not confident enough to act on - a wrong tempo is worse than none,
     * because nudging playback speed to match it is audible.
     */
    val bpm: Float? = null,

    /** Reliability of [bpm], from zero to one. */
    val tempoConfidence: Float = 0f,

    /** First detected beat and downbeat positions from the start of the file. */
    val beatOffsetMs: Long = 0,
    val downbeatOffsetMs: Long = 0,
    val downbeatConfidence: Float = 0f,

    /** Tempo/grid measured at the outro, avoiding whole-song drift. */
    val outroBpm: Float? = null,
    val outroTempoConfidence: Float = 0f,
    val outroDownbeatLeadMs: Long = 0,
    val outroBeatLeadMs: Long = 0,
    val outroDownbeatConfidence: Float = 0f,

    /** A musically useful outro phrase boundary, measured back from the end. */
    val phraseOutroLeadMs: Long = 0,
    val phraseConfidence: Float = 0f,

    /** Krumhansl-Schmuckler key estimate. Pitch class is C=0 through B=11. */
    val keyPitchClass: Int? = null,
    val keyMode: String? = null,
    val keyConfidence: Float = 0f,

    /** Key measured at the outro; songs often modulate after their intro. */
    val outroKeyPitchClass: Int? = null,
    val outroKeyMode: String? = null,
    val outroKeyConfidence: Float = 0f,

    /**
     * Confirmed dead air after the last audible sound, in milliseconds.
     *
     * Distinct from [outroLeadMs], which is bounded by the twenty-second tail
     * window and uses a relative threshold: this one asks specifically whether
     * the recording keeps going after the music has stopped - a hidden track
     * gap, an extended silence pad - and measures how far, probing sparsely
     * up to a minute past the tail window. Zero unless the very end of the
     * track is genuinely silent.
     */
    val trailingSilenceMs: Long = 0,

    /** Schema version, so a later measurement change can invalidate old rows. */
    val version: Int = CURRENT_VERSION
) {
    companion object {
        // Version 5 uses transient-aware rhythm analysis, persists the schema
        // marker explicitly, and measures the outro key independently.
        // Version 6 adds trailingSilenceMs for the AutoMix silence skip.
        // Version 7 discards silence skips inferred from failed/empty probes.
        const val CURRENT_VERSION = 7
    }
}
