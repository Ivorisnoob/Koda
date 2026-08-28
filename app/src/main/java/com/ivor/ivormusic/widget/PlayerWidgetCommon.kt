package com.ivor.ivormusic.widget

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.imageLoader
import coil.request.ImageRequest
import com.ivor.ivormusic.service.MusicService
import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

/**
 * The widget family's data layer: what a widget knows, where it is kept, and
 * the one way to talk to the session.
 *
 * **Widgets never read the session in order to draw.** [MusicService] writes
 * the state from its own player callback - the one place that cannot be wrong
 * about it - and [PlayerWidgetHost] hands it to the compositions. A render is a
 * preference read. The version that bound a controller per redraw was wrong
 * constantly for three compounding reasons: the read happened at an arbitrary
 * moment relative to the change that triggered it, six widgets raced each other
 * for the same answer, and a bind that timed out blanked the home screen.
 *
 * A controller is bound for exactly two things: issuing a command a tap asked
 * for, and seeding the store the very first time. Both go through
 * [withController], which hops to the main thread - a [MediaController] may
 * only ever be touched on its application thread, and Glance runs every
 * `provideGlance` and `ActionCallback` on a background dispatcher, so direct
 * calls threw IllegalStateException into a catch block and silently did
 * nothing. That was "the buttons only work sometimes".
 */

/** What one render knows about playback. */
data class PlayerWidgetSnapshot(
    val hasMedia: Boolean,
    val isPlaying: Boolean,
    val title: String?,
    val artist: String?,
    val artworkUri: Uri?,
    val positionMs: Long,
    val durationMs: Long,
    val hasNext: Boolean,
    val hasPrevious: Boolean,
    val shuffleEnabled: Boolean,
    val repeatMode: Int,
    /** The next few queue entries, in play order, for the Lineup widget. */
    val upNext: List<UpNextEntry>,
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
            hasNext = false,
            hasPrevious = false,
            shuffleEnabled = false,
            repeatMode = Player.REPEAT_MODE_OFF,
            upNext = emptyList(),
        )
    }
}

/** One upcoming queue row. [index] addresses the timeline, never a media id. */
data class UpNextEntry(
    val index: Int,
    val title: String,
    val artist: String?,
)

/** How many queue rows the Lineup widget can ever want. */
internal const val UP_NEXT_LOOKAHEAD = 5

/**
 * The one place widget state lives, as a single JSON blob in its own
 * preference file. JSON rather than a key per field because the queue is a
 * list, and one string keeps a write atomic - a widget can never render half of
 * one update and half of the next.
 *
 * Deliberately not in `ivor_music_theme_prefs`: this is a cache of playback,
 * not a setting, and it has no business in backups.
 */
object PlayerWidgetStore {

    private const val PREFS = "koda_widget_state"
    private const val KEY_STATE = "state"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun write(context: Context, snapshot: PlayerWidgetSnapshot) {
        val json = JSONObject().apply {
            put("hasMedia", snapshot.hasMedia)
            put("isPlaying", snapshot.isPlaying)
            put("title", snapshot.title ?: JSONObject.NULL)
            put("artist", snapshot.artist ?: JSONObject.NULL)
            put("artwork", snapshot.artworkUri?.toString() ?: JSONObject.NULL)
            put("position", snapshot.positionMs)
            put("duration", snapshot.durationMs)
            put("hasNext", snapshot.hasNext)
            put("hasPrevious", snapshot.hasPrevious)
            put("shuffle", snapshot.shuffleEnabled)
            put("repeat", snapshot.repeatMode)
            put(
                "upNext",
                JSONArray().apply {
                    snapshot.upNext.forEach { entry ->
                        put(
                            JSONObject().apply {
                                put("index", entry.index)
                                put("title", entry.title)
                                put("artist", entry.artist ?: JSONObject.NULL)
                            }
                        )
                    }
                }
            )
        }
        prefs(context).edit().putString(KEY_STATE, json.toString()).apply()
    }

    /** Null means nothing has ever been written - not "nothing is playing". */
    fun read(context: Context): PlayerWidgetSnapshot? {
        val raw = prefs(context).getString(KEY_STATE, null) ?: return null
        return try {
            val json = JSONObject(raw)
            val queue = json.optJSONArray("upNext")
            val entries = mutableListOf<UpNextEntry>()
            if (queue != null) {
                for (i in 0 until queue.length()) {
                    val item = queue.optJSONObject(i) ?: continue
                    val index = item.optInt("index", -1)
                    if (index < 0) continue
                    entries.add(
                        UpNextEntry(
                            index = index,
                            title = item.optString("title", ""),
                            artist = item.optString("artist", "").takeIf { it.isNotBlank() },
                        )
                    )
                }
            }
            PlayerWidgetSnapshot(
                hasMedia = json.optBoolean("hasMedia", false),
                isPlaying = json.optBoolean("isPlaying", false),
                title = json.optString("title", "").takeIf { it.isNotBlank() },
                artist = json.optString("artist", "").takeIf { it.isNotBlank() },
                artworkUri = json.optString("artwork", "").takeIf { it.isNotBlank() }
                    ?.let(Uri::parse),
                positionMs = json.optLong("position", 0L),
                durationMs = json.optLong("duration", 0L),
                hasNext = json.optBoolean("hasNext", false),
                hasPrevious = json.optBoolean("hasPrevious", false),
                shuffleEnabled = json.optBoolean("shuffle", false),
                repeatMode = json.optInt("repeat", Player.REPEAT_MODE_OFF),
                upNext = entries,
            )
        } catch (e: Exception) {
            KLog.w("PlayerWidget", "Widget state unreadable: ${e.message}")
            null
        }
    }

