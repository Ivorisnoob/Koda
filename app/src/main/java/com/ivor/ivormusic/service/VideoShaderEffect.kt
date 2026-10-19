package com.ivor.ivormusic.service

import android.content.Context
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import com.ivor.ivormusic.data.VideoShaderCustomization
import com.ivor.ivormusic.data.VideoShaderPreset
import com.ivor.ivormusic.util.KLog

/**
 * What the main thread tells the GL thread: one immutable snapshot, swapped
 * whole, so a frame never draws one change's preset with another's sliders.
 */
class VideoShaderControl {
    @Volatile
    var settings: VideoShaderCustomization = VideoShaderCustomization()
}

/**
 * Opt-in looks for the video picture: a stage in Media3's effect graph that
 * draws each decoded frame through a fragment shader.
 *
 * Like Smooth motion, the graph is chosen once per player (see
 * [FrameInterpolationEffect]), so this is installed when the player is built
 * and everything after that is [VideoShaderControl], read per frame. With
 * nothing to draw the frame is copied.
 */
@UnstableApi
class VideoShaderEffect(
    private val control: VideoShaderControl
) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        VideoShaderProgram(control, useHdr)
}

/**
 * One program per look, built the first time it is asked for. Every built-in
 * look is the same three parts ([FRAGMENT_HEADER], its own `look()`,
 * [FRAGMENT_MAIN]), which is what lets Sharpen and the picture sliders tune
 * any of them rather than only the looks that happened to read them.
 *
 * A look that fails to build or draw on this GPU is left out for the rest of
 * the player's life and its frames are copied; it never fails playback.
 */
