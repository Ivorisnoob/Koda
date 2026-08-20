package com.ivor.ivormusic.data

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Collections
import java.util.LinkedHashMap

/**
 * Fetches the best available lyric format without coupling the player to a
 * particular remote service. Word-timed providers are tried first, followed
 * by line-synced and plain-text fallbacks.
 */
class LyricsRepository internal constructor(
    private val http: LyricsHttpClient = LyricsHttpClient(),
    private val wordProviders: List<RemoteLyricsProvider> = defaultWordLyricsProviders(http),
    private val fallbackProviders: List<RemoteLyricsProvider> = defaultFallbackLyricsProviders(http)
) {
    private companion object {
        const val TAG = "LyricsRepository"
        const val STAGE_TIMEOUT_MS = 9_500L
        const val MAX_CACHE_ENTRIES = 64
    }

    private val cache = Collections.synchronizedMap(
        object : LinkedHashMap<String, LyricsResult.Success>(MAX_CACHE_ENTRIES, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<String, LyricsResult.Success>?
            ): Boolean = size > MAX_CACHE_ENTRIES
        }
    )

    suspend fun fetchLyrics(
        songId: String,
        title: String,
        artist: String,
        album: String = "",
        durationMs: Long
    ): LyricsResult = withContext(Dispatchers.IO) {
        if (title.isBlank()) return@withContext LyricsResult.NotFound

        val request = LyricsRequest(
            songId = songId,
            title = title.trim(),
            artist = artist.trim(),
            album = album.trim(),
            durationMs = durationMs.coerceAtLeast(0L)
        )
        val key = request.cacheKey()
        cache[key]?.let { return@withContext it }

        try {
            val wordStage = fetchStage(wordProviders, request)
            wordStage.earlyWordResult?.let { candidate ->
                return@withContext candidate.toSuccess().also { cache[key] = it }
            }

            val fallbackStage = fetchStage(fallbackProviders, request)
            val best = (wordStage.candidates + fallbackStage.candidates)
                .sortedWith(
                    compareByDescending<ProviderCandidate> { it.parsed.syncType.quality }
                        .thenBy { it.provider.priority }
                )
                .firstOrNull()

            if (best != null) {
                return@withContext best.toSuccess().also { cache[key] = it }
            }

            if (wordStage.hadFailure || fallbackStage.hadFailure) {
                LyricsResult.Error("Lyrics services are temporarily unavailable")
            } else {
                LyricsResult.NotFound
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected lyrics failure", e)
            LyricsResult.Error("Lyrics services are temporarily unavailable")
        }
    }

    /** Kept public for callers that need to parse imported LRC text. */
    fun parseLrc(lrcContent: String): List<LrcLine> =
        LyricsParser.parseLrc(lrcContent)?.lines.orEmpty()

    fun clearCache() {
        cache.clear()
    }

    private suspend fun fetchStage(
        providers: List<RemoteLyricsProvider>,
        request: LyricsRequest
    ): ProviderStageResult = supervisorScope {
        if (providers.isEmpty()) return@supervisorScope ProviderStageResult()

        val candidates = mutableListOf<ProviderCandidate>()
        var errorCount = 0
        val running = providers.map { provider ->
            RunningProvider(
                provider = provider,
                deferred = async {
                    try {
                        ProviderAttempt(parsed = provider.fetch(request))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        ProviderAttempt(error = e)
                    }
                }
            )
        }.toMutableList()

        val earlyWordResult = withTimeoutOrNull(STAGE_TIMEOUT_MS) {
            var wordCandidate: ProviderCandidate? = null
            while (running.isNotEmpty() && wordCandidate == null) {
                val (completed, attempt) = select<Pair<RunningProvider, ProviderAttempt>> {
                    running.forEach { item ->
                        item.deferred.onAwait { item to it }
                    }
                }
                running.remove(completed)

                attempt.error?.let { error ->
                    errorCount++
                    Log.w(TAG, "${completed.provider.name} lyrics request failed", error)
                }
                attempt.parsed
                    ?.takeIf { it.lines.isNotEmpty() }
                    ?.let { parsed ->
                        val candidate = ProviderCandidate(completed.provider, parsed)
                        candidates += candidate
                        if (parsed.syncType == LyricsSyncType.WORD) wordCandidate = candidate
                    }
            }
            wordCandidate
        }

        val timedOut = earlyWordResult == null && running.isNotEmpty()
        running.forEach { it.deferred.cancel() }

        ProviderStageResult(
            candidates = candidates,
            earlyWordResult = earlyWordResult,
            hadFailure = timedOut || errorCount > 0
        )
    }
}

private data class ProviderAttempt(
    val parsed: ParsedLyrics? = null,
    val error: Exception? = null
)

private data class RunningProvider(
    val provider: RemoteLyricsProvider,
    val deferred: Deferred<ProviderAttempt>
)

private data class ProviderCandidate(
    val provider: RemoteLyricsProvider,
    val parsed: ParsedLyrics
) {
    fun toSuccess() = LyricsResult.Success(
        lines = parsed.lines,
        provider = provider.name,
        syncType = parsed.syncType
    )
}

private data class ProviderStageResult(
    val candidates: List<ProviderCandidate> = emptyList(),
    val earlyWordResult: ProviderCandidate? = null,
    val hadFailure: Boolean = false
)

private fun LyricsRequest.cacheKey(): String = buildString {
    append(songId.trim())
    append('|')
    append(title.lowercase())
    append('|')
    append(artist.lowercase())
    append('|')
    append(album.lowercase())
    append('|')
    append(durationSeconds)
}
