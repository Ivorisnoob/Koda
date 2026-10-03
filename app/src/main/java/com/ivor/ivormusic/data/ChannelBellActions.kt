package com.ivor.ivormusic.data

import android.content.Context
import com.ivor.ivormusic.R
import com.ivor.ivormusic.work.UploadCheckWorker
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * The one place that decides what picking a bell level means (#86).
 *
 * Three surfaces draw the bell - the watch page, the channel page and the
 * Subscriptions tab's channel list - each over its own ViewModel, so the write,
 * the wording and what Koda does about it live here rather than three times.
 *
 * The level is written to the account, which is what the official app and
 * YouTube's own inbox follow. Koda's upload check follows it too: a channel on
 * All is checked in the background like a device follow ([UploadCheckWorker]).
 * Choosing All while that check is off, or while notifications are blocked,
 * would otherwise look like it worked and then never notify, so the message
 * says what is missing instead of YouTube's plain confirmation.
 *
 * Messages go to [messages], collected by the app-wide snackbar host: two of
 * the three surfaces are overlays above the NavHost, where a per-screen host
 * would be drawn underneath them.
 */
class ChannelBellActions(
    context: Context,
    private val youtubeRepository: YouTubeRepository
) {
    private val appContext = context.applicationContext

    /**
     * Move [bell] to [level]. Returns the channel's new bell, or null when the
     * write did not land and the caller should put its old one back. Either
     * way a message is posted.
     */
    suspend fun change(bell: ChannelBell, level: BellLevel, channelName: String): ChannelBell? {
        val change = youtubeRepository.setChannelBell(bell, level)
        if (change == null) {
            post(appContext.getString(R.string.bell_change_failed, channelName))
            return null
        }
        val message = if (level == BellLevel.ALL && !kodaNotifiesUploads()) {
            appContext.getString(R.string.bell_koda_uploads_off)
        } else {
            change.message ?: appContext.getString(
                R.string.bell_changed,
                appContext.getString(
                    when (level) {
                        BellLevel.ALL -> R.string.bell_level_all
                        BellLevel.PERSONALIZED -> R.string.bell_level_personalized
                        BellLevel.NONE -> R.string.bell_level_none
                    }
                )
            )
        }
        post(message)
        return change.bell
    }

    /** Fresh reads: Settings holds its own ThemePreferences, and permission can change any time. */
    private fun kodaNotifiesUploads(): Boolean =
        ThemePreferences(appContext).getUploadNotificationsEnabled() &&
            UploadCheckWorker.canPostNotifications(appContext)

    private fun post(message: String) {
        messagesFlow.tryEmit(message)
    }

    companion object {
        // A transient bus, not state: nothing replays, and a message nobody is
        // showing is dropped rather than kept.
        private val messagesFlow = MutableSharedFlow<String>(extraBufferCapacity = 4)
        val messages: SharedFlow<String> = messagesFlow.asSharedFlow()
    }
}
