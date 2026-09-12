package com.ivor.ivormusic.data

import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

internal object LastFmProtocol {
    fun signature(parameters: Map<String, String>, secret: String): String {
        val text = parameters.filterKeys { it !in setOf("format", "callback", "api_sig") }
            .toSortedMap().entries.joinToString("") { it.key + it.value } + secret
        return MessageDigest.getInstance("MD5").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    fun objects(value: Any?): List<JSONObject> = when (value) {
        is JSONArray -> (0 until value.length()).mapNotNull { value.optJSONObject(it) }
        is JSONObject -> listOf(value)
        else -> emptyList()
    }

    fun recent(root: JSONObject): List<LastFmTrack> = objects(root.getJSONObject("recenttracks").opt("track")).map {
        LastFmTrack(it.getString("name"), it.getJSONObject("artist").optString("#text"),
            it.optJSONObject("@attr")?.optString("nowplaying") == "true",
            album = it.optJSONObject("album")?.optString("#text").orEmpty(),
            artwork = imageUrl(it.opt("image")),
            playedAt = it.optJSONObject("date")?.optLong("uts", 0)?.takeIf { timestamp -> timestamp > 0 })
    }

    private fun imageUrl(value: Any?): String? = objects(value).asReversed()
        .map { it.optString("#text") }.firstOrNull { it.startsWith("https://") }

    fun profile(user: JSONObject) = LastFmProfile(
        realName = user.optString("realname"), country = user.optString("country"),
        avatar = imageUrl(user.opt("image")),
        registeredAt = user.optJSONObject("registered")?.optLong("unixtime", 0)?.takeIf { it > 0 },
        subscriber = user.optString("subscriber") == "1"
    )

    fun scrobbleCode(root: JSONObject): Int {
        val result = root.getJSONObject("scrobbles")
        val code = objects(result.opt("scrobble")).single().getJSONObject("ignoredMessage").getInt("code")
        val attributes = result.getJSONObject("@attr")
        check(attributes.getInt("accepted") == (if (code == 0) 1 else 0) &&
            attributes.getInt("ignored") == (if (code == 0) 0 else 1)) { "Inconsistent scrobble acknowledgement" }
        return code
    }
}

data class LastFmTrack(
    val title: String, val artist: String, val nowPlaying: Boolean,
    val album: String = "", val artwork: String? = null, val playedAt: Long? = null
)

data class LastFmProfile(
    val realName: String = "", val country: String = "", val avatar: String? = null,
    val registeredAt: Long? = null, val subscriber: Boolean = false
)

/** Counts audible wall time, never seek position or buffering time. */
internal class LastFmListeningClock {
    private var occurrence: String? = null
    private var lastTick = 0L
    private var wasPlaying = false
    private var listened = 0L
    private var submitted = false
    var startedAt = 0L
        private set

    fun reset() { occurrence = null; wasPlaying = false; listened = 0; submitted = false; startedAt = 0 }

    fun sample(id: String, playing: Boolean, nowMs: Long, unixSeconds: Long, durationMs: Long): Boolean {
        if (id != occurrence) {
            reset(); occurrence = id; lastTick = nowMs
        }
        if (playing && startedAt == 0L) startedAt = unixSeconds
        if (playing && wasPlaying) listened += (nowMs - lastTick).coerceIn(0, 1500)
        lastTick = nowMs
        wasPlaying = playing
        if (!submitted && durationMs > 30_000 && listened >= minOf(durationMs / 2, 240_000)) {
            submitted = true
            return true
        }
        return false
    }
}
