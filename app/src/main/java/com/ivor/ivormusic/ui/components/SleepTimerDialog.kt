package com.ivor.ivormusic.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * 😴 Sleep timer picker / status dialog.
 *
 * Inactive: preset duration buttons. Active: live countdown + stop button.
 * Playback pauses when the timer fires (see PlayerViewModel.startSleepTimer).
 */
@Composable
fun SleepTimerDialog(
    endsAt: Long?,
    onStart: (minutes: Int) -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Bedtime, contentDescription = null) },
        title = { Text(if (endsAt != null) "Sleep timer running" else "Sleep timer") },
        text = {
            if (endsAt != null) {
                var remainingMs by remember(endsAt) {
                    mutableLongStateOf(endsAt - System.currentTimeMillis())
                }
                LaunchedEffect(endsAt) {
                    while (true) {
                        remainingMs = endsAt - System.currentTimeMillis()
                        if (remainingMs <= 0) break
                        delay(1_000)
                    }
                }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val totalSeconds = (remainingMs / 1000).coerceAtLeast(0)
                    Text(
                        String.format(Locale.US, "%d:%02d", totalSeconds / 60, totalSeconds % 60),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Playback will pause when the timer ends",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(listOf(5, 10, 15), listOf(30, 45, 60)).forEach { presetRow ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            presetRow.forEach { minutes ->
                                FilledTonalButton(
                                    onClick = { onStart(minutes) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("$minutes min")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (endsAt != null) {
                TextButton(onClick = onStop) { Text("Stop timer") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
