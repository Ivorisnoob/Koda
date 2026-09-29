package com.ivor.ivormusic.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastRoundToInt
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.data.isUnknownArtist
import com.ivor.ivormusic.ui.components.SongArtwork

/**
 * Below this page width Classic Home keeps its phone composition: the title
 * block above, the collage under it. From here the two sit side by side.
 */
internal val CLASSIC_WIDE_HERO_MIN_WIDTH = 720.dp

/**
 * The widest Classic Home's page grows. Beyond it the page centres, the way a
 * reading column does: shelves spanning a 1900dp desktop window put their
 * first and last cards farther apart than an eye travels, and the hero's two
 * halves drifted to opposite edges with nothing between them.
 */
internal val CLASSIC_PAGE_MAX_WIDTH = 1440.dp

/** The collage's native size, as `OrganicSongLayout` composes it. */
private val COLLAGE_WIDTH = 460.dp
private val COLLAGE_HEIGHT = 480.dp

/**
 * Classic Home's opening on a wide page.
 *
 * The phone version stacks "Your Mix" with Play pinned to the far edge, then
 * the three-cover collage below; stretched across a tablet that left the
 * title and its button a screen apart and the collage alone in the middle.
 * Here the page is split the way a record sleeve is: the words and the
 * actions on the start side with the next few songs of the mix under them
 * (the room a phone does not have, spent on the thing this section is about),
 * and the collage on the end side at its own proportions, scaled down rather
 * than cropped when the window is short.
 *
 * The collage and the title are the same components the phone uses, so the
 * two Homes cannot drift apart.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ClassicWideMixHero(
    songs: List<Song>,
    isLoading: Boolean,
    skeletonAlpha: Float,
    onPlayClick: () -> Unit,
    onSpinClick: () -> Unit,
    onSongClick: (Song) -> Unit,
    onSongLongPress: ((Song) -> Unit)?,
    /** Height the collage may take; it scales down to fit, never up. */
    maxCollageHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val collageScale = (maxCollageHeight / COLLAGE_HEIGHT).coerceIn(0.5f, 1f)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(40.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.your_mix_line1),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = stringResource(R.string.your_mix_line2),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(8.dp))
            if (isLoading) {
                com.ivor.ivormusic.ui.components.SkeletonTextLine(
                    width = 168.dp,
                    height = 14.dp,
                    modifier = Modifier.padding(vertical = 3.dp),
                    alpha = skeletonAlpha
                )
            } else {
                val artists = songs.asSequence()
                    .map { it.artist }
                    .filterNot { isUnknownArtist(it) }
                    .distinct()
                    .take(3)
                    .toList()
                Text(
                    text = artists.joinToString(", ").ifEmpty { stringResource(R.string.unknown_artist) },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onPlayClick,
                    enabled = songs.isNotEmpty(),
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.height(56.dp),
                    contentPadding = ButtonDefaults.contentPaddingFor(56.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.cd_play), style = MaterialTheme.typography.titleMedium)
                }
                FilledTonalButton(
                    onClick = onSpinClick,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.height(56.dp),
                    contentPadding = ButtonDefaults.contentPaddingFor(56.dp)
                ) {
                    Icon(Icons.Rounded.Casino, contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.spin_action), style = MaterialTheme.typography.titleMedium)
                }
            }

            // The collage shows the first three songs; the next ones go here.
            val upNext = songs.drop(3)
            if (!isLoading && upNext.isNotEmpty()) {
                Spacer(Modifier.height(28.dp))
                Text(
                    text = stringResource(R.string.home_mix_up_next),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(8.dp))
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val columns = when {
                        maxWidth >= 900.dp -> 3
                        maxWidth >= 480.dp -> 2
                        else -> 1
                    }
                    // Three rows at most: this is a taste of the mix under its
                    // title, not a second copy of the Library.
                    val shown = upNext.take(columns * 3)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        shown.chunked(columns).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                row.forEach { song ->
                                    MixSongRow(
                                        song = song,
                                        onClick = { onSongClick(song) },
                                        onLongClick = onSongLongPress?.let { press -> { press(song) } },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        }

        ScaledToFit(scale = collageScale, width = COLLAGE_WIDTH, height = COLLAGE_HEIGHT) {
            if (isLoading) {
                OrganicSongLayoutSkeleton(skeletonAlpha = skeletonAlpha)
            } else if (songs.isNotEmpty()) {
                OrganicSongLayout(
                    songs = songs,
                    onSongClick = onSongClick,
                    onSongLongPress = onSongLongPress
                )
            }
        }
    }
}

@Composable
private fun MixSongRow(
    song: Song,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .homeCardClickable(
                shape = RoundedCornerShape(16.dp),
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SongArtwork(
            song = song,
            contentDescription = null,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(12.dp))
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Lays [content] out at exactly [width] by [height] and draws it at [scale],
 * taking only the scaled room. For compositions built from absolute sizes
 * (the collage's pill is a fixed 260 by 500) that must shrink as a picture
 * rather than reflow. Touches follow the drawn position, since the scale is a
 * graphics layer on the placed child.
 */
@Composable
private fun ScaledToFit(
    scale: Float,
    width: Dp,
    height: Dp,
    content: @Composable () -> Unit,
) {
    Layout(content = { Box { content() } }) { measurables, _ ->
        val w = width.roundToPx()
        val h = height.roundToPx()
        val placeable = measurables.first().measure(Constraints.fixed(w, h))
        layout((w * scale).fastRoundToInt(), (h * scale).fastRoundToInt()) {
            placeable.placeWithLayer(0, 0) {
                scaleX = scale
                scaleY = scale
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
            }
        }
    }
}
