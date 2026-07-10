package com.ivor.ivormusic.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.Morph
import androidx.media3.common.Player
import com.ivor.ivormusic.ui.components.LikeBurstIcon
import kotlinx.coroutines.delay

/**
 * Poster Player - the kinetic type style.
 *
 * The song title IS the screen: a stack of display-scale words on a flat
 * field, like a letterpress gig poster. Pure-flat contract: no gradients,
 * no shadows, no scrims.
 *
 * Signature moves:
 * - Progress is a hard-edged highlighter fill sweeping through the title
 *   glyphs themselves (played characters in primary, ahead in onSurface).
 * - Play state is an oversized punctuation mark: a MaterialShapes burst
 *   that slowly spins while playing and morphs to a full stop on pause.
 * - Dragging horizontally across the words scrubs; a single seek fires on
 *   release.
 * - Controls are summoned by tapping the field and hide themselves again;
 *   the resting screen is pure typography.
 *
 * See docs/PLAYER_STYLES_PURE_EXPRESSIVE_CONCEPTS.md section 1.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PosterPlayerSheetContent(
    viewModel: PlayerViewModel,
    ambientBackground: Boolean = true,
    onCollapse: () -> Unit,
    onLoadMore: () -> Unit = {},
    onArtistClick: (String) -> Unit = {}
) {
    BackHandler(enabled = true) { onCollapse() }

    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isBuffering by viewModel.isBuffering.collectAsState()
    val playWhenReady by viewModel.playWhenReady.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val shuffleModeEnabled by viewModel.shuffleModeEnabled.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val currentQueue by viewModel.currentQueue.collectAsState()
    val isFavorite by viewModel.isCurrentSongLiked.collectAsState()
    val lyricsResult by viewModel.lyricsResult.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val localPlaylists by viewModel.localPlaylists.collectAsState()

    var showQueue by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var showAddToPlaylist by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }

    // Poster palette: quiet field, ink for unplayed type, primary for the
    // sweep and every control. Roles only.
    val field = MaterialTheme.colorScheme.surfaceContainerLowest
    val ink = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary
    val onAccent = MaterialTheme.colorScheme.onPrimary

    // Summoned controls: any tap on the field brings them back; they slip
    // away on their own while music plays.
    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying) {
            delay(5000)
            controlsVisible = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(field)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { controlsVisible = true }
    ) {
        Crossfade(targetState = showQueue, label = "PosterQueueTransition") { queueVisible ->
            if (queueVisible) {
                EditorialQueueView(
                    queue = currentQueue,
                    currentSong = currentSong,
                    onSongClick = { song -> viewModel.skipToSong(song) },
                    onRemoveSong = { index -> viewModel.removeQueueItem(index) },
                    onLoadMore = onLoadMore,
                    isLoadingMore = isLoadingMore,
                    onCollapse = onCollapse,
                    onBackToPlayer = { showQueue = false },
                    field = field,
                    accent = ink
                )
            } else {
                Column(modifier = Modifier.fillMaxSize()) {

                    // ========== TOP BAR ==========
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        EditorialCircleButton(
                            onClick = onCollapse,
                            accent = accent,
                            field = onAccent,
                            size = 44.dp
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowDown, "Collapse",
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            EditorialCircleButton(
                                onClick = { showAddToPlaylist = true },
                                accent = accent,
                                field = onAccent,
                                size = 44.dp
                            ) {
                                Icon(
                                    Icons.Rounded.PlaylistAdd, "Add to Playlist",
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            EditorialCircleButton(
                                onClick = { showQueue = true },
                                accent = accent,
                                field = onAccent,
                                size = 44.dp
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.QueueMusic, "Queue",
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }

                    // ========== THE POSTER (title stack or lyrics) ==========
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        Crossfade(targetState = showLyrics, label = "PosterLyrics") { lyricsVisible ->
                            if (lyricsVisible) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 24.dp)
                                ) {
                                    SyncedLyricsView(
                                        lyricsResult = lyricsResult,
                                        currentPositionMs = progress,
                                        onSeekTo = { viewModel.seekTo(it) },
                                        ambientBackground = false,
                                        primaryColor = accent,
                                        onSurfaceColor = ink,
                                        onSurfaceVariantColor = ink.copy(alpha = 0.6f)
                                    )
                                }
                            } else {
                                PosterTitleStack(
                                    title = currentSong?.title
                                        ?.takeIf { !it.startsWith("Unknown") } ?: "Untitled",
                                    progress = progress,
                                    duration = duration,
                                    onSeekTo = { viewModel.seekTo(it) },
                                    ink = ink,
                                    accent = accent
                                )
                            }
                        }
                    }

                    // ========== SIGNATURE ROW: glyph + artist + times ==========
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PosterPunctuationGlyph(
                            isPlaying = isPlaying,
                            isBuffering = isBuffering && playWhenReady && !isPlaying,
                            onTap = { viewModel.togglePlayPause() },
                            accent = accent,
                            onAccent = onAccent
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            val artistName = currentSong?.artist
                                ?.takeIf { !it.startsWith("Unknown") } ?: "Unknown Artist"
                            Text(
                                text = artistName.uppercase(),
                                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 2.sp),
                                color = ink.copy(alpha = 0.8f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(enabled = artistName != "Unknown Artist") {
                                        onArtistClick(artistName)
                                    }
                                    .padding(vertical = 2.dp)
                            )
                            Text(
                                text = "${formatEditorialTime(progress)} of ${formatEditorialTime(duration)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = ink.copy(alpha = 0.55f)
                            )
                        }
                    }

                    // ========== SUMMONED CONTROL DECK ==========
                    AnimatedVisibility(
                        visible = controlsVisible,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                                .padding(top = 8.dp, bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(
                                10.dp,
                                Alignment.CenterHorizontally
                            ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            EditorialCircleButton(
                                onClick = { viewModel.skipToPrevious() },
                                accent = accent,
                                field = onAccent,
                                size = 56.dp
                            ) {
                                Icon(
                                    Icons.Default.SkipPrevious, "Previous",
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            EditorialCircleButton(
                                onClick = { viewModel.skipToNext() },
                                accent = accent,
                                field = onAccent,
                                size = 56.dp
                            ) {
                                Icon(
                                    Icons.Default.SkipNext, "Next",
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            EditorialChip(
                                checked = isFavorite,
                                onClick = { viewModel.toggleCurrentSongLike() },
                                accent = accent,
                                field = onAccent
                            ) {
                                LikeBurstIcon(isFavorite = isFavorite, iconSize = 20.dp)
                            }
                            EditorialChip(
                                checked = shuffleModeEnabled,
                                onClick = { viewModel.toggleShuffle() },
                                accent = accent,
                                field = onAccent
                            ) {
                                Icon(Icons.Default.Shuffle, "Shuffle", modifier = Modifier.size(20.dp))
                            }
                            EditorialChip(
                                checked = repeatMode != Player.REPEAT_MODE_OFF,
                                onClick = { viewModel.toggleRepeat() },
                                accent = accent,
                                field = onAccent
                            ) {
                                Icon(
                                    if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne
                                    else Icons.Default.Repeat,
                                    "Repeat",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            EditorialChip(
                                checked = showLyrics,
                                onClick = { showLyrics = !showLyrics },
                                accent = accent,
                                field = onAccent
                            ) {
                                Icon(Icons.Rounded.Lyrics, "Lyrics", modifier = Modifier.size(20.dp))
                            }
                        }
                    }

                    Spacer(
                        modifier = Modifier
                            .navigationBarsPadding()
                            .height(8.dp)
                    )
                }
            }
        }
    }

    if (showAddToPlaylist) {
        AddToPlaylistSheet(
            playlists = localPlaylists,
            onPlaylistClick = { playlist ->
                viewModel.addToPlaylist(playlist.id)
                showAddToPlaylist = false
            },
            onCreateNewClick = { name, desc ->
                viewModel.createPlaylist(name, desc)
                showAddToPlaylist = false
            },
            onDismissRequest = { showAddToPlaylist = false }
        )
    }
}

/**
 * The word stack: each word of the title on its own display-scale line.
 * Playback progress sweeps a hard-edged accent fill through the glyphs,
 * character-weighted across the words. Dragging horizontally anywhere on
 * the stack scrubs; one seek fires on release.
 */
