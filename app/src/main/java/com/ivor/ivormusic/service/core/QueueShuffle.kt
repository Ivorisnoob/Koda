package com.ivor.ivormusic.service.core

import java.util.Random

/**
 * Shuffle as a mode over a queue whose timeline is always the order it plays.
 *
 * The player walks its timeline in order; shuffling rearranges the timeline
 * itself, so every controller, queue screen, widget and car sees what will
 * actually be heard, without a permutation to carry across a session boundary.
 * What this remembers is the order shuffling replaced, by queue occurrence,
 * so turning it off puts that order back with the current song still current.
 *
 * Pure: it plans orders of occurrence ids, and the service applies them.
 */
class QueueShuffle {
    /** The order before shuffle, by occurrence; null while unshuffled. */
    private var unshuffled: MutableList<String>? = null

    /** The timeline last seen, to tell a new queue from an edit of this one. */
    private var known: List<String> = emptyList()

    val isOn: Boolean get() = unshuffled != null

    /** The order shuffling replaced, or null while unshuffled. */
    fun unshuffledOrder(): List<String>? = unshuffled?.toList()

    /**
     * Shuffle [timeline] with the song at [currentIndex] first and every other
     * song after it, those not heard lately ([isRecent]) ahead of those that
     * were. Returns the timeline to play; a no-op if already on.
     */
    fun turnOn(
        timeline: List<String>,
        currentIndex: Int,
        isRecent: (String) -> Boolean,
        random: Random,
    ): List<String> {
        if (isOn) return timeline
        unshuffled = timeline.toMutableList()
        known = timeline
        return shuffled(timeline, currentIndex, isRecent, random).also { known = it }
    }

    /**
     * The order shuffling replaced, restored. Songs added while shuffled sit
     * where [follow] placed them in it; anything it never saw goes last.
     */
    fun turnOff(timeline: List<String>): List<String> {
        val order = unshuffled ?: return timeline
        unshuffled = null
        val present = timeline.toHashSet()
        val restored = order.filter { it in present }.toMutableList()
        val placed = restored.toHashSet()
        timeline.filterTo(restored) { it !in placed }
        known = restored
        return restored
    }

    /**
     * A timeline that arrived whole, from a saved session: [order] is the order
     * it had before it was shuffled, or null when it was not.
     */
    fun adopt(timeline: List<String>, order: List<String>?) {
        unshuffled = order?.toMutableList()
        known = timeline
    }

    /**
     * Keep the remembered order in step with an edited [timeline]: removed songs
     * leave it, and added ones join it - at the end when they were added at the
     * end, otherwise right after the song before them, which is where "Play
     * next" and a queued song belong once shuffle is off.
     *
     * Returns true when [timeline] shares no song with the one before it, an
     * empty one included: a new queue rather than an edit, which the caller
     * shuffles afresh when shuffle is on.
     */
    fun follow(timeline: List<String>): Boolean {
        val previous = known.toHashSet()
        known = timeline
        val isNewQueue = timeline.isNotEmpty() && timeline.none { it in previous }
        val order = unshuffled ?: return isNewQueue
        if (isNewQueue) return true

        val present = timeline.toHashSet()
        order.retainAll(present)
        val remembered = order.toHashSet()
        var index = 0
        while (index < timeline.size) {
            if (timeline[index] in remembered) {
                index++
                continue
            }
            val blockStart = index
            while (index < timeline.size && timeline[index] !in remembered) index++
            val block = timeline.subList(blockStart, index)
            val insertAt = when {
                index == timeline.size -> order.size
                blockStart == 0 -> 0
                else -> order.indexOf(timeline[blockStart - 1]) + 1
            }
            order.addAll(insertAt, block)
            remembered.addAll(block)
        }
        return false
    }

    /** A new queue arrived while shuffled: remember it as given, and shuffle it. */
    fun reshuffle(
        timeline: List<String>,
        currentIndex: Int,
        isRecent: (String) -> Boolean,
        random: Random,
    ): List<String> {
        unshuffled = timeline.toMutableList()
        return shuffled(timeline, currentIndex, isRecent, random).also { known = it }
    }

    private fun shuffled(
        timeline: List<String>,
        currentIndex: Int,
        isRecent: (String) -> Boolean,
        random: Random,
    ): List<String> {
        val opening = timeline.getOrNull(currentIndex)
        val fresh = ArrayList<String>(timeline.size)
        val recent = ArrayList<String>()
        timeline.forEachIndexed { index, id ->
            if (index == currentIndex) return@forEachIndexed
            if (isRecent(id)) recent += id else fresh += id
        }
        // Each press of Shuffle used to be an independent draw, so the songs
        // from yesterday's shuffle of the same playlist came round again just
        // as often as the ones never reached. Shuffling the two sides apart,
        // not-recent first, rotates through a playlist instead of resampling it.
        java.util.Collections.shuffle(fresh, random)
        java.util.Collections.shuffle(recent, random)
        return listOfNotNull(opening) + fresh + recent
    }
}
