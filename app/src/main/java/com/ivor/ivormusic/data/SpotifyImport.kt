package com.ivor.ivormusic.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.Normalizer
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Reading a Spotify playlist or album so its songs can be found on YouTube
 * Music. Nothing is played or downloaded from Spotify, and there is no
 * Spotify account, key or token of the user's involved: the only request is
 * for the public embed page, the one a blog post showing the playlist loads.
 */

/** A playlist or album a Spotify link names. */
data class SpotifyRef(val kind: String, val id: String)

data class SpotifyTrack(
    val title: String,
    /** The artist line as Spotify prints it: several names joined by commas. */
    val artists: String,
    val durationMs: Long,
)

data class SpotifyCollection(
    val name: String,
    val owner: String?,
    val tracks: List<SpotifyTrack>,
    /**
     * The page stops at [SPOTIFY_EMBED_TRACK_LIMIT] songs and says nothing
     * about how many there really are, so a list that long may be cut short.
     */
    val mayBeTruncated: Boolean,
)

/** The embed page lists no more than this many songs of a playlist. [verified October 2026] */
const val SPOTIFY_EMBED_TRACK_LIMIT = 100

object SpotifyLinks {
    private val web = Regex(
        """open\.spotify\.com/(?:intl-[a-z]{2,3}(?:-[a-z]{2,4})?/)?(?:embed/)?(playlist|album)/([A-Za-z0-9]{22})"""
    )
    private val uri = Regex("""spotify:(playlist|album):([A-Za-z0-9]{22})""")

    /** The first playlist or album link in [text], which may be a whole shared message. */
    fun parse(text: String): SpotifyRef? {
        val match = web.find(text) ?: uri.find(text) ?: return null
        return SpotifyRef(match.groupValues[1], match.groupValues[2])
    }
}

internal object SpotifyEmbedParser {
    private val nextData = Regex(
        """<script id="__NEXT_DATA__" type="application/json">(.*?)</script>""",
        RegexOption.DOT_MATCHES_ALL
    )

    /**
     * The embed page carries its state as JSON in a `__NEXT_DATA__` script:
     * `props.pageProps.state.data.entity`, with `name`, `subtitle` (the owner)
     * and a `trackList` of `{title, subtitle, duration, entityType}` where
     * `subtitle` is the artist line and `duration` is milliseconds. The same
     * shape serves playlists and albums. [verified October 2026, anonymous]
     *
     * Null when the page is not that shape - a private or removed playlist
     * answers with a page that has no entity.
     */
    fun parse(html: String): SpotifyCollection? {
        val json = nextData.find(html)?.groupValues?.get(1) ?: return null
        val entity = runCatching {
            JSONObject(json).optJSONObject("props")?.optJSONObject("pageProps")
                ?.optJSONObject("state")?.optJSONObject("data")?.optJSONObject("entity")
        }.getOrNull() ?: return null
        val list = entity.optJSONArray("trackList") ?: return null
        val tracks = buildList {
            for (i in 0 until list.length()) {
                val item = list.optJSONObject(i) ?: continue
                // Playlists can hold podcast episodes, which have no song to find.
                if (item.optString("entityType", "track") != "track") continue
                val title = item.optString("title").trim()
                if (title.isEmpty()) continue
                add(
                    SpotifyTrack(
                        title = title,
                        artists = item.optString("subtitle").trim(),
                        durationMs = item.optLong("duration", 0L),
                    )
                )
            }
        }
        val name = entity.optString("name").ifBlank { entity.optString("title") }.trim()
        if (name.isEmpty() && tracks.isEmpty()) return null
        return SpotifyCollection(
            name = name,
            owner = entity.optString("subtitle").trim().takeIf { it.isNotEmpty() },
            tracks = tracks,
            mayBeTruncated = entity.optString("type") == "playlist" &&
                list.length() >= SPOTIFY_EMBED_TRACK_LIMIT,
        )
    }
}

/**
 * Whether a YouTube Music song is the same recording as a Spotify track.
 *
 * Deliberately strict: an import that guesses fills a playlist with covers and
 * live versions nobody asked for, which is why this feature was once turned
 * down. A song is taken only when the title is the same, the artist is the
 * same and the length agrees to within a couple of seconds; anything less is
 * reported as not found and left out.
 */
