package com.ivor.ivormusic.data.stream

/**
 * How many times the visitorData may be replaced within a window.
 *
 * [scar] Every surface that sees a refusal replaces the token, and nothing
 * counted how often that happened: the video player's recovery could loop on a
 * stream refused after its opening megabyte, minting a new visitor each round
 * for as long as the player stayed open. A brand-new anonymous visitor every
 * few seconds from one address is exactly what the bot check is built to
 * catch, so replacing more often than a real refusal streak needs makes the
 * refusals worse, not better. Past the budget the current token is kept and
 * the caller's replacement fails, which every caller already handles; a
 * request the user makes still goes out on the token held.
 *
 * Pure and clock-injected so it can be tested; [VisitorIdentity] holds the
 * process-wide instance.
 */
internal class ReplacementBudget(
    private val max: Int,
    private val windowMs: Long,
) {
    private val times = ArrayDeque<Long>()

    /** Whether one more replacement fits in the window ending at [nowMs]. */
    @Synchronized
    fun allows(nowMs: Long): Boolean {
        prune(nowMs)
        return times.size < max
    }

    /** A replacement happened at [nowMs]. */
    @Synchronized
    fun record(nowMs: Long) {
        prune(nowMs)
        times.addLast(nowMs)
    }

    /** A new network: the verdicts the old address earned do not apply to it. */
    @Synchronized
    fun reset() = times.clear()

    // A negative age is a clock moved backwards; that entry is forgotten
    // rather than allowed to pin the budget shut.
    private fun prune(nowMs: Long) {
        while (times.isNotEmpty() && nowMs - times.first() !in 0 until windowMs) {
            times.removeFirst()
        }
    }
}
