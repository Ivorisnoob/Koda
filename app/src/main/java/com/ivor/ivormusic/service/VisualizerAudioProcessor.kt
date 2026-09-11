package com.ivor.ivormusic.service

import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.ivor.ivormusic.util.VisualizerMath
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.sqrt

/**
 * Where the player visualizer's band levels live between the audio thread that
 * measures them and the composable that draws them.
 *
 * Process-wide, and deliberately not one of the ten process-wide repositories
 * invariant 6 lists: this holds no user data, is not persisted, is not part of
 * a backup, and is empty again the moment nothing is drawing. It is transport,
 * the way the shared OkHttp dispatcher is - the alternative is publishing
 * thirty arrays a second through session extras, which is a channel meant for
 * state that survives the UI rather than for a frame signal.
 *
 * Both crossfade engines own their own sink and therefore their own tap, so a
 * transition has two writers. Their submissions are merged by taking the
 * louder band, which is also what an overlap actually sounds like; last writer
 * wins would flicker between two tracks for the length of every fade.
 */
object VisualizerBus {

    /** How often levels reach the UI. Half of a 60Hz frame budget is plenty. */
    private const val EMIT_INTERVAL_MS = 33L

    /** After this long with no PCM the strip is stale and reads as silence. */
    const val STALE_AFTER_MS = 120L

    private val subscribers = AtomicInteger(0)
    private val lock = Any()
    private var pending: FloatArray? = null
    private var lastEmitMs = 0L

    @Volatile private var requestedBands = 32

    @Volatile private var lastSubmitMs = 0L

    private val _levels = MutableStateFlow(FloatArray(0))

    /** Latest merged band levels in 0..1. A new array on every submission. */
    val levels: StateFlow<FloatArray> = _levels.asStateFlow()

    /** Whether anything is drawing. False means the taps do no analysis work. */
    val isActive: Boolean get() = subscribers.get() > 0

    /** How many bands the drawing surface wants. Read on the audio thread. */
    val bandCount: Int get() = requestedBands

    /** True when no PCM has arrived recently, so the strip should settle. */
    fun isStale(nowMs: Long = SystemClock.uptimeMillis()): Boolean =
        nowMs - lastSubmitMs > STALE_AFTER_MS

    fun acquire(bandCount: Int) {
        requestedBands = bandCount.coerceIn(1, 128)
        subscribers.incrementAndGet()
    }

    fun release() {
        if (subscribers.decrementAndGet() <= 0) {
            subscribers.set(0)
            synchronized(lock) { pending = null }
            _levels.value = FloatArray(0)
        }
    }

    /** Called from an audio thread, once per analysed window. */
    fun submit(bands: FloatArray) {
        if (!isActive) return
        var emit: FloatArray? = null
        val now = SystemClock.uptimeMillis()
        lastSubmitMs = now
        synchronized(lock) {
            val accumulated = pending
            if (accumulated == null || accumulated.size != bands.size) {
                pending = bands.copyOf()
            } else {
                for (i in accumulated.indices) {
                    if (bands[i] > accumulated[i]) accumulated[i] = bands[i]
                }
            }
            if (now - lastEmitMs >= EMIT_INTERVAL_MS) {
                lastEmitMs = now
                emit = pending
                pending = null
            }
        }
        emit?.let { _levels.value = it }
    }
}

/**
 * The loudest sample heard since the last read, for the waveform seek bar.
 *
 * Separate from [VisualizerBus] because the two want different work. The visualizer needs a
 * spectrum and only while something is drawing it; the waveform needs one number and wants it
 * for the whole of every song, whether or not the player is on screen - so gating it on the
 * visualizer would have meant running an FFT forty times a second purely to reach a peak, or
 * only ever measuring the part of a song someone happened to be watching.
 *
 * A crossfade has two writers, and like the visualizer they merge by taking the louder, which
 * is what an overlap actually sounds like. The few seconds where both contribute are recorded
 * against the incoming song's timeline; that is a known and deliberate smear rather than a
 * correctness claim.
 */
object WaveformTap {

    private val subscribers = AtomicInteger(0)
    private val lock = Any()
    private var energy = 0.0
    private var windows = 0

    /** False means the taps skip the measurement entirely. */
    val isActive: Boolean get() = subscribers.get() > 0

    fun acquire() {
        subscribers.incrementAndGet()
    }

    fun release() {
        if (subscribers.decrementAndGet() <= 0) {
            subscribers.set(0)
            synchronized(lock) {
                energy = 0.0
                windows = 0
            }
        }
    }

