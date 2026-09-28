package com.ivor.ivormusic.service

import android.opengl.GLES20
import androidx.media3.common.C
import androidx.media3.common.GlObjectsProvider
import androidx.media3.common.GlTextureInfo
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.GlShaderProgram
import com.ivor.ivormusic.util.KLog
import java.util.concurrent.Executor
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * The GPU half of [FrameInterpolationEffect]: while interpolating, emits a
 * frame on every tick of a steady output clock ([OutputClock]); a tick that
 * lands on a decoded frame B shows B as it is, and a tick between the kept
 * predecessor A and B is drawn at its phase t in (0, 1) between them by a
 * [MotionEngine].
 *
 * **The strongest engine the GPU has.** On a GLES 3.1+ context (every recent
 * phone; Media3 asks for ES 3 and gets the driver's highest) it runs
 * [ComputeMotionEngine], the bidirectional block matcher with occlusion-aware
 * splatting; anywhere else, or if that engine fails to build or throws while
 * running, [FragmentMotionEngine]. Only when the last engine fails does
 * interpolation stop - never playback.
 *
 * **Capacity: one input at a time, and only with room for all its outputs.**
 * The final stage holds each output texture until its display time, so the
 * pool bounds how far ahead of the screen decoding runs
 * ([FrameInterpolationPolicy.poolCapacity]); textures are allocated lazily and
 * a pool that shrinks releases its surplus as it comes back.
 *
 * Every method runs on Media3's GL thread with the pipeline's context current.
 */
@UnstableApi
internal class FrameInterpolationShaderProgram(
    private val control: FrameInterpolationControl,
    private val useHdr: Boolean
) : GlShaderProgram {

    private var inputListener: GlShaderProgram.InputListener =
        object : GlShaderProgram.InputListener {}
    private var outputListener: GlShaderProgram.OutputListener =
        object : GlShaderProgram.OutputListener {}
    private var errorListener: GlShaderProgram.ErrorListener = GlShaderProgram.ErrorListener { }
    private var errorExecutor: Executor = Executor { it.run() }

    private var copyProgram: GlProgram? = null

    /** The engine in use, and the ones still to fall back to, strongest first. */
    private var engine: MotionEngine? = null
    private val fallbacks = ArrayDeque<MotionEngine>()

    /** False once every engine failed; frames still pass through. */
    private var interpolationUsable = !useHdr
    private var programsBuilt = false

    private var frameWidth = 0
    private var frameHeight = 0
    private val bytesPerPixel = if (useHdr) 8 else 4

    /** Outputs the next input may produce, and the pool sized for it. */
    private var inputDemand = 1
    private var poolCapacity = FrameInterpolationPolicy.poolCapacity(1, 0, 0, bytesPerPixel)
    private val freeOutputs = ArrayDeque<GlTextureInfo>()
    private val usedOutputs = ArrayList<GlTextureInfo>()
    private var awaitingCapacity = false

    /** Full-size copy of the last kept frame A. */
    private var reference: GlTextureInfo? = null
    private var hasReference = false
    /** The engine already prepared this input as B, so it can become A without redoing it. */
    private var currentPrepared = false
    private var referenceTimeUs = C.TIME_UNSET
    private var lastInputTimeUs = C.TIME_UNSET

    /** Smoothed media-time gap between decoded frames, 0 until measured. */
    private var sourceIntervalUs = 0.0
    private val clock = OutputClock()
    /** Timestamps handed downstream must strictly increase. */
    private var lastEmittedUs = Long.MIN_VALUE

    override fun setInputListener(inputListener: GlShaderProgram.InputListener) {
        this.inputListener = inputListener
        if (freeCapacity() >= inputDemand) {
            inputListener.onReadyToAcceptInputFrame()
        } else {
            awaitingCapacity = true
        }
    }

    override fun setOutputListener(outputListener: GlShaderProgram.OutputListener) {
        this.outputListener = outputListener
    }

    override fun setErrorListener(executor: Executor, errorListener: GlShaderProgram.ErrorListener) {
        errorExecutor = executor
        this.errorListener = errorListener
    }

    override fun queueInputFrame(
        glObjectsProvider: GlObjectsProvider,
        inputTexture: GlTextureInfo,
        presentationTimeUs: Long
    ) {
        try {
            ensureConfigured(inputTexture.width, inputTexture.height)
            measureSource(presentationTimeUs)
            currentPrepared = false
            if (interpolationUsable && control.active) {
                queueInterpolating(inputTexture, presentationTimeUs)
            } else {
                passThrough()
                emitCopy(inputTexture, presentationTimeUs)
            }
        } catch (e: GlUtil.GlException) {
            reportError(e, presentationTimeUs)
        } catch (e: VideoFrameProcessingException) {
            reportError(e, presentationTimeUs)
        }

        inputListener.onInputFrameProcessed(inputTexture)
        poolCapacity = FrameInterpolationPolicy.poolCapacity(
            inputDemand, frameWidth, frameHeight, bytesPerPixel
        )
        if (freeCapacity() >= inputDemand) {
            inputListener.onReadyToAcceptInputFrame()
        } else {
            awaitingCapacity = true
        }
    }

    override fun releaseOutputFrame(outputTexture: GlTextureInfo) {
        val index = usedOutputs.indexOfFirst { it.texId == outputTexture.texId }
        if (index < 0) return
        recycle(usedOutputs.removeAt(index))
        if (awaitingCapacity && freeCapacity() >= inputDemand) {
            awaitingCapacity = false
            inputListener.onReadyToAcceptInputFrame()
        }
    }

    override fun signalEndOfCurrentInputStream() {
        // The stream's last decoded frame may sit between two ticks; end on it
        // rather than on a drawn frame or one short of the end.
        if (hasReference && referenceTimeUs > lastEmittedUs) {
            try {
                emitCopy(reference!!, referenceTimeUs)
            } catch (e: GlUtil.GlException) {
                reportError(e, referenceTimeUs)
            }
        }
        // The next stream may be a different video, size or rate; never blend
        // across, and measure it afresh.
        passThrough()
        lastInputTimeUs = C.TIME_UNSET
        lastEmittedUs = Long.MIN_VALUE
        sourceIntervalUs = 0.0
        control.sourceFps = 0f
        outputListener.onCurrentOutputStreamEnded()
    }

    override fun flush() {
        // The consumer has already dropped every frame it held without
        // releasing them, as it does for BaseGlShaderProgram. A seek stays in
        // the same stream, so the measured rate survives it.
        val held = ArrayList(usedOutputs)
        usedOutputs.clear()
        held.forEach(::recycle)
        hasReference = false
        engine?.forgetHistory()
        clock.stop()
        lastInputTimeUs = C.TIME_UNSET
        lastEmittedUs = Long.MIN_VALUE
        awaitingCapacity = false
        inputListener.onFlush()
        inputListener.onReadyToAcceptInputFrame()
    }

    override fun release() {
        try {
            copyProgram?.delete()
            engine?.release()
            fallbacks.forEach { it.release() }
            fallbacks.clear()
            engine = null
            MotionGl.releaseQuietly(reference)
            reference = null
            for (texture in freeOutputs) texture.release()
            for (texture in usedOutputs) texture.release()
            freeOutputs.clear()
            usedOutputs.clear()
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }
    }

    // ---------------------------------------------------------------- frames

    private fun measureSource(presentationTimeUs: Long) {
        val intervalUs = if (lastInputTimeUs == C.TIME_UNSET) {
            C.TIME_UNSET
        } else {
            presentationTimeUs - lastInputTimeUs
        }
        lastInputTimeUs = presentationTimeUs
        if (intervalUs != C.TIME_UNSET && intervalUs in 1..MAX_MEASURED_INTERVAL_US) {
            sourceIntervalUs = if (sourceIntervalUs <= 0.0) {
                intervalUs.toDouble()
            } else {
                sourceIntervalUs * 0.9 + intervalUs * 0.1
            }
            control.sourceFps = (1_000_000.0 / sourceIntervalUs).toFloat()
        }
    }

    /** Frames go out as they came in, and nothing is kept for later. */
    private fun passThrough() {
        clock.stop()
        hasReference = false
        engine?.forgetHistory()
        inputDemand = 1
        control.outputFps = 0
        control.limitedByResolution = false
    }

    /**
     * One decoded frame B while interpolating: the ticks between the kept
     * frame A and B are drawn, a tick on B shows B, and B is kept as the next
     * pair's A. When the pair cannot be interpolated (the first frame, a gap,
     * a source already as fast as the output) B goes out at its own time and
     * the clock restarts from it if the stream still looks worth it.
     */
    private fun queueInterpolating(input: GlTextureInfo, presentationTimeUs: Long) {
        val fps = FrameInterpolationPolicy.affordableFps(control.targetFps, frameWidth, frameHeight)
        val speed = control.speed
        val stepUs = FrameInterpolationPolicy.outputStepUs(fps, speed, sourceIntervalUs)
        // A new rate or speed restarts the clock on this frame rather than
        // bending the old one.
        if (clock.isRunning && clock.needsRestart(stepUs)) clock.stop()

        val pairUs = if (hasReference) presentationTimeUs - referenceTimeUs else C.TIME_UNSET
        if (hasReference && clock.isRunning &&
            FrameInterpolationPolicy.shouldInterpolate(pairUs, clock.stepUs)
        ) {
            emitTicks(input, presentationTimeUs)
        } else {
            emitCopy(input, presentationTimeUs)
            // A gap breaks the temporal chain; its vectors describe other motion.
            engine?.forgetHistory()
            // Unmeasured counts as worth trying: the next pair decides.
            val worthIt = sourceIntervalUs <= 0.0 ||
                FrameInterpolationPolicy.shouldInterpolate(sourceIntervalUs.roundToLong(), stepUs)
            if (worthIt && interpolationUsable) clock.start(presentationTimeUs, stepUs) else clock.stop()
        }

        if (!clock.isRunning || !interpolationUsable) {
            // A 60 fps video on a 60 Hz screen costs one copy per frame and
            // nothing else.
            passThrough()
            return
        }
        keepAsReference(input, presentationTimeUs)
        if (!interpolationUsable || !hasReference) {
            passThrough()
            return
        }
        control.outputFps = (speed * 1_000_000.0 / clock.stepUs).roundToInt()
        control.limitedByResolution = fps < control.targetFps
        // One spare for a tick that rounding pushes into this input's span.
        inputDemand = FrameInterpolationPolicy.outputsPerInput(sourceIntervalUs, clock.stepUs) + 1
    }

    private fun emitTicks(input: GlTextureInfo, presentationTimeUs: Long) {
        val previousUs = referenceTimeUs
        val spanUs = (presentationTimeUs - previousUs).toFloat()
        // Rounding and a source a hair off its nominal rate put ticks a few
        // microseconds either side of a decoded frame; that is the frame.
        val toleranceUs = minOf(1_000L, (clock.stepUs / 4).toLong())
        var motionReady = false
        try {
            var guard = 0
            while (guard++ < MAX_TICKS_PER_INPUT) {
                val tickUs = clock.nextTickUs()
                if (tickUs >= presentationTimeUs - toleranceUs) break
                if (tickUs > lastEmittedUs && tickUs > previousUs) {
                    val motion = engine!!
                    if (!motionReady) {
                        motion.estimate(input)
                        currentPrepared = true
                        motionReady = true
                        val pairs = ++control.pairs
                        if (pairs % READBACK_EVERY_PAIRS == 1L) sampleMatchQuality()
                    }
                    val output = takeOutput()
                    try {
                        motion.compose(reference!!, input, output, (tickUs - previousUs) / spanUs)
                    } catch (e: Exception) {
                        recycle(output)
                        throw e
                    }
                    emit(output, tickUs)
                    control.synthesized++
                }
                clock.advance()
            }
        } catch (e: Exception) {
            // GL errors, a shader some driver rejects at run time, a uniform
            // optimised away (GlProgram throws NullPointerException for
            // those): this engine goes, playback stays.
            currentPrepared = false
            engineFailed(e)
            emitCopy(input, presentationTimeUs)
            return
        }
        val tickUs = clock.nextTickUs()
        if (tickUs <= presentationTimeUs + toleranceUs) {
            emitCopy(input, tickUs)
            clock.advance()
        }
    }

    private fun keepAsReference(input: GlTextureInfo, presentationTimeUs: Long) {
        try {
            drawCopy(input, reference!!)
            val motion = engine ?: return
            if (currentPrepared) motion.promoteCurrent() else motion.prepareReference(input)
            hasReference = true
            referenceTimeUs = presentationTimeUs
        } catch (e: Exception) {
            hasReference = false
            engineFailed(e)
        }
    }

    /**
     * Reads the engine's match quality back for the log. A readback waits for
     * the GPU to finish, so it happens once every [READBACK_EVERY_PAIRS] pairs
     * (about two seconds), never per frame.
     */
    private fun sampleMatchQuality() {
        try {
            val unmatched = engine?.sampleUnmatched() ?: return
            if (unmatched < 0f) return
            control.unmatchedPercent = (unmatched * 100).roundToInt()
            if (unmatched > CUT_FRACTION) control.sampledCuts++
        } catch (e: Exception) {
            KLog.w(TAG, "Could not sample match quality: ${e.message}")
        }
    }

    /** A copy of [source] out at [presentationTimeUs]; a time that would go backwards is skipped. */
    private fun emitCopy(source: GlTextureInfo, presentationTimeUs: Long) {
        if (presentationTimeUs <= lastEmittedUs) return
        val output = takeOutput()
        try {
            drawCopy(source, output)
        } catch (e: GlUtil.GlException) {
            recycle(output)
            throw e
        }
        emit(output, presentationTimeUs)
    }

    private fun emit(texture: GlTextureInfo, presentationTimeUs: Long) {
        usedOutputs += texture
        lastEmittedUs = presentationTimeUs
        control.emitted++
        outputListener.onOutputFrameAvailable(texture, presentationTimeUs)
    }

    private fun freeCapacity(): Int = poolCapacity - usedOutputs.size

    private fun takeOutput(): GlTextureInfo =
        freeOutputs.removeFirstOrNull() ?: MotionGl.texture(frameWidth, frameHeight, useHdr)

    /** Back into the pool, or released if it is the wrong size or the pool has shrunk. */
    private fun recycle(texture: GlTextureInfo) {
        if (texture.width == frameWidth && texture.height == frameHeight &&
            freeOutputs.size + usedOutputs.size < poolCapacity
        ) {
            freeOutputs.addLast(texture)
        } else {
            MotionGl.releaseQuietly(texture)
        }
    }

    /**
     * The engine in use cannot run here: drop to the next one down, set up
     * for the current size, starting a fresh pair. With none left,
     * interpolation stops and frames pass through.
     */
    private fun engineFailed(cause: Exception) {
        val failed = engine
        KLog.e(TAG, "Engine ${failed?.name} failed on this GPU", cause)
        try {
            failed?.release()
        } catch (e: Exception) {
            KLog.w(TAG, "Could not release the failed engine: ${e.message}")
        }
        engine = null
        hasReference = false
        currentPrepared = false
        clock.stop()
        while (fallbacks.isNotEmpty()) {
            val next = fallbacks.removeFirst()
            try {
                if (frameWidth > 0) next.configure(frameWidth, frameHeight)
                engine = next
                control.engine = next.name
                KLog.w(TAG, "Falling back to ${next.name}")
                return
            } catch (e: Exception) {
                KLog.e(TAG, "Engine ${next.name} could not start either", e)
                try {
                    next.release()
                } catch (ignored: Exception) {
                }
            }
        }
        KLog.e(TAG, "No interpolation engine runs on this GPU; passing frames through")
        interpolationUsable = false
        control.unsupported = true
        control.engine = ""
    }

    private fun reportError(cause: Exception, presentationTimeUs: Long) {
        val exception = cause as? VideoFrameProcessingException
            ?: VideoFrameProcessingException.from(cause, presentationTimeUs)
        errorExecutor.execute { errorListener.onError(exception) }
    }

    // ------------------------------------------------------------- resources

    private fun ensureConfigured(width: Int, height: Int) {
        if (!programsBuilt) buildPrograms()
        if (width == frameWidth && height == frameHeight) return

        // Free outputs of the old size go now; ones still on screen go when
        // they come back through releaseOutputFrame.
        while (freeOutputs.isNotEmpty()) MotionGl.releaseQuietly(freeOutputs.removeFirst())
        MotionGl.releaseQuietly(reference)
        reference = null
        frameWidth = width
        frameHeight = height
        poolCapacity = FrameInterpolationPolicy.poolCapacity(inputDemand, width, height, bytesPerPixel)
        hasReference = false
        clock.stop()

        if (interpolationUsable) {
            reference = MotionGl.texture(width, height)
            try {
                engine?.configure(width, height)
                KLog.i(TAG, "Configured ${width}x$height on ${engine?.name}")
            } catch (e: Exception) {
                engineFailed(e)
            }
        }
    }

    /**
     * The copy program, then the strongest engine this context can build:
     * compute on GLES 3.1+, fragment passes otherwise, each falling back to
     * the next.
     */
    private fun buildPrograms() {
        programsBuilt = true
        copyProgram = GlProgram(VERTEX_SHADER, COPY_FRAGMENT).apply {
            setBufferAttribute(
                "aFramePosition",
                GlUtil.getNormalizedCoordinateBounds(),
                GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE
            )
        }
        if (!interpolationUsable) return
        val gles = MotionGl.glesVersion()
        KLog.i(
            TAG,
            "GPU ${GLES20.glGetString(GLES20.GL_RENDERER)} | ${GLES20.glGetString(GLES20.GL_VERSION)}"
        )
        val candidates = buildList {
            if (gles >= 31) add(ComputeMotionEngine(control))
            add(FragmentMotionEngine(control))
        }
        for ((index, candidate) in candidates.withIndex()) {
            try {
                candidate.build()
                engine = candidate
                fallbacks.clear()
                fallbacks.addAll(candidates.drop(index + 1))
                control.engine = candidate.name
                KLog.i(TAG, "Motion engine: ${candidate.name}")
                return
            } catch (e: Exception) {
                KLog.e(TAG, "Engine ${candidate.name} could not build here", e)
                try {
                    candidate.release()
                } catch (ignored: Exception) {
                }
            }
        }
        KLog.e(TAG, "No interpolation engine builds on this GPU; passing frames through")
        interpolationUsable = false
        control.unsupported = true
    }

    private fun drawCopy(source: GlTextureInfo, target: GlTextureInfo) {
        val program = copyProgram!!
        GlUtil.focusFramebufferUsingCurrentContext(target.fboId, target.width, target.height)
        program.use()
        program.setSamplerTexIdUniform("uTex", source.texId, 0)
        program.bindAttributesAndUniforms()
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GlUtil.checkGlError()
    }

    private companion object {
        const val TAG = MotionGl.TAG

        /**
         * A backstop against a runaway loop over ticks; the policy keeps a
         * real pair to eight at most ([FrameInterpolationPolicy.MAX_OUTPUTS_PER_INPUT]).
         */
        const val MAX_TICKS_PER_INPUT = 32

        /** Gaps longer than this (under 5 fps) are pauses or cuts, not a frame rate. */
        const val MAX_MEASURED_INTERVAL_US = 200_000L

        /** One match-quality readback per this many pairs, for the log. */
        const val READBACK_EVERY_PAIRS = 60L

        /** The share of a pair left unmatched past which the log counts a scene cut. */
        const val CUT_FRACTION = 0.5f

        const val VERTEX_SHADER = """
attribute vec4 aFramePosition;
varying vec2 vTexCoord;
void main() {
  gl_Position = aFramePosition;
  vTexCoord = aFramePosition.xy * 0.5 + 0.5;
}
"""

        const val COPY_FRAGMENT = """
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
uniform sampler2D uTex;
varying vec2 vTexCoord;
void main() {
  gl_FragColor = texture2D(uTex, vTexCoord);
}
"""
    }
}
