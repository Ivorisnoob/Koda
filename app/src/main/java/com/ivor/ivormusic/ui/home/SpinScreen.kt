package com.ivor.ivormusic.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.util.lerp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.data.googleImageAtSize
import com.ivor.ivormusic.data.isUnknownArtist
import com.ivor.ivormusic.data.isUnknownTitle
import com.ivor.ivormusic.ui.library.songRowClick
import com.ivor.ivormusic.ui.theme.MontserratFamily
import com.ivor.ivormusic.util.rememberKodaHaptics
import kotlin.math.abs
import kotlin.random.Random
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Spin: a rotary drum of songs that lands on one to play.
 *
 * It is for the moment where choosing is the chore. The Homes rank and
 * recommend; this deliberately does neither - it shuffles a pool the user
 * picks the shape of (their mix, For you, liked, recent) onto a wheel, and
 * either a flick or the Spin button turns it until one slot sits in the
 * middle. That slot grows into a pill with Play, and Play queues the wheel
 * from there, so what comes next is what sat below it.
 *
 * **Everything here resolves from flows Home already holds**, which is why it
 * costs no request of its own except "For you", which asks for the same
 * discovery fetch Spotlight's tab makes, and only when that source is chosen.
 *
 * **Two rules keep the wheel smooth, and both were learned on it.** Nothing
 * that is read while it turns may recompose a row: the drum face is drawn in
 * the layer from the list's own layout, and the middle-slot state rows read
 * only changes when the wheel *settles*, because a per-crossing state had
 * every visible row recomposing on every slot a spin passed. And every cover
 * is fetched once, at the size it is drawn, before the wheel needs it: the
 * wheel repeats its pool, so after the prefetch every row that arrives is a
 * memory-cache hit rather than a network fetch and a decode mid-spin.
 */

/**
 * Where the wheel's songs come from. Not persisted: a spin is a moment. Each
 * Home opens the wheel on the pool it is showing, so "spin these" means these.
 */
enum class SpinSource { Mix, ForYou, Liked, Recent }

@Composable
private fun spinSourceLabel(source: SpinSource): String = when (source) {
    SpinSource.Mix -> stringResource(R.string.spin_source_mix)
    SpinSource.ForYou -> stringResource(R.string.spin_source_for_you)
    SpinSource.Liked -> stringResource(R.string.spin_source_liked)
    SpinSource.Recent -> stringResource(R.string.spin_source_recent)
}

/** One slot on the drum. Fixed, so a spin can land to the pixel. */
private val SPIN_ROW_HEIGHT = 100.dp

/** The record-shaped cover in each slot. */
private val SPIN_DISC_SIZE = 72.dp

/**
 * What a Google-hosted cover is asked for: 72dp at 3x, rounded up. Fixed
 * rather than derived from the density so the prefetch and the row always
 * name the same URL.
 */
private const val SPIN_COVER_REQUEST_PX = 240

/**
 * The drum repeats its songs this many times so it turns freely in both
 * directions without an end to hit. It opens in the middle lap.
 */
private const val SPIN_LAPS = 400

/** The most songs one wheel carries; past this a spin stops being a choice. */
private const val SPIN_POOL_MAX = 60

/**
 * A wheel let go: quick off the mark, then a long coast into its slot. A
 * time-driven curve rather than a spring on purpose - the landing slot is
 * chosen before the wheel moves, and a spring cannot be told how far to go
 * without also being told how it overshoots.
 */
private val SpinEasing = CubicBezierEasing(0.12f, 0.72f, 0.18f, 1f)

/**
 * The child page over the music Home. Slides in and out like every other
 * child page in the app; a completed back gesture has already moved it, so
 * its own exit is skipped rather than replayed.
 */
