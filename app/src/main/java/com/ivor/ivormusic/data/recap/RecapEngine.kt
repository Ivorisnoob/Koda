package com.ivor.ivormusic.data.recap

import com.ivor.ivormusic.data.PlayHistoryEntry
import com.ivor.ivormusic.data.isUnknownArtist
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Builds a recap from the listening history, on the device, for any period.
 *
 * Every story is computed from the plays themselves - timestamps, songs,
 * artists, the time heard - and is left out (null) when the plays do not
 * support it, rather than made to fit: a recap of one quiet week says less,
 * and says nothing false. There is no genre, mood or "personality" anywhere,
 * because the history holds none.
 *
 * Pure, so it is tested without a device: the caller supplies the history,
 * the time zone and today.
 */
object RecapEngine {

    /** A silence this long between one play's end and the next one's start ends a session. */
    private const val SESSION_BREAK_MS = 30 * 60_000L

    private class Play(val entry: PlayHistoryEntry, val date: LocalDate, val hour: Int, val minute: Float, val day: Int)

    /**
     * The recap of [period] from [history] (any order). Null when nothing was
     * played in it. [history] is the whole history, not just the period: what
     * came before decides what is new and what came back.
     */
    fun build(period: RecapPeriod, history: List<PlayHistoryEntry>, zone: ZoneId, today: LocalDate): Recap? {
        val start = period.startMillis(zone)
        val end = period.endMillis(zone)
        val all = history.sortedBy { it.timestamp }
        val inPeriod = all.filter { it.timestamp in start until end }
        if (inPeriod.isEmpty()) return null

        val plays = inPeriod.map { entry ->
            val at = Instant.ofEpochMilli(entry.timestamp).atZone(zone)
            val date = at.toLocalDate()
            Play(
                entry = entry,
                date = date,
                hour = at.hour,
                minute = at.hour * 60f + at.minute + at.second / 60f,
                day = ChronoUnit.DAYS.between(period.firstDay, date).toInt(),
            )
        }
        val elapsedDays = if (period.inProgress) {
            (ChronoUnit.DAYS.between(period.firstDay, today).toInt() + 1).coerceIn(1, period.dayCount)
        } else {
            period.dayCount
        }

        // --- Songs -------------------------------------------------------
        val bySong = plays.groupBy { it.entry.songId }
        val songs = bySong.mapValues { (id, list) -> songOf(id, list) }
        val topSongs = songs.values
            .sortedWith(
                compareByDescending<RecapSong> { it.days }
                    .thenByDescending { it.plays }
                    .thenByDescending { it.heardMs }
                    .thenBy { it.title }
            )
            .take(10)

        // --- Artists -----------------------------------------------------
        val weeksWithListening = plays.map { it.day / 7 }.toSet()
        val byArtist = plays.filterNot { isUnknownArtist(it.entry.artist) }.groupBy { it.entry.artist }
        val artists = byArtist.map { (name, list) ->
            val hourMs = LongArray(24)
            list.forEach { hourMs[it.hour] += it.entry.heardMs }
            val peak = (0 until 24).maxBy { h -> hourMs[h] + hourMs[(h + 1) % 24] + hourMs[(h + 2) % 24] }
            val peakHour = (peak + 1) % 24
            RecapArtist(
                name = name,
                plays = list.size,
                days = list.map { it.date }.distinct().size,
                songs = list.map { it.entry.songId }.distinct().size,
                heardMs = list.sumOf { it.entry.heardMs },
                hourMs = hourMs.toList(),
                peakHour = peakHour,
                dayPart = dayPartOf(peakHour),
                songIds = list.groupingBy { it.entry.songId }.eachCount().entries
                    .sortedByDescending { it.value }.map { it.key },
                everyWeek = list.map { it.day / 7 }.toSet().containsAll(weeksWithListening),
            )
        }.sortedWith(
            compareByDescending<RecapArtist> { it.plays }
                .thenByDescending { it.heardMs }
                .thenBy { it.name }
        )
        val topArtists = artists.take(5)
        val namedPlays = byArtist.values.sumOf { it.size }

        // --- Hours -------------------------------------------------------
        val hourMs = LongArray(24)
        plays.forEach { hourMs[it.hour] += it.entry.heardMs }
        val band = (0 until 24).maxBy { h -> hourMs[h] + hourMs[(h + 1) % 24] }
        val inBand = plays.filter { it.hour == band || it.hour == (band + 1) % 24 }
        val hours = RecapHours(
            heardMs = hourMs.toList(),
            bandStart = band,
            bandArtist = inBand.filterNot { isUnknownArtist(it.entry.artist) }
                .groupBy { it.entry.artist }.maxByOrNull { (_, l) -> l.sumOf { it.entry.heardMs } }?.key,
            bandSongId = inBand.groupBy { it.entry.songId }
                .maxByOrNull { (_, l) -> l.sumOf { it.entry.heardMs } }?.key,
        )

        // --- Calendar ----------------------------------------------------
        val byDay = plays.groupBy { it.day }
        val days = (0 until elapsedDays).map { index ->
            val list = byDay[index].orEmpty()
            RecapDay(
                index = index,
                date = period.firstDay.plusDays(index.toLong()),
                plays = list.size,
                heardMs = list.sumOf { it.entry.heardMs },
                songId = list.groupBy { it.entry.songId }
                    .maxByOrNull { (_, l) -> l.sumOf { it.entry.heardMs } }?.key,
            )
        }

        val segments = segmentsOf(period, plays)

        return Recap(
            period = period,
            catalog = catalogOf(inPeriod),
            plays = plays.size,
            activeDays = byDay.size,
            elapsedDays = elapsedDays,
            heardMs = plays.sumOf { it.entry.heardMs },
            measuredShare = plays.count { it.entry.listenedMs != null }.toFloat() / plays.size,
            songCount = songs.size,
            artistCount = byArtist.size,
            topSongs = topSongs,
            topArtists = topArtists,
            topArtistShare = if (namedPlays == 0) 0f else topArtists.firstOrNull()?.let { it.plays.toFloat() / plays.size } ?: 0f,
            race = raceOf(plays, songs, elapsedDays),
            hours = hours,
            days = days,
            cameBack = cameBackOf(all, plays, songs),
            longSitting = longSittingOf(plays),
            newArtist = newArtistOf(all, start, plays),
            oddHour = oddHourOf(plays),
            arcs = plays.map {
                RecapArc(it.day, it.minute, it.entry.heardMs / 60_000f, it.entry.songId)
            },
            segments = segments,
            playsOf = topSongs.associate { song ->
                song.id to bySong[song.id].orEmpty().map { it.entry }.sortedByDescending { it.timestamp }
            },
            songs = songs,
        )
    }

