package com.ivor.ivormusic.service

import android.app.Notification
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession

/**
 * Custom media notification provider that enables Android 16 Live Updates (Live Activities)
 * for music playback notifications.
 * 
 * On Android 16+, this adds the requestPromotedOngoing flag to make the media notification
 * appear as a Live Activity - showing prominently on lock screen, notification shade top,
 * and as a status bar chip.
 */
@UnstableApi
class LiveUpdateMediaNotificationProvider(
    private val context: Context
) : MediaNotification.Provider {
    
    private val defaultProvider = DefaultMediaNotificationProvider.Builder(context).build()
    
    companion object {
        private const val TAG = "LiveUpdateMediaNotificationProvider"
        private const val LIVE_UPDATE_EXTRA = "android.requestPromotedOngoing"
    }
    
    override fun createNotification(
        mediaSession: MediaSession,
        customLayout: com.google.common.collect.ImmutableList<androidx.media3.session.CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback
    ): MediaNotification {
        // Get the default notification from the default provider
        val defaultNotification = defaultProvider.createNotification(
            mediaSession, 
            customLayout, 
            actionFactory, 
            onNotificationChangedCallback
        )
        
        // For Android 16+, rebuild notification with Live Update flag
        if (Build.VERSION.SDK_INT >= 36) { // API 36 = Android 16
            try {
                val originalNotification = defaultNotification.notification
                
                // Rebuild the notification with the Live Update extra
                val rebuiltNotification = Notification.Builder.recoverBuilder(context, originalNotification)
                    .apply {
                        // Add the live updates flag to extras
                        extras.putBoolean(LIVE_UPDATE_EXTRA, true)
                    }
                    .build()
                
                // Return new MediaNotification with the rebuilt notification
                return MediaNotification(
                    defaultNotification.notificationId,
                    rebuiltNotification
                )
            } catch (e: Exception) {
                // If rebuilding fails, return original notification
                android.util.Log.w(TAG, "Failed to add Live Update flag", e)
            }
        }
        
        return defaultNotification
    }
    
    override fun handleCustomCommand(
        session: MediaSession,
        action: String,
        extras: Bundle
    ): Boolean {
        return defaultProvider.handleCustomCommand(session, action, extras)
    }
}
