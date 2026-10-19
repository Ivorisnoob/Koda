package com.ivor.ivormusic.service

import kotlin.math.sqrt

/**
 * Changes the tempo of interleaved 16-bit PCM without changing its pitch,
 * tuned for music at small ratios - the few percent AutoMix needs to bring two
 * songs onto one beat.
 *
 * **Why not the player's own stretcher.** Media3 speeds audio up and down with
 * Sonic, which looks for a single pitch period between 65 and 400 Hz. That is
 * the right model for a voice and the wrong one for a full mix, where it locks
 * onto the wrong period and repeats or drops slices that do not line up: the
 * warble that kept AutoMix's tempo correction to four percent.
 *
 * This is the overlap-add method audio tools use for music (the one SoundTouch
 * made standard). The input is cut into sequences of about 82 ms. Each is
 * joined to the one before over a 12 ms crossfade, and where exactly the next
 * sequence is taken from is searched over a 28 ms window for the position whose
 * waveform best matches the tail being faded out, so the join lands in phase
 * whatever the music is doing. Between joins the audio is copied untouched.
 * Tempo is how far the read position advances per sequence written.
 *
 * Pure arithmetic on arrays, no Android types, so it is tested on the JVM
 * ([MusicTimeStretcherTest]).
 */
internal class MusicTimeStretcher(
    sampleRate: Int,
    private val channels: Int,
    tempo: Float,
) {
    private val sequence = (sampleRate * SEQUENCE_MS / 1000).coerceAtLeast(8)
    private val seek = (sampleRate * SEEK_MS / 1000).coerceAtLeast(2)
    private val overlap = (sampleRate * OVERLAP_MS / 1000).coerceIn(1, sequence / 2 - 1)
    private val middle = sequence - 2 * overlap

    /** How far the read position moves for each sequence written, in frames. */
    private val nominalSkip = tempo.toDouble() * (sequence - overlap)

    /** Frames that must be waiting before one more sequence can be written. */
    private val required = maxOf((nominalSkip + 0.5).toInt() + overlap, sequence) + seek

    private var fifo = ShortArray(required * channels * 3)
    private var head = 0          // first waiting frame, in frames
    private var waiting = 0       // waiting frames

    /** The tail of the last sequence written, kept back to be faded into the next. */
    private val tail = ShortArray(overlap * channels)
    private var beginning = true
    private var skipFraction = 0.0

    /** Output produced and not yet taken, interleaved. */
    var output = ShortArray(sequence * channels * 4)
        private set
    var outputSamples = 0
        private set

    /** Input frames consumed for good - behind the read position. */
    var consumedFrames = 0L
        private set

    val waitingFrames: Int get() = waiting

    /** Queue [count] interleaved samples and write every sequence they complete. */
    fun push(samples: ShortArray, count: Int) {
        append(samples, count)
        while (waiting >= required) writeSequence()
    }

    /** The caller has copied [output]; start filling it again. */
    fun clearOutput() {
        outputSamples = 0
    }

    /**
     * The stream is ending or about to change tempo: write what is waiting,
     * unstretched, joined to the last sequence so nothing is dropped.
     */
    fun drain() {
        if (!beginning && waiting >= overlap) {
            val offset = if (waiting >= seek + overlap) bestOffset() else 0
            crossfadeInto(offset)
            emit(offset + overlap, waiting - offset - overlap)
        } else {
            emit(0, waiting)
        }
        consumedFrames += waiting
        head = 0
        waiting = 0
        beginning = true
        skipFraction = 0.0
    }

    private fun writeSequence() {
        var offset = 0
        if (beginning) {
            // The first sequence has nothing before it to join to.
            beginning = false
        } else {
            offset = bestOffset()
            crossfadeInto(offset)
            offset += overlap
        }
        emit(offset, middle)
        System.arraycopy(fifo, (head + offset + middle) * channels, tail, 0, overlap * channels)

        skipFraction += nominalSkip
        val skip = skipFraction.toInt().coerceAtMost(waiting)
        skipFraction -= skip
        head += skip
        waiting -= skip
        consumedFrames += skip
    }

    /**
     * Where, within the seek window, the waiting audio best continues [tail].
     * Normalised cross-correlation on the channels summed, at half resolution:
     * the search only has to find the right cycle, and the crossfade forgives a
     * sample either way.
     */
    private fun bestOffset(): Int {
        var best = 0
        var bestScore = Double.NEGATIVE_INFINITY
        val base = head * channels
        var offset = 0
        while (offset < seek) {
            var correlation = 0.0
            var energy = 1e-9
            var i = 0
            while (i < overlap) {
                var kept = 0
                var candidate = 0
                val t = i * channels
                val f = base + (offset + i) * channels
                for (c in 0 until channels) {
                    kept += tail[t + c]
                    candidate += fifo[f + c]
                }
                correlation += kept.toDouble() * candidate
                energy += candidate.toDouble() * candidate
                i += SEARCH_SAMPLE_STEP
            }
            val score = correlation / sqrt(energy)
            if (score > bestScore) {
                bestScore = score
                best = offset
            }
            offset += SEARCH_OFFSET_STEP
        }
        return best
    }

    /** Fade [tail] out against the waiting audio at [offset], and write the mix. */
    private fun crossfadeInto(offset: Int) {
        ensureOutput(overlap * channels)
        val base = (head + offset) * channels
        for (i in 0 until overlap) {
            val rise = i.toFloat() / overlap
            for (c in 0 until channels) {
                val mixed = tail[i * channels + c] * (1f - rise) + fifo[base + i * channels + c] * rise
                output[outputSamples++] = mixed.toInt().toShort()
            }
        }
    }

    private fun emit(fromFrame: Int, frames: Int) {
        if (frames <= 0) return
        ensureOutput(frames * channels)
        System.arraycopy(fifo, (head + fromFrame) * channels, output, outputSamples, frames * channels)
        outputSamples += frames * channels
    }

    private fun append(samples: ShortArray, count: Int) {
        val needed = (head + waiting) * channels + count
        if (needed > fifo.size) {
            // Slide the waiting audio to the front first; grow only if that is not enough.
            System.arraycopy(fifo, head * channels, fifo, 0, waiting * channels)
            head = 0
            if (waiting * channels + count > fifo.size) {
                fifo = fifo.copyOf(maxOf(fifo.size * 2, waiting * channels + count))
            }
        }
        System.arraycopy(samples, 0, fifo, (head + waiting) * channels, count)
        waiting += count / channels
    }

    private fun ensureOutput(extra: Int) {
        if (outputSamples + extra > output.size) {
            output = output.copyOf(maxOf(output.size * 2, outputSamples + extra))
        }
    }

    private companion object {
        const val SEQUENCE_MS = 82
        const val SEEK_MS = 28
        const val OVERLAP_MS = 12
        const val SEARCH_OFFSET_STEP = 2
        const val SEARCH_SAMPLE_STEP = 2
    }
}
