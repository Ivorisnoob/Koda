package com.ivor.ivormusic.service.core

import com.ivor.ivormusic.util.VisualizerMath
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * What the quality check concluded about the audio actually being decoded.
 *
 * The check is the one people run in Spek or Audacity to catch a fake: every
 * encoding leaves a ceiling in the spectrum, and the ceiling gives it away. A
 * lossy encoder (AAC, MP3, Opus) throws away everything above roughly 16-20
 * kHz, so a "lossless" file made from one stops dead there. A CD master holds
 * up to 22.05 kHz and no further, so a "hi-res" file resampled from one has
 * nothing above about 22 kHz however high its sample rate claims. Real hi-res
 * recordings carry sound, faint but there, past 24 kHz.
 *
 * Where the sound stops is only the first clue; [QualityEvidence] carries the
 * others the verdict weighs against it.
 */
enum class QualityVerdict {
    /** Not enough has been heard yet to say. */
    LISTENING,

    /** Sound above 24 kHz in a hi-res file that moves with the music: a real hi-res recording. */
    GENUINE_HI_RES,

    /** A hi-res file with nothing but noise above ~22 kHz: a CD master at a higher rate. */
    UPSAMPLED_CD,

    /** The full CD band in a CD-rate lossless file, with no sign of a lossy encoder. */
    LOSSLESS_CD,

    /** A lossless container whose sound carries a lossy encoder's marks. */
    LOSSY_SOURCE,

    /** A lossy stream, honestly so (YouTube's AAC or Opus). */
    LOSSY_STREAM,

    /** The music itself gives too little to tell (old, dull or very quiet recordings). */
    INCONCLUSIVE,
}

/** How sure a verdict is: one sign of it, or two independent ones agreeing. */
enum class VerdictConfidence { LIKELY, CERTAIN }

data class QualityAssessment(val verdict: QualityVerdict, val confidence: VerdictConfidence?)

/**
 * Everything the measurement found, each piece answering a different way a
 * fake gives itself away. Null where the audio could not answer that question.
 */
data class QualityEvidence(
    /** Highest frequency still carrying sound in the averaged spectrum. */
    val cutoffHz: Int?,
    /**
     * dB lost across the ceiling (the 1.2 kHz below it against the 1.2 kHz
     * above). A lossy encoder's low-pass is a wall of 25 dB or more; music that
     * simply has little treble fades over several kHz. Null when the ceiling is
     * at the top of the range, so there is nothing above it to compare.
     */
    val wallDb: Float?,
    /**
     * Of the moments where the music is loud a few kHz under the ceiling, the
     * share whose top band went empty. Lossy encoders drop the highest bands
     * whenever bits run short, so their top end flickers on and off; a real
     * recording's treble rises and falls with the music but rarely vanishes.
     */
    val dropoutShare: Float?,
    /**
     * Correlation, -1..1, between the level above 24 kHz and the music's own
     * level from moment to moment. Real ultrasonic content follows the music;
     * the hiss a resampler or dither leaves there does not. Hi-res rates only.
     */
    val ultrasonicFollow: Float?,
    /** Whether the music has enough treble (10-15 kHz) to judge at all. */
    val enoughTreble: Boolean,
)

/**
 * Averages a song's spectrum and keeps a coarse per-moment record of it, so
 * that both the shape of the ceiling and how the top end behaves over time
 * can be judged. Fed one mono window of [FFT_SIZE] samples at a time, from
 * playback or from a whole decoded file. Not thread-safe; callers serialise.
 *
 * The per-moment record is 500 Hz bands, one row per window, capped at
 * [MAX_ROWS]: when full, every other row is dropped and only every second
 * window is kept from then on, so a long file is sampled evenly from start to
 * end rather than only its first minutes.
 */
class SpectrumAccumulator(val sampleRate: Int) {

    companion object {
        const val FFT_SIZE = 4096
        const val BAND_HZ = 500
        const val MAX_ROWS = 2048

        private val hann = FloatArray(FFT_SIZE) { i -> (0.5 - 0.5 * cos(2.0 * PI * i / (FFT_SIZE - 1))).toFloat() }
    }