@Composable
private fun PosterTitleStack(
    title: String,
    progress: Long,
    duration: Long,
    onSeekTo: (Long) -> Unit,
    ink: Color,
    accent: Color
) {
    val styleWheel = LocalPlayerStyleWheelController.current
    val allWords = remember(title) { title.trim().split(Regex("\\s+")).filter { it.isNotBlank() } }
    val words = remember(allWords) {
        if (allWords.size > 5) allWords.take(4) + "…" else allWords.ifEmpty { listOf("Untitled") }
    }
    val charTotals = remember(words) {
        val counts = words.map { it.length.coerceAtLeast(1) }
        val total = counts.sum().toFloat()
        var running = 0
        counts.map { count ->
            val start = running / total
            running += count
            start to running / total
        }
    }

    var scrubFraction by remember { mutableStateOf<Float?>(null) }
    var stackWidthPx by remember { mutableFloatStateOf(1f) }
    // pointerInput captures its lambda once per key; read the live values
    // through rememberUpdatedState so a drag starts from the real position.
    val liveProgress by rememberUpdatedState(progress)
    val liveDuration by rememberUpdatedState(duration)
    val playedFraction = scrubFraction
        ?: if (duration > 0) (progress.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .onSizeChanged { stackWidthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        scrubFraction = if (liveDuration > 0) {
                            (liveProgress.toFloat() / liveDuration.toFloat()).coerceIn(0f, 1f)
                        } else 0f
                    },
                    onDragEnd = {
                        scrubFraction?.let { onSeekTo((it * liveDuration).toLong()) }
                        scrubFraction = null
                    },
                    onDragCancel = { scrubFraction = null },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        scrubFraction = ((scrubFraction ?: 0f) + dragAmount / stackWidthPx)
                            .coerceIn(0f, 1f)
                    }
                )
            }
            // Last in the chain: processes the pointer stream first, so the
            // post-long-press hold-drag is consumed before the scrub
            // detector can see it.
            .styleWheelHold(styleWheel),
        verticalArrangement = Arrangement.Center
    ) {
        val wordStyle = when {
            words.maxOf { it.length } <= 8 -> MaterialTheme.typography.displayLarge
            words.maxOf { it.length } <= 13 -> MaterialTheme.typography.displayMedium
            else -> MaterialTheme.typography.displaySmall
        }.copy(fontWeight = FontWeight.Black)

        words.forEachIndexed { index, word ->
            val (start, end) = charTotals[index]
            val wordFraction = when {
                playedFraction <= start -> 0f
                playedFraction >= end -> 1f
                else -> (playedFraction - start) / (end - start)
            }
            // Base ink word with a hard-clipped accent overlay: the
            // highlighter sweep. Animate only the smoothly-progressing
            // fraction, no crossfade.
            val animatedWordFraction by animateFloatAsState(
                targetValue = wordFraction,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                label = "PosterWordFill$index"
            )
            Box {
                Text(
                    text = word,
                    style = wordStyle,
                    color = ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = word,
                    style = wordStyle,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.drawWithContent {
                        clipRect(right = size.width * animatedWordFraction) {
                            this@drawWithContent.drawContent()
                        }
                    }
                )
            }
        }
    }
}

