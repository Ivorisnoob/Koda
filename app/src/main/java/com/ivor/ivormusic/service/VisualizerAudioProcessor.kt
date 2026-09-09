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
 * A pass-through tap on Koda's own decoded PCM, feeding [VisualizerBus].
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
        if (!VisualizerBus.isActive) return
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
            window[filled++] = sum / channels
            if (filled == VisualizerMath.FFT_SIZE) {
                filled = 0
                analyze()
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
    }

    override fun onReset() {
        filled = 0
        bands = FloatArray(0)
        edges = IntArray(0)
    }
}
