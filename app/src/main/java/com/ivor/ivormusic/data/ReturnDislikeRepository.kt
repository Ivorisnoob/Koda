package com.ivor.ivormusic.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Read-only dislike counts from Return YouTube Dislike. Opt-in; failures return null. */
class ReturnDislikeRepository {

    suspend fun getDislikes(videoId: String): Long? = withContext(Dispatchers.IO) {
        if (videoId.length != 11) return@withContext null
        synchronized(cache) { cache[videoId] }?.let { return@withContext it }
        try {
            val request = Request.Builder()
                .url("$API_BASE/votes?videoId=$videoId")
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val dislikes = JSONObject(response.body?.string().orEmpty()).optLong("dislikes", -1L)
                dislikes.takeIf { it >= 0 }?.also { synchronized(cache) { cache[videoId] = it } }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        const val API_HOST = "returnyoutubedislikeapi.com"
        private const val API_BASE = "https://$API_HOST"
        private const val USER_AGENT = "Koda Android (https://github.com/Ivorisnoob/Koda)"
        private val client = OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS)
            .build()
        private val cache = object : LinkedHashMap<String, Long>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>) = size > 200
        }
    }
}
