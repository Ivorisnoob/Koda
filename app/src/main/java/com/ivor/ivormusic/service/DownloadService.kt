package com.ivor.ivormusic.service

import com.ivor.ivormusic.util.KLog

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.ivor.ivormusic.data.DownloadNotificationHelper
import com.ivor.ivormusic.data.DownloadRepository
import com.ivor.ivormusic.data.DownloadStatus
import com.ivor.ivormusic.data.NotificationArtworkLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Keeps the process alive while downloads run.
 *
 * Transfers themselves live in [DownloadRepository], not here. Previously they
 * ran in whatever coroutine scope the caller happened to have - usually a
 * ViewModel's - so navigating away or letting the screen go killed the transfer
 * mid-file. The repository now owns a process-scoped worker, and this service
 * exists so Android does not reclaim that process while the worker is busy.
 *
 * It is also the owner of the progress notification: a foreground service must
 * present one, and having two competing progress notifications would be worse
 * than one that retitles itself as the queue advances.
 */
class DownloadService : Service() {

    companion object {
        private const val TAG = "DownloadService"

        /**
         * Bring the service up. Safe to call repeatedly; Android collapses
         * repeat starts onto the running instance.
         *
         * Failure is non-fatal by design: if the platform refuses a background
         * foreground-service start, downloads still proceed in the repository's
         * own scope. They simply lose the protection against the process being
         * reclaimed, which beats crashing.
         */
        fun start(context: Context) {
            val intent = Intent(context, DownloadService::class.java)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                KLog.w(TAG, "Could not start download service: ${e.message}")
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, DownloadService::class.java))
            } catch (e: Exception) {
                KLog.w(TAG, "Could not stop download service: ${e.message}")
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var repository: DownloadRepository
    private lateinit var notificationHelper: DownloadNotificationHelper
    private var started = false

    /** Artwork URLs already being fetched, so a fast progress stream does not
     *  kick off the same load dozens of times. */
    private val artworkRequested = mutableSetOf<String>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        repository = DownloadRepository.getInstance(this)
        notificationHelper = DownloadNotificationHelper(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must go foreground within a few seconds of being started, so post a
        // notification from whatever state is available right now rather than
        // waiting for the first progress emission.
        if (!promoteToForeground(buildCurrentNotification())) {
            // A service launched with startForegroundService must either
            // promote successfully or stop promptly. Continuing after a
            // refused promotion causes Android's fatal
            // ForegroundServiceDidNotStartInTimeException a few seconds later.
            stopSelf(startId)
            return START_NOT_STICKY
        }

        if (!started) {
            started = true
            observeProgress()
        }

        // Not sticky: a restarted service with no queue would show an empty
        // notification. The repository is the thing that knows whether work
        // remains, and it restarts the service itself when it does.
        return START_NOT_STICKY
    }

    private fun observeProgress() {
        serviceScope.launch {
            combine(
                repository.downloadProgress,
                repository.downloadQueue
            ) { progress, queue ->
                progress.values.firstOrNull { it.status == DownloadStatus.DOWNLOADING } to queue.size
            }.collect { (active, queued) ->
                if (active == null && queued == 0) return@collect

                // Fetch artwork once per item, off the notification path. The
                // first post for a song shows no icon; the fetch completes and
                // the next progress update carries the album art. Kicking it off
                // here rather than awaiting it keeps the foreground start
                // immediate.
                val artUrl = active?.request?.thumbnailUrl
                if (artUrl != null && NotificationArtworkLoader.cached(artUrl) == null &&
                    artworkRequested.add(artUrl)
                ) {
                    serviceScope.launch {
                        NotificationArtworkLoader.load(this@DownloadService, artUrl)
                        // Repost so the artwork lands even if this was the final
                        // progress emission.
                        refreshNotification()
                    }
                }

                val notification = if (active != null) {
                    notificationHelper.buildProgressNotification(
                        songTitle = active.request.title,
                        artistName = active.request.subtitle,
                        progress = active.progress,
                        bytesDownloaded = active.bytesDownloaded,
                        totalBytes = active.totalBytes,
                        queuedCount = queued,
                        isVideo = active.request.isVideo,
                        artwork = NotificationArtworkLoader.cached(artUrl)
                    )
                } else {
                    notificationHelper.buildProgressNotification(
                        songTitle = "Preparing downloads",
                        artistName = "",
                        progress = 0f,
                        bytesDownloaded = 0,
                        totalBytes = 0,
                        queuedCount = queued
                    )
                }
                // notify() rather than startForeground(): the service is already
                // foreground and re-promoting on every percent would be wasteful.
                if (NotificationManagerCompat.from(this@DownloadService)
                        .areNotificationsEnabled()
                ) {
                    NotificationManagerCompat.from(this@DownloadService)
                        .notify(
                            DownloadNotificationHelper.FOREGROUND_NOTIFICATION_ID,
                            notification
                        )
                }
            }
        }
    }

    private fun buildCurrentNotification(): android.app.Notification {
        val active = repository.downloadProgress.value.values
            .firstOrNull { it.status == DownloadStatus.DOWNLOADING }
        val queued = repository.downloadQueue.value.size
        return notificationHelper.buildProgressNotification(
            songTitle = active?.request?.title ?: "Preparing downloads",
            artistName = active?.request?.subtitle ?: "",
            progress = active?.progress ?: 0f,
            bytesDownloaded = active?.bytesDownloaded ?: 0,
            totalBytes = active?.totalBytes ?: 0,
            queuedCount = queued,
            isVideo = active?.request?.isVideo ?: false,
            artwork = NotificationArtworkLoader.cached(active?.request?.thumbnailUrl)
        )
    }

    /**
     * Repost the current state. Used when artwork finishes loading, since that
     * may happen after the last progress emission.
     */
    private fun refreshNotification() {
        val manager = NotificationManagerCompat.from(this)
        if (!manager.areNotificationsEnabled()) return
        val stillRunning = repository.downloadProgress.value.values.any {
            it.status == DownloadStatus.DOWNLOADING
        }
        if (!stillRunning) return
        manager.notify(
            DownloadNotificationHelper.FOREGROUND_NOTIFICATION_ID,
            buildCurrentNotification()
        )
    }

    private fun promoteToForeground(notification: android.app.Notification): Boolean {
        return try {
            ServiceCompat.startForeground(
                this,
                DownloadNotificationHelper.FOREGROUND_NOTIFICATION_ID,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                } else {
                    0
                }
            )
            true
        } catch (e: Exception) {
            KLog.w(TAG, "startForeground refused: ${e.message}")
            false
        }
    }

    /**
     * Android 15 caps background dataSync foreground-service time. Once the
     * system grants this callback only a few seconds remain before it throws a
     * fatal RemoteServiceException, so relinquish foreground state and stop
     * synchronously. DownloadRepository owns the queue and can continue while
     * the process remains alive; the service itself must not overstay.
     */
    override fun onTimeout(startId: Int, fgsType: Int) {
        KLog.w(TAG, "dataSync foreground-service time limit reached")
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
