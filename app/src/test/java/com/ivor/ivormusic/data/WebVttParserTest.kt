package com.ivor.ivormusic.data

import org.junit.Assert.assertEquals
import org.junit.Test

class WebVttParserTest {

    @Test
    fun parsesWebVttAndStripsInlineMarkup() {
        val cues = WebVttParser.parse(
            """
            WEBVTT

            00:00:01.000 --> 00:00:03.500
            <b>Hello</b> &amp; welcome
            """.trimIndent()
        )

        assertEquals(1, cues.size)
        assertEquals(1_000L, cues.single().startMs)
        assertEquals("Hello & welcome", cues.single().text)
    }

    @Test
    fun parsesSrtCommaTimestamps() {
        val cues = WebVttParser.parse(
            """
            1
            00:01:02,250 --> 00:01:04,000
            First line
            Second line
            """.trimIndent()
        )

        assertEquals(62_250L, cues.single().startMs)
        assertEquals("First line\nSecond line", cues.single().text)
    }
}
