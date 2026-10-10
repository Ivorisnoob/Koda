package com.ivor.ivormusic.ui.taste

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.ivor.ivormusic.data.CacheManager
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.data.YouTubeRepository
import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Plays the song on top of the taste deck, and nothing else.
 *
 * **A player of its own rather than the music player.** The deck is a string
 * of songs the user has not chosen, half of which they are about to say no to.
 * Through `MusicService` every one of them would be a play: recorded in the
 * listening history the deck exists to supplement, scrobbled, put in the
 * notification and the mini player, and it would replace whatever queue was
 * loaded. This one records nothing and owns no session. It takes audio focus,
 * so music already playing pauses for it the way it does for any other app.
 *
 * **The next card is already loaded.** [scar October 2026] A swipe used to
 * start from nothing: resolve the stream, open it, buffer to forty seconds in,
 * and only then make a sound, which on every card was a second or two of
 * silence after the gesture. So there are two players. While one plays the top
 * card, the other holds the card underneath, resolved, seeked and buffered but
 * paused; a swipe swaps them, and the freed one starts on the card after that.
 * The stream for one card further ahead is resolved as well, so the standby
 * never waits on that either. Two cards of lookahead is the whole cost: about
 * one extra resolve and a few hundred kilobytes for a card that might be
 * finished before it is reached.
 *
 * Streams come through the same resolver and the same per-client,
 * ranged-request data source as music playback (the cache read-only, so
 * samples do not evict songs the user actually keeps).
 *
 * Main thread only, like any ExoPlayer; [scope] is the owning ViewModel's.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class TastePreviewPlayer(
    context: Context,
    private val repository: YouTubeRepository,
    private val scope: CoroutineScope
) {
    private val appContext = context.applicationContext

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** The user paused; later cards stay silent until they press play again. */
    private var muted = false
    private var released = false

    /** One of the two players and the card it currently holds. */
    private inner class Slot {
        var songId: String? = null
        var loadJob: Job? = null
        val player: ExoPlayer = ExoPlayer.Builder(appContext)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(CacheManager.createPlaybackDataSourceFactory(appContext) { false })
            )
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .build()

        init {
            player.addListener(object : Player.Listener {
                // Only the audible player speaks for the screen; the standby
                // buffering underneath is not "loading" anyone is waiting on.
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (this@Slot === active) _isPlaying.value = isPlaying
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (this@Slot === active) publishLoading()
                }

                override fun onPlayerError(error: PlaybackException) {
                    // A sample that will not play is a silent card, not an
                    // error worth interrupting a swipe for.
                    KLog.w("TastePreview", "Preview failed for $songId", error)
                    if (this@Slot === active) _isLoading.value = false
                }
            })
        }

        /** Resolve [song] and hold it ready at the sample point; play it only if [audible]. */
        fun load(song: Song, audible: Boolean) {
            loadJob?.cancel()
            player.stop()
            songId = song.id
            loadJob = scope.launch {
                val url = urlFor(song.id).await()
                if (released || songId != song.id) return@launch
                if (url == null) {
                    if (this@Slot === active) _isLoading.value = false
                    return@launch
                }
                player.setMediaItem(MediaItem.fromUri(url), PREVIEW_START_MS)
                player.playWhenReady = audible && this@Slot === active && !muted
                player.prepare()
            }
        }

        fun clear() {
            loadJob?.cancel()
            songId = null
            player.stop()
        }
    }

    private var active = Slot()
    private var standby = Slot()

    /** Stream lookups in flight or done, so a card is never resolved twice. */
    private val urls = HashMap<String, Deferred<String?>>()

    private fun urlFor(songId: String): Deferred<String?> = urls.getOrPut(songId) {
        scope.async { repository.getStreamUrl(songId).getOrNull() }
    }

    private fun publishLoading() {
        val state = active.player.playbackState
        _isLoading.value = !muted && active.songId != null &&
            (state == Player.STATE_IDLE || state == Player.STATE_BUFFERING)
    }

    /**
     * Play [song], and get ready for what follows it: [upcoming] is the next
     * cards in deck order, the first of which is loaded into the standby.
     */
    fun play(song: Song, upcoming: List<Song> = emptyList()) {
        if (released) return
        if (standby.songId == song.id) {
            // The common case: this card was the one waiting underneath.
            val finished = active
            active = standby
            standby = finished
            standby.clear()
            active.player.playWhenReady = !muted
        } else {
            active.load(song, audible = true)
        }
        _isPlaying.value = active.player.isPlaying
        publishLoading()

        val next = upcoming.firstOrNull()
        if (next == null) standby.clear() else if (standby.songId != next.id) standby.load(next, audible = false)
        upcoming.getOrNull(1)?.let { urlFor(it.id) }
    }

    fun toggle(current: Song?) {
        if (released) return
        if (active.player.isPlaying || _isLoading.value) {
            muted = true
            active.player.pause()
            _isLoading.value = false
        } else {
            muted = false
            when {
                active.songId != null -> active.player.play()
                current != null -> active.load(current, audible = true)
            }
            publishLoading()
        }
    }

    fun stop() {
        if (released) return
        active.clear()
        standby.clear()
        _isLoading.value = false
        _isPlaying.value = false
    }

    fun release() {
        if (released) return
        released = true
        active.loadJob?.cancel()
        standby.loadJob?.cancel()
        urls.values.forEach { it.cancel() }
        active.player.release()
        standby.player.release()
    }

    private companion object {
        /** Past the intro: the part of a song that says whether you like it. */
        const val PREVIEW_START_MS = 40_000L
    }
}
