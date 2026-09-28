package com.ivor.ivormusic.service

import android.app.ActivityManager
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.PowerManager
import android.os.SystemClock
import android.view.Display
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Opt-in video frame interpolation ("Smooth motion"): a GPU effect in Media3's
 * video pipeline that puts frames on a steady output clock at the screen's
 * rate (60, 90 or 120 fps) and draws each one that falls between two decoded
 * frames by motion compensation, so a 24-60 fps video reaches the screen at
 * up to 120 fps.
 *
 * **The pipeline is fixed per player.** [verified September 2026 against the
 * Media3 1.11.0 bytecode] `MediaCodecVideoRenderer.onEnabled` decides once, on
 * the renderer's first enable, whether it renders through the effect graph
 * (`hasSetVideoSink`), so `setVideoEffects` has to be called before the first
 * prepare and cannot add the graph to a player that started without it. That
 * is why the ViewModel installs the effect when it builds the player and
 * rebuilds the player on close when the setting has changed since, and why
 * every per-video decision is a field the effect reads per frame
 * ([FrameInterpolationControl]) rather than a change to the effect list.
 *
 * **Any number of output frames per input is legal in playback, and the
 * screen follows the output rate.** [verified September 2026, same bytecode]
 * `PlaybackVideoGraphWrapper.onOutputFrameAvailableForRendering` hands each
 * output timestamp to the sink on its own; `DefaultVideoSink` feeds those
 * output timestamps to its own `FixedFrameRateEstimator`, whose synced rate
 * is what `VideoFrameReleaseHelper` passes to `Surface.setFrameRate` and what
 * it paces vsync alignment by. So a steady 120 fps output asks the display
 * for 120 Hz by itself, and an irregular one (a real frame dropped in between
 * synthesized ones off the clock) would lose that sync - which is why the
 * output is a clock and not "midpoints plus the real frames".
 */
/**
 * Whether this phone's GPU runs Smooth motion: OpenGL ES 3.2 or newer, as the
 * system declares it (`reqGlEsVersion`), so it is known before any player or
 * GL context exists. Below that the setting is not offered and the effect is
 * never installed; there is no lesser engine to fall back to.
 */
object FrameInterpolationSupport {
    /** The GLES version the engine needs, as major * 10 + minor ([MotionGl.glesVersion]). */
    const val MIN_GLES = 32

    private const val GLES_3_2 = 0x30002

    fun isSupported(context: Context): Boolean {
        val activityManager = context.getSystemService(ActivityManager::class.java) ?: return false
        return activityManager.deviceConfigurationInfo.reqGlEsVersion >= GLES_3_2
    }
}

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

    /** Main thread writes: the output rate to aim for, in frames per second of real time. */
    @Volatile
    var targetFps: Int = FrameInterpolationPolicy.MIN_TARGET_FPS

    /** Main thread writes: the playback speed, so the output clock runs in real time. */
    @Volatile
    var speed: Float = 1f

    /** GL thread writes: the source's measured frame rate in media time, 0 until known. */
    @Volatile
    var sourceFps: Float = 0f

    /** GL thread writes: the real-time rate frames leave at while interpolating, 0 while passing through. */
    @Volatile
    var outputFps: Int = 0

    /** GL thread writes: [outputFps] is below [targetFps] because of the frame size. */
    @Volatile
    var limitedByResolution: Boolean = false

    /** GL thread writes: frames drawn between decoded ones so far, so the main thread can see it working. */
    @Volatile
    var synthesized: Long = 0L

    /** GL thread writes: every frame handed downstream, decoded or drawn. */
    @Volatile
    var emitted: Long = 0L

    /** GL thread writes: frame pairs whose motion was estimated. */
    @Volatile
    var pairs: Long = 0L

    /**
     * GL thread writes: the share of the frame, in percent, that found no good
     * match in the last sampled pair (about every two seconds), -1 until
     * sampled. High on a scene cut; persistently high means the video moves
     * in ways the search cannot follow.
     */
    @Volatile
    var unmatchedPercent: Int = -1

    /** GL thread writes: sampled pairs judged a scene cut. */
    @Volatile
    var sampledCuts: Long = 0L

    /** GL thread writes: the motion engine in use ("compute (GLES 3.1)", "fragment (GLES 2)"), empty before one runs. */
    @Volatile
    var engine: String = ""

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
    data class Active(
        val sourceFps: Int,
        val outputFps: Int,
        /** Why [outputFps] is below the screen's rate, if it is. */
        val limit: RateLimit,
        /** The rate the user and the screen asked for. */
        val targetFps: Int,
    ) : FrameInterpolationStatus
    /** The source already plays at about the output rate, so frames pass through. */
    data class NotNeeded(val playingFps: Int) : FrameInterpolationStatus
    data object Live : FrameInterpolationStatus
    data object Hdr : FrameInterpolationStatus
    data object Hot : FrameInterpolationStatus
    data object BatterySaver : FrameInterpolationStatus
    /** Dropped too many frames while interpolating; off until the video or quality changes. */
    data object CannotKeepUp : FrameInterpolationStatus
    /** The interpolation shaders failed on this GPU. */
    data object Unsupported : FrameInterpolationStatus

    enum class RateLimit { NONE, RESOLUTION, SCREEN }
}

