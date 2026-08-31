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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.toArgb
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
import com.ivor.ivormusic.data.tv.TvSubtitleTrack
import com.ivor.ivormusic.ui.video.ExpressivePlayPauseButton
import com.ivor.ivormusic.ui.video.PlayerGestureSurface
import com.ivor.ivormusic.ui.video.PlayerSeekBar
import com.ivor.ivormusic.ui.video.QueueSkipButton
import com.ivor.ivormusic.ui.video.CaptionOverlay
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
fun TvPlayerScreen(
    viewModel: TvPlayerViewModel,
    onOpenExtensions: () -> Unit = {},
) {
    val playback by viewModel.playback.collectAsState()
    val current = playback ?: return

    // The source sheet is hosted here rather than on the detail page.
    // [scar] It used to live there, which meant "change source" had to call
    // close() first - the sheet is inside the NavHost and this player is an
    // overlay above it, so it would otherwise have opened underneath the video.
    // Tearing the player down to change quality lost the exact position (the
    // progress store only checkpoints every 15 seconds) and dropped the viewer
    // back to the detail page mid-film. Hosting it here lets switchSource()
    // swap the file in place at the current position instead.
    val sourcesViewModel: TvSourcesViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    var showSourceSheet by remember { mutableStateOf(false) }

    val sheetSources by sourcesViewModel.visible.collectAsState()
    val sheetLoading by sourcesViewModel.isLoading.collectAsState()
    val sheetLoaded by sourcesViewModel.loaded.collectAsState()
    val sheetAutoPick by sourcesViewModel.autoPick.collectAsState()
    val sheetFacets by sourcesViewModel.facets.collectAsState()
    val sheetFilter by sourcesViewModel.filter.collectAsState()
    val sheetTotal by sourcesViewModel.totalCount.collectAsState()
    val sheetFailedAddons by sourcesViewModel.failedAddons.collectAsState()

    val isPlaying by viewModel.isPlaying.collectAsState()
    val isBuffering by viewModel.isBuffering.collectAsState()
    val positionMs by viewModel.positionMs.collectAsState()
    val durationMs by viewModel.durationMs.collectAsState()
    val bufferedMs by viewModel.bufferedMs.collectAsState()
    val audioTracks by viewModel.audioTracks.collectAsState()
    val subtitleTracks by viewModel.subtitleTracks.collectAsState()
    val selectedSubtitle by viewModel.selectedSubtitle.collectAsState()
    val embeddedSubtitleTracks by viewModel.embeddedSubtitleTracks.collectAsState()
    val selectedEmbeddedSubtitle by viewModel.selectedEmbeddedSubtitle.collectAsState()
    val subtitleCues by viewModel.subtitleCues.collectAsState()
    val isSubtitlesLoading by viewModel.isSubtitlesLoading.collectAsState()
    val subtitleLoadFailed by viewModel.subtitleLoadFailed.collectAsState()
    val captionTextSize by viewModel.captionTextSize.collectAsState()
    val captionTextColor by viewModel.captionTextColor.collectAsState()
    val captionBackground by viewModel.captionBackground.collectAsState()
    val problem by viewModel.problem.collectAsState()
    val nextEpisode by viewModel.nextEpisode.collectAsState()
    val countdown by viewModel.autoplayCountdown.collectAsState()
    val isAdvancing by viewModel.isAdvancing.collectAsState()

    val context = LocalContext.current
    val activity = context as? Activity
    val videoBackground = MaterialTheme.colorScheme.scrim.toArgb()

    var controlsVisible by remember { mutableStateOf(true) }
    // Owned by PlayerSeekBar and reported back, because the auto-hide timer
    // must not fire while a drag is in progress.
    var isScrubbing by remember { mutableStateOf(false) }
    var showAudioSheet by remember { mutableStateOf(false) }
    var showSubtitleSheet by remember { mutableStateOf(false) }
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
            .background(MaterialTheme.colorScheme.scrim)
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
                        setShutterBackgroundColor(videoBackground)
                    }
                },
                update = { view ->
                    view.setShutterBackgroundColor(videoBackground)
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

        CaptionOverlay(
            cues = subtitleCues,
            player = viewModel.exoPlayer(),
            bottomPadding = if (controlsVisible) 104.dp else 28.dp,
            compact = false,
            textSize = captionTextSize,
            textColor = captionTextColor,
            background = captionBackground,
            modifier = Modifier.fillMaxSize(),
        )

        // Both this and the centre transport are centred, so only one may draw
        // at a time. Ordinary buffering is reported by the play button itself
        // while the controls are up, and only needs its own indicator once they
        // have hidden; advancing to the next episode always gets the card,
        // because it is a multi-second fan-out that has to say what it is doing.
        if (isAdvancing || (isBuffering && !controlsVisible)) {
            PlaybackLoadingStatus(
                isAdvancing = isAdvancing,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            // No flat dim over the whole frame: the two gradients below darken
            // exactly the bands the text sits in, which is what the video
            // player does and what keeps the picture itself untouched.
            Box(Modifier.fillMaxSize()) {
                TopChrome(
                    title = current.title,
                    subtitle = current.subtitle,
                    onClose = { viewModel.close() },
                    modifier = Modifier.align(Alignment.TopStart),
                )

                // Stands down while the next episode is being resolved: the
                // loading card occupies the same centre slot, and a transport
                // for a file that is not chosen yet has nothing to command.
                if (!isAdvancing) CentreControls(
                    isPlaying = isPlaying,
                    isBuffering = isBuffering,
                    hasNext = nextEpisode != null,
                    onPlayPause = {
                        viewModel.togglePlayPause()
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
                    onNext = {
                        viewModel.playNextEpisode()
                        interaction++
                    },
                    modifier = Modifier.align(Alignment.Center),
                )

                BottomChrome(
                    streamId = current.streamId,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    bufferedMs = bufferedMs,
                    hasAudioChoice = audioTracks.size > 1,
                    hasSubtitles = subtitleTracks.isNotEmpty() ||
                        embeddedSubtitleTracks.isNotEmpty() || isSubtitlesLoading,
                    selectedSubtitle = selectedSubtitle,
                    selectedEmbeddedSubtitle = selectedEmbeddedSubtitle,
                    onSeekFraction = { fraction ->
                        viewModel.seekTo((fraction * durationMs).toLong())
                        interaction++
                    },
                    onScrubbingChanged = { isScrubbing = it },
                    onOpenAudio = {
                        showAudioSheet = true
                        interaction++
                    },
                    onOpenSubtitles = {
                        showSubtitleSheet = true
                        interaction++
                    },
                    onChangeSource = {
                        showSourceSheet = true
                        interaction++
                        // Exhaustive: the viewer opened this to see the field.
                        // It takes a few seconds and the sheet shows its own
                        // spinner while it fills, rather than blocking playback
                        // - the film keeps running underneath the whole time.
                        sourcesViewModel.load(
                            current.item.type,
                            current.streamId,
                            exhaustive = true,
                        )
                    },
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
                onRetry = { viewModel.retryPlayback() },
                onChangeSource = {
                    showSourceSheet = true
                    sourcesViewModel.load(
                        current.item.type,
                        current.streamId,
                        exhaustive = true,
                    )
                },
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


    if (showSourceSheet) {
        TvSourceSheet(
            title = listOfNotNull(
                current.title,
                current.subtitle?.takeIf { it.isNotBlank() },
            ).joinToString("  "),
            hasStreamSource = sourcesViewModel.hasStreamSource(),
            isLoading = sheetLoading,
            loaded = sheetLoaded,
            sources = sheetSources,
            totalCount = sheetTotal,
            autoPick = sheetAutoPick,
            facets = sheetFacets,
            filter = sheetFilter,
            failedAddons = sheetFailedAddons,
            // The whole point of hosting this here: swap the file at the
            // current position, with no teardown and no return to the detail
            // page. The film does not stop.
            onPlay = { source ->
                showSourceSheet = false
                viewModel.switchSource(source)
            },
            onOpenExternal = { link ->
                runCatching {
                    context.startActivity(
                        android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(link),
                        )
                    )
                }
            },
            onSetResolution = sourcesViewModel::setResolution,
            onSetLanguage = sourcesViewModel::setLanguage,
            onSetSourceQuality = sourcesViewModel::setSourceQuality,
            onSetCachedOnly = sourcesViewModel::setCachedOnly,
            onSetDub = sourcesViewModel::setDub,
            onClearFilters = sourcesViewModel::clearFilters,
            onRetry = {
                sourcesViewModel.load(
                    current.item.type,
                    current.streamId,
                    force = true,
                    exhaustive = true,
                )
            },
            onBrowseAddons = {
                showSourceSheet = false
                onOpenExtensions()
            },
            onDismiss = { showSourceSheet = false },
        )
    }

    if (showSubtitleSheet) {
        SubtitleTrackSheet(
            tracks = subtitleTracks,
            selected = selectedSubtitle,
            embeddedTracks = embeddedSubtitleTracks,
            selectedEmbedded = selectedEmbeddedSubtitle,
            isLoading = isSubtitlesLoading,
            loadFailed = subtitleLoadFailed,
            onSelect = { viewModel.selectSubtitle(it) },
            onSelectEmbedded = viewModel::selectEmbeddedSubtitle,
            onDisable = viewModel::disableSubtitles,
            onDismiss = { showSubtitleSheet = false },
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
            // Darkens only the band the title sits in. A title over a bright
            // frame is unreadable without it, and dimming the whole picture to
            // fix one line of text is the wrong trade.
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.scrim.copy(alpha = 0.75f),
                        MaterialTheme.colorScheme.scrim.copy(alpha = 0f),
                    )
                )
            )
            .systemBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.cd_back),
                tint = MaterialTheme.colorScheme.inverseOnSurface,
            )
        }
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * The centre transport, in the video player's vocabulary.
 *
 * Seek buttons flank play/pause even though double-tap already seeks, because
 * a gesture nobody is told about is a feature only the people who wrote it
 * have. The buttons are the discoverable path and the gesture is the fast one;
 * both call the same [TvPlayerViewModel.SEEK_STEP_MS].
 *
 * Next-episode appears only when there is one, so a film shows three controls
 * rather than a permanently dead fourth.
 */
@Composable
private fun CentreControls(
    isPlaying: Boolean,
    isBuffering: Boolean,
    hasNext: Boolean,
    onPlayPause: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        QueueSkipButton(
            icon = Icons.Rounded.Replay10,
            contentDescription = stringResource(R.string.cd_seek_backward),
            enabled = true,
            onClick = onSeekBackward,
        )
        ExpressivePlayPauseButton(
            isPlaying = isPlaying,
            isBuffering = isBuffering,
            onClick = onPlayPause,
        )
        QueueSkipButton(
            icon = Icons.Rounded.Forward10,
            contentDescription = stringResource(R.string.cd_seek_forward),
            enabled = true,
            onClick = onSeekForward,
        )
        if (hasNext) {
            QueueSkipButton(
                icon = Icons.Rounded.SkipNext,
                contentDescription = stringResource(R.string.tv_next_episode),
                enabled = true,
                onClick = onNext,
            )
        }
    }
}

