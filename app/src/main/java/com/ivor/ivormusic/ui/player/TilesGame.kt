package com.ivor.ivormusic.ui.player

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ivor.ivormusic.data.TILE_LANES
import com.ivor.ivormusic.data.TileChart
import com.ivor.ivormusic.data.TileKind
import com.ivor.ivormusic.data.TileRunResult
import kotlin.math.abs
import kotlin.math.min

/** How close a tap has to be, and what it is worth. */
enum class TileJudgment(val label: String, val windowMs: Long, val points: Int) {
    PERFECT("Perfect", 55L, 100),
    GREAT("Great", 100L, 60),
    GOOD("Good", 155L, 25),
    MISS("Miss", Long.MAX_VALUE, 0)
}

/** Per-tile progress through a run. */
internal object TileStatus {
    const val PENDING: Byte = 0
    const val HELD: Byte = 1
    const val CLEARED: Byte = 2
    const val MISSED: Byte = 3

    /** Passed over by a seek: not played, so never counted for or against. */
    const val SKIPPED: Byte = 4
}

/**
 * A run of one chart: what has been hit, what it scored, and what the board
 * should draw.
 *
 * **Playback is never a consequence.** Missing costs score and breaks the
 * streak, and that is all it does - this is a player style before it is a
 * game, and a music app that stops your music because your thumb was late is a
 * worse music app. Every rule below follows from that: nothing here calls the
 * player, and the clock is playback's, never the game's.
 */
@Stable
class TilesGameState(val chart: TileChart) {

    private val status = ByteArray(chart.tiles.size)

    /** Tile index each lane is currently holding, or -1. */
    private val heldInLane = IntArray(TILE_LANES) { -1 }

    /** Frame time of the last hit in each lane, for the board's flash. */
    private val laneFlashAtMs = LongArray(TILE_LANES) { Long.MIN_VALUE }

    /**
     * First tile whose window has not yet closed. Judging scans forward from
     * here rather than over the whole chart, so a five-minute track costs the
     * same per frame as a thirty-second one.
     */
    private var scanStart = 0

    private var lastSongTimeMs = Long.MIN_VALUE

    /**
     * Whether this run has already been written to the score store.
     *
     * A run ends either by the track changing or by the chart simply running
     * out, and both can happen to the same run - the flag is what keeps the
     * second from re-reporting a result the first already banked.
     */
    internal var submitted: Boolean = false

    var score by mutableIntStateOf(0)
        private set
    var combo by mutableIntStateOf(0)
        private set
    var maxCombo by mutableIntStateOf(0)
        private set
    var perfect by mutableIntStateOf(0)
        private set
    var great by mutableIntStateOf(0)
        private set
    var good by mutableIntStateOf(0)
        private set
    var missed by mutableIntStateOf(0)
        private set

    /** The last judgment, and a serial so a repeat of the same word re-animates. */
    var lastJudgment by mutableStateOf<TileJudgment?>(null)
        private set
    var judgmentSerial by mutableIntStateOf(0)
        private set

    val judged: Int get() = perfect + great + good + missed

    /** x1 to x4. Rewards a streak without letting one run away with the score. */
    val multiplier: Int get() = 1 + min(combo / 12, 3)

    internal fun statusOf(index: Int): Byte = status.getOrElse(index) { TileStatus.SKIPPED }

    internal fun laneFlashAt(lane: Int): Long = laneFlashAtMs[lane]

    /**
     * Resolve everything the clock has carried past.
     *
     * A jump - a seek, a track loop, the queue moving on - resyncs instead of
     * scoring: tiles nobody was shown must not be counted as misses, and the
     * streak does not survive skipping part of the song either.
     */
    fun advance(songTimeMs: Long, scoringEnabled: Boolean) {
        val previous = lastSongTimeMs
        lastSongTimeMs = songTimeMs
        if (previous == Long.MIN_VALUE) {
            resync(songTimeMs)
            return
        }
        val delta = songTimeMs - previous
        if (delta < -JUMP_TOLERANCE_MS || delta > JUMP_TOLERANCE_MS) {
            resync(songTimeMs)
            return
        }
        if (!scoringEnabled) return

        // Holds clear themselves once their tail is past the line: staying down
        // longer than asked is not a mistake, and forcing an exact release
        // would punish the player for the animation's own timing.
        for (lane in 0 until TILE_LANES) {
            val index = heldInLane[lane]
            if (index >= 0 && songTimeMs >= chart.tiles[index].endMs) {
                heldInLane[lane] = -1
                status[index] = TileStatus.CLEARED
                award(TileJudgment.PERFECT, chart.tiles[index].holdLengthMs)
                laneFlashAtMs[lane] = songTimeMs
            }
        }

        while (scanStart < chart.tiles.size) {
            val tile = chart.tiles[scanStart]
            if (tile.startMs + TileJudgment.GOOD.windowMs >= songTimeMs) break
            if (status[scanStart] == TileStatus.PENDING) {
                status[scanStart] = TileStatus.MISSED
                registerMiss()
            }
            scanStart++
        }
    }

