package com.ivor.ivormusic.ui.components
import androidx.compose.ui.res.stringResource
import com.ivor.ivormusic.R

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.toPath
import com.ivor.ivormusic.data.MiniCoverMotion
import com.ivor.ivormusic.data.MiniCoverTap
import com.ivor.ivormusic.data.MiniPlayerButton
import com.ivor.ivormusic.data.MiniPlayerColor
import com.ivor.ivormusic.data.MiniPlayerCustomization
import com.ivor.ivormusic.data.MiniSecondLine
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.ui.player.EditorialPolygonShape
import com.ivor.ivormusic.ui.player.rememberPlayerHaptics
import kotlinx.coroutines.isActive

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MiniPlayerContent(
    currentSong: Song,
    isPlaying: Boolean,
    isBuffering: Boolean,
    playWhenReady: Boolean,
    progress: Float,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onClick: () -> Unit,
    /** What holding the pill does, or null when the user has chosen nothing. */
    onLongClick: (() -> Unit)?,
    /**
     * The pill's sideways swipe. The pill itself stays put; the song inside it
     * slides, with [previousSong] or [nextSong] peeking in from the edge.
     */
    skipState: MiniSkipState,
    previousSong: Song?,
    nextSong: Song?,
    /**
     * Opacity of everything but the cover - the title, the artist and the
     * buttons - which fade as the pill closes into a bubble round the cover.
     * Read in a layer, since it follows the page's scroll.
     */
    detailAlpha: () -> Float = { 1f },
    /** The track's length, so the progress can be carried between the once-a-second samples. */
    durationMs: Long = 0L,
    /** The user's choices for the pill: which buttons, what the cover does, its colours. */
    customization: MiniPlayerCustomization,
    /**
     * The optional buttons, which the host answers: previous, like, shuffle,
     * repeat and close. Play/pause and next keep their own callbacks above.
     */
    onButtonClick: (MiniPlayerButton) -> Unit,
    isLiked: Boolean,
    shuffleOn: Boolean,
    /** A `Player.REPEAT_MODE_*` constant. */
    repeatMode: Int,
) {
    val playerHaptics = rememberPlayerHaptics()
    // What sits on the pill. It follows the pill's own colour: the container
    // is painted by ExpandablePlayer from the same setting.
    val contentColor = if (customization.color == MiniPlayerColor.NEUTRAL) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }
    // Which progress readouts to draw. A setting, read through the pill's own
    // preferences: the flow follows the stored value, so the Settings row
    // changes the pill behind it at once.
    val context = androidx.compose.ui.platform.LocalContext.current
    val progressStyle by remember(context) {
        com.ivor.ivormusic.data.ThemePreferences(context)
    }.miniPlayerProgress.collectAsState()
    // The sampled position, extrapolated every frame at the playing speed
    // (see rememberSmoothProgress). Read only through this lambda, inside
    // draw blocks, so sixty updates a second redraw the pill's fill and the
    // cover's outline without recomposing the pill.
    val smoothProgress = com.ivor.ivormusic.ui.player.rememberSmoothProgress(
        positionMs = (progress * durationMs).toLong(),
        durationMs = durationMs,
        isPlaying = isPlaying
    )
    val currentSampled = rememberUpdatedState(progress)
    val progressNow: () -> Float = {
        if (durationMs > 0L) smoothProgress.value else currentSampled.value
    }
    val kodaHaptics = com.ivor.ivormusic.util.rememberKodaHaptics()
    // The line under the title. Time left only makes sense for the song that
    // is playing; a neighbour peeking in during a swipe shows its artist.
    val secondLineOf: (Song, Boolean) -> String = { song, playing ->
        when (customization.secondLine) {
            MiniSecondLine.ARTIST -> song.artist
            MiniSecondLine.ALBUM -> song.album.ifBlank { song.artist }
            MiniSecondLine.TIME_LEFT -> if (playing && durationMs > 0L) {
                val leftSeconds = ((durationMs * (1f - progress.coerceIn(0f, 1f))) / 1000f).toLong()
                "-%d:%02d".format(leftSeconds / 60, leftSeconds % 60)
            } else {
                song.artist
            }
        }
    }
    val playLabel = stringResource(R.string.cd_play)
    val pauseLabel = stringResource(R.string.cd_pause)

    // Toggling while a track is still resolving would call play() again rather
    // than cancelling the pending start (togglePlayPause keys off isPlaying),
    // so the artwork goes inert for exactly the window the play/pause button
    // is replaced by the loading indicator. A tap then falls through to the
    // pill's own onClick and expands, as it always did.
    //
    // With the cover set to open the player it is inert for good, and every
    // tap on it is the pill's.
    val artworkTogglesPlayback = !(isBuffering && playWhenReady) &&
        customization.coverTap == MiniCoverTap.PLAY_PAUSE

    // Transparent: the ExpandablePlayer container draws the pill background.
    // A second opaque surface here created a visible "pill behind a pill"
    // (its own shadow + tonal tint), and its drag handler swallowed the
    // swipe-up-to-expand gesture the container listens for.
    //
    // A combined click rather than Surface's own onClick, so the pill can
    // answer a long press too. The buttons and the cover are children with
    // clicks of their own and keep them.
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(50))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick?.let { action ->
                    {
                        kodaHaptics.longPress()
                        action()
                    }
                }
            ),
        color = Color.Transparent,
        shape = RoundedCornerShape(50) // Full pill shape
    ) {
        // Behind everything, clipped by the pill: a Surface stacks its
        // children, so this simply sits under the row.
        if (progressStyle.showsFill) {
            MiniProgressFill(progress = progressNow, isPlaying = isPlaying, detailAlpha = detailAlpha)
        }
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MiniSkipCarousel(
                state = skipState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    // Rounded like the pill's own end, so the song slides out
                    // under a curve rather than a square edge.
                    .clip(RoundedCornerShape(topStart = 26.dp, bottomStart = 26.dp)),
                previousLabel = stringResource(R.string.cd_previous),
                nextLabel = stringResource(R.string.cd_next),
                previous = previousSong,
                next = nextSong,
                neighbour = { song -> MiniSongIdentity(song, secondLineOf(song, false), contentColor, detailAlpha, isPlaying = false) },
            ) {
                MiniSongIdentity(currentSong, secondLineOf(currentSong, true), contentColor, detailAlpha, isPlaying = isPlaying) {
                    // Album Art with Circular Progress Ring - doubles as the
                    // play/pause target, so the most-hit part of the pill
                    // toggles playback instead of only expanding the player.
                    val artworkInteraction = remember { MutableInteractionSource() }
                    val artworkPressed by artworkInteraction.collectIsPressedAsState()
                    val artworkScale by animateFloatAsState(
                        targetValue = if (artworkPressed) 0.92f else 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        label = "miniArtworkScale"
                    )

                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = artworkInteraction,
                                indication = null,
                                enabled = artworkTogglesPlayback,
                                onClickLabel = if (isPlaying) pauseLabel else playLabel,
                                role = Role.Button,
                                onClick = {
                                    playerHaptics.playPause(!isPlaying)
                                    onPlayPauseClick()
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        // The cover and its progress ring, which is the
                        // cover's own outline. Only the cover springs on
                        // press - the ring is a progress readout and would
                        // read as a glitch if it scaled with it.
                        MiniPlayingArtwork(
                            song = currentSong,
                            isPlaying = isPlaying,
                            progress = progressNow,
                            motion = customization.coverMotion,
                            trackColor = contentColor.copy(alpha = 0.2f),
                            // With the fill alone chosen, the outline still
                            // comes in as the pill closes into its bubble:
                            // the fill has faded by then, and the bubble
                            // would otherwise show no progress at all.
                            outlineAlpha = if (progressStyle.showsOutline) {
                                { 1f }
                            } else {
                                { 1f - detailAlpha() }
                            },
                            coverModifier = Modifier.scale(artworkScale)
                        )
                    }
                }
            }

            val buttons = customization.orderedButtons
            val loading = isBuffering && playWhenReady
            if (buttons.isNotEmpty() || loading) {
                Spacer(modifier = Modifier.width(8.dp))
            }

            // The buttons fade together as the pill closes. They are already
            // outside the bubble's bounds by then, so only the fade needs
            // handling; nothing invisible can be pressed.
            Row(
                modifier = Modifier.graphicsLayer { alpha = detailAlpha() },
                // Three buttons sit closer, so the title keeps some room.
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(
                    if (buttons.size >= MiniPlayerCustomization.MAX_BUTTONS) 4.dp else 8.dp
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // With no play button chosen, a song still resolving has
                // nowhere to say so; the indicator stands alone for that window.
                if (loading && MiniPlayerButton.PLAY_PAUSE !in buttons) {
                    MiniLoadingIndicator(contentColor)
                }
                buttons.forEach { button ->
                    when (button) {
                        MiniPlayerButton.PLAY_PAUSE -> if (loading) {
                            MiniLoadingIndicator(contentColor)
                        } else {
                            FilledIconButton(
                                onClick = {
                                    // Same action as the artwork tap, so same feedback.
                                    playerHaptics.playPause(!isPlaying)
                                    onPlayPauseClick()
                                },
                                modifier = Modifier.size(44.dp),
                                shapes = IconButtonDefaults.shapes(), // Bouncy shape morphing
                                // The one filled control on the pill, in the strong accent.
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) pauseLabel else playLabel,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        // Everything else is a plain icon: a second filled
                        // circle beside play gave the pill two targets of
                        // equal weight.
                        MiniPlayerButton.NEXT -> MiniPlainButton(
                            icon = Icons.Default.SkipNext,
                            label = stringResource(R.string.cd_next),
                            tint = contentColor,
                            onClick = onNextClick
                        )
                        MiniPlayerButton.PREVIOUS -> MiniPlainButton(
                            icon = Icons.Default.SkipPrevious,
                            label = stringResource(R.string.cd_previous),
                            tint = contentColor,
                            onClick = { onButtonClick(button) }
                        )
                        MiniPlayerButton.LIKE -> MiniPlainButton(
                            icon = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            label = stringResource(R.string.cd_favorite),
                            tint = if (isLiked) MaterialTheme.colorScheme.primary else contentColor,
                            onClick = { onButtonClick(button) }
                        )
                        MiniPlayerButton.SHUFFLE -> MiniPlainButton(
                            icon = Icons.Default.Shuffle,
                            label = stringResource(R.string.cd_shuffle),
                            tint = if (shuffleOn) MaterialTheme.colorScheme.primary
                            else contentColor.copy(alpha = MINI_BUTTON_OFF_ALPHA),
                            onClick = { onButtonClick(button) }
                        )
                        MiniPlayerButton.REPEAT -> MiniPlainButton(
                            icon = if (repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE) {
                                Icons.Default.RepeatOne
                            } else {
                                Icons.Default.Repeat
                            },
                            label = stringResource(R.string.cd_repeat),
                            tint = if (repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                contentColor.copy(alpha = MINI_BUTTON_OFF_ALPHA)
                            },
                            onClick = { onButtonClick(button) }
                        )
                        MiniPlayerButton.CLOSE -> MiniPlainButton(
                            icon = Icons.Default.Close,
                            label = stringResource(R.string.cd_close),
                            tint = contentColor,
                            onClick = { onButtonClick(button) }
                        )
                    }
                }
            }
        }
    }
}