@UnstableApi
internal class VideoShaderProgram(
    private val control: VideoShaderControl,
    private val useHdr: Boolean
) : BaseGlShaderProgram(useHdr, 1) {

    private val resolution = FloatArray(2)

    private var copyPass: ShaderPass? = null
    private val presetPasses = HashMap<VideoShaderPreset, ShaderPass>()
    private val brokenPresets = HashSet<VideoShaderPreset>()

    /** The source [customPass] was built from, or failed to build from: one attempt per edit. */
    private var customSource: String? = null
    private var customPass: ShaderPass? = null

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        resolution[0] = inputWidth.toFloat()
        resolution[1] = inputHeight.toFloat()
        return Size(inputWidth, inputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        val settings = control.settings
        val pass = passFor(settings)
        if (pass != null) {
            try {
                pass.draw(inputTexId, resolution, presentationTimeUs, settings)
                return
            } catch (e: Exception) {
                // GlProgram answers a uniform the driver dropped with a
                // NullPointerException rather than a GL error, hence Exception.
                KLog.w(TAG, "${settings.activePreset} failed to draw, copying frames: ${e.message}")
                retire(settings.activePreset)
            }
        }
        try {
            copy().draw(inputTexId, resolution, presentationTimeUs, settings)
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e, presentationTimeUs)
        }
    }

    private fun passFor(settings: VideoShaderCustomization): ShaderPass? {
        // An HDR frame is linear light above 1.0 here, and every look is
        // written for an SDR picture: it is copied at full precision instead.
        if (useHdr || !settings.isEffectActive) return null
        val preset = settings.activePreset
        if (preset == VideoShaderPreset.CUSTOM) return customPassFor(settings.customShaderSource)
        if (preset in brokenPresets) return null
        presetPasses[preset]?.let { return it }
        return try {
            ShaderPass(FRAGMENT_HEADER + lookSource(preset) + FRAGMENT_MAIN).also { presetPasses[preset] = it }
        } catch (e: Exception) {
            KLog.w(TAG, "$preset did not compile on this GPU: ${e.message}")
            brokenPresets += preset
            null
        }
    }

    private fun customPassFor(source: String): ShaderPass? {
        if (source == customSource) return customPass
        customSource = source
        customPass?.let { runCatching { it.delete() } }
        customPass = if (source.isBlank()) {
            null
        } else {
            try {
                ShaderPass(source)
            } catch (e: Exception) {
                KLog.w(TAG, "Custom shader did not compile: ${e.message}")
                null
            }
        }
        return customPass
    }

    private fun retire(preset: VideoShaderPreset) {
        if (preset == VideoShaderPreset.CUSTOM) {
            customPass?.let { runCatching { it.delete() } }
            customPass = null
        } else {
            presetPasses.remove(preset)?.let { runCatching { it.delete() } }
            brokenPresets += preset
        }
    }

    private fun copy(): ShaderPass = copyPass ?: ShaderPass(COPY_FRAGMENT).also { copyPass = it }

    override fun release() {
        super.release()
        try {
            presetPasses.values.forEach { it.delete() }
            presetPasses.clear()
            customPass?.delete()
            customPass = null
            copyPass?.delete()
            copyPass = null
        } catch (e: GlUtil.GlException) {
            KLog.w(TAG, "Releasing shaders: ${e.message}")
        }
    }

    /**
     * A compiled look. `GlProgram` throws for a uniform the shader never read
     * (the driver drops it), so which ones survived is asked once, here, and
     * each is set only where it did.
     */
    private class ShaderPass(fragmentSource: String) {
        private val program = GlProgram(VERTEX_SHADER, fragmentSource).apply {
            setBufferAttribute(
                "aFramePosition",
                GlUtil.getNormalizedCoordinateBounds(),
                GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE
            )
        }
        private val hasFrame = has("uTex")
        private val hasResolution = has("uResolution")
        private val hasTime = has("uTime")
        private val hasSharpness = has("uSharpness")
        private val hasContrast = has("uContrast")
        private val hasBrightness = has("uBrightness")
        private val hasSaturation = has("uSaturation")

        private fun has(uniform: String): Boolean = program.getUniformLocation(uniform) >= 0

        fun draw(
            inputTexId: Int,
            resolution: FloatArray,
            presentationTimeUs: Long,
            settings: VideoShaderCustomization
        ) {
            program.use()
            if (hasFrame) program.setSamplerTexIdUniform("uTex", inputTexId, 0)
            if (hasResolution) program.setFloatsUniform("uResolution", resolution)
            // Wrapped, because a float holding hours of microseconds as seconds
            // steps too coarsely to animate grain by.
            if (hasTime) program.setFloatUniform("uTime", (presentationTimeUs % TIME_WRAP_US) / 1_000_000f)
            if (hasSharpness) {
                program.setFloatUniform(
                    "uSharpness",
                    if (settings.activePreset.sharpens) settings.sharpness else 0f
                )
            }
            if (hasContrast) program.setFloatUniform("uContrast", settings.contrast)
            if (hasBrightness) program.setFloatUniform("uBrightness", settings.brightness)
            if (hasSaturation) program.setFloatUniform("uSaturation", settings.saturation)
            program.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GlUtil.checkGlError()
        }

        fun delete() = program.delete()
    }

    private companion object {
        const val TAG = "VideoShader"

        /** An hour: long enough that the wrap is never seen, short enough to keep sub-millisecond steps. */
        const val TIME_WRAP_US = 3_600_000_000L
    }
}

/**
 * Checks a custom shader the way playback will build it, without a player:
 * compiled and linked against Koda's vertex stage on a throwaway context.
 */
object VideoShaderValidator {

