package com.ivor.ivormusic.ui.lyricsshare

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CropPortrait
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.ui.components.SongArtwork
import com.ivor.ivormusic.ui.player.PlayerViewModel
import com.ivor.ivormusic.ui.player.rememberArtworkSeed
import com.ivor.ivormusic.ui.standby.appleSpring
import com.ivor.ivormusic.ui.theme.MontserratFamily
import com.ivor.ivormusic.ui.theme.PaletteStyle
import com.ivor.ivormusic.ui.theme.buildSeedColorScheme
import com.ivor.ivormusic.util.lyricsFitDisplayFont
import com.ivor.ivormusic.util.rememberKodaHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.roundToInt

/** The most lines a card holds; past it the words get too small to read in a story. */
private const val MAX_LINES = 6

private enum class ShareStep { PICK, CARD }

/**
 * Share lyrics: pick lines from the song, then make them into a card to share,
 * save or copy.
 *
 * **Above everything.** Opened from the player's menu or a long press on a
 * lyric line in any player style, and hosted in MainActivity after Standby, so
 * it is the same screen whichever of the nine players it was opened from and
 * nothing in a player's own layout changes for it. It reads the request the
 * [PlayerViewModel] took when it opened (the song and its lines at that
 * moment), so the song ending underneath does not swap the lyrics out.
 *
 * **Its colours are the cover's.** The screen wears the dark scheme of the
 * playing cover, and the six card colourways are roles of that cover's light,
 * dark and vibrant schemes - nothing is a literal.
 *
 * **Motion.** It rises from the bottom on Apple's springs (`appleSpring`).
 * Picking a line fills it and joins it to its picked neighbours as one
 * connected group, Expressive's segmented shape, with a bouncy morph of the
 * corners; the unpicked lines step back. Going on to the card, the list gives
 * way and the card lands, then the words rise onto it.
 */
@Composable
fun LyricsShareOverlay(viewModel: PlayerViewModel) {
    val request by viewModel.lyricsShare.collectAsState()
    // Held through the exit, so the screen leaves with its content on it.
    var shown by remember { mutableStateOf<LyricsShareRequest?>(null) }
    LaunchedEffect(request) { if (request != null) shown = request }

    AnimatedVisibility(
        visible = request != null,
        enter = slideInVertically(appleSpring(0.5f)) { it / 5 } + fadeIn(tween(220)),
        exit = slideOutVertically(appleSpring(0.42f)) { it / 4 } + fadeOut(tween(180)),
    ) {
        val current = shown ?: return@AnimatedVisibility
        key(current.openedAt) {
            LyricsShareScreen(request = current, onClose = viewModel::closeLyricsShare)
        }
    }
}

@Composable
private fun LyricsShareScreen(request: LyricsShareRequest, onClose: () -> Unit) {
    val song = request.song
    val fallbackSeed = MaterialTheme.colorScheme.primary
    val seed = rememberArtworkSeed(
        enabled = true,
        albumArtUri = song.albumArtUri?.toString() ?: song.highResThumbnailUrl ?: song.thumbnailUrl,
    ) ?: fallbackSeed
    val scheme = animatedScheme(remember(seed) { buildSeedColorScheme(seed, dark = true, style = PaletteStyle.TONAL_SPOT) })
    val lyricsFont: FontFamily? = remember(request.lines) {
        if (lyricsFitDisplayFont(request.lines)) MontserratFamily else null
    }

    val picked = remember { mutableStateListOf<Int>().apply { request.initialLine?.let(::add) } }
    var step by remember { mutableStateOf(ShareStep.PICK) }
    BackHandler {
        if (step == ShareStep.CARD) step = ShareStep.PICK else onClose()
    }

    MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes) {
        CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides scheme.onSurface) {
            Box(
                Modifier
                    .fillMaxSize()
                    // Nothing under this screen should answer a touch meant for it.
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            ) {
                ShareBackdrop(song)
                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        if (targetState == ShareStep.CARD) {
                            (fadeIn(tween(260, delayMillis = 70)) + scaleIn(appleSpring(0.5f), initialScale = 0.92f)) togetherWith
                                (fadeOut(tween(160)) + scaleOut(appleSpring(0.4f), targetScale = 1.04f))
                        } else {
                            (fadeIn(tween(240, delayMillis = 60)) + scaleIn(appleSpring(0.5f), initialScale = 1.04f)) togetherWith
                                (fadeOut(tween(160)) + scaleOut(appleSpring(0.4f), targetScale = 0.92f))
                        }
                    },
                    label = "LyricsShareStep",
                    modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars),
                ) { target ->
                    when (target) {
                        ShareStep.PICK -> PickLines(
                            request = request,
                            picked = picked,
                            lyricsFont = lyricsFont,
                            onClose = onClose,
                            onNext = { step = ShareStep.CARD },
                        )
                        ShareStep.CARD -> CardEditor(
                            song = song,
                            lines = picked.sorted().map { request.lines[it] },
                            seed = seed,
                            lyricsFont = lyricsFont,
                            onBack = { step = ShareStep.PICK },
                        )
                    }
                }
            }
        }
    }
}

