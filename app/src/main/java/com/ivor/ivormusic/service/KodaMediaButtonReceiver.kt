package com.ivor.ivormusic.service

import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaButtonReceiver
import com.ivor.ivormusic.data.PlaybackSessionRepository
import com.ivor.ivormusic.data.ThemePreferences

/**
 * Media3's receiver, starting [MusicService] for a play press only when there
 * is something to resume.
 *
 * A play press with no session alive is started as a foreground service, and
 * the service then has a few seconds to post its notification. That needs a
 * queue: `onPlaybackResumption` rebuilds the saved one, and with nothing saved
 * (a fresh install, cleared data, nothing played yet) it refuses, the player
 * stays empty, no notification is ever posted, and Android kills the app with
 * "Context.startForegroundService() did not then call startForeground()".
 * Media3 leaves this decision to the app through [shouldStartForegroundService].
 *
 * The check mirrors what resumption reads: the saved queue file, or the
 * last-played song it falls back to. Both are a file stat and a preference
 * read, cheap enough for the main thread a receiver runs on.
 */
@OptIn(UnstableApi::class)
class KodaMediaButtonReceiver : MediaButtonReceiver() {
    override fun shouldStartForegroundService(context: Context, intent: Intent): Boolean =
        PlaybackSessionRepository(context).hasSavedQueue() ||
            ThemePreferences.hasLastPlayedSong(context)
}
