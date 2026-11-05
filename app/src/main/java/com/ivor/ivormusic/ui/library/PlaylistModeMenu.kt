package com.ivor.ivormusic.ui.library

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.PlaylistMode

/**
 * What a playlist's menu can do about the modes it shows in (#357), seen from
 * [here]. A playlist shown in both offers to leave this mode for the other one;
 * a playlist already kept to this mode offers the other one back. A playlist
 * kept to the other mode is never drawn here, so it needs no case: its way
 * back is this same item in the other mode.
 */
data class PlaylistModeAction(
    val kept: PlaylistMode?,
    val here: PlaylistMode,
    val onChange: (PlaylistMode?) -> Unit,
)

@Composable
internal fun PlaylistModeMenuItem(action: PlaylistModeAction, onDone: () -> Unit) {
    val other = action.here.other
    val keptHere = action.kept == action.here
    val label = when {
        keptHere && other == PlaylistMode.VIDEO -> R.string.pl_mode_also_video
        keptHere -> R.string.pl_mode_also_music
        other == PlaylistMode.VIDEO -> R.string.pl_mode_only_video
        else -> R.string.pl_mode_only_music
    }
    DropdownMenuItem(
        text = { Text(stringResource(label)) },
        onClick = {
            onDone()
            action.onChange(if (keptHere) null else other)
        },
        leadingIcon = {
            Icon(
                if (other == PlaylistMode.VIDEO) Icons.Rounded.VideoLibrary else Icons.Rounded.LibraryMusic,
                contentDescription = null
            )
        }
    )
}
