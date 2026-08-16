package com.ivor.ivormusic.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * A playlist of videos held on the device, with the videos embedded.
 *
 * The video counterpart of [UserPlaylist], and a copy rather than a reference,
 * which is what separates it from [SavedPlaylist]: saving somebody else's
 * playlist keeps a pointer at a list they own, while this *is* the list. There
 * is nothing upstream to re-fetch.
 */
data class LocalVideoPlaylist(
    val id: String,
    val name: String,
    val videos: List<VideoItem> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
) {
    /**
     * The same playlist as every video-mode surface takes it.
     *
     * [VideoPlaylist.subtitle] carries "On this device", which is the only
     * thing that tells a local row from an account one in the Library list.
     */
    fun toVideoPlaylist(): VideoPlaylist = VideoPlaylist(
        playlistId = id,
        title = name,
        thumbnailUrl = videos.firstOrNull()?.thumbnailUrl,
        videoCountText = when (videos.size) {
            // No badge on an empty playlist: the placeholder tile behind it
            // already reads as empty, and "0 videos" stamped over artwork that
            // is not there is noise.
            0 -> null
            1 -> "1 video"
            else -> "${videos.size} videos"
        },
        subtitle = ON_DEVICE_SUBTITLE
    )

    companion object {
        const val ON_DEVICE_SUBTITLE = "On this device"
    }
}

/**
 * Video playlists that live only on this device.
 *
 * Video mode could only ever save into the signed-in account's playlists:
 * [YouTubeRepository.getVideoPlaylists] needs a session, so signed out the save
 * sheet listed nothing and its pinned Watch Later row posted to an endpoint
 * that answers 200 without doing anything. Music mode has had local playlists
 * since the beginning ([PlaylistRepository]); this is the missing half on the
 * video side, and it is deliberately shaped the same way so the two modes mean
 * the same thing by "your playlist".
 *
 * **The backing state is process-wide**, for the reason
 * [LocalSubscriptionsRepository]'s and [SavedPlaylistsRepository]'s are: with no
 * DI every ViewModel news up its own instance, and the save is taken in the
 * video player overlay while the list that has to show it is the Library tab,
 * which is a different ViewModel holding a different instance. A per-instance
 * flow would leave whichever one did not take the write showing a stale list.
 *
 * Device-wide rather than per-profile, matching local music playlists and
 * downloads: a playlist you assembled yourself is yours, not the Google
 * account's, and it must survive switching to a profile that has no account at
 * all.
 *
 * One JSON file per playlist rather than a single blob, because the videos are
 * embedded: adding one video to one playlist rewrites that playlist alone.
 */
class LocalVideoPlaylistsRepository(context: Context) {

    private val appContext = context.applicationContext
    private val dir = File(appContext.filesDir, DIR_NAME)

    init {
        synchronized(LOCK) {
            if (shared == null) {
                if (!dir.exists()) dir.mkdirs()
                shared = MutableStateFlow(load())
            }
        }
    }

    private val state: MutableStateFlow<List<LocalVideoPlaylist>> get() = shared!!

    /** Every local video playlist, newest first. */
    val playlists: StateFlow<List<LocalVideoPlaylist>> get() = state.asStateFlow()

    fun find(playlistId: String?): LocalVideoPlaylist? =
        playlistId?.let { id -> state.value.firstOrNull { it.id == id } }

    /** The videos in [playlistId], or empty for an id this store does not hold. */
    fun videosOf(playlistId: String?): List<VideoItem> = find(playlistId)?.videos.orEmpty()

    fun contains(playlistId: String?, videoId: String): Boolean =
        find(playlistId)?.videos?.any { it.videoId == videoId } == true

