package com.ivor.ivormusic.data

import android.graphics.Color

/**
 * The color system behind generated playlist covers, shared by the real
 * generator ([PlaylistRepository]) and the live preview in the playlist
 * studio, so what the preview promises is what the bitmap delivers.
 *
 * **One hue per cover, never a two-hue blend.** [judgement] The first
 * generator ran a corner-to-corner gradient from the palette's primary hue to
 * its tertiary at pinned high saturation, and two unrelated vivid hues melting
 * into each other (blue into pink, most palettes) is precisely the stock
 * "AI gradient" look. A cover here is a tonal ramp of a *single* seed hue -
 * light into deep of the same color - with the other seed appearing only as
 * one small solid accent. Monochrome with one pop reads designed; a rainbow
 * melt reads generated.
 *
 * Which seed carries the cover and which accents it is decided by [key]
 * (the playlist id's hash), as is the tonal variation, so a library on one
 * palette still has range and a rename regenerates the same cover.
 *
 * Near-neutral seeds keep their own saturation: pinning saturation on a
 * Graphite-style palette would resolve a gray's meaningless hue (0 = red) into
 * actual red covers, which is the old generator's washed-out bug inverted.
 */
object PlaylistCoverArt {

    /** Motif families the drawers agree on: arch, rings, ribbons, quarters. */
    const val FAMILY_COUNT = 4

    data class Scheme(
        /** Top of the tonal ramp. */
        val top: Int,
        /** Bottom of the tonal ramp, same hue, deeper. */
        val bottom: Int,
        /** The one accent pop, from the other palette seed. */
        val accent: Int,
        /** Geometry base color; drawers apply their own alphas. */
        val overlay: Int,
        /** True when the ramp is pale and geometry is ink rather than white. */
        val lightBase: Boolean
    )

    fun family(key: Int): Int = (key and 0x7FFFFFFF) % FAMILY_COUNT

    fun scheme(seedA: Int, seedB: Int, key: Int): Scheme {
        val k = key and 0x7FFFFFFF
        // Independent of the family/variation moduli, so base choice does not
        // correlate with which motif is drawn.
        val baseFirst = (k / 7) % 2 == 0
        val base = if (baseFirst) seedA else seedB
        val accentSeed = if (baseFirst) seedB else seedA

        val baseHsv = FloatArray(3).also { Color.colorToHSV(base, it) }
        val accentHsv = FloatArray(3).also { Color.colorToHSV(accentSeed, it) }
        val hue = baseHsv[0]
        val accentHue = accentHsv[0]

        return when (k % 3) {
            // Rich: mid-value color into its own shadow.
            0 -> Scheme(
                top = hsv(hue + 6f, sat(baseHsv[1], 0.52f), 0.80f),
                bottom = hsv(hue - 5f, sat(baseHsv[1], 0.66f), 0.40f),
                accent = hsv(accentHue, sat(accentHsv[1], 0.42f), 0.96f),
                overlay = Color.WHITE,
                lightBase = false
            )
            // Deep: the same ramp pushed into the dark end, near-black floor.
            1 -> Scheme(
                top = hsv(hue - 6f, sat(baseHsv[1], 0.58f), 0.52f),
                bottom = hsv(hue + 5f, sat(baseHsv[1], 0.70f), 0.22f),
                accent = hsv(accentHue, sat(accentHsv[1], 0.38f), 0.97f),
                overlay = Color.WHITE,
                lightBase = false
            )
            // Soft: pale ramp with ink geometry - the editorial one.
            else -> Scheme(
                top = hsv(hue, sat(baseHsv[1], 0.26f), 0.94f),
                bottom = hsv(hue + 7f, sat(baseHsv[1], 0.40f), 0.70f),
                accent = hsv(accentHue, sat(accentHsv[1], 0.55f), 0.42f),
                overlay = hsv(hue, sat(baseHsv[1], 0.52f), 0.30f),
                lightBase = true
            )
        }
    }

    /** Pin saturation for real colors; let genuinely neutral seeds stay gray. */
    private fun sat(seedSat: Float, target: Float): Float =
        if (seedSat < 0.12f) seedSat else target

    private fun hsv(hue: Float, sat: Float, value: Float): Int =
        Color.HSVToColor(floatArrayOf(((hue % 360f) + 360f) % 360f, sat, value))
}
