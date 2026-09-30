package com.ivor.ivormusic.service

import com.ivor.ivormusic.util.KLog

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.ivor.ivormusic.MainActivity
import com.ivor.ivormusic.R

/**
 * Publishes the video player to the rest of Android.
 *
 * Music has always had this through [MusicService]: a MediaSession is what puts
 * a track on the lock screen, in the notification shade's media area and in the
 * output switcher, and what makes headset and Bluetooth transport buttons work.
 * Video had none of it - it was an ExoPlayer owned by a ViewModel and nothing
 * else - so a video kept playing with the screen off with no way to pause it
 * short of reopening the app, and the system had no idea Koda was playing
 * anything at all. It was also playing audio in the background with no
 * foreground service, which is exactly the state Android kills processes for.
 *
 * The player deliberately stays owned by `VideoPlayerViewModel` (see docs/playback-video.md:
 * the video pipeline is a plain ExoPlayer, not a service, so the surface, PiP
 * and quality switching stay in one place). This service borrows it: it wraps
 * the ViewModel's player in a session and owns nothing but that session, and it
 * never releases the player.
 *
 * Lifecycle is driven from the ViewModel - [start] when a video loads, [stop]
 * when the player is closed or the ViewModel dies. [stop] tears the session
 * down synchronously on the caller's thread (which is the player's application
 * thread) so the session is always gone before the player it wraps is released.
 */
@UnstableApi
class VideoPlaybackService : MediaSessionService() {

    private var session: MediaSession? = null

    /** The ViewModel's own player, which [session] publishes through a [QueuePlayer]. */
    private var sessionSource: Player? = null
    private var queuePlayer: QueuePlayer? = null

    /**
     * What the system's previous/next buttons do. The video player holds one
     * ExoPlayer media item and its queue lives in `VideoPlayerViewModel`, so
     * ExoPlayer's own answer is useless: next would be permanently dead and
     * previous would only ever seek to zero. The ViewModel answers instead.
     */
    interface QueueControls {
        val canSkipNext: Boolean
        val canSkipPrevious: Boolean
        fun skipNext()
        fun skipPrevious()
    }

