package com.ivor.ivormusic.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.R

/**
 * Where a title's playable sources are chosen.
 *
 * **The body is a `LazyColumn` from this first commit, before there is anything
 * to scroll.** A bottom sheet whose content does not scroll has a silent hard
 * ceiling - `VideoOptionsSheet` was clipped identically at zero playlists and at
 * three hundred before it was split - and this list is unbounded by
 * construction, since a single title routinely returns sixty-odd releases.
 *
 * Today it explains why nothing plays. The next phase fills the list with
 * parsed, ranked sources and pins an auto-pick above it; the shell, the scroll
 * and the pinned header are already the right shape for that.
 */
@Composable
fun TvSourceSheet(
    title: String,
    hasStreamSource: Boolean,
    onBrowseAddons: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
        ) {
            Text(
                text = stringResource(R.string.tv_sources_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "explain") {
                    Text(
                        text = stringResource(
                            if (hasStreamSource) R.string.tv_sources_not_built
                            else R.string.tv_sources_none_installed
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!hasStreamSource) {
                    item(key = "action") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = onBrowseAddons) {
                                Text(stringResource(R.string.tv_browse_addons))
                            }
                        }
                    }
                }
            }
        }
    }
}
