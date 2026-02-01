package com.ivor.ivormusic.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ivor.ivormusic.MainActivity
import com.ivor.ivormusic.R

/**
 * Manages a secondary ProgressStyle notification that shows music playback progress
 * as an Android 16 Live Update (status bar chip, prominent placement).
 * 
 * This is separate from the MediaStyle notification which provides playback controls.
 * The Live Update shows:
 * - Song title in the chip
 * - Playback progress bar
 * - Time remaining
 */
class MusicProgressLiveUpdate(private val context: Context) {
    
    companion object {
        private const val TAG = "MusicProgressLiveUpdate"
        private const val CHANNEL_ID = "music_live_update"
        private const val CHANNEL_NAME = "Now Playing"
        private const val NOTIFICATION_ID = 9999
    }
    
    private val notificationManager = NotificationManagerCompat.from(context)
    private var isShowing = false
    
    init {
        createNotificationChannel()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW // Low importance, no sound
            ).apply {
                description = "Shows what's currently playing"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    // Track last state to prevent redundant updates (stutter)
    private var lastProgress = -1
    private var lastChipText = ""
    private var lastTitle = ""
    
    /**
     * Show or update the Live Update notification with current playback progress.
     */
    fun updateProgress(
        songTitle: String,
        artistName: String,
        currentPositionMs: Long,
        durationMs: Long,
        isPlaying: Boolean
    ) {
        // Only show on Android 16+ where Live Updates exist
        if (Build.VERSION.SDK_INT < 36) return
        
        if (durationMs <= 0) return
        
        val progress = ((currentPositionMs.toFloat() / durationMs) * 100).toInt().coerceIn(0, 100)
        val remainingMs = durationMs - currentPositionMs
        val remainingMin = (remainingMs / 60000).toInt()
        val remainingSec = ((remainingMs % 60000) / 1000).toInt()
        
        // Short text for chip (max ~7 chars for full display)
        val chipText = if (remainingMin > 0) "${remainingMin}m" else "${remainingSec}s"
        
        // Check if anything visually changed
        if (isShowing && 
            progress == lastProgress && 
            chipText == lastChipText && 
            songTitle == lastTitle) {
            return // No visual change, skip update to prevent stutter
        }
        
        // Update valid state
        lastProgress = progress
        lastChipText = chipText
        lastTitle = songTitle
        
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(songTitle)
            .setContentText(artistName)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setColorized(false) // Required for Live Updates
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        
        // Build the notification first
        var notification = builder.build()
        
        // Apply Live Update flags via native builder
        try {
            val nativeBuilder = Notification.Builder.recoverBuilder(context, notification)
            nativeBuilder.setOngoing(true)
            nativeBuilder.setColorized(false)
            
            // Use reflection for Android 16 APIs
            try {
                nativeBuilder.javaClass.getMethod("setRequestPromotedOngoing", Boolean::class.java)
                    .invoke(nativeBuilder, true)
                
                nativeBuilder.javaClass.getMethod("setShortCriticalText", CharSequence::class.java)
                    .invoke(nativeBuilder, chipText)
            } catch (e: Exception) {
                Log.d(TAG, "Reflection failed, using extras fallback")
                val extras = Bundle()
                extras.putBoolean("android.requestPromotedOngoing", true)
                nativeBuilder.setExtras(extras)
            }
            
            notification = nativeBuilder.build()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply Live Update flags", e)
        }
        
        notificationManager.notify(NOTIFICATION_ID, notification)
        isShowing = true
    }
    
    /**
     * Hide the Live Update notification (when playback stops).
     */
    fun hide() {
        if (isShowing) {
            notificationManager.cancel(NOTIFICATION_ID)
            isShowing = false
        }
    }
    
    /**
     * Check if the notification is currently showing.
     */
    fun isShowing(): Boolean = isShowing
}
