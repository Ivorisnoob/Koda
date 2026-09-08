package com.ivor.ivormusic.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.width
import com.ivor.ivormusic.R

/**
 * Bloom: the cover cut into a twelve-lobed cookie, sitting straight on the
 * wallpaper, with play and next floating along its lower edge.
 *
 * This is the widget that carries the family's shape identity, and it is the
 * one that has no card. A square of album art is what every other music app
 * puts on a home screen; the scallop is what makes it read as Koda from across
 * the room, and a rounded card behind it puts the square straight back - the
 * lobes are only legible against the wallpaper. The cover is not a touch
 * target, so the shape never has to agree with a ripple.
 *
 * **The empty state keeps its card.** Nothing else here would: two lines of
 * theme-coloured text on an unknown wallpaper is the one thing on this widget
 * that cannot supply its own contrast, and a widget that has never played
 * anything is exactly when someone needs to read it.
 *
 * No title, no artist, no scrim band. An earlier version captioned the art
 * along the bottom, which cut the cover in half to say what the cover already
 * says. The controls need no scrim either: both are opaque fills that carry
 * their own contrast over any cover and over any wallpaper.
 */
class BloomWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(140.dp, 140.dp),
            DpSize(200.dp, 200.dp),
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        PlayerWidgetHost.ensureSeeded(context)
        provideContent {
            val ui by PlayerWidgetHost.state.collectAsState()
            GlanceTheme(colors = KodaWidgetTheme.colors(context)) {
                BloomContent(ui)
            }
        }
    }
}

@Composable
private fun BloomContent(ui: PlayerWidgetUi) {
    if (!ui.snapshot.hasMedia) {
        WidgetSurface { WidgetEmptyState() }
        return
    }

    val size = LocalSize.current
    val cell = minOf(size.width, size.height)
    // LocalSize reports the matched bucket rather than the real cell, so the
    // lobes are given a couple of dp to breathe instead of being drawn to an
    // edge that may not be where it says it is.
    val cover = cell - 8.dp
    // Two buckets, two control sizes. At 140dp the pair has to stay inside the
    // 28dp corner clip, and 48 + 6 + 44 does.
    val large = cell >= 200.dp
    val hero = if (large) 56.dp else 48.dp
    val side = if (large) 48.dp else 44.dp

    WidgetSurface(background = null) {
        Box(
            modifier = GlanceModifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            WidgetArtwork(ui.artwork, size = cover, shape = ArtworkShape.COOKIE)
        }
        Box(
            modifier = GlanceModifier.fillMaxSize().padding(bottom = 6.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            // Centred rather than tucked into the corner, which is where a
            // single hero sat. Two controls in a corner crowd one side of a
            // symmetrical shape; on its axis they read as the cover's own
            // transport.
            Row(verticalAlignment = Alignment.CenterVertically) {
                HeroPlayButton(isPlaying = ui.snapshot.isPlaying, size = hero)
                Spacer(modifier = GlanceModifier.width(6.dp))
                WidgetSquareButton(
                    iconRes = R.drawable.ic_media_next,
                    description = widgetString(R.string.widget_action_next),
                    action = actionRunCallback<SkipNextAction>(),
                    size = side,
                )
            }
        }
    }
}

class BloomWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BloomWidget()
}
