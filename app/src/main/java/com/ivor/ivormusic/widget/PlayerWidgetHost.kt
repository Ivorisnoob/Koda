package com.ivor.ivormusic.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.updateAll
import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * What every widget draws, as observable state.
 *
 * **This is the piece that was missing, and it is worth being precise about
 * why.** `GlanceAppWidget.provideGlance` runs *once per session*, not once per
 * update: `updateAll` re-runs the composition, it does not re-run the suspend
 * function around it. A snapshot read imperatively before `provideContent` is
 * therefore captured for the life of the session, and recomposing produces
 * byte-identical RemoteViews no matter how much the underlying playback has
 * changed. Pausing genuinely paused, the store genuinely flipped, the launcher
 * genuinely got an update - and the picture did not move, because nothing in
 * the composition had read anything new. Sessions do get torn down eventually,
 * which is exactly why it looked intermittent rather than broken.
 *
 * So the state lives in a flow that the composition collects. A push updates
 * [state]; every live session recomposes against the new value; [PlayerWidgets]
 * still calls `updateAll` so that widgets with no live session get one started.
 *
 * The artwork rides along with the snapshot rather than being loaded inside the
 * composition. One push decodes one cover for all six widgets, and a Glance
 * composition is not a good place to start IO.
 */
internal data class PlayerWidgetUi(
    val snapshot: PlayerWidgetSnapshot,
    val artwork: Bitmap?,
) {
    companion object {
        val Empty = PlayerWidgetUi(PlayerWidgetSnapshot.empty(), null)
    }
}

internal object PlayerWidgetHost {

    private const val TAG = "PlayerWidgetHost"

    /** Decoded at this size and re-cut per widget; large enough for the 2x4. */
    private const val ARTWORK_PX = 384

    private val _state = MutableStateFlow(PlayerWidgetUi.Empty)
    val state: StateFlow<PlayerWidgetUi> = _state.asStateFlow()

    private val loadLock = Mutex()

    @Volatile
    private var seeded = false

    /**
     * Make sure [state] holds something real before the first composition of a
     * session. Reads the store, and only falls back to a session bind when the
     * store has never been written - a widget added before this build ever ran.
     */
    suspend fun ensureSeeded(context: Context) {
        if (seeded) return
        loadLock.withLock {
            if (seeded) return
            val stored = PlayerWidgetStore.read(context)
            val snapshot = stored ?: withController(context) { it.toSnapshot() }
            if (snapshot != null) {
                if (stored == null) PlayerWidgetStore.write(context, snapshot)
                _state.value = PlayerWidgetUi(snapshot, loadArtwork(context, snapshot))
            }
            // Only latch once something was actually resolved, so a failed bind
            // retries on the next render instead of caching an empty widget.
            seeded = snapshot != null
        }
    }

    /** Publish new state to every live composition. */
    suspend fun set(context: Context, snapshot: PlayerWidgetSnapshot) {
        val sameCover = _state.value.snapshot.artworkUri == snapshot.artworkUri
        val artwork = if (sameCover) _state.value.artwork else loadArtwork(context, snapshot)
        _state.value = PlayerWidgetUi(snapshot, artwork)
        seeded = true
    }

    private suspend fun loadArtwork(context: Context, snapshot: PlayerWidgetSnapshot): Bitmap? {
        val uri = snapshot.artworkUri ?: return null
        return PlayerWidgetReader.loadArtwork(context, uri, ARTWORK_PX)
    }
}

object PlayerWidgets {

    private val receivers = listOf(
        PulseWidgetReceiver::class.java,
        ConsoleWidgetReceiver::class.java,
        DeckWidgetReceiver::class.java,
        LineupWidgetReceiver::class.java,
        BloomWidgetReceiver::class.java,
        OrbitWidgetReceiver::class.java,
    )

    private val widgets = listOf<GlanceAppWidget>(
        PulseWidget(),
        ConsoleWidget(),
        DeckWidget(),
        LineupWidget(),
        BloomWidget(),
        OrbitWidget(),
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var pushJob: Job? = null

    private const val PUSH_DEBOUNCE_MS = 120L

    /**
     * Write the state, hand it to every live composition, and make sure a
     * composition exists.
     *
     * The store write is unconditional but the redraw is not: state is worth
     * keeping current even with nothing on the home screen, so a widget added
     * later draws the right thing on its first frame.
     *
     * Redraws are debounced. One user action produces several event batches
     * within a few hundred milliseconds - play-when-ready, then playback state,
     * then metadata as it resolves - and each costs six RemoteViews trees.
     */
    fun publish(context: Context, snapshot: PlayerWidgetSnapshot) {
        val appContext = context.applicationContext
        PlayerWidgetStore.write(appContext, snapshot)
        if (!anyInstalled(appContext)) return
        pushJob?.cancel()
        pushJob = scope.launch {
            delay(PUSH_DEBOUNCE_MS)
            PlayerWidgetHost.set(appContext, snapshot)
            updateEveryWidget(appContext)
        }
    }

    /** Redraw without new state - used when the service is going away. */
    fun pushAll(context: Context) {
        val appContext = context.applicationContext
        if (!anyInstalled(appContext)) return
        pushJob?.cancel()
        pushJob = scope.launch {
            delay(PUSH_DEBOUNCE_MS)
            PlayerWidgetStore.read(appContext)?.let { PlayerWidgetHost.set(appContext, it) }
            updateEveryWidget(appContext)
        }
    }

    private suspend fun updateEveryWidget(context: Context) {
        for (widget in widgets) {
            runCatching { widget.updateAll(context) }
                .onFailure { KLog.w("PlayerWidgets", "Update failed: ${it.message}") }
        }
    }

    private fun anyInstalled(context: Context): Boolean =
        receivers.any { receiver ->
            runCatching {
                android.appwidget.AppWidgetManager.getInstance(context)
                    .getAppWidgetIds(android.content.ComponentName(context, receiver))
                    .isNotEmpty()
            }.getOrDefault(false)
        }
}
