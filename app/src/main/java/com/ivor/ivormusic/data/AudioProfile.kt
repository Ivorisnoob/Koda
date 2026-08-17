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
 * they all come from the same cheap measurement - an RMS envelope over the
 * first and last few seconds, taken from audio already sitting in the cache.
 *
 * Absent for a track never profiled, and everything downstream has to work
 * without one: the fallback is a plain equal-power fade at the user's chosen
 * duration, which is what the engine did before profiles existed.
 */
@Serializable
data class AudioProfile(
    val songId: String,

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
     * That is a hard cut into whatever came next on the record - a live album,
     * a DJ mix, anything that segues - and it is the one case where the right
     * amount of crossfade is none. This is the signal `Song` cannot provide:
     * it carries an album name but no track number, so "same album, consecutive
     * track" is not available as data and could only be guessed from queue
     * adjacency.
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
     * Detected tempo, for beat-matched transitions. Null when the estimate was
     * not confident enough to act on - a wrong tempo is worse than none,
     * because nudging playback speed to match it is audible.
     */
    val bpm: Float? = null,

    /** Schema version, so a later measurement change can invalidate old rows. */
    val version: Int = CURRENT_VERSION
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}