    private val bins = FFT_SIZE / 2
    private val binHz = sampleRate / 2.0 / bins
    val bandCount = max(1, ceil(sampleRate / 2.0 / BAND_HZ).toInt())
    private val bandOfBin = IntArray(bins) { bin -> min((bin * binHz / BAND_HZ).toInt(), bandCount - 1) }
    private val binsInBand = IntArray(bandCount).also { counts -> bandOfBin.forEach { counts[it]++ } }

    private val re = FloatArray(FFT_SIZE)
    private val im = FloatArray(FFT_SIZE)
    private val power = DoubleArray(bins)
    private val bandPower = DoubleArray(bandCount)
    private var rows = FloatArray(MAX_ROWS * bandCount)
    private var rowCount = 0
    private var stride = 1

    /** Windows added so far. */
    var windows = 0
        private set

    /** Audio measured so far, in seconds. */
    val seconds: Float get() = if (sampleRate > 0) windows.toFloat() * FFT_SIZE / sampleRate else 0f

    fun add(window: FloatArray) {
        require(window.size == FFT_SIZE)
        for (i in 0 until FFT_SIZE) {
            re[i] = window[i] * hann[i]
            im[i] = 0f
        }
        VisualizerMath.fft(re, im)
        bandPower.fill(0.0)
        for (bin in 0 until bins) {
            val p = (re[bin] * re[bin] + im[bin] * im[bin]).toDouble()
            power[bin] += p
            bandPower[bandOfBin[bin]] += p
        }
        if (windows % stride == 0) {
            if (rowCount == MAX_ROWS) {
                // Keep rows 0, 2, 4...: they are the windows a doubled stride would have kept.
                for (row in 0 until MAX_ROWS / 2) {
                    System.arraycopy(rows, (row * 2) * bandCount, rows, row * bandCount, bandCount)
                }
                rowCount = MAX_ROWS / 2
                stride *= 2
            }
            if (windows % stride == 0) {
                val base = rowCount * bandCount
                for (band in 0 until bandCount) {
                    rows[base + band] = SpectrumAnalysis.toDb(bandPower[band] / binsInBand[band].coerceAtLeast(1))
                }
                rowCount++
            }
        }
        windows++
    }

    /** Averaged level per FFT bin, 0 Hz to Nyquist. */
    fun levelsDb(): FloatArray {
        val n = windows.coerceAtLeast(1)
        return FloatArray(bins) { bin -> SpectrumAnalysis.toDb(power[bin] / n) }
    }

    /** What the audio so far says, against [levels] (from [levelsDb]). */
    fun evidence(levels: FloatArray = levelsDb()): QualityEvidence {
        if (windows == 0) return QualityEvidence(null, null, null, null, enoughTreble = false)
        val floor = SpectrumAnalysis.floorDb(levels, sampleRate)
        val cutoff = SpectrumAnalysis.cutoffHz(levels, sampleRate)
        val nyquist = sampleRate / 2
        val treble = nyquist > 15_000 &&
            SpectrumAnalysis.meanDb(levels, sampleRate, 10_000, 15_000)?.let { it - floor >= 12f } == true
        return QualityEvidence(
            cutoffHz = cutoff,
            wallDb = cutoff?.let { SpectrumAnalysis.wallDb(levels, sampleRate, it) },
            dropoutShare = cutoff?.let { dropoutShare(it, floor) },
            ultrasonicFollow = if (nyquist > 26_000) ultrasonicFollow(floor) else null,
            enoughTreble = treble,
        )
    }

    private fun bandRange(fromHz: Int, toHz: Int): IntRange? {
        val first = ceil(fromHz.toDouble() / BAND_HZ).toInt().coerceAtLeast(0)
        val last = (toHz / BAND_HZ - 1).coerceAtMost(bandCount - 1)
        return if (first <= last) first..last else null
    }

