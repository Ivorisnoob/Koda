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
import com.ivor.ivormusic.data.YouTubeRateLimit
import com.ivor.ivormusic.data.YouTubeRepository
import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
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

        // Nobody asked for this round. Standing down during a hold and letting
        // WorkManager's own backoff reschedule is strictly better than spending
        // one request per channel against a limit that is already tripped.
        if (YouTubeRateLimit.isHeld()) {
            KLog.w(TAG, "Upload check skipped: rate limited")
            return Result.retry()
        }

        // This used to launch one coroutine per channel with no ceiling, so a
        // 200-channel library opened 200 sockets at once - the shape the feed
        // path caps at FEED_CONCURRENCY precisely because mobile radios handle
        // it badly and it reads as a scrape from the other end.
        val gate = Semaphore(FEED_CONCURRENCY)
        coroutineScope {
            channels.map { channel ->
                async {
                    gate.acquire()
                    try {
                        // A sibling tripped the limit mid-round; the rest stand
                        // down rather than each finding out the hard way.
                        if (YouTubeRateLimit.isHeld()) return@async
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
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // One bad channel must not cost the rest of the round;
                        // its last-seen stays put, so nothing is lost, only deferred.
                        KLog.w(TAG, "Upload check failed for ${channel.channelId}: ${e.message}")
                    } finally {
                        gate.release()
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
         * How many channels this round fetches at once. Matches the local feed
         * refresh's own ceiling in `YouTubeRepository` - the constant is
         * private there, and duplicating the number is better than widening its
         * visibility for a background job that has the same reason to want it.
         */
        private const val FEED_CONCURRENCY = 6

        /** Keep exactly one periodic check while the user has opted in. */
        fun sync(context: Context) {
            setEnabled(context, ThemePreferences(context).getUploadNotificationsEnabled())
        }

        fun setEnabled(context: Context, enabled: Boolean) {
            if (!enabled) {
                WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
                return
            }

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
