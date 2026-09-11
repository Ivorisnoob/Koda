package com.ivor.ivormusic.data

import com.ivor.ivormusic.util.KLog

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Collections
import java.util.LinkedHashMap

/**
 * Fetches the best available lyric format without coupling the player to a
 * particular remote service.
 *
 * Lyrics that travel with a device file are tried before anything on the
 * network and win outright: they are what the user deliberately put next to
 * that track, they are the only ones that work offline, and matching a local
 * file to a provider by title and artist is exactly the guess that fails on
 * the thinly-tagged rips these are usually paired with. Only then do the
 * enabled providers run concurrently; user order and timing preference select the result.
 *
 * Local-only mode turns off the providers, not the local read - see
 * [fetchLyrics]'s `allowRemote`.
 */
class LyricsRepository internal constructor(
    context: Context? = null,
    private val http: LyricsHttpClient = LyricsHttpClient(),
    private val wordProviders: List<RemoteLyricsProvider> = defaultWordLyricsProviders(http),
    private val fallbackProviders: List<RemoteLyricsProvider> = defaultFallbackLyricsProviders(http),
    private val localLyricsSource: LocalLyricsSource =
        context?.let(LocalLyricsSource::forContext) ?: LocalLyricsSource(),
    private val configurationSource: () -> LyricsConfiguration =
        context?.let { ctx ->
            val preferences = ThemePreferences(ctx)
            preferences::readLyricsConfiguration
        }
            ?: { LyricsConfiguration() }
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

    suspend fun fetchLyrics(song: Song, allowRemote: Boolean = true): LyricsResult = withContext(Dispatchers.IO) {
        localLyricsSource.find(song)?.let { return@withContext it }
        val configuration = configurationSource().normalized()
        if (!allowRemote || !configuration.remoteEnabled || song.title.isBlank()) return@withContext LyricsResult.NotFound

        val request = LyricsRequest(
            songId = song.id,
            title = song.title.trim(),
            artist = song.artist.trim(),
            album = song.album.trim(),
            durationMs = song.duration.coerceAtLeast(0L)
        )
        val key = request.cacheKey() + "|" + configuration.toString()
        cache[key]?.let { return@withContext it }

        try {
            val providers = (wordProviders + fallbackProviders)
                .filter { it.name !in configuration.disabledProviders }
                .sortedBy { configuration.providerOrder.indexOf(it.name).takeIf { rank -> rank >= 0 } ?: it.priority }
            val stage = fetchStage(providers, request, configuration)
            val best = if (configuration.preferSynced) {
                stage.candidates.maxByOrNull { it.parsed.syncType.quality }
            } else stage.candidates.firstOrNull()

            if (best != null) {
                return@withContext best.toSuccess().also { cache[key] = it }
            }

            if (stage.hadFailure) {
                LyricsResult.Error("Lyrics services are temporarily unavailable")
            } else {
                LyricsResult.NotFound
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            KLog.e(TAG, "Unexpected lyrics failure", e)
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
        request: LyricsRequest,
        configuration: LyricsConfiguration
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
            var winner: ProviderCandidate? = null
            while (running.isNotEmpty() && winner == null) {
                val (completed, attempt) = select<Pair<RunningProvider, ProviderAttempt>> {
                    running.forEach { item -> item.deferred.onAwait { item to it } }
                }
                running.remove(completed)
                attempt.error?.let { error ->
                    errorCount++
                    KLog.w(TAG, "${completed.provider.name} lyrics request failed", error)
                }
                attempt.parsed
                    ?.takeIf { it.lines.isNotEmpty() && (configuration.allowPlainText || it.syncType != LyricsSyncType.PLAIN) }
                    ?.let { candidates += ProviderCandidate(completed.provider, it) }
                // Completion speed must not override priority. A top-quality result
                // can win once every higher-ranked provider has finished.
                val best = candidates.sortedBy { providers.indexOf(it.provider) }.let { ordered ->
                    if (configuration.preferSynced) ordered.maxByOrNull { it.parsed.syncType.quality }
                    else ordered.firstOrNull()
                }
                if (best != null && (!configuration.preferSynced || best.parsed.syncType == LyricsSyncType.WORD) &&
                    running.none { providers.indexOf(it.provider) < providers.indexOf(best.provider) }) {
                    winner = best
                }
            }
            winner
        }
        candidates.sortBy { providers.indexOf(it.provider) }

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
