package com.ivor.ivormusic.ui.video

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.SponsorCategory
import com.ivor.ivormusic.data.SponsorSegment

/**
 * The two SponsorBlock affordances that live over the video.
 *
 * One composable rather than two because they are mutually exclusive and
 * occupy the same spot: a skip that just happened and can be undone, or a
 * segment being sat in that the viewer can choose to skip. Showing both at
 * once is not a state that can occur - performing the manual skip replaces it
 * with the notice, and the notice's segment is in the ignore set.
 *
 * Deliberately **not** gated on control visibility. A skip is the one thing
 * that happens without the viewer asking, so the notice has to appear whether
 * or not the chrome is up - otherwise an automatic skip during quiet playback
 * is silent and unundoable, which is the failure the undo exists to prevent.
 */
@Composable
internal fun SponsorBlockOverlay(
    skipNotice: SponsorSkipNotice?,
    manualSegment: SponsorSegment?,
    onUndoSkip: () -> Unit,
    onSkipSegment: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        AnimatedVisibility(
            visible = skipNotice != null,
            enter = fadeIn() + slideInVertically(
                initialOffsetY = { it / 2 },
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
            ),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
        ) {
            // Held across the exit animation so the chip does not blank its
            // own text on the way out.
            val notice = skipNotice ?: return@AnimatedVisibility
            SkippedChip(
                category = notice.segment.category,
                onUndo = onUndoSkip
            )
        }

        AnimatedVisibility(
            visible = skipNotice == null && manualSegment != null,
            enter = fadeIn() + slideInVertically(
                initialOffsetY = { it / 2 },
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
            ),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
        ) {
            val segment = manualSegment ?: return@AnimatedVisibility
            SkipButtonChip(
                category = segment.category,
                onSkip = onSkipSegment
            )
        }
    }
}

@Composable
private fun SkippedChip(category: SponsorCategory, onUndo: () -> Unit) {
    ChipSurface {
        CategoryDot(category)
        Spacer(Modifier.width(10.dp))
        Text(
            text = stringResource(R.string.sb_skipped, stringResource(categoryLabel(category))),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.width(4.dp))
        TextButton(onClick = onUndo) {
            Text(
                text = stringResource(R.string.undo),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SkipButtonChip(category: SponsorCategory, onSkip: () -> Unit) {
    ChipSurface {
        CategoryDot(category)
        Spacer(Modifier.width(10.dp))
        TextButton(onClick = onSkip) {
            Icon(
                imageVector = Icons.Rounded.FastForward,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.sb_skip, stringResource(categoryLabel(category))),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * The shared shell. An opaque tonal surface rather than a translucent scrim,
 * because this sits over arbitrary video frames and has to stay readable
 * against a white one.
 */
@Composable
private fun ChipSurface(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            content()
        }
    }
}

/** Ties the chip to the mark on the seek bar it came from. */
@Composable
private fun CategoryDot(category: SponsorCategory) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(category.color)
    )
}

/** The user-facing name of a category, shared with the settings page. */
internal fun categoryLabel(category: SponsorCategory): Int = when (category) {
    SponsorCategory.SPONSOR -> R.string.sb_cat_sponsor
    SponsorCategory.SELF_PROMO -> R.string.sb_cat_selfpromo
    SponsorCategory.INTERACTION -> R.string.sb_cat_interaction
    SponsorCategory.INTRO -> R.string.sb_cat_intro
    SponsorCategory.OUTRO -> R.string.sb_cat_outro
    SponsorCategory.PREVIEW -> R.string.sb_cat_preview
    SponsorCategory.MUSIC_OFFTOPIC -> R.string.sb_cat_music_offtopic
    SponsorCategory.FILLER -> R.string.sb_cat_filler
}

/** The one-line explanation of what a category actually covers. */
internal fun categoryDescription(category: SponsorCategory): Int = when (category) {
    SponsorCategory.SPONSOR -> R.string.sb_cat_sponsor_sub
    SponsorCategory.SELF_PROMO -> R.string.sb_cat_selfpromo_sub
    SponsorCategory.INTERACTION -> R.string.sb_cat_interaction_sub
    SponsorCategory.INTRO -> R.string.sb_cat_intro_sub
    SponsorCategory.OUTRO -> R.string.sb_cat_outro_sub
    SponsorCategory.PREVIEW -> R.string.sb_cat_preview_sub
    SponsorCategory.MUSIC_OFFTOPIC -> R.string.sb_cat_music_offtopic_sub
    SponsorCategory.FILLER -> R.string.sb_cat_filler_sub
}
