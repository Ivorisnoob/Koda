package com.ivor.ivormusic.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WaveformEnvelopeTest {

    @Test fun recordsPeaksIntoTheBucketHoldingTheirFraction() {
        val envelope = WaveformEnvelope.empty()
        assertTrue(envelope.record(0f, 0.5f))
        assertTrue(envelope.record(0.999f, 0.9f))
        val bars = envelope.bars(WaveformEnvelope.BUCKETS)
        // Two measurements at opposite ends, so the stretch puts one at each extreme.
        assertEquals(WaveformEnvelope.RESTING, bars.first(), 0.02f)
        assertEquals(1f, bars.last(), 0.02f)
    }

    /**
     * The regression the flat-bar report was about: a song whose quiet passages are already a
     * good fraction of its loud ones must still use the whole height of the bar.
     */
    @Test fun aLoudlyMasteredSongStillUsesTheFullHeight() {
        val envelope = WaveformEnvelope.empty()
        repeat(WaveformEnvelope.BUCKETS) { bucket ->
            val fraction = bucket.toFloat() / WaveformEnvelope.BUCKETS
            // A quiet half and a loud half only 1.36:1 apart - the ratio that normalising
            // against the loudest bucket would have drawn as bars between 73% and 100%.
            envelope.record(fraction, if (bucket < WaveformEnvelope.BUCKETS / 2) 0.55f else 0.75f)
        }
        val bars = envelope.bars(64)
        assertTrue(bars.toList().toString(), bars.min() <= 0.15f)
        assertTrue(bars.toList().toString(), bars.max() >= 0.95f)
    }

    @Test fun aLoudMomentStandsOutInsteadOfBeingDilutedByItsNeighbours() {
        val envelope = WaveformEnvelope.empty()
        repeat(WaveformEnvelope.BUCKETS) { envelope.record(it.toFloat() / WaveformEnvelope.BUCKETS, 0.2f) }
        // One loud moment inside what becomes a single drawn bar.
        envelope.record(0.5f, 1f)
        val bars = envelope.bars(32)
        assertEquals(1f, bars.max(), 0.001f)
        // ...and it must be the only bar that rises, not the whole song lifted with it.
        assertEquals(1, bars.count { it > 0.5f })
    }

    /** A song that really is level throughout has no dynamics to invent from noise. */
    @Test fun aUniformSongDrawsAsASolidBandRatherThanInventedDynamics() {
        val envelope = WaveformEnvelope.empty()
        repeat(WaveformEnvelope.BUCKETS) { envelope.record(it.toFloat() / WaveformEnvelope.BUCKETS, 0.6f) }
        val bars = envelope.bars(48)
        assertTrue(bars.toList().toString(), bars.all { it > 0.5f && it < 0.95f })
        assertEquals(bars.first(), bars.max(), 0.001f)
    }

    @Test fun aBucketKeepsItsLoudestVisitSoReplayingCannotErodeIt() {
        val envelope = WaveformEnvelope.empty()
        assertTrue(envelope.record(0.5f, 0.8f))
        // A quieter second pass over the same stretch must change nothing.
        assertEquals(false, envelope.record(0.5f, 0.2f))
        assertTrue(envelope.record(0.5f, 0.95f))
    }

    @Test fun refusesFractionsAndPeaksOutsideTheirRange() {
        val envelope = WaveformEnvelope.empty()
        for (bad in listOf(-0.01f, 1.01f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertEquals(bad.toString(), false, envelope.record(bad, 0.5f))
        }
        assertEquals(false, envelope.record(0.5f, Float.NaN))
        assertEquals(false, envelope.record(0.5f, -1f))
        assertEquals(0f, envelope.coverage, 0f)
    }

    @Test fun aMeasuredSilenceIsNotAnUnmeasuredBucket() {
        val envelope = WaveformEnvelope.empty()
        assertTrue(envelope.record(0.25f, 0f))
        assertEquals(1f / WaveformEnvelope.BUCKETS, envelope.coverage, 1e-6f)
    }

    @Test fun anUnmeasuredSongDrawsAsAFlatRestingBar() {
        val bars = WaveformEnvelope.empty().bars(64)
        assertEquals(64, bars.size)
        assertTrue(bars.all { it == WaveformEnvelope.RESTING })
    }

    @Test fun gapsAreInterpolatedSoAPartialListenStillReadsAsOneWaveform() {
        val envelope = WaveformEnvelope.empty()
        envelope.record(0f, 1f)
        envelope.record(1f, 1f)
        val bars = envelope.bars(16)
        // Nothing between the ends was measured, yet no bar may collapse to the resting floor.
        assertTrue(bars.toList().toString(), bars.all { it > WaveformEnvelope.RESTING })
        assertTrue(bars.all { it <= 1f })
    }

    @Test fun aQuietMasterStillReachesTheTopOfTheBar() {
        val quiet = WaveformEnvelope.empty()
        quiet.record(0.1f, 0.05f)
        quiet.record(0.9f, 0.025f)
        val bars = quiet.bars(WaveformEnvelope.BUCKETS)
        assertEquals(1f, bars.max(), 0.02f)
    }

    @Test fun barsAlwaysFillTheRequestedCountAndStayInRange() {
        val envelope = WaveformEnvelope.empty()
        repeat(50) { envelope.record(it / 50f, 0.4f + it / 200f) }
        for (count in listOf(1, 7, 64, 256, 400)) {
            val bars = envelope.bars(count)
            assertEquals(count, bars.size)
            assertTrue(bars.all { it in WaveformEnvelope.RESTING..1f })
        }
        assertEquals(0, envelope.bars(0).size)
    }

    @Test fun survivesAStorageRoundTripAndRejectsJunk() {
        val envelope = WaveformEnvelope.empty()
        repeat(20) { envelope.record(it / 20f, 0.3f + it / 100f) }
        val restored = WaveformEnvelope.decodeFromString(envelope.encodeToString())
        assertTrue(restored != null)
        assertEquals(envelope.coverage, restored!!.coverage, 0f)
        assertTrue(envelope.bars(64).contentEquals(restored.bars(64)))

        assertNull(WaveformEnvelope.decodeFromString(null))
        assertNull(WaveformEnvelope.decodeFromString(""))
        assertNull(WaveformEnvelope.decodeFromString("not base64 %%%"))
        // A stored envelope from a build with a different bucket count must be discarded,
        // never read as a short one.
        assertNull(WaveformEnvelope.decodeFromString(java.util.Base64.getEncoder().encodeToString(ByteArray(8))))
    }

    @Test fun encodingKeepsZeroReservedForUnmeasured() {
        assertNotEquals(0, WaveformEnvelope.encode(0f))
        assertEquals(1, WaveformEnvelope.encode(0f))
        assertEquals(255, WaveformEnvelope.encode(1f))
        assertEquals(255, WaveformEnvelope.encode(9f))
    }
}
