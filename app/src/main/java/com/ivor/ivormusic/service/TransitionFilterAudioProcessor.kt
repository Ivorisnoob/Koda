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
 * Reset-safe high-pass used only on the outgoing half of an AutoMix overlap.
 *
 * Two gentle one-pole stages remove bass progressively without the resonance
 * and clipping risk of an aggressive DJ-style filter. [sweep] is lock-free
 * because playback writes it on the main thread while Media3 consumes PCM on
 * its audio thread. At zero the processor is bit-for-bit bypassed.
 */
@UnstableApi
class TransitionFilterAudioProcessor : BaseAudioProcessor() {

    @Volatile private var requestedSweep = 0f
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
        if (sweep <= BYPASS_EPSILON || inputAudioFormat.channelCount <= 0) {
            if (!bypassed) clearHistory()
            bypassed = true
            output.put(inputBuffer)
            output.flip()
            return
        }
        bypassed = false

        val channels = inputAudioFormat.channelCount
        val frames = inputBuffer.remaining() / (2 * channels)
        val cutoffHz = MIN_CUTOFF_HZ * (MAX_CUTOFF_HZ / MIN_CUTOFF_HZ).pow(sweep)
        val targetAlpha = exp((-2.0 * PI * cutoffHz / inputAudioFormat.sampleRate)).toFloat()
        val alphaStep = if (frames > 0) (targetAlpha - currentAlpha) / frames else 0f

        var frame = 0
        while (inputBuffer.remaining() >= 2 * channels) {
            currentAlpha += alphaStep
            for (channel in 0 until channels) {
                val sample = inputBuffer.short / 32768f
                val first = currentAlpha * (y1[channel] + sample - x1[channel])
                x1[channel] = sample
                y1[channel] = first
                val second = currentAlpha * (y2[channel] + first - x2[channel])
                x2[channel] = first
                y2[channel] = second
                output.putShort((second.coerceIn(-1f, 0.999969f) * 32768f).toInt().toShort())
            }
            frame++
        }
        // Defensive copy for a malformed partial PCM frame.
        while (inputBuffer.hasRemaining()) output.put(inputBuffer.get())
        output.flip()
    }

    override fun onFlush() = clearHistory()

    override fun onReset() {
        requestedSweep = 0f
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
    }

    private companion object {
        const val BYPASS_EPSILON = 0.001f
        const val MIN_CUTOFF_HZ = 20f
        const val MAX_CUTOFF_HZ = 1_800f
    }
}