    /**
     * The driver's complaint about [fragmentSource] (possibly blank, when it
     * gave no reason), or null when it builds. Null too when this phone would
     * not give a context to check on, since playback copies the frame for a
     * shader that fails there. Blocking: call it off the main thread.
     */
    fun check(fragmentSource: String): String? {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) return null
        // The display is the process's own, shared with every player, so it is
        // initialised (a no-op when it already is) and never terminated.
        val version = IntArray(2)
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) return null
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        val chosen = EGL14.eglChooseConfig(
            display,
            intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_NONE
            ),
            0, configs, 0, 1, count, 0
        )
        val config = configs[0]
        if (!chosen || count[0] == 0 || config == null) return null
        val context = EGL14.eglCreateContext(
            display, config, EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0
        )
        if (context == null || context == EGL14.EGL_NO_CONTEXT) return null
        val surface = EGL14.eglCreatePbufferSurface(
            display, config,
            intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0
        )
        try {
            if (surface == null || surface == EGL14.EGL_NO_SURFACE ||
                !EGL14.eglMakeCurrent(display, surface, surface, context)
            ) {
                return null
            }
            return build(fragmentSource)
        } finally {
            EGL14.eglMakeCurrent(
                display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT
            )
            if (surface != null && surface != EGL14.EGL_NO_SURFACE) {
                EGL14.eglDestroySurface(display, surface)
            }
            EGL14.eglDestroyContext(display, context)
            EGL14.eglReleaseThread()
        }
    }

    private fun build(fragmentSource: String): String? {
        val status = IntArray(1)
        val vertex = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER)
        val fragment = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER)
        val program = GLES20.glCreateProgram()
        try {
            GLES20.glShaderSource(fragment, fragmentSource)
            GLES20.glCompileShader(fragment)
            GLES20.glGetShaderiv(fragment, GLES20.GL_COMPILE_STATUS, status, 0)
            if (status[0] != GLES20.GL_TRUE) return GLES20.glGetShaderInfoLog(fragment).orEmpty().trim()

            GLES20.glShaderSource(vertex, VERTEX_SHADER)
            GLES20.glCompileShader(vertex)
            GLES20.glAttachShader(program, vertex)
            GLES20.glAttachShader(program, fragment)
            GLES20.glLinkProgram(program)
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
            if (status[0] != GLES20.GL_TRUE) return GLES20.glGetProgramInfoLog(program).orEmpty().trim()
            return null
        } finally {
            GLES20.glDeleteProgram(program)
            GLES20.glDeleteShader(vertex)
            GLES20.glDeleteShader(fragment)
        }
    }
}

private const val VERTEX_SHADER = """
attribute vec4 aFramePosition;
varying vec2 vTexCoord;
void main() {
  gl_Position = aFramePosition;
  vTexCoord = aFramePosition.xy * 0.5 + 0.5;
}
"""

// High precision throughout: at medium precision a texture coordinate has
// about ten bits, fewer than a 1080p frame has columns, and GPUs that honour
// the qualifier (Mali, PowerVR) then sample neighbouring pixels as one.
private const val FRAGMENT_PRECISION = """
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
"""

private const val COPY_FRAGMENT = FRAGMENT_PRECISION + """
uniform sampler2D uTex;
varying vec2 vTexCoord;
void main() {
  gl_FragColor = texture2D(uTex, vTexCoord);
}
"""

/**
 * What every built-in look starts from. `frameAt` is the frame, sharpened
 * while uSharpness is above zero: contrast-adaptive sharpening on a cross of
 * five taps, which backs off where the neighbourhood is already near black or
 * white, so edges gain definition without ringing.
 */
