package com.ivor.ivormusic.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout

/** Service-owned browse work. A deadline bounds the waiter even when the loader blocks. */
internal class MediaBrowseCache<K : Any, V : Any>(
    private val scope: CoroutineScope,
    private val timeoutMs: Long = 10_000L,
    private val ttlMs: Long = 5 * 60 * 1000L,
    private val maxEntries: Int = 64,
    private val now: () -> Long = System::currentTimeMillis,
    private val ttlForValue: (V) -> Long = { ttlMs },
    private val onUpdated: (K, V) -> Unit = { _, _ -> },
) {
    private data class Entry<V>(val value: V, val writtenAt: Long)
    private val lock = Any()
    private var generation = 0L
    private val permits = Semaphore(2)
    private val active = mutableMapOf<K, Deferred<V>>()
    private val entries = LinkedHashMap<K, Entry<V>>(16, 0.75f, true)

    fun peek(key: K): V? = synchronized(lock) { entries[key]?.value }

    fun values(): List<V> = synchronized(lock) { entries.values.map { it.value } }

    suspend fun get(key: K, loader: suspend () -> V): V {
        var stale: V? = null
        val work = synchronized(lock) {
            entries[key]?.let {
                if (now() - it.writtenAt < ttlForValue(it.value)) return it.value
                stale = it.value
            }
            active[key] ?: run {
                val startedIn = generation
                scope.async(start = CoroutineStart.LAZY) {
                    val value = permits.withPermit {
                        currentCoroutineContext().ensureActive()
                        loader()
                    }
                    currentCoroutineContext().ensureActive()
                    val published = synchronized(lock) {
                        if (generation != startedIn) false else {
                            entries[key] = Entry(value, now())
                            while (entries.size > maxEntries) {
                                entries.remove(entries.keys.first())
                            }
                            true
                        }
                    }
                    if (published) onUpdated(key, value)
                    value
                }.also { created ->
                    active[key] = created
                    created.invokeOnCompletion {
                        synchronized(lock) {
                            if (active[key] === created) active.remove(key)
                        }
                    }
                }
            }
        }
        work.start()
        stale?.let { return it }
        // The loader belongs to the service scope, not this timeout's child job.
        // Repeated requests join it rather than starting more blocking HTTP calls.
        return withTimeout(timeoutMs) { work.await() }
    }

    fun clear() {
        val jobs = synchronized(lock) {
            generation++
            entries.clear()
            active.values.toList().also { active.clear() }
        }
        jobs.forEach { it.cancel() }
    }
}

/** Auto commonly requests Int.MAX_VALUE rows; page arithmetic must not overflow. */
internal fun <T> mediaBrowsePage(items: List<T>, page: Int, pageSize: Int): List<T> {
    require(page >= 0 && pageSize > 0)
    val from = page.toLong() * pageSize.toLong()
    if (from >= items.size) return emptyList()
    val to = (from + pageSize).coerceAtMost(items.size.toLong())
    return items.subList(from.toInt(), to.toInt())
}
