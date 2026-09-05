package com.ivor.ivormusic.data

import android.content.Context
import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * A playlist the user has told Koda not to show them, kept with enough of its
 * name to be un-hidden by name rather than by opaque id.
 */
data class HiddenPlaylist(
    val playlistId: String,
    val name: String,
    val uploaderName: String = "",
    val thumbnailUrl: String? = null,
    val hiddenAt: Long = System.currentTimeMillis()
)

/**
 * Playlists the user does not want to see in Koda.
 *
 * **Local only, and it has to be.** The two cases this exists for are a
 * playlist full of videos that is meaningless in music mode, and a playlist on
 * the account that the user simply does not want in front of them - neither of
 * which is a request to change anything on YouTube. Unsubscribing from your own
 * playlist is not what was asked for, and deleting it certainly is not, so the
 * whole feature stops at this device. It is the same shape as
 * [NotInterestedRepository] for the same reason: the only thing that can work
 * immediately, and signed out, is a filter Koda applies itself.
 *
 * **Hiding is not the same as not having.** A hidden playlist stays on the
 * account, keeps playing if something links to it, and is still reachable
 * through a share link or search - hiding removes it from the lists Koda draws,
 * which is what the user asked for. Nothing here writes to InnerTube.
 *
 * **Device-wide rather than profile-scoped**, matching the split the rest of
 * the app already keeps: local subscriptions, the blocklist and watch history
 * are per profile because they shape recommendations; playlists, liked songs
 * and downloads are device-wide. A hidden id from another account simply never
 * matches anything, which costs nothing.
 *
 * The flow is process-wide for the reason [LocalSubscriptionsRepository]'s is:
 * hiding a playlist from its own page has to remove it from the Library grid
 * behind it and from the add-to-playlist sheet the player opens, and those are
 * different ViewModels holding different instances of everything.
 */
class HiddenPlaylistsRepository(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        synchronized(LOCK) {
            if (shared == null) shared = MutableStateFlow(load())
        }
    }

    private val state: MutableStateFlow<List<HiddenPlaylist>>
        get() = shared!!

    val hiddenPlaylists: StateFlow<List<HiddenPlaylist>> get() = state.asStateFlow()

    fun isHidden(playlistId: String?): Boolean =
        playlistId != null && state.value.any { it.playlistId == playlistId }

    /** Hide [playlist]. A second hide of the same id is a no-op, not a duplicate row. */
    fun hide(playlist: HiddenPlaylist) {
        if (playlist.playlistId.isBlank()) return
        val current = state.value
        if (current.any { it.playlistId == playlist.playlistId }) return
        publish(current + playlist)
    }

    fun unhide(playlistId: String) {
        val current = state.value
        val next = current.filterNot { it.playlistId == playlistId }
        if (next.size != current.size) publish(next)
    }

    fun clear() {
        if (state.value.isNotEmpty()) publish(emptyList())
    }

    private fun publish(next: List<HiddenPlaylist>) {
        state.value = next
        persist(next)
    }

    private fun persist(list: List<HiddenPlaylist>) {
        val array = JSONArray()
        list.forEach { entry ->
            array.put(
                JSONObject().apply {
                    put(KEY_ID, entry.playlistId)
                    put(KEY_NAME, entry.name)
                    put(KEY_UPLOADER, entry.uploaderName)
                    entry.thumbnailUrl?.let { put(KEY_THUMB, it) }
                    put(KEY_AT, entry.hiddenAt)
                }
            )
        }
        prefs.edit().putString(KEY_HIDDEN, array.toString()).apply()
    }

    private fun load(): List<HiddenPlaylist> {
        val raw = prefs.getString(KEY_HIDDEN, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                val id = obj.optString(KEY_ID).takeIf { it.isNotBlank() } ?: return@mapNotNull null
                HiddenPlaylist(
                    playlistId = id,
                    name = obj.optString(KEY_NAME),
                    uploaderName = obj.optString(KEY_UPLOADER),
                    thumbnailUrl = obj.optString(KEY_THUMB).takeIf { it.isNotBlank() },
                    hiddenAt = obj.optLong(KEY_AT, 0L)
                )
            }
        } catch (e: Exception) {
            // A corrupt store must not take the Library down with it; the worst
            // case is that hidden playlists come back, which is visible and
            // fixable, unlike a crash on launch.
            KLog.w(TAG, "Could not read hidden playlists", e)
            emptyList()
        }
    }

    companion object {
        private const val TAG = "HiddenPlaylists"
        const val PREFS_NAME = "hidden_playlists"
        private const val KEY_HIDDEN = "hidden"
        private const val KEY_ID = "id"
        private const val KEY_NAME = "name"
        private const val KEY_UPLOADER = "uploader"
        private const val KEY_THUMB = "thumbnail"
        private const val KEY_AT = "hiddenAt"

        private val LOCK = Any()
        private var shared: MutableStateFlow<List<HiddenPlaylist>>? = null
    }
}
