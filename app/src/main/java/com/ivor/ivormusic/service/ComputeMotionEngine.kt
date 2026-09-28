package com.ivor.ivormusic.service

import android.opengl.GLES20
import android.opengl.GLES30
import android.opengl.GLES31
import androidx.media3.common.GlTextureInfo
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Smooth motion's first-class engine, for GLES 3.1+ (compute shaders, shared
 * memory, image atomics): the method real-time frame generation uses
 * (FidelityFX FSR3's optical-flow path), adapted to video.
 *
 * **1. Motion in both directions, by block matching at full resolution.** A
 * luma pyramid from the frame itself (capped at a 1920 long side) down to
 * about 120 px. At every level, one workgroup per 8x8 block: candidates from
 * the parent block and its four neighbours (the way FidelityFX upscales its
 * flow), the previous pair's vector and no motion pick a centre; then an
 * exhaustive search around it held in shared memory (+-8 px on coarse levels,
 * +-4 at full resolution) and a parabolic sub-pixel fit. Done twice: A to B
 * (forward) and B to A (backward). Unlike a symmetric search at the midpoint,
 * two one-sided fields can say what is appearing and what is disappearing.
 *
 * **2. Cleaned and cross-checked.** Each level's field goes through a 3x3
 * vector median (FidelityFX's filter: keeps a vector a neighbour really had).
 * At full resolution each forward vector is checked against the backward
 * field where it lands (and vice versa): content visible in both frames
 * agrees (f + b = 0), content being covered or uncovered does not. That
 * agreement, times how well the block matched, is each vector's confidence.
 *
 * **3. Splatted to the output moment.** For each drawn tick, every vector is
 * pushed to where its content is at that moment (FSR3's approach), with an
 * atomic max on a packed (confidence, speed, source index) key in a shader
 * storage buffer (buffer atomics are core in ES 3.1; image atomics need ES
 * 3.2 or an extension, so they are not used), so where two
 * contents land on one spot the one visible in both frames - the foreground -
 * wins. Forward vectors carry A's content, backward vectors B's.
 *
 * **4. Occlusion-aware resolve and compose.** Per splat texel, the landed
 * candidates (and their neighbours, which fill small holes) are scored by how
 * well A and B agree along them, with a vector that only one frame supports
 * scored as an occlusion rather than a mismatch; the winner carries how
 * visible its content is in each frame. Per output pixel, the compose pass
 * picks among the resolved vectors around it and blends A and B by time and
 * visibility: content being covered comes from A only, content being revealed
 * from B only. Holes and poor matches fade to a plain blend; a scene cut
 * shows the nearer real frame.
 *
 * **GLSL ES 3.10, checked with glslangValidator** (the SDK's copy under
 * emulator/lib64/vulkan; `.probe/interp/fi/validate.py`). Images are
 * write-only (ES 3.1 allows no other access for these formats), and the
 * textures this engine writes as images are immutable (glTexStorage2D), which
 * image binding requires. Programs are this engine's own rather than Media3's
 * GlProgram, which has no compute stage.
 *
 * Any failure (a driver rejecting a shader, a GL error) throws, and the host
 * falls back to [FragmentMotionEngine].
 */
@UnstableApi
internal class ComputeMotionEngine(private val control: FrameInterpolationControl) : MotionEngine {

    override val name = "compute (GLES 3.1)"

    private val programs = ArrayList<EsProgram>()
    private lateinit var lumaProgram: EsProgram
    private lateinit var searchProgram: EsProgram
    private lateinit var medianProgram: EsProgram
    private lateinit var consistencyProgram: EsProgram
    private lateinit var cutProgram: EsProgram
    private lateinit var clearProgram: EsProgram
    private lateinit var splatProgram: EsProgram
    private lateinit var resolveProgram: EsProgram
    private lateinit var composeProgram: EsProgram

    private val textures = ArrayList<Tex>()
    private var levels: List<Size> = emptyList()
    private var fieldSizes: List<Size> = emptyList()

    private var lumaRef: Array<Tex> = emptyArray()
    private var lumaCur: Array<Tex> = emptyArray()
    private var rawF: Array<Tex> = emptyArray()
    private var rawB: Array<Tex> = emptyArray()
    private var filtF: Array<Tex> = emptyArray()
    private var filtB: Array<Tex> = emptyArray()
    private var finalF: Tex? = null
    private var finalB: Tex? = null
    private var prevFinalF: Tex? = null
    private var prevFinalB: Tex? = null
    /** Shader storage buffers of packed splat keys, one uint per splat texel. */
    private var splatBufferA = 0
    private var splatBufferB = 0
    private var splatSize = Size(1, 1)
    private var resolved: Tex? = null
    private var cut: Tex? = null
    private var splatStep = 4
    private var hasPrevious = false

    private val readback: ByteBuffer = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())
    private val quad: FloatBuffer = ByteBuffer
        .allocateDirect(QUAD.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply { put(QUAD); position(0) }

    override fun build() {
        lumaProgram = EsProgram(VERTEX, LUMA).also(programs::add)
        searchProgram = EsProgram(null, SEARCH).also(programs::add)
        medianProgram = EsProgram(null, MEDIAN).also(programs::add)
        consistencyProgram = EsProgram(null, CONSISTENCY).also(programs::add)
        cutProgram = EsProgram(null, CUT).also(programs::add)
        clearProgram = EsProgram(null, CLEAR).also(programs::add)
        splatProgram = EsProgram(null, SPLAT).also(programs::add)
        resolveProgram = EsProgram(null, RESOLVE).also(programs::add)
        composeProgram = EsProgram(VERTEX, COMPOSE).also(programs::add)
    }

    override fun configure(width: Int, height: Int) {
        releaseTextures()
        hasPrevious = false
        levels = ComputeMotionPlan.levels(width, height)
        fieldSizes = levels.map { ComputeMotionPlan.fieldSize(it) }
        val l0 = levels[0]
        lumaRef = Array(levels.size) { luma(levels[it]) }
        lumaCur = Array(levels.size) { luma(levels[it]) }
        rawF = Array(levels.size) { field(fieldSizes[it]) }
        rawB = Array(levels.size) { field(fieldSizes[it]) }
        filtF = Array(levels.size) { field(fieldSizes[it]) }
        filtB = Array(levels.size) { field(fieldSizes[it]) }
        finalF = field(fieldSizes[0])
        finalB = field(fieldSizes[0])
        prevFinalF = field(fieldSizes[0])
        prevFinalB = field(fieldSizes[0])
        splatStep = ComputeMotionPlan.splatStep(l0)
        splatSize = ComputeMotionPlan.splatSize(l0, splatStep)
        splatBufferA = storageBuffer(splatSize.w * splatSize.h)
        splatBufferB = storageBuffer(splatSize.w * splatSize.h)
        resolved = storage(splatSize, GLES30.GL_RGBA16F, linear = true, withFbo = false)
        cut = storage(Size(1, 1), GLES30.GL_RGBA8, linear = false, withFbo = true)
        GlUtil.checkGlError()
    }

    override fun prepareReference(input: GlTextureInfo) = buildLuma(input, lumaRef)

    override fun estimate(current: GlTextureInfo) {
        buildLuma(current, lumaCur)
        // Last pair's final fields become this pair's temporal candidates.
        finalF = prevFinalF.also { prevFinalF = finalF }
        finalB = prevFinalB.also { prevFinalB = finalB }
        searchDirection(lumaRef, lumaCur, rawF, filtF, prevFinalF!!)
        searchDirection(lumaCur, lumaRef, rawB, filtB, prevFinalB!!)
        consistency(filtF[0], filtB[0], finalF!!)
        consistency(filtB[0], filtF[0], finalB!!)
        dispatchCut(rawF[levels.lastIndex], finalF!!)
        hasPrevious = true
    }

    override fun promoteCurrent() {
        lumaRef = lumaCur.also { lumaCur = lumaRef }
    }

    override fun compose(reference: GlTextureInfo, current: GlTextureInfo, target: GlTextureInfo, phase: Float) {
        val t = phase.coerceIn(0f, 1f)
        clearSplat(splatBufferA)
        clearSplat(splatBufferB)
        splat(finalF!!, t, splatBufferA)
        splat(finalB!!, 1f - t, splatBufferB)
        resolve(t)
        drawCompose(reference, current, target, t)
    }

    override fun forgetHistory() {
        hasPrevious = false
    }

    override fun sampleUnmatched(): Float {
        val cutTex = cut ?: return -1f
        GlUtil.focusFramebufferUsingCurrentContext(cutTex.fbo, 1, 1)
        readback.clear()
        GLES20.glReadPixels(0, 0, 1, 1, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, readback)
        GlUtil.checkGlError()
        return (readback.get(0).toInt() and 0xFF) / 255f
    }

    override fun release() {
        programs.forEach { it.delete() }
        programs.clear()
        releaseTextures()
    }

    // ------------------------------------------------------------------ passes

    private fun buildLuma(source: GlTextureInfo, pyramid: Array<Tex>) {
        drawLuma(source.texId, fromLuma = false, pyramid[0])
        for (k in 1 until pyramid.size) drawLuma(pyramid[k - 1].id, fromLuma = true, pyramid[k])
    }

    private fun drawLuma(sourceTex: Int, fromLuma: Boolean, target: Tex) {
        val p = lumaProgram
        GlUtil.focusFramebufferUsingCurrentContext(target.fbo, target.size.w, target.size.h)
        p.use()
        p.sampler("uTex", 0, sourceTex)
        p.int("uFromLuma", if (fromLuma) 1 else 0)
        p.vec2("uDstTexel", 1f / target.size.w, 1f / target.size.h)
        drawQuad(p)
    }

    /** Coarsest level first; each level searches around its parent's vectors, then is median-filtered. */
    private fun searchDirection(
        src: Array<Tex>,
        dst: Array<Tex>,
        raw: Array<Tex>,
        filtered: Array<Tex>,
        temporal: Tex
    ) {
        val l0 = levels[0]
        val temporalPx = fieldSizes[0].let { floatArrayOf(it.w * 8f, it.h * 8f) }
        for (k in levels.indices.reversed()) {
            val hasCoarse = k + 1 < levels.size
            val p = searchProgram
            p.use()
            p.sampler("uSrc", 0, src[k].id)
            p.sampler("uDst", 1, dst[k].id)
            p.sampler("uCoarse", 2, if (hasCoarse) filtered[k + 1].id else temporal.id)
            p.sampler("uTemporal", 3, temporal.id)
            p.int("uHasCoarse", if (hasCoarse) 1 else 0)
            p.int("uHasTemporal", if (hasPrevious) 1 else 0)
            p.int("uRadius", ComputeMotionPlan.radius(k, levels.size, l0))
            p.float("uTemporalScale", levels[k].w.toFloat() / l0.w)
            p.vec2("uTemporalPx", temporalPx[0], temporalPx[1])
            p.float("uLambda", LAMBDA)
            p.image(0, raw[k], GLES31.GL_WRITE_ONLY, GLES30.GL_RGBA16F)
            dispatch(fieldSizes[k].w, fieldSizes[k].h)

            val m = medianProgram
            m.use()
            m.sampler("uField", 0, raw[k].id)
            m.image(0, filtered[k], GLES31.GL_WRITE_ONLY, GLES30.GL_RGBA16F)
            dispatch(groups(fieldSizes[k].w), groups(fieldSizes[k].h))
        }
    }

    private fun consistency(field: Tex, other: Tex, out: Tex) {
        val p = consistencyProgram
        p.use()
        p.sampler("uField", 0, field.id)
        p.sampler("uOther", 1, other.id)
        p.vec2("uFieldPx", field.size.w * 8f, field.size.h * 8f)
        p.image(0, out, GLES31.GL_WRITE_ONLY, GLES30.GL_RGBA16F)
        dispatch(groups(field.size.w), groups(field.size.h))
    }

    private fun dispatchCut(coarsest: Tex, confidence: Tex) {
        val p = cutProgram
        p.use()
        p.sampler("uField", 0, coarsest.id)
        p.sampler("uConfidence", 1, confidence.id)
        p.image(0, cut!!, GLES31.GL_WRITE_ONLY, GLES30.GL_RGBA8)
        dispatch(1, 1)
    }

    private fun clearSplat(buffer: Int) {
        val count = splatSize.w * splatSize.h
        val p = clearProgram
        p.use()
        p.int("uCount", count)
        GLES31.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, 0, buffer)
        dispatch((count + 63) / 64, 1)
    }

    private fun splat(field: Tex, time: Float, buffer: Int) {
        val p = splatProgram
        p.use()
        p.sampler("uField", 0, field.id)
        p.vec2("uFieldPx", field.size.w * 8f, field.size.h * 8f)
        p.float("uTime", time)
        p.float("uStep", splatStep.toFloat())
        p.ivec2("uSplatSize", splatSize.w, splatSize.h)
        GLES31.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, 0, buffer)
        dispatch(groups(splatSize.w), groups(splatSize.h))
    }

    private fun resolve(phase: Float) {
        val p = resolveProgram
        val out = resolved!!
        p.use()
        GLES31.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, 0, splatBufferA)
        GLES31.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, 1, splatBufferB)
        p.ivec2("uSplatSize", splatSize.w, splatSize.h)
        p.sampler("uFieldF", 2, finalF!!.id)
        p.sampler("uFieldB", 3, finalB!!.id)
        p.sampler("uLumaA", 4, lumaRef[0].id)
        p.sampler("uLumaB", 5, lumaCur[0].id)
        p.vec2("uFieldPx", fieldSizes[0].w * 8f, fieldSizes[0].h * 8f)
        p.vec2("uLumaPx", levels[0].w.toFloat(), levels[0].h.toFloat())
        p.float("uStep", splatStep.toFloat())
        p.float("uPhase", phase)
        p.image(0, out, GLES31.GL_WRITE_ONLY, GLES30.GL_RGBA16F)
        dispatch(groups(out.size.w), groups(out.size.h))
    }

    private fun drawCompose(reference: GlTextureInfo, current: GlTextureInfo, target: GlTextureInfo, phase: Float) {
        val p = composeProgram
        val res = resolved!!
        GlUtil.focusFramebufferUsingCurrentContext(target.fboId, target.width, target.height)
        p.use()
        p.sampler("uPrev", 0, reference.texId)
        p.sampler("uCur", 1, current.texId)
        p.sampler("uResolved", 2, res.id)
        p.sampler("uCut", 3, cut!!.id)
        p.vec2("uLevel0Px", levels[0].w.toFloat(), levels[0].h.toFloat())
        p.vec2("uResolvedPx", res.size.w * splatStep.toFloat(), res.size.h * splatStep.toFloat())
        p.float("uPhase", phase)
        drawQuad(p)
    }

    private fun dispatch(x: Int, y: Int) {
        GLES31.glDispatchCompute(max(1, x), max(1, y), 1)
        GLES31.glMemoryBarrier(GLES31.GL_ALL_BARRIER_BITS)
        GlUtil.checkGlError()
    }

    private fun drawQuad(program: EsProgram) {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        val location = program.attribute("aFramePosition")
        quad.position(0)
        GLES20.glVertexAttribPointer(location, 4, GLES20.GL_FLOAT, false, 0, quad)
        GLES20.glEnableVertexAttribArray(location)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(location)
        GlUtil.checkGlError()
    }

    // ----------------------------------------------------------------- textures

    private fun luma(size: Size) = storage(size, GLES30.GL_R8, linear = true, withFbo = true)

    private fun field(size: Size) = storage(size, GLES30.GL_RGBA16F, linear = true, withFbo = false)

    /** Immutable storage, which image binding requires; integer formats must be NEAREST to be complete. */
    private fun storage(size: Size, format: Int, linear: Boolean, withFbo: Boolean): Tex {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        val id = ids[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id)
        GLES30.glTexStorage2D(GLES20.GL_TEXTURE_2D, 1, format, size.w, size.h)
        val filter = if (linear) GLES20.GL_LINEAR else GLES20.GL_NEAREST
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, filter)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, filter)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        var fbo = 0
        if (withFbo) {
            GLES20.glGenFramebuffers(1, ids, 0)
            fbo = ids[0]
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo)
            GLES20.glFramebufferTexture2D(
                GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, id, 0
            )
            val status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
            check(status == GLES20.GL_FRAMEBUFFER_COMPLETE) { "Framebuffer incomplete: $status" }
        }
        GlUtil.checkGlError()
        return Tex(id, fbo, size).also(textures::add)
    }

    private fun storageBuffer(uints: Int): Int {
        val ids = IntArray(1)
        GLES20.glGenBuffers(1, ids, 0)
        GLES20.glBindBuffer(GLES31.GL_SHADER_STORAGE_BUFFER, ids[0])
        GLES20.glBufferData(GLES31.GL_SHADER_STORAGE_BUFFER, uints * 4, null, GLES30.GL_DYNAMIC_COPY)
        GLES20.glBindBuffer(GLES31.GL_SHADER_STORAGE_BUFFER, 0)
        GlUtil.checkGlError()
        return ids[0]
    }

    private fun releaseTextures() {
        for (tex in textures) {
            if (tex.fbo != 0) GLES20.glDeleteFramebuffers(1, intArrayOf(tex.fbo), 0)
            GLES20.glDeleteTextures(1, intArrayOf(tex.id), 0)
        }
        textures.clear()
        for (buffer in intArrayOf(splatBufferA, splatBufferB)) {
            if (buffer != 0) GLES20.glDeleteBuffers(1, intArrayOf(buffer), 0)
        }
        splatBufferA = 0
        splatBufferB = 0
        lumaRef = emptyArray()
        lumaCur = emptyArray()
        rawF = emptyArray()
        rawB = emptyArray()
        filtF = emptyArray()
        filtB = emptyArray()
        finalF = null
        finalB = null
        prevFinalF = null
        prevFinalB = null
        resolved = null
        cut = null
    }

    private fun groups(n: Int) = (n + 7) / 8

    internal data class Size(val w: Int, val h: Int)

    private data class Tex(val id: Int, val fbo: Int, val size: Size)

    /** A program of this engine's own: vertex + fragment, or compute alone. */
    private class EsProgram(vertex: String?, source: String) {
        private val id = GLES20.glCreateProgram()
        private val uniforms = HashMap<String, Int>()

        init {
            val shaders = if (vertex == null) {
                listOf(compile(GLES31.GL_COMPUTE_SHADER, source))
            } else {
                listOf(compile(GLES20.GL_VERTEX_SHADER, vertex), compile(GLES20.GL_FRAGMENT_SHADER, source))
            }
            shaders.forEach { GLES20.glAttachShader(id, it) }
            GLES20.glLinkProgram(id)
            shaders.forEach { GLES20.glDeleteShader(it) }
            val status = IntArray(1)
            GLES20.glGetProgramiv(id, GLES20.GL_LINK_STATUS, status, 0)
            check(status[0] == GLES20.GL_TRUE) { "Link failed: ${GLES20.glGetProgramInfoLog(id)}" }
        }

        fun use() = GLES20.glUseProgram(id)

        /** -1 for a uniform the driver optimised away, which GL then ignores. */
        private fun location(name: String) = uniforms.getOrPut(name) { GLES20.glGetUniformLocation(id, name) }

        fun attribute(name: String): Int {
            val location = GLES20.glGetAttribLocation(id, name)
            check(location >= 0) { "Missing attribute $name" }
            return location
        }

        fun int(name: String, value: Int) = GLES20.glUniform1i(location(name), value)
        fun float(name: String, value: Float) = GLES20.glUniform1f(location(name), value)
        fun vec2(name: String, x: Float, y: Float) = GLES20.glUniform2f(location(name), x, y)
        fun ivec2(name: String, x: Int, y: Int) = GLES20.glUniform2i(location(name), x, y)

        fun sampler(name: String, unit: Int, texture: Int) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + unit)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
            GLES20.glUniform1i(location(name), unit)
        }

        fun image(unit: Int, texture: Tex, access: Int, format: Int) =
            GLES31.glBindImageTexture(unit, texture.id, 0, false, 0, access, format)

        fun delete() = GLES20.glDeleteProgram(id)

        private fun compile(type: Int, source: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
            val status = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
            if (status[0] != GLES20.GL_TRUE) {
                val log = GLES20.glGetShaderInfoLog(shader)
                GLES20.glDeleteShader(shader)
                error("Shader compile failed: $log")
            }
            return shader
        }
    }

    private companion object {
        /** SAD units (sum over a block of 0-1 luma differences) charged per px of departure from the prediction. */
        const val LAMBDA = 0.3f

        val QUAD = floatArrayOf(
            -1f, -1f, 0f, 1f,
            1f, -1f, 0f, 1f,
            -1f, 1f, 0f, 1f,
            1f, 1f, 0f, 1f,
        )

        const val HEADER = "#version 310 es\n"

        const val COMPUTE_PRECISION = """
precision highp float;
precision highp int;
precision highp sampler2D;
precision highp usampler2D;
precision highp image2D;
precision highp uimage2D;
"""

        const val FRAGMENT_PRECISION = """
precision highp float;
precision highp int;
precision highp sampler2D;
"""

        const val VERTEX = HEADER + """
in vec4 aFramePosition;
out vec2 vTexCoord;
void main() {
  gl_Position = aFramePosition;
  vTexCoord = aFramePosition.xy * 0.5 + 0.5;
}
"""

        /**
         * Luma at the target size from four bilinear taps a quarter texel
         * out: a 2x2 box for a halving, a light filter at the same size.
         */
        const val LUMA = HEADER + FRAGMENT_PRECISION + """
uniform sampler2D uTex;
uniform int uFromLuma;
uniform vec2 uDstTexel;
in vec2 vTexCoord;
out vec4 outColor;

float lumaAt(vec2 uv) {
  vec4 c = texture(uTex, uv);
  return uFromLuma == 1 ? c.r : dot(c.rgb, vec3(0.299, 0.587, 0.114));
}

void main() {
  vec2 o = 0.25 * uDstTexel;
  float s = lumaAt(vTexCoord + vec2(-o.x, -o.y)) + lumaAt(vTexCoord + vec2(o.x, -o.y))
      + lumaAt(vTexCoord + vec2(-o.x, o.y)) + lumaAt(vTexCoord + vec2(o.x, o.y));
  outColor = vec4(s * 0.25, 0.0, 0.0, 1.0);
}
"""

        /**
         * One workgroup per 8x8 block of uSrc, searching uDst. Output: xy the
         * vector in this level's px (uSrc position + v = uDst position), z
         * the best match's mean absolute luma difference, w 1.
         */
        const val SEARCH = HEADER + COMPUTE_PRECISION + """
layout(local_size_x = 8, local_size_y = 8) in;
uniform sampler2D uSrc;
uniform sampler2D uDst;
uniform sampler2D uCoarse;
uniform sampler2D uTemporal;
uniform int uHasCoarse;
uniform int uHasTemporal;
uniform int uRadius;
uniform float uTemporalScale;
uniform vec2 uTemporalPx;
uniform float uLambda;
layout(rgba16f, binding = 0) writeonly uniform highp image2D uOut;

const int BLOCK = 8;
const int MAX_RADIUS = 8;
const int WIN = BLOCK + 2 * MAX_RADIUS;

shared float sBlock[64];
shared float sWin[WIN * WIN];
shared vec2 sCand[7];
shared float sCandCost[7];
shared uint sBest;

float lumaAt(sampler2D tex, ivec2 p) {
  ivec2 size = textureSize(tex, 0);
  return texelFetch(tex, clamp(p, ivec2(0), size - 1), 0).r;
}

float sadDirect(ivec2 origin, ivec2 d) {
  float sad = 0.0;
  for (int i = 0; i < 64; i++) {
    sad += abs(sBlock[i] - lumaAt(uDst, origin + ivec2(i % 8, i / 8) + d));
  }
  return sad;
}

float sadWindow(ivec2 d) {
  float sad = 0.0;
  for (int y = 0; y < BLOCK; y++) {
    int row = (y + MAX_RADIUS + d.y) * WIN + MAX_RADIUS + d.x;
    for (int x = 0; x < BLOCK; x++) {
      sad += abs(sBlock[y * BLOCK + x] - sWin[row + x]);
    }
  }
  return sad;
}

vec2 coarseVector(ivec2 parent, ivec2 offset) {
  ivec2 size = textureSize(uCoarse, 0);
  return texelFetch(uCoarse, clamp(parent + offset, ivec2(0), size - 1), 0).xy * 2.0;
}

void main() {
  ivec2 block = ivec2(gl_WorkGroupID.xy);
  int li = int(gl_LocalInvocationIndex);
  ivec2 origin = block * BLOCK;
  sBlock[li] = lumaAt(uSrc, origin + ivec2(gl_LocalInvocationID.xy));
  if (li == 0) sBest = 0xFFFFFFFFu;
  memoryBarrierShared();
  barrier();

  vec2 centerPx = vec2(origin) + 4.0;
  ivec2 parent = block / 2;
  vec2 temporal = vec2(0.0);
  if (uHasTemporal == 1) {
    temporal = texture(uTemporal, (centerPx / uTemporalScale) / uTemporalPx).xy * uTemporalScale;
  }
  vec2 prediction = uHasCoarse == 1 ? coarseVector(parent, ivec2(0)) : temporal;

  if (li < 7) {
    vec2 candidate = vec2(0.0);
    if (li == 1) {
      candidate = prediction;
    } else if (li == 2) {
      candidate = temporal;
    } else if (li >= 3 && uHasCoarse == 1) {
      ivec2 offset = li == 3 ? ivec2(1, 0) : (li == 4 ? ivec2(-1, 0) : (li == 5 ? ivec2(0, 1) : ivec2(0, -1)));
      candidate = coarseVector(parent, offset);
    }
    candidate = floor(candidate + 0.5);
    sCand[li] = candidate;
    sCandCost[li] = sadDirect(origin, ivec2(candidate)) + uLambda * length(candidate - prediction);
  }
  memoryBarrierShared();
  barrier();

  int bestCandidate = 0;
  for (int k = 1; k < 7; k++) {
    if (sCandCost[k] < sCandCost[bestCandidate]) bestCandidate = k;
  }
  ivec2 center = ivec2(sCand[bestCandidate]);

  ivec2 windowOrigin = origin + center - ivec2(MAX_RADIUS);
  for (int k = li; k < WIN * WIN; k += 64) {
    sWin[k] = lumaAt(uDst, windowOrigin + ivec2(k % WIN, k / WIN));
  }
  memoryBarrierShared();
  barrier();

  int side = 2 * uRadius + 1;
  int count = side * side;
  for (int k = li; k < count; k += 64) {
    ivec2 d = ivec2(k % side, k / side) - ivec2(uRadius);
    float cost = sadWindow(d) + uLambda * length(vec2(center + d) - prediction);
    uint key = (uint(min(cost * 64.0, 4194303.0)) << 10) | uint(k);
    atomicMin(sBest, key);
  }
  memoryBarrierShared();
  barrier();

  if (li == 0) {
    int k = int(sBest & 1023u);
    ivec2 d = ivec2(k % side, k / side) - ivec2(uRadius);
    float s0 = sadWindow(d);
    vec2 sub = vec2(0.0);
    if (abs(d.x) < MAX_RADIUS) {
      float l = sadWindow(d - ivec2(1, 0));
      float r = sadWindow(d + ivec2(1, 0));
      float den = l - 2.0 * s0 + r;
      if (den > 0.0001) sub.x = clamp(0.5 * (l - r) / den, -0.5, 0.5);
    }
    if (abs(d.y) < MAX_RADIUS) {
      float u = sadWindow(d - ivec2(0, 1));
      float w = sadWindow(d + ivec2(0, 1));
      float den = u - 2.0 * s0 + w;
      if (den > 0.0001) sub.y = clamp(0.5 * (u - w) / den, -0.5, 0.5);
    }
    imageStore(uOut, block, vec4(vec2(center + d) + sub, s0 / 64.0, 1.0));
  }
}
"""

        /** 3x3 vector median: the neighbour vector with the least summed squared distance to the rest. */
        const val MEDIAN = HEADER + COMPUTE_PRECISION + """
layout(local_size_x = 8, local_size_y = 8) in;
uniform sampler2D uField;
layout(rgba16f, binding = 0) writeonly uniform highp image2D uOut;

void main() {
  ivec2 p = ivec2(gl_GlobalInvocationID.xy);
  ivec2 size = textureSize(uField, 0);
  if (p.x >= size.x || p.y >= size.y) return;
  vec4 center = texelFetch(uField, p, 0);
  vec2 v[9];
  for (int k = 0; k < 9; k++) {
    v[k] = texelFetch(uField, clamp(p + ivec2(k % 3 - 1, k / 3 - 1), ivec2(0), size - 1), 0).xy;
  }
  vec2 m = center.xy;
  float best = 0.0;
  for (int j = 0; j < 9; j++) {
    vec2 d = m - v[j];
    best += dot(d, d);
  }
  for (int i = 0; i < 9; i++) {
    float s = 0.0;
    for (int j = 0; j < 9; j++) {
      vec2 d = v[i] - v[j];
      s += dot(d, d);
    }
    if (s < best - 0.0001) {
      best = s;
      m = v[i];
    }
  }
  imageStore(uOut, p, vec4(m, center.z, center.w));
}
"""

        /**
         * Confidence of each full-resolution vector: how well the other
         * direction's field, where this vector lands, points back (content
         * visible in both frames agrees; content being covered or revealed
         * does not), times how well its block matched.
         */
        const val CONSISTENCY = HEADER + COMPUTE_PRECISION + """
layout(local_size_x = 8, local_size_y = 8) in;
uniform sampler2D uField;
uniform sampler2D uOther;
uniform vec2 uFieldPx;
layout(rgba16f, binding = 0) writeonly uniform highp image2D uOut;

void main() {
  ivec2 p = ivec2(gl_GlobalInvocationID.xy);
  ivec2 size = textureSize(uField, 0);
  if (p.x >= size.x || p.y >= size.y) return;
  vec4 f = texelFetch(uField, p, 0);
  vec2 center = (vec2(p) + 0.5) * 8.0;
  vec2 back = texture(uOther, (center + f.xy) / uFieldPx).xy;
  float err = length(f.xy + back);
  float agree = exp(-err * err * 0.125);
  float match = 1.0 - smoothstep(0.03, 0.1, f.z);
  imageStore(uOut, p, vec4(f.xy, f.z, agree * match));
}
"""

        /**
         * How much of the pair failed to match, 0 to 1; past half, a scene
         * cut. The larger of two shares: coarse blocks whose best match is
         * poor, and full-resolution vectors with low confidence (forward and
         * backward disagreeing). [verified September 2026] At a cut between
         * two Tears of Steel shots the coarse share was 54% - barely over -
         * while 99% of vectors had confidence under 0.25; ordinary footage,
         * the explosion at double motion included, stayed at or under 8%.
         * The confidence share is what catches cuts, and pairs drawn far
         * apart when a slow device drops frames, before the occlusion path
         * can build a collage out of two unrelated shots.
         */
        const val CUT = HEADER + COMPUTE_PRECISION + """
layout(local_size_x = 8, local_size_y = 8) in;
uniform sampler2D uField;
uniform sampler2D uConfidence;
layout(rgba8, binding = 0) writeonly uniform highp image2D uOut;
shared uint sBad;
shared uint sTotal;
shared uint sDoubtful;
shared uint sVectors;

void main() {
  int li = int(gl_LocalInvocationIndex);
  if (li == 0) {
    sBad = 0u;
    sTotal = 0u;
    sDoubtful = 0u;
    sVectors = 0u;
  }
  memoryBarrierShared();
  barrier();
  ivec2 size = textureSize(uField, 0);
  int n = size.x * size.y;
  uint bad = 0u;
  uint total = 0u;
  for (int k = li; k < n; k += 64) {
    float z = texelFetch(uField, ivec2(k % size.x, k / size.x), 0).z;
    bad += z > 0.08 ? 1u : 0u;
    total += 1u;
  }
  ivec2 confSize = textureSize(uConfidence, 0);
  int m = confSize.x * confSize.y;
  uint doubtful = 0u;
  uint vectors = 0u;
  for (int k = li; k < m; k += 64) {
    float w = texelFetch(uConfidence, ivec2(k % confSize.x, k / confSize.x), 0).w;
    doubtful += w < 0.25 ? 1u : 0u;
    vectors += 1u;
  }
  atomicAdd(sBad, bad);
  atomicAdd(sTotal, total);
  atomicAdd(sDoubtful, doubtful);
  atomicAdd(sVectors, vectors);
  memoryBarrierShared();
  barrier();
  if (li == 0) {
    float coarse = float(sBad) / max(1.0, float(sTotal));
    float confidence = float(sDoubtful) / max(1.0, float(sVectors));
    imageStore(uOut, ivec2(0), vec4(max(coarse, confidence), 0.0, 0.0, 1.0));
  }
}
"""

        /**
         * Pushes each vector, sampled on a grid uStep px apart, to where its
         * content is at the output moment (uTime of the way along it), onto
         * the 2x2 texels around that point. The key is packed so atomicMax
         * keeps the most confident, then fastest, content: 9 bits of
         * confidence, 5 of speed, 18 of source index + 1 (0 = empty).
         */
        const val SPLAT = HEADER + COMPUTE_PRECISION + """
layout(local_size_x = 8, local_size_y = 8) in;
uniform sampler2D uField;
uniform vec2 uFieldPx;
uniform float uTime;
uniform float uStep;
uniform ivec2 uSplatSize;
layout(std430, binding = 0) buffer Splat {
  uint cells[];
};

void main() {
  ivec2 s = ivec2(gl_GlobalInvocationID.xy);
  ivec2 size = uSplatSize;
  if (s.x >= size.x || s.y >= size.y) return;
  vec2 p = (vec2(s) + 0.5) * uStep;
  vec4 f = texture(uField, p / uFieldPx);
  vec2 target = (p + uTime * f.xy) / uStep - 0.5;
  uint priority = (uint(clamp(f.w, 0.0, 1.0) * 511.0) << 5) | uint(min(length(f.xy) * 0.25, 31.0));
  uint value = (priority << 18) | uint(s.y * size.x + s.x + 1);
  ivec2 base = ivec2(floor(target));
  for (int j = 0; j <= 1; j++) {
    for (int i = 0; i <= 1; i++) {
      ivec2 q = base + ivec2(i, j);
      if (q.x >= 0 && q.y >= 0 && q.x < size.x && q.y < size.y) {
        atomicMax(cells[q.y * size.x + q.x], value);
      }
    }
  }
}
"""

        /** Zeroes a splat buffer before a tick's splats. */
        const val CLEAR = HEADER + COMPUTE_PRECISION + """
layout(local_size_x = 64) in;
uniform int uCount;
layout(std430, binding = 0) writeonly buffer Target {
  uint cells[];
};

void main() {
  int i = int(gl_GlobalInvocationID.x);
  if (i < uCount) cells[i] = 0u;
}
"""

        /**
         * Per splat texel, the vectors that landed here and one texel around
         * (which fills small holes), from both directions, scored by how
         * well A and B agree along them; a vector only one frame supports is
         * scored as an occlusion. Output: xy the vector as A-to-B motion in
         * level-0 px, z and w how visible its content is in A and in B (0,0
         * where nothing landed).
         */
        const val RESOLVE = HEADER + COMPUTE_PRECISION + """
layout(local_size_x = 8, local_size_y = 8) in;
layout(std430, binding = 0) readonly buffer SplatA {
  uint cellsA[];
};
layout(std430, binding = 1) readonly buffer SplatB {
  uint cellsB[];
};
uniform ivec2 uSplatSize;
uniform sampler2D uFieldF;
uniform sampler2D uFieldB;
uniform sampler2D uLumaA;
uniform sampler2D uLumaB;
uniform vec2 uFieldPx;
uniform vec2 uLumaPx;
uniform float uStep;
uniform float uPhase;
layout(rgba16f, binding = 0) writeonly uniform highp image2D uOut;

const float OCCLUDED_COST = 0.05;

float lumaDiff(vec2 pa, vec2 pb) {
  return abs(texture(uLumaA, pa / uLumaPx).r - texture(uLumaB, pb / uLumaPx).r);
}

float patchError(vec2 x, vec2 f) {
  vec2 pa = x - uPhase * f;
  vec2 pb = x + (1.0 - uPhase) * f;
  float e = lumaDiff(pa, pb);
  e += lumaDiff(pa + vec2(2.0, 0.0), pb + vec2(2.0, 0.0));
  e += lumaDiff(pa - vec2(2.0, 0.0), pb - vec2(2.0, 0.0));
  e += lumaDiff(pa + vec2(0.0, 2.0), pb + vec2(0.0, 2.0));
  e += lumaDiff(pa - vec2(0.0, 2.0), pb - vec2(0.0, 2.0));
  return e * 0.2;
}

void consider(uint value, bool fromA, ivec2 size, vec2 x, inout float bestScore, inout vec4 best) {
  if (value == 0u) return;
  int index = int(value & 0x3FFFFu) - 1;
  vec2 p = (vec2(index % size.x, index / size.x) + 0.5) * uStep;
  vec4 v;
  if (fromA) {
    v = texture(uFieldF, p / uFieldPx);
  } else {
    v = texture(uFieldB, p / uFieldPx);
  }
  vec2 f = fromA ? v.xy : -v.xy;
  float conf = clamp(v.w, 0.0, 1.0);
  float score = mix(patchError(x, f), OCCLUDED_COST, 1.0 - conf);
  if (score < bestScore) {
    bestScore = score;
    best = vec4(f, fromA ? 1.0 : conf, fromA ? conf : 1.0);
  }
}

void main() {
  ivec2 s = ivec2(gl_GlobalInvocationID.xy);
  ivec2 size = uSplatSize;
  if (s.x >= size.x || s.y >= size.y) return;
  vec2 x = (vec2(s) + 0.5) * uStep;
  float bestScore = 1000000.0;
  vec4 best = vec4(0.0);
  for (int k = 0; k < 5; k++) {
    ivec2 offset = k == 0 ? ivec2(0) : (k == 1 ? ivec2(1, 0) : (k == 2 ? ivec2(-1, 0) : (k == 3 ? ivec2(0, 1) : ivec2(0, -1))));
    ivec2 q = clamp(s + offset, ivec2(0), size - 1);
    int cell = q.y * size.x + q.x;
    consider(cellsA[cell], true, size, x, bestScore, best);
    consider(cellsB[cell], false, size, x, bestScore, best);
  }
  imageStore(uOut, s, best);
}
"""

        /**
         * Per output pixel: the resolved vector here (bilinear) and at the
         * four resolved texels around, whichever makes A and B agree best
         * (an occluded one scored as an occlusion), blended by time and by
         * how visible its content is in each frame. Holes and poor matches
         * fade to a plain blend; a scene cut shows the nearer real frame.
         */
        const val COMPOSE = HEADER + FRAGMENT_PRECISION + """
uniform sampler2D uPrev;
uniform sampler2D uCur;
uniform sampler2D uResolved;
uniform sampler2D uCut;
uniform vec2 uLevel0Px;
uniform vec2 uResolvedPx;
uniform float uPhase;
in vec2 vTexCoord;
out vec4 outColor;

const float OCCLUDED_COST = 0.05;
const float EDGE_BIAS = 0.02;

void consider(vec4 r, float bias, vec2 x, inout float bestScore, inout vec4 bestColor, inout float found) {
  if (r.z + r.w < 0.05) return;
  vec4 a = texture(uPrev, (x - uPhase * r.xy) / uLevel0Px);
  vec4 b = texture(uCur, (x + (1.0 - uPhase) * r.xy) / uLevel0Px);
  vec3 d = abs(a.rgb - b.rgb);
  float err = (d.r + d.g + d.b) / 3.0;
  float score = mix(err, OCCLUDED_COST, 1.0 - min(r.z, r.w)) + bias;
  if (score < bestScore) {
    float wa = (1.0 - uPhase) * r.z;
    float wb = uPhase * r.w;
    bestScore = score;
    bestColor = (wa * a + wb * b) / max(wa + wb, 0.0001);
    found = 1.0;
  }
}

void main() {
  vec4 prev = texture(uPrev, vTexCoord);
  vec4 cur = texture(uCur, vTexCoord);
  if (texture(uCut, vec2(0.5)).r > 0.5) {
    outColor = uPhase > 0.5 ? cur : prev;
    return;
  }
  vec2 x = vTexCoord * uLevel0Px;
  vec2 uv = x / uResolvedPx;
  ivec2 size = textureSize(uResolved, 0);
  ivec2 base = ivec2(floor(uv * vec2(size) - 0.5));
  vec4 blended = mix(prev, cur, uPhase);
  float bestScore = 1000000.0;
  vec4 bestColor = blended;
  float found = 0.0;
  consider(texture(uResolved, uv), 0.0, x, bestScore, bestColor, found);
  for (int k = 0; k < 4; k++) {
    ivec2 q = clamp(base + ivec2(k % 2, k / 2), ivec2(0), size - 1);
    consider(texelFetch(uResolved, q, 0), EDGE_BIAS, x, bestScore, bestColor, found);
  }
  float trust = found * (1.0 - smoothstep(0.08, 0.2, bestScore));
  outColor = mix(blended, bestColor, trust);
}
"""
    }
}

