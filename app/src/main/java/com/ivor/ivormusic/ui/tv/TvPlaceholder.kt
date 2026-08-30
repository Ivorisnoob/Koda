package com.ivor.ivormusic.ui.tv

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.R

/**
 * Which of TV mode's three tabs a [TvPlaceholder] is standing in for.
 *
 * This whole file is scaffolding for the mode's first phase: the tab system,
 * the toggle and the persisted mode are real, and the content is not built yet.
 * Each tab says what will live there rather than showing one shared "coming
 * soon", because a mode where every tab looks identical reads as broken rather
 * than unfinished.
 */
enum class TvPlaceholderTab {
    HOME,
    SEARCH,
    LIBRARY,
}

@Composable
fun TvPlaceholder(
    tab: TvPlaceholderTab,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val (icon: ImageVector, titleRes: Int, bodyRes: Int) = when (tab) {
        TvPlaceholderTab.HOME -> Triple(
            Icons.Rounded.Movie,
            R.string.tv_placeholder_home_title,
            R.string.tv_placeholder_home_body
        )
        TvPlaceholderTab.SEARCH -> Triple(
            Icons.Rounded.Search,
            R.string.tv_placeholder_search_title,
            R.string.tv_placeholder_search_body
        )
        TvPlaceholderTab.LIBRARY -> Triple(
            Icons.Rounded.VideoLibrary,
            R.string.tv_placeholder_library_title,
            R.string.tv_placeholder_library_body
        )
    }

    // Matches the staggered entrance the rest of the app uses, so switching
    // into TV mode does not feel like landing on a different app's empty page.
    var visible by remember(tab) { mutableStateOf(false) }
    LaunchedEffect(tab) { visible = true }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.animation.AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + scaleIn(
                initialScale = 0.9f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
            )
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.widthIn(max = 340.dp)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                Text(
                    text = stringResource(titleRes),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(bodyRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(R.string.tv_placeholder_footnote),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
