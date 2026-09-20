package com.ivor.ivormusic.ui.video

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.BookmarkAdded
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.LocalVideoPlaylistsRepository
import com.ivor.ivormusic.data.ThemePreferences
import com.ivor.ivormusic.data.VideoItem
import com.ivor.ivormusic.data.VideoPlaylist
import com.ivor.ivormusic.data.VideoQueue
import com.ivor.ivormusic.data.googleImageAtSize
import com.ivor.ivormusic.ui.artist.PlaySplitButton
import com.ivor.ivormusic.ui.components.DismissibleSnackbarHost
import com.ivor.ivormusic.ui.components.VideoThumbnail
import com.ivor.ivormusic.ui.components.VideoThumbnailBadge
import com.ivor.ivormusic.ui.downloads.VideoPlaylistDownloadAction
import com.ivor.ivormusic.ui.home.HomeViewModel
import com.ivor.ivormusic.ui.library.CollectionPlaybackActions
import com.ivor.ivormusic.ui.library.EmptyLibraryState
import com.ivor.ivormusic.ui.library.HERO_COVER_PX
import com.ivor.ivormusic.ui.library.PLAYLIST_GUTTER
import com.ivor.ivormusic.ui.library.formatTotalDuration
import com.ivor.ivormusic.ui.library.playlistOnGround
import com.ivor.ivormusic.ui.library.playlistPageGround
import com.ivor.ivormusic.ui.library.playlistRaisedSurface
import com.ivor.ivormusic.ui.player.rememberArtworkColorScheme
import com.ivor.ivormusic.ui.theme.MontserratFamily
import kotlinx.coroutines.launch

