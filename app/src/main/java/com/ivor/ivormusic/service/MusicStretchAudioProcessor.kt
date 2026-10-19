package com.ivor.ivormusic.service

import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessorChain
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * [MusicTimeStretcher] as a Media3 processor: the tempo change for speeds close
 * to normal, where the music has to stay intact.
 *
 * Inactive at exactly 1.0, where it is not in the pipeline at all. A new speed
 * takes effect at the next flush, which is when the sink applies one: it drains
 * the pipeline, so [onQueueEndOfStream] writes out what was waiting, and then
 * flushes, so the next sequence starts clean at the new tempo.
 */
@UnstableApi
class MusicStretchAudioProcessor : BaseAudioProcessor() {

    @Volatile private var speed = 1f
    private var stretcher: MusicTimeStretcher? = null
    private var scratch = ShortArray(0)

    // Frames in and out since the last flush, for mapping the sink's clock
    // back onto the media.
    @Volatile private var inputFrames = 0L
    @Volatile private var outputFrames = 0L
    @Volatile private var waitingFrames = 0L

    fun setSpeed(value: Float) {
        speed = value
    }

    override fun isActive(): Boolean = super.isActive() && abs(speed - 1f) >= SPEED_EPSILON

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        val active = stretcher ?: return passThrough(inputBuffer)
        val channels = inputAudioFormat.channelCount
        val samples = inputBuffer.remaining() / 2
        // Whole frames only; a stray byte or sample is left for the next call.
        val usable = samples - samples % channels
        if (scratch.size < usable) scratch = ShortArray(usable)
        inputBuffer.order(ByteOrder.nativeOrder()).asShortBuffer().get(scratch, 0, usable)
        // PCM arrives in whole frames; anything else is dropped rather than
        // left unconsumed, which the pipeline would offer back forever.
        inputBuffer.position(inputBuffer.limit())
        inputFrames += usable / channels
        active.push(scratch, usable)
        emit(active)
    }

    override fun onQueueEndOfStream() {
        val active = stretcher ?: return
        active.drain()
        emit(active)
    }

    override fun onFlush() {
        val format = inputAudioFormat
        stretcher = if (format.channelCount > 0 && format.sampleRate > 0) {
            MusicTimeStretcher(format.sampleRate, format.channelCount, speed)
        } else null
        inputFrames = 0L
        outputFrames = 0L
        waitingFrames = 0L
    }

    override fun onReset() {
        stretcher = null
        speed = 1f
        scratch = ShortArray(0)
    }

    /**
     * How much media the sink has played, given how much stretched audio it
     * has written. Measured from the frames that actually went in and came
     * out, the way Sonic's processor does it, so it stays exact over a long
     * mix instead of drifting by the rounding in each sequence.
     */
    fun getMediaDuration(playoutDurationUs: Long): Long {
        if (abs(speed - 1f) < SPEED_EPSILON) return playoutDurationUs
        val written = outputFrames
        return if (written >= MIN_FRAMES_FOR_MEASURED_RATIO) {
            Util.scaleLargeTimestamp(playoutDurationUs, inputFrames - waitingFrames, written)
        } else {
            (playoutDurationUs * speed.toDouble()).toLong()
        }
    }

    private fun emit(active: MusicTimeStretcher) {
        waitingFrames = active.waitingFrames.toLong()
        val count = active.outputSamples
        if (count == 0) return
        val out = replaceOutputBuffer(count * 2).order(ByteOrder.nativeOrder())
        out.asShortBuffer().put(active.output, 0, count)
        out.position(count * 2)
        out.flip()
        outputFrames += count / inputAudioFormat.channelCount
        active.clearOutput()
    }

    private fun passThrough(inputBuffer: ByteBuffer) {
        val out = replaceOutputBuffer(inputBuffer.remaining())
        out.put(inputBuffer)
        out.flip()
    }

    private companion object {
        const val SPEED_EPSILON = 0.0001f
        const val MIN_FRAMES_FOR_MEASURED_RATIO = 512L
    }
}

/**
 * The music player's processor chain: Koda's own processors, then the tempo
 * change, which is one of two stretchers depending on how far from normal the
 * speed is.
 *
 * Close to normal - a mix bringing two songs onto one beat, or a listener's
 * slightly faster or slower setting - [MusicStretchAudioProcessor] keeps the
 * music intact. Further out, where the job is "play this podcast-fast" and
 * Sonic's range and pitch control matter more than fidelity, Sonic does it, as
 * it always has. Only one is ever active.
 *
 * This replaces the sink's default chain, which is the same [processors]
 * followed by silence skipping and Sonic. Silence skipping is not offered for
 * music, so it is left out and reported as unsupported.
 */
@UnstableApi
class MusicAudioProcessorChain(processors: Array<AudioProcessor>) : AudioProcessorChain {

    private val sonic = SonicAudioProcessor()
    private val stretch = MusicStretchAudioProcessor()
    private val all: Array<AudioProcessor> = arrayOf(*processors, sonic, stretch)

    override fun getAudioProcessors(): Array<AudioProcessor> = all

    override fun applyPlaybackParameters(playbackParameters: PlaybackParameters): PlaybackParameters {
        val musical = playbackParameters.pitch == 1f &&
            playbackParameters.speed in MUSIC_STRETCH_MIN_SPEED..MUSIC_STRETCH_MAX_SPEED
        if (musical) {
            sonic.setSpeed(1f)
            sonic.setPitch(1f)
            stretch.setSpeed(playbackParameters.speed)
        } else {
            stretch.setSpeed(1f)
            sonic.setSpeed(playbackParameters.speed)
            sonic.setPitch(playbackParameters.pitch)
        }
        return playbackParameters
    }

    // The stretcher is last, so its output is what the sink counts.
    override fun getMediaDuration(playoutDuration: Long): Long =
        sonic.getMediaDuration(stretch.getMediaDuration(playoutDuration))

    override fun getSkippedOutputFrameCount(): Long = 0L

    override fun applySkipSilenceEnabled(skipSilenceEnabled: Boolean): Boolean = false

    private companion object {
        /**
         * The range the music stretcher handles. Its sequence length is set
         * for small changes; past about a fifth either way the joins come too
         * often to hide and Sonic's coarser method is the better trade.
         */
        const val MUSIC_STRETCH_MIN_SPEED = 0.84f
        const val MUSIC_STRETCH_MAX_SPEED = 1.19f
    }
}