/**
 * Seek bar and the track controls under it.
 *
 * The seek bar is the video player's [PlayerSeekBar], shared rather than
 * reimplemented. That is not only tidiness: it draws the buffered range *on*
 * the track instead of as a second bar underneath it, and it holds the thumb at
 * the committed position until the twice-a-second position poll catches up, so
 * releasing a drag no longer snaps backwards for half a second. Both were
 * wrong here and right there.
 *
 * [streamId] keys its internal scrub state, so moving to the next episode
 * starts a fresh bar rather than inheriting the previous one's drag.
 */
@Composable
private fun BottomChrome(
    streamId: String,
    positionMs: Long,
    durationMs: Long,
    bufferedMs: Long,
    hasAudioChoice: Boolean,
    hasSubtitles: Boolean,
    selectedSubtitle: TvSubtitleTrack?,
    selectedEmbeddedSubtitle: TvEmbeddedSubtitleTrack?,
    onSeekFraction: (Float) -> Unit,
    onScrubbingChanged: (Boolean) -> Unit,
    onOpenAudio: () -> Unit,
    onOpenSubtitles: () -> Unit,
    onChangeSource: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress =
        if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val buffered =
        if (durationMs > 0) (bufferedMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.scrim.copy(alpha = 0f),
                        MaterialTheme.colorScheme.scrim.copy(alpha = 0.85f),
                    )
                )
            )
            .systemBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = formatPlaybackTime(positionMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.inverseOnSurface,
            )
            PlayerSeekBar(
                mediaId = streamId,
                progress = progress,
                bufferedProgress = buffered,
                onSeek = onSeekFraction,
                durationMs = durationMs,
                // There is no storyboard for an addon's file - the protocol
                // carries no thumbnail track - so the preview is switched off
                // rather than shown empty.
                showSeekPreview = false,
                onScrubbingChanged = onScrubbingChanged,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = formatPlaybackTime(durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.inverseOnSurface,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Spacer(Modifier.weight(1f))
            if (hasAudioChoice) {
                ChromeActionButton(
                    icon = Icons.Rounded.Tune,
                    label = stringResource(R.string.tv_audio_track),
                    onClick = onOpenAudio,
                )
            }
            if (hasSubtitles) {
                ChromeActionButton(
                    icon = Icons.Rounded.Subtitles,
                    // Shows the chosen track rather than the word "Subtitles",
                    // so the current state is readable without opening a sheet.
                    label = selectedSubtitle?.lang?.takeIf { it.isNotBlank() }
                        ?.let(::languageLabel)
                        ?: selectedEmbeddedSubtitle?.label
                        ?: stringResource(R.string.tv_subtitles),
                    onClick = onOpenSubtitles,
                )
            }
            ChromeActionButton(
                icon = Icons.Rounded.SwapHoriz,
                label = stringResource(R.string.tv_change_source),
                onClick = onChangeSource,
            )
        }
    }
}

