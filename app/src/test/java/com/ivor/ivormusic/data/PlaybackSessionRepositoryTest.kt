package com.ivor.ivormusic.data

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PlaybackSessionRepositoryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun repository() = PlaybackSessionRepository(temporaryFolder.root)

    private fun queue(size: Int) = List(size) { index ->
        MusicQueueItem(
            id = "row$index",
            song = Song(
                id = "song$index",
                title = "Title $index",
                artist = "Artist",
                album = "",
                duration = 180_000L,
                source = SongSource.YOUTUBE,
            ),
        )
    }

    @Test fun `a saved queue loads back as it was`() {
        val repository = repository()
        repository.saveQueue(queue(10), currentIndex = 4, positionMs = 42_000L)

        val session = repository.load()!!
        assertEquals(queue(10), session.queue)
        assertEquals(4, session.currentIndex)
        assertEquals(42_000L, session.positionMs)
    }

    @Test fun `a queue within the cap is kept whole even near its end`() {
        val repository = repository()
        repository.saveQueue(queue(3_000), currentIndex = 2_900, positionMs = 0L)

        val session = repository.load()!!
        assertEquals(3_000, session.queue.size)
        assertEquals(2_900, session.currentIndex)
    }

    @Test fun `a queue over the cap keeps a full window around the current song`() {
        val size = PlaybackSessionRepository.MAX_SAVED_SONGS + 1_000
        val repository = repository()
        repository.saveQueue(queue(size), currentIndex = size - 10, positionMs = 0L)

        val session = repository.load()!!
        assertEquals(PlaybackSessionRepository.MAX_SAVED_SONGS, session.queue.size)
        assertEquals("row${size - 10}", session.queue[session.currentIndex].id)
    }

    @Test fun `the window start is clamped so the window is always full`() {
        val max = PlaybackSessionRepository.MAX_SAVED_SONGS
        assertEquals(0, PlaybackSessionRepository.savedWindowStart(max, max - 1))
        assertEquals(0, PlaybackSessionRepository.savedWindowStart(max + 10, 3))
        assertEquals(10, PlaybackSessionRepository.savedWindowStart(max + 10, max + 9))
        assertEquals(500, PlaybackSessionRepository.savedWindowStart(max * 2, max / 2 + 500))
    }

    @Test fun `a newer position finds its row by id`() {
        val repository = repository()
        repository.saveQueue(queue(10), currentIndex = 2, positionMs = 1_000L)
        repository.savePosition(queueItemId = "row7", currentIndex = 99, positionMs = 30_000L)

        val session = repository.load()!!
        assertEquals(7, session.currentIndex)
        assertEquals(30_000L, session.positionMs)
    }

    @Test fun `a position whose row is gone is ignored`() {
        val repository = repository()
        repository.saveQueue(queue(10), currentIndex = 2, positionMs = 1_000L)
        repository.savePosition(queueItemId = "elsewhere", currentIndex = 5, positionMs = 30_000L)

        val session = repository.load()!!
        assertEquals(2, session.currentIndex)
        assertEquals(1_000L, session.positionMs)
    }

    @Test fun `a new queue supersedes the previous queue's position`() {
        val repository = repository()
        repository.saveQueue(queue(10), currentIndex = 2, positionMs = 1_000L)
        repository.savePosition(queueItemId = "row7", currentIndex = 7, positionMs = 30_000L)
        repository.saveQueue(queue(10), currentIndex = 3, positionMs = 5_000L)

        val session = repository.load()!!
        assertEquals(3, session.currentIndex)
        assertEquals(5_000L, session.positionMs)
    }

    @Test fun `a position without a queue writes nothing`() {
        val repository = repository()
        repository.savePosition(queueItemId = "row1", currentIndex = 1, positionMs = 1_000L)
        assertNull(repository.load())
    }

    @Test fun `clearing removes the queue and the position`() {
        val repository = repository()
        repository.saveQueue(queue(10), currentIndex = 2, positionMs = 1_000L)
        repository.savePosition(queueItemId = "row7", currentIndex = 7, positionMs = 30_000L)
        repository.clear()

        assertNull(repository.load())
        assertTrue(temporaryFolder.root.listFiles().orEmpty().isEmpty())
    }

    @Test fun `the shuffle order survives trimming`() {
        val repository = repository()
        val order = intArrayOf(4, 0, 9, 1, 2, 3, 5, 6, 7, 8)
        repository.saveQueue(queue(10), currentIndex = 4, positionMs = 0L, playOrder = order)

        assertEquals(order.toList(), repository.load()!!.playOrder)
    }
}
