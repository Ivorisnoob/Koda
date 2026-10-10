package com.ivor.ivormusic.service

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow

/**
 * The effects an AutoMix overlap puts on one track: a reset-safe high-pass,
 * and an echo for the echo-out.
 *
 * The filter is two gentle one-pole stages that remove bass progressively
 * without the resonance and clipping risk of an aggressive DJ-style filter.
 * The echo is a feedback delay whose input closes as it opens: at full amount
 * the track itself is silent and only its repeats are left, dying away.
 *
 * Both amounts are lock-free because playback writes them on the main thread
 * while Media3 consumes PCM on its audio thread. With both at zero the
 * processor is bit-for-bit bypassed.
 */
@UnstableApi
class TransitionFilterAudioProcessor : BaseAudioProcessor() {

    @Volatile private var requestedSweep = 0f
    @Volatile private var requestedEcho = 0f
    @Volatile private var requestedEchoDelayMs = 0
    private var echoLine = FloatArray(0)
    private var echoIndex = 0
    private var echoFrames = 0
    private var currentEcho = 0f
    private var currentAlpha = 1f
    private var x1 = FloatArray(0)
    private var y1 = FloatArray(0)
    private var x2 = FloatArray(0)
    private var y2 = FloatArray(0)
    private var bypassed = true

    fun setSweep(amount: Float) {
        requestedSweep = amount.coerceIn(0f, 1f)
    }

    fun clearSweep() {
        requestedSweep = 0f
    }

    /**
     * [amount] 0..1: how far the track has been handed over to its own echo.
     * [delayMs] is the repeat time, read when the echo starts.
     */
    fun setEcho(amount: Float, delayMs: Int) {
        requestedEchoDelayMs = delayMs
        requestedEcho = if (delayMs > 0) amount.coerceIn(0f, 1f) else 0f
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        allocateChannels(inputAudioFormat.channelCount)
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        // Media3 pumps EMPTY_BUFFER through the tail of a processing pipeline
        // to drain downstream processors. BaseAudioProcessor initially owns
        // that same singleton as its zero-capacity output buffer, so asking it
        // for a zero-byte replacement and then copying would be a buffer onto
        // itself. There is nothing to consume or emit in this case.
        if (!inputBuffer.hasRemaining()) return
        val output = replaceOutputBuffer(inputBuffer.remaining()).order(ByteOrder.nativeOrder())
        inputBuffer.order(ByteOrder.nativeOrder())
        val sweep = requestedSweep
        val echo = requestedEcho
        if ((sweep <= BYPASS_EPSILON && echo <= BYPASS_EPSILON && currentEcho <= BYPASS_EPSILON) ||
            inputAudioFormat.channelCount <= 0
        ) {
            if (!bypassed) clearHistory()
            bypassed = true
            output.put(inputBuffer)
            output.flip()
            return
        }
        bypassed = false

        val channels = inputAudioFormat.channelCount
        val frames = inputBuffer.remaining() / (2 * channels)
        val filtering = sweep > BYPASS_EPSILON
        val cutoffHz = MIN_CUTOFF_HZ * (MAX_CUTOFF_HZ / MIN_CUTOFF_HZ).pow(sweep)
        val targetAlpha = if (filtering) {
            exp((-2.0 * PI * cutoffHz / inputAudioFormat.sampleRate)).toFloat()
        } else 1f
        val alphaStep = if (frames > 0) (targetAlpha - currentAlpha) / frames else 0f
        if (echo > BYPASS_EPSILON && echoFrames == 0) startEcho(channels)
        val echoStep = if (frames > 0) (echo - currentEcho) / frames else 0f

        var frame = 0
        while (inputBuffer.remaining() >= 2 * channels) {
            currentAlpha += alphaStep
            currentEcho += echoStep
            val dry = 1f - currentEcho
            for (channel in 0 until channels) {
                val sample = inputBuffer.short / 32768f
                var value = sample
                if (filtering || currentAlpha < 0.9999f) {
                    val first = currentAlpha * (y1[channel] + sample - x1[channel])
                    x1[channel] = sample
                    y1[channel] = first
                    val second = currentAlpha * (y2[channel] + first - x2[channel])
                    x2[channel] = first
                    y2[channel] = second
                    value = second
                }
                if (echoFrames > 0) {
                    val slot = echoIndex * channels + channel
                    val repeat = echoLine[slot]
                    // The line is fed by what is still playing, so as the
                    // track closes the repeats are of its last moments.
                    echoLine[slot] = value * dry * ECHO_SEND + repeat * ECHO_FEEDBACK
                    value = value * dry + repeat
                }
                output.putShort((value.coerceIn(-1f, 0.999969f) * 32768f).toInt().toShort())
            }
            if (echoFrames > 0) echoIndex = (echoIndex + 1) % echoFrames
            frame++
        }
        // Defensive copy for a malformed partial PCM frame.
        while (inputBuffer.hasRemaining()) output.put(inputBuffer.get())
        output.flip()
    }

    override fun onFlush() = clearHistory()

    override fun onReset() {
        requestedSweep = 0f
        requestedEcho = 0f
        requestedEchoDelayMs = 0
        echoLine = FloatArray(0)
        echoFrames = 0
        echoIndex = 0
        currentEcho = 0f
        x1 = FloatArray(0); y1 = FloatArray(0)
        x2 = FloatArray(0); y2 = FloatArray(0)
        currentAlpha = 1f
        bypassed = true
    }

    private fun allocateChannels(channels: Int) {
        x1 = FloatArray(channels); y1 = FloatArray(channels)
        x2 = FloatArray(channels); y2 = FloatArray(channels)
        currentAlpha = 1f
        bypassed = true
    }

    private fun clearHistory() {
        x1.fill(0f); y1.fill(0f); x2.fill(0f); y2.fill(0f)
        currentAlpha = 1f
        // An echo that has been switched off starts from silence next time.
        echoFrames = 0
        echoIndex = 0
        currentEcho = 0f
    }

    private fun startEcho(channels: Int) {
        val frames = (inputAudioFormat.sampleRate.toLong() * requestedEchoDelayMs / 1000L)
            .toInt().coerceIn(1, inputAudioFormat.sampleRate)
        if (echoLine.size < frames * channels) echoLine = FloatArray(frames * channels)
        else echoLine.fill(0f)
        echoFrames = frames
        echoIndex = 0
    }

    private companion object {
        const val BYPASS_EPSILON = 0.001f
        const val MIN_CUTOFF_HZ = 20f
        const val MAX_CUTOFF_HZ = 1_800f
        /** How much of the playing track feeds the echo, and how much of each repeat survives. */
        const val ECHO_SEND = 0.7f
        const val ECHO_FEEDBACK = 0.5f
    }
}
