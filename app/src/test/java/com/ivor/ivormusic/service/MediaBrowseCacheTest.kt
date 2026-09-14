package com.ivor.ivormusic.service

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class MediaBrowseCacheTest {
    @Test fun `cold concurrent requests share one load including empty results`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val cache = MediaBrowseCache<String, List<String>>(scope)
            val release = CompletableDeferred<Unit>()
            val calls = AtomicInteger()
            val requests = List(8) {
                async { cache.get("playlist") { calls.incrementAndGet(); release.await(); emptyList() } }
            }
            delay(30)
            release.complete(Unit)
            requests.forEach { assertEquals(emptyList<String>(), it.await()) }
            assertEquals(emptyList<String>(), cache.get("playlist") { error("empty is cached") })
            assertEquals(1, calls.get())
        } finally { scope.cancel() }
    }

    @Test fun `blocking loader cannot hold browser deadline and retry joins it`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val release = CountDownLatch(1)
        try {
            val calls = AtomicInteger()
            val cache = MediaBrowseCache<String, String>(scope, timeoutMs = 100)
            repeat(2) {
                try {
                    withTimeout(2_000) {
                        cache.get("slow") {
                            calls.incrementAndGet()
                            check(release.await(5, TimeUnit.SECONDS))
                            "ready"
                        }
                    }
                    fail("expected browse timeout")
                } catch (e: TimeoutCancellationException) {
                    assertTrue("loader must still be blocked", release.count == 1L)
                }
            }
            assertEquals(1, calls.get())
            release.countDown()
            withTimeout(2_000) { while (cache.peek("slow") == null) delay(5) }
            assertEquals("ready", cache.get("slow") { error("late result must be reused") })
        } finally { release.countDown(); scope.cancel() }
    }

    @Test fun `stale content returns immediately and refresh notifies once`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            var now = 0L
            val updates = AtomicInteger()
            val cache = MediaBrowseCache<String, String>(scope, ttlMs = 10, now = { now },
                onUpdated = { _, _ -> updates.incrementAndGet() })
            assertEquals("old", cache.get("home") { "old" })
            now = 20
            val release = CompletableDeferred<Unit>()
            repeat(5) { assertEquals("old", cache.get("home") { release.await(); "new" }) }
            release.complete(Unit)
            withTimeout(2_000) { while (updates.get() != 2) delay(5) }
            assertEquals("new", cache.peek("home"))
        } finally { scope.cancel() }
    }

    @Test fun `profile invalidation rejects a late blocking result`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        try {
            val cache = MediaBrowseCache<String, String>(scope)
            val old = async {
                runCatching { cache.get("home") { started.countDown(); release.await(5, TimeUnit.SECONDS); "old account" } }
            }
            withTimeout(2_000) { while (started.count != 0L) delay(5) }
            cache.clear()
            assertEquals("new account", cache.get("home") { "new account" })
            release.countDown()
            old.await()
            assertEquals("new account", cache.peek("home"))
        } finally { release.countDown(); scope.cancel() }
    }

    @Test fun `two blocked loads leave the next load queued rather than executing`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val release = CountDownLatch(1)
        try {
            val started = AtomicInteger()
            val cache = MediaBrowseCache<Int, Int>(scope, timeoutMs = 100)
            val requests = (1..3).map { key -> async {
                runCatching { cache.get(key) { started.incrementAndGet(); release.await(5, TimeUnit.SECONDS); key } }
            } }
            requests.forEach { it.await() }
            assertEquals(2, started.get())
            release.countDown()
            withTimeout(2_000) { while (cache.values().size != 3) delay(5) }
        } finally { release.countDown(); scope.cancel() }
    }

    @Test fun `different queries stay separate and entries are bounded`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val cache = MediaBrowseCache<String, String>(scope, maxEntries = 2)
            cache.get("a") { "A" }; cache.get("b") { "B" }
            assertEquals("A", cache.get("a") { error("cached") })
            cache.get("c") { "C" }
            assertNull(cache.peek("b"))
            assertEquals("A", cache.peek("a"))
            assertEquals("C", cache.peek("c"))
        } finally { scope.cancel() }
    }

    @Test fun `pagination supports legacy maximum page size without overflow`() {
        val items = listOf(1, 2, 3)
        assertEquals(items, mediaBrowsePage(items, 0, Int.MAX_VALUE))
        assertEquals(emptyList<Int>(), mediaBrowsePage(items, Int.MAX_VALUE, Int.MAX_VALUE))
        assertEquals(listOf(3), mediaBrowsePage(items, 1, 2))
    }

    @Test fun `empty response can refresh sooner after connectivity recovers`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            var now = 0L
            val cache = MediaBrowseCache<String, List<String>>(scope, now = { now },
                ttlForValue = { if (it.isEmpty()) 15 else 300 })
            assertTrue(cache.get("home") { emptyList() }.isEmpty())
            now = 20
            cache.get("home") { listOf("reconnected") }
            withTimeout(2_000) { while (cache.peek("home").isNullOrEmpty()) delay(5) }
            assertEquals(listOf("reconnected"), cache.peek("home"))
        } finally { scope.cancel() }
    }
}
