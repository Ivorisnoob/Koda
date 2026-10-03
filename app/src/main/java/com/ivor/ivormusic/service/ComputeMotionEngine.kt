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
 * Smooth motion's engine (GLES 3.2 GPUs only). The frame it draws is SVP's: the
 * rendering, masks and scene handling are a port of SmoothVideo Project's
 * `SVSmoothFps` as reimplemented in open-svpflow (`.probe/interp/open-svpflow`,
 * Apache-2.0): `svpflow-core/src/renderer.rs` (the renderer, whose output this
 * reproduces, and the coverage and magnitude masks), `svpflow-core/src/metadata.rs`
 * (scene classes), `svpflow-core/src/frame_math.rs` (phase re-timing) and
 * `svpflow2/src/core.rs` (how they combine per frame).
 *
 * **No per-pixel decisions.** Every output pixel is one formula over two
 * samples: frame A along the backward vector field (B to A) at t, frame B
 * along the forward field (A to B) at 1 - t, both fields read at the pixel's
 * own position, bilinearly between block centres. Algorithm 21 blends the two
 * by time, swapping a sample for the other where coverage masks say its
 * content is uncovered; algorithm 13, used when the pair is hard, clamps the
 * plain time blend between the two samples (`median3`), so a pixel can never
 * take a colour neither frame gives it. A per-block "bad area" mask fades
 * poorly matched blocks toward the plain blend. All of it varies smoothly
 * between block centres, which is why the picture goes soft rather than
 * breaking where motion is hard.
 *
 * **Per pair:** the motion search (below) gives both fields; FIELD restates
 * them as SVP's vectors - per block its vector, SAD (8-bit luma, SVP's score
 * scaling) and mean luma; PREP clamps vectors to the frame and turns scores
 * into the bad-area mask; SCENE sorts the pair into SVP's classes (0 fine,
 * 1-2 hard, 3 a cut) by the share of blocks whose brightness-weighted SAD
 * passes SVP's limits.
 *
 * **Per drawn tick:** PHASE applies the timing and class rules (every tick
 * of an easy pair is drawn at its own phase, a cut shows the nearer frame,
 * hard pairs switch to algorithm 13 and are re-timed toward real frames);
 * COVER splats each field's blocks to the output moment and counts how much
 * of every block cell is covered (SVP's `coverage_mask`); RENDER is the
 * kernel.
 *
 * **The motion search** is this engine's own for now (a luma pyramid of
 * normalised luma, 8x8 blocks matched over 12x12 windows, coarse to fine,
 * both directions, 3x3 vector median); its output is restated in SVP's terms
 * by FIELD, so a search that works like SVP's (svpflow1) can replace it
 * without touching the rest. The finest level is at most 320 px on its long
 * side whatever the video's size ([ComputeMotionPlan.LEVEL0_LONG_SIDE]), so
 * the search costs the same at 480p and 4K; only the coarsest level searches
 * wide, and the finer ones refine around their best candidate
 * ([ComputeMotionPlan.radius]).
 *
 * **Scheduling.** Dispatches that do not read each other's output are queued
 * together (the two search directions, FIELD's two fields, PREP with SCENE,
 * PHASE with the coverage clear, the two coverage planes), and each stage
 * ends in one memory barrier carrying only the bits the next stage needs
 * ([STAGE_BARRIER_BITS]). GL errors are checked once per call from the host,
 * not per dispatch, and the scene class the log reports is read back behind a
 * fence ([takeHardness]), never by waiting for the GPU.
 *
 * GLSL ES 3.10, checked with glslangValidator (`.probe/interp/fi/validate.py`)
 * and run against open-svpflow's own output by `.probe/scratch/sm/render.py`.
 * Images are write-only and on immutable (glTexStorage2D) textures, which
 * image binding requires; 32-bit float textures are NEAREST (float filtering
 * is an extension) and read with texelFetch.
 */
@UnstableApi
internal class ComputeMotionEngine(private val control: FrameInterpolationControl) : MotionEngine {

    override val name = "compute (GLES 3.2)"

    private val programs = ArrayList<EsProgram>()
    private lateinit var lumaProgram: EsProgram
    private lateinit var normalizeProgram: EsProgram
    private lateinit var searchProgram: EsProgram
    private lateinit var medianProgram: EsProgram
    private lateinit var fieldProgram: EsProgram
    private lateinit var prepProgram: EsProgram
    private lateinit var sceneProgram: EsProgram
    private lateinit var phaseProgram: EsProgram
    private lateinit var coverClearProgram: EsProgram
    private lateinit var coverSplatProgram: EsProgram
    private lateinit var coverFinishProgram: EsProgram
    private lateinit var renderProgram: EsProgram

    private val textures = ArrayList<Tex>()
    private var levels: List<Size> = emptyList()
    private var fieldSizes: List<Size> = emptyList()

    /** Per level: plain luma, before normalisation. */
    private var lumaScratch: Array<Tex> = emptyArray()
    /** Per level: (luma, normalised luma) of A and of B. */
    private var pyramidRef: Array<Tex> = emptyArray()
    private var pyramidCur: Array<Tex> = emptyArray()
    private var rawF: Array<Tex> = emptyArray()
    private var rawB: Array<Tex> = emptyArray()
    private var filtF: Array<Tex> = emptyArray()
    private var filtB: Array<Tex> = emptyArray()
    /** The previous pair's full-resolution fields, this pair's temporal candidates. */
    private var prevFieldF: Tex? = null
    private var prevFieldB: Tex? = null

    /** SVP's vectors per block: xy vector (level-0 px), z score, w mean luma. Forward on A's grid, backward on B's. */
    private var svpForward: Tex? = null
    private var svpBackward: Tex? = null
    /** Per motion cell: (backward x, forward y, forward x, backward y), clamped to the frame. */
    private var motion: Tex? = null
    /** Per motion cell: bad-area mask of the forward and backward vectors, 0-255. */
    private var magnitude: Tex? = null
    /** Per motion cell, per tick: (bad-area, coverage for A's sample, coverage for B's sample, 0), 0-255. */
    private var masks: Tex? = null
    /**
     * 1x1, per tick: (phase / 256, algorithm, show, class / 3). Two that
     * alternate, so a tick never overwrites the one the previous tick's RENDER
     * may still be reading: nothing orders that read before the next PHASE's
     * store except the barriers of the tick in between.
     */
    private var states: Array<Tex> = emptyArray()
    private var stateIndex = 0
    /** { int class; int show; int algorithm; int phase; } */
    private var stateBuffer = 0
    /** Two coverage accumulators of (motion + 2)^2 ints each. */
    private var coverBuffer = 0
    /** A copy of the scene class the CPU maps once [readbackFence] has signalled. */
    private var readbackBuffer = 0
    /** Behind the copy into [readbackBuffer]; 0 when no reading is in flight. */
    private var readbackFence = 0L
    private var grid = Size(1, 1)
    private var motionGrid = Size(1, 1)
    private var hasPrevious = false

