package com.ivor.ivormusic.data

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.random.Random

/** Lanes on the Tiles board. Four is the genre's shape and a thumb's reach. */
const val TILE_LANES = 4

/**
 * What a single tile asks of the player.
 *
 * Three kinds rather than one, because a chart of identical taps has no shape:
 * the bar line should feel different from the beats inside it, and a held note
 * is the only way a sustained passage reads as sustained.
 */
enum class TileKind {
    /** One tap on the beat. The body of every chart. */
    TAP,

    /** A tap on the bar's downbeat. Worth double, printed differently. */
    ACCENT,

    /** Press and stay down until the tail clears the line. */
    HOLD
}

/**
 * One playable tile.
 *
 * [startMs] and [endMs] are absolute positions in the track, so the chart is a
 * pure function of the song and survives seeking, pausing and re-entry without
 * any notion of "when the game started".
 */
data class Tile(
    val lane: Int,
    val startMs: Long,
    val endMs: Long,
    val kind: TileKind,
    /**
     * How long the tile is on screen, expressed as travel time rather than
     * pixels so the board can be any height.
     *
     * A tap is a chunky fixed block, except where the next tile in the same
     * lane arrives sooner - two tiles that overlap on screen cannot be aimed
     * at separately, so the earlier one is shortened rather than drawn over.
     */
    val drawLenMs: Long,
    /** Bar number from the first downbeat, printed on accent tiles. */
    val bar: Int
) {
    val isHold: Boolean get() = kind == TileKind.HOLD
    val holdLengthMs: Long get() = (endMs - startMs).coerceAtLeast(0L)
}

/**
 * How busy the chart is.
 *
 * The target is notes per second rather than a subdivision, because a
 * subdivision means something different at 80 BPM than at 170 and a chart that
 * is comfortable for one tempo is unplayable or empty at the other.
 */
enum class TileDifficulty(
    val key: String,
    val label: String,
    val targetNotesPerSecond: Float
) {
    CHILL("chill", "Chill", 1.5f),
    STANDARD("standard", "Standard", 2.4f),
    RUSH("rush", "Rush", 3.4f);

    companion object {
        fun from(key: String?): TileDifficulty =
            entries.firstOrNull { it.key == key } ?: STANDARD
    }
}

/**
 * A generated beat map for one track.
 *
 * [bpm] and [keyLabel] are carried for the masthead: the analysis is the reason
 * this player can exist, so the numbers behind the chart are printed rather
 * than hidden.
 */
data class TileChart(
    val songId: String,
    val tiles: List<Tile>,
    val bpm: Float?,
    val keyLabel: String?,
    val difficulty: TileDifficulty,
    /** True when no confident tempo was measured and the grid is assumed. */
    val isFreestyle: Boolean,
    val beatMs: Double,
    val firstDownbeatMs: Long
) {
    val isEmpty: Boolean get() = tiles.isEmpty()

    /** First tile at or after [positionMs]; [tiles].size when the chart is spent. */
    fun indexAtOrAfter(positionMs: Long): Int {
        var low = 0
        var high = tiles.size
        while (low < high) {
            val mid = (low + high) / 2
            if (tiles[mid].startMs < positionMs) low = mid + 1 else high = mid
        }
        return low
    }
}

/**
 * Turns an [AudioProfile] into a playable chart.
 *
 * **The app already measures every track it plays** - tempo, the first beat and
 * downbeat, the key, where the audio actually starts and how it ends - for
 * AutoMix. That measurement is the whole basis of this: nothing here decodes
 * audio, makes a request or costs anything at play time beyond a few hundred
 * objects, and a track that has never been profiled simply has no chart rather
 * than a wrong one.
 *
 * Everything is derived from the song id, so a chart is identical on every
 * replay. A rhythm game whose notes move between attempts cannot be practised,
 * and a best score against a chart that changed means nothing.
 */
object TileChartFactory {