/** Pure decisions, kept apart from GL and Android so they can be unit tested. */
object FrameInterpolationPolicy {

    /**
     * Above this gap (below ~16.7 fps) the frames are too far apart to have
     * motion worth tracking, and a gap this large inside a normal video is
     * usually a discontinuity rather than a frame interval.
     */
    const val MAX_INTERVAL_US = 60_000L

    /**
     * A source within this factor of the output rate already looks smooth:
     * 60 fps on a 60 Hz screen passes through, 50 fps on it is lifted to 60.
     */
    const val PASS_THROUGH_MARGIN = 1.15

    /** The stored choices for the output cap. Frozen: they are persisted. */
    const val MIN_TARGET_FPS = 60
    const val MAX_TARGET_FPS = 120

    /**
     * Output pixels a second the full-size passes may cost: a little over
     * 1080p at 120 fps, so a decoder's 1088-line frame still qualifies.
     * Larger frames step down to 90, then 60, which is never withheld.
     */
    const val PIXEL_RATE_BUDGET = 2_200_000L * 120

    /**
     * The most outputs one decoded frame may produce. A slow playback speed
     * that would ask for more halves the output rate instead.
     */
    const val MAX_OUTPUTS_PER_INPUT = 8

    /**
     * The finest output step while the source is unmeasured: eight to the
     * widest gap still interpolated ([MAX_INTERVAL_US]).
     */
    const val MIN_STEP_US = MAX_INTERVAL_US.toDouble() / MAX_OUTPUTS_PER_INPUT

    /** A step within this fraction of a whole division of the source interval snaps to it. */
    const val SNAP_TOLERANCE = 0.015

    /** Output textures may take this much memory before the lead shortens. */
    const val POOL_BUDGET_BYTES = 100L * 1024 * 1024

    /** Frames above this many pixels keep the smaller floor of four outputs. */
    const val LARGE_FRAME_PIXELS = 2_500_000L

    /**
     * The rate to aim for: the user's cap and the display's fastest mode,
     * whichever is lower, and never under 60.
     */
    fun targetFps(userMaxFps: Int, displayMaxHz: Float): Int =
        minOf(userMaxFps, displayMaxHz.roundToInt()).coerceIn(MIN_TARGET_FPS, MAX_TARGET_FPS)

    /**
     * [targetFps] for a [width] x [height] frame, stepping down to 90 and then
     * 60 when every output frame at that size would cost more than
     * [PIXEL_RATE_BUDGET]. 60 is never withheld: that is where the feature
     * started, including 4K.
     */
    fun affordableFps(targetFps: Int, width: Int, height: Int): Int {
        val pixels = width.toLong() * height
        for (fps in intArrayOf(targetFps, 90, MIN_TARGET_FPS)) {
            if (fps <= targetFps && pixels * fps <= PIXEL_RATE_BUDGET) return fps
        }
        return minOf(targetFps, MIN_TARGET_FPS)
    }