    private val quad: FloatBuffer = ByteBuffer
        .allocateDirect(QUAD.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply { put(QUAD); position(0) }

    override fun build() {
        lumaProgram = EsProgram(VERTEX, LUMA).also(programs::add)
        normalizeProgram = EsProgram(VERTEX, NORMALIZE).also(programs::add)
        searchProgram = EsProgram(null, SEARCH).also(programs::add)
        medianProgram = EsProgram(null, MEDIAN).also(programs::add)
        fieldProgram = EsProgram(null, FIELD).also(programs::add)
        prepProgram = EsProgram(null, PREP).also(programs::add)
        sceneProgram = EsProgram(null, SCENE).also(programs::add)
        phaseProgram = EsProgram(null, PHASE).also(programs::add)
        coverClearProgram = EsProgram(null, COVER_CLEAR).also(programs::add)
        coverSplatProgram = EsProgram(null, COVER_SPLAT).also(programs::add)
        coverFinishProgram = EsProgram(null, COVER_FINISH).also(programs::add)
        renderProgram = EsProgram(VERTEX, RENDER).also(programs::add)
    }

    override fun configure(width: Int, height: Int) {
        releaseTextures()
        hasPrevious = false
        levels = ComputeMotionPlan.levels(width, height)
        fieldSizes = levels.map { ComputeMotionPlan.fieldSize(it) }
        lumaScratch = Array(levels.size) { storage(levels[it], GLES30.GL_R8, linear = true, withFbo = true) }
        pyramidRef = Array(levels.size) { storage(levels[it], GLES30.GL_RG8, linear = true, withFbo = true) }
        pyramidCur = Array(levels.size) { storage(levels[it], GLES30.GL_RG8, linear = true, withFbo = true) }
        rawF = Array(levels.size) { field(fieldSizes[it]) }
        rawB = Array(levels.size) { field(fieldSizes[it]) }
        filtF = Array(levels.size) { field(fieldSizes[it]) }
        filtB = Array(levels.size) { field(fieldSizes[it]) }
        prevFieldF = field(fieldSizes[0])
        prevFieldB = field(fieldSizes[0])

        grid = fieldSizes[0]
        motionGrid = ComputeMotionPlan.motionGrid(levels[0], grid)
        svpForward = storage(grid, GLES30.GL_RGBA32F, linear = false, withFbo = false)
        svpBackward = storage(grid, GLES30.GL_RGBA32F, linear = false, withFbo = false)
        motion = storage(motionGrid, GLES30.GL_RGBA32F, linear = false, withFbo = false)
        magnitude = storage(motionGrid, GLES30.GL_RGBA16F, linear = false, withFbo = false)
        masks = storage(motionGrid, GLES30.GL_RGBA16F, linear = false, withFbo = false)
        states = Array(2) { storage(Size(1, 1), GLES30.GL_RGBA16F, linear = false, withFbo = false) }
        stateIndex = 0
        stateBuffer = storageBuffer(4)
        coverBuffer = storageBuffer(2 * (motionGrid.w + 2) * (motionGrid.h + 2))
        readbackBuffer = newReadbackBuffer()
        GlUtil.checkGlError()
    }

    override fun prepareReference(input: GlTextureInfo) {
        buildPyramid(input, pyramidRef)
        GlUtil.checkGlError()
    }

    override fun estimate(current: GlTextureInfo) {
        buildPyramid(current, pyramidCur)
        // Last pair's level-0 fields become this pair's temporal candidates.
        prevFieldF = filtF[0].also { filtF[0] = prevFieldF!! }
        prevFieldB = filtB[0].also { filtB[0] = prevFieldB!! }
        search()
        // Forward vectors belong to A's blocks, backward to B's.
        svpField(filtF[0], pyramidRef[0], pyramidCur[0], svpForward!!)
        svpField(filtB[0], pyramidCur[0], pyramidRef[0], svpBackward!!)
        barrier()
        prep()
        scene()
        barrier()
        GlUtil.checkGlError()
        hasPrevious = true
    }

    override fun promoteCurrent() {
        pyramidRef = pyramidCur.also { pyramidCur = pyramidRef }
    }

    override fun compose(
        reference: GlTextureInfo,
        current: GlTextureInfo,
        target: GlTextureInfo,
        phase: Float,
        step: Float
    ) {
        stateIndex = 1 - stateIndex
        val tickState = states[stateIndex]
        phase(phase.coerceIn(0f, 1f), step, tickState)
        clearCover()
        barrier()
        splatCover()
        barrier()
        finishCover()
        barrier()
        drawRender(reference, current, target, tickState)
        GlUtil.checkGlError()
    }

    override fun forgetHistory() {
        hasPrevious = false
    }

    /**
     * Copies the scene class SCENE just stored into [readbackBuffer] and fences
     * the copy; [takeHardness] maps it once the fence has signalled, so neither
     * call waits for the GPU. Nothing is started while a reading is in flight.
     */
    override fun requestHardness() {
        if (stateBuffer == 0 || readbackBuffer == 0 || readbackFence != 0L) return
        // The copy reads what a shader stored, which only this bit orders.
        GLES31.glMemoryBarrier(GLES31.GL_BUFFER_UPDATE_BARRIER_BIT)
        GLES20.glBindBuffer(GLES30.GL_COPY_READ_BUFFER, stateBuffer)
        GLES20.glBindBuffer(GLES30.GL_COPY_WRITE_BUFFER, readbackBuffer)
        GLES30.glCopyBufferSubData(GLES30.GL_COPY_READ_BUFFER, GLES30.GL_COPY_WRITE_BUFFER, 0, 0, 4)
        GLES20.glBindBuffer(GLES30.GL_COPY_READ_BUFFER, 0)
        GLES20.glBindBuffer(GLES30.GL_COPY_WRITE_BUFFER, 0)
        readbackFence = GLES30.glFenceSync(GLES30.GL_SYNC_GPU_COMMANDS_COMPLETE, 0)
        GlUtil.checkGlError()
    }

    /** The requested pair's SVP scene class over 3: 0 fine, 0.33 and 0.67 hard, 1 a cut; -1 until the GPU has got there. */
    override fun takeHardness(): Float {
        val fence = readbackFence
        if (fence == 0L) return -1f
        // A zero timeout asks and returns; the flush makes sure the fence is on its way.
        val status = GLES30.glClientWaitSync(fence, GLES30.GL_SYNC_FLUSH_COMMANDS_BIT, 0L)
        if (status == GLES30.GL_TIMEOUT_EXPIRED) return -1f
        GLES30.glDeleteSync(fence)
        readbackFence = 0L
        if (status == GLES30.GL_WAIT_FAILED) {
            GlUtil.checkGlError()
            return -1f
        }
        GLES20.glBindBuffer(GLES30.GL_COPY_WRITE_BUFFER, readbackBuffer)
        val mapped = GLES30.glMapBufferRange(GLES30.GL_COPY_WRITE_BUFFER, 0, 4, GLES30.GL_MAP_READ_BIT)
        val sceneClass = (mapped as? ByteBuffer)?.order(ByteOrder.nativeOrder())?.getInt(0) ?: -3
        if (mapped != null) GLES30.glUnmapBuffer(GLES30.GL_COPY_WRITE_BUFFER)
        GLES20.glBindBuffer(GLES30.GL_COPY_WRITE_BUFFER, 0)
        GlUtil.checkGlError()
        return sceneClass / 3f
    }

    override fun release() {
        programs.forEach { it.delete() }
        programs.clear()
        releaseTextures()
    }

    // ------------------------------------------------------------ motion search

    /** Luma at each level, then its normalised twin beside it. */
    private fun buildPyramid(source: GlTextureInfo, pyramid: Array<Tex>) {
        for (k in levels.indices) {
            val scratch = lumaScratch[k]
            val p = lumaProgram
            GlUtil.focusFramebufferUsingCurrentContext(scratch.fbo, scratch.size.w, scratch.size.h)
            p.use()
            p.sampler("uTex", 0, if (k == 0) source.texId else pyramid[k - 1].id)
            p.int("uFromLuma", if (k == 0) 0 else 1)
            p.vec2("uDstTexel", 1f / scratch.size.w, 1f / scratch.size.h)
            val ratio = if (k == 0) {
                max(source.width.toFloat() / scratch.size.w, source.height.toFloat() / scratch.size.h)
            } else {
                2f
            }
            p.int("uTaps", ComputeMotionPlan.lumaTaps(ratio))
            drawQuad(p)

            val n = normalizeProgram
            val target = pyramid[k]
            GlUtil.focusFramebufferUsingCurrentContext(target.fbo, target.size.w, target.size.h)
            n.use()
            n.sampler("uLuma", 0, scratch.id)
            n.vec2("uTexel", 1f / target.size.w, 1f / target.size.h)
            drawQuad(n)
        }
    }

    /**
     * Both directions, coarsest level first: each level searches around its
     * parent's vectors, then is median-filtered. The directions never read
     * each other's fields, so each stage queues both and waits once, which
     * also keeps the GPU busy on the coarse levels, where one direction is
     * only a few dozen workgroups.
     */
    private fun search() {
        val forwardTemporal = prevFieldF!!
        val backwardTemporal = prevFieldB!!
        for (k in levels.indices.reversed()) {
            searchLevel(k, pyramidRef, pyramidCur, rawF, filtF, forwardTemporal)
            searchLevel(k, pyramidCur, pyramidRef, rawB, filtB, backwardTemporal)
            barrier()
            median(rawF[k], filtF[k], fieldSizes[k])
            median(rawB[k], filtB[k], fieldSizes[k])
            barrier()
        }
    }

    /** Level [k] of one direction: [src]'s 8x8 blocks matched in [dst], one workgroup per block. */
    private fun searchLevel(
        k: Int,
        src: Array<Tex>,
        dst: Array<Tex>,
        raw: Array<Tex>,
        filtered: Array<Tex>,
        temporal: Tex
    ) {
        val l0 = levels[0]
        val hasCoarse = k + 1 < levels.size
        val p = searchProgram
        p.use()
        p.sampler("uSrc", 0, src[k].id)
        p.sampler("uDst", 1, dst[k].id)
        p.sampler("uCoarse", 2, if (hasCoarse) filtered[k + 1].id else temporal.id)
        p.sampler("uTemporal", 3, temporal.id)
        p.int("uHasCoarse", if (hasCoarse) 1 else 0)
        p.int("uHasTemporal", if (hasPrevious) 1 else 0)
        p.int("uRadius", ComputeMotionPlan.radius(k, levels.size))
        p.float("uTemporalScale", levels[k].w.toFloat() / l0.w)
        p.vec2("uTemporalPx", fieldSizes[0].w * 8f, fieldSizes[0].h * 8f)
        p.float("uLambda", LAMBDA)
        p.image(0, raw[k], GLES31.GL_WRITE_ONLY, GLES30.GL_RGBA16F)
        dispatch(fieldSizes[k].w, fieldSizes[k].h)
    }

    private fun median(raw: Tex, filtered: Tex, size: Size) {
        val m = medianProgram
        m.use()
        m.sampler("uField", 0, raw.id)
        m.image(0, filtered, GLES31.GL_WRITE_ONLY, GLES30.GL_RGBA16F)
        dispatch(groups(size.w), groups(size.h))
    }

    // ------------------------------------------------------------ SVP, per pair

    /** [field]'s level-0 vectors as SVP's: vector, score and mean luma of each block of [own] (its frame) against [other]. */
    private fun svpField(field: Tex, own: Tex, other: Tex, out: Tex) {
        val l0 = levels[0]
        val p = fieldProgram
        p.use()
        p.sampler("uField", 0, field.id)
        p.sampler("uOwn", 1, own.id)
        p.sampler("uOther", 2, other.id)
        p.vec2("uLumaPx", l0.w.toFloat(), l0.h.toFloat())
        p.float("uDiag", kotlin.math.sqrt((l0.w.toDouble() * l0.w + l0.h.toDouble() * l0.h)).toInt().toFloat())
        p.image(0, out, GLES31.GL_WRITE_ONLY, GLES30.GL_RGBA32F)
        dispatch(groups(grid.w), groups(grid.h))
    }

    private fun prep() {
        val l0 = levels[0]
        val p = prepProgram
        p.use()
        p.sampler("uForward", 0, svpForward!!.id)
        p.sampler("uBackward", 1, svpBackward!!.id)
        p.ivec2("uGrid", grid.w, grid.h)
        p.ivec2("uMotionGrid", motionGrid.w, motionGrid.h)
        p.vec2("uFramePx", l0.w.toFloat(), l0.h.toFloat())
        p.float("uBlock", BLOCK.toFloat())
        p.float("uStep", STEP.toFloat())
        p.float("uAreaScale", AREA_MASK / 15000f)
        p.image(0, motion!!, GLES31.GL_WRITE_ONLY, GLES30.GL_RGBA32F)
        p.image(1, magnitude!!, GLES31.GL_WRITE_ONLY, GLES30.GL_RGBA16F)
        dispatch(groups(motionGrid.w), groups(motionGrid.h))
    }

    private fun scene() {
        val p = sceneProgram
        p.use()
        p.sampler("uForward", 0, svpForward!!.id)
        p.sampler("uBackward", 1, svpBackward!!.id)
        p.ivec2("uGrid", grid.w, grid.h)
        p.float("uBlockArea", (BLOCK * BLOCK).toFloat())
        GLES31.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, 0, stateBuffer)
        dispatch(1, 1)
    }

