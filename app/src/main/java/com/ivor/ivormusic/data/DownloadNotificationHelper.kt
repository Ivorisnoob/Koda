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
import java.util.concurrent.ConcurrentHashMap

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
         * Floor on how often a single download may repost. The transfer loop
         * reports every 8KB buffer, which for a normal track is hundreds of
         * updates per second - posting them all is what made these
         * notifications flicker and hammer SystemUI.
         */
        private const val MIN_UPDATE_INTERVAL_MS = 500L

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

    /** Last posted percent + timestamp per download, for throttling. */
    private data class PostState(val atMs: Long, val percent: Int)
    private val lastPost = ConcurrentHashMap<String, PostState>()

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
     * Show or update download progress.
     *
     * Reposts are throttled: an update is skipped unless the whole-percent value
     * changed and [MIN_UPDATE_INTERVAL_MS] has elapsed. The 0% and 100% edges
     * always post so the notification appears and completes promptly.
     */
    fun showDownloadProgress(
        songId: String,
        songTitle: String,
        artistName: String,
        progress: Float, // 0.0 to 1.0
        bytesDownloaded: Long,
        totalBytes: Long,
        // Picks the tracker icon. A plain flag for now; this becomes part of the
        // download model proper once video downloads land.
        isVideo: Boolean = false
    ) {
        if (!hasNotificationPermission()) return

        val percent = (progress * 100).toInt().coerceIn(0, 100)
        if (!shouldPost(songId, percent)) return

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

        // Byte counts only once the transfer is actually underway, otherwise
        // the subtext reads "0.0 / 0.0 MB" during URL resolution.
        if (totalBytes > 0) {
            val downloadedMb = bytesDownloaded / (1024 * 1024f)
            val totalMb = totalBytes / (1024 * 1024f)
            builder.setSubText("%.1f / %.1f MB".format(downloadedMb, totalMb))
        } else {
            builder.setSubText("Preparing")
        }

        notificationManager.notify(getNotificationId(songId), builder.build())
    }

    /**
     * Show download complete notification.
     */
    fun showDownloadComplete(
        songId: String,
        songTitle: String,
        artistName: String
    ) {
        lastPost.remove(songId)
        if (!hasNotificationPermission()) return

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download_notification)
            .setContentTitle("Downloaded")
            .setContentText("$songTitle - $artistName")
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
        lastPost.remove(songId)
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
        lastPost.remove(songId)
        notificationManager.cancel(getNotificationId(songId))
    }

    /**
     * Throttle gate. Posts on the first update, on every whole-percent change
     * that is at least [MIN_UPDATE_INTERVAL_MS] after the last one, and always
     * on the 0/100 edges.
     */
    private fun shouldPost(songId: String, percent: Int): Boolean {
        val now = System.currentTimeMillis()
        val previous = lastPost[songId]

        val allow = when {
            previous == null -> true
            percent >= 100 || percent == 0 -> true
            percent == previous.percent -> false
            else -> now - previous.atMs >= MIN_UPDATE_INTERVAL_MS
        }

        if (allow) lastPost[songId] = PostState(now, percent)
        return allow
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
