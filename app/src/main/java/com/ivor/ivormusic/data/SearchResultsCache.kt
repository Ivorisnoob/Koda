package com.ivor.ivormusic.data

/**
 * A short-lived, bounded cache of one kind of search result.
 *
 * Search is filtered by a row of category chips - Songs, Artists, Albums,
 * Playlists in music mode, Videos, Playlists, Channels in video mode - and
 * every switch between them re-ran the search from scratch, debounce included.
 * Tapping Artists, looking, tapping Songs and tapping Artists again was three
 * requests for two distinct questions, which is a cost paid on someone else's
 * rate limit as well as on the user's connection.
 *
 * One instance per result type rather than one shared map of `Any`: the types
 * are what tell the entries apart, so making that the compiler's job costs a
 * field per category and removes a whole class of mistake.
 *
 * Deliberately not process-wide state. Search is driven by one `HomeViewModel`
 * and nothing else reaches it, so this is an ordinary field on that ViewModel
 * rather than an eleventh entry on the closed list of process-wide stores -
 * that list is for results a second surface holding its own repository has to
 * see, which this is not.
 *
 * Two rules worth keeping:
 *
 * **Empty results are never stored.** The repository answers a failed search
 * and a search with no matches identically, with an empty list, so caching one
 * would pin a network failure in place for the whole TTL and make it look like
 * the query genuinely has no results.
 *
 * **Access order, not insertion order.** Someone comparing two categories of
 * the same query is the case this exists for, and insertion order would evict
 * the one they keep coming back to.
 */
class SearchResultsCache<T>(
    private val ttlMs: Long = DEFAULT_TTL_MS,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    /** Injected so the TTL is testable without sleeping. */
    private val now: () -> Long = System::currentTimeMillis
) {

    private class Entry<T>(val value: List<T>, val storedAt: Long)

    private val entries = object : LinkedHashMap<String, Entry<T>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry<T>>): Boolean =
            size > maxEntries
    }

    /** The cached list for [key], or null when absent or past its TTL. */
    @Synchronized
    fun get(key: String): List<T>? {
        val entry = entries[key] ?: return null
        if (now() - entry.storedAt > ttlMs) {
            entries.remove(key)
            return null
        }
        return entry.value
    }

    /** Whether [get] would answer, without disturbing access order's usefulness. */
    @Synchronized
    fun has(key: String): Boolean = get(key) != null

    @Synchronized
    fun put(key: String, value: List<T>) {
        if (value.isEmpty()) return
        entries[key] = Entry(value, now())
    }

    /**
     * Replace a cached list with one that has grown.
     *
     * Pagination is why this exists rather than only [put]: the repository's
     * continuation cursor advances as pages are loaded, so a cache holding only
     * page one would, after a tab switch, show one page while the next "load
     * more" returned page four. What is stored is always everything the screen
     * is showing.
     */
    @Synchronized
    fun append(key: String, more: List<T>) {
        if (more.isEmpty()) return
        val existing = get(key) ?: return
        entries[key] = Entry(existing + more, now())
    }

    @Synchronized
    fun clear() = entries.clear()

    companion object {
        /**
         * Long enough to cover moving between categories and stepping into a
         * result and back, short enough that a search re-run minutes later is
         * genuinely re-run. Search results do change; this is not a store.
         */
        const val DEFAULT_TTL_MS = 5 * 60 * 1000L

        /** A handful of queries across every category, and no more. */
        const val DEFAULT_MAX_ENTRIES = 24
    }
}