    // ----------------------------------------------------------- SVP, per tick

    /**
     * [phase] is this tick's place between A and B, [step] the distance between
     * ticks in the same units: SVP's `frame_den / frame_num`, which its
     * re-timing rules are written in. Writes the tick's state into [out].
     */
    private fun phase(phase: Float, step: Float, out: Tex) {
        val p = phaseProgram
        p.use()
        p.float("uRawPhase", phase * 256f)
        p.float("uStep256", step * 256f)
        // SVP re-times hard pairs only when two or more output frames fall on each source frame.
        p.int("uAdaptive", if (step <= 0.5f + 1e-4f) 1 else 0)
        GLES31.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, 0, stateBuffer)
        p.image(0, out, GLES31.GL_WRITE_ONLY, GLES30.GL_RGBA16F)
        dispatch(1, 1)
    }

    /** Touches only the coverage buffer, so it runs beside PHASE. */
    private fun clearCover() {
        val cells = 2 * (motionGrid.w + 2) * (motionGrid.h + 2)
        val clear = coverClearProgram
        clear.use()
        clear.int("uCount", cells)
        GLES31.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, 1, coverBuffer)
        dispatch((cells + 63) / 64, 1)
    }

    /** Each plane adds into its own half of the accumulators, so the two run side by side. */
    private fun splatCover() {
        val splat = coverSplatProgram
        splat.use()
        splat.sampler("uForward", 0, svpForward!!.id)
        splat.sampler("uBackward", 1, svpBackward!!.id)
        splat.ivec2("uGrid", grid.w, grid.h)
        splat.ivec2("uMotionGrid", motionGrid.w, motionGrid.h)
        splat.int("uBlock", BLOCK)
        splat.int("uStep", STEP)
        splat.float("uCoverDivisor", COVER_DIVISOR)
        GLES31.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, 0, stateBuffer)
        GLES31.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, 1, coverBuffer)
        for (plane in 0..1) {
            splat.int("uPlane", plane)
            dispatch(groups(grid.w), groups(grid.h))
        }
    }

    private fun finishCover() {
        val finish = coverFinishProgram
        finish.use()
        finish.sampler("uMagnitude", 0, magnitude!!.id)
        finish.ivec2("uMotionGrid", motionGrid.w, motionGrid.h)
        finish.int("uArea", BLOCK * BLOCK)
        finish.float("uStrength", COVER_STRENGTH / 100f)
        GLES31.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, 0, stateBuffer)
        GLES31.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, 1, coverBuffer)
        finish.image(0, masks!!, GLES31.GL_WRITE_ONLY, GLES30.GL_RGBA16F)
        dispatch(groups(motionGrid.w), groups(motionGrid.h))
    }

    private fun drawRender(reference: GlTextureInfo, current: GlTextureInfo, target: GlTextureInfo, tickState: Tex) {
        val l0 = levels[0]
        val p = renderProgram
        GlUtil.focusFramebufferUsingCurrentContext(target.fboId, target.width, target.height)
        p.use()
        p.sampler("uPrev", 0, reference.texId)
        p.sampler("uCur", 1, current.texId)
        p.sampler("uMotion", 2, motion!!.id)
        p.sampler("uMasks", 3, masks!!.id)
        p.sampler("uState", 4, tickState.id)
        p.vec2("uFramePx", target.width.toFloat(), target.height.toFloat())
        p.vec2("uLevel0Px", l0.w.toFloat(), l0.h.toFloat())
        p.float("uBlock", BLOCK.toFloat())
        p.float("uStep", STEP.toFloat())
        p.int("uAreaMask", if (AREA_MASK > 0) 1 else 0)
        drawQuad(p)
    }

    /** Queues a dispatch; whatever reads what it writes waits behind the next [barrier]. */
    private fun dispatch(x: Int, y: Int) {
        GLES31.glDispatchCompute(max(1, x), max(1, y), 1)
    }

    /** The end of a stage: its image and storage-buffer writes become visible to what follows. */
    private fun barrier() {
        GLES31.glMemoryBarrier(STAGE_BARRIER_BITS)
    }

    private fun drawQuad(program: EsProgram) {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        val location = program.attribute("aFramePosition")
        quad.position(0)
        GLES20.glVertexAttribPointer(location, 4, GLES20.GL_FLOAT, false, 0, quad)
        GLES20.glEnableVertexAttribArray(location)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(location)
    }

    // ---------------------------------------------------------------- textures

    private fun field(size: Size) = storage(size, GLES30.GL_RGBA16F, linear = true, withFbo = false)

    /** Immutable storage, which image binding requires; float32 and integer formats must be NEAREST to be complete. */
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

    /** A zeroed shader storage buffer of [words] 32-bit words. */
    private fun storageBuffer(words: Int): Int {
        val ids = IntArray(1)
        GLES20.glGenBuffers(1, ids, 0)
        GLES20.glBindBuffer(GLES31.GL_SHADER_STORAGE_BUFFER, ids[0])
        val zeros = ByteBuffer.allocateDirect(words * 4).order(ByteOrder.nativeOrder())
        GLES20.glBufferData(GLES31.GL_SHADER_STORAGE_BUFFER, words * 4, zeros, GLES30.GL_DYNAMIC_COPY)
        GLES20.glBindBuffer(GLES31.GL_SHADER_STORAGE_BUFFER, 0)
        GlUtil.checkGlError()
        return ids[0]
    }

    /** A zeroed one-word buffer the GPU copies into and the CPU maps. */
    private fun newReadbackBuffer(): Int {
        val ids = IntArray(1)
        GLES20.glGenBuffers(1, ids, 0)
        GLES20.glBindBuffer(GLES30.GL_COPY_WRITE_BUFFER, ids[0])
        val zeros = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())
        GLES20.glBufferData(GLES30.GL_COPY_WRITE_BUFFER, 4, zeros, GLES30.GL_STREAM_READ)
        GLES20.glBindBuffer(GLES30.GL_COPY_WRITE_BUFFER, 0)
        GlUtil.checkGlError()
        return ids[0]
    }

    private fun releaseTextures() {
        for (tex in textures) {
            if (tex.fbo != 0) GLES20.glDeleteFramebuffers(1, intArrayOf(tex.fbo), 0)
            GLES20.glDeleteTextures(1, intArrayOf(tex.id), 0)
        }
        textures.clear()
        if (readbackFence != 0L) {
            GLES30.glDeleteSync(readbackFence)
            readbackFence = 0L
        }
        for (buffer in intArrayOf(stateBuffer, coverBuffer, readbackBuffer)) {
            if (buffer != 0) GLES20.glDeleteBuffers(1, intArrayOf(buffer), 0)
        }
        stateBuffer = 0
        coverBuffer = 0
        readbackBuffer = 0
        lumaScratch = emptyArray()
        pyramidRef = emptyArray()
        pyramidCur = emptyArray()
        rawF = emptyArray()
        rawB = emptyArray()
        filtF = emptyArray()
        filtB = emptyArray()
        prevFieldF = null
        prevFieldB = null
        svpForward = null
        svpBackward = null
        motion = null
        magnitude = null
        masks = null
        states = emptyArray()
    }

    private fun groups(n: Int) = (n + 7) / 8

    internal data class Size(val w: Int, val h: Int)

    private data class Tex(val id: Int, val fbo: Int, val size: Size)

    /** A program of this engine's own: vertex + fragment, or compute alone. */
    private class EsProgram(vertex: String?, source: String) {
        private val id = GLES20.glCreateProgram()
        private val uniforms = HashMap<String, Int>()
        private val attributes = HashMap<String, Int>()

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

        fun attribute(name: String): Int = attributes.getOrPut(name) {
            val location = GLES20.glGetAttribLocation(id, name)
            check(location >= 0) { "Missing attribute $name" }
            location
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
        /**
         * Mean normalised-luma difference charged per px of departure from the
         * predicted vector: the field's coherence (SVP's `penalty.lambda`).
         */
        const val LAMBDA = 0.006f

        /** The search's block and grid step, in level-0 px (SVP's block minus overlap). */
        const val BLOCK = 8
        const val STEP = 8

        /** SVP's `mask.cover`: coverage mask strength, percent. */
        const val COVER_STRENGTH = 100

        /** SVP's `mask.area`: bad-area mask strength (0 = off); the references were rendered at 100. */
        const val AREA_MASK = 100

        /**
         * open-svpflow's coverage splat divides the (already whole-pixel)
         * vectors by the search's pel precision again (`splat_coverage`,
         * `denom = scale_shift_base << 8`); its references were made at pel 2.
         */
        const val COVER_DIVISOR = 2f

        /**
         * What one stage's writes need before the next stage reads them:
         * texture fetches, image access and storage-buffer access see the
         * writes, and later stores wait for earlier reads. The full
         * `GL_ALL_BARRIER_BITS` also orders framebuffer, pixel-transfer,
         * command and vertex traffic that no shader here writes for, which
         * drivers may pay for with extra cache flushes.
         */
        const val STAGE_BARRIER_BITS = GLES31.GL_TEXTURE_FETCH_BARRIER_BIT or
            GLES31.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT or
            GLES31.GL_SHADER_STORAGE_BARRIER_BIT

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
precision highp image2D;
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
         * Luma at the target size as the mean of uTaps x uTaps bilinear taps
         * spread evenly over the target texel ([ComputeMotionPlan.lumaTaps]):
         * with 2, taps a quarter texel out, a 2x2 box for a halving; more for
         * the first level's larger step down from the frame.
         */
        const val LUMA = HEADER + FRAGMENT_PRECISION + """
uniform sampler2D uTex;
uniform int uFromLuma;
uniform vec2 uDstTexel;
uniform int uTaps;
in vec2 vTexCoord;
out vec4 outColor;

float lumaAt(vec2 uv) {
  vec4 c = texture(uTex, uv);
  return uFromLuma == 1 ? c.r : dot(c.rgb, vec3(0.299, 0.587, 0.114));
}

void main() {
  float n = float(uTaps);
  float s = 0.0;
  for (int j = 0; j < uTaps; j++) {
    for (int i = 0; i < uTaps; i++) {
      vec2 o = (vec2(float(i), float(j)) + 0.5) / n - 0.5;
      s += lumaAt(vTexCoord + o * uDstTexel);
    }
  }
  outColor = vec4(s / (n * n), 0.0, 0.0, 1.0);
}
"""

        /**
         * r: luma. g: luma relative to its neighbourhood (about 6x6: nine
         * bilinear taps 1.5 texels apart), divided by the neighbourhood's
         * contrast and centred on 0.5, so a fade or an exposure change still
         * matches.
         */
        const val NORMALIZE = HEADER + FRAGMENT_PRECISION + """
uniform sampler2D uLuma;
uniform vec2 uTexel;
in vec2 vTexCoord;
out vec4 outColor;

const float CONTRAST_FLOOR = 0.02;
const float SPREAD = 0.18;

void main() {
  float l = texture(uLuma, vTexCoord).r;
  float s = 0.0;
  float s2 = 0.0;
  for (int j = -1; j <= 1; j++) {
    for (int i = -1; i <= 1; i++) {
      float v = texture(uLuma, vTexCoord + vec2(float(i), float(j)) * 1.5 * uTexel).r;
      s += v;
      s2 += v * v;
    }
  }
  float mean = s / 9.0;
  float sd = sqrt(max(s2 / 9.0 - mean * mean, 0.0));
  float n = (l - mean) / (sd + CONTRAST_FLOOR);
  outColor = vec4(l, clamp(0.5 + SPREAD * n, 0.0, 1.0), 0.0, 1.0);
}
"""

        /**
         * One workgroup per 8x8 block of uSrc, matched over the 12x12 window
         * around it in uDst, on the normalised channel. Output: xy the vector
         * in this level's px (uSrc position + v = uDst position), z the best
         * match's mean absolute difference, w 1.
         *
         * Up to seven candidates (zero, the parent's vector, the previous
         * pair's, the parent's four neighbours; a repeat is measured once)
         * pick a centre, and every offset within uRadius of it is measured.
         * All 64 invocations share every phase: a block's difference is split
         * into its twelve rows, and rows are added into shared memory as fixed
         * point, so the total does not depend on the order they land in. The
         * sub-pixel fit reads its neighbours from that table and measures
         * only the ones past the radius. Only the part of uDst the radius
         * (plus one for the fit) reaches is loaded.
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
const int MARGIN = 2;
const int MATCH = BLOCK + 2 * MARGIN;
const int AREA = MATCH * MATCH;
const int MAX_RADIUS = 8;
const int WIN = MATCH + 2 * MAX_RADIUS;
const int MAX_SIDE = 2 * MAX_RADIUS + 1;
const int CANDIDATES = 7;
const int THREADS = 64;
const float FIXED = 65536.0;

shared float sBlock[AREA];
shared float sWin[WIN * WIN];
shared vec2 sCand[CANDIDATES];
shared uint sCandSad[CANDIDATES];
shared uint sSad[MAX_SIDE * MAX_SIDE];
shared uint sEdgeSad[4];
shared uint sBest;

float structureAt(sampler2D tex, ivec2 p) {
  ivec2 size = textureSize(tex, 0);
  return texelFetch(tex, clamp(p, ivec2(0), size - 1), 0).g;
}

vec2 coarseVector(ivec2 parent, ivec2 offset) {
  ivec2 size = textureSize(uCoarse, 0);
  return texelFetch(uCoarse, clamp(parent + offset, ivec2(0), size - 1), 0).xy * 2.0;
}

uint fixedSum(float sad) {
  return uint(sad * FIXED + 0.5);
}

float windowRow(int y, ivec2 d) {
  int own = y * MATCH;
  int row = (y + MAX_RADIUS + d.y) * WIN + MAX_RADIUS + d.x;
  float sad = 0.0;
  for (int x = 0; x < MATCH; x++) {
    sad += abs(sBlock[own + x] - sWin[row + x]);
  }
  return sad;
}

bool repeats(int c) {
  for (int j = 0; j < c; j++) {
    if (sCand[j] == sCand[c]) return true;
  }
  return false;
}

ivec2 edgeOffset(int e) {
  return e == 0 ? ivec2(-1, 0) : (e == 1 ? ivec2(1, 0) : (e == 2 ? ivec2(0, -1) : ivec2(0, 1)));
}

bool inTable(ivec2 d, int radius) {
  return abs(d.x) <= radius && abs(d.y) <= radius;
}

bool needsEdge(ivec2 d, int e, int radius) {
  int along = e < 2 ? d.x : d.y;
  return abs(along) < MAX_RADIUS && !inTable(d + edgeOffset(e), radius);
}

float sadNear(ivec2 d, int e, int radius) {
  ivec2 n = d + edgeOffset(e);
  if (inTable(n, radius)) {
    ivec2 i = n + ivec2(radius);
    return float(sSad[i.y * (2 * radius + 1) + i.x]) / FIXED;
  }
  return float(sEdgeSad[e]) / FIXED;
}

void main() {
  ivec2 block = ivec2(gl_WorkGroupID.xy);
  int li = int(gl_LocalInvocationIndex);
  ivec2 origin = block * BLOCK;
  ivec2 corner = origin - ivec2(MARGIN);
  int radius = clamp(uRadius, 0, MAX_RADIUS);
  int side = 2 * radius + 1;
  int count = side * side;
  for (int k = li; k < AREA; k += THREADS) {
    sBlock[k] = structureAt(uSrc, corner + ivec2(k % MATCH, k / MATCH));
  }
  for (int k = li; k < count; k += THREADS) {
    sSad[k] = 0u;
  }
  if (li < 4) sEdgeSad[li] = 0u;
  if (li == 0) sBest = 0xFFFFFFFFu;

  vec2 centerPx = vec2(origin) + 4.0;
  ivec2 parent = block / 2;
  vec2 temporal = vec2(0.0);
  if (uHasTemporal == 1) {
    temporal = texture(uTemporal, (centerPx / uTemporalScale) / uTemporalPx).xy * uTemporalScale;
  }
  vec2 prediction = uHasCoarse == 1 ? coarseVector(parent, ivec2(0)) : temporal;

  if (li < CANDIDATES) {
    vec2 candidate = vec2(0.0);
    if (li == 1) {
      candidate = prediction;
    } else if (li == 2) {
      candidate = temporal;
    } else if (li >= 3 && uHasCoarse == 1) {
      ivec2 offset = li == 3 ? ivec2(1, 0) : (li == 4 ? ivec2(-1, 0) : (li == 5 ? ivec2(0, 1) : ivec2(0, -1)));
      candidate = coarseVector(parent, offset);
    }
    sCand[li] = floor(candidate + 0.5);
    sCandSad[li] = 0u;
  }
  memoryBarrierShared();
  barrier();

  ivec2 dstSize = textureSize(uDst, 0);
  for (int t = li; t < CANDIDATES * MATCH; t += THREADS) {
    int c = t / MATCH;
    if (repeats(c)) continue;
    int y = t - c * MATCH;
    ivec2 at = corner + ivec2(sCand[c]) + ivec2(0, y);
    float sad = 0.0;
    for (int x = 0; x < MATCH; x++) {
      sad += abs(sBlock[y * MATCH + x] - texelFetch(uDst, clamp(at + ivec2(x, 0), ivec2(0), dstSize - 1), 0).g);
    }
    atomicAdd(sCandSad[c], fixedSum(sad));
  }
  memoryBarrierShared();
  barrier();

  int bestCandidate = 0;
  float bestCost = float(sCandSad[0]) / FIXED / float(AREA) + uLambda * length(sCand[0] - prediction);
  for (int c = 1; c < CANDIDATES; c++) {
    if (repeats(c)) continue;
    float cost = float(sCandSad[c]) / FIXED / float(AREA) + uLambda * length(sCand[c] - prediction);
    if (cost < bestCost) {
      bestCost = cost;
      bestCandidate = c;
    }
  }
  ivec2 center = ivec2(sCand[bestCandidate]);

  int pad = min(radius + 1, MAX_RADIUS);
  int span = MATCH + 2 * pad;
  int skip = MAX_RADIUS - pad;
  ivec2 windowOrigin = corner + center - ivec2(MAX_RADIUS);
  for (int k = li; k < span * span; k += THREADS) {
    ivec2 q = ivec2(k % span, k / span) + ivec2(skip);
    sWin[q.y * WIN + q.x] = structureAt(uDst, windowOrigin + q);
  }
  memoryBarrierShared();
  barrier();

  for (int t = li; t < count * MATCH; t += THREADS) {
    int k = t / MATCH;
    ivec2 d = ivec2(k % side, k / side) - ivec2(radius);
    atomicAdd(sSad[k], fixedSum(windowRow(t - k * MATCH, d)));
  }
  memoryBarrierShared();
  barrier();

  for (int k = li; k < count; k += THREADS) {
    ivec2 d = ivec2(k % side, k / side) - ivec2(radius);
    float cost = float(sSad[k]) / FIXED / float(AREA) + uLambda * length(vec2(center + d) - prediction);
    uint key = (uint(min(cost * 65536.0, 4194303.0)) << 10) | uint(k);
    atomicMin(sBest, key);
  }
  memoryBarrierShared();
  barrier();

  int best = int(sBest & 1023u);
  ivec2 found = ivec2(best % side, best / side) - ivec2(radius);
  for (int t = li; t < 4 * MATCH; t += THREADS) {
    int e = t / MATCH;
    if (!needsEdge(found, e, radius)) continue;
    atomicAdd(sEdgeSad[e], fixedSum(windowRow(t - e * MATCH, found + edgeOffset(e))));
  }
  memoryBarrierShared();
  barrier();

  if (li == 0) {
    float s0 = float(sSad[best]) / FIXED;
    vec2 sub = vec2(0.0);
    if (abs(found.x) < MAX_RADIUS) {
      float l = sadNear(found, 0, radius);
      float r = sadNear(found, 1, radius);
      float den = l - 2.0 * s0 + r;
      if (den > 0.0001) sub.x = clamp(0.5 * (l - r) / den, -0.5, 0.5);
    }
    if (abs(found.y) < MAX_RADIUS) {
      float u = sadNear(found, 2, radius);
      float w = sadNear(found, 3, radius);
      float den = u - 2.0 * s0 + w;
      if (den > 0.0001) sub.y = clamp(0.5 * (u - w) / den, -0.5, 0.5);
    }
    imageStore(uOut, block, vec4(vec2(center + found) + sub, s0 / float(AREA), 1.0));
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
         * One 8x8 block of the search's level-0 field as SVP's vector record:
         * xy the vector, z the score - the block's SAD in 8-bit luma at that
         * vector (bilinear where it is fractional), with SVP's extra weight on
         * long vectors (svpflow1 `rescale_scores`, pel 2) - and w the block's
         * mean luma in its own frame (svpflow1 `block_luma_dc`).
         */
        const val FIELD = HEADER + COMPUTE_PRECISION + """
layout(local_size_x = 8, local_size_y = 8) in;
uniform sampler2D uField;
uniform sampler2D uOwn;
uniform sampler2D uOther;
uniform vec2 uLumaPx;
uniform float uDiag;
layout(rgba32f, binding = 0) writeonly uniform highp image2D uOut;

const int BLOCK = 8;
const float PEL = 2.0;

void main() {
  ivec2 c = ivec2(gl_GlobalInvocationID.xy);
  ivec2 size = textureSize(uField, 0);
  if (c.x >= size.x || c.y >= size.y) return;
  vec2 v = texelFetch(uField, c, 0).xy;
  ivec2 lumaSize = textureSize(uOwn, 0);
  ivec2 origin = c * BLOCK;
  float sad = 0.0;
  float sum = 0.0;
  for (int y = 0; y < BLOCK; y++) {
    for (int x = 0; x < BLOCK; x++) {
      ivec2 p = origin + ivec2(x, y);
      float a = texelFetch(uOwn, clamp(p, ivec2(0), lumaSize - 1), 0).r * 255.0;
      float b = texture(uOther, (vec2(p) + 0.5 + v) / uLumaPx).r * 255.0;
      sad += abs(a - b);
      sum += a;
    }
  }
  float score = floor(sad + 0.5);
  float mag = (abs(v.x) + abs(v.y)) * PEL;
  float denom = max(uDiag * float(BLOCK), 1.0);
  float scaled = 100.0 * mag;
  if (scaled >= 51.0 * denom) {
    score *= 20.0;
  } else if (scaled >= 16.0 * denom) {
    float ratio = floor(scaled / denom);
    float v71 = floor(score * (ratio - 15.0) * 3926827243.0 / 4294967296.0);
    score += 9.0 * floor(v71 / 32.0);
  }
  score = min(score, 16777215.0);
  float luma = min(floor(sum / float(BLOCK * BLOCK)), 255.0);
  imageStore(uOut, c, vec4(v, score, luma));
}
"""

        /**
         * Per motion cell (the block grid, plus a column or row when the grid
         * stops short of the frame): the backward and forward vectors kept
         * inside the frame (svpflow-core `vector_planes`, `clamp_to_frame`),
         * packed as the kernel reads them - (backward x, forward y, forward x,
         * backward y) - and the bad-area mask of each (`magnitude_mask`,
         * `scale_magnitude`: (4 x score x scale / block area) x 255), whose
         * extra cells copy the way SVP's do.
         */
        const val PREP = HEADER + COMPUTE_PRECISION + """
layout(local_size_x = 8, local_size_y = 8) in;
uniform sampler2D uForward;
uniform sampler2D uBackward;
uniform ivec2 uGrid;
uniform ivec2 uMotionGrid;
uniform vec2 uFramePx;
uniform float uBlock;
uniform float uStep;
uniform float uAreaScale;
layout(rgba32f, binding = 0) writeonly uniform highp image2D uMotion;
layout(rgba16f, binding = 1) writeonly uniform highp image2D uMagnitude;

float clampAxis(float v, float pos, float frame) {
  if (v + pos < 0.0) return -pos;
  if (v + uBlock + pos > frame) return max(frame - uBlock - pos, 0.0);
  return v;
}

vec2 planeVector(vec4 record, ivec2 c) {
  if (abs(record.x) > 1023.0 || abs(record.y) > 1023.0) return vec2(0.0);
  vec2 pos = vec2(c) * uStep;
  return vec2(clampAxis(record.x, pos.x, uFramePx.x), clampAxis(record.y, pos.y, uFramePx.y));
}

float magnitudeOf(float score) {
  float value = 4.0 * score * uAreaScale / (uBlock * uBlock) * 255.0;
  if (value <= 0.0) return 0.0;
  return value >= 255.0 ? 255.0 : floor(value);
}

void main() {
  ivec2 c = ivec2(gl_GlobalInvocationID.xy);
  if (c.x >= uMotionGrid.x || c.y >= uMotionGrid.y) return;
  ivec2 s = min(c, uGrid - 1);
  vec2 back = planeVector(texelFetch(uBackward, s, 0), c);
  vec2 fore = planeVector(texelFetch(uForward, s, 0), c);
  imageStore(uMotion, c, vec4(back.x, fore.y, fore.x, back.y));
  ivec2 m = c.y >= uGrid.y ? ivec2(0, uGrid.y - 1) : ivec2(min(c.x, uGrid.x - 1), c.y);
  float magForward = magnitudeOf(texelFetch(uForward, m, 0).z);
  float magBackward = magnitudeOf(texelFetch(uBackward, m, 0).z);
  imageStore(uMagnitude, c, vec4(magForward, magBackward, 0.0, 1.0));
}
"""

        /**
         * SVP's scene class of the pair (svpflow-core `classify_scene_pair`):
         * each forward block's score over a luma weight - the two frames'
         * block means through SVP's gamma-1.5 table, byte-truncated as its is
         * - against its limits scaled by block area / 32; a 4% border is
         * ignored and up to two thirds of near-still blocks do not count.
         * 3 when a fifth of the counted blocks pass the scene limit, 2 when
         * they pass m2, 1 when they pass m1, else 0. Written to the state
         * buffer (and copied back for the log by [requestHardness]); w of the
         * state image follows.
         */
        const val SCENE = HEADER + COMPUTE_PRECISION + """
layout(local_size_x = 16, local_size_y = 16) in;
uniform sampler2D uForward;
uniform sampler2D uBackward;
uniform ivec2 uGrid;
uniform float uBlockArea;
layout(std430, binding = 0) buffer State {
  int sceneClass;
  int show;
  int algorithm;
  int phase;
};
shared uint sZero;
shared uint sOther;
shared uint sScene;
shared uint sM2;
shared uint sM1;

void main() {
  int li = int(gl_LocalInvocationIndex);
  if (li == 0) {
    sZero = 0u;
    sOther = 0u;
    sScene = 0u;
    sM2 = 0u;
    sM1 = 0u;
  }
  memoryBarrierShared();
  barrier();
  float scale = uBlockArea / 32.0;
  float zeroLimit = floor(200.0 * scale);
  float m1 = floor(1600.0 * scale);
  float m2 = floor(2800.0 * scale);
  float sceneLimit = floor(4000.0 * scale);
  int borderX = max(int(float(uGrid.x) * 0.04), 1);
  int borderY = max(int(float(uGrid.y) * 0.04), 1);
  uint zero = 0u;
  uint other = 0u;
  uint sceneCount = 0u;
  uint m2Count = 0u;
  uint m1Count = 0u;
  int n = uGrid.x * uGrid.y;
  for (int k = li; k < n; k += 256) {
    ivec2 p = ivec2(k % uGrid.x, k / uGrid.x);
    if (p.x < borderX || p.x >= uGrid.x - borderX || p.y < borderY || p.y >= uGrid.y - borderY) continue;
    vec4 fore = texelFetch(uForward, p, 0);
    vec4 back = texelFetch(uBackward, p, 0);
    float sum = back.w + fore.w;
    int lut = max(int(pow(sum / 255.0, 1.5) * 255.0), 20) & 255;
    float luma = float(max(lut, 1));
    float score = floor(min(fore.z * 255.0, 2147483647.0) / luma);
    if (score < zeroLimit) {
      zero += 1u;
    } else {
      other += 1u;
      if (score >= sceneLimit) sceneCount += 1u;
      else if (score >= m2) m2Count += 1u;
      else if (score >= m1) m1Count += 1u;
    }
  }
  atomicAdd(sZero, zero);
  atomicAdd(sOther, other);
  atomicAdd(sScene, sceneCount);
  atomicAdd(sM2, m2Count);
  atomicAdd(sM1, m1Count);
  memoryBarrierShared();
  barrier();
  if (li == 0) {
    int zeroAllowed = (uGrid.x * uGrid.y * 2) / 3;
    int considered = int(sOther) + max(0, int(sZero) - zeroAllowed);
    int required = considered * 20 / 100;
    int high = int(sScene) + int(sM2);
    int mid = high + int(sM1);
    int result = 0;
    if (int(sScene) >= required) result = 3;
    else if (high >= required) result = 2;
    else if (mid >= required) result = 1;
    sceneClass = result;
  }
}
"""

        /**
         * One tick: the cut and class rules. Every tick of an easy pair (class
         * 0) is drawn at its own phase with algorithm 21, so motion advances
         * evenly. A cut shows the nearer frame. A hard pair (class 1, 2) is
         * drawn with algorithm 13 and, when two or more ticks fall on each
         * source frame, re-timed toward the real frames (frame_math
         * `scene_phase_256`, modes 0 and 1 - SVP's adaptive digits "210"),
         * where a wrong vector shows least. A phase that lands on 0 or 256
         * shows that real frame.
         *
         * [scar, September 2026] The port first carried SVP's cadence rule for
         * its minimal-artifact mode as well: any tick at or past the pair's
         * midpoint showed the next real frame. At 30 fps into 60 the only tick
         * is the midpoint, so no frame was ever drawn while the whole search
         * still ran; into 120 one frame was drawn and the next real frame
         * repeated three times, which read as stutter rather than smoothness.
         */
        const val PHASE = HEADER + COMPUTE_PRECISION + """
layout(local_size_x = 1) in;
uniform float uRawPhase;
uniform float uStep256;
uniform int uAdaptive;
layout(std430, binding = 0) buffer State {
  int sceneClass;
  int show;
  int algorithm;
  int phase;
};
layout(rgba16f, binding = 0) writeonly uniform highp image2D uOut;

int scenePhase(float raw, int mode, float step) {
  float leftFloor = floor(raw / step);
  float leftRem = raw - leftFloor * step;
  int left;
  if (mode > 1) {
    left = int(leftRem) > 0 ? int(leftFloor) : max(int(leftFloor) - 1, 0);
  } else {
    left = int(leftFloor) + (int(leftRem) >= int(abs(leftRem - step)) ? 1 : 0);
  }
  float rightFloor = floor((256.0 - raw - 0.001) / step);
  float rightRem = 256.0 - (rightFloor * step + raw);
  int right;
  if (mode > 1) {
    right = int(rightFloor) + (abs(rightRem - step) < 0.1 ? 1 : 0);
  } else {
    right = int(rightFloor) + (int(rightRem) > int(abs(rightRem - step)) ? 1 : 0);
  }
  int total = left + right;
  if (total == 0) return 0;
  int result = left * 256 / total;
  if ((mode & ~2) == 0) return result;
  if (left > right) return 256 - (right * 256 / total) / 2;
  return result / 2;
}

void main() {
  int cls = sceneClass;
  int p = int(floor(uRawPhase + 0.5));
  int s = 0;
  int effective = p;
  if (cls >= 3) {
    s = p < 128 ? 1 : 2;
  } else {
    if (uAdaptive == 1 && cls == 1) effective = scenePhase(uRawPhase, 0, uStep256);
    if (uAdaptive == 1 && cls == 2) effective = scenePhase(uRawPhase, 1, uStep256);
    if (effective <= 0) s = 1;
    else if (effective >= 256) s = 2;
  }
  int algo = (cls == 1 || cls == 2) ? 13 : 21;
  show = s;
  algorithm = algo;
  phase = effective;
  imageStore(uOut, ivec2(0), vec4(float(effective) / 256.0, float(algo), float(s), float(cls) / 3.0));
}
"""

        /** Zeroes the coverage accumulators. */
        const val COVER_CLEAR = HEADER + COMPUTE_PRECISION + """
layout(local_size_x = 64) in;
uniform int uCount;
layout(std430, binding = 1) writeonly buffer Cover {
  int cover[];
};

void main() {
  int i = int(gl_GlobalInvocationID.x);
  if (i < uCount) cover[i] = 0;
}
"""

        /**
         * svpflow-core `splat_coverage`: every block of a field, moved by
         * threshold/256 of its vector (whole px, divided by uCoverDivisor as
         * open-svpflow does), lands its area on the two-by-two cells under
         * its top-left corner, split by overlap. Plane 0 (the mask for A's
         * sample) moves the forward field by 256 - phase; plane 1 (for B's)
         * the backward field by phase.
         */
        const val COVER_SPLAT = HEADER + COMPUTE_PRECISION + """
layout(local_size_x = 8, local_size_y = 8) in;
uniform sampler2D uForward;
uniform sampler2D uBackward;
uniform ivec2 uGrid;
uniform ivec2 uMotionGrid;
uniform int uBlock;
uniform int uStep;
uniform float uCoverDivisor;
uniform int uPlane;
layout(std430, binding = 0) readonly buffer State {
  int sceneClass;
  int show;
  int algorithm;
  int phase;
};
layout(std430, binding = 1) buffer Cover {
  int cover[];
};

int floorDiv(int a, int b) {
  return a >= 0 ? a / b : -((-a + b - 1) / b);
}

void add(int x, int y, int weight, int base, int stride) {
  if (x >= 0 && y >= 0 && x < uMotionGrid.x && y < uMotionGrid.y) {
    atomicAdd(cover[base + y * stride + x], weight);
  }
}

void main() {
  ivec2 c = ivec2(gl_GlobalInvocationID.xy);
  if (c.x >= uGrid.x || c.y >= uGrid.y || show != 0) return;
  int threshold = uPlane == 0 ? 256 - phase : phase;
  vec4 v = uPlane == 0 ? texelFetch(uForward, c, 0) : texelFetch(uBackward, c, 0);
  int dx = int(float(threshold) * v.x / (uCoverDivisor * 256.0));
  int dy = int(float(threshold) * v.y / (uCoverDivisor * 256.0));
  int shiftedX = c.x * uStep + dx;
  int shiftedY = c.y * uStep + dy;
  int left = floorDiv(shiftedX, uStep);
  int top = floorDiv(shiftedY, uStep);
  int right = left + 1;
  int bottom = top + 1;
  int nextX = right * uStep;
  int leftWeight = nextX - shiftedX;
  int rightWeight = shiftedX + uBlock - nextX;
  int topWeight = uStep * bottom - shiftedY;
  int bottomWeight = uBlock - topWeight;
  int stride = uMotionGrid.x + 2;
  int base = uPlane * stride * (uMotionGrid.y + 2);
  add(left, top, leftWeight * topWeight, base, stride);
  add(right, top, topWeight * rightWeight, base, stride);
  add(right, bottom, rightWeight * bottomWeight, base, stride);
  add(left, bottom, leftWeight * bottomWeight, base, stride);
}
"""

        /**
         * svpflow-core `window_3x3` + `finish_coverage`: per cell, the landed
         * area around it (3x3), an eighth of it counted as covered; what is
         * left of a block's area, scaled by strength, is how uncovered the cell
         * is (0-255). Packed with the bad-area mask - the larger of the two
         * directions' per cell (`max_mask`), as the renderer uses it:
         * (bad-area, A's coverage, B's coverage, 0).
         */
        const val COVER_FINISH = HEADER + COMPUTE_PRECISION + """
layout(local_size_x = 8, local_size_y = 8) in;
uniform sampler2D uMagnitude;
uniform ivec2 uMotionGrid;
uniform int uArea;
uniform float uStrength;
layout(std430, binding = 0) readonly buffer State {
  int sceneClass;
  int show;
  int algorithm;
  int phase;
};
layout(std430, binding = 1) readonly buffer Cover {
  int cover[];
};
layout(rgba16f, binding = 0) writeonly uniform highp image2D uOut;

float coverage(int base, int stride, ivec2 c) {
  int sum = 0;
  for (int j = -1; j <= 1; j++) {
    for (int i = -1; i <= 1; i++) {
      ivec2 q = c + ivec2(i, j);
      if (q.x >= 0 && q.y >= 0 && q.x < uMotionGrid.x && q.y < uMotionGrid.y) {
        sum += cover[base + q.y * stride + q.x];
      }
    }
  }
  int covered = sum >> 3;
  int remaining = uArea <= covered ? 0 : uArea - covered;
  int value = int(float(remaining) * uStrength * 256.0 / float(uArea));
  return float(value >= 255 ? 255 : value);
}

void main() {
  ivec2 c = ivec2(gl_GlobalInvocationID.xy);
  if (c.x >= uMotionGrid.x || c.y >= uMotionGrid.y || show != 0) return;
  int stride = uMotionGrid.x + 2;
  int plane = stride * (uMotionGrid.y + 2);
  vec4 mag = texelFetch(uMagnitude, c, 0);
  imageStore(uOut, c, vec4(max(mag.x, mag.y), coverage(0, stride, c), coverage(plane, stride, c), 0.0));
}
"""

        /**
         * SVP's renderer, per output pixel and RGB channel (svpflow-core
         * `render_dual_warp_rows` with `mode13_pixel` / `mode21_pixel` and
         * `mode11_or_13_pixel` / `mode21_or_22_pixel` for the bad-area mask).
         * The vector field and masks are read at the pixel's own place on the
         * block grid, bilinear between block centres (`render_tiles`: cell i
         * at block/2 + i x step). A is sampled along the backward vector x
         * phase, B along the forward vector x (1 - phase), bilinear rather
         * than SVP's whole pixels. Algorithm 13: the median of the two
         * samples and the plain time blend. 21: each sample swapped toward
         * the other by its coverage mask, then blended by time. Then the
         * bad-area mask fades 13 toward the time blend and 21 toward the
         * nearer real frame (`threshold_limit`: A up to phase 126, else B).
         *
         * [verified September 2026] Fed open-svpflow's own vectors for the
         * pipes test clip, this matches its CPU renderer at 43.2 dB (easy
         * pairs) and 44.7 dB (hard) in Y, against a 46.7 dB ceiling from RGB
         * versus YUV on real frames. Its OpenCL kernel's reading (a half-cell
         * offset, time-mixed masks, linear light) matched worse: 37.1 dB.
         */
        const val RENDER = HEADER + FRAGMENT_PRECISION + """
uniform sampler2D uPrev;
uniform sampler2D uCur;
uniform sampler2D uMotion;
uniform sampler2D uMasks;
uniform sampler2D uState;
uniform vec2 uFramePx;
uniform vec2 uLevel0Px;
uniform float uBlock;
uniform float uStep;
uniform int uAreaMask;
in vec2 vTexCoord;
out vec4 outColor;

vec4 cellsAt(sampler2D tex, vec2 g) {
  ivec2 size = textureSize(tex, 0);
  ivec2 b = ivec2(floor(g));
  vec2 f = g - vec2(b);
  vec4 v00 = texelFetch(tex, clamp(b, ivec2(0), size - 1), 0);
  vec4 v10 = texelFetch(tex, clamp(b + ivec2(1, 0), ivec2(0), size - 1), 0);
  vec4 v01 = texelFetch(tex, clamp(b + ivec2(0, 1), ivec2(0), size - 1), 0);
  vec4 v11 = texelFetch(tex, clamp(b + ivec2(1, 1), ivec2(0), size - 1), 0);
  return mix(mix(v00, v10, f.x), mix(v01, v11, f.x), f.y);
}

vec3 sampleAt(sampler2D tex, vec2 x, vec2 displacement) {
  vec2 position = clamp(x + displacement, vec2(0.0), uFramePx - 1.0) + 0.5;
  return texture(tex, position / uFramePx).rgb;
}

vec3 median3(vec3 a, vec3 b, vec3 c) {
  vec3 lo = min(a, b);
  return max(lo, min(a + b - lo, c));
}

void main() {
  vec4 st = texelFetch(uState, ivec2(0), 0);
  vec2 x = floor(vTexCoord * uFramePx);
  if (st.b > 1.5) {
    outColor = texture(uCur, (x + 0.5) / uFramePx);
    return;
  }
  if (st.b > 0.5) {
    outColor = texture(uPrev, (x + 0.5) / uFramePx);
    return;
  }
  float time = st.r;
  int algorithm = int(st.g + 0.5);
  vec2 level0 = uLevel0Px / uFramePx;
  vec2 position = (x * level0 - uBlock * 0.5) / uStep;
  vec4 v = cellsAt(uMotion, position) / vec4(level0.x, level0.y, level0.x, level0.y);
  vec4 m = cellsAt(uMasks, position) / 255.0;
  vec3 refF = sampleAt(uPrev, x, vec2(v.x, v.w) * time);
  vec3 refB = sampleAt(uCur, x, vec2(v.z, v.y) * (1.0 - time));
  vec3 curF = sampleAt(uPrev, x, vec2(0.0));
  vec3 curB = sampleAt(uCur, x, vec2(0.0));
  vec3 base = mix(curF, curB, time);
  vec3 result;
  if (algorithm == 13) {
    result = median3(refF, refB, base);
  } else {
    result = mix(mix(refF, refB, m.y), mix(refB, refF, m.z), time);
  }
  if (uAreaMask == 1) {
    vec3 fallback = algorithm == 13 ? base : (time <= 126.0 / 256.0 ? curF : curB);
    result = mix(result, fallback, m.x);
  }
  outColor = vec4(clamp(result, 0.0, 1.0), 1.0);
}
"""
    }
}

