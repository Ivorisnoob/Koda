package com.ivor.ivormusic.data

import com.ivor.ivormusic.service.TransitionPlan
import com.ivor.ivormusic.service.TransitionPlanner
import org.junit.Assert.assertEquals
import org.junit.Test

class TrailingSilenceProbeTest {
    @Test
    fun `quiet final half second does not classify the preceding music as silence`() {
        val tail = FloatArray(1000) { if (it >= 975) 0f else 0.4f }
        assertEquals(500L, measuredTailSilenceMs(tail))
    }

    @Test
    fun `a fully silent tail may be extended but an unknown sample breaks it`() {
        assertEquals(20_000L, measuredTailSilenceMs(FloatArray(1000)))
        assertEquals(0L, measuredTailSilenceMs(floatArrayOf(0f, Float.NaN)))
    }

    @Test
    fun `failed first probe cannot cut a ninety second track at thirty seconds`() {
        val silenceMs = probeTrailingSilenceMs(90_000L) { null }
        assertEquals(0L, silenceMs)
        val plan = TransitionPlanner.plan(
            fallbackOverlapMs = 3_000L,
            maximumOverlapMs = 15_000L,
            outgoing = AudioProfile(songId = "song", trailingSilenceMs = silenceMs),
            incoming = null,
            outgoingDurationMs = 90_000L,
        )
        assertEquals(TransitionPlan.Reason.FALLBACK, plan.reason)
        assertEquals(3_000L, plan.effectivePrepareLeadMs)
    }

    @Test
    fun `failure after silent samples still declines the silence skip`() {
        var probes = 0
        assertEquals(0L, probeTrailingSilenceMs(90_000L) {
            if (++probes < 3) true else null
        })
    }

    @Test
    fun `audible boundary retains the measured tail rather than the maximum`() {
        assertEquals(20_000L, probeTrailingSilenceMs(90_000L) { false })
    }

    @Test
    fun `successful silent probes retain the one minute cap`() {
        var probes = 0
        assertEquals(60_000L, probeTrailingSilenceMs(180_000L) { probes++; true })
        assertEquals(9, probes)
    }

    @Test
    fun `short recordings stop probing once the start is reached`() {
        val positions = mutableListOf<Long>()
        assertEquals(12_000L, probeTrailingSilenceMs(12_000L) {
            positions += it
            true
        })
        assertEquals(listOf(0L), positions)
    }

    @Test
    fun `unknown duration cannot authorize a silence skip`() {
        assertEquals(0L, probeTrailingSilenceMs(0L) { error("Must not probe") })
    }
}
