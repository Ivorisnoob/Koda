package com.ivor.ivormusic.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.R

/** Playback is visible at arrival; labels can wrap without shrinking touch targets. */
@Composable
internal fun CollectionPlaybackActions(
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 2,
    ) {
        Button(
            onClick = onPlay,
            modifier = Modifier.weight(1f).widthIn(min = 132.dp).heightIn(min = 56.dp),
            shapes = ButtonDefaults.shapes(),
        ) {
            Icon(Icons.Rounded.PlayArrow, null, Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.action_play_all), style = MaterialTheme.typography.titleMedium)
        }
        FilledTonalButton(
            onClick = onShuffle,
            modifier = Modifier.weight(1f).widthIn(min = 132.dp).heightIn(min = 56.dp),
            shapes = ButtonDefaults.shapes(),
        ) {
            Icon(Icons.Rounded.Shuffle, null, Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.cd_shuffle), style = MaterialTheme.typography.titleMedium)
        }
    }
}
