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
        // A session saved around a trimmed window, or by an older version,
        // can carry an order that no longer describes its queue. Drawing it
        // would place songs in positions nothing is going to play them in.
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
}
