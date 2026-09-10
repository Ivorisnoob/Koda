package com.ivor.ivormusic.data

import android.content.Context
import android.os.SystemClock
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** One playback's local progress and ordered, best-effort YouTube reports. Main-thread owned. */
internal class VideoWatchTracker(
    private val context: Context,
    private val scope: CoroutineScope,
    private val youtube: YouTubeRepository,
) {
    private val history = VideoHistoryRepository(context)
    private val preferences = ThemePreferences(context)
    private var active: Watch? = null

    fun start(player: Player, video: () -> VideoItem?, thresholdMs: Long = 10_000L) {
        close()
        val item = video() ?: return
        if (item.videoId.startsWith("external:")) return
        active = Watch(player, item.videoId, video, thresholdMs).also { it.start() }
    }

    fun close() {
        active?.close()
        active = null
    }

    private inner class Watch(
        val player: Player,
        val videoId: String,
        val video: () -> VideoItem?,
        thresholdMs: Long,
    ) : Player.Listener {
        val profileId = ProfileManager.activeProfileId(context)
        val clock = VideoWatchClock(thresholdMs)
        var recorded = false
        var session: VideoHistorySession? = null
        var ticker: Job? = null
        var pending: Job? = null
        var segmentStart = player.currentPosition.coerceAtLeast(0L)
        var lastSave = 0L
        var lastReport = 0L
        var lastReportedPosition: Long? = null
        var epoch = 0

        fun enabled() = profileId == ProfileManager.activeProfileId(context) &&
            preferences.isSaveVideoHistoryEnabled() && !IncognitoMode.isEnabled(context)

        fun start() {
            player.addListener(this)
            ticker = scope.launch {
                while (isActive) {
                    sample()
                    delay(1_000L)
                }
            }
        }

        fun sample(position: Long = player.currentPosition.coerceAtLeast(0L), flush: Boolean = false) {
            val now = SystemClock.elapsedRealtime()
            val allowed = enabled()
            val qualified = clock.sample(now, player.isPlaying, allowed)
            if (!allowed) {
                epoch++
                pending?.cancel()
                session = null
                recorded = false
                lastReportedPosition = null
                segmentStart = position
                return
            }
            val item = video()?.takeIf { it.videoId == videoId } ?: return
            if (!qualified) return
            if (!recorded) {
                history.addVideo(item)
                recorded = true
            }
            val duration = player.duration.takeIf { it > 0L } ?: item.duration * 1_000L
            if (!item.isLive && !player.isCurrentMediaItemLive && (flush || now - lastSave >= 15_000L)) {
                history.saveWatchProgress(videoId, position, duration)
                lastSave = now
            }
            if (position != lastReportedPosition &&
                (flush || (player.isPlaying && now - lastReport >= 30_000L))) {
                report(position, flush, item.isLive || player.isCurrentMediaItemLive)
                lastReport = now
                lastReportedPosition = position
            }
        }

        fun report(position: Long, final: Boolean, live: Boolean) {
            if (videoId.startsWith("device:")) return
            val previous = pending
            val start = segmentStart
            val reportEpoch = epoch
            segmentStart = position
            pending = scope.launch {
                previous?.join()
                if (!enabled() || epoch != reportEpoch) return@launch
                val tracking = session ?: youtube.beginVideoHistorySession(videoId, start)
                    ?.also { session = it } ?: return@launch
                if (!live && enabled() && epoch == reportEpoch) {
                    youtube.reportVideoWatchProgress(tracking, start, position, final)
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            sample(flush = !isPlaying)
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            // End the old interval before a seek, SponsorBlock skip, or repeat.
            // Never claim that the skipped span was watched.
            sample(oldPosition.positionMs.coerceAtLeast(0L), flush = true)
            segmentStart = newPosition.positionMs.coerceAtLeast(0L)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) sample(flush = true)
        }

        fun close() {
            sample(flush = true)
            ticker?.cancel()
            player.removeListener(this)
        }
    }
}
