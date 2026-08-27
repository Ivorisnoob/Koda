package com.ivor.ivormusic.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.ivor.ivormusic.MainActivity
import com.ivor.ivormusic.R

/**
 * A 2x2 square widget: artwork fills the top, title/artist and play/next
 * controls sit below.
 */
class MiniPlayerWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(DpSize(180.dp, 180.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = PlayerWidgetReader.snapshot(context)
        val artwork = snapshot.artworkUri?.let {
            PlayerWidgetReader.loadArtwork(context, it, 192)
        }
        provideContent {
            GlanceTheme {
                MiniPlayerContent(snapshot, artwork)
            }
        }
    }
}

@Composable
private fun MiniPlayerContent(snapshot: PlayerWidgetSnapshot, artwork: Bitmap?) {
    val accent = extractAccentColor(artwork)
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(24.dp)
            .background(GlanceTheme.colors.widgetBackground)
    ) {
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .defaultWeight()
                .cornerRadius(24.dp)
                .clickable(actionStartActivity<MainActivity>()),
            contentAlignment = Alignment.Center,
        ) {
            if (artwork != null) {
                Image(
                    provider = ImageProvider(roundedBitmap(artwork, 0.18f)),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .cornerRadius(24.dp),
                )
            } else if (!snapshot.hasMedia) {
                Image(
                    provider = ImageProvider(R.mipmap.ic_launcher_round),
                    contentDescription = null,
                    modifier = GlanceModifier.size(40.dp),
                )
            }
        }
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = snapshot.title ?: "Koda",
                    style = TextStyle(
                        color = GlanceTheme.colors.onBackground,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                )
                Text(
                    text = snapshot.artist ?: "Tap to listen",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 11.sp,
                    ),
                    maxLines = 1,
                )
            }
            Spacer(modifier = GlanceModifier.width(4.dp))
            val playBg = accent?.let { withAlpha(it, 220) }
            val playTint = accent?.let { readableOn(withAlpha(it, 220)) }
            TransportButton(
                iconRes = if (snapshot.isPlaying) R.drawable.ic_media_pause else R.drawable.ic_media_play,
                description = if (snapshot.isPlaying) "Pause" else "Play",
                action = androidx.glance.appwidget.action.actionRunCallback<TogglePlaybackAction>(),
                buttonSize = 38.dp,
                glyphSize = 20.dp,
                container = playBg?.let { androidx.glance.unit.ColorProvider(it) },
                tint = playTint?.let { androidx.glance.unit.ColorProvider(it) }
                    ?: GlanceTheme.colors.onPrimaryContainer,
            )
            Spacer(modifier = GlanceModifier.width(2.dp))
            TransportButton(
                iconRes = R.drawable.ic_media_next,
                description = "Next",
                action = androidx.glance.appwidget.action.actionRunCallback<SkipNextAction>(),
                buttonSize = 38.dp,
                glyphSize = 20.dp,
            )
        }
    }
}

class MiniPlayerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MiniPlayerWidget()
}
