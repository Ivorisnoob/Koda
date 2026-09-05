package com.ivor.ivormusic.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.data.googleImageAtSize

/**
 * A creator's avatar, drawn at a size worth looking at.
 *
 * The picture is the whole content of a 140dp artist card, and it was being
 * asked for at whatever size the search response happened to return - often
 * `=s48`, blown up nine times. This asks for [targetPx] and layers it over the
 * original, so a channel with no large avatar loses sharpness rather than
 * showing nothing.
 *
 * Everything else - the held-off Material 3 loading indicator, the two retries
 * for a fetch that dropped, the settled broken-image state - is
 * [VideoThumbnail]'s, deliberately: one implementation of "an image that might
 * not arrive" rather than a second one that drifts from it.
 */
@Composable
fun AvatarImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    targetPx: Int = com.ivor.ivormusic.data.AVATAR_TARGET_PX,
    showProgress: Boolean = true,
    indicatorSize: Dp = 28.dp,
    emptyIcon: ImageVector? = Icons.Rounded.Person,
    placeholderColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest
) {
    VideoThumbnail(
        thumbnailUrl = url,
        contentDescription = contentDescription,
        modifier = modifier,
        highResThumbnailUrl = googleImageAtSize(url, targetPx),
        showProgress = showProgress,
        indicatorSize = indicatorSize,
        emptyIcon = emptyIcon,
        placeholderColor = placeholderColor
    )
}
