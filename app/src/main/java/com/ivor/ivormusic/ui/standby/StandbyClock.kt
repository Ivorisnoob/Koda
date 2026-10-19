package com.ivor.ivormusic.ui.standby

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * The wall-clock time, updated on each minute boundary rather than on a timer
 * that drifts away from it, so a digit changes when the minute does.
 */
@Composable
internal fun rememberMinuteClock(): State<Long> = produceState(System.currentTimeMillis()) {
    while (true) {
        val now = System.currentTimeMillis()
        value = now
        delay(60_000L - now % 60_000L + 25L)
    }
}

/** "10:42" in the device's 12/24-hour setting, without an AM/PM marker, the way a bedside clock reads. */
internal fun clockText(context: Context, epochMs: Long): String {
    val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"
    return DateFormat.format(pattern, epochMs).toString()
}

/** "Tue 14 Oct", ordered and spelt for the user's locale. */
internal fun dateText(epochMs: Long): String {
    val pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEdMMM")
    return java.text.SimpleDateFormat(pattern, Locale.getDefault()).format(Date(epochMs))
}

/** Battery level and whether it is charging, from the sticky broadcast: no polling. */
internal data class BatteryInfo(val percent: Int, val charging: Boolean)

@Composable
internal fun rememberBatteryInfo(): State<BatteryInfo?> {
    val context = LocalContext.current
    val state = remember { mutableStateOf<BatteryInfo?>(null) }
    DisposableEffect(context) {
        fun read(intent: Intent?) {
            intent ?: return
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            if (level < 0 || scale <= 0) return
            state.value = BatteryInfo(
                percent = level * 100 / scale,
                charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL,
            )
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) = read(intent)
        }
        read(
            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        )
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    return state
}

/**
 * The time, one slot per character, each rolling to its new value on its own:
 * at 10:59 to 11:00 the minutes turn over first-to-last as they would on a
 * flip clock, and an hour gaining a digit makes room for it smoothly.
 *
 * Every digit slot is the width of the widest digit in [style], so the time
 * never shifts sideways as it changes, whatever the font's own figures do.
 * Slots are keyed from the right, so the minutes stay the minutes when the hour
 * changes length.
 */
@Composable
internal fun StandbyClock(
    timeText: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val digitWidth = remember(style, density) {
        with(density) {
            ('0'..'9').maxOf { measurer.measure(it.toString(), style).size.width }.toDp()
        }
    }
    val colonWidth = remember(style, density) {
        with(density) { measurer.measure(":", style).size.width.toDp() }
    }
    val slots = timeText.padStart(CLOCK_SLOTS)
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = timeText },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        slots.forEachIndexed { index, char ->
            androidx.compose.runtime.key(CLOCK_SLOTS - index) {
                AnimatedContent(
                    targetState = char,
                    transitionSpec = {
                        (slideInVertically(appleSpring(0.55f)) { it / 3 } + fadeIn(tween(260))) togetherWith
                            (slideOutVertically(appleSpring(0.55f)) { -it / 3 } + fadeOut(tween(180))) using
                            SizeTransform(clip = false) { _, _ -> appleSpring(0.55f) }
                    },
                    contentAlignment = Alignment.Center,
                    label = "StandbyClockSlot",
                ) { shown ->
                    val width = when {
                        shown == ' ' -> 0.dp
                        shown == ':' -> colonWidth
                        else -> digitWidth
                    }
                    Box(Modifier.width(width), contentAlignment = Alignment.Center) {
                        if (shown != ' ') {
                            Text(text = shown.toString(), style = style, textAlign = TextAlign.Center, maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
        }
    }
}

/** "HH:mm" is five characters; a 12-hour "h:mm" pads to the same five slots. */
private const val CLOCK_SLOTS = 5
