package com.ivor.ivormusic.service.core

import com.ivor.ivormusic.service.core.ListeningLedger.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListeningLedgerTest {

    /** Samples every 250ms from [from] to [to] (ms, exclusive), collecting what the ledger asks for. */
    private fun ListeningLedger.run(occurrence: String?, playing: Boolean, from: Long, to: Long): List<Action> {
        val out = mutableListOf<Action>()
        var t = from
        while (t < to) {
            out += sample(occurrence, playing, t)
            t += 250
        }
        return out
    }

    @Test
    fun aSkipBeforeFifteenSecondsLeavesNothing() {
        val ledger = ListeningLedger()
        val actions = ledger.run("a", true, 0, 14_000) + ledger.sample("b", true, 14_000)
        assertTrue(actions.isEmpty())
    }

    @Test
    fun recordsOnceAtFifteenSecondsOfListening() {
        val ledger = ListeningLedger()
        val actions = ledger.run("a", true, 0, 20_000)
        val records = actions.filterIsInstance<Action.Record>()
        assertEquals(1, records.size)
        assertEquals("a", records[0].occurrence)
        assertTrue(records[0].listenedMs >= ListeningLedger.QUALIFY_MS)
    }

    @Test
    fun pausedTimeAndBufferingAreNotListening() {
        val ledger = ListeningLedger()
        ledger.run("a", true, 0, 10_000)
        ledger.run("a", false, 10_000, 60_000)
        val actions = ledger.run("a", true, 60_000, 64_000)
        assertTrue(actions.none { it is Action.Record })
        assertEquals("a", ledger.current?.first)
        assertTrue(ledger.current!!.second < 15_000)
    }

    @Test
    fun aFrozenProcessAddsAtMostOneCappedTick() {
        val ledger = ListeningLedger()
        ledger.sample("a", true, 0)
        ledger.sample("a", true, 3_600_000)
        assertEquals(ListeningLedger.MAX_TICK_MS, ledger.current!!.second)
    }

    @Test
    fun movingOnAmendsThePlayWithTheTimeHeard() {
        val ledger = ListeningLedger()
        ledger.run("a", true, 0, 40_000)
        val actions = ledger.sample("b", true, 40_000)
        val amend = actions.single() as Action.Amend
        assertEquals("a", amend.occurrence)
        assertTrue(amend.listenedMs in 39_000..40_000)
    }

    @Test
    fun longListeningIsAmendedAsItGoes() {
        val ledger = ListeningLedger()
        val actions = ledger.run("a", true, 0, 120_000)
        assertEquals(1, actions.count { it is Action.Record })
        assertTrue(actions.count { it is Action.Amend } >= 3)
    }

    @Test
    fun pausingWritesDownWhereItGot() {
        val ledger = ListeningLedger()
        ledger.run("a", true, 0, 20_000)
        val actions = ledger.sample("a", false, 20_000)
        assertTrue(actions.single() is Action.Amend)
    }

    @Test
    fun repeatOneIsANewPlayEachTimeRound() {
        val ledger = ListeningLedger()
        ledger.run("a", true, 0, 20_000)
        assertTrue(ledger.restart() is Action.Amend)
        val second = ledger.run("a", true, 20_000, 40_000)
        assertEquals(1, second.count { it is Action.Record })
    }

    @Test
    fun anOccurrenceNeverRecordedClosesQuietly() {
        val ledger = ListeningLedger()
        ledger.run("a", true, 0, 5_000)
        assertNull(ledger.close())
    }
}
