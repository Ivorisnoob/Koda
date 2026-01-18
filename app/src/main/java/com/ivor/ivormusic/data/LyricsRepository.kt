package com.ivor.ivormusic.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Repository for fetching synced lyrics from LRCLIB API.
 * LRCLIB is a free, open lyrics database with ~3M tracks.
 * 
 * API: https://lrclib.net/api/get?track_name={title}&artist_name={artist}&duration={seconds}
 */
class LyricsRepository {
    
    companion object {
        private const val TAG = "LyricsRepository"
        private const val BASE_URL = "https://lrclib.net/api/get"
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    
    // Simple in-memory cache: song ID -> lyrics
    private val cache = mutableMapOf<String, List<LrcLine>>()
    
    /**
     * Fetch synced lyrics for a song.
     * @param songId Unique song ID for caching
     * @param title Song title
     * @param artist Artist name
     * @param durationMs Duration in milliseconds
     * @return LyricsResult indicating success, not found, or error
     */
    suspend fun fetchLyrics(
        songId: String,
        title: String,
        artist: String,
        durationMs: Long
    ): LyricsResult = withContext(Dispatchers.IO) {
        // Validate inputs - avoid API calls with invalid data
        if (title.isBlank() || title.startsWith("Unknown", ignoreCase = true)) {
            Log.d(TAG, "Skipping lyrics fetch: invalid title '$title'")
            return@withContext LyricsResult.NotFound
        }
        if (artist.isBlank() || artist.startsWith("Unknown", ignoreCase = true)) {
            Log.d(TAG, "Skipping lyrics fetch: invalid artist '$artist'")
            return@withContext LyricsResult.NotFound
        }
        if (durationMs <= 0) {
            Log.d(TAG, "Skipping lyrics fetch: invalid duration $durationMs")
            return@withContext LyricsResult.NotFound
        }
        
        // Check cache first
        cache[songId]?.let { 
            Log.d(TAG, "Cache hit for: $title")
            return@withContext LyricsResult.Success(it) 
        }
        
        // Retry configuration
        val maxRetries = 3
        var lastError: String? = null
        
        for (attempt in 1..maxRetries) {
            try {
                val result = fetchLyricsInternal(songId, title, artist, durationMs)
                
                when (result) {
                    is LyricsResult.Success -> return@withContext result
                    is LyricsResult.NotFound -> return@withContext result
                    is LyricsResult.Error -> {
                        lastError = result.message
                        // Check if error is retryable
                        val isRetryable = result.message.contains("400") || 
                                          result.message.contains("429") || 
                                          result.message.contains("500") ||
                                          result.message.contains("502") ||
                                          result.message.contains("503") ||
                                          result.message.contains("timeout", ignoreCase = true)
                        
                        if (isRetryable && attempt < maxRetries) {
                            // Exponential backoff: 500ms, 1000ms, 2000ms
                            val delayMs = 500L * (1 shl (attempt - 1))
                            Log.d(TAG, "Retrying lyrics fetch (attempt $attempt/$maxRetries) after ${delayMs}ms for: $title")
                            kotlinx.coroutines.delay(delayMs)
                            continue
                        }
                        return@withContext result
                    }
                    else -> return@withContext result
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching lyrics (attempt $attempt)", e)
                lastError = e.message ?: "Unknown error"
                
                if (attempt < maxRetries) {
                    val delayMs = 500L * (1 shl (attempt - 1))
                    kotlinx.coroutines.delay(delayMs)
                }
            }
        }
        
        LyricsResult.Error(lastError ?: "Failed after $maxRetries attempts")
    }
    
    /**
     * Internal lyrics fetch without retry logic.
     */
    private suspend fun fetchLyricsInternal(
        songId: String,
        title: String,
        artist: String,
        durationMs: Long
    ): LyricsResult = withContext(Dispatchers.IO) {
        try {
            val durationSeconds = (durationMs / 1000).toInt()
            
            // Clean up title and artist for better matching
            val cleanTitle = cleanSearchTerm(title)
            val cleanArtist = cleanSearchTerm(artist)
            
            val url = buildString {
                append(BASE_URL)
                append("?track_name=")
                append(URLEncoder.encode(cleanTitle, "UTF-8"))
                append("&artist_name=")
                append(URLEncoder.encode(cleanArtist, "UTF-8"))
                append("&duration=")
                append(durationSeconds)
            }
            
            Log.d(TAG, "Fetching lyrics from: $url")
            
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "IvorMusic/1.0")
                .build()
            
            val response = client.newCall(request).execute()
            
            when (response.code) {
                200 -> {
                    val json = response.body?.string() ?: return@withContext LyricsResult.NotFound
                    val syncedLyrics = extractSyncedLyrics(json)
                    
                    if (syncedLyrics.isNullOrBlank()) {
                        // API returned but no synced lyrics available
                        Log.d(TAG, "No synced lyrics in response for: $title")
                        return@withContext LyricsResult.NotFound
                    }
                    
                    val lines = parseLrc(syncedLyrics)
                    if (lines.isEmpty()) {
                        return@withContext LyricsResult.NotFound
                    }
                    
                    // Cache the result
                    cache[songId] = lines
                    Log.d(TAG, "Fetched ${lines.size} lyrics lines for: $title")
                    LyricsResult.Success(lines)
                }
                404 -> {
                    Log.d(TAG, "Lyrics not found for: $title by $artist")
                    LyricsResult.NotFound
                }
                else -> {
                    Log.e(TAG, "API error ${response.code}: ${response.message}")
                    LyricsResult.Error("Failed to fetch lyrics (${response.code})")
                }
            }
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "Timeout fetching lyrics", e)
            LyricsResult.Error("Connection timeout")
        } catch (e: java.io.IOException) {
            Log.e(TAG, "Network error fetching lyrics", e)
            LyricsResult.Error("Network error: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching lyrics", e)
            LyricsResult.Error(e.message ?: "Unknown error")
        }
    }
    