/** The roles this screen draws, eased from the app's colours to the cover's once it is read. */
@Composable
private fun animatedScheme(target: ColorScheme): ColorScheme {
    val spec = tween<Color>(500, easing = FastOutSlowInEasing)
    val primary by animateColorAsState(target.primary, spec, label = "lsPrimary")
    val onPrimary by animateColorAsState(target.onPrimary, spec, label = "lsOnPrimary")
    val primaryContainer by animateColorAsState(target.primaryContainer, spec, label = "lsPrimaryContainer")
    val onPrimaryContainer by animateColorAsState(target.onPrimaryContainer, spec, label = "lsOnPrimaryContainer")
    val background by animateColorAsState(target.background, spec, label = "lsBackground")
    val onSurface by animateColorAsState(target.onSurface, spec, label = "lsOnSurface")
    val high by animateColorAsState(target.surfaceContainerHigh, spec, label = "lsHigh")
    val highest by animateColorAsState(target.surfaceContainerHighest, spec, label = "lsHighest")
    return target.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        background = background,
        onSurface = onSurface,
        surfaceContainerHigh = high,
        surfaceContainerHighest = highest,
    )
}

/** The room: the cover as a soft blur over the scheme's background, darkening at the edges. */
@Composable
private fun ShareBackdrop(song: Song) {
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().background(scheme.background)) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SongArtwork(
                song = song,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.4f
                        scaleY = 1.4f
                        alpha = 0.38f
                    }
                    .blur(80.dp),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to scheme.scrim.copy(alpha = 0.45f),
                        0.4f to scheme.scrim.copy(alpha = 0.2f),
                        1f to scheme.scrim.copy(alpha = 0.6f),
                    )
                )
        )
    }
}

