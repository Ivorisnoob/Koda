package com.ivor.ivormusic.data

import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.supervisorScope
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlinx.coroutines.suspendCancellableCoroutine

internal data class LyricsRequest(
    val songId: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long
) {
    val durationSeconds: Int
        get() = (durationMs / 1_000L).toInt().coerceAtLeast(0)
}

internal interface RemoteLyricsProvider {
    val name: String
    val priority: Int
    suspend fun fetch(request: LyricsRequest): ParsedLyrics?
}

internal class LyricsHttpClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(7, TimeUnit.SECONDS)
        .readTimeout(7, TimeUnit.SECONDS)
        .callTimeout(9, TimeUnit.SECONDS)
        .build()

    suspend fun get(baseUrl: String, parameters: Map<String, Any?> = emptyMap()): HttpPayload {
        val builder = Uri.parse(baseUrl).buildUpon()
        parameters.forEach { (name, value) ->
            if (value != null) builder.appendQueryParameter(name, value.toString())
        }
        val request = Request.Builder()
            .url(builder.build().toString())
            .header("Accept", "application/json, application/xml, text/plain, */*")
            .header("User-Agent", "Koda Android (https://github.com/Ivorisnoob/Koda)")
            .build()

        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        val body = it.body?.string().orEmpty()
                        if (continuation.isActive) continuation.resume(HttpPayload(it.code, body))
                    }
                }
            })
        }
    }
}

internal data class HttpPayload(val code: Int, val body: String) {
    val isSuccessful: Boolean
        get() = code in 200..299
}

internal fun defaultWordLyricsProviders(http: LyricsHttpClient): List<RemoteLyricsProvider> = listOf(
    YouLyPlusProvider(http, priority = 0),
    BetterLyricsProvider(http, priority = 1),
    NeteaseLyricsProvider(http, priority = 2)
)

internal fun defaultFallbackLyricsProviders(http: LyricsHttpClient): List<RemoteLyricsProvider> = listOf(
    SimpMusicLyricsProvider(http, priority = 3),
    LrcLibLyricsProvider(http, priority = 4),
    KuGouLyricsProvider(http, priority = 5),
    UnisonLyricsProvider(http, priority = 6)
)

private class BetterLyricsProvider(
    private val http: LyricsHttpClient,
    override val priority: Int
) : RemoteLyricsProvider {
    override val name = "BetterLyrics"

    override suspend fun fetch(request: LyricsRequest): ParsedLyrics? {
        if (request.artist.isBlank()) return null
        val parameters = metadataParameters(request, shortNames = true)
        val endpoints = listOf(
            "https://lyrics-api.boidu.dev/getLyrics",
            "https://lyrics-api.boidu.dev/qq/getLyrics",
            "https://lyrics-api.boidu.dev/kugou/getLyrics"
        )
        for (endpoint in endpoints) {
            val payload = http.get(endpoint, parameters)
            if (!payload.isSuccessful || payload.body.isBlank()) continue
            decodeWrappedLyrics(payload.body)?.let(LyricsParser::parse)?.let { return it }
        }
        return null
    }
}

