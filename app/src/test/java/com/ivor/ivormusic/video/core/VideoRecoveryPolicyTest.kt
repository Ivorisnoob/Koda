package com.ivor.ivormusic.video.core

import com.ivor.ivormusic.video.core.VideoRecoveryStep.FALL_BACK_TO_SDR
import com.ivor.ivormusic.video.core.VideoRecoveryStep.HOLD_OFFLINE
import com.ivor.ivormusic.video.core.VideoRecoveryStep.RE_PREPARE
import com.ivor.ivormusic.video.core.VideoRecoveryStep.RE_RESOLVE
import com.ivor.ivormusic.video.core.VideoRecoveryStep.SHOW_ERROR
import com.ivor.ivormusic.video.core.VideoRecoveryStep.SKIP_IN_QUEUE
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoRecoveryPolicyTest {
    private val sourceFailure = VideoFailure(
        isRendererError = false,
        isSourceError = true,
        httpStatus = null,
        hasSdrFallback = false,
        networkAllowed = true,
        isLocalPlayback = false,
        isBotCheckActive = false,
        queueHasNext = false,
    )
    private val rendererFailure = sourceFailure.copy(isRendererError = true, isSourceError = false)

    @Test
    fun `an HDR failure falls back to SDR once a video`() {
        val policy = VideoRecoveryPolicy()
        val failure = sourceFailure.copy(hasSdrFallback = true)
        assertEquals(FALL_BACK_TO_SDR, policy.next(failure, 0L))
        assertEquals(RE_RESOLVE, policy.next(failure, 0L))

        policy.onNewVideo()
        assertEquals(FALL_BACK_TO_SDR, policy.next(failure, 0L))
    }

    @Test
    fun `a 403 on HDR re-resolves and leaves the fallback armed`() {
        val policy = VideoRecoveryPolicy()
        assertEquals(RE_RESOLVE, policy.next(sourceFailure.copy(hasSdrFallback = true, httpStatus = 403), 0L))
        assertEquals(FALL_BACK_TO_SDR, policy.next(sourceFailure.copy(hasSdrFallback = true), 0L))
    }

    @Test
    fun `a renderer failure re-prepares twice and comes back on READY`() {
        val policy = VideoRecoveryPolicy()
        assertEquals(RE_PREPARE, policy.next(rendererFailure, 0L))
        assertEquals(RE_PREPARE, policy.next(rendererFailure, 0L))
        assertEquals(SHOW_ERROR, policy.next(rendererFailure, 0L))

        policy.onReady()
        assertEquals(RE_PREPARE, policy.next(rendererFailure, 0L))
    }

    @Test
    fun `offline holds the video and spends nothing`() {
        val policy = VideoRecoveryPolicy()
        val offline = sourceFailure.copy(networkAllowed = false, queueHasNext = true)
        assertEquals(HOLD_OFFLINE, policy.next(offline, 0L))
        assertEquals(HOLD_OFFLINE, policy.next(offline, 0L))
        assertEquals(RE_RESOLVE, policy.next(sourceFailure, 0L))
    }

    @Test
    fun `reaching READY does not refill the source budget`() {
        val policy = VideoRecoveryPolicy()
        assertEquals(RE_RESOLVE, policy.next(sourceFailure, 0L))
        policy.onReady()
        assertEquals(SHOW_ERROR, policy.next(sourceFailure, 1_000L))
    }

    @Test
    fun `a minute of healthy playback gives the source attempt back`() {
        val policy = VideoRecoveryPolicy()
        assertEquals(RE_RESOLVE, policy.next(sourceFailure, 1_000L))

        policy.onHealthy(1_000L + VideoRecoveryPolicy.SOURCE_RETRY_REFUND_MS - 1)
        assertEquals(SHOW_ERROR, policy.next(sourceFailure, 2_000L))

        policy.onHealthy(1_000L + VideoRecoveryPolicy.SOURCE_RETRY_REFUND_MS)
        assertEquals(RE_RESOLVE, policy.next(sourceFailure, 70_000L))
    }

    @Test
    fun `the bot check verdict skips the silent re-resolve and spends nothing`() {
        val policy = VideoRecoveryPolicy()
        assertEquals(SHOW_ERROR, policy.next(sourceFailure.copy(isBotCheckActive = true), 0L))
        assertEquals(RE_RESOLVE, policy.next(sourceFailure, 0L))
    }

    @Test
    fun `a device video never re-resolves`() {
        val policy = VideoRecoveryPolicy()
        assertEquals(SHOW_ERROR, policy.next(sourceFailure.copy(isLocalPlayback = true), 0L))
    }

    @Test
    fun `a playlist steps over three dead entries, then stops`() {
        val policy = VideoRecoveryPolicy()
        val inQueue = sourceFailure.copy(queueHasNext = true, isBotCheckActive = true)
        repeat(VideoRecoveryPolicy.MAX_QUEUE_SKIPS) {
            assertEquals(SKIP_IN_QUEUE, policy.next(inQueue, 0L))
        }
        assertEquals(SHOW_ERROR, policy.next(inQueue, 0L))
    }

    @Test
    fun `anything playing or a chosen queue move restarts the skip count`() {
        val policy = VideoRecoveryPolicy()
        val inQueue = sourceFailure.copy(queueHasNext = true, isBotCheckActive = true)
        repeat(VideoRecoveryPolicy.MAX_QUEUE_SKIPS) { policy.next(inQueue, 0L) }

        policy.onReady()
        assertEquals(SKIP_IN_QUEUE, policy.next(inQueue, 0L))

        repeat(VideoRecoveryPolicy.MAX_QUEUE_SKIPS - 1) { policy.next(inQueue, 0L) }
        policy.onQueueMoved()
        assertEquals(SKIP_IN_QUEUE, policy.next(inQueue, 0L))
    }

    @Test
    fun `the last entry of a playlist shows the error`() {
        val policy = VideoRecoveryPolicy()
        val lastEntry = sourceFailure.copy(queueHasNext = false, isBotCheckActive = true)
        assertEquals(SHOW_ERROR, policy.next(lastEntry, 0L))
    }

    @Test
    fun `retry gives back the retries and keeps the HDR fallback spent`() {
        val policy = VideoRecoveryPolicy()
        val hdr = sourceFailure.copy(hasSdrFallback = true)
        assertEquals(FALL_BACK_TO_SDR, policy.next(hdr, 0L))
        assertEquals(RE_RESOLVE, policy.next(hdr, 0L))

        policy.onRetry()
        assertEquals(RE_RESOLVE, policy.next(hdr, 0L))
    }

    @Test
    fun `an unrecognised failure goes straight to the error`() {
        val policy = VideoRecoveryPolicy()
        assertEquals(SHOW_ERROR, policy.next(sourceFailure.copy(isSourceError = false), 0L))
    }
}