    private fun rowMean(row: Int, bands: IntRange): Float {
        var sum = 0f
        for (band in bands) sum += rows[row * bandCount + band]
        return sum / (bands.last - bands.first + 1)
    }

    private fun dropoutShare(cutoffHz: Int, floor: Float): Float? {
        if (cutoffHz < 8_000) return null
        val top = bandRange(cutoffHz - 2_000, cutoffHz - 250) ?: return null
        val reference = bandRange(cutoffHz - 6_000, cutoffHz - 3_000) ?: return null
        var counted = 0
        var holes = 0
        for (row in 0 until rowCount) {
            if (rowMean(row, reference) < floor + 20f) continue
            counted++
            if (rowMean(row, top) < floor + 3f) holes++
        }
        return if (counted >= 16) holes.toFloat() / counted else null
    }

    private fun ultrasonicFollow(floor: Float): Float? {
        val ultra = bandRange(24_000, min((sampleRate / 2 * 0.9).toInt(), 44_000)) ?: return null
        val music = bandRange(1_000, 12_000) ?: return null
        val us = ArrayList<Float>(rowCount)
        val ms = ArrayList<Float>(rowCount)
        for (row in 0 until rowCount) {
            val m = rowMean(row, music)
            // Digital silence pins both bands to the same bottom and would
            // read as agreement for any file at all.
            if (m < floor + 3f) continue
            ms += m
            us += rowMean(row, ultra)
        }
        if (ms.size < 24) return null
        return SpectrumAnalysis.correlation(us, ms)
    }
}

/**
 * Counts which of a 24-bit grid's bits the decoded samples actually use,
 * from float PCM (where a 24-bit value is exact). A 16-bit recording padded
 * to 24 leaves the bottom eight bits zero in every sample.
 */
class BitDepthMeter {
    private var used = 0
    private var offGrid = false
    private var seen = 0L

    fun add(samples: FloatArray, count: Int) {
        if (offGrid) return
        for (i in 0 until count) {
            // Scaling by a power of two is exact in floating point.
            val scaled = samples[i] * 8_388_608f
            val whole = scaled.toInt()
            if (whole.toFloat() != scaled) {
                offGrid = true
                return
            }
            used = used or whole
        }
        seen += count
    }

    /**
     * Bits in use, 1..24; null when nothing but silence was seen, or when the
     * samples are finer than 24 bits (a 32-bit or float source), which this
     * cannot count.
     */
    fun usedBits(): Int? {
        if (offGrid || used == 0 || seen == 0L) return null
        return 24 - Integer.numberOfTrailingZeros(used)
    }
}

/**
 * Pure arithmetic behind the quality check: no Android, pinned by a JVM test.
 */
object SpectrumAnalysis {

    /** Seconds of playback averaged before a verdict is offered. */
    const val SECONDS_FOR_VERDICT = 8f

    /** Above this the treble counts as the CD band reached. */
    const val CD_BAND_HZ = 19_500

    /** A wall this steep, below this, is a lossy encoder's low-pass rather than a CD's. */
    private const val LOSSY_WALL_BELOW_HZ = 20_600

    /** Above this a hi-res file has shown sound a CD cannot hold. */
    const val HI_RES_EVIDENCE_HZ = 24_000

    /** A drop across the ceiling at least this steep is a filter, not the music. */
    const val STEEP_WALL_DB = 25f

    /** Below this a lossy claim is not made at all. */
    private const val LOSSY_FLOOR_HZ = 10_000

    /** How far above the noise floor a frequency must stand to count as sound. */
    private const val PRESENCE_DB = 15f

    /** Power per bin to decibels, never minus infinity. */
    fun toDb(power: Double): Float = (10.0 * log10(max(power, 1e-20))).toFloat()

    /** [levelsDb] averaged over a centred span of about 200 Hz. */
    private fun smoothed(levelsDb: FloatArray, binHz: Double): FloatArray {
        val bins = levelsDb.size
        val half = max(1, (100 / binHz).toInt())
        val prefix = DoubleArray(bins + 1)
        for (i in 0 until bins) prefix[i + 1] = prefix[i] + levelsDb[i]
        return FloatArray(bins) { i ->
            val from = max(0, i - half)
            val to = min(bins, i + half + 1)
            ((prefix[to] - prefix[from]) / (to - from)).toFloat()
        }
    }