private class YouLyPlusProvider(
    private val http: LyricsHttpClient,
    override val priority: Int
) : RemoteLyricsProvider {
    override val name = "YouLyPlus"

    private val mirrors = listOf(
        "https://lyricsplus.binimum.org/",
        "https://lyricsplus.prjktla.my.id/",
        "https://lyricsplus.prjktla.workers.dev/",
        "https://lyricsplus.atomix.one/",
        "https://lyricsplus-seven.vercel.app/"
    )

    override suspend fun fetch(request: LyricsRequest): ParsedLyrics? {
        if (request.artist.isBlank()) return null
        raceMirrors("v1/ttml/get", request) { body ->
            decodeWrappedLyrics(body)?.let(LyricsParser::parse)
        }?.let { return it }

        return raceMirrors("v2/lyrics/get", request, ::decodeStructuredLyrics)
    }

    private suspend fun raceMirrors(
        path: String,
        request: LyricsRequest,
        decode: (String) -> ParsedLyrics?
    ): ParsedLyrics? = supervisorScope {
        val parameters = metadataParameters(request, shortNames = false)
        val pending = mirrors.map { mirror ->
            async {
                try {
                    val payload = http.get(mirror + path, parameters)
                    if (payload.isSuccessful) decode(payload.body) else null
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
            }
        }.toMutableList()

        while (pending.isNotEmpty()) {
            val completed = select<Pair<kotlinx.coroutines.Deferred<ParsedLyrics?>, ParsedLyrics?>> {
                pending.forEach { deferred ->
                    deferred.onAwait { deferred to it }
                }
            }
            pending.remove(completed.first)
            completed.second?.let { result ->
                pending.forEach { it.cancel() }
                return@supervisorScope result
            }
        }
        null
    }

    private fun decodeStructuredLyrics(body: String): ParsedLyrics? {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return null
        val linesJson = root.optJSONArray("lyrics") ?: return null
        val lines = mutableListOf<LrcLine>()
        var hasWords = false

        for (index in 0 until linesJson.length()) {
            val item = linesJson.optJSONObject(index) ?: continue
            val time = item.optLongOrNull("time")
            val lineDuration = item.optLongOrNull("duration") ?: 0L
            val text = item.optNullableString("text").orEmpty()
            val syllables = item.optJSONArray("syllabus")
            val spans = mutableListOf<LrcContentSpan>()
            if (syllables != null) {
                for (wordIndex in 0 until syllables.length()) {
                    val word = syllables.optJSONObject(wordIndex) ?: continue
                    val wordText = word.optNullableString("text").orEmpty()
                    val wordTime = word.optLongOrNull("time") ?: continue
                    if (wordText.isEmpty()) continue
                    spans += LrcContentSpan(
                        timeMs = wordTime,
                        text = wordText,
                        durationMs = word.optLongOrNull("duration") ?: 0L
                    )
                }
            }
            if (time != null && lineDuration > 0L && spans.isNotEmpty()) {
                val lastIndex = spans.lastIndex
                val last = spans[lastIndex]
                if (last.durationMs <= 0L) {
                    spans[lastIndex] = last.copy(
                        durationMs = (time + lineDuration - last.timeMs).coerceAtLeast(0L)
                    )
                }
            }
            val lineText = text.ifBlank { spans.joinToString(separator = "") { it.text }.trim() }
            if (lineText.isBlank()) continue
            if (time != null) {
                hasWords = hasWords || spans.isNotEmpty()
                lines += LrcLine(time, lineText, spans)
            } else {
                lines += LrcLine(-1L, lineText)
            }
        }

        if (lines.isEmpty()) return null
        val syncType = when {
            hasWords -> LyricsSyncType.WORD
            lines.any { it.timeMs >= 0L } -> LyricsSyncType.LINE
            else -> LyricsSyncType.PLAIN
        }
        return ParsedLyrics(fillMissingSpanDurations(lines), syncType)
    }
}

private class NeteaseLyricsProvider(
    private val http: LyricsHttpClient,
    override val priority: Int
) : RemoteLyricsProvider {
    override val name = "NetEase"
    private val baseUrl = "https://lyrics.paxsenix.org/"

    override suspend fun fetch(request: LyricsRequest): ParsedLyrics? {
        if (request.artist.isBlank()) return null
        val search = http.get(baseUrl + "netease/search", mapOf("q" to "${request.title} ${request.artist}"))
        if (!search.isSuccessful) return null
        val songs = runCatching {
            JSONObject(search.body).optJSONObject("result")?.optJSONArray("songs")
        }.getOrNull() ?: return null
        val best = closestJsonItem(songs, request.durationMs, "duration", 10_000L) ?: return null
        val id = best.optLongOrNull("id") ?: return null
        val lyrics = http.get(baseUrl + "netease/lyrics", mapOf("id" to id, "word" to true))
        if (!lyrics.isSuccessful) return null
        val root = runCatching { JSONObject(lyrics.body) }.getOrNull() ?: return null
        val content = root.optJSONObject("klyric")?.optNullableString("lyric")
            ?: root.optJSONObject("lrc")?.optNullableString("lyric")
            ?: return null
        return LyricsParser.parse(content)
    }
}

private class SimpMusicLyricsProvider(
    private val http: LyricsHttpClient,
    override val priority: Int
) : RemoteLyricsProvider {
    override val name = "SimpMusic"

    override suspend fun fetch(request: LyricsRequest): ParsedLyrics? {
        if (request.songId.isBlank()) return null
        val response = http.get("https://api-lyrics.simpmusic.org/v1/${Uri.encode(request.songId)}")
        if (!response.isSuccessful) return null
        val root = runCatching { JSONObject(response.body) }.getOrNull() ?: return null
        if (!root.optString("type").equals("success", ignoreCase = true)) return null
        val data = root.optJSONArray("data") ?: return null
        val best = closestJsonItem(data, request.durationMs, "durationSeconds", 12_000L, valueIsSeconds = true)
            ?: data.optJSONObject(0)
            ?: return null
        return sequenceOf("richSyncLyrics", "syncedLyrics", "plainLyric")
            .mapNotNull { best.optNullableString(it) }
            .mapNotNull { LyricsParser.parse(it) }
            .firstOrNull()
    }
}

private class LrcLibLyricsProvider(
    private val http: LyricsHttpClient,
    override val priority: Int
) : RemoteLyricsProvider {
    override val name = "LRCLIB"

    override suspend fun fetch(request: LyricsRequest): ParsedLyrics? {
        if (request.artist.isNotBlank() && request.durationSeconds > 0) {
            val exact = http.get(
                "https://lrclib.net/api/get",
                mapOf(
                    "track_name" to request.title,
                    "artist_name" to request.artist,
                    "album_name" to request.album.takeIf(String::isNotBlank),
                    "duration" to request.durationSeconds
                )
            )
            if (exact.isSuccessful) {
                val item = runCatching { JSONObject(exact.body) }.getOrNull()
                parseLrcLibItem(item)?.let { return it }
            }
        }

        val search = http.get(
            "https://lrclib.net/api/search",
            mapOf(
                "track_name" to request.title,
                "artist_name" to request.artist,
                "album_name" to request.album.takeIf(String::isNotBlank)
            )
        )
        if (!search.isSuccessful) return null
        val results = runCatching { JSONArray(search.body) }.getOrNull() ?: return null
        val ranked = (0 until results.length()).mapNotNull { results.optJSONObject(it) }.sortedByDescending { item ->
            lrcLibScore(item, request)
        }
        for (item in ranked) {
            if (request.durationMs > 0L) {
                val candidateMs = (item.optDouble("duration", 0.0) * 1_000.0).toLong()
                if (candidateMs > 0L && abs(candidateMs - request.durationMs) > 10_000L) continue
            }
            parseLrcLibItem(item)?.let { return it }
        }
        return null
    }

    private fun parseLrcLibItem(item: JSONObject?): ParsedLyrics? {
        item ?: return null
        if (item.optBoolean("instrumental", false)) return null
        return item.optNullableString("syncedLyrics")?.let(LyricsParser::parse)
            ?: item.optNullableString("plainLyrics")?.let(LyricsParser::parse)
    }

    private fun lrcLibScore(item: JSONObject, request: LyricsRequest): Int {
        var score = 0
        val title = item.optString("trackName")
        val artist = item.optString("artistName")
        val album = item.optString("albumName")
        if (title.equals(request.title, ignoreCase = true)) score += 40
        else if (title.contains(request.title, ignoreCase = true) || request.title.contains(title, ignoreCase = true)) score += 18
        if (artist.equals(request.artist, ignoreCase = true)) score += 30
        else if (artist.contains(request.artist, ignoreCase = true) || request.artist.contains(artist, ignoreCase = true)) score += 12
        if (request.album.isNotBlank() && album.equals(request.album, ignoreCase = true)) score += 10
        val durationMs = (item.optDouble("duration", 0.0) * 1_000.0).toLong()
        if (request.durationMs > 0L && durationMs > 0L) {
            val difference = abs(durationMs - request.durationMs)
            if (difference <= 2_000L) score += 20 else if (difference <= 10_000L) score += 8
        }
        if (item.optNullableString("syncedLyrics") != null) score += 5
        return score
    }
}

private class KuGouLyricsProvider(
    private val http: LyricsHttpClient,
    override val priority: Int
) : RemoteLyricsProvider {
    override val name = "KuGou"

    override suspend fun fetch(request: LyricsRequest): ParsedLyrics? {
        val keyword = "${cleanTitle(request.title)} - ${cleanArtist(request.artist)}"
        val songSearch = http.get(
            "https://mobileservice.kugou.com/api/v3/search/song",
            mapOf("version" to 9108, "plat" to 0, "pagesize" to 8, "showtype" to 0, "keyword" to keyword)
        )
        if (songSearch.isSuccessful) {
            val songs = runCatching {
                JSONObject(songSearch.body).optJSONObject("data")?.optJSONArray("info")
            }.getOrNull()
            if (songs != null) {
                for (index in 0 until songs.length()) {
                    val song = songs.optJSONObject(index) ?: continue
                    val duration = song.optLongOrNull("duration") ?: 0L
                    if (request.durationSeconds > 0 && abs(duration - request.durationSeconds) > 8L) continue
                    val hash = song.optNullableString("hash") ?: continue
                    findCandidate(mapOf("hash" to hash))?.let { candidate ->
                        download(candidate)?.let { return it }
                    }
                }
            }
        }

        return findCandidate(
            mapOf(
                "duration" to request.durationSeconds.takeIf { it > 0 }?.times(1_000),
                "keyword" to keyword
            )
        )?.let { download(it) }
    }

    private suspend fun findCandidate(searchParameters: Map<String, Any?>): JSONObject? {
        val response = http.get(
            "https://lyrics.kugou.com/search",
            mapOf("ver" to 1, "man" to "yes", "client" to "pc") + searchParameters
        )
        if (!response.isSuccessful) return null
        return runCatching { JSONObject(response.body).optJSONArray("candidates")?.optJSONObject(0) }.getOrNull()
    }

    private suspend fun download(candidate: JSONObject): ParsedLyrics? {
        val id = candidate.optLongOrNull("id") ?: return null
        val accessKey = candidate.optNullableString("accesskey") ?: return null
        val response = http.get(
            "https://lyrics.kugou.com/download",
            mapOf("fmt" to "lrc", "charset" to "utf8", "client" to "pc", "ver" to 1, "id" to id, "accesskey" to accessKey)
        )
        if (!response.isSuccessful) return null
        val encoded = runCatching { JSONObject(response.body).optNullableString("content") }.getOrNull() ?: return null
        val decoded = runCatching { String(Base64.decode(encoded, Base64.DEFAULT), Charsets.UTF_8) }.getOrNull() ?: return null
        return LyricsParser.parse(decoded)
    }

    private fun cleanTitle(title: String): String =
        title.replace(Regex("""[（(「『<《〈＜].*?[）)」』>》〉＞]"""), "").trim()

    private fun cleanArtist(artist: String): String =
        artist.replace(", ", "、").replace(" & ", "、").replace(".", "").trim()
}

private class UnisonLyricsProvider(
    private val http: LyricsHttpClient,
    override val priority: Int
) : RemoteLyricsProvider {
    override val name = "Unison"
    private val baseUrl = "https://unison.boidu.dev/"

    override suspend fun fetch(request: LyricsRequest): ParsedLyrics? {
        if (request.artist.isNotBlank()) {
            val search = http.get(
                baseUrl + "lyrics/search",
                mapOf(
                    "song" to request.title,
                    "artist" to request.artist,
                    "album" to request.album.takeIf(String::isNotBlank),
                    "duration" to request.durationSeconds.takeIf { it > 0 },
                    "limit" to 5
                )
            )
            if (search.isSuccessful) {
                val results = runCatching { JSONObject(search.body).optJSONArray("data") }.getOrNull()
                if (results != null) {
                    for (index in 0 until results.length()) {
                        val item = results.optJSONObject(index) ?: continue
                        item.optNullableString("lyrics")?.let(LyricsParser::parse)?.let { return it }
                        val id = item.optLongOrNull("id") ?: continue
                        fetchByUrl(baseUrl + "lyrics/$id")?.let { return it }
                    }
                }
            }
        }

        if (request.songId.isBlank()) return null
        return fetchByUrl(baseUrl + "lyrics", mapOf("v" to request.songId))
    }

    private suspend fun fetchByUrl(url: String, parameters: Map<String, Any?> = emptyMap()): ParsedLyrics? {
        val response = http.get(url, parameters)
        if (!response.isSuccessful) return null
        val lyrics = runCatching {
            JSONObject(response.body).optJSONObject("data")?.optNullableString("lyrics")
        }.getOrNull() ?: return null
        return LyricsParser.parse(lyrics)
    }
}

private fun metadataParameters(request: LyricsRequest, shortNames: Boolean): Map<String, Any?> =
    if (shortNames) {
        mapOf(
            "s" to request.title,
            "a" to request.artist,
            "al" to request.album.takeIf(String::isNotBlank),
            "d" to request.durationSeconds.takeIf { it > 0 }
        )
    } else {
        mapOf(
            "title" to request.title,
            "artist" to request.artist,
            "album" to request.album.takeIf(String::isNotBlank),
            "duration" to request.durationSeconds.takeIf { it > 0 }
        )
    }

private fun decodeWrappedLyrics(body: String): String? {
    val trimmed = body.trim()
    if (trimmed.startsWith("<")) return trimmed
    val root = runCatching { JSONObject(trimmed) }.getOrNull() ?: return null
    return sequenceOf("ttml", "lyrics", "lrc", "content", "text")
        .mapNotNull { root.optNullableString(it) }
        .firstOrNull(String::isNotBlank)
}

private fun closestJsonItem(
    array: JSONArray,
    targetDurationMs: Long,
    durationKey: String,
    toleranceMs: Long,
    valueIsSeconds: Boolean = false
): JSONObject? {
    val items = (0 until array.length()).mapNotNull { array.optJSONObject(it) }
    if (items.isEmpty()) return null
    if (targetDurationMs <= 0L) return items.first()
    return items.minByOrNull { item ->
        val raw = item.optDouble(durationKey, 0.0)
        val duration = if (valueIsSeconds) (raw * 1_000.0).toLong() else raw.toLong()
        abs(duration - targetDurationMs)
    }?.takeIf { item ->
        val raw = item.optDouble(durationKey, 0.0)
        val duration = if (valueIsSeconds) (raw * 1_000.0).toLong() else raw.toLong()
        duration <= 0L || abs(duration - targetDurationMs) <= toleranceMs
    }
}

private fun JSONObject.optNullableString(key: String): String? {
    if (!has(key) || isNull(key)) return null
    return optString(key).trim().takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
}

private fun JSONObject.optLongOrNull(key: String): Long? {
    if (!has(key) || isNull(key)) return null
    return when (val value = opt(key)) {
        is Number -> value.toLong()
        is String -> value.toLongOrNull()
        else -> null
    }
}

private fun fillMissingSpanDurations(lines: List<LrcLine>): List<LrcLine> {
    val sorted = lines.sortedBy { it.timeMs }
    return sorted.mapIndexed { lineIndex, line ->
        if (line.contentSpans.isEmpty()) return@mapIndexed line
        val lineEnd = sorted.getOrNull(lineIndex + 1)?.timeMs?.takeIf { it > line.timeMs } ?: line.timeMs + 5_000L
        val sortedSpans = line.contentSpans.sortedBy { it.timeMs }
        line.copy(
            contentSpans = sortedSpans.mapIndexed { spanIndex, span ->
                if (span.durationMs > 0L) span else {
                    val end = sortedSpans.getOrNull(spanIndex + 1)?.timeMs ?: lineEnd
                    span.copy(durationMs = (end - span.timeMs).coerceIn(80L, 10_000L))
                }
            }
        )
    }
}
