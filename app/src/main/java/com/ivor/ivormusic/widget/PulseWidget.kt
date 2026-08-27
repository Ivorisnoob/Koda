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
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
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
import com.ivor.ivormusic.R

/**
 * Pulse: the everyday widget. A scalloped cover, the track, and one hero at
 * 4x1; the same card grows a progress strip and a full transport group when
 * given a second row.
 *
 * The two buckets are different layouts rather than one squeezed - a 4x1 has
 * room for a cover, two lines and a single control at a real touch target, and
 * five controls in that space would leave every one of them under 48dp.
 */
class PulseWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(250.dp, 64.dp),
            DpSize(300.dp, 140.dp),
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        PlayerWidgetHost.ensureSeeded(context)
        provideContent {
            // Collected, not captured: provideGlance runs once per session
            // while updateAll only recomposes, so a value read out here would
            // never change again for the life of that session.
            val ui by PlayerWidgetHost.state.collectAsState()
            GlanceTheme(colors = KodaWidgetTheme.colors(context)) {
                PulseContent(ui)
            }
        }
    }
}

@Composable
private fun PulseContent(ui: PlayerWidgetUi) {
    val snapshot = ui.snapshot
    val tall = LocalSize.current.height >= 140.dp
    WidgetSurface {
        when {
            !snapshot.hasMedia -> WidgetEmptyState()
            tall -> PulseCard(ui)
            else -> PulseRow(ui)
        }
    }
}

/** 4x1: cover, two lines, one hero. */
@Composable
private fun PulseRow(ui: PlayerWidgetUi) {
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WidgetArtwork(ui.artwork, size = 48.dp, shape = ArtworkShape.CLOVER)
        Spacer(modifier = GlanceModifier.width(12.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            WidgetTitle(
                ui.snapshot.title ?: widgetString(R.string.app_name),
                fontSize = 14.sp,
            )
            Spacer(modifier = GlanceModifier.height(2.dp))
            WidgetSubtitle(ui.snapshot.artist ?: "", fontSize = 11.sp)
        }
        Spacer(modifier = GlanceModifier.width(8.dp))
        HeroPlayButton(isPlaying = ui.snapshot.isPlaying, size = 48.dp)
    }
}

/** 4x2: cover, metadata, progress and a transport group. Seven children max. */
@Composable
private fun PulseCard(ui: PlayerWidgetUi) {
    val snapshot = ui.snapshot
    Row(
        modifier = GlanceModifier.fillMaxSize().padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WidgetArtwork(ui.artwork, size = 88.dp, shape = ArtworkShape.COOKIE)
        Spacer(modifier = GlanceModifier.width(14.dp))
        Column(
            modifier = GlanceModifier.defaultWeight().fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WidgetTitle(snapshot.title ?: widgetString(R.string.app_name))
            Spacer(modifier = GlanceModifier.height(2.dp))
            WidgetSubtitle(snapshot.artist ?: "")
            Spacer(modifier = GlanceModifier.height(8.dp))
            if (snapshot.durationMs > 0) {
                WidgetProgress(snapshot)
                Spacer(modifier = GlanceModifier.height(8.dp))
            }
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WidgetTransportRow(snapshot, heroSize = 48.dp, sideSize = 44.dp, gap = 6.dp)
                Spacer(modifier = GlanceModifier.defaultWeight())
                WidgetToggleButton(
                    iconRes = R.drawable.ic_media_shuffle,
                    description = widgetString(R.string.widget_action_shuffle),
                    action = androidx.glance.appwidget.action
                        .actionRunCallback<ToggleShuffleAction>(),
                    selected = snapshot.shuffleEnabled,
                    size = 42.dp,
                )
            }
        }
    }
}

class PulseWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PulseWidget()
}
