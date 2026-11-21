package com.ivor.ivormusic.data.recap

import com.ivor.ivormusic.data.PlayHistoryEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

class RecapEngineTest {

    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 11, 21)

    private fun play(
        id: String,
        artist: String,
        at: LocalDateTime,
        listened: Long? = 200_000L,
        duration: Long = 200_000L,
    ) = PlayHistoryEntry(
        songId = id, title = "Song $id", artist = artist, album = "",
        timestamp = at.toInstant(zone).toEpochMilli(), duration = duration, listenedMs = listened,
    )

    private fun period(kind: RecapKind, day: LocalDate) =
        RecapPeriod.containing(kind, day, today, DayOfWeek.MONDAY, LocalDate.of(2026, 1, 1))

    private fun day(month: Int, d: Int, hour: Int = 12, minute: Int = 0) = LocalDateTime.of(2026, month, d, hour, minute)

    @Test
    fun nothingPlayedIsNoRecap() {
        val history = listOf(play("a", "A", day(1, 5)))
        assertNull(RecapEngine.build(period(RecapKind.MONTH, LocalDate.of(2026, 3, 1)), history, zone, today))
    }

    @Test
    fun theTopSongIsTheOneOnMostDaysNotMostPlays() {
        // "a" ten times in one day; "b" once a day on three days.
        val history = List(10) { play("a", "A", day(6, 2, 10, it * 4)) } +
            (1..3).map { play("b", "B", day(6, 10 + it)) }
        val recap = RecapEngine.build(period(RecapKind.MONTH, LocalDate.of(2026, 6, 1)), history, zone, today)!!
        assertEquals("b", recap.topSongs.first().id)
        assertEquals(3, recap.topSongs.first().days)
    }

    @Test
    fun measuredTimeIsUsedAndOldPlaysFallBackToTheirLength() {
        val history = listOf(
            play("a", "A", day(6, 1), listened = 30_000L, duration = 200_000L),
            play("b", "B", day(6, 2), listened = null, duration = 100_000L),
        )
        val recap = RecapEngine.build(period(RecapKind.MONTH, LocalDate.of(2026, 6, 1)), history, zone, today)!!
        assertEquals(130_000L, recap.heardMs)
        assertEquals(0.5f, recap.measuredShare, 0.001f)
    }

    @Test
    fun aSongComingBackAfterAMonthIsFoundWithItsGap() {
        val history = listOf(
            play("x", "X", day(5, 1)),
            play("x", "X", day(6, 15)),
            play("x", "X", day(6, 16)),
        )
        val recap = RecapEngine.build(period(RecapKind.MONTH, LocalDate.of(2026, 6, 1)), history, zone, today)!!
        val back = assertNotNull(recap.cameBack).let { recap.cameBack!! }
        assertEquals("x", back.song.id)
        assertEquals(45, back.gapDays)
        assertEquals(2, back.playsWithinWeek)
    }

    @Test
    fun theOvertakeIsTheLastTimeTheLeadChangedHands() {
        // "early" leads the first week, "late" takes over in the third and keeps it.
        val history = (1..7).flatMap { d -> List(2) { play("early", "E", day(6, d, 10, it)) } } +
            (8..30).map { d -> play("late", "L", day(6, d)) }
        val recap = RecapEngine.build(period(RecapKind.MONTH, LocalDate.of(2026, 6, 1)), history, zone, today)!!
        val race = recap.race!!
        assertEquals("late", race.leader.id)
        assertEquals("early", race.passed.id)
        // Fourteen each at the end of 21 June (index 20) is a tie, and a tie
        // keeps the old leader; "late" passes on the 22nd, index 21.
        assertEquals(21, race.dayIndex)
    }

    @Test
    fun aShortPeriodHasNoRace() {
        val history = (1..5).map { play("a", "A", day(6, it)) } + (1..7).map { play("b", "B", day(6, it, 13)) }
        val recap = RecapEngine.build(period(RecapKind.WEEK, LocalDate.of(2026, 6, 3)), history, zone, today)!!
        assertNull(recap.race)
    }

    @Test
    fun nothingIsNewInAHistoryThatStartsInThePeriod() {
        val history = (1..10).map { play("a$it", "Fresh", day(6, it)) }
        val recap = RecapEngine.build(period(RecapKind.MONTH, LocalDate.of(2026, 6, 1)), history, zone, today)!!
        assertNull(recap.newArtist)
    }

    @Test
    fun anArtistFirstHeardInThePeriodAndKeptIsNew() {
        val history = listOf(play("old", "Old", day(4, 1))) +
            (5..9).map { play("n$it", "Newcomer", day(6, it)) }
        val recap = RecapEngine.build(period(RecapKind.MONTH, LocalDate.of(2026, 6, 1)), history, zone, today)!!
        assertEquals("Newcomer", recap.newArtist?.name)
        assertEquals(5, recap.newArtist?.days)
    }

    @Test
    fun theLongSittingIsPlaysWithoutAHalfHourBreak() {
        // Twelve songs back to back from 22:00, then one alone the next day.
        val start = day(6, 3, 22)
        val history = List(12) { play("s$it", "A", start.plusMinutes(it * 4L), listened = 240_000L) } +
            play("lone", "A", day(6, 4, 9))
        val recap = RecapEngine.build(period(RecapKind.MONTH, LocalDate.of(2026, 6, 1)), history, zone, today)!!
        val sitting = recap.longSitting!!
        assertEquals(12, sitting.plays)
        assertEquals(48 * 60_000L, sitting.heardMs)
    }

    @Test
    fun hoursAreBandedByWhenPlaysStarted() {
        val history = List(20) { play("n$it", "Night", day(6, 1 + it % 20, 22, 30)) } +
            List(5) { play("m$it", "Morning", day(6, 1 + it, 8)) }
        val recap = RecapEngine.build(period(RecapKind.MONTH, LocalDate.of(2026, 6, 1)), history, zone, today)!!
        assertTrue(recap.hours.bandStart == 21 || recap.hours.bandStart == 22)
        assertEquals("Night", recap.hours.bandArtist)
        assertEquals(DayPart.NIGHT, recap.topArtists.first { it.name == "Night" }.dayPart)
        assertEquals(DayPart.MORNING, recap.topArtists.first { it.name == "Morning" }.dayPart)
    }

    @Test
    fun theCatalogNumberIsTheSameForTheSameListening() {
        val history = (1..5).map { play("a$it", "A", day(6, it)) }
        val p = period(RecapKind.MONTH, LocalDate.of(2026, 6, 1))
        val one = RecapEngine.build(p, history, zone, today)!!.catalog
        val two = RecapEngine.build(p, history.shuffled(), zone, today)!!.catalog
        assertEquals(one, two)
        assertEquals(8, one.length)
    }

    @Test
    fun previewsAreNewestFirstOnePerPeriodWithListening() {
        val history = listOf(play("a", "A", day(3, 2)), play("b", "B", day(6, 2)), play("c", "C", day(6, 20)))
        val previews = RecapEngine.previews(RecapKind.MONTH, history, zone, today, DayOfWeek.MONDAY)
        assertEquals(listOf(6, 3), previews.map { it.period.firstDay.monthValue })
        assertEquals(2, previews.first().plays)
    }

    @Test
    fun aMonthInProgressCountsOnlyToToday() {
        val history = listOf(play("a", "A", LocalDateTime.of(2026, 11, 2, 12, 0)))
        val recap = RecapEngine.build(period(RecapKind.MONTH, today), history, zone, today)!!
        assertTrue(recap.period.inProgress)
        assertEquals(21, recap.elapsedDays)
        assertEquals(21, recap.days.size)
    }
}