    /**
     * The service is going away. Keeps the track so the widget still shows what
     * was on, but drops the playing flag - a widget claiming to play music that
     * stopped when the process died is the one lie this store can tell.
     */
    fun markStopped(context: Context) {
        val current = read(context) ?: return
        if (!current.isPlaying) return
        write(context, current.copy(isPlaying = false))
    }
}

internal object PlayerWidgetReader {

    private const val TAG = "PlayerWidget"

    /**
     * One push decodes one cover for six widgets. Only the newest is kept - a
     * widget is always drawing the current track, so a second entry would only
     * ever be the one that just stopped being needed.
     */
    @Volatile
    private var artworkCache: Pair<String, Bitmap>? = null

    suspend fun loadArtwork(context: Context, uri: Uri, sizePx: Int): Bitmap? {
        val key = "$uri@$sizePx"
        artworkCache?.let { (cachedKey, bitmap) -> if (cachedKey == key) return bitmap }
        return try {
            val request = ImageRequest.Builder(context)
                .data(uri)
                .allowHardware(false)
                .size(sizePx)
                .build()
            val result = context.imageLoader.execute(request)
            val bitmap = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
            if (bitmap != null) artworkCache = key to bitmap
            bitmap
        } catch (e: Exception) {
            KLog.w(TAG, "Widget artwork failed: ${e.message}")
            null
        }
    }
}

/**
 * Read a player's current state. Valid on the player's own thread only, which
 * is the main thread for both callers - [MusicService]'s listener and a
 * controller inside [withController].
 */
internal fun Player.toWidgetSnapshot(): PlayerWidgetSnapshot {
    if (currentTimeline.isEmpty) return PlayerWidgetSnapshot.empty()
    val upcoming = mutableListOf<UpNextEntry>()
    var index = currentMediaItemIndex + 1
    while (index < mediaItemCount && upcoming.size < UP_NEXT_LOOKAHEAD) {
        val metadata = getMediaItemAt(index).mediaMetadata
        upcoming.add(
            UpNextEntry(
                index = index,
                title = metadata.title?.toString()?.takeIf { it.isNotBlank() } ?: "Unknown",
                artist = metadata.artist?.toString()?.takeIf { it.isNotBlank() },
            )
        )
        index++
    }
    return PlayerWidgetSnapshot(
        hasMedia = true,
        // Intent, not achievement: pressing play swaps the glyph now instead of
        // after the first buffer, which is what every other transport surface
        // on the device does.
        isPlaying = playWhenReady,
        title = mediaMetadata.title?.toString()?.takeIf { it.isNotBlank() },
        artist = mediaMetadata.artist?.toString()?.takeIf { it.isNotBlank() },
        artworkUri = mediaMetadata.artworkUri?.takeIf { it.toString().isNotBlank() },
        positionMs = currentPosition.coerceAtLeast(0L),
        durationMs = duration.takeIf { it > 0 } ?: 0L,
        hasNext = hasNextMediaItem(),
        hasPrevious = hasPreviousMediaItem(),
        shuffleEnabled = shuffleModeEnabled,
        repeatMode = repeatMode,
        upNext = upcoming,
    )
}

internal fun MediaController.toSnapshot(): PlayerWidgetSnapshot = toWidgetSnapshot()

/**
 * Bind a throwaway [MediaController], run [block] on the main thread, and
 * release it again. Widgets live for days while controllers are expensive
 * session clients, and a held controller would keep the service bound forever.
 *
 * Returns null when the session could not be reached at all, which callers use
 * to tell "nothing is playing" apart from "could not ask".
 */
internal suspend fun <T> withController(
    context: Context,
    timeoutMs: Long = 3_000L,
    block: suspend (MediaController) -> T,
): T? = withContext(Dispatchers.Main) {
    val appContext = context.applicationContext
    val token = SessionToken(appContext, ComponentName(appContext, MusicService::class.java))
    val future = MediaController.Builder(appContext, token).buildAsync()
    var controller: MediaController? = null
    try {
        controller = withTimeoutOrNull(timeoutMs) { future.await() } ?: return@withContext null
        block(controller)
    } catch (e: Exception) {
        KLog.w("PlayerWidget", "Session call failed: ${e.message}")
        null
    } finally {
        // Always both: release() covers a connected controller, releaseFuture
        // covers one still connecting when the timeout fired. Skipping the
        // second leaks a binding that keeps the service alive for nothing.
        runCatching { controller?.release() }
        runCatching { MediaController.releaseFuture(future) }
    }
}