    /** One preview per period of [kind] that has any listening, newest first. */
    fun previews(
        kind: RecapKind,
        history: List<PlayHistoryEntry>,
        zone: ZoneId,
        today: LocalDate,
        weekStart: DayOfWeek,
    ): List<RecapPreview> {
        if (history.isEmpty()) return emptyList()
        val firstEver = history.minOf { it.timestamp }.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
        val grouped = HashMap<LocalDate, MutableList<Pair<PlayHistoryEntry, LocalDate>>>()
        for (entry in history) {
            val date = Instant.ofEpochMilli(entry.timestamp).atZone(zone).toLocalDate()
            if (date.isAfter(today)) continue
            val period = RecapPeriod.containing(kind, date, today, weekStart, firstEver)
            grouped.getOrPut(period.firstDay) { mutableListOf() } += entry to date
        }
        return grouped.map { (_, list) ->
            val period = RecapPeriod.containing(kind, list.first().second, today, weekStart, firstEver)
            val total = list.sumOf { it.first.heardMs }.coerceAtLeast(1L)
            val bySong = list.groupBy { it.first.songId }
                .mapValues { (_, l) -> l.sumOf { it.first.heardMs } }
                .entries.sortedByDescending { it.value }
            val top = bySong.firstOrNull()?.key
            RecapPreview(
                period = period,
                plays = list.size,
                heardMs = list.sumOf { it.first.heardMs },
                activeDays = list.map { it.second }.distinct().size,
                stripe = bySong.take(8).map { it.key to it.value.toFloat() / total },
                topSongId = top,
                topThumbnail = list.lastOrNull { it.first.songId == top }?.first?.thumbnailUrl
                    ?: list.firstOrNull { it.first.songId == top }?.first?.thumbnailUrl,
            )
        }.sortedByDescending { it.period.firstDay }
    }

