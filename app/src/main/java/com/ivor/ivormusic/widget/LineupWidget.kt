package com.ivor.ivormusic.widget

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.ivor.ivormusic.R

/**
 * Lineup: what is playing, and what is after it, each row a jump.
 *
 * The one widget that shows something the app keeps behind a sheet, and the
 * only one whose rows are targets rather than decoration. Rows address the
 * timeline by index and never by media id, because a queue can hold the same
 * track twice, and a jump goes through the app's own skip-to-index command so
 * it runs the crossfade path exactly like a tap in the queue sheet. The
 * timeline is the play order (shuffle rearranges it rather than layering an
 * order over it), so the next index is the next song even while shuffling.
 *
 * **The queue is one segmented group, not a stack of pills.** The rows sit on
 * a `surface` panel - a lighter sheet on the tinted card in light, a recessed
 * well in dark - joined by 2dp seams with small inner corners and the group's
 * outer corners concentric with the card's. Separate pills each with its own
 * 12dp radius read as a column of buttons; one group reads as one list.
 *
 * How many rows fit is derived from the bucket, never assumed: everything
 * above the list is added up in [LIST_TOP] and the list gets what is left,
 * up to four - a Glance container truncates past ten children silently, and a
 * column that overruns its cell loses its bottom rows the same way.
 */
class LineupWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(250.dp, 110.dp),
            DpSize(250.dp, 150.dp),
            DpSize(250.dp, 190.dp),
            DpSize(250.dp, 230.dp),
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        PlayerWidgetHost.ensureSeeded(context)
        provideContent {
            val ui by PlayerWidgetHost.state.collectAsState()
            GlanceTheme(colors = KodaWidgetTheme.colors(context)) {
                LineupContent(ui)
            }
        }
    }
}

/** The card's inner gutter; the list's corners are drawn concentric with it. */
private val GUTTER = 8.dp
private val HEADER = 48.dp
private val HEADER_GAP = 6.dp
private val ROW_HEIGHT = 38.dp
private val SEAM = 2.dp

/** Everything above the first row, top gutter included. */
private val LIST_TOP = GUTTER + HEADER + HEADER_GAP

private fun rowsThatFit(height: Dp): Int {
    // n rows take n * ROW_HEIGHT + (n - 1) * SEAM, plus the bottom gutter.
    val room = height - LIST_TOP - GUTTER + SEAM
    return (room.value / (ROW_HEIGHT + SEAM).value).toInt().coerceIn(1, 4)
}

@Composable
private fun LineupContent(ui: PlayerWidgetUi) {
    val snapshot = ui.snapshot
    val rows = rowsThatFit(LocalSize.current.height)

    WidgetSurface {
        if (!snapshot.hasMedia) {
            WidgetEmptyState()
            return@WidgetSurface
        }
        Column(modifier = GlanceModifier.fillMaxSize().padding(GUTTER)) {
            LineupHeader(ui)
            Spacer(modifier = GlanceModifier.height(HEADER_GAP))
            LineupList(snapshot.upNext.take(rows))
        }
    }
}

@Composable
private fun LineupHeader(ui: PlayerWidgetUi) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(HEADER),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WidgetArtwork(ui.artwork, size = HEADER, shape = ArtworkShape.SQUIRCLE)
        Spacer(modifier = GlanceModifier.width(12.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            WidgetTitle(
                ui.snapshot.title ?: widgetString(R.string.app_name),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = GlanceModifier.height(1.dp))
            WidgetSubtitle(ui.snapshot.artist ?: "", fontSize = 12.sp)
        }
        Spacer(modifier = GlanceModifier.width(8.dp))
        HeroPlayButton(isPlaying = ui.snapshot.isPlaying, size = HEADER)
    }
}

@Composable
private fun LineupList(entries: List<UpNextEntry>) {
    if (entries.isEmpty()) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(ROW_HEIGHT)
                .shaped(R.drawable.widget_shape_segment_single, GlanceTheme.colors.surface)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = widgetString(R.string.widget_nothing_up_next),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                maxLines = 1,
            )
        }
        return
    }
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        entries.forEachIndexed { position, entry ->
            if (position > 0) Spacer(modifier = GlanceModifier.height(SEAM))
            LineupRow(entry, segmentShape(position, entries.size))
        }
    }
}

@DrawableRes
private fun segmentShape(position: Int, count: Int): Int = when {
    count == 1 -> R.drawable.widget_shape_segment_single
    position == 0 -> R.drawable.widget_shape_segment_top
    position == count - 1 -> R.drawable.widget_shape_segment_bottom
    else -> R.drawable.widget_shape_segment_middle
}

@Composable
private fun LineupRow(entry: UpNextEntry, @DrawableRes shape: Int) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(ROW_HEIGHT)
            .shaped(shape, GlanceTheme.colors.surface)
            // The shape draws the row; this only clips its ripple on API 31+.
            // One radius between the 4dp seams and the 20dp ends: a ripple a
            // little rounder than a middle row stays inside it, and one a
            // little squarer than an end row barely shows past it.
            .cornerRadius(12.dp)
            .clickable(
                actionRunCallback<PlayQueueIndexAction>(
                    actionParametersOf(PlayQueueIndexAction.QueueIndexKey to entry.index)
                )
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = entry.title,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
            if (entry.artist != null) {
                Text(
                    text = entry.artist,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 11.sp,
                    ),
                    maxLines = 1,
                )
            }
        }
    }
}

class LineupWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LineupWidget()
}
