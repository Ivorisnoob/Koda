package com.ivor.ivormusic.service

import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Opt-in video frame interpolation ("Smooth motion"): a GPU effect in Media3's
 * video pipeline that draws one motion-compensated frame between each pair of
 * decoded frames, so a 24-30 fps video reaches the screen at 48-60 fps.
 *
 * **The pipeline is fixed per player.** [verified September 2026 against the
 * Media3 1.11.0 bytecode] `MediaCodecVideoRenderer.onEnabled` decides once, on
 * the renderer's first enable, whether it renders through the effect graph
 * (`hasSetVideoSink`), so `setVideoEffects` has to be called before the first
 * prepare and cannot add the graph to a player that started without it. That
 * is why the ViewModel installs the effect when it builds the player and
 * rebuilds the player on close when the setting has changed since, and why
 * every per-video decision is a flag the effect reads per frame
 * ([FrameInterpolationControl]) rather than a change to the effect list.
 *
 * **Extra output frames are legal in playback.** [verified September 2026,
 * same bytecode] `PlaybackVideoGraphWrapper.onOutputFrameAvailableForRendering`
 * hands each output timestamp to the sink on its own, and `FrameConsumptionManager`
 * queues a producer's outputs while the consumer has no capacity, so nothing
 * requires one output per input.
 */
@UnstableApi
class FrameInterpolationEffect(
    private val control: FrameInterpolationControl
) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        FrameInterpolationShaderProgram(control, useHdr)
}

/**
 * What the main thread and the GL thread tell each other, one writer per
 * field. A stale read costs one frame or one poll either way.
 */
class FrameInterpolationControl {
    /** Main thread writes: interpolate now, or pass frames through. */
    @Volatile
    var active: Boolean = false

    /** GL thread writes: the source's measured frame rate, 0 until known. */
    @Volatile
    var sourceFps: Float = 0f

    /** GL thread writes: midpoint frames drawn so far, so the main thread can see it working. */
    @Volatile
    var midpoints: Long = 0L

    /** GL thread writes: the interpolation shaders failed on this GPU; frames only pass through. */
    @Volatile
    var unsupported: Boolean = false
}

/** What Smooth motion is doing for the video on screen, for the player to show. */
sealed interface FrameInterpolationStatus {
    /** The setting is off; nothing to show. */
    data object Disabled : FrameInterpolationStatus
    /** On, but this player was built before it was turned on. */
    data object NeedsRestart : FrameInterpolationStatus
    /** No frames measured yet for this video. */
    data object Measuring : FrameInterpolationStatus
    data class Active(val sourceFps: Int, val outputFps: Int) : FrameInterpolationStatus
    /** The source is already smooth (above 40 fps), so frames pass through. */
    data class NotNeeded(val sourceFps: Int) : FrameInterpolationStatus
    data object Live : FrameInterpolationStatus
    data object Hdr : FrameInterpolationStatus
    data object SpedUp : FrameInterpolationStatus
    data object Hot : FrameInterpolationStatus
    data object BatterySaver : FrameInterpolationStatus
    /** Dropped too many frames while interpolating; off until the video or quality changes. */
    data object CannotKeepUp : FrameInterpolationStatus
    /** The interpolation shaders failed on this GPU. */
    data object Unsupported : FrameInterpolationStatus
}

/** Pure decisions, kept apart from GL and Android so they can be unit tested. */
object FrameInterpolationPolicy {

    /**
     * Below this gap (above 40 fps) the source is already smooth: a 50 or 60
     * fps video doubled would ask for 100-120 fps, which most screens cannot
     * show and every GPU would pay for.
     */
    const val MIN_INTERVAL_US = 25_000L

    /**
     * Above this gap (below ~16.7 fps) the frames are too far apart to have
     * motion worth tracking, and a gap this large inside a normal video is
     * usually a discontinuity rather than a frame interval.
     */
    const val MAX_INTERVAL_US = 60_000L

    /** The frame rate [MIN_INTERVAL_US] corresponds to. */
    const val MAX_SOURCE_FPS = 1_000_000f / MIN_INTERVAL_US

    /** Faster than this and the source already reaches the screen at 36+ fps. */
    const val MAX_SPEED = 1.2f

    /** The long side of the finest motion level, in texels. */
    const val FINE_LEVEL_LONG_SIDE = 320

    fun shouldInterpolate(intervalUs: Long): Boolean =
        intervalUs in MIN_INTERVAL_US..MAX_INTERVAL_US

    fun midpointUs(previousUs: Long, currentUs: Long): Long =
        previousUs + (currentUs - previousUs) / 2

