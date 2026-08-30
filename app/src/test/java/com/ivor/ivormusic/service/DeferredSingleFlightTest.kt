package com.ivor.ivormusic.service

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DeferredSingleFlightTest {

    @Test
    fun `immediate completion cleans up without a recursive map update`() = runBlocking {
        val singleFlight = DeferredSingleFlight<String, Int>(this)

        assertEquals(42, singleFlight.getOrStart("cached") { 42 }.await())
        yield()

        assertEquals(0, singleFlight.activeCount)
        assertFalse(singleFlight.contains("cached"))
    }

    @Test
    fun `same key shares one in-flight task`() = runBlocking {
        val singleFlight = DeferredSingleFlight<String, Int>(this)
        val gate = CompletableDeferred<Unit>()
        var calls = 0

        val first = singleFlight.getOrStart("video") {
            calls++
            gate.await()
            7
        }
        val second = singleFlight.getOrStart("video") { error("must not run") }

        assertSame(first, second)
        gate.complete(Unit)
        assertEquals(7, second.await())
        assertEquals(1, calls)
    }

    @Test
    fun `old completion cannot remove a replacement task`() = runBlocking {
        val singleFlight = DeferredSingleFlight<String, Int>(this)
        val firstGate = CompletableDeferred<Unit>()
        val secondGate = CompletableDeferred<Unit>()

        val first = singleFlight.getOrStart("video") {
            firstGate.await()
            1
        }
        assertSame(first, singleFlight.forget("video"))

        val replacement = singleFlight.getOrStart("video") {
            secondGate.await()
            2
        }
        firstGate.complete(Unit)
        assertEquals(1, first.await())
        yield()

        assertTrue(singleFlight.contains("video"))
        secondGate.complete(Unit)
        assertEquals(2, replacement.await())
    }
}