// ---------------------------------------------------------------------------
// Step one: the lines
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PickLines(
    request: LyricsShareRequest,
    picked: MutableList<Int>,
    lyricsFont: FontFamily?,
    onClose: () -> Unit,
    onNext: () -> Unit,
) {
    val haptics = rememberKodaHaptics()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = ((request.initialLine ?: 0) - 2).coerceAtLeast(0)
    )
    // A seventh line is refused with a shake and a word, not silently ignored.
    val shake = remember { Animatable(0f) }
    var limitHint by remember { mutableIntStateOf(0) }
    LaunchedEffect(limitHint) {
        if (limitHint > 0) {
            delay(1800)
            limitHint = 0
        }
    }

    fun toggle(index: Int) {
        if (index in picked) {
            picked.remove(index)
            haptics.tick()
        } else if (picked.size >= MAX_LINES) {
            haptics.reject()
            limitHint++
            scope.launch {
                for (x in listOf(14f, -12f, 9f, -6f, 3f, 0f)) shake.animateTo(x, spring(stiffness = 2200f))
            }
        } else {
            picked.add(index)
            haptics.tick()
        }
    }

    Column(Modifier.fillMaxSize()) {
        // Header: close, what this is, and the song it is from.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalIconButton(
                onClick = onClose,
                shapes = IconButtonDefaults.shapes(),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
                ),
            ) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.lyrics_share_close))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.lyrics_share_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = listOf(request.song.title, request.song.artist).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            SongArtwork(
                song = request.song,
                contentDescription = null,
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)),
            )
        }

        Box(Modifier.weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationX = shake.value },
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 140.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                itemsIndexed(request.lines, key = { index, _ -> index }) { index, line ->
                    PickableLine(
                        text = line,
                        selected = index in picked,
                        joinsAbove = index in picked && (index - 1) in picked,
                        joinsBelow = index in picked && (index + 1) in picked,
                        dimmed = picked.isNotEmpty() && index !in picked,
                        lyricsFont = lyricsFont,
                        onClick = { toggle(index) },
                    )
                }
            }
            // The bar floats over the end of the list, which scrolls under it.
            PickBar(
                count = picked.size,
                limitHint = limitHint > 0,
                onClear = {
                    haptics.tick()
                    picked.clear()
                },
                onNext = {
                    haptics.confirm()
                    onNext()
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            )
        }
    }
}

/**
 * One line of the song. Picked, it fills with the accent's container and
 * joins any picked neighbour into one rounded group - the corners between two
 * picked lines tighten, the outer ones stay round - on a bouncy spring, so a
 * run of lines visibly becomes one quote. Unpicked lines step back while
 * anything is picked.
 */
@Composable
private fun PickableLine(
    text: String,
    selected: Boolean,
    joinsAbove: Boolean,
    joinsBelow: Boolean,
    dimmed: Boolean,
    lyricsFont: FontFamily?,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val bouncy = spring<androidx.compose.ui.unit.Dp>(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)
    val top by animateDpAsState(if (joinsAbove) 6.dp else 26.dp, bouncy, label = "lineTop")
    val bottom by animateDpAsState(if (joinsBelow) 6.dp else 26.dp, bouncy, label = "lineBottom")
    val container by animateColorAsState(
        if (selected) scheme.primaryContainer else scheme.primaryContainer.copy(alpha = 0f),
        spring(stiffness = Spring.StiffnessMediumLow),
        label = "lineContainer",
    )
    val content by animateColorAsState(
        when {
            selected -> scheme.onPrimaryContainer
            dimmed -> scheme.onSurface.copy(alpha = 0.42f)
            else -> scheme.onSurface.copy(alpha = 0.92f)
        },
        spring(stiffness = Spring.StiffnessMediumLow),
        label = "lineContent",
    )
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = when {
            pressed -> 0.96f
            dimmed -> 0.98f
            else -> 1f
        },
        animationSpec = if (pressed) appleSpring(0.18f) else spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "lineScale",
    )
    val shape = RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(container)
            .semantics {
                role = Role.Checkbox
                this.selected = selected
            }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.ExtraBold,
                fontFamily = lyricsFont ?: MaterialTheme.typography.headlineSmall.fontFamily,
                letterSpacing = (-0.01).em,
            ),
            color = content,
            modifier = Modifier.weight(1f),
        )
        AnimatedVisibility(
            visible = selected,
            enter = scaleIn(spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium), initialScale = 0.3f) + fadeIn(tween(120)),
            exit = scaleOut(spring(stiffness = Spring.StiffnessHigh), targetScale = 0.3f) + fadeOut(tween(100)),
        ) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = scheme.onPrimaryContainer,
                modifier = Modifier.padding(start = 12.dp).size(26.dp),
            )
        }
    }
}

