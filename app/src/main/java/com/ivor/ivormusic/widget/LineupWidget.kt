package com.ivor.ivormusic.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
 * The one widget here that shows something the app itself keeps behind a sheet,
 * and the only one whose rows are targets rather than decoration. Rows address
 * the timeline by index and never by media id, because a queue can legitimately
 * hold the same track twice - the same rule the in-app video queue follows -
 * and a jump goes through the app's own skip-to-index command so it runs the
 * crossfade path exactly like a tap in the queue sheet.
 *
 * How many rows fit is derived from the cell rather than baked into a bucket,
 * and capped at four: the whole column including the header has to stay inside
 * a Glance container's ten-child limit, past which it truncates silently.
 */
class LineupWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(250.dp, 110.dp),
            DpSize(250.dp, 180.dp),
            DpSize(250.dp, 250.dp),
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

private val ROW_HEIGHT = 46.dp
private val HEADER_HEIGHT = 80.dp

@Composable
private fun LineupContent(ui: PlayerWidgetUi) {
    val snapshot = ui.snapshot
    val available = LocalSize.current.height - HEADER_HEIGHT
    val rows = (available.value / ROW_HEIGHT.value).toInt().coerceIn(0, 4)

    WidgetSurface {
        if (!snapshot.hasMedia) {
            WidgetEmptyState()
            return@WidgetSurface
        }
        Column(modifier = GlanceModifier.fillMaxSize().padding(12.dp)) {
            LineupHeader(ui)
            if (rows == 0) return@Column
            Spacer(modifier = GlanceModifier.height(8.dp))
            LineupRows(snapshot.upNext.take(rows))
        }
    }
}

@Composable
private fun LineupHeader(ui: PlayerWidgetUi) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WidgetArtwork(ui.artwork, size = 50.dp, shape = ArtworkShape.CLOVER)
        Spacer(modifier = GlanceModifier.width(10.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            WidgetTitle(
                ui.snapshot.title ?: widgetString(R.string.app_name),
                fontSize = 15.sp,
            )
            WidgetSubtitle(ui.snapshot.artist ?: "", fontSize = 11.sp)
        }
        Spacer(modifier = GlanceModifier.width(8.dp))
        HeroPlayButton(isPlaying = ui.snapshot.isPlaying, size = 48.dp)
    }
}

@Composable
private fun LineupRows(entries: List<UpNextEntry>) {
    if (entries.isEmpty()) {
        Text(
            text = widgetString(R.string.widget_nothing_up_next),
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
            maxLines = 1,
        )
        return
    }
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        for (entry in entries) {
            LineupRow(entry)
            Spacer(modifier = GlanceModifier.height(3.dp))
        }
    }
}

@Composable
private fun LineupRow(entry: UpNextEntry) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(ROW_HEIGHT - 3.dp)
            .shaped(WidgetShape.Medium, GlanceTheme.colors.surfaceVariant)
            // Both: the shape draws the row, the corner radius clips its ripple.
            .cornerRadius(12.dp)
            .clickable(
                actionRunCallback<PlayQueueIndexAction>(
                    actionParametersOf(PlayQueueIndexAction.QueueIndexKey to entry.index)
                )
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = entry.title,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
            if (entry.artist != null) {
                Text(
                    text = entry.artist,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 10.sp,
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
