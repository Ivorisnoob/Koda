package com.ivor.ivormusic.service

import android.content.ComponentName
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.ivor.ivormusic.R
import com.ivor.ivormusic.util.KLog

/**
 * The Quick Settings playback tile. One tap in the shade plays or pauses
 * whatever the music player is doing, without unlocking or opening Koda.

 * It is a client, not a second player: it binds to [MusicService]'s media
 * session through a [MediaController], exactly the way PlayerViewModel does,
 * so tile state and in-app state can never disagree - they are the same
 * session. Deliberately music-only; the video player has no service-backed
 * queue and a shade tap mid-video would fight PiP for the same screen.

 * Lifecycle: TileService callbacks arrive on the main thread but between
 * calls the system may unbind the service entirely, so nothing here may be
 * cached across onStartListening/onStopListening except the controller pair,
 * and every path must leave them released.
 */
class PlaybackTileService : TileService() {

    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            renderTile()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            renderTile()
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            renderTile()
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        if (controller != null) {
            renderTile()
        } else {
            connect()
        }
    }

    override fun onStopListening() {
        disconnect()
        super.onStopListening()
    }

    override fun onDestroy() {
        disconnect()
        super.onDestroy()
    }

    override fun onClick() {
        super.onClick()
        val ctrl = controller
        if (ctrl == null) {
            // Nothing bound yet (first tap after boot, say). Connecting takes
            // a round trip, so this tap only starts the bind; the next one
            // toggles. Rendering now shows the honest "nothing playing" state
            // rather than leaving whatever the system drew last.
            connect()
            return
        }
        if (ctrl.isPlaying || ctrl.playbackState == Player.STATE_BUFFERING) {
            ctrl.pause()
        } else {
            // play() on an idle, empty player is a no-op rather than an
            // error, which is the right answer for "tile tapped with nothing
            // queued" - the session's last playlist only resumes if it exists.
            ctrl.play()
        }
    }

    private fun connect() {
        if (controllerFuture != null || controller != null) return
        val token = SessionToken(this, ComponentName(this, MusicService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                val result = runCatching { future.get() }
                val ctrl = result.getOrNull()
                when {
                    // Connection failed - service torn down mid-bind, usually.
                    // Media3 requires every buildAsync() future to be released
                    // exactly once, failed ones included.
                    ctrl == null -> {
                        MediaController.releaseFuture(future)
                        if (controllerFuture === future) controllerFuture = null
                        KLog.w(TAG, "Tile controller connect failed: ${result.exceptionOrNull()?.message}")
                        renderTile()
                    }

                    // The user stopped listening before the bind finished;
                    // this future's controller was never adopted, so release
                    // it directly instead of leaking it into a dead tile.
                    controllerFuture !== future -> ctrl.release()

                    else -> {
                        controller = ctrl
                        ctrl.addListener(playerListener)
                        renderTile()
                    }
                }
            },
            ContextCompat.getMainExecutor(this)
        )
    }

    private fun disconnect() {
        controller?.removeListener(playerListener)
        controller?.release()
        controller = null

        // A bind still in flight has no controller to release yet; releasing
        // its pending future cancels it cleanly. A completed future whose
        // controller was released above must not be touched again - Media3
        // forbids double-releasing.
        val future = controllerFuture
        controllerFuture = null
        if (future != null && !future.isDone && !future.isCancelled) {
            MediaController.releaseFuture(future)
        }
    }

    private fun renderTile() {
        val tile = qsTile ?: return
        val ctrl = controller
        val hasSomething = ctrl != null && !ctrl.currentTimeline.isEmpty

        when {
            !hasSomething -> {
                tile.state = Tile.STATE_UNAVAILABLE
                tile.label = getString(R.string.app_name)
                tile.icon = Icon.createWithResource(this, R.drawable.ic_media_play)
            }

            else -> {
                val playing = ctrl.isPlaying
                tile.state = if (playing) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                tile.label = ctrl.mediaMetadata.title?.toString().takeUnless { it.isNullOrBlank() }
                    ?: getString(R.string.app_name)
                ctrl.mediaMetadata.artist?.toString()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { tile.subtitle = it }
                tile.icon = Icon.createWithResource(
                    this,
                    if (playing) R.drawable.ic_media_pause else R.drawable.ic_media_play
                )
            }
        }
        tile.updateTile()
    }

    private companion object {
        const val TAG = "PlaybackTileService"
    }
}
