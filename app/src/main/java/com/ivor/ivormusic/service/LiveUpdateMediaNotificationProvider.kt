package com.ivor.ivormusic.service

import android.app.Notification
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import com.ivor.ivormusic.data.ThemePreferences

/**
 * Custom media notification provider that enables Android 16 Live Updates for
 * music playback notifications.
 *
 * On Android 16+, this adds the requestPromotedOngoing flag to make the media
 * notification appear prominently on the lock screen, at the top of the shade,
 * and as a status bar chip.
 *
 * Gated by the same preference as [MusicProgressLiveUpdate], read fresh on each
 * build because the provider only holds a Context. Without that gate the
 * setting would be a half-truth: the separate progress chip would disappear
 * while the media notification carried on promoting itself.
 */
@UnstableApi
class LiveUpdateMediaNotificationProvider(
    private val context: Context
) : MediaNotification.Provider {
    
    private val defaultProvider = DefaultMediaNotificationProvider.Builder(context).build()
    
    companion object {
        private const val TAG = "LiveUpdateMediaNotificationProvider"
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
        
        // Android 16+ only, and only when the user has asked for it. The
        // platform check lives inside isLivePlaybackUpdatesEnabled, but the
        // SDK_INT guard has to be here too for the API 36 builder calls below.
        if (Build.VERSION.SDK_INT >= 36 &&
            ThemePreferences.isLivePlaybackUpdatesEnabled(context)
        ) {
            try {
                val originalNotification = defaultNotification.notification
                val title = originalNotification.extras
                    ?.getCharSequence(Notification.EXTRA_TITLE)
                    ?: "Music"

                // The default provider hands back a platform Notification, so
                // this rebuilds through the platform builder rather than
                // NotificationCompat. compileSdk is 36, so the Live Update
                // setters are callable directly - no reflection needed.
                val builder = Notification.Builder.recoverBuilder(context, originalNotification)
                    .setOngoing(true)
                    // Colorized and promoted are mutually exclusive; a
                    // colorized notification is silently refused promotion.
                    .setColorized(false)
                    .setRequestPromotedOngoing(true)
                    .setShortCriticalText(title.toString())

                return MediaNotification(
                    defaultNotification.notificationId,
                    builder.build()
                )
            } catch (e: Exception) {
                // Promotion is a nicety; a failure here must never cost the
                // user their transport controls.
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