    /**
     * The media-time gap between output frames for [fps] of real time at
     * [speed]. When the source interval divides into whole steps within
     * [SNAP_TOLERANCE] (30 into 120, 24 into 120, 30 into 60), the step snaps
     * to it so every Nth output is a decoded frame shown as it is, rather
     * than one drawn next to it.
     */
    fun outputStepUs(fps: Int, speed: Float, sourceIntervalUs: Double): Double {
        val pace = speed.coerceIn(0.1f, 8f).toDouble()
        val minStep = if (sourceIntervalUs > 0.0) {
            sourceIntervalUs / MAX_OUTPUTS_PER_INPUT * (1 - SNAP_TOLERANCE)
        } else {
            MIN_STEP_US
        }
        var rate = fps.coerceAtLeast(1)
        var step = pace * 1_000_000.0 / rate
        while (step < minStep && rate > 30) {
            rate /= 2
            step = pace * 1_000_000.0 / rate
        }
        if (sourceIntervalUs > 0.0) {
            val divisions = (sourceIntervalUs / step).roundToInt()
            if (divisions >= 2) {
                val snapped = sourceIntervalUs / divisions
                if (abs(snapped - step) / step <= SNAP_TOLERANCE) return snapped
            }
        }
        return step
    }

    /** Whether a pair [intervalUs] apart is worth drawing frames between at [stepUs]. */
    fun shouldInterpolate(intervalUs: Long, stepUs: Double): Boolean =
        intervalUs in 1..MAX_INTERVAL_US && intervalUs > stepUs * PASS_THROUGH_MARGIN

    /** Output frames one input can produce at [stepUs]: its drawn frames plus itself. */
    fun outputsPerInput(intervalUs: Double, stepUs: Double): Int =
        if (intervalUs <= 0.0 || stepUs <= 0.0) 1 else ceil(intervalUs / stepUs - 1e-6).toInt().coerceAtLeast(1)

    /**
     * How many output textures to keep for [outputsPerInput] a frame. The
     * final stage holds each output until its display time, so this bounds
     * how far ahead of the screen the pipeline runs: three inputs' worth,
     * within [POOL_BUDGET_BYTES], and never so few that one input cannot fit
     * with room to spare.
     */
    fun poolCapacity(outputsPerInput: Int, width: Int, height: Int, bytesPerPixel: Int): Int {
        val pixels = width.toLong() * height
        val floor = if (pixels > LARGE_FRAME_PIXELS) 4 else 6
        val frameBytes = (pixels * bytesPerPixel).coerceAtLeast(1L)
        val affordable = (POOL_BUDGET_BYTES / frameBytes).toInt()
        return maxOf(floor, outputsPerInput + 2, minOf(outputsPerInput * 3, affordable))
    }

    /**
     * Whether this moment of playback should be interpolated at all.
     * [thermalStatus] is a `PowerManager.THERMAL_STATUS_*` value; moderate and
     * above means the system is already throttling.
     */
    fun isGateOpen(
        enabledByUser: Boolean,
        isLive: Boolean,
        isHdr: Boolean,
        thermalStatus: Int,
        powerSave: Boolean,
        cannotKeepUp: Boolean
    ): Boolean = enabledByUser &&
        !isLive &&
        !isHdr &&
        thermalStatus < PowerManager.THERMAL_STATUS_MODERATE &&
        !powerSave &&
        !cannotKeepUp
}

/**
 * The steady clock output frames leave on while interpolating. Ticks are
 * computed from the origin rather than accumulated, so a fractional step
 * (8333.3 us at 120 fps) never drifts.
 */
class OutputClock {
    private var originUs = 0L
    private var index = 0L

    var stepUs: Double = 0.0
        private set

    var isRunning: Boolean = false
        private set

