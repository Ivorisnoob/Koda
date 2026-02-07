package com.ivor.ivormusic.data

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
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
        private const val TAG = "KugouLyricsRepository"
        private const val SEARCH_SONG_URL = "https://mobileservice.kugou.com/api/v3/search/song"
        private const val SEARCH_LYRICS_URL = "https://lyrics.kugou.com/search"
        private const val DOWNLOAD_LYRICS_URL = "https://lyrics.kugou.com/download"
        
        private const val DURATION_TOLERANCE_SEC = 5 // Tolerance for duration matching
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
        durationMs: Long
    ): LyricsResult = withContext(Dispatchers.IO) {
        if (title.isBlank()) return@withContext LyricsResult.NotFound
        
        // Check cache
        cache[songId]?.let { return@withContext LyricsResult.Success(it) }

        try {
            val durationSec = (durationMs / 1000).toInt()
            val keyword = "$title - $artist"
            
            // Try to find a candidate
            val candidate = getLyricsCandidate(keyword, durationSec)
                ?: return@withContext LyricsResult.NotFound
                
            Log.d(TAG, "Found candidate: ${candidate.id}, accessKey: ${candidate.accessKey}")
            
            // Download lyrics
            val lyricsContent = downloadLyrics(candidate.id, candidate.accessKey)
                ?: return@withContext LyricsResult.NotFound
                
            // Normalize
            val normalizedLyrics = normalizeLyrics(lyricsContent)
            
            // Parse
            val parsedLines = parseLrc(normalizedLyrics)
            if (parsedLines.isEmpty()) return@withContext LyricsResult.NotFound
            
            // Cache
            cache[songId] = parsedLines
            return@withContext LyricsResult.Success(parsedLines)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching lyrics", e)
            return@withContext LyricsResult.Error(e.message ?: "Unknown error")
        }
    }
    
    // --- Helper Classes ---
    
    private data class Candidate(val id: String, val accessKey: String, val duration: Int)
    private data class SongInfo(val hash: String, val duration: Int)
    
    // --- API Methods ---
    
    private fun getLyricsCandidate(keyword: String, durationSec: Int): Candidate? {
        val encodedKeyword = URLEncoder.encode(keyword, "UTF-8")
        
        // Strategy 1: Search song -> Get Hash -> Search Lyrics by Hash
        // This is more accurate as we verify song duration first
        try {
            val songResults = searchSongs(encodedKeyword)
            for (song in songResults) {
                // If duration match is good (or unknown duration)
                if (durationSec <= 0 || abs(song.duration - durationSec) <= DURATION_TOLERANCE_SEC) {
                    val candidate = searchLyricsByHash(song.hash)
                    if (candidate != null) return candidate
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Strategy 1 failed", e)
        }
        
        // Strategy 2: Search lyrics directly by keyword
        try {
            val candidates = searchLyricsByKeyword(encodedKeyword, durationSec)
            return candidates.firstOrNull()
        } catch (e: Exception) {
            Log.w(TAG, "Strategy 2 failed", e)
        }
        
        return null
    }
    
    private fun searchSongs(encodedKeyword: String): List<SongInfo> {
        val url = "$SEARCH_SONG_URL?version=9108&plat=0&pagesize=10&showtype=0&keyword=$encodedKeyword"
        val json = fetchJson(url) ?: return emptyList()
        val list = mutableListOf<SongInfo>()
        
        val infoArray = json.optJSONObject("data")?.optJSONArray("info") ?: return emptyList()
        
        for (i in 0 until infoArray.length()) {
            val item = infoArray.optJSONObject(i) ?: continue
            val hash = item.optString("hash")
            val duration = item.optInt("duration")
            if (hash.isNotBlank()) {
                list.add(SongInfo(hash, duration))
            }
        }
        return list
    }
    
    private fun searchLyricsByHash(hash: String): Candidate? {
        val url = "$SEARCH_LYRICS_URL?ver=1&man=yes&client=pc&hash=$hash"
        val json = fetchJson(url) ?: return null
        
        val candidates = json.optJSONArray("candidates") ?: return null
        if (candidates.length() > 0) {
            val item = candidates.optJSONObject(0)
            val id = item.optString("id")
            val accessKey = item.optString("accesskey")
            if (id.isNotBlank() && accessKey.isNotBlank()) {
                return Candidate(id, accessKey, 0)
            }
        }
        return null
    }

    private fun searchLyricsByKeyword(encodedKeyword: String, durationSec: Int): List<Candidate> {
        val durationParam = if (durationSec > 0) "&duration=${durationSec * 1000}" else ""
        val url = "$SEARCH_LYRICS_URL?ver=1&man=yes&client=pc&keyword=$encodedKeyword$durationParam"
        val json = fetchJson(url) ?: return emptyList()
        
        val list = mutableListOf<Candidate>()
        val candidates = json.optJSONArray("candidates") ?: return emptyList()
        
        for (i in 0 until candidates.length()) {
            val item = candidates.optJSONObject(i) ?: continue
            val id = item.optString("id")
            val accessKey = item.optString("accesskey")
            val dur = item.optInt("duration") // ms
            if (id.isNotBlank() && accessKey.isNotBlank()) {
                list.add(Candidate(id, accessKey, dur / 1000))
            }
        }
        return list
    }
    
    private fun downloadLyrics(id: String, accessKey: String): String? {
        val url = "$DOWNLOAD_LYRICS_URL?fmt=lrc&charset=utf8&client=pc&ver=1&id=$id&accesskey=$accessKey"
        val json = fetchJson(url) ?: return null
        
        val contentBase64 = json.optString("content")
        if (contentBase64.isBlank()) return null
        
        return try {
            val decodedBytes = Base64.decode(contentBase64, Base64.DEFAULT)
            String(decodedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Base64 decode failed", e)
            null
        }
    }
    
    private fun fetchJson(url: String): JSONObject? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
            .build()
        
        val response = client.newCall(request).execute()
        val body = response.body?.string()
        if (!response.isSuccessful || body.isNullOrBlank()) return null
        
        return try {
            JSONObject(body)
        } catch (e: Exception) {
            null
        }
    }

    // --- Normalization & Parsing ---
    
    private fun normalizeLyrics(lrc: String): String {
        // Simple normalization: remove BOM if present
        var result = lrc.replace("\ufeff", "")
        
        // Decode HTML entities if any (simple ones)
        result = result.replace("&apos;", "'")
        
        return result
    }

    fun parseLrc(lrcContent: String): List<LrcLine> {
        val lines = mutableListOf<LrcLine>()
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
            // Allow empty lines if they are instrumental breaks, but generally we want content
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