/** Sizes for [ComputeMotionEngine], kept pure for tests. */
internal object ComputeMotionPlan {
    /**
     * The long side of the finest level the motion is searched at, whatever
     * the video's size: the search's cost follows this level, not the frame,
     * and the renderer scales its vectors up to the frame. [measured September
     * 2026] On an Adreno 613 a full-size search took ~310 ms per 480p pair
     * against a 33 ms budget (about 2.6 ms per million pixel comparisons), and
     * ~1.4 s at 1080p; at 320 px it is a few million comparisons.
     */
    const val LEVEL0_LONG_SIDE = 320

    /**
     * Levels stop once the long side is at or under this, or at six. At 40 px
     * the coarsest level's +-8 reaches a fifth of the frame per source frame,
     * and its handful of blocks makes that wide search cheap.
     */
    const val COARSEST_LONG_SIDE = 40

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

    /**
     * SVP's motion grid (metadata `extended_grid`): the block grid plus a
     * column or row where the blocks stop short of the frame. The search's
     * 8 px grid with no overlap rounds up, so it covers the frame already.
     */
    fun motionGrid(level0: ComputeMotionEngine.Size, grid: ComputeMotionEngine.Size) =
        ComputeMotionEngine.Size(
            grid.w + if (8 * grid.w < level0.w) 1 else 0,
            grid.h + if (8 * grid.h < level0.h) 1 else 0,
        )

