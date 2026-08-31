package com.ivor.ivormusic.ui.tv

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ivor.ivormusic.data.tv.TvItem
import com.ivor.ivormusic.data.tv.TvLibraryEntry

/** Standard poster aspect (1:0.675 in the spec, expressed the tall way). */
const val TV_POSTER_RATIO = 0.675f

/** Landscape cards for Continue Watching, matching the spec's `landscape` shape. */
const val TV_LANDSCAPE_RATIO = 16f / 9f

val TvPosterWidth = 124.dp
val TvLandscapeWidth = 232.dp

/**
 * Artwork with a generated fallback.
 *
 * Community addons drop posters constantly, so the fallback is not a rare path.
 * It is a tonal card seeded from the title rather than a broken-image glyph -
 * the same idea `playlistCoverSeeds` already uses for generated covers, and for
 * the same reason: a wall of identical grey rectangles reads as a broken screen
 * while a wall of differently tinted ones reads as art that has not loaded.
 */
@Composable
fun TvArtwork(
    url: String?,
    title: String,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(16.dp),
) {
    Box(modifier = modifier.clip(shape).background(seedColor(title))) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(8.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Movie,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/**
 * A stable tint per title, taken from the theme rather than invented, so the
 * fallback follows palette, AMOLED and dynamic colour like everything else.
 */
@Composable
private fun seedColor(title: String): Color {
    val scheme = MaterialTheme.colorScheme
    val options = listOf(
        scheme.primaryContainer, scheme.secondaryContainer, scheme.tertiaryContainer,
        scheme.surfaceContainerHigh, scheme.surfaceContainerHighest,
    )
    return options[seedIndex(title, options.size)]
}

/**
 * A stable index in `[0, count)` for [title].
 *
 * [scar] The first version masked the hash to unsigned and then narrowed it
 * back with `.toInt()`, which restores the sign and undoes the mask entirely -
 * so any title with a negative hash produced a negative index and Kotlin's
 * truncated `%` kept it negative. That crashed on roughly four in ten titles,
 * which is to say TV mode crashed as soon as a shelf drew a poster without
 * artwork.
 *
 * The modulo happens on the Long, where the masked value is genuinely
 * non-negative, and only the result - which is now known to be in range - is
 * narrowed. Do not "simplify" the order of those two operations.
 */
internal fun seedIndex(title: String, count: Int): Int {
    if (count <= 0) return 0
    return ((title.hashCode().toLong() and 0xFFFFFFFFL) % count).toInt()
}

/** Press physics shared by every tappable TV card. */
@Composable
private fun Modifier.tvCardPress(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "tvCardScale",
    )
    return this
        .scale(scale)
        .combinedClickable(
            interactionSource = interaction,
            indication = null,
            onClick = onClick,
            onLongClick = onLongClick,
        )
}

/** A poster card for a shelf, a search result or the watchlist grid. */
@Composable
fun TvPosterCard(
    item: TvItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    showTitle: Boolean = true,
) {
    Column(
        modifier = modifier
            .width(TvPosterWidth)
            .tvCardPress(onClick, onLongClick)
    ) {
        Box {
            TvArtwork(
                url = item.poster,
                title = item.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(TV_POSTER_RATIO),
            )
            item.imdbRating?.takeIf { it.isNotBlank() }?.let { rating ->
                RatingBadge(rating, Modifier.align(Alignment.TopEnd).padding(6.dp))
            }
        }
        if (showTitle) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
            item.releaseInfo?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/** The watchlist grid's card: same art, sized by its parent rather than fixed. */
@Composable
fun TvLibraryCard(
    entry: TvLibraryEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    Column(modifier = modifier.tvCardPress(onClick, onLongClick)) {
        TvArtwork(
            url = entry.poster,
            title = entry.name,
            modifier = Modifier.fillMaxWidth().aspectRatio(TV_POSTER_RATIO),
        )
        Text(
            text = entry.name,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/**
 * Continue Watching: a landscape card with a determinate progress bar and the
 * episode it stopped on.
 */
@Composable
fun TvContinueCard(
    entry: TvLibraryEntry,
    progressFraction: Float,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .width(TvLandscapeWidth)
            .tvCardPress(onClick, onLongClick)
    ) {
        Box {
            TvArtwork(
                url = entry.background ?: entry.poster,
                title = entry.name,
                modifier = Modifier.fillMaxWidth().aspectRatio(TV_LANDSCAPE_RATIO),
            )
            LinearProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(4.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f),
                drawStopIndicator = {},
                gapSize = 0.dp,
            )
        }
        Text(
            text = entry.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun RatingBadge(rating: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.88f),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.inversePrimary,
                modifier = Modifier.size(11.dp),
            )
            Text(
                text = rating,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier.padding(start = 3.dp),
            )
        }
    }
}

/** Bottom-up scrim so a logo or title stays readable over arbitrary artwork. */
@Composable
fun BoxScopeScrim(modifier: Modifier = Modifier, heightFraction: Float = 0.75f) {
    val scrim = MaterialTheme.colorScheme.scrim
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to scrim.copy(alpha = 0f),
                    (1f - heightFraction) to scrim.copy(alpha = 0f),
                    1f to scrim.copy(alpha = 0.82f),
                )
            )
    )
}

/** Section header shared by every shelf and search group. */
@Composable
fun TvSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (!trailing.isNullOrBlank()) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}
