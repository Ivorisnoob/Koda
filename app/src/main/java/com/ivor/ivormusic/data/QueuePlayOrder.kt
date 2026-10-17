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

/**
 * Where queue index [index] ends up after the item at [from] is moved to [to].
 *
 * A play order is a list of queue indices, so editing the queue renumbers it:
 * the songs either side of the gap close up. Remapping the order through this
 * keeps it describing the same arrangement of the same songs - and the moved
 * song keeps the play position it had, which is what a queue move means when
 * the queue is not the order being heard.
 */
fun queueIndexAfterMove(index: Int, from: Int, to: Int): Int = when {
    index == from -> to
    from < to && index in (from + 1)..to -> index - 1
    to < from && index in to until from -> index + 1
    else -> index
}

/** [order] remapped for a queue move from [from] to [to]; see [queueIndexAfterMove]. */
fun IntArray.afterQueueMove(from: Int, to: Int): IntArray =
    IntArray(size) { queueIndexAfterMove(this[it], from, to) }

/** [order] with queue index [removed] taken out; every higher index closes up. */
fun IntArray.afterQueueRemoval(removed: Int): IntArray =
    filter { it != removed }
        .map { if (it > removed) it - 1 else it }
        .toIntArray()

/**
 * [order] with queue index [at] put back, taking the play position [playIndex]
 * it had before it was removed.
 *
 * Undo has to land the song back on the row the user saw it on, which under
 * shuffle is a play-order position and not a queue position.
 */
fun IntArray.afterQueueRestore(at: Int, playIndex: Int): IntArray {
    val out = map { if (it >= at) it + 1 else it }.toMutableList()
    out.add(playIndex.coerceIn(0, out.size), at)
    return out.toIntArray()
}

/**
 * The play order after [addedCount] songs are appended to the end of the
 * queue [order] describes, placed to play straight after the current song and
 * after any upcoming songs the listener queued themselves ([isUserQueued],
 * asked of queue indices).
 *
 * **Why "Add to queue" needs this with shuffle on.** An append lands at the
 * end of the play order - right for auto-queue recommendations, which must not
 * mix into the playlist being shuffled, and wrong for a song someone just
 * asked to hear, which then waited behind the whole shuffled playlist, often
 * hours away. Queued songs line up in the order they were added, like any
 * other player's queue.
 *
 * **"Play next" needs it only with the playing song as the last queue row.**
 * That insert reaches the player as an append as well, so the new song would
 * play last. Anywhere else `QueueShuffleOrder.cloneAndInsert` puts the new
 * rows straight after the song they were inserted behind, which is what "Play
 * next" means - and why it does not pass [isUserQueued]: next goes ahead of
 * anything queued earlier rather than behind it.
 *
 * Null when the current song is not in [order] or nothing was added; the
 * caller keeps the plain append.
 */
fun playOrderQueuingUpNext(
    order: IntArray,
    currentIndex: Int,
    addedCount: Int,
    isUserQueued: (queueIndex: Int) -> Boolean = { false },
): IntArray? {
    if (addedCount <= 0) return null
    val currentAt = order.indexOf(currentIndex)
    if (currentAt < 0) return null
    var insertAt = currentAt + 1
    while (insertAt < order.size && isUserQueued(order[insertAt])) insertAt++
    val size = order.size
    return IntArray(size + addedCount) { position ->
        when {
            position < insertAt -> order[position]
            position < insertAt + addedCount -> size + (position - insertAt)
            else -> order[position - addedCount]
        }
    }
}