/** How strongly a mode button (shuffle, repeat) is drawn while its mode is off. */
private const val MINI_BUTTON_OFF_ALPHA = 0.6f

/** One of the pill's unfilled buttons. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MiniPlainButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
) {
    androidx.compose.material3.IconButton(
        onClick = onClick,
        modifier = Modifier.size(44.dp),
        shapes = IconButtonDefaults.shapes(),
        colors = IconButtonDefaults.iconButtonColors(contentColor = tint)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(24.dp)
        )
    }
}

/** What stands in the play button's place while a song is still resolving. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MiniLoadingIndicator(color: Color) {
    Box(
        modifier = Modifier.size(44.dp),
        contentAlignment = Alignment.Center
    ) {
        //Organic morphing loading with MaterialShapes
        LoadingIndicator(
            modifier = Modifier.size(24.dp),
            color = color,
            polygons = listOf(
                MaterialShapes.SoftBurst,
                MaterialShapes.Cookie9Sided,
                MaterialShapes.Pill,
                MaterialShapes.Sunny
            )
        )
    }
}

/**
 * One song as the pill shows it: artwork, then title over artist. The playing
 * song passes its own [artwork] (the wavy progress ring, the turning cover and
 * the play/pause target); a neighbour peeking in during a swipe gets the
 * resting cover in the same 52dp slot, so the two line up as one slides in
 * over the other.
 */
