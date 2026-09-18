package com.ivor.ivormusic.ui.video

import android.content.Context
import android.net.ConnectivityManager
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.ivor.ivormusic.data.CacheManager
import com.ivor.ivormusic.data.LocalVideo
import com.ivor.ivormusic.data.ThemePreferences
import com.ivor.ivormusic.data.VideoItem
import com.ivor.ivormusic.data.VideoQuality
import com.ivor.ivormusic.data.YouTubeRateLimit
import com.ivor.ivormusic.data.YouTubeRepository
import com.ivor.ivormusic.data.defaultForPreference
import com.ivor.ivormusic.data.deviceVideoHeightCap
import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A card must be at least this visible before it can claim the preview, and
 * falls below the second number to give it up.
 *
 * Two thresholds rather than one on purpose: with a single figure a card
 * resting exactly on it flaps between claiming and releasing on every frame of
 * a slow drag, which is a preview starting and stopping several times a second.
 */
private const val CLAIM_VISIBILITY = 0.6f
private const val RELEASE_VISIBILITY = 0.4f

/**
 * How long a card has to sit still before it starts.
 *
 * The timer only runs while the list is settled and restarts on any touch, so
 * this is dwell rather than elapsed time. Much below half a second the feed
 * starts playing things while the eye is still moving down it; much above a
 * second and resting on a card feels like nothing happened.
 */
private const val DWELL_MS = 700L

/**
 * Resolution starts before the dwell is up, so the picture can appear at the
 * dwell mark instead of a network round trip after it. A card that loses the
 * claim before then simply drops the work.
 */
private const val RESOLVE_AFTER_MS = 300L

/** How often visibility is sampled and the holder reconsidered. */
private const val POLL_MS = 150L

/** Beyond this the video is not worth waiting for; the card stays a thumbnail. */
private const val RESOLVE_BUDGET_MS = 6_000L

/**
 * Plays one video card in place, silently, while it is rested on.
 *
 * **One player, one card.** The coordinator owns a single [ExoPlayer] and a
 * single [previewingId]; a card draws a surface only when it is that id. Two
 * cards playing at once is therefore not a bug that can happen, it is a state
 * that cannot be represented - the same reason the Shorts overlay keeps one
 * player following its pager rather than one per page.
 *
 * **Previews are muted, and that is what keeps them out of the way of
 * everything else.** No audio focus is requested, so music keeps playing while
 * a card previews under it, and the preview needs no place in the
 * mutual-exclusion effects that pause the music, video and Shorts pipelines
 * against each other. A preview is a moving thumbnail, not a fourth playback
 * pipeline, and the moment the user wants it to be playback they tap it and get
 * the real player.
 *
 * Previews never run on a metered connection: this is discretionary traffic
 * spent on something nobody asked for, and a feed quietly downloading video on
 * mobile data is the kind of thing people uninstall an app over. Resolution is
 * discretionary too, so it stands down behind [YouTubeRateLimit].
 */
