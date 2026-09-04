package com.ivor.ivormusic.data.scrobble

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScrobbleModelTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun scrobbleTrackSerializationRoundTrip() {
        val track = ScrobbleTrack(
            mediaId = "youtube_12345",
            title = "Bohemian Rhapsody",
            artist = "Queen",
            album = "A Night at the Opera",
            durationSeconds = 354L,
            timestampSeconds = 1700000000L
        )

        val serialized = json.encodeToString(track)
        val deserialized = json.decodeFromString<ScrobbleTrack>(serialized)

        assertEquals(track, deserialized)
    }

    @Test
    fun scrobbleTrackNullableAlbum() {
        val track = ScrobbleTrack(
            mediaId = "local_999",
            title = "Track Without Album",
            artist = "Independent Artist",
            album = null,
            durationSeconds = 180L,
            timestampSeconds = 1700000000L
        )

        val serialized = json.encodeToString(track)
        val deserialized = json.decodeFromString<ScrobbleTrack>(serialized)

        assertEquals(track, deserialized)
        assertNull(deserialized.album)
    }

    @Test
    fun queuedScrobbleSerializationRoundTrip() {
        val track = ScrobbleTrack(
            mediaId = "vid_1",
            title = "Starboy",
            artist = "The Weeknd",
            album = "Starboy",
            durationSeconds = 230L,
            timestampSeconds = 1700000050L
        )
        val queued = QueuedScrobble(
            id = "queue_uuid_001",
            track = track,
            pendingLastFm = true,
            pendingListenBrainz = true,
            attempts = 2,
            addedAtMs = 1700000100000L
        )

        val serialized = json.encodeToString(queued)
        val deserialized = json.decodeFromString<QueuedScrobble>(serialized)

        assertEquals(queued, deserialized)
        assertEquals(true, deserialized.pendingLastFm)
        assertEquals(true, deserialized.pendingListenBrainz)
        assertEquals(2, deserialized.attempts)
        assertEquals(1700000100000L, deserialized.addedAtMs)
    }

    @Test
    fun listenBrainzNormalizeBaseUrlHandlesVariants() {
        assertEquals("https://api.listenbrainz.org/1/", ListenBrainzClient.normalizeBaseUrl(""))
        assertEquals("https://api.listenbrainz.org/1/", ListenBrainzClient.normalizeBaseUrl("   "))
        assertEquals("https://api.listenbrainz.org/1/", ListenBrainzClient.normalizeBaseUrl("https://api.listenbrainz.org"))
        assertEquals("https://api.listenbrainz.org/1/", ListenBrainzClient.normalizeBaseUrl("https://api.listenbrainz.org/"))
        assertEquals("https://api.listenbrainz.org/1/", ListenBrainzClient.normalizeBaseUrl("https://api.listenbrainz.org/1"))
        assertEquals("https://api.listenbrainz.org/1/", ListenBrainzClient.normalizeBaseUrl("https://api.listenbrainz.org/1/"))
        assertEquals("https://my-maloja.org/apis/listenbrainz/1/", ListenBrainzClient.normalizeBaseUrl("https://my-maloja.org/apis/listenbrainz"))
    }
}