    /**
     * The noise floor: the 1st percentile of the smoothed spectrum above 1 kHz
     * - the dither and quantisation noise every digital file has, found in the
     * emptiest stretch whether that is above a lossy ceiling or the last few
     * hundred hertz before Nyquist.
     */
    fun floorDb(levelsDb: FloatArray, sampleRate: Int): Float {
        val bins = levelsDb.size
        if (bins == 0 || sampleRate <= 0) return -200f
        val binHz = sampleRate / 2.0 / bins
        val fromBin = (1_000 / binHz).toInt().coerceIn(0, bins - 1)
        val sorted = smoothed(levelsDb, binHz).copyOfRange(fromBin, bins).sorted()
        return sorted[(sorted.size * 0.01).toInt().coerceAtMost(sorted.size - 1)]
    }

    /**
     * The highest frequency, in Hz, that still carries sound in [levelsDb]
     * (one averaged level per FFT bin from 0 Hz to Nyquist), or null for a
     * spectrum that is all floor. Sound is anything standing [PRESENCE_DB]
     * above [floorDb], judged on a centred ~200 Hz average so one stray bin
     * cannot fake a ceiling.
     */
    fun cutoffHz(levelsDb: FloatArray, sampleRate: Int): Int? {
        val bins = levelsDb.size
        if (bins < 16 || sampleRate <= 0) return null
        val binHz = sampleRate / 2.0 / bins
        val fromBin = (1_000 / binHz).toInt().coerceIn(0, bins - 1)
        val floor = floorDb(levelsDb, sampleRate)
        val smooth = smoothed(levelsDb, binHz)
        for (i in bins - 1 downTo fromBin) {
            if (smooth[i] > floor + PRESENCE_DB) return ((i + 1) * binHz).toInt()
        }
        return null
    }

    /** Mean level of [levelsDb] between two frequencies, or null for an empty range. */
    fun meanDb(levelsDb: FloatArray, sampleRate: Int, fromHz: Int, toHz: Int): Float? {
        val bins = levelsDb.size
        if (bins == 0 || sampleRate <= 0) return null
        val binHz = sampleRate / 2.0 / bins
        val from = (fromHz / binHz).toInt().coerceIn(0, bins)
        val to = (toHz / binHz).toInt().coerceIn(0, bins)
        if (to - from < 3) return null
        var sum = 0.0
        for (i in from until to) sum += levelsDb[i]
        return (sum / (to - from)).toFloat()
    }

    /** See [QualityEvidence.wallDb]. */
    fun wallDb(levelsDb: FloatArray, sampleRate: Int, cutoffHz: Int): Float? {
        val below = meanDb(levelsDb, sampleRate, cutoffHz - 1_500, cutoffHz - 300) ?: return null
        val above = meanDb(levelsDb, sampleRate, cutoffHz + 300, cutoffHz + 1_500) ?: return null
        return below - above
    }

    /** Pearson correlation of two equal-length series, or null when either is flat. */
    fun correlation(a: List<Float>, b: List<Float>): Float? {
        val n = min(a.size, b.size)
        if (n < 2) return null
        var meanA = 0.0
        var meanB = 0.0
        for (i in 0 until n) {
            meanA += a[i]
            meanB += b[i]
        }
        meanA /= n
        meanB /= n
        var cov = 0.0
        var varA = 0.0
        var varB = 0.0
        for (i in 0 until n) {
            val da = a[i] - meanA
            val db = b[i] - meanB
            cov += da * db
            varA += da * da
            varB += db * db
        }
        if (varA < 1e-9 || varB < 1e-9) return null
        return (cov / sqrt(varA * varB)).toFloat()
    }

