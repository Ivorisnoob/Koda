package com.ivor.ivormusic.service

import com.ivor.ivormusic.data.AudioProfile
import org.junit.Assert.*
import org.junit.Test

class TransitionPlannerTest {
    private fun rhythmic(id: String = "song", bpm: Float = 120f) = AudioProfile(
        songId = id, durationMs = 180_000L,
        bpm = bpm, tempoConfidence = 0.9f,
        outroBpm = bpm, outroTempoConfidence = 0.9f,
        outroLeadMs = 12_000L,
    )

    private fun plan(outgoing: AudioProfile?, incoming: AudioProfile?) = TransitionPlanner.plan(
        fallbackOverlapMs = 3_000L, maximumOverlapMs = 15_000L,
        outgoing = outgoing, incoming = incoming, outgoingDurationMs = 180_000L,
    )

    @Test fun `unknown tracks get a clean three second crossfade`() {
        val result = plan(null, null)
        assertEquals(3_000L, result.overlapMs)
        assertEquals(1f, result.incomingSpeed, 0f)
        assertEquals(0f, result.filterSweepStrength, 0f)
        assertEquals(0L, result.incomingStartMs)
    }

    @Test fun `near tempos get bounded pitch preserving speed correction`() {
        val result = plan(rhythmic(), rhythmic("incoming", 118f))
        assertEquals(120f / 118f, result.incomingSpeed, 0.0001f)
        assertTrue(result.overlapMs in 3_001L..15_000L)
        assertEquals(0L, result.incomingStartMs)
    }

    @Test fun `incompatible tempos do not get a long outro overlap`() {
        val result = plan(rhythmic(), rhythmic("incoming", 95f))
        assertEquals(TransitionPlan.Reason.TEMPO_MISMATCH, result.reason)
        assertEquals(1_500L, result.overlapMs)
        assertEquals(1f, result.incomingSpeed, 0f)
        assertEquals(0f, result.filterSweepStrength, 0f)
    }

    @Test fun `a lone energy dip does not authorize a twelve second mix`() {
        val result = plan(AudioProfile("out", outroLeadMs = 12_000L), null)
        assertEquals(TransitionPlan.Reason.FALLBACK, result.reason)
        assertEquals(3_000L, result.overlapMs)
    }

    @Test fun `natural fade stays brief without filter or tempo processing`() {
        val result = plan(rhythmic().copy(tailFadeMs = 2_000L), rhythmic("in", 118f))
        assertEquals(1_000L, result.overlapMs)
        assertEquals(1f, result.incomingSpeed, 0f)
        assertEquals(0f, result.filterSweepStrength, 0f)
    }

    @Test fun `sequential album playback preserves both ends even without analysis`() {
        val result = TransitionPlanner.plan(
            3_000L, outgoing = null, incoming = null, preserveAlbumSequence = true,
        )
        assertEquals(TransitionPlan.Reason.PRESERVE_ALBUM, result.reason)
        assertFalse(result.shouldOverlap)
        assertEquals(0L, result.incomingStartMs)
    }

    @Test fun `incoming interlude limits overlap to a quarter of its duration`() {
        val result = plan(rhythmic(), rhythmic("in").copy(durationMs = 16_000L))
        assertTrue(result.overlapMs in 750L..4_000L)
    }

    @Test fun `a two second incoming track is not swallowed by a transition`() {
        assertFalse(plan(rhythmic(), rhythmic("in").copy(durationMs = 2_000L)).shouldOverlap)
    }

    @Test fun `bad tempo data cannot cause invalid speed or an endless cue search`() {
        for (bpm in listOf(Float.NaN, Float.POSITIVE_INFINITY, 0f, -120f)) {
            val result = plan(rhythmic(), rhythmic("in", bpm))
            assertEquals(1f, result.incomingSpeed, 0f)
            assertTrue(result.overlapMs <= 3_000L)
        }
    }

    @Test fun `a confident key clash shortens even a compatible tempo pair`() {
        val out = rhythmic().copy(keyPitchClass = 0, keyMode = "major", keyConfidence = 0.9f)
        val incoming = rhythmic("in").copy(keyPitchClass = 1, keyMode = "major", keyConfidence = 0.9f)
        val result = plan(out, incoming)
        assertEquals(TransitionPlan.HarmonicMatch.CLASH, result.harmonicMatch)
        assertTrue(result.overlapMs <= 3_000L)
    }

    @Test fun `relative major and minor keys are compatible`() {
        val out = rhythmic().copy(keyPitchClass = 0, keyMode = "major", keyConfidence = 0.9f)
        val incoming = rhythmic("in").copy(keyPitchClass = 9, keyMode = "minor", keyConfidence = 0.9f)
        assertEquals(TransitionPlan.HarmonicMatch.COMPATIBLE, plan(out, incoming).harmonicMatch)
    }

    @Test fun `uncertain downbeat uses the beat grid without skipping a pickup`() {
        val out = rhythmic().copy(outroLeadMs = 8_000L, outroDownbeatConfidence = 0.9f)
        val incoming = rhythmic("in").copy(downbeatOffsetMs = 500L, downbeatConfidence = 0.9f)
        val bars = plan(out, incoming)
        val beats = plan(out, incoming.copy(downbeatConfidence = 0f))
        assertEquals(0L, bars.incomingStartMs)
        assertEquals(0L, beats.incomingStartMs)
        assertEquals(500L, bars.incomingDownbeatDelayMs)
        assertEquals(0L, beats.incomingDownbeatDelayMs)
        assertTrue(bars.overlapMs < beats.overlapMs)
    }

    @Test fun `silence skip uses a short unprocessed overlap`() {
        val result = plan(rhythmic().copy(trailingSilenceMs = 60_000L), rhythmic("in", 118f))
        assertEquals(TransitionPlan.Reason.SILENCE_SKIP, result.reason)
        assertEquals(60_000L, result.effectivePrepareLeadMs)
        assertEquals(1_000L, result.overlapMs)
        assertEquals(1f, result.incomingSpeed, 0f)
        assertEquals(0f, result.filterSweepStrength, 0f)
    }
}