    /** @return the new playlist's id, or null when [name] was blank. */
    suspend fun create(name: String): String? = withContext(Dispatchers.IO) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@withContext null
        val playlist = LocalVideoPlaylist(id = LOCAL_ID_PREFIX + UUID.randomUUID(), name = trimmed)
        persist(playlist)
        playlist.id
    }

    /**
     * The device's own Watch Later, created the first time something is saved
     * to it.
     *
     * Signed in, Watch Later is the account's and this is never reached. Signed
     * out the save sheet's pinned row had nowhere to go, and a hero action that
     * silently fails is worse than one that is not offered.
     */
    suspend fun ensureWatchLater(): String = withContext(Dispatchers.IO) {
        find(WATCH_LATER_ID)?.let { return@withContext it.id }
        persist(LocalVideoPlaylist(id = WATCH_LATER_ID, name = WATCH_LATER_NAME))
        WATCH_LATER_ID
    }

    /**
     * Append [video] to [playlistId].
     *
     * @return false only when the playlist does not exist. Re-adding a video
     * already in it reports success: the user asked for it to be in there and
     * it is, and an error on a no-op would read as the save having failed.
     */
    suspend fun addVideo(playlistId: String, video: VideoItem): Boolean =
        withContext(Dispatchers.IO) {
            val playlist = find(playlistId) ?: return@withContext false
            if (playlist.videos.any { it.videoId == video.videoId }) return@withContext true
            persist(playlist.copy(videos = playlist.videos + video))
            true
        }

    suspend fun removeVideo(playlistId: String, videoId: String) = withContext(Dispatchers.IO) {
        val playlist = find(playlistId) ?: return@withContext
        val remaining = playlist.videos.filterNot { it.videoId == videoId }
        if (remaining.size == playlist.videos.size) return@withContext
        persist(playlist.copy(videos = remaining))
    }

    suspend fun rename(playlistId: String, name: String) = withContext(Dispatchers.IO) {
        val playlist = find(playlistId) ?: return@withContext
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@withContext
        persist(playlist.copy(name = trimmed))
    }

    suspend fun delete(playlistId: String) = withContext(Dispatchers.IO) {
        File(dir, fileNameFor(playlistId)).delete()
        state.value = state.value.filterNot { it.id == playlistId }
    }

    // ---------------- Persistence ----------------

    /**
     * The id carries a fixed prefix and a UUID, so it is already filesystem
     * safe; the sanitise is a belt-and-braces guard against a stray separator
     * ever reaching a path built here.
     */
    private fun fileNameFor(playlistId: String): String =
        playlistId.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".json"

    private fun persist(playlist: LocalVideoPlaylist) {
        try {
            File(dir, fileNameFor(playlist.id)).writeText(encode(playlist).toString())
        } catch (e: Exception) {
            Log.e(TAG, "Could not write playlist ${playlist.id}", e)
            return
        }
        val current = state.value
        state.value = if (current.any { it.id == playlist.id }) {
            current.map { if (it.id == playlist.id) playlist else it }
        } else {
            listOf(playlist) + current
        }
    }

    private fun load(): List<LocalVideoPlaylist> {
        val files = dir.listFiles { _, name -> name.endsWith(".json") } ?: return emptyList()
        return files.mapNotNull { file ->
            try {
                decode(JSONObject(file.readText()))
            } catch (e: Exception) {
                Log.w(TAG, "Skipping unreadable playlist ${file.name}", e)
                null
            }
        }.sortedByDescending { it.createdAt }
    }

    private fun encode(playlist: LocalVideoPlaylist): JSONObject = JSONObject().apply {
        put("id", playlist.id)
        put("name", playlist.name)
        put("createdAt", playlist.createdAt)
        put("videos", JSONArray().apply { playlist.videos.forEach { put(encodeVideo(it)) } })
    }

    /**
     * Only the fields a stored row needs to draw itself and start playback.
     *
     * The description and its rich links are left out because they are the bulk
     * of a [VideoItem] and the watch page fetches its own anyway, and
     * [VideoItem.dismissal] because those tokens are single-use and bound to
     * the response that carried them, so a persisted one is dead on arrival.
     */
    private fun encodeVideo(video: VideoItem): JSONObject = JSONObject().apply {
        put("videoId", video.videoId)
        put("title", video.title)
        put("channelName", video.channelName)
        video.channelId?.let { put("channelId", it) }
        video.channelIconUrl?.let { put("channelIcon", it) }
        video.thumbnailUrl?.let { put("thumbnail", it) }
        put("duration", video.duration)
        put("viewCount", video.viewCount)
        video.uploadedDate?.let { put("uploaded", it) }
        put("isLive", video.isLive)
        video.publishedAtMs?.let { put("publishedAt", it) }
    }

    private fun decode(obj: JSONObject): LocalVideoPlaylist? {
        val id = obj.optString("id").takeIf { it.isNotBlank() } ?: return null
        val array = obj.optJSONArray("videos") ?: JSONArray()
        val videos = (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val videoId = item.optString("videoId").takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            VideoItem(
                videoId = videoId,
                title = item.optString("title"),
                channelName = item.optString("channelName"),
                channelId = item.optString("channelId").takeIf { it.isNotBlank() },
                channelIconUrl = item.optString("channelIcon").takeIf { it.isNotBlank() },
                thumbnailUrl = item.optString("thumbnail").takeIf { it.isNotBlank() },
                duration = item.optLong("duration", 0L),
                viewCount = item.optString("viewCount"),
                uploadedDate = item.optString("uploaded").takeIf { it.isNotBlank() },
                isLive = item.optBoolean("isLive", false),
                publishedAtMs = item.optLong("publishedAt", 0L).takeIf { it > 0L }
            )
        }
        return LocalVideoPlaylist(
            id = id,
            name = obj.optString("name").takeIf { it.isNotBlank() } ?: "Playlist",
            videos = videos,
            createdAt = obj.optLong("createdAt", 0L)
        )
    }

    companion object {
        private const val TAG = "LocalVideoPlaylists"
        private const val DIR_NAME = "video_playlists"

        /**
         * Marks an id as this store's.
         *
         * A local playlist id has to be tellable from a YouTube one on sight,
         * because the same [VideoPlaylist.playlistId] reaches
         * `addToYouTubePlaylist`, the playlist browse, the share URL builder
         * and the saved-playlist filter, and every one of those has to branch.
         * The prefix is filesystem safe so it can also name the file, and it
         * cannot collide with YouTube's own shapes ("PL...", "UU...", "OLAK5uy_...",
         * "WL", "LL").
         */
        const val LOCAL_ID_PREFIX = "localvp_"

        /** The device's Watch Later, used only while signed out. */
        const val WATCH_LATER_ID = LOCAL_ID_PREFIX + "WL"
        const val WATCH_LATER_NAME = "Watch Later"

        fun isLocal(playlistId: String?): Boolean =
            playlistId?.startsWith(LOCAL_ID_PREFIX) == true

        private val LOCK = Any()

        @Volatile
        private var shared: MutableStateFlow<List<LocalVideoPlaylist>>? = null
    }
}
