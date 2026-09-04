package com.ivor.ivormusic.ui.video

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    modifier: Modifier = Modifier,
    /**
     * The watch page draws the video in a band a few hundred dp tall, so a chip
     * sized for fullscreen covers a real fraction of the picture. Compact drops
     * the leading icon and steps the type and padding down again.
     */
    compact: Boolean = false
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
        val textStyle = if (compact) {
            MaterialTheme.typography.labelSmall
        } else {
            MaterialTheme.typography.labelMedium
        }
        Surface(
            shape = RoundedCornerShape(if (compact) 14.dp else 18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = if (compact) 3.dp else 6.dp,
            shadowElevation = if (compact) 2.dp else 4.dp
        ) {
            Row(
                modifier = Modifier.padding(
                    start = if (compact) 10.dp else 12.dp,
                    end = 2.dp,
                    top = 2.dp,
                    bottom = 2.dp
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                // The icon is the first thing to go: on the watch page the
                // words already say what happened, and 16dp plus its gap is
                // most of what makes the chip read as a bubble over the video.
                if (!compact) {
                    Icon(
                        imageVector = Icons.Rounded.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(7.dp))
                }
                Text(
                    text = stringResource(R.string.vp_resumed_from, formatResumeTimestamp(position)),
                    style = textStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(if (compact) 6.dp else 8.dp))
                // A plain clickable rather than a TextButton: the button's own
                // minimum size and 24dp of internal padding are what made this
                // twice the height it needs to be.
                Text(
                    text = stringResource(R.string.vp_play_from_start),
                    style = textStyle,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(if (compact) 12.dp else 16.dp))
                        .clickable(onClick = onPlayFromStart)
                        .padding(
                            horizontal = if (compact) 8.dp else 10.dp,
                            vertical = if (compact) 5.dp else 7.dp
                        )
                )
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
