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
    
    // Fetches run on Dispatchers.IO; concurrent skips can hit this map from
    // multiple threads at once.
    private val cache = java.util.Collections.synchronizedMap(mutableMapOf<String, List<LrcLine>>())
    
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
        val json = fetchJson(url) ?: return null

        // Check if instrumental
        if (json.optBoolean("instrumental", false)) {
             return null // Or handle instrumental differently
        }

        return syncedLyricsOf(json)
    }

    /**
     * Read the syncedLyrics field defensively: lrclib sends an explicit JSON
     * null for plain-text-only entries, and org.json's optString turns that
     * into the literal string "null" instead of an empty string.
     */
    private fun syncedLyricsOf(json: JSONObject): String? {
        if (json.isNull("syncedLyrics")) return null
        return json.optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" }
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
            val synced = syncedLyricsOf(item) ?: continue

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
            return syncedLyricsOf(bestMatch)
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
        // 1. Standard LRC: [mm:ss.xx] text
        // 2. Enhanced LRC: [mm:ss.xx] <mm:ss.xx> word <mm:ss.xx> word
        val timeTagPattern = Regex("""\[(\d{1,2}):(\d{2})\.(\d{2,3})\]""")
        val wordTagPattern = Regex("""<(\d{1,2}):(\d{2})\.(\d{2,3})>""")
        
        for (line in normalizeLyrics(lrcContent).lines()) {
            val trimLine = line.trim()
            if (trimLine.isEmpty()) continue

            // Collect every leading time tag: repeated lines (choruses) are
            // compressed as [00:12.00][01:15.30]text with one tag per
            // occurrence, and all of them must produce a line — parsing only
            // the first leaks the remaining tags into the displayed text.
            val occurrenceTimes = mutableListOf<Long>()
            var contentStartIndex = 0
            while (true) {
                val tag = timeTagPattern.matchAt(trimLine, contentStartIndex) ?: break
                val (minutes, seconds, centis) = tag.destructured
                occurrenceTimes.add(parseTime(minutes, seconds, centis))
                contentStartIndex = tag.range.last + 1
                // Tolerate whitespace between stacked tags; the content is
                // trimmed below so this never eats meaningful text.
                while (contentStartIndex < trimLine.length && trimLine[contentStartIndex] == ' ') {
                    contentStartIndex++
                }
            }
            if (occurrenceTimes.isEmpty()) continue
            if (contentStartIndex >= trimLine.length) continue

            val timeMs = occurrenceTimes.first()
            val repeatTimes = occurrenceTimes.drop(1)
            val content = trimLine.substring(contentStartIndex).trim()
            
            // Check for word tags
            if (wordTagPattern.containsMatchIn(content)) {
                // Enhanced LRC parsing
                val spans = mutableListOf<LrcContentSpan>()

                // We split by tags but keep the text
                // Example: "Word1 <00:01.50> Word2"
                // Initial time is line timeMs.
                // We need to associate text segments with durations.
                
                val wordMatches = wordTagPattern.findAll(content).toList()
                
                // Text before first tag
                if (wordMatches.isNotEmpty()) {
                    val firstTagStart = wordMatches[0].range.first
                    if (firstTagStart > 0) {
                        val textSegment = content.substring(0, firstTagStart).trim()
                        if (textSegment.isNotEmpty()) {
                             val nextTime = parseTime(wordMatches[0].groupValues[1], wordMatches[0].groupValues[2], wordMatches[0].groupValues[3])
                             spans.add(LrcContentSpan(timeMs, textSegment, nextTime - timeMs))
                        }
                    }
                }
                
                for (i in wordMatches.indices) {
                    val currentMatch = wordMatches[i]
                    val (wm, ws, wc) = currentMatch.destructured
                    val currentTime = parseTime(wm, ws, wc)
                    
                    val nextMatchStartIndex = if (i + 1 < wordMatches.size) wordMatches[i+1].range.first else content.length
                    val textStart = currentMatch.range.last + 1
                    
                    if (textStart < nextMatchStartIndex) {
                        val textSegment = content.substring(textStart, nextMatchStartIndex).trim()
                        if (textSegment.isNotEmpty()) {
                            // Duration is up to next tag or unknown
                            // For last segment, we don't know duration yet
                            val nextTime = if (i + 1 < wordMatches.size) {
                                val (nm, ns, nc) = wordMatches[i+1].destructured
                                parseTime(nm, ns, nc)
                            } else {
                                0L // Unknown, wil be filled later
                            }
                            
                            val duration = if (nextTime > 0) nextTime - currentTime else 0L
                            spans.add(LrcContentSpan(currentTime, textSegment, duration))
                        }
                    }
                }
                
                // Clean text for display (remove tags)
                val cleanText = content.replace(wordTagPattern, "").replace(Regex("\\s+"), " ").trim()
                lines.add(LrcLine(timeMs, cleanText, spans))
                // Word tags carry absolute times, so they only fit the first
                // occurrence; repeats fall back to line-level sync.
                repeatTimes.forEach { lines.add(LrcLine(it, cleanText)) }

            } else {
                // Standard LRC
                if (content.isNotEmpty()) {
                    lines.add(LrcLine(timeMs, content))
                    repeatTimes.forEach { lines.add(LrcLine(it, content)) }
                }
            }
        }
        
        val sortedLines = lines.sortedBy { it.timeMs }

        // Post-processing: Fill missing durations ONLY for explicit spans
        return sortedLines.mapIndexed { index, line ->
            val nextLineTime = if (index + 1 < sortedLines.size) sortedLines[index+1].timeMs else line.timeMs + 5000
            
            if (line.contentSpans.isNotEmpty()) {
                 // Fix last span duration if it was 0 (unknown)
                 val newSpans = line.contentSpans.mapIndexed { spanIndex, span ->
                     if (span.durationMs == 0L && spanIndex == line.contentSpans.lastIndex) {
                         span.copy(durationMs = (nextLineTime - span.timeMs).coerceAtLeast(500))
                     } else span
                 }
                 line.copy(contentSpans = newSpans)
            } else {
                // Standard LRC: No spans. Let UI handle it as line-synced.
                line
            }
        }
    }
    
    private fun parseTime(min: String, sec: String, centis: String): Long {
        val m = min.toIntOrNull() ?: 0
        val s = sec.toIntOrNull() ?: 0
        val c = centis.toIntOrNull() ?: 0
        val ms = if (centis.length == 2) c * 10 else c
        return (m * 60 * 1000L) + (s * 1000L) + ms
    }
    
    fun clearCache() {
        cache.clear()
    }
}
