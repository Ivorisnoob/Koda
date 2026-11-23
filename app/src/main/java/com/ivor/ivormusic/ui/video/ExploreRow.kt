package com.ivor.ivormusic.ui.video

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.ExploreTopic

/**
 * Video Home's Explore row (#358): "For you" (the feed), then YouTube's
 * topics. Picking a topic shows its videos in place of the feed; "For you"
 * brings the feed back where it was.
 */
@Composable
internal fun ExploreTopicRow(
    selected: ExploreTopic?,
    onSelect: (ExploreTopic?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = com.ivor.ivormusic.util.rememberKodaHaptics()
    val entries: List<ExploreTopic?> = listOf(null) + ExploreTopic.entries
    LazyRow(
        state = rememberLazyListState(),
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(entries, key = { it?.name ?: "for_you" }) { topic ->
            val isSelected = topic == selected
            FilterChip(
                selected = isSelected,
                onClick = {
                    if (!isSelected) {
                        haptics.tick()
                        onSelect(topic)
                    }
                },
                label = {
                    Text(stringResource(topic?.labelRes ?: R.string.explore_for_you))
                },
                shape = CircleShape,
            )
        }
    }
}

/** A topic that is loading for the first time, or came back empty. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ExploreTopicStatus(
    isLoading: Boolean,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (isLoading) {
            LoadingIndicator(modifier = Modifier.size(48.dp), color = MaterialTheme.colorScheme.primary)
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.explore_failed),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                FilledTonalButton(onClick = onRetry) {
                    Text(stringResource(R.string.explore_retry))
                }
            }
        }
    }
}
