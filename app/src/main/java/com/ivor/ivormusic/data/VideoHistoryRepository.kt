package com.ivor.ivormusic.data

import com.ivor.ivormusic.util.KLog

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Positions before this threshold are accidental starts; positions at the end
 * should reopen from the beginning. Kept pure for boundary tests.
 */
internal fun retainedVideoResumePosition(positionMs: Long, durationMs: Long): Long? {
    if (durationMs <= 0L || positionMs < 10_000L) return null
    val bounded = positionMs.coerceAtMost(durationMs)
    val remaining = durationMs - bounded
    val almostComplete = bounded.toDouble() / durationMs.toDouble() >= 0.95
    return bounded.takeUnless { remaining <= 30_000L || almostComplete }
}

/**
 * Persists watched videos locally so watch history (and the taste-based feed)
 * works without a YouTube login. Most recent first, capped at [MAX_ENTRIES].
 *
 * **Scoped per profile.** [scar] It was device-wide, and the reasoning that put
 * it there - "playlists, liked songs, downloads, stats and theme stay
 * device-wide" - does not survive contact with what this list actually is:
 * every profile saw every other profile's watches, and because the taste-based
 * feed is built from this, one account's recommendations were shaped by another
 * account's viewing. It belongs with local subscriptions and the blocklist,
 * which are keyed per profile for exactly that reason. The pre-profiles
 * install keeps the un-suffixed key, so nobody's history disappears on upgrade.
 */
