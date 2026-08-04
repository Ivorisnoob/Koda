package com.ivor.ivormusic.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * A channel the user asked not to be recommended, kept for the management
 * screen so the block can be lifted by name rather than by opaque id.
 */
data class BlockedChannel(
    val channelId: String,
    val name: String,
    val avatarUrl: String? = null,
    val blockedAt: Long = System.currentTimeMillis()
)

/** The two things a "don't recommend" tap can mean. */
enum class NotInterestedScope { VIDEO, CHANNEL }

/**
 * What the user has asked not to see again: individual videos, and whole
 * channels.
 *
 * **This is the engine of the feature, not a cache in front of YouTube's.**
 * Probed August 2026: signed out, InnerTube returns no `feedbackToken`
 * anywhere - not on watch-next related items, not on browse, not on search -
 * because there is no account to store the preference against. So a local
 * blocklist is the only thing that can work for a signed-out user at all.
 * It is also the only thing that works *immediately* for anyone: YouTube's
 * own feedback is advisory and takes days to visibly change what it
 * recommends, whereas a filter takes effect on the next frame.
 *
 * Sending the preference on to the account when one exists is handled
 * separately and is strictly a bonus - see
 * [YouTubeRepository.sendNotInterestedFeedback].
 *
 * The backing flows are process-wide, for the same reason
 * [LocalSubscriptionsRepository]'s are: hiding a video from the player's
 * related list has to remove it from the home grid behind it, and those are
 * different ViewModels holding different instances.
 */