@Composable
private fun MiniSongIdentity(
    song: Song,
    /** The line under the title: the artist, the album or the time left. */
    subtitle: String,
    contentColor: Color,
    detailAlpha: () -> Float = { 1f },
    isPlaying: Boolean = false,
    artwork: @Composable () -> Unit = {
        Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.Center) {
            MiniSongArtwork(song)
        }
    },
) {
    Row(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        artwork()
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .graphicsLayer { alpha = detailAlpha() }
        ) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.titleMarquee(isPlaying)
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * The pill fills with the song: a deeper tone of the accent runs from the
 * start edge as far as the song has played, behind the title and the buttons,
 * and its leading edge is a wave that travels while the song plays and lies
 * straight when it is paused. [trial October 2026]
 *
 * The whole pill is the progress bar, so how far along the song is can be
 * read from across the room without a bar being drawn anywhere.
 *
 * Drawn in the draw phase from states: the wave's phase is written by a frame
 * loop and [progress] is read through an updated state, so a frame of this
 * redraws the fill and recomposes nothing. [detailAlpha] fades it out as the
 * pill closes into its bubble, where the cover's own outline shows progress.
 */
@Composable
private fun MiniProgressFill(progress: () -> Float, isPlaying: Boolean, detailAlpha: () -> Float) {
    val phase = remember { mutableFloatStateOf(0f) }
    // Eased in and out, so pausing settles the wave flat rather than freezing it.
    val amplitude = androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 450),
        label = "miniFillWave"
    )
    LaunchedEffect(isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (isActive) {
            withFrameNanos { now ->
                val seconds = (now - last) / 1_000_000_000f
                last = now
                phase.floatValue = (phase.floatValue + seconds * MINI_FILL_WAVE_SPEED) % (2f * Math.PI.toFloat())
            }
        }
    }
    val fillColor = MaterialTheme.colorScheme.primary.copy(alpha = MINI_FILL_STRENGTH)
    val path = remember { Path() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                val fraction = progress().coerceIn(0f, 1f)
                val strength = detailAlpha().coerceIn(0f, 1f)
                if (fraction <= 0f || strength <= 0f) return@drawBehind
                val edge = size.width * fraction
                val wave = MINI_FILL_WAVE_AMPLITUDE.toPx() * amplitude.value
                val wavelength = MINI_FILL_WAVELENGTH.toPx()
                path.rewind()
                path.moveTo(0f, 0f)
                // Down the leading edge in short steps, as a travelling sine.
                val steps = 16
                for (step in 0..steps) {
                    val y = size.height * step / steps
                    val x = edge + wave * kotlin.math.sin(
                        2f * Math.PI.toFloat() * y / wavelength + phase.floatValue
                    )
                    path.lineTo(x, y)
                }
                path.lineTo(0f, size.height)
                path.close()
                drawPath(path = path, color = fillColor, alpha = strength)
            }
    )
}

