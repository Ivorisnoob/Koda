package com.ivor.ivormusic.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.IncognitoMode

/**
 * The "history is paused" indicator, in one place because it belongs on every
 * bar the mode is in force on.
 *
 * A mode that silently stops recording is a promise, and a promise whose state
 * cannot be seen is one somebody will assume is off at the wrong moment - which
 * is exactly what video mode was, since its top bar carried no indicator at all
 * while the music one did. The switch itself stays in the account sheet, so
 * tapping this opens that sheet rather than toggling: an indicator that turns
 * the mode off when brushed is worse than no indicator.
 *
 * It owns its own visibility - reading [IncognitoMode] here rather than taking
 * a boolean - so a third bar cannot render the chip and forget the state it is
 * meant to report.
 *
 * [compact] drops the label for bars that are already carrying four controls
 * and a mode toggle; the icon keeps its content description, so the chip says
 * the same thing to a screen reader in both forms.
 */
@Composable
fun IncognitoIndicator(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val context = LocalContext.current
    val incognito by IncognitoMode.enabled(context).collectAsState()
    val label = stringResource(R.string.incognito_active)

    AnimatedVisibility(
        visible = incognito,
        enter = fadeIn() + expandHorizontally(),
        exit = fadeOut() + shrinkHorizontally(),
        modifier = modifier
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onClick)
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = if (compact) 9.dp else 12.dp,
                    vertical = 7.dp
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.VisibilityOff,
                    contentDescription = if (compact) label else null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(16.dp)
                )
                if (!compact) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.incognito_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}