    /**
     * Move the cursor to [songTimeMs] without judging anything in between.
     *
     * The streak still breaks. Skipping the hard part is not a way to keep a
     * combo, and a run assembled out of the easy halves of a song is not the
     * same run as one played through.
     *
     * Seeking *backwards* moves the cursor back but leaves tiles already
     * resolved as they are, so a chorus cannot be replayed for a second helping
     * of score - the same reason the streak breaks. A missed tile stays a ghost
     * on the way past either way.
     */
    fun resync(songTimeMs: Long) {
        for (lane in 0 until TILE_LANES) heldInLane[lane] = -1
        val target = chart.indexAtOrAfter(songTimeMs - TileJudgment.GOOD.windowMs)
        for (index in scanStart until target) {
            if (status[index] == TileStatus.PENDING) status[index] = TileStatus.SKIPPED
        }
        scanStart = target
        if (combo != 0) combo = 0
        lastSongTimeMs = songTimeMs
    }

    /**
     * Judge a press in [lane].
     *
     * The nearest unresolved tile in the window wins, which is what makes a
     * fast pair in one lane playable: the first press takes the first tile even
     * when the second is already on screen.
     *
     * A press with nothing in range is ignored rather than penalised. Mashing
     * cannot score - there is no tile to score against - and a player style
     * that punishes an exploratory tap teaches people not to touch it.
     */
    fun press(lane: Int, songTimeMs: Long): TileJudgment? {
        if (heldInLane[lane] >= 0) return null
        var bestIndex = -1
        var bestDelta = Long.MAX_VALUE
        var index = scanStart
        while (index < chart.tiles.size) {
            val tile = chart.tiles[index]
            if (tile.startMs - songTimeMs > TileJudgment.GOOD.windowMs) break
            if (tile.lane == lane && status[index] == TileStatus.PENDING) {
                val delta = abs(tile.startMs - songTimeMs)
                if (delta <= TileJudgment.GOOD.windowMs && delta < bestDelta) {
                    bestDelta = delta
                    bestIndex = index
                }
            }
            index++
        }
        if (bestIndex < 0) return null

        val tile = chart.tiles[bestIndex]
        val judgment = when {
            bestDelta <= TileJudgment.PERFECT.windowMs -> TileJudgment.PERFECT
            bestDelta <= TileJudgment.GREAT.windowMs -> TileJudgment.GREAT
            else -> TileJudgment.GOOD
        }
        laneFlashAtMs[lane] = songTimeMs
        if (tile.kind == TileKind.HOLD) {
            status[bestIndex] = TileStatus.HELD
            heldInLane[lane] = bestIndex
        } else {
            status[bestIndex] = TileStatus.CLEARED
        }
        award(judgment, accent = tile.kind == TileKind.ACCENT)
        return judgment
    }

    /**
     * Lift in [lane].
     *
     * Letting go early is a broken hold: the head was hit, so it does not count
     * as a miss against accuracy, but the streak goes, because the tile was not
     * played.
     */
    fun release(lane: Int, songTimeMs: Long): Boolean {
        val index = heldInLane[lane]
        if (index < 0) return false
        heldInLane[lane] = -1
        val tile = chart.tiles[index]
        return if (songTimeMs >= tile.endMs - HOLD_RELEASE_GRACE_MS) {
            status[index] = TileStatus.CLEARED
            award(TileJudgment.PERFECT, tile.holdLengthMs)
            laneFlashAtMs[lane] = songTimeMs
            true
        } else {
            status[index] = TileStatus.CLEARED
            good++
            combo = 0
            lastJudgment = TileJudgment.GOOD
            judgmentSerial++
            false
        }
    }

    /**
     * Let go of every hold without judging it, for a pause or for the player
     * being put away mid-tile.
     *
     * Deliberately not [release]: the clock stopped, so the tail is never going
     * to reach the line, and charging someone their streak for pausing their
     * own music would be the game overruling the player it lives inside.
     */
    fun abandonHolds() {
        for (lane in 0 until TILE_LANES) {
            val index = heldInLane[lane]
            if (index < 0) continue
            heldInLane[lane] = -1
            status[index] = TileStatus.SKIPPED
        }
    }

    private fun award(judgment: TileJudgment, holdMs: Long = 0L, accent: Boolean = false) {
        val base = judgment.points * (if (accent) 2 else 1)
        val holdBonus = if (holdMs > 0L) (holdMs / 20L).toInt().coerceAtMost(120) else 0
        score += (base + holdBonus) * multiplier
        combo++
        if (combo > maxCombo) maxCombo = combo
        when (judgment) {
            TileJudgment.PERFECT -> perfect++
            TileJudgment.GREAT -> great++
            TileJudgment.GOOD -> good++
            TileJudgment.MISS -> Unit
        }
        lastJudgment = judgment
        judgmentSerial++
    }

    private fun registerMiss() {
        missed++
        combo = 0
        lastJudgment = TileJudgment.MISS
        judgmentSerial++
    }

    fun result(): TileRunResult = TileRunResult(
        songId = chart.songId,
        difficulty = chart.difficulty.key,
        score = score,
        maxCombo = maxCombo,
        perfect = perfect,
        great = great,
        good = good,
        missed = missed,
        judged = judged,
        updatedAt = System.currentTimeMillis()
    )

    private companion object {
        /**
         * Beyond this, the clock moved for a reason other than time passing.
         * Wide enough to absorb a stall or a slow frame, tight enough that a
         * real seek is never mistaken for one.
         */
        const val JUMP_TOLERANCE_MS = 700L

        /** Letting go this close to the tail still counts as holding it out. */
        const val HOLD_RELEASE_GRACE_MS = 130L
    }
}
