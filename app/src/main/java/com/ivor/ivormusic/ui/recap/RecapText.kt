package com.ivor.ivormusic.ui.recap

import android.content.Context
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.recap.Recap
import com.ivor.ivormusic.data.recap.RecapKind
import com.ivor.ivormusic.data.recap.RecapPeriod
import com.ivor.ivormusic.ui.theme.MontserratFamily
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle as JavaTextStyle
import java.util.Calendar
import java.util.Locale

/** The story's type: Montserrat for the one thing a card says, the app's body face for the rest. */
internal object RecapType {
    val hero = TextStyle(
        fontFamily = MontserratFamily, fontWeight = FontWeight.ExtraBold,
        fontSize = 84.sp, lineHeight = 84.sp, letterSpacing = (-0.02).em,
    )
    val big = TextStyle(
        fontFamily = MontserratFamily, fontWeight = FontWeight.ExtraBold,
        fontSize = 38.sp, lineHeight = 42.sp, letterSpacing = (-0.015).em,
    )
    val title = TextStyle(
        fontFamily = MontserratFamily, fontWeight = FontWeight.Bold,
        fontSize = 27.sp, lineHeight = 32.sp, letterSpacing = (-0.01).em,
    )
    val body = TextStyle(fontSize = 17.sp, lineHeight = 23.sp)
    val small = TextStyle(fontSize = 14.sp, lineHeight = 19.sp)
}

private val zone: ZoneId get() = ZoneId.systemDefault()

private fun LocalDate.millis(): Long = atStartOfDay(zone).toInstant().toEpochMilli()

/** The period's name: "2026", "November", "10 - 16 Nov", "All time". */
internal fun periodTitle(context: Context, period: RecapPeriod): String {
    val thisYear = LocalDate.now(zone).year
    return when (period.kind) {
        RecapKind.YEAR -> period.firstDay.year.toString()
        RecapKind.MONTH -> {
            val month = period.firstDay.month.getDisplayName(JavaTextStyle.FULL, Locale.getDefault())
                .replaceFirstChar { it.titlecase(Locale.getDefault()) }
            if (period.firstDay.year == thisYear) month else "$month ${period.firstDay.year}"
        }
        RecapKind.WEEK -> DateUtils.formatDateRange(
            context,
            period.firstDay.millis(),
            period.lastDay.plusDays(1).millis(),
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH or
                if (period.firstDay.year == thisYear) DateUtils.FORMAT_NO_YEAR else DateUtils.FORMAT_SHOW_YEAR,
        )
        RecapKind.ALL_TIME -> context.getString(R.string.recap_kind_all)
    }
}

/** The days the recap actually covers: up to today while it is in progress. */
internal fun rangeText(context: Context, recap: Recap): String {
    val first = recap.period.firstDay
    val last = first.plusDays(recap.elapsedDays - 1L)
    val sameYear = first.year == last.year && first.year == LocalDate.now(zone).year
    return DateUtils.formatDateRange(
        context,
        first.millis(),
        last.plusDays(1).millis(),
        DateUtils.FORMAT_SHOW_DATE or if (sameYear) DateUtils.FORMAT_NO_YEAR else DateUtils.FORMAT_SHOW_YEAR,
    )
}

/** "21 November", "Thursday 17 September", with the year only when it is not this one. */
internal fun dateText(context: Context, millis: Long, weekday: Boolean = false, abbreviated: Boolean = false): String {
    val cal = Calendar.getInstance().apply { timeInMillis = millis }
    val thisYear = Calendar.getInstance().get(Calendar.YEAR)
    var flags = DateUtils.FORMAT_SHOW_DATE
    flags = flags or if (cal.get(Calendar.YEAR) == thisYear) DateUtils.FORMAT_NO_YEAR else DateUtils.FORMAT_SHOW_YEAR
    if (weekday) flags = flags or DateUtils.FORMAT_SHOW_WEEKDAY
    if (abbreviated) flags = flags or DateUtils.FORMAT_ABBREV_ALL
    return DateUtils.formatDateTime(context, millis, flags)
}

internal fun dayText(context: Context, date: LocalDate, weekday: Boolean = false, abbreviated: Boolean = false): String =
    dateText(context, date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(), weekday, abbreviated)

/** A clock time in the phone's own 12 or 24 hour style. */
internal fun timeText(context: Context, millis: Long): String =
    DateUtils.formatDateTime(context, millis, DateUtils.FORMAT_SHOW_TIME)

/** An hour as words where words are better: "midnight", "noon", "10 pm", "22:00". */
internal fun hourName(context: Context, hour: Int): String {
    val h = ((hour % 24) + 24) % 24
    if (h == 0) return context.getString(R.string.recap_midnight)
    if (h == 12) return context.getString(R.string.recap_noon)
    val skeleton = if (DateFormat.is24HourFormat(context)) "Hm" else "ha"
    val pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton)
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, 0)
    }
    return DateFormat.format(pattern, cal).toString()
}

/** "2 h 38 min", or "47 min" under an hour. */
internal fun durationText(context: Context, ms: Long): String = formatHeard(
    ms,
    { h, m -> context.getString(R.string.recap_duration_hm, h.toInt(), m.toInt()) },
    { m -> context.getString(R.string.recap_duration_m, m.toInt()) },
)

/** A period's total: whole hours once there are a few, minutes before that. */
internal fun totalText(context: Context, ms: Long): String {
    val hours = ms / 3_600_000L
    return if (hours >= 3) {
        context.resources.getQuantityString(R.plurals.recap_hours, hours.toInt(), hours.toInt())
    } else {
        durationText(context, ms)
    }
}

/** A part of the period, for "June was sky blue": a month, the first or last week, a weekday. */
internal fun segmentName(context: Context, kind: RecapKind, start: LocalDate, first: Boolean): String = when (kind) {
    RecapKind.YEAR, RecapKind.ALL_TIME -> {
        val month = start.month.getDisplayName(JavaTextStyle.FULL, Locale.getDefault())
        if (kind == RecapKind.ALL_TIME) "$month ${start.year}" else month
    }
    RecapKind.MONTH -> context.getString(if (first) R.string.recap_calendar_first_week else R.string.recap_calendar_last_week)
    RecapKind.WEEK -> start.dayOfWeek.getDisplayName(JavaTextStyle.FULL, Locale.getDefault())
}

internal fun String.capitalised(): String = replaceFirstChar { it.titlecase(Locale.getDefault()) }

/** [template] with its one `%1$s` filled by [accent] drawn in [colour]. */
internal fun accented(template: String, accent: String, colour: Color): AnnotatedString = buildAnnotatedString {
    val at = template.indexOf("%1\$s")
    if (at < 0) {
        append(template)
        return@buildAnnotatedString
    }
    append(template.substring(0, at))
    withStyle(SpanStyle(color = colour)) { append(accent) }
    append(template.substring(at + 4))
}

/** Plays as words for a total: "1,468 plays". */
internal fun playsText(context: Context, count: Int): String =
    context.resources.getQuantityString(R.plurals.recap_plays, count, java.text.NumberFormat.getIntegerInstance().format(count))
