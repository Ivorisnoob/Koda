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

/**
 * Orbit: one cell, the cover as a record, the hero centred on it.
 *
 * The play control is a real button rather than the whole tile being tappable.
 * The previous single-cell widget made the entire disc the target and drew a
 * glyph only when paused, which looked like a decorative sticker that happened
 * to respond to taps - and gave a full-square ripple across the cover on every
 * press. A proper circular button says what it is and brings its own ripple.
 */
class OrbitWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(DpSize(90.dp, 90.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        PlayerWidgetHost.ensureSeeded(context)
        provideContent {
            val ui by PlayerWidgetHost.state.collectAsState()
            GlanceTheme(colors = KodaWidgetTheme.colors(context)) {
                OrbitContent(ui)
            }
        }
    }
}

@Composable
private fun OrbitContent(ui: PlayerWidgetUi) {
    val cell = minOf(LocalSize.current.width, LocalSize.current.height)
    Box(
        modifier = GlanceModifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        WidgetArtwork(ui.artwork, size = cell, shape = ArtworkShape.CIRCLE)
        if (ui.snapshot.hasMedia) {
            HeroPlayButton(
                isPlaying = ui.snapshot.isPlaying,
                size = (cell * 0.46f).coerceAtLeast(44.dp),
                background = OverArtwork.scrim,
                content = OverArtwork.primaryText,
            )
        }
    }
}

class OrbitWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = OrbitWidget()
}