    private fun songOf(id: String, list: List<Play>): RecapSong {
        val newest = list.maxBy { it.entry.timestamp }.entry
        return RecapSong(
            id = id,
            title = newest.title,
            artist = newest.artist,
            album = newest.album,
            thumbnailUrl = list.asReversed().firstNotNullOfOrNull { it.entry.thumbnailUrl },
            source = newest.source,
            durationMs = newest.duration,
            plays = list.size,
            days = list.map { it.day }.distinct().size,
            heardMs = list.sumOf { it.entry.heardMs },
            dayIndexes = list.map { it.day }.distinct().sorted(),
        )
    }

    fun dayPartOf(hour: Int): DayPart = when (hour) {
        in 5..11 -> DayPart.MORNING
        in 12..16 -> DayPart.AFTERNOON
        in 17..20 -> DayPart.EVENING
        else -> DayPart.NIGHT
    }

    /**
     * The song that ended up with the most plays, and the day it last took the
     * lead from another. Told only when the lead really changed: the period is
     * at least two weeks long, the song it passed had led for at least three
     * days, and the lead was never given back.
     */
    private fun raceOf(plays: List<Play>, songs: Map<String, RecapSong>, elapsedDays: Int): RecapRace? {
        if (elapsedDays < 14) return null
        val contenders = songs.values.sortedByDescending { it.plays }.take(10).map { it.id }
        if (contenders.size < 2) return null
        val perDay = HashMap<String, IntArray>()
        contenders.forEach { perDay[it] = IntArray(elapsedDays) }
        plays.forEach { play -> perDay[play.entry.songId]?.let { if (play.day in it.indices) it[play.day]++ } }
        val soFar = perDay.mapValues { (_, counts) ->
            var sum = 0
            IntArray(counts.size) { i -> sum += counts[i]; sum }
        }
        // The leader at the end of each day; a tie keeps the previous leader.
        var leader: String? = null
        val leaders = Array<String?>(elapsedDays) { null }
        for (d in 0 until elapsedDays) {
            var best = leader
            var bestCount = best?.let { soFar.getValue(it)[d] } ?: 0
            for (id in contenders) {
                val c = soFar.getValue(id)[d]
                if (c > bestCount) {
                    best = id
                    bestCount = c
                }
            }
            leader = best
            leaders[d] = best
        }
        val winner = leaders.last() ?: return null
        val change = (1 until elapsedDays).lastOrNull { leaders[it] == winner && leaders[it - 1] != winner } ?: return null
        val passed = leaders[change - 1] ?: return null
        val heldFor = (0 until change).count { leaders[it] == passed }
        if (heldFor < 3 || soFar.getValue(passed).last() < 5) return null
        return RecapRace(
            leader = songs.getValue(winner),
            passed = songs.getValue(passed),
            dayIndex = change,
            leaderSoFar = soFar.getValue(winner).toList(),
            passedSoFar = soFar.getValue(passed).toList(),
        )
    }

    /** The longest gap a song was left for before being played again inside the period. */
    private fun cameBackOf(all: List<PlayHistoryEntry>, plays: List<Play>, songs: Map<String, RecapSong>): RecapReturn? {
        val periodStart = plays.first().entry.timestamp
        val lastSeen = HashMap<String, Long>()
        var best: RecapReturn? = null
        for (entry in all) {
            val previous = lastSeen[entry.songId]
            lastSeen[entry.songId] = entry.timestamp
            if (previous == null || entry.timestamp < periodStart) continue
            val song = songs[entry.songId] ?: continue
            val gap = ((entry.timestamp - previous) / 86_400_000L).toInt()
            if (gap < 21) continue
            if (best == null || gap >= best.gapDays) {
                val week = entry.timestamp + 7 * 86_400_000L
                best = RecapReturn(
                    song = song,
                    gapDays = gap,
                    lastBefore = previous,
                    returnedAt = entry.timestamp,
                    playsWithinWeek = all.count { it.songId == entry.songId && it.timestamp >= entry.timestamp && it.timestamp < week },
                )
            }
        }
        return best
    }

