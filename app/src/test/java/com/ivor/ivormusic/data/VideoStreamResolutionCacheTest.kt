package com.ivor.ivormusic.data

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoStreamResolutionCacheTest {

    @Test
    fun `concurrent surfaces share one stream extraction`() = runBlocking {
        val videoId = "single-flight-test"
        VideoStreamResolutionCache.invalidate(videoId)
        val starts = AtomicInteger(0)
        val release = CompletableDeferred<Unit>()
        val result = VideoStreamResult(
            listOf(VideoQuality("1080p", "https://media.example/video"))
        )
        val first = async {
            VideoStreamResolutionCache.getOrResolve(videoId) {
                starts.incrementAndGet()
                release.await()
                result
            }
        }
        while (starts.get() == 0) yield()
        val second = async {
            VideoStreamResolutionCache.getOrResolve(videoId) {
                starts.incrementAndGet()
                result
            }
        }

        release.complete(Unit)

        assertEquals(result, first.await())
        assertEquals(result, second.await())
        assertEquals(1, starts.get())
        VideoStreamResolutionCache.invalidate(videoId)
    }

    @Test
    fun `split stream cache expires with its earliest signed url`() {
        val result = VideoStreamResult(
            qualities = listOf(
                VideoQuality(
                    resolution = "2160p60",
                    url = "https://video.example/media?expire=3000",
                    audioUrl = "https://audio.example/media?expire=2400",
                )
            )
        )

        assertEquals(
            2_100_000L,
            streamResultExpiryMs(
                result = result,
                nowMs = 1_000_000L,
                fallbackTtlMs = 900_000L,
                expirySafetyMs = 300_000L,
            )
        )
    }

    @Test
    fun `unsigned manifest uses the short fallback lifetime`() {
        val result = VideoStreamResult(
            qualities = listOf(
                VideoQuality(
                    resolution = "Auto (HLS)",
                    url = "https://manifest.example/live.m3u8",
                    isDASH = true,
                    isLive = true,
                )
            )
        )

        assertEquals(
            1_900_000L,
            streamResultExpiryMs(
                result = result,
                nowMs = 1_000_000L,
                fallbackTtlMs = 900_000L,
                expirySafetyMs = 300_000L,
            )
        )
    }
}
