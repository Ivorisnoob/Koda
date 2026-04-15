package com.ivor.ivormusic.ui.components

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Expressive Pull-to-Refresh container using Material 3 Expressive ContainedLoadingIndicator.
 * 
 * This provides a visually engaging loading experience with shape morphing animation
 * that scales based on pull distance and continues to animate during refresh.
 * 
 * @param isRefreshing Whether the refresh operation is currently in progress
 * @param onRefresh Callback invoked when the user triggers a refresh
 * @param modifier Modifier to apply to the container
 * @param indicatorAlignment Alignment of the loading indicator (default: TopCenter)
 * @param containerColor Optional container color for the loading indicator
 * @param indicatorColor Optional indicator color for the loading animation
 * @param content The content to display within the pull-to-refresh container
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressivePullToRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    indicatorAlignment: Alignment = Alignment.TopCenter,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    indicatorColor: Color = MaterialTheme.colorScheme.primary,
    content: @Composable BoxScope.() -> Unit
) {
    val state = rememberPullToRefreshState()
    
    // Calculate scale based on pull distance with easing
    val scaleFraction = {
        if (isRefreshing) 1f
        else LinearOutSlowInEasing.transform(state.distanceFraction).coerceIn(0f, 1f)
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .pullToRefresh(
                state = state,
                isRefreshing = isRefreshing,
                onRefresh = onRefresh
            )
    ) {
        // Main content
        content()
        
        // Expressive Loading Indicator with scale animation
        Box(
            modifier = Modifier
                .align(indicatorAlignment)
                .graphicsLayer {
                    scaleX = scaleFraction()
                    scaleY = scaleFraction()
                }
        ) {
            PullToRefreshDefaults.LoadingIndicator(
                state = state,
                isRefreshing = isRefreshing,
                color = indicatorColor,
                containerColor = containerColor
            )
        }
    }
}

/**
 * Overload that accepts an external PullToRefreshState for more control.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressivePullToRefresh(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    indicatorAlignment: Alignment = Alignment.TopCenter,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    indicatorColor: Color = MaterialTheme.colorScheme.primary,
    content: @Composable BoxScope.() -> Unit
) {
    // Calculate scale based on pull distance with easing
    val scaleFraction = {
        if (isRefreshing) 1f
        else LinearOutSlowInEasing.transform(state.distanceFraction).coerceIn(0f, 1f)
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .pullToRefresh(
                state = state,
                isRefreshing = isRefreshing,
                onRefresh = onRefresh
            )
    ) {
        // Main content
        content()
        
        // Expressive Loading Indicator with scale animation
        Box(
            modifier = Modifier
                .align(indicatorAlignment)
                .graphicsLayer {
                    scaleX = scaleFraction()
                    scaleY = scaleFraction()
                }
        ) {
            PullToRefreshDefaults.LoadingIndicator(
                state = state,
                isRefreshing = isRefreshing,
                color = indicatorColor,
                containerColor = containerColor
            )
        }
    }
}
