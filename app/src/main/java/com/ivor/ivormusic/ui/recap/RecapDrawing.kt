package com.ivor.ivormusic.ui.recap

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import com.ivor.ivormusic.data.recap.RecapArc
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * The pictures a recap draws. Plain DrawScope work, used by the cards and by
 * the share images alike, so a shared pressing is the one on screen.
 */

/**
 * The pressing, cut like a record: one groove per day of the period (the
 * first day at the centre), every play an arc along its day's groove from the
 * minute it started, as long as it was heard; midnight at the top. [progress]
 * cuts the plays in the order they happened, and the empty grooves of every
 * day cut so far show faintly, so the disc grows outward as it is pressed.
 */
internal fun DrawScope.drawPressing(
    center: Offset,
    radius: Float,
    arcs: List<RecapArc>,
    days: Int,
    progress: Float,
    colourOf: (String) -> Color,
    groove: Color,
) {
    if (arcs.isEmpty() || days <= 0) return
    val inner = radius * 0.3f
    val pitch = (radius - inner) / days
    val shown = progress.coerceIn(0f, 1f) * arcs.size
    val cut = shown.toInt().coerceAtMost(arcs.size - 1)
    val lastDay = if (shown <= 0f) -1 else arcs[cut].day
    // The empty grooves only where they can be told apart; a year's worth at
    // phone size would be hundreds of hairlines a frame drawing one grey disc.
    if (pitch >= 2f) {
        val grooveWidth = max(0.5f, pitch * 0.45f)
        for (d in 0..lastDay) {
            drawCircle(groove, radius = inner + pitch * (d + 0.5f), center = center, style = Stroke(grooveWidth))
        }
    }
    val stroke = Stroke(width = max(0.9f, pitch * 1.3f), cap = StrokeCap.Butt)
    for (i in arcs.indices) {
        val local = (shown - i).coerceIn(0f, 1f)
        if (local <= 0f) break
        val arc = arcs[i]
        val r = inner + pitch * (arc.day + 0.5f)
        val startDeg = arc.startMinute / 1440f * 360f - 90f
        val sweep = max(0.25f, arc.heardMinutes / 1440f * 360f) * local
        drawArc(
            color = colourOf(arc.songId),
            startAngle = startDeg,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = Offset(center.x - r, center.y - r),
            size = Size(r * 2, r * 2),
            style = stroke,
        )
    }
}

/**
 * Two songs' plays so far, day by day, up to [upToDay] (fractional). The line
 * of the one that passed the other is drawn last, so it sits on top.
 */
