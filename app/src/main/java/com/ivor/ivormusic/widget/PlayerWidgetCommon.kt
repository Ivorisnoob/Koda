package com.ivor.ivormusic.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.updateAll
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.imageLoader
import coil.request.ImageRequest
import com.ivor.ivormusic.service.MusicService
import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Shared plumbing for the widget family: one snapshot of playback read through
 * a short-lived [MediaController], one artwork loader, and the push fan-out to
 * every installed widget. Each widget stays a pure function of a snapshot so
 * they can never disagree with each other or with in-app playback.
 */

/** What one render knows about playback; [empty] renders the tap-to-open state. */
internal data class PlayerWidgetSnapshot(
    val hasMedia: Boolean,
    val isPlaying: Boolean,
    val title: String?,
    val artist: String?,
    val artworkUri: Uri?,
    val positionMs: Long,
    val durationMs: Long,
    val nextTitle: String?,
    val shuffleEnabled: Boolean,
    val repeatMode: Int,
) {
    /** 0f..1f for the progress strip; 0 when the duration is unknown. */
    val progress: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    companion object {
        fun empty() = PlayerWidgetSnapshot(
            hasMedia = false,
            isPlaying = false,
            title = null,
            artist = null,
            artworkUri = null,
            positionMs = 0L,
            durationMs = 0L,
            nextTitle = null,
            shuffleEnabled = false,
            repeatMode = Player.REPEAT_MODE_OFF,
        )
    }
}

internal object PlayerWidgetReader {

    private const val TAG = "PlayerWidget"
    private const val CONTROLLER_CONNECT_TIMEOUT_MS = 2_000L
    private const val SNAPSHOT_TTL_MS = 2_000L

    @Volatile
    private var cached: Pair<Long, PlayerWidgetSnapshot>? = null

    suspend fun snapshot(context: Context): PlayerWidgetSnapshot {
        val now = SystemClock.elapsedRealtime()
        cached?.let { (at, value) -> if (now - at < SNAPSHOT_TTL_MS) return value }
        val fresh = read(context)
        cached = SystemClock.elapsedRealtime() to fresh
        return fresh
    }

    fun invalidate() {
        cached = null
    }

    private suspend fun read(context: Context): PlayerWidgetSnapshot {
        val token = SessionToken(context, ComponentName(context, MusicService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        var controller: MediaController? = null
        try {
            controller = withTimeoutOrNull(CONTROLLER_CONNECT_TIMEOUT_MS) { future.await() }
            if (controller == null || controller.currentTimeline.isEmpty) {
                return PlayerWidgetSnapshot.empty()
            }
            val nextTitle = if (controller.hasNextMediaItem()) {
                controller.getMediaItemAt(controller.getNextMediaItemIndex())
                    .mediaMetadata.title?.toString()?.takeIf { it.isNotBlank() }
            } else {
                null
            }
            return PlayerWidgetSnapshot(
                hasMedia = true,
                isPlaying = controller.isPlaying,
                title = controller.mediaMetadata.title?.toString()?.takeIf { it.isNotBlank() },
                artist = controller.mediaMetadata.artist?.toString()?.takeIf { it.isNotBlank() },
                artworkUri = controller.mediaMetadata.artworkUri
                    ?.takeIf { it.toString().isNotBlank() },
                positionMs = controller.currentPosition.coerceAtLeast(0L),
                durationMs = controller.duration.takeIf { it > 0 } ?: 0L,
                nextTitle = nextTitle,
                shuffleEnabled = controller.shuffleModeEnabled,
                repeatMode = controller.repeatMode,
            )
        } catch (e: Exception) {
            KLog.w(TAG, "Widget snapshot failed: ${e.message}")
            return PlayerWidgetSnapshot.empty()
        } finally {
            runCatching { controller?.release() }
            if (!future.isDone && !future.isCancelled) {
                runCatching { MediaController.releaseFuture(future) }
            }
        }
    }

    suspend fun loadArtwork(context: Context, uri: Uri, sizePx: Int): Bitmap? =
        try {
            val request = ImageRequest.Builder(context)
                .data(uri)
                .allowHardware(false)
                .size(sizePx)
                .build()
            val result = context.imageLoader.execute(request)
            (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
        } catch (e: Exception) {
            null
        }
}

internal suspend fun withTransientController(
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
        KLog.w("PlayerWidget", "Transport action failed: ${e.message}")
        runCatching { MediaController.releaseFuture(future) }
    }
}

object PlayerWidgets {

    private val receivers = listOf(
        NowPlayingWidgetReceiver::class.java,
        CirclePlayerWidgetReceiver::class.java,
        MiniPlayerWidgetReceiver::class.java,
        LargePlayerWidgetReceiver::class.java,
        CanvasPlayerWidgetReceiver::class.java,
        QuickControlsWidgetReceiver::class.java,
    )

    private val widgets = listOf<GlanceAppWidget>(
        NowPlayingWidget(),
        CirclePlayerWidget(),
        MiniPlayerWidget(),
        LargePlayerWidget(),
        CanvasPlayerWidget(),
        QuickControlsWidget(),
    )

    fun pushAll(context: Context) {
        val appContext = context.applicationContext
        if (!anyInstalled(appContext)) return
        PlayerWidgetReader.invalidate()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            for (widget in widgets) {
                runCatching { widget.updateAll(appContext) }
                    .onFailure { KLog.w("PlayerWidgets", "Update failed: ${it.message}") }
            }
        }
    }

    private fun anyInstalled(context: Context): Boolean =
        receivers.any { receiver ->
            AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, receiver))
                .isNotEmpty()
        }
}