class VideoHistoryRepository(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        synchronized(LOCK) {
            if (sharedHistory == null) sharedHistory = MutableStateFlow(load())
        }
    }

    /**
     * Which profile's history this is.
     *
     * Read fresh on every access rather than captured: instances outlive a
     * profile switch, and a captured id would keep writing the new profile's
     * watches into the one the user just left.
     */
    private fun historyKey(): String = ProfileManager.profileScopedKey(
        KEY_HISTORY,
        ProfileManager.activeProfileId(appContext),
        ProfileManager.legacyProfileId(appContext)
    )

    private fun hiddenKey(): String = ProfileManager.profileScopedKey(
        KEY_HIDDEN,
        ProfileManager.activeProfileId(appContext),
        ProfileManager.legacyProfileId(appContext)
    )

    private fun resumeKey(): String = ProfileManager.profileScopedKey(
        KEY_RESUME_POSITIONS,
        ProfileManager.activeProfileId(appContext),
        ProfileManager.legacyProfileId(appContext)
    )

    private val state: MutableStateFlow<List<VideoItem>> get() = sharedHistory!!
    val history: StateFlow<List<VideoItem>> get() = state.asStateFlow()

    fun getHistory(): List<VideoItem> {
        return synchronized(LOCK) {
            // Re-read in case another process restored the preference while
            // this one was alive. Same-process writes already update [state].
            load().also { state.value = it }
        }
    }

    /**
     * Whether [videoId] was removed from history.
     *
     * The options sheet asks so it can leave the row off an entry that has
     * already been taken out - the list it acts on is a snapshot, and offering
     * a removal twice is offering to do nothing.
     */
    fun isRemoved(videoId: String): Boolean = videoId in hiddenIds()

    /**
     * Record a watch, unless incognito is on.
     *
     * Gated here rather than at the two call sites - the video player and
     * Shorts - because a third surface that records a watch would otherwise
     * have to remember, and forgetting is silent.
     */
    fun addVideo(video: VideoItem) {
        if (IncognitoMode.isEnabled(appContext)) return
        synchronized(LOCK) {
            // Watching it again is the clearest possible statement that it
            // belongs in history, so a re-watch lifts an earlier removal
            // rather than being recorded into a list that filters it out.
            if (video.videoId in hiddenIds()) {
                prefs.edit().putStringSet(hiddenKey(), hiddenIds() - video.videoId).apply()
            }
            // Read from disk inside the same lock as the write. Player and
            // Shorts own separate repository instances; using either
            // instance's cached value here could erase the other's last play.
            val current = load().toMutableList()
            current.removeAll { it.videoId == video.videoId } // move to top on rewatch
            current.add(0, video)
            save(current.take(MAX_ENTRIES))
        }
    }

    /**
     * Whether Koda has recorded a watch for [videoId].
     *
     * The local store rather than the account's own history, deliberately:
     * this answers a feed filter and a menu row that both have to be correct
     * the instant they are drawn, and the account's list is fetched lazily by
     * whichever screen asked for it last. Signed in, the two agree for
     * anything watched in Koda, which is the case the filter exists for.
     */
    fun isWatched(videoId: String?): Boolean =
        videoId != null && state.value.any { it.videoId == videoId }

    /**
     * Mark a video watched because the user said so, rather than because they
     * watched it.
     *
     * Not gated on incognito, and that is the point of it being separate from
     * [addVideo]: incognito suppresses *recording*, not *doing*, and silently
     * discarding something the user asked for by name would be a different
     * feature wearing incognito's label. The same reasoning downloads,
     * playlists and likes already follow.
     */
    fun markWatched(video: VideoItem) {
        if (video.videoId.isBlank()) return
        synchronized(LOCK) {
            if (video.videoId in hiddenIds()) {
                prefs.edit().putStringSet(hiddenKey(), hiddenIds() - video.videoId).apply()
            }
            val current = load().toMutableList()
            current.removeAll { it.videoId == video.videoId }
            current.add(0, video)
            save(current.take(MAX_ENTRIES))
        }
    }

    /**
     * The inverse: forget that this was watched.
     *
     * Routed through [removeVideo] rather than only dropping the local entry,
     * because signed in the history list comes from the account's FEhistory
     * and a local-only removal would be undone by the next refresh - the video
     * would go back to counting as watched with no explanation.
     */
    fun markUnwatched(videoId: String) = removeVideo(videoId)

    /**
     * Take one video out of the watch history for good.
     *
     * Two writes, and both are needed. Dropping the local entry is the obvious
     * half; the id also goes into a persistent hidden set, because signed in
     * the list on screen comes from the account's own FEhistory rather than
     * from this store, and a removal that only touched the store would be
     * undone by the next refresh - the row would come back with no explanation.
     *
     * The hidden set is what makes this mean "gone from Koda's history",
     * whichever source the entry arrived from. It deliberately does **not**
     * delete anything from youtube.com: that needs a per-item feedback token
     * off the history response, which is its own piece of work.
     */
    fun removeVideo(videoId: String) {
        if (videoId.isBlank()) return
        synchronized(LOCK) {
            prefs.edit().putStringSet(hiddenKey(), hiddenIds() + videoId).apply()
            save(load().filterNot { it.videoId == videoId })
            removeResumePositionLocked(videoId)
        }
    }

    /** Ids the user has removed, so an account refresh cannot bring them back. */
    fun hiddenIds(): Set<String> =
        prefs.getStringSet(hiddenKey(), emptySet()).orEmpty()

    /** Everything in [videos] the user has not removed, in the given order. */
    fun withoutRemoved(videos: List<VideoItem>): List<VideoItem> {
        val hidden = hiddenIds()
        return if (hidden.isEmpty()) videos else videos.filterNot { it.videoId in hidden }
    }

    /**
     * Put a removed entry back where it was, for the undo snackbar.
     *
     * Re-inserting at [at] rather than at the top, because history is ordered
     * by when things were watched and undoing a mis-tap must not claim the
     * video was watched again just now.
     */
    fun restoreVideo(video: VideoItem, at: Int) {
        synchronized(LOCK) {
            prefs.edit().putStringSet(hiddenKey(), hiddenIds() - video.videoId).apply()
            val current = load().toMutableList()
            current.removeAll { it.videoId == video.videoId }
            current.add(at.coerceIn(0, current.size), video)
            save(current.take(MAX_ENTRIES))
        }
    }

    /**
     * Empty the history.
     *
     * The hidden set goes with it rather than accumulating: it exists only to
     * keep individually removed entries from reappearing, and with nothing left
     * to reappear it is a list of ids about videos the user has said they are
     * done with. Keeping it would also mean a cleared history slowly stopped
     * being able to record those videos again.
     */
    fun clearHistory() {
        synchronized(LOCK) {
            prefs.edit().remove(hiddenKey()).remove(resumeKey()).apply()
            save(emptyList())
        }
    }

    /** Last meaningful position for this profile, suppressed in incognito mode. */
    fun resumePosition(videoId: String, durationMs: Long): Long? {
        if (videoId.isBlank() || IncognitoMode.isEnabled(appContext)) return null
        return synchronized(LOCK) {
            val stored = loadResumePositions()
                .firstOrNull { it.videoId == videoId }
                ?: return@synchronized null
            retainedVideoResumePosition(
                positionMs = stored.positionMs,
                durationMs = durationMs.takeIf { it > 0L } ?: stored.durationMs,
            )
        }
    }

    /**
     * Persist a checkpoint without growing preferences forever. Very early
     * checkpoints leave an older useful value alone; completion clears it.
     */
    fun saveResumePosition(videoId: String, positionMs: Long, durationMs: Long) {
        if (videoId.isBlank() || IncognitoMode.isEnabled(appContext) || durationMs <= 0L) return
        synchronized(LOCK) {
            val retained = retainedVideoResumePosition(positionMs, durationMs)
            if (retained == null) {
                val nearEnd = positionMs.coerceAtLeast(0L) >= durationMs - 30_000L ||
                    positionMs.toDouble() / durationMs.toDouble() >= 0.95
                if (nearEnd) removeResumePositionLocked(videoId)
                return
            }

            val points = loadResumePositions().toMutableList()
            points.removeAll { it.videoId == videoId }
            points.add(
                0,
                VideoResumePoint(videoId, retained, durationMs, System.currentTimeMillis())
            )
            saveResumePositions(points.take(MAX_RESUME_ENTRIES))
        }
    }

    fun clearResumePosition(videoId: String) {
        if (videoId.isBlank()) return
        synchronized(LOCK) { removeResumePositionLocked(videoId) }
    }

    private fun removeResumePositionLocked(videoId: String) {
        val points = loadResumePositions()
        if (points.none { it.videoId == videoId }) return
        saveResumePositions(points.filterNot { it.videoId == videoId })
    }

    private fun saveResumePositions(points: List<VideoResumePoint>) {
        val array = JSONArray()
        points.forEach { point ->
            array.put(JSONObject().apply {
                put("videoId", point.videoId)
                put("positionMs", point.positionMs)
                put("durationMs", point.durationMs)
                put("updatedAt", point.updatedAt)
            })
        }
        prefs.edit().putString(resumeKey(), array.toString()).apply()
    }

    private fun loadResumePositions(): List<VideoResumePoint> {
        val raw = prefs.getString(resumeKey(), null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val videoId = item.optString("videoId")
                val positionMs = item.optLong("positionMs")
                val durationMs = item.optLong("durationMs")
                if (videoId.isBlank() || positionMs < 0L || durationMs <= 0L) {
                    return@mapNotNull null
                }
                VideoResumePoint(
                    videoId = videoId,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    updatedAt = item.optLong("updatedAt"),
                )
            }
        } catch (e: Exception) {
            KLog.e("VideoHistoryRepo", "Failed to load resume positions", e)
            emptyList()
        }
    }

    private fun save(videos: List<VideoItem>) {
        state.value = videos
        val array = JSONArray()
        videos.forEach { video ->
            array.put(JSONObject().apply {
                put("videoId", video.videoId)
                put("title", video.title)
                put("channelName", video.channelName)
                put("channelId", video.channelId ?: JSONObject.NULL)
                put("channelIconUrl", video.channelIconUrl ?: JSONObject.NULL)
                put("thumbnailUrl", video.thumbnailUrl ?: JSONObject.NULL)
                put("duration", video.duration)
                put("viewCount", video.viewCount)
                put("uploadedDate", video.uploadedDate ?: JSONObject.NULL)
                put("isLive", video.isLive)
            })
        }
        prefs.edit().putString(historyKey(), array.toString()).apply()
    }

    private fun load(): List<VideoItem> {
        val raw = prefs.getString(historyKey(), null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                val videoId = obj.optString("videoId")
                if (videoId.isBlank()) return@mapNotNull null
                VideoItem(
                    videoId = videoId,
                    title = obj.optString("title"),
                    channelName = obj.optString("channelName"),
                    channelId = obj.optString("channelId").takeIf { it.isNotBlank() && it != "null" },
                    channelIconUrl = obj.optString("channelIconUrl").takeIf { it.isNotBlank() && it != "null" },
                    thumbnailUrl = obj.optString("thumbnailUrl").takeIf { it.isNotBlank() && it != "null" },
                    duration = obj.optLong("duration"),
                    viewCount = obj.optString("viewCount"),
                    uploadedDate = obj.optString("uploadedDate").takeIf { it.isNotBlank() && it != "null" },
                    isLive = obj.optBoolean("isLive", false)
                )
            }
        } catch (e: Exception) {
            KLog.e("VideoHistoryRepo", "Failed to load local video history", e)
            emptyList()
        }
    }

    companion object {
        private val LOCK = Any()
        @Volatile private var sharedHistory: MutableStateFlow<List<VideoItem>>? = null
        const val PREFS_NAME = "video_history"
        const val KEY_HISTORY = "history_list"
        const val KEY_HIDDEN = "history_removed_ids"
        /**
         * Public because [BackupRepository] restates it: this store is
         * profile-scoped, so it travels in the backup's structural profile
         * section rather than the raw preference copy.
         */
        const val KEY_RESUME_POSITIONS = "resume_positions"
        private const val MAX_ENTRIES = 100
        private const val MAX_RESUME_ENTRIES = 100

        /**
         * Re-read the flow after a profile switch.
         *
         * The state is process-wide, so without this the new profile would be
         * shown the previous one's watches until something else happened to
         * reload - which for the Library's history preview is never.
         */
        fun reloadForActiveProfile(context: Context) {
            val repository = VideoHistoryRepository(context)
            synchronized(LOCK) {
                sharedHistory?.value = repository.load()
            }
        }
    }
}

private data class VideoResumePoint(
    val videoId: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
)
