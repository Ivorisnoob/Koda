package com.ivor.ivormusic.ui.player

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Cast action styled by whichever music-player top row owns it. */
@Composable
internal fun MusicCastIconButton(
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    shape: Shape = CircleShape
) {
    val action = LocalMusicCastAction.current ?: return
    FilledIconButton(
        onClick = action.onClick,
        modifier = modifier.size(size),
        shape = shape,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = if (action.isCasting) {
                MaterialTheme.colorScheme.primary
            } else {
                containerColor
            },
            contentColor = if (action.isCasting) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                contentColor
            }
        )
    ) {
        Icon(
            imageVector = if (action.isCasting) {
                Icons.Rounded.CastConnected
            } else {
                Icons.Rounded.Cast
            },
            contentDescription = if (action.isCasting && action.deviceName != null) {
                "Casting to ${action.deviceName}"
            } else {
                "Cast"
            },
            modifier = Modifier.size(if (size >= 48.dp) 24.dp else 22.dp)
        )
    }
}
