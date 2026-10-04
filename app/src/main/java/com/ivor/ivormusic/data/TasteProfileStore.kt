package com.ivor.ivormusic.data

import android.content.Context
import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** An artist the user chose: in taste setup, or by following them. */
data class TasteArtist(
    /** The artist's `UC...` browse id. Blank for one known only by name (carried over from history). */
    val id: String,
    val name: String,
    val thumbnailUrl: String? = null
) {
    /** One identity whether or not the id is known, so a name-only pick and its resolved form are the same artist. */
    val key: String get() = id.ifBlank { "name:" + name.trim().lowercase() }

    fun sameArtistAs(other: TasteArtist): Boolean =
        (id.isNotBlank() && id == other.id) || name.trim().equals(other.name.trim(), ignoreCase = true)
}

/**
 * A genre the user chose, by name. The name is the whole of it: it is shown,
 * and it is what recommendations search for.
 */
data class TasteGenre(val title: String)

/** A song the user said yes to in the taste deck: a seed, not a library entry. */
data class TasteSong(
    val id: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String? = null
)

data class TasteProfileData(
    val artists: List<TasteArtist> = emptyList(),
    val genres: List<TasteGenre> = emptyList(),
    val songs: List<TasteSong> = emptyList()
) {
    val isEmpty: Boolean get() = artists.isEmpty() && genres.isEmpty() && songs.isEmpty()
}

/**
 * What the user has *said* they like, beside what the play history shows they
 * like: the artists, genres and songs picked in taste setup, and the artists
 * followed since.
 *
 * **Why it exists.** [RecommendationEngine] built its taste profile from plays,
 * likes and searches alone, which is nothing at all on a new install: the first
 * Home was "trending music" for everybody. This is the half that can be filled
 * in on day one, and it stays useful afterwards as the explicit signal - a
 * followed artist is a stronger statement than three plays.
 *
 * **Per profile, with the history and likes it sits beside** (keyed through
 * [ProfileManager.historyScopedKey], so the profile that kept the history also
 * keeps this), copied to a profile signed in from a device-only one, and
 * carried in backups as profile data. The setup-seen flag is the one
 * device-wide value here: it is about whether this install has offered the
 * screen, not about anyone's taste.
 *
 * **Process-wide state**, the fourteenth such store, for the stated reason and
 * no other: following an artist on their page has to show in the Library and
 * in an open taste screen, each of which holds its own instance.
 */
class TasteProfileStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        ensureLoaded(appContext)
    }

    val profile: StateFlow<TasteProfileData> get() = shared.asStateFlow()

    /** The active profile's taste, re-read first if a switch has happened under this instance. */
    fun current(): TasteProfileData {
        ensureLoaded(appContext)
        return shared.value
    }

    fun isFollowing(artist: TasteArtist): Boolean = current().artists.any { it.sameArtistAs(artist) }

    fun follow(artist: TasteArtist) {
        if (artist.name.isBlank()) return
        update { data ->
            // A pick known only by name is replaced by the same artist with an
            // id and a picture, rather than sitting beside it.
            val others = data.artists.filterNot { it.sameArtistAs(artist) }
            data.copy(artists = (listOf(artist) + others).take(MAX_ARTISTS))
        }
    }

    fun unfollow(artist: TasteArtist) {
        update { data -> data.copy(artists = data.artists.filterNot { it.sameArtistAs(artist) }) }
    }

    fun setArtists(artists: List<TasteArtist>) {
        update { it.copy(artists = artists.distinctBy(TasteArtist::key).take(MAX_ARTISTS)) }
    }

    fun setGenres(genres: List<TasteGenre>) {
        update { it.copy(genres = genres.distinctBy(TasteGenre::title).take(MAX_GENRES)) }
    }

    fun addSong(song: TasteSong) {
        if (song.id.isBlank()) return
        update { data ->
            data.copy(songs = (listOf(song) + data.songs.filterNot { it.id == song.id }).take(MAX_SONGS))
        }
    }

    fun removeSong(songId: String) {
        update { data -> data.copy(songs = data.songs.filterNot { it.id == songId }) }
    }

    fun clear() {
        update { TasteProfileData() }
    }

    /** Whether this install has shown taste setup, finished or skipped. */
    var setupSeen: Boolean
        get() = prefs.getBoolean(KEY_SETUP_SEEN, false)
        set(value) = prefs.edit().putBoolean(KEY_SETUP_SEEN, value).apply()

    private fun update(change: (TasteProfileData) -> TasteProfileData) {
        synchronized(lock) {
            ensureLoaded(appContext)
            val next = change(shared.value)
            if (next == shared.value) return
            shared.value = next
            prefs.edit()
                .putString(keyFor(appContext, ProfileManager.requireActiveProfileId(appContext)), encode(next))
                .apply()
        }
    }

    companion object {
        internal const val PREFS_NAME = "taste_profile"
        // Both keys were renamed once before the feature shipped, to drop the
        // picks made against the per-country genre list during testing. They
        // are stored names from the first release on: do not rename them again.
        internal const val KEY_PROFILE = "taste"
        private const val KEY_SETUP_SEEN = "setup_offered"

        /** Generous: a followed-artists list is a library, not a seed list. */
        private const val MAX_ARTISTS = 300
        private const val MAX_GENRES = 20
        private const val MAX_SONGS = 60

        private val lock = Any()
        private val shared = MutableStateFlow(TasteProfileData())
        @Volatile private var loadedFor: String? = null

        /** [profileId]'s key, for backups and a new profile's copy. */
        internal fun keyFor(context: Context, profileId: String): String =
            ProfileManager.historyScopedKey(KEY_PROFILE, profileId, context)

        private fun ensureLoaded(context: Context) {
            val active = ProfileManager.requireActiveProfileId(context)
            if (loadedFor == active) return
            synchronized(lock) {
                if (loadedFor == active) return
                val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                shared.value = decode(prefs.getString(keyFor(context, active), null))
                loadedFor = active
            }
        }

        /** Re-point the shared state at the profile now stored as active. */
        internal fun reloadForActiveProfile(context: Context) {
            synchronized(lock) { loadedFor = null }
            ensureLoaded(context.applicationContext)
        }

        /** Give [toProfileId] a copy of [fromProfileId]'s taste, if it has none. */
        internal fun copyProfileData(context: Context, fromProfileId: String, toProfileId: String) {
            ProfileManager.copyScopedPreferences(
                context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
                listOf(KEY_PROFILE), fromProfileId, toProfileId
            ) { _, profileId -> keyFor(context, profileId) }
        }

        internal fun encode(data: TasteProfileData): String = JSONObject()
            .put("artists", JSONArray().apply {
                data.artists.forEach { artist ->
                    put(
                        JSONObject().put("id", artist.id).put("name", artist.name)
                            .put("thumb", artist.thumbnailUrl ?: JSONObject.NULL)
                    )
                }
            })
            .put("genres", JSONArray().apply {
                data.genres.forEach { put(JSONObject().put("title", it.title)) }
            })
            .put("songs", JSONArray().apply {
                data.songs.forEach { song ->
                    put(
                        JSONObject().put("id", song.id).put("title", song.title).put("artist", song.artist)
                            .put("thumb", song.thumbnailUrl ?: JSONObject.NULL)
                    )
                }
            })
            .toString()

        /** Anything unreadable is an empty profile: a taste store is never worth a crash. */
        internal fun decode(text: String?): TasteProfileData {
            if (text.isNullOrBlank()) return TasteProfileData()
            return try {
                val root = JSONObject(text)
                fun <T> list(name: String, read: (JSONObject) -> T?): List<T> {
                    val array = root.optJSONArray(name) ?: return emptyList()
                    return (0 until array.length()).mapNotNull { array.optJSONObject(it)?.let(read) }
                }
                fun JSONObject.textOrNull(name: String): String? =
                    if (isNull(name)) null else optString(name).takeIf { it.isNotBlank() }
                TasteProfileData(
                    artists = list("artists") { item ->
                        val name = item.optString("name").takeIf { it.isNotBlank() } ?: return@list null
                        TasteArtist(item.optString("id"), name, item.textOrNull("thumb"))
                    },
                    genres = list("genres") { item ->
                        val title = item.optString("title").takeIf { it.isNotBlank() } ?: return@list null
                        TasteGenre(title)
                    },
                    songs = list("songs") { item ->
                        val id = item.optString("id").takeIf { it.isNotBlank() } ?: return@list null
                        TasteSong(id, item.optString("title"), item.optString("artist"), item.textOrNull("thumb"))
                    }
                )
            } catch (e: org.json.JSONException) {
                KLog.w("TasteProfileStore", "Unreadable taste profile", e)
                TasteProfileData()
            }
        }
    }
}
