package com.ivor.ivormusic.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsParserTest {
    @Test
    fun enhancedLrcPreservesTextAndWordTiming() {
        val parsed = LyricsParser.parse(
            """
            [00:01.00]<00:01.00>Hello <00:01.50>world!
            [00:03.00]Next line
            """.trimIndent()
        )!!

        assertEquals(LyricsSyncType.WORD, parsed.syncType)
        assertEquals("Hello world!", parsed.lines.first().text)
        assertEquals(listOf("Hello ", "world!"), parsed.lines.first().contentSpans.map { it.text })
        assertEquals(500L, parsed.lines.first().contentSpans.first().durationMs)
        assertTrue(parsed.lines.first().contentSpans.last().durationMs > 0L)
    }

    @Test
    fun standardLrcKeepsEveryRepeatedTimestamp() {
        val parsed = LyricsParser.parse("[00:01.00][00:04.25]Same line")!!

        assertEquals(LyricsSyncType.LINE, parsed.syncType)
        assertEquals(listOf(1_000L, 4_250L), parsed.lines.map { it.timeMs })
        assertTrue(parsed.lines.all { it.contentSpans.isEmpty() })
    }

    @Test
    fun qrcProducesWordDurations() {
        val parsed = LyricsParser.parse("[1000,1200]Hello(1000,500) world(1500,700)")!!

        assertEquals(LyricsSyncType.WORD, parsed.syncType)
        assertEquals("Hello world", parsed.lines.single().text)
        assertEquals(listOf(500L, 700L), parsed.lines.single().contentSpans.map { it.durationMs })
    }

    @Test
    fun ttmlRepairsLatinWordSpacingAndReadsTiming() {
        val parsed = LyricsParser.parse(
            """
            <tt xmlns="http://www.w3.org/ns/ttml">
              <body><div><p begin="1s" end="3s">
                <span begin="1s" end="2s">Hello</span><span begin="2s" end="3s">world</span>
              </p></div></body>
            </tt>
            """.trimIndent()
        )!!

        assertEquals(LyricsSyncType.WORD, parsed.syncType)
        assertEquals("Hello world", parsed.lines.single().text)
        assertEquals(listOf(1_000L, 2_000L), parsed.lines.single().contentSpans.map { it.timeMs })
        assertEquals(listOf(1_000L, 1_000L), parsed.lines.single().contentSpans.map { it.durationMs })
    }

    @Test
    fun plainTextRemainsUnsynced() {
        val parsed = LyricsParser.parse("First line\nSecond line")!!

        assertEquals(LyricsSyncType.PLAIN, parsed.syncType)
        assertEquals(listOf(-1L, -1L), parsed.lines.map { it.timeMs })
    }
}
