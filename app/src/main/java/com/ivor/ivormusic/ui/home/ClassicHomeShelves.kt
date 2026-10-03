package com.ivor.ivormusic.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ivor.ivormusic.R

/**
 * The press every Classic Home rail card shares: the card settles back a few
 * percent under the finger on the expressive fast spatial spring, and the
 * ripple is clipped to [shape] so it follows the card's outline.
 *
 * Scale is drawn in `graphicsLayer`, so the press never re-lays-out the rail,
 * and a spring rather than a tween so a quick tap-and-release is interrupted
 * cleanly mid-flight. With animations off the spring settles instantly.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun Modifier.homeCardClickable(
    shape: Shape,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "homeCardPress",
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clip(shape)
        .combinedClickable(
            interactionSource = interaction,
            indication = ripple(),
            role = Role.Button,
            onClick = onClick,
            onLongClick = onLongClick,
        )
}

/**
 * The Liked songs entry, leading the Your playlists rail.
 *
 * The one deliberately loud card on Classic Home: the whole tile is the
 * primary container and the heart sits on a SoftBurst in primary, the shape
 * library's badge the Library and Stats already use for "yours". Everything
 * around it is artwork, so a colour field is enough to find it at a glance
 * without a cover of its own.
 */
@Composable
internal fun LikedSongsTile(count: Int, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(ARTWORK_SIZE)
            .homeCardClickable(HOME_CARD_SHAPE, onClick = onClick)
            .padding(bottom = CAPTION_BOTTOM_INSET)
    ) {
        Box(
            modifier = Modifier
                .size(ARTWORK_SIZE)
                .clip(HOME_CARD_SHAPE)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(MaterialShapes.SoftBurst.toShape())
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Favorite,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(CAPTION_GAP))
        Text(
            text = stringResource(R.string.shortcut_liked_songs),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = CAPTION_SIDE_INSET)
        )
        Text(
            text = pluralStringResource(R.plurals.n_songs, count, count),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = CAPTION_SIDE_INSET)
        )
    }
}

/** Avatar edge on the top artists rail. */
private val ARTIST_AVATAR_SIZE = 104.dp

/**
 * The artists the user plays most lately, as faces.
 *
 * Masked to the 9-sided cookie the artist page itself draws its avatar in,
 * so the rail and the page it opens read as the same person. Until a photo is
 * known the cookie carries the artist's initial on the secondary container -
 * a deliberate placeholder, never a stranger's face - and the photo, when it
 * lands, is drawn over it.
 */
@Composable
internal fun TopArtistsSection(
    artists: List<TopArtist>,
    photos: Map<String, String>,
    onArtistClick: (TopArtist) -> Unit,
) {
    if (artists.isEmpty()) return
    val avatarShape = MaterialShapes.Cookie9Sided.toShape()

    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(24.dp))
        HomeSectionHeader(title = stringResource(R.string.home_section_top_artists))

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(artists, key = { "artist_${it.name.lowercase()}" }) { artist ->
                Column(
                    modifier = Modifier
                        .width(ARTIST_AVATAR_SIZE + 16.dp)
                        .homeCardClickable(HOME_CARD_SHAPE, onClick = { onArtistClick(artist) })
                        .padding(top = 8.dp, bottom = CAPTION_BOTTOM_INSET),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(ARTIST_AVATAR_SIZE)
                            .clip(avatarShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = artist.name.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "#",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        photos[artist.name]?.let { url ->
                            AsyncImage(
                                model = url,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(CAPTION_GAP))
                    Text(
                        text = artist.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = CAPTION_SIDE_INSET)
                    )
                    Text(
                        text = pluralStringResource(R.plurals.n_plays, artist.plays, artist.plays),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = CAPTION_SIDE_INSET)
                    )
                }
            }
        }
    }
}
