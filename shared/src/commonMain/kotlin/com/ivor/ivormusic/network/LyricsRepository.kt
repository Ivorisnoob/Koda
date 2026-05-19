package com.ivor.ivormusic.network

import com.ivor.ivormusic.domain.LrcContentSpan
import com.ivor.ivormusic.domain.LrcLine
import com.ivor.ivormusic.domain.LyricsResult
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLQueryComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs

class LyricsRepository(private val httpClient: HttpClient) {

    companion object {
        private const val API_BASE = "https://lrclib.net/api"
        private const val USER_AGENT = "IvorMusic/1.0 (https://github.com/Ivorisnoob/Koda)"
        private const val DURATION_TOLERANCE_SEC = 3
    }

    private val cache = mutableMapOf<String, List<LrcLine>>()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchLyrics(
        songId: String,
        title: String,
        artist: String,
        album: String = "",
        durationMs: Long
    ): LyricsResult = withContext(Dispatchers.IO) {
        if (title.isBlank()) return@withContext LyricsResult.NotFound
        cache[songId]?.let { return@withContext LyricsResult.Success(it) }

        try {
            val durationSec = (durationMs / 1000).toInt()

            val exactMatch = getLyricsExact(title, artist, album, durationSec)
            if (exactMatch != null) {
                val parsed = parseLrc(exactMatch)
                if (parsed.isNotEmpty()) {
                    cache[songId] = parsed
                    return@withContext LyricsResult.Success(parsed)
                }
            }

            val searchMatch = searchLyrics(title, artist, durationSec)
            if (searchMatch != null) {
                val parsed = parseLrc(searchMatch)
                if (parsed.isNotEmpty()) {
                    cache[songId] = parsed
                    return@withContext LyricsResult.Success(parsed)
                }
            }

            LyricsResult.NotFound
        } catch (e: Exception) {
            LyricsResult.Error(e.message ?: "Unknown error")
        }
    }

    private suspend fun getLyricsExact(title: String, artist: String, album: String, duration: Int): String? {
        if (title.isBlank() || artist.isBlank() || duration <= 0) return null
        val albumPart = if (album.isNotBlank()) "&album_name=${album.encodeURLQueryComponent()}" else ""
        val url = "$API_BASE/get?track_name=${title.encodeURLQueryComponent()}&artist_name=${artist.encodeURLQueryComponent()}&duration=$duration$albumPart"
        val responseText = doGet(url) ?: return null
        val obj = runCatching { json.parseToJsonElement(responseText).jsonObject }.getOrNull() ?: return null
        if (obj["instrumental"]?.jsonPrimitive?.boolean == true) return null
        return obj["syncedLyrics"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
    }

    private suspend fun searchLyrics(title: String, artist: String, duration: Int): String? {
        val query = "$title $artist"
        val url = "$API_BASE/search?q=${query.encodeURLQueryComponent()}"
        val responseText = doGet(url) ?: return null
        val arr = runCatching { json.parseToJsonElement(responseText).jsonArray }.getOrNull() ?: return null

        var bestSynced: String? = null
        var bestDiff = Int.MAX_VALUE

        for (elem in arr) {
            val item = runCatching { elem.jsonObject }.getOrNull() ?: continue
            val synced = item["syncedLyrics"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: continue
            val itemDuration = item["duration"]?.jsonPrimitive?.int ?: 0
            val diff = abs(itemDuration - duration)
            if (diff <= DURATION_TOLERANCE_SEC) return synced
            if (diff < bestDiff) { bestDiff = diff; bestSynced = synced }
        }

        return if (bestSynced != null && bestDiff <= 10) bestSynced else null
    }

    private suspend fun doGet(url: String): String? {
        return try {
            val response = httpClient.get(url) { header("User-Agent", USER_AGENT) }
            if (response.status == HttpStatusCode.NotFound) return null
            if (!response.status.value.toString().startsWith("2")) return null
            response.bodyAsText()
        } catch (_: Exception) { null }
    }

    fun parseLrc(lrcContent: String): List<LrcLine> {
        val lines = mutableListOf<LrcLine>()
        val timeTagPattern = Regex("""\[(\d{1,2}):(\d{2})\.(\d{2,3})\]""")
        val wordTagPattern = Regex("""<(\d{1,2}):(\d{2})\.(\d{2,3})>""")

        for (line in lrcContent.lines()) {
            val trimLine = line.trim()
            if (trimLine.isEmpty()) continue
            val match = timeTagPattern.find(trimLine) ?: continue
            val (minutes, seconds, centis) = match.destructured
            val timeMs = parseTime(minutes, seconds, centis)
            val contentStart = match.range.last + 1
            if (contentStart >= trimLine.length) continue
            val content = trimLine.substring(contentStart).trim()

            if (wordTagPattern.containsMatchIn(content)) {
                val spans = mutableListOf<LrcContentSpan>()
                val wordMatches = wordTagPattern.findAll(content).toList()
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
                    val cur = wordMatches[i]
                    val curTime = parseTime(cur.groupValues[1], cur.groupValues[2], cur.groupValues[3])
                    val nextStart = if (i + 1 < wordMatches.size) wordMatches[i + 1].range.first else content.length
                    val textStart = cur.range.last + 1
                    if (textStart < nextStart) {
                        val seg = content.substring(textStart, nextStart).trim()
                        if (seg.isNotEmpty()) {
                            val nextTime = if (i + 1 < wordMatches.size) parseTime(wordMatches[i+1].groupValues[1], wordMatches[i+1].groupValues[2], wordMatches[i+1].groupValues[3]) else 0L
                            spans.add(LrcContentSpan(curTime, seg, if (nextTime > 0) nextTime - curTime else 0L))
                        }
                    }
                }
                val cleanText = content.replace(wordTagPattern, "").replace(Regex("\\s+"), " ").trim()
                lines.add(LrcLine(timeMs, cleanText, spans))
            } else {
                if (content.isNotEmpty()) lines.add(LrcLine(timeMs, content))
            }
        }

        val sorted = lines.sortedBy { it.timeMs }
        return sorted.mapIndexed { index, line ->
            val nextTime = if (index + 1 < sorted.size) sorted[index + 1].timeMs else line.timeMs + 5000
            if (line.contentSpans.isNotEmpty()) {
                val newSpans = line.contentSpans.mapIndexed { si, span ->
                    if (span.durationMs == 0L && si == line.contentSpans.lastIndex)
                        span.copy(durationMs = (nextTime - span.timeMs).coerceAtLeast(500))
                    else span
                }
                line.copy(contentSpans = newSpans)
            } else line
        }
    }

    private fun parseTime(min: String, sec: String, centis: String): Long {
        val m = min.toIntOrNull() ?: 0
        val s = sec.toIntOrNull() ?: 0
        val c = centis.toIntOrNull() ?: 0
        val ms = if (centis.length == 2) c * 10 else c
        return (m * 60 * 1000L) + (s * 1000L) + ms
    }

    fun clearCache() = cache.clear()
}
