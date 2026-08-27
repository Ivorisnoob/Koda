package com.ivor.ivormusic.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * State for the background upload check (#137/#138): which locally-followed
 * channels are allowed to notify, and how far each channel's uploads have
 * already been seen.
 *
 * The muted set is profile-scoped, like [LocalSubscriptionsRepository]: it
 * shapes what an identity gets told about, so switching profiles switches the
 * mute list with everything else that belongs to that identity's feed. The
 * last-seen timestamps are device bookkeeping rather than identity - they say
 * what this phone has processed, whoever is signed in - so they stay
 * un-suffixed and shared.
 *
 * Backing flows are process-wide, same justification as its four siblings:
 * muting a channel in Settings must be visible to the worker and to any other
 * surface holding its own instance of this class.
 */
class UploadCheckRepository(context: Context) {

    private val appContext = context.applicationContext

    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        synchronized(LOCK) {
            if (sharedMutedChannels == null) {
                sharedMutedChannels = MutableStateFlow(loadMuted())
            }
        }
    }

    /** Which profile's mute list this is; read fresh, never captured. */
    private fun mutedKey() = ProfileManager.profileScopedKey(
        KEY_MUTED_CHANNELS,
        ProfileManager.activeProfileId(appContext),
        ProfileManager.legacyProfileId(appContext)
    )

    private fun loadMuted(): Set<String> =
        prefs.getStringSet(mutedKey(), emptySet()) ?: emptySet()

    private val mutedState: MutableStateFlow<Set<String>> get() = sharedMutedChannels!!

    val mutedChannelIds: StateFlow<Set<String>> get() = mutedState.asStateFlow()

    fun isMuted(channelId: String): Boolean = mutedState.value.contains(channelId)

    fun setMuted(channelId: String, muted: Boolean) {
        val next = if (muted) {
            mutedState.value + channelId
        } else {
            mutedState.value - channelId
        }
        prefs.edit().putStringSet(mutedKey(), next).apply()
        mutedState.value = next
    }

    // --- Last-seen bookkeeping ---

    /**
     * The newest upload timestamp already accounted for on [channelId], or
     * null when the channel has never been checked - which is the worker's
     * signal to set a baseline silently instead of notifying.
     */
    @Synchronized
    fun lastSeenFor(channelId: String): Long? {
        val stored = prefs.getLong(lastSeenKey(channelId), -1L)
        return stored.takeIf { it >= 0L }
    }

    @Synchronized
    fun markSeen(channelId: String, timestampMs: Long) {
        prefs.edit().putLong(lastSeenKey(channelId), timestampMs).apply()
    }

    private fun lastSeenKey(channelId: String) = "${KEY_LAST_SEEN}_$channelId"

    companion object {
        private const val TAG = "UploadCheckRepository"
        private const val PREFS_NAME = "upload_check"
        private const val KEY_MUTED_CHANNELS = "upload_muted_channels"
        private const val KEY_LAST_SEEN = "upload_last_seen"

        private val LOCK = Any()

        @Volatile
        private var sharedMutedChannels: MutableStateFlow<Set<String>>? = null
    }
}
