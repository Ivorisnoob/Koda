package com.ivor.ivormusic.widget

import android.content.Context
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
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.ivor.ivormusic.MainActivity
import com.ivor.ivormusic.R
import androidx.media3.common.Player

/**
 * A 4x1 control strip: previous, play/pause, next, shuffle, repeat. Shuffle
 * and repeat toggle buttons act directly on the session.
 */
class QuickControlsWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(DpSize(260.dp, 56.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = PlayerWidgetReader.snapshot(context)
        provideContent {
            GlanceTheme {
                QuickControlsContent(snapshot)
            }
        }
    }
}

@Composable
private fun QuickControlsContent(snapshot: PlayerWidgetSnapshot) {
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(28.dp)
            .background(GlanceTheme.colors.widgetBackground)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!snapshot.hasMedia) {
            Image(
                provider = ImageProvider(R.mipmap.ic_launcher_round),
                contentDescription = null,
                modifier = GlanceModifier.size(24.dp)
                    .clickable(actionStartActivity<MainActivity>()),
            )
            Spacer(modifier = GlanceModifier.width(8.dp))
            Text(
                text = "Tap to listen",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
        } else {
            TransportButton(
                iconRes = R.drawable.ic_media_previous,
                description = "Previous",
                action = androidx.glance.appwidget.action.actionRunCallback<SkipPreviousAction>(),
                buttonSize = 40.dp,
                glyphSize = 20.dp,
            )
            Spacer(modifier = GlanceModifier.width(4.dp))
            TransportButton(
                iconRes = if (snapshot.isPlaying) R.drawable.ic_media_pause else R.drawable.ic_media_play,
                description = if (snapshot.isPlaying) "Pause" else "Play",
                action = androidx.glance.appwidget.action.actionRunCallback<TogglePlaybackAction>(),
                buttonSize = 44.dp,
                glyphSize = 22.dp,
                container = GlanceTheme.colors.primaryContainer,
                tint = GlanceTheme.colors.onPrimaryContainer,
            )
            Spacer(modifier = GlanceModifier.width(4.dp))
            TransportButton(
                iconRes = R.drawable.ic_media_next,
                description = "Next",
                action = androidx.glance.appwidget.action.actionRunCallback<SkipNextAction>(),
                buttonSize = 40.dp,
                glyphSize = 20.dp,
            )
            Spacer(modifier = GlanceModifier.width(8.dp))
            // shuffle toggle
            val shuffleBg = if (snapshot.shuffleEnabled) GlanceTheme.colors.secondaryContainer
                else GlanceTheme.colors.surfaceVariant
            val shuffleTint = if (snapshot.shuffleEnabled) GlanceTheme.colors.onSecondaryContainer
                else GlanceTheme.colors.onSurfaceVariant
            TransportButton(
                iconRes = R.drawable.ic_media_shuffle,
                description = "Shuffle",
                action = androidx.glance.appwidget.action.actionRunCallback<ToggleShuffleAction>(),
                buttonSize = 40.dp,
                glyphSize = 20.dp,
                container = shuffleBg,
                tint = shuffleTint,
            )
            Spacer(modifier = GlanceModifier.width(4.dp))
            // repeat toggle
            val repeatIcon = when (snapshot.repeatMode) {
                Player.REPEAT_MODE_ONE -> R.drawable.ic_media_repeat_one
                else -> R.drawable.ic_media_repeat
            }
            val repeatDesc = when (snapshot.repeatMode) {
                Player.REPEAT_MODE_OFF -> "Repeat"
                Player.REPEAT_MODE_ONE -> "Repeat one"
                else -> "Repeat all"
            }
            val repeatBg = if (snapshot.repeatMode != Player.REPEAT_MODE_OFF) GlanceTheme.colors.secondaryContainer
                else GlanceTheme.colors.surfaceVariant
            val repeatTint = if (snapshot.repeatMode != Player.REPEAT_MODE_OFF) GlanceTheme.colors.onSecondaryContainer
                else GlanceTheme.colors.onSurfaceVariant
            TransportButton(
                iconRes = repeatIcon,
                description = repeatDesc,
                action = androidx.glance.appwidget.action.actionRunCallback<CycleRepeatAction>(),
                buttonSize = 40.dp,
                glyphSize = 20.dp,
                container = repeatBg,
                tint = repeatTint,
            )
        }
    }
}

class QuickControlsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickControlsWidget()
}
