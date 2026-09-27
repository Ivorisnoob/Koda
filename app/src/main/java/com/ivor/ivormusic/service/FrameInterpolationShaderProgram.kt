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

/**
 * The GPU half of [FrameInterpolationEffect]: for each decoded frame B with a
 * kept predecessor A, emits a motion-compensated A/B midpoint and then B.
 *
 * **Motion is estimated symmetrically, at the midpoint.** For each position p
 * of the frame being invented, the search looks for the offset d at which
 * A(p - d) matches B(p + d), so the vector field is already expressed in the
 * new frame's coordinates: no forward warp, no holes to fill. The search runs
 * coarse to fine over a three-level luma pyramid whose finest level has a
 * 320-texel long side ([FrameInterpolationPolicy.levelSizes]), so its cost does
 * not grow with the video's resolution; only the final compose pass runs at
 * full size.
 *
 * **Where the best match is still poor it repeats A instead of inventing.** A
 * scene cut, an occlusion or motion beyond the search range leaves a high
 * residual, and the compose pass fades from the warped blend to A as it rises.
 * On a cut that means the midpoint is simply a repeated frame, which is what
 * no interpolation would have shown.
 *
 * **GLSL ES 1.00 throughout.** [verified September 2026 against the Media3
 * 1.11.0 bytecode] `DefaultVideoFrameProcessor` asks for an ES 3 context and
 * falls back to ES 2, so the shaders must compile on both.
 *
 * **Anything wrong in the interpolation path turns interpolation off, not
 * playback.** A shader that fails to compile on some GPU, or a GL error while
 * interpolating, logs and falls back to passing frames through. Only a failure
 * to copy the frame itself is reported as a pipeline error.
 *
 * **Capacity: one input at a time, and only with room for two outputs.**
 * The final stage holds each output texture until its display time, so the
 * pool bounds how far ahead of the screen decoding runs; it is allocated
 * lazily and smaller for frames above ~2.5 MP, where each texture costs 33 MB
 * at 4K.
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
    private var lumaProgram: GlProgram? = null
    private var searchProgram: GlProgram? = null
    private var refineProgram: GlProgram? = null
    private var composeProgram: GlProgram? = null

    /** False once the interpolation shaders failed; frames still pass through. */
    private var interpolationUsable = !useHdr
    private var programsBuilt = false

    private var frameWidth = 0
    private var frameHeight = 0
    private var poolCapacity = POOL_CAPACITY
    private val freeOutputs = ArrayDeque<GlTextureInfo>()
    private val usedOutputs = ArrayList<GlTextureInfo>()
    private var awaitingCapacity = false

    /** Full-size copy of the last kept frame, and both luma pyramids, finest first. */
    private var reference: GlTextureInfo? = null
    private var referenceLevels: Array<GlTextureInfo>? = null
    private var currentLevels: Array<GlTextureInfo>? = null
    private var fields: Array<GlTextureInfo>? = null
    private var hasReference = false
    private var currentLevelsBuilt = false
    private var referenceTimeUs = C.TIME_UNSET
    private var lastInputTimeUs = C.TIME_UNSET

    override fun setInputListener(inputListener: GlShaderProgram.InputListener) {
        this.inputListener = inputListener
        if (freeCapacity() >= OUTPUTS_PER_INPUT) {
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
            val intervalUs = if (lastInputTimeUs == C.TIME_UNSET) {
                C.TIME_UNSET
            } else {
                presentationTimeUs - lastInputTimeUs
            }
            lastInputTimeUs = presentationTimeUs
            if (intervalUs != C.TIME_UNSET && intervalUs in 1..MAX_MEASURED_INTERVAL_US) {
                val fps = 1_000_000f / intervalUs
                val previous = control.sourceFps
                control.sourceFps = if (previous <= 0f) fps else previous * 0.9f + fps * 0.1f
            }
            val wanted = interpolationUsable && control.active
            currentLevelsBuilt = false

            if (wanted && hasReference &&
                FrameInterpolationPolicy.shouldInterpolate(presentationTimeUs - referenceTimeUs)
            ) {
                interpolate(inputTexture, presentationTimeUs)
            }

            val output = takeOutput()
            drawCopy(inputTexture, output)
            emit(output, presentationTimeUs)

            // Keep the frame for the next midpoint only while the stream looks
            // interpolatable, so a 60 fps video costs one copy per frame and
            // no pyramid.
            val keep = interpolationUsable && control.active &&
                (intervalUs == C.TIME_UNSET || FrameInterpolationPolicy.shouldInterpolate(intervalUs))
            if (keep) keepAsReference(inputTexture, presentationTimeUs) else hasReference = false
        } catch (e: GlUtil.GlException) {
            reportError(e, presentationTimeUs)
        } catch (e: VideoFrameProcessingException) {
            reportError(e, presentationTimeUs)
        }

        inputListener.onInputFrameProcessed(inputTexture)
        if (freeCapacity() >= OUTPUTS_PER_INPUT) {
            inputListener.onReadyToAcceptInputFrame()
        } else {
            awaitingCapacity = true
        }
    }

    override fun releaseOutputFrame(outputTexture: GlTextureInfo) {
        val index = usedOutputs.indexOfFirst { it.texId == outputTexture.texId }
        if (index < 0) return
        val texture = usedOutputs.removeAt(index)
        if (texture.width == frameWidth && texture.height == frameHeight) {
            freeOutputs.addLast(texture)
        } else {
            releaseQuietly(texture)
        }
        if (awaitingCapacity && freeCapacity() >= OUTPUTS_PER_INPUT) {
            awaitingCapacity = false
            inputListener.onReadyToAcceptInputFrame()
        }
    }

    override fun signalEndOfCurrentInputStream() {
        // The next stream may be a different video or size; never blend across.
        hasReference = false
        lastInputTimeUs = C.TIME_UNSET
        control.sourceFps = 0f
        outputListener.onCurrentOutputStreamEnded()
    }

    override fun flush() {
        // The consumer has already dropped every frame it held without
        // releasing them, as it does for BaseGlShaderProgram.
        for (texture in usedOutputs) {
            if (texture.width == frameWidth && texture.height == frameHeight) {
                freeOutputs.addLast(texture)
            } else {
                releaseQuietly(texture)
            }
        }
        usedOutputs.clear()
        hasReference = false
        lastInputTimeUs = C.TIME_UNSET
        awaitingCapacity = false
        inputListener.onFlush()
        inputListener.onReadyToAcceptInputFrame()
    }

    override fun release() {
        try {
            copyProgram?.delete()
            lumaProgram?.delete()
            searchProgram?.delete()
            refineProgram?.delete()
            composeProgram?.delete()
            releaseFrameTextures()
            for (texture in freeOutputs) texture.release()
            for (texture in usedOutputs) texture.release()
            freeOutputs.clear()
            usedOutputs.clear()
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }
    }

    // ---------------------------------------------------------------- frames

    private fun interpolate(input: GlTextureInfo, presentationTimeUs: Long) {
        var output: GlTextureInfo? = null
        try {
            output = takeOutput()
            buildPyramid(input, currentLevels!!)
            currentLevelsBuilt = true
            estimateMotion()
            drawCompose(input, output)
        } catch (e: GlUtil.GlException) {
            output?.let(freeOutputs::addLast)
            currentLevelsBuilt = false
            disableInterpolation(e)
            return
        }
        emit(output, FrameInterpolationPolicy.midpointUs(referenceTimeUs, presentationTimeUs))
        control.midpoints++
    }

    private fun keepAsReference(input: GlTextureInfo, presentationTimeUs: Long) {
        try {
            drawCopy(input, reference!!)
            if (currentLevelsBuilt) {
                // The pyramid built for this frame's midpoint is the next
                // midpoint's reference; swap rather than rebuild.
                val kept = currentLevels
                currentLevels = referenceLevels
                referenceLevels = kept
            } else {
                buildPyramid(input, referenceLevels!!)
            }
            hasReference = true
            referenceTimeUs = presentationTimeUs
        } catch (e: GlUtil.GlException) {
            disableInterpolation(e)
        }
    }

    private fun buildPyramid(source: GlTextureInfo, levels: Array<GlTextureInfo>) {
        var from = source
        for (level in levels) {
            drawLuma(from, level)
            from = level
        }
    }

    /** Coarsest level searched outright, then each finer level refines its parent. */
    private fun estimateMotion() {
        val prev = referenceLevels!!
        val cur = currentLevels!!
        val field = fields!!
        drawSearch(prev[2], cur[2], field[2])
        drawRefine(prev[1], cur[1], field[2], field[1], halfStep = false)
        drawRefine(prev[0], cur[0], field[1], field[0], halfStep = true)
    }

    private fun emit(texture: GlTextureInfo, presentationTimeUs: Long) {
        usedOutputs += texture
        outputListener.onOutputFrameAvailable(texture, presentationTimeUs)
    }

    private fun freeCapacity(): Int = poolCapacity - usedOutputs.size

    private fun takeOutput(): GlTextureInfo =
        freeOutputs.removeFirstOrNull() ?: createTexture(frameWidth, frameHeight, useHdr)

    private fun disableInterpolation(cause: Exception) {
        KLog.e(TAG, "Interpolation failed on this GPU; passing frames through", cause)
        interpolationUsable = false
        hasReference = false
        control.unsupported = true
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
        while (freeOutputs.isNotEmpty()) releaseQuietly(freeOutputs.removeFirst())
        releaseFrameTextures()
        frameWidth = width
        frameHeight = height
        poolCapacity = if (width.toLong() * height > LARGE_FRAME_PIXELS) {
            POOL_CAPACITY_LARGE
        } else {
            POOL_CAPACITY
        }
        hasReference = false

        if (interpolationUsable) {
            try {
                val sizes = FrameInterpolationPolicy.levelSizes(width, height)
                reference = createTexture(width, height, highPrecision = false)
                referenceLevels = Array(sizes.size) { createTexture(sizes[it].first, sizes[it].second, false) }
                currentLevels = Array(sizes.size) { createTexture(sizes[it].first, sizes[it].second, false) }
                fields = Array(sizes.size) { createTexture(sizes[it].first, sizes[it].second, false) }
            } catch (e: GlUtil.GlException) {
                disableInterpolation(e)
            }
        }
    }

    private fun buildPrograms() {
        programsBuilt = true
        copyProgram = createProgram(COPY_FRAGMENT)
        if (!interpolationUsable) return
        try {
            lumaProgram = createProgram(LUMA_FRAGMENT)
            searchProgram = createProgram(SEARCH_FRAGMENT)
            refineProgram = createProgram(REFINE_FRAGMENT)
            composeProgram = createProgram(COMPOSE_FRAGMENT)
        } catch (e: GlUtil.GlException) {
            disableInterpolation(e)
        }
    }

    private fun createProgram(fragmentShader: String): GlProgram =
        GlProgram(VERTEX_SHADER, fragmentShader).apply {
            setBufferAttribute(
                "aFramePosition",
                GlUtil.getNormalizedCoordinateBounds(),
                GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE
            )
        }

    private fun createTexture(width: Int, height: Int, highPrecision: Boolean): GlTextureInfo {
        val texId = GlUtil.createTexture(width, height, highPrecision)
        val fboId = GlUtil.createFboForTexture(texId)
        return GlTextureInfo(texId, fboId, C.INDEX_UNSET, width, height)
    }

    private fun releaseFrameTextures() {
        reference?.let(::releaseQuietly)
        referenceLevels?.forEach(::releaseQuietly)
        currentLevels?.forEach(::releaseQuietly)
        fields?.forEach(::releaseQuietly)
        reference = null
        referenceLevels = null
        currentLevels = null
        fields = null
    }

    private fun releaseQuietly(texture: GlTextureInfo) {
        try {
            texture.release()
        } catch (e: GlUtil.GlException) {
            KLog.w(TAG, "Could not release a texture: ${e.message}")
        }
    }

    // ----------------------------------------------------------------- draws

    private fun drawCopy(source: GlTextureInfo, target: GlTextureInfo) {
        val program = copyProgram!!
        begin(program, target)
        program.setSamplerTexIdUniform("uTex", source.texId, 0)
        finish(program)
    }

    private fun drawLuma(source: GlTextureInfo, target: GlTextureInfo) {
        val program = lumaProgram!!
        begin(program, target)
        program.setSamplerTexIdUniform("uTex", source.texId, 0)
        program.setFloatsUniform("uDstTexel", texel(target))
        finish(program)
    }

    private fun drawSearch(prev: GlTextureInfo, cur: GlTextureInfo, target: GlTextureInfo) {
        val program = searchProgram!!
        begin(program, target)
        program.setSamplerTexIdUniform("uPrev", prev.texId, 0)
        program.setSamplerTexIdUniform("uCur", cur.texId, 1)
        program.setFloatsUniform("uTexel", texel(target))
        finish(program)
    }

    private fun drawRefine(
        prev: GlTextureInfo,
        cur: GlTextureInfo,
        coarse: GlTextureInfo,
        target: GlTextureInfo,
        halfStep: Boolean
    ) {
        val program = refineProgram!!
        begin(program, target)
        program.setSamplerTexIdUniform("uPrev", prev.texId, 0)
        program.setSamplerTexIdUniform("uCur", cur.texId, 1)
        program.setSamplerTexIdUniform("uCoarse", coarse.texId, 2)
        program.setFloatsUniform("uTexel", texel(target))
        program.setFloatsUniform("uCoarseTexel", texel(coarse))
        program.setFloatsUniform(
            "uCoarseScale",
            floatArrayOf(
                target.width.toFloat() / coarse.width,
                target.height.toFloat() / coarse.height
            )
        )
        program.setFloatUniform("uHalfStep", if (halfStep) 1f else 0f)
        finish(program)
    }

    private fun drawCompose(current: GlTextureInfo, target: GlTextureInfo) {
        val program = composeProgram!!
        val field = fields!![0]
        begin(program, target)
        program.setSamplerTexIdUniform("uPrev", reference!!.texId, 0)
        program.setSamplerTexIdUniform("uCur", current.texId, 1)
        program.setSamplerTexIdUniform("uField", field.texId, 2)
        program.setFloatsUniform("uFieldTexel", texel(field))
        finish(program)
    }

    private fun begin(program: GlProgram, target: GlTextureInfo) {
        GlUtil.focusFramebufferUsingCurrentContext(target.fboId, target.width, target.height)
        program.use()
    }

    private fun finish(program: GlProgram) {
        program.bindAttributesAndUniforms()
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GlUtil.checkGlError()
    }

    private fun texel(texture: GlTextureInfo) =
        floatArrayOf(1f / texture.width, 1f / texture.height)

    private companion object {
        const val TAG = "FrameInterpolation"

        /** A midpoint and the frame itself. */
        const val OUTPUTS_PER_INPUT = 2
        const val POOL_CAPACITY = 6
        const val POOL_CAPACITY_LARGE = 4
        const val LARGE_FRAME_PIXELS = 2_500_000L

        /** Gaps longer than this (under 5 fps) are pauses or cuts, not a frame rate. */
        const val MAX_MEASURED_INTERVAL_US = 200_000L

        const val VERTEX_SHADER = """
attribute vec4 aFramePosition;
varying vec2 vTexCoord;
void main() {
  gl_Position = aFramePosition;
  vTexCoord = aFramePosition.xy * 0.5 + 0.5;
}
"""

        const val PRECISION = """
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
"""

        const val COPY_FRAGMENT = PRECISION + """
uniform sampler2D uTex;
varying vec2 vTexCoord;
void main() {
  gl_FragColor = texture2D(uTex, vTexCoord);
}
"""

        /**
         * Luma at the target size from a 3x3 grid of bilinear taps, which
         * averages the footprint of a 6:1 reduction closely enough to keep
         * fine texture from aliasing into false matches.
         */
        const val LUMA_FRAGMENT = PRECISION + """
uniform sampler2D uTex;
uniform vec2 uDstTexel;
varying vec2 vTexCoord;
void main() {
  vec3 weights = vec3(0.299, 0.587, 0.114);
  vec2 spacing = uDstTexel / 3.0;
  float sum = 0.0;
  for (int j = -1; j <= 1; j++) {
    for (int i = -1; i <= 1; i++) {
      sum += dot(texture2D(uTex, vTexCoord + vec2(float(i), float(j)) * spacing).rgb, weights);
    }
  }
  gl_FragColor = vec4(vec3(sum / 9.0), 1.0);
}
"""

        /**
         * Vectors are half the A-to-B motion, in the level's own texels,
         * stored as rg = d / 40 + 0.5 (range +-20). b is the match residual
         * times four; the compose pass trusts the vector less as it rises.
         */
        const val MOTION_COMMON = PRECISION + """
uniform sampler2D uPrev;
uniform sampler2D uCur;
uniform vec2 uTexel;
varying vec2 vTexCoord;
const float SCALE = 40.0;

float matchCost(vec2 d) {
  float sum = 0.0;
  for (int j = -1; j <= 1; j++) {
    for (int i = -1; i <= 1; i++) {
      vec2 p = vTexCoord + vec2(float(i), float(j)) * uTexel;
      sum += abs(texture2D(uPrev, p - d * uTexel).r - texture2D(uCur, p + d * uTexel).r);
    }
  }
  return sum / 9.0;
}

vec4 encode(vec2 d, float cost) {
  return vec4(clamp(d / SCALE + 0.5, 0.0, 1.0), clamp(cost * 4.0, 0.0, 1.0), 1.0);
}
"""

        /**
         * Exhaustive +-3 texel search at the coarsest level, with a small
         * pull towards no motion so flat areas do not pick noise.
         */
        const val SEARCH_FRAGMENT = MOTION_COMMON + """
void main() {
  vec2 best = vec2(0.0);
  float bestCost = matchCost(best);
  for (int y = -3; y <= 3; y++) {
    for (int x = -3; x <= 3; x++) {
      vec2 d = vec2(float(x), float(y));
      float c = matchCost(d) + 0.002 * length(d);
      if (c < bestCost) {
        bestCost = c;
        best = d;
      }
    }
  }
  gl_FragColor = encode(best, bestCost);
}
"""

        /**
         * The parent's vector here and at its four neighbours (so an edge can
         * take its neighbour's motion) plus zero, then a +-1 texel refinement
         * of the winner, and at the finest level a +-0.5 one.
         */
        const val REFINE_FRAGMENT = MOTION_COMMON + """
uniform sampler2D uCoarse;
uniform vec2 uCoarseTexel;
uniform vec2 uCoarseScale;
uniform float uHalfStep;

vec2 parentAt(vec2 offset) {
  vec4 v = texture2D(uCoarse, vTexCoord + offset * uCoarseTexel);
  return (v.rg - 0.5) * SCALE * uCoarseScale;
}

void main() {
  vec2 anchor = parentAt(vec2(0.0));
  vec2 best = anchor;
  float bestCost = matchCost(anchor);
  vec2 candidates[5];
  candidates[0] = parentAt(vec2(1.0, 0.0));
  candidates[1] = parentAt(vec2(-1.0, 0.0));
  candidates[2] = parentAt(vec2(0.0, 1.0));
  candidates[3] = parentAt(vec2(0.0, -1.0));
  candidates[4] = vec2(0.0);
  for (int k = 0; k < 5; k++) {
    float c = matchCost(candidates[k]) + 0.003 * length(candidates[k] - anchor);
    if (c < bestCost) {
      bestCost = c;
      best = candidates[k];
    }
  }
  vec2 center = best;
  for (int j = -1; j <= 1; j++) {
    for (int i = -1; i <= 1; i++) {
      vec2 d = center + vec2(float(i), float(j));
      float c = matchCost(d) + 0.003 * length(d - anchor);
      if (c < bestCost) {
        bestCost = c;
        best = d;
      }
    }
  }
  if (uHalfStep > 0.5) {
    center = best;
    for (int j = -1; j <= 1; j++) {
      for (int i = -1; i <= 1; i++) {
        vec2 d = center + 0.5 * vec2(float(i), float(j));
        float c = matchCost(d) + 0.003 * length(d - anchor);
        if (c < bestCost) {
          bestCost = c;
          best = d;
        }
      }
    }
  }
  gl_FragColor = encode(best, bestCost);
}
"""

        const val COMPOSE_FRAGMENT = PRECISION + """
uniform sampler2D uPrev;
uniform sampler2D uCur;
uniform sampler2D uField;
uniform vec2 uFieldTexel;
varying vec2 vTexCoord;
const float SCALE = 40.0;
void main() {
  vec4 field = texture2D(uField, vTexCoord);
  vec2 d = (field.rg - 0.5) * SCALE * uFieldTexel;
  vec4 warped = mix(texture2D(uPrev, vTexCoord - d), texture2D(uCur, vTexCoord + d), 0.5);
  vec4 held = texture2D(uPrev, vTexCoord);
  float distrust = smoothstep(0.05, 0.12, field.b * 0.25);
  gl_FragColor = mix(warped, held, distrust);
}
"""
    }
}
