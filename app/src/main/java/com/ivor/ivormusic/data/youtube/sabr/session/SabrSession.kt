/*
 * Control handling, redirect/backoff/integrity budgets and the progress loop adapted
 * from PipePipeExtractor's YoutubeSabrSession (c0cd0d61863f430af86475aaac968fbef245f507)
 * and PipePipe's SabrRequestCoordinator (08b277619ac05a5b227ca53a7fe4cb1958663c4d),
 * GPL-3.0. Copyright the upstream contributors. See THIRD_PARTY_NOTICES.md.
 * Koda changes: direct call cancellation, cancellable waits, identity/expiry checks,
 * one request number per sent request.
 */
package com.ivor.ivormusic.data.youtube.sabr.session

import com.ivor.ivormusic.data.youtube.sabr.exception.SabrAttestationException
import com.ivor.ivormusic.data.youtube.sabr.exception.SabrCancelledException
import com.ivor.ivormusic.data.youtube.sabr.exception.SabrHttpException
import com.ivor.ivormusic.data.youtube.sabr.exception.SabrIncompleteMediaException
import com.ivor.ivormusic.data.youtube.sabr.exception.SabrProtocolException
import com.ivor.ivormusic.data.youtube.sabr.exception.SabrReloadException
import com.ivor.ivormusic.data.youtube.sabr.exception.SabrServerErrorException
import com.ivor.ivormusic.data.youtube.sabr.exception.SabrStaleDescriptorException
import com.ivor.ivormusic.data.youtube.sabr.media.SabrSegment
import com.ivor.ivormusic.data.youtube.sabr.media.SabrSegmentReader
import com.ivor.ivormusic.data.youtube.sabr.media.SabrSpool
import com.ivor.ivormusic.data.youtube.sabr.model.SabrDescriptor
import com.ivor.ivormusic.data.youtube.sabr.model.SabrIdentity
import com.ivor.ivormusic.data.youtube.sabr.protocol.SabrResponseControls
import java.io.Closeable
import java.io.EOFException
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal sealed class SabrExchange {
    /** No request was sent: the server's backoff has [remainingMs] left. */
    data class Deferred(val remainingMs: Long) : SabrExchange()

    /** The response was truncated or inconsistent; [attempt] of the bounded retries. */
    data class Incomplete(val attempt: Int) : SabrExchange()

    data class Completed(val segments: Int, val backoffMs: Int, val redirected: Boolean) : SabrExchange()
}

/** [judgement] Upstream's values; tests shrink the waits. */
internal class SabrSessionLimits(
    val maxRedirects: Int = 3,
    val maxIncompleteResponses: Int = 3,
    val maxAttestationPendingResponses: Int = 3,
    val maxBackoffMs: Int = 30_000,
    val maxContinuousWaitMs: Long = 30_000,
    val emptyResponseRetryMs: Long = 250,
    val maxContexts: Int = 64,
)

/**
 * One playback source's SABR conversation. Holds the request counter, playback cookie,
 * contexts, redirect target and backoff; never shared between players. Transactions
 * are serialized. [close] may be called from any thread: it aborts the active HTTP
 * call and any wait. The caller owns [spool] and releases it after closing.
 */
