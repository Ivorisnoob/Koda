package com.ivor.ivormusic.service

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.ShuffleOrder
import java.util.Random

/**
 * Koda's shuffle permutation.
 *
 * Media3's `DefaultShuffleOrder` puts a new queue's start song, and every later
 * insertion, at a random position. Songs that land before the one playing are
 * never reached, and auto-queue recommendations get mixed into the playlist.
 * Here a new queue opens with its start song, appended songs play after
 * everything already queued, and a mid-queue insertion ("Play next") plays
 * straight after the song it was inserted behind.
 *
 * Immutable: Media3 reads it from the playback thread.
 */
@UnstableApi
internal class QueueShuffleOrder private constructor(
    private val shuffled: IntArray,
    private val random: Random,
) : ShuffleOrder {

    private val positionOf = IntArray(shuffled.size).also { positions ->
        shuffled.forEachIndexed { position, index -> positions[index] = position }
    }

    /** The permutation, queue indices in playing order. */
    fun toArray(): IntArray = shuffled.copyOf()

    override fun getLength(): Int = shuffled.size

    override fun getNextIndex(index: Int): Int =
        shuffled.getOrNull(positionOf[index] + 1) ?: C.INDEX_UNSET

    override fun getPreviousIndex(index: Int): Int =
        shuffled.getOrNull(positionOf[index] - 1) ?: C.INDEX_UNSET

    override fun getLastIndex(): Int = shuffled.lastOrNull() ?: C.INDEX_UNSET

    override fun getFirstIndex(): Int = shuffled.firstOrNull() ?: C.INDEX_UNSET

    /** A replaced queue: `setMediaItems` passes the index playback starts at. */
    override fun cloneAndSet(insertionCount: Int, startIndex: Int): ShuffleOrder =
        startingAt(insertionCount, startIndex, random)

    override fun cloneAndInsert(insertionIndex: Int, insertionCount: Int): ShuffleOrder {
        if (shuffled.isEmpty()) return startingAt(insertionCount, C.INDEX_UNSET, random)
        val block = IntArray(insertionCount) { insertionIndex + it }
        val shifted = shuffled.map { if (it >= insertionIndex) it + insertionCount else it }
        val result = ArrayList<Int>(shuffled.size + insertionCount)
        if (insertionIndex >= shuffled.size) {
            result.addAll(shifted)
            result.addAll(block.also { shuffleInPlace(it, random) }.toList())
        } else {
            val at = if (insertionIndex > 0) positionOf[insertionIndex - 1] + 1 else positionOf[0]
            result.addAll(shifted.subList(0, at))
            result.addAll(block.toList())
            result.addAll(shifted.subList(at, shifted.size))
        }
        return QueueShuffleOrder(result.toIntArray(), random)
    }

    override fun cloneAndRemove(indexFrom: Int, indexToExclusive: Int): ShuffleOrder {
        val removed = indexToExclusive - indexFrom
        val kept = shuffled
            .filter { it < indexFrom || it >= indexToExclusive }
            .map { if (it >= indexToExclusive) it - removed else it }
        return QueueShuffleOrder(kept.toIntArray(), random)
    }

    /** Each song keeps its place in the playing order; only its queue index moves. */
    override fun cloneAndMove(indexFrom: Int, indexToExclusive: Int, newIndexFrom: Int): ShuffleOrder {
        val queue = MutableList(shuffled.size) { it }
        val moved = queue.subList(indexFrom, indexToExclusive).toList()
        queue.subList(indexFrom, indexToExclusive).clear()
        queue.addAll(newIndexFrom.coerceIn(0, queue.size), moved)
        val newIndexOf = IntArray(queue.size).also { map -> queue.forEachIndexed { at, old -> map[old] = at } }
        return QueueShuffleOrder(IntArray(shuffled.size) { newIndexOf[shuffled[it]] }, random)
    }

    override fun cloneAndClear(): ShuffleOrder = QueueShuffleOrder(IntArray(0), random)

    companion object {
        /** A fresh shuffle of [length] items that opens with [startIndex] when it is in range. */
        fun startingAt(length: Int, startIndex: Int, random: Random): QueueShuffleOrder {
            val order = IntArray(length) { it }.also { shuffleInPlace(it, random) }
            if (startIndex in 0 until length) {
                val at = order.indexOf(startIndex)
                System.arraycopy(order, 0, order, 1, at)
                order[0] = startIndex
            }
            return QueueShuffleOrder(order, random)
        }

        /** [order] as given; the caller has checked it addresses every index once. */
        fun of(order: IntArray, random: Random): QueueShuffleOrder =
            QueueShuffleOrder(order.copyOf(), random)

        /** Any [ShuffleOrder] walked into this type, so later edits follow these rules. */
        fun copyOf(order: ShuffleOrder, random: Random): QueueShuffleOrder {
            if (order is QueueShuffleOrder) return QueueShuffleOrder(order.shuffled, random)
            val walked = IntArray(order.length)
            var index = order.firstIndex
            for (position in walked.indices) {
                walked[position] = index
                index = order.getNextIndex(index)
            }
            return QueueShuffleOrder(walked, random)
        }

        private fun shuffleInPlace(values: IntArray, random: Random) {
            for (i in values.size - 1 downTo 1) {
                val j = random.nextInt(i + 1)
                val swap = values[i]
                values[i] = values[j]
                values[j] = swap
            }
        }
    }
}
