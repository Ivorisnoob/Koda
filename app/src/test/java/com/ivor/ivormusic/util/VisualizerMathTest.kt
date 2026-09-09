package com.ivor.ivormusic.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.sin

class VisualizerMathTest {

    private val size = VisualizerMath.FFT_SIZE

    private fun scratch() = FloatArray(size) to FloatArray(size)

    /** A full-scale sine at [binIndex] cycles per window. */
    private fun tone(binIndex: Int, amplitude: Float = 1f) = FloatArray(size) { i ->
        (amplitude * sin(2.0 * PI * binIndex * i / size)).toFloat()
    }

    // ---- bandEdges ----------------------------------------------------

    @Test
    fun bandEdgesAreStrictlyIncreasingAndInRange() {
        val edges = VisualizerMath.bandEdges(512, 32)
        assertEquals(33, edges.size)
        assertEquals(1, edges.first())
        assertEquals(512, edges.last())
        for (i in 1 until edges.size) {
            assertTrue("edge $i went backwards", edges[i] >= edges[i - 1])
            assertTrue("edge $i past the end", edges[i] <= 512)
        }
    }

    @Test
    fun bandEdgesSurviveFewerBinsThanBands() {
        // The old fftToBands built an edge with coerceIn(previous + 1, bins),
        // which throws an empty-range IllegalArgumentException as soon as the
        // previous edge reaches the bin count - while its doc promised short
        // input would answer zeros. Every degenerate shape must be answerable.
        for (bins in 0..8) {
            for (bands in 1..48) {
                val edges = VisualizerMath.bandEdges(bins, bands)
                assertEquals(bands + 1, edges.size)
                for (i in 1 until edges.size) {
                    assertTrue(edges[i] >= edges[i - 1])
                }
            }
        }
    }

    @Test
    fun bandEdgesAreLogarithmicRatherThanEven() {
        val edges = VisualizerMath.bandEdges(512, 16)
        val firstWidth = edges[1] - edges[0]
        val lastWidth = edges[16] - edges[15]
        // An even split would make these equal; log spacing keeps the low end
        // narrow so the strip does not spend half its width on sub-bass.
        assertTrue("expected widening bands, got $firstWidth then $lastWidth", lastWidth > firstWidth * 4)
    }

    // ---- fft ----------------------------------------------------------

    @Test
    fun fftOfAnImpulseIsFlat() {
        val re = FloatArray(size)
        val im = FloatArray(size)
        re[0] = 1f
        VisualizerMath.fft(re, im)
        for (i in 0 until size) {
            assertEquals(1f, hypot(re[i], im[i]), 1e-3f)
        }
    }

    @Test
    fun fftPutsAToneInItsOwnBin() {
        val re = tone(64)
        val im = FloatArray(size)
        VisualizerMath.fft(re, im)
        val magnitudes = FloatArray(size / 2) { hypot(re[it], im[it]) }
        val loudest = magnitudes.indices.maxByOrNull { magnitudes[it] }
        assertEquals(64, loudest)
    }

    @Test
    fun fftLeavesMalformedInputAlone() {
        val re = FloatArray(6) { 1f }
        val im = FloatArray(6)
        VisualizerMath.fft(re, im) // not a power of two
        assertTrue(re.all { it == 1f })
        val mismatched = FloatArray(size) { 1f }
        VisualizerMath.fft(mismatched, FloatArray(4))
        assertTrue(mismatched.all { it == 1f })
    }

    // ---- analyze ------------------------------------------------------

    @Test
    fun silenceAnswersZeros() {
        val (re, im) = scratch()
        val out = FloatArray(32)
        VisualizerMath.analyze(FloatArray(size), re, im, VisualizerMath.bandEdges(size / 2, 32), out)
        assertTrue(out.all { it == 0f })
    }

