package com.ivor.ivormusic.data.scrobble

import kotlinx.serialization.Serializable

/**
 * Metadata representation of a track to be submitted to scrobbling services.
 */
@Serializable
data class ScrobbleTrack(
    val mediaId: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationSeconds: Long,
    val timestampSeconds: Long
)

/**
 * An item stored in the local offline scrobble queue when network requests fail.
 */
@Serializable
data class QueuedScrobble(
    val id: String,
    val track: ScrobbleTrack,
    val pendingLastFm: Boolean = false,
    val pendingListenBrainz: Boolean = false,
    val attempts: Int = 0,
    val addedAtMs: Long = System.currentTimeMillis()
)

/**
 * Connection status for a scrobble service.
 */
sealed class ScrobbleServiceState {
    object Disconnected : ScrobbleServiceState()
    object Loading : ScrobbleServiceState()
    data class Connected(val username: String) : ScrobbleServiceState()
    data class Error(val message: String) : ScrobbleServiceState()
}

/**
 * Result of submitting a scrobble or now-playing update.
 */
sealed class ScrobbleResult {
    object Success : ScrobbleResult()
    data class Failure(val error: String, val canRetry: Boolean = true) : ScrobbleResult()
    object AuthRequired : ScrobbleResult()
}
