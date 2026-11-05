package com.ivor.ivormusic.service

import android.os.SystemClock
import com.ivor.ivormusic.service.core.SpectrumAnalysis
import com.ivor.ivormusic.util.VisualizerMath
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.PI
import kotlin.math.cos

/** One look at the averaged spectrum of what has played since the check began. */
class SpectrumReading(
    val sampleRate: Int,
    /** Averaged level per FFT bin, 0 Hz to Nyquist, in dBFS-ish. */
    val levelsDb: FloatArray,
    /** Audio heard so far, in seconds. */
    val seconds: Float,
    /** Highest frequency still carrying sound, or null while there is none. */
    val cutoffHz: Int?,
)

/**
 * The quality sheet's frequency check, fed from Koda's own decoded PCM in
 * [VisualizerAudioProcessor].
 *
 * Transport like [VisualizerBus], not repository state: nothing persisted,
 * nothing a listener entered, and it does no work at all unless the quality
 * sheet is open ([acquire]). Then every 4096-sample window - about 23 a second
 * at 96 kHz - is windowed, transformed and added to a running average of power
 * per bin, which is what a spectrum analyser shows and what a ceiling stands
 * out in. Long averages are the point: one window of a quiet verse says
 * nothing, a few seconds of a song says a lot.
 *
 * The PCM here is 16-bit at the source's own sample rate: the sink lowers the
 * depth but never resamples, so a 96 kHz file is measured to 48 kHz. Bit
 * depth cannot be verified this way and the sheet does not claim to.
 */
object SpectrumProbe {
    const val FFT_SIZE = 4096
    private const val PUBLISH_INTERVAL_MS = 250L

    private val subscribers = AtomicInteger(0)
    private val lock = Any()
    private val hann = FloatArray(FFT_SIZE) { i -> (0.5 - 0.5 * cos(2.0 * PI * i / (FFT_SIZE - 1))).toFloat() }
    private val re = FloatArray(FFT_SIZE)
    private val im = FloatArray(FFT_SIZE)
    private var power = DoubleArray(FFT_SIZE / 2)
    private var windows = 0
    private var sampleRate = 0
    private var lastPublishMs = 0L

    private val _reading = MutableStateFlow<SpectrumReading?>(null)
    val reading: StateFlow<SpectrumReading?> = _reading.asStateFlow()

    val isActive: Boolean get() = subscribers.get() > 0

    fun acquire() {
        if (subscribers.incrementAndGet() == 1) reset()
    }

    fun release() {
        if (subscribers.decrementAndGet() <= 0) {
            subscribers.set(0)
            reset()
        }
    }

    /** Start the average again: a new song is a new measurement. */
    fun reset() {
        synchronized(lock) {
            power = DoubleArray(FFT_SIZE / 2)
            windows = 0
            sampleRate = 0
        }
        _reading.value = null
    }

    /** One full window of mono samples at [rate]. Audio thread. */
    fun submit(window: FloatArray, rate: Int) {
        if (!isActive || window.size != FFT_SIZE || rate <= 0) return
        var publish: SpectrumReading? = null
        synchronized(lock) {
            // A rate change mid-measurement is another song; begin again.
            if (rate != sampleRate) {
                power = DoubleArray(FFT_SIZE / 2)
                windows = 0
                sampleRate = rate
            }
            for (i in 0 until FFT_SIZE) {
                re[i] = window[i] * hann[i]
                im[i] = 0f
            }
            VisualizerMath.fft(re, im)
            for (bin in 0 until FFT_SIZE / 2) {
                power[bin] += (re[bin] * re[bin] + im[bin] * im[bin]).toDouble()
            }
            windows++
            val now = SystemClock.uptimeMillis()
            if (now - lastPublishMs >= PUBLISH_INTERVAL_MS) {
                lastPublishMs = now
                val levels = FloatArray(FFT_SIZE / 2) { bin -> SpectrumAnalysis.toDb(power[bin] / windows) }
                publish = SpectrumReading(
                    sampleRate = rate,
                    levelsDb = levels,
                    seconds = windows.toFloat() * FFT_SIZE / rate,
                    cutoffHz = SpectrumAnalysis.cutoffHz(levels, rate),
                )
            }
        }
        publish?.let { _reading.value = it }
    }
}