/**
 * The oversized punctuation mark: a burst that slowly spins while music
 * plays and morphs into a full stop when paused. Tapping it toggles
 * playback - the shape is the play/pause control and its own feedback.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PosterPunctuationGlyph(
    isPlaying: Boolean,
    isBuffering: Boolean,
    onTap: () -> Unit,
    accent: Color,
    onAccent: Color
) {
    val burstToDot = remember { Morph(MaterialShapes.SoftBurst, MaterialShapes.Circle) }
    val morphProgress by animateFloatAsState(
        targetValue = if (isPlaying) 0f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "PosterGlyphMorph"
    )
    val glyphShape = remember(morphProgress) { EditorialMorphShape(burstToDot, morphProgress) }

    val spin = rememberInfiniteTransition(label = "PosterGlyphSpin")
    val spinAngle by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "PosterGlyphAngle"
    )

    Box(
        modifier = Modifier
            .size(64.dp)
            .graphicsLayer { rotationZ = if (isPlaying) spinAngle else 0f }
            .clip(glyphShape)
            .background(accent)
            .clickable { onTap() },
        contentAlignment = Alignment.Center
    ) {
        if (isBuffering) {
            LoadingIndicator(
                modifier = Modifier.size(28.dp),
                color = onAccent,
                polygons = listOf(
                    MaterialShapes.SoftBurst,
                    MaterialShapes.Cookie9Sided,
                    MaterialShapes.Pill,
                    MaterialShapes.Sunny
                )
            )
        }
    }
}
