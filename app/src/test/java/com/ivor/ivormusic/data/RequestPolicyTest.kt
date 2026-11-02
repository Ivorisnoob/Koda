package com.ivor.ivormusic.data

import com.ivor.ivormusic.data.stream.BotCheckVerdict
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * [RequestPolicy] on a real OkHttp client whose last interceptor answers
 * instead of the network, so the test makes no request and still exercises
 * the chain the app runs: the policy reads the thread-local on the thread
 * `execute()` was called from.
 */
class RequestPolicyTest {

    private val client = OkHttpClient.Builder()
        .addInterceptor(RequestPolicy)
        .addInterceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("{}".toResponseBody())
                .build()
        }
        .build()

    @After fun clearHolds() {
        YouTubeRateLimit.clear()
        BotCheckVerdict.reset()
    }

    private fun call(url: String): Int =
        client.newCall(Request.Builder().url(url).build()).execute().use { it.code }

    private fun assertHeld(url: String) {
        try {
            call(url)
            fail("expected $url to be held")
        } catch (expected: YouTubeRequestHeldException) {
        }
    }

    private val browse = "https://www.youtube.com/youtubei/v1/browse"
    private val player = "https://www.youtube.com/youtubei/v1/player"

    @Test fun `work is not automatic unless marked`() = runBlocking {
        assertFalse(RequestPriority.isAutomatic())
        withContext(Dispatchers.IO) { assertFalse(RequestPriority.isAutomatic()) }
    }

    @Test fun `the mark follows the work onto other threads and children`() = runBlocking {
        withContext(RequestPriority.Automatic) {
            val seen = (1..4).map { async(Dispatchers.IO) { RequestPriority.isAutomatic() } }.awaitAll()
            assertEquals(listOf(true, true, true, true), seen)
            withContext(Dispatchers.IO) { assertTrue(RequestPriority.isAutomatic()) }
        }
        assertFalse(RequestPriority.isAutomatic())
    }

    @Test fun `nothing is held without a hold`() = runBlocking {
        withContext(RequestPriority.Automatic + Dispatchers.IO) {
            assertEquals(200, call(browse))
        }
    }

    @Test fun `a 429 hold keeps automatic YouTube requests on the device`() = runBlocking {
        YouTubeRateLimit.note(429, "test")
        withContext(RequestPriority.Automatic + Dispatchers.IO) {
            assertHeld(browse)
            assertHeld("https://www.youtube.com/feeds/videos.xml?channel_id=UC")
        }
    }

    @Test fun `a request someone made goes out during a hold`() = runBlocking {
        YouTubeRateLimit.note(429, "test")
        withContext(Dispatchers.IO) { assertEquals(200, call(browse)) }
    }

    @Test fun `media bytes are never held`() = runBlocking {
        YouTubeRateLimit.note(429, "test")
        withContext(RequestPriority.Automatic + Dispatchers.IO) {
            assertEquals(200, call("https://rr1---sn-example.googlevideo.com/videoplayback"))
        }
    }

    @Test fun `the bot-check verdict holds automatic stream calls only`() = runBlocking {
        BotCheckVerdict.note("test")
        withContext(RequestPriority.Automatic + Dispatchers.IO) {
            assertHeld(player)
            assertEquals(200, call(browse))
        }
        withContext(Dispatchers.IO) { assertEquals(200, call(player)) }
    }
}
