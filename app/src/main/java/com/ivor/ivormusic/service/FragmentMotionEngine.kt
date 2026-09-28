package com.ivor.ivormusic.service

import android.opengl.GLES20
import androidx.media3.common.GlTextureInfo
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Smooth motion's portable engine: GLSL ES 1.00 fragment passes only, so it
 * runs on any GPU Media3 can run, and it is what [ComputeMotionEngine] falls
 * back to.
 *
 * **Motion is estimated once per pair, symmetrically, at the midpoint.** For
 * each position p of the midpoint frame, the search looks for the offset d at
 * which A(p - d) matches B(p + d), so the vector field is expressed in the
 * midpoint's coordinates: no forward warp, no holes to fill. The search runs
 * coarse to fine over a three-level luma pyramid whose finest level has a
 * 320-texel long side ([FrameInterpolationPolicy.levelSizes]), so its cost does
 * not grow with the video's resolution. Per pair:
 * 1. every level matches 3x3 patches by zero-mean difference plus a quarter of
 *    the brightness offset, so a fade or a flash does not read as a mismatch;
 * 2. each level also tries the previous pair's vector at the same place (a
 *    temporal candidate), which keeps the field steady from pair to pair
 *    instead of re-deciding flat areas at random;
 * 3. the finest field goes through a 3x3 vector median (FidelityFX's filter),
 *    which removes the lone wrong vectors that otherwise show as a block
 *    moving the wrong way, and marks where neighbours disagree;
 * 4. a 1x1 pass counts how much of the coarsest level failed to match, which
 *    is how a scene cut is recognised without a readback.
 *
 * **The compose pass decides per pixel, never per block.** [scar, September
 * 2026] The first version faded to a repeated frame wherever the motion
 * search's block residual rose, so a failed block became a visible square.
 * Now each output pixel tries its projected vector and the four field texels
 * around it, keeps the one whose two samples agree best (so an object's edge
 * follows the pixels rather than the 6-pixel field grid), and where the field
 * is untrustworthy fades, through a bilinear mask, to a plain blend of the two
 * real frames. A scene cut shows the nearer real frame outright. For a phase
 * other than a half, the vector is looked up where the pixel's content sits at
 * the midpoint, one step of projecting the field forward.
 *
 * **Tuned against real frames, not by eye.** [verified September 2026] Scored
 * as PSNR of the drawn t=0.5 frame against the real frame between two frames
 * a pair apart (three Tears of Steel clips): the original engine 28.3 dB,
 * repeating A 27.2, blending A and B 30.0, this engine 30.8. The harness is
 * described in docs/playback-video.md.
 *
 * **GLSL ES 1.00 throughout.** Every uniform the code sets must be read by its
 * shader: `GlProgram` throws a NullPointerException, not a GL error, for one a
 * driver optimised away, and the host treats any exception as "fall back".
 */
@UnstableApi
internal class FragmentMotionEngine(private val control: FrameInterpolationControl) : MotionEngine {

    override val name = "fragment (GLES 2)"

    private var lumaProgram: GlProgram? = null
    private var searchProgram: GlProgram? = null
    private var refineProgram: GlProgram? = null
    private var medianProgram: GlProgram? = null
    private var cutProgram: GlProgram? = null
    private var composeProgram: GlProgram? = null

    /** Both luma pyramids, finest first. */
    private var referenceLevels: Array<GlTextureInfo>? = null
    private var currentLevels: Array<GlTextureInfo>? = null

    /**
     * This pair's vector fields and the previous pair's, finest first. Index 0
     * holds the median-filtered finest field once a pair is estimated; the
     * finest level's raw search result goes to [rawFineField] first.
     */
    private var fields: Array<GlTextureInfo>? = null
    private var previousFields: Array<GlTextureInfo>? = null
    private var rawFineField: GlTextureInfo? = null
    /** 1x1: the share of the coarsest level that found no good match. */
    private var cutTexture: GlTextureInfo? = null
    private var hasPreviousFields = false

    private val readback: ByteBuffer = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())

    override fun build() {
        lumaProgram = createProgram(LUMA_FRAGMENT)
        searchProgram = createProgram(SEARCH_FRAGMENT)
        refineProgram = createProgram(REFINE_FRAGMENT)
        medianProgram = createProgram(MEDIAN_FRAGMENT)
        cutProgram = createProgram(CUT_FRAGMENT)
        composeProgram = createProgram(COMPOSE_FRAGMENT)
    }

    override fun configure(width: Int, height: Int) {
        releaseTextures()
        hasPreviousFields = false
        val sizes = FrameInterpolationPolicy.levelSizes(width, height)
        referenceLevels = Array(sizes.size) { MotionGl.texture(sizes[it].first, sizes[it].second) }
        currentLevels = Array(sizes.size) { MotionGl.texture(sizes[it].first, sizes[it].second) }
        fields = Array(sizes.size) { MotionGl.texture(sizes[it].first, sizes[it].second) }
        previousFields = Array(sizes.size) { MotionGl.texture(sizes[it].first, sizes[it].second) }
        rawFineField = MotionGl.texture(sizes[0].first, sizes[0].second)
        cutTexture = MotionGl.texture(1, 1)
    }

    override fun prepareReference(input: GlTextureInfo) = buildPyramid(input, referenceLevels!!)

    /**
     * Coarsest level searched outright, each finer level refining its parent,
     * each seeded with the previous pair's vector; then the finest field's
     * median and the scene-cut count.
     */
    override fun estimate(current: GlTextureInfo) {
        buildPyramid(current, currentLevels!!)
        // Last pair's fields become this pair's temporal candidates.
        val last = fields!!
        fields = previousFields
        previousFields = last
        val prev = referenceLevels!!
        val cur = currentLevels!!
        val field = fields!!
        val temporal = previousFields!!
        val useTemporal = hasPreviousFields
        drawSearch(prev[2], cur[2], temporal[2], useTemporal, field[2])
        drawRefine(prev[1], cur[1], field[2], temporal[1], useTemporal, field[1], halfStep = false)
        drawRefine(prev[0], cur[0], field[1], temporal[0], useTemporal, rawFineField!!, halfStep = true)
        drawMedian(rawFineField!!, field[0])
        drawCut(field[2], cutTexture!!)
        hasPreviousFields = true
    }

    override fun promoteCurrent() {
        // The pyramid built for this pair is the next pair's reference; swap
        // rather than rebuild.
        val kept = currentLevels
        currentLevels = referenceLevels
        referenceLevels = kept
    }

    override fun compose(reference: GlTextureInfo, current: GlTextureInfo, target: GlTextureInfo, phase: Float) {
        val program = composeProgram!!
        val field = fields!![0]
        begin(program, target)
        program.setSamplerTexIdUniform("uPrev", reference.texId, 0)
        program.setSamplerTexIdUniform("uCur", current.texId, 1)
        program.setSamplerTexIdUniform("uField", field.texId, 2)
        program.setSamplerTexIdUniform("uCut", cutTexture!!.texId, 3)
        program.setFloatsUniform("uFieldTexel", MotionGl.texel(field))
        program.setFloatUniform("uPhase", phase.coerceIn(0f, 1f))
        finish(program)
    }

    override fun forgetHistory() {
        hasPreviousFields = false
    }

    override fun sampleUnmatched(): Float {
        val cut = cutTexture ?: return -1f
        GlUtil.focusFramebufferUsingCurrentContext(cut.fboId, 1, 1)
        readback.clear()
        GLES20.glReadPixels(0, 0, 1, 1, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, readback)
        GlUtil.checkGlError()
        return (readback.get(0).toInt() and 0xFF) / 255f
    }

    override fun release() {
        for (program in listOf(lumaProgram, searchProgram, refineProgram, medianProgram, cutProgram, composeProgram)) {
            try {
                program?.delete()
            } catch (e: GlUtil.GlException) {
                // Released with the context anyway.
            }
        }
        releaseTextures()
    }

    private fun releaseTextures() {
        referenceLevels?.forEach(MotionGl::releaseQuietly)
        currentLevels?.forEach(MotionGl::releaseQuietly)
        fields?.forEach(MotionGl::releaseQuietly)
        previousFields?.forEach(MotionGl::releaseQuietly)
        MotionGl.releaseQuietly(rawFineField)
        MotionGl.releaseQuietly(cutTexture)
        referenceLevels = null
        currentLevels = null
        fields = null
        previousFields = null
        rawFineField = null
        cutTexture = null
    }

    private fun buildPyramid(source: GlTextureInfo, levels: Array<GlTextureInfo>) {
        var from = source
        for (level in levels) {
            drawLuma(from, level)
            from = level
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

    private fun drawLuma(source: GlTextureInfo, target: GlTextureInfo) {
        val program = lumaProgram!!
        begin(program, target)
        program.setSamplerTexIdUniform("uTex", source.texId, 0)
        program.setFloatsUniform("uDstTexel", MotionGl.texel(target))
        finish(program)
    }

    private fun drawSearch(
        prev: GlTextureInfo,
        cur: GlTextureInfo,
        temporal: GlTextureInfo,
        useTemporal: Boolean,
        target: GlTextureInfo
    ) {
        val program = searchProgram!!
        begin(program, target)
        program.setSamplerTexIdUniform("uPrev", prev.texId, 0)
        program.setSamplerTexIdUniform("uCur", cur.texId, 1)
        program.setSamplerTexIdUniform("uTemporal", temporal.texId, 2)
        program.setFloatUniform("uUseTemporal", if (useTemporal) 1f else 0f)
        program.setFloatsUniform("uTexel", MotionGl.texel(target))
        finish(program)
    }

    private fun drawRefine(
        prev: GlTextureInfo,
        cur: GlTextureInfo,
        coarse: GlTextureInfo,
        temporal: GlTextureInfo,
        useTemporal: Boolean,
        target: GlTextureInfo,
        halfStep: Boolean
    ) {
        val program = refineProgram!!
        begin(program, target)
        program.setSamplerTexIdUniform("uPrev", prev.texId, 0)
        program.setSamplerTexIdUniform("uCur", cur.texId, 1)
        program.setSamplerTexIdUniform("uCoarse", coarse.texId, 2)
        program.setSamplerTexIdUniform("uTemporal", temporal.texId, 3)
        program.setFloatUniform("uUseTemporal", if (useTemporal) 1f else 0f)
        program.setFloatsUniform("uTexel", MotionGl.texel(target))
        program.setFloatsUniform("uCoarseTexel", MotionGl.texel(coarse))
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

    private fun drawMedian(source: GlTextureInfo, target: GlTextureInfo) {
        val program = medianProgram!!
        begin(program, target)
        program.setSamplerTexIdUniform("uField", source.texId, 0)
        program.setFloatsUniform("uTexel", MotionGl.texel(target))
        finish(program)
    }

    private fun drawCut(coarse: GlTextureInfo, target: GlTextureInfo) {
        val program = cutProgram!!
        begin(program, target)
        program.setSamplerTexIdUniform("uCoarse", coarse.texId, 0)
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

    private companion object {
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
         * stored as rg = d / 40 + 0.5 (range +-20). b is the match cost
         * times four, read by the scene-cut pass.
         *
         * The cost is a zero-mean difference over a 3x3 patch (each patch's
         * own mean removed, so a fade or a flash still matches) plus a quarter
         * of the brightness offset, so that among equal shapes the one that
         * also keeps its brightness still wins.
         */
        const val MOTION_COMMON = PRECISION + """
uniform sampler2D uPrev;
uniform sampler2D uCur;
uniform sampler2D uTemporal;
uniform float uUseTemporal;
uniform vec2 uTexel;
varying vec2 vTexCoord;
const float SCALE = 40.0;

float matchCost(vec2 d) {
  float a[9];
  float b[9];
  float meanA = 0.0;
  float meanB = 0.0;
  for (int k = 0; k < 9; k++) {
    int row = k / 3;
    vec2 o = vec2(float(k - row * 3) - 1.0, float(row) - 1.0) * uTexel;
    float va = texture2D(uPrev, vTexCoord + o - d * uTexel).r;
    float vb = texture2D(uCur, vTexCoord + o + d * uTexel).r;
    a[k] = va;
    b[k] = vb;
    meanA += va;
    meanB += vb;
  }
  float offset = (meanA - meanB) / 9.0;
  float sum = 0.0;
  for (int k = 0; k < 9; k++) {
    sum += abs(a[k] - b[k] - offset);
  }
  return sum / 9.0 + 0.25 * abs(offset);
}

vec2 decode(vec4 v) {
  return (v.rg - 0.5) * SCALE;
}

vec4 encode(vec2 d, float cost) {
  return vec4(clamp(d / SCALE + 0.5, 0.0, 1.0), clamp(cost * 4.0, 0.0, 1.0), 1.0);
}
"""

        /**
         * Exhaustive +-3 texel search at the coarsest level, plus the
         * previous pair's vector here. A small pull towards the previous
         * vector (or towards no motion, without one) keeps flat areas from
         * picking noise.
         */
        const val SEARCH_FRAGMENT = MOTION_COMMON + """
void main() {
  vec2 prior = uUseTemporal > 0.5 ? decode(texture2D(uTemporal, vTexCoord)) : vec2(0.0);
  vec2 best = vec2(0.0);
  float bestCost = matchCost(best) + 0.002 * length(best - prior);
  if (uUseTemporal > 0.5) {
    float c = matchCost(prior);
    if (c < bestCost) {
      bestCost = c;
      best = prior;
    }
  }
  for (int y = -3; y <= 3; y++) {
    for (int x = -3; x <= 3; x++) {
      vec2 d = vec2(float(x), float(y));
      float c = matchCost(d) + 0.002 * length(d - prior);
      if (c < bestCost) {
        bestCost = c;
        best = d;
      }
    }
  }
  gl_FragColor = encode(best, matchCost(best));
}
"""

        /**
         * The parent's vector here and at its four neighbours (so an edge can
         * take its neighbour's motion), no motion, and the previous pair's
         * vector here; then a +-1 texel refinement of the winner, and at the
         * finest level a +-0.5 one.
         */
        const val REFINE_FRAGMENT = MOTION_COMMON + """
uniform sampler2D uCoarse;
uniform vec2 uCoarseTexel;
uniform vec2 uCoarseScale;
uniform float uHalfStep;

vec2 parentAt(vec2 offset) {
  vec4 v = texture2D(uCoarse, vTexCoord + offset * uCoarseTexel);
  return decode(v) * uCoarseScale;
}

void main() {
  vec2 anchor = parentAt(vec2(0.0));
  vec2 best = anchor;
  float bestCost = matchCost(anchor);
  vec2 candidates[6];
  candidates[0] = parentAt(vec2(1.0, 0.0));
  candidates[1] = parentAt(vec2(-1.0, 0.0));
  candidates[2] = parentAt(vec2(0.0, 1.0));
  candidates[3] = parentAt(vec2(0.0, -1.0));
  candidates[4] = vec2(0.0);
  candidates[5] = uUseTemporal > 0.5 ? decode(texture2D(uTemporal, vTexCoord)) : anchor;
  for (int k = 0; k < 6; k++) {
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
  gl_FragColor = encode(best, matchCost(best));
}
"""

        /**
         * A 3x3 vector median of the finest field: the neighbour vector
         * closest to all the others (least summed squared distance, the
         * filter FidelityFX's optical flow uses). Unlike a per-component
         * median it only ever keeps a vector that was really found, so an
         * edge between two motions keeps one or the other, never a mixture.
         *
         * b becomes how little to trust this texel, 0 to 1: a high match
         * cost, or neighbours that disagree with the median in most
         * directions (the median of their distances from it, so a clean
         * edge between two motions - where most neighbours agree - is not
         * penalised, while debris, smoke and water, where no two agree, is).
         */
        const val MEDIAN_FRAGMENT = PRECISION + """
uniform sampler2D uField;
uniform vec2 uTexel;
varying vec2 vTexCoord;
const float SCALE = 40.0;

#define SORT1(a, b) { float u_ = a; a = min(u_, b); b = max(u_, b); }

void main() {
  vec4 center = texture2D(uField, vTexCoord);
  vec2 v[9];
  for (int k = 0; k < 9; k++) {
    int row = k / 3;
    vec2 o = vec2(float(k - row * 3) - 1.0, float(row) - 1.0) * uTexel;
    v[k] = texture2D(uField, vTexCoord + o).rg;
  }
  vec2 m = center.rg;
  float bestSum = 0.0;
  for (int j = 0; j < 9; j++) {
    vec2 dd = (m - v[j]) * SCALE;
    bestSum += dot(dd, dd);
  }
  for (int i = 0; i < 9; i++) {
    float s = 0.0;
    for (int j = 0; j < 9; j++) {
      vec2 dd = (v[i] - v[j]) * SCALE;
      s += dot(dd, dd);
    }
    if (s < bestSum - 0.001) {
      bestSum = s;
      m = v[i];
    }
  }

  float d0 = length(v[0] - m) * SCALE; float d1 = length(v[1] - m) * SCALE;
  float d2 = length(v[2] - m) * SCALE; float d3 = length(v[3] - m) * SCALE;
  float d4 = length(v[4] - m) * SCALE; float d5 = length(v[5] - m) * SCALE;
  float d6 = length(v[6] - m) * SCALE; float d7 = length(v[7] - m) * SCALE;
  float d8 = length(v[8] - m) * SCALE;
  SORT1(d1, d2) SORT1(d4, d5) SORT1(d7, d8)
  SORT1(d0, d1) SORT1(d3, d4) SORT1(d6, d7)
  SORT1(d1, d2) SORT1(d4, d5) SORT1(d7, d8)
  SORT1(d0, d3) SORT1(d5, d8) SORT1(d4, d7)
  SORT1(d3, d6) SORT1(d1, d4) SORT1(d2, d5)
  SORT1(d4, d7) SORT1(d4, d2) SORT1(d6, d4)
  SORT1(d4, d2)

  float cost = center.b * 0.25;
  float distrust = max(smoothstep(0.04, 0.09, cost), smoothstep(0.5, 1.5, d4));
  gl_FragColor = vec4(m, distrust, 1.0);
}
"""

        /**
         * The share of an 8x6 grid over the coarsest field whose best match
         * still cost more than a real match does. Past half, the pair is two
         * different shots.
         */
        const val CUT_FRAGMENT = PRECISION + """
uniform sampler2D uCoarse;
varying vec2 vTexCoord;
const float BAD_COST = 0.05;
void main() {
  float bad = 0.0;
  for (int j = 0; j < 6; j++) {
    for (int i = 0; i < 8; i++) {
      vec2 p = vec2((float(i) + 0.5) / 8.0, (float(j) + 0.5) / 6.0);
      bad += step(BAD_COST, texture2D(uCoarse, p).b * 0.25);
    }
  }
  gl_FragColor = vec4(bad / 48.0, 0.0, 0.0, 1.0);
}
"""

        /**
         * The frame at uPhase between A (0) and B (1).
         *
         * The field holds half the A-to-B motion as seen from the midpoint,
         * so the vector is first looked up where this pixel's content sits
         * at the midpoint. Then the pixel tries that (bilinear) vector and
         * the four field texels around it, and keeps whichever makes its two
         * samples agree best; the bilinear one carries a small bonus so
         * smooth motion stays smooth, and a texel's own vector wins at an
         * object's edge, where blending two motions would fit neither.
         *
         * Where the field itself is untrustworthy (the median pass's b:
         * debris, smoke, water, motion past the search range) the pixel fades
         * to a plain blend of the two real frames, through a bilinear mask so
         * the fade has no block edges. Measured against real middle frames, a
         * blend there beat snapping to the nearer frame, and leaning on the
         * nearer warped sample where a pixel's pair disagrees beat neither,
         * so neither is done. A scene cut shows the nearer real frame
         * outright.
         */
        const val COMPOSE_FRAGMENT = PRECISION + """
uniform sampler2D uPrev;
uniform sampler2D uCur;
uniform sampler2D uField;
uniform sampler2D uCut;
uniform vec2 uFieldTexel;
uniform float uPhase;
varying vec2 vTexCoord;
const float SCALE = 40.0;
const float CUT_FRACTION = 0.5;
const float EDGE_BIAS = 0.025;

vec2 motionAt(vec2 q) {
  return 2.0 * (texture2D(uField, q).rg - 0.5) * SCALE * uFieldTexel;
}

void consider(vec2 m, float bias, inout float bestErr, inout vec4 bestA, inout vec4 bestB) {
  vec4 a = texture2D(uPrev, vTexCoord - uPhase * m);
  vec4 b = texture2D(uCur, vTexCoord + (1.0 - uPhase) * m);
  vec3 diff = abs(a.rgb - b.rgb);
  float err = (diff.r + diff.g + diff.b) / 3.0 + bias;
  if (err < bestErr) {
    bestErr = err;
    bestA = a;
    bestB = b;
  }
}

void main() {
  if (texture2D(uCut, vec2(0.5)).r > CUT_FRACTION) {
    gl_FragColor = uPhase > 0.5 ? texture2D(uCur, vTexCoord) : texture2D(uPrev, vTexCoord);
    return;
  }
  vec2 q = vTexCoord + (0.5 - uPhase) * motionAt(vTexCoord);
  vec2 cell = (floor(q / uFieldTexel - 0.5) + 0.5) * uFieldTexel;

  float bestErr = 1.0e6;
  vec4 bestA = vec4(0.0);
  vec4 bestB = vec4(0.0);
  consider(motionAt(q), 0.0, bestErr, bestA, bestB);
  consider(motionAt(cell), EDGE_BIAS, bestErr, bestA, bestB);
  consider(motionAt(cell + vec2(uFieldTexel.x, 0.0)), EDGE_BIAS, bestErr, bestA, bestB);
  consider(motionAt(cell + vec2(0.0, uFieldTexel.y)), EDGE_BIAS, bestErr, bestA, bestB);
  consider(motionAt(cell + uFieldTexel), EDGE_BIAS, bestErr, bestA, bestB);
  vec4 warped = mix(bestA, bestB, uPhase);

  float fieldDistrust = texture2D(uField, q).b;
  vec4 blended = mix(texture2D(uPrev, vTexCoord), texture2D(uCur, vTexCoord), uPhase);
  gl_FragColor = mix(warped, blended, fieldDistrust);
}
"""
    }
}
