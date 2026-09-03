package com.ivor.ivormusic.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Watch, search and listening history are not recorded while this is on.
 *
 * Process-wide state, which the app otherwise keeps to a deliberately short
 * list. It earns a place on that list for the usual reason: it is toggled from
 * one surface - the account sheet - and has to be true immediately for the
 * five that read it, each holding its own repository instance. A per-instance
 * flag would leave the player still writing history after the sheet said it had
 * stopped, which for a privacy control is not a staleness bug but a broken
 * promise.
 *
 * What it suppresses, all at the write rather than at the call site so a new
 * surface cannot forget: the local video history, YouTube's own watch history
 * for both video and music, search suggestions, and listening stats. What it
 * deliberately does not touch: playback itself, downloads, playlists, likes and
 * subscriptions - those are things the user asked for by name, and silently
 * discarding one would be a different feature wearing this one's label.
 *
 * Persisted rather than held for the lifetime of the process, so it fails
 * closed: a session that outlives a process death - a service restarted after
 * the app was evicted - must not start recording again on its own.
 */
object IncognitoMode {

    private const val PREFS_NAME = "koda_incognito"
    private const val KEY_ENABLED = "incognito_enabled"

    private val lock = Any()

    @Volatile
    private var state: MutableStateFlow<Boolean>? = null

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun state(context: Context): MutableStateFlow<Boolean> {
        state?.let { return it }
        return synchronized(lock) {
            state ?: MutableStateFlow(prefs(context).getBoolean(KEY_ENABLED, false))
                .also { state = it }
        }
    }

    /** Observable for the UI: the indicator and the account sheet's switch. */
    fun enabled(context: Context): StateFlow<Boolean> = state(context).asStateFlow()

    /**
     * A direct read, for the write paths.
     *
     * Non-suspending and never blocking, because every caller is on the path of
     * something the user is waiting on - a play, a search - and this must not
     * be the reason any of them is slower.
     */
    fun isEnabled(context: Context): Boolean = state(context).value

    fun setEnabled(context: Context, enabled: Boolean) {
        // The preference is written first: an in-memory flag that is true while
        // the stored one is still false would record nothing this session and
        // everything after the next process death.
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
        state(context).value = enabled
    }
}
