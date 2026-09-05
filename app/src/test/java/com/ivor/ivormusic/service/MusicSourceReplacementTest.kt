package com.ivor.ivormusic.service

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicSourceReplacementTest {
    @Test
    fun `stream recovery at thirty seconds resumes the same position`() {
        val state = ResettingPlayer(positionMs = 30_000L)

        state.player.replaceMusicSource(2, MediaItem.EMPTY)

        assertEquals(30_000L, state.positionMs)
        assertEquals(2, state.currentIndex)
        assertTrue(state.playWhenReady)
        assertEquals(listOf("replace", "seek", "prepare", "intent"), state.operations)
        assertEquals(30_000L, state.preparedPositionMs)
    }

    @Test
    fun `a pause and seek during resolution are retained at apply time`() {
        val state = ResettingPlayer(positionMs = 8_000L)
        state.positionMs = 47_000L
        state.playWhenReady = false

        state.player.replaceMusicSource(2, MediaItem.EMPTY)

        assertEquals(47_000L, state.positionMs)
        assertFalse(state.playWhenReady)
    }

    @Test
    fun `a future item replacement leaves audible playback alone`() {
        val state = ResettingPlayer(positionMs = 19_000L)

        state.player.replaceMusicSource(3, MediaItem.EMPTY)

        assertEquals(19_000L, state.positionMs)
        assertEquals(2, state.currentIndex)
        assertEquals(listOf("replace"), state.operations)
    }

    @Test
    fun `prefetch that becomes current before completion keeps its progress`() {
        val state = ResettingPlayer(positionMs = 19_000L)
        val prefetchedIndex = 3
        state.currentIndex = prefetchedIndex
        state.positionMs = 5_000L

        state.player.replaceMusicSource(prefetchedIndex, MediaItem.EMPTY)

        assertEquals(5_000L, state.positionMs)
        assertEquals(prefetchedIndex, state.currentIndex)
    }

    @Test
    fun `replacement restores the intended index even at position zero`() {
        val state = ResettingPlayer(positionMs = 0L)

        state.player.replaceMusicSource(2, MediaItem.EMPTY)

        assertEquals(2, state.currentIndex)
        assertEquals(0L, state.positionMs)
    }

    @Test
    fun `an unset position starts at zero`() {
        val state = ResettingPlayer(positionMs = -1L)

        state.player.replaceMusicSource(2, MediaItem.EMPTY)

        assertEquals(0L, state.positionMs)
    }

    /**
     * Model the destructive source replacement, including selection of another
     * successor under shuffle. Unexpected calls fail instead of silently acting
     * like a functioning player; no Android playback thread is needed here.
     */
    private class ResettingPlayer(var positionMs: Long) {
        var currentIndex = 2
        var playWhenReady = true
        var preparedPositionMs: Long? = null
        val operations = mutableListOf<String>()

        val player = Proxy.newProxyInstance(
            Player::class.java.classLoader,
            arrayOf(Player::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getCurrentMediaItemIndex" -> currentIndex
                "getCurrentPosition" -> positionMs
                "getPlayWhenReady" -> playWhenReady
                "replaceMediaItem" -> {
                    operations += "replace"
                    if (args!![0] == currentIndex) {
                        positionMs = 0L
                        currentIndex++
                    }
                    null
                }
                "seekTo" -> {
                    operations += "seek"
                    currentIndex = args!![0] as Int
                    positionMs = args[1] as Long
                    null
                }
                "prepare" -> {
                    operations += "prepare"
                    preparedPositionMs = positionMs
                    null
                }
                "setPlayWhenReady" -> {
                    operations += "intent"
                    playWhenReady = args!![0] as Boolean
                    null
                }
                else -> error("Unexpected player call: ${method.name}")
            }
        } as Player
    }
}
