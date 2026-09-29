package com.ivor.ivormusic.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.googleImageAtSize
import com.ivor.ivormusic.ui.components.VideoThumbnail

/**
 * From this pane width the playlist page splits: header in a column of its
 * own at the start, tracks beside it. Below it a phone's single list, with
 * the cover as a banner across the top.
 *
 * [judgement] The banner is right for a phone and wrong beside a track list:
 * across a 1000dp pane it becomes a letterbox of a square image, cropped to a
 * strip, and pushes the first track below the fold. A square cover in a
 * column is the record sleeve next to its tracklist.
 */
internal val PLAYLIST_WIDE_MIN_WIDTH = 760.dp

/**
 * From this content width the Library is list and detail side by side: the
 * list at 340-440dp and at least 500dp left for what it opens. A phone on its
 * side falls just short and keeps the stack, which is the better answer at
 * that height anyway.
 */
internal val LIBRARY_TWO_PANE_MIN_WIDTH = 840.dp

/**
 * What the detail pane beside the Library list is showing, so the card that
 * opened it can say so. Provided only in the two-pane layout: on a phone the
 * page covers the list and there is nothing to point back at.
 */
internal data class LibraryOpenItem(
    val playlistId: String? = null,
    val albumName: String? = null,
    val artistName: String? = null,
)

internal val LocalLibraryOpenItem =
    androidx.compose.runtime.compositionLocalOf<LibraryOpenItem?> { null }

/** The ring an open card wears: the accent at a weight that reads on any cover. */
@Composable
internal fun libraryOpenBorder(open: Boolean): androidx.compose.foundation.BorderStroke? =
    if (open) androidx.compose.foundation.BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null

/**
 * The detail pane before anything is chosen. Two panes means the right one is
 * always on screen, and an empty half of a tablet reads as a failed load; this
 * says what the space is for instead, in the Library's own shape language.
 */
@androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
@Composable
internal fun LibraryDetailPlaceholder(
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(contentPadding),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Surface(
                shape = androidx.compose.material3.MaterialShapes.Cookie9Sided.toShape(),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(112.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.PlaylistPlay,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
            androidx.compose.foundation.layout.Spacer(Modifier.size(20.dp))
            androidx.compose.material3.Text(
                text = stringResource(R.string.lib_detail_placeholder_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            androidx.compose.foundation.layout.Spacer(Modifier.size(6.dp))
            androidx.compose.material3.Text(
                text = stringResource(R.string.lib_detail_placeholder_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

/**
 * The cover as it sits in the wide page's header column: square, rounded,
 * inset by the page gutter, with the same fallback glyph and (for a local
 * playlist) the same tap-to-change affordance the banner has.
 */
@Composable
internal fun PlaylistSideCover(
    heroArt: String?,
    isAlbum: Boolean,
    onPickCover: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = PLAYLIST_GUTTER)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .then(if (onPickCover != null) Modifier.clickable(onClick = onPickCover) else Modifier)
    ) {
        if (heroArt != null) {
            VideoThumbnail(
                thumbnailUrl = heroArt,
                highResThumbnailUrl = googleImageAtSize(heroArt, HERO_COVER_PX)
                    ?.takeIf { it != heroArt },
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                showProgress = false,
                placeholderColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        } else {
            Icon(
                imageVector = if (isAlbum) Icons.Rounded.Album else Icons.AutoMirrored.Rounded.PlaylistPlay,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(72.dp)
            )
        }
        if (onPickCover != null) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .clickable(onClick = onPickCover)
            ) {
                Icon(
                    Icons.Rounded.PhotoCamera,
                    contentDescription = stringResource(R.string.lib_change_cover_art),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .padding(10.dp)
                        .size(20.dp)
                )
            }
        }
    }
}
