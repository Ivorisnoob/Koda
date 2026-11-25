package com.ivor.ivormusic.service

import android.os.SystemClock
import com.ivor.ivormusic.service.core.QualityEvidence
import com.ivor.ivormusic.service.core.SpectrumAccumulator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger

/** One look at a song's measured spectrum and what it shows. */
class SpectrumReading(
    val sampleRate: Int,
    /** Averaged level per FFT bin, 0 Hz to Nyquist, in dBFS-ish. */
    val levelsDb: FloatArray,
    /** Audio measured so far, in seconds. */
    val seconds: Float,
    val evidence: QualityEvidence,
    /** True when the whole file was decoded, rather than a stretch of playback heard. */
    val wholeFile: Boolean = false,
    /** True when the samples were read at the file's own depth, so [usedBits] means something. */
    val fullDepth: Boolean = false,
    /** Bits of a 24-bit grid the samples actually use, when [fullDepth]. */
    val usedBits: Int? = null,
) {
    /** Highest frequency still carrying sound, or null while there is none. */
    val cutoffHz: Int? get() = evidence.cutoffHz
}

/**
 * The quality sheet's frequency check on what is playing, fed from Koda's
 * own decoded PCM in [VisualizerAudioProcessor]. Used for songs streamed from
 * the lossless server; a song whose file is on the device is checked whole
 * instead ([FileQualityAnalyzer]).
 *
 * Transport like [VisualizerBus], not repository state: nothing persisted,
 * nothing a listener entered, and it does no work at all unless the quality
 * sheet is open ([acquire]). Then every 4096-sample window goes into a
 * [SpectrumAccumulator], which is what a spectrum analyser shows and what a
 * ceiling stands out in.
 *
 * The PCM here is 16-bit at the source's own sample rate: the sink lowers the
 * depth but never resamples, so a 96 kHz file is measured to 48 kHz. Bit
 * depth cannot be verified this way and the sheet does not claim to.
 */
object SpectrumProbe {
    const val FFT_SIZE = SpectrumAccumulator.FFT_SIZE
    private const val PUBLISH_INTERVAL_MS = 250L

    private val subscribers = AtomicInteger(0)
    private val lock = Any()
    private var accumulator: SpectrumAccumulator? = null
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

    /** Start the measurement again: a new song is a new measurement. */
    fun reset() {
        synchronized(lock) { accumulator = null }
        _reading.value = null
    }

    /** One full window of mono samples at [rate]. Audio thread. */
    fun submit(window: FloatArray, rate: Int) {
        if (!isActive || window.size != FFT_SIZE || rate <= 0) return
        var publish: SpectrumReading? = null
        synchronized(lock) {
            // A rate change mid-measurement is another song; begin again.
            val current = accumulator?.takeIf { it.sampleRate == rate }
                ?: SpectrumAccumulator(rate).also { accumulator = it }
            current.add(window)
            val now = SystemClock.uptimeMillis()
            if (now - lastPublishMs >= PUBLISH_INTERVAL_MS) {
                lastPublishMs = now
                val levels = current.levelsDb()
                publish = SpectrumReading(
                    sampleRate = rate,
                    levelsDb = levels,
                    seconds = current.seconds,
                    evidence = current.evidence(levels),
                )
            }
        }
        publish?.let { _reading.value = it }
    }
}
