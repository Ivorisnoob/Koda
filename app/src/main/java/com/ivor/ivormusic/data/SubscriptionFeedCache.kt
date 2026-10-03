package com.ivor.ivormusic.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Last fetched subscriptions feed per profile, so a restart can skip the refetch. Cache dir, not backed up. */
class SubscriptionFeedCache(context: Context) {

    private val appContext = context.applicationContext

    data class Snapshot(val videos: List<VideoItem>, val fetchedAtMs: Long, val key: String)

    private fun file(): File =
        File(appContext.cacheDir, "subs_feed_${ProfileManager.activeProfileId(appContext)}.json")

    fun read(): Snapshot? = runCatching {
        val f = file().takeIf { it.exists() } ?: return null
        val root = JSONObject(f.readText())
        val array = root.optJSONArray("videos") ?: return null
        val videos = (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            VideoItem(
                videoId = o.optString("videoId").takeIf { it.isNotBlank() } ?: return@mapNotNull null,
                title = o.optString("title"),
                channelName = o.optString("channelName"),
                channelId = o.optString("channelId").takeIf { it.isNotBlank() },
                channelIconUrl = o.optString("channelIcon").takeIf { it.isNotBlank() },
                thumbnailUrl = o.optString("thumbnail").takeIf { it.isNotBlank() },
                duration = o.optLong("duration", 0L),
                viewCount = o.optString("viewCount"),
                uploadedDate = o.optString("uploaded").takeIf { it.isNotBlank() },
                isLive = o.optBoolean("isLive", false),
                publishedAtMs = o.optLong("publishedAt", 0L).takeIf { it > 0L }
            )
        }
        Snapshot(videos, root.optLong("fetchedAt"), root.optString("key"))
    }.getOrNull()

    /** [key] identifies what the feed was built from (source + channels), so a changed set is never served stale. */
    fun write(videos: List<VideoItem>, key: String) {
        runCatching {
            val array = JSONArray()
            videos.forEach { v ->
                array.put(JSONObject().apply {
                    put("videoId", v.videoId)
                    put("title", v.title)
                    put("channelName", v.channelName)
                    v.channelId?.let { put("channelId", it) }
                    v.channelIconUrl?.let { put("channelIcon", it) }
                    v.thumbnailUrl?.let { put("thumbnail", it) }
                    put("duration", v.duration)
                    put("viewCount", v.viewCount)
                    v.uploadedDate?.let { put("uploaded", it) }
                    put("isLive", v.isLive)
                    v.publishedAtMs?.let { put("publishedAt", it) }
                })
            }
            file().writeText(
                JSONObject().put("videos", array).put("fetchedAt", System.currentTimeMillis()).put("key", key).toString()
            )
        }
    }
}
