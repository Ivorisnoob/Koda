package com.ivor.ivormusic.data

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Repository for fetching synced lyrics from KuGou API.
 * Adapted from ViMusic / SuvMusic implementation.
 */
class LyricsRepository {
    
    companion object {
        private const val TAG = "LrclibRepository"
        private const val API_GET_URL = "https://lrclib.net/api/get"
        private const val API_SEARCH_URL = "https://lrclib.net/api/search"
        
        private const val DURATION_TOLERANCE_SEC = 3
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    
    private val cache = mutableMapOf<String, List<LrcLine>>()
    
    suspend fun fetchLyrics(
        songId: String,
        title: String,
        artist: String,
        album: String = "",
        durationMs: Long
    ): LyricsResult = withContext(Dispatchers.IO) {
        if (title.isBlank()) return@withContext LyricsResult.NotFound
        
        // Check cache
        cache[songId]?.let { return@withContext LyricsResult.Success(it) }

        try {
            val durationSec = (durationMs / 1000).toInt()
            
            // 1. Try exact match with /api/get
            val exactMatch = getLyricsExact(title, artist, album, durationSec)
            if (exactMatch != null) {
                val parsed = parseLrc(exactMatch)
                if (parsed.isNotEmpty()) {
                    cache[songId] = parsed
                    return@withContext LyricsResult.Success(parsed)
                }
            }
            
            // 2. If exact match fails, try search
            val searchMatch = searchLyrics(title, artist, album, durationSec)
            if (searchMatch != null) {
                val parsed = parseLrc(searchMatch)
                if (parsed.isNotEmpty()) {
                    cache[songId] = parsed
                    return@withContext LyricsResult.Success(parsed)
                }
            }
            
            return@withContext LyricsResult.NotFound
            
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching lyrics", e)
            return@withContext LyricsResult.Error(e.message ?: "Unknown error")
        }
    }
    
    private fun getLyricsExact(title: String, artist: String, album: String, duration: Int): String? {
        // Only try exact if we have reliable metadata
        if (title.isBlank() || artist.isBlank() || duration <= 0) return null
        
        val urlBuilder = android.net.Uri.parse(API_GET_URL).buildUpon()
            .appendQueryParameter("track_name", title)
            .appendQueryParameter("artist_name", artist)
            .appendQueryParameter("duration", duration.toString())
            
        if (album.isNotBlank()) {
            urlBuilder.appendQueryParameter("album_name", album)
        }
            
        val url = urlBuilder.build().toString()
        val json = fetchJson(url)
        
        // Check if instrumental
        if (json?.optBoolean("instrumental", false) == true) {
             return null // Or handle instrumental differently
        }
        
        return json?.optString("syncedLyrics")?.takeIf { it.isNotBlank() }
    }

    private fun searchLyrics(title: String, artist: String, album: String, duration: Int): String? {
        val query = "$title $artist"
        val url = android.net.Uri.parse(API_SEARCH_URL).buildUpon()
            .appendQueryParameter("q", query)
            .build().toString()
            
        val jsonArray = fetchJsonArray(url) ?: return null
        
        var bestMatch: JSONObject? = null
        var bestDiff = Int.MAX_VALUE
        
        for (i in 0 until jsonArray.length()) {
            val item = jsonArray.optJSONObject(i) ?: continue
            val itemDuration = item.optInt("duration", 0)
            val synced = item.optString("syncedLyrics")
            
            if (synced.isBlank()) continue
            
            // Check duration validity (allow 0 if no better option, but prefer close match)
            val diff = abs(itemDuration - duration)
            
            if (diff <= DURATION_TOLERANCE_SEC) {
                // If we find a very close match, take it immediately? 
                // Let's verify album if possible, but duration is strongest signal for synced lyrics.
                return synced
            }
            
            if (diff < bestDiff) {
                bestDiff = diff
                bestMatch = item
            }
        }
        
        // Fallback: if we have a match within reasonable range (e.g. 10s)
        if (bestMatch != null && bestDiff <= 10) {
            return bestMatch.optString("syncedLyrics")
        }
        
        return null
    }
    
    private fun fetchJson(url: String): JSONObject? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "IvorMusic/1.0 (https://github.com/Ivorisnoob/TheMusicApp)")
                .build()
            
            val response = client.newCall(request).execute()
            if (response.code == 404) return null
            val body = response.body?.string()
            if (!response.isSuccessful || body.isNullOrBlank()) return null
            JSONObject(body)
        } catch (e: Exception) {
            Log.e(TAG, "Request failed: $url", e)
            null
        }
    }
    
    private fun fetchJsonArray(url: String): JSONArray? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "IvorMusic/1.0 (https://github.com/Ivorisnoob/TheMusicApp)")
                .build()
            
            val response = client.newCall(request).execute()
            val body = response.body?.string()
            if (!response.isSuccessful || body.isNullOrBlank()) return null
            JSONArray(body)
        } catch (e: Exception) {
            Log.e(TAG, "Request failed: $url", e)
            null
        }
    }

    // --- Normalization & Parsing ---
    
    private fun normalizeLyrics(lrc: String): String {
        return lrc.replace("\ufeff", "").replace("&apos;", "'")
    }

    fun parseLrc(lrcContent: String): List<LrcLine> {
        val lines = mutableListOf<LrcLine>()
        // Regex for standard LRC: [mm:ss.xx] text
        val pattern = Regex("""\[(\d{1,2}):(\d{2})\.(\d{2,3})](.*)""")
        
        for (line in lrcContent.lines()) {
            val trimLine = line.trim()
            if (trimLine.isEmpty()) continue
            
            val match = pattern.find(trimLine) ?: continue
            val (minutes, seconds, centis, text) = match.destructured
            
            val mins = minutes.toIntOrNull() ?: continue
            val secs = seconds.toIntOrNull() ?: continue
            val rawCs = centis.toIntOrNull() ?: continue
            
            val ms = if (centis.length == 2) rawCs * 10 else rawCs
            val timeMs = (mins * 60 * 1000L) + (secs * 1000L) + ms
            
            val content = text.trim()
            if (content.isNotEmpty()) {
                lines.add(LrcLine(timeMs, content))
            }
        }
        return lines.sortedBy { it.timeMs }
    }
    
    fun clearCache() {
        cache.clear()
    }
}
