package com.ivor.ivormusic.service

import com.ivor.ivormusic.util.KLog

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
    
    // Pointed at the same channel as MusicProgressLiveUpdate. Left to itself,
    // DefaultMediaNotificationProvider creates its own "Now playing" channel,
    // and the system notification settings screen lists two identical-looking
    // entries for one feature.
    private val defaultProvider = DefaultMediaNotificationProvider.Builder(context)
        .setChannelId(MusicProgressLiveUpdate.CHANNEL_ID)
        .setChannelName(com.ivor.ivormusic.R.string.now_playing_channel_name)
        .build()
    
    companion object {
        private const val TAG = "LiveUpdateMediaNotificationProvider"

        /** What NotificationCompat.setRequestPromotedOngoing writes. */
        private const val EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"

        /**
         * Remove the channel Media3 created for itself before we started
         * sharing one. Without this an upgrading user still sees two "Now
         * playing" entries in system settings - the shared one, plus the old
         * one sitting there empty forever.
         *
         * Safe to call every start: deleting an absent channel is a no-op.
         */
        fun deleteLegacyMediaChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            if (DefaultMediaNotificationProvider.DEFAULT_CHANNEL_ID ==
                MusicProgressLiveUpdate.CHANNEL_ID
            ) return
            runCatching {
                androidx.core.app.NotificationManagerCompat.from(context)
                    .deleteNotificationChannel(
                        DefaultMediaNotificationProvider.DEFAULT_CHANNEL_ID
                    )
            }
        }
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
                // NotificationCompat - recovering a MediaStyle notification
                // through the compat builder risks losing the media template.
                //
                // API 36's Notification.Builder has setShortCriticalText but no
                // setter for promotion: only NotificationCompat has
                // setRequestPromotedOngoing, and all it does is write this
                // extra. So set the extra directly. setExtras replaces the
                // bundle wholesale, hence the copy, and it runs before
                // setShortCriticalText so it cannot clobber it.
                val builder = Notification.Builder.recoverBuilder(context, originalNotification)
                    .setOngoing(true)
                    // Colorized and promoted are mutually exclusive; a
                    // colorized notification is silently refused promotion.
                    .setColorized(false)

                val extras = Bundle(originalNotification.extras ?: Bundle())
                extras.putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true)
                builder.setExtras(extras)
                builder.setShortCriticalText(title.toString())

                return MediaNotification(
                    defaultNotification.notificationId,
                    builder.build()
                )
            } catch (e: Exception) {
                // Promotion is a nicety; a failure here must never cost the
                // user their transport controls.
                KLog.w(TAG, "Failed to add Live Update flag", e)
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

    override fun getNotificationChannelInfo(): MediaNotification.Provider.NotificationChannelInfo =
        defaultProvider.notificationChannelInfo
}
