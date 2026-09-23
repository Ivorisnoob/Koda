package com.ivor.ivormusic.service

import androidx.media3.common.C
import androidx.media3.exoplayer.source.ShuffleOrder
import java.util.Random
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueShuffleOrderTest {

    private fun ShuffleOrder.walk(): IntArray {
        val out = ArrayList<Int>()
        var index = firstIndex
        while (index != C.INDEX_UNSET) {
            out += index
            index = getNextIndex(index)
        }
        return out.toIntArray()
    }

    private fun assertPermutation(order: IntArray, size: Int) {
        assertEquals((0 until size).toList(), order.sorted())
    }

    @Test
    fun `a new queue opens with its start song for every start`() {
        for (start in 0 until 20) {
            val order = QueueShuffleOrder.startingAt(0, C.INDEX_UNSET, Random(start.toLong()))
                .cloneAndSet(20, start)
                .walk()
            assertEquals(start, order[0])
            assertPermutation(order, 20)
        }
    }

    @Test
    fun `turning shuffle on mid-queue puts the current song first`() {
        val order = QueueShuffleOrder.startingAt(12, 9, Random(4)).walk()
        assertEquals(9, order[0])
        assertPermutation(order, 12)
    }

    @Test
    fun `appended songs play after everything already queued`() {
        val before = QueueShuffleOrder.startingAt(10, 3, Random(1))
        val after = before.cloneAndInsert(10, 5).walk()
        assertArrayEquals(before.walk(), after.copyOfRange(0, 10))
        assertEquals((10 until 15).toSet(), after.copyOfRange(10, 15).toSet())
    }

    @Test
    fun `an insertion behind a song plays right after it`() {
        val before = QueueShuffleOrder.of(intArrayOf(3, 0, 4, 1, 2), Random(1))
        // Two songs inserted behind queue index 0 ("Play next" while 0 plays).
        val after = before.cloneAndInsert(1, 2).walk()
        assertArrayEquals(intArrayOf(5, 0, 1, 2, 6, 3, 4), after)
    }

    @Test
    fun `removal keeps the remaining order`() {
        val order = QueueShuffleOrder.of(intArrayOf(3, 0, 4, 1, 2), Random(1))
            .cloneAndRemove(1, 3)
            .walk()
        assertArrayEquals(intArrayOf(1, 0, 2), order)
    }

    @Test
    fun `a moved song keeps its place in the playing order`() {
        // Queue [a b c d e], played as d a e b c. Move a (0) to the end.
        val order = QueueShuffleOrder.of(intArrayOf(3, 0, 4, 1, 2), Random(1))
            .cloneAndMove(0, 1, 4)
            .walk()
        // Queue is now [b c d e a]: still d a e b c.
        assertArrayEquals(intArrayOf(2, 4, 3, 0, 1), order)
    }

    @Test
    fun `copying a foreign order keeps its sequence`() {
        val source = ShuffleOrder.DefaultShuffleOrder(9, 7L)
        val copied = QueueShuffleOrder.copyOf(source, Random(1))
        assertArrayEquals(source.walk(), copied.walk())
        assertTrue(copied.cloneAndClear().length == 0)
    }
}
