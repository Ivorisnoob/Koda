package com.ivor.ivormusic.ui.tv

import com.ivor.ivormusic.data.tv.TvEpisode
import com.ivor.ivormusic.data.tv.TvItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Episode range chips.
 *
 * These exist for the thousand-episode case, and the failure that matters is
 * them appearing on a six-episode season - a control that does nothing is worse
 * than no control.
 */
class EpisodeRangeTest {

    private fun show(count: Int, season: Int = 1, startAt: Int = 1) = TvItem(
        id = "tt1", type = "series", name = "Show",
        videos = (0 until count).map { i ->
            TvEpisode(id = "tt1:$season:${startAt + i}", season = season, episode = startAt + i)
        }
    )

    @Test
    fun shortSeasonsGetNoRangeChips() {
        assertTrue(TvDetailViewModel.rangesFor(show(6), 1).isEmpty())
        assertTrue(TvDetailViewModel.rangesFor(show(60), 1).isEmpty())
    }

    @Test
    fun longSeasonsSplitIntoBlocksOfFifty() {
        val ranges = TvDetailViewModel.rangesFor(show(120), 1)
        assertEquals(3, ranges.size)
        assertEquals(EpisodeRange(1, 50), ranges[0])
        assertEquals(EpisodeRange(51, 100), ranges[1])
        assertEquals(EpisodeRange(101, 120), ranges[2])
    }

    @Test
    fun theLastBlockStopsAtTheRealFinalEpisode() {
        // Not at a round 50, which would offer chips for episodes that do not exist.
        val ranges = TvDetailViewModel.rangesFor(show(1100), 1)
        assertEquals(1100, ranges.last().last)
    }

    @Test
    fun rangesFollowTheActualNumberingRatherThanTheCount() {
        // A season continuing another's numbering starts where it starts.
        val ranges = TvDetailViewModel.rangesFor(show(120, season = 2, startAt = 501), 2)
        assertEquals(501, ranges.first().first)
        assertEquals(620, ranges.last().last)
    }

    @Test
    fun labelsReadAsRanges() {
        assertEquals("51-100", EpisodeRange(51, 100).label)
    }

    @Test
    fun aNullItemOrEmptySeasonIsHandled() {
        assertTrue(TvDetailViewModel.rangesFor(null, 1).isEmpty())
        assertTrue(TvDetailViewModel.rangesFor(show(0), 1).isEmpty())
    }

    @Test
    fun rangesCoverEveryEpisodeWithNoGapsOrOverlaps() {
        val ranges = TvDetailViewModel.rangesFor(show(237), 1)
        assertEquals(1, ranges.first().first)
        assertEquals(237, ranges.last().last)
        ranges.zipWithNext().forEach { (a, b) ->
            assertEquals("no gap or overlap", a.last + 1, b.first)
        }
    }
}