/** Sizes for [ComputeMotionEngine], kept pure for tests. */
internal object ComputeMotionPlan {
    /** The long side of the full-resolution level; 4K is matched at half size. */
    const val LEVEL0_LONG_SIDE = 1920

    /** Levels stop once the long side is at or under this, or at six. */
    const val COARSEST_LONG_SIDE = 120

    /** Splat texels are indexed in 18 bits, with 0 meaning empty. */
    const val MAX_SPLAT_TEXELS = (1 shl 18) - 1

    fun levels(width: Int, height: Int): List<ComputeMotionEngine.Size> {
        val longSide = max(width, height).coerceAtLeast(1)
        val scale = minOf(1f, LEVEL0_LONG_SIDE.toFloat() / longSide)
        var w = max(16, (width * scale).roundToInt())
        var h = max(16, (height * scale).roundToInt())
        val out = ArrayList<ComputeMotionEngine.Size>()
        out += ComputeMotionEngine.Size(w, h)
        while (out.size < 6 && max(w, h) > COARSEST_LONG_SIDE) {
            w = max(8, (w + 1) / 2)
            h = max(8, (h + 1) / 2)
            out += ComputeMotionEngine.Size(w, h)
        }
        return out
    }

    fun fieldSize(level: ComputeMotionEngine.Size) =
        ComputeMotionEngine.Size((level.w + 7) / 8, (level.h + 7) / 8)

    /** Full-resolution frames up to this many pixels (720p) get the wide search there too. */
    const val WIDE_LEVEL0_PIXELS = 1_000_000

    /**
     * Search radius in px. Coarse levels, where motion is found, always get
     * +-8. At full resolution the search is the most expensive pass (its cost
     * grows with the frame and the square of the radius), so +-8 up to 720p
     * and +-4 above, where the parent's vector is already within a few
     * pixels. [verified September 2026] On the scoring clips +-8 there was
     * worth 0.08 dB over +-4.
     */
    fun radius(level: Int, levelCount: Int, level0: ComputeMotionEngine.Size): Int =
        if (level == 0 && levelCount > 1 && level0.w.toLong() * level0.h > WIDE_LEVEL0_PIXELS) 4 else 8

    fun splatStep(level0: ComputeMotionEngine.Size): Int {
        var step = 4
        while (splatSize(level0, step).let { it.w * it.h } > MAX_SPLAT_TEXELS) step *= 2
        return step
    }

    fun splatSize(level0: ComputeMotionEngine.Size, step: Int) =
        ComputeMotionEngine.Size((level0.w + step - 1) / step, (level0.h + step - 1) / step)
}
