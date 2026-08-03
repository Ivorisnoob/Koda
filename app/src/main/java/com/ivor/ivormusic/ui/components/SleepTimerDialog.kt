package com.ivor.ivormusic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.util.Locale

/** Durations offered, laid out three to a row. */
private val PRESET_MINUTES = listOf(listOf(5, 10, 15), listOf(30, 45, 60))

/**
 * Sleep timer picker and status.
 *
 * One sheet shared by all eight player styles rather than eight bespoke ones -
 * but it takes its colours from the caller, so it lands inside each style's own
 * palette instead of ignoring it. The styles that compute their own two-tone
 * pairs (Editorial's accent/field, Canvas's glyph/scrim, Bento's tiles) pass
 * those straight in; the rest fall through to the theme, which is already
 * artwork-tinted where the user has asked for that.
 *
 * A sheet rather than the AlertDialog this used to be: it is a picker, the
 * players already host sheets, and a centred dialog over the full-bleed styles
 * read as something bolted on from another app.
 *
 * The timer itself lives in MusicService - see the note there for why - so what
 * this shows is the service's state, not this screen's.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerSheet(
    endsAt: Long?,
    endOfTrack: Boolean,
    onStartMinutes: (Int) -> Unit,
    onStartEndOfTrack: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    accent: Color = MaterialTheme.colorScheme.primary,
    onAccent: Color = MaterialTheme.colorScheme.onPrimary,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    onContainer: Color = MaterialTheme.colorScheme.onSurface
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = container,
        contentColor = onContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Bedtime,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(30.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Sleep timer",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = onContainer
            )

            Spacer(modifier = Modifier.height(20.dp))

            if (endOfTrack) {
                SleepTimerStatus(
                    headline = "This track",
                    detail = "Playback stops when the song playing now finishes",
                    accent = accent,
                    onContainer = onContainer
                )
                Spacer(modifier = Modifier.height(20.dp))
                TurnOffButton(onCancel, accent, onAccent)
            } else if (endsAt != null) {
                // Ticks against the wall clock the service published, so the
                // countdown stays honest across a screen-off stretch instead of
                // drifting away from when playback will actually stop.
                var remainingMs by remember(endsAt) {
                    mutableLongStateOf(endsAt - System.currentTimeMillis())
                }
                LaunchedEffect(endsAt) {
                    while (true) {
                        remainingMs = endsAt - System.currentTimeMillis()
                        if (remainingMs <= 0L) break
                        delay(1_000)
                    }
                }
                SleepTimerStatus(
                    headline = formatRemaining(remainingMs),
                    detail = "Playback fades out and pauses when the timer ends",
                    accent = accent,
                    onContainer = onContainer
                )
                Spacer(modifier = Modifier.height(20.dp))
                TurnOffButton(onCancel, accent, onAccent)
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PRESET_MINUTES.forEach { presetRow ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            presetRow.forEach { minutes ->
                                FilledTonalButton(
                                    onClick = { onStartMinutes(minutes) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = accent.copy(alpha = 0.16f),
                                        contentColor = onContainer
                                    )
                                ) {
                                    Text("$minutes min")
                                }
                            }
                        }
                    }

                    // The option the presets cannot express, and usually the
                    // one people actually want: finish this song, then stop.
                    Surface(
                        onClick = onStartEndOfTrack,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(18.dp),
                        color = accent,
                        contentColor = onAccent
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MusicNote,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "End of this track",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SleepTimerStatus(
    headline: String,
    detail: String,
    accent: Color,
    onContainer: Color
) {
    Text(
        text = headline,
        style = MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.Bold,
        color = accent
    )
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = detail,
        style = MaterialTheme.typography.bodyMedium,
        color = onContainer.copy(alpha = 0.7f),
        textAlign = TextAlign.Center
    )
}

@Composable
private fun TurnOffButton(onCancel: () -> Unit, accent: Color, onAccent: Color) {
    Button(
        onClick = onCancel,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = accent,
            contentColor = onAccent
        )
    ) {
        Text("Turn off", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

/** m:ss under an hour, h:mm:ss over it. */
private fun formatRemaining(remainingMs: Long): String {
    val totalSeconds = (remainingMs / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}
