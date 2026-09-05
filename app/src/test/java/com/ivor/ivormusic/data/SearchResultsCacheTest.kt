package com.ivor.ivormusic.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchResultsCacheTest {

    private var clock = 0L
    private fun cache(ttlMs: Long = 1_000L, maxEntries: Int = 3) =
        SearchResultsCache<String>(ttlMs = ttlMs, maxEntries = maxEntries, now = { clock })

    @Test
    fun `stores and returns a list within its ttl`() {
        val cache = cache()
        cache.put("a", listOf("one", "two"))
        clock = 999L
        assertEquals(listOf("one", "two"), cache.get("a"))
        assertTrue(cache.has("a"))
    }

    @Test
    fun `forgets an entry once its ttl passes`() {
        val cache = cache()
        cache.put("a", listOf("one"))
        clock = 1_001L
        assertNull(cache.get("a"))
        assertFalse(cache.has("a"))
    }

    /**
     * The repository answers a failed search and a search with no matches
     * identically. Caching the empty list would pin a network failure in place
     * for the whole TTL and make it look like the query has no results.
     */
    @Test
    fun `never stores an empty result`() {
        val cache = cache()
        cache.put("a", emptyList())
        assertNull(cache.get("a"))
    }

    @Test
    fun `evicts the least recently used entry past the ceiling`() {
        val cache = cache(maxEntries = 3)
        cache.put("a", listOf("a"))
        cache.put("b", listOf("b"))
        cache.put("c", listOf("c"))
        // Reading "a" makes "b" the least recently used, which is the whole
        // point of access order: someone comparing two categories of one query
        // must not have the one they keep returning to evicted.
        assertEquals(listOf("a"), cache.get("a"))
        cache.put("d", listOf("d"))

        assertNull(cache.get("b"))
        assertEquals(listOf("a"), cache.get("a"))
        assertEquals(listOf("c"), cache.get("c"))
        assertEquals(listOf("d"), cache.get("d"))
    }

    /**
     * Pagination: the repository's continuation cursor advances as pages load,
     * so what is cached has to be everything the screen is showing.
     */
    @Test
    fun `append grows an existing entry`() {
        val cache = cache()
        cache.put("a", listOf("page1"))
        cache.append("a", listOf("page2"))
        assertEquals(listOf("page1", "page2"), cache.get("a"))
    }

    @Test
    fun `append does not create an entry that was never stored`() {
        val cache = cache()
        cache.append("a", listOf("page2"))
        assertNull(cache.get("a"))
    }

    @Test
    fun `append refreshes the ttl so a paged list does not expire mid-scroll`() {
        val cache = cache()
        cache.put("a", listOf("page1"))
        clock = 900L
        cache.append("a", listOf("page2"))
        clock = 1_800L
        assertEquals(listOf("page1", "page2"), cache.get("a"))
    }

    @Test
    fun `clear drops everything`() {
        val cache = cache()
        cache.put("a", listOf("a"))
        cache.put("b", listOf("b"))
        cache.clear()
        assertNull(cache.get("a"))
        assertNull(cache.get("b"))
    }
}
