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
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.width

/**
 * Console: transport and nothing else. No cover, no title - the widget for
 * someone who wants the controls under their thumb and reads what is playing
 * off the notification like everyone does anyway.
 *
 * It carries shuffle and repeat because it is the only widget with no artwork
 * competing for the width, and those are the two controls people reach for
 * without wanting to look at anything. Below 290dp they drop rather than
 * squeezing the transport under a 48dp target.
 */
class ConsoleWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(200.dp, 56.dp),
            DpSize(300.dp, 56.dp),
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        PlayerWidgetHost.ensureSeeded(context)
        provideContent {
            val ui by PlayerWidgetHost.state.collectAsState()
            GlanceTheme(colors = KodaWidgetTheme.colors(context)) {
                ConsoleContent(ui)
            }
        }
    }
}

@Composable
private fun ConsoleContent(ui: PlayerWidgetUi) {
    val roomForToggles = LocalSize.current.width >= 290.dp
    WidgetSurface(openAppOnTap = !ui.snapshot.hasMedia) {
        if (!ui.snapshot.hasMedia) {
            WidgetEmptyState()
            return@WidgetSurface
        }
        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            WidgetTransportRow(ui.snapshot, heroSize = 48.dp, sideSize = 44.dp, gap = 6.dp)
            if (roomForToggles) {
                Spacer(modifier = GlanceModifier.width(12.dp))
                WidgetModeToggles(ui.snapshot, size = 42.dp, gap = 6.dp)
            }
        }
    }
}

class ConsoleWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ConsoleWidget()
}
