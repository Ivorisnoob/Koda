package com.ivor.ivormusic.service

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import java.lang.reflect.Proxy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class CrossfadeEngineTest {
    private fun item(id: String) = MediaItem.Builder().setMediaId(id).build()

    private suspend fun awaitCondition(condition: () -> Boolean) {
        withTimeout(3_000L) { while (!condition()) delay(10L) }
    }

    private class Pairing(scope: CoroutineScope, onSwap: (ExoPlayer) -> Unit = {}) {
        val outgoing = ClockPlayer()
        val incoming = ClockPlayer()
        private var built = 0
        val engine = CrossfadeEngine(
            scope, playerFactory = { if (built++ == 0) outgoing.player else incoming.player },
            onActiveChanged = onSwap, gainFor = { 1f },
        )
    }

    @Test fun `handoff retains incoming progress and splices the live queue`() = runBlocking {
        var swaps = 0
        val pair = Pairing(this) { swaps++ }
        val songs = listOf(item("a"), item("b"), item("c"))
        pair.outgoing.items += songs
        try {
            assertTrue(pair.engine.startTransition(songs[1], 400L, targetIndex = 1))
            awaitCondition { !pair.engine.isFading }
            assertSame(pair.incoming.player, pair.engine.active)
            assertEquals(1, swaps)
            assertEquals(songs, pair.incoming.items)
            assertEquals(1, pair.incoming.index)
            assertTrue(pair.incoming.position > 300L)
            assertTrue(pair.outgoing.items.isEmpty())
        } finally { pair.engine.release() }
    }

    @Test fun `same track cannot be mixed into itself under repeat all`() = runBlocking {
        val pair = Pairing(this)
        val same = item("same")
        pair.outgoing.items += listOf(same, same)
        try {
            assertFalse(pair.engine.startTransition(same, 400L, targetIndex = 1))
            assertFalse(pair.engine.isFading)
        } finally { pair.engine.release() }
    }

    @Test fun `removing the target during an overlap keeps the outgoing track`() = runBlocking {
        val pair = Pairing(this)
        pair.outgoing.items += listOf(item("a"), item("b"), item("c"))
        try {
            assertTrue(pair.engine.startTransition(pair.outgoing.items[1], 800L, targetIndex = 1))
            awaitCondition { pair.incoming.playWhenReady }
            pair.outgoing.items.removeAt(1)
            awaitCondition { !pair.engine.isFading }
            assertSame(pair.outgoing.player, pair.engine.active)
            assertEquals(1f, pair.outgoing.volume, 0.001f)
            assertTrue(pair.incoming.items.isEmpty())
        } finally { pair.engine.release() }
    }

    @Test fun `incoming clock that never starts cannot take the session`() = runBlocking {
        val pair = Pairing(this)
        pair.outgoing.items += listOf(item("a"), item("b"))
        pair.incoming.stalled = true
        try {
            assertTrue(pair.engine.startTransition(pair.outgoing.items[1], 400L, targetIndex = 1))
            awaitCondition { !pair.engine.isFading }
            assertSame(pair.outgoing.player, pair.engine.active)
            assertEquals(1f, pair.outgoing.volume, 0.001f)
        } finally { pair.engine.release() }
    }

    @Test fun `diverging clocks abort rather than completing a mistimed handoff`() = runBlocking {
        val pair = Pairing(this)
        pair.outgoing.items += listOf(item("a"), item("b"))
        try {
            assertTrue(pair.engine.startTransition(pair.outgoing.items[1], 800L, targetIndex = 1))
            awaitCondition { pair.incoming.volume > 0.05f }
            pair.incoming.position += 500L
            awaitCondition { !pair.engine.isFading }
            assertSame(pair.outgoing.player, pair.engine.active)
            assertEquals(1f, pair.outgoing.volume, 0.001f)
        } finally { pair.engine.release() }
    }

    @Test fun `repeat mode change cancels a pending transition synchronously`() = runBlocking {
        val pair = Pairing(this)
        pair.outgoing.items += listOf(item("a"), item("b"))
        try {
            assertTrue(pair.engine.startTransition(pair.outgoing.items[1], 800L, targetIndex = 1))
            pair.engine.setRepeatMode(Player.REPEAT_MODE_ONE)
            assertFalse(pair.engine.isFading)
            assertSame(pair.outgoing.player, pair.engine.active)
            assertTrue(pair.incoming.items.isEmpty())
        } finally { pair.engine.release() }
    }

    @Test fun `failed session handoff restores the active player reference`() = runBlocking {
        val pair = Pairing(this) { error("Session rejected the swap") }
        pair.outgoing.items += listOf(item("a"), item("b"))
        try {
            assertTrue(pair.engine.startTransition(pair.outgoing.items[1], 400L, targetIndex = 1))
            awaitCondition { !pair.engine.isFading }
            assertSame(pair.outgoing.player, pair.engine.active)
            assertEquals("a", pair.outgoing.items[pair.outgoing.index].mediaId)
            assertTrue(pair.outgoing.playWhenReady)
            assertTrue(pair.incoming.items.isEmpty())
        } finally { pair.engine.release() }
    }

    /** A queue and advancing playback clock, with no decoder or Android looper. */
    private class ClockPlayer {
        val items = mutableListOf<MediaItem>()
        var index = 0
        var volume = 1f
        var stalled = false
        private var state = Player.STATE_READY
        private var parameters = PlaybackParameters.DEFAULT
        private var storedPosition = 8_000L
        private var updatedAt = System.nanoTime()
        var playWhenReady = true
            set(value) {
                position = position
                field = value
            }
        var position: Long
            get() = storedPosition + if (playWhenReady && state == Player.STATE_READY && !stalled) {
                ((System.nanoTime() - updatedAt) / 1_000_000L * parameters.speed).toLong()
            } else 0L
            set(value) { storedPosition = value; updatedAt = System.nanoTime() }

        val player = Proxy.newProxyInstance(
            ExoPlayer::class.java.classLoader, arrayOf(ExoPlayer::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getMediaItemCount" -> items.size
                "getCurrentMediaItemIndex" -> index
                "getCurrentMediaItem" -> items.getOrNull(index)
                "getMediaItemAt" -> items[args!![0] as Int]
                "getNextMediaItemIndex" -> if (index + 1 < items.size) index + 1 else -1
                "getDuration" -> 10_000L
                "getCurrentPosition" -> position
                "getPlaybackParameters" -> parameters
                "setPlaybackParameters" -> { position = position; parameters = args!![0] as PlaybackParameters; null }
                "getPlaybackState" -> state
                "getPlayerError" -> null
                "isPlaying" -> playWhenReady && state == Player.STATE_READY
                "getPlayWhenReady" -> playWhenReady
                "setPlayWhenReady" -> { playWhenReady = args!![0] as Boolean; null }
                "getVolume" -> volume
                "setVolume" -> { volume = args!![0] as Float; null }
                "setMediaItem" -> {
                    items.clear(); items += args!![0] as MediaItem
                    index = 0; position = args[1] as Long
                    null
                }
                "addMediaItems" -> {
                    @Suppress("UNCHECKED_CAST")
                    if (args!!.size == 1) items.addAll(args[0] as List<MediaItem>)
                    else {
                        val at = args[0] as Int
                        val added = args[1] as List<MediaItem>
                        items.addAll(at, added)
                        if (at <= index) index += added.size
                    }
                    null
                }
                "prepare" -> { state = Player.STATE_READY; null }
                "stop" -> { position = position; state = Player.STATE_IDLE; null }
                "clearMediaItems" -> { items.clear(); index = 0; position = 0; null }
                "setShuffleOrder", "setShuffleModeEnabled", "setRepeatMode",
                "addListener", "removeListener", "release" -> null
                else -> error("Unexpected player call: ${method.name}")
            }
        } as ExoPlayer
    }
}
