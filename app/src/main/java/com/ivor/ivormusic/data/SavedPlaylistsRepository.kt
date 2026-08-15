package com.ivor.ivormusic.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * A playlist or album the user kept, stored as a reference rather than a copy.
 *
 * Only enough to draw the card and open the page: the tracks are fetched live
 * every time it is opened, so a playlist that gains songs upstream gains them
 * here too. Copying the contents would have frozen someone else's playlist at
 * the moment it was saved, which is the opposite of what saving one is for.
 */
data class SavedPlaylist(
    val id: String,
    /**
     * The identity as the rest of the UI knows it - a real URL for a remote
     * playlist. Kept alongside [id] because [PlaylistDisplayItem] derives one
     * from the other and albums, playlists and browse pages do not agree on
     * the shape.
     */
    val url: String,
    val name: String,
    val uploaderName: String,
    val thumbnailUrl: String? = null,
    val itemCount: Int = -1,
    val isAlbum: Boolean = false,
    val savedAt: Long = System.currentTimeMillis()
) {
    fun toDisplayItem(): PlaylistDisplayItem = PlaylistDisplayItem(
        name = name,
        url = url,
        uploaderName = uploaderName,
        itemCount = itemCount,
        thumbnailUrl = thumbnailUrl
    )
}

/**
 * Playlists and albums the user saved from search, an artist page or anywhere
 * else they found one, held on the device.
 *
 * Local rather than written to the YouTube account on purpose: most of this app
 * works signed out, and there is no known InnerTube endpoint for adding
 * somebody else's playlist to your library. Saving something you found should
 * not be the one thing that demands a Google login.
 *
 * **The backing state is process-wide**, for the same reason
 * [LocalSubscriptionsRepository]'s is: the save is taken on the playlist page,
 * which the search stack, Spotlight and the Library tab all open, and it has to
 * be visible in the Library the moment the user gets back there. With no DI
 * there is no shared instance to rely on, and a per-instance flow would leave
 * whichever ViewModel did not take the write showing a stale list.
 *
 * Device-wide rather than per-profile, matching playlists, liked songs and
 * downloads: what you kept is yours, not the Google account's.
 */
class SavedPlaylistsRepository(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        synchronized(LOCK) {
            if (shared == null) shared = MutableStateFlow(load())
        }
    }

    private val state: MutableStateFlow<List<SavedPlaylist>> get() = shared!!

    /** Everything saved, most recently saved first. */
    val savedPlaylists: StateFlow<List<SavedPlaylist>> get() = state.asStateFlow()

    fun isSaved(playlistId: String?): Boolean {
        if (playlistId.isNullOrBlank()) return false
        return state.value.any { it.id == playlistId }
    }

    /**
     * Keep [item]. Re-saving one already held refreshes its metadata (track
     * counts and cover art drift) without moving it up the list, so the Library
     * does not reshuffle itself when a page is merely reopened.
     */
    fun save(item: SavedPlaylist) {
        if (item.id.isBlank()) return
        val current = state.value
        val existing = current.firstOrNull { it.id == item.id }
        val next = if (existing == null) {
            listOf(item) + current
        } else {
            current.map {
                if (it.id != item.id) it else it.copy(
                    name = item.name.takeIf { n -> n.isNotBlank() } ?: it.name,
                    uploaderName = item.uploaderName.takeIf { u -> u.isNotBlank() } ?: it.uploaderName,
                    thumbnailUrl = item.thumbnailUrl ?: it.thumbnailUrl,
                    // A page that has not counted its tracks yet reports -1;
                    // that is "unknown", not "empty", and must not overwrite a
                    // count we already have.
                    itemCount = if (item.itemCount >= 0) item.itemCount else it.itemCount
                )
            }
        }
        persist(next)
    }

    fun remove(playlistId: String) {
        val next = state.value.filterNot { it.id == playlistId }
        if (next.size == state.value.size) return
        persist(next)
    }

    /** Returns the state after the toggle: true = now saved. */
    fun toggle(item: SavedPlaylist): Boolean =
        if (isSaved(item.id)) {
            remove(item.id)
            false
        } else {
            save(item)
            true
        }

    // ---------------- Persistence ----------------

    private fun persist(items: List<SavedPlaylist>) {
        state.value = items
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("url", item.url)
                    put("name", item.name)
                    put("uploader", item.uploaderName)
                    item.thumbnailUrl?.let { put("thumbnail", it) }
                    put("count", item.itemCount)
                    put("album", item.isAlbum)
                    put("savedAt", item.savedAt)
                }
            )
        }
        prefs.edit().putString(KEY_SAVED, array.toString()).apply()
    }

    private fun load(): List<SavedPlaylist> {
        val raw = prefs.getString(KEY_SAVED, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val obj = array.optJSONObject(index) ?: return@mapNotNull null
                val id = obj.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                SavedPlaylist(
                    id = id,
                    url = obj.optString("url").takeIf { it.isNotBlank() } ?: id,
                    name = obj.optString("name").takeIf { it.isNotBlank() } ?: "Playlist",
                    uploaderName = obj.optString("uploader"),
                    thumbnailUrl = obj.optString("thumbnail").takeIf { it.isNotBlank() },
                    itemCount = obj.optInt("count", -1),
                    isAlbum = obj.optBoolean("album", false),
                    savedAt = obj.optLong("savedAt", 0L)
                )
            }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Could not read saved playlists", e)
            emptyList()
        }
    }

    companion object {
        private const val TAG = "SavedPlaylistsRepo"
        private const val PREFS_NAME = "saved_playlists"
        private const val KEY_SAVED = "playlists"

        private val LOCK = Any()

        @Volatile
        private var shared: MutableStateFlow<List<SavedPlaylist>>? = null
    }
}