/** How strongly the played part of the pill is tinted with the accent. */
private const val MINI_FILL_STRENGTH = 0.26f

/** How far the fill's leading edge swings either side of the true position. */
private val MINI_FILL_WAVE_AMPLITUDE = 4.dp

/** The height of one wave on the leading edge. */
private val MINI_FILL_WAVELENGTH = 34.dp

/** How fast the wave travels, in radians a second. */
private const val MINI_FILL_WAVE_SPEED = 3.2f

/** The slot the playing cover and its outline share. */
private val MINI_ARTWORK_SLOT = 52.dp

/** The cover's size inside that slot: clear of the outline drawn round it. */
private val MINI_COVER_SIZE = 40.dp

/** The outline's stroke. */
private val MINI_OUTLINE_WIDTH = 3.dp

/** One turn of the playing cover every ten seconds: slow enough to read, plainly moving. */
private const val MINI_SPIN_DEGREES_PER_SECOND = 36f

/** How long the cover holds each shape before moving to the next. */
private const val MINI_SHAPE_HOLD_SECONDS = 1.6f

/** How long the move from one shape to the next takes. */
private const val MINI_SHAPE_MORPH_SECONDS = 0.9f

/**
 * The cover the pill is playing, and its progress: the cover spins like a
 * record and walks through a set of shapes while the song runs, and the
 * progress is drawn as that same shape's outline. Paused, all of it holds
 * still wherever it had got to.
 *
 * **The ring is the shape.** [judgement October 2026] A round ring round a
 * morphing cover hid the morph: at 40dp the eye reads the ring, and the ring
 * never changed. Drawn along the cover's own outline, the progress line is the
 * thing that turns into a clover and a flower.
 *
 * **Progress is revealed from twelve o'clock, not measured along the path.**
 * [scar October 2026] The first outline version trimmed the path to the played
 * fraction. A path's starting point is wherever its polygon happens to begin,
 * it differs from one shape to the next, and it turned with the cover - so the
 * played part leapt to a new place at every shape change and crawled round
 * the pill in between. The outline is now drawn whole, turning and morphing,
 * and the played colour is the part of it inside a wedge that opens clockwise
 * from the top and never moves: the fill behaves like any round progress ring
 * while the line it colours changes shape underneath.
 *
 * **One fixed geometry.** [scar] Each morph was also fitted to its own bounds,
 * which differ slightly between pairs of shapes, so the cover and the line
 * popped in size at every step. The Material shapes are normalised to a unit
 * square; both are mapped from that square and nothing is re-measured.
 *
 * **Everything moving lives in the draw phase.** The angle and the shape clock
 * are states written from a frame loop, and [progress] is read through an
 * updated state, all only inside `graphicsLayer` and draw blocks. A frame of
 * this redraws one 52dp slot and recomposes nothing - the pill around it is
 * composed by every screen in the app.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MiniPlayingArtwork(
    song: Song,
    isPlaying: Boolean,
    progress: () -> Float,
    /** Whether the cover turns, changes shape, both or neither. */
    motion: MiniCoverMotion,
    /** The unplayed part of the outline, a faint tone of what sits on the pill. */
    trackColor: Color,
    /** Opacity of the outline, track and played part alike. Read in the draw phase. */
    outlineAlpha: () -> Float = { 1f },
    coverModifier: Modifier = Modifier
) {
    // Each shape and the move to the one after it, the last back to the first.
    val morphs = remember {
        val shapes = listOf(
            MaterialShapes.Cookie9Sided,
            MaterialShapes.Clover4Leaf,
            MaterialShapes.Cookie6Sided,
            MaterialShapes.Flower,
            MaterialShapes.Cookie4Sided
        ).map(::fittedToCircle)
        shapes.indices.map { index -> Morph(shapes[index], shapes[(index + 1) % shapes.size]) }
    }
    val angle = remember { mutableFloatStateOf(0f) }
    val shapeSeconds = remember { mutableFloatStateOf(0f) }
    val step = MINI_SHAPE_HOLD_SECONDS + MINI_SHAPE_MORPH_SECONDS

    // A motion turned off goes back to rest rather than holding wherever it
    // had got to: a cover left at an odd angle reads as a fault.
    LaunchedEffect(motion) {
        if (!motion.spins) angle.floatValue = 0f
        if (!motion.morphs) shapeSeconds.floatValue = 0f
    }
    LaunchedEffect(isPlaying, motion) {
        if (!isPlaying || motion == MiniCoverMotion.STILL) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (isActive) {
            withFrameNanos { now ->
                val seconds = (now - last) / 1_000_000_000f
                last = now
                if (motion.spins) {
                    angle.floatValue = (angle.floatValue + seconds * MINI_SPIN_DEGREES_PER_SECOND) % 360f
                }
                if (motion.morphs) {
                    shapeSeconds.floatValue = (shapeSeconds.floatValue + seconds) % (step * morphs.size)
                }
            }
        }
    }

    // Which morph, and how far through it. Read in draw and layer scopes only.
    fun morphIndex(): Int = (shapeSeconds.floatValue / step).toInt().coerceIn(0, morphs.lastIndex)
    fun morphProgress(): Float {
        val clock = shapeSeconds.floatValue
        val moving = ((clock - morphIndex() * step - MINI_SHAPE_HOLD_SECONDS) / MINI_SHAPE_MORPH_SECONDS)
            .coerceIn(0f, 1f)
        // Eased at both ends, so a shape settles rather than stops.
        return moving * moving * (3f - 2f * moving)
    }

    val progressColor = MaterialTheme.colorScheme.primary
    val wedge = remember { Path() }

    Box(
        modifier = Modifier
            .size(MINI_ARTWORK_SLOT)
            .drawBehind {
                val lineAlpha = outlineAlpha().coerceIn(0f, 1f)
                if (lineAlpha <= 0f) return@drawBehind
                val strokeWidth = MINI_OUTLINE_WIDTH.toPx()
                // The unit-square shape, fitted inside the slot by half a
                // stroke so the line is not cut by the slot's edge.
                val outline = morphs[morphIndex()].toPath(morphProgress()).asComposePath()
                val matrix = Matrix()
                matrix.translate(strokeWidth / 2f, strokeWidth / 2f)
                matrix.scale(size.width - strokeWidth, size.height - strokeWidth)
                outline.transform(matrix)
                val stroke = Stroke(width = strokeWidth, join = StrokeJoin.Round)
                val turn = angle.floatValue

                rotate(turn) { drawPath(path = outline, color = trackColor, alpha = lineAlpha, style = stroke) }

                val fraction = progress().coerceIn(0f, 1f)
                if (fraction >= 0.999f) {
                    rotate(turn) { drawPath(path = outline, color = progressColor, alpha = lineAlpha, style = stroke) }
                } else if (fraction > 0f) {
                    // A wedge from the centre, well past the slot's corners,
                    // opening clockwise from the top. It does not turn.
                    val reach = size.maxDimension
                    wedge.rewind()
                    wedge.moveTo(center.x, center.y)
                    wedge.arcTo(
                        rect = Rect(center.x - reach, center.y - reach, center.x + reach, center.y + reach),
                        startAngleDegrees = -90f,
                        sweepAngleDegrees = 360f * fraction,
                        forceMoveTo = false
                    )
                    wedge.close()
                    clipPath(wedge) {
                        rotate(turn) { drawPath(path = outline, color = progressColor, alpha = lineAlpha, style = stroke) }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = coverModifier
                .size(MINI_COVER_SIZE)
                .graphicsLayer {
                    rotationZ = angle.floatValue
                    shape = UnitMorphShape(morphs[morphIndex()], morphProgress())
                    clip = true
                }
        ) {
            MiniCoverImage(song)
        }
    }
}

/**
 * [polygon] resized about its centre so that it stays inside the unit square's
 * inscribed circle at every angle.
 *
 * [scar October 2026] A Material shape is normalised to fit the unit square
 * as it is drawn, not as it turns. The clover and the soft square reach into
 * the square's corners, so half a turn later those corners were outside the
 * slot and the progress line was cut off there. Fitted to the circle, nothing
 * the cover turns through can leave the slot; the small margin covers the
 * shapes a morph passes through on the way.
 */
private fun fittedToCircle(polygon: androidx.graphics.shapes.RoundedPolygon): androidx.graphics.shapes.RoundedPolygon {
    // The square that contains the shape at any rotation.
    val bounds = polygon.calculateMaxBounds()
    val radius = (bounds[2] - bounds[0]) / 2f
    if (radius <= 0f) return polygon
    val centerX = (bounds[0] + bounds[2]) / 2f
    val centerY = (bounds[1] + bounds[3]) / 2f
    val scale = MINI_SHAPE_FIT / radius
    return polygon.transformed { x, y ->
        androidx.graphics.shapes.TransformResult(
            0.5f + (x - centerX) * scale,
            0.5f + (y - centerY) * scale
        )
    }
}

/** Radius the shapes are fitted to, of the unit square's 0.5: a little inside it. */
private const val MINI_SHAPE_FIT = 0.48f

/**
 * A [Morph] between two unit-square shapes, stretched to the composable's
 * size. No bounds are measured: see the fixed-geometry note above.
 */
private class UnitMorphShape(private val morph: Morph, private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = morph.toPath(progress).asComposePath()
        val matrix = Matrix()
        matrix.scale(size.width, size.height)
        path.transform(matrix)
        return Outline.Generic(path)
    }
}

/**
 * The cover at rest, in the same slot and the same scalloped outline the
 * playing one starts from: what a neighbour wears as it peeks in during a
 * swipe, so the two line up as one slides in over the other.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MiniSongArtwork(song: Song, modifier: Modifier = Modifier) {
    val shape = remember { EditorialPolygonShape(MaterialShapes.Cookie9Sided) }
    Box(
        modifier = modifier
            .size(MINI_COVER_SIZE)
            .clip(shape)
    ) {
        MiniCoverImage(song)
    }
}

/**
 * The picture itself, over a note that shows when there is no cover or it has
 * not arrived. The caller clips it.
 *
 * [scar] Plain `AsyncImage` over the note, not `SubcomposeAsyncImage` with a
 * loading slot. The subcomposed version draws its loading content for at least
 * a frame on every model change, even for a cover already in the memory cache,
 * so the moment a swipe handed over from the peeking neighbour (which had the
 * cover loaded) to the pill's own slot, the cover blinked to the note and back
 * - read as the pill showing some other song. `AsyncImage` draws a memory-cache
 * hit on its first frame, and Coil skips the crossfade for one.
 */
@Composable
private fun MiniCoverImage(song: Song, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        val model = song.albumArtUri ?: song.highResThumbnailUrl ?: song.thumbnailUrl
        if (model != null) {
            coil.compose.AsyncImage(
                model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                    .data(model)
                    .crossfade(true)
                    .build(),
                contentDescription = stringResource(R.string.cd_album_art),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}
