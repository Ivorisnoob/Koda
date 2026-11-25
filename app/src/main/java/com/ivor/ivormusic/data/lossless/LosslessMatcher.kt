package com.ivor.ivormusic.data.lossless

import java.text.Normalizer
import kotlin.math.abs

/** One search result from the lossless server. */
data class LosslessTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "",
    /** Seconds; 0 when the server did not say. */
    val durationSec: Int = 0,
    val format: String = "flac",
    val quality: String = "",
)

/**
 * Picks the server's copy of a YouTube song, or nothing.
 *
 * The Windows client took the first title that merely contained the song's
 * title, and failing that the first result of all: a live take, a remix or a
 * different song could be saved as the one asked for, and nobody would know
 * until it played. Here a candidate has to clear three bars - the title, the
 * artist and, where both sides know it, the length - and versions the request
 * did not ask for (live, remix, acoustic, sped up...) are held against it.
 * No candidate clearing them is an honest "not on the server".
 *
 * Pure, so it is pinned by a JVM test.
 */
object LosslessMatcher {

    /** Lengths further apart than this are different recordings. */
    private const val MAX_DURATION_GAP_SEC = 12

    private const val MIN_TITLE_SCORE = 0.6

    // Remasters and "Taylor's Version" are left out on purpose: they are the
    // same song to the listener, and catalogues spell them differently.
    private val VERSION_WORDS = listOf(
        "live", "remix", "acoustic", "instrumental", "karaoke", "sped up", "slowed",
        "reverb", "nightcore", "cover", "demo", "radio edit", "extended",
        "a cappella", "acapella", "8d",
    )

    /** Noise YouTube titles carry that a catalogue title never does. */
    private val YOUTUBE_NOISE = Regex(
        """\((?:official|lyric|lyrics|audio|video|visuali[sz]er|music video|hd|hq|4k)[^)]*\)|\[(?:official|lyric|lyrics|audio|video|visuali[sz]er|music video|hd|hq|4k)[^]]*]""",
        RegexOption.IGNORE_CASE
    )
    // A bracketed guest anywhere, or a bare "feat." to the end. A bare "with"
    // stays: it is part of too many real titles ("Stay With Me").
    private val FEATURING = Regex(
        """[(\[]\s*(?:feat|ft|featuring|with)\b\.?[^)\]]*[)\]]|\s(?:feat|ft|featuring)\.?\s.*$""",
        RegexOption.IGNORE_CASE
    )
    private val ARTIST_SPLIT = Regex("""\s*(?:,|&|\band\b|\bx\b|\bvs\.?\b|\bfeat\.?\b|\bft\.?\b|\bfeaturing\b|\bwith\b|/|;)\s*""", RegexOption.IGNORE_CASE)
    private val NON_WORD = Regex("""[^\p{L}\p{N}]+""")
    private val DIACRITICS = Regex("""\p{Mn}+""")

    /** The text sent as the search: the clean title and the lead artist. */
    fun query(title: String, artist: String): String {
        val cleanTitle = FEATURING.replace(YOUTUBE_NOISE.replace(title, " "), " ")
            .replace(Regex("""\s+"""), " ").trim()
        val lead = artists(artist).firstOrNull().orEmpty()
        return listOf(cleanTitle.ifBlank { title }, lead).filter { it.isNotBlank() }.joinToString(" ")
    }

    /**
     * The best candidate for a song of [title] by [artist], [durationMs] long
     * (0 when unknown), or null when none is convincing.
     */
    fun pick(
        title: String,
        artist: String,
        durationMs: Long,
        candidates: List<LosslessTrack>,
    ): LosslessTrack? {
        val wantedTitle = normalizeTitle(title)
        if (wantedTitle.isBlank()) return null
        val wantedArtists = artists(artist).map(::normalize).filter { it.isNotBlank() }
        val wantedVersions = versionWords(title)
        val wantedSec = (durationMs / 1000L).toInt()

        return candidates
            .mapNotNull { candidate ->
                val titleScore = titleScore(wantedTitle, normalizeTitle(candidate.title))
                if (titleScore < MIN_TITLE_SCORE) return@mapNotNull null

                val candidateArtists = artists(candidate.artist).map(::normalize).filter { it.isNotBlank() }
                val artistScore = artistScore(wantedArtists, candidateArtists)
                // A server that names no artist cannot be checked; one that
                // names a different one is a different song.
                if (candidateArtists.isNotEmpty() && wantedArtists.isNotEmpty() && artistScore == 0.0) {
                    return@mapNotNull null
                }

                val gap = if (wantedSec > 0 && candidate.durationSec > 0) {
                    abs(wantedSec - candidate.durationSec)
                } else null
                if (gap != null && gap > MAX_DURATION_GAP_SEC) return@mapNotNull null
                val durationScore = when {
                    gap == null -> 0.5
                    gap <= 2 -> 1.0
                    gap <= 5 -> 0.7
                    else -> 0.3
                }

                // A version the request did not name (or one it did that this
                // copy lacks) is a different recording.
                val extraVersions = versionWords(candidate.title) - wantedVersions
                val missingVersions = wantedVersions - versionWords(candidate.title)
                val versionPenalty = 0.6 * extraVersions.size + 0.4 * missingVersions.size

                val score = titleScore * 2.0 + artistScore * 1.5 + durationScore - versionPenalty
                if (score < 2.0) null else candidate to score
            }
            .maxByOrNull { it.second }
            ?.first
    }

    internal fun normalize(text: String): String =
        DIACRITICS.replace(Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD), "")
            .replace("&", " and ")
            .let { NON_WORD.replace(it, " ") }
            .trim()
            .replace(Regex("""\s+"""), " ")

    /** The title without YouTube's decorations and featured guests. */
    internal fun normalizeTitle(title: String): String =
        normalize(FEATURING.replace(YOUTUBE_NOISE.replace(title, " "), " "))

    internal fun artists(artist: String): List<String> =
        artist.split(ARTIST_SPLIT).map { it.trim() }.filter { it.isNotBlank() }

    private fun versionWords(title: String): Set<String> {
        val normalized = " ${normalize(title)} "
        return VERSION_WORDS.filterTo(mutableSetOf()) { normalized.contains(" $it ") }
    }

    private fun titleScore(wanted: String, candidate: String): Double {
        if (candidate.isBlank()) return 0.0
        if (wanted == candidate) return 1.0
        val a = wanted.split(' ').toSet()
        val b = candidate.split(' ').toSet()
        val jaccard = (a intersect b).size.toDouble() / (a union b).size
        // "Song" against "Song - 2011 Remaster": the shorter is all there.
        val contained = candidate.startsWith(wanted) || wanted.startsWith(candidate)
        return if (contained) maxOf(0.8, jaccard) else jaccard
    }

    private fun artistScore(wanted: List<String>, candidate: List<String>): Double {
        if (wanted.isEmpty() || candidate.isEmpty()) return 0.5
        val hits = wanted.count { w ->
            candidate.any { c -> c == w || c.contains(w) || w.contains(c) }
        }
        return when {
            hits == 0 -> 0.0
            normalize(wanted.first()) in candidate.map(::normalize) -> 1.0
            else -> 0.8
        }
    }
}