@OptIn(UnstableApi::class)
class InlinePreviewController(
    private val context: Context,
    private val repository: YouTubeRepository,
    private val scope: kotlinx.coroutines.CoroutineScope,
) {
    /** The card currently allowed to draw a moving picture, if any. */
    var previewingId: String? by mutableStateOf(null)
        private set

    /** 0f..1f through the preview, for the card's own progress line. */
    var progress: Float by mutableFloatStateOf(0f)
        private set

    /** True once the picture is actually moving, so the card can cross-fade. */
    var isRendering: Boolean by mutableStateOf(false)
        private set

    /**
     * Previews start silent and stay that way until the user says otherwise.
     * Silence is what keeps a preview a moving thumbnail rather than a fourth
     * playback pipeline: it takes no audio focus, so music keeps playing
     * underneath and the preview needs no place in the mutual-exclusion
     * effects. Unmuting is always an explicit tap on the card's toggle.
     */
    var isMuted: Boolean by mutableStateOf(true)
        private set

    /** Silent or audible. Explicit user choice - nothing calls this on its own. */
    fun setPreviewMuted(muted: Boolean) {
        isMuted = muted
        player?.volume = if (muted) 0f else 1f
    }

    /**
     * Whether the feed is sitting still. Held here rather than passed to every
     * card so that a card, which may be drawn on surfaces that have no feed at
     * all, does not have to know which list it is in.
     */
    var isListSettled: Boolean by mutableStateOf(true)

    private var player: ExoPlayer? = null
    private var claimJob: Job? = null
    private var progressJob: Job? = null
    // Fresh pref read per resolve (same reason as the video player VMs: Settings
    // toggles through its own ThemePreferences instance). getDefaultVideoQuality
    // reads SharedPreferences directly, so this never goes stale.
    private val themePreferences = ThemePreferences(context)

    init {
        // One loop for the whole feed rather than one per card, because the
        // decision is about the cards as a set: which of them is most visible.
        scope.launch {
            while (isActive) {
                arbitrate()
                delay(POLL_MS)
            }
        }
    }

    /** The id whose dwell timer is running; not yet the one playing. */
    private var claimedId: String? = null

    /** What every card on screen currently reports about itself. */
    private val onScreen = LinkedHashMap<String, Pair<VideoItem, Float>>()

    val exoPlayer: ExoPlayer? get() = player

    /**
     * A card reports how visible it is. Cards do not claim the preview
     * themselves.
     *
     * **This arbitration is the whole reason the feature works.** [scar] Cards
     * used to call `claim` directly whenever they were over the threshold, and
     * a 16:9 feed routinely has two cards more than 60% on screen at once - so
     * both claimed, every poll, and each claim cancelled the other's dwell
     * timer. Neither ever survived to the end of its 700ms, resolution never
     * started, and the feature did nothing while looking, in the log, like it
     * was constantly about to.
     */
    fun report(video: VideoItem, fraction: Float) {
        synchronized(onScreen) { onScreen[video.videoId] = video to fraction }
    }

    /** A card left the screen; it stops being a candidate. */
    fun forget(videoId: String) {
        synchronized(onScreen) { onScreen.remove(videoId) }
        releaseIfHeldBy(videoId)
    }

    private fun fractionOf(videoId: String): Float =
        synchronized(onScreen) { onScreen[videoId]?.second ?: 0f }

    /**
     * Pick who holds the preview. One holder, chosen here and nowhere else.
     *
     * **The holder keeps it while it stays above the release threshold**, even
     * if another card is momentarily more visible. Handing the preview to
     * whichever card is highest at each tick would restart playback every time
     * two cards traded places by a percent, which on a settled list is
     * constant.
     */
    private fun arbitrate() {
        if (!isListSettled) {
            release()
            return
        }
        claimedId?.let { holder ->
            if (fractionOf(holder) >= RELEASE_VISIBILITY) return
            release()
        }
        val best = synchronized(onScreen) {
            onScreen.values.filter { it.second >= CLAIM_VISIBILITY }.maxByOrNull { it.second }
        } ?: return
        claim(best.first)
    }

    private fun claim(video: VideoItem) {
        if (claimedId == video.videoId) return
        if (!isPreviewable(video)) {
            KLog.d(TAG, "Not previewing ${video.videoId}: ${skipReason(video)}")
            return
        }
        release()
        claimedId = video.videoId
        KLog.d(TAG, "Claimed ${video.videoId}, dwelling")
        claimJob = scope.launch {
            delay(RESOLVE_AFTER_MS)
            val quality = resolve(video.videoId) ?: run {
                KLog.d(TAG, "No playable rendition for ${video.videoId}")
                claimedId = null
                return@launch
            }
            KLog.d(TAG, "Resolved ${video.videoId}: ${quality.resolution} dash=${quality.isDASH}")
            // The rest of the dwell, after the resolve rather than before it.
            delay((DWELL_MS - RESOLVE_AFTER_MS).coerceAtLeast(0L))
            if (claimedId != video.videoId) return@launch
            start(video.videoId, quality)
        }
    }

    /** The card is no longer eligible: scrolled away, or something opened over it. */
    fun release() {
        claimJob?.cancel()
        claimJob = null
        progressJob?.cancel()
        progressJob = null
        claimedId = null
        previewingId = null
        isRendering = false
        progress = 0f
        // Back to silent: the next preview starts muted, and a stopped player
        // holding volume 1f would otherwise leak sound into it.
        isMuted = true
        player?.volume = 0f
        player?.stop()
        player?.clearMediaItems()
    }

    /** Only the card that holds the preview can give it up, so a card leaving the
     * screen cannot cancel a preview that has already moved on to another. */
    fun releaseIfHeldBy(videoId: String) {
        if (claimedId == videoId || previewingId == videoId) release()
    }

    fun destroy() {
        release()
        player?.release()
        player = null
        CacheManager.setVideoPlaybackActive(CACHE_OWNER, false)
    }

    private fun skipReason(video: VideoItem): String = when {
        video.isLive -> "live"
        LocalVideo.isDeviceVideoId(video.videoId) -> "device file"
        video.videoId.startsWith("external:") -> "external"
        YouTubeRateLimit.isHeld() -> "rate limited"
        isMetered() -> "metered connection"
        else -> "eligible"
    }

    private fun isPreviewable(video: VideoItem): Boolean {
        if (video.isLive) return false
        if (LocalVideo.isDeviceVideoId(video.videoId)) return false
        if (video.videoId.startsWith("external:")) return false
        if (YouTubeRateLimit.isHeld()) return false
        if (isMetered()) return false
        return true
    }

    /**
     * Data Saver and mobile data both count as metered. Asked per claim rather
     * than cached: this is exactly the setting that changes while the app is
     * open, walking out of the house with the feed already on screen.
     */
    private fun isMetered(): Boolean = runCatching {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        // Data Saver is not checked separately: its status only applies on a
        // metered network, which this already refuses, and reading it as a
        // second gate turned previews off on Wi-Fi for anyone who leaves Data
        // Saver on permanently.
        manager.isActiveNetworkMetered
    }.getOrDefault(true)

    /**
     * The rendition matching the user's default video quality preference,
     * never HDR (previews exclude HDR at resolve time) and never live.
     *
     * Ranked on the `resolution` label via [defaultForPreference] - the same
     * rule the watch page and Shorts use - never on [VideoQuality.height],
     * which is 0 for entries whose resolver never declared both dimensions.
     * Ordering by height silently treated "unknown" as "smallest" and handed
     * back a 1440p VP9 stream the decoder refused to initialise.
     */
    private suspend fun resolve(videoId: String): VideoQuality? = withTimeoutOrNull(RESOLVE_BUDGET_MS) {
        runCatching {
            val playable = repository.getVideoStreamQualities(videoId, includeHdr = false)
                .filterNot { it.isHdr || it.isLive }
            // Same pick as opening the video: the user's default quality for
            // this network, highest-first ladder, first entry at or below the
            // target - capped at what this panel can show, so a preview never
            // opens a 4K stream on a 720p phone. Adaptive manifests stay
            // eligible - most videos resolve to an adaptive ladder and nothing
            // else, so excluding them left an empty list and a preview that
            // silently never started.
            playable.defaultForPreference(
                themePreferences.getDefaultVideoQuality(),
                context.deviceVideoHeightCap()
            )
        }.onFailure { error ->
            if (error is kotlinx.coroutines.CancellationException) throw error
            KLog.w(TAG, "Preview resolve failed for $videoId", error)
        }.getOrNull()
    }

    private fun start(videoId: String, quality: VideoQuality) {
        KLog.d(TAG, "Starting preview of $videoId")
        val active = player ?: buildPlayer().also { player = it }
        previewingId = videoId
        progress = 0f
        isRendering = false
        // Every preview starts silent; sound is strictly opt-in per preview
        // via the card's toggle, never inherited from a previous unmute.
        isMuted = true
        active.volume = 0f

        if (quality.isDASH) {
            // Feeding a manifest to ProgressiveMediaSource fails extraction
            // outright; the MediaSourceFactory on the player builds the right
            // source from the MIME type.
            active.setMediaItem(
                MediaItem.Builder()
                    .setUri(quality.url)
                    .setMimeType(adaptivePreviewMimeType(quality))
                    .build()
            )
            active.prepare()
            active.play()
            trackProgress(active, videoId)
            return
        }

        val factory = CacheManager.createVideoPlaybackDataSourceFactory(context, shorts = false)
        val videoSource = ProgressiveMediaSource.Factory(factory)
            .createMediaSource(MediaItem.fromUri(quality.url))
        val audioUrl = quality.audioUrl
        // The audio track is loaded and then silenced rather than dropped: a
        // video-only rendition of a merged stream desynchronises its own
        // timeline on some sources, and volume 0 costs a few kilobytes against
        // a preview that stutters.
        if (audioUrl != null) {
            active.setMediaSource(
                MergingMediaSource(
                    true,
                    videoSource,
                    ProgressiveMediaSource.Factory(factory).createMediaSource(MediaItem.fromUri(audioUrl)),
                )
            )
        } else {
            active.setMediaSource(videoSource)
        }
        active.prepare()
        active.play()
        trackProgress(active, videoId)
    }

    /** MIME for an adaptive quality; both DASH and HLS arrive with isDASH set. */
    private fun adaptivePreviewMimeType(quality: VideoQuality): String =
        if (quality.url.contains(".m3u8")) androidx.media3.common.MimeTypes.APPLICATION_M3U8
        else androidx.media3.common.MimeTypes.APPLICATION_MPD

    private fun trackProgress(active: ExoPlayer, videoId: String) {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive && previewingId == videoId) {
                val duration = active.duration
                progress = if (duration > 0) {
                    (active.currentPosition.toFloat() / duration).coerceIn(0f, 1f)
                } else 0f
                delay(250)
            }
        }
    }

    private fun buildPlayer(): ExoPlayer {
        CacheManager.setVideoPlaybackActive(CACHE_OWNER, true)
        val loadControl = DefaultLoadControl.Builder()
            // A preview only ever plays the opening, so a long buffer is data
            // spent on seconds nobody reaches.
            .setBufferDurationsMs(2_000, 8_000, 500, 1_000)
            .build()
        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(CacheManager.createVideoPlaybackDataSourceFactory(context, shorts = false)))
            .setLoadControl(loadControl)
            .build()
            .apply {
                volume = 0f
                repeatMode = Player.REPEAT_MODE_ONE
                addListener(object : Player.Listener {
                    override fun onRenderedFirstFrame() {
                        KLog.d(TAG, "First frame for $previewingId")
                        isRendering = true
                    }

                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        // A preview that cannot play is a thumbnail. Nothing is
                        // shown to the user: they did not ask for this.
                        KLog.w(TAG, "Preview playback failed", error)
                        release()
                    }
                })
            }
    }

    private companion object {
        const val TAG = "InlinePreview"
        const val CACHE_OWNER = "inline_preview"
    }
}

