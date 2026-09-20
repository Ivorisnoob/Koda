package com.ivor.ivormusic.data

import org.junit.Assert.assertEquals
import org.junit.Test

class QueuePlayOrderTest {
    private fun song(id: String) = Song(id, "Track $id", "Artist", "Album", 1000,
        source = SongSource.YOUTUBE)

    private fun queue(vararg ids: String) = ids.map { MusicQueueItem(id = "occ_$it", song = song(it)) }

    @Test fun anOrderRearrangesTheQueue() {
        val q = queue("a", "b", "c", "d")
        assertEquals(
            listOf("c", "a", "d", "b"),
            q.arrangedBy(intArrayOf(2, 0, 3, 1)).map { it.song.id }
        )
    }

    @Test fun theIdentityOrderChangesNothing() {
        val q = queue("a", "b", "c")
        assertEquals(q, q.arrangedBy(intArrayOf(0, 1, 2)))
    }

    @Test fun anOrderForADifferentQueueIsRefused() {
        // The queue is edited here first and the service publishes a moment
        // later, so a stale order describing a queue that no longer exists is
        // an ordinary state, not a corruption. Drawing it would place songs in
        // positions nothing is going to play them in.
        val q = queue("a", "b", "c")
        assertEquals(q, q.arrangedBy(intArrayOf(0, 1)))
        assertEquals(q, q.arrangedBy(intArrayOf(0, 1, 2, 3)))
        assertEquals(q, q.arrangedBy(IntArray(0)))
    }

    @Test fun anOrderThatRepeatsOrEscapesTheQueueIsRefused() {
        val q = queue("a", "b", "c")
        assertEquals(q, q.arrangedBy(intArrayOf(0, 0, 2)))
        assertEquals(q, q.arrangedBy(intArrayOf(0, 1, 7)))
        assertEquals(q, q.arrangedBy(intArrayOf(-1, 1, 2)))
    }

    @Test fun aDraggedPositionTranslatesBackToItsQueuePosition() {
        // Row 1 of a shuffled screen is queue position 0; row 0 is position 2.
        val order = intArrayOf(2, 0, 3, 1)
        assertEquals(2, queueIndexForPlayOrder(order, 4, 0))
        assertEquals(0, queueIndexForPlayOrder(order, 4, 1))
        assertEquals(1, queueIndexForPlayOrder(order, 4, 3))
    }

    @Test fun aDraggedPositionIsItselfWhenThereIsNoUsableOrder() {
        assertEquals(3, queueIndexForPlayOrder(IntArray(0), 4, 3))
        assertEquals(3, queueIndexForPlayOrder(intArrayOf(1, 0), 4, 3))
        // Out of range rather than crashing the drag: the queue moved under it.
        assertEquals(9, queueIndexForPlayOrder(intArrayOf(2, 0, 3, 1), 4, 9))
    }
}
