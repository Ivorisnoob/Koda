package com.ivor.ivormusic.data

import androidx.annotation.StringRes
import com.ivor.ivormusic.R

/**
 * The looks the video player can draw a frame in.
 *
 * [id] is what is stored, so it is frozen; the constant names are free to
 * change. [titleRes] is one or two short words on purpose: the same name is a
 * cell a quarter of the playback panel wide, and a long one breaks mid-word
 * there.
 */
enum class VideoShaderPreset(
    val id: String,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    /**
     * Whether the Sharpen slider reaches this look. The ones that rebuild the
     * picture out of blocks, noise or shifted colour channels would only
     * sharpen what they are about to destroy, so they skip it.
     */
    val sharpens: Boolean = true
) {
    NONE("none", R.string.vsp_preset_none, R.string.vsp_preset_none_sub),

    // Grades: the same picture, better or moodier. Listed in the order they
    // are shown, four to a row in the playback panel.
    VIVID("vivid", R.string.vsp_preset_vivid, R.string.vsp_preset_vivid_sub),
    CLARITY("clarity", R.string.vsp_preset_clarity, R.string.vsp_preset_clarity_sub),
    FILM("film", R.string.vsp_preset_film, R.string.vsp_preset_film_sub),
    NOIR("noir", R.string.vsp_preset_noir, R.string.vsp_preset_noir_sub),
    ANIME("anime", R.string.vsp_preset_anime, R.string.vsp_preset_anime_sub),
    NEON("neon", R.string.vsp_preset_neon, R.string.vsp_preset_neon_sub),
    DREAM("dream", R.string.vsp_preset_dream, R.string.vsp_preset_dream_sub),

    // Another medium altogether.
    VINTAGE("vintage", R.string.vsp_preset_vintage, R.string.vsp_preset_vintage_sub, sharpens = false),
    VHS("vhs", R.string.vsp_preset_vhs, R.string.vsp_preset_vhs_sub, sharpens = false),
    CRT("crt", R.string.vsp_preset_crt, R.string.vsp_preset_crt_sub, sharpens = false),
    PIXEL("pixel", R.string.vsp_preset_pixel, R.string.vsp_preset_pixel_sub, sharpens = false),
    COMIC("comic", R.string.vsp_preset_comic, R.string.vsp_preset_comic_sub),
    SKETCH("sketch", R.string.vsp_preset_sketch, R.string.vsp_preset_sketch_sub, sharpens = false),
    NIGHT_VISION(
        "night_vision", R.string.vsp_preset_night, R.string.vsp_preset_night_sub, sharpens = false
    ),

    /** The user's own fragment shader, which is the whole picture: no slider reaches it. */
    CUSTOM("custom", R.string.vsp_preset_custom, R.string.vsp_preset_custom_sub, sharpens = false);

    companion object {
        fun fromId(id: String?): VideoShaderPreset = entries.firstOrNull { it.id == id } ?: NONE
    }
}

/** A shader to start a custom one from. */
class VideoShaderTemplate(@StringRes val titleRes: Int, val source: String)

/**
 * Everything the video shader stage draws from: whether it is installed at
 * all, the look, and the picture sliders that tune every built-in look.
 */