internal class SabrSession(
    private val descriptor: SabrDescriptor,
    private val transport: SabrTransport,
    private val spool: SabrSpool,
    private val currentIdentity: () -> SabrIdentity,
    private val limits: SabrSessionLimits = SabrSessionLimits(),
    private val nowMs: () -> Long = System::currentTimeMillis,
) : Closeable {
    private val transaction = ReentrantLock()
    private val waitLock = ReentrantLock()
    private val wake = waitLock.newCondition()
    private val activeCall = AtomicReference<SabrCall?>()
    @Volatile private var closed = false
    @Volatile private var backoffDeadlineNs = 0L

    // Guarded by [transaction].
    private var streamingUrl = descriptor.serverAbrStreamingUrl
    private var playbackCookie: ByteArray? = null
    private val contexts = LinkedHashMap<Int, ByteArray>()
    private val activeContextTypes = LinkedHashSet<Int>()
    private var redirects = 0
    private var incompleteResponses = 0
    private var attestationPendingResponses = 0
    private var bandwidthEstimateBps = -1L

    @Volatile var requestNumber = 0
        private set

    init {
        SabrRequestEncoder.validateStreamingUrl(streamingUrl)
    }

    fun backoffRemainingMs(): Long {
        val remaining = backoffDeadlineNs - System.nanoTime()
        return if (backoffDeadlineNs == 0L || remaining <= 0) 0 else maxOf(1, TimeUnit.NANOSECONDS.toMillis(remaining))
    }

    /**
     * Runs one logical request until [progress] accepts a response, waiting out backoff
     * and retrying truncated or empty responses within continuous budgets. Attestation,
     * reload, server errors and stale descriptors are thrown for the caller to handle.
     */
    fun request(
        request: SabrRequest,
        onSegment: (SabrSegment) -> Unit,
        progress: (SabrExchange.Completed) -> Boolean = { it.segments > 0 },
    ): SabrExchange.Completed {
        transaction.lock()
        try {
            return requestLocked(request, onSegment, progress)
        } finally {
            transaction.unlock()
        }
    }

    private fun requestLocked(
        request: SabrRequest,
        onSegment: (SabrSegment) -> Unit,
        progress: (SabrExchange.Completed) -> Boolean,
    ): SabrExchange.Completed {
        var backoffEpisodeEnd = 0L
        var noProgressEpisodeEnd = 0L
        while (true) {
            val pending = backoffRemainingMs()
            if (pending > 0) {
                backoffEpisodeEnd = withinBudget(backoffEpisodeEnd, pending, "backoff")
                sleep(pending)
                continue
            }
            when (val result = execute(request, onSegment)) {
                is SabrExchange.Deferred -> Unit
                is SabrExchange.Incomplete -> {
                    noProgressEpisodeEnd = withinBudget(noProgressEpisodeEnd, limits.emptyResponseRetryMs, "no-progress")
                    sleep(limits.emptyResponseRetryMs)
                }
                is SabrExchange.Completed -> {
                    if (progress(result)) return result
                    if (result.backoffMs > 0) {
                        backoffEpisodeEnd = withinBudget(backoffEpisodeEnd, result.backoffMs.toLong(), "backoff")
                    }
                    noProgressEpisodeEnd = withinBudget(noProgressEpisodeEnd,
                        maxOf(result.backoffMs.toLong(), limits.emptyResponseRetryMs), "no-progress")
                    if (result.backoffMs <= 0) sleep(limits.emptyResponseRetryMs)
                }
            }
        }
    }

    /** Exactly one transaction, or [SabrExchange.Deferred] without network while backing off. */
    fun execute(request: SabrRequest, onSegment: (SabrSegment) -> Unit): SabrExchange = transaction.withLock {
        ensureOpen()
        if (!descriptor.isUsable(nowMs(), currentIdentity())) throw SabrStaleDescriptorException()
        backoffRemainingMs().let { if (it > 0) return SabrExchange.Deferred(it) }

        val state = SabrStreamerState(
            playbackCookie = playbackCookie?.copyOf(),
            activeContexts = activeContextTypes.mapNotNull { type -> contexts[type]?.let { type to it.copyOf() } },
            unsentContextTypes = contexts.keys.filter { it !in activeContextTypes },
            bandwidthEstimateBps = bandwidthEstimateBps,
        )
        val body = SabrRequestEncoder.body(descriptor, request, state, followUp = requestNumber > 0)
        val call = transport.newCall(
            SabrRequestEncoder.url(streamingUrl, descriptor.cpn, requestNumber),
            SabrRequestEncoder.headers(), body)
        activeCall.set(call)
        if (closed) call.cancel()

        val controls = SabrResponseControls()
        var segments = 0
        val startedNs = System.nanoTime()
        var responseBytes = 0L
        try {
            val response = call.execute()
            // Counted once the server has seen it, so a retry never reuses a number.
            requestNumber++
            response.use {
                if (it.status != 200) throw SabrHttpException(it.status)
                if (it.contentType?.lowercase()?.contains("application/vnd.yt-ump") != true) {
                    throw SabrProtocolException("Expected a UMP response")
                }
                val counting = CountingInputStream(it.body)
                SabrSegmentReader.read(counting, spool, controls::accept) { segment ->
                    segments++
                    onSegment(segment)
                }
                responseBytes = counting.count
            }
        } catch (error: IOException) {
            if (closed || Thread.currentThread().isInterrupted) {
                throw SabrCancelledException().apply { initCause(error) }
            }
            if (error is SabrIncompleteMediaException || error is EOFException) {
                if (++incompleteResponses >= limits.maxIncompleteResponses) throw error
                return SabrExchange.Incomplete(incompleteResponses)
            }
            throw error
        } finally {
            activeCall.compareAndSet(call, null)
        }
        incompleteResponses = 0
        updateBandwidth(responseBytes, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNs))
        return apply(controls, segments)
    }

    private fun apply(controls: SabrResponseControls, segments: Int): SabrExchange.Completed {
        val backoff = controls.backoffMs ?: 0
        if (backoff > limits.maxBackoffMs) throw SabrProtocolException("SABR backoff exceeds limit")
        backoffDeadlineNs = if (backoff > 0) System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(backoff.toLong()) else 0L

        if (controls.attestationPending && segments == 0) {
            if (++attestationPendingResponses >= limits.maxAttestationPendingResponses) {
                throw SabrAttestationException("SABR attestation stayed pending without media")
            }
        } else {
            attestationPendingResponses = 0
        }

        controls.playbackCookie?.let { playbackCookie = it }
        controls.contextUpdates.forEach { update ->
            if (update.keepExisting && update.type in contexts) return@forEach
            if (update.type !in contexts && contexts.size >= limits.maxContexts) {
                throw SabrProtocolException("Too many SABR contexts")
            }
            contexts[update.type] = update.value
            if (update.sendByDefault) activeContextTypes += update.type
        }
        activeContextTypes += controls.startSending
        activeContextTypes -= controls.stopSending.toSet()
        controls.discard.forEach { contexts.remove(it); activeContextTypes.remove(it) }
        // A start request for a type we never received is not something we can send.
        activeContextTypes.retainAll(contexts.keys)

        var redirected = false
        controls.redirectUrl?.let {
            if (++redirects > limits.maxRedirects) throw SabrProtocolException("SABR redirect limit exceeded")
            SabrRequestEncoder.validateStreamingUrl(it)
            streamingUrl = it
            redirected = true
        }

        if (controls.hasError) throw SabrServerErrorException(controls.errorType, controls.errorCode ?: 0)
        if (controls.attestationRequired) throw SabrAttestationException("SABR attestation required")
        if (controls.reloadRequested) throw SabrReloadException("SABR requested a player response reload")
        // Invariant 4: live and post-live DVR stay on HLS.
        if (controls.live) throw SabrProtocolException("SABR live metadata on a VOD session")

        if (segments > 0) redirects = 0
        return SabrExchange.Completed(segments, backoff, redirected)
    }

    private fun updateBandwidth(bytes: Long, elapsedMs: Long) {
        if (bytes <= 0 || elapsedMs <= 0) return
        val sample = bytes * 8_000L / elapsedMs
        bandwidthEstimateBps = if (bandwidthEstimateBps <= 0) sample else (bandwidthEstimateBps * 3 + sample) / 4
    }

    private fun withinBudget(episodeEndNs: Long, waitMs: Long, name: String): Long {
        val now = System.nanoTime()
        if (episodeEndNs == 0L) return now + TimeUnit.MILLISECONDS.toNanos(limits.maxContinuousWaitMs)
        if (TimeUnit.MILLISECONDS.toNanos(waitMs) > episodeEndNs - now) {
            throw IOException("SABR continuous $name budget exceeded")
        }
        return episodeEndNs
    }

    private fun sleep(ms: Long) {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(ms)
        waitLock.withLock {
            while (!closed) {
                val left = deadline - System.nanoTime()
                if (left <= 0) break
                try {
                    wake.awaitNanos(left)
                } catch (error: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw SabrCancelledException("SABR wait interrupted").apply { initCause(error) }
                }
            }
        }
        ensureOpen()
    }

    private fun ensureOpen() {
        if (closed) throw SabrCancelledException()
        if (Thread.currentThread().isInterrupted) throw SabrCancelledException("SABR thread interrupted")
    }

    override fun close() {
        closed = true
        activeCall.getAndSet(null)?.cancel()
        waitLock.withLock { wake.signalAll() }
    }

    override fun toString() = "SabrSession(requests=$requestNumber, closed=$closed)"

    private class CountingInputStream(input: InputStream) : FilterInputStream(input) {
        var count = 0L
            private set

        override fun read(): Int = super.read().also { if (it >= 0) count++ }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            super.read(buffer, offset, length).also { if (it > 0) count += it }
    }
}