/**
 * Null when previews are off, which is the whole of the feature's cost when it
 * is disabled: cards read a null local and draw exactly what they drew before.
 */
val LocalInlinePreview = compositionLocalOf<InlinePreviewController?> { null }

/**
 * Builds the controller for a feed, and tears it down with the screen.
 *
 * Also stops on every lifecycle pause, so backgrounding the app or turning the
 * screen off cannot leave a video decoding behind a locked screen.
 */
@Composable
fun rememberInlinePreviewController(enabled: Boolean): InlinePreviewController? {
    // Built either way and returned only when enabled, rather than returning
    // early on the flag: an early return before the remembers below changes the
    // shape of the composition when the setting is toggled with the feed open,
    // which is exactly when someone turning it on is looking at it. Nothing is
    // spent while it is off - the player is built on the first preview, not
    // here.
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = remember(context) {
        InlinePreviewController(
            context = context.applicationContext,
            repository = YouTubeRepository(context.applicationContext),
            scope = scope,
        )
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, controller) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) controller.release()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller.destroy()
        }
    }
    LaunchedEffect(controller, enabled) {
        KLog.d("InlinePreview", "Controller enabled=$enabled")
        if (!enabled) controller.release()
    }
    return controller.takeIf { enabled }
}

/**
 * Wires one card to the coordinator: claims the preview when the card is
 * settled and visible enough, gives it up when it is not.
 *
 * [visibility] is a lambda rather than a value so the card can report a
 * fraction that changes every frame of a scroll without recomposing itself to
 * do it.
 */