    private fun longSittingOf(plays: List<Play>): RecapSession? {
        var best: RecapSession? = null
        var start = 0
        fun close(endExclusive: Int) {
            val run = plays.subList(start, endExclusive)
            val heard = run.sumOf { it.entry.heardMs }
            if (run.size >= 4 && heard >= 40 * 60_000L && (best == null || heard > best!!.heardMs)) {
                best = RecapSession(
                    startedAt = run.first().entry.timestamp,
                    heardMs = heard,
                    plays = run.size,
                    songIds = run.take(6).map { it.entry.songId },
                )
            }
        }
        for (i in 1 until plays.size) {
            val previous = plays[i - 1].entry
            if (plays[i].entry.timestamp - (previous.timestamp + previous.heardMs) >= SESSION_BREAK_MS) {
                close(i)
                start = i
            }
        }
        close(plays.size)
        return best
    }

    /**
     * An artist first heard in the period who was played on at least three of
     * its days. Only when there is listening from before the period: in a
     * history that starts here everything is new, and saying so means nothing.
     */
    private fun newArtistOf(all: List<PlayHistoryEntry>, periodStart: Long, plays: List<Play>): RecapNewArtist? {
        if (all.first().timestamp >= periodStart) return null
        val firstPlayed = HashMap<String, Long>()
        for (entry in all) firstPlayed.putIfAbsent(entry.artist, entry.timestamp)
        return plays.filterNot { isUnknownArtist(it.entry.artist) }
            .groupBy { it.entry.artist }
            .filter { (name, _) -> (firstPlayed[name] ?: 0L) >= periodStart }
            .map { (name, list) ->
                RecapNewArtist(
                    name = name,
                    firstPlayedAt = firstPlayed.getValue(name),
                    days = list.map { it.date }.distinct().size,
                    songId = list.groupingBy { it.entry.songId }.eachCount().maxBy { it.value }.key,
                )
            }
            .filter { it.days >= 3 }
            .maxByOrNull { it.days }
    }

    /** One play in the small hours, at an hour that almost never had any. */
    private fun oddHourOf(plays: List<Play>): PlayHistoryEntry? {
        if (plays.size < 20) return null
        val perHour = IntArray(24)
        plays.forEach { perHour[it.hour]++ }
        return plays
            .filter { it.hour in 1..4 && perHour[it.hour] <= 3 }
            .minWithOrNull(compareBy<Play> { perHour[it.hour] }.thenByDescending { it.entry.timestamp })
            ?.entry
    }

    private fun segmentsOf(period: RecapPeriod, plays: List<Play>): RecapSegments? {
        fun segmentStart(p: Play): LocalDate = when (period.kind) {
            RecapKind.WEEK -> p.date
            RecapKind.MONTH -> period.firstDay.plusDays((p.day / 7) * 7L)
            RecapKind.YEAR, RecapKind.ALL_TIME -> p.date.withDayOfMonth(1)
        }
        val bySegment = plays.groupBy(::segmentStart).toSortedMap()
        if (bySegment.size < 2) return null
        fun lead(list: List<Play>): String =
            list.groupBy { it.entry.songId }.maxBy { (_, l) -> l.sumOf { it.entry.heardMs } }.key
        val first = bySegment.entries.first()
        val last = bySegment.entries.last()
        val lastArtist = last.value.filterNot { isUnknownArtist(it.entry.artist) }
            .groupBy { it.entry.artist }.maxByOrNull { (_, l) -> l.sumOf { it.entry.heardMs } }?.key
        val nights = lastArtist?.let { name ->
            plays.filter { it.entry.artist == name && (it.hour >= 21 || it.hour < 4) }
                .map { if (it.hour < 4) it.date.minusDays(1) else it.date }
                .distinct().size
        } ?: 0
        return RecapSegments(
            firstStart = first.key,
            firstSongId = lead(first.value),
            lastStart = last.key,
            lastSongId = lead(last.value),
            lastArtist = lastArtist,
            lastArtistNights = nights,
        )
    }

    /** FNV-1a over the period's plays: the same listening always gets the same number. */
    private fun catalogOf(plays: List<PlayHistoryEntry>): String {
        var hash = 0x811C9DC5.toInt()
        for (entry in plays) {
            val text = "${entry.timestamp}${entry.songId}"
            for (ch in text) {
                hash = hash xor ch.code
                hash *= 0x01000193
            }
        }
        return "%08X".format(hash)
    }
}
