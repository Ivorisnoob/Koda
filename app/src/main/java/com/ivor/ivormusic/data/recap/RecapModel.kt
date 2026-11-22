package com.ivor.ivormusic.data.recap

import com.ivor.ivormusic.data.PlayHistoryEntry
import com.ivor.ivormusic.data.SongSource
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** The lengths of time a recap can cover. Stored nowhere; the order is the shelf's. */
enum class RecapKind { WEEK, MONTH, YEAR, ALL_TIME }

/**
 * One recap's span of days, inclusive at both ends. [inProgress] when today
 * falls inside it: the recap is then a "test pressing" that keeps changing,
 * and every count runs to today rather than to [lastDay].
 */
data class RecapPeriod(
    val kind: RecapKind,
    val firstDay: LocalDate,
    val lastDay: LocalDate,
    val inProgress: Boolean,
) {
    /** Stable across launches: what the shelf remembers a choice by. */
    val id: String get() = "${kind.name}:$firstDay"

    fun startMillis(zone: ZoneId): Long = firstDay.atStartOfDay(zone).toInstant().toEpochMilli()

    /** Exclusive. */
    fun endMillis(zone: ZoneId): Long = lastDay.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    /** Days from the first to the last, both counted. */
    val dayCount: Int get() = (lastDay.toEpochDay() - firstDay.toEpochDay() + 1).toInt()

    companion object {
        /** The period of [kind] holding [day]. [firstEver] anchors All time. */
        fun containing(kind: RecapKind, day: LocalDate, today: LocalDate, weekStart: DayOfWeek, firstEver: LocalDate): RecapPeriod {
            val (first, last) = when (kind) {
                RecapKind.WEEK -> {
                    val start = day.with(TemporalAdjusters.previousOrSame(weekStart))
                    start to start.plusDays(6)
                }
                RecapKind.MONTH -> day.withDayOfMonth(1) to day.with(TemporalAdjusters.lastDayOfMonth())
                RecapKind.YEAR -> day.withDayOfYear(1) to day.with(TemporalAdjusters.lastDayOfYear())
                RecapKind.ALL_TIME -> firstEver to today
            }
            return RecapPeriod(kind, first, last, inProgress = !today.isBefore(first) && !today.isAfter(last))
        }
    }
}

/** A song's part in one recap. [dayIndexes] are the period's days it was played on. */
data class RecapSong(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val thumbnailUrl: String?,
    val source: SongSource,
    val durationMs: Long,
    val plays: Int,
    val days: Int,
    val heardMs: Long,
    val dayIndexes: List<Int>,
)

enum class DayPart { MORNING, AFTERNOON, EVENING, NIGHT }

/** An artist's part in one recap, with when in the day they were played. */
data class RecapArtist(
    val name: String,
    val plays: Int,
    val days: Int,
    val songs: Int,
    val heardMs: Long,
    /** Heard time by the hour each play started in, 0 to 23. */
    val hourMs: List<Long>,
    /** The middle of the three hours holding most of their listening. */
    val peakHour: Int,
    val dayPart: DayPart,
    /** Their songs, most played first. */
    val songIds: List<String>,
    /** Whether every week of the period that had any listening had them in it. */
    val everyWeek: Boolean,
)

/** The day a song took the lead for good, and the song it passed. */
data class RecapRace(
    val leader: RecapSong,
    val passed: RecapSong,
    /** Index into the period's days of the day the lead changed hands. */
    val dayIndex: Int,
    /** Plays so far at the end of each day, for both. */
    val leaderSoFar: List<Int>,
    val passedSoFar: List<Int>,
)

data class RecapHours(
    /** Heard time by the hour each play started in. */
    val heardMs: List<Long>,
    /** The first hour of the two-hour band holding the most listening. */
    val bandStart: Int,
    val bandArtist: String?,
    val bandSongId: String?,
)

/** One day of the calendar. [songId] is what was heard most that day. */
data class RecapDay(
    val index: Int,
    val date: LocalDate,
    val plays: Int,
    val heardMs: Long,
    val songId: String?,
)

/** A song left alone for [gapDays], then played again inside the period. */
data class RecapReturn(
    val song: RecapSong,
    val gapDays: Int,
    val lastBefore: Long,
    val returnedAt: Long,
    val playsWithinWeek: Int,
)

/** The longest stretch of playing without a half-hour break. */
data class RecapSession(
    val startedAt: Long,
    val heardMs: Long,
    val plays: Int,
    val songIds: List<String>,
)

/** An artist first heard inside the period who then kept coming back. */
data class RecapNewArtist(
    val name: String,
    val firstPlayedAt: Long,
    val days: Int,
    val songId: String,
)

/** One play on the pressing: its day's groove, where it started, how long it was heard. */
data class RecapArc(
    val day: Int,
    val startMinute: Float,
    val heardMinutes: Float,
    val songId: String,
)

/**
 * What the light was at the start and at the end: the first and last parts
 * of the period that had any listening (months of a year, weeks of a month,
 * days of a week), each with the song heard most in it. Null when only one
 * part had any.
 */
data class RecapSegments(
    val firstStart: LocalDate,
    val firstSongId: String,
    val lastStart: LocalDate,
    val lastSongId: String,
    /** Who led the last part, and on how many nights of the period they were played. */
    val lastArtist: String?,
    val lastArtistNights: Int,
)

/** Everything one recap can say. Each story is null when the plays do not support it. */
data class Recap(
    val period: RecapPeriod,
    /** A short code made from this exact listening: the pressing's catalog number. */
    val catalog: String,
    val plays: Int,
    val activeDays: Int,
    /** Days counted so far: all of them, or up to today while in progress. */
    val elapsedDays: Int,
    val heardMs: Long,
    /** The share of plays whose time was measured rather than taken from the song's length. */
    val measuredShare: Float,
    val songCount: Int,
    val artistCount: Int,
    val topSongs: List<RecapSong>,
    val topArtists: List<RecapArtist>,
    val topArtistShare: Float,
    val race: RecapRace?,
    val hours: RecapHours,
    val days: List<RecapDay>,
    val cameBack: RecapReturn?,
    val longSitting: RecapSession?,
    val newArtist: RecapNewArtist?,
    val oddHour: PlayHistoryEntry?,
    val arcs: List<RecapArc>,
    val segments: RecapSegments?,
    /** Each top song's plays in the period, newest first: what a card's proof lists. */
    val playsOf: Map<String, List<PlayHistoryEntry>>,
    /** Every song mentioned anywhere, for cover colours. */
    val songs: Map<String, RecapSong>,
)

/** A period on the shelf: enough to draw its spine and card without building the recap. */
data class RecapPreview(
    val period: RecapPeriod,
    val plays: Int,
    val heardMs: Long,
    val activeDays: Int,
    /** The most heard songs and their share of the period's time, most first. */
    val stripe: List<Pair<String, Float>>,
    val topSongId: String?,
    val topThumbnail: String?,
)
