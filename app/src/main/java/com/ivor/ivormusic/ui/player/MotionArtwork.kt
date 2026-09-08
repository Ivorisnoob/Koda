package com.ivor.ivormusic.ui.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.TextureView
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import com.ivor.ivormusic.data.IncognitoMode
import com.ivor.ivormusic.data.MotionArtworkRepository
import com.ivor.ivormusic.data.MotionArtworkResolver
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.data.ThemePreferences
import com.ivor.ivormusic.ui.components.SongArtwork
import java.io.File
import java.io.IOException
import kotlinx.coroutines.delay

internal val LocalMotionArtwork = staticCompositionLocalOf<MotionArtworkSession?> { null }

/** Only hero artwork opts in. Queue rows, backgrounds and the mini-player remain ordinary images. */
@Composable
internal fun PlayerArtwork(
    song: Song,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    motionEligible: Boolean = true,
) {
    val session = LocalMotionArtwork.current?.takeIf { it.songId == song.id && motionEligible }
    Box(modifier) {
        SongArtwork(song, contentDescription, Modifier.matchParentSize(), contentScale)
        if (session != null && !session.failed) {
            key(session) {
                MotionArtworkSurface(session, contentScale, Modifier.matchParentSize())
            }
        }
    }
}

@Composable
internal fun rememberMotionArtworkSession(song: Song, active: Boolean): MotionArtworkSession? {
    val context = LocalContext.current.applicationContext
    val preferences = remember(context) { ThemePreferences(context) }
    val enabled by preferences.motionArtwork.collectAsState()
    val wifiOnly by preferences.motionArtworkWifiOnly.collectAsState()
    val localOnly by preferences.localOnlyMode.collectAsState()
    val incognito by remember(context) { IncognitoMode.enabled(context) }.collectAsState()
    val repository = remember(context) { MotionArtworkRepository(context) }
    // No network observer, catalog request or video decoder until this feature is opted into.
    if (!enabled || !active || localOnly || incognito) return null
    val allowed = rememberMotionArtworkAllowed(wifiOnly)
    if (!allowed) return null
    var url by remember(song) { mutableStateOf<String?>(null) }
    LaunchedEffect(song, repository) { url = repository.resolve(song) }
    val resolvedUrl = url ?: return null
    var session by remember(song, resolvedUrl) { mutableStateOf<MotionArtworkSession?>(null) }
    DisposableEffect(song, resolvedUrl) {
        val created = MotionArtworkSession.create(context, song.id, resolvedUrl)
        session = created
        onDispose { created?.release() }
    }
    return session
}

/** Observe changes, not a one-time Wi-Fi check: switching to mobile data stops artwork immediately. */
@Composable
private fun rememberMotionArtworkAllowed(wifiOnly: Boolean): Boolean {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var allowed by remember(wifiOnly, lifecycle) { mutableStateOf(false) }
    DisposableEffect(context, lifecycle, wifiOnly) {
        val handler = Handler(Looper.getMainLooper())
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val power = context.getSystemService(PowerManager::class.java)
        var disposed = false
        fun update() {
            if (disposed) return
            val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
            val connected = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
            val wifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            val reducedMotion = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
            allowed = connected && (!wifiOnly || wifi) && !power.isPowerSaveMode && !reducedMotion &&
                connectivity.restrictBackgroundStatus != ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED &&
                lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        val network = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { handler.post { update() } }
            override fun onLost(network: Network) { handler.post { update() } }
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) { handler.post { update() } }
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { update() }
        }
        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) { update() }
        }
        val lifecycleObserver = LifecycleEventObserver { _, _ -> update() }
        connectivity.registerDefaultNetworkCallback(network)
        context.registerReceiver(receiver, IntentFilter().apply {
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            addAction(ConnectivityManager.ACTION_RESTRICT_BACKGROUND_CHANGED)
        })
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        lifecycle.addObserver(lifecycleObserver)
        update()
        onDispose {
            disposed = true
            connectivity.unregisterNetworkCallback(network)
            context.unregisterReceiver(receiver)
            context.contentResolver.unregisterContentObserver(observer)
            lifecycle.removeObserver(lifecycleObserver)
        }
    }
    return allowed
}