    /**
     * Extract the syncedLyrics field from LRCLIB JSON response.
     */
    private fun extractSyncedLyrics(json: String): String? {
        return try {
            val jsonObject = org.json.JSONObject(json)
            if (jsonObject.has("syncedLyrics") && !jsonObject.isNull("syncedLyrics")) {
                jsonObject.getString("syncedLyrics")
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "JSON parse error", e)
            null
        }
    }

    
    /**
     * Parse LRC format lyrics into structured LrcLine objects.
     * LRC format: [mm:ss.xx]Lyrics text
     */
    fun parseLrc(lrcContent: String): List<LrcLine> {
        val lines = mutableListOf<LrcLine>()
        val pattern = Regex("""\[(\d{1,2}):(\d{2})\.(\d{2,3})](.*)""")
        
        for (line in lrcContent.lines()) {
            val match = pattern.find(line.trim()) ?: continue
            val (minutes, seconds, centis, text) = match.destructured
            
            // Convert to milliseconds
            val mins = minutes.toIntOrNull() ?: continue
            val secs = seconds.toIntOrNull() ?: continue
            val cs = centis.toIntOrNull() ?: continue
            
            // Handle both 2-digit (centiseconds) and 3-digit (milliseconds) formats
            val millisFromCentis = if (centis.length == 2) cs * 10 else cs
            val timeMs = (mins * 60 * 1000L) + (secs * 1000L) + millisFromCentis
            
            // Only add lines with actual text
            val trimmedText = text.trim()
            if (trimmedText.isNotEmpty()) {
                lines.add(LrcLine(timeMs, trimmedText))
            }
        }
        
        return lines.sortedBy { it.timeMs }
    }
    
    /**
     * Clean up search terms by removing common suffixes/prefixes that hurt matching.
     * Also sanitizes characters that can cause API errors.
     */
    private fun cleanSearchTerm(term: String): String {
        if (term.isBlank()) return ""
        
        return term
            .replace(Regex("""\s*\(.*?\)\s*"""), " ")  // Remove parenthetical content
            .replace(Regex("""\s*\[.*?]\s*"""), " ")   // Remove bracketed content
            .replace(Regex("""\s*-\s*(Official|Music|Video|Audio|Lyrics|HD|HQ|4K).*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""[^\p{L}\p{N}\s\-']"""), "") // Remove special chars except letters, numbers, spaces, hyphens, apostrophes
            .replace(Regex("""\s+"""), " ")            // Normalize whitespace
            .trim()
            .take(100) // Limit length to prevent API issues
    }
    
    /**
     * Clear the lyrics cache.
     */
    fun clearCache() {
        cache.clear()
    }
}
