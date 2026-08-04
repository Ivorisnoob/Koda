package com.ivor.ivormusic.ui.player

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.ivor.ivormusic.ui.components.SleepTimerSheet

/**
 * What a player style needs to show a sleep timer control: whether one is
 * armed, and a way to open the picker.
 */
@Immutable
internal class SleepTimerControl(
    /** True while a duration or end-of-track timer is running. */
    val active: Boolean,
    val open: () -> Unit
)

/**
 * Wire a player style to the sleep timer.
 *
 * Also *emits* the picker sheet, which is why every style needs only this call
 * plus one button. Placement is the part that belongs to each style - the
 * button goes in that style's own top bar, in that style's own idiom - so the
 * shared piece here is deliberately only the state and the sheet.
 *
 * The colour pair defaults to the theme, which is already artwork-tinted when
 * the user has that on. Styles that run their own two-tone palette (Editorial's
 * paper and ink, Canvas's white-on-black over the artwork) pass theirs in so
 * the sheet does not arrive looking like it came from a different app. Pass
 * matched pairs only: they are used as fill and content together.
 */
@Composable
internal fun rememberSleepTimerControl(
    viewModel: PlayerViewModel,
    accent: Color = MaterialTheme.colorScheme.primary,
    onAccent: Color = MaterialTheme.colorScheme.onPrimary,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    onContainer: Color = MaterialTheme.colorScheme.onSurface
): SleepTimerControl {
    val endsAt by viewModel.sleepTimerEndsAt.collectAsState()
    val endOfTrack by viewModel.sleepTimerEndOfTrack.collectAsState()
    var showSheet by remember { mutableStateOf(false) }

    if (showSheet) {
        SleepTimerSheet(
            endsAt = endsAt,
            endOfTrack = endOfTrack,
            onStartMinutes = { viewModel.startSleepTimer(it) },
            onStartEndOfTrack = { viewModel.startSleepTimerEndOfTrack() },
            onCancel = { viewModel.cancelSleepTimer() },
            onDismiss = { showSheet = false },
            accent = accent,
            onAccent = onAccent,
            container = container,
            onContainer = onContainer
        )
    }

    return SleepTimerControl(
        active = endsAt != null || endOfTrack,
        open = { showSheet = true }
    )
}