internal fun DrawScope.drawRace(
    origin: Offset,
    width: Float,
    height: Float,
    days: Int,
    max: Int,
    passed: List<Int>,
    leader: List<Int>,
    upToDay: Float,
    passedColour: Color,
    leaderColour: Color,
    baseline: Color,
    stroke: Float,
) {
    drawLine(baseline, origin, Offset(origin.x + width, origin.y), strokeWidth = stroke / 3f)
    fun x(day: Float) = origin.x + width * day / (days - 1).coerceAtLeast(1)
    fun y(v: Float) = origin.y - height * v / max.coerceAtLeast(1)
    fun valueAt(series: List<Int>, day: Float): Float {
        val i = day.toInt().coerceIn(0, series.lastIndex)
        val j = (i + 1).coerceAtMost(series.lastIndex)
        val f = day - i
        return series[i] + (series[j] - series[i]) * f
    }
    for ((series, colour) in listOf(passed to passedColour, leader to leaderColour)) {
        val path = Path()
        var d = 0f
        path.moveTo(x(0f), y(series[0].toFloat()))
        while (d < upToDay) {
            d = (d + 0.5f).coerceAtMost(upToDay)
            path.lineTo(x(d), y(valueAt(series, d)))
        }
        drawPath(path, colour, style = Stroke(stroke, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}

/** Where a race line's head is at [day]. */
internal fun raceHead(origin: Offset, width: Float, height: Float, days: Int, max: Int, series: List<Int>, day: Float): Offset {
    val i = day.toInt().coerceIn(0, series.lastIndex)
    val j = (i + 1).coerceAtMost(series.lastIndex)
    val v = series[i] + (series[j] - series[i]) * (day - i)
    return Offset(origin.x + width * day / (days - 1).coerceAtLeast(1), origin.y - height * v / max.coerceAtLeast(1))
}

/**
 * Twenty-four wedges round [center], each as long as that hour's listening
 * (square-rooted, so a quiet hour is still visible) and coloured by what was
 * heard in it; [grow] (0 to 1 per hour) lets them arrive one after another.
 */
internal fun DrawScope.drawHoursRing(
    center: Offset,
    inner: Float,
    span: Float,
    heardMs: List<Long>,
    colourOf: (Int) -> Color?,
    grow: (Int) -> Float,
    empty: Color,
    band: Int,
    bandProgress: Float,
    bandColour: Color,
) {
    val max = (heardMs.maxOrNull() ?: 0L).coerceAtLeast(1L)
    for (h in 0 until 24) {
        val g = grow(h).coerceIn(0f, 1.2f)
        val length = if (heardMs[h] > 0) span * (0.1f + 0.9f * sqrt(heardMs[h].toFloat() / max)) else span * 0.04f
        val outer = inner + length * g
        val a0 = h / 24f * 360f - 90f + 1.2f
        val sweep = 360f / 24f - 2.4f
        val path = Path().apply {
            arcTo(androidx.compose.ui.geometry.Rect(center, outer), a0, sweep, true)
            arcTo(androidx.compose.ui.geometry.Rect(center, inner), a0 + sweep, -sweep, false)
            close()
        }
        drawPath(path, if (heardMs[h] > 0) colourOf(h) ?: empty else empty)
    }
    if (bandProgress > 0f) {
        val r = inner + span + span * 0.09f
        drawArc(
            color = bandColour,
            startAngle = band / 24f * 360f - 90f + 1f,
            sweepAngle = (2f / 24f * 360f - 2f) * bandProgress.coerceIn(0f, 1f),
            useCenter = false,
            topLeft = Offset(center.x - r, center.y - r),
            size = Size(r * 2, r * 2),
            style = Stroke(span * 0.022f, cap = StrokeCap.Round),
        )
    }
}

/**
 * One artist's day as a smoothed landscape, scaled to their own busiest
 * hour: the shape says when, not how much. Returns the 97 samples (every
 * quarter of an hour, both midnights).
 */
internal fun ridgeOf(hourMs: List<Long>): FloatArray {
    val samples = FloatArray(97)
    val sigma = 1.1f
    for (k in samples.indices) {
        val t = k / 96f * 24f
        var v = 0f
        for (h in 0 until 24) {
            for (wrap in intArrayOf(-24, 0, 24)) {
                val d = t - (h + 0.5f + wrap)
                v += hourMs[h] * exp(-(d * d) / (2 * sigma * sigma))
            }
        }
        samples[k] = v
    }
    val max = samples.max().coerceAtLeast(1f)
    for (k in samples.indices) samples[k] /= max
    return samples
}

/** Draws [ridge] above [baseline] from [left] to [right], revealed up to [sweep] (0 to 1). */
internal fun DrawScope.drawRidge(
    ridge: FloatArray,
    left: Float,
    right: Float,
    baseline: Float,
    height: Float,
    sweep: Float,
    fill: Color,
    under: Color,
    edge: Color,
    edgeWidth: Float,
) {
    val area = Path()
    val line = Path()
    area.moveTo(left, baseline)
    ridge.forEachIndexed { k, v ->
        val x = left + (right - left) * k / (ridge.size - 1)
        val y = baseline - v * height
        area.lineTo(x, y)
        if (k == 0) line.moveTo(x, y) else line.lineTo(x, y)
    }
    area.lineTo(right, baseline)
    area.close()
    clipRect(right = left + (right - left) * sweep.coerceIn(0f, 1f)) {
        drawPath(area, under)
        drawPath(area, fill)
        drawPath(line, edge, style = Stroke(edgeWidth))
    }
}

/** A point [radius] from [center] at [degrees] clockwise from the top. */
internal fun polar(center: Offset, radius: Float, degrees: Float): Offset {
    val a = (degrees - 90f) / 180f * PI.toFloat()
    return Offset(center.x + cos(a) * radius, center.y + sin(a) * radius)
}