internal object SpotifyMatcher {
    /** YouTube reports whole seconds, so identical recordings can differ by a second or two. */
    private const val DURATION_SLACK_MS = 2_500L

    private val featuring = Regex("""[(\[]\s*(feat\.?|ft\.?|featuring|with)\s[^)\]]*[)\]]""", RegexOption.IGNORE_CASE)
    private val trailingFeaturing = Regex("""\s(feat\.?|ft\.?|featuring)\s.*$""", RegexOption.IGNORE_CASE)
    private val marks = Regex("""\p{Mn}+""")
    private val notWord = Regex("""[^\p{L}\p{N}]+""")

    /** Lower case, accents and punctuation gone, so "Beyoncé" and "Song - Live" compare cleanly. */
    fun normalize(text: String): String =
        marks.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "")
            .lowercase()
            .replace("&", " and ")
            .replace(notWord, " ")
            .trim()

    /**
     * Guest credits live in the title on one service and in the artist line on
     * the other, so they are dropped from the title before comparing. Version
     * words (live, remaster, acoustic) stay: those are different recordings.
     */
    fun normalizeTitle(title: String): String =
        normalize(trailingFeaturing.replace(featuring.replace(title, " "), ""))

    private val between = Regex("""\s*(?:,|;|•|&|\band\b|\bx\b|\bfeat\.?|\bft\.?)\s*""", RegexOption.IGNORE_CASE)

    /** The artists an artist line names, each normalized. */
    private fun names(line: String): List<String> =
        line.split(between).map(::normalize).filter { it.isNotEmpty() }

    private fun words(line: String): List<String> =
        normalize(line).split(" ").filter { it.isNotEmpty() && it != "and" }

    /**
     * The lead artist of either line is one of the artists the other names -
     * a whole name, so "Low" is not "Lowell" and "Band" is not "Tribute Band".
     * Names with a comma or an ampersand of their own ("Tyler, The Creator",
     * "Simon & Garfunkel") split wrongly on both sides alike, so the lines are
     * also compared whole: one may only add guests to the end of the other.
     */
    fun sameArtist(spotifyArtists: String, youtubeArtist: String): Boolean {
        val spotifyNames = names(spotifyArtists)
        val youtubeNames = names(youtubeArtist)
        if (spotifyNames.isEmpty() || youtubeNames.isEmpty()) return false
        if (spotifyNames.first() in youtubeNames || youtubeNames.first() in spotifyNames) return true
        val a = words(spotifyArtists)
        val b = words(youtubeArtist)
        val (short, long) = if (a.size <= b.size) a to b else b to a
        return short.isNotEmpty() && long.take(short.size) == short
    }

    fun isExact(track: SpotifyTrack, song: Song): Boolean {
        if (song.duration <= 0L || track.durationMs <= 0L) return false
        if (abs(song.duration - track.durationMs) > DURATION_SLACK_MS) return false
        if (normalizeTitle(track.title) != normalizeTitle(song.title)) return false
        return sameArtist(track.artists, song.artist)
    }

    /** The first exact match in search order, which is YouTube Music's own ranking. */
    fun pick(track: SpotifyTrack, candidates: List<Song>): Song? =
        candidates.firstOrNull { isExact(track, it) }

    /** What to type into YouTube Music's search for this track. */
    fun query(track: SpotifyTrack): String =
        "${track.title} ${track.artists.split(",").first().trim()}".trim()
}

/** Fetches the public embed page. One request per import. */
internal class SpotifyPlaylistSource {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()

    /** Null when the playlist is private, gone, or the page was not readable. */
    suspend fun load(ref: SpotifyRef): SpotifyCollection? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://open.spotify.com/embed/${ref.kind}/${ref.id}")
            .header("User-Agent", USER_AGENT)
            .header("Accept-Language", "en")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            response.body?.string()?.let(SpotifyEmbedParser::parse)
        }
    }

    private companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/126.0 Safari/537.36"
    }
}
