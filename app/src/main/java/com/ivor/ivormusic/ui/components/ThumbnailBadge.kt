package com.ivor.ivormusic.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.VideoItem

/**
 * The duration or LIVE label in the corner of a video thumbnail.
 *
 * **It sits on a photograph, so it takes its contrast from a scrim rather than
 * from the theme.** Eight surfaces drew this badge by hand and two of them had
 * reached for `surfaceContainerHigh`, which is a sensible-looking choice and
 * the wrong one: a thumbnail is the same image in every palette, so a
 * theme-derived container means near-black text on a bright frame in light mode
 * and near-white text on the same frame in dark mode. One of the two is always
 * unreadable, and which one flips with a setting that has nothing to do with
 * the image. The remaining six had each arrived independently at black-and-white
 * and drifted to six different alphas and paddings.
 *
 * [MaterialTheme.colorScheme.scrim] keeps this inside invariant 1: scrim is a
 * `ColorScheme` role, and Material defines it as black in every scheme, palette
 * style and dynamic-color extraction, which is exactly the property wanted. The
 * label is the one literal, because M3 publishes no `onScrim` and the pair is
 * now written once in this file rather than eight times.
 *
 * Live keeps its red. That is the universal signifier for a broadcast rather
 * than decoration - the same reasoning that lets Super Chat and SponsorBlock
 * carry their own colors - and a scrim-grey LIVE reads as a duration.
 */
@Composable
fun ThumbnailBadge(
    text: String,
    modifier: Modifier = Modifier,
    live: Boolean = false
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = if (live) LIVE_BADGE_RED else MaterialTheme.colorScheme.scrim.copy(alpha = 0.8f)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (live) FontWeight.Bold else FontWeight.Medium,
            color = Color.White,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/**
 * The badge for a [VideoItem], drawing nothing when there is nothing honest to
 * say - an unknown duration on a VOD is blank rather than "0:00".
 */
@Composable
fun VideoThumbnailBadge(video: VideoItem, modifier: Modifier = Modifier) {
    when {
        video.isLive -> ThumbnailBadge(
            text = stringResource(R.string.badge_live),
            modifier = modifier,
            live = true
        )
        video.duration > 0L -> ThumbnailBadge(
            text = video.formattedDuration,
            modifier = modifier
        )
    }
}

private val LIVE_BADGE_RED = Color(0xFFFF0000)