private const val FRAGMENT_HEADER = FRAGMENT_PRECISION + """
uniform sampler2D uTex;
uniform vec2 uResolution;
uniform float uTime;
uniform float uSharpness;
uniform float uContrast;
uniform float uBrightness;
uniform float uSaturation;
varying vec2 vTexCoord;

const vec3 LUMA = vec3(0.2126, 0.7152, 0.0722);

float lumaOf(vec3 c) {
  return dot(c, LUMA);
}

float hash(vec2 p) {
  return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

vec3 frameAt(vec2 uv) {
  vec3 c = texture2D(uTex, uv).rgb;
  if (uSharpness <= 0.0) return c;
  vec2 px = 1.0 / max(uResolution, vec2(1.0));
  vec3 a = texture2D(uTex, uv - vec2(0.0, px.y)).rgb;
  vec3 b = texture2D(uTex, uv - vec2(px.x, 0.0)).rgb;
  vec3 d = texture2D(uTex, uv + vec2(px.x, 0.0)).rgb;
  vec3 e = texture2D(uTex, uv + vec2(0.0, px.y)).rgb;
  vec3 lo = min(c, min(min(a, b), min(d, e)));
  vec3 hi = max(c, max(max(a, b), max(d, e)));
  vec3 amp = clamp(min(lo, 2.0 - hi) / max(hi, 0.001), 0.0, 1.0);
  vec3 w = -sqrt(amp) * (0.2 * uSharpness);
  return clamp((c + (a + b + d + e) * w) / (1.0 + 4.0 * w), 0.0, 1.0);
}

// Sobel edge strength on brightness, its taps `reach` pixels apart.
float edgeAt(vec2 uv, float reach) {
  vec2 d = reach / max(uResolution, vec2(1.0));
  vec3 grey = vec3(0.299, 0.587, 0.114);
  float tl = dot(texture2D(uTex, uv + vec2(-d.x, -d.y)).rgb, grey);
  float tr = dot(texture2D(uTex, uv + vec2( d.x, -d.y)).rgb, grey);
  float bl = dot(texture2D(uTex, uv + vec2(-d.x,  d.y)).rgb, grey);
  float br = dot(texture2D(uTex, uv + vec2( d.x,  d.y)).rgb, grey);
  float t  = dot(texture2D(uTex, uv + vec2( 0.0, -d.y)).rgb, grey);
  float b  = dot(texture2D(uTex, uv + vec2( 0.0,  d.y)).rgb, grey);
  float l  = dot(texture2D(uTex, uv + vec2(-d.x,  0.0)).rgb, grey);
  float r  = dot(texture2D(uTex, uv + vec2( d.x,  0.0)).rgb, grey);
  float h = -tl - 2.0 * t - tr + bl + 2.0 * b + br;
  float v = -tl - 2.0 * l - bl + tr + 2.0 * r + br;
  return sqrt(h * h + v * v);
}

// Scanlines counted per frame rather than per pixel, so a 4K video and a
// 360p one get the same lines instead of a moire and a set of bars.
float scanlinesAt(float y) {
  return min(max(uResolution.y, 1.0) * 0.5, 240.0) * y * 6.2832;
}

// A soft copy of the frame: thirteen taps on two rings, the outer one `spread`
// of the frame's height away, so the softness is the same at 360p and at 4K.
vec3 softAt(vec2 uv, float spread) {
  vec2 res = max(uResolution, vec2(1.0));
  vec2 r = spread * vec2(res.y / res.x, 1.0);
  vec3 sum = texture2D(uTex, uv).rgb * 2.0;
  sum += texture2D(uTex, uv + r * vec2( 1.0,  0.0)).rgb;
  sum += texture2D(uTex, uv + r * vec2(-1.0,  0.0)).rgb;
  sum += texture2D(uTex, uv + r * vec2( 0.0,  1.0)).rgb;
  sum += texture2D(uTex, uv + r * vec2( 0.0, -1.0)).rgb;
  sum += texture2D(uTex, uv + r * vec2( 0.7,  0.7)).rgb;
  sum += texture2D(uTex, uv + r * vec2(-0.7,  0.7)).rgb;
  sum += texture2D(uTex, uv + r * vec2( 0.7, -0.7)).rgb;
  sum += texture2D(uTex, uv + r * vec2(-0.7, -0.7)).rgb;
  sum += texture2D(uTex, uv + r * vec2( 0.35,  0.35)).rgb * 1.5;
  sum += texture2D(uTex, uv + r * vec2(-0.35,  0.35)).rgb * 1.5;
  sum += texture2D(uTex, uv + r * vec2( 0.35, -0.35)).rgb * 1.5;
  sum += texture2D(uTex, uv + r * vec2(-0.35, -0.35)).rgb * 1.5;
  return sum / 16.0;
}

// The frame's edge darkening toward its corners; 0 leaves it alone.
float vignetteAt(vec2 uv, float strength) {
  vec2 v = (uv - 0.5) * 2.0;
  return clamp(1.0 - dot(v, v) * strength, 0.0, 1.0);
}

// A 4x4 ordered-dither threshold for the cell at integer position p, 0 to 1.
float bayer2(vec2 p) {
  p = floor(p);
  return fract(p.x / 2.0 + p.y * p.y * 0.75);
}
float bayer4(vec2 p) {
  return bayer2(0.5 * p) * 0.25 + bayer2(p);
}
"""