/** The floating bar: how many lines, a way to clear them, and on to the card. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PickBar(
    count: Int,
    limitHint: Boolean,
    onClear: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.widthIn(max = 520.dp).fillMaxWidth().shadow(18.dp, RoundedCornerShape(32.dp)),
        shape = RoundedCornerShape(32.dp),
        color = scheme.surfaceContainerHigh,
        contentColor = scheme.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(start = 22.dp, end = 8.dp, top = 8.dp, bottom = 8.dp).heightIn(min = 56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedContent(
                targetState = when {
                    limitHint -> -1
                    else -> count
                },
                transitionSpec = {
                    (fadeIn(tween(180)) + slideInVertically(appleSpring(0.35f)) { it / 2 }) togetherWith
                        (fadeOut(tween(120)) + slideOutVertically(appleSpring(0.35f)) { -it / 2 })
                },
                modifier = Modifier.weight(1f),
                label = "pickCount",
            ) { shownCount ->
                Text(
                    text = when {
                        shownCount < 0 -> stringResource(R.string.lyrics_share_limit, MAX_LINES)
                        shownCount == 0 -> stringResource(R.string.lyrics_share_pick_hint)
                        else -> stringResource(R.string.lyrics_share_count, shownCount, MAX_LINES)
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = if (shownCount < 0) scheme.error else scheme.onSurface.copy(alpha = 0.85f),
                    maxLines = 2,
                )
            }
            AnimatedVisibility(visible = count > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onClear) { Text(stringResource(R.string.lyrics_share_clear)) }
                    Spacer(Modifier.width(4.dp))
                    Button(
                        onClick = onNext,
                        shapes = ButtonDefaults.shapes(),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                    ) {
                        Text(stringResource(R.string.lyrics_share_next), fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Step two: the card
// ---------------------------------------------------------------------------

private enum class BusyAction { NONE, SHARE, SAVE }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CardEditor(
    song: Song,
    lines: List<String>,
    seed: Color,
    lyricsFont: FontFamily?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val haptics = rememberKodaHaptics()
    val scope = rememberCoroutineScope()
    val palettes = rememberCardPalettes(seed)
    var style by remember { mutableStateOf(LyricsCardStyle.ALBUM) }
    // Square first: it fits a post, a chat and a story alike; Story is one tap away.
    var aspect by remember { mutableStateOf(CardAspect.SQUARE) }
    var paletteIndex by remember { mutableIntStateOf(0) }
    val palette = animatedPalette(palettes[paletteIndex.coerceIn(0, palettes.lastIndex)])

    // The layer of whichever card is on show; what Share and Save capture.
    var cardLayer by remember { mutableStateOf<GraphicsLayer?>(null) }
    var busy by remember { mutableStateOf(BusyAction.NONE) }
    var saved by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(saved) { if (saved) { delay(2200); saved = false } }
    LaunchedEffect(copied) { if (copied) { delay(1800); copied = false } }
    LaunchedEffect(failed) { if (failed) { delay(3000); failed = false } }
    // Anything changed on the card means it has not been saved as it now is.
    LaunchedEffect(style, aspect, paletteIndex) { saved = false }

    // The card lands, then the words rise onto it.
    val landing = remember { Animatable(0f) }
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { landing.animateTo(1f, appleSpring(0.55f, 0.82f)) }
        delay(160)
        reveal.animateTo(1f, appleSpring(0.6f, 0.85f))
    }

    fun export(action: BusyAction) {
        val layer = cardLayer ?: return
        if (busy != BusyAction.NONE) return
        busy = action
        scope.launch {
            try {
                val bitmap = layer.toImageBitmap()
                val ok = when (action) {
                    BusyAction.SHARE -> LyricsShareExport.share(context, bitmap, song)
                    BusyAction.SAVE -> LyricsShareExport.save(context, bitmap, song)
                    BusyAction.NONE -> true
                }
                if (ok) {
                    haptics.confirm()
                    if (action == BusyAction.SAVE) saved = true
                } else {
                    haptics.reject()
                    failed = true
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                com.ivor.ivormusic.util.KLog.e("LyricsShare", "Could not capture the card", e)
                haptics.reject()
                failed = true
            } finally {
                busy = BusyAction.NONE
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        // Top bar: back to the lines, the title, the shape of the card.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalIconButton(
                onClick = onBack,
                shapes = IconButtonDefaults.shapes(),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
                ),
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.lyrics_share_back))
            }
            Spacer(Modifier.width(14.dp))
            Text(
                text = stringResource(R.string.lyrics_share_card_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            ConnectedChoices(
                options = CardAspect.entries,
                selected = aspect,
                label = { stringResource(it.labelRes) },
                icon = { if (it == CardAspect.STORY) Icons.Rounded.CropPortrait else Icons.Rounded.CropSquare },
                iconOnly = true,
                onSelect = {
                    haptics.tick()
                    aspect = it
                },
            )
        }

        // The card, as large as the room allows.
        BoxWithConstraints(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            val roomW = constraints.maxWidth.toFloat()
            val roomH = constraints.maxHeight.toFloat()
            AnimatedContent(
                targetState = aspect,
                transitionSpec = {
                    (fadeIn(tween(220, delayMillis = 60)) + scaleIn(appleSpring(0.45f, 0.85f), initialScale = 0.94f)) togetherWith
                        (fadeOut(tween(140)) + scaleOut(appleSpring(0.4f), targetScale = 0.94f))
                },
                contentAlignment = Alignment.Center,
                label = "cardAspect",
            ) { shownAspect ->
                val fit = min(roomW / CARD_EXPORT_WIDTH_PX, roomH / shownAspect.heightPx)
                val layer = rememberGraphicsLayer()
                // Only the card for the aspect now chosen is the one exported.
                if (shownAspect == aspect) SideEffect { cardLayer = layer }
                Box(
                    Modifier
                        .graphicsLayer {
                            val l = landing.value
                            alpha = l.coerceIn(0f, 1f)
                            val s = 0.86f + 0.14f * l
                            scaleX = s
                            scaleY = s
                        }
                        .shadow(24.dp, RoundedCornerShape(22.dp))
                        .clip(RoundedCornerShape(22.dp))
                        // Laid out at export size and shown scaled to fit, so the
                        // image matches the preview line for line.
                        .layout { measurable, _ ->
                            val w = CARD_EXPORT_WIDTH_PX
                            val h = shownAspect.heightPx
                            val placeable = measurable.measure(Constraints.fixed(w, h))
                            layout((w * fit).roundToInt(), (h * fit).roundToInt()) {
                                placeable.placeWithLayer(0, 0) {
                                    scaleX = fit
                                    scaleY = fit
                                    transformOrigin = TransformOrigin(0f, 0f)
                                }
                            }
                        }
                        .drawWithContent {
                            layer.record { this@drawWithContent.drawContent() }
                            drawLayer(layer)
                        }
                ) {
                    Crossfade(
                        targetState = style,
                        animationSpec = tween(260, easing = FastOutSlowInEasing),
                        label = "cardStyle",
                    ) { shownStyle ->
                        LyricsCard(
                            song = song,
                            lines = lines,
                            style = shownStyle,
                            palette = palette,
                            aspect = shownAspect,
                            lyricsFont = lyricsFont,
                            reveal = { reveal.value },
                        )
                    }
                }
            }
        }

        // Look: the four styles, then the colourways.
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ConnectedChoices(
                options = LyricsCardStyle.entries,
                selected = style,
                label = { stringResource(it.labelRes) },
                modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth(),
                onSelect = {
                    haptics.tick()
                    style = it
                },
            )
            Spacer(Modifier.height(14.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                palettes.forEachIndexed { index, swatch ->
                    Swatch(
                        palette = swatch,
                        selected = index == paletteIndex,
                        description = stringResource(R.string.lyrics_share_colour, index + 1),
                        onClick = {
                            haptics.tick()
                            paletteIndex = index
                        },
                    )
                }
            }
        }

        AnimatedVisibility(visible = failed) {
            Text(
                text = stringResource(R.string.lyrics_share_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }

        // What to do with it: copy and save beside the one loud action.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .height(56.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FilledTonalButton(
                onClick = {
                    LyricsShareExport.copy(context, lines, song)
                    haptics.confirm()
                    copied = true
                },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxSize().weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp),
            ) {
                ActionLabel(
                    done = copied,
                    icon = Icons.Rounded.ContentCopy,
                    label = stringResource(if (copied) R.string.lyrics_share_copied else R.string.lyrics_share_copy),
                )
            }
            FilledTonalButton(
                onClick = { export(BusyAction.SAVE) },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxSize().weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp),
            ) {
                if (busy == BusyAction.SAVE) {
                    LoadingIndicator(modifier = Modifier.size(28.dp))
                } else {
                    ActionLabel(
                        done = saved,
                        icon = Icons.Rounded.Download,
                        label = stringResource(if (saved) R.string.lyrics_share_saved else R.string.lyrics_share_save),
                    )
                }
            }
            Button(
                onClick = { export(BusyAction.SHARE) },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxSize().weight(1.4f),
            ) {
                if (busy == BusyAction.SHARE) {
                    LoadingIndicator(modifier = Modifier.size(28.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.lyrics_share_send), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** A colourway as it will look: the page, with the words' colour as a dot. */
