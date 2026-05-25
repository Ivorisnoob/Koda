package com.ivor.ivormusic.media

import com.ivor.ivormusic.domain.Song
import com.ivor.ivormusic.network.YouTubeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DesktopPlayerController(
    private val youtubeRepository: YouTubeRepository
) : PlayerController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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
        scope.launch {
            while (true) {
                delay(500)
                if (_isPlaying.value) {
                    val duration = _durationMs.value
                    if (duration > 0) {
                        val next = (_progressMs.value + 500).coerceAtMost(duration)
                        _progressMs.value = next
                        if (next >= duration) {
                            skipToNext()
                        }
                    }
                }
            }
        }
    }

    override fun playSong(song: Song, queue: List<Song>, startIndex: Int) {
        _currentSong.value = song
        _queue.value = queue.ifEmpty { listOf(song) }
        _currentIndex.value = startIndex
        _progressMs.value = 0L
        _durationMs.value = song.duration.takeIf { it > 0 } ?: 240_000L
        _isPlaying.value = true
        _isBuffering.value = false
    }

    override fun togglePlayPause() { _isPlaying.value = !_isPlaying.value }

    override fun skipToNext() {
        val q = _queue.value
        if (q.isEmpty()) return
        val next = when (_repeatMode.value) {
            PlayerController.REPEAT_MODE_ONE -> _currentIndex.value
            PlayerController.REPEAT_MODE_ALL -> (_currentIndex.value + 1) % q.size
            else -> (_currentIndex.value + 1).takeIf { it < q.size } ?: return
        }
        _currentIndex.value = next
        _currentSong.value = q[next]
        _progressMs.value = 0L
        _durationMs.value = q[next].duration.takeIf { it > 0 } ?: 240_000L
    }

    override fun skipToPrevious() {
        if (_progressMs.value > 3000L) { _progressMs.value = 0L; return }
        val q = _queue.value
        if (q.isEmpty()) return
        val prev = (_currentIndex.value - 1).coerceAtLeast(0)
        _currentIndex.value = prev
        _currentSong.value = q[prev]
        _progressMs.value = 0L
        _durationMs.value = q[prev].duration.takeIf { it > 0 } ?: 240_000L
    }

    override fun seekTo(positionMs: Long) { _progressMs.value = positionMs }
    override fun setShuffleMode(enabled: Boolean) { _shuffleModeEnabled.value = enabled }
    override fun setRepeatMode(mode: Int) { _repeatMode.value = mode }

    override fun addToQueue(song: Song) { _queue.value = _queue.value + song }
    override fun removeFromQueue(index: Int) {
        val q = _queue.value.toMutableList()
        if (index in q.indices) q.removeAt(index)
        _queue.value = q
    }
    override fun reorderQueue(from: Int, to: Int) {
        val q = _queue.value.toMutableList()
        if (from in q.indices && to in q.indices) {
            val song = q.removeAt(from)
            q.add(to, song)
        }
        _queue.value = q
    }
    override fun clearQueue() { _queue.value = emptyList() }
    override fun release() { _isPlaying.value = false }
}
