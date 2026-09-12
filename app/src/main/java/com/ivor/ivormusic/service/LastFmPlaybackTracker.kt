package com.ivor.ivormusic.service

import android.os.SystemClock
import androidx.media3.common.Player
import com.ivor.ivormusic.data.LastFmListeningClock
import com.ivor.ivormusic.data.LastFmRepository

/** Main-thread snapshots of the audible player also follow crossfade engine swaps. */
internal class LastFmPlaybackTracker(
    private val repository: LastFmRepository,
    private val monotonicMillis: () -> Long = SystemClock::elapsedRealtime,
    private val unixSeconds: () -> Long = { System.currentTimeMillis() / 1000 }
) {
    private val clock = LastFmListeningClock()
    private var announced: String? = null
    private var lastRetry = 0L
    private var identity = ""
    private var generation = -1

    fun reset() { clock.reset(); announced = null }

    fun sample(player: Player) {
        val session = repository.state.value
        if (generation != repository.recordingGeneration) {
            reset(); generation = repository.recordingGeneration
        }
        if (!repository.canScrobble()) { reset(); return }
        if (identity != session.user) { reset(); identity = session.user }
        val item = player.currentMediaItem ?: run { reset(); return }
        val artist = item.mediaMetadata.artist?.toString().orEmpty().trim()
        val title = item.mediaMetadata.title?.toString().orEmpty().trim()
        if (artist.isBlank() || title.isBlank() || player.isCurrentMediaItemLive) { reset(); return }
        val id = item.queueItemId ?: item.mediaId
        val elapsed = monotonicMillis()
        if (player.isPlaying && announced != id && !session.busy) {
            repository.nowPlaying(artist, title, item.mediaMetadata.albumTitle?.toString().orEmpty(), player.duration)
            announced = id
        }
        if (clock.sample(id, player.isPlaying, elapsed, unixSeconds(), player.duration)) {
            repository.enqueue(artist, title, item.mediaMetadata.albumTitle?.toString().orEmpty(), player.duration, clock.startedAt)
        }
        if (elapsed - lastRetry >= 300_000) {
            lastRetry = elapsed
            repository.syncPending()
        }
    }
}
