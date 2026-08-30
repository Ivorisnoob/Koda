package com.ivor.ivormusic.ui.tv

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.ivor.ivormusic.R
import com.ivor.ivormusic.ui.video.PlayerGestureSurface
import java.util.Locale

/**
 * TV playback, full screen.
 *
 * **No mini bar, deliberately** - see the note on [TvPlayerViewModel]. That is
 * what keeps this one boolean above the NavHost rather than a third overlay
 * with its own expand animation, step-aside path and nav-bar arithmetic.
 *
 * The chrome is the same vocabulary as the video player so nothing has to be
 * learned twice: tap to toggle, double-tap either side to seek ten seconds,
 * press and hold for 2x, drag the left half for brightness and the right for
 * volume. All of that comes from `PlayerGestureSurface`, shared rather than
 * copied, because a gesture that behaves differently in one mode is worse than
 * one that is missing.
 */
@OptIn(UnstableApi::class)
@Composable
fun TvPlayerScreen(viewModel: TvPlayerViewModel) {
    val playback by viewModel.playback.collectAsState()
    val current = playback ?: return

    val isPlaying by viewModel.isPlaying.collectAsState()
    val isBuffering by viewModel.isBuffering.collectAsState()
    val positionMs by viewModel.positionMs.collectAsState()
    val durationMs by viewModel.durationMs.collectAsState()
    val bufferedMs by viewModel.bufferedMs.collectAsState()
    val audioTracks by viewModel.audioTracks.collectAsState()
    val problem by viewModel.problem.collectAsState()
    val nextEpisode by viewModel.nextEpisode.collectAsState()
    val countdown by viewModel.autoplayCountdown.collectAsState()
    val isAdvancing by viewModel.isAdvancing.collectAsState()

    val context = LocalContext.current
    val activity = context as? Activity

    var controlsVisible by remember { mutableStateOf(true) }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableFloatStateOf(0f) }
    var showAudioSheet by remember { mutableStateOf(false) }
    var zoomedToFill by remember { mutableStateOf(false) }
    var speedBeforeBoost by remember { mutableFloatStateOf(1f) }
    // Bumped on every interaction so the auto-hide timer restarts rather than
    // firing while someone is still using the controls.
    var interaction by remember { mutableIntStateOf(0) }

    BackHandler(enabled = true) { viewModel.close() }

    // Hide the chrome after a few idle seconds, but never while paused: a
    // paused player with no controls is a still image nobody can restart.
    LaunchedEffect(controlsVisible, isPlaying, interaction, isScrubbing) {
        if (controlsVisible && isPlaying && !isScrubbing) {
            kotlinx.coroutines.delay(CONTROLS_TIMEOUT_MS)
            controlsVisible = false
        }
    }

    // Stop decoding whenever the app stops being visible. ON_STOP is the right
    // signal for the same reason the video player uses it: the Surface is
    // destroyed under a decoding MediaCodec on home, recents and screen-off,
    // and this player has no background form to keep running for.
    DisposableEffect(activity, viewModel) {
        val lifecycle = activity?.let { it as? androidx.lifecycle.LifecycleOwner }?.lifecycle
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) viewModel.onEnterBackground()
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }

    // Immersive while this is on screen, restored on the way out. Playing a
    // film behind a status bar is the one place the system chrome is pure loss.
    DisposableEffect(activity) {
        val window = activity?.window
        val controller = window?.let {
            androidx.core.view.WindowCompat.getInsetsController(it, it.decorView)
        }
        controller?.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        controller?.systemBarsBehavior =
            androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Turned to landscape on open, unlike the YouTube player which starts
        // inline in portrait. This one has no inline form: everything it plays
        // is 16:9 long-form, so portrait is a letterboxed strip and a rotation
        // every viewer would perform themselves. Sensor landscape rather than a
        // fixed one, so the device can still be held either way up.
        val previousOrientation = activity?.requestedOrientation
        activity?.requestedOrientation =
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            controller?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.requestedOrientation = previousOrientation
                ?: android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        PlayerGestureSurface(
            onToggleControls = {
                controlsVisible = !controlsVisible
                interaction++
            },
            onSeekBackward = {
                viewModel.seekBy(-TvPlayerViewModel.SEEK_STEP_MS)
                interaction++
            },
            onSeekForward = {
                viewModel.seekBy(TvPlayerViewModel.SEEK_STEP_MS)
                interaction++
            },
            modifier = Modifier.fillMaxSize(),
            fullscreenGesturesEnabled = true,
            onZoomedToFillChange = { zoomedToFill = it },
            onSpeedBoostStart = {
                speedBeforeBoost = viewModel.currentSpeed()
                viewModel.setSpeed(2f)
            },
            onSpeedBoostEnd = { viewModel.setSpeed(speedBeforeBoost) },
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        player = viewModel.exoPlayer()
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                    }
                },
                update = { view ->
                    view.resizeMode =
                        if (zoomedToFill) AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        else AspectRatioFrameLayout.RESIZE_MODE_FIT
                },
                // The player outlives this view, so the view has to let go of
                // it - a PlayerView holding a released player is a surface leak
                // and a crash on the next attach.
                onRelease = { it.player = null },
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (isBuffering || isAdvancing) {
            LoadingIndicator(modifier = Modifier.align(Alignment.Center))
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f))) {
                TopChrome(
                    title = current.title,
                    subtitle = current.subtitle,
                    onClose = { viewModel.close() },
                    modifier = Modifier.align(Alignment.TopStart),
                )

                CentreControls(
                    isPlaying = isPlaying,
                    hasNext = nextEpisode != null,
                    onPlayPause = {
                        viewModel.togglePlayPause()
                        interaction++
                    },
                    onNext = {
                        viewModel.playNextEpisode()
                        interaction++
                    },
                    modifier = Modifier.align(Alignment.Center),
                )

                BottomChrome(
                    positionMs = if (isScrubbing) (scrubValue * durationMs).toLong() else positionMs,
                    durationMs = durationMs,
                    bufferedMs = bufferedMs,
                    isScrubbing = isScrubbing,
                    scrubValue = scrubValue,
                    hasAudioChoice = audioTracks.size > 1,
                    onScrub = {
                        isScrubbing = true
                        scrubValue = it
                    },
                    onScrubFinished = {
                        isScrubbing = false
                        viewModel.seekTo((scrubValue * durationMs).toLong())
                        interaction++
                    },
                    onOpenAudio = {
                        showAudioSheet = true
                        interaction++
                    },
                    onChangeSource = { viewModel.requestSourceChange() },
                    modifier = Modifier.align(Alignment.BottomStart),
                )
            }
        }

        // The up-next card sits above the chrome and outlives its auto-hide,
        // because it is time-limited and dismissing it by accident would start
        // the next episode without asking.
        countdown?.let { seconds ->
            UpNextCard(
                secondsLeft = seconds,
                label = nextEpisode?.let { episodeLabel(it) }.orEmpty(),
                onPlayNow = { viewModel.playNextEpisode() },
                onCancel = { viewModel.declineNextEpisode() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .systemBarsPadding()
                    .padding(end = 20.dp, bottom = 96.dp),
            )
        }

        problem?.let { kind ->
            ProblemCard(
                problem = kind,
                onChangeSource = { viewModel.requestSourceChange() },
                onDismiss = { viewModel.dismissProblem() },
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }

    if (showAudioSheet) {
        AudioTrackSheet(
            tracks = audioTracks,
            onSelect = {
                viewModel.selectAudioTrack(it)
                showAudioSheet = false
            },
            onDismiss = { showAudioSheet = false },
        )
    }
}

@Composable
private fun TopChrome(
    title: String,
    subtitle: String?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .systemBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.cd_back),
                tint = Color.White,
            )
        }
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun CentreControls(
    isPlaying: Boolean,
    hasNext: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Surface(
            onClick = onPlayPause,
            shape = RoundedCornerShape(percent = 50),
            color = Color.White.copy(alpha = 0.16f),
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.padding(16.dp).size(36.dp),
            )
        }
        if (hasNext) {
            Surface(
                onClick = onNext,
                shape = RoundedCornerShape(percent = 50),
                color = Color.White.copy(alpha = 0.10f),
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = stringResource(R.string.tv_next_episode),
                    tint = Color.White,
                    modifier = Modifier.padding(12.dp).size(26.dp),
                )
            }
        }
    }
}

