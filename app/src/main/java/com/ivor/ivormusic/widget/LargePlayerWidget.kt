package com.ivor.ivormusic.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
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
import androidx.glance.unit.ColorProvider
import com.ivor.ivormusic.MainActivity
import com.ivor.ivormusic.R

/**
 * A 4x2 card widget: artwork left, metadata + "Up next" right, a progress
 * row, and full transport including +/-10s seek.
 */
class LargePlayerWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(300.dp, 180.dp),
            DpSize(340.dp, 210.dp),
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = PlayerWidgetReader.snapshot(context)
        val artwork = snapshot.artworkUri?.let {
            PlayerWidgetReader.loadArtwork(context, it, 256)
        }
        provideContent {
            GlanceTheme {
                LargePlayerContent(snapshot, artwork)
            }
        }
    }
}

@Composable
private fun LargePlayerContent(snapshot: PlayerWidgetSnapshot, artwork: Bitmap?) {
    val accent = extractAccentColor(artwork)
    val size = LocalSize.current
    val tall = size.height >= 190.dp
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(24.dp)
            .background(GlanceTheme.colors.widgetBackground)
            .padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (artwork != null) {
            Image(
                provider = ImageProvider(artwork),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = GlanceModifier
                    .size(120.dp)
                    .cornerRadius(16.dp)
                    .clickable(actionStartActivity<MainActivity>()),
            )
        } else {
            Box(
                modifier = GlanceModifier
                    .size(120.dp)
                    .cornerRadius(16.dp)
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
        Spacer(modifier = GlanceModifier.width(14.dp))
        Column(
            modifier = GlanceModifier
                .defaultWeight()
                .fillMaxSize(),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = snapshot.title ?: "Koda",
                style = TextStyle(
                    color = GlanceTheme.colors.onBackground,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
            Spacer(modifier = GlanceModifier.height(2.dp))
            Text(
                text = snapshot.artist ?: "Tap to listen",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 12.sp,
                ),
                maxLines = 1,
            )
            if (tall && snapshot.nextTitle != null) {
                Spacer(modifier = GlanceModifier.height(4.dp))
                Text(
                    text = "Next: ${snapshot.nextTitle}",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 11.sp,
                    ),
                    maxLines = 1,
                )
            }
            Spacer(modifier = GlanceModifier.defaultWeight())
            if (snapshot.durationMs > 0) {
                val barColor = accent ?: GlanceTheme.colors.primary.read()
                val trackColor = GlanceTheme.colors.onSurfaceVariant.read().copy(alpha = 0.2f)
                androidx.glance.appwidget.LinearProgressIndicator(
                    progress = snapshot.progress,
                    modifier = GlanceModifier.fillMaxWidth(),
                    color = ColorProvider(barColor),
                    backgroundColor = ColorProvider(trackColor),
                )
                Row(modifier = GlanceModifier.fillMaxWidth().padding(top = 2.dp)) {
                    Text(
                        text = formatTime(snapshot.positionMs),
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 10.sp,
                        ),
                    )
                    Spacer(modifier = GlanceModifier.defaultWeight())
                    Text(
                        text = formatTime(snapshot.durationMs),
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 10.sp,
                        ),
                    )
                }
            }
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TransportButton(
                    iconRes = R.drawable.ic_media_replay_10,
                    description = "Rewind 10 seconds",
                    action = androidx.glance.appwidget.action.actionRunCallback<SeekBackAction>(),
                    buttonSize = 34.dp,
                    glyphSize = 18.dp,
                )
                Spacer(modifier = GlanceModifier.width(2.dp))
                TransportButton(
                    iconRes = R.drawable.ic_media_previous,
                    description = "Previous",
                    action = androidx.glance.appwidget.action.actionRunCallback<SkipPreviousAction>(),
                    buttonSize = 34.dp,
                    glyphSize = 18.dp,
                )
                Spacer(modifier = GlanceModifier.width(2.dp))
                val pillBg = accent?.let { withAlpha(it, 220) } ?: GlanceTheme.colors.primaryContainer.read()
                val pillTint = accent?.let { readableOn(withAlpha(it, 220)) } ?: GlanceTheme.colors.onPrimaryContainer.read()
                TransportButton(
                    iconRes = if (snapshot.isPlaying) R.drawable.ic_media_pause else R.drawable.ic_media_play,
                    description = if (snapshot.isPlaying) "Pause" else "Play",
                    action = androidx.glance.appwidget.action.actionRunCallback<TogglePlaybackAction>(),
                    buttonSize = 42.dp,
                    glyphSize = 22.dp,
                    container = ColorProvider(pillBg),
                    tint = ColorProvider(pillTint),
                )
                Spacer(modifier = GlanceModifier.width(2.dp))
                TransportButton(
                    iconRes = R.drawable.ic_media_next,
                    description = "Next",
                    action = androidx.glance.appwidget.action.actionRunCallback<SkipNextAction>(),
                    buttonSize = 34.dp,
                    glyphSize = 18.dp,
                )
                Spacer(modifier = GlanceModifier.width(2.dp))
                TransportButton(
                    iconRes = R.drawable.ic_media_forward_10,
                    description = "Forward 10 seconds",
                    action = androidx.glance.appwidget.action.actionRunCallback<SeekForwardAction>(),
                    buttonSize = 34.dp,
                    glyphSize = 18.dp,
                )
            }
        }
    }
}

class LargePlayerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LargePlayerWidget()
}
