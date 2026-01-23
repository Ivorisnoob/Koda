package com.ivor.ivormusic.data

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ivor.ivormusic.MainActivity
import com.ivor.ivormusic.R

/**
 * Helper class for managing download notifications with support for Android 16 Live Updates.
 * 
 * Key features:
 * - Progress-based notification that updates in real-time
 * - Auto-dismisses when download completes
 * - Supports Android 16+ "Live Updates" (progress-centric notifications) for prominent display
 */
class DownloadNotificationHelper(private val context: Context) {
    
    companion object {
        private const val CHANNEL_ID = "download_channel"
        private const val CHANNEL_NAME = "Downloads"
        private const val CHANNEL_DESCRIPTION = "Song download progress"
        
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
                NotificationManager.IMPORTANCE_LOW // Low importance for progress notifications
            ).apply {
                description = CHANNEL_DESCRIPTION
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Show or update download progress notification.
     */
    fun showDownloadProgress(
        songId: String,
        songTitle: String,
        artistName: String,
        progress: Float, // 0.0 to 1.0
        bytesDownloaded: Long,
        totalBytes: Long
    ) {
        if (!hasNotificationPermission()) return
        
        val notificationId = getNotificationId(songId)
        val progressPercent = (progress * 100).toInt()
        
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading: $songTitle")
            .setContentText(artistName)
            .setProgress(100, progressPercent, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        
        // Add subtext with download info
        if (totalBytes > 0) {
            val downloadedMB = bytesDownloaded / (1024 * 1024f)
            val totalMB = totalBytes / (1024 * 1024f)
            builder.setSubText("%.1f / %.1f MB".format(downloadedMB, totalMB))
        }
        
        // Android 16+ (API 36+) Live Updates (Promoted Ongoing Notifications)
        if (Build.VERSION.SDK_INT >= 36) {
            try {
                builder.setOngoing(true)
                builder.setColorized(false)
                builder.extras.putBoolean("android.requestPromotedOngoing", true)
            } catch (e: Exception) {
                // Ignore if setting extras fails
            }
        }
        
        var notification = builder.build()
        
        // Android 16 (API 36+) Rebuild for Chip Text
        if (Build.VERSION.SDK_INT >= 36) {
            try {
                val nativeBuilder = Notification.Builder.recoverBuilder(context, notification)
                nativeBuilder.setOngoing(true)
                nativeBuilder.setColorized(false)
                
                try {
                     nativeBuilder.javaClass.getMethod("setRequestPromotedOngoing", Boolean::class.java)
                         .invoke(nativeBuilder, true)
                     
                     // Use percentage for chip text, e.g. "50%"
                     nativeBuilder.javaClass.getMethod("setShortCriticalText", CharSequence::class.java)
                         .invoke(nativeBuilder, "$progressPercent%")
                } catch (e: Exception) {
                     // Reflection failed
                }
                notification = nativeBuilder.build()
            } catch (e: Exception) {
                // Rebuild failed, use original
            }
        }
        
        notificationManager.notify(notificationId, notification)
        return // Early return since we notified
    }
    
    /**
     * Show download complete notification.
     */
    fun showDownloadComplete(
        songId: String,
        songTitle: String,
        artistName: String
    ) {
        if (!hasNotificationPermission()) return
        
        val notificationId = getNotificationId(songId)
        
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Downloaded: $songTitle")
            .setContentText(artistName)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        
        notificationManager.notify(notificationId, builder.build())
        
        // Auto-dismiss after 3 seconds
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            dismissNotification(songId)
        }, 3000)
    }
    
    /**
     * Show download failed notification.
     */
    fun showDownloadFailed(
        songId: String,
        songTitle: String
    ) {
        if (!hasNotificationPermission()) return
        
        val notificationId = getNotificationId(songId)
        
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Download failed")
            .setContentText(songTitle)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        
        notificationManager.notify(notificationId, builder.build())
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