/** The picture sliders, after the look, so they tune all of them alike. */
private const val FRAGMENT_MAIN = """
void main() {
  vec3 rgb = look(vTexCoord) + uBrightness;
  rgb = (rgb - 0.5) * uContrast + 0.5;
  rgb = mix(vec3(lumaOf(rgb)), rgb, uSaturation);
  gl_FragColor = vec4(clamp(rgb, 0.0, 1.0), 1.0);
}
"""

private fun lookSource(preset: VideoShaderPreset): String = when (preset) {
    // Custom is the user's whole shader and never reaches here.
    VideoShaderPreset.NONE, VideoShaderPreset.CUSTOM -> LOOK_ORIGINAL
    VideoShaderPreset.VIVID -> LOOK_VIVID
    VideoShaderPreset.CLARITY -> LOOK_CLARITY
    VideoShaderPreset.FILM -> LOOK_FILM
    VideoShaderPreset.NOIR -> LOOK_NOIR
    VideoShaderPreset.ANIME -> LOOK_ANIME
    VideoShaderPreset.NEON -> LOOK_NEON
    VideoShaderPreset.DREAM -> LOOK_DREAM
    VideoShaderPreset.VINTAGE -> LOOK_VINTAGE
    VideoShaderPreset.VHS -> LOOK_VHS
    VideoShaderPreset.CRT -> LOOK_CRT
    VideoShaderPreset.PIXEL -> LOOK_PIXEL
    VideoShaderPreset.COMIC -> LOOK_COMIC
    VideoShaderPreset.SKETCH -> LOOK_SKETCH
    VideoShaderPreset.NIGHT_VISION -> LOOK_NIGHT_VISION
}

/** The frame as it is, for Sharpen and the picture sliders on their own. */
private const val LOOK_ORIGINAL = """
vec3 look(vec2 uv) {
  return frameAt(uv);
}
"""

/**
 * Vibrance rather than saturation: muted colours gain the most and ones that
 * are already rich are left alone, so a washed-out video comes alive without
 * faces turning orange.
 */
private const val LOOK_VIVID = """
vec3 look(vec2 uv) {
  vec3 rgb = frameAt(uv);
  float rich = max(rgb.r, max(rgb.g, rgb.b)) - min(rgb.r, min(rgb.g, rgb.b));
  rgb = mix(vec3(lumaOf(rgb)), rgb, 1.0 + 0.6 * (1.0 - rich));
  return mix(rgb, smoothstep(0.0, 1.0, rgb), 0.3);
}
"""

/**
 * Local contrast: each pixel pushed away from the soft copy of its
 * neighbourhood, which separates shapes the way an HDR picture does. Weighted
 * to the midtones so blacks and whites do not clip.
 */
private const val LOOK_CLARITY = """
vec3 look(vec2 uv) {
  vec3 rgb = frameAt(uv);
  float l = lumaOf(rgb);
  float detail = l - lumaOf(softAt(uv, 0.03));
  rgb += detail * 0.85 * (0.3 + 2.8 * l * (1.0 - l));
  return mix(vec3(lumaOf(rgb)), rgb, 1.1);
}
"""

private const val LOOK_FILM = """
vec3 look(vec2 uv) {
  vec3 rgb = frameAt(uv);
  float l = lumaOf(rgb);
  // Teal into the shadows, warmth into the highlights, then a gentle S-curve.
  rgb = mix(rgb + vec3(-0.01, 0.035, 0.07) * (1.0 - l), rgb * vec3(1.07, 0.99, 0.88), l);
  rgb = mix(rgb, smoothstep(0.0, 1.0, rgb), 0.55);
  // Print stock never reaches pure black.
  rgb = rgb * 0.97 + 0.02;
  // Grain, strongest in the midtones where film shows it.
  vec2 cell = uv * max(uResolution, vec2(1.0)) * 0.75 + vec2(uTime * 17.0, uTime * 23.0);
  rgb += (hash(cell) - 0.5) * 0.05 * (1.0 - abs(l - 0.5) * 1.5);
  return rgb * vignetteAt(uv, 0.22);
}
"""