class NotInterestedRepository(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        synchronized(LOCK) {
            if (sharedHiddenVideos == null) {
                sharedHiddenVideos = MutableStateFlow(loadHiddenVideos())
                sharedBlockedChannels = MutableStateFlow(loadBlockedChannels())
                sharedLastAction = MutableStateFlow(null)
            }
        }
    }

    private val hiddenState: MutableStateFlow<List<HiddenVideo>> get() = sharedHiddenVideos!!
    private val blockedState: MutableStateFlow<List<BlockedChannel>> get() = sharedBlockedChannels!!

    val hiddenVideos: StateFlow<List<HiddenVideo>> get() = hiddenState.asStateFlow()
    val blockedChannels: StateFlow<List<BlockedChannel>> get() = blockedState.asStateFlow()

    /**
     * The most recent hide/block, for the undo snackbar.
     *
     * It lives here rather than in a ViewModel because the action can be
     * taken from four different surfaces - the home grid, the subscriptions
     * feed, the player's related list and Shorts - and the app shows exactly
     * one snackbar for all of them. Anything hidden by a mis-tap has to be
     * one tap away from coming back; a permanent, silent removal with no
     * recovery is the thing that makes people distrust the feature.
     */
    val lastAction: StateFlow<UndoableAction?> get() = sharedLastAction!!.asStateFlow()

    /** A hide or block that can still be taken back. */
    data class UndoableAction(
        val scope: NotInterestedScope,
        val message: String,
        val videoId: String? = null,
        val channelId: String? = null,
        val channelName: String = "",
        /** Distinguishes consecutive identical actions so the UI re-shows. */
        val id: Long = System.nanoTime()
    )

    /** Reverses [action] and clears the undo state. */
    fun undo(action: UndoableAction) {
        when (action.scope) {
            NotInterestedScope.VIDEO -> action.videoId?.let { unhideVideo(it) }
            NotInterestedScope.CHANNEL -> unblockChannel(action.channelId.orEmpty(), action.channelName)
        }
        sharedLastAction!!.value = null
    }

    /** Dismiss without reversing - the snackbar timed out or was swiped away. */
    fun clearLastAction() {
        sharedLastAction!!.value = null
    }

    /** A hidden video keeps its title so the management screen can name it. */
    data class HiddenVideo(
        val videoId: String,
        val title: String,
        val channelName: String? = null,
        val hiddenAt: Long = System.currentTimeMillis()
    )

    // ---------------- Reads ----------------

    fun isVideoHidden(videoId: String?): Boolean =
        videoId != null && hiddenState.value.any { it.videoId == videoId }

    fun isChannelBlocked(channelId: String?): Boolean =
        channelId != null && blockedState.value.any { it.channelId == channelId }

    /**
     * Whether [video] should be kept out of a recommendation feed.
     *
     * Channel matching falls back to the display name when the item carries no
     * channel id. Feed lockups sometimes omit it, and a block that silently
     * stopped working on half the home grid would look like a bug in the
     * feature rather than a gap in the data.
     */
    fun isFiltered(video: VideoItem): Boolean {
        if (isVideoHidden(video.videoId)) return true
        val blocked = blockedState.value
        if (blocked.isEmpty()) return false
        val channelId = video.channelId?.takeIf { it.isNotBlank() }
        val channelName = video.channelName.takeIf { it.isNotBlank() }
        return blocked.any { entry ->
            if (entry.channelId.isNotBlank() && channelId != null) {
                entry.channelId == channelId
            } else {
                // One side lacks an id, so the name is all there is to go on.
                channelName != null && entry.name.equals(channelName, ignoreCase = true)
            }
        }
    }

    fun filter(videos: List<VideoItem>): List<VideoItem> {
        if (hiddenState.value.isEmpty() && blockedState.value.isEmpty()) return videos
        return videos.filterNot { isFiltered(it) }
    }

    // ---------------- Writes ----------------

    fun hideVideo(video: VideoItem) {
        if (video.videoId.isBlank()) return
        if (isVideoHidden(video.videoId)) return
        val entry = HiddenVideo(
            videoId = video.videoId,
            title = video.title.takeIf { it.isNotBlank() } ?: video.videoId,
            channelName = video.channelName.takeIf { it.isNotBlank() }
        )
        // Newest first, capped: this list exists to filter feeds, not to be a
        // second watch history, and an unbounded set would grow into every
        // SharedPreferences read for the life of the install.
        saveHidden((listOf(entry) + hiddenState.value).take(MAX_HIDDEN_VIDEOS))
        sharedLastAction!!.value = UndoableAction(
            scope = NotInterestedScope.VIDEO,
            message = "Video hidden",
            videoId = video.videoId
        )
    }

    fun unhideVideo(videoId: String) {
        val next = hiddenState.value.filterNot { it.videoId == videoId }
        if (next.size != hiddenState.value.size) saveHidden(next)
    }

    fun blockChannel(channelId: String?, name: String, avatarUrl: String? = null) {
        // A blank id is still worth storing: the name-based fallback in
        // [isFiltered] is the only thing that can filter feed items whose
        // lockup omitted the channel id.
        val id = channelId.orEmpty()
        if (id.isBlank() && name.isBlank()) return
        val already = blockedState.value.any {
            (id.isNotBlank() && it.channelId == id) || (id.isBlank() && it.name.equals(name, true))
        }
        if (already) return
        saveBlocked(listOf(BlockedChannel(id, name, avatarUrl)) + blockedState.value)
        sharedLastAction!!.value = UndoableAction(
            scope = NotInterestedScope.CHANNEL,
            message = if (name.isNotBlank()) "$name won't be recommended" else "Channel hidden",
            channelId = id,
            channelName = name
        )
    }

    fun unblockChannel(channelId: String, name: String) {
        val next = blockedState.value.filterNot {
            if (channelId.isNotBlank()) it.channelId == channelId else it.name.equals(name, true)
        }
        if (next.size != blockedState.value.size) saveBlocked(next)
    }

    fun clearHiddenVideos() = saveHidden(emptyList())

    fun clearBlockedChannels() = saveBlocked(emptyList())

    fun clearAll() {
        saveHidden(emptyList())
        saveBlocked(emptyList())
    }

    // ---------------- Persistence ----------------

    private fun saveHidden(list: List<HiddenVideo>) {
        hiddenState.value = list
        val array = JSONArray()
        list.forEach { entry ->
            array.put(JSONObject().apply {
                put("videoId", entry.videoId)
                put("title", entry.title)
                put("channelName", entry.channelName ?: JSONObject.NULL)
                put("hiddenAt", entry.hiddenAt)
            })
        }
        prefs.edit().putString(KEY_HIDDEN_VIDEOS, array.toString()).apply()
    }

    private fun loadHiddenVideos(): List<HiddenVideo> {
        val raw = prefs.getString(KEY_HIDDEN_VIDEOS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                val id = obj.optString("videoId").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                HiddenVideo(
                    videoId = id,
                    title = obj.optString("title").takeIf { it.isNotBlank() } ?: id,
                    channelName = obj.optString("channelName")
                        .takeIf { it.isNotBlank() && it != "null" },
                    hiddenAt = obj.optLong("hiddenAt", 0L)
                )
            }.distinctBy { it.videoId }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to load hidden videos", e)
            emptyList()
        }
    }

    private fun saveBlocked(list: List<BlockedChannel>) {
        blockedState.value = list
        val array = JSONArray()
        list.forEach { channel ->
            array.put(JSONObject().apply {
                put("channelId", channel.channelId)
                put("name", channel.name)
                put("avatarUrl", channel.avatarUrl ?: JSONObject.NULL)
                put("blockedAt", channel.blockedAt)
            })
        }
        prefs.edit().putString(KEY_BLOCKED_CHANNELS, array.toString()).apply()
    }

    private fun loadBlockedChannels(): List<BlockedChannel> {
        val raw = prefs.getString(KEY_BLOCKED_CHANNELS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                val name = obj.optString("name")
                val id = obj.optString("channelId")
                if (name.isBlank() && id.isBlank()) return@mapNotNull null
                BlockedChannel(
                    channelId = id,
                    name = name.takeIf { it.isNotBlank() } ?: id,
                    avatarUrl = obj.optString("avatarUrl")
                        .takeIf { it.isNotBlank() && it != "null" },
                    blockedAt = obj.optLong("blockedAt", 0L)
                )
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to load blocked channels", e)
            emptyList()
        }
    }

    companion object {
        private const val TAG = "NotInterestedRepo"
        private const val PREFS_NAME = "not_interested"
        private const val KEY_HIDDEN_VIDEOS = "hidden_videos"
        private const val KEY_BLOCKED_CHANNELS = "blocked_channels"

        /**
         * Ceiling on individually hidden videos. Blocking a channel is the
         * tool for "never show me this again" at scale; this list is for
         * one-offs, and the oldest entries stop mattering once the feed has
         * moved on from them.
         */
        private const val MAX_HIDDEN_VIDEOS = 1000

        private val LOCK = Any()

        @Volatile
        private var sharedHiddenVideos: MutableStateFlow<List<HiddenVideo>>? = null

        @Volatile
        private var sharedBlockedChannels: MutableStateFlow<List<BlockedChannel>>? = null

        @Volatile
        private var sharedLastAction: MutableStateFlow<UndoableAction?>? = null
    }
}