    /** Starts ticking one step after [atUs], a frame already shown. */
    fun start(atUs: Long, stepUs: Double) {
        originUs = atUs
        this.stepUs = stepUs
        index = 1
        isRunning = true
    }

    fun stop() {
        isRunning = false
    }

    /**
     * Whether [stepUs] differs enough from the running step to restart on it:
     * a new rate or speed does, while a variable-rate source wobbling in and
     * out of [FrameInterpolationPolicy.SNAP_TOLERANCE] does not, since every
     * restart shows one frame off the clock.
     */
    fun needsRestart(stepUs: Double): Boolean =
        !isRunning || abs(stepUs - this.stepUs) > this.stepUs * RESTART_TOLERANCE

    private companion object {
        const val RESTART_TOLERANCE = 0.03
    }

    fun nextTickUs(): Long = originUs + (index * stepUs).roundToLong()

    fun advance() {
        index++
    }
}

/**
 * Decides whether the phone is keeping up, from the video renderer's drop
 * counter and the effect's own output count, sampled on each poll.
 *
 * **Why counters and a window rather than the renderer's drop reports.** The
 * first version acted on `onDroppedVideoFrames`, which arrives in batches -
 * at fifty drops, or whenever the renderer stops - covering whatever happened
 * since the last one. A quality change stops the renderer, so twelve seconds
 * of 1080p60 (which Smooth motion then left alone) arrived as one batch the
 * moment the viewer picked a 30 fps quality, and switched the feature off for
 * exactly the part it was about to work on. Here every poll is attributed
 * separately, and only polls that actually drew frames count.
 *
 * **What a drop is measured against.** With effects on, the renderer's
 * `droppedBufferCount` counts both decoder buffers too late to hand over and
 * output frames the sink dropped late, drawn ones included [verified
 * September 2026 against the Media3 1.11.0 bytecode: the sink listener's
 * `onFrameDropped` calls `updateDroppedBufferCounters(0, 1)`], while
 * `renderedOutputBufferCount` counts only decoder buffers. Measured against
 * the decoder count, 5% of a 120 fps output looked like 20% at 30 fps, so
 * the ratio is against the frames the effect emitted.
 *
 * **What it tolerates.** A grace period after anything that restarts the
 * pipeline (the first frames of a video, a quality change, a seek, a resume
 * after pause or buffering), where late frames are the decoder warming up;
 * and up to [maxDropRatio] of frames dropped over a sustained window.
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
    private var lastEmitted = -1L
    private var lastSynthesized = -1L
    private var lastPositionMs = 0L
    private var lastPollAtMs = 0L
    private var wasPlaying = false
    private var graceUntilMs = 0L
    private val window = ArrayDeque<Pair<Int, Long>>()

    /** A new video or quality: start over, grace included. */
    fun restart(nowMs: Long) {
        lastDropped = -1
        lastEmitted = -1L
        lastSynthesized = -1L
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
        emittedFrames: Long,
        synthesizedFrames: Long,
    ): Boolean {
        val first = lastDropped < 0
        val countersReset = !first && (droppedFrames < lastDropped || emittedFrames < lastEmitted)
        val expectedAdvance = ((nowMs - lastPollAtMs) * speed).toLong()
        val seeked = !first && wasPlaying && isPlaying &&
            abs((positionMs - lastPositionMs) - expectedAdvance) > seekToleranceMs
        val resumed = !first && isPlaying && !wasPlaying
        if (first || countersReset || seeked || resumed) {
            graceUntilMs = max(graceUntilMs, nowMs + graceMs)
            window.clear()
        }
        val dropped = if (first || countersReset) 0 else droppedFrames - lastDropped
        val emitted = if (first || countersReset) 0L else emittedFrames - lastEmitted
        val interpolated = !first && !countersReset && synthesizedFrames > lastSynthesized

        lastDropped = droppedFrames
        lastEmitted = emittedFrames
        lastSynthesized = synthesizedFrames
        lastPositionMs = positionMs
        lastPollAtMs = nowMs
        wasPlaying = isPlaying

        // Only sustained, uninterrupted interpolation is judged.
        if (!isPlaying || !interpolated) {
            window.clear()
            return false
        }
        if (nowMs < graceUntilMs) return false
        window.addLast(dropped to emitted)
        while (window.size > windowPolls) window.removeFirst()
        if (window.size < minimumPolls) return false
        val droppedTotal = window.sumOf { it.first }
        val emittedTotal = window.sumOf { it.second }
        return emittedTotal > 0 && droppedTotal.toFloat() / emittedTotal > maxDropRatio
    }
}

