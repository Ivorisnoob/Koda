package com.ivor.ivormusic.data

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicQueueItemTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val song = Song(
        id = "same-song",
        title = "Same Song",
        artist = "Artist",
        album = "Album",
        duration = 180_000L
    )

    @Test
    fun duplicateSongsReceiveDistinctQueueIdentity() {
        val first = MusicQueueItem(song = song)
        val second = MusicQueueItem(song = song)

        assertNotEquals(first.id, second.id)
        assertEquals(first.song.id, second.song.id)
    }

    @Test
    fun queueIdentitySurvivesPersistence() {
        val first = MusicQueueItem(id = "queue-first", song = song)
        val second = MusicQueueItem(id = "queue-second", song = song)
        val session = PlaybackSession(
            queue = listOf(first, second),
            currentIndex = 1,
            positionMs = 42_000L,
            savedAt = 100L
        )

        val restored = json.decodeFromString<PlaybackSession>(json.encodeToString(session))

        assertEquals(listOf("queue-first", "queue-second"), restored.items.map { it.id })
        assertEquals("queue-second", restored.items[restored.currentIndex].id)
    }

    @Test
    fun legacySongOnlySessionMigratesEveryOccurrence() {
        val legacyJson = """
            {
              "songs": [
                {"id":"same-song","title":"Same Song","artist":"Artist","album":"Album","duration":180000},
                {"id":"same-song","title":"Same Song","artist":"Artist","album":"Album","duration":180000}
              ],
              "currentIndex": 1,
              "positionMs": 42000,
              "savedAt": 100
            }
        """.trimIndent()

        val restored = json.decodeFromString<PlaybackSession>(legacyJson)
        val items = restored.items

        assertEquals(2, items.size)
        assertNotEquals(items[0].id, items[1].id)
        assertTrue(items.all { it.song.id == "same-song" })
        assertEquals("same-song", items[restored.currentIndex].song.id)
    }
}
