package com.ivor.ivormusic.widget

import android.content.Context
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
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import com.ivor.ivormusic.R

/**
 * Deck: the full player, vertical, because that is the shape a phone home
 * screen actually has room for. Cover, track, progress with times, transport,
 * shuffle and repeat.
 *
 * **Two limits shape this file and both bite silently.** A Glance container
 * truncates past ten children with only a log line, and a column that adds up
 * to more than the cell loses its bottom rows the same way. So the controls are
 * grouped into composables rather than flattened into the parent - the column
 * below is seven children, not thirteen - and [belowCoverHeight] is the honest
 * sum of everything under the artwork, with the cover taking the remainder.
 * That is also what lets one layout serve both size buckets.
 */
class DeckWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(150.dp, 260.dp),
            DpSize(200.dp, 340.dp),
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        PlayerWidgetHost.ensureSeeded(context)
        provideContent {
            val ui by PlayerWidgetHost.state.collectAsState()
            GlanceTheme(colors = KodaWidgetTheme.colors(context)) {
                DeckContent(ui)
            }
        }
    }
}

private val CARD_PADDING = 14.dp

/** Everything under the cover, added up. Kept beside the layout it measures. */
private fun belowCoverHeight(roomy: Boolean, hasDuration: Boolean): Dp {
    var total = 12.dp // gap under the cover
    total += 21.dp // title
    total += 2.dp
    total += 16.dp // artist
    if (hasDuration) {
        total += 10.dp + 4.dp // gap + progress bar
        if (roomy) total += 3.dp + 14.dp // gap + elapsed/total row
    }
    total += 10.dp // gap above the transport group
    total += if (roomy) 56.dp else 48.dp // hero, the tallest thing in that row
    if (roomy) total += 6.dp + 44.dp // gap + toggles
    return total
}

@Composable
private fun DeckContent(ui: PlayerWidgetUi) {
    val snapshot = ui.snapshot
    val size = LocalSize.current
    val roomy = size.height >= 330.dp

    WidgetSurface {
        if (!snapshot.hasMedia) {
            WidgetEmptyState()
            return@WidgetSurface
        }
        val hasDuration = snapshot.durationMs > 0
        val coverSize = minOf(
            size.width - CARD_PADDING * 2,
            size.height - CARD_PADDING * 2 - belowCoverHeight(roomy, hasDuration),
        ).coerceIn(64.dp, 200.dp)

        Column(
            modifier = GlanceModifier.fillMaxSize().padding(CARD_PADDING),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WidgetArtwork(ui.artwork, size = coverSize, shape = ArtworkShape.SQUIRCLE)
            Spacer(modifier = GlanceModifier.height(12.dp))
            DeckMeta(snapshot, roomy, hasDuration)
            Spacer(modifier = GlanceModifier.height(10.dp))
            WidgetTransportRow(
                snapshot,
                heroSize = if (roomy) 56.dp else 48.dp,
                sideSize = if (roomy) 48.dp else 44.dp,
                gap = 6.dp,
            )
            if (roomy) {
                Spacer(modifier = GlanceModifier.height(6.dp))
                WidgetModeToggles(snapshot, size = 44.dp, gap = 6.dp)
            }
        }
    }
}

/** Title, artist and progress as one child of the parent column. */
@Composable
private fun DeckMeta(snapshot: PlayerWidgetSnapshot, roomy: Boolean, hasDuration: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        WidgetTitle(
            snapshot.title ?: widgetString(R.string.app_name),
            fontSize = if (roomy) 16.sp else 14.sp,
        )
        Spacer(modifier = GlanceModifier.height(2.dp))
        WidgetSubtitle(snapshot.artist ?: "", fontSize = if (roomy) 12.sp else 11.sp)
        if (hasDuration) {
            Spacer(modifier = GlanceModifier.height(10.dp))
            WidgetProgress(snapshot)
            if (roomy) {
                Spacer(modifier = GlanceModifier.height(3.dp))
                WidgetTimes(snapshot)
            }
        }
    }
}

class DeckWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DeckWidget()
}
