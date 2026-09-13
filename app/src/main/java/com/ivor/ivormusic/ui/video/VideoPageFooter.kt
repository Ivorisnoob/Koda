package com.ivor.ivormusic.ui.video

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.PageLoadState

/** Only detail screens own this effect. A Library preview cannot advance a cursor. */
@Composable
internal fun LoadVideoPageAtEnd(
    listState: LazyListState,
    itemCount: Int,
    pageState: PageLoadState,
    onLoadMore: () -> Unit
) {
    val atEnd by remember(listState) {
        derivedStateOf {
            listState.canScrollBackward && listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
                ?.let { it >= listState.layoutInfo.totalItemsCount - 2 } == true
        }
    }
    var lastRequestedCount by remember(listState) { mutableIntStateOf(-1) }
    LaunchedEffect(atEnd, itemCount, pageState) {
        if (!atEnd) {
            lastRequestedCount = -1
        } else if (itemCount > 0 && itemCount != lastRequestedCount &&
            pageState.hasMore && !pageState.isLoading && !pageState.failed) {
            lastRequestedCount = itemCount
            onLoadMore()
        }
    }
}

/** Failure retains the cursor and earlier rows; retry is an explicit action. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun VideoPageFooter(state: PageLoadState, onLoadMore: () -> Unit) {
    if (!state.isLoading && !state.hasMore && !state.failed) return
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (state.isLoading) {
            LoadingIndicator()
        } else {
            if (state.failed) Text(
                text = stringResource(R.string.video_page_load_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onLoadMore) {
                Text(stringResource(if (state.failed) R.string.action_retry else R.string.load_more))
            }
        }
    }
}
