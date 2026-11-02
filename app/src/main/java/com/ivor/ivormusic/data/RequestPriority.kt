package com.ivor.ivormusic.data

import com.ivor.ivormusic.data.stream.BotCheckVerdict
import java.io.IOException
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.asContextElement
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Whether the request being made is one somebody is waiting on, or work the
 * app started by itself.
 *
 * [scar] Every rate-limit loop found in October 2026 had one shape: something
 * automatic - a recovery, a feed top-up, a preview, a history report - repeated
 * its work at the moment YouTube was already refusing the device, and each
 * place had a guard of its own or none at all. This is the one guard. Work the
 * app starts on its own runs inside [Automatic]:
 *
 *     withContext(RequestPriority.Automatic) { repository.getChannelPosts(id) }
 *
 * and [RequestPolicy] keeps its YouTube requests on the device while a 429
 * hold is on, and its `/player` calls while the bot-check verdict stands.
 * Anything unmarked is a request someone made and goes out regardless, which
 * is the rule both holds already keep.
 *
 * It rides the coroutine as a thread-local. OkHttp's synchronous `execute()`
 * runs interceptors on the calling thread, the thread the coroutine is resumed
 * on, and the `withContext(Dispatchers.IO)` inside the repositories carries the
 * element across. Work handed to another scope does not carry it:
 * [VideoStreamResolutionCache] resolves in its own, so a stream resolution is
 * never marked, and a caller that starts one automatically checks the holds
 * itself (the inline preview, the video player's recovery).
 */
object RequestPriority {
    private val automatic = ThreadLocal<Boolean?>()

    /** Marks the coroutine's requests as work the app started by itself. */
    val Automatic: CoroutineContext.Element = automatic.asContextElement(true)

    /** Whether the request being built on this thread belongs to automatic work. */
    internal fun isAutomatic(): Boolean = automatic.get() == true
}

/**
 * An automatic request [RequestPolicy] kept on the device. An [IOException], so
 * every caller already handles it as the failed request it is.
 *
 * Expected, not exceptional: it carries no stack trace, so the many callers
 * that log any failure with its trace write one line into the bug report per
 * held request rather than a page. [YouTubeRequestLedger] counts them.
 */
class YouTubeRequestHeldException(endpoint: String, reason: String) :
    IOException("Automatic $endpoint request held: $reason") {
    override fun fillInStackTrace(): Throwable = this
}

/**
 * Refuses automatic YouTube requests during a hold; see [RequestPriority].
 *
 * Installed on `YouTubeRepository`'s base client ahead of
 * [YouTubeRequestLedger], so a held request is listed as held rather than
 * counted as one YouTube saw. Media bytes from googlevideo are not YouTube
 * requests to the ledger and pass untouched.
 */
internal object RequestPolicy : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!RequestPriority.isAutomatic()) return chain.proceed(request)
        val endpoint = YouTubeRequestLedger.endpointOf(request.url)
            ?: return chain.proceed(request)
        val reason = when {
            YouTubeRateLimit.isHeld() -> "rate limited"
            // A stream call under a standing verdict is refused again: the
            // verdict is on the address, not on the token or the video.
            endpoint == "player" && BotCheckVerdict.isActive() -> "bot check"
            else -> return chain.proceed(request)
        }
        YouTubeRequestLedger.noteHeld(endpoint)
        throw YouTubeRequestHeldException(endpoint, reason)
    }
}