/**
 * Notices a screen that stays below the output rate: a phone locked to 60 Hz,
 * "smooth display" off, or a mode switch the system refuses as not seamless.
 * Frames beyond the refresh rate are drawn and never seen, so once the
 * display has held below the output for [patienceMs] of steady output, the
 * output drops to what the display shows for the rest of this video and
 * quality. Media3 only asks for the new rate once its estimator has synced
 * on the output, which takes a moment, hence the patience.
 */
class ScreenRateWatch(private val patienceMs: Long = 5_000L) {
    private var belowSinceMs = 0L

    /** The rate the display was seen to stay at, or 0 while nothing argues against the target. */
    var limitFps: Int = 0
        private set

    fun restart() {
        belowSinceMs = 0L
        limitFps = 0
    }

    fun onPoll(nowMs: Long, emitting: Boolean, outputFps: Int, displayHz: Float) {
        val shown = displayHz.roundToInt()
        if (!emitting || outputFps <= 0 || shown <= 0 || shown + 5 >= outputFps) {
            belowSinceMs = 0L
            return
        }
        if (belowSinceMs == 0L) {
            belowSinceMs = nowMs
        } else if (nowMs - belowSinceMs >= patienceMs) {
            limitFps = shown.coerceAtLeast(FrameInterpolationPolicy.MIN_TARGET_FPS)
            belowSinceMs = 0L
        }
    }
}

/**
 * Decides, from the main thread, whether the effect should be interpolating
 * right now and at what rate, and says what it is doing: the user's switch
 * and rate cap, the screen, the current video, the playback speed, the
 * phone's thermal state and battery saver, and whether this video at this
 * quality has already shown the phone cannot keep up.
 */
class FrameInterpolationGovernor(context: Context) {

    val control = FrameInterpolationControl()

    private val powerManager = context.getSystemService(PowerManager::class.java)
    private val displayManager = context.getSystemService(DisplayManager::class.java)
    private val dropWatch = FrameDropWatch()
    private val screenWatch = ScreenRateWatch()
    private var cannotKeepUp = false
    private var playbackKey: String? = null
    private var lastEmitted = 0L
    private var lastReportAtMs = 0L

    private val _status = MutableStateFlow<FrameInterpolationStatus>(FrameInterpolationStatus.Disabled)
    val status: StateFlow<FrameInterpolationStatus> = _status.asStateFlow()

    /** Called from the player's progress poll, twice a second. */
    fun update(
        enabledByUser: Boolean,
        userMaxFps: Int,
        pipelineInstalled: Boolean,
        currentVideoId: String?,
        qualityKey: String?,
        isLive: Boolean,
        isHdr: Boolean,
        speed: Float,
        isPlaying: Boolean,
        positionMs: Long,
        droppedFrames: Int?,
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
            screenWatch.restart()
        }