private const val LOOK_NOIR = """
vec3 look(vec2 uv) {
  // Weighted as a red filter over the lens: skin glows, skies and shadows fall away.
  float l = dot(frameAt(uv), vec3(0.45, 0.45, 0.10));
  l = mix(l, smoothstep(0.0, 1.0, l), 0.8);
  vec2 cell = uv * max(uResolution, vec2(1.0)) * 0.8 + vec2(uTime * 19.0, uTime * 29.0);
  l += (hash(cell) - 0.5) * 0.06;
  return vec3(l) * vignetteAt(uv, 0.36);
}
"""

/**
 * For animation: line art is drawn darker and crisper and the flat colours a
 * little richer, which is what a compressed stream takes away first. A pixel
 * counts as line when it is darker than the four around it.
 */
private const val LOOK_ANIME = """
vec3 look(vec2 uv) {
  vec2 px = 1.25 / max(uResolution, vec2(1.0));
  vec3 rgb = frameAt(uv);
  vec3 around = (texture2D(uTex, uv + vec2(px.x, 0.0)).rgb
    + texture2D(uTex, uv - vec2(px.x, 0.0)).rgb
    + texture2D(uTex, uv + vec2(0.0, px.y)).rgb
    + texture2D(uTex, uv - vec2(0.0, px.y)).rgb) * 0.25;
  float line = smoothstep(0.015, 0.16, lumaOf(around) - lumaOf(rgb));
  rgb += (rgb - around) * 0.55;
  rgb *= 1.0 - 0.4 * line;
  return mix(vec3(lumaOf(rgb)), rgb, 1.16);
}
"""

/**
 * The picture's brightness mapped onto a violet, magenta and cyan ramp, laid
 * over the picture's own colour so faces still read.
 */
private const val LOOK_NEON = """
vec3 look(vec2 uv) {
  vec3 rgb = frameAt(uv);
  float l = smoothstep(0.02, 0.95, lumaOf(rgb));
  vec3 low = vec3(0.07, 0.02, 0.30);
  vec3 mid = vec3(0.92, 0.12, 0.62);
  vec3 high = vec3(0.35, 0.95, 1.0);
  vec3 tone = l < 0.5 ? mix(low, mid, l * 2.0) : mix(mid, high, l * 2.0 - 1.0);
  rgb = mix(rgb, tone, 0.55);
  return mix(rgb, smoothstep(0.0, 1.0, rgb), 0.4);
}
"""

/** A soft copy of the picture screened over itself: highlights bloom and edges melt. */
private const val LOOK_DREAM = """
vec3 look(vec2 uv) {
  vec3 rgb = frameAt(uv);
  vec3 soft = softAt(uv, 0.035);
  vec3 glow = soft * soft * 0.6;
  rgb = 1.0 - (1.0 - rgb) * (1.0 - glow);
  rgb = mix(rgb, soft, 0.28);
  // The screen lifts everything, so the curve puts the shadows back.
  rgb = mix(rgb, smoothstep(0.0, 1.0, rgb), 0.45);
  return rgb * vec3(1.03, 1.0, 1.02);
}
"""

/**
 * Old home-movie film: faded toward sepia, flickering and weaving in the gate
 * as each frame is pulled down, with grain and the odd scratch.
 */
private const val LOOK_VINTAGE = """
vec3 look(vec2 uv) {
  // Eighteen frames a second, the rate the film itself ran at.
  float tick = floor(uTime * 18.0);
  uv += (vec2(hash(vec2(tick, 1.0)), hash(vec2(tick, 2.0))) - 0.5) * 0.004;
  vec3 rgb = texture2D(uTex, uv).rgb;
  float l = lumaOf(rgb);
  rgb = mix(rgb, l * vec3(1.12, 0.93, 0.68), 0.62);
  rgb = rgb * 0.84 + 0.08;
  rgb *= 0.93 + 0.07 * hash(vec2(tick, 3.0));
  rgb += (hash(uv * max(uResolution, vec2(1.0)) * 0.6 + tick) - 0.5) * 0.10;
  // A scratch is a thin bright line that lives for one frame.
  for (int i = 0; i < 2; i++) {
    float seed = tick + float(i) * 7.0;
    float there = step(0.55, hash(vec2(seed, 4.0)));
    float off = abs(uv.x - hash(vec2(seed, 5.0)));
    rgb += there * (1.0 - smoothstep(0.0, 0.0012, off)) * 0.22;
  }
  return rgb * vignetteAt(uv, 0.42);
}
"""

