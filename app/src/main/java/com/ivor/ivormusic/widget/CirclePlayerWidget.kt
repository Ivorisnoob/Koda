package com.ivor.ivormusic.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.size
import com.ivor.ivormusic.R
import com.ivor.ivormusic.MainActivity

/**
 * A 1x1 circle widget: artwork fills the cell, play/pause overlays only when
 * paused so the resting state is a clean round record. Tap toggles playback;
 * the system-owned long-press opens the app.
 */
class CirclePlayerWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(DpSize(108.dp, 108.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = PlayerWidgetReader.snapshot(context)
        val artwork = snapshot.artworkUri?.let {
            PlayerWidgetReader.loadArtwork(context, it, 256)
        }
        provideContent {
            GlanceTheme {
                CirclePlayerContent(snapshot, artwork)
            }
        }
    }
}

@Composable
private fun CirclePlayerContent(snapshot: PlayerWidgetSnapshot, artwork: Bitmap?) {
    val accent = extractAccentColor(artwork)
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(108.dp)
            .background(GlanceTheme.colors.widgetBackground)
            .clickable(
                if (snapshot.hasMedia) {
                    androidx.glance.appwidget.action.actionRunCallback<TogglePlaybackAction>()
                } else {
                    actionStartActivity<MainActivity>()
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (artwork != null) {
            Image(
                provider = ImageProvider(roundedBitmap(artwork, 0.5f)),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = GlanceModifier
                    .fillMaxSize()
                    .cornerRadius(108.dp),
            )
        } else if (!snapshot.hasMedia) {
            Image(
                provider = ImageProvider(R.mipmap.ic_launcher_round),
                contentDescription = "Koda",
                modifier = GlanceModifier.size(48.dp),
            )
        }

        if (snapshot.hasMedia && !snapshot.isPlaying) {
            val scrimColor = accent?.let { withAlpha(it, 160) }
                ?: withAlpha(GlanceTheme.colors.surfaceVariant.read(), 200)
            Box(
                modifier = GlanceModifier
                    .size(40.dp)
                    .cornerRadius(40.dp)
                    .background(scrimColor),
                contentAlignment = Alignment.Center,
            ) {
                val iconTint = accent?.let { readableOn(scrimColor) }
                    ?: GlanceTheme.colors.onBackground.read()
                Image(
                    provider = ImageProvider(R.drawable.ic_media_play),
                    contentDescription = "Play",
                    modifier = GlanceModifier.size(20.dp),
                    colorFilter = androidx.glance.ColorFilter.tint(androidx.glance.unit.ColorProvider(iconTint)),
                )
            }
        }
    }
}

class CirclePlayerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CirclePlayerWidget()
}
