package com.ivor.ivormusic.ui.video

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.R
import kotlinx.coroutines.delay
import java.util.Locale

/** How long the notice stays up before it stops being an interruption. */
private const val RESUME_NOTICE_MS = 7_000L

/**
 * "Picked up where you left off", with the way back.
 *
 * Deliberately outside the control-visibility gate, for the same reason the
 * SponsorBlock chip is: the jump happens without the viewer asking for it, so
 * the undo has to be reachable whether or not the chrome is up. It occupies
 * the same corner as that chip and is suppressed by the caller while one is
 * showing, since a skip is the more urgent of the two and both are transient.
 *
 * The dismissal timer is keyed on the position, so reopening a different video
 * restarts it rather than inheriting the tail of the previous notice.
 */
@Composable
internal fun ResumePlaybackChip(
    resumedFromMs: Long?,
    onPlayFromStart: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(resumedFromMs) {
        if (resumedFromMs == null) return@LaunchedEffect
        delay(RESUME_NOTICE_MS)
        onDismiss()
    }

    AnimatedVisibility(
        visible = resumedFromMs != null,
        enter = fadeIn() + slideInVertically(
            initialOffsetY = { it / 2 },
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
        ),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
        modifier = modifier
    ) {
        // Held across the exit animation so the chip does not blank its own
        // text on the way out.
        val position = resumedFromMs ?: return@AnimatedVisibility
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            shadowElevation = 6.dp
        ) {
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                Icon(
                    imageVector = Icons.Rounded.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.vp_resumed_from, formatResumeTimestamp(position)),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = onPlayFromStart) {
                    Text(
                        text = stringResource(R.string.vp_play_from_start),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private fun formatResumeTimestamp(millis: Long): String {
    val seconds = millis / 1000
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}
