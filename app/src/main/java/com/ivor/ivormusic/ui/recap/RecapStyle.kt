package com.ivor.ivormusic.ui.recap

import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.TransformResult
import androidx.graphics.shapes.toPath
import com.ivor.ivormusic.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Recap's visual vocabulary: the room's tone, the covers' colours, the
 * window's shapes, and the clock every card's choreography runs on.
 *
 * Colour rule. Everything that is chrome comes from the story's colour scheme
 * (one built from the period's top cover, always dark). The covers' own
 * colours - the arcs of the pressing, the calendar's stitches, the ridgeline,
 * the room's tint - are the data being shown, the same class of exception as
 * SponsorBlock's category swatches: the picture is *of* those colours.
 */

/** The window's shapes. Their order is not stored anywhere. */
internal enum class RecapShape { COOKIE9, CLOVER4, SUNNY, SOFTBURST, COOKIE12, FLOWER, COOKIE6 }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun RecapShape.material(): RoundedPolygon = when (this) {
    RecapShape.COOKIE9 -> MaterialShapes.Cookie9Sided
    RecapShape.CLOVER4 -> MaterialShapes.Clover4Leaf
    RecapShape.SUNNY -> MaterialShapes.Sunny
    RecapShape.SOFTBURST -> MaterialShapes.SoftBurst
    RecapShape.COOKIE12 -> MaterialShapes.Cookie12Sided
    RecapShape.FLOWER -> MaterialShapes.Flower
    RecapShape.COOKIE6 -> MaterialShapes.Cookie6Sided
}

private val fittedShapes = HashMap<RecapShape, RoundedPolygon>()

/**
 * The shape resized about its centre to stay inside the unit square's
 * inscribed circle at every angle - the mini player's scar: a Material shape
 * fills the square as drawn, so a turned clover reaches past its slot.
 */
internal fun RecapShape.polygon(): RoundedPolygon = fittedShapes.getOrPut(this) {
    val polygon = material()
    val bounds = polygon.calculateMaxBounds()
    val radius = (bounds[2] - bounds[0]) / 2f
    if (radius <= 0f) return@getOrPut polygon
    val cx = (bounds[0] + bounds[2]) / 2f
    val cy = (bounds[1] + bounds[3]) / 2f
    val scale = 0.5f / radius
    polygon.transformed { x, y -> TransformResult(0.5f + (x - cx) * scale, 0.5f + (y - cy) * scale) }
}

private val morphs = HashMap<Pair<RecapShape, RecapShape>, Morph>()

internal fun morphOf(from: RecapShape, to: RecapShape): Morph =
    morphs.getOrPut(from to to) { Morph(from.polygon(), to.polygon()) }

/** A [Morph] between two unit-square shapes, stretched to whatever it clips. */
internal class MorphShape(private val morph: Morph, private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = morph.toPath(progress).asComposePath()
        path.transform(Matrix().apply { scale(size.width, size.height) })
        return Outline.Generic(path)
    }
}

/** One shape at rest, for windows that never change shape. */
internal fun shapeOf(shape: RecapShape): Shape = MorphShape(morphOf(shape, shape), 0f)

/*
 * The clock. Each card's choreography is a function of the seconds since it
 * appeared, the way the concept film was cut: an element "rises" at its own
 * moment on a critically damped spring, so any frame can be drawn on its own
 * and nothing depends on the frame before. Compose scales the clock by the
 * system's animator duration scale, so with animations off every card lands
 * finished.
 */

/**
 * Where a spring released at time 0 is at [t] seconds, 0 to 1: Apple's
 * (response, damping) in closed form - the same spring Standby animates with.
 */
internal fun springAt(t: Float, response: Float = 0.5f, damping: Float = 1f): Float {
    if (t <= 0f) return 0f
    val w = (2.0 * PI / response).toFloat()
    val value = if (damping >= 1f) {
        1f - (1f + w * t) * exp(-w * t)
    } else {
        val wd = w * sqrt(1f - damping * damping)
        1f - exp(-damping * w * t) * (cos(wd * t) + (damping * w / wd) * sin(wd * t))
    }
    return if (t > response * 3f && damping >= 1f) 1f else value
}

/**
 * Rise into place at [at] seconds: up [distance] through a soft blur (where
 * the platform blurs, Android 12 and up), on the critical spring. Read in the
 * draw phase only.
 */
internal fun Modifier.rise(clock: () -> Float, at: Float, distance: Dp = 18.dp): Modifier = graphicsLayer {
    val p = springAt(clock() - at, 0.5f)
    alpha = p.coerceIn(0f, 1f)
    translationY = (1f - p) * distance.toPx()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val blur = (1f - p) * 6.dp.toPx()
        renderEffect = if (blur > 0.5f) BlurEffect(blur, blur) else null
    }
}

/** Arrive from [from] scale with a little give, at [at] seconds. */
internal fun Modifier.drop(clock: () -> Float, at: Float, from: Float = 0.55f, turn: Float = 0f): Modifier = graphicsLayer {
    val p = springAt(clock() - at, 0.45f, 0.72f)
    alpha = (springAt(clock() - at, 0.3f)).coerceIn(0f, 1f)
    val s = from + (1f - from) * p
    scaleX = s
    scaleY = s
    rotationZ = turn * (1f - p)
}

/*
 * Cover colours. A cover's colour as extracted is the data; to be drawn on
 * the dark room its lightness is held inside a band, so a black sleeve still
 * reads - as smoke - and a white one does not glare.
 */

private fun Color.withLightness(min: Float, max: Float): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(toArgb(), hsl)
    hsl[2] = hsl[2].coerceIn(min, max)
    return Color(ColorUtils.HSLToColor(hsl))
}

/** For marks drawn on the room: arcs, wedges, stitches, ridges. */
internal fun Color.onRoom(): Color = withLightness(0.34f, 0.74f)

/** For a spine on a light or dark card: deeper, so a black month reads black. */
internal fun Color.deep(): Color = withLightness(0.12f, 0.8f)

/**
 * The room: the scheme's darkest surface leaning [amount] toward a cover's
 * colour. Its tone is the period's light.
 */
internal fun roomTone(base: Color, cover: Color?, amount: Float = 0.14f): Color =
    if (cover == null) base else lerp(base, cover, amount)

/** A plain-words name for a cover's colour, for the note's sentence. */
@StringRes
internal fun colourName(color: Color): Int {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(color.toArgb(), hsl)
    val (hue, sat, light) = Triple(hsl[0], hsl[1], hsl[2])
    return when {
        light < 0.16f -> R.string.recap_colour_black
        sat < 0.14f -> if (light < 0.6f) R.string.recap_colour_smoke else R.string.recap_colour_paper
        hue in 190f..235f -> if (light > 0.45f) R.string.recap_colour_sky else R.string.recap_colour_deep_blue
        hue in 160f..190f -> R.string.recap_colour_sea
        hue in 70f..160f -> R.string.recap_colour_meadow
        hue in 35f..70f -> R.string.recap_colour_lamp
        hue in 15f..35f -> if (light < 0.55f) R.string.recap_colour_terracotta else R.string.recap_colour_peach
        hue < 15f || hue >= 330f -> R.string.recap_colour_cherry
        else -> R.string.recap_colour_lilac
    }
}

/** Long sitting, hours and the like, as words: "2 h 38 min", "47 min". */
internal fun formatHeard(ms: Long, hoursAndMinutes: (Long, Long) -> String, minutesOnly: (Long) -> String): String {
    val minutes = ms / 60_000L
    val h = minutes / 60
    return if (h > 0) hoursAndMinutes(h, minutes % 60) else minutesOnly(minutes)
}
