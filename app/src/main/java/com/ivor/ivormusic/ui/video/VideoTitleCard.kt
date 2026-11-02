package com.ivor.ivormusic.ui.video

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.RichText
import com.ivor.ivormusic.data.VideoItem
import com.ivor.ivormusic.ui.components.rememberLinkedText
import com.ivor.ivormusic.util.rememberKodaHaptics

/**
 * The title and what the video is about, in one card that opens in place.
 *
 * Closed it takes a fixed amount of the page: two lines of title and [status],
 * so a very long title can no longer push the actions and the channel off the
 * screen. Open it holds the whole title, the views and date large, and the
 * description. Only the top of the card toggles, so the links and the selection
 * in the description keep their own taps. A short title with nothing to reveal
 * draws no chevron and takes no tap.
 */
@Composable
internal fun VideoTitleCard(
    video: VideoItem,
    /** Tiles for the views and date; off for live and offline, which have neither. */
    showStats: Boolean,
    /** Description is off for live and offline, as it always was. */
    showDescription: Boolean,
    onSeekTo: ((seconds: Long) -> Unit)?,
    status: @Composable () -> Unit,
) {
    val haptics = rememberKodaHaptics()
    var expanded by remember(video.videoId) { mutableStateOf(false) }
    var titleOverflows by remember(video.videoId, video.title) { mutableStateOf(false) }
    val description = if (showDescription) cleanDescription(video) else ""
    val canExpand = titleOverflows || description.isNotEmpty() || expanded
    val turn by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "titleChevron"
    )

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.animateContentSize(
                spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(enabled = canExpand) {
                        haptics.subtle()
                        expanded = !expanded
                    }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = video.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = if (expanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis,
                        onTextLayout = { result ->
                            if (!expanded) titleOverflows = result.hasVisualOverflow
                        }
                    )
                    if (!expanded || !showStats) status()
                }
                if (canExpand) {
                    Icon(
                        Icons.Rounded.ExpandMore,
                        contentDescription = stringResource(
                            if (expanded) R.string.vp_show_less else R.string.action_show_more
                        ),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp).rotate(turn)
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (showStats) VideoStatTiles(video)
                    if (description.isNotEmpty()) {
                        val linked = rememberLinkedText(
                            rich = RichText(description, video.descriptionLinks),
                            onTimestampClick = onSeekTo
                        )
                        SelectionContainer {
                            Text(
                                text = linked,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * YouTube's link offsets are measured against the raw attributedDescription
 * text, so it must not be rewritten when we have them. Only the
 * NewPipe-sourced descriptions (which carry no links) still need HTML
 * unescaping.
 */
@Composable
private fun cleanDescription(video: VideoItem): String =
    remember(video.description, video.descriptionLinks) {
        val raw = video.description
        when {
            raw.isNullOrBlank() -> ""
            video.descriptionLinks.isNotEmpty() -> raw
            else -> androidx.core.text.HtmlCompat.fromHtml(
                raw,
                androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY
            ).toString().trim()
        }
    }

/** "137M views" and "6 years ago" as a big figure over a small word. */
@Composable
private fun VideoStatTiles(video: VideoItem) {
    val tiles = listOfNotNull(
        video.viewCount.takeIf { it.isNotBlank() }?.let(::splitStat),
        video.uploadedDate?.takeIf { it.isNotBlank() }?.let(::splitStat),
    )
    if (tiles.isEmpty()) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tiles.forEach { (figure, word) ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = figure,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (word.isNotEmpty()) {
                        Text(
                            text = word,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/** The last word goes underneath: "6 years ago" reads "6 years" over "ago". */
private fun splitStat(text: String): Pair<String, String> {
    val cut = text.trim().lastIndexOf(' ')
    return if (cut <= 0) text.trim() to "" else text.trim().substring(0, cut) to text.trim().substring(cut + 1)
}
