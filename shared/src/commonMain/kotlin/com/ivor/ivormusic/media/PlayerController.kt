package com.ivor.ivormusic.media

import com.ivor.ivormusic.domain.Song
import kotlinx.coroutines.flow.StateFlow

/**
 * Platform-independent player controller interface.
 * Android: backed by Media3 MediaController / MusicService.
 * iOS: backed by AVPlayer + AVAudioSession.
 */
interface PlayerController {
    val currentSong: StateFlow<Song?>
    val isPlaying: StateFlow<Boolean>
    val progressMs: StateFlow<Long>
    val durationMs: StateFlow<Long>
    val isBuffering: StateFlow<Boolean>
    val shuffleModeEnabled: StateFlow<Boolean>
    val repeatMode: StateFlow<Int>
    val queue: StateFlow<List<Song>>
    val currentIndex: StateFlow<Int>

    fun playSong(song: Song, queue: List<Song> = emptyList(), startIndex: Int = 0)
    fun togglePlayPause()
    fun skipToNext()
    fun skipToPrevious()
    fun seekTo(positionMs: Long)
    fun setShuffleMode(enabled: Boolean)
    fun setRepeatMode(mode: Int)
    fun addToQueue(song: Song)
    fun removeFromQueue(index: Int)
    fun reorderQueue(from: Int, to: Int)
    fun clearQueue()
    fun release()

    companion object {
        const val REPEAT_MODE_OFF = 0
        const val REPEAT_MODE_ONE = 1
        const val REPEAT_MODE_ALL = 2
    }
}
