package com.ivor.ivormusic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest

/**
 * A reusable album art image component that shows a music icon placeholder
 * when the image fails to load or is loading.
 * 
 * @param imageUrl The URL of the album art to load
 * @param contentDescription Content description for accessibility
 * @param modifier Modifier for the composable
 * @param size The size of the image (used for both width and height)
 * @param shape The shape to clip the image to
 * @param placeholderBackgroundColor Background color for the placeholder
 * @param placeholderIconColor Color for the music note icon
 */
@Composable
fun AlbumArtImage(
    imageUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    shape: Shape = RoundedCornerShape(12.dp),
    placeholderBackgroundColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    placeholderIconColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
) {
    val context = LocalContext.current
    
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(context)
            .data(imageUrl)
            .crossfade(true)
            .build(),
        contentDescription = contentDescription,
        modifier = modifier
            .size(size)
            .clip(shape),
        contentScale = ContentScale.Crop,
        loading = {
            MusicPlaceholder(
                backgroundColor = placeholderBackgroundColor,
                iconColor = placeholderIconColor,
                iconSize = size * 0.5f
            )
        },
        error = {
            MusicPlaceholder(
                backgroundColor = placeholderBackgroundColor,
                iconColor = placeholderIconColor,
                iconSize = size * 0.5f
            )
        }
    )
}

/**
 * A placeholder with a music note icon, used when album art fails to load.
 */
@Composable
fun MusicPlaceholder(
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    iconColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
    iconSize: Dp = 24.dp
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(iconSize)
        )
    }
}

/**
 * A variant that takes a nullable Uri for local album art as well.
 */
@Composable
fun AlbumArtImage(
    imageUrl: String?,
    localUri: android.net.Uri?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    shape: Shape = RoundedCornerShape(12.dp),
    placeholderBackgroundColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    placeholderIconColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
) {
    val context = LocalContext.current
    
    // Prefer local URI, fallback to remote URL
    val imageData: Any? = localUri ?: imageUrl
    
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(context)
            .data(imageData)
            .crossfade(true)
            .build(),
        contentDescription = contentDescription,
        modifier = modifier
            .size(size)
            .clip(shape),
        contentScale = ContentScale.Crop,
        loading = {
            MusicPlaceholder(
                backgroundColor = placeholderBackgroundColor,
                iconColor = placeholderIconColor,
                iconSize = size * 0.5f
            )
        },
        error = {
            MusicPlaceholder(
                backgroundColor = placeholderBackgroundColor,
                iconColor = placeholderIconColor,
                iconSize = size * 0.5f
            )
        }
    )
}