    /**
     * Whether this moment of playback should be interpolated at all.
     * [thermalStatus] is a `PowerManager.THERMAL_STATUS_*` value; moderate and
     * above means the system is already throttling.
     */
    fun isGateOpen(
        enabledByUser: Boolean,
        isLive: Boolean,
        isHdr: Boolean,
        speed: Float,
        thermalStatus: Int,
        powerSave: Boolean,
        cannotKeepUp: Boolean
    ): Boolean = enabledByUser &&
        !isLive &&
        !isHdr &&
        speed <= MAX_SPEED &&
        thermalStatus < PowerManager.THERMAL_STATUS_MODERATE &&
        !powerSave &&
        !cannotKeepUp

    /**
     * Sizes of the three motion-search levels for a [width] x [height] frame,
     * finest first, each half the one before. Scaled by the long side so a
     * portrait video costs what the same video in landscape does.
     */
    fun levelSizes(width: Int, height: Int): List<Pair<Int, Int>> {
        val longSide = max(width, height).coerceAtLeast(1)
        val fineLong = minOf(FINE_LEVEL_LONG_SIDE, max(16, longSide / 2))
        val scale = fineLong.toFloat() / longSide
        var w = max(4, (width * scale).roundToInt())
        var h = max(4, (height * scale).roundToInt())
        val sizes = ArrayList<Pair<Int, Int>>(3)
        repeat(3) {
            sizes += w to h
            w = max(4, (w + 1) / 2)
            h = max(4, (h + 1) / 2)
        }
        return sizes
    }
}

/**
 * Decides whether the phone is keeping up, from the video renderer's own
 * counters sampled on each poll.
 *
 * **Why counters and a window rather than the renderer's drop reports.** The
 * first version acted on `onDroppedVideoFrames`, which arrives in batches -
 * at fifty drops, or whenever the renderer stops - covering whatever happened
 * since the last one. A quality change stops the renderer, so twelve seconds
 * of 1080p60 (which Smooth motion leaves alone) arrived as one batch the
 * moment the viewer picked a 30 fps quality, and switched the feature off for
 * exactly the part it was about to work on. Here every poll is attributed
 * separately, and only polls that actually drew midpoints count.
 *
 * **What it tolerates.** A grace period after anything that restarts the
 * pipeline (the first frames of a video, a quality change, a seek, a resume
 * after pause or buffering), where late frames are the decoder warming up;
 * and up to [maxDropRatio] of frames dropped over a sustained window. With
 * effects on, frames handed to the GPU count as rendered and frames too late
 * to hand over count as dropped [verified September 2026 against the Media3
 * 1.11.0 bytecode: `MediaCodecVideoRenderer`'s sink frame handler renders or
 * drops each decoder buffer], so the ratio is a fair "cannot keep up" signal.
 */
class FrameDropWatch(
    private val graceMs: Long = 3_000L,
    private val windowPolls: Int = 16,
    private val minimumPolls: Int = 12,
    private val maxDropRatio: Float = 0.05f,
    /** Position movement off the expected pace beyond this is a seek. */
    private val seekToleranceMs: Long = 1_500L,
) {
    private var lastDropped = -1
    private var lastRendered = -1
    private var lastMidpoints = -1L
    private var lastPositionMs = 0L
    private var lastPollAtMs = 0L
    private var wasPlaying = false
    private var graceUntilMs = 0L
    private val window = ArrayDeque<Pair<Int, Int>>()

    /** A new video or quality: start over, grace included. */
    fun restart(nowMs: Long) {
        lastDropped = -1
        lastRendered = -1
        lastMidpoints = -1L
        wasPlaying = false
        window.clear()
        graceUntilMs = nowMs + graceMs
    }

    /** @return true when sustained interpolation dropped more than the budget. */
    fun onPoll(
        nowMs: Long,
        isPlaying: Boolean,
        positionMs: Long,
        speed: Float,
        droppedFrames: Int,
        renderedFrames: Int,
        midpoints: Long,
    ): Boolean {
        val first = lastDropped < 0
        val countersReset = !first && (droppedFrames < lastDropped || renderedFrames < lastRendered)
        val expectedAdvance = ((nowMs - lastPollAtMs) * speed).toLong()
        val seeked = !first && wasPlaying && isPlaying &&
            abs((positionMs - lastPositionMs) - expectedAdvance) > seekToleranceMs
        val resumed = !first && isPlaying && !wasPlaying
        if (first || countersReset || seeked || resumed) {
            graceUntilMs = max(graceUntilMs, nowMs + graceMs)
            window.clear()
        }
        val dropped = if (first || countersReset) 0 else droppedFrames - lastDropped
        val rendered = if (first || countersReset) 0 else renderedFrames - lastRendered
        val interpolated = !first && midpoints > lastMidpoints

        lastDropped = droppedFrames
        lastRendered = renderedFrames
        lastMidpoints = midpoints
        lastPositionMs = positionMs
        lastPollAtMs = nowMs
        wasPlaying = isPlaying

        // Only sustained, uninterrupted interpolation is judged.
        if (!isPlaying || !interpolated) {
            window.clear()
            return false
        }
        if (nowMs < graceUntilMs) return false
        window.addLast(dropped to rendered)
        while (window.size > windowPolls) window.removeFirst()
        if (window.size < minimumPolls) return false
        val droppedTotal = window.sumOf { it.first }
        val total = droppedTotal + window.sumOf { it.second }
        return total > 0 && droppedTotal.toFloat() / total > maxDropRatio
    }
}

