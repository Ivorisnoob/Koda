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

    @Test fun queuedSongsPlayRightAfterTheCurrentSong() {
        // Queue of 5, shuffled 3 -> 0 -> 4 -> 1 -> 2, playing index 0.
        val order = intArrayOf(3, 0, 4, 1, 2)
        assertEquals(
            listOf(3, 0, 5, 4, 1, 2),
            playOrderQueuingUpNext(order, currentIndex = 0, addedCount = 1)?.toList()
        )
    }

    @Test fun laterQueuedSongsLineUpBehindEarlierOnes() {
        // Index 5 was queued earlier and sits right after the current song.
        val order = intArrayOf(3, 0, 5, 4, 1, 2)
        assertEquals(
            listOf(3, 0, 5, 6, 7, 4, 1, 2),
            playOrderQueuingUpNext(order, currentIndex = 0, addedCount = 2) { it == 5 }?.toList()
        )
    }

    @Test fun queuingAfterTheLastSongAppends() {
        val order = intArrayOf(2, 0, 1)
        assertEquals(
            listOf(2, 0, 1, 3),
            playOrderQueuingUpNext(order, currentIndex = 1, addedCount = 1)?.toList()
        )
    }

    @Test fun playingNextFromTheLastQueueRowStillPlaysNext() {
        // The playing song is the last queue row, so its insert reaches the
        // player as an append, which the shuffle order plays last. It has to
        // land just after the current song instead - play row 3, not row 5.
        // Anywhere else in the queue the shuffle order already gets this right,
        // which is why a mid-queue "Play next" is left alone.
        val order = intArrayOf(3, 0, 4, 1, 2)
        assertEquals(
            listOf(3, 0, 4, 5, 1, 2),
            playOrderQueuingUpNext(order, currentIndex = 4, addedCount = 1)?.toList()
        )
    }

    @Test fun queuingWithTheCurrentSongOutsideTheOrderIsRefused() {
        assertEquals(null, playOrderQueuingUpNext(intArrayOf(1, 0), currentIndex = 5, addedCount = 1))
        assertEquals(null, playOrderQueuingUpNext(intArrayOf(1, 0), currentIndex = 0, addedCount = 0))
    }

    @Test fun aQueueMoveRenumbersTheOrderButNotTheArrangement() {
        // Play order d,a,e,b,c over a queue a..e. Moving a from queue row 0 to
        // row 3 lifts b, c and d down one; the order still names the same songs
        // in the same sequence, so the moved song keeps its play row.
        val order = intArrayOf(3, 0, 4, 1, 2)
        val moved = order.afterQueueMove(from = 0, to = 3)
        assertEquals(listOf(2, 3, 4, 0, 1), moved.toList())
        assertEquals(order.toList(), moved.afterQueueMove(from = 3, to = 0).toList())
    }

    @Test fun removingASongClosesTheOrderBehindIt() {
        val order = intArrayOf(3, 0, 4, 1, 2)
        assertEquals(listOf(2, 3, 0, 1), order.afterQueueRemoval(0).toList())
        assertEquals(listOf(3, 0, 1, 2), order.afterQueueRemoval(4).toList())
        // An index the order never named - a stale UI position - changes nothing.
        assertEquals(order.toList(), order.afterQueueRemoval(9).toList())
    }

    @Test fun undoPutsTheSongBackOnItsPlayRow() {
        // d,a,e,b,c; undoing the removal of b has to restore its queue row and
        // its play row. Appending, or inserting at the recorded queue index
        // without renumbering, would drop it somewhere else.
        val order = intArrayOf(3, 0, 4, 1, 2)
        val afterRemoval = order.afterQueueRemoval(1)
        assertEquals(listOf(2, 0, 3, 1), afterRemoval.toList())
        assertEquals(order.toList(), afterRemoval.afterQueueRestore(at = 1, playIndex = 3).toList())
    }
}
