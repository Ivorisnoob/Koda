package com.ivor.ivormusic.service.core

/**
 * When a play enters the listening history, and how long it was heard.
 *
 * Fed a sample of the audible player every few hundred milliseconds (see
 * `ListeningRecorder`), it counts wall time while playing - never seek
 * position, never buffering - per queue occurrence, so a song that appears
 * twice in a queue is two plays and a song repeated with repeat-one is a new
 * one each time round ([restart]).
 *
 * A play is recorded once it has been heard for [QUALIFY_MS], the rule the
 * history has always used: skips and a cold-start restore that never played
 * leave nothing behind. From then on its measured time is brought up to date
 * every [AMEND_EVERY_MS] of further listening, when playback stops, and when
 * the occurrence changes - so a process killed mid-song loses at most that
 * much of one song's time, and never the play itself.
 *
 * Pure: the caller owns the clock, the player and the writes.
 */
class ListeningLedger {

    sealed interface Action {
        /** Write a new play for [occurrence], heard [listenedMs] so far. */
        data class Record(val occurrence: String, val listenedMs: Long) : Action

        /** Bring the recorded play of [occurrence] up to [listenedMs]. */
        data class Amend(val occurrence: String, val listenedMs: Long) : Action
    }

    private var occurrence: String? = null
    private var lastTick = 0L
    private var wasPlaying = false
    private var listened = 0L
    private var recorded = false
    private var amendedAt = 0L

    /** The occurrence being counted and the time heard so far. */
    val current: Pair<String, Long>? get() = occurrence?.let { it to listened }

    /**
     * One sample. [occurrence] is the queue occurrence playing now, or null
     * when nothing is. Returns what to write, in order: an amendment closing
     * the previous occurrence comes before anything about the new one.
     */
    fun sample(occurrence: String?, playing: Boolean, nowMs: Long): List<Action> {
        val actions = ArrayList<Action>(2)
        if (occurrence != this.occurrence) {
            close()?.let(actions::add)
            this.occurrence = occurrence
            listened = 0
            recorded = false
            amendedAt = 0
            wasPlaying = false
            lastTick = nowMs
        }
        if (occurrence == null) return actions
        // A gap longer than a couple of samples is the process being frozen,
        // not listening; it is capped so a suspended phone adds nothing.
        if (playing && wasPlaying) listened += (nowMs - lastTick).coerceIn(0, MAX_TICK_MS)
        val stopped = wasPlaying && !playing
        lastTick = nowMs
        wasPlaying = playing
        when {
            !recorded && listened >= QUALIFY_MS -> {
                recorded = true
                amendedAt = listened
                actions += Action.Record(occurrence, listened)
            }
            recorded && (stopped || listened - amendedAt >= AMEND_EVERY_MS) && listened != amendedAt -> {
                amendedAt = listened
                actions += Action.Amend(occurrence, listened)
            }
        }
        return actions
    }

    /**
     * The same occurrence starts over (repeat-one): close the play that was
     * counting, so the next pass is a play of its own.
     */
    fun restart(): Action? {
        val closing = close()
        occurrence = null
        listened = 0
        recorded = false
        amendedAt = 0
        wasPlaying = false
        return closing
    }

    /** The last word on the occurrence being counted, if it was recorded and moved on. */
    fun close(): Action? {
        val id = occurrence ?: return null
        if (!recorded || listened == amendedAt) return null
        amendedAt = listened
        return Action.Amend(id, listened)
    }

    companion object {
        /** Heard for this long before it counts as a play. */
        const val QUALIFY_MS = 15_000L

        /** How much further listening goes unwritten at most. */
        const val AMEND_EVERY_MS = 30_000L

        /** The most one sample may add; samples arrive every 250ms. */
        const val MAX_TICK_MS = 1_500L
    }
}
