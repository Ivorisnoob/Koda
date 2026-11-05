package com.ivor.ivormusic.data

import android.content.Context
import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * The one mode a playlist is kept to. Persisted by [name]; these constants are frozen.
 */
enum class PlaylistMode {
    MUSIC,
    VIDEO;

    val other: PlaylistMode get() = if (this == MUSIC) VIDEO else MUSIC
}

/**
 * Playlists the user has kept to one mode (#357): a playlist of talks or vlogs
 * from the account has no place on music Home, and a playlist of songs may not
 * want to be in video mode's Library. A playlist with no entry shows in both,
 * as every playlist did before this existed.
 *
 * Like [HiddenPlaylistsRepository] it is a filter Koda applies to the lists it
 * draws, never a write to YouTube, and keyed by playlist id so one entry covers
 * the playlist whether it is the account's, saved or on the device. Unlike
 * hiding, the way back is in the other mode: a playlist kept to video mode
 * still shows there, and its menu there offers music mode again.
 *
 * Device-wide, with playlists, and read and written only through the one
 * [com.ivor.ivormusic.ui.home.HomeViewModel] both modes share, so it is not
 * process-wide state.
 */
class PlaylistModeStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val state = MutableStateFlow(load())

    /** Playlist id to the only mode it shows in. */
    val modes: StateFlow<Map<String, PlaylistMode>> = state.asStateFlow()

    /** Keep [playlistId] to [mode], or show it in both modes again when null. */
    fun set(playlistId: String, mode: PlaylistMode?) {
        if (playlistId.isBlank()) return
        val current = state.value
        if (current[playlistId] == mode) return
        val next = if (mode == null) current - playlistId else current + (playlistId to mode)
        state.value = next
        val json = JSONObject()
        next.forEach { (id, kept) -> json.put(id, kept.name) }
        prefs.edit().putString(KEY_MODES, json.toString()).apply()
    }

    private fun load(): Map<String, PlaylistMode> {
        val raw = prefs.getString(KEY_MODES, null) ?: return emptyMap()
        return try {
            val json = JSONObject(raw)
            buildMap {
                json.keys().forEach { id ->
                    val mode = PlaylistMode.entries.firstOrNull { it.name == json.optString(id) }
                    if (mode != null && id.isNotBlank()) put(id, mode)
                }
            }
        } catch (e: org.json.JSONException) {
            // A corrupt store only brings every playlist back into both modes,
            // which is visible and fixable; it must not take the Library down.
            KLog.w(TAG, "Could not read playlist modes", e)
            emptyMap()
        }
    }

    companion object {
        private const val TAG = "PlaylistModes"
        const val PREFS_NAME = "playlist_modes"
        private const val KEY_MODES = "modes"
    }
}

/** Whether a playlist with this id is drawn in [mode]. */
fun Map<String, PlaylistMode>.showsIn(playlistId: String, mode: PlaylistMode): Boolean =
    this[playlistId]?.let { it == mode } ?: true
