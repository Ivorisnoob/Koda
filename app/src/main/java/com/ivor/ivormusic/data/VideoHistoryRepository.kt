package com.ivor.ivormusic.data

import com.ivor.ivormusic.util.KLog

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists watched videos locally so watch history (and the taste-based feed)
 * works without a YouTube login. Most recent first, capped at [MAX_ENTRIES].
 */
class VideoHistoryRepository(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        synchronized(LOCK) {
            if (sharedHistory == null) sharedHistory = MutableStateFlow(load())
        }
    }

    private val state: MutableStateFlow<List<VideoItem>> get() = sharedHistory!!
    val history: StateFlow<List<VideoItem>> get() = state.asStateFlow()

    fun getHistory(): List<VideoItem> {
        return synchronized(LOCK) {
            // Re-read in case another process restored the preference while
            // this one was alive. Same-process writes already update [state].
            load().also { state.value = it }
        }
    }

    fun addVideo(video: VideoItem) {
        synchronized(LOCK) {
            // Read from disk inside the same lock as the write. Player and
            // Shorts own separate repository instances; using either
            // instance's cached value here could erase the other's last play.
            val current = load().toMutableList()
            current.removeAll { it.videoId == video.videoId } // move to top on rewatch
            current.add(0, video)
            save(current.take(MAX_ENTRIES))
        }
    }

    fun removeVideo(videoId: String) {
        synchronized(LOCK) {
            save(load().filterNot { it.videoId == videoId })
        }
    }

    fun clearHistory() {
        synchronized(LOCK) { save(emptyList()) }
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
        prefs.edit().putString(KEY_HISTORY, array.toString()).apply()
    }

    private fun load(): List<VideoItem> {
        val raw = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
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
        private const val PREFS_NAME = "video_history"
        private const val KEY_HISTORY = "history_list"
        private const val MAX_ENTRIES = 100
    }
}