/**
 * One control in the bottom action row.
 *
 * A filled tonal pill rather than the bare `TextButton` these used to be:
 * over an arbitrary video frame an unbounded label does not read as something
 * tappable, and these three are the only way to reach audio, subtitles and a
 * different release.
 */
@Composable
private fun ChromeActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    FilledTonalButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 8.dp),
        )
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
    onRetry: () -> Unit,
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
                        TvPlaybackProblem.NETWORK_FAILED -> R.string.tv_playback_network_title
                        TvPlaybackProblem.FORMAT_UNSUPPORTED -> R.string.tv_playback_format_title
                        TvPlaybackProblem.DECODER_FAILED -> R.string.tv_playback_decoder_title
                        TvPlaybackProblem.SOURCE_FAILED -> R.string.tv_playback_failed_title
                    }
                ),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            val body = when (problem) {
                TvPlaybackProblem.SOURCE_FAILED -> R.string.tv_playback_failed_body
                TvPlaybackProblem.NETWORK_FAILED -> R.string.tv_playback_network_body
                TvPlaybackProblem.FORMAT_UNSUPPORTED -> R.string.tv_playback_format_body
                TvPlaybackProblem.DECODER_FAILED -> R.string.tv_playback_decoder_body
                TvPlaybackProblem.NO_SUPPORTED_AUDIO,
                TvPlaybackProblem.NO_NEXT_SOURCE -> null
            }
            if (body != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(16.dp))
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (
                    problem != TvPlaybackProblem.NO_SUPPORTED_AUDIO &&
                    problem != TvPlaybackProblem.NO_NEXT_SOURCE
                ) {
                    Button(onClick = onRetry) {
                        Text(stringResource(R.string.tv_retry))
                    }
                }
                FilledTonalButton(onClick = onChangeSource) {
                    Text(stringResource(R.string.tv_change_source))
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.tv_got_it)) }
            }
        }
    }
}

