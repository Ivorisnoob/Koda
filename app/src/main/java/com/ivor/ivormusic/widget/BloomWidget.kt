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
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding

/**
 * Bloom: the cover cut into a twelve-lobed cookie, filling the cell, with the
 * hero tucked into the corner.
 *
 * This is the widget that carries the family's shape identity. A square of
 * album art is what every other music app puts on a home screen; the scallop is
 * what makes it read as Koda from across the room, and it costs nothing because
 * the cover is not a touch target - the shape never has to agree with a ripple.
 *
 * No title, no artist, no scrim band. An earlier version captioned the art
 * along the bottom, which cut the cover in half to say what the cover already
 * says. The hero needs no scrim of its own: a filled primary circle carries its
 * own contrast against anything underneath it.
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
    val size = LocalSize.current
    // The lobes need to clear the cell edge, and the hero overhangs the
    // bottom-right corner, so the cover sits a little inside its own box.
    val cover = minOf(size.width, size.height) - 12.dp

    WidgetSurface(background = GlanceTheme.colors.widgetBackground) {
        if (!ui.snapshot.hasMedia) {
            WidgetEmptyState()
            return@WidgetSurface
        }
        Box(
            modifier = GlanceModifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            WidgetArtwork(ui.artwork, size = cover, shape = ArtworkShape.COOKIE)
        }
        Box(
            modifier = GlanceModifier.fillMaxSize().padding(10.dp),
            contentAlignment = Alignment.BottomEnd,
        ) {
            HeroPlayButton(isPlaying = ui.snapshot.isPlaying, size = 52.dp)
        }
    }
}

class BloomWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BloomWidget()
}
