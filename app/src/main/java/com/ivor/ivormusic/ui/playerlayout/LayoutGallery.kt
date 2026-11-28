package com.ivor.ivormusic.ui.playerlayout

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import androidx.compose.ui.util.lerp
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.PlayerStyle
import com.ivor.ivormusic.data.ThemePreferences
import com.ivor.ivormusic.data.playerlayout.PlayerLayout
import com.ivor.ivormusic.data.playerlayout.PlayerLayoutStore
import com.ivor.ivormusic.ui.player.CustomPlayerCanvas
import com.ivor.ivormusic.ui.player.LayoutHost
import com.ivor.ivormusic.ui.player.LocalMotionArtwork
import com.ivor.ivormusic.ui.player.PlayerFrameKind
import com.ivor.ivormusic.ui.player.PlayerViewModel
import com.ivor.ivormusic.ui.player.playerFrameFor
import com.ivor.ivormusic.ui.player.rememberArtworkColorScheme
import com.ivor.ivormusic.ui.theme.currentWindowLayout
import com.ivor.ivormusic.ui.theme.windowDpSize
import com.ivor.ivormusic.util.rememberKodaHaptics
import kotlinx.coroutines.launch
import kotlin.math.abs

/** The player's size and safe area on this phone, as the editor and the gallery draw it. */
internal class PlayerCanvasGeometry(val size: DpSize, val safeArea: PaddingValues)

/**
 * The player as this phone draws it: the window, or the phone-shaped stage on
 * a wide screen. On its side the player uses its landscape layout instead, so
 * the editor and the gallery show the upright one.
 */
@Composable
internal fun rememberPlayerCanvasGeometry(): PlayerCanvasGeometry {
    val window = windowDpSize()
    val frame = playerFrameFor(currentWindowLayout())
    val size = when (frame.kind) {
        PlayerFrameKind.STAGE -> DpSize(frame.stageWidth, window.height)
        PlayerFrameKind.LANDSCAPE -> DpSize(min(window.width, window.height), max(window.width, window.height))
        PlayerFrameKind.FULL -> window
    }
    val systemSafeArea = WindowInsets.safeDrawing.asPaddingValues()
    val safeArea = if (frame.kind == PlayerFrameKind.LANDSCAPE) {
        PaddingValues(top = 32.dp, bottom = 24.dp)
    } else {
        systemSafeArea
    }
    return PlayerCanvasGeometry(size, safeArea)
}

/**
 * Every player layout on this phone as a card, live and playing the song, the
 * way lock screens are chosen: swipe between them, tap one to use it,
 * Customize to change it, and a last card to make a new one.
 *
 * It opens by zooming out of the player itself (the card in use starts the
 * size of the screen, exactly the player under it, and shrinks into its
 * place), and choosing a card zooms back in to it.
 */
