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
 * assume the chip actually appeared.
 */
class MusicProgressLiveUpdate(private val context: Context) {

    companion object {
        private const val CHANNEL_ID = "music_live_update"
        private const val CHANNEL_NAME = "Now Playing"
        private const val NOTIFICATION_ID = 9999

        /**
         * Playback is a single continuous leg, unlike a download's
         * prepare-then-transfer split, so the bar is one full-width segment.
         */
        private const val PLAYBACK_SEGMENT = 100
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
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                // Must stay above IMPORTANCE_MIN or the notification becomes
                // ineligible for promotion to a Live Update.
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows what's currently playing"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Whether a promoted playback notification can actually be posted right
     * now: the user's in-app setting and the system-level permission both have
     * to agree. The platform check lives inside
     * [ThemePreferences.isLivePlaybackUpdatesEnabled].
     */
    fun canPostLiveUpdates(): Boolean =
        ThemePreferences.isLivePlaybackUpdatesEnabled(context) &&
            notificationManager.canPostPromotedNotifications()

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
        if (!canPostLiveUpdates()) {
            hide()
            return
        }
        if (durationMs <= 0) return

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
            .setContentIntent(contentIntent)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            // Colorized and promoted are mutually exclusive; a colorized
            // notification is silently refused promotion.
            .setColorized(false)
            .setRequestPromotedOngoing(true)
            .setShortCriticalText(chipText)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
        isShowing = true
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