@Composable
fun InlinePreviewClaim(
    video: VideoItem,
    visibility: () -> Float,
) {
    val controller = LocalInlinePreview.current ?: return
    val settled = controller.isListSettled
    DisposableEffect(controller, video.videoId) {
        onDispose { controller.forget(video.videoId) }
    }
    LaunchedEffect(controller, video.videoId, settled) {
        if (!settled) {
            // Any touch on the list gives up the claim, and the dwell starts
            // again from zero once it settles. Scrolling past a card is not
            // resting on it.
            controller.releaseIfHeldBy(video.videoId)
            return@LaunchedEffect
        }
        // Polled rather than driven by the layout pass: the card reports its
        // visibility into a state nothing reads during composition, so a scroll
        // moves the number without recomposing ten cards a frame to do it.
        // Reporting only - the controller decides who plays, because that
        // decision needs every card's number and a card only knows its own.
        while (isActive) {
            controller.report(video, visibility())
            delay(POLL_MS)
        }
    }
}

/**
 * How much of this card is on screen, vertically.
 *
 * Written into a float state the card does not read in composition - only the
 * claim's polling lambda does - so a scroll updates it without recomposing
 * every card on every frame.
 */
fun LayoutCoordinates.visibleVerticalFraction(): Float {
    val height = size.height.toFloat()
    if (height <= 0f) return 0f
    val bounds = boundsInWindow()
    return ((bounds.bottom - bounds.top) / height).coerceIn(0f, 1f)
}

/**
 * The moving picture itself, drawn over the card's thumbnail.
 *
 * Only the one card holding the preview composes this, which is what keeps a
 * single player attached to a single surface - handing an ExoPlayer to a second
 * PlayerView detaches it from the first, and both cards would end up blank.
 */
@OptIn(UnstableApi::class)
@Composable
fun InlinePreviewSurface(
    controller: InlinePreviewController,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
) {
    val player = controller.exoPlayer ?: return
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { ctx ->
            androidx.media3.ui.PlayerView(ctx).apply {
                useController = false
                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
        },
        update = { view -> view.player = player },
        onRelease = { view -> view.player = null },
        modifier = modifier,
    )
}
