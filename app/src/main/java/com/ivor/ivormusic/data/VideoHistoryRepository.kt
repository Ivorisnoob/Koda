package com.ivor.ivormusic.data

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

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _history = MutableStateFlow(load())
    val history: StateFlow<List<VideoItem>> = _history.asStateFlow()

    fun getHistory(): List<VideoItem> {
        // Re-read from prefs: other instances (e.g. the video player's) may
        // have recorded watches since this instance was constructed.
        val fresh = load()
        _history.value = fresh
        return fresh
    }

    fun addVideo(video: VideoItem) {
        val current = _history.value.toMutableList()
        current.removeAll { it.videoId == video.videoId } // move to top on rewatch
        current.add(0, video)
        save(current.take(MAX_ENTRIES))
    }

    fun removeVideo(videoId: String) {
        save(_history.value.filterNot { it.videoId == videoId })
    }

    fun clearHistory() {
        save(emptyList())
    }

    private fun save(videos: List<VideoItem>) {
        _history.value = videos
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
            android.util.Log.e("VideoHistoryRepo", "Failed to load local video history", e)
            emptyList()
        }
    }

    companion object {
        private const val PREFS_NAME = "video_history"
        private const val KEY_HISTORY = "history_list"
        private const val MAX_ENTRIES = 100
    }
}