/**
 * Tape: colour smeared sideways while brightness stays, every line a hair out
 * of place, a tracking band rolling through and noise where the heads switch
 * at the bottom of the picture.
 */
private const val LOOK_VHS = """
vec3 look(vec2 uv) {
  float row = floor(uv.y * 240.0);
  float field = floor(uTime * 30.0);
  uv.x += (hash(vec2(row, field)) - 0.5) * 0.0016;
  // The tracking band, a strip a twentieth of the picture tall, rolling upward.
  float along = fract(uv.y + uTime * 0.11);
  float band = smoothstep(0.0, 0.015, along) * (1.0 - smoothstep(0.03, 0.05, along));
  uv.x += band * (hash(vec2(row, field + 9.0)) - 0.5) * 0.035;
  // Head switching: the last few lines lean over.
  float foot = 1.0 - smoothstep(0.0, 0.025, uv.y);
  uv.x += foot * (0.02 + (hash(vec2(row, field + 5.0)) - 0.5) * 0.04);

  vec3 here = texture2D(uTex, uv).rgb;
  vec3 smear = (here
    + texture2D(uTex, uv + vec2(0.0035, 0.0)).rgb
    + texture2D(uTex, uv + vec2(0.0070, 0.0)).rgb
    + texture2D(uTex, uv - vec2(0.0035, 0.0)).rgb
    + texture2D(uTex, uv - vec2(0.0070, 0.0)).rgb) * 0.2;
  float l = mix(lumaOf(here), lumaOf(smear), 0.35);
  vec3 rgb = vec3(l) + (smear - lumaOf(smear)) * 1.15;

  rgb += (hash(uv * 300.0 + uTime) - 0.5) * 0.07;
  rgb += (band + foot) * (hash(vec2(row * 3.0, uv.x * 40.0 + uTime)) - 0.35) * 0.22;
  return (rgb * 0.9 + 0.05) * vec3(1.03, 1.0, 0.95);
}
"""

private const val LOOK_CRT = """
vec3 look(vec2 uv) {
  // Bulge the picture as curved glass does; outside it is the tube's bezel.
  vec2 c = (uv - 0.5) * 2.1;
  vec2 p = c * (1.0 + pow(abs(c.yx) * 0.16, vec2(2.0))) * 0.5 + 0.5;
  if (p.x < 0.0 || p.x > 1.0 || p.y < 0.0 || p.y > 1.0) return vec3(0.0);
  // Red and blue land a hair either side of green.
  vec3 rgb = vec3(
    texture2D(uTex, p + vec2(0.0016, 0.0)).r,
    texture2D(uTex, p).g,
    texture2D(uTex, p - vec2(0.0016, 0.0)).b);
  // Scanlines, thinner where the picture is bright, as a hot beam spreads.
  float beam = sin(scanlinesAt(p.y)) * 0.5 + 0.5;
  rgb *= mix(0.62 + 0.38 * beam, 1.0, lumaOf(rgb) * 0.6) * 1.12;
  // The refresh, a faint brighter bar drifting up the screen.
  float bar = fract(p.y - uTime * 0.07);
  rgb *= 1.0 + 0.05 * (1.0 - smoothstep(0.0, 0.12, bar));
  float corners = p.x * p.y * (1.0 - p.x) * (1.0 - p.y) * 16.0;
  return rgb * clamp(pow(corners, 0.22), 0.0, 1.0);
}
"""

/**
 * Square blocks, at most 160 across, in a short palette, with the steps
 * between its colours broken up by an ordered dither as a console of the time
 * had to. The dither is half strength: at full strength flat areas turn to
 * a checkerboard of speckles rather than a picture.
 */
