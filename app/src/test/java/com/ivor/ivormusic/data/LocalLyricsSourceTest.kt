package com.ivor.ivormusic.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlinx.coroutines.runBlocking

class LocalLyricsSourceTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun matchingLrcSidecarWinsOverEmbeddedLyrics() {
        val audio = temporaryFolder.newFile("Track.mp3")
        temporaryFolder.newFile("Track.lrc").writeText("[00:01.00]Sidecar line")
        val source = LocalLyricsSource { "Embedded line" }

        val result = source.find(localSong(audio))!!

        assertEquals("Local LRC", result.provider)
        assertEquals(LyricsSyncType.LINE, result.syncType)
        assertEquals("Sidecar line", result.lines.single().text)
    }

    @Test
    fun matchingTtmlSidecarIsParsed() {
        val audio = temporaryFolder.newFile("Track.flac")
        temporaryFolder.newFile("Track.ttml").writeText(
            """<tt xmlns="http://www.w3.org/ns/ttml"><body><p begin="1s">TTML line</p></body></tt>"""
        )

        val result = LocalLyricsSource { null }.find(localSong(audio))!!

        assertEquals("Local TTML", result.provider)
        assertEquals("TTML line", result.lines.single().text)
    }

    @Test
    fun embeddedLyricsAreUsedWithoutSidecar() {
        val audio = temporaryFolder.newFile("Track.ogg")

        val result = LocalLyricsSource { "First line\nSecond line" }.find(localSong(audio))!!

        assertEquals("Embedded lyrics", result.provider)
        assertEquals(LyricsSyncType.PLAIN, result.syncType)
        assertEquals(listOf("First line", "Second line"), result.lines.map { it.text })
    }

    @Test
    fun utf16SidecarsAreDecodedRatherThanShownAsGarbage() {
        val audio = temporaryFolder.newFile("Track.mp3")
        // What a Windows editor writes when asked for "Unicode": UTF-16LE
        // behind a BOM. Read as UTF-8 this is replacement characters that
        // still parse as plain text, so the failure is a screenful of garbage
        // rather than a miss, and it stops the search before the fallbacks.
        temporaryFolder.newFile("Track.lrc")
            .writeBytes(byteArrayOf(0xFF.toByte(), 0xFE.toByte()) +
                "[00:01.00]Encoded line".toByteArray(Charsets.UTF_16LE))

        val result = LocalLyricsSource { null }.find(localSong(audio))!!

        assertEquals("Encoded line", result.lines.single().text)
        assertEquals(LyricsSyncType.LINE, result.syncType)
    }

    @Test
    fun unparseableSidecarFallsThroughToEmbeddedLyrics() {
        val audio = temporaryFolder.newFile("Track.mp3")
        temporaryFolder.newFile("Track.ttml").writeText("<tt><body></body></tt>")

        val result = LocalLyricsSource { "Embedded line" }.find(localSong(audio))!!

        assertEquals("Embedded lyrics", result.provider)
        assertEquals("Embedded line", result.lines.single().text)
    }

    @Test
    fun youtubeSongsNeverReadLocalFiles() {
        val audio = temporaryFolder.newFile("Track.mp3")
        var embeddedRead = false
        val source = LocalLyricsSource {
            embeddedRead = true
            "Lyrics"
        }

        val result = source.find(localSong(audio).copy(source = SongSource.YOUTUBE))

        assertNull(result)
        assertEquals(false, embeddedRead)
    }

    @Test
    fun repositoryReturnsLocalLyricsBeforeCallingRemoteProviders() = runBlocking {
        val audio = temporaryFolder.newFile("Track.mp3")
        temporaryFolder.newFile("Track.lrc").writeText("[00:01.00]Stored here")
        var remoteCalled = false
        val remote = recordingProvider { remoteCalled = true }
        val repository = LyricsRepository(
            wordProviders = listOf(remote),
            fallbackProviders = emptyList()
        )

        val result = repository.fetchLyrics(localSong(audio)) as LyricsResult.Success

        assertEquals("Stored here", result.lines.single().text)
        assertEquals(false, remoteCalled)
    }

    @Test
    fun localOnlyRequestDoesNotCallRemoteProvidersWhenLocalLyricsAreMissing() = runBlocking {
        val audio = temporaryFolder.newFile("Track.mp3")
        var remoteCalled = false
        val remote = recordingProvider { remoteCalled = true }
        val repository = LyricsRepository(
            wordProviders = listOf(remote),
            fallbackProviders = listOf(remote),
            localLyricsSource = LocalLyricsSource { null }
        )

        val result = repository.fetchLyrics(localSong(audio), allowRemote = false)

        assertEquals(LyricsResult.NotFound, result)
        assertEquals(false, remoteCalled)
    }

    private fun recordingProvider(onFetch: () -> Unit) = object : RemoteLyricsProvider {
        override val name = "test"
        override val priority = 0

        override suspend fun fetch(request: LyricsRequest): ParsedLyrics? {
            onFetch()
            return ParsedLyrics(listOf(LrcLine(0L, "Remote")), LyricsSyncType.LINE)
        }
    }

    private fun localSong(file: java.io.File) = Song(
        id = "1",
        title = "Track",
        artist = "Artist",
        album = "Album",
        duration = 120_000L,
        source = SongSource.LOCAL,
        filePath = file.absolutePath
    )
}
