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
 * The flagship home screen widget: full-bleed album artwork when the cell is
 * wide enough, compact row when it is not. The hero moment is a pill-shaped
 * play/pause button tinted by the cover's dominant color, with a thin progress
 * strip along the bottom that advances while the song plays.
 */
class NowPlayingWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(250.dp, 64.dp),
            DpSize(300.dp, 110.dp),
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = PlayerWidgetReader.snapshot(context)
        val artwork = snapshot.artworkUri?.let {
            PlayerWidgetReader.loadArtwork(context, it, 256)
        }
        provideContent {
            GlanceTheme {
                NowPlayingContent(snapshot, artwork)
            }
        }
    }
}

@Composable
private fun NowPlayingContent(snapshot: PlayerWidgetSnapshot, artwork: Bitmap?) {
    val size = LocalSize.current
    val wide = size.width >= 280.dp
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(28.dp)
            .background(GlanceTheme.colors.widgetBackground)
            .clickable(actionStartActivity<MainActivity>())
    ) {
        if (!snapshot.hasMedia) {
            NowPlayingEmpty()
        } else if (wide) {
            NowPlayingWide(snapshot, artwork)
        } else {
            NowPlayingCompact(snapshot, artwork)
        }
    }
}

// ---- empty ----

@Composable
private fun NowPlayingEmpty() {
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = GlanceModifier
                .size(42.dp)
                .cornerRadius(12.dp)
                .background(GlanceTheme.colors.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(R.mipmap.ic_launcher_round),
                contentDescription = null,
                modifier = GlanceModifier.size(28.dp),
            )
        }
        Spacer(modifier = GlanceModifier.width(12.dp))
        Column {
            Text(
                text = "Koda",
                style = TextStyle(
                    color = GlanceTheme.colors.onBackground,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
            Text(
                text = "Tap to listen",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 12.sp,
                ),
                maxLines = 1,
            )
        }
    }
}

// ---- compact (<280dp width) ----

@Composable
private fun NowPlayingCompact(snapshot: PlayerWidgetSnapshot, artwork: Bitmap?) {
    val accent = extractAccentColor(artwork)
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NowPlayingArt(artwork, 44.dp, 12.dp)
        Spacer(modifier = GlanceModifier.width(10.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = snapshot.title ?: "",
                style = TextStyle(
                    color = GlanceTheme.colors.onBackground,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
            Spacer(modifier = GlanceModifier.height(1.dp))
            Text(
                text = snapshot.artist ?: "",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 11.sp,
                ),
                maxLines = 1,
            )
        }
        Spacer(modifier = GlanceModifier.width(6.dp))
        NowPlayingPlayPill(snapshot.isPlaying, accent)
    }
}

// ---- wide (full-bleed artwork) ----

@Composable
private fun NowPlayingWide(snapshot: PlayerWidgetSnapshot, artwork: Bitmap?) {
    val accent = extractAccentColor(artwork)
    val fallbackScrim = GlanceTheme.colors.widgetBackground.read()
    val scrimColor = accent?.let { withAlpha(it, 180) } ?: withAlpha(fallbackScrim, 200)
    val textColor = accent?.let { readableOn(scrimColor) } ?: GlanceTheme.colors.onBackground.read()

    if (artwork != null) {
        Image(
            provider = ImageProvider(roundedBitmap(artwork)),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(28.dp),
        )
    } else {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surfaceVariant),
        ) {}
    }

    // scrim
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(28.dp)
            .background(scrimColor),
    ) {}

    // content over scrim
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = snapshot.title ?: "",
            style = TextStyle(
                color = ColorProvider(textColor),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
        )
        Text(
            text = snapshot.artist ?: "",
            style = TextStyle(
                color = ColorProvider(textColor.copy(alpha = 0.7f)),
                fontSize = 12.sp,
            ),
            maxLines = 1,
        )
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NowPlayingPlayPill(snapshot.isPlaying, accent)
            Spacer(modifier = GlanceModifier.width(4.dp))
            Row(modifier = GlanceModifier.defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                TransportButton(
                    iconRes = R.drawable.ic_media_previous,
                    description = "Previous",
                    action = androidx.glance.appwidget.action.actionRunCallback<SkipPreviousAction>(),
                    buttonSize = 38.dp,
                    glyphSize = 20.dp,
                    tint = ColorProvider(textColor),
                )
                Spacer(modifier = GlanceModifier.width(4.dp))
                TransportButton(
                    iconRes = R.drawable.ic_media_next,
                    description = "Next",
                    action = androidx.glance.appwidget.action.actionRunCallback<SkipNextAction>(),
                    buttonSize = 38.dp,
                    glyphSize = 20.dp,
                    tint = ColorProvider(textColor),
                )
            }
        }
        // progress strip
        androidx.glance.appwidget.LinearProgressIndicator(
            progress = snapshot.progress,
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 2.dp),
            color = ColorProvider(textColor),
            backgroundColor = ColorProvider(textColor.copy(alpha = 0.25f)),
        )
    }
}

// ---- shared pieces ----

@Composable
private fun NowPlayingArt(artwork: Bitmap?, size: androidx.compose.ui.unit.Dp, radius: androidx.compose.ui.unit.Dp) {
    if (artwork != null) {
        Image(
            provider = ImageProvider(artwork),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = GlanceModifier
                .size(size)
                .cornerRadius(radius),
        )
    } else {
        Box(
            modifier = GlanceModifier
                .size(size)
                .cornerRadius(radius)
                .background(GlanceTheme.colors.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_playback_notification),
                contentDescription = null,
                modifier = GlanceModifier.size(20.dp),
                colorFilter = androidx.glance.ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant),
            )
        }
    }
}

@Composable
private fun NowPlayingPlayPill(isPlaying: Boolean, accent: Color?) {
    val pillBg = accent?.let { withAlpha(it, 220) } ?: GlanceTheme.colors.primaryContainer.read()
    val pillTint = accent?.let { readableOn(withAlpha(it, 220)) } ?: GlanceTheme.colors.onPrimaryContainer.read()
    TransportButton(
        iconRes = if (isPlaying) R.drawable.ic_media_pause else R.drawable.ic_media_play,
        description = if (isPlaying) "Pause" else "Play",
        action = androidx.glance.appwidget.action.actionRunCallback<TogglePlaybackAction>(),
        buttonSize = 46.dp,
        glyphSize = 24.dp,
        container = ColorProvider(pillBg),
        tint = ColorProvider(pillTint),
    )
}

class NowPlayingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NowPlayingWidget()
}
