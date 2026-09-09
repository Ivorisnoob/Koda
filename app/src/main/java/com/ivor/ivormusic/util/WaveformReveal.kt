package com.ivor.ivormusic.util

/**
 * How the waveform seek bar's bars arrive: a sweep across the track, each bar springing up to
 * its measured level as the sweep reaches it.
 *
 * Kept out of the composable so the JVM suite can hold it to its endpoints, which is where an
 * entrance animation goes wrong in a way nobody notices - a curve that never quite reaches 1
 * leaves every bar permanently short of its real height, and a waveform whose levels are
 * silently scaled is a waveform that lies about the song.
 *
 * The sweep itself runs at a constant rate rather than on a spring, which is the one place this
 * departs from the house default. A spring drives the *front* of the sweep, and with a stagger
 * laid over it every bar past the front is clamped at its resting value, so the bounce lands
 * nowhere: the bars closest to the finish would arrive dead flat. The bounce belongs to each
 * bar instead, which is what [factorAt] gives it.
 */
object WaveformReveal {

    /**
     * The share of the sweep spent starting bars, leaving the rest for each bar's own rise.
     *
     * Too little and the bars come up as one block, which is a fade rather than a sweep; too
     * much and the last bar has almost no time to rise and snaps. A little over half reads as a
     * wave crossing the track.
     */
    private const val STAGGER = 0.55f

    /** How far past its level a bar goes before settling. Enough to feel elastic, not springy. */
    private const val OVERSHOOT = 1.15f

    /**
     * How far bar [index] of [count] has risen when the sweep is [reveal] through, in 0..~1.1.
     *
     * Returns exactly 1 once the sweep is done, for every bar: the whole point of this animation
     * is that it ends, and the waveform it leaves behind is the song's own levels, unscaled and
     * unmoving until the next song.
     */
    fun factorAt(index: Int, count: Int, reveal: Float): Float {
        if (count <= 0) return 0f
        if (reveal >= 1f) return 1f
        if (reveal <= 0f) return 0f
        val start = if (count == 1) 0f else index.coerceIn(0, count - 1).toFloat() / (count - 1) * STAGGER
        val local = ((reveal - start) / (1f - STAGGER)).coerceIn(0f, 1f)
        return overshoot(local)
    }

    /** Ease-out-back: 0 at 0, 1 at 1, a little above 1 in between. */
    private fun overshoot(t: Float): Float {
        val past = t - 1f
        return 1f + past * past * ((OVERSHOOT + 1f) * past + OVERSHOOT)
    }
}
