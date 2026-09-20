package com.ivor.ivormusic.data

/**
 * The queue rearranged into the order it will actually be heard.
 *
 * **Media3 keeps the shuffle order to itself.** The player holds a
 * `ShuffleOrder` alongside its timeline and walks it to decide what comes
 * next; nothing about that reaches a `MediaController`, whose timeline is the
 * plain base class - its shuffle-aware walk is `index + 1` and the
 * shuffleModeEnabled argument is ignored [verified September 2026 against the
 * media3-common 1.11.0 bytecode]. So a queue screen asking the controller what
 * comes next gets the order the songs were added in, which is how every queue
 * surface in this app came to draw one order while the player played another.
 *
 * [order] is the answer the service read off its own player. With shuffle off
 * it is 0..n and this returns the queue unchanged.
 *
 * **An order that does not address the queue exactly once is refused**, and
 * the queue is returned as it is. That happens legitimately: the queue is
 * edited here first and the service publishes a moment later, so between the
 * two there is an order describing a queue that no longer exists. Drawing it
 * anyway would put songs in positions nothing is going to play them in - and
 * a subtly wrong order looks right, while an unshuffled one is visibly
 * unshuffled.
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

/**
 * Where [playOrderIndex] sits in the queue itself.
 *
 * Queue screens draw the play order, so every position they hand back - the
 * row being dragged, the row it was dropped on - is a position in that order
 * and means nothing to a player addressing its timeline.
 */
fun queueIndexForPlayOrder(order: IntArray, size: Int, playOrderIndex: Int): Int {
    if (order.size != size) return playOrderIndex
    return order.getOrElse(playOrderIndex) { playOrderIndex }
}
