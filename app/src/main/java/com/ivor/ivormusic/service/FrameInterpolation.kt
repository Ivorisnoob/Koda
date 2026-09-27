package com.ivor.ivormusic.service

import android.content.Context
import android.os.PowerManager
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import com.ivor.ivormusic.util.KLog
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
 * The one switch the GL thread reads per frame. Written from the main thread by
 * [FrameInterpolationGovernor]; a stale read costs one frame either way.
 */
class FrameInterpolationControl {
    @Volatile
    var active: Boolean = false
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

    /** Faster than this and the source already reaches the screen at 36+ fps. */
    const val MAX_SPEED = 1.2f

    /**
     * Dropped frames per second that mean this phone cannot keep up. Media3
     * reports drops in batches of 50, so this is "50 drops in under 25s".
     */
    private const val DROPS_PER_SECOND_BUDGET = 2.0

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
        suspendedForDrops: Boolean
    ): Boolean = enabledByUser &&
        !isLive &&
        !isHdr &&
        speed <= MAX_SPEED &&
        thermalStatus < PowerManager.THERMAL_STATUS_MODERATE &&
        !powerSave &&
        !suspendedForDrops

    fun dropsExceedBudget(droppedFrames: Int, elapsedMs: Long): Boolean {
        if (droppedFrames <= 0) return false
        if (elapsedMs <= 0L) return true
        return droppedFrames * 1000.0 / elapsedMs > DROPS_PER_SECOND_BUDGET
    }

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
 * Decides, from the main thread, whether the effect should be interpolating
 * right now: the user's switch, the current video, the playback speed, the
 * phone's thermal state and battery saver, and whether this video has already
 * shown the phone cannot keep up.
 */
class FrameInterpolationGovernor(context: Context) {

    val control = FrameInterpolationControl()

    private val powerManager = context.getSystemService(PowerManager::class.java)
    private var suspendedForDrops = false
    private var videoId: String? = null

    /** Called from the player's progress poll; cheap enough to run twice a second. */
    fun update(
        enabledByUser: Boolean,
        currentVideoId: String?,
        isLive: Boolean,
        isHdr: Boolean,
        speed: Float
    ) {
        if (currentVideoId != videoId) {
            // A new video gets a fresh chance: the one that overloaded the GPU
            // may have been a 4K stream, the next a 720p one.
            videoId = currentVideoId
            suspendedForDrops = false
        }
        val open = FrameInterpolationPolicy.isGateOpen(
            enabledByUser = enabledByUser,
            isLive = isLive,
            isHdr = isHdr,
            speed = speed,
            thermalStatus = powerManager?.currentThermalStatus ?: PowerManager.THERMAL_STATUS_NONE,
            powerSave = powerManager?.isPowerSaveMode == true,
            suspendedForDrops = suspendedForDrops
        )
        if (open != control.active) {
            KLog.i(TAG, if (open) "Interpolating" else "Passing frames through")
            control.active = open
        }
    }

    /** From `AnalyticsListener.onDroppedVideoFrames`, on the main thread. */
    fun onDroppedFrames(droppedFrames: Int, elapsedMs: Long) {
        if (!control.active || suspendedForDrops) return
        if (FrameInterpolationPolicy.dropsExceedBudget(droppedFrames, elapsedMs)) {
            KLog.w(TAG, "Dropped $droppedFrames frames in ${elapsedMs}ms; off for this video")
            suspendedForDrops = true
            control.active = false
        }
    }

    private companion object {
        const val TAG = "FrameInterpolation"
    }
}