@Composable
private fun PlaybackLoadingStatus(
    isAdvancing: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier,
    ) {
        LoadingIndicator()
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Text(
                text = stringResource(
                    if (isAdvancing) R.string.tv_loading_next_source
                    else R.string.tv_playback_buffering
                ),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            )
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

@Composable
private fun SubtitleTrackSheet(
    tracks: List<TvSubtitleTrack>,
    selected: TvSubtitleTrack?,
    embeddedTracks: List<TvEmbeddedSubtitleTrack>,
    selectedEmbedded: TvEmbeddedSubtitleTrack?,
    isLoading: Boolean,
    loadFailed: Boolean,
    onSelect: (TvSubtitleTrack) -> Unit,
    onSelectEmbedded: (TvEmbeddedSubtitleTrack) -> Unit,
    onDisable: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val groups = remember(tracks) {
        tracks.groupBy { it.lang.ifBlank { "und" } }
            .toList()
            .sortedBy { (language, _) -> languageLabel(language) }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)
        ) {
            Text(
                text = stringResource(R.string.tv_subtitles),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.tv_subtitles_style_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 3.dp, bottom = 10.dp),
            )
            if (isLoading && tracks.isEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(vertical = 16.dp),
                ) {
                    LoadingIndicator(modifier = Modifier.size(22.dp))
                    Text(stringResource(R.string.tv_subtitles_loading))
                }
            }
            if (loadFailed) {
                Text(
                    stringResource(R.string.tv_subtitles_failed),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item(key = "off") {
                    SubtitleRow(
                        title = stringResource(R.string.tv_subtitles_off),
                        subtitle = null,
                        selected = selected == null && selectedEmbedded == null,
                        onClick = {
                            onDisable()
                            onDismiss()
                        },
                    )
                }
                if (embeddedTracks.isNotEmpty()) {
                    item(key = "embedded-header") {
                        Text(
                            stringResource(R.string.tv_subtitles_embedded),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                        )
                    }
                    items(
                        embeddedTracks,
                        key = { "embedded/${it.groupIndex}/${it.trackIndex}" },
                    ) { track ->
                        SubtitleRow(
                            title = track.label,
                            subtitle = if (track.isSupported) {
                                stringResource(R.string.tv_subtitles_embedded_in_file)
                            } else {
                                stringResource(R.string.tv_subtitles_unsupported)
                            },
                            selected = track == selectedEmbedded,
                            enabled = track.isSupported,
                            onClick = {
                                onSelectEmbedded(track)
                                onDismiss()
                            },
                        )
                    }
                }
                for ((language, languageTracks) in groups) {
                    item(key = "language/$language") {
                        Text(
                            languageLabel(language),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                        )
                    }
                    items(languageTracks, key = { it.url }) { track ->
                        SubtitleRow(
                            title = track.name?.takeIf { it.isNotBlank() }
                                ?: languageLabel(track.lang),
                            subtitle = track.addonName,
                            selected = track == selected,
                            onClick = {
                                onSelect(track)
                                onDismiss()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SubtitleRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(
                    alpha = if (enabled) 1f else 0.45f
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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
