package com.ivor.ivormusic.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class MusicTimeStretcherTest {
    private val rate = 48_000

    /** Two tones a musical interval apart, stereo, a few seconds long. */
    private fun tone(seconds: Int): ShortArray {
        val frames = rate * seconds
        val out = ShortArray(frames * 2)
        for (n in 0 until frames) {
            val value = sin(2 * PI * 220 * n / rate) * 0.5 + sin(2 * PI * 587 * n / rate) * 0.25
            val sample = (value * 20_000).toInt().toShort()
            out[n * 2] = sample
            out[n * 2 + 1] = sample
        }
        return out
    }

    private fun stretch(input: ShortArray, tempo: Float): Pair<ShortArray, MusicTimeStretcher> {
        val stretcher = MusicTimeStretcher(rate, 2, tempo)
        val collected = ArrayList<Short>(input.size)
        var position = 0
        while (position < input.size) {
            val count = minOf(8_192, input.size - position)
            stretcher.push(input.copyOfRange(position, position + count), count)
            for (i in 0 until stretcher.outputSamples) collected.add(stretcher.output[i])
            stretcher.clearOutput()
            position += count
        }
        return collected.toShortArray() to stretcher
    }

    @Test fun `the output is the input divided by the tempo`() {
        val input = tone(4)
        for (tempo in listOf(0.92f, 0.96f, 1.04f, 1.08f)) {
            val (output, stretcher) = stretch(input, tempo)
            val ratio = stretcher.consumedFrames.toDouble() / (output.size / 2)
            assertEquals("tempo $tempo", tempo.toDouble(), ratio, 0.01)
        }
    }

    @Test fun `joins leave no step larger than the music's own`() {
        val input = tone(3)
        val natural = (2 until input.size step 2).maxOf { abs(input[it] - input[it - 2]) }
        for (tempo in listOf(0.92f, 1.08f)) {
            val (output, _) = stretch(input, tempo)
            val step = (2 until output.size step 2).maxOf { abs(output[it] - output[it - 2]) }
            assertTrue("tempo $tempo: step $step against $natural", step <= natural * 1.15)
        }
    }

    @Test fun `pitch is unchanged`() {
        val input = tone(4)
        fun risingCrossingsPerSecond(samples: ShortArray): Double {
            var crossings = 0
            for (i in 2 until samples.size step 2) if (samples[i - 2] < 0 && samples[i] >= 0) crossings++
            return crossings / (samples.size / 2.0 / rate)
        }
        val source = risingCrossingsPerSecond(input)
        for (tempo in listOf(0.92f, 1.08f)) {
            val (output, _) = stretch(input, tempo)
            assertEquals("tempo $tempo", source, risingCrossingsPerSecond(output), source * 0.02)
        }
    }

    @Test fun `draining writes out everything that was waiting`() {
        val input = tone(1)
        val stretcher = MusicTimeStretcher(rate, 2, 1.05f)
        stretcher.push(input, input.size)
        val before = stretcher.outputSamples
        val waiting = stretcher.waitingFrames
        assertTrue(waiting > 0)
        stretcher.drain()
        assertEquals(0, stretcher.waitingFrames)
        assertEquals(input.size / 2L, stretcher.consumedFrames)
        // Everything waiting comes out, less the stretch a final join takes up.
        assertTrue(stretcher.outputSamples - before >= (waiting - 2_000) * 2)
    }

    @Test fun `input arriving in small uneven pieces gives the same length`() {
        val input = tone(2)
        val stretcher = MusicTimeStretcher(rate, 2, 1.06f)
        var produced = 0
        var position = 0
        var size = 222
        while (position < input.size) {
            val count = minOf(size, input.size - position)
            stretcher.push(input.copyOfRange(position, position + count), count)
            produced += stretcher.outputSamples
            stretcher.clearOutput()
            position += count
            size = if (size == 222) 1_998 else 222
        }
        assertEquals(1.06, stretcher.consumedFrames.toDouble() / (produced / 2), 0.01)
    }
}
