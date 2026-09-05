package com.ivor.ivormusic.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
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
 */

/** One rounded container holding a run of [OptionRow]s. */
@Composable
internal fun OptionGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(content = content)
    }
}

@Composable
internal fun OptionRowDivider() {
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
