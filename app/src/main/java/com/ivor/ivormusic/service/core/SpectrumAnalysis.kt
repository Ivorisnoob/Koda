package com.ivor.ivormusic.service.core

import kotlin.math.log10
import kotlin.math.max

/**
 * What the frequency check concluded about the audio actually being decoded.
 *
 * The check is the one people run in Spek or Audacity to catch a fake: every
 * encoding leaves a ceiling in the spectrum, and the ceiling gives it away. A
 * lossy encoder (AAC, MP3, Opus) throws away everything above roughly 16-20
 * kHz, so a "lossless" file made from one stops dead there. A CD master holds
 * up to 22.05 kHz and no further, so a "hi-res" file resampled from one has
 * nothing above about 22 kHz however high its sample rate claims. Real hi-res
 * recordings carry sound, faint but there, past 24 kHz.
 */
enum class QualityVerdict {
    /** Not enough has been heard yet to say. */
    LISTENING,

    /** Sound above 24 kHz in a hi-res file: a real hi-res recording. */
    GENUINE_HI_RES,

    /** A hi-res file with nothing above ~22 kHz: a CD master at a higher rate. */
    UPSAMPLED_CD,

    /** The full CD band (to ~20 kHz and beyond) in a CD-rate lossless file. */
    LOSSLESS_CD,

    /** A lossless container whose sound stops at a lossy encoder's ceiling. */
    LOSSY_SOURCE,

    /** A lossy stream, honestly so (YouTube's AAC or Opus). */
    LOSSY_STREAM,

    /** The music itself has too little treble to tell (old or very quiet recordings). */
    INCONCLUSIVE,
}

/**
 * Pure arithmetic behind the frequency check: no Android, pinned by a JVM test.
 */
object SpectrumAnalysis {

    /** Seconds of audio averaged before a verdict is offered. */
    const val SECONDS_FOR_VERDICT = 8f

    /** Above this the treble counts as the CD band reached. */
    const val CD_BAND_HZ = 19_500

    /** Above this a hi-res file has shown sound a CD cannot hold. */
    const val HI_RES_EVIDENCE_HZ = 24_000

    /** Below this a missing top end is the music, not the encoding. */
    private const val LOSSY_FLOOR_HZ = 14_000

    /** How far above the noise floor a frequency must stand to count as sound. */
    private const val PRESENCE_DB = 15f

    /** Power per bin to decibels, never minus infinity. */
    fun toDb(power: Double): Float = (10.0 * log10(max(power, 1e-20))).toFloat()

    /**
     * The highest frequency, in Hz, that still carries sound in [levelsDb]
     * (one averaged level per FFT bin from 0 Hz to Nyquist), or null for a
     * spectrum that is all floor.
     *
     * The floor is a low percentile of everything above 1 kHz - the dither and
     * quantisation noise every digital file has - and sound is anything
     * standing [PRESENCE_DB] above it, judged on a ~200 Hz running average so
     * one stray bin cannot fake a ceiling.
     */
    fun cutoffHz(levelsDb: FloatArray, sampleRate: Int): Int? {
        val bins = levelsDb.size
        if (bins < 16 || sampleRate <= 0) return null
        val binHz = sampleRate / 2.0 / bins
        val fromBin = (1_000 / binHz).toInt().coerceIn(0, bins - 1)
        val floor = levelsDb.copyOfRange(fromBin, bins).sorted().let { it[(it.size * 0.05).toInt()] }

        val span = max(1, (200 / binHz).toInt())
        var sum = 0.0
        val smoothed = FloatArray(bins)
        for (i in 0 until bins) {
            sum += levelsDb[i]
            if (i >= span) sum -= levelsDb[i - span]
            smoothed[i] = (sum / minOf(i + 1, span)).toFloat()
        }
        for (i in bins - 1 downTo fromBin) {
            if (smoothed[i] > floor + PRESENCE_DB) return ((i + 1) * binHz).toInt()
        }
        return null
    }

    /**
     * The verdict for [cutoffHz] heard over [seconds] of a track decoded at
     * [sampleRate], [lossless] saying whether its codec is a lossless one.
     */
    fun verdict(lossless: Boolean, sampleRate: Int, cutoffHz: Int?, seconds: Float): QualityVerdict {
        if (!lossless) return QualityVerdict.LOSSY_STREAM
        if (seconds < SECONDS_FOR_VERDICT || cutoffHz == null) return QualityVerdict.LISTENING
        val hiResRate = sampleRate > 48_000
        return when {
            hiResRate && cutoffHz > HI_RES_EVIDENCE_HZ -> QualityVerdict.GENUINE_HI_RES
            cutoffHz >= CD_BAND_HZ -> if (hiResRate) QualityVerdict.UPSAMPLED_CD else QualityVerdict.LOSSLESS_CD
            cutoffHz >= LOSSY_FLOOR_HZ -> QualityVerdict.LOSSY_SOURCE
            else -> QualityVerdict.INCONCLUSIVE
        }
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
