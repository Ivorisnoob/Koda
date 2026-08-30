package com.ivor.ivormusic.service

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel

/**
 * Deduplicates in-flight work by key without running user code inside a
 * [ConcurrentHashMap] mutation.
 *
 * A lazy deferred is published before it is started. This ordering matters:
 * an immediately-completing task can safely remove itself because the map is
 * no longer inside a compute operation. Conditional removal also prevents an
 * older completion from removing a replacement task for the same key.
 */
internal class DeferredSingleFlight<K : Any, V>(
    private val scope: CoroutineScope,
) {
    private val active = ConcurrentHashMap<K, Deferred<V>>()

    fun getOrStart(key: K, block: suspend () -> V): Deferred<V> {
        active[key]?.let { return it }

        val candidate = scope.async(start = CoroutineStart.LAZY) { block() }
        val existing = active.putIfAbsent(key, candidate)
        if (existing != null) {
            // The candidate was never started, so cancelling it cannot run block.
            candidate.cancel()
            return existing
        }

        candidate.invokeOnCompletion {
            active.remove(key, candidate)
        }
        candidate.start()
        return candidate
    }

    fun contains(key: K): Boolean = active.containsKey(key)

    fun forget(key: K): Deferred<V>? = active.remove(key)

    fun clear() = active.clear()

    internal val activeCount: Int
        get() = active.size
}
