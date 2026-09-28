package com.ivor.ivormusic.service

import android.opengl.GLES20
import androidx.media3.common.C
import androidx.media3.common.GlTextureInfo
import androidx.media3.common.util.GlUtil
import com.ivor.ivormusic.util.KLog

/**
 * The motion half of Smooth motion: everything between "here are two decoded
 * frames" and "here is the frame at phase t". [FrameInterpolationShaderProgram]
 * owns the clock, the output pool and the reference copy, and asks an engine
 * for the drawn frames, so the two engines ([ComputeMotionEngine] on GLES 3.1+,
 * [FragmentMotionEngine] everywhere else) share all of that.
 *
 * Every call runs on Media3's GL thread with the pipeline's context current.
 * Any exception means "this engine cannot run here": the host drops to the
 * next engine down, and only when the last one fails does interpolation stop.
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

    /** Draws the frame [phase] of the way from [reference] (A) to [current] (B) into [target]. */
    fun compose(reference: GlTextureInfo, current: GlTextureInfo, target: GlTextureInfo, phase: Float)

    /** The next pair does not follow this one (a seek, a gap): drop temporal hints. */
    fun forgetHistory()

    /**
     * The share of the last pair that found no good match, 0 to 1, or -1 when
     * unknown. May wait for the GPU, so the host calls it rarely.
     */
    fun sampleUnmatched(): Float

    fun release()
}

/** GL helpers both engines use. */
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