    /** Below this the head tempo estimate is a guess, not a grid. */
    private const val MIN_TEMPO_CONFIDENCE = 0.18f

    /** The outro anchor only corrects drift when it agrees with the head. */
    private const val MIN_OUTRO_CONFIDENCE = 0.3f
    private const val MAX_DRIFT_CORRECTION = 0.06

    /** Eight slots to the bar: the chart is written in eighth notes. */
    private const val SLOTS_PER_BAR = 8
    private const val BEATS_PER_BAR = 4

    /** Assumed tempo for a freestyle chart, which the user opts into knowingly. */
    private const val FREESTYLE_BPM = 120f

    /** How long a tap tile is on screen, before crowding shortens it. */
    private const val TAP_DRAW_MS = 340L

    /** A gap of at least this many beats can become a held note. */
    private const val HOLD_MIN_GAP_BEATS = 2.0
    private const val HOLD_MAX_BEATS = 4.0
    private const val HOLD_CHANCE = 0.55f

    /** Guard against a runaway grid on a bad duration or a silly tempo. */
    private const val MAX_TILES = 4000

    /**
     * Rhythms available to a bar, indexed by how many notes they contain.
     *
     * Written out rather than generated so every bar is a rhythm somebody would
     * actually play: the accents fall where a listener expects them, and the
     * rests are where a phrase breathes. Picking the group by density and the
     * member by seed is what keeps a four-minute chart from being a metronome.
     */
    private val PATTERNS: Map<Int, List<Int>> = mapOf(
        1 to listOf("x-------"),
        2 to listOf("x---x---", "x-----x-", "x--x----"),
        3 to listOf("x---x-x-", "x-x---x-", "x--x--x-", "x---xx--"),
        4 to listOf("x-x-x-x-", "x-x-x--x", "x--xx-x-", "x-xx--x-"),
        5 to listOf("x-x-x-xx", "x-xxx-x-", "xx-x-x-x", "x-x-xxx-"),
        6 to listOf("xx-xx-xx", "x-xxx-xx", "xxx-xx-x", "xx-xxx-x"),
        7 to listOf("xxxx-xxx", "xx-xxxxx", "xxxxx-xx"),
        8 to listOf("xxxxxxxx")
    ).mapValues { (_, patterns) -> patterns.map(::maskOf) }

    private fun maskOf(pattern: String): Int {
        var mask = 0
        pattern.forEachIndexed { slot, char -> if (char == 'x') mask = mask or (1 shl slot) }
        return mask
    }

    private val PITCH_NAMES = listOf(
        "C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B"
    )

