package com.ivor.ivormusic.ui.tv

import com.ivor.ivormusic.data.tv.TvEpisode
import com.ivor.ivormusic.data.tv.TvItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The pure logic behind TV playback: what plays next, and how things are
 * labelled.
 *
 * Next-episode selection is the one worth pinning down. It runs unattended at
 * the end of every episode, and a wrong answer is not an error - it is the next
 * thing simply starting, which reads as the app choosing badly rather than as a
 * bug.
 */
class TvPlayerLogicTest {

    private fun episode(season: Int, number: Int, id: String = "tt1:" + season + ":" + number) =
        TvEpisode(id = id, name = "E" + number, season = season, episode = number)

    private fun series(vararg episodes: TvEpisode) =
        TvItem(id = "tt1", type = "series", name = "Show", videos = episodes.toList())

    // --- Next episode -------------------------------------------------------

    @Test
    fun advancesWithinTheSeason() {
        val show = series(episode(1, 1), episode(1, 2), episode(1, 3))
        val next = TvPlayerViewModel.findNextEpisode(show, episode(1, 1))
        assertEquals("tt1:1:2", next?.id)
    }

    @Test
    fun aFinaleAdvancesIntoTheNextSeason() {
        val show = series(episode(1, 1), episode(1, 2), episode(2, 1))
        val next = TvPlayerViewModel.findNextEpisode(show, episode(1, 2))
        assertEquals("tt1:2:1", next?.id)
    }

    @Test
    fun specialsNeverLead() {
        // Season 0 is specials. A season-one finale must go to season two, not
        // into the OVAs, which is what sorting by season number alone would do.
        val show = series(
            episode(0, 1, "tt1:0:1"),
            episode(1, 1),
            episode(1, 2),
            episode(2, 1),
        )
        assertEquals("tt1:2:1", TvPlayerViewModel.findNextEpisode(show, episode(1, 2))?.id)
    }

    @Test
    fun theLastEpisodeOfTheLastSeasonHasNoNext() {
        val show = series(episode(1, 1), episode(1, 2))
        assertNull(TvPlayerViewModel.findNextEpisode(show, episode(1, 2)))
    }

    @Test
    fun aGapInNumberingDoesNotEndTheRun() {
        // Addressed by position within the season, not by number + 1.
        val show = series(episode(1, 1), episode(1, 5), episode(1, 6))
        assertEquals("tt1:1:5", TvPlayerViewModel.findNextEpisode(show, episode(1, 1))?.id)
    }

    @Test
    fun aFilmHasNoNextEpisode() {
        val film = TvItem(id = "tt2", type = "movie", name = "Film")
        assertNull(TvPlayerViewModel.findNextEpisode(film, null))
    }

    @Test
    fun anEpisodeThatIsNotInTheListYieldsNothing() {
        // A stale episode from a different show must not advance into this one.
        val show = series(episode(1, 1), episode(1, 2))
        assertNull(
            TvPlayerViewModel.findNextEpisode(show, episode(1, 9, id = "other:1:9"))
        )
    }

    // --- Labels -------------------------------------------------------------

    @Test
    fun audioTracksAreLabelledByLanguageAndLayout() {
        assertEquals("Japanese 5.1", TvPlayerViewModel.audioLabel("ja", null, 6))
        assertEquals("English 2.0", TvPlayerViewModel.audioLabel("en", null, 2))
    }

    @Test
    fun anUndeterminedLanguageFallsBackToTheEmbeddedLabel() {
        // "und" is what a muxer writes when nobody tagged the track, and
        // "Undetermined 5.1" tells a viewer less than the file's own label.
        assertEquals(
            "Commentary 5.1",
            TvPlayerViewModel.audioLabel("und", "Commentary", 6)
        )
    }

    @Test
    fun aTrackWithNothingToSayIsStillNamed() {
        // Never an empty row: an unlabelled track is still selectable.
        assertEquals("Audio", TvPlayerViewModel.audioLabel(null, null, 0))
    }

    @Test
    fun playbackTimeGrowsAnHoursFieldOnlyWhenThereIsOne() {
        assertEquals("0:00", formatPlaybackTime(0))
        assertEquals("4:12", formatPlaybackTime(252_000))
        assertEquals("1:42:07", formatPlaybackTime(6_127_000))
    }

    @Test
    fun episodeLabelsUseWhicheverHalfExists() {
        assertEquals("S2E4  The Title", episodeLabel(
            TvEpisode(id = "x", name = "The Title", season = 2, episode = 4)
        ))
        assertEquals("Just A Title", episodeLabel(TvEpisode(id = "x", name = "Just A Title")))
        assertEquals("S1E1", episodeLabel(TvEpisode(id = "x", season = 1, episode = 1)))
    }

    // --- Formatting ---------------------------------------------------------

    @Test
    fun sizesUseBinaryUnitsTheWayReleasesArePosted() {
        val gib = 1024L * 1024 * 1024
        assertEquals("2.0 GB", formatSize(2 * gib))
        assertEquals("700 MB", formatSize(700 * 1024 * 1024))
    }

    @Test
    fun languageChipsAreNamedNotCoded() {
        assertEquals("English", languageLabel("en"))
        assertEquals("Japanese", languageLabel("ja"))
    }

    @Test
    fun anUnknownLanguageCodeStaysTappable() {
        // Dropping it would leave a filter with no way to clear it.
        assertEquals("ZZ", languageLabel("zz"))
    }
}
