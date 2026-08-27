package com.ivor.ivormusic.widget

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/**
 * The expressive half of the widget shape story.
 *
 * M3 Expressive puts its most adventurous shapes where they cost nothing:
 * "photography cropping, personalized avatar masking, and other non-interactive
 * elements". That is exactly the right split for a widget, because the
 * interactive half cannot follow. A Glance click target gets a ripple drawn by
 * the framework as a rectangle over the view's bounds, and there is no ripple
 * override in Glance 1.1 - so a scalloped *button* would flash a hard square on
 * every tap, which is worse than a plain one. Controls therefore use the
 * Material components that ship their own matching ripple, and the album art
 * carries the shape language instead.
 *
 * Shapes are generated as [Path]s rather than authored as vector XML because
 * they are used to mask bitmaps, not only to fill backgrounds, and a path that
 * defines both keeps the cover and its placeholder identical.
 */
internal object WidgetShapes {

    /** Points per revolution. Plenty for a cover that is at most ~200dp wide. */
    private const val SAMPLES = 360

    /**
     * A cookie: a circle with [lobes] soft scallops. The house shape for album
     * art - it reads as deliberate at a glance and still shows the whole cover,
     * which a heavy crop would not.
     */
    fun cookie(size: Float, lobes: Int = 12, amplitude: Float = 0.055f): Path =
        polar(size) { angle -> (1f + amplitude * cos(lobes * angle)).toFloat() }

    /** Four fat lobes. Used small, where a subtle scallop would read as a circle. */
    fun clover(size: Float, amplitude: Float = 0.13f): Path =
        polar(size) { angle -> (1f + amplitude * cos(4.0 * angle)).toFloat() }

    /** A superellipse - squarer than a rounded rect, with no corner to catch. */
    fun squircle(size: Float, exponent: Float = 4f): Path {
        val radius = size / 2f
        val path = Path()
        for (i in 0 until SAMPLES) {
            val angle = 2.0 * Math.PI * i / SAMPLES
            val cosine = cos(angle)
            val sine = sin(angle)
            // Signed power keeps the sign of the axis while rounding the corner.
            val x = radius * signedPow(cosine, 2f / exponent)
            val y = radius * signedPow(sine, 2f / exponent)
            val px = radius + x.toFloat()
            val py = radius + y.toFloat()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        return path
    }

    fun circle(size: Float): Path = Path().apply {
        addOval(RectF(0f, 0f, size, size), Path.Direction.CW)
    }

    fun roundedRect(width: Float, height: Float, radius: Float): Path = Path().apply {
        addRoundRect(RectF(0f, 0f, width, height), radius, radius, Path.Direction.CW)
    }

    private fun polar(size: Float, radiusAt: (Double) -> Float): Path {
        val center = size / 2f
        // Normalise so the widest point of the lobe touches the edge rather
        // than overflowing it - otherwise the scallops get clipped flat.
        var peak = 0f
        for (i in 0 until SAMPLES) {
            peak = max(peak, radiusAt(2.0 * Math.PI * i / SAMPLES))
        }
        val scale = center / peak
        val path = Path()
        for (i in 0 until SAMPLES) {
            val angle = 2.0 * Math.PI * i / SAMPLES
            val r = radiusAt(angle) * scale
            val px = center + (r * cos(angle)).toFloat()
            val py = center + (r * sin(angle)).toFloat()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        return path
    }

    private fun signedPow(value: Double, exponent: Float): Double =
        if (value < 0) -((-value).pow(exponent.toDouble())) else value.pow(exponent.toDouble())
}

/**
 * The shape an album cover is cut into. Named rather than passed as a path so a
 * widget declares its identity and the drawing stays in one place.
 */
internal enum class ArtworkShape {
    COOKIE,
    CLOVER,
    SQUIRCLE,
    CIRCLE,
    ROUNDED;

    fun path(size: Float): Path = when (this) {
        COOKIE -> WidgetShapes.cookie(size)
        CLOVER -> WidgetShapes.clover(size)
        SQUIRCLE -> WidgetShapes.squircle(size)
        CIRCLE -> WidgetShapes.circle(size)
        ROUNDED -> WidgetShapes.roundedRect(size, size, size * 0.16f)
    }
}

/**
 * Cut [src] to [shape], centre-cropped square.
 *
 * The mask is baked into the bitmap rather than clipped at draw time because
 * Glance can only round an image through the launcher's outline provider, which
 * does rectangles and nothing else - and does not exist at all below API 31.
 */
internal fun maskedArtwork(src: Bitmap, shape: ArtworkShape, sizePx: Int): Bitmap {
    val side = sizePx.coerceAtLeast(1)
    val out = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)

    // Centre-crop the source into the square before masking, so a non-square
    // cover is not squashed into the shape.
    val scale = side.toFloat() / minOf(src.width, src.height)
    val matrix = android.graphics.Matrix().apply {
        setScale(scale, scale)
        postTranslate(
            (side - src.width * scale) / 2f,
            (side - src.height * scale) / 2f,
        )
    }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setLocalMatrix(matrix)
        }
    }
    canvas.drawPath(shape.path(side.toFloat()), paint)
    return out
}

/** The same silhouette in one flat colour, for the no-artwork placeholder. */
internal fun shapeTile(shape: ArtworkShape, sizePx: Int, color: Int): Bitmap {
    val side = sizePx.coerceAtLeast(1)
    val out = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    canvas.drawPath(shape.path(side.toFloat()), paint)
    return out
}
