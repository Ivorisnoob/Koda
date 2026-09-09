package com.ivor.ivormusic.util

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Pure spectrum math for the player visualizer, kept out of both the audio
 * processor and the composable so the JVM suite can reach all of it.
 *
 * The input is Koda's own decoded PCM, taken from the playback pipeline rather
 * than from `android.media.audiofx.Visualizer` - that class is the platform's
 * audio *capture* API and is gated on RECORD_AUDIO whatever session it is
 * pointed at, while these samples are already in the app's hands on their way
 * to the sink. So everything here works with no permission at all.
 *
 * Everything is written to caller-owned scratch arrays: this runs on the audio
 * thread roughly fifty times a second, and allocating a working set per call
 * there is the kind of steady garbage that shows up as a dropout.
 */
object VisualizerMath {

    /** Window length in samples. 1024 at 48kHz is a 21ms slice, ~47 per second. */
    const val FFT_SIZE = 1024

    /** Quietest band level that still reads as movement rather than silence. */
    const val FLOOR_DB = 60f

    private val hann = FloatArray(FFT_SIZE) { i ->
        (0.5 - 0.5 * cos(2.0 * PI * i / (FFT_SIZE - 1))).toFloat()
    }

    /**
     * Band boundaries over [binCount] usable FFT bins, as [bandCount] + 1 edges.
     *
     * Logarithmic, because bin magnitudes bunch at the low end and an even
     * split spends half the strip on sub-bass nobody can tell apart. Bin 0 is
     * the DC term and is never included.
     *
     * Total safety is the point: edges are forced strictly increasing and are
     * clamped to what actually exists, so a short capture or a band count
     * larger than the bin count answers narrow bands rather than throwing.
     * The previous version built an edge with `coerceIn(previous + 1, bins)`,
     * which throws once `previous` reaches `bins` - Kotlin refuses an empty
     * range - and its own doc promised the opposite.
     */
    fun bandEdges(binCount: Int, bandCount: Int): IntArray {
        val bands = bandCount.coerceAtLeast(1)
        val edges = IntArray(bands + 1)
        val usable = binCount.coerceAtLeast(1)
        if (usable <= 1) return edges
        val lowest = 1.0
        val highest = usable.toDouble()
        val span = ln(highest / lowest)
        edges[0] = 1
        for (band in 1..bands) {
            val ideal = lowest * Math.exp(span * band / bands)
            // Strictly increasing while there is room, then flat at the end.
            // The lower bound is itself clamped to what exists, because asking
            // coerceIn for a range whose minimum has passed its maximum is the
            // exception this whole function was rewritten to stop throwing.
            val lower = minOf(edges[band - 1] + 1, usable)
            edges[band] = ideal.roundToInt().coerceIn(lower, usable)
        }
        return edges
    }

    /**
     * In-place iterative radix-2 Cooley-Tukey FFT. [re] and [im] must be the
     * same power-of-two length; [im] is zero for real input.
     */
    fun fft(re: FloatArray, im: FloatArray) {
        val n = re.size
        if (n < 2 || n and (n - 1) != 0 || im.size != n) return
        // Bit-reversal permutation.
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
            if (i < j) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }
        var length = 2
        while (length <= n) {
            val angle = -2.0 * PI / length
            val wRealStep = cos(angle).toFloat()
            val wImagStep = kotlin.math.sin(angle).toFloat()
            var start = 0
            while (start < n) {
                var wReal = 1f
                var wImag = 0f
                for (k in 0 until length / 2) {
                    val a = start + k
                    val b = a + length / 2
                    val tReal = re[b] * wReal - im[b] * wImag
                    val tImag = re[b] * wImag + im[b] * wReal
                    re[b] = re[a] - tReal
                    im[b] = im[a] - tImag
                    re[a] += tReal
                    im[a] += tImag
                    val nextReal = wReal * wRealStep - wImag * wImagStep
                    wImag = wReal * wImagStep + wImag * wRealStep
                    wReal = nextReal
                }
                start += length
            }
            length = length shl 1
        }
    }

    /**
     * Window [samples], transform it, and write [out].size band levels in 0..1.
     *
     * [re] and [im] are scratch of length [FFT_SIZE]; [edges] comes from
     * [bandEdges] and is cached by the caller for as long as the band count
     * holds. Each band takes the loudest bin it covers, then a decibel curve,
     * so a quiet mix still moves instead of hugging the floor the way raw
     * linear magnitude does.
     */
    fun analyze(samples: FloatArray, re: FloatArray, im: FloatArray, edges: IntArray, out: FloatArray) {
        if (out.isEmpty()) return
        if (samples.size != FFT_SIZE || re.size != FFT_SIZE || im.size != FFT_SIZE) {
            out.fill(0f)
            return
        }
        for (i in 0 until FFT_SIZE) {
            re[i] = samples[i] * hann[i]
            im[i] = 0f
        }
        fft(re, im)
        // A full-scale sine puts roughly amplitude * N / 4 into its own bin
        // once the Hann window has taken half the energy, so that is 0 dBFS.
        val fullScale = FFT_SIZE / 4f
        val bands = minOf(out.size, edges.size - 1)
        for (band in 0 until bands) {
            var peak = 0f
            val from = edges[band]
            val to = edges[band + 1]
            // A zero-width band is a band clamped against the end of the
            // spectrum; it reads as silence rather than borrowing a neighbour.
            for (bin in from until minOf(to, FFT_SIZE / 2)) {
                val magnitude = hypot(re[bin], im[bin])
                if (magnitude > peak) peak = magnitude
            }
            val normalized = peak / fullScale
            val db = 20f * log10(max(normalized, 1e-6f))
            out[band] = ((db + FLOOR_DB) / FLOOR_DB).coerceIn(0f, 1f)
        }
        for (band in bands until out.size) out[band] = 0f
    }

    /**
     * Ease [current] toward [target] in place, fast on the way up and slow on
     * the way down: beats land on time and bars fall like gravity instead of
     * snapping to zero. Sizes need not match; the overlap is what moves.
     */
    fun smoothBands(current: FloatArray, target: FloatArray, attack: Float, release: Float): FloatArray {
        val count = minOf(current.size, target.size)
        val up = attack.coerceIn(0f, 1f)
        val down = release.coerceIn(0f, 1f)
        for (i in 0 until count) {
            val goal = target[i]
            current[i] += (goal - current[i]) * if (goal > current[i]) up else down
        }
        return current
    }
}