@Composable
internal fun LayoutGallery(
    focusId: String?,
    viewModel: PlayerViewModel,
    onCustomize: (String) -> Unit,
    onNew: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val haptics = rememberKodaHaptics()
    val scope = rememberCoroutineScope()
    val store = remember(context) { PlayerLayoutStore(context) }
    val prefs = remember(context) { ThemePreferences(context) }
    val layouts by store.layouts.collectAsState()
    val activeId by store.activeId.collectAsState()
    val playerStyle by prefs.playerStyle.collectAsState()
    val ambientBackground by prefs.ambientBackground.collectAsState()
    val artworkColors by prefs.playerArtworkColors.collectAsState()
    val song by viewModel.currentSong.collectAsState()
    val inUseId = if (playerStyle == PlayerStyle.CUSTOM) PlayerLayoutStore.active(layouts, activeId)?.id else null

    val geometry = rememberPlayerCanvasGeometry()
    val playerScheme = rememberArtworkColorScheme(
        enabled = artworkColors,
        albumArtUri = song?.albumArtUri?.toString() ?: song?.thumbnailUrl,
        base = MaterialTheme.colorScheme
    )
    val host = remember(viewModel) {
        LayoutHost(
            viewModel = viewModel,
            interactive = false,
            onAction = {},
            onArtistClick = {},
            lyricsOpen = false,
            sleepTimerActive = false,
            swipeToSkip = null,
        )
    }

    val startPage = remember {
        layouts.indexOfFirst { it.id == (focusId ?: inUseId) }.coerceAtLeast(0)
    }
    val pager = rememberPagerState(initialPage = startPage) { layouts.size + 1 }

    // 0 is the card the size of the screen; 1 is the gallery.
    val zoom = remember { Animatable(0f) }
    var zoomPage by remember { mutableIntStateOf(startPage) }
    var cardCenterY by remember { mutableStateOf<Float?>(null) }
    var rootHeight by remember { mutableFloatStateOf(0f) }
    val zoomIn = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val zoomOut = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    // Only the card that is the player under the gallery zooms out of it;
    // any other opening simply fades in.
    val zoomsFromPlayer = remember { layouts.getOrNull(startPage)?.id?.let { it == inUseId } == true }
    LaunchedEffect(cardCenterY != null) {
        if (cardCenterY == null) return@LaunchedEffect
        if (zoomsFromPlayer) zoom.animateTo(1f, zoomIn) else zoom.snapTo(1f)
    }
    var closing by remember { mutableStateOf(false) }
    /** Leaves the gallery, zooming [page]'s card back into the player when it is the one in use. */
    fun closeInto(page: Int, intoPlayer: Boolean = layouts.getOrNull(page)?.id == inUseId) {
        if (closing) return
        closing = true
        scope.launch {
            if (intoPlayer) {
                zoomPage = page
                zoom.animateTo(0f, zoomOut)
            }
            onClose()
        }
    }
    fun use(layout: PlayerLayout) {
        store.setActive(layout.id)
        prefs.setPlayerStyle(PlayerStyle.CUSTOM)
        haptics.confirm()
        closeInto(pager.currentPage, intoPlayer = true)
    }

    var menuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<PlayerLayout?>(null) }
    var deleting by remember { mutableStateOf<PlayerLayout?>(null) }

    BackHandler { closeInto(pager.currentPage) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { rootHeight = it.size.height.toFloat() }
    ) {
        val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val aspect = geometry.size.width / geometry.size.height
        var cardHeight = (maxHeight - statusTop - 64.dp - navBottom - 176.dp).coerceAtLeast(200.dp)
        var cardWidth = cardHeight * aspect
        if (cardWidth > maxWidth * 0.74f) {
            cardWidth = maxWidth * 0.74f
            cardHeight = cardWidth / aspect
        }
        val cardScale = cardHeight / geometry.size.height
        val sidePadding = (maxWidth - cardWidth) / 2
        val cardHeightPx = with(density) { cardHeight.toPx() }

        // The room behind the cards fades in as the player shrinks into one.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = zoom.value.coerceIn(0f, 1f) }
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
        )

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(64.dp)
                    .padding(horizontal = 8.dp)
                    .graphicsLayer { alpha = zoom.value.coerceIn(0f, 1f) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { closeInto(pager.currentPage) }) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cpl_close_add))
                }
                Text(
                    text = stringResource(R.string.cpl_gallery_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(48.dp))
            }

            HorizontalPager(
                state = pager,
                contentPadding = PaddingValues(horizontal = sidePadding),
                pageSpacing = 14.dp,
                beyondViewportPageCount = 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(cardHeight)
                    .onGloballyPositioned {
                        val bounds = it.boundsInRoot()
                        cardCenterY = bounds.center.y
                    }
            ) { page ->
                val layout = layouts.getOrNull(page)
                Box(
                    modifier = Modifier
                        .size(cardWidth, cardHeight)
                        .graphicsLayer {
                            val away = abs((pager.currentPage - page) + pager.currentPageOffsetFraction)
                                .coerceAtMost(1f)
                            val z = zoom.value
                            if (page == zoomPage && rootHeight > 0f && cardCenterY != null) {
                                val full = rootHeight / cardHeightPx
                                val settled = 1f - 0.1f * away
                                val s = lerp(full, settled, z)
                                scaleX = s
                                scaleY = s
                                translationY = (rootHeight / 2f - cardCenterY!!) * (1f - z)
                                shape = RoundedCornerShape(lerp(0.dp, 32.dp, z.coerceIn(0f, 1f)))
                            } else {
                                val s = 1f - 0.1f * away
                                scaleX = s
                                scaleY = s
                                alpha = lerp(0.55f, 1f, 1f - away) * z.coerceIn(0f, 1f)
                                shape = RoundedCornerShape(32.dp)
                            }
                            clip = true
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (layout != null) {
                        Box(
                            modifier = Modifier
                                .requiredSize(geometry.size)
                                .graphicsLayer {
                                    scaleX = cardScale
                                    scaleY = cardScale
                                }
                        ) {
                            MaterialTheme(colorScheme = playerScheme) {
                                // A card never starts a video decoder of its own.
                                CompositionLocalProvider(LocalMotionArtwork provides null) {
                                    CustomPlayerCanvas(
                                        layout = layout,
                                        host = host,
                                        safeArea = geometry.safeArea,
                                        ambientBackground = ambientBackground,
                                    )
                                }
                            }
                        }
                    } else {
                        NewLayoutCard()
                    }
                    // Over the card, so the pieces drawn in it never take a
                    // touch or a swipe meant for the gallery.
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable {
                                if (page != pager.currentPage) {
                                    scope.launch { pager.animateScrollToPage(page) }
                                } else if (layout != null) {
                                    use(layout)
                                } else {
                                    onNew()
                                }
                            }
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            // What the card in the middle is, and what can be done with it.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .graphicsLayer { alpha = zoom.value.coerceIn(0f, 1f) },
                contentAlignment = Alignment.TopCenter
            ) {
                val fade = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
                AnimatedContent(
                    targetState = pager.currentPage,
                    transitionSpec = { fadeIn(fade) togetherWith fadeOut(fade) },
                    label = "GalleryCaption"
                ) { page ->
                    val layout = layouts.getOrNull(page)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                    ) {
                        if (layout == null) {
                            Text(
                                text = stringResource(R.string.cpl_new_player),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = stringResource(R.string.cpl_new_player_sub),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Button(onClick = onNew) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.cpl_start_from_title))
                            }
                        } else {
                            val inUse = layout.id == inUseId
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (inUse) {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = stringResource(R.string.cpl_in_use),
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                }
                                Text(
                                    text = layout.name,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilledTonalButton(onClick = { onCustomize(layout.id) }) {
                                    Text(stringResource(R.string.cpl_customize))
                                }
                                if (!inUse) {
                                    Button(onClick = { use(layout) }) { Text(stringResource(R.string.cpl_use)) }
                                }
                                Box {
                                    FilledTonalIconButton(onClick = { menuOpen = true }) {
                                        Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.cpl_more))
                                    }
                                    DropdownMenu(
                                        expanded = menuOpen,
                                        onDismissRequest = { menuOpen = false },
                                        shape = RoundedCornerShape(20.dp),
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.cpl_rename)) },
                                            leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                                            onClick = {
                                                menuOpen = false
                                                renaming = layout
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.cpl_duplicate)) },
                                            leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
                                            onClick = {
                                                menuOpen = false
                                                val copy = layout.copy(
                                                    id = PlayerLayout.newId(),
                                                    name = uniqueLayoutName(layout.name, layouts.map { it.name }),
                                                )
                                                store.save(copy)
                                                haptics.confirm()
                                                scope.launch { pager.animateScrollToPage(layouts.size) }
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.cpl_delete)) },
                                            leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null) },
                                            colors = MenuDefaults.itemColors(
                                                textColor = MaterialTheme.colorScheme.error,
                                                leadingIconColor = MaterialTheme.colorScheme.error,
                                            ),
                                            onClick = {
                                                menuOpen = false
                                                deleting = layout
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            PageDots(
                count = layouts.size + 1,
                current = pager.currentPage,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp)
                    .graphicsLayer { alpha = zoom.value.coerceIn(0f, 1f) }
            )
        }
    }

    renaming?.let { layout ->
        RenameLayoutDialog(
            name = layout.name,
            onRename = { name ->
                store.save(layout.copy(name = name))
                renaming = null
            },
            onDismiss = { renaming = null }
        )
    }
    deleting?.let { layout ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.cpl_delete_title, layout.name)) },
            text = { Text(stringResource(R.string.cpl_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    store.delete(layout.id)
                    haptics.confirm()
                    deleting = null
                }) {
                    Text(stringResource(R.string.cpl_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cpl_cancel)) }
            }
        )
    }
}

/** The last card: a new layout, started from any player. */
@Composable
private fun NewLayoutCard() {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.surfaceContainer)
            .border(BorderStroke(2.dp, scheme.outlineVariant), RoundedCornerShape(32.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(scheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    tint = scheme.onPrimaryContainer,
                    modifier = Modifier.size(36.dp)
                )
            }
            Text(
                text = stringResource(R.string.cpl_new_player),
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurface
            )
        }
    }
}

/** Which card is in the middle, as a row of dots; the current one is a pill. */
@Composable
private fun PageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier.semantics { contentDescription = "${current + 1} / $count" },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(count) { index ->
            val selected = index == current
            val width: Dp by animateDpAsState(
                targetValue = if (selected) 18.dp else 6.dp,
                animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                label = "PageDot"
            )
            Box(
                modifier = Modifier
                    .size(width, 6.dp)
                    .clip(CircleShape)
                    .background(if (selected) scheme.primary else scheme.outlineVariant)
            )
        }
    }
}
