package com.ivor.ivormusic.data

/**
 * The queue rearranged into the order it will be heard.
 *
 * A saved session keeps its queue in the order it had before it was shuffled,
 * and [order] as the queue indices in the order they play, so restoring a
 * shuffled session lays the rows out by this.
 *
 * **An order that does not address the queue exactly once is refused**, and
 * the queue is returned as it is. A subtly wrong order looks right, while an
 * unshuffled one is visibly unshuffled.
 */
fun List<MusicQueueItem>.arrangedBy(order: IntArray): List<MusicQueueItem> {
    if (size < 2 || order.size != size) return this

    val arranged = ArrayList<MusicQueueItem>(size)
    val seen = HashSet<Int>(size)
    for (index in order) {
        if (index !in indices || !seen.add(index)) return this
        arranged.add(this[index])
    }
    return arranged
}
