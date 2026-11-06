package com.ivor.ivormusic.data

/**
 * When automatic work that failed may try again.
 *
 * [scar] Three loops found in October 2026 were the same mistake made three
 * times: something automatic failed and nothing remembered it, so the next
 * tick asked again. The watch-history reporter re-sent its `/player` every 30
 * seconds and on every pause and seek; the inline preview re-resolved a video
 * that had just failed 150ms after it failed; Home refetched an empty feed on
 * every return to the screen. Each now asks one of these first.
 *
 * After the n-th failure in a row the next attempt waits
 * `initialDelayMs * 2^(n-1)`, capped at [maxDelayMs], and after [maxFailures]
 * it stops until [succeeded] or [reset]. Times come from a monotonic clock
 * (`SystemClock.elapsedRealtime()`), so a wall-clock change cannot pin or
 * release it. Something the user does - a pull to refresh, Retry - goes around
 * it: a backoff decides only when the app may ask again by itself.
 */
internal class RetryBackoff(
    private val initialDelayMs: Long,
    private val maxDelayMs: Long = initialDelayMs,
    private val maxFailures: Int = Int.MAX_VALUE,
) {
    /** Failures in a row since the last success. */
    var failures = 0
        private set
    private var retryAtMs = 0L

    /** Whether an automatic attempt may go ahead at [nowMs]. */
    @Synchronized
    fun allows(nowMs: Long): Boolean = failures < maxFailures && nowMs >= retryAtMs

    /** The attempt made at [nowMs] failed. */
    @Synchronized
    fun failed(nowMs: Long) {
        failures++
        val doublings = (failures - 1).coerceAtMost(MAX_DOUBLINGS)
        retryAtMs = nowMs + (initialDelayMs shl doublings).coerceAtMost(maxDelayMs)
    }

    /** It worked: the next failure starts from the first delay again. */
    @Synchronized
    fun succeeded() {
        failures = 0
        retryAtMs = 0L
    }

    /** Forget every failure, for a new subject or an explicit user retry. */
    fun reset() = succeeded()

    private companion object {
        /** Far past any cap in use, and short of overflowing a Long. */
        const val MAX_DOUBLINGS = 16
    }
}

/**
 * A [RetryBackoff] per key, for work that fails one item at a time - one
 * video's preview, say - without holding back the others. Bounded: the least
 * recently touched key is forgotten first, which only means it may be tried
 * again.
 */
internal class RetryBackoffs(
    private val capacity: Int,
    private val newBackoff: () -> RetryBackoff,
) {
    private val entries = object : LinkedHashMap<String, RetryBackoff>(capacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, RetryBackoff>) =
            size > capacity
    }

    @Synchronized
    fun allows(key: String, nowMs: Long): Boolean = entries[key]?.allows(nowMs) ?: true

    @Synchronized
    fun failed(key: String, nowMs: Long) = entries.getOrPut(key, newBackoff).failed(nowMs)

    @Synchronized
    fun succeeded(key: String) {
        entries.remove(key)
    }
}
