package com.ivor.ivormusic.media

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.ivor.ivormusic.domain.Song
import com.ivor.ivormusic.domain.SongSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AndroidPlayerController(private val context: Context) : PlayerController {

    private var controllerFuture: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null
    private val controller: MediaController?
        get() = try {
            if (controllerFuture?.isDone == true) controllerFuture?.get() else null
        } catch (_: Exception) { null }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var progressJob: Job? = null
    private var retryAttempts = 0

    private val _currentSong = MutableStateFlow<Song?>(null)
    override val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _progressMs = MutableStateFlow(0L)
    override val progressMs: StateFlow<Long> = _progressMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    override val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    override val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _shuffleModeEnabled = MutableStateFlow(false)
    override val shuffleModeEnabled: StateFlow<Boolean> = _shuffleModeEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(PlayerController.REPEAT_MODE_OFF)
    override val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    override val queue: StateFlow<List<Song>> = _queue.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    override val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    init {
        connect()
        startProgressUpdates()
    }

    private fun connect() {
        try {
            // MusicService class name — dynamic lookup avoids hard import dependency
            val serviceClass = Class.forName("com.ivor.ivormusic.service.MusicService")
            val token = SessionToken(context, ComponentName(context, serviceClass))
            val future = MediaController.Builder(context, token).buildAsync()
            controllerFuture = future
            future.addListener({
                val ctrl = try { future.get() } catch (e: Exception) {
                    android.util.Log.w("PlayerController", "Connect failed: ${e.message}")
                    MediaController.releaseFuture(future)
                    controllerFuture = null
                    if (retryAttempts < 3) {
                        retryAttempts++
                        scope.launch { delay(300L * retryAttempts); connect() }
                    }
                    return@addListener
                }
                retryAttempts = 0
                syncState(ctrl)
                ctrl.addListener(makeListener())
            }, MoreExecutors.directExecutor())
        } catch (e: ClassNotFoundException) {
            android.util.Log.e("PlayerController", "MusicService not found", e)
        }
    }

    private fun syncState(ctrl: MediaController) {
        _isPlaying.value = ctrl.isPlaying
        _isBuffering.value = ctrl.playbackState == Player.STATE_BUFFERING
        _durationMs.value = ctrl.duration.coerceAtLeast(0L)
        _progressMs.value = ctrl.currentPosition
        _shuffleModeEnabled.value = ctrl.shuffleModeEnabled
        _repeatMode.value = ctrl.repeatMode
        if (ctrl.mediaItemCount > 0 && _queue.value.isEmpty()) {
            val songs = (0 until ctrl.mediaItemCount).mapNotNull { i -> songFromMediaItem(ctrl.getMediaItemAt(i)) }
            if (songs.isNotEmpty()) {
                _queue.value = songs
                _currentIndex.value = ctrl.currentMediaItemIndex
                _currentSong.value = songs.getOrNull(ctrl.currentMediaItemIndex)
            }
        }
    }

    private fun makeListener() = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) { _isPlaying.value = playing; if (playing) _isBuffering.value = false }
        override fun onPlaybackStateChanged(state: Int) {
            _isBuffering.value = state == Player.STATE_BUFFERING
            if (state == Player.STATE_READY) { val d = controller?.duration ?: 0L; if (d > 0) _durationMs.value = d }
        }
        override fun onShuffleModeEnabledChanged(enabled: Boolean) { _shuffleModeEnabled.value = enabled }
        override fun onRepeatModeChanged(mode: Int) { _repeatMode.value = mode }
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val id = mediaItem?.mediaId
            val song = if (!id.isNullOrEmpty()) _queue.value.find { it.id == id }
                       else controller?.currentMediaItemIndex?.let { _queue.value.getOrNull(it) }
                       ?: mediaItem?.let { songFromMediaItem(it) }
            song?.let {
                _currentSong.value = it
                _currentIndex.value = controller?.currentMediaItemIndex ?: 0
            }
        }
    }

    private fun startProgressUpdates() {
        progressJob = scope.launch {
            while (isActive) {
                controller?.let { ctrl ->
                    if (ctrl.isPlaying) {
                        _progressMs.value = ctrl.currentPosition
                        val d = ctrl.duration
                        if (d > 0) _durationMs.value = d
                    }
                }
                delay(500L)
            }
        }
    }

    override fun playSong(song: Song, queue: List<Song>, startIndex: Int) {
        val songs = queue.ifEmpty { listOf(song) }
        val idx = startIndex.coerceIn(0, songs.lastIndex)
        _queue.value = songs
        _currentSong.value = songs[idx]
        _currentIndex.value = idx
        _isBuffering.value = true
        _durationMs.value = 0L
        controller?.let { ctrl ->
            ctrl.setMediaItem(toMediaItem(songs[idx]))
            val before = songs.subList(0, idx).map { toMediaItem(it) }
            val after = songs.subList(idx + 1, songs.size).map { toMediaItem(it) }
            if (before.isNotEmpty()) ctrl.addMediaItems(0, before)
            if (after.isNotEmpty()) ctrl.addMediaItems(before.size + 1, after)
            ctrl.prepare()
            ctrl.play()
        }
    }

    override fun togglePlayPause() { controller?.let { if (it.isPlaying) it.pause() else it.play() } }
    override fun skipToNext() { controller?.seekToNextMediaItem() }
    override fun skipToPrevious() { controller?.seekToPreviousMediaItem() }
    override fun seekTo(positionMs: Long) { controller?.seekTo(positionMs) }
    override fun setShuffleMode(enabled: Boolean) { controller?.shuffleModeEnabled = enabled }
    override fun setRepeatMode(mode: Int) {
        controller?.repeatMode = when (mode) {
            PlayerController.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ONE
            PlayerController.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }
    }

    override fun addToQueue(song: Song) {
        _queue.value = _queue.value + song
        controller?.addMediaItem(toMediaItem(song))
    }

    override fun removeFromQueue(index: Int) {
        val list = _queue.value.toMutableList()
        if (index !in list.indices) return
        list.removeAt(index)
        _queue.value = list
        controller?.removeMediaItem(index)
    }

    override fun reorderQueue(from: Int, to: Int) {
        val list = _queue.value.toMutableList()
        if (from !in list.indices || to !in list.indices) return
        val item = list.removeAt(from)
        list.add(to, item)
        _queue.value = list
        controller?.moveMediaItem(from, to)
    }

    override fun clearQueue() {
        _queue.value = emptyList()
        _currentSong.value = null
        _currentIndex.value = 0
        controller?.clearMediaItems()
        controller?.stop()
    }

    override fun release() {
        progressJob?.cancel()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
    }

    private fun toMediaItem(song: Song): MediaItem {
        return if (song.source == SongSource.LOCAL && song.uri != null) {
            MediaItem.Builder()
                .setUri(Uri.parse(song.uri))
                .setMediaId(song.id)
                .setMediaMetadata(MediaMetadata.Builder()
                    .setTitle(song.title).setArtist(song.artist)
                    .setArtworkUri(song.albumArtUri?.let { Uri.parse(it) })
                    .build())
                .build()
        } else {
            MediaItem.Builder()
                .setMediaId(song.id)
                .setUri("https://placeholder.ivormusic/${song.id}")
                .setMediaMetadata(MediaMetadata.Builder()
                    .setTitle(song.title).setArtist(song.artist)
                    .setArtworkUri(Uri.parse(song.highResThumbnailUrl ?: song.thumbnailUrl ?: ""))
                    .build())
                .build()
        }
    }

    private fun songFromMediaItem(item: MediaItem): Song? {
        val id = item.mediaId.takeIf { it.isNotEmpty() } ?: return null
        val meta = item.mediaMetadata
        return Song(
            id = id,
            title = meta.title?.toString() ?: "Unknown",
            artist = meta.artist?.toString() ?: "Unknown Artist",
            album = meta.albumTitle?.toString() ?: "",
            duration = 0L,
            thumbnailUrl = meta.artworkUri?.toString(),
            source = if (item.localConfiguration?.uri?.scheme == "https" && item.localConfiguration?.uri?.host == "placeholder.ivormusic") SongSource.YOUTUBE else SongSource.LOCAL
        )
    }
}
