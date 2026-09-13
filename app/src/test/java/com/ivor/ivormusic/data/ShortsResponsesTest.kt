package com.ivor.ivormusic.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

/** Reduced live September 2026 responses; no account, tracking or playback URL data. */
class ShortsResponsesTest {
    private fun fixture(name: String): JSONObject = javaClass.getResourceAsStream("/shorts/$name.json")!!
        .bufferedReader().use { JSONObject(it.readText()) }

    @Test fun `seed follows replacement endpoint and retains account continuation`() {
        val page = parseShortsSeed(fixture("seed"))
        assertEquals("XKnmhvNpIvw", page.items.single().videoId)
        assertTrue(page.items.single().title.startsWith("How To Extract Your DNA At Home"))
        assertEquals("21,003,010 views", page.items.single().viewCount)
        assertEquals("seed-continuation", page.continuation)
        assertEquals(page.continuation, page.items.single().sequenceParams)
    }

    @Test fun `missing optional seed metadata still opens correct Short`() {
        val root = fixture("seed").apply { remove("engagementPanels"); remove("sequenceContinuation") }
        val page = parseShortsSeed(root)
        assertEquals("XKnmhvNpIvw", page.items.single().videoId)
        assertEquals("", page.items.single().title)
        assertNull(page.continuation)
    }

    @Test fun `failed or malformed seed does not become an empty success`() {
        for (root in listOf(JSONObject(), fixture("seed").put("status", "REEL_ITEM_WATCH_STATUS_BAD_REQUEST"),
            fixture("seed").apply { remove("replacementEndpoint") })) {
            assertThrows(IOException::class.java) { parseShortsSeed(root) }
        }
    }

    @Test fun `sequence preserves order and uses optional prefetched metadata`() {
        val page = parseShortsSequence(fixture("sequence"))
        assertEquals(8, page.items.size)
        assertEquals("ssbYyEAz7hs", page.items.first().videoId)
        assertEquals("jUcuCLtc2tQ", page.items.last().videoId)
        assertEquals("Microsoft Creates 5TB Glass Storage Lasts 10,000 Years", page.items.first().title)
        assertEquals("", page.items[1].title)
        assertEquals("next-continuation", page.continuation)
    }

    @Test fun `nested actions cannot replace the page continuation or add unrelated Shorts`() {
        val root = fixture("sequence")
        root.put("engagementPanels", JSONArray().put(JSONObject()
            .put("reelWatchEndpoint", JSONObject().put("videoId", "unrelated01"))
            .put("continuationEndpoint", JSONObject().put("continuationCommand", JSONObject().put("token", "wrong")))))
        val page = parseShortsSequence(root)
        assertEquals(8, page.items.size)
        assertEquals("next-continuation", page.continuation)
        assertFalse(page.items.any { it.videoId == "unrelated01" })
    }

    @Test fun `duplicates and malformed entries are skipped without losing continuation`() {
        val root = fixture("sequence")
        val entries = root.getJSONArray("entries")
        entries.put(entries.getJSONObject(0)).put(JSONObject()).put(JSONObject()
            .put("command", JSONObject().put("reelWatchEndpoint", JSONObject().put("videoId", "bad"))))
        val page = parseShortsSequence(root)
        assertEquals(8, page.items.size)
        assertEquals("next-continuation", page.continuation)
    }

    @Test fun `metadata for a different video is ignored`() {
        val reel = fixture("sequence").getJSONArray("entries").getJSONObject(0)
            .getJSONObject("command").getJSONObject("reelWatchEndpoint")
        reel.getJSONObject("unserializedPrefetchData").getJSONObject("playerResponse")
            .getJSONObject("videoDetails").put("videoId", "unrelated01")
        assertEquals("", parseShortsEndpoint(reel)!!.title)
    }

    @Test fun `empty page can advance while an error shell remains retryable`() {
        val root = fixture("sequence").put("entries", JSONArray())
        val page = parseShortsSequence(root)
        assertTrue(page.items.isEmpty())
        assertEquals("next-continuation", page.continuation)
        root.remove("continuationEndpoint")
        assertNull(parseShortsSequence(root).continuation)
        assertThrows(IOException::class.java) { parseShortsSequence(JSONObject()) }
    }
}