/**
 * Decides, from the main thread, whether the effect should be interpolating
 * right now, and says what it is doing: the user's switch, the current video,
 * the playback speed, the phone's thermal state and battery saver, and
 * whether this video at this quality has already shown the phone cannot keep
 * up.
 */
class FrameInterpolationGovernor(context: Context) {

    val control = FrameInterpolationControl()

    private val powerManager = context.getSystemService(PowerManager::class.java)
    private val dropWatch = FrameDropWatch()
    private var cannotKeepUp = false
    private var playbackKey: String? = null

    private val _status = MutableStateFlow<FrameInterpolationStatus>(FrameInterpolationStatus.Disabled)
    val status: StateFlow<FrameInterpolationStatus> = _status.asStateFlow()

    /** Called from the player's progress poll, twice a second. */
    fun update(
        enabledByUser: Boolean,
        pipelineInstalled: Boolean,
        currentVideoId: String?,
        qualityKey: String?,
        isLive: Boolean,
        isHdr: Boolean,
        speed: Float,
        isPlaying: Boolean,
        positionMs: Long,
        droppedFrames: Int?,
        renderedFrames: Int?,
    ) {
        if (!enabledByUser || !pipelineInstalled) {
            setActive(false)
            _status.value = if (!enabledByUser) {
                FrameInterpolationStatus.Disabled
            } else {
                FrameInterpolationStatus.NeedsRestart
            }
            return
        }

        val now = SystemClock.elapsedRealtime()
        // A new video, or a new quality of the same one, gets a fresh chance:
        // the stream that overloaded the GPU may have been 1080p, the next 480p.
        val key = "$currentVideoId|$qualityKey"
        if (key != playbackKey) {
            playbackKey = key
            cannotKeepUp = false
            dropWatch.restart(now)
        }

        val thermal = powerManager?.currentThermalStatus ?: PowerManager.THERMAL_STATUS_NONE
        val powerSave = powerManager?.isPowerSaveMode == true
        val open = FrameInterpolationPolicy.isGateOpen(
            enabledByUser = true,
            isLive = isLive,
            isHdr = isHdr,
            speed = speed,
            thermalStatus = thermal,
            powerSave = powerSave,
            cannotKeepUp = cannotKeepUp,
        )
        setActive(open)

        if (open && droppedFrames != null && renderedFrames != null &&
            dropWatch.onPoll(
                nowMs = now,
                isPlaying = isPlaying,
                positionMs = positionMs,
                speed = speed,
                droppedFrames = droppedFrames,
                renderedFrames = renderedFrames,
                midpoints = control.midpoints,
            )
        ) {
            KLog.w(TAG, "Dropping frames while interpolating; off for this video and quality")
            cannotKeepUp = true
            setActive(false)
        }

        val sourceFps = control.sourceFps
        _status.value = when {
            control.unsupported -> FrameInterpolationStatus.Unsupported
            cannotKeepUp -> FrameInterpolationStatus.CannotKeepUp
            isLive -> FrameInterpolationStatus.Live
            isHdr -> FrameInterpolationStatus.Hdr
            speed > FrameInterpolationPolicy.MAX_SPEED -> FrameInterpolationStatus.SpedUp
            thermal >= PowerManager.THERMAL_STATUS_MODERATE -> FrameInterpolationStatus.Hot
            powerSave -> FrameInterpolationStatus.BatterySaver
            sourceFps <= 0f -> FrameInterpolationStatus.Measuring
            sourceFps > FrameInterpolationPolicy.MAX_SOURCE_FPS ->
                FrameInterpolationStatus.NotNeeded(sourceFps.roundToInt())
            else -> {
                val fps = sourceFps.roundToInt()
                FrameInterpolationStatus.Active(fps, fps * 2)
            }
        }
    }

    /** The player closed: whatever opens next, even the same video, starts fresh. */
    fun reset() {
        playbackKey = null
        cannotKeepUp = false
        setActive(false)
    }

    private fun setActive(active: Boolean) {
        if (active != control.active) {
            KLog.i(TAG, if (active) "Interpolating" else "Passing frames through")
            control.active = active
        }
    }

    private companion object {
        const val TAG = "FrameInterpolation"
    }
}
