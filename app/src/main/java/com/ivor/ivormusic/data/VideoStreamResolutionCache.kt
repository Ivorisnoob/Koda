package com.ivor.ivormusic.data

import java.util.LinkedHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * One short-lived stream resolution shared by video playback, Shorts and the
 * download sheet. A NewPipe fetchPage is the expensive part of opening a video;
 * resolving the same id again on another surface wastes both time and radio.
 */
internal object VideoStreamResolutionCache {
    private const val MAX_ENTRIES = 16
    private const val FALLBACK_TTL_MS = 15 * 60 * 1000L
    private const val EXPIRY_SAFETY_MS = 5 * 60 * 1000L

    private data class Entry(
        val result: VideoStreamResult,
        val expiresAtMs: Long,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val epoch = AtomicLong(0L)
    private val lock = Any()
    private val active = mutableMapOf<String, Deferred<VideoStreamResult>>()
    private val cached = object : LinkedHashMap<String, Entry>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, Entry>,
        ): Boolean = size > MAX_ENTRIES
    }

    suspend fun getOrResolve(
        videoId: String,
        resolver: suspend () -> VideoStreamResult,
    ): VideoStreamResult {
        var shouldStart = false
        val deferred = synchronized(lock) {
            val now = System.currentTimeMillis()
            cached[videoId]?.let { entry ->
                if (entry.expiresAtMs > now) {
                    return entry.result
                }
                cached.remove(videoId)
            }
            active[videoId] ?: run {
                val startEpoch = epoch.get()
                scope.async(start = CoroutineStart.LAZY) {
                    val result = resolver()
                    currentCoroutineContext().ensureActive()
                    val expiresAtMs = streamResultExpiryMs(
                        result = result,
                        nowMs = System.currentTimeMillis(),
                        fallbackTtlMs = FALLBACK_TTL_MS,
                        expirySafetyMs = EXPIRY_SAFETY_MS,
                    )
                    if (result.qualities.isNotEmpty() &&
                        expiresAtMs > System.currentTimeMillis() &&
                        epoch.get() == startEpoch
                    ) {
                        synchronized(lock) {
                            if (epoch.get() == startEpoch) {
                                cached[videoId] = Entry(result, expiresAtMs)
                            }
                        }
                    }
                    result
                }.also { created ->
                    active[videoId] = created
                    created.invokeOnCompletion {
                        synchronized(lock) {
                            if (active[videoId] === created) active.remove(videoId)
                        }
                    }
                    shouldStart = true
                }
            }
        }
        if (shouldStart) deferred.start()
        return deferred.await()
    }

    fun invalidate(videoId: String) {
        val activeResolution = synchronized(lock) {
            cached.remove(videoId)
            active.remove(videoId)
        }
        activeResolution?.cancel()
    }

    fun clear() {
        val activeResolutions = synchronized(lock) {
            epoch.incrementAndGet()
            cached.clear()
            active.values.toList().also { active.clear() }
        }
        activeResolutions.forEach { it.cancel() }
    }
}

internal fun streamResultExpiryMs(
    result: VideoStreamResult,
    nowMs: Long,
    fallbackTtlMs: Long,
    expirySafetyMs: Long,
): Long {
    val urls = result.qualities.flatMap { quality ->
        listOfNotNull(quality.url.takeIf(String::isNotBlank), quality.audioUrl)
    }
    if (urls.isEmpty()) return nowMs
    return urls.minOf { url ->
        val expireSeconds = url.toHttpUrlOrNull()
            ?.queryParameter("expire")
            ?.toLongOrNull()
        if (expireSeconds != null && expireSeconds > 0L) {
            expireSeconds * 1000L - expirySafetyMs
        } else {
            nowMs + fallbackTtlMs
        }
    }
}
