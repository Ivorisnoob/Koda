package com.ivor.ivormusic.service.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaNotificationGateTest {
    private val gate = MediaNotificationGate()

    private val playing = MediaNotificationState(
        playerIdentity = 1,
        mediaId = "v1",
        queueItemId = "q1",
        title = "Song",
        artist = "Artist",
        album = "Album",
        artworkUri = "https://example.com/a.jpg",
        playWhenReady = true,
        playbackState = 3,
        suppressionReason = 0,
        isPlaying = true,
        speed = 1f,
        canGoPrevious = true,
        canGoNext = true,
        canPlayPause = true,
        repeatMode = 0,
        liked = false,
        promoted = false,
    )

    @Test
    fun firstRequestPosts() {
        assertTrue(gate.shouldPost(playing))
    }

    @Test
    fun anUnchangedRequestIsSkipped() {
        gate.shouldPost(playing)
        // What a prefetched stream swapped into an upcoming row looks like.
        assertFalse(gate.shouldPost(playing.copy()))
        assertFalse(gate.shouldPost(playing))
    }

    @Test
    fun everythingTheNotificationDrawsPostsAgain() {
        gate.shouldPost(playing)
        val changes = listOf(
            playing.copy(playWhenReady = false),
            playing.copy(mediaId = "v2"),
            playing.copy(queueItemId = "q2"),
            playing.copy(title = "Other"),
            playing.copy(artworkUri = "https://example.com/b.jpg"),
            playing.copy(liked = true),
            playing.copy(repeatMode = 2),
            playing.copy(canGoNext = false),
            playing.copy(playerIdentity = 2),
            playing.copy(promoted = true),
        )
        for (changed in changes) {
            assertTrue("$changed", gate.shouldPost(changed))
            assertTrue(gate.shouldPost(playing))
        }
    }

    @Test
    fun resetLetsTheSameStatePostAgain() {
        gate.shouldPost(playing)
        gate.reset()
        assertTrue(gate.shouldPost(playing))
    }
}
