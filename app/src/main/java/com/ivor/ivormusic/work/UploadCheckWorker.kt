package com.ivor.ivormusic.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.LocalSubscriptionsRepository
import com.ivor.ivormusic.data.ThemePreferences
import com.ivor.ivormusic.data.UploadCheckRepository
import com.ivor.ivormusic.data.YouTubeRepository
import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.TimeUnit

/**
 * Periodically checks the locally-followed channels' RSS feeds for uploads the
 * app was not open to see, and notifies. This is the background half of the
 * subscriptions feature: a follow that only works while Koda is on screen is
 * half a follow.
 *
 * Deliberately built on the same per-channel RSS source as in-app fast refresh
 * (~50 KB per channel against ~1 MB for a browse), with the same concurrency
 * cap and the same honest limitation - RSS carries neither duration nor live
 * status - so the check costs little and cannot drift from what the feed tab
 * shows.
 *
 * First sight of a channel sets a baseline silently instead of notifying:
 * following someone should not greet you with their last forty uploads.
 */
class UploadCheckWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val themePreferences = ThemePreferences(applicationContext)
        // Fresh read: workers run in a process where every ViewModel is long dead.
        if (!themePreferences.getUploadNotificationsEnabled()) {
            return Result.success()
        }

        val localSubscriptions = LocalSubscriptionsRepository(applicationContext)
        val uploadCheck = UploadCheckRepository(applicationContext)
        val repository = YouTubeRepository(applicationContext)

        val channels = localSubscriptions.getAll()
            .filter { !uploadCheck.isMuted(it.channelId) }
        if (channels.isEmpty()) return Result.success()

        var notifiedAny = false
        coroutineScope {
            channels.map { channel ->
                async {
                    try {
                        val feed = repository.getChannelFeedRss(channel.channelId, channel.avatarUrl)
                        if (feed.isEmpty()) return@async

                        val newest = feed.maxOfOrNull { it.publishedAtMs ?: 0L } ?: 0L
                        val seenUpTo = uploadCheck.lastSeenFor(channel.channelId)

                        if (seenUpTo == null) {
                            // Baseline without noise.
                            if (newest > 0L) uploadCheck.markSeen(channel.channelId, newest)
                            return@async
                        }
                        if (newest <= seenUpTo) return@async

                        val fresh = feed.filter { (it.publishedAtMs ?: 0L) > seenUpTo }
                        notifyNewUploads(channel.name, channel.channelId, fresh.take(MAX_PER_CHANNEL))
                        uploadCheck.markSeen(channel.channelId, newest)
                        notifiedAny = true
                    } catch (e: Exception) {
                        // One bad channel must not cost the rest of the round;
                        // its last-seen stays put, so nothing is lost, only deferred.
                        KLog.w(TAG, "Upload check failed for ${channel.channelId}: ${e.message}")
                    }
                }
            }.awaitAll()
        }
        return Result.success()
    }

    private fun notifyNewUploads(channelName: String, channelId: String, uploads: List<com.ivor.ivormusic.data.VideoItem>) {
        val context = applicationContext
        ensureChannel(context)

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val openApp = android.content.Intent(context, com.ivor.ivormusic.MainActivity::class.java)
        val pending = android.app.PendingIntent.getActivity(
            context,
            channelId.hashCode(),
            openApp,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        val text = when (uploads.size) {
            1 -> uploads.first().title
            else -> uploads.joinToString("\n") { it.title }
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_playback_notification)
            .setContentTitle(
                if (uploads.size == 1) channelName
                else context.getString(R.string.upload_notification_title, channelName, uploads.size)
            )
            .setContentText(text)
            .setStyle(NotificationCompat.InboxStyle().also { style ->
                uploads.forEach { style.addLine(it.title) }
            })
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        NotificationManagerCompat.from(context).notify(NOTIFICATION_TAG, channelId.hashCode(), builder.build())
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.upload_check_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.upload_check_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "UploadCheckWorker"
        private const val CHANNEL_ID = "upload_checks"
        private const val NOTIFICATION_TAG = "upload_check"
        private const val UNIQUE_WORK_NAME = "upload_check"
        private const val MAX_PER_CHANNEL = 3

        /**
         * Schedule or re-schedule the periodic check. Idempotent (KEEP): called
         * from Application.onCreate so an install, an update or a reboot all
         * converge on exactly one running job.
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UploadCheckWorker>(6, TimeUnit.HOURS)
                .setConstraints(
                    androidx.work.Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
