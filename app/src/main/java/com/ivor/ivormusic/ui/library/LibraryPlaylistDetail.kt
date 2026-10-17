package com.ivor.ivormusic.ui.library

import com.ivor.ivormusic.ui.components.DismissibleSnackbarHost
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.ivor.ivormusic.R
import com.ivor.ivormusic.ui.components.displaySubtitle
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import androidx.compose.ui.platform.LocalContext
import com.ivor.ivormusic.ui.components.PredictiveBackStack
import com.ivor.ivormusic.data.PlaylistDisplayItem
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.data.SongSource
import com.ivor.ivormusic.data.ThemePreferences
import com.ivor.ivormusic.data.albumArtistLabel
import com.ivor.ivormusic.data.googleImageAtSize
import com.ivor.ivormusic.data.groupSongsByAlbum
import com.ivor.ivormusic.data.groupSongsByArtist
import com.ivor.ivormusic.data.isUnknownAlbum
import com.ivor.ivormusic.data.isUnknownArtist
import com.ivor.ivormusic.data.sortedInAlbumOrder
import com.ivor.ivormusic.ui.artist.ArtistScreen
import com.ivor.ivormusic.ui.components.ExpressivePullToRefresh
import com.ivor.ivormusic.ui.components.VideoThumbnail
import com.ivor.ivormusic.ui.downloads.MusicPlaylistDownloadAction
import com.ivor.ivormusic.ui.player.rememberArtworkColorScheme
import com.ivor.ivormusic.ui.theme.MontserratFamily
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.mutableFloatStateOf
import com.ivor.ivormusic.ui.home.HomeViewModel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import androidx.lifecycle.viewModelScope
import androidx.compose.material.icons.rounded.CloudUpload
import kotlin.math.roundToInt

/**
 * Tap to play, hold for the options sheet.
 *
 * Falls back to a plain `clickable` when there is no long-press action, because
 * `combinedClickable` with a null `onLongClick` still consumes the long press
 * and swallows it from anything underneath.
 */
@Composable
internal fun Modifier.songRowClick(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?
): Modifier {
    if (onLongClick == null) return this.clickable(onClick = onClick)
    val haptics = com.ivor.ivormusic.util.rememberKodaHaptics()
    return this.combinedClickable(
        onClick = onClick,
        onLongClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onLongClick()
        }
    )
}

/** Compact "when was this added" label for the Recently added sort. */
internal fun formatRelativeDate(
    timestamp: Long,
    todayLabel: String,
    yesterdayLabel: String
): String {
    val days = (System.currentTimeMillis() - timestamp) / 86_400_000L
    return when {
        days < 0L -> todayLabel // Clock skew or a file dated in the future
        days == 0L -> todayLabel
        days == 1L -> yesterdayLabel
        days < 7L -> "${days}d ago"
        days < 30L -> "${days / 7}w ago"
        days < 365L -> "${days / 30}mo ago"
        else -> "${days / 365}y ago"
    }
}

internal fun formatSongDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(java.util.Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(java.util.Locale.US, "%d:%02d", minutes, seconds)
    }
}

/**
 * Vibrant floating toolbar shown instead of the play split button while the
 * playlist is in edit mode. Vibrant color is the M3 Expressive cue for a
 * temporary change in page behavior, and it keeps Done reachable mid-list.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ManageModeToolbar(
    hint: String,
    icon: ImageVector,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    HorizontalFloatingToolbar(
        expanded = true,
        modifier = modifier,
        colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
        floatingActionButton = {
            FloatingToolbarDefaults.VibrantFloatingActionButton(onClick = onDone) {
                Icon(Icons.Rounded.Done, contentDescription = "Finish editing")
            }
        }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.padding(start = 8.dp)
        )
        Text(
            text = hint,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
    }
}

@Composable
fun EmptyLibraryState(
    title: String,
    subtitle: String,
    icon: ImageVector = Icons.AutoMirrored.Rounded.List,
    /** Optional recovery action, e.g. a Retry button on a failed fetch. */
    action: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 100.dp)
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
        )
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            action()
        }
    }
}

// ============ PLAYLIST DETAIL SCREEN ============

/** Row height used only to size the loading skeleton; real rows measure themselves. */
internal val TrackRowHeight = 68.dp

/**
 * Section header above the track list. Carries the count and, for editable
 * playlists, the Edit/Done toggle - the mode switch sits with the content it
 * changes rather than in the app bar.
 */
@Composable
internal fun TrackSectionHeader(
    countLabel: String,
    manageEnabled: Boolean,
    canEdit: Boolean,
    onToggleEdit: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Tracks",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                // Stands on the page's ground, not on a theme surface.
                color = playlistOnGround()
            )
            Text(
                text = countLabel,
                style = MaterialTheme.typography.bodySmall,
                color = playlistOnGround().copy(alpha = 0.78f)
            )
        }
        if (canEdit) {
            TextButton(
                onClick = onToggleEdit,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Icon(
                    imageVector = if (manageEnabled) Icons.Rounded.Done else Icons.Rounded.EditNote,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(if (manageEnabled) stringResource(R.string.action_done) else stringResource(R.string.action_edit))
            }
        }
    }
}

/**
 * Placeholder rows shown while the track list is being fetched. Skeletons are
 * the house pattern for content whose shape is known in advance - they say how
 * much is coming, which a bare spinner cannot.
 */
@Composable
internal fun TrackSkeletonList(rows: Int = 6) {
    val transition = rememberInfiniteTransition(label = "trackSkeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.30f,
        targetValue = 0.70f,
        // A pulse is a timed effect, not spatial motion, so a tween is correct here.
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "trackSkeletonAlpha"
    )
    val placeholder = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = alpha)
    Column {
        repeat(rows) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TrackRowHeight)
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(placeholder)
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.62f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(placeholder)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.36f)
                            .height(11.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(placeholder)
                    )
                }
            }
        }
    }
}

/**
 * One track row on the playlist page.
 *
 * Entering edit mode only swaps the trailing controls - rows keep their place
 * and their geometry. Only the row actually being dragged lifts into a card
 * (tonal fill, rounded corners, shadow, slight scale). That is the standard
 * reorder grammar, and it keeps mode entry to a content swap on the handful of
 * visible rows instead of a list-wide layout animation.
 *
 * The drag handle is a real handle: dragging it starts a reorder immediately.
 * A long press anywhere on the row is the accelerator for the same gesture.
 * Because the handle works on touch, no row needs a "hold" caption explaining
 * itself, and the destructive Remove stays a calm outlined glyph instead of a
 * filled block competing with the track for attention.
 *
 * Note on specs: the Dp animations below deliberately use a non-bouncy spring.
 * Corner radius and shadow elevation are clamped non-negative by the framework,
 * and an Expressive *spatial* spec is bouncy - it undershoots past zero on the
 * way back down and throws. Bouncy specs belong on scale, which can overshoot
 * safely.
 */
