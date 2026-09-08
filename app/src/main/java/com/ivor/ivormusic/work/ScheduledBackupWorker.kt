package com.ivor.ivormusic.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ivor.ivormusic.MainActivity
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.ScheduledBackupRepository
import com.ivor.ivormusic.data.ScheduledBackupResult
import com.ivor.ivormusic.data.ScheduledBackupStatus
import com.ivor.ivormusic.util.KLog
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit

/**
 * Background worker executing unattended backups on the user's schedule (#230).
 *
 * Adheres strictly to the safety contract:
 * - Backups only write; restore is strictly user-initiated.
 * - Snapshots share identical allowlists and exclusions via [BackupRepository.collect].
 * - Old archives are pruned only after a new backup file is written and flushed.
 * - Surfacing folder access loss (revoked permissions or missing directories) promptly via notifications.
 */
class ScheduledBackupWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val repo = ScheduledBackupRepository(context)

        // If disabled or folder missing, cancel unique work
        if (!repo.enabled.value || repo.folderUri.value == null) {
            KLog.i(TAG, "Scheduled backup disabled; cancelling work request")
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
            return Result.success()
        }

        KLog.i(TAG, "Executing scheduled backup...")

        return try {
            when (val result = repo.executeBackup()) {
                is ScheduledBackupResult.Success -> {
                    KLog.i(TAG, "Scheduled backup finished successfully. Pruned ${result.prunedCount} old backups.")
                    Result.success()
                }
                is ScheduledBackupResult.Failure -> {
                    KLog.w(TAG, "Scheduled backup failed: ${result.status} - ${result.message}")
                    when (result.status) {
                        ScheduledBackupStatus.PERMISSION_REVOKED -> {
                            notifyFailure(context, context.getString(R.string.bk_notif_perm_revoked))
                            Result.failure()
                        }
                        ScheduledBackupStatus.FOLDER_UNAVAILABLE -> {
                            notifyFailure(context, context.getString(R.string.bk_notif_folder_missing))
                            Result.failure()
                        }
                        else -> {
                            // Transient failure
                            val failures = repo.consecutiveFailures.value
                            if (failures >= MAX_TRANSIENT_FAILURES_BEFORE_NOTIFY) {
                                notifyFailure(
                                    context,
                                    context.getString(R.string.bk_notif_failed_repeated, failures)
                                )
                            }
                            Result.retry()
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            KLog.e(TAG, "Unexpected error in ScheduledBackupWorker", e)
            val failures = repo.consecutiveFailures.value
            if (failures >= MAX_TRANSIENT_FAILURES_BEFORE_NOTIFY) {
                notifyFailure(
                    context,
                    context.getString(R.string.bk_notif_failed_repeated, failures)
                )
            }
            Result.retry()
        }
    }

    private fun notifyFailure(context: Context, text: String) {
        ensureChannel(context)

        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val openApp = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "backup")
        }
        val pending = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            openApp,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_playback_notification)
            .setContentTitle(context.getString(R.string.bk_notif_failed_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        NotificationManagerCompat.from(context).notify(NOTIFICATION_TAG, NOTIFICATION_ID, builder.build())
    }

    companion object {
        private const val TAG = "ScheduledBackupWorker"
        private const val CHANNEL_ID = "scheduled_backups"
        private const val NOTIFICATION_TAG = "scheduled_backup"
        private const val NOTIFICATION_ID = 230
        const val UNIQUE_WORK_NAME = "koda_scheduled_backup"
        private const val MAX_TRANSIENT_FAILURES_BEFORE_NOTIFY = 3

        fun sync(context: Context) {
            val repo = ScheduledBackupRepository(context)
            val enabled = repo.enabled.value
            val uri = repo.folderUri.value

            if (!enabled || uri == null) {
                WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
                return
            }

            val constraints = Constraints.Builder().apply {
                if (repo.requiresCharging.value) setRequiresCharging(true)
                if (repo.requiresDeviceIdle.value) setRequiresDeviceIdle(true)
                setRequiresBatteryNotLow(true)
                if (repo.requiresUnmetered.value) {
                    setRequiredNetworkType(NetworkType.UNMETERED)
                } else {
                    setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                }
            }.build()

            val request = PeriodicWorkRequestBuilder<ScheduledBackupWorker>(
                repo.frequencyHours.value,
                TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        private fun ensureChannel(context: Context) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.bk_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.bk_channel_desc)
            }
            manager.createNotificationChannel(channel)
        }
    }
}
