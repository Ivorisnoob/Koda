package com.ivor.ivormusic.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import com.ivor.ivormusic.MainActivity
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.ThemePreferences
import com.ivor.ivormusic.util.KLog

/**
 * Playback progress as an Android 16 Live Update: a status bar chip with the
 * time remaining, plus a prominent entry in the shade.
 *
 * Built the same way as the download notification (see
 * [com.ivor.ivormusic.data.DownloadNotificationHelper]) so the two read as one
 * feature: a [NotificationCompat.ProgressStyle] bar with the Koda note riding
 * it as the tracker icon, and promotion requested through the compat API
 * rather than reflection.
 *
 * Separate from the MediaStyle notification, which carries the transport
 * controls. Both are gated by the same preference, which is OFF by default -
 * a chip that sits in the status bar for the length of every song is a lot of
 * chrome to hand someone who did not ask for it.
 *
 * Promotion is only ever a *request*; the system decides, so nothing here may
 * assume the chip actually appeared. When it is refused this degrades to an
 * ordinary progress notification in the shade rather than vanishing - the same
 * fallback the download notification has always had. Gating the post on
 * promotion instead is what made this feature look broken everywhere except a
 * Pixel emulator: plenty of Android 16 builds answer false from
 * [NotificationManagerCompat.canPostPromotedNotifications].
 */
class MusicProgressLiveUpdate(private val context: Context) {