    @Test
    fun aLoudToneLightsItsOwnBandAndLeavesTheRestLow() {
        val (re, im) = scratch()
        val bands = 24
        val edges = VisualizerMath.bandEdges(size / 2, bands)
        val out = FloatArray(bands)
        VisualizerMath.analyze(tone(64), re, im, edges, out)
        assertTrue(out.all { it in 0f..1f })
        val loudest = out.indices.maxByOrNull { out[it] }!!
        // Bin 64 must land in the band whose edges bracket it.
        assertTrue(edges[loudest] <= 64 && 64 < edges[loudest + 1])
        assertTrue("a full-scale tone should be near the top", out[loudest] > 0.9f)
        val others = out.filterIndexed { i, _ -> i != loudest }
        assertTrue("spectral leakage should stay well below the peak", others.all { it < out[loudest] })
    }

    @Test
    fun quieterIsLower() {
        val (re, im) = scratch()
        val edges = VisualizerMath.bandEdges(size / 2, 24)
        val loud = FloatArray(24)
        val soft = FloatArray(24)
        VisualizerMath.analyze(tone(64, 1f), re, im, edges, loud)
        VisualizerMath.analyze(tone(64, 0.05f), re, im, edges, soft)
        val band = loud.indices.maxByOrNull { loud[it] }!!
        assertTrue(soft[band] < loud[band])
        assertTrue("a quiet tone must still register", soft[band] > 0f)
    }

    @Test
    fun wrongSizedInputAnswersZerosRatherThanThrowing() {
        val (re, im) = scratch()
        val out = FloatArray(32) { 0.9f }
        VisualizerMath.analyze(FloatArray(7), re, im, VisualizerMath.bandEdges(size / 2, 32), out)
        assertTrue(out.all { it == 0f })
        // An empty band array is a no-op, not an index error.
        VisualizerMath.analyze(tone(64), re, im, VisualizerMath.bandEdges(size / 2, 32), FloatArray(0))
    }

    @Test
    fun moreBandsThanEdgesLeavesTheTailAtZero() {
        val (re, im) = scratch()
        val out = FloatArray(32) { 0.9f }
        VisualizerMath.analyze(tone(64), re, im, VisualizerMath.bandEdges(size / 2, 8), out)
        assertTrue(out.drop(8).all { it == 0f })
    }

    // ---- smoothBands --------------------------------------------------

    @Test
    fun smoothingConvergesWithoutOvershooting() {
        val current = FloatArray(8)
        val target = FloatArray(8) { 1f }
        repeat(200) { VisualizerMath.smoothBands(current, target, 0.55f, 0.16f) }
        assertTrue(current.all { it > 0.99f && it <= 1f })
    }

    @Test
    fun attackIsFasterThanRelease() {
        val rising = floatArrayOf(0f)
        VisualizerMath.smoothBands(rising, floatArrayOf(1f), 0.55f, 0.16f)
        val falling = floatArrayOf(1f)
        VisualizerMath.smoothBands(falling, floatArrayOf(0f), 0.55f, 0.16f)
        // Same distance travelled, so the rise must have covered more of it.
        assertTrue("rise ${rising[0]} should exceed fall ${1f - falling[0]}", rising[0] > 1f - falling[0])
    }

    @Test
    fun smoothingHandlesExtremesAndMismatchedSizes() {
        val held = floatArrayOf(0.5f)
        VisualizerMath.smoothBands(held, floatArrayOf(1f), 0f, 0f)
        assertEquals(0.5f, held[0], 0f)
        VisualizerMath.smoothBands(held, floatArrayOf(1f), 1f, 1f)
        assertEquals(1f, held[0], 0f)
        // A shorter target moves only the overlap and never indexes past it.
        val wide = FloatArray(8) { 0.5f }
        VisualizerMath.smoothBands(wide, FloatArray(3) { 1f }, 1f, 1f)
        assertTrue(wide.take(3).all { it == 1f })
        assertTrue(wide.drop(3).all { it == 0.5f })
    }
}
