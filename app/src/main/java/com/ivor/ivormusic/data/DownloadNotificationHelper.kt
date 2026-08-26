package com.ivor.ivormusic.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import com.ivor.ivormusic.MainActivity
import com.ivor.ivormusic.R

/**
 * Download notifications, including Android 16 Live Updates.
 *
 * Progress is reported as two [NotificationCompat.ProgressStyle] segments that
 * mirror how a download actually runs: a short "preparing" leg while the stream
 * URL is resolved, then the byte transfer. The Koda mark rides the bar as the
 * tracker icon, and the status bar chip carries the percentage.
 *
 * Live Updates need three things to line up, and any one of them missing simply
 * degrades to an ordinary progress notification:
 *  - API 36+ (below that [NotificationCompat.Builder.setRequestPromotedOngoing]
 *    is a no-op),
 *  - the user leaving the in-app setting on,
 *  - the user not having revoked promoted notifications for Koda in system
 *    settings, which is what [NotificationManagerCompat.canPostPromotedNotifications]
 *    reports.
 *
 * Promotion is only ever a *request*; the system decides, so nothing here may
 * assume the chip actually appeared.
 */
class DownloadNotificationHelper(private val context: Context) {

    companion object {
        private const val CHANNEL_ID = "download_channel"
        private const val CHANNEL_NAME = "Downloads"
        private const val CHANNEL_DESCRIPTION = "Song and video download progress"

        /**
         * Fixed id for the foreground service notification. Downloads run one
         * at a time behind a single service, so progress is one notification
         * that retitles itself rather than one per song.
         */
        const val FOREGROUND_NOTIFICATION_ID = 0xD0AD

        /**
         * Share of the bar given to stream resolution, matching the 0.1f the
         * repository reserves before the first byte arrives.
         */
        private const val PREPARE_SEGMENT = 10
        private const val TRANSFER_SEGMENT = 90

        // Use unique notification IDs per song (hash of song ID)
        fun getNotificationId(songId: String): Int = songId.hashCode().let {
            // Ensure positive ID
            if (it == Int.MIN_VALUE) Int.MAX_VALUE else kotlin.math.abs(it)
        }
    }

    private val notificationManager = NotificationManagerCompat.from(context)

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
                description = CHANNEL_DESCRIPTION
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Whether a promoted (Live Update) notification can actually be posted right
     * now: platform support, the user's in-app setting, and the system-level
     * permission all have to agree.
     */
    fun canPostLiveUpdates(): Boolean =
        ThemePreferences.isLiveDownloadUpdatesEnabled(context) &&
            notificationManager.canPostPromotedNotifications()

    /**
     * Whether the platform supports Live Updates at all, regardless of whether
     * the user has them switched on. Drives visibility of the setting row.
     */
    fun supportsLiveUpdates(): Boolean = ThemePreferences.SUPPORTS_LIVE_UPDATES

    /**
     * System settings screen where promoted notifications are granted or
     * revoked for this app. Null when the platform has no such screen, so the
     * caller can hide the affordance.
     */
    fun promotedNotificationSettingsIntent(): Intent? {
        if (!ThemePreferences.SUPPORTS_LIVE_UPDATES) return null
        return Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    }

    /**
     * Build the download progress notification.
     *
     * This returns rather than posts, because the notification belongs to the
     * foreground service: the same object has to be handed to startForeground
     * on the first call and to notify() on every update, and a service that
     * cannot produce its notification synchronously cannot start.
     *
     * Update rate is governed upstream - the repository only emits on
     * whole-percent changes - so there is no throttle here.
     */
    fun buildProgressNotification(
        songTitle: String,
        artistName: String,
        progress: Float, // 0.0 to 1.0
        bytesDownloaded: Long,
        totalBytes: Long,
        queuedCount: Int = 0,
        // Picks the tracker icon. A plain flag for now; this becomes part of the
        // download model proper once video downloads land.
        isVideo: Boolean = false,
        // Album art / video thumbnail. Null until it has been fetched, so the
        // notification shows immediately and gains the artwork on the next
        // update rather than waiting on the network.
        artwork: android.graphics.Bitmap? = null
    ): android.app.Notification {
        val percent = (progress * 100).toInt().coerceIn(0, 100)

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Two segments: stream resolution, then the transfer. Their lengths sum
        // to the bar maximum - ProgressStyle has no setProgressMax, the total is
        // derived from the segments.
        val style = NotificationCompat.ProgressStyle()
            .setProgress(percent)
            .setProgressSegments(
                listOf(
                    NotificationCompat.ProgressStyle.Segment(PREPARE_SEGMENT)
                        .setColor(
                            ContextCompat.getColor(
                                context,
                                R.color.notification_progress_preparing
                            )
                        ),
                    NotificationCompat.ProgressStyle.Segment(TRANSFER_SEGMENT)
                        .setColor(
                            ContextCompat.getColor(
                                context,
                                R.color.notification_progress_transfer
                            )
                        )
                )
            )
            .setProgressTrackerIcon(
                IconCompat.createWithResource(
                    context,
                    if (isVideo) {
                        R.drawable.ic_download_tracker_video
                    } else {
                        R.drawable.ic_download_tracker_music
                    }
                )
            )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download_notification)
            .setContentTitle(songTitle)
            .setContentText(artistName)
            .setStyle(style)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            // Requesting promotion is harmless below API 36 and when the user
            // has it switched off; the compat layer drops it.
            .setRequestPromotedOngoing(canPostLiveUpdates())
            .setShortCriticalText("$percent%")
            // Album art in place of the app icon. Null is fine - the system
            // simply falls back to the small icon.
            .setLargeIcon(artwork)

        // Byte counts only once the transfer is actually underway, otherwise
        // the subtext reads "0.0 / 0.0 MB" during URL resolution. The queue
        // depth matters more than bytes when there is one, so it wins.
        builder.setSubText(
            when {
                queuedCount > 0 -> "$queuedCount more in queue"
                totalBytes > 0 -> "%.1f / %.1f MB".format(
                    bytesDownloaded / (1024 * 1024f),
                    totalBytes / (1024 * 1024f)
                )
                else -> "Preparing"
            }
        )

        return builder.build()
    }

    /**
     * Show download complete notification.
     */
    fun showDownloadComplete(
        songId: String,
        songTitle: String,
        artistName: String,
        artwork: android.graphics.Bitmap? = null
    ) {
        if (!hasNotificationPermission()) return

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download_notification)
            .setContentTitle(songTitle)
            .setContentText(artistName)
            .setSubText(context.getString(R.string.song_options_downloaded))
            .setLargeIcon(artwork)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setTimeoutAfter(5_000)

        notificationManager.notify(getNotificationId(songId), builder.build())
    }

    /**
     * Show download failed notification.
     */
    fun showDownloadFailed(
        songId: String,
        songTitle: String
    ) {
        if (!hasNotificationPermission()) return

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download_notification)
            .setContentTitle("Download failed")
            .setContentText(songTitle)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        notificationManager.notify(getNotificationId(songId), builder.build())
    }

    /**
     * Dismiss a download notification.
     */
    fun dismissNotification(songId: String) {
        notificationManager.cancel(getNotificationId(songId))
    }

    /**
     * Check if we have notification permission (required for Android 13+).
     */
    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}
