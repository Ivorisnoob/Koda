package com.ivor.ivormusic.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicPlaybackItemTest {

    @Test
    fun `same occurrence remains equal after its source is resolved`() {
        assertTrue(isSameQueueOccurrence("queue-1", "song", "queue-1", "song"))
    }

    @Test
    fun `same track in duplicate rows is not the same occurrence`() {
        assertFalse(isSameQueueOccurrence("queue-1", "song", "queue-2", "song"))
    }

    @Test
    fun `item without an occurrence id cannot impersonate a Koda queue item`() {
        assertFalse(isSameQueueOccurrence("queue-1", "song", null, "song"))
    }

    @Test
    fun `external items fall back to media id when both lack occurrence ids`() {
        assertTrue(isSameQueueOccurrence(null, "song", null, "song"))
        assertFalse(isSameQueueOccurrence(null, "song", null, "other"))
    }
}
