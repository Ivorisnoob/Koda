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
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.unit.ColorProvider
import com.ivor.ivormusic.MainActivity
import com.ivor.ivormusic.R

/**
 * The "Canvas" style widget: full-bleed artwork, minimal overlay, only a small
 * play/pause chip at the bottom corner. No text.
 */
class CanvasPlayerWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(DpSize(180.dp, 180.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = PlayerWidgetReader.snapshot(context)
        val artwork = snapshot.artworkUri?.let {
            PlayerWidgetReader.loadArtwork(context, it, 256)
        }
        provideContent {
            GlanceTheme {
                CanvasPlayerContent(snapshot, artwork)
            }
        }
    }
}

@Composable
private fun CanvasPlayerContent(snapshot: PlayerWidgetSnapshot, artwork: Bitmap?) {
    val accent = extractAccentColor(artwork)
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(28.dp)
            .background(GlanceTheme.colors.widgetBackground),
        contentAlignment = Alignment.BottomEnd,
    ) {
        if (artwork != null) {
            Image(
                provider = ImageProvider(roundedBitmap(artwork, 0.14f)),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = GlanceModifier
                    .fillMaxSize()
                    .cornerRadius(28.dp)
                    .clickable(actionStartActivity<MainActivity>()),
            )
        } else {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .cornerRadius(28.dp)
                    .background(GlanceTheme.colors.surfaceVariant)
                    .clickable(actionStartActivity<MainActivity>()),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    provider = ImageProvider(R.mipmap.ic_launcher_round),
                    contentDescription = null,
                    modifier = GlanceModifier.size(40.dp),
                )
            }
        }
        if (snapshot.hasMedia) {
            val chipColor = accent?.let { withAlpha(it, 180) }
                ?: withAlpha(GlanceTheme.colors.surfaceVariant.read(), 200)
            val chipTint = accent?.let { readableOn(chipColor) }
                ?: GlanceTheme.colors.onBackground.read()
            Box(
                modifier = GlanceModifier.padding(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                TransportButton(
                    iconRes = if (snapshot.isPlaying) R.drawable.ic_media_pause else R.drawable.ic_media_play,
                    description = if (snapshot.isPlaying) "Pause" else "Play",
                    action = androidx.glance.appwidget.action.actionRunCallback<TogglePlaybackAction>(),
                    buttonSize = 40.dp,
                    glyphSize = 20.dp,
                    container = ColorProvider(chipColor),
                    tint = ColorProvider(chipTint),
                )
            }
        }
    }
}

class CanvasPlayerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CanvasPlayerWidget()
}