    /**
     * The verdict for what [evidence] shows of a track decoded at [sampleRate],
     * [lossless] saying whether its codec is a lossless one. [seconds] is how
     * much was measured and [minSeconds] how much a verdict needs: a stretch of
     * playback needs [SECONDS_FOR_VERDICT], a whole file nothing more.
     *
     * Two signs of the same thing make a verdict certain; one makes it likely.
     */
    fun assess(
        lossless: Boolean,
        sampleRate: Int,
        evidence: QualityEvidence?,
        seconds: Float,
        minSeconds: Float = SECONDS_FOR_VERDICT,
    ): QualityAssessment {
        if (!lossless) return QualityAssessment(QualityVerdict.LOSSY_STREAM, VerdictConfidence.CERTAIN)
        if (evidence == null || seconds < minSeconds) return QualityAssessment(QualityVerdict.LISTENING, null)
        val cutoff = evidence.cutoffHz ?: return QualityAssessment(QualityVerdict.INCONCLUSIVE, null)
        val dropouts = evidence.dropoutShare ?: 0f

        // The marks a lossy encoder leaves: a wall below a CD's own filter,
        // and a top end that keeps cutting out. Dropouts need to be commoner
        // to count once the sound already reaches the CD band, where a real
        // recording's top octave is often quiet.
        val wall = (evidence.wallDb ?: 0f) >= STEEP_WALL_DB && cutoff < LOSSY_WALL_BELOW_HZ
        val flicker = dropouts >= if (cutoff < CD_BAND_HZ) 0.2f else 0.35f
        val lossySigns = (if (wall) 1 else 0) + (if (flicker) 1 else 0)
        if (lossySigns > 0 && cutoff >= LOSSY_FLOOR_HZ) {
            return QualityAssessment(
                QualityVerdict.LOSSY_SOURCE,
                if (lossySigns == 2) VerdictConfidence.CERTAIN else VerdictConfidence.LIKELY,
            )
        }
        // A gentle fade below the CD band with nothing flickering is the
        // recording, not the encoding.
        if (cutoff < CD_BAND_HZ) return QualityAssessment(QualityVerdict.INCONCLUSIVE, null)

        if (sampleRate > 48_000) {
            if (cutoff <= HI_RES_EVIDENCE_HZ) {
                return QualityAssessment(
                    QualityVerdict.UPSAMPLED_CD,
                    if (cutoff <= 22_500) VerdictConfidence.CERTAIN else VerdictConfidence.LIKELY,
                )
            }
            val follow = evidence.ultrasonicFollow
            return when {
                follow == null -> QualityAssessment(QualityVerdict.GENUINE_HI_RES, VerdictConfidence.LIKELY)
                follow >= 0.5f -> QualityAssessment(QualityVerdict.GENUINE_HI_RES, VerdictConfidence.CERTAIN)
                follow >= 0.25f -> QualityAssessment(QualityVerdict.GENUINE_HI_RES, VerdictConfidence.LIKELY)
                // Something is up there, but it ignores the music: hiss.
                else -> QualityAssessment(QualityVerdict.UPSAMPLED_CD, VerdictConfidence.LIKELY)
            }
        }
        val clean = dropouts < 0.05f && evidence.enoughTreble
        return QualityAssessment(
            QualityVerdict.LOSSLESS_CD,
            if (clean) VerdictConfidence.CERTAIN else VerdictConfidence.LIKELY,
        )
    }

    /**
     * [levelsDb] folded into [bands] equal-width bands for drawing, each the
     * loudest bin it covers so a narrow ceiling is not averaged away.
     */
    fun bands(levelsDb: FloatArray, bands: Int): FloatArray {
        if (levelsDb.isEmpty() || bands <= 0) return FloatArray(0)
        val out = FloatArray(bands) { Float.NEGATIVE_INFINITY }
        for (i in levelsDb.indices) {
            val band = (i.toLong() * bands / levelsDb.size).toInt().coerceIn(0, bands - 1)
            if (levelsDb[i] > out[band]) out[band] = levelsDb[i]
        }
        return out
    }
}