    /**
     * @param freestyle when true, a track with no measured tempo still gets a
     *   chart on an assumed 4/4 grid. Off by default: tiles that do not match
     *   the music are worse than no tiles, so this is only ever the user's
     *   explicit choice.
     * @return null when there is nothing honest to generate.
     */
    fun build(
        songId: String,
        durationMs: Long,
        profile: AudioProfile?,
        difficulty: TileDifficulty,
        freestyle: Boolean = false
    ): TileChart? {
        if (durationMs <= 0L) return null

        val measuredBpm = profile?.bpm?.takeIf {
            it > 0f && profile.tempoConfidence >= MIN_TEMPO_CONFIDENCE
        }
        if (measuredBpm == null && !freestyle) return null

        val isFreestyle = measuredBpm == null
        val bpm = measuredBpm ?: FREESTYLE_BPM
        var beatMs = 60_000.0 / bpm
        // The head window measures the intro. Anchoring the far end of the grid
        // on the independently measured outro downbeat turns two point
        // estimates into a tempo fit, which is what keeps the last minute of a
        // long track in time instead of a few hundred milliseconds adrift.
        val firstDownbeat = if (isFreestyle) 0L else (profile?.downbeatOffsetMs ?: 0L)
        if (!isFreestyle && profile != null) {
            beatMs = correctedBeat(profile, beatMs, firstDownbeat, durationMs)
        }
        val barMs = beatMs * BEATS_PER_BAR
        if (barMs <= 0.0) return null

        val firstSound = (profile?.leadInSilenceMs ?: 0L).coerceAtLeast(0L)
        val startMs = (firstDownbeat.toDouble() + barMs * kotlin.math.ceil(
            ((firstSound + 200L) - firstDownbeat).coerceAtLeast(0L) / barMs
        )).roundToLong()
        val endMs = chartEnd(profile, durationMs)
        if (endMs - startMs < barMs) return null

        val notesPerBar = (difficulty.targetNotesPerSecond * (barMs / 1000.0))
            .roundToInt()
            .coerceIn(1, SLOTS_PER_BAR)
        val slotMs = barMs / SLOTS_PER_BAR
        val seed = songId.hashCode().toLong() * 31 + difficulty.ordinal

        val raw = ArrayList<RawNote>(256)
        var barIndex = 0
        var barStart = startMs.toDouble()
        while (barStart < endMs && raw.size < MAX_TILES) {
            val random = Random(seed * 8191 + barIndex)
            // A bar of rest every so often. A chart with no gaps reads as noise
            // and gives the player nowhere to recover a dropped combo.
            val rests = !isFreestyle && barIndex > 0 && barIndex % 8 == 7
            if (!rests) {
                val group = PATTERNS[notesPerBar] ?: PATTERNS.getValue(4)
                val mask = group[random.nextInt(group.size)]
                for (slot in 0 until SLOTS_PER_BAR) {
                    if (mask and (1 shl slot) == 0) continue
                    val at = (barStart + slot * slotMs).roundToLong()
                    if (at < startMs || at > endMs) continue
                    raw.add(
                        RawNote(
                            startMs = at,
                            isDownbeat = slot == 0,
                            bar = barIndex + 1
                        )
                    )
                }
            }
            barIndex++
            barStart = startMs + barIndex * barMs
        }
        if (raw.isEmpty()) return null

        val tiles = assign(raw, beatMs, endMs, seed)
        if (tiles.isEmpty()) return null

        return TileChart(
            songId = songId,
            tiles = tiles,
            bpm = if (isFreestyle) null else bpm,
            keyLabel = keyLabel(profile),
            difficulty = difficulty,
            isFreestyle = isFreestyle,
            beatMs = beatMs,
            firstDownbeatMs = firstDownbeat
        )
    }

    /**
     * Fit the beat length to the outro downbeat when the tail measurement is
     * both confident and consistent with the head.
     *
     * A tail estimate that disagrees by more than a few percent is a half- or
     * double-time reading rather than drift, and following it would put the
     * whole back half of the chart on the wrong grid.
     */
    private fun correctedBeat(
        profile: AudioProfile,
        beatMs: Double,
        firstDownbeatMs: Long,
        durationMs: Long
    ): Double {
        if (profile.outroTempoConfidence < MIN_OUTRO_CONFIDENCE) return beatMs
        if (profile.outroDownbeatLeadMs <= 0L) return beatMs
        val target = durationMs - profile.outroDownbeatLeadMs
        val span = target - firstDownbeatMs
        val barMs = beatMs * BEATS_PER_BAR
        if (span < barMs * 8) return beatMs
        val bars = (span / barMs).roundToInt()
        if (bars < 8) return beatMs
        val corrected = span.toDouble() / bars / BEATS_PER_BAR
        if (abs(corrected - beatMs) / beatMs > MAX_DRIFT_CORRECTION) return beatMs
        return corrected
    }

    /**
     * Where the chart stops.
     *
     * A track that fades out should not be asking for taps through the fade,
     * and one that is cut off mid-phrase has no outro to protect.
     */
    private fun chartEnd(profile: AudioProfile?, durationMs: Long): Long {
        if (profile == null) return durationMs - 800L
        if (profile.endsAbruptly) return durationMs - 300L
        val quiet = maxOf(profile.tailFadeMs, profile.outroLeadMs, 800L)
        return durationMs - quiet
    }

