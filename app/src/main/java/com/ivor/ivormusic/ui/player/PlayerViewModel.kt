package com.ivor.ivormusic.ui.player

import android.content.ComponentName
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.service.MusicService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PlayerViewModel(private val context: Context) : ViewModel() {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val controller: MediaController? get() = if (controllerFuture?.isDone == true) controllerFuture?.get() else null

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _progress = MutableStateFlow(0L)
    val progress: StateFlow<Long> = _progress.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    init {
        initializeController()
        startProgressUpdates()
    }

    private fun initializeController() {
        val sessionToken = SessionToken(context, ComponentName(context, MusicService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        
        controllerFuture?.addListener({
            controller?.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _isPlaying.value = isPlaying
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        _duration.value = controller?.duration ?: 0L
                    }
                }
            })
        }, MoreExecutors.directExecutor())
    }

    private fun startProgressUpdates() {
        viewModelScope.launch {
            while (isActive) {
                controller?.let {
                    if (it.isPlaying) {
                        _progress.value = it.currentPosition
                    }
                }
                delay(1000)
            }
        }
    }

    fun playSong(song: Song) {
        playQueue(listOf(song))
    }

    fun playQueue(songs: List<Song>) {
        if (songs.isEmpty()) return
        
        // Update current song to the first one immediately for UI responsiveness
        _currentSong.value = songs.first()
        
        controller?.let {
            val mediaItems = songs.map { song ->
                if (song.source == com.ivor.ivormusic.data.SongSource.LOCAL && song.uri != null) {
                    MediaItem.fromUri(song.uri)
                } else {
                    MediaItem.Builder()
                        .setMediaId(song.id) // This ID is used by Service to fetch stream
                         .setMediaMetadata(
                            androidx.media3.common.MediaMetadata.Builder()
                                .setTitle(song.title)
                                .setArtist(song.artist)
                                .setArtworkUri(android.net.Uri.parse(song.thumbnailUrl ?: ""))
                                .build()
                        )
                        .build()
                }
            }
            it.setMediaItems(mediaItems)
            it.prepare()
            it.play()
        }
    }

    fun togglePlayPause() {
        controller?.let {
            if (it.isPlaying) {
                it.pause()
            } else {
                it.play()
            }
        }
    }

    fun seekTo(position: Long) {
        controller?.seekTo(position)
        _progress.value = position
    }

    fun skipToNext() {
        controller?.seekToNext()
    }

    fun skipToPrevious() {
        controller?.seekToPrevious()
    }

    override fun onCleared() {
        super.onCleared()
        MediaController.releaseFuture(controllerFuture ?: return)
    }
}