/**
 * Playlist contents page for video mode: the Library tab drill-in, video-mode
 * search results and a channel's playlists.
 *
 * It is the music playlist page's layout for videos. It used to be a plain top
 * bar over a list of cards on the theme background, which made the same
 * playlist look finished in music mode and unfinished in video mode. Now it
 * shares that page's construction and its colour helpers
 * (`ui/library/PlaylistPageStyle.kt`): a cover hero under the status bar that
 * dissolves into one flat ground tinted from the artwork, the title and facts
 * on that ground below it, Play and Shuffle as the primary actions, Save and
 * Download as the secondary row, and the videos as one connected group.
 * Differences are deliberate: the hero is 16:9 because a video playlist's
 * cover is a video frame, and a square crop would cut away most of it.
 *
 * [allowRemove] hides the remove action for playlists the user does not own
 * (for example ones found through search).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VideoPlaylistDetail(
    playlist: VideoPlaylist,
    viewModel: HomeViewModel,
    onVideoClick: (VideoItem) -> Unit,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    allowRemove: Boolean = true,
    /**
     * Start the whole playlist at the tapped video. Falls back to [onVideoClick]
     * when a caller has not wired it up, which plays the one video and drops the
     * playlist - the behaviour every caller had before there was a queue.
     */
    onPlayQueue: ((VideoQueue) -> Unit)? = null,
    /** Queue a video without leaving the playlist. */
    onEnqueueVideo: ((VideoItem, Boolean) -> Unit)? = null,
    /** Open a video's creator, from the options sheet. */
    onOpenChannel: ((String) -> Unit)? = null
) {
    val videos by viewModel.playlistVideos.collectAsState()
    val isLoading by viewModel.isPlaylistVideosLoading.collectAsState()
    val pageState by viewModel.playlistPageState.collectAsState()
    val listState = rememberLazyListState()
    LaunchedEffect(playlist.playlistId) { listState.scrollToItem(0) }
    LoadVideoPageAtEnd(listState, videos.size, pageState) {
        viewModel.loadMorePlaylistVideos(playlist.playlistId)
    }
    DisposableEffect(playlist.playlistId) {
        onDispose { viewModel.stopPlaylistVideoPagination(playlist.playlistId) }
    }

    val context = LocalContext.current
    val haptics = com.ivor.ivormusic.util.rememberKodaHaptics()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // Resolved in composition so a configuration change (locale, per-app
    // language) reaches the messages; a click-time context lookup would not.
    val savedMessage = stringResource(R.string.lib_saved_to_library)
    val removedMessage = stringResource(R.string.lib_removed_from_library)
    val shareChooserTitle = stringResource(R.string.share_playlist_chooser)

    // Long-press options, same as any other video card. This is the page where
    // "play this next" has the most to say - the whole list is right there.
    var optionsTarget by remember { mutableStateOf<VideoItem?>(null) }
    // Removal from an account playlist is a write against YouTube - from Watch
    // Later or Liked it unlikes outright - so it confirms first. A device
    // playlist stays instant, same as music mode's local playlists.
    var videoPendingRemoval by remember { mutableStateOf<VideoItem?>(null) }

    // Keeping the playlist you just found is the whole reason for arriving here
    // from search, so Save sits in the header rather than behind anything.
    // Offered only on playlists that are not already the user's: the account's
    // own are in the library by definition, and the pinned feeds have no
    // published playlist behind them to keep.
    val savedPlaylistIds by viewModel.savedVideoPlaylistIds.collectAsState()
    val accountPlaylists by viewModel.videoPlaylists.collectAsState()
    val isSaved = playlist.playlistId in savedPlaylistIds
    // A device playlist is already the user's own and has no published page
    // behind it, so keeping a reference to it would mean nothing.
    val isLocal = LocalVideoPlaylistsRepository.isLocal(playlist.playlistId)
    val canSave = !isLocal &&
        playlist.playlistId !in NON_SAVABLE_VIDEO_PLAYLIST_IDS &&
        (isSaved || accountPlaylists.none { it.playlistId == playlist.playlistId })

    // Watch Later ("WL") and Liked ("LL") are private, non-shareable feeds,
    // and a device playlist was never published at all; only real YouTube
    // playlists have a public URL. Albums (MPRE browse ids) share as their
    // /browse/ address - a playlist URL built from a browse id is dead.
    val isShareable = !isLocal &&
        playlist.playlistId != "WL" && playlist.playlistId != "LL"
    val shareUrl = if (playlist.playlistId.startsWith("MPRE")) {
        "https://youtube.com/browse/${playlist.playlistId}"
    } else {
        "https://youtube.com/playlist?list=${playlist.playlistId}"
    }

    // The list is snapshotted at the tap: `videos` is HomeViewModel state that
    // the next playlist the user opens overwrites, and a queue reading through
    // to it would re-point itself mid-playback.
    val playFrom: (List<VideoItem>, Int) -> Unit = { list, index ->
        val snapshot = list.toList()
        val play = onPlayQueue
        if (play != null) {
            play(
                VideoQueue(
                    videos = snapshot,
                    index = index,
                    title = playlist.title,
                    playlistId = playlist.playlistId
                )
            )
        } else {
            snapshot.getOrNull(index)?.let(onVideoClick)
        }
    }

    optionsTarget?.let { video ->
        VideoOptionsSheetHost(
            video = video,
            viewModel = viewModel,
            onDismiss = { optionsTarget = null },
            onEnqueue = onEnqueueVideo?.let { enqueue -> { next -> enqueue(video, next) } },
            onOpenChannel = onOpenChannel,
            // Hiding a video from your feeds, taken from inside a playlist you
            // put it in yourself, is the app arguing with the user. Remove is
            // the tool here, and it is on the row already.
            allowNotInterested = false
        )
    }

    // Cover art. A published playlist brings its own; the pinned feeds and a
    // device playlist often bring none, and there the first video's frame is
    // the cover the user would recognise. Nothing at all until the first page
    // lands falls through to the glyph below.
    val firstVideo = videos.firstOrNull()
    val playlistArt = playlist.thumbnailUrl?.takeIf { it.isNotBlank() && it != "null" }
    val heroArt = playlistArt ?: firstVideo?.thumbnailUrl?.takeIf { it.isNotBlank() }
    val heroHighRes = remember(heroArt, firstVideo?.videoId) {
        when {
            heroArt == null -> null
            // A Google-hosted cover can be asked for at the drawn size.
            heroArt.contains("googleusercontent.com") || heroArt.contains("ggpht.com") ->
                googleImageAtSize(heroArt, HERO_COVER_PX)
            // A video frame upgrades through the same rule every other video
            // surface uses; a missing maxres simply leaves the base visible.
            else -> firstVideo?.copy(thumbnailUrl = heroArt)?.highResThumbnailUrl
        }?.takeIf { it != heroArt }
    }

    // The page dresses in the cover, gated on the same Album Art Colors setting
    // as the music page and the player: a page that tints itself while that
    // switch is off is the switch not working.
    val prefs = remember(context) { ThemePreferences(context) }
    val artworkColorsEnabled by prefs.playerArtworkColors.collectAsState()
    val artworkScheme = rememberArtworkColorScheme(
        enabled = artworkColorsEnabled,
        albumArtUri = heroArt,
        base = MaterialTheme.colorScheme
    )

    // How far the hero has scrolled away, 0..1. Kept as State and read only
    // inside graphicsLayer / drawBehind, so a scroll re-runs a layer rather
    // than recomposing the page.
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
    val barAlpha = remember { derivedStateOf { (headerCollapse.value / 0.55f).coerceIn(0f, 1f) } }
    val headerScrolledAway by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }

    MaterialTheme(colorScheme = artworkScheme) {
        val ground = playlistPageGround()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ground)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                // Clearance for the floating overlays below plus the play button.
                contentPadding = PaddingValues(
                    bottom = contentPadding.calculateBottomPadding() + 88.dp
                )
            ) {
                item(key = "playlist_hero") {
                    VideoPlaylistHero(
                        playlist = playlist,
                        heroArt = heroArt,
                        heroHighRes = heroHighRes,
                        fallbackVideo = firstVideo.takeIf { heroArt == null },
                        collapse = { headerCollapse.value },
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = PLAYLIST_GUTTER)
                            .padding(top = 14.dp)
                    ) {
                        Text(
                            text = playlist.title,
                            style = MaterialTheme.typography.displaySmall.copy(
                                fontFamily = MontserratFamily,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.02).em,
                                lineHeight = 1.08.em
                            ),
                            color = playlistOnGround(),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            autoSize = TextAutoSize.StepBased(
                                minFontSize = 24.sp,
                                maxFontSize = 38.sp,
                                stepSize = 2.sp
                            )
                        )

                        // A count only when it is true. YouTube's own count text
                        // wins; otherwise the loaded rows are a count only once
                        // every page is in, and a "+" says so before that. The
                        // running time follows the same rule - a sum over the
                        // first page is not the playlist's length.
                        val allLoaded = !pageState.hasMore && !pageState.isLoading && !isLoading
                        val countLabel = playlist.videoCountText?.takeIf { it.isNotBlank() }
                            ?: when {
                                videos.isEmpty() -> null
                                !allLoaded -> stringResource(R.string.vpl_video_count_partial, videos.size)
                                else -> pluralStringResource(R.plurals.vpl_video_count, videos.size, videos.size)
                            }
                        val durationLabel = if (allLoaded) {
                            formatTotalDuration(videos.sumOf { it.duration.coerceAtLeast(0L) } * 1000L)
                        } else null
                        val facts = listOfNotNull(
                            stringResource(R.string.label_playlist),
                            playlist.subtitle?.takeIf { it.isNotBlank() && it != countLabel },
                            countLabel,
                            durationLabel
                        ).joinToString("  ·  ")
                        Text(
                            text = facts,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            color = playlistOnGround().copy(alpha = 0.8f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    // Playback is the page's one primary action, directly under
                    // the facts it acts on. Shuffle covers the rows loaded so
                    // far, exactly as tapping a row queues them.
                    if (videos.isNotEmpty()) {
                        CollectionPlaybackActions(
                            onPlay = { playFrom(videos, 0) },
                            onShuffle = { playFrom(videos.shuffled(), 0) },
                            modifier = Modifier
                                .padding(horizontal = PLAYLIST_GUTTER)
                                .padding(top = 18.dp)
                        )
                    }

                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = PLAYLIST_GUTTER)
                            .padding(top = 10.dp),
                        // Centred for the same reason as the music page: these
                        // wrap under two full-width controls, and a lone pill
                        // hanging off the left edge reads as a stray.
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (canSave) {
                            FilledTonalButton(
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                    val nowSaved = viewModel.toggleSavedVideoPlaylist(playlist)
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            if (nowSaved) savedMessage else removedMessage
                                        )
                                    }
                                },
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                // Crossfade rather than a spatial spec: the button
                                // must not resize under the finger still on it.
                                AnimatedContent(
                                    targetState = isSaved,
                                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                                    label = "saveVideoPlaylist"
                                ) { saved ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (saved) Icons.Rounded.BookmarkAdded
                                                else Icons.Rounded.BookmarkAdd,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            if (saved) stringResource(R.string.cd_saved)
                                            else stringResource(R.string.action_save)
                                        )
                                    }
                                }
                            }
                        }
                        // Saving keeps a live reference; downloading makes the
                        // videos available offline. Separate on purpose.
                        if (videos.isNotEmpty()) {
                            VideoPlaylistDownloadAction(
                                playlistId = playlist.playlistId,
                                playlistTitle = playlist.title,
                                videos = videos,
                                playlistCountText = playlist.videoCountText,
                                allPagesLoaded = !pageState.hasMore && !pageState.failed && !pageState.isLoading,
                                modifier = Modifier.heightIn(min = 48.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                }

                when {
                    isLoading && videos.isEmpty() -> item(key = "playlist_loading") {
                        VideoPlaylistSkeleton()
                    }

                    videos.isEmpty() && pageState.failed -> item(key = "playlist_failed") {
                        // A remote playlist that came back empty after a failed
                        // request is a fetch problem, not an empty playlist, so
                        // this state offers the recovery instead of dead-ending.
                        EmptyLibraryState(
                            icon = Icons.Rounded.CloudOff,
                            title = stringResource(R.string.vpl_load_failed_title),
                            subtitle = stringResource(R.string.vpl_load_failed_body),
                            action = {
                                Button(onClick = { viewModel.loadPlaylistVideos(playlist.playlistId) }) {
                                    Text(stringResource(R.string.action_retry))
                                }
                            }
                        )
                    }

                    videos.isEmpty() && !pageState.hasMore -> item(key = "playlist_empty") {
                        EmptyLibraryState(
                            icon = Icons.Rounded.VideoLibrary,
                            title = stringResource(R.string.vl_no_videos),
                            subtitle = stringResource(R.string.vpl_empty_body)
                        )
                    }

                    else -> {
                        item(key = "playlist_section") {
                            Text(
                                text = stringResource(R.string.vpl_section_videos),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = playlistOnGround(),
                                modifier = Modifier.padding(
                                    start = PLAYLIST_GUTTER,
                                    end = PLAYLIST_GUTTER,
                                    top = 8.dp,
                                    bottom = 12.dp
                                )
                            )
                        }
                        // Index-qualified: YouTube playlists can contain the same
                        // video twice, and duplicate LazyColumn keys crash.
                        itemsIndexed(
                            videos,
                            key = { index, video -> "${video.videoId}_$index" }
                        ) { index, video ->
                            val isFirst = index == 0
                            val isLast = index == videos.lastIndex
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp)
                            ) {
                                if (!isFirst) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp)
                                            .height(1.dp)
                                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    )
                                }
                                VideoPlaylistRow(
                                    video = video,
                                    onClick = { playFrom(videos, index) },
                                    onOptions = { optionsTarget = video },
                                    onRemove = if (allowRemove) {
                                        {
                                            if (isLocal) viewModel.removePlaylistVideo(playlist.playlistId, video)
                                            else videoPendingRemoval = video
                                        }
                                    } else null,
                                    removeLabel = when (playlist.playlistId) {
                                        "WL" -> stringResource(R.string.remove_from_watch_later)
                                        "LL" -> stringResource(R.string.remove_from_liked_videos)
                                        else -> stringResource(R.string.remove_from_playlist)
                                    },
                                    // One connected group: round outer corners,
                                    // small seams, as the music track list.
                                    topCorner = if (isFirst) 24.dp else 8.dp,
                                    bottomCorner = if (isLast) 24.dp else 8.dp
                                )
                            }
                        }
                        item(key = "playlist_next_page") {
                            VideoPageFooter(pageState) {
                                if (pageState.hasMore) viewModel.loadMorePlaylistVideos(playlist.playlistId)
                                else viewModel.loadPlaylistVideos(playlist.playlistId)
                            }
                        }
                    }
                }
            }

            // The bar settles into the page's ground as the hero scrolls away,
            // so what it separates is the rows passing under it, not itself.
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .drawBehind { drawRect(ground, alpha = barAlpha.value) }
            ) {
                TopAppBar(
                    title = {
                        Text(
                            playlist.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                            color = playlistOnGround(),
                            modifier = Modifier.graphicsLayer {
                                alpha = barAlpha.value
                                translationY = (1f - barAlpha.value) * 20.dp.toPx()
                            }
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.cd_back),
                                tint = playlistOnGround()
                            )
                        }
                    },
                    actions = {
                        if (isShareable) {
                            IconButton(onClick = {
                                val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(android.content.Intent.EXTRA_TEXT, shareUrl)
                                }
                                context.startActivity(
                                    android.content.Intent.createChooser(send, shareChooserTitle)
                                )
                            }) {
                                Icon(
                                    Icons.Rounded.Share,
                                    contentDescription = stringResource(R.string.share_playlist_chooser),
                                    tint = playlistOnGround()
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent
                    )
                )
            }

            // Once Play and Shuffle have scrolled away, the split button keeps
            // playback one tap away however far down the list the user is.
            AnimatedVisibility(
                visible = headerScrolledAway && videos.isNotEmpty(),
                enter = fadeIn() + scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = contentPadding.calculateBottomPadding() + 16.dp)
            ) {
                PlaySplitButton(
                    onPlay = { playFrom(videos, 0) },
                    onShuffle = { playFrom(videos.shuffled(), 0) },
                    onStartRadio = null
                )
            }

            DismissibleSnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = contentPadding.calculateBottomPadding())
            )
        }
    }

    videoPendingRemoval?.let { video ->
        AlertDialog(
            onDismissRequest = { videoPendingRemoval = null },
            shape = RoundedCornerShape(32.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text(stringResource(R.string.remove_from_account_q)) },
            text = {
                Text(
                    when (playlist.playlistId) {
                        "WL" -> "This removes \"${video.title}\" from Watch Later on your YouTube account, everywhere you use YouTube."
                        "LL" -> "This removes the like from \"${video.title}\" on your YouTube account, everywhere you use YouTube."
                        else -> "This removes \"${video.title}\" from \"${playlist.title}\" on your YouTube account, everywhere you use YouTube."
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.removePlaylistVideo(playlist.playlistId, video)
                        videoPendingRemoval = null
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
                TextButton(onClick = { videoPendingRemoval = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

/**
 * The cover edge to edge under the status bar, dissolving into the page's
 * ground. 16:9 plus the status bar, capped at 44% of the window so the title,
 * Play and the first video still land above the fold in landscape.
 *
 * The foot fade is painted in the ground's own colour rather than masked, for
 * the reason `docs/screens.md` records against the music page: a `DstIn` mask
 * composited to black on a device.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun VideoPlaylistHero(
    playlist: VideoPlaylist,
    heroArt: String?,
    heroHighRes: String?,
    /** A device video's frame, drawn when there is no URL to load at all. */
    fallbackVideo: VideoItem?,
    collapse: () -> Float,
) {
    val ground = playlistPageGround()
    val windowHeight = LocalWindowInfo.current.containerDpSize.height
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val heroHeight: Dp = (maxWidth * (9f / 16f) + statusBarTop)
            .coerceAtMost(windowHeight * 0.44f)
            .coerceAtLeast(200.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(heroHeight)
                .clipToBounds()
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        // Scroll-driven, not time-driven: the cover trails the
                        // list rather than animating on its own.
                        val amount = collapse()
                        translationY = amount * size.height * 0.24f
                        alpha = 1f - amount * 0.55f
                    }
            ) {
                when {
                    heroArt != null -> VideoThumbnail(
                        thumbnailUrl = heroArt,
                        highResThumbnailUrl = heroHighRes,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        showProgress = false,
                        placeholderColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                    fallbackVideo != null -> VideoThumbnail(
                        video = fallbackVideo,
                        modifier = Modifier.fillMaxSize(),
                        showProgress = false,
                        placeholderColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                    else -> Box(
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
                            modifier = Modifier
                                .padding(top = statusBarTop)
                                .size(120.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.VideoLibrary,
                                    contentDescription = null,
                                    modifier = Modifier.size(52.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            }

            // Where the cover hands the page over: a plain fade in the exact
            // ground colour.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0f to ground.copy(alpha = 0f),
                            0.5f to ground.copy(alpha = 0f),
                            0.84f to ground.copy(alpha = 0.85f),
                            1f to ground
                        )
                    )
            )

            // Top fade: back and share need ground to sit on whatever the
            // cover is. Faded to the same colour at zero alpha rather than to
            // Transparent, which is transparent black and greys a light theme.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(132.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(ground.copy(alpha = 0.6f), ground.copy(alpha = 0f))
                        )
                    )
            )
        }
    }
}

/**
 * One video in the playlist's connected group. Tap plays the playlist from
 * here, long press or the trailing button opens the video's options, and a
 * playlist the user can write to adds Remove to that button's menu.
 */
@Composable
private fun VideoPlaylistRow(
    video: VideoItem,
    onClick: () -> Unit,
    onOptions: () -> Unit,
    onRemove: (() -> Unit)?,
    removeLabel: String,
    topCorner: Dp,
    bottomCorner: Dp,
) {
    var showMenu by remember { mutableStateOf(false) }
    val onGround = playlistOnGround()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(
            topStart = topCorner,
            topEnd = topCorner,
            bottomStart = bottomCorner,
            bottomEnd = bottomCorner
        ),
        // Rows stand on the page's ground, not on a neutral surface role - see
        // playlistRaisedSurface for why a theme container lands wrong here.
        color = playlistRaisedSurface(),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .combinedClickable(onClick = onClick, onLongClick = onOptions)
                .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(128.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                VideoThumbnail(
                    video = video,
                    modifier = Modifier.fillMaxSize(),
                    indicatorSize = 24.dp,
                    placeholderColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
                VideoThumbnailBadge(
                    video = video,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(5.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = onGround,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (video.channelName.isNotBlank()) {
                    Text(
                        text = video.channelName,
                        style = MaterialTheme.typography.bodySmall,
                        color = onGround.copy(alpha = 0.78f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                val meta = listOfNotNull(
                    video.viewCount.takeIf { it.isNotBlank() },
                    video.uploadedDate?.takeIf { it.isNotBlank() }
                ).joinToString(" · ")
                if (meta.isNotBlank()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = onGround.copy(alpha = 0.66f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Box {
                IconButton(onClick = { if (onRemove != null) showMenu = true else onOptions() }) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = stringResource(R.string.cd_video_options),
                        tint = onGround.copy(alpha = 0.78f)
                    )
                }
                if (onRemove != null) {
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.cd_video_options)) },
                            leadingIcon = { Icon(Icons.Rounded.Tune, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onOptions()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(removeLabel) },
                            leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onRemove()
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Placeholder rows while the first page loads, in the shape of the real group
 * - a skeleton says how much is coming, which a bare spinner cannot.
 */
@Composable
private fun VideoPlaylistSkeleton(rows: Int = 5) {
    val transition = rememberInfiniteTransition(label = "videoPlaylistSkeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.10f,
        targetValue = 0.22f,
        // A pulse is a timed effect, not spatial motion, so a tween is correct.
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "videoPlaylistSkeletonAlpha"
    )
    val placeholder = playlistOnGround().copy(alpha = alpha)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        repeat(rows) { index ->
            val top = if (index == 0) 24.dp else 8.dp
            val bottom = if (index == rows - 1) 24.dp else 8.dp
            Surface(
                shape = RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom),
                color = playlistRaisedSurface(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(128.dp)
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(placeholder)
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .height(14.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(placeholder)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.5f)
                                .height(11.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(placeholder)
                        )
                    }
                }
            }
        }
    }
}