        val display = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)
        val screenTarget = FrameInterpolationPolicy.targetFps(userMaxFps, displayMaxHz(display))
        val target = screenWatch.limitFps.takeIf { it > 0 }?.let { minOf(it, screenTarget) } ?: screenTarget
        control.targetFps = target
        control.speed = speed

        val thermal = powerManager?.currentThermalStatus ?: PowerManager.THERMAL_STATUS_NONE
        val powerSave = powerManager?.isPowerSaveMode == true
        val open = FrameInterpolationPolicy.isGateOpen(
            enabledByUser = true,
            isLive = isLive,
            isHdr = isHdr,
            thermalStatus = thermal,
            powerSave = powerSave,
            cannotKeepUp = cannotKeepUp,
        )
        setActive(open)

        val emitted = control.emitted
        val emitting = isPlaying && emitted > lastEmitted
        lastEmitted = emitted
        if (open && display != null) {
            screenWatch.onPoll(now, emitting, control.outputFps, display.refreshRate)
        }

        if (open && droppedFrames != null &&
            dropWatch.onPoll(
                nowMs = now,
                isPlaying = isPlaying,
                positionMs = positionMs,
                speed = speed,
                droppedFrames = droppedFrames,
                emittedFrames = emitted,
                synthesizedFrames = control.synthesized,
            )
        ) {
            KLog.w(TAG, "Dropping frames while interpolating; off for this video and quality")
            cannotKeepUp = true
            setActive(false)
        }

        // One line every ten seconds of playback: enough to read a phone's
        // behaviour off `adb logcat -s FrameInterpolation` without flooding it.
        if (isPlaying && now - lastReportAtMs >= REPORT_EVERY_MS) {
            lastReportAtMs = now
            KLog.d(
                TAG,
                "source=${"%.2f".format(control.sourceFps)}fps out=${control.outputFps}fps " +
                    "target=$target screen=${display?.refreshRate?.roundToInt()}Hz " +
                    "drawn=${control.synthesized} emitted=$emitted dropped=$droppedFrames " +
                    "pairs=${control.pairs} unmatched=${control.unmatchedPercent}% " +
                    "sampledCuts=${control.sampledCuts} active=${control.active} engine=${control.engine}"
            )
        }

        val sourceFps = control.sourceFps
        val outputFps = control.outputFps
        _status.value = when {
            control.unsupported -> FrameInterpolationStatus.Unsupported
            cannotKeepUp -> FrameInterpolationStatus.CannotKeepUp
            isLive -> FrameInterpolationStatus.Live
            isHdr -> FrameInterpolationStatus.Hdr
            thermal >= PowerManager.THERMAL_STATUS_MODERATE -> FrameInterpolationStatus.Hot
            powerSave -> FrameInterpolationStatus.BatterySaver
            sourceFps <= 0f -> FrameInterpolationStatus.Measuring
            outputFps > 0 -> FrameInterpolationStatus.Active(
                sourceFps = sourceFps.roundToInt(),
                outputFps = outputFps,
                limit = when {
                    control.limitedByResolution -> FrameInterpolationStatus.RateLimit.RESOLUTION
                    target < screenTarget -> FrameInterpolationStatus.RateLimit.SCREEN
                    else -> FrameInterpolationStatus.RateLimit.NONE
                },
                targetFps = screenTarget,
            )
            sourceFps * speed * FrameInterpolationPolicy.PASS_THROUGH_MARGIN >= target ->
                FrameInterpolationStatus.NotNeeded((sourceFps * speed).roundToInt())
            else -> FrameInterpolationStatus.Measuring
        }
    }

    /** The player closed: whatever opens next, even the same video, starts fresh. */
    fun reset() {
        playbackKey = null
        cannotKeepUp = false
        screenWatch.restart()
        setActive(false)
    }

    private fun setActive(active: Boolean) {
        if (active != control.active) {
            KLog.i(TAG, if (active) "Interpolating up to ${control.targetFps} fps" else "Passing frames through")
            control.active = active
        }
    }

    /**
     * The fastest refresh rate the display offers at its current resolution.
     * Not the current rate: a phone idles at 60 Hz until something asks for
     * more, and asking is exactly what a 120 fps output does.
     */
    private fun displayMaxHz(display: Display?): Float {
        display ?: return FrameInterpolationPolicy.MIN_TARGET_FPS.toFloat()
        val mode = display.mode
        return display.supportedModes
            .filter { it.physicalWidth == mode.physicalWidth && it.physicalHeight == mode.physicalHeight }
            .maxOfOrNull { it.refreshRate }
            ?: display.refreshRate
    }

    private companion object {
        const val TAG = "FrameInterpolation"
        const val REPORT_EVERY_MS = 10_000L
    }
}
