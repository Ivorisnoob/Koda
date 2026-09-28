package com.ivor.ivormusic.service

import android.opengl.GLES20
import androidx.media3.common.C
import androidx.media3.common.GlTextureInfo
import androidx.media3.common.util.GlUtil
import com.ivor.ivormusic.util.KLog

/**
 * The motion half of Smooth motion: everything between "here are two decoded
 * frames" and "here is the frame at phase t". [FrameInterpolationShaderProgram]
 * owns the clock, the output pool and the reference copy, and asks the engine
 * ([ComputeMotionEngine], GLES 3.2) for the drawn frames.
 *
 * Every call runs on Media3's GL thread with the pipeline's context current.
 * Any exception means "this engine cannot run here": the host stops
 * interpolating and passes frames through. There is no lesser engine to fall
 * back to, on purpose.
 */
internal interface MotionEngine {
    /** For the log and the player's read-out. */
    val name: String

    /** Compiles everything; throws when this GPU cannot run the engine. */
    fun build()

    /** Allocates for a [width] x [height] stream, releasing any previous size. */
    fun configure(width: Int, height: Int)

    /** Prepares [input], about to become the kept frame A, from scratch. */
    fun prepareReference(input: GlTextureInfo)

    /** Prepares [current] as B and estimates the motion between A and B. */
    fun estimate(current: GlTextureInfo)

    /** After a pair: B's prepared data becomes the next pair's A. */
    fun promoteCurrent()

    /**
     * Draws the frame [phase] of the way from [reference] (A) to [current] (B)
     * into [target]. [step] is the distance between output ticks in the same
     * units (a 24 fps pair drawn at 60 fps: 0.4), which the engine's re-timing
     * rules are written in.
     */
    fun compose(reference: GlTextureInfo, current: GlTextureInfo, target: GlTextureInfo, phase: Float, step: Float)

    /** The next pair does not follow this one (a seek, a gap): drop temporal hints. */
    fun forgetHistory()

    /**
     * Starts reading how hard the pair just estimated was, without waiting for
     * the GPU; does nothing while an earlier reading is still in flight.
     */
    fun requestHardness()

    /**
     * The reading [requestHardness] started, 0 to 1 (1 a scene cut), once the
     * GPU has got that far; -1 while it is still in flight or when none was
     * asked for. Never waits for the GPU, so the host may call it every pair.
     */
    fun takeHardness(): Float

    /**
     * Diagnostic: the next pair and its ticks wait for the GPU after every
     * stage and record how long each took. Stalls that pair, so the host asks
     * for it rarely.
     */
    fun startProfile()

    /** The report of the last profiled pair, once, or null. */
    fun takeProfile(): String?

    fun release()
}

/** GL helpers the engine and its host share. */
internal object MotionGl {
    const val TAG = "FrameInterpolation"

    /** A GL_RGBA8 (or half-float) texture with an FBO, the way Media3 makes them. */
    fun texture(width: Int, height: Int, highPrecision: Boolean = false): GlTextureInfo {
        val texId = GlUtil.createTexture(width, height, highPrecision)
        val fboId = GlUtil.createFboForTexture(texId)
        return GlTextureInfo(texId, fboId, C.INDEX_UNSET, width, height)
    }

    fun releaseQuietly(texture: GlTextureInfo?) {
        texture ?: return
        try {
            texture.release()
        } catch (e: GlUtil.GlException) {
            KLog.w(TAG, "Could not release a texture: ${e.message}")
        }
    }

    fun texel(texture: GlTextureInfo) = floatArrayOf(1f / texture.width, 1f / texture.height)

    /** The context's GLES version as major * 10 + minor, 20 when unreadable. */
    fun glesVersion(): Int {
        val version = GLES20.glGetString(GLES20.GL_VERSION) ?: return 20
        val match = Regex("""OpenGL ES (\d+)\.(\d+)""").find(version) ?: return 20
        return match.groupValues[1].toInt() * 10 + match.groupValues[2].toInt()
    }
}
