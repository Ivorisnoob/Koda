package com.ivor.ivormusic.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsConfigurationTest {
    private val song = Song("test", "Track", "Artist", "Album", 180_000, source = SongSource.YOUTUBE)

    @Test fun normalizesUnknownAndDuplicateProvidersWithoutLosingNewSources() {
        val config = LyricsConfiguration(listOf("LRCLIB", "unknown", "LRCLIB"), setOf("unknown", "KuGou")).normalized()
        assertEquals("LRCLIB", config.providerOrder.first())
        assertEquals(7, config.providerOrder.size)
        assertEquals(setOf("KuGou"), config.disabledProviders)
    }

    @Test fun priorityBeatsCompletionSpeedAndCacheTracksPreferenceChanges() = runBlocking {
        var config = LyricsConfiguration()
        val repository = repository({ config },
            provider("YouLyPlus", LyricsSyncType.WORD, 60),
            provider("LRCLIB", LyricsSyncType.WORD))
        assertEquals("YouLyPlus", (repository.fetchLyrics(song) as LyricsResult.Success).provider)
        config = config.copy(providerOrder = listOf("LRCLIB"))
        assertEquals("LRCLIB", (repository.fetchLyrics(song) as LyricsResult.Success).provider)
        config = config.copy(disabledProviders = setOf("LRCLIB"))
        assertEquals("YouLyPlus", (repository.fetchLyrics(song) as LyricsResult.Success).provider)
    }

    @Test fun timingPreferenceCanBeOverriddenByStrictOrder() = runBlocking {
        var config = LyricsConfiguration()
        val repository = repository({ config }, provider("YouLyPlus", LyricsSyncType.PLAIN), provider("LRCLIB", LyricsSyncType.LINE))
        assertEquals("LRCLIB", (repository.fetchLyrics(song) as LyricsResult.Success).provider)
        config = config.copy(preferSynced = false)
        assertEquals("YouLyPlus", (repository.fetchLyrics(song) as LyricsResult.Success).provider)
        config = config.copy(allowPlainText = false)
        assertEquals("LRCLIB", (repository.fetchLyrics(song) as LyricsResult.Success).provider)
    }

    @Test fun disabledProvidersAndRemoteSwitchNeverCallTheSource() = runBlocking {
        var calls = 0
        val source = object : RemoteLyricsProvider {
            override val name = "YouLyPlus"
            override val priority = 0
            override suspend fun fetch(request: LyricsRequest): ParsedLyrics? { calls++; return null }
        }
        var config = LyricsConfiguration(disabledProviders = setOf("YouLyPlus"))
        val repository = repository({ config }, source)
        assertEquals(LyricsResult.NotFound, repository.fetchLyrics(song))
        config = LyricsConfiguration(remoteEnabled = false)
        assertEquals(LyricsResult.NotFound, repository.fetchLyrics(song))
        assertEquals(0, calls)
    }

    @Test fun sourceFailureStillAllowsFallback() = runBlocking {
        val failing = object : RemoteLyricsProvider {
            override val name = "YouLyPlus"
            override val priority = 0
            override suspend fun fetch(request: LyricsRequest): ParsedLyrics? = error("Unavailable")
        }
        val repository = repository({ LyricsConfiguration() }, failing, provider("LRCLIB", LyricsSyncType.LINE))
        assertEquals("LRCLIB", (repository.fetchLyrics(song) as LyricsResult.Success).provider)
    }

    private fun repository(configuration: () -> LyricsConfiguration, vararg providers: RemoteLyricsProvider) =
        LyricsRepository(wordProviders = providers.toList(), fallbackProviders = emptyList(),
            localLyricsSource = LocalLyricsSource { null }, configurationSource = configuration)

    private fun provider(id: String, type: LyricsSyncType, waitMs: Long = 0) = object : RemoteLyricsProvider {
        override val name = id
        override val priority = 0
        override suspend fun fetch(request: LyricsRequest): ParsedLyrics {
            delay(waitMs)
            return ParsedLyrics(listOf(LrcLine(0, "Lyrics")), type)
        }
    }
}
