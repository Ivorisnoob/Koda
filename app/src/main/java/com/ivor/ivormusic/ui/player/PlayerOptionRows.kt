package com.ivor.ivormusic.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.ui.components.SongArtwork

/**
 * The row language shared by music mode's two option sheets - the long-press
 * [SongOptionsSheet] and the now-playing [NowPlayingOptionsSheet].
 *
 * One file rather than a copy each, for the reason `QueueReorder.kt` is one
 * file: the two sheets are opened by different gestures on different screens
 * and must not drift into two different-looking menus, but only the *rows* are
 * the same - what goes in them is each sheet's own business. It matches
 * `VideoOptionsSheet`'s rows on the video side, so the same gesture produces a
 * recognisably similar sheet in both modes.
 *
 * It holds three shapes now, not one, and which a sheet reaches for is about
 * what the sheet is. [OptionRow] is a list line: a long-press menu of one-shot
 * actions on some row in a list. [OptionTile] and [OptionUtility] are a control
 * panel: the now-playing sheet is opened *from* the player, on the song already
 * playing, and the things in it are pressed and toggled rather than read - so
 * the four common actions are filled tiles under the thumb, and the occasional
 * ones sit unfilled on the same columns beneath them. The destinations there
 * are [OptionRow]s, because a name is the label. The vocabulary stays shared so
 * the two sheets cannot drift into two different-looking menus.
 */

/**
 * A run of [OptionRow]s as a Material 3 Expressive segmented group, each row
 * its own container with its ripple clipped to it. The height is the rows'
 * own, so a sheet holding groups still scrolls as it did.
 */
@Composable
internal fun OptionGroup(content: @Composable ColumnScope.() -> Unit) {
    com.ivor.ivormusic.ui.components.SegmentedColumn(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        content = content
    )
}

@Composable
internal fun OptionRowDivider() {
    // The segment gaps separate rows inside a group.
    if (com.ivor.ivormusic.ui.components.LocalInSegmentedColumn.current) return
    HorizontalDivider(
        // Indented past the icon column, so the divider separates the labels
        // rather than cutting the row in half.
        modifier = Modifier.padding(start = 56.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
    )
}

/** Whether the row goes somewhere, is working, has already happened, or acts. */
internal enum class OptionRowTrailing { NONE, CHEVRON, CHECK, LOADING }

/**
 * One action inside a group: 24dp icon, title, optional subtitle, and a
 * trailing glyph.
 *
 * Deliberately lighter than a standalone card - no icon plate, no per-row
 * spring scale, ripple for the press - because a menu of six standalone cards
 * is what overflows a bottom sheet.
 */
@Composable
internal fun OptionRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    subtitle: String? = null,
    trailing: OptionRowTrailing = OptionRowTrailing.NONE,
    enabled: Boolean = true,
    /** Overrides the accent, for a row whose state is the icon (liked). */
    iconTint: Color? = null
) {
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val resolvedIconTint = iconTint ?: if (enabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            // A minimum rather than a fixed height: the labels grow with the
            // user's font scale instead of being cut off by a literal.
            .heightIn(min = 56.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = resolvedIconTint,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (trailing != OptionRowTrailing.NONE) {
            Spacer(modifier = Modifier.width(12.dp))
            when (trailing) {
                OptionRowTrailing.CHEVRON -> Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )

                OptionRowTrailing.CHECK -> Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )

                OptionRowTrailing.LOADING -> LoadingIndicator(
                    modifier = Modifier.size(22.dp),
                    color = MaterialTheme.colorScheme.primary
                )

                OptionRowTrailing.NONE -> Unit
            }
        }
    }
}

/**
 * One action as a tile: a 24dp icon over a one-word label, sized to sit four
 * across in a row.
 *
 * **A tile is for an action that is used, not read.** The rows above are a list
 * you scan once; these are the four things someone opens the player's overflow
 * to *press*, and at four across they are all reachable with one thumb without
 * the sheet growing. [selected] is the state of a toggle - liked, downloaded -
 * carried by the container rather than a tick, so the state is legible from
 * across the sheet; the shape squares off as it fills, the same selection
 * language as the app's connected [androidx.compose.material3.ToggleButton]
 * groups.
 *
 * The label sits on two lines at most, because the tile's width is a quarter of
 * the sheet and a large display scale is exactly where one line would clip.
 */
@Composable
internal fun OptionTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    /** Replaces the icon while the action is in flight (a download starting). */
    loading: Boolean = false,
) {
    val corner by animateDpAsState(
        targetValue = if (selected) 16.dp else 26.dp,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "optionTileCorner"
    )
    val container by animateColorAsState(
        targetValue = when {
            selected -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "optionTileContainer"
    )
    val content = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val accent = if (selected) content else MaterialTheme.colorScheme.primary

    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 86.dp),
        enabled = enabled,
        shape = RoundedCornerShape(corner),
        color = container,
        contentColor = content
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (loading) {
                LoadingIndicator(
                    modifier = Modifier.size(24.dp),
                    color = accent
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = content,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * A secondary action: an icon over a short label, straight on the sheet with no
 * container of its own.
 *
 * **Laid on the same columns as the [OptionTile]s above it.** The sheet's
 * actions form one grid - four filled tiles, then a row of these - so every
 * icon centre lines up with the one above it and the eye reads two rows of
 * one panel rather than a second, differently shaped widget. The difference in
 * emphasis is carried by the fill alone: tiles are the things this sheet is
 * opened to press, these are the ones reached for now and then, and a second
 * row of filled containers would give the panel two loud bands. The press
 * still has a rounded ripple, so the touch target is the whole column.
 *
 * [quiet] mutes the icon for the one action that takes something away.
 */
@Composable
internal fun OptionUtility(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Read instead of [label] by accessibility services, where it says more. */
    contentDescription: String? = null,
    quiet: Boolean = false,
) {
    Column(
        modifier = modifier
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .then(
                if (contentDescription != null) {
                    Modifier.clearAndSetSemantics {
                        this.contentDescription = contentDescription
                        role = Role.Button
                    }
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 4.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (quiet) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            // Two lines, for the same reason as a tile's: a quarter of the
            // sheet at a large display scale is where one line would clip.
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Artwork, title and artist, so a sheet says which song it is acting on. */
@Composable
internal fun SongOptionsHeader(song: Song) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // The 8dp below joins the sheet column's own 8dp gap, so the header
            // stands further off the first group than the groups do off each
            // other - it is a caption, not another action.
            .padding(top = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(56.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest
        ) {
            SongArtwork(
                song = song,
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                // Two lines: a long track name truncated to one is the same
                // ellipsis for every song in an album, which tells the user
                // nothing about which one they pressed.
                maxLines = 2,
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
