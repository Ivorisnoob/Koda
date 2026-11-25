package com.ivor.ivormusic.service.core

import com.ivor.ivormusic.util.VisualizerMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class SpectrumAnalysisTest {

    private val n = SpectrumAccumulator.FFT_SIZE

    /**
     * One window of noise whose spectrum is [amplitude] of each frequency,
     * random in phase, over a faint floor everywhere: built in the frequency
     * domain and turned back into samples.
     */
    private fun window(sampleRate: Int, random: Random, amplitude: (Double) -> Double): FloatArray {
        val re = FloatArray(n)
        val im = FloatArray(n)
        val binHz = sampleRate.toDouble() / n
        for (k in 1 until n / 2) {
            val a = amplitude(k * binHz) + 1e-4 * random.nextDouble()
            val phase = random.nextDouble() * 2 * PI
            re[k] = (a * cos(phase)).toFloat()
            im[k] = (a * sin(phase)).toFloat()
            re[n - k] = re[k]
            im[n - k] = -im[k]
        }
        // Inverse transform: conjugate, forward, conjugate, scale.
        for (i in 0 until n) im[i] = -im[i]
        VisualizerMath.fft(re, im)
        return FloatArray(n) { re[it] / n }
    }

    private fun measure(sampleRate: Int, windows: Int = 120, amplitude: (Int, Double) -> Double): Pair<QualityEvidence, QualityAssessment> {
        val random = Random(7)
        val acc = SpectrumAccumulator(sampleRate)
        repeat(windows) { w -> acc.add(window(sampleRate, random) { hz -> amplitude(w, hz) }) }
        val evidence = acc.evidence()
        return evidence to SpectrumAnalysis.assess(true, sampleRate, evidence, acc.seconds)
    }

    /** Music-like: louder low down, falling with frequency, up to [toHz]. */
    private fun music(hz: Double, toHz: Int, level: Double = 1.0): Double =
        if (hz < toHz) level * 0.5 / (1 + hz / 4_000) else 0.0

    @Test
    fun `a lossy wall at 16 kHz is a lossy source`() {
        val (evidence, assessment) = measure(44_100) { _, hz -> music(hz, 16_000) }
        assertTrue("cutoff ${evidence.cutoffHz}", evidence.cutoffHz!! in 15_500..16_500)
        assertTrue("wall ${evidence.wallDb}", evidence.wallDb!! >= SpectrumAnalysis.STEEP_WALL_DB)
        assertEquals(QualityVerdict.LOSSY_SOURCE, assessment.verdict)
    }

    @Test
    fun `the full cd band is lossless cd`() {
        val (_, assessment) = measure(44_100) { _, hz -> music(hz, 21_600) }
        assertEquals(QualityVerdict.LOSSLESS_CD, assessment.verdict)
    }

    @Test
    fun `a top band that keeps cutting out is a lossy source even at 19 kHz`() {
        val (evidence, assessment) = measure(44_100) { w, hz ->
            if (hz > 17_200 && w % 2 == 0) 0.0 else music(hz, 19_200)
        }
        assertTrue("dropouts ${evidence.dropoutShare}", evidence.dropoutShare!! >= 0.35f)
        assertEquals(QualityVerdict.LOSSY_SOURCE, assessment.verdict)
    }

    @Test
    fun `a 96 kHz file stopping at 22 kHz is upsampled`() {
        val (_, assessment) = measure(96_000) { _, hz -> music(hz, 21_800) }
        assertEquals(QualityVerdict.UPSAMPLED_CD, assessment.verdict)
    }

    @Test
    fun `ultrasonic sound that moves with the music is genuine hi-res`() {
        val (evidence, assessment) = measure(96_000) { w, hz ->
            val level = if (w % 3 == 0) 0.2 else 1.0
            music(hz, 40_000, level)
        }
        assertTrue("follow ${evidence.ultrasonicFollow}", evidence.ultrasonicFollow!! >= 0.5f)
        assertEquals(QualityVerdict.GENUINE_HI_RES, assessment.verdict)
        assertEquals(VerdictConfidence.CERTAIN, assessment.confidence)
    }

    @Test
    fun `steady hiss above a cd's range is not hi-res`() {
        val (evidence, assessment) = measure(96_000) { w, hz ->
            val level = if (w % 3 == 0) 0.2 else 1.0
            when {
                hz < 21_800 -> music(hz, 21_800, level)
                hz in 24_000.0..40_000.0 -> 0.01
                else -> 0.0
            }
        }
        assertTrue("follow ${evidence.ultrasonicFollow}", (evidence.ultrasonicFollow ?: 0f) < 0.25f)
        assertEquals(QualityVerdict.UPSAMPLED_CD, assessment.verdict)
    }

    @Test
    fun `a gentle fade below the cd band cannot be judged`() {
        val (_, assessment) = measure(44_100) { _, hz ->
            // Rolls off by about 6 dB per kHz from 12 kHz: no wall anywhere.
            if (hz < 12_000) music(hz, 22_000) else music(hz, 22_000) * Math.pow(10.0, -(hz - 12_000) / 1_000 * 0.3)
        }
        assertEquals(QualityVerdict.INCONCLUSIVE, assessment.verdict)
    }

    @Test
    fun `too little heard is still listening and lossy streams say so`() {
        val evidence = QualityEvidence(21_000, null, null, null, enoughTreble = true)
        assertEquals(QualityVerdict.LISTENING, SpectrumAnalysis.assess(true, 44_100, evidence, 2f).verdict)
        assertEquals(QualityVerdict.LISTENING, SpectrumAnalysis.assess(true, 44_100, null, 20f).verdict)
        assertEquals(QualityVerdict.LOSSY_STREAM, SpectrumAnalysis.assess(false, 44_100, evidence, 20f).verdict)
        // A whole file needs no minimum.
        assertEquals(QualityVerdict.LOSSLESS_CD, SpectrumAnalysis.assess(true, 44_100, evidence, 2f, minSeconds = 0f).verdict)
    }

    @Test
    fun `an all floor spectrum has no ceiling`() {
        assertNull(SpectrumAnalysis.cutoffHz(FloatArray(2048) { -110f }, 44_100))
    }

    @Test
    fun `a long file is sampled from end to end`() {
        val acc = SpectrumAccumulator(44_100)
        val random = Random(1)
        repeat(SpectrumAccumulator.MAX_ROWS * 3) { acc.add(window(44_100, random) { hz -> music(hz, 16_000) }) }
        assertEquals(SpectrumAccumulator.MAX_ROWS * 3, acc.windows)
        assertEquals(QualityVerdict.LOSSY_SOURCE, SpectrumAnalysis.assess(true, 44_100, acc.evidence(), acc.seconds).verdict)
    }

    @Test
    fun `bit meter tells a padded 16-bit file from a real 24-bit one`() {
        val random = Random(3)
        val padded = BitDepthMeter()
        val sixteen = FloatArray(4096) { (random.nextInt(-32768, 32768) / 32768f) }
        padded.add(sixteen, sixteen.size)
        assertEquals(16, padded.usedBits())

        val real = BitDepthMeter()
        val twentyFour = FloatArray(4096) { (random.nextInt(-8_388_608, 8_388_608) / 8_388_608f) }
        real.add(twentyFour, twentyFour.size)
        assertEquals(24, real.usedBits())

        val silent = BitDepthMeter()
        silent.add(FloatArray(100), 100)
        assertNull(silent.usedBits())

        val finer = BitDepthMeter()
        finer.add(floatArrayOf(1e-9f), 1)
        assertNull(finer.usedBits())
    }
}