@Composable
private fun BottomChrome(
    positionMs: Long,
    durationMs: Long,
    bufferedMs: Long,
    isScrubbing: Boolean,
    scrubValue: Float,
    hasAudioChoice: Boolean,
    onScrub: (Float) -> Unit,
    onScrubFinished: () -> Unit,
    onOpenAudio: () -> Unit,
    onChangeSource: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = when {
        isScrubbing -> scrubValue
        durationMs > 0 -> (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
        else -> 0f
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .systemBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Slider(
            value = progress,
            onValueChange = onScrub,
            onValueChangeFinished = onScrubFinished,
            enabled = durationMs > 0,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                // Deliberate literals: this control sits on arbitrary video
                // rather than a themed surface, the same exception the caption
                // scrim takes.
                inactiveTrackColor = Color.White.copy(alpha = 0.28f),
            ),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = formatPlaybackTime(positionMs) + " / " + formatPlaybackTime(durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.85f),
            )
            Spacer(Modifier.weight(1f))
            if (hasAudioChoice) {
                TextButton(onClick = onOpenAudio) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(17.dp),
                    )
                    Text(
                        text = stringResource(R.string.tv_audio_track),
                        color = Color.White,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
            TextButton(onClick = onChangeSource) {
                Icon(
                    imageVector = Icons.Rounded.SwapHoriz,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(17.dp),
                )
                Text(
                    text = stringResource(R.string.tv_change_source),
                    color = Color.White,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
    }
}

/**
 * The up-next card.
 *
 * Both actions are present from the first second: a countdown with only a
 * cancel is a countdown that takes the decision away, and one with only a play
 * is a countdown that did not need to exist.
 */
@Composable
private fun UpNextCard(
    secondsLeft: Int,
    label: String,
    onPlayNow: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.width(260.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.tv_next_episode_in, secondsLeft),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (label.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onPlayNow) { Text(stringResource(R.string.tv_play_now)) }
                TextButton(onClick = onCancel) { Text(stringResource(R.string.tv_dismiss)) }
            }
        }
    }
}

