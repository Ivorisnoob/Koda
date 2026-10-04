package com.ivor.ivormusic.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseHighlightsTest {

    @Test
    fun `bundled file is read as plain bullets`() {
        val text = "- First thing\n\n* **Second** thing\nnot a bullet\n-   Third thing  \n"
        assertEquals(
            listOf("First thing", "Second thing", "Third thing"),
            ReleaseHighlights.parseBullets(text)
        )
    }

    @Test
    fun `at most six highlights are kept`() {
        val text = (1..9).joinToString("\n") { "- Item $it" }
        assertEquals(ReleaseHighlights.MAX_HIGHLIGHTS, ReleaseHighlights.parseBullets(text).size)
    }

    @Test
    fun `release body gives only the Highlights section`() {
        val body = """
            Koda 5.2

            ## Highlights
            - Posts in your feeds
            - Endless subscriptions

            ## Everything else
            - Fixed a crash
        """.trimIndent()
        assertEquals(
            listOf("Posts in your feeds", "Endless subscriptions"),
            ReleaseHighlights.fromReleaseBody(body)
        )
    }

    @Test
    fun `heading match ignores case, level and decoration`() {
        assertEquals(listOf("One"), ReleaseHighlights.fromReleaseBody("### highlights:\n- One"))
        assertEquals(listOf("One"), ReleaseHighlights.fromReleaseBody("**Highlights**\n- One"))
    }

    @Test
    fun `release without a Highlights section has none`() {
        assertTrue(ReleaseHighlights.fromReleaseBody("## What's Changed\n- Fixed a crash").isEmpty())
    }

    @Test
    fun `debug suffix is not part of the release version`() {
        assertEquals("5.2", ReleaseHighlights.releaseVersion("5.2-debug"))
        assertEquals("5.2", ReleaseHighlights.releaseVersion("5.2"))
    }

    @Test
    fun `a release never shown is prompted for`() {
        assertTrue(UpdatePromptStore.shouldPrompt(null, 0L, "5.2", nowMs = 1_000L))
        assertTrue(UpdatePromptStore.shouldPrompt("5.1", 900L, "5.2", nowMs = 1_000L))
    }

    @Test
    fun `later is quiet for two days and then asks again`() {
        val shownAt = 10_000L
        val almost = shownAt + UpdatePromptStore.REPROMPT_AFTER_MS - 1
        assertFalse(UpdatePromptStore.shouldPrompt("5.2", shownAt, "5.2", nowMs = almost))
        assertTrue(UpdatePromptStore.shouldPrompt("5.2", shownAt, "5.2", nowMs = almost + 1))
    }

    @Test
    fun `a clock set backwards does not silence the prompt for good`() {
        assertTrue(UpdatePromptStore.shouldPrompt("5.2", 10_000L, "5.2", nowMs = 5_000L))
    }
}