private const val LOOK_PIXEL = """
vec3 look(vec2 uv) {
  vec2 res = max(uResolution, vec2(1.0));
  float across = min(res.x, 160.0);
  vec2 cell = vec2(1.0 / across, res.x / (across * res.y));
  vec2 index = floor(uv / cell);
  vec3 rgb = texture2D(uTex, (index + 0.5) * cell).rgb;
  rgb = mix(vec3(lumaOf(rgb)), rgb, 1.25);
  return floor(rgb * 5.0 + 0.5 + (bayer4(index) - 0.47) * 0.4) / 5.0;
}
"""

/**
 * Print: flat steps of richer colour, a black line on every edge and halftone
 * dots that grow as the picture darkens.
 */
private const val LOOK_COMIC = """
vec3 look(vec2 uv) {
  vec2 res = max(uResolution, vec2(1.0));
  vec3 rgb = frameAt(uv);
  float l = lumaOf(rgb);
  // Brightness in five steps with the colour kept whole: stepping each
  // channel on its own turns skin into patches of olive and rust.
  float stepped = (floor(l * 4.0 + 0.5) + 0.35) / 4.35;
  rgb = mix(vec3(l), rgb, 1.5) * (stepped / max(l, 0.02));
  // Dots on a grid turned 45 degrees, counted per frame height, in the
  // darker half of the picture only.
  vec2 g = vec2(uv.x * res.x / res.y, uv.y) * min(res.y * 0.25, 130.0);
  g = vec2(g.x - g.y, g.x + g.y) * 0.7071;
  float dotSize = sqrt(clamp(0.62 - l, 0.0, 1.0)) * 0.7;
  float dots = 1.0 - smoothstep(dotSize - 0.08, dotSize + 0.08, length(fract(g) - 0.5));
  rgb *= 1.0 - 0.28 * dots;
  float ink = smoothstep(0.26, 0.42, edgeAt(uv, 1.5));
  return mix(rgb, vec3(0.05, 0.04, 0.07), ink);
}
"""

/**
 * Pencil on paper: graphite where the picture has an edge, hatching laid in
 * as it darkens and crossed where it is darkest.
 */
private const val LOOK_SKETCH = """
vec3 look(vec2 uv) {
  vec2 res = max(uResolution, vec2(1.0));
  float l = dot(texture2D(uTex, uv).rgb, vec3(0.299, 0.587, 0.114));
  float paper = 1.0 - smoothstep(0.04, 0.4, edgeAt(uv, 1.2)) * 0.9;
  // Strokes counted per frame height, so they are pencil-width at any size.
  vec2 p = vec2(uv.x * res.x / res.y, uv.y) * min(res.y * 0.5, 300.0);
  float stroke = sin((p.x + p.y) * 2.2) * 0.5 + 0.5;
  float crossed = sin((p.x - p.y) * 2.2) * 0.5 + 0.5;
  paper -= (1.0 - smoothstep(0.3, 0.72, l)) * stroke * 0.36;
  paper -= (1.0 - smoothstep(0.08, 0.38, l)) * crossed * 0.34;
  paper += (hash(uv * res) - 0.5) * 0.04;
  return mix(vec3(0.17, 0.17, 0.21), vec3(0.96, 0.95, 0.91), clamp(paper, 0.0, 1.0));
}
"""

/**
 * An image intensifier: brightness amplified onto green phosphor, bright
 * things flaring, sensor noise over all of it and the dark ring of the tube.
 */
private const val LOOK_NIGHT_VISION = """
vec3 look(vec2 uv) {
  float l = dot(texture2D(uTex, uv).rgb, vec3(0.299, 0.587, 0.114));
  float flare = lumaOf(softAt(uv, 0.02));
  l = pow(l, 0.7) * 1.3 + flare * flare * 0.35;
  l += (hash(uv * 400.0 + uTime * 8.0) - 0.5) * 0.16;
  l *= 0.9 + 0.1 * sin(scanlinesAt(uv.y));
  return vec3(0.10, 1.0, 0.30) * l * vignetteAt(uv, 0.55);
}
"""
