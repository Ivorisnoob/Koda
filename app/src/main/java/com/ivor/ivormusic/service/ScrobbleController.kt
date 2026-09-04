package com.ivor.ivormusic.service

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.ivor.ivormusic.data.scrobble.ScrobbleRepository
import com.ivor.ivormusic.data.scrobble.ScrobbleTrack
import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Controller attached to [MusicService] managing playback accumulation,
 * debounced now-playing announcements, and AudioScrobbler threshold compliance.
 *
 * Invariants:
 * 1. Tracks shorter than 30s are never scrobbled.
 * 2. Scrobble threshold is min(duration / 2, 240s).
 * 3. Now Playing is sent after 5s of continuous playback.
 * 4. Seeking does not increment playback time; only real playback ticks count.
 * 5. YouTube placeholder URI replacement preserves accumulated play time.
 * 6. CrossfadeEngine swaps are captured via [onEngineSwapped].
 */
class ScrobbleController(
    private val scrobbleRepository: ScrobbleRepository
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var currentMediaId: String? = null
    private var currentTrack: ScrobbleTrack? = null
    private var playbackStartEpochSeconds: Long = 0L
    private var accumulatedPlayMs: Long = 0L
    private var nowPlayingSent: Boolean = false
    private var scrobbled: Boolean = false

    companion object {
        private const val TAG = "ScrobbleController"
        const val MIN_TRACK_DURATION_SECONDS = 30L
        const val NOW_PLAYING_DEBOUNCE_MS = 5_000L
        const val MAX_THRESHOLD_MS = 240_000L // 4 minutes

        fun calculateThresholdMs(durationSeconds: Long): Long {
            return if (durationSeconds > 0) {
                minOf((durationSeconds * 1000) / 2, MAX_THRESHOLD_MS)
            } else {
                MAX_THRESHOLD_MS
            }
        }

        fun isScrobbleEligible(durationSeconds: Long): Boolean {
            return durationSeconds <= 0 || durationSeconds >= MIN_TRACK_DURATION_SECONDS
        }
    }

    /**
     * Called whenever a media item transition occurs on the active player.
     */
    fun onTrackTransition(mediaItem: MediaItem?) {
        if (mediaItem == null) {
            finalizeCurrentTrack()
            resetState()
            return
        }

        val newMediaId = mediaItem.mediaId

        // Handle YouTube placeholder resolution or gain update:
        // When a placeholder URI is replaced with the resolved stream URL,
        // onMediaItemTransition is emitted with the same mediaId.
        if (newMediaId == currentMediaId && currentTrack != null) {
            // Update metadata if newly populated (e.g. title/album)
            val updatedMetadata = extractTrackMetadata(mediaItem)
            if (updatedMetadata != null) {
                currentTrack = updatedMetadata.copy(
                    durationSeconds = if (updatedMetadata.durationSeconds > 0) {
                        updatedMetadata.durationSeconds
                    } else {
                        currentTrack?.durationSeconds ?: 0L
                    },
                    timestampSeconds = playbackStartEpochSeconds
                )
            }
            KLog.d(TAG, "Placeholder resolution for $newMediaId: preserving accumulated playback")
            return
        }

        // New track transitioned
        finalizeCurrentTrack()
        resetState()

        val track = extractTrackMetadata(mediaItem)
        currentMediaId = newMediaId

        if (track == null) {
            KLog.d(TAG, "Track $newMediaId skipped from scrobbling: invalid or unknown metadata")
            return
        }

        playbackStartEpochSeconds = System.currentTimeMillis() / 1000
        currentTrack = track.copy(timestampSeconds = playbackStartEpochSeconds)
        KLog.d(TAG, "Started tracking for scrobble: ${track.artist} - ${track.title}")
    }

    /**
     * Called when [CrossfadeEngine] swaps to the standby player.
     */
    fun onEngineSwapped(newActivePlayer: Player) {
        val mediaItem = newActivePlayer.currentMediaItem
        onTrackTransition(mediaItem)
    }

    /**
     * Called on each 1-second progress tick while playback is running.
     */
    fun onProgressTick(isPlaying: Boolean, currentDurationMs: Long) {
        if (!isPlaying) return
        val track = currentTrack ?: return

        // Update duration if discovered from player
        if (currentDurationMs > 0 && track.durationSeconds <= 0) {
            currentTrack = track.copy(durationSeconds = currentDurationMs / 1000)
        }

        // Rule 1: Tracks shorter than 30s are ignored
        if (track.durationSeconds in 1 until MIN_TRACK_DURATION_SECONDS) {
            return
        }

        accumulatedPlayMs += 1000L

        // Rule 2: Now Playing after 5s continuous playback
        if (accumulatedPlayMs >= NOW_PLAYING_DEBOUNCE_MS && !nowPlayingSent) {
            nowPlayingSent = true
            scope.launch {
                scrobbleRepository.updateNowPlaying(track)
            }
        }

        // Rule 3: Scrobble when threshold is reached (min(duration / 2, 240s))
        val thresholdMs = calculateThresholdMs(track.durationSeconds)

        if (accumulatedPlayMs >= thresholdMs && !scrobbled) {
            scrobbled = true
            scope.launch {
                scrobbleRepository.scrobbleTrack(track)
            }
        }
    }

    private fun finalizeCurrentTrack() {
        val track = currentTrack ?: return
        if (scrobbled) return

        // If the threshold was met right before transition, ensure it is scrobbled
        val thresholdMs = calculateThresholdMs(track.durationSeconds)

        if (accumulatedPlayMs >= thresholdMs && isScrobbleEligible(track.durationSeconds)) {
            scrobbled = true
            scope.launch {
                scrobbleRepository.scrobbleTrack(track)
            }
        }
    }

    private fun resetState() {
        currentMediaId = null
        currentTrack = null
        playbackStartEpochSeconds = 0L
        accumulatedPlayMs = 0L
        nowPlayingSent = false
        scrobbled = false
    }

    private fun extractTrackMetadata(mediaItem: MediaItem): ScrobbleTrack? {
        val metadata = mediaItem.mediaMetadata
        val title = metadata.title?.toString()?.trim().orEmpty()
        val artist = metadata.artist?.toString()?.trim().orEmpty()
        val album = metadata.albumTitle?.toString()?.trim()

        if (title.isBlank() || artist.isBlank()) return null
        if (title.equals("Unknown", ignoreCase = true) || artist.equals("Unknown", ignoreCase = true)) return null

        val durationSeconds = (metadata.durationMs ?: 0L) / 1000

        return ScrobbleTrack(
            mediaId = mediaItem.mediaId,
            title = title,
            artist = artist,
            album = album?.takeIf { it.isNotBlank() },
            durationSeconds = durationSeconds,
            timestampSeconds = System.currentTimeMillis() / 1000
        )
    }

    fun onDestroy() {
        finalizeCurrentTrack()
        scope.cancel()
    }
}