    /**
     * Called from an audio thread, once per analysed window, with that window's mean square.
     *
     * [scar] This carried the window's *peak* first, and it made the bar nearly flat on most
     * songs. Peak amplitude in anything mastered sits near full scale almost everywhere, so
     * every bucket saturated and a whole song drew as one rectangle. Loudness that visibly
     * differs between a verse and a chorus is RMS, so energy is what accumulates here and the
     * root is taken once on the way out.
     */
    fun submitEnergy(meanSquare: Float) {
        if (!meanSquare.isFinite() || meanSquare < 0f) return
        synchronized(lock) {
            energy += meanSquare.toDouble()
            windows++
        }
    }

    /**
     * Root-mean-square since the previous call, and resets. Read-and-reset rather than a plain
     * read so a sampler still measures everything it stepped over, instead of whatever happened
     * to be playing at the instant it looked. Returns -1 when nothing was heard at all, which a
     * caller must not confuse with a measured silence.
     */
    fun takeRms(): Float = synchronized(lock) {
        if (windows == 0) return -1f
        val mean = energy / windows
        energy = 0.0
        windows = 0
        sqrt(mean).toFloat()
    }
}

/**
 * A pass-through tap on Koda's own decoded PCM, feeding [VisualizerBus]
 * and [WaveformTap].
 *
 * This is why the visualizer needs no permission. Both engines already build
 * their sink with an app-owned processor chain for the AutoMix filter, so the
 * samples are in the app's hands on their way to the AudioTrack; reading them
 * here is not audio capture in the platform's sense and lights no privacy
 * indicator. It sits after the transition filter so the strip shows what is
 * actually heard during an overlap rather than the unfiltered source.
 *
 * The buffer is copied through untouched. Analysis happens only while
 * something is drawing, so with the visualizer off the cost is one memcpy of
 * each PCM buffer and nothing else.
 */
@UnstableApi
class VisualizerAudioProcessor : BaseAudioProcessor() {

    private val window = FloatArray(VisualizerMath.FFT_SIZE)
    private val scratchReal = FloatArray(VisualizerMath.FFT_SIZE)
    private val scratchImaginary = FloatArray(VisualizerMath.FFT_SIZE)
    private var filled = 0
    private var windowEnergy = 0.0

    private var bands = FloatArray(0)
    private var edges = IntArray(0)

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        // Media3 pumps EMPTY_BUFFER through the tail of the pipeline to drain
        // it, and BaseAudioProcessor owns that same singleton as its initial
        // zero-capacity output - asking for a zero-byte replacement and then
        // copying would be a buffer onto itself.
        if (!inputBuffer.hasRemaining()) return
        val output = replaceOutputBuffer(inputBuffer.remaining()).order(ByteOrder.nativeOrder())
        inputBuffer.order(ByteOrder.nativeOrder())
        output.put(inputBuffer)
        output.flip()
        if (!VisualizerBus.isActive && !WaveformTap.isActive) return
        // Read the copy rather than rewinding the input: the pipeline decides
        // whether to keep feeding this processor from what the input buffer has
        // left, so putting its position back would re-queue the same PCM.
        accumulate(output.duplicate().order(ByteOrder.nativeOrder()))
    }

    private fun accumulate(samples: ByteBuffer) {
        val channels = inputAudioFormat.channelCount.coerceAtLeast(1)
        while (samples.remaining() >= 2 * channels) {
            var sum = 0f
            for (channel in 0 until channels) sum += samples.short / 32768f
            val mono = sum / channels
            window[filled++] = mono
            windowEnergy += mono.toDouble() * mono
            if (filled == VisualizerMath.FFT_SIZE) {
                filled = 0
                if (VisualizerBus.isActive) analyze()
                if (WaveformTap.isActive) {
                    WaveformTap.submitEnergy((windowEnergy / VisualizerMath.FFT_SIZE).toFloat())
                }
                windowEnergy = 0.0
            }
        }
    }

    private fun analyze() {
        val wanted = VisualizerBus.bandCount
        if (bands.size != wanted) {
            bands = FloatArray(wanted)
            edges = VisualizerMath.bandEdges(VisualizerMath.FFT_SIZE / 2, wanted)
        }
        VisualizerMath.analyze(window, scratchReal, scratchImaginary, edges, bands)
        VisualizerBus.submit(bands)
    }

    override fun onFlush() {
        filled = 0
        windowEnergy = 0.0
    }

    override fun onReset() {
        filled = 0
        windowEnergy = 0.0
        bands = FloatArray(0)
        edges = IntArray(0)
    }
}