    /**
     * The ViewModel's player with previous/next routed to [QueueControls].
     *
     * Advertising the commands is not enough on its own: the session learns
     * what is available from `onAvailableCommandsChanged`, and ExoPlayer fires
     * that with its single-item view whenever the item changes. Every listener
     * is therefore wrapped so those events carry the patched set, and
     * [publishQueueCommands] fires one itself when the queue moves without the
     * player noticing.
     */
    private class QueuePlayer(
        player: Player,
        private val controls: () -> QueueControls?
    ) : ForwardingPlayer(player) {

        private val listeners = HashMap<Player.Listener, Player.Listener>()

        private fun patch(commands: Player.Commands): Player.Commands {
            val queue = controls()
            val builder = commands.buildUpon().removeAll(
                COMMAND_SEEK_TO_NEXT,
                COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                COMMAND_SEEK_TO_PREVIOUS,
                COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
            )
            if (queue?.canSkipNext == true) {
                builder.addAll(COMMAND_SEEK_TO_NEXT, COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            }
            if (queue?.canSkipPrevious == true) {
                builder.addAll(COMMAND_SEEK_TO_PREVIOUS, COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            }
            return builder.build()
        }

        override fun getAvailableCommands(): Player.Commands = patch(super.getAvailableCommands())

        override fun isCommandAvailable(command: Int): Boolean =
            getAvailableCommands().contains(command)

        override fun hasNextMediaItem(): Boolean = controls()?.canSkipNext == true

        override fun hasPreviousMediaItem(): Boolean = controls()?.canSkipPrevious == true

        override fun seekToNext() {
            controls()?.takeIf { it.canSkipNext }?.skipNext()
        }

        override fun seekToNextMediaItem() = seekToNext()

        /**
         * The music player's rule, which every system surface expects: a few
         * seconds in, previous restarts the video; right at the start it goes
         * to the one before.
         */
        override fun seekToPrevious() {
            val queue = controls()
            if (queue?.canSkipPrevious == true &&
                currentPosition <= maxSeekToPreviousPosition
            ) {
                queue.skipPrevious()
            } else if (isCommandAvailable(COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) {
                seekTo(0L)
            }
        }

        override fun seekToPreviousMediaItem() {
            controls()?.takeIf { it.canSkipPrevious }?.skipPrevious()
        }

        override fun addListener(listener: Player.Listener) {
            val patched = listeners.getOrPut(listener) { PatchingListener(listener) }
            super.addListener(patched)
        }

        override fun removeListener(listener: Player.Listener) {
            val patched = listeners.remove(listener) ?: return
            super.removeListener(patched)
        }

        /** Tell every listener the queue moved. Application thread only. */
        fun publishQueueCommands() {
            val commands = getAvailableCommands()
            listeners.values.toList().forEach { it.onAvailableCommandsChanged(commands) }
        }

        private inner class PatchingListener(
            private val delegate: Player.Listener
        ) : Player.Listener by delegate {
            override fun onAvailableCommandsChanged(availableCommands: Player.Commands) {
                delegate.onAvailableCommandsChanged(patch(availableCommands))
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        // Its own notification id and channel: the default provider uses one
        // fixed id, so sharing it with MusicService would mean whichever
        // pipeline posted last erased the other's notification.
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setNotificationId(NOTIFICATION_ID)
                .setChannelId(CHANNEL_ID)
                .setChannelName(R.string.video_playback_channel_name)
                .build()
                .apply { setSmallIcon(R.drawable.ic_playback_notification) }
        )

        instance = this

        val player = pendingPlayer
        if (player == null) {
            // Nothing to publish - the ViewModel went away between the start
            // request and the service actually being created, or the system
            // restarted us stickily after the process died.
            KLog.w(TAG, "Started without a player; stopping")
            stopSelf()
            return
        }
        ensureSession(player)
    }

    /**
     * Make sure a session is publishing [player], building one if there is not
     * one already.
     *
     * Needed because `stopService` is asynchronous: closing a video and opening
     * another one quickly enough finds the service still alive with its session
     * already gone, and `onCreate` will not run a second time to rebuild it.
     * Without this the second video would play with nothing on the lock screen.
     */
    private fun ensureSession(player: Player) {
        session?.let {
            if (sessionSource === player) return
            releaseSession()
        }
        val published = QueuePlayer(player) { pendingControls }

        val sessionIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val built = try {
            MediaSession.Builder(this, published)
                // A process may not hold two sessions with the same id, and
                // MusicService already owns the default (empty) one.
                .setId(SESSION_ID)
                .setSessionActivity(sessionIntent)
                .setCallback(VideoSessionCallback())
                .setCustomLayout(seekLayout())
                .build()
        } catch (e: Exception) {
            KLog.e(TAG, "Could not build the video media session", e)
            stopSelf()
            return
        }
        session = built
        sessionSource = player
        queuePlayer = published
        // Explicit, not incidental: Media3 registers a session automatically
        // only when a MediaController binds and onGetSession answers. Nothing in
        // Koda binds to this service - the ViewModel already has the player - so
        // without this the session would exist and the notification would never
        // be posted.
        addSession(built)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /**
     * Swiped out of recents. The player belongs to the ViewModel, which is
     * being torn down with the task, so pause and go away rather than leaving
     * an undismissable foreground notification behind.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        pauseAllPlayersAndStopSelf()
    }

    override fun onDestroy() {
        releaseSession()
        if (instance === this) instance = null
        super.onDestroy()
    }

    /** Drop the session without touching the player - the ViewModel owns that. */
    private fun releaseSession() {
        val current = session ?: return
        session = null
        sessionSource = null
        queuePlayer = null
        runCatching { removeSession(current) }
        current.release()
    }

    /**
     * Back 10 / forward 10 in the media notification, matching the double-tap
     * seek on the player and the PiP controls.
     *
     * These have to be custom session commands rather than
     * [Player.COMMAND_SEEK_BACK] buttons: Media3's notification provider only
     * promotes custom-layout entries that carry a [SessionCommand]. Previous
     * and next, when the queue has them, take the built-in slots beside play;
     * these two fill the extra slots after them.
     */
    private fun seekLayout(): ImmutableList<CommandButton> = ImmutableList.of(
        CommandButton.Builder()
            .setDisplayName("Back 10 seconds")
            .setIconResId(R.drawable.ic_media_replay_10)
            .setSessionCommand(SessionCommand(ACTION_REWIND, Bundle.EMPTY))
            .build(),
        CommandButton.Builder()
            .setDisplayName("Forward 10 seconds")
            .setIconResId(R.drawable.ic_media_forward_10)
            .setSessionCommand(SessionCommand(ACTION_FORWARD, Bundle.EMPTY))
            .build()
    )

    private inner class VideoSessionCallback : MediaSession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(
                    MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(SessionCommand(ACTION_REWIND, Bundle.EMPTY))
                        .add(SessionCommand(ACTION_FORWARD, Bundle.EMPTY))
                        .build()
                )
                // Previous and next stay granted: QueuePlayer only advertises
                // them while the ViewModel's queue has somewhere to go, and
                // routes them there rather than to the single ExoPlayer item.
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                // seekBack/seekForward, not a raw seekTo: the player is built
                // with 10s increments, so these agree with the UI by
                // construction and clamp at the ends of the media for free.
                ACTION_REWIND -> session.player.seekBack()
                ACTION_FORWARD -> session.player.seekForward()
                else -> return Futures.immediateFuture(
                    SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED)
                )
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    companion object {
        private const val TAG = "VideoPlaybackService"
        private const val SESSION_ID = "koda_video"
        private const val CHANNEL_ID = "video_playback"

        // Must differ from DefaultMediaNotificationProvider's default (which is
        // what MusicService posts under) and from MusicProgressLiveUpdate's.
        private const val NOTIFICATION_ID = 1002

        const val ACTION_REWIND = "com.ivor.ivormusic.VIDEO_REWIND"
        const val ACTION_FORWARD = "com.ivor.ivormusic.VIDEO_FORWARD"

        @Volatile
        private var instance: VideoPlaybackService? = null

        /**
         * The player the session will wrap, handed over before the service is
         * created. Held statically because there is no DI here and a Service
         * cannot be given constructor arguments.
         */
        @Volatile
        internal var pendingPlayer: Player? = null
            private set

        /** Where previous/next go. Held for the same reason as [pendingPlayer]. */
        @Volatile
        private var pendingControls: QueueControls? = null

        /**
         * The queue changed - a video started, the playlist was edited, Up Next
         * arrived - so previous/next may have become available or gone away.
         * Call on the player's application thread.
         */
        fun publishQueueCommands() {
            instance?.queuePlayer?.publishQueueCommands()
        }

        /**
         * Publish [player] to the system. Safe to call repeatedly - once the
         * service is up this is just a no-op start command.
         *
         * Must be called from the foreground (it is, from `playVideo`): a
         * background service start is refused from Android 12 onwards.
         */
        fun start(context: Context, player: Player, controls: QueueControls) {
            pendingPlayer = player
            pendingControls = controls
            // A start request cancels a pending stop, but it does not re-run
            // onCreate on a service that is still alive, so an already-running
            // instance is asked to rebuild its session directly.
            instance?.ensureSession(player)
            try {
                context.startService(Intent(context, VideoPlaybackService::class.java))
            } catch (e: Exception) {
                // A refused start costs the notification, never playback.
                KLog.w(TAG, "Could not start the video playback service", e)
            }
        }

        /**
         * Take the video off the system's media controls.
         *
         * The session is released here rather than being left to `onDestroy`
         * because `stopService` is asynchronous: callers release the player
         * immediately afterwards, and a session outliving its player is a
         * crash. This runs on the caller's thread, which is the player's
         * application thread, so the teardown is ordered correctly.
         */
        fun stop(context: Context) {
            instance?.releaseSession()
            pendingPlayer = null
            pendingControls = null
            try {
                context.stopService(Intent(context, VideoPlaybackService::class.java))
            } catch (e: Exception) {
                KLog.w(TAG, "Could not stop the video playback service", e)
            }
        }
    }
}
