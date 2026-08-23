package com.ivor.ivormusic.ui.video

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoPlaybackBehaviorTest {

    @Test
    fun `autoplay off always stops even when more content exists`() {
        assertEquals(
            VideoEndAction.STOP,
            resolveVideoEndAction(
                autoplayEnabled = false,
                queueHasNext = true,
                isInPipMode = false,
                hasRelatedVideo = true
            )
        )
    }

    @Test
    fun `playlist advances before related video`() {
        assertEquals(
            VideoEndAction.NEXT_IN_QUEUE,
            resolveVideoEndAction(
                autoplayEnabled = true,
                queueHasNext = true,
                isInPipMode = false,
                hasRelatedVideo = true
            )
        )
    }

    @Test
    fun `playlist may continue in picture in picture`() {
        assertEquals(
            VideoEndAction.NEXT_IN_QUEUE,
            resolveVideoEndAction(
                autoplayEnabled = true,
                queueHasNext = true,
                isInPipMode = true,
                hasRelatedVideo = true
            )
        )
    }

    @Test
    fun `related video is suppressed in picture in picture`() {
        assertEquals(
            VideoEndAction.STOP,
            resolveVideoEndAction(
                autoplayEnabled = true,
                queueHasNext = false,
                isInPipMode = true,
                hasRelatedVideo = true
            )
        )
    }

    @Test
    fun `related video plays when autoplay is on outside picture in picture`() {
        assertEquals(
            VideoEndAction.NEXT_RELATED,
            resolveVideoEndAction(
                autoplayEnabled = true,
                queueHasNext = false,
                isInPipMode = false,
                hasRelatedVideo = true
            )
        )
    }

    @Test
    fun `player stops when autoplay has no next item`() {
        assertEquals(
            VideoEndAction.STOP,
            resolveVideoEndAction(
                autoplayEnabled = true,
                queueHasNext = false,
                isInPipMode = false,
                hasRelatedVideo = false
            )
        )
    }
}
