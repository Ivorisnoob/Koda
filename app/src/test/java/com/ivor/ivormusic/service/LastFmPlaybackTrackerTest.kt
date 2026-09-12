package com.ivor.ivormusic.service

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.ivor.ivormusic.data.LastFmRepository
import com.ivor.ivormusic.data.LastFmState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test
import org.mockito.Mockito.*

class LastFmPlaybackTrackerTest {
    private val repository = mock(LastFmRepository::class.java)
    private val player = mock(Player::class.java)
    private var time = 0L
    private val tracker = LastFmPlaybackTracker(repository, { time }, { 1000 + time / 1000 })

    private fun setup(allowed: Boolean = true, artist: String = "Artist") {
        `when`(repository.state).thenReturn(MutableStateFlow(LastFmState(enabled = allowed, connected = true, user = "listener")))
        `when`(repository.canScrobble()).thenReturn(allowed)
        `when`(player.currentMediaItem).thenReturn(MediaItem.Builder().setMediaId("song")
            .setMediaMetadata(MediaMetadata.Builder().setTitle("Song").setArtist(artist).build()).build())
        `when`(player.duration).thenReturn(60_000)
        `when`(player.isPlaying).thenReturn(true)
    }

    private fun ticks(count: Int) { repeat(count) { tracker.sample(player); time += 1000 } }

    @Test fun disabledIntegrationNeverTouchesPlayerOrSendsAnything() {
        setup(allowed = false)
        clearInvocations(player)
        ticks(40)
        verifyNoInteractions(player)
        verify(repository, never()).nowPlaying(anyString(), anyString(), anyString(), anyLong())
        verify(repository, never()).enqueue(anyString(), anyString(), anyString(), anyLong(), anyLong())
    }

    @Test fun eligiblePlayIsAnnouncedAndQueuedOnceWithOriginalTimestamp() {
        setup()
        ticks(65)
        verify(repository, times(1)).nowPlaying("Artist", "Song", "", 60_000)
        verify(repository, times(1)).enqueue("Artist", "Song", "", 60_000, 1000)
    }

    @Test fun missingArtistAndLiveMediaNeverScrobble() {
        setup(artist = "")
        ticks(40)
        setup()
        `when`(player.isCurrentMediaItemLive).thenReturn(true)
        ticks(40)
        verify(repository, never()).enqueue(anyString(), anyString(), anyString(), anyLong(), anyLong())
        verify(repository, never()).nowPlaying(anyString(), anyString(), anyString(), anyLong())
    }

    @Test fun privacyGenerationDiscardsEarlierListeningEvenWhenAlreadyReenabled() {
        setup()
        ticks(29)
        `when`(repository.recordingGeneration).thenReturn(1)
        ticks(29)
        verify(repository, never()).enqueue(anyString(), anyString(), anyString(), anyLong(), anyLong())
        ticks(2)
        verify(repository).enqueue("Artist", "Song", "", 60_000, 1029)
    }

    @Test fun repeatStartsASeparateListen() {
        setup()
        ticks(31)
        tracker.reset()
        ticks(31)
        verify(repository).enqueue("Artist", "Song", "", 60_000, 1000)
        verify(repository).enqueue("Artist", "Song", "", 60_000, 1031)
    }
}
