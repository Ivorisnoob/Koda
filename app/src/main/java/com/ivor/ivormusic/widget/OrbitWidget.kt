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
import androidx.glance.layout.size

/**
 * Orbit: one cell. The cover as a disc on the wallpaper, and the hero tucked
 * against its lower edge like a badge on an avatar.
 *
 * It is the smallest thing that is still a player: what is on, and the one
 * control everyone wants. The earlier version centred the play button on the
 * cover behind a black scrim, which hid the middle of the art - the part a
 * cover is composed around - and read as a sticker stuck on it. Off-centre,
 * the cover stays whole and the button reads as a button: primary, opaque,
 * carrying its own contrast over any art and any wallpaper, with no literal
 * colour anywhere.
 *
 * No card, like Bloom and Vinyl: a disc and a circle stand on the wallpaper
 * by themselves. A tap on the cover opens Koda; only the badge plays. The
 * badge and the cover are laid out in one square sized from the bucket, so a
 * cell that is not square cannot pull them apart.
 */
class OrbitWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(64.dp, 64.dp),
            DpSize(90.dp, 90.dp),
        )
    )

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
    // A few dp of air, for the same reason as Bloom: the bucket is a lower
    // bound on the cell, not its edge.
    val disc = cell - 4.dp
    // About 45% of the disc. Its centre sits inside the cover and its outer
    // edge a little past it, so it overlaps rather than floats.
    val badge = if (cell >= 90.dp) 40.dp else 32.dp

    // With nothing ever played, the placeholder disc (the brand mark on a
    // tonal circle) is the empty state: a tap opens the app, and there is no
    // badge to promise playback that does not exist.
    WidgetSurface(background = null) {
        Box(modifier = GlanceModifier.size(disc)) {
            WidgetArtwork(
                if (ui.snapshot.hasMedia) ui.artwork else null,
                size = disc,
                shape = ArtworkShape.CIRCLE,
            )
            if (ui.snapshot.hasMedia) {
                Box(
                    modifier = GlanceModifier.fillMaxSize(),
                    contentAlignment = Alignment.BottomEnd,
                ) {
                    HeroPlayButton(isPlaying = ui.snapshot.isPlaying, size = badge)
                }
            }
        }
    }
}

class OrbitWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = OrbitWidget()
}
