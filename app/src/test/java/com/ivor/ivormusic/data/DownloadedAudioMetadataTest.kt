package com.ivor.ivormusic.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DownloadedAudioMetadataTest {

    @Test
    fun plainLyricsRemainPlainAndCollapseEmbeddedNewlines() {
        val lyrics = success(
            LyricsSyncType.PLAIN,
            LrcLine(-1L, " First\nline "),
            LrcLine(-1L, ""),
            LrcLine(-1L, "Second line")
        )

        val serialized = lyrics.toDownloadLyrics()
        val parsed = LyricsParser.parse(serialized)

        assertEquals("First line\nSecond line", serialized)
        assertNotNull(parsed)
        assertEquals(LyricsSyncType.PLAIN, parsed!!.syncType)
        assertEquals(listOf("First line", "Second line"), parsed.lines.map { it.text })
    }

    @Test
    fun lineSyncedLyricsRoundTripThroughLrc() {
        val lyrics = success(
            LyricsSyncType.LINE,
            LrcLine(1_230L, "Opening line"),
            LrcLine(3_661_990L, "Past an hour")
        )

        val serialized = lyrics.toDownloadLyrics()
        val parsed = LyricsParser.parse(serialized)

        assertEquals("[00:01.23]Opening line\n[61:01.99]Past an hour", serialized)
        assertNotNull(parsed)
        assertEquals(LyricsSyncType.LINE, parsed!!.syncType)
        assertEquals(listOf(1_230L, 3_661_990L), parsed.lines.map { it.timeMs })
        assertEquals(listOf("Opening line", "Past an hour"), parsed.lines.map { it.text })
    }

    @Test
    fun wordSyncedLyricsRoundTripThroughEnhancedLrc() {
        val lyrics = success(
            LyricsSyncType.WORD,
            LrcLine(
                timeMs = 10_000L,
                text = "Hello world",
                contentSpans = listOf(
                    LrcContentSpan(10_000L, "Hello ", 500L),
                    LrcContentSpan(10_500L, "world", 500L)
                )
            )
        )

        val serialized = lyrics.toDownloadLyrics()
        val parsed = LyricsParser.parse(serialized)

        assertEquals("[00:10.00]<00:10.00>Hello <00:10.50>world", serialized)
        assertNotNull(parsed)
        assertEquals(LyricsSyncType.WORD, parsed!!.syncType)
        assertEquals("Hello world", parsed.lines.single().text)
        assertEquals(listOf(10_000L, 10_500L), parsed.lines.single().contentSpans.map { it.timeMs })
        assertEquals(listOf("Hello ", "world"), parsed.lines.single().contentSpans.map { it.text })
    }

    private fun success(syncType: LyricsSyncType, vararg lines: LrcLine) =
        LyricsResult.Success(lines.toList(), "test", syncType)
}
