package com.ivor.ivormusic.widget

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.size
import androidx.glance.unit.ColorProvider

/**
 * Shared drawing helpers for the widget family. Widgets cannot run real
 * animation, so the expressiveness lives in shape and color states instead:
 * artwork-derived scrims, pill-shaped hero play buttons, and tinted toggles.
 */

/** Resolve a Glance [ColorProvider] to an actual [Color] at composition time. */
@androidx.compose.runtime.Composable
internal fun ColorProvider.read(): Color =
    getColor(LocalContext.current)

/**
 * Dominant vibrant color of an album cover, extracted by hand rather than via
 * the Palette library: a 24x24 sample bucketed into hue bins is plenty for a
 * scrim or accent, and avoids a whole dependency for one number. Null when the
 * bitmap is missing or genuinely colorless - callers fall back to theme colors.
 */
internal fun extractAccentColor(bitmap: Bitmap?): Color? {
    bitmap ?: return null
    val sample = 24
    val step = maxOf(1, minOf(bitmap.width, bitmap.height) / sample)
    var totalWeight = 0f
    // 18 hue bins; per-bin weight favors saturated, mid-bright pixels, which is
    // where a cover's identity color lives.
    val weights = FloatArray(18)
    val sumS = FloatArray(18)
    val sumV = FloatArray(18)
    val hsv = FloatArray(3)
    var y = 0
    while (y < bitmap.height) {
        var x = 0
        while (x < bitmap.width) {
            android.graphics.Color.colorToHSV(bitmap.getPixel(x, y), hsv)
            val s = hsv[1]
            val v = hsv[2]
            if (v > 0.15f && v < 0.98f) {
                val w = s * (1f - kotlin.math.abs(v - 0.6f))
                if (w > 0f) {
                    val bin = (hsv[0] / 20f).toInt().coerceIn(0, 17)
                    weights[bin] += w
                    sumS[bin] += s * w
                    sumV[bin] += v * w
                    totalWeight += w
                }
            }
            x += step
        }
        y += step
    }
    if (totalWeight <= 0f) return null
    var best = 0
    for (i in 1 until weights.size) if (weights[i] > weights[best]) best = i
    // A near-gray winner means the art has no real color to lend.
    if (weights[best] / totalWeight < 0.08f) return null
    val s = (sumS[best] / weights[best]).coerceIn(0.25f, 0.85f)
    val v = (sumV[best] / weights[best]).coerceIn(0.35f, 0.75f)
    return Color(android.graphics.Color.HSVToColor(floatArrayOf(best * 20f + 10f, s, v)))
}

/** Same hue, opaque alpha channel: widgets draw scrims as solid ARGB colors. */
internal fun withAlpha(color: Color, alpha: Int): Color =
    color.copy(alpha = alpha / 255f)

/** White or black text, whichever the scrim underneath can carry. */
internal fun readableOn(background: Color): Color {
    val bgInt = android.graphics.Color.argb(
        255,
        (background.red * 255).toInt(),
        (background.green * 255).toInt(),
        (background.blue * 255).toInt()
    )
    return if (android.graphics.Color.luminance(bgInt) > 0.4) Color.Black else Color.White
}

/**
 * Pre-rounded bitmap. Glance's cornerRadius clips solid backgrounds but not
 * image ones on every launcher, so full-bleed artwork corners are baked into
 * the bitmap itself and cornerRadius stays on for the launchers that do honor it.
 */
internal fun roundedBitmap(src: Bitmap, radiusFractionOfMinSide: Float = 0.12f): Bitmap {
    val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val radius = minOf(src.width, src.height) * radiusFractionOfMinSide
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    }
    canvas.drawRoundRect(
        RectF(0f, 0f, src.width.toFloat(), src.height.toFloat()),
        radius,
        radius,
        paint
    )
    return out
}

internal fun formatTime(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSeconds = ms / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

/**
 * One transport control: a glyph inside a round hit target that comfortably
 * clears 48dp without ballooning the widget's footprint.
 */
@androidx.compose.runtime.Composable
internal fun TransportButton(
    iconRes: Int,
    description: String,
    action: Action,
    buttonSize: Dp = 48.dp,
    glyphSize: Dp = 24.dp,
    container: ColorProvider? = null,
    tint: ColorProvider = GlanceTheme.colors.onBackground,
) {
    var modifier = GlanceModifier
        .size(buttonSize)
        .clickable(action)
    if (container != null) {
        modifier = modifier.cornerRadius(buttonSize / 2).background(container)
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Image(
            provider = ImageProvider(iconRes),
            contentDescription = description,
            modifier = GlanceModifier.size(glyphSize),
            colorFilter = androidx.glance.ColorFilter.tint(tint),
        )
    }
}
