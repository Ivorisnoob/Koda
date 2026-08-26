package com.ivor.ivormusic.widget

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
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
import com.ivor.ivormusic.service.MusicService
import com.ivor.ivormusic.util.KLog
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The home screen widget: current artwork, title and artist, plus transport
 * controls - the long-standing request, reached through the same session the
 * notification, Auto and the QS tile already share.

 * It is deliberately a *client*, not a second player. Every render binds a
 * throwaway [MediaController] to [MusicService], snapshots what is playing and
 * releases it again, and every button reconnects the same way. That means the
 * widget can never disagree with playback anywhere else - it is the same
 * source of truth - and it works whether or not the app process is warm.

 * Updates are pushed from the service's active-player listener (track change,
 * play/pause, end of queue) rather than polled, so the widget redraws exactly
 * when playback changes and sits still otherwise.
 */
class NowPlayingWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(250.dp, 64.dp),
            DpSize(300.dp, 84.dp),
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = readSnapshot(context)
        val artwork = snapshot.artworkUri?.let { loadArtwork(context, it) }
        provideContent {
            GlanceTheme {
                WidgetContent(snapshot, artwork)
            }
        }
    }

    companion object {
        private const val TAG = "NowPlayingWidget"
        private const val CONTROLLER_CONNECT_TIMEOUT_MS = 2_000L

        /** Fire-and-forget redraw, called by [MusicService] on playback changes. */
        fun push(context: Context) {
            val appContext = context.applicationContext
            CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
                runCatching { NowPlayingWidget().updateAll(appContext) }
                    .onFailure { KLog.w(TAG, "Widget update failed: ${it.message}") }
            }
        }

        /**
         * Snapshot playback through a short-lived controller. Binds the music
         * service as a side effect, which is harmless - the system does the
         * same for every browse/tile client - and bounded by a timeout so a
         * wedged bind cannot stall the widget render.
         */
        private suspend fun readSnapshot(context: Context): WidgetSnapshot {
            val token = SessionToken(context, ComponentName(context, MusicService::class.java))
            val future = MediaController.Builder(context, token).buildAsync()
            var controller: MediaController? = null
            try {
                controller = withTimeoutOrNull(CONTROLLER_CONNECT_TIMEOUT_MS) {
                    future.await()
                }
                if (controller == null || controller.currentTimeline.isEmpty) {
                    return WidgetSnapshot.empty()
                }
                return WidgetSnapshot(
                    hasMedia = true,
                    isPlaying = controller.isPlaying,
                    title = controller.mediaMetadata.title?.toString()?.takeIf { it.isNotBlank() },
                    artist = controller.mediaMetadata.artist?.toString()?.takeIf { it.isNotBlank() },
                    artworkUri = controller.mediaMetadata.artworkUri
                        ?.takeIf { it.toString().isNotBlank() },
                )
            } catch (e: Exception) {
                KLog.w(TAG, "Widget snapshot failed: ${e.message}")
                return WidgetSnapshot.empty()
            } finally {
                runCatching { controller?.release() }
                if (!future.isDone && !future.isCancelled) {
                    runCatching { MediaController.releaseFuture(future) }
                }
            }
        }

        private suspend fun loadArtwork(context: Context, uri: Uri): Bitmap? =
            try {
                val request = ImageRequest.Builder(context)
                    .data(uri)
                    .allowHardware(false)
                    .size(128)
                    .build()
                val result = context.imageLoader.execute(request)
                (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
            } catch (e: Exception) {
                null
            }
    }
}

/** What one render knows about playback; [empty] renders the tap-to-open state. */
private data class WidgetSnapshot(
    val hasMedia: Boolean,
    val isPlaying: Boolean,
    val title: String?,
    val artist: String?,
    val artworkUri: Uri?,
) {
    companion object {
        fun empty() = WidgetSnapshot(false, false, null, null, null)
    }
}

@Composable
private fun WidgetContent(snapshot: WidgetSnapshot, artwork: Bitmap?) {
    val size = androidx.glance.LocalSize.current
    val compact = size.width < 280.dp

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(28.dp)
            .background(GlanceTheme.colors.widgetBackground)
            .clickable(actionStartActivity<MainActivity>())
    ) {
        if (!snapshot.hasMedia) {
            EmptyState()
        } else {
            PlayingState(snapshot, artwork, compact)
        }
    }
}