@Composable
private fun ProblemCard(
    problem: TvPlaybackProblem,
    onChangeSource: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.padding(24.dp).width(320.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = stringResource(
                    when (problem) {
                        TvPlaybackProblem.NO_SUPPORTED_AUDIO -> R.string.tv_audio_unsupported
                        TvPlaybackProblem.NO_NEXT_SOURCE -> R.string.tv_sources_none_found_title
                        TvPlaybackProblem.SOURCE_FAILED -> R.string.tv_playback_failed_title
                    }
                ),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (problem == TvPlaybackProblem.SOURCE_FAILED) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.tv_playback_failed_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onChangeSource) {
                    Text(stringResource(R.string.tv_change_source))
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.tv_got_it)) }
            }
        }
    }
}

/**
 * The audio picker.
 *
 * This is mechanism B of "dub" - the languages inside one file - and it is only
 * ever reached when there is more than one, because a picker with a single
 * entry is a control that cannot do anything. A track this device cannot decode
 * is listed and marked rather than hidden, so the reason a file is silent is
 * visible where the fix is.
 */
@Composable
private fun AudioTrackSheet(
    tracks: List<TvAudioTrack>,
    onSelect: (TvAudioTrack) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(
                text = stringResource(R.string.tv_audio_track),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))
            tracks.forEach { track ->
                Surface(
                    onClick = { onSelect(track) },
                    shape = RoundedCornerShape(14.dp),
                    color = if (track.isSelected) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        Text(
                            text = track.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (track.isSupported) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                            modifier = Modifier.weight(1f),
                        )
                        if (!track.isSupported) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private const val CONTROLS_TIMEOUT_MS = 3_500L

/** "1:42:07" or "4:12". Hoisted so the player and the up-next card agree. */
internal fun formatPlaybackTime(millis: Long): String {
    if (millis <= 0) return "0:00"
    val seconds = millis / 1000
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) String.format(Locale.US, "%d:%02d:%02d", hours, minutes, secs)
    else String.format(Locale.US, "%d:%02d", minutes, secs)
}

/** "S2E4  The Title", falling back to whichever half exists. */
internal fun episodeLabel(episode: com.ivor.ivormusic.data.tv.TvEpisode): String {
    val code = if (episode.season != null && episode.episodeNumber != null) {
        "S" + episode.season + "E" + episode.episodeNumber
    } else null
    return listOfNotNull(code, episode.displayTitle.takeIf { it.isNotBlank() })
        .joinToString("  ")
}