@Composable
internal fun SpinOverlay(
    open: Boolean,
    committedByGesture: Boolean,
    initialSource: SpinSource,
    mix: List<Song>,
    recent: List<Song>,
    viewModel: HomeViewModel,
    contentPadding: PaddingValues,
    onPlay: (List<Song>, Song) -> Unit,
    onSongLongPress: ((Song) -> Unit)?,
    onBack: () -> Unit,
) {
    val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val effectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    AnimatedContent(
        targetState = open,
        label = "SpinPage",
        transitionSpec = {
            val content = when {
                committedByGesture -> EnterTransition.None togetherWith ExitTransition.None
                !targetState ->
                    fadeIn(animationSpec = effectsSpec) togetherWith
                        (slideOutHorizontally(animationSpec = spatialSpec) { it } +
                            fadeOut(animationSpec = effectsSpec))
                else ->
                    (slideInHorizontally(animationSpec = spatialSpec) { it } +
                        fadeIn(animationSpec = effectsSpec)) togetherWith
                        fadeOut(animationSpec = effectsSpec)
            }
            // The closed state is empty on this layer, so the default
            // SizeTransform would animate between nothing and full screen
            // and clip the page to it on the way.
            content using SizeTransform(clip = false) { _, _ -> snap() }
        }
    ) { visible ->
        if (visible) {
            val liked by viewModel.likedSongs.collectAsState()
            val forYou by viewModel.discoverySongs.collectAsState()
            val forYouLoading by viewModel.isDiscoveryLoading.collectAsState()
            SpinScreen(
                initialSource = initialSource,
                mix = mix,
                forYou = forYou,
                isForYouLoading = forYouLoading,
                onRequestForYou = { viewModel.loadDiscovery() },
                liked = liked,
                recent = recent,
                contentPadding = contentPadding,
                onPlay = onPlay,
                onSongLongPress = onSongLongPress,
                onBack = onBack
            )
        } else {
            Box(Modifier.fillMaxSize())
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SpinScreen(
    initialSource: SpinSource,
    mix: List<Song>,
    forYou: List<Song>,
    isForYouLoading: Boolean,
    onRequestForYou: () -> Unit,
    liked: List<Song>,
    recent: List<Song>,
    contentPadding: PaddingValues,
    onPlay: (List<Song>, Song) -> Unit,
    onSongLongPress: ((Song) -> Unit)?,
    onBack: () -> Unit,
) {
    val haptics = rememberKodaHaptics()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val density = LocalDensity.current
    val rowPx = with(density) { SPIN_ROW_HEIGHT.toPx() }
    val coverPx = with(density) { SPIN_DISC_SIZE.roundToPx() }

    var source by rememberSaveable { mutableStateOf(initialSource) }
    // Asked for only when chosen: it is several searches and a radio call.
    LaunchedEffect(source) {
        if (source == SpinSource.ForYou) onRequestForYou()
    }

    val raw = when (source) {
        SpinSource.Mix -> mix
        SpinSource.ForYou -> forYou
        SpinSource.Liked -> liked
        SpinSource.Recent -> recent
    }
    // Keyed on the ids rather than the list, so a Home refresh that re-emits
    // the same songs does not reshuffle the wheel under the user's thumb.
    val rawIds = raw.map { it.id }
    val pool = remember(source, rawIds) {
        raw.distinctBy { it.id }.take(SPIN_POOL_MAX).shuffled()
    }

    // Every cover on the wheel, fetched once at the size the disc draws it,
    // into the shared loader's memory cache. The rows ask for the identical
    // request, so a slot arriving mid-spin draws from memory.
    LaunchedEffect(pool, coverPx) {
        val loader = context.imageLoader
        pool.forEach { song ->
            spinCoverModel(song)?.let { model ->
                loader.enqueue(
                    ImageRequest.Builder(context).data(model).size(coverPx).build()
                )
            }
        }
    }

    // A fresh wheel per pool, opening centred in the middle lap and unfolding
    // again, so a change of source is something you see happen.
    val drumState = remember(pool) {
        LazyListState(firstVisibleItemIndex = pool.size * (SPIN_LAPS / 2))
    }
    val unfold = remember(pool) { Animatable(0f) }
    LaunchedEffect(unfold) {
        unfold.animateTo(
            1f,
            spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessLow)
        )
    }

    val headerIn = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        headerIn.animateTo(
            1f,
            spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
        )
    }

    var spinning by remember { mutableStateOf(false) }
    val dice = remember { Animatable(0f) }
    // A kick of velocity on landing: the slot pops and settles.
    val landing = remember { Animatable(0f) }

    val spin: () -> Unit = spin@{
        if (spinning || pool.isEmpty()) return@spin
        scope.launch {
            spinning = true
            try {
                // At least most of a lap on a small wheel, plus a random
                // stretch, so two spins in a row do not land together.
                val steps = pool.size.coerceIn(8, 20) +
                    Random.nextInt(1, pool.size.coerceAtLeast(2) + 1)
                val durationMs = (1700 + steps * 24).coerceAtMost(3000)
                launch { dice.animateTo(dice.value + 720f, tween(durationMs, easing = SpinEasing)) }
                drumState.spinBy(steps, rowPx, durationMs)
                haptics.confirm()
                launch {
                    landing.snapTo(0f)
                    landing.animateTo(
                        0f,
                        spring(dampingRatio = 0.35f, stiffness = 420f),
                        initialVelocity = 7f
                    )
                }
            } finally {
                // A thumb on the wheel cancels the spin mid-turn, which is
                // the wheel doing what a wheel does; the button comes back.
                spinning = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Back and the title share one row: every dp the header does not
        // take is a dp of wheel.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 12.dp, end = 20.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledIconButton(
                onClick = onBack,
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer {
                        alpha = headerIn.value
                        translationX = (1f - headerIn.value) * 24.dp.toPx()
                    }
            ) {
                Text(
                    text = stringResource(R.string.spin_title),
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontFamily = MontserratFamily,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.02).em
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = stringResource(R.string.spin_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        SpinSourceRow(
            selected = source,
            // Held while a spin runs: a new pool mid-turn would land the old
            // wheel's confirm on a wheel nobody is looking at.
            onSelect = { if (!spinning) source = it }
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(bottom = contentPadding.calculateBottomPadding())
        ) {
            when {
                pool.isNotEmpty() -> SpinDrum(
                    pool = pool,
                    state = drumState,
                    rowPx = rowPx,
                    coverPx = coverPx,
                    unfold = unfold,
                    landing = landing,
                    // The wheel is the queue, in wheel order from the landed
                    // song, and the landed song is always in it.
                    onPlay = { song -> onPlay(pool, song) },
                    onLongPress = onSongLongPress,
                    modifier = Modifier.fillMaxSize()
                )
                source == SpinSource.ForYou && isForYouLoading -> LoadingIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(56.dp)
                )
                else -> SpinEmpty(modifier = Modifier.align(Alignment.Center))
            }

            // The button floats over the wheel's faded foot rather than
            // taking a band of its own. The scrim has no click of its own,
            // so a drag that starts on it still turns the wheel.
            val page = MaterialTheme.colorScheme.background
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            0f to page.copy(alpha = 0f),
                            0.45f to page.copy(alpha = 0.9f),
                            1f to page
                        )
                    )
                    .padding(horizontal = 20.dp)
                    .padding(top = 32.dp, bottom = 8.dp)
            ) {
                Button(
                    onClick = spin,
                    enabled = pool.isNotEmpty() && !spinning,
                    shapes = ButtonDefaults.shapes(),
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                ) {
                    Icon(
                        Icons.Rounded.Casino,
                        contentDescription = null,
                        modifier = Modifier
                            .size(26.dp)
                            // The die turns with the wheel and slows with it.
                            .graphicsLayer { rotationZ = dice.value }
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.spin_action),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * The drum. A snapping list whose rows are drawn as the face of a wheel:
 * tilted, shrunk, faded and pushed into an arc by their distance from the
 * middle. All of it is computed in the layer, from the list's own layout,
 * so turning the wheel redraws rows and never recomposes them.
 */
@Composable
private fun SpinDrum(
    pool: List<Song>,
    state: LazyListState,
    rowPx: Float,
    coverPx: Int,
    unfold: Animatable<Float, AnimationVector1D>,
    landing: Animatable<Float, AnimationVector1D>,
    onPlay: (Song) -> Unit,
    onLongPress: ((Song) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberKodaHaptics()
    val scope = rememberCoroutineScope()
    // The slot the rows treat as chosen: only once the wheel has stopped.
    // Every visible row reads this, so a value that changed on each crossing
    // recomposed the whole face of the wheel for every slot a spin passed.
    val settled by remember(state) {
        derivedStateOf { if (state.isScrollInProgress) -1 else state.centeredIndex() }
    }

    // One detent per slot crossing the middle, the way a dial clicks. Read
    // here, outside composition. Floored, so a fast spin does not queue
    // pulses that arrive after the wheel has already stopped.
    LaunchedEffect(state) {
        var last = 0L
        snapshotFlow { state.centeredIndex() }.drop(1).collect {
            val now = System.nanoTime()
            if (now - last >= 40_000_000L) {
                haptics.subtle()
                last = now
            }
        }
    }

    BoxWithConstraints(modifier = modifier) {
        // Half the viewport either side, so any slot can sit in the middle.
        val edge = ((maxHeight - SPIN_ROW_HEIGHT) / 2).coerceAtLeast(0.dp)
        LazyColumn(
            state = state,
            flingBehavior = rememberSnapFlingBehavior(
                lazyListState = state,
                snapPosition = SnapPosition.Center
            ),
            contentPadding = PaddingValues(vertical = edge),
            modifier = Modifier.fillMaxSize()
        ) {
            items(
                count = pool.size * SPIN_LAPS,
                key = { it },
                // One shape of row, so the list reuses compositions as slots
                // scroll through rather than building each one fresh.
                contentType = { 0 }
            ) { index ->
                val song = pool[index % pool.size]
                val chosen = index == settled
                SpinRow(
                    song = song,
                    centered = chosen,
                    coverPx = coverPx,
                    onTap = {
                        if (chosen) {
                            onPlay(song)
                        } else {
                            // A tap on another slot turns the wheel to it.
                            scope.launch {
                                state.animateScrollBy(
                                    state.distanceFromCenter(index, rowPx) * rowPx,
                                    spring(
                                        dampingRatio = 0.8f,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )
                            }
                        }
                    },
                    onPlay = { onPlay(song) },
                    onLongPress = onLongPress?.let { press -> { press(song) } },
                    modifier = Modifier
                        .height(SPIN_ROW_HEIGHT)
                        .graphicsLayer {
                            drumFace(state, index, rowPx, unfold.value, landing.value)
                        }
                )
            }
        }
    }
}

/**
 * One row as the face of the wheel. [unfold] runs 0 to 1 once per wheel: at 0
 * every row lies folded onto the middle slot, and they swing out to their
 * places with the outer ones last. [landing] is the pop a spin lands with,
 * felt only by the slots nearest the middle.
 */
private fun GraphicsLayerScope.drumFace(
    state: LazyListState,
    index: Int,
    rowPx: Float,
    unfold: Float,
    landing: Float,
) {
    val d = state.distanceFromCenter(index, rowPx)
    val a = abs(d)
    val local = (unfold * 1.6f - a * 0.18f).coerceIn(0f, 1f)
    val nearMiddle = (1f - a).coerceAtLeast(0f)

    translationY = -d * rowPx * (1f - local)
    // Rows above the middle face down towards it and rows below face up,
    // as the front of a drum does.
    rotationX = (-d * 14f).coerceIn(-72f, 72f)
    cameraDistance = 12f * density
    val scale = (1f - 0.07f * a).coerceAtLeast(0.62f) *
        (0.86f + 0.14f * local) *
        (1f + 0.05f * landing * nearMiddle)
    scaleX = scale
    scaleY = scale
    alpha = (1f - 0.2f * a).coerceIn(0f, 1f) * local
    // Alpha applied to each draw call rather than through an offscreen
    // buffer per row per frame; the row's parts barely overlap, so the
    // cheaper path looks the same.
    compositingStrategy = CompositingStrategy.ModulateAlpha
    // The arc: rows curve away from the leading edge as they leave the
    // middle, so the column reads as the rim of a wheel seen side-on.
    translationX = (a * a * 7.dp.toPx()).coerceAtMost(56.dp.toPx())
    transformOrigin = TransformOrigin(0.1f, 0.5f)
}

/** The slot index sitting nearest the middle of the viewport. */
private fun LazyListState.centeredIndex(): Int {
    val info = layoutInfo
    val middle = (info.viewportStartOffset + info.viewportEndOffset) / 2f
    return info.visibleItemsInfo
        .minByOrNull { abs(it.offset + it.size / 2f - middle) }
        ?.index ?: firstVisibleItemIndex
}

/**
 * How far [index]'s centre sits from the middle of the viewport, in slots:
 * negative above, positive below. A slot off screen reports as far away.
 */
private fun LazyListState.distanceFromCenter(index: Int, rowPx: Float): Float {
    val info = layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.index == index }
        ?: return if (index < firstVisibleItemIndex) -6f else 6f
    val middle = (info.viewportStartOffset + info.viewportEndOffset) / 2f
    return (item.offset + item.size / 2f - middle) / rowPx
}

/**
 * Turns the wheel [steps] slots on and lands the chosen one in the middle
 * exactly: a coast past it by a fifth of a slot, then a spring back, which
 * is the click of a wheel dropping into its detent.
 */
private suspend fun LazyListState.spinBy(steps: Int, rowPx: Float, durationMs: Int) {
    val start = centeredIndex()
    val distance = (steps + distanceFromCenter(start, rowPx)) * rowPx
    val overshoot = rowPx * 0.2f
    animateScrollBy(distance + overshoot, tween(durationMs, easing = SpinEasing))
    animateScrollBy(
        -overshoot,
        spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        )
    )
}

/**
 * What a slot's cover is fetched as. The same function feeds the prefetch
 * and the row, which is what makes the row a memory-cache hit.
 */
private fun spinCoverModel(song: Song): Any? =
    song.thumbnailUrl?.let { googleImageAtSize(it, SPIN_COVER_REQUEST_PX) } ?: song.albumArtUri

/**
 * A slot on the wheel: a record-cut cover, the title and artist, and - once
 * the wheel settles on it - a pill behind it with Play.
 */
@Composable
private fun SpinRow(
    song: Song,
    centered: Boolean,
    coverPx: Int,
    onTap: () -> Unit,
    onPlay: () -> Unit,
    onLongPress: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    // Kept as a State and read only in draw and layer lambdas, so the pill
    // growing is a redraw, not a recomposition per frame.
    val pill = animateFloatAsState(
        targetValue = if (centered) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
        label = "SpinPill"
    )
    val pillColor = MaterialTheme.colorScheme.surfaceContainerHigh
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .drawBehind {
                val p = pill.value
                if (p > 0.01f) {
                    drawRoundRect(
                        color = pillColor.copy(alpha = p),
                        cornerRadius = CornerRadius(size.height / 2f)
                    )
                }
            }
            .clip(RoundedCornerShape(percent = 50))
            .songRowClick(onClick = onTap, onLongClick = onLongPress)
            .padding(start = 8.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SpinDisc(song = song, turning = centered, coverPx = coverPx, pill = pill)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title.takeIf { !isUnknownTitle(it) } ?: stringResource(R.string.untitled_song),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist.takeIf { !isUnknownArtist(it) } ?: stringResource(R.string.unknown_artist),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        AnimatedVisibility(
            visible = centered,
            enter = scaleIn(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                initialScale = 0.6f
            ) + fadeIn(tween(150)),
            exit = scaleOut(targetScale = 0.6f) + fadeOut(tween(120))
        ) {
            Surface(
                onClick = onPlay,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Row(
                    modifier = Modifier.padding(start = 12.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.cd_play),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * The cover cut round like a record. The chosen one turns once the wheel has
 * settled on it; the rest hold still, so at most one animation runs on the
 * wheel and none run while it spins.
 */
@Composable
private fun SpinDisc(song: Song, turning: Boolean, coverPx: Int, pill: State<Float>) {
    val context = LocalContext.current
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(turning) {
        while (turning) {
            if (rotation.value > 3600f) rotation.snapTo(rotation.value % 360f)
            rotation.animateTo(rotation.value + 360f, tween(9000, easing = LinearEasing))
        }
    }
    // The exact request the prefetch made: same data, same size, so the
    // memory cache answers it. No crossfade - a hit should simply be there.
    val request = remember(song.id, coverPx) {
        spinCoverModel(song)?.let { model ->
            ImageRequest.Builder(context)
                .data(model)
                .size(coverPx)
                .crossfade(false)
                .build()
        }
    }
    Box(
        modifier = Modifier
            .size(SPIN_DISC_SIZE)
            .graphicsLayer {
                val grow = lerp(0.88f, 1f, pill.value)
                scaleX = grow
                scaleY = grow
                rotationZ = rotation.value
            }
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        if (request != null) {
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(28.dp)
            )
        }
        // The spindle hole, in the page's own colour.
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(SPIN_DISC_SIZE * 0.16f)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.background)
        )
    }
}

/**
 * Where the wheel's songs come from: connected toggle buttons, the segmented
 * shape the artist sort and Spotlight's filters use, since these are mutually
 * exclusive pools.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SpinSourceRow(
    selected: SpinSource,
    onSelect: (SpinSource) -> Unit,
) {
    val haptics = rememberKodaHaptics()
    val entries = SpinSource.entries.toList()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
    ) {
        entries.forEachIndexed { index, entry ->
            ToggleButton(
                checked = entry == selected,
                onCheckedChange = {
                    if (entry != selected) {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onSelect(entry)
                    }
                },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                colors = ToggleButtonDefaults.toggleButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    checkedContainerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    checkedContentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(spinSourceLabel(entry))
            }
        }
    }
}

/** An empty pool: said plainly, with what fills it. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SpinEmpty(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(MaterialShapes.Cookie9Sided.toShape())
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.Casino,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.spin_empty),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(R.string.spin_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * The way into Spin beside Play all in Spotlight's quick picks: a die on a
 * tonal button that morphs on press, in the Play all pill's own colour so the
 * two read as a pair. Rolled into place once as it appears - one-shot rather
 * than a loop, so the Home it sits on is not drawing a frame every vsync for
 * a decoration.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SpinButton(onClick: () -> Unit, size: Dp, modifier: Modifier = Modifier) {
    val turn = remember { Animatable(-150f) }
    LaunchedEffect(Unit) {
        turn.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow))
    }
    FilledTonalIconButton(
        onClick = onClick,
        shapes = IconButtonDefaults.shapes(),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ),
        modifier = modifier.size(size)
    ) {
        Icon(
            Icons.Rounded.Casino,
            contentDescription = stringResource(R.string.spin_title),
            modifier = Modifier
                .size(size * 0.46f)
                .graphicsLayer { rotationZ = turn.value }
        )
    }
}

/**
 * Spin as a badge on Your Mix's Play button, sat the way the incognito badge
 * sits on the profile avatar: a small disc on the lower edge, ringed in the
 * page colour so it reads as cut out of what it sits on. The theme's accent
 * rather than a second palette, so Play and the wheel read as one control
 * with two ways in. Dips on press and rolls into place once as it appears.
 */
@Composable
internal fun SpinBadge(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "SpinBadgePress"
    )
    val turn = remember { Animatable(-150f) }
    LaunchedEffect(Unit) {
        turn.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow))
    }
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        border = BorderStroke(3.dp, MaterialTheme.colorScheme.background),
        modifier = modifier
            .size(44.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Rounded.Casino,
                contentDescription = stringResource(R.string.spin_title),
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer { rotationZ = turn.value }
            )
        }
    }
}
