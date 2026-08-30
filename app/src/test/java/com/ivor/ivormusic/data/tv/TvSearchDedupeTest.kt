package com.ivor.ivormusic.data.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cross-addon search deduplication.
 *
 * This is the piece of search whose failure is silent: get it wrong and the
 * user sees the same show twice, or - worse - the surviving row is the one that
 * cannot resolve to any stream.
 */
class TvSearchDedupeTest {

    private fun item(
        id: String,
        name: String,
        year: String? = null,
        poster: String? = "p",
        background: String? = null,
        logo: String? = null,
        type: String = "series",
    ) = TvItem(
        id = id, type = type, name = name, releaseInfo = year,
        poster = poster, background = background, logo = logo
    )

    @Test
    fun identicalIdsCollapseToOne() {
        val out = TvRepository.dedupe(
            listOf(item("tt1", "Dune"), item("tt1", "Dune"))
        )
        assertEquals(1, out.size)
    }

    @Test
    fun theAnimeNativeIdSurvivesOverTheImdbOne() {
        // Anime stream addons index on kitsu ids. Keeping the IMDb row would
        // give the user a page that finds nothing.
        val out = TvRepository.dedupe(
            listOf(
                item("tt22248376", "Sousou no Frieren", "2023"),
                item("kitsu:46474", "Sousou no Frieren", "2023"),
            )
        )
        assertEquals(1, out.size)
        assertEquals("kitsu:46474", out.first().id)
    }

    @Test
    fun orderOfArrivalDoesNotChangeTheWinner() {
        val reversed = TvRepository.dedupe(
            listOf(
                item("kitsu:46474", "Sousou no Frieren", "2023"),
                item("tt22248376", "Sousou no Frieren", "2023"),
            )
        )
        assertEquals("kitsu:46474", reversed.first().id)
    }

    @Test
    fun titlesDifferingOnlyByPunctuationAndCaseCollapse() {
        val out = TvRepository.dedupe(
            listOf(
                item("tt1", "Frieren: Beyond Journey's End", "2023"),
                item("kitsu:2", "frieren beyond journeys end", "2023"),
            )
        )
        assertEquals(1, out.size)
    }

    @Test
    fun sameTitleDifferentYearsAreDifferentThings() {
        // Remakes are not duplicates. Dune 1984 and Dune 2021 must both survive.
        val out = TvRepository.dedupe(
            listOf(item("tt0087182", "Dune", "1984"), item("tt1160419", "Dune", "2021"))
        )
        assertEquals(2, out.size)
    }

    @Test
    fun betweenTwoEquallyNativeIdsTheRicherArtworkWins() {
        val out = TvRepository.dedupe(
            listOf(
                item("tt1", "Show", "2020", poster = "p"),
                item("tt2", "Show", "2020", poster = "p", background = "b", logo = "l"),
            )
        )
        assertEquals(1, out.size)
        assertEquals("tt2", out.first().id)
    }

    @Test
    fun anEntryWithNoYearDoesNotSwallowEveryOtherYear() {
        val out = TvRepository.dedupe(
            listOf(
                item("tt1", "Dune", null),
                item("tt2", "Dune", "2021"),
            )
        )
        assertEquals(2, out.size)
    }

    @Test
    fun animeIdPrefixesAreRecognised() {
        assertTrue(item("kitsu:1", "A").isAnimeNativeId)
        assertTrue(item("mal:1", "A").isAnimeNativeId)
        assertTrue(item("anilist:1", "A").isAnimeNativeId)
        assertTrue(item("anidb:1", "A").isAnimeNativeId)
        assertTrue(!item("tt1", "A").isAnimeNativeId)
    }
}