@Composable
internal fun PlaylistTrackRow(
    song: Song,
    /** The music player is on this song: the row stands out (see below). */
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
    manageEnabled: Boolean,
    reorderEnabled: Boolean,
    isDragging: Boolean,
    dragHandleModifier: Modifier,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    /**
     * Connected-group geometry: the list gives the first row a round top and
     * the last row a round bottom while the seams between rows stay small, so
     * the whole track list reads as one container like the library shortcuts
     * card. A dragged row still lifts into a fully round card of its own.
     */
    topCorner: Dp = 20.dp,
    bottomCorner: Dp = 20.dp
) {
    val liftSpec = spring<Dp>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
    // Rows stand on the page's own ground rather than on a neutral surface
    // role - see [playlistRaisedSurface] for why a `surfaceContainerLow` card
    // lands on the wrong side of a colored ground in a dark theme.
    // The playing row is the segmented list's "selected" state: a solid
    // `primary` fill and a fully rounded segment, so it stands out of the group
    // at a glance and moves when playback moves on. Not primaryContainer: this
    // page's ground *is* that colour (see playlistPageGround), so a container
    // fill vanished into it. `primary` is the cover's colour at the opposite
    // tone - bright on the dark ground, deep on the light one - which is the
    // contrast that survives every cover.
    val highlighted = isCurrent && !isDragging
    val containerColor by animateColorAsState(
        targetValue = when {
            isDragging -> MaterialTheme.colorScheme.secondaryContainer
            highlighted -> MaterialTheme.colorScheme.primary
            else -> playlistRaisedSurface()
        },
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "trackContainer"
    )
    val contentColor by animateColorAsState(
        targetValue = if (highlighted) MaterialTheme.colorScheme.onPrimary else playlistOnGround(),
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "trackContent"
    )
    val topRadius by animateDpAsState(
        targetValue = when {
            isDragging -> 24.dp
            highlighted -> 16.dp
            else -> topCorner
        },
        animationSpec = liftSpec,
        label = "trackTopCorner"
    )
    val bottomRadius by animateDpAsState(
        targetValue = when {
            isDragging -> 24.dp
            highlighted -> 16.dp
            else -> bottomCorner
        },
        animationSpec = liftSpec,
        label = "trackBottomCorner"
    )
    val shadowElevation by animateDpAsState(
        targetValue = if (isDragging) 12.dp else 0.dp,
        animationSpec = liftSpec,
        label = "trackShadow"
    )
    val liftScale by animateFloatAsState(
        targetValue = if (isDragging) 1.03f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "trackLift"
    )

    Surface(
        modifier = modifier.graphicsLayer {
            scaleX = liftScale
            scaleY = liftScale
        },
        color = containerColor,
        shape = RoundedCornerShape(
            topStart = topRadius.coerceAtLeast(0.dp),
            topEnd = topRadius.coerceAtLeast(0.dp),
            bottomStart = bottomRadius.coerceAtLeast(0.dp),
            bottomEnd = bottomRadius.coerceAtLeast(0.dp)
        ),
        tonalElevation = 2.dp,
        shadowElevation = shadowElevation.coerceAtLeast(0.dp)
    ) {
        // A plain Row rather than ListItem: on material3 1.5 alphas a long
        // headline starved the trailing duration to zero width.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = (if (manageEnabled) Modifier else Modifier.songRowClick(onClick = onClick, onLongClick = onLongClick))
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .padding(start = 16.dp, end = if (manageEnabled) 4.dp else 16.dp, top = 8.dp, bottom = 8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (song.albumArtUri != null || song.thumbnailUrl != null) {
                        AsyncImage(
                            model = song.highResThumbnailUrl ?: song.albumArtUri ?: song.thumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // Playing: the bars over a scrim on the artwork, moving
                    // while it plays and resting while paused.
                    androidx.compose.animation.AnimatedVisibility(
                        visible = highlighted,
                        enter = androidx.compose.animation.fadeIn() +
                            androidx.compose.animation.scaleIn(initialScale = 0.6f),
                        exit = androidx.compose.animation.fadeOut() +
                            androidx.compose.animation.scaleOut(targetScale = 0.6f)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)),
                            contentAlignment = Alignment.Center
                        ) {
                            com.ivor.ivormusic.ui.components.PlayingBars(
                                playing = isPlaying,
                                color = MaterialTheme.colorScheme.inverseOnSurface,
                                size = 20.dp
                            )
                        }
                    }
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    song.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = if (highlighted) FontWeight.Bold else FontWeight.SemiBold
                )
                Text(
                    song.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.78f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (manageEnabled) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onRemove, enabled = !isDragging) {
                        Icon(
                            imageVector = Icons.Rounded.RemoveCircleOutline,
                            contentDescription = "Remove ${song.title}",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    if (reorderEnabled) {
                        Box(
                            modifier = dragHandleModifier.size(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DragIndicator,
                                contentDescription = "Reorder ${song.title}",
                                tint = if (isDragging) {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            } else if (song.duration > 0) {
                Text(
                    formatSongDuration(song.duration),
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor.copy(alpha = 0.7f),
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

/**
 * The swipe chrome around one playlist row. Both directions arm from the
 * swipe-actions setting, each naming its own action - remove, play or the
 * options sheet - so a right swipe can play while a left swipe removes.
 *
 * A swipe never keeps a dismissed row: removal leaves the list on its own
 * (a local row with an undo, a remote row through its confirm dialog), and
 * play and options leave nothing behind at all.
 */
@Composable
internal fun PlaylistTrackSwipeContainer(
    startAction: String?,
    endAction: String?,
    onAction: (String) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // `rememberSwipeToDismissBoxState` holds the lambda it was first given,
    // so the actions resolve through updated state - a settings change must
    // reach rows that are already on screen.
    val currentStart by rememberUpdatedState(startAction)
    val currentEnd by rememberUpdatedState(endAction)
    val currentAction by rememberUpdatedState(onAction)
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            val action = when (value) {
                SwipeToDismissBoxValue.StartToEnd -> currentStart
                SwipeToDismissBoxValue.EndToStart -> currentEnd
                SwipeToDismissBoxValue.Settled -> null
            }
            if (action != null) currentAction(action)
            false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        // A null direction does not arm rather than snapping back from a
        // gesture that looked like it worked.
        enableDismissFromStartToEnd = startAction != null,
        enableDismissFromEndToStart = endAction != null,
        backgroundContent = {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
                // A snapshot read, so the backdrop recomposes with every
                // pixel of the drag rather than only past the threshold.
                val offsetPx = runCatching { dismissState.requireOffset() }.getOrDefault(0f)
                PlaylistTrackSwipeBackground(
                    offsetPx = offsetPx,
                    widthPx = widthPx,
                    startAction = startAction,
                    endAction = endAction,
                )
            }
        },
        modifier = modifier,
    ) {
        content()
    }
}

/**
 * What sits behind a playlist row being swiped: a tonal wash in the action's
 * color with the action riding it as a solid pill - icon plus name. The label
 * is the point: a wash alone does not say whether letting go plays the song
 * or removes it.
 *
 * The pill grows out of the drag - small and faint at first touch, full size
 * and ink at the threshold - leans slightly toward the finger, and lifts with
 * a shadow once past it, so "letting go runs this" is visible before it
 * happens. Finger-driven throughout, so values map straight off the offset
 * with no spring or tween involved.
 *
 * @param offsetPx live drag offset, 0 at rest. Its sign picks the side, its
 * magnitude the intensity: 1.0 is the dismiss threshold, half the row.
 */
@Composable
internal fun PlaylistTrackSwipeBackground(
    offsetPx: Float,
    widthPx: Float,
    startAction: String?,
    endAction: String?,
) {
    val action = when {
        offsetPx > 0.5f -> startAction
        offsetPx < -0.5f -> endAction
        else -> null
    }
    val intensity = if (widthPx > 0f) {
        (kotlin.math.abs(offsetPx) / widthPx / 0.5f).coerceIn(0f, 1f)
    } else {
        0f
    }
    // Nothing to show at rest: the row covers this layer entirely, so settled
    // rows in a long list skip the icon and label work below.
    if (intensity <= 0f) return
    val container = when (action) {
        ThemePreferences.PLAYLIST_SWIPE_ACTION_REMOVE -> MaterialTheme.colorScheme.errorContainer
        ThemePreferences.PLAYLIST_SWIPE_ACTION_PLAY -> MaterialTheme.colorScheme.primaryContainer
        ThemePreferences.PLAYLIST_SWIPE_ACTION_QUEUE -> MaterialTheme.colorScheme.secondaryContainer
        ThemePreferences.PLAYLIST_SWIPE_ACTION_OPTIONS -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val content = when (action) {
        ThemePreferences.PLAYLIST_SWIPE_ACTION_REMOVE -> MaterialTheme.colorScheme.onErrorContainer
        ThemePreferences.PLAYLIST_SWIPE_ACTION_PLAY -> MaterialTheme.colorScheme.onPrimaryContainer
        ThemePreferences.PLAYLIST_SWIPE_ACTION_QUEUE -> MaterialTheme.colorScheme.onSecondaryContainer
        ThemePreferences.PLAYLIST_SWIPE_ACTION_OPTIONS -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val icon = when (action) {
        ThemePreferences.PLAYLIST_SWIPE_ACTION_REMOVE -> Icons.Rounded.DeleteOutline
        ThemePreferences.PLAYLIST_SWIPE_ACTION_PLAY -> Icons.Rounded.PlayArrow
        ThemePreferences.PLAYLIST_SWIPE_ACTION_QUEUE -> Icons.AutoMirrored.Rounded.QueueMusic
        else -> Icons.Rounded.MoreVert
    }
    val label = when (action) {
        ThemePreferences.PLAYLIST_SWIPE_ACTION_REMOVE -> stringResource(R.string.swipe_action_remove)
        ThemePreferences.PLAYLIST_SWIPE_ACTION_PLAY -> stringResource(R.string.swipe_action_play)
        ThemePreferences.PLAYLIST_SWIPE_ACTION_QUEUE -> stringResource(R.string.swipe_action_queue)
        ThemePreferences.PLAYLIST_SWIPE_ACTION_OPTIONS -> stringResource(R.string.swipe_action_options)
        else -> null
    }
    // Past the dismiss threshold the finger has committed.
    val committed = intensity >= 1f

    // No clip of its own: the swipe container is already clipped to the row's
    // connected-group shape, so this wash is exactly card-shaped for free.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(container.copy(alpha = 0.35f + 0.65f * intensity)),
        contentAlignment = if (offsetPx < 0f) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = container,
            contentColor = content,
            tonalElevation = 2.dp,
            shadowElevation = if (committed) 6.dp else 0.dp,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .graphicsLayer {
                    val scale = (0.6f + 0.4f * intensity) * (if (committed) 1.08f else 1f)
                    scaleX = scale
                    scaleY = scale
                    alpha = intensity
                    // The pill leans toward the finger without chasing it.
                    translationX = (offsetPx * 0.18f).coerceIn(-72f, 72f)
                }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                if (label != null) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * Playlist / album detail page.
 *
 * Layout follows the house grammar: one hero (the artwork), one primary action
 * (the play split button), page actions in the app bar overflow, and the edit
 * mode toggle in the track section header next to what it changes. Destructive
 * actions (delete playlist, remove track) run calm - no bounce, plain glyphs,
 * and an undo where the change is locally reversible.
 *
 * Reordering measures the real laid-out row geometry from the lazy list rather
 * than assuming a fixed row height, swaps at most once per frame from the
 * frame loop (so a burst of pointer events cannot double-swap against stale
 * layout), and auto-scrolls when the dragged row nears a viewport edge.
 */
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class
)
@Composable
fun PlaylistDetailScreen(
    playlist: PlaylistDisplayItem,
    onBack: () -> Unit,
    onPlayQueue: (List<Song>, Song?) -> Unit,
    /** Play a collection with shuffle mode on; see PlayerViewModel.playQueueShuffled. */
    onShuffleQueue: (List<Song>) -> Unit,
    viewModel: HomeViewModel,
    preloadedSongs: List<Song>? = null,
    isAlbum: Boolean = false,
    onSongLongPress: ((Song) -> Unit)? = null,
    /**
     * Queue a song without playing it. Hoisted like [onSongLongPress] because
     * the queue lives in the PlayerViewModel up in HomeScreen. Null leaves the
     * queue swipe action disarmed. The boolean is play-next: true inserts after
     * the current song, false appends at the end.
     */
    onEnqueueSong: ((Song, Boolean) -> Unit)? = null,
    /**
     * Open the "add from your playlists" flow for this playlist. Only offered
     * on local playlists, which are the ones whose track list this app owns.
     * Deliberately no default: a null default is the half-wired-feature trap,
     * so every caller states whether its surface can host the flow.
     */
    onAddSongsRequest: (() -> Unit)?,
    /**
     * Open the add-songs screen (recents, likes, a YouTube Music search) for
     * this playlist: the plus in the app bar. Offered wherever tracks can be
     * added, a device playlist or one on the account. No default, for the
     * same reason as [onAddSongsRequest].
     */
    onSearchSongsRequest: (() -> Unit)?
) {
    val userPlaylists by viewModel.userPlaylists.collectAsState()
    val localPlaylistIds by viewModel.localPlaylistIds.collectAsState()
    val savedPlaylistIds by viewModel.savedPlaylistIds.collectAsState()
    val resolvedPlaylist = remember(playlist, userPlaylists) {
        userPlaylists.firstOrNull { it.id == playlist.id } ?: playlist
    }
    val isLocalPlaylist = remember(playlist.id, localPlaylistIds, isAlbum) {
        !isAlbum && localPlaylistIds.contains(playlist.id)
    }
    val isSavedPlaylist = savedPlaylistIds.contains(playlist.id)
    // A video-mode device playlist opened from the music Library's "From video
    // mode" section: a device list with nothing on YouTube behind it, so it is
    // neither savable nor a YouTube playlist, and it is edited in video mode.
    val isVideoModePlaylist = com.ivor.ivormusic.data.LocalVideoPlaylistsRepository.isLocal(playlist.id)
    /**
     * Whether this page can be kept.
     *
     * Anything already in the library that is not a saved reference is the
     * user's own - local or on their account - and saving it would be a second
     * copy of something they already have. The synthesized feeds have no
     * playlist behind them to keep at all.
     */
    val canSavePlaylist = remember(playlist.id, userPlaylists, isLocalPlaylist, isSavedPlaylist) {
        !isLocalPlaylist && !isVideoModePlaylist &&
            playlist.id !in NON_SAVABLE_PLAYLIST_IDS &&
            (isSavedPlaylist || userPlaylists.none { it.id == playlist.id })
    }
    // YouTube playlists can be edited through InnerTube. "LM" (Your Likes) only
    // supports song removal (= removing the like), not rename/delete, and the
    // synthesized Supermix ("RTM") and radio mixes are not editable at all.
    val isYouTubePlaylist = !isAlbum && !isLocalPlaylist && !isVideoModePlaylist
    // Whether the library actually holds this playlist. A "PL" prefix says an
    // id is a real YouTube playlist, not that it is yours: every editing
    // affordance below is a write, and this page is reached from search and
    // from a shared link as well as from the Library, so without this a
    // playlist somebody else owns offered rename, delete and track editing -
    // all of which fail at the endpoint, after the UI has already moved. Being
    // absent from the library is only ever grounds for offering less, so the
    // moment before the list loads costs a hidden button, not a failed write.
    val isInLibrary = userPlaylists.any { it.id == playlist.id }
    val canEditYouTubeSongs = isYouTubePlaylist && !isSavedPlaylist &&
        (playlist.id == "LM" || (isInLibrary && playlist.id.startsWith("PL")))
    // A saved playlist starts with "PL" like any other, but it belongs to
    // whoever made it: rename and delete would be writes the account has no
    // rights to, and offering them is the UI promising something it cannot do.
    val canRenameDeleteYouTube = isYouTubePlaylist && isInLibrary &&
        playlist.id.startsWith("PL") && !isSavedPlaylist
    // Real "PL" playlists can be reordered remotely via edit_playlist moves;
    // "LM" (Your Likes) only supports removal.
    val canReorderRemote = isYouTubePlaylist && isInLibrary && playlist.id.startsWith("PL")
    val canEditSongs = isLocalPlaylist || canEditYouTubeSongs

    val trackKeyPrefix = "playlist_${resolvedPlaylist.id}"
    // Which row is playing. By song id: the page cannot tell which occurrence
    // the queue is on, so a song listed twice marks both.
    val nowPlaying = com.ivor.ivormusic.ui.components.LocalNowPlaying.current
    var songRows by remember(resolvedPlaylist.id) {
        val initialSongs = preloadedSongs
            ?.let { if (isAlbum) it.sortedInAlbumOrder() else it }
            .orEmpty()
        mutableStateOf(playlistSongRows(initialSongs, trackKeyPrefix))
    }
    val songs = remember(songRows) { songRows.map { it.song } }
    val isFetching = remember { mutableStateOf(songs.isEmpty()) }
    var loadAttempted by remember { mutableStateOf(preloadedSongs != null) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var songPendingRemoval by remember { mutableStateOf<Song?>(null) }
    var showOverflow by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var isReorderMode by remember { mutableStateOf(false) }
    var descriptionExpanded by remember { mutableStateOf(false) }
    var reorderDirty by remember { mutableStateOf(false) }
    // Fetched lazily the first time edit mode opens. The ids are attached to
    // PlaylistSongRow occurrences rather than keyed by videoId, because a
    // playlist may legitimately contain the same video twice.
    var remoteRowIdsLoaded by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val haptics = com.ivor.ivormusic.util.rememberKodaHaptics()
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val isYouTubeConnected by viewModel.isYouTubeConnected.collectAsState()
    var isUploading by remember(playlist.id) { mutableStateOf(false) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    val shareContext = LocalContext.current

    // Swipe actions, read live so a settings change reaches rows that are
    // already on screen - no DI here, one instance per screen.
    val swipePrefs = remember(shareContext) { ThemePreferences(shareContext) }
    val swipeEnabled by swipePrefs.playlistSwipeEnabled.collectAsState()
    val swipeStartSetting by swipePrefs.playlistSwipeStartAction.collectAsState()
    val swipeEndSetting by swipePrefs.playlistSwipeEndAction.collectAsState()

    // Search state
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    val searchFocus = remember { FocusRequester() }

    // Cover art. Only a playlist the user made has artwork of its own to
    // replace - a YouTube playlist's cover belongs to whoever published it.
    val hasCustomCover = resolvedPlaylist.thumbnailUrl?.contains("/custom_") == true
    val coverPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        // Null is the user backing out of the picker, which is not a failure
        // and must not clear the cover they already have.
        if (uri != null) viewModel.setLocalPlaylistCover(resolvedPlaylist.id, uri)
    }
    val pickCover: () -> Unit = {
        coverPicker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("audio/x-mpegurl")
    ) { uri ->
        if (uri != null) scope.launch {
            val ok = viewModel.exportLocalPlaylist(songRows.map { it.song }, uri)
            snackbarHostState.showSnackbar(
                shareContext.getString(if (ok) R.string.pl_export_done else R.string.pl_export_failed)
            )
        }
    }

    // Drag state. The dragged row is addressed by its lazy list key so the
    // reorder maths can read real offsets and sizes out of the layout.
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var dragStartIndex by remember { mutableIntStateOf(-1) }

    val filteredRows = remember(songRows, searchQuery) {
        if (searchQuery.isBlank()) songRows
        else songRows.filter { row ->
            row.song.title.contains(searchQuery, ignoreCase = true) ||
                row.song.artist.contains(searchQuery, ignoreCase = true)
        }
    }
    val filteredSongs = remember(filteredRows) { filteredRows.map { it.song } }

    // How far the hero header has scrolled away, 0..1. Drives the app bar fade
    // and the artwork parallax continuously - a boolean threshold makes the bar
    // pop, which is exactly the snap the motion rules forbid.
    //
    // These stay as State objects and are only ever read inside graphicsLayer /
    // drawBehind lambdas. Reading them in the composable body would recompose
    // the whole page (LazyColumn content included) on every scroll frame; read
    // from a deferred lambda, a scroll only re-runs the layer or the draw.
    val headerCollapse = remember {
        derivedStateOf {
            val header = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 }
            when {
                header == null -> 1f
                header.size <= 0 -> 0f
                else -> (-header.offset.toFloat() / header.size).coerceIn(0f, 1f)
            }
        }
    }
    val barAlpha = remember {
        derivedStateOf {
            if (isSearchActive) 1f else (headerCollapse.value / 0.55f).coerceIn(0f, 1f)
        }
    }

    LaunchedEffect(resolvedPlaylist.id) {
        if (preloadedSongs == null) {
            isFetching.value = true
            songRows = playlistSongRows(
                viewModel.fetchPlaylistSongs(resolvedPlaylist.id),
                trackKeyPrefix
            )
            isFetching.value = false
            loadAttempted = true
        }
    }

    LaunchedEffect(resolvedPlaylist.id, isLocalPlaylist, isFetching.value) {
        if (!isLocalPlaylist || isFetching.value) return@LaunchedEffect
        val found = viewModel.backfillLocalPlaylistDurations(resolvedPlaylist.id, songRows.map { it.song })
        if (found.isNotEmpty()) {
            songRows = songRows.map { row ->
                found[row.song.id]?.takeIf { row.song.duration <= 0 }
                    ?.let { row.copy(song = row.song.copy(duration = it)) } ?: row
            }
        }
    }

    LaunchedEffect(isReorderMode) {
        if (isReorderMode && canReorderRemote && !remoteRowIdsLoaded) {
            val setVideoIds = viewModel.fetchYouTubePlaylistSetVideoIds(resolvedPlaylist.id)
            songRows = attachPlaylistSetVideoIds(songRows, setVideoIds)
            // A failed/empty fetch may be retried the next time edit mode is
            // opened; a successful mapping is stable for this screen session.
            remoteRowIdsLoaded = setVideoIds.isNotEmpty()
        }
    }

    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            withFrameNanos { }
            runCatching { searchFocus.requestFocus() }
        } else {
            focusManager.clearFocus()
        }
    }

    val commitLocalOrder: () -> Unit = {
        if (reorderDirty) {
            viewModel.replaceLocalPlaylistSongs(
                resolvedPlaylist.id,
                songRows.map { it.song }
            )
            reorderDirty = false
        }
    }

    val exitReorderMode: () -> Unit = {
        isReorderMode = false
        draggingKey = null
        dragOffsetY = 0f
        commitLocalOrder()
    }

    // Back leaves the transient modes before it leaves the page.
    BackHandler(enabled = isSearchActive || isReorderMode) {
        if (isSearchActive) {
            isSearchActive = false
            searchQuery = ""
        } else {
            exitReorderMode()
        }
    }

    // Shared end-of-drag handler: local playlists persist the whole new order,
    // YouTube playlists send one edit_playlist move for the dragged row (and
    // resync from the server if the row is not addressable or the move fails).
    val finishReorderDrag: () -> Unit = finish@{
        val movedKey = draggingKey
        val startIndex = dragStartIndex
        draggingKey = null
        dragOffsetY = 0f
        dragStartIndex = -1
        if (movedKey != null) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        if (isLocalPlaylist) {
            commitLocalOrder()
            return@finish
        }
        if (movedKey == null || startIndex < 0) return@finish
        val newIndex = songRows.indexOfFirst { it.key == movedKey }
        if (newIndex < 0 || newIndex == startIndex) return@finish
        val movedSetId = songRows[newIndex].setVideoId
        val successor = songRows.getOrNull(newIndex + 1)
        val successorSetId = successor?.setVideoId
        if (movedSetId == null || (successor != null && successorSetId == null)) {
            // Row beyond the first browse page - no setVideoId to move it by,
            // so restore the server's order instead of guessing.
            scope.launch {
                songRows = playlistSongRows(
                    viewModel.fetchPlaylistSongs(resolvedPlaylist.id),
                    trackKeyPrefix
                )
                remoteRowIdsLoaded = false
            }
        } else {
            scope.launch {
                val ok = viewModel.moveSongInYouTubePlaylist(
                    resolvedPlaylist.id, movedSetId, successorSetId
                )
                if (!ok) {
                    songRows = playlistSongRows(
                        viewModel.fetchPlaylistSongs(resolvedPlaylist.id),
                        trackKeyPrefix
                    )
                    remoteRowIdsLoaded = false
                }
            }
        }
    }

    val beginDrag: (PlaylistSongRow) -> Unit = { row ->
        draggingKey = row.key
        dragOffsetY = 0f
        dragStartIndex = songRows.indexOfFirst { it.key == row.key }
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    // Swaps the dragged row with whichever row its centre currently covers.
    // Offsets come from the live layout, so rows of any height (edit mode adds
    // padding) stay glued to the finger. Called once per frame from the drag
    // loop below, never straight from a pointer event, because layoutInfo only
    // refreshes after relayout and two swaps against one snapshot would jump.
    val settleSwap: () -> Unit = settle@{
        val key = draggingKey ?: return@settle
        val info = listState.layoutInfo
        val current = info.visibleItemsInfo.firstOrNull { it.key == key } ?: return@settle
        val centre = current.offset + current.size / 2f + dragOffsetY
        val target = info.visibleItemsInfo.firstOrNull { candidate ->
            val candidateKey = candidate.key
            candidateKey is String &&
                candidateKey != key &&
                candidateKey.startsWith(trackKeyPrefix) &&
                centre >= candidate.offset &&
                centre <= candidate.offset + candidate.size
        } ?: return@settle
        val from = songRows.indexOfFirst { it.key == key }
        val to = songRows.indexOfFirst { it.key == target.key }
        if (from < 0 || to < 0 || from == to) return@settle
        songRows = songRows.toMutableList().apply { add(to, removeAt(from)) }
        // The row's settled slot just moved to the target's; cancel that out of
        // the drag offset so the row does not visibly jump under the finger.
        dragOffsetY -= (target.offset - current.offset).toFloat()
        if (isLocalPlaylist) reorderDirty = true
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    // Per-frame drag loop: auto-scroll near the viewport edges, then resolve a
    // swap. Running the swap here (not in onDrag) also means a finger parked at
    // the edge keeps reordering while the list scrolls under it.
    LaunchedEffect(draggingKey) {
        val key = draggingKey ?: return@LaunchedEffect
        val edge = with(density) { 96.dp.toPx() }
        val maxStep = with(density) { 14.dp.toPx() }
        while (isActive) {
            withFrameNanos { }
            val info = listState.layoutInfo
            val current = info.visibleItemsInfo.firstOrNull { it.key == key }
            if (current != null) {
                val top = current.offset + dragOffsetY
                val bottom = top + current.size
                val delta = when {
                    bottom > info.viewportEndOffset - edge ->
                        (bottom - (info.viewportEndOffset - edge)).coerceAtMost(maxStep)
                    top < info.viewportStartOffset + edge ->
                        (top - (info.viewportStartOffset + edge)).coerceAtLeast(-maxStep)
                    else -> 0f
                }
                if (delta != 0f) listState.scrollBy(delta)
            }
            settleSwap()
        }
    }

    val sortLocal: (PlaylistSortAction) -> Unit = { action ->
        val previous = songRows
        songRows = when (action) {
            PlaylistSortAction.Title -> songRows.sortedBy { it.song.title.lowercase() }
            PlaylistSortAction.Artist -> songRows.sortedWith(
                compareBy({ it.song.artist.lowercase() }, { it.song.title.lowercase() })
            )
            PlaylistSortAction.Album -> songRows.sortedWith(
                compareBy({ it.song.album.lowercase() }, { it.song.title.lowercase() })
            )
            PlaylistSortAction.Reverse -> songRows.reversed()
            PlaylistSortAction.Shuffle -> songRows.shuffled()
        }
        if (songRows != previous) {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            viewModel.replaceLocalPlaylistSongs(resolvedPlaylist.id, songRows.map { it.song })
            reorderDirty = false
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = shareContext.getString(R.string.lib_sorted),
                    actionLabel = shareContext.getString(R.string.undo),
                    duration = SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) {
                    songRows = previous
                    viewModel.replaceLocalPlaylistSongs(resolvedPlaylist.id, previous.map { it.song })
                }
            }
        }
    }

    val removeSong: (PlaylistSongRow) -> Unit = { row ->
        val song = row.song
        val previous = songRows
        songRows = songRows.filterNot { it.key == row.key }
        if (isLocalPlaylist) {
            viewModel.replaceLocalPlaylistSongs(
                resolvedPlaylist.id,
                songRows.map { it.song }
            )
            reorderDirty = false
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = "Removed ${song.title}",
                    actionLabel = shareContext.getString(R.string.undo),
                    duration = SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) {
                    songRows = previous
                    viewModel.replaceLocalPlaylistSongs(
                        resolvedPlaylist.id,
                        previous.map { it.song }
                    )
                }
            }
        } else {
            // Remote removal is not locally reversible (a re-add would land at
            // the end of the playlist) and it is a write against the signed-in
            // YouTube account - for "LM" it removes the like outright - so it
            // is confirmed rather than offered with an undo.
            songPendingRemoval = song
        }
    }

    // A swipe direction names its action in settings. Remove only arms where
    // this page can actually remove, queue only where someone above handed a
    // queue over, options only where a sheet exists to open. A YouTube list
    // that is not yours cannot be edited from here at all, so only the two
    // actions that leave the playlist alone arm there: play it, or queue
    // from it.
    val queueOnly = isYouTubePlaylist && !canEditSongs
    val resolveSwipeSetting: (String) -> String? = { setting ->
        when {
            setting == ThemePreferences.PLAYLIST_SWIPE_ACTION_OFF -> null
            setting == ThemePreferences.PLAYLIST_SWIPE_ACTION_REMOVE && !canEditSongs -> null
            setting == ThemePreferences.PLAYLIST_SWIPE_ACTION_QUEUE && onEnqueueSong == null -> null
            setting == ThemePreferences.PLAYLIST_SWIPE_ACTION_OPTIONS &&
                (queueOnly || onSongLongPress == null) -> null
            setting != ThemePreferences.PLAYLIST_SWIPE_ACTION_PLAY &&
                setting != ThemePreferences.PLAYLIST_SWIPE_ACTION_QUEUE &&
                setting != ThemePreferences.PLAYLIST_SWIPE_ACTION_REMOVE &&
                setting != ThemePreferences.PLAYLIST_SWIPE_ACTION_OPTIONS -> null
            else -> setting
        }
    }

    // The page dresses in the cover. Palette accents extracted from the
    // artwork replace the theme's accent roles while surfaces stay on the app
    // theme (the player's own rememberArtworkColorScheme, reused), so Play,
    // Save, the FAB and the wash behind the list all match the art. The base
    // theme holds while extraction runs, when the art has no usable swatches,
    // and when the user has turned artwork colors off - someone who switched
    // that off did so because they want the app's own palette, and a page
    // that tints itself from the cover anyway is the switch not working.
    val heroArt = resolvedPlaylist.thumbnailUrl?.takeIf { it.isNotBlank() && it != "null" }
    val artworkColorsEnabled by swipePrefs.playerArtworkColors.collectAsState()
    val artworkScheme = rememberArtworkColorScheme(
        enabled = artworkColorsEnabled,
        albumArtUri = heroArt,
        base = MaterialTheme.colorScheme
    )
    MaterialTheme(colorScheme = artworkScheme) {
    Scaffold(
        topBar = {
            // The bar settles into the page's ground rather than onto it: an
            // untinted `surface` slab fading in over a cover-colored page
            // reads as a grey card sliding over it. It is the ground exactly,
            // so what the bar separates is the scrolling rows passing under
            // it, not itself from the page.
            val barColor = playlistPageGround()
            Box(modifier = Modifier.drawBehind { drawRect(barColor, alpha = barAlpha.value) }) {
                Column {
                    TopAppBar(
                        title = {
                            Text(
                                resolvedPlaylist.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.graphicsLayer {
                                    alpha = barAlpha.value
                                    translationY = (1f - barAlpha.value) * 20.dp.toPx()
                                }
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = {
                                commitLocalOrder()
                                onBack()
                            }) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                            }
                        },
                        actions = {
                            // Your Likes takes removals only: a song joins it
                            // by being liked, not by being added.
                            if (onSearchSongsRequest != null && !isSearchActive &&
                                (isLocalPlaylist || canReorderRemote)
                            ) {
                                IconButton(onClick = {
                                    if (isReorderMode) exitReorderMode()
                                    onSearchSongsRequest()
                                }) {
                                    Icon(
                                        Icons.Rounded.Add,
                                        contentDescription = stringResource(R.string.import_title)
                                    )
                                }
                            }
                            IconButton(onClick = {
                                if (isReorderMode) exitReorderMode()
                                isSearchActive = !isSearchActive
                                if (!isSearchActive) searchQuery = ""
                            }) {
                                Icon(
                                    imageVector = if (isSearchActive) Icons.Rounded.Close else Icons.Rounded.Search,
                                    contentDescription = if (isSearchActive) stringResource(R.string.cd_clear_search) else stringResource(R.string.lib_search_in_playlist)
                                )
                            }
                            // Page-level actions live behind one overflow so the
                            // bar keeps to the 1-2 visible actions M3E asks for,
                            // and Delete is not a peer of Rename on the page.
                            val hasRename = isLocalPlaylist || canRenameDeleteYouTube
                            val hasShare = !isLocalPlaylist && !isVideoModePlaylist &&
                                resolvedPlaylist.id !in NON_SHAREABLE_PLAYLIST_IDS
                            // Anything that is not already the user's own local
                            // playlist can be copied into one, once its tracks
                            // are on screen - an album and a generated mix
                            // included, since a snapshot of either is a
                            // perfectly good starting point for a playlist of
                            // your own. There is nothing to copy before the
                            // list loads, so the row waits for it rather than
                            // creating an empty playlist.
                            val canCopyToLocal = !isLocalPlaylist && songs.isNotEmpty()
                            val radioSeed = songs.firstOrNull { it.source == SongSource.YOUTUBE }
                            if (hasRename || hasShare || canCopyToLocal || radioSeed != null) {
                                Box {
                                    IconButton(onClick = { showOverflow = true }) {
                                        Icon(Icons.Rounded.MoreVert, "More options")
                                    }
                                    DropdownMenu(
                                        expanded = showSortMenu,
                                        onDismissRequest = { showSortMenu = false }
                                    ) {
                                        PlaylistSortAction.entries.forEach { action ->
                                            DropdownMenuItem(
                                                text = { Text(stringResource(action.label)) },
                                                onClick = {
                                                    showSortMenu = false
                                                    sortLocal(action)
                                                }
                                            )
                                        }
                                    }
                                    DropdownMenu(
                                        expanded = showOverflow,
                                        onDismissRequest = { showOverflow = false }
                                    ) {
                                        if (radioSeed != null) {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.ar_start_radio)) },
                                                leadingIcon = { Icon(Icons.Rounded.Radio, null) },
                                                onClick = {
                                                    showOverflow = false
                                                    scope.launch {
                                                        val radio = viewModel.getRadioSongs(radioSeed.id)
                                                        if (radio.isNotEmpty()) {
                                                            onPlayQueue(listOf(radioSeed) + radio, radioSeed)
                                                        }
                                                    }
                                                },
                                            )
                                        }
                                        if (hasRename) {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.lib_edit_details)) },
                                                leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                                                onClick = {
                                                    showOverflow = false
                                                    showEditDialog = true
                                                }
                                            )
                                        }
                                        if (isLocalPlaylist && songs.size > 1) {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.lib_sort_playlist)) },
                                                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Sort, null) },
                                                onClick = {
                                                    showOverflow = false
                                                    showSortMenu = true
                                                }
                                            )
                                        }
                                        if (isLocalPlaylist && songs.isNotEmpty()) {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.pl_export)) },
                                                leadingIcon = { Icon(Icons.Rounded.FileUpload, null) },
                                                onClick = {
                                                    showOverflow = false
                                                    val base = (resolvedPlaylist.name ?: "playlist")
                                                        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                                                    exportLauncher.launch("$base.m3u8")
                                                }
                                            )
                                        }
                                        if (isLocalPlaylist && onAddSongsRequest != null) {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.lib_add_from_playlists)) },
                                                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null) },
                                                onClick = {
                                                    showOverflow = false
                                                    onAddSongsRequest()
                                                }
                                            )
                                        }
                                        if (isLocalPlaylist) {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.lib_change_cover)) },
                                                leadingIcon = { Icon(Icons.Rounded.PhotoCamera, null) },
                                                onClick = {
                                                    showOverflow = false
                                                    pickCover()
                                                }
                                            )
                                            // Only offered once there is
                                            // something to undo: on a generated
                                            // cover it would do nothing visible.
                                            if (hasCustomCover) {
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(R.string.lib_reset_cover)) },
                                                    leadingIcon = { Icon(Icons.Rounded.Refresh, null) },
                                                    onClick = {
                                                        showOverflow = false
                                                        viewModel.resetLocalPlaylistCover(resolvedPlaylist.id)
                                                    }
                                                )
                                            }
                                        }
                                        if (isLocalPlaylist && isYouTubeConnected && !isUploading &&
                                            songs.any { it.source == com.ivor.ivormusic.data.SongSource.YOUTUBE }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.lib_upload_ytm)) },
                                                leadingIcon = { Icon(Icons.Rounded.CloudUpload, null) },
                                                onClick = {
                                                    showOverflow = false
                                                    isUploading = true
                                                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                                    scope.launch {
                                                        snackbarHostState.showSnackbar(
                                                            shareContext.getString(R.string.lib_upload_ytm_started)
                                                        )
                                                    }
                                                    // Uploaded in the ViewModel's scope, so leaving
                                                    // the page does not abandon it halfway; only
                                                    // the result message waits on this screen.
                                                    val upload = viewModel.viewModelScope.async {
                                                        viewModel.uploadLocalPlaylistToYouTube(
                                                            name = resolvedPlaylist.name ?: "",
                                                            description = resolvedPlaylist.description,
                                                            songs = songs,
                                                        )
                                                    }
                                                    scope.launch {
                                                        val (result, skipped) = upload.await()
                                                        isUploading = false
                                                        val message = when {
                                                            result == null -> shareContext.getString(R.string.lib_upload_ytm_failed)
                                                            result.uploaded < result.total -> shareContext.getString(
                                                                R.string.lib_upload_ytm_partial, result.uploaded, result.total
                                                            )
                                                            skipped > 0 -> shareContext.resources.getQuantityString(
                                                                R.plurals.lib_upload_ytm_done_skipped, skipped, skipped
                                                            )
                                                            else -> shareContext.getString(R.string.lib_upload_ytm_done)
                                                        }
                                                        snackbarHostState.showSnackbar(message)
                                                    }
                                                }
                                            )
                                        }
                                        if (canCopyToLocal) {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.lib_copy_editable)) },
                                                leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) },
                                                onClick = {
                                                    showOverflow = false
                                                    haptics.performHapticFeedback(
                                                        HapticFeedbackType.Confirm
                                                    )
                                                    scope.launch {
                                                        val copied = viewModel.copyPlaylistToLocal(
                                                            name = resolvedPlaylist.name,
                                                            description = resolvedPlaylist.description,
                                                            songs = songs
                                                        )
                                                        if (copied != null) {
                                                            snackbarHostState.showSnackbar(
                                                                shareContext.getString(
                                                                    R.string.lib_copy_editable_done
                                                                )
                                                            )
                                                        }
                                                    }
                                                }
                                            )
                                        }
                                        if (hasShare) {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.video_options_share)) },
                                                leadingIcon = { Icon(Icons.Rounded.Share, null) },
                                                onClick = {
                                                    showOverflow = false
                                                    val shareUrl = if (isAlbum) {
                                                        "https://music.youtube.com/browse/${resolvedPlaylist.id}"
                                                    } else {
                                                        "https://music.youtube.com/playlist?list=${resolvedPlaylist.id}"
                                                    }
                                                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                                        type = "text/plain"
                                                        putExtra(android.content.Intent.EXTRA_TEXT, shareUrl)
                                                    }
                                                    shareContext.startActivity(
                                                        android.content.Intent.createChooser(send, shareContext.getString(R.string.share_playlist_chooser))
                                                    )
                                                }
                                            )
                                        }
                                        if (hasRename) {
                                            HorizontalDivider()
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        stringResource(R.string.delete_playlist),
                                                        color = MaterialTheme.colorScheme.error
                                                    )
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        Icons.Rounded.Delete,
                                                        null,
                                                        tint = MaterialTheme.colorScheme.error
                                                    )
                                                },
                                                onClick = {
                                                    showOverflow = false
                                                    showDeleteDialog = true
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            scrolledContainerColor = Color.Transparent
                        )
                    )
                    // Pinned rather than sticky in the list: the field is always
                    // reachable while searching, and it composes immediately so
                    // it can take focus without waiting for a scroll.
                    // Default (non-bouncy) size springs on purpose: an
                    // Expressive spatial spec bounces, and a bouncing height
                    // undershoots negative. Bounce belongs on scale and
                    // translation, not on anything that measures.
                    AnimatedVisibility(
                        visible = isSearchActive,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Find in ${if (isAlbum) "album" else "playlist"}") },
                            leadingIcon = { Icon(Icons.Rounded.Search, null) },
                            trailingIcon = if (searchQuery.isNotEmpty()) {
                                {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Rounded.Close, "Clear search")
                                    }
                                }
                            } else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 12.dp)
                                .focusRequester(searchFocus),
                            shape = RoundedCornerShape(28.dp),
                            colors = TextFieldDefaults.colors(
                                // Stands on the ground the same way the rows
                                // it filters do.
                                focusedContainerColor = playlistRaisedSurface(),
                                unfocusedContainerColor = playlistRaisedSurface(),
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent
                            ),
                            singleLine = true
                        )
                    }
                }
            }
        },
        snackbarHost = {
            DismissibleSnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(
                    bottom = com.ivor.ivormusic.ui.components.LocalBottomOverlayInset.current
                )
            )
        },
        floatingActionButton = {
            if (isReorderMode) {
                // Vibrant floating toolbar = the M3 Expressive signal for a
                // temporary edit mode; stays reachable however far the list scrolls
                val canReorder = isLocalPlaylist ||
                    (canReorderRemote && songRows.any { it.setVideoId != null })
                ManageModeToolbar(
                    hint = if (canReorder) stringResource(R.string.lib_drag_hint) else stringResource(R.string.lib_tap_remove_hint),
                    icon = if (canReorder) Icons.Rounded.DragIndicator else Icons.Rounded.RemoveCircleOutline,
                    onDone = exitReorderMode,
                    modifier = Modifier.padding(
                        bottom = com.ivor.ivormusic.ui.components.LocalBottomOverlayInset.current
                    )
                )
            }
        }
    ) { _ ->
        // The scaffold's inset is deliberately dropped: the cover draws under
        // the transparent top bar and the status bar, which is the whole point
        // of the hero, and the list carries its own bottom padding for the
        // navigation bar and the floating overlays.
        // The cover's color is the page, not a flourish at the top of it: one
        // flat ground the artwork blends into and the page then carries to the
        // bottom, however long the track list is. See [playlistPageGround] for
        // why this is not a gradient.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(playlistPageGround())
        ) {
        // Enough scroll clearance for the floating overlays plus the FAB
        val listBottomPadding = com.ivor.ivormusic.ui.components.LocalBottomOverlayInset.current +
            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 88.dp
        // The page's items, as one block both layouts draw from: a phone gets
        // header and tracks in one list, a wide pane gets the header in a
        // column of its own beside the tracks (see the layout at the end).
        val pageItems: androidx.compose.foundation.lazy.LazyListScope.(
            showHeader: Boolean,
            showTracks: Boolean
        ) -> Unit = { showHeader, showTracks ->
            // Hero: the cover fills the top of the page edge to edge under
            // the status bar, then dissolves into the page's own tint, with
            // the title, the facts and the controls stacked on that tint
            // below it - the artist page's immersive top, for collections.
            //
            // The title sits *below* the cover rather than on it. On the
            // artist page a name can be set over a photo because a portrait
            // has a predictable shape; a playlist cover is any image at all,
            // including a white one, and no scrim tuning makes type over it
            // reliable across every cover. Below it, the title is drawn on
            // the page's own tint, so it reads in both themes on all of the
            // app's palettes.
            if (showHeader) item {
                // The status-bar scrim is drawn in the page's ground rather
                // than in the bare background, so the band the app bar fades
                // in over is already the page's own color.
                val ground = playlistPageGround()
                val windowHeight = com.ivor.ivormusic.ui.theme.windowDpSize().height
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Beside the tracks the cover is an object in a column,
                    // not a banner across the page: square, rounded, inset.
                    if (!showTracks) PlaylistSideCover(
                        heroArt = heroArt,
                        isAlbum = isAlbum,
                        onPickCover = if (isLocalPlaylist) pickCover else null
                    ) else BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        // Near-square on a phone, but never more than a
                        // comfortable share of the window: the title, the
                        // controls and the first track all have to fit under
                        // it, in landscape as much as in portrait.
                        val heroHeight = (maxWidth * 0.94f)
                            .coerceAtMost(windowHeight * 0.44f)
                            .coerceAtLeast(220.dp)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(heroHeight)
                                // The cover drifts under the scroll; clipped
                                // here so it never draws over the list.
                                .clipToBounds()
                        ) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .graphicsLayer {
                                        // Scroll-driven, not time-driven: the
                                        // cover trails the list rather than
                                        // animating on its own, so there is
                                        // nothing here to reduce.
                                        val collapse = headerCollapse.value
                                        translationY = collapse * size.height * 0.24f
                                        alpha = 1f - collapse * 0.55f
                                    }
                                    .then(
                                        // The cover is the affordance: a whole
                                        // banner of target, exactly where
                                        // someone looking to change the
                                        // picture looks.
                                        if (isLocalPlaylist) {
                                            Modifier.clickable(onClick = pickCover)
                                        } else Modifier
                                    )
                            ) {
                                if (heroArt != null) {
                                    // Asked for at the size it is drawn and
                                    // layered over the feed-size original, so
                                    // the banner is sharp rather than a 120px
                                    // playlist thumbnail stretched over it.
                                    VideoThumbnail(
                                        thumbnailUrl = heroArt,
                                        highResThumbnailUrl = googleImageAtSize(heroArt, HERO_COVER_PX)
                                            ?.takeIf { it != heroArt },
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        showProgress = false,
                                        placeholderColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                    )
                                } else {
                                    // No cover anywhere: the theme's own
                                    // containers with the collection's glyph
                                    // cut into an Expressive shape, so the
                                    // page still has a top.
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                Brush.linearGradient(
                                                    listOf(
                                                        MaterialTheme.colorScheme.primaryContainer,
                                                        MaterialTheme.colorScheme.tertiaryContainer
                                                    )
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Surface(
                                            shape = MaterialShapes.Cookie9Sided.toShape(),
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f),
                                            modifier = Modifier.size(150.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = if (isAlbum) Icons.Rounded.Album else Icons.AutoMirrored.Rounded.PlaylistPlay,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(64.dp),
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Where the cover hands the page over: a plain
                            // fade, in the page's exact ground color.
                            //
                            // This was a `DstIn` mask that erased the cover's
                            // foot instead. [scar] On screen it composited to
                            // *black* rather than to transparency and ended
                            // the cover on a hard dark edge - the layer was
                            // not isolating the way `CompositingStrategy`
                            // promises. Painting the fade is only correct
                            // because the ground is one flat color: there is
                            // exactly one value to match, so a plain SrcOver
                            // gradient lands on it precisely, with no blend
                            // mode and no offscreen buffer to go wrong.
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .background(
                                        Brush.verticalGradient(
                                            0f to ground.copy(alpha = 0f),
                                            0.58f to ground.copy(alpha = 0f),
                                            0.86f to ground.copy(alpha = 0.85f),
                                            1f to ground
                                        )
                                    )
                            )

                            // Top fade: back, search and the overflow need
                            // ground to sit on whatever the cover is. Faded to
                            // the same color at zero alpha rather than to
                            // Transparent, which is transparent *black* and
                            // greys the band in a light theme.
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(132.dp)
                                    .align(Alignment.TopCenter)
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                ground.copy(alpha = 0.55f),
                                                ground.copy(alpha = 0f)
                                            )
                                        )
                                    )
                            )

                            // Badge on the cover's corner, so the artwork
                            // reads as editable without a caption telling
                            // people to tap it.
                            if (isLocalPlaylist) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    // It sits on a layer that moves with every
                                    // scroll frame, and a shadow under one of
                                    // those shimmers instead of grounding it.
                                    shadowElevation = 0.dp,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(end = 20.dp, bottom = 12.dp)
                                        .clickable(onClick = pickCover)
                                ) {
                                    Icon(
                                        Icons.Rounded.PhotoCamera,
                                        contentDescription = stringResource(R.string.lib_change_cover_art),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier
                                            .padding(10.dp)
                                            .size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Title and facts, set the way the artist page sets a
                    // name: poster face, start-aligned, sized down rather
                    // than cut when the name is a long one.
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = PLAYLIST_GUTTER)
                            .padding(top = 14.dp)
                    ) {
                        Text(
                            text = resolvedPlaylist.name,
                            style = MaterialTheme.typography.displaySmall.copy(
                                fontFamily = MontserratFamily,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.02).em,
                                lineHeight = 1.08.em
                            ),
                            color = playlistOnGround(),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            // Big for "Focus", smaller rather than cut for
                            // "Songs to sing in the shower at 3am".
                            autoSize = TextAutoSize.StepBased(
                                minFontSize = 24.sp,
                                maxFontSize = 38.sp,
                                stepSize = 2.sp
                            )
                        )

                        val totalDurationLabel = remember(songs) {
                            formatTotalDuration(songs.sumOf { it.duration })
                        }
                        val releaseType = songs.firstOrNull()?.releaseType ?: resolvedPlaylist.releaseType
                        val releaseYear = songs.firstOrNull()?.releaseYear ?: resolvedPlaylist.releaseYear
                        val kindLabel = stringResource(releaseType?.labelRes ?: R.string.label_album)
                        Text(
                            text = listOfNotNull(
                                if (isAlbum) kindLabel else stringResource(R.string.label_playlist),
                                resolvedPlaylist.uploaderName.takeIf { isAlbum && it.isNotBlank() },
                                releaseYear?.toString()?.takeIf { isAlbum },
                                if (songs.size == 1) "1 track" else "${songs.size} tracks",
                                totalDurationLabel
                            ).joinToString("  ·  "),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            color = playlistOnGround().copy(alpha = 0.8f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    // Playback is the page's one primary action, directly
                    // under the facts it acts on. Everything else is a
                    // quieter row beneath it.
                    if (songs.isNotEmpty() && !isReorderMode && !isSearchActive) {
                        CollectionPlaybackActions(
                            onPlay = { onPlayQueue(songs, songs.first()) },
                            onShuffle = { onShuffleQueue(songs) },
                            modifier = Modifier
                                .padding(horizontal = PLAYLIST_GUTTER)
                                .padding(top = 18.dp),
                        )
                    }

                    androidx.compose.foundation.layout.FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = PLAYLIST_GUTTER)
                            .padding(top = 10.dp),
                        // Centred rather than on the title's rail: these wrap
                        // to their own line under two full-width controls, and
                        // a lone pill hanging off the left edge under them
                        // reads as a stray rather than as the row's second
                        // rank. Centring also keeps it looking deliberate
                        // whether there are one, two or three of them.
                        horizontalArrangement = Arrangement.spacedBy(
                            8.dp,
                            Alignment.CenterHorizontally
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // Keeping a playlist is the whole point of arriving
                        // here from search, so it sits in the header rather
                        // than behind the overflow. Play is still the one
                        // primary action, above this secondary group.
                        if (canSavePlaylist) {
                            FilledTonalButton(
                                onClick = {
                                    val nowSaved = viewModel.toggleSavedPlaylist(
                                        resolvedPlaylist,
                                        isAlbum = isAlbum
                                    )
                                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                    scope.launch {
                                        val snackbarMsg =
                                            if (nowSaved) shareContext.getString(R.string.lib_saved_to_library)
                                            else shareContext.getString(R.string.lib_removed_from_library)
                                        snackbarHostState.showSnackbar(snackbarMsg)
                                    }
                                },
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                // Crossfade rather than a spatial spec: the
                                // button must not resize under the finger that
                                // is still on it.
                                AnimatedContent(
                                    targetState = isSavedPlaylist,
                                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                                    label = "savePlaylist"
                                ) { saved ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (saved) Icons.Rounded.BookmarkAdded
                                                else Icons.Rounded.BookmarkAdd,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(if (saved) stringResource(R.string.cd_saved) else stringResource(R.string.action_save))
                                    }
                                }
                            }
                        }

                        // Saving keeps a live reference; downloading makes the
                        // loaded snapshot available offline. They stay separate
                        // actions because either can be useful without the other.
                        if (songs.isNotEmpty()) {
                            MusicPlaylistDownloadAction(
                                playlistId = resolvedPlaylist.id,
                                playlistTitle = resolvedPlaylist.name,
                                artworkUrl = resolvedPlaylist.thumbnailUrl,
                                songs = songs,
                                modifier = Modifier.heightIn(min = 48.dp)
                            )
                        }
                    }

                    // Tap to expand: a published playlist's description can
                    // run to paragraphs, and two lines is all the page owes
                    // it before the tracks.
                    if (!resolvedPlaylist.description.isNullOrBlank()) {
                        Text(
                            text = resolvedPlaylist.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = playlistOnGround().copy(alpha = 0.78f),
                            maxLines = if (descriptionExpanded) Int.MAX_VALUE else 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = PLAYLIST_GUTTER)
                                .padding(top = 14.dp)
                                .animateContentSize()
                                .clickable { descriptionExpanded = !descriptionExpanded }
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                }
            }

            if (showTracks) {
            // Track section header. Hidden while searching - the search field
            // already labels what the list below it is.
            if (!isSearchActive && !isFetching.value && songs.isNotEmpty()) {
                item {
                    TrackSectionHeader(
                        countLabel = if (songs.size == 1) "1 track" else "${songs.size} tracks",
                        manageEnabled = isReorderMode,
                        canEdit = canEditSongs,
                        onToggleEdit = {
                            if (isReorderMode) {
                                exitReorderMode()
                            } else {
                                isReorderMode = true
                                isSearchActive = false
                                searchQuery = ""
                            }
                        }
                    )
                }
            }

            if (isSearchActive && searchQuery.isNotBlank() && filteredSongs.isNotEmpty()) {
                item {
                    Text(
                        text = if (filteredSongs.size == 1) "1 result" else "${filteredSongs.size} results",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 24.dp, top = 8.dp, bottom = 4.dp)
                    )
                }
            }

            when {
                isFetching.value -> item { TrackSkeletonList() }

                filteredSongs.isEmpty() && searchQuery.isNotBlank() -> item {
                    EmptyLibraryState(
                        icon = Icons.Rounded.SearchOff,
                        title = stringResource(R.string.search_no_results),
                        subtitle = "Nothing in this ${if (isAlbum) "album" else "playlist"} matches \"$searchQuery\""
                    )
                }

                songs.isEmpty() && loadAttempted && !isLocalPlaylist && !isAlbum -> item {
                    // A remote playlist that came back empty is more often a
                    // failed fetch than a genuinely empty playlist, so this
                    // state offers the recovery instead of dead-ending.
                    EmptyLibraryState(
                        icon = Icons.Rounded.CloudOff,
                        title = stringResource(R.string.lib_load_failed_title),
                        subtitle = stringResource(R.string.lib_load_failed_body),
                        action = {
                            Button(onClick = {
                                scope.launch {
                                    isFetching.value = true
                                    songRows = playlistSongRows(
                                        viewModel.fetchPlaylistSongs(resolvedPlaylist.id),
                                        trackKeyPrefix
                                    )
                                    remoteRowIdsLoaded = false
                                    isFetching.value = false
                                }
                            }) {
                                Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.action_retry))
                            }
                        }
                    )
                }

                songs.isEmpty() -> item {
                    // A fresh local playlist's empty state offers the way to
                    // fill it rather than only reporting that it is empty.
                    EmptyLibraryState(
                        icon = Icons.Rounded.MusicNote,
                        title = stringResource(if (isAlbum) R.string.lib_album_empty else R.string.lib_playlist_empty),
                        subtitle = "Songs you add to this ${if (isAlbum) "album" else "playlist"} will show up here",
                        action = if (isLocalPlaylist && onAddSongsRequest != null) {
                            {
                                Button(onClick = onAddSongsRequest) {
                                    Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.lib_add_from_playlists))
                                }
                            }
                        } else null
                    )
                }

                else -> itemsIndexed(
                    items = filteredRows,
                    key = { _, row -> row.key }
                ) { index, row ->
                    val song = row.song
                    // Manage mode: song removal for local and YouTube playlists.
                    // Drag reordering works locally and on owned "PL" playlists
                    // (remote rows become draggable once their setVideoId loads).
                    val manageEnabled = isReorderMode && searchQuery.isBlank() && canEditSongs
                    val reorderEnabled = manageEnabled &&
                        (isLocalPlaylist || (canReorderRemote && row.setVideoId != null))
                    val isDragging = draggingKey == row.key

                    // The handle drags on touch (the discoverable path); long
                    // pressing anywhere on the row is the accelerator for the
                    // same reorder. Both feed the one drag state.
                    val handleDrag = if (reorderEnabled) {
                        Modifier.pointerInput(row.key) {
                            detectDragGestures(
                                onDragStart = { beginDrag(row) },
                                onDragEnd = { finishReorderDrag() },
                                onDragCancel = { finishReorderDrag() },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragOffsetY += amount.y
                                }
                            )
                        }
                    } else Modifier
                    val rowDrag = if (reorderEnabled) {
                        Modifier.pointerInput(row.key) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { beginDrag(row) },
                                onDragEnd = { finishReorderDrag() },
                                onDragCancel = { finishReorderDrag() },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragOffsetY += amount.y
                                }
                            )
                        }
                    } else Modifier

                    // Connected track group: rows sit flush inside one visual
                    // container - round outer corners, small seams, a divider
                    // between rows - instead of floating as separate cards.
                    // The dividers live outside the swipe container so a swipe
                    // moves only its own row, and indices are untouched because
                    // no extra lazy items are added (reorder math relies on that).
                    val isFirstTrack = index == 0
                    val isLastTrack = index == filteredRows.lastIndex
                    // Segmented list geometry (16dp outer, 4dp inner, 2dp gaps),
                    // the same tokens as every other grouped list in the app.
                    val trackTopCorner = if (isFirstTrack) 16.dp else 4.dp
                    val trackBottomCorner = if (isLastTrack) 16.dp else 4.dp
                    val trackShape = RoundedCornerShape(
                        topStart = trackTopCorner,
                        topEnd = trackTopCorner,
                        bottomStart = trackBottomCorner,
                        bottomEnd = trackBottomCorner
                    )

                    // The swipe disarms while the row is managed or dragged:
                    // both gestures start from the same finger, and a reorder
                    // that ends in an accidental removal is the worst outcome
                    // either of them can produce.
                    val swipeArmed = swipeEnabled && !manageEnabled && !isDragging
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (isDragging) Modifier else Modifier.animateItem())
                            // The group's 20dp inset and its breathing room
                            // live here, on the item, so the swipe wash behind
                            // each row shares the row's exact bounds instead
                            // of flooding past the card.
                            .padding(
                                start = 20.dp,
                                end = 20.dp,
                                top = if (isFirstTrack) 12.dp else 0.dp,
                                bottom = if (isLastTrack) 16.dp else ListItemDefaults.SegmentedGap
                            )
                    ) {
                    PlaylistTrackSwipeContainer(
                        startAction = if (swipeArmed) resolveSwipeSetting(swipeStartSetting) else null,
                        endAction = if (swipeArmed) resolveSwipeSetting(swipeEndSetting) else null,
                        onAction = { action ->
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            when (action) {
                                ThemePreferences.PLAYLIST_SWIPE_ACTION_REMOVE -> removeSong(row)
                                ThemePreferences.PLAYLIST_SWIPE_ACTION_PLAY -> onPlayQueue(filteredSongs, song)
                                ThemePreferences.PLAYLIST_SWIPE_ACTION_QUEUE -> onEnqueueSong?.invoke(song, false)
                                ThemePreferences.PLAYLIST_SWIPE_ACTION_OPTIONS -> onSongLongPress?.invoke(song)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            // The swipe wash stays inside the group's rounded
                            // corners. Skipped while dragging so it never clips
                            // the lifted row's shadow.
                            .then(if (isDragging) Modifier else Modifier.clip(trackShape))
                            .zIndex(if (isDragging) 2f else 0f)
                            .offset { IntOffset(0, if (isDragging) dragOffsetY.roundToInt() else 0) }
                            .then(rowDrag)
                    ) {
                        PlaylistTrackRow(
                            song = song,
                            isCurrent = nowPlaying.songId == song.id,
                            isPlaying = nowPlaying.isPlaying,
                            manageEnabled = manageEnabled,
                            reorderEnabled = reorderEnabled,
                            isDragging = isDragging,
                            dragHandleModifier = handleDrag,
                            onClick = { onPlayQueue(filteredSongs, song) },
                            onLongClick = onSongLongPress?.let { press -> { press(song) } },
                            onRemove = { removeSong(row) },
                            topCorner = trackTopCorner,
                            bottomCorner = trackBottomCorner,
                            // No padding of its own: the inset lives on the
                            // item above so the swipe wash matches these bounds.
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    }
                }
            }
            } // showTracks
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val wide = maxWidth >= PLAYLIST_WIDE_MIN_WIDTH
            if (wide) {
                // Under the transparent top bar, which the tracks scroll
                // beneath and which fades in over them as they do.
                val topClearance = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 64.dp
                val sideListState = androidx.compose.foundation.lazy.rememberLazyListState()
                val headerColumnWidth = (maxWidth * 0.4f).coerceIn(320.dp, 460.dp)
                Row(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = sideListState,
                        contentPadding = PaddingValues(top = topClearance, bottom = listBottomPadding),
                        modifier = Modifier
                            .width(headerColumnWidth)
                            .fillMaxHeight()
                    ) { pageItems(true, false) }
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(top = topClearance, bottom = listBottomPadding),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) { pageItems(false, true) }
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = listBottomPadding),
                    modifier = Modifier.fillMaxSize()
                ) { pageItems(true, true) }
            }
        }
        }
    }
    } // MaterialTheme(artworkScheme)

    if (showEditDialog && (isLocalPlaylist || canRenameDeleteYouTube)) {
        EditPlaylistDialog(
            initialName = resolvedPlaylist.name,
            initialDescription = resolvedPlaylist.description,
            onDismiss = { showEditDialog = false },
            onConfirm = { newName, newDescription ->
                if (isLocalPlaylist) {
                    viewModel.updateLocalPlaylist(resolvedPlaylist.id, newName, newDescription)
                } else {
                    viewModel.renameYouTubePlaylist(resolvedPlaylist.id, newName, newDescription)
                }
                showEditDialog = false
            }
        )
    }

    if (showDeleteDialog && (isLocalPlaylist || canRenameDeleteYouTube)) {
        // Destructive confirmation runs calm: no bounce, no scale-in entrance.
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            shape = RoundedCornerShape(32.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            icon = {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            },
            title = { Text(stringResource(R.string.delete_playlist_q)) },
            text = {
                Text(
                    // Only reached for the account's own playlists when it can
                    // actually be deleted there; a local one stays device-only.
                    if (!isLocalPlaylist) {
                        "This will permanently delete \"${resolvedPlaylist.name}\" from your YouTube account. " +
                            stringResource(R.string.lib_delete_body)
                    } else {
                        "This will permanently remove \"${resolvedPlaylist.name}\" from this device."
                    },
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isLocalPlaylist) {
                            viewModel.deleteLocalPlaylist(resolvedPlaylist.id)
                        } else {
                            viewModel.deleteYouTubePlaylist(resolvedPlaylist.id)
                        }
                        showDeleteDialog = false
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    songPendingRemoval?.let { song ->
        // Same calm destructive idiom as the delete dialog above: no bounce,
        // no scale-in entrance, error-colored confirm.
        AlertDialog(
            onDismissRequest = { songPendingRemoval = null },
            shape = RoundedCornerShape(32.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text(stringResource(R.string.remove_from_account_q)) },
            text = {
                Text(
                    if (resolvedPlaylist.id == "LM") {
                        "Removing \"${song.title}\" from Your Likes removes the like from your YouTube account, everywhere you use YouTube."
                    } else {
                        "This removes \"${song.title}\" from \"${resolvedPlaylist.name}\" on your YouTube account, everywhere you use YouTube."
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.removeSongFromYouTubePlaylist(resolvedPlaylist.id, song)
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                message = "Removed ${song.title}",
                                duration = SnackbarDuration.Short
                            )
                        }
                        songPendingRemoval = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(stringResource(R.string.remove))
                }
            },
            dismissButton = {
                TextButton(onClick = { songPendingRemoval = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}