    companion object {
        private const val TAG = "MusicProgressLiveUpdate"
        /**
         * Shared with the MediaStyle notification: [LiveUpdateMediaNotificationProvider]
         * points Media3's DefaultMediaNotificationProvider at this id so the two
         * playback notifications sit on one channel. Media3 otherwise creates
         * its own, and system settings lists "Now playing" twice.
         */
        const val CHANNEL_ID = "music_live_update"
        private const val NOTIFICATION_ID = 9999

        /**
         * Playback is a single continuous leg, unlike a download's
         * prepare-then-transfer split, so the bar is one full-width segment.
         */
        private const val PLAYBACK_SEGMENT = 100

        /**
         * Create the shared playback channel. Called unconditionally from
         * MusicService.onCreate - not from this class's init - because the
         * media notification needs the channel on every API level, while this
         * class only exists on API 36+.
         *
         * Channel settings are frozen at creation, so getting silence right
         * here is the only chance: a channel that ships with sound cannot be
         * quietened by a later app update.
         */
        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.now_playing_channel_name),
                // Must stay above IMPORTANCE_MIN or the notification becomes
                // ineligible for promotion to a Live Update. LOW is already
                // silent; the explicit nulls below make that non-negotiable
                // rather than a property of the importance constant.
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows what's currently playing"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
                enableLights(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            NotificationManagerCompat.from(context).createNotificationChannel(channel)
        }
    }

    private val notificationManager = NotificationManagerCompat.from(context)
    private var isShowing = false

    // Last rendered values. SystemUI redraws on every notify(), so posting an
    // identical notification once a second visibly stutters the chip.
    private var lastProgress = -1
    private var lastChipText = ""
    private var lastTitle = ""
    private var lastArtwork: android.graphics.Bitmap? = null

    init {
        // Idempotent: the service creates this too, before the media provider
        // is installed. Harmless to repeat, and keeps this class usable alone.
        ensureChannel(context)
    }

    /**
     * Whether the user has asked for the playback notification at all. The
     * platform check lives inside [ThemePreferences.isLivePlaybackUpdatesEnabled].
     *
     * Deliberately separate from [canPostLiveUpdates]: this decides whether to
     * *post*, that one only decides whether to ask for promotion.
     */
    private fun isEnabled(): Boolean = ThemePreferences.isLivePlaybackUpdatesEnabled(context)

    /**
     * Whether promotion to a status bar chip can actually be requested right
     * now: the user's in-app setting and the system-level permission both have
     * to agree.
     *
     * A false here must never suppress the notification itself - see
     * [updateProgress]. Many Android 16 builds (OEM skins especially) report
     * false from [NotificationManagerCompat.canPostPromotedNotifications], and
     * gating the post on it meant playback showed nothing at all on those
     * devices while downloads degraded gracefully to an ordinary progress
     * notification.
     */
    fun canPostLiveUpdates(): Boolean =
        isEnabled() && notificationManager.canPostPromotedNotifications()

    /**
     * Show or update the Live Update with current playback progress. A no-op
     * when the setting is off, which also clears an already-posted chip so
     * switching the toggle off takes effect on the next tick rather than at
     * the end of the song.
     */
    fun updateProgress(
        songTitle: String,
        artistName: String,
        currentPositionMs: Long,
        durationMs: Long,
        isPlaying: Boolean,
        // Album art for the shade entry. Null until it has been decoded, so
        // the notification shows immediately and gains the cover on a later
        // tick rather than waiting on the network. It cannot reach the status
        // bar chip - that slot is the small icon, drawn as a tinted
        // silhouette - so this is the only place the cover can appear.
        artwork: android.graphics.Bitmap? = null
    ) {
        if (!isEnabled()) {
            hide()
            return
        }
        if (durationMs <= 0) return
        // Same guard DownloadService uses before its notify(): notifications
        // switched off app-wide makes the post a silent no-op anyway.
        if (!notificationManager.areNotificationsEnabled()) return

        val progress = ((currentPositionMs.toFloat() / durationMs) * 100).toInt().coerceIn(0, 100)
        val remainingMs = (durationMs - currentPositionMs).coerceAtLeast(0)
        val remainingMin = (remainingMs / 60000).toInt()
        val remainingSec = ((remainingMs % 60000) / 1000).toInt()

        // The chip clips past roughly seven characters, so this is minutes
        // until the last minute and then seconds.
        val chipText = if (remainingMin > 0) "${remainingMin}m" else "${remainingSec}s"

        if (isShowing &&
            progress == lastProgress &&
            chipText == lastChipText &&
            songTitle == lastTitle &&
            artwork === lastArtwork
        ) {
            return
        }

        lastProgress = progress
        lastChipText = chipText
        lastTitle = songTitle
        lastArtwork = artwork

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val style = NotificationCompat.ProgressStyle()
            .setProgress(progress)
            .setProgressSegments(
                listOf(
                    NotificationCompat.ProgressStyle.Segment(PLAYBACK_SEGMENT)
                        .setColor(
                            ContextCompat.getColor(
                                context,
                                R.color.notification_progress_playback
                            )
                        )
                )
            )
            .setProgressTrackerIcon(
                IconCompat.createWithResource(context, R.drawable.ic_download_tracker_music)
            )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_playback_notification)
            .setContentTitle(songTitle)
            .setContentText(artistName)
            .setStyle(style)
            // Album art in place of the app icon in the shade. Null is fine -
            // the system falls back to the small icon.
            .setLargeIcon(artwork)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            // Belt and braces with the channel: a progress readout that ticks
            // ~150 times a song must never make a sound.
            .setSilent(true)
            .setContentIntent(contentIntent)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            // Colorized and promoted are mutually exclusive; a colorized
            // notification is silently refused promotion.
            .setColorized(false)
            // Requesting promotion is harmless when the system has it switched
            // off; the compat layer drops it and this stays an ordinary
            // progress notification in the shade. Matches the download path.
            .setRequestPromotedOngoing(canPostLiveUpdates())
            .setShortCriticalText(chipText)
            .build()

        try {
            notificationManager.notify(NOTIFICATION_ID, notification)
            isShowing = true
        } catch (e: SecurityException) {
            // Permission can be revoked after areNotificationsEnabled(). A
            // progress chip is optional and must never take playback down.
            KLog.w(TAG, "Notification permission changed before post: ${e.message}")
            isShowing = false
        }
    }

    /**
     * Hide the Live Update (playback stopped, or the setting was switched off).
     */
    fun hide() {
        if (isShowing) {
            notificationManager.cancel(NOTIFICATION_ID)
            isShowing = false
            lastProgress = -1
            lastChipText = ""
            lastTitle = ""
            lastArtwork = null
        }
    }

    /**
     * Check if the notification is currently showing.
     */
    fun isShowing(): Boolean = isShowing
}
