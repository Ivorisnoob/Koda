package com.ivor.ivormusic.data.youtube.sabr

import com.ivor.ivormusic.data.youtube.sabr.bridge.reloadOnce
import com.ivor.ivormusic.data.youtube.sabr.exception.SabrReloadException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SabrReloadTest {
    @Test fun `first success never reloads`() = runBlocking {
        var attempts = 0
        var reloads = 0
        val result = reloadOnce(onReload = { reloads++ }) { attempts++; "ok" }
        assertEquals("ok", result)
        assertEquals(1, attempts)
        assertEquals(0, reloads)
    }

    @Test fun `first null stays null without reloading`() = runBlocking {
        var attempts = 0
        var reloads = 0
        val result = reloadOnce(onReload = { reloads++ }) { attempts++; null as String? }
        assertNull(result)
        assertEquals(1, attempts)
        assertEquals(0, reloads)
    }

    @Test fun `reload then success returns fresh value`() = runBlocking {
        var attempts = 0
        var reloads = 0
        val result = reloadOnce(onReload = { reloads++ }) {
            if (++attempts == 1) throw SabrReloadException("stale player response")
            "fresh"
        }
        assertEquals("fresh", result)
        assertEquals(2, attempts)
        assertEquals(1, reloads)
    }

    @Test fun `second failure falls back instead of looping`() = runBlocking {
        var attempts = 0
        var reloads = 0
        val result = reloadOnce(onReload = { reloads++ }) {
            attempts++
            if (attempts == 1) throw SabrReloadException("stale player response")
            throw SabrReloadException("stale again")
        }
        assertNull(result)
        assertEquals(2, attempts)
        assertEquals(1, reloads)
    }

    @Test fun `non reload failure propagates without reloading`() = runBlocking {
        var attempts = 0
        var reloads = 0
        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                reloadOnce(onReload = { reloads++ }) {
                    attempts++
                    throw IllegalStateException("broken")
                }
            }
        }
        assertEquals(1, attempts)
        assertEquals(0, reloads)
    }

    @Test fun `cancellation propagates on both attempts`() {
        var reloads = 0
        assertThrows(CancellationException::class.java) {
            runBlocking {
                reloadOnce(onReload = { reloads++ }) { throw CancellationException() }
            }
        }
        assertEquals(0, reloads)
        var attempts = 0
        assertThrows(CancellationException::class.java) {
            runBlocking {
                reloadOnce(onReload = { reloads++ }) {
                    if (++attempts == 1) throw SabrReloadException("stale player response")
                    throw CancellationException()
                }
            }
        }
        assertEquals(1, reloads)
        assertEquals(2, attempts)
    }
}