data class VideoShaderCustomization(
    /**
     * Whether players are built with the shader stage. It joins the effect
     * graph when a player is built, so a change waits for the next one.
     */
    val masterEnabled: Boolean = false,
    val activePreset: VideoShaderPreset = VideoShaderPreset.NONE,
    /** Contrast-adaptive sharpening, 0 (off) to 1. */
    val sharpness: Float = SHARPNESS_OFF,
    /** Multiplier around mid grey, [CONTRAST_RANGE]. */
    val contrast: Float = NEUTRAL_CONTRAST,
    /** Added to every channel, [BRIGHTNESS_RANGE]. */
    val brightness: Float = NEUTRAL_BRIGHTNESS,
    /** 0 is black and white, 1 the video's own colour, [SATURATION_RANGE]. */
    val saturation: Float = NEUTRAL_SATURATION,
    /** The fragment shader [VideoShaderPreset.CUSTOM] runs. */
    val customShaderSource: String = DEFAULT_CUSTOM_SHADER
) {
    /** Every slider where it leaves the picture alone. */
    val isPictureNeutral: Boolean
        get() = sharpness == SHARPNESS_OFF &&
            contrast == NEUTRAL_CONTRAST &&
            brightness == NEUTRAL_BRIGHTNESS &&
            saturation == NEUTRAL_SATURATION

    /** Whether a frame comes out different from how it went in. */
    val isEffectActive: Boolean
        get() = masterEnabled && (activePreset != VideoShaderPreset.NONE || !isPictureNeutral)

    fun withNeutralPicture(): VideoShaderCustomization = copy(
        sharpness = SHARPNESS_OFF,
        contrast = NEUTRAL_CONTRAST,
        brightness = NEUTRAL_BRIGHTNESS,
        saturation = NEUTRAL_SATURATION
    )

    companion object {
        const val SHARPNESS_OFF = 0f
        const val NEUTRAL_CONTRAST = 1f
        const val NEUTRAL_BRIGHTNESS = 0f
        const val NEUTRAL_SATURATION = 1f

        // Each range is centred on its neutral value, so the slider's middle
        // is "untouched" and a detent can sit there.
        val SHARPNESS_RANGE = 0f..1f
        val CONTRAST_RANGE = 0.5f..1.5f
        val BRIGHTNESS_RANGE = -0.4f..0.4f
        val SATURATION_RANGE = 0f..2f

        val DEFAULT_CUSTOM_SHADER: String = """
// uTex         the video frame
// vTexCoord    where this pixel is in it, 0.0 to 1.0
// uResolution  the frame's size in pixels
// uTime        a clock in seconds, for anything that moves

precision highp float;
uniform sampler2D uTex;
uniform vec2 uResolution;
uniform float uTime;
varying vec2 vTexCoord;

void main() {
    vec4 color = texture2D(uTex, vTexCoord);

    // Darken toward the corners.
    vec2 fromCentre = vTexCoord - 0.5;
    color.rgb *= 1.0 - dot(fromCentre, fromCentre) * 0.45;

    gl_FragColor = color;
}
""".trimIndent()

        val STARTER_TEMPLATES: List<VideoShaderTemplate> = listOf(
            VideoShaderTemplate(
                R.string.vst_vignette,
                """
precision highp float;
uniform sampler2D uTex;
varying vec2 vTexCoord;

void main() {
    vec4 color = texture2D(uTex, vTexCoord);
    vec2 fromCentre = vTexCoord - 0.5;
    color.rgb *= clamp(1.0 - dot(fromCentre, fromCentre) * 0.7, 0.0, 1.0);
    color.r *= 1.08;
    color.b *= 0.94;
    gl_FragColor = color;
}
""".trimIndent()
            ),
            VideoShaderTemplate(
                R.string.vst_invert,
                """
precision highp float;
uniform sampler2D uTex;
varying vec2 vTexCoord;

void main() {
    vec4 color = texture2D(uTex, vTexCoord);
    gl_FragColor = vec4(1.0 - color.rgb, color.a);
}
""".trimIndent()
            ),
            VideoShaderTemplate(
                R.string.vst_sepia,
                """
precision highp float;
uniform sampler2D uTex;
varying vec2 vTexCoord;

void main() {
    vec4 color = texture2D(uTex, vTexCoord);
    float luma = dot(color.rgb, vec3(0.299, 0.587, 0.114));
    vec3 sepia = vec3(luma * 1.2, luma, luma * 0.8);
    gl_FragColor = vec4(clamp(sepia, 0.0, 1.0), color.a);
}
""".trimIndent()
            ),
            VideoShaderTemplate(
                R.string.vst_edge_glow,
                """
precision highp float;
uniform sampler2D uTex;
uniform vec2 uResolution;
varying vec2 vTexCoord;

void main() {
    vec2 texel = 1.5 / uResolution;
    vec3 here = texture2D(uTex, vTexCoord).rgb;
    vec3 right = texture2D(uTex, vTexCoord + vec2(texel.x, 0.0)).rgb;
    vec3 below = texture2D(uTex, vTexCoord + vec2(0.0, texel.y)).rgb;
    float edge = length(abs(here - right) + abs(here - below));
    gl_FragColor = vec4(mix(here, vec3(0.2, 0.9, 1.0), clamp(edge * 2.0, 0.0, 1.0)), 1.0);
}
""".trimIndent()
            ),
            VideoShaderTemplate(
                R.string.vst_wobble,
                """
precision highp float;
uniform sampler2D uTex;
uniform float uTime;
varying vec2 vTexCoord;

void main() {
    vec2 uv = vTexCoord;
    uv.x += sin(uv.y * 20.0 + uTime * 4.0) * 0.01;
    gl_FragColor = texture2D(uTex, uv);
}
""".trimIndent()
            )
        )
    }
}
