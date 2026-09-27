package com.ivor.ivormusic.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlainTextLinksTest {

    private fun linked(text: String): List<Pair<String, String>> {
        val rich = linkifyPlainText(text)
        return rich.links.map { link ->
            val url = (link.target as RichLinkTarget.Url).url
            rich.text.substring(link.start, link.endExclusive) to url
        }
    }

    @Test
    fun `web addresses need a scheme, www or a YouTube host`() {
        assertEquals(
            listOf(
                "https://lttstore.com" to "https://lttstore.com",
                "www.linusmediagroup.com" to "https://www.linusmediagroup.com",
                "youtube.com/@ShortCircuit" to "https://youtube.com/@ShortCircuit"
            ),
            linked("Merch: https://lttstore.com, site www.linusmediagroup.com and youtube.com/@ShortCircuit.")
        )
    }

    @Test
    fun `prose with dots is left alone`() {
        assertTrue(linked("Version 2.0 is out, e.g. today. Ask Dr. Who").isEmpty())
    }

    @Test
    fun `an email is one mail link, not a handle or a url`() {
        // From the MKBHD About panel, September 2026.
        assertEquals(
            listOf("business@MKBHD.com" to "mailto:business@MKBHD.com"),
            linked("MKBHD: Quality Tech Videos\n\nbusiness@MKBHD.com\n\nNYC")
        )
    }

    @Test
    fun `a handle opens the channel and drops trailing punctuation`() {
        assertEquals(
            listOf("@LinusTechTips" to "https://www.youtube.com/@LinusTechTips"),
            linked("Main channel: @LinusTechTips.")
        )
    }

    @Test
    fun `links keep their offsets after astral characters`() {
        val text = "🎵 new video https://youtu.be/dQw4w9WgXcQ"
        val rich = linkifyPlainText(text)
        val link = rich.links.single()
        assertEquals("https://youtu.be/dQw4w9WgXcQ", text.substring(link.start, link.endExclusive))
    }

    @Test
    fun `blank text has no links`() {
        assertTrue(linkifyPlainText("   ").links.isEmpty())
    }
}