@Composable
private fun Swatch(palette: CardPalette, selected: Boolean, description: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.12f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium),
        label = "swatchScale",
    )
    val ring by animateDpAsState(if (selected) 3.dp else 0.dp, spring(stiffness = Spring.StiffnessMedium), label = "swatchRing")
    Box(
        modifier = Modifier
            .size(40.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(palette.container, palette.containerEnd)))
            .border(ring, scheme.onSurface, CircleShape)
            .semantics {
                role = Role.RadioButton
                this.selected = selected
                contentDescription = description
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(targetState = selected, label = "swatchMark") { isSelected ->
            if (isSelected) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = palette.content, modifier = Modifier.size(20.dp))
            } else {
                Box(Modifier.size(10.dp).clip(CircleShape).background(palette.content))
            }
        }
    }
}

@Composable
private fun ActionLabel(done: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    AnimatedContent(
        targetState = done,
        transitionSpec = {
            (scaleIn(spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium), initialScale = 0.6f) + fadeIn(tween(120))) togetherWith
                (scaleOut(targetScale = 0.6f) + fadeOut(tween(100)))
        },
        label = "actionLabel",
    ) { isDone ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (isDone) Icons.Rounded.Check else icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A connected row of toggle buttons in this screen's colours, one of them chosen. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun <T> ConnectedChoices(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    icon: ((T) -> androidx.compose.ui.graphics.vector.ImageVector)? = null,
    iconOnly: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    // Unselected buttons on their own container, or they vanish into the backdrop.
    val colors = ToggleButtonDefaults.colors(
        containerColor = scheme.surfaceContainerHighest.copy(alpha = 0.8f),
        contentColor = scheme.onSurface,
    )
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, option ->
            val checked = option == selected
            val text = label(option)
            ToggleButton(
                checked = checked,
                onCheckedChange = { if (!checked) onSelect(option) },
                modifier = (if (iconOnly) Modifier else Modifier.weight(1f))
                    .semantics { contentDescription = text },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                colors = colors,
            ) {
                if (icon != null) {
                    Icon(icon(option), contentDescription = null, modifier = Modifier.size(20.dp))
                }
                if (!iconOnly) {
                    if (icon != null) Spacer(Modifier.width(6.dp))
                    Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** The card's colours, eased from one colourway to the next. */
@Composable
private fun animatedPalette(target: CardPalette): CardPalette {
    val spec = spring<Color>(stiffness = Spring.StiffnessMediumLow)
    val container by animateColorAsState(target.container, spec, label = "cardContainer")
    val containerEnd by animateColorAsState(target.containerEnd, spec, label = "cardContainerEnd")
    val content by animateColorAsState(target.content, spec, label = "cardContent")
    val accent by animateColorAsState(target.accent, spec, label = "cardAccent")
    return CardPalette(container, containerEnd, content, accent)
}