/** Nothing playing, nothing queued: brand plus an invitation, not a blank tile. */
@Composable
private fun EmptyState() {
    Row(
        modifier = GlanceModifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.Start
    ) {
        Image(
            provider = ImageProvider(R.mipmap.ic_launcher_round),
            contentDescription = null,
            modifier = GlanceModifier.size(36.dp)
        )
        Spacer(modifier = GlanceModifier.width(12.dp))
        Text(
            text = stringResource(R.string.widget_tap_to_listen),
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            ),
            maxLines = 1
        )
    }
}

@Composable
private fun PlayingState(snapshot: WidgetSnapshot, artwork: Bitmap?, compact: Boolean) {
    Row(
        modifier = GlanceModifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Artwork, or the launcher mark when none exists yet - never a hole.
        if (artwork != null) {
            Image(
                provider = ImageProvider(artwork),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = GlanceModifier
                    .size(if (compact) 40.dp else 52.dp)
                    .cornerRadius(14.dp)
            )
        } else {
            Box(
                modifier = GlanceModifier
                    .size(if (compact) 40.dp else 52.dp)
                    .cornerRadius(14.dp)
                    .background(GlanceTheme.colors.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_playback_notification),
                    contentDescription = null,
                    modifier = GlanceModifier.size(22.dp),
                    colorFilter = androidx.glance.ColorFilter.tint(
                        GlanceTheme.colors.onSurfaceVariant
                    )
                )
            }
        }

        Spacer(modifier = GlanceModifier.width(12.dp))

        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = snapshot.title ?: "Koda",
                style = TextStyle(
                    color = GlanceTheme.colors.onBackground,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
            Spacer(modifier = GlanceModifier.height(2.dp))
            Text(
                text = snapshot.artist ?: "",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 12.sp
                ),
                maxLines = 1
            )
        }

        Spacer(modifier = GlanceModifier.width(8.dp))

        TransportControls(isPlaying = snapshot.isPlaying, compact = compact)
    }
}

@Composable
private fun TransportControls(isPlaying: Boolean, compact: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (!compact) {
            TransportButton(
                iconRes = R.drawable.ic_media_previous,
                description = "Previous",
                action = actionRunCallback<SkipPreviousAction>()
            )
            Spacer(modifier = GlanceModifier.width(4.dp))
        }
        TransportButton(
            iconRes = if (isPlaying) R.drawable.ic_media_pause else R.drawable.ic_media_play,
            description = if (isPlaying) "Pause" else "Play",
            action = actionRunCallback<TogglePlaybackAction>()
        )
        if (!compact) {
            Spacer(modifier = GlanceModifier.width(4.dp))
            TransportButton(
                iconRes = R.drawable.ic_media_next,
                description = "Next",
                action = actionRunCallback<SkipNextAction>()
            )
        }
    }
}

@Composable
private fun TransportButton(iconRes: Int, description: String, action: androidx.glance.action.Action) {
    // 24dp glyph inside 48dp of touch area: generous without ballooning the
    // widget's footprint on small cells.
    Box(
        modifier = GlanceModifier
            .size(48.dp)
            .clickable(action),
        contentAlignment = Alignment.Center
    ) {
        Image(
            provider = ImageProvider(iconRes),
            contentDescription = description,
            modifier = GlanceModifier.size(24.dp),
            colorFilter = androidx.glance.ColorFilter.tint(GlanceTheme.colors.onBackground)
        )
    }
}

/**
 * Every transport tap reconnects a throwaway controller rather than holding
 * one: a widget lives for days while controllers are expensive session
 * clients, and a held controller would keep the service bound forever.
 */
class TogglePlaybackAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        withTransientController(context) { controller ->
            // Decide from live state, not from the last render: the widget's
            // picture of the world can be a beat behind by tap time.
            if (controller.isPlaying || controller.playbackState == androidx.media3.common.Player.STATE_BUFFERING) {
                controller.pause()
            } else {
                controller.play()
            }
        }
        NowPlayingWidget.push(context)
    }
}

class SkipNextAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        withTransientController(context) { it.seekToNext() }
        NowPlayingWidget.push(context)
    }
}

class SkipPreviousAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        withTransientController(context) { it.seekToPrevious() }
        NowPlayingWidget.push(context)
    }
}

private suspend fun withTransientController(
    context: Context,
    block: (MediaController) -> Unit
) {
    val token = SessionToken(context, ComponentName(context, MusicService::class.java))
    val future = MediaController.Builder(context, token).buildAsync()
    try {
        val controller = withTimeoutOrNull(2_000L) { future.await() } ?: return
        block(controller)
        controller.release()
    } catch (e: Exception) {
        KLog.w("NowPlayingWidget", "Transport action failed: ${e.message}")
        runCatching { MediaController.releaseFuture(future) }
    }
}

class NowPlayingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NowPlayingWidget()
}
