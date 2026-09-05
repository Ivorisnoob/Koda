package com.ivor.ivormusic.data

import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class AudioRhythmTest {
    private fun analyse(envelope: FloatArray) = AudioProfiler.analyseRhythm(
        AudioProfiler.DecodedAudio(envelope, FloatArray(0), 44_100, 0L, complete = true)
    )

    @Test fun `regular 120 bpm pulses have tempo evidence without inventing a downbeat`() {
        val rhythm = analyse(FloatArray(750) { if (it % 25 == 0) 1f else 0f })
        assertEquals(120f, rhythm.bpm!!, 1f)
        assertTrue(rhythm.confidence > 0.35f)
        assertTrue(rhythm.barConfidence < 0.28f)
    }

    @Test fun `accented bars have separate downbeat evidence`() {
        val rhythm = analyse(FloatArray(750) {
            when { it % 100 == 0 -> 1f; it % 25 == 0 -> 0.5f; else -> 0f }
        })
        assertEquals(120f, rhythm.bpm!!, 1f)
        assertTrue(rhythm.barConfidence > 0.28f)
    }

    @Test fun `silence and constant energy do not produce a beat grid`() {
        assertNull(analyse(FloatArray(750)).bpm)
        assertNull(analyse(FloatArray(750) { 0.5f }).bpm)
    }

    @Test fun `irregular noise is not confident enough for tempo correction`() {
        val random = Random(73)
        val rhythm = analyse(FloatArray(750) { random.nextFloat() })
        assertTrue(rhythm.confidence < 0.35f)
    }
}