    /**
     * The coarsest level's radius, the widest the SEARCH shader's window holds
     * (its MAX_RADIUS): at a 40 px long side, +-8 px there is +-384 px of 1080p.
     */
    const val COARSE_RADIUS = 8

    /** The level under the coarsest re-searches this far, for small, fast objects it averaged away. */
    const val MIDDLE_RADIUS = 2

    /** The finer levels only refine, around a best candidate already within a pixel. */
    const val FINE_RADIUS = 1

    /**
     * Search radius in px at [level] of [levelCount] (0 the finest level).
     * Only the coarsest level searches wide. Every finer level first picks
     * the best of seven predictions - its parent's vector, the parent's four
     * neighbours', the previous pair's and zero - and only refines around it:
     * 9 positions at +-1, against 81 at +-4, and the sub-pixel fit finishes
     * the job. A wide window there mostly costs time and finds wrong matches
     * in repeating texture that the coarser levels had ruled out.
     * [judgement September 2026]
     */
    fun radius(level: Int, levelCount: Int): Int = when (levelCount - 1 - level) {
        0 -> COARSE_RADIUS
        1 -> MIDDLE_RADIUS
        else -> FINE_RADIUS
    }

    /**
     * Bilinear taps per side the LUMA pass takes for one texel of a level
     * [ratio] times smaller than its source: each tap already averages 2x2
     * source texels, so ceil(ratio / 2) of them cover the texel's footprint
     * (2, the plain 2x2 box, for a halving) and a big first step does not
     * alias. Capped so a 4K frame costs a bounded pass.
     */
    fun lumaTaps(ratio: Float): Int = kotlin.math.ceil(ratio / 2f).toInt().coerceIn(2, MAX_LUMA_TAPS)

    const val MAX_LUMA_TAPS = 8
}