/** One muted decoder per expanded player, with a bounded disk cache so looping does not redownload segments. */
@androidx.annotation.OptIn(UnstableApi::class)
internal class MotionArtworkSession private constructor(
    context: Context,
    val songId: String,
    url: String,
    private val database: StandaloneDatabaseProvider,
    private val cache: SimpleCache,
) {
    var firstFrame by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)
        private set
    private var surface: TextureView? = null
    private val player = ExoPlayer.Builder(context)
        .setMediaSourceFactory(DefaultMediaSourceFactory(CacheDataSource.Factory().setCache(cache)
            .setUpstreamDataSourceFactory(ResolvingDataSource.Factory(
                DefaultHttpDataSource.Factory().setConnectTimeoutMs(8_000).setReadTimeoutMs(8_000)
            ) { dataSpec ->
                if (!MotionArtworkResolver.isMediaUrl(dataSpec.uri.toString())) throw IOException("Untrusted artwork media host")
                dataSpec
            })))
        .setLoadControl(DefaultLoadControl.Builder().setBufferDurationsMs(2_000, 5_000, 500, 1_000)
            .setTargetBufferBytes(4 * 1024 * 1024).setPrioritizeTimeOverSizeThresholds(false).build())
        .build().apply {
            volume = 0f
            setAudioAttributes(androidx.media3.common.AudioAttributes.DEFAULT, false)
            setHandleAudioBecomingNoisy(false)
            trackSelectionParameters = trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
            repeatMode = Player.REPEAT_MODE_ONE
            addListener(object : Player.Listener {
                override fun onRenderedFirstFrame() { firstFrame = true }
                override fun onPlayerError(error: PlaybackException) {
                    fail()
                }
            })
            setMediaItem(MediaItem.fromUri(url))
        }

    fun attach(view: TextureView) {
        if (failed) return
        firstFrame = false
        surface = view
        player.setVideoTextureView(view)
        player.prepare()
        player.play()
    }

    fun detach(view: TextureView) {
        if (surface !== view) return
        player.clearVideoTextureView(view)
        player.stop()
        surface = null
        firstFrame = false
    }

    fun release() {
        surface = null
        player.release()
        cache.release()
        database.close()
    }

    fun fail() {
        failed = true
        firstFrame = false
        player.stop()
    }

    companion object {
        fun create(context: Context, songId: String, url: String): MotionArtworkSession? {
            val database = StandaloneDatabaseProvider(context)
            var cache: SimpleCache? = null
            return try {
                cache = SimpleCache(File(context.cacheDir, "motion-artwork-media"), LeastRecentlyUsedCacheEvictor(32L * 1024 * 1024), database)
                MotionArtworkSession(context, songId, url, database, cache)
            } catch (_: RuntimeException) {
                // Disk/cache ownership failure must not break the music player.
                cache?.release()
                database.close()
                null
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun MotionArtworkSurface(session: MotionArtworkSession, contentScale: ContentScale, modifier: Modifier) {
    LaunchedEffect(session) {
        delay(12_000)
        if (!session.firstFrame) session.fail()
    }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            AspectRatioFrameLayout(context).apply {
                setAspectRatio(1f)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                addView(TextureView(context).apply {
                    isOpaque = false
                    alpha = 0f
                    session.attach(this)
                }, android.widget.FrameLayout.LayoutParams(-1, -1))
            }
        },
        update = { view ->
            view.resizeMode = if (contentScale == ContentScale.Crop) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
            view.getChildAt(0).alpha = if (session.firstFrame && !session.failed) 1f else 0f
        },
        onRelease = { view -> session.detach(view.getChildAt(0) as TextureView) },
    )
}
