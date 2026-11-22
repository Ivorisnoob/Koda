package com.ivor.ivormusic.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** A picked .srt goes through the WebVTT reader; this pins that it reads one. */
class SrtThroughVttParserTest {
    @Test
    fun `reads a SubRip file`() {
        val srt = "1\r\n00:00:01,000 --> 00:00:03,500\r\n<i>Hello</i> there\r\n\r\n" +
            "2\r\n00:00:04,000 --> 00:01:02,250\r\nTwo\r\nlines\r\n"
        assertEquals(
            listOf(VttCue(1_000, 3_500, "Hello there"), VttCue(4_000, 62_250, "Two\nlines")),
            WebVttParser.parse(srt),
        )
    }
}