    /**
     * Lanes, holds and draw lengths in one pass.
     *
     * Lane choice is seeded rather than random so the chart is stable, and
     * constrained rather than free: a lane still under a held tile cannot be
     * asked for, and the same lane is not demanded three times in a row at
     * speed, because both are unplayable rather than merely hard.
     */
    private fun assign(
        raw: List<RawNote>,
        beatMs: Double,
        endMs: Long,
        seed: Long
    ): List<Tile> {
        val holdEnds = LongArray(TILE_LANES) { Long.MIN_VALUE }
        val lanes = IntArray(raw.size) { -1 }
        val holdUntil = LongArray(raw.size) { 0L }
        var previousLane = -1
        var repeats = 0

        raw.forEachIndexed { index, note ->
            val random = Random(seed * 131 + index)
            val next = raw.getOrNull(index + 1)
            val gap = ((next?.startMs ?: endMs) - note.startMs).coerceAtLeast(0L)

            val makeHold = !note.isDownbeat &&
                gap >= (beatMs * HOLD_MIN_GAP_BEATS) &&
                random.nextFloat() < HOLD_CHANCE
            val holdEnd = if (makeHold) {
                val length = (gap - beatMs * 0.5).coerceAtMost(beatMs * HOLD_MAX_BEATS)
                note.startMs + length.roundToLong().coerceAtLeast(1L)
            } else 0L

            val free = (0 until TILE_LANES).filter { holdEnds[it] < note.startMs }
                .ifEmpty { (0 until TILE_LANES).toList() }
            val tight = gap <= beatMs * 0.75
            val avoid = when {
                previousLane < 0 -> -1
                repeats >= 2 -> previousLane
                tight -> previousLane
                else -> -1
            }
            val choices = free.filterNot { it == avoid }.ifEmpty { free }
            val lane = choices[random.nextInt(choices.size)]

            repeats = if (lane == previousLane) repeats + 1 else 0
            previousLane = lane
            lanes[index] = lane
            holdUntil[index] = holdEnd
            if (holdEnd > 0L) holdEnds[lane] = holdEnd
        }

        // A tap is drawn as a chunky block, shortened when the same lane is
        // asked for again before the block would have cleared: two overlapping
        // blocks in one lane are one long smear to aim at.
        val nextInLane = LongArray(raw.size) { Long.MAX_VALUE }
        val seenAfter = LongArray(TILE_LANES) { Long.MAX_VALUE }
        for (index in raw.indices.reversed()) {
            nextInLane[index] = seenAfter[lanes[index]]
            seenAfter[lanes[index]] = raw[index].startMs
        }

        return raw.mapIndexed { index, note ->
            val holdEnd = holdUntil[index]
            val room = nextInLane[index].let {
                if (it == Long.MAX_VALUE) TAP_DRAW_MS else ((it - note.startMs) * 0.8).toLong()
            }
            Tile(
                lane = lanes[index],
                startMs = note.startMs,
                endMs = if (holdEnd > 0L) holdEnd else note.startMs,
                kind = when {
                    holdEnd > 0L -> TileKind.HOLD
                    note.isDownbeat -> TileKind.ACCENT
                    else -> TileKind.TAP
                },
                drawLenMs = if (holdEnd > 0L) {
                    holdEnd - note.startMs
                } else {
                    room.coerceIn(90L, TAP_DRAW_MS)
                },
                bar = note.bar
            )
        }
    }

    /** "F# MINOR", or null when the key estimate was not worth printing. */
    private fun keyLabel(profile: AudioProfile?): String? {
        val pitch = profile?.keyPitchClass ?: return null
        val mode = profile.keyMode ?: return null
        if (profile.keyConfidence <= 0f) return null
        val name = PITCH_NAMES.getOrNull(((pitch % 12) + 12) % 12) ?: return null
        return "$name ${mode.uppercase()}"
    }

    private data class RawNote(val startMs: Long, val isDownbeat: Boolean, val bar: Int)
}
