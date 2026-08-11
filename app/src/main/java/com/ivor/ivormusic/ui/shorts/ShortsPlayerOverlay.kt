package com.ivor.ivormusic.ui.shorts

import android.content.Intent
import androidx.activity.compose.PredictiveBackHandler
import kotlinx.coroutines.CancellationException
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.NotInterested
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.ivor.ivormusic.data.LikeStatus
import com.ivor.ivormusic.data.ThemePreferences
import com.ivor.ivormusic.ui.video.CommentsSheet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive as coroutineIsActive

/**
 * Fullscreen vertical-swipe Shorts player, layered above the NavHost like
 * VideoPlayerOverlay. One shared ExoPlayer follows the settled pager page;
 * neighbouring pages show their portrait thumbnails.
 *
 * Speaks Koda's Material 3 Expressive dialect: standalone round action
 * buttons that morph to a cookie shape while active, secondaryContainer
 * active states, a Burst-shape flash on like (LikeBurstIcon pattern),
 * MaterialShapes for the pause badge and channel avatar, and a wavy
 * progress line that flattens while paused.
 *
 * [hiddenActions] holds ThemePreferences.SHORTS_ACTION_* ids the user chose
 * to hide from the action rail in Settings.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShortsPlayerOverlay(
    viewModel: ShortsPlayerViewModel,
    hiddenActions: Set<String> = emptySet()
) {
    val isActive by viewModel.isActive.collectAsState()
    if (!isActive) return

    val context = LocalContext.current
    val activity = context as? androidx.activity.ComponentActivity

    val shorts by viewModel.shorts.collectAsState()
    val currentVideo by viewModel.currentVideo.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isBuffering by viewModel.isBuffering.collectAsState()
    val playbackError by viewModel.playbackError.collectAsState()
    val engagement by viewModel.engagement.collectAsState()
    // Account subscription OR device subscription - engagement only knows the
    // first, and read alone it showed "Subscribe" for locally followed channels.
    val isSubscribed by viewModel.isSubscribedToChannel.collectAsState()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()

    val comments by viewModel.comments.collectAsState()
    val commentReplies by viewModel.replies.collectAsState()
    val loadingReplyIds by viewModel.loadingReplyIds.collectAsState()
    val isCommentsLoading by viewModel.isCommentsLoading.collectAsState()
    val isLoadingMoreComments by viewModel.isLoadingMoreComments.collectAsState()
    val canComment by viewModel.canComment.collectAsState()
    val isPostingComment by viewModel.isPostingComment.collectAsState()

    var showCommentsSheet by remember { mutableStateOf(false) }
    var showSignInDialog by remember { mutableStateOf(false) }
    var showDismissSheet by remember { mutableStateOf(false) }

    fun requireLogin(action: () -> Unit) {
        if (isLoggedIn) action() else showSignInDialog = true
    }

    /**
     * Subscribing has a signed-out path now - it saves to the device unless
     * the user explicitly picked the YouTube-account target - so it gets its
     * own gate instead of the blanket login wall the other actions use.
     */
    fun requireSubscribeLogin(action: () -> Unit) {
        if (viewModel.subscribeNeedsLogin()) showSignInDialog = true else action()
    }

    /**
     * Back previews the Shorts overlay leaving.
     *
     * The odd one out among the overlays: the two players collapse into a mini
     * pill and already own a progress value describing that journey, but this
     * one has no smaller resting state - it simply closes, and what is behind
     * it is the app it was opened from. So the peel is its own value rather
     * than a scrub of an existing one, and it shrinks the whole overlay
     * inward, which is the shape the system uses for leaving a screen with no
     * parent inside the app.
     */
    val shortsScope = rememberCoroutineScope()
    val shortsPeel = remember { Animatable(0f) }
    PredictiveBackHandler(enabled = true) { events ->
        try {
            events.collect { event ->
                shortsPeel.snapTo(event.progress.coerceIn(0f, 1f))
            }
            viewModel.close()
        } catch (cancelled: CancellationException) {
            // From the overlay's own scope: this coroutine is the one being
            // cancelled, and a spring started here would leave the Shorts
            // sitting shrunken with no way back.
            shortsScope.launch {
                shortsPeel.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
            }
        }
    }

    // Pause when the app stops being visible. Shorts are short-form video with
    // no audio-only expectation, and decoding on into a Surface the system is
    // about to destroy is what throws inside MediaCodecVideoRenderer.
    DisposableEffect(activity, viewModel) {
        val lifecycle = activity?.lifecycle
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) viewModel.pause()
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }

    // Keep the screen awake while a Short is playing
    DisposableEffect(isPlaying) {
        val window = activity?.window
        if (isPlaying) {
            window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // The whole overlay leaves composition on close, so this pager (and its
    // initial page) is recreated fresh on every open
    val pagerState = rememberPagerState(
        initialPage = viewModel.currentIndex.value
    ) { shorts.size }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            viewModel.onPageSelected(page)
        }
    }

    // Playback progress for the wavy line at the bottom
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(currentVideo) {
        progress = 0f
        while (coroutineIsActive) {
            val player = viewModel.exoPlayer
            val duration = player?.duration ?: 0L
            progress = if (duration > 0) {
                (player?.currentPosition ?: 0L).toFloat() / duration.toFloat()
            } else 0f
            delay(250)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val p = shortsPeel.value
                val scale = androidx.compose.ui.util.lerp(1f, 0.88f, p)
                scaleX = scale
                scaleY = scale
                // Rounds off as it shrinks, so it reads as a card being put
                // down rather than the screen contents scaling.
                shape = androidx.compose.foundation.shape.RoundedCornerShape(
                    androidx.compose.ui.util.lerp(0f, 32f, p).dp
                )
                clip = true
            }
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { /* consume clicks so the app underneath stays inert */ }
    ) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1
        ) { page ->
            val item = shorts[page]
            val isCurrent = page == pagerState.settledPage

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { if (isCurrent) viewModel.togglePlayPause() }
            ) {
                // Portrait thumbnail behind the surface: visible on
                // neighbouring pages and while the current one buffers
                AsyncImage(
                    model = item.portraitThumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                if (isCurrent && playbackError == null) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                useController = false
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            }
                        },
                        update = { pv -> pv.player = viewModel.exoPlayer },
                        onRelease = { pv -> pv.player = null },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                if (isCurrent) {
                    // Buffering: the expressive shape-morphing loader on a
                    // tonal puck so it reads on any video frame
                    AnimatedVisibility(
                        visible = isBuffering && playbackError == null,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f)),
                            contentAlignment = Alignment.Center
                        ) {
                            LoadingIndicator(
                                modifier = Modifier.size(44.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Paused badge: expressive cookie shape with a springy pop
                    AnimatedVisibility(
                        visible = !isPlaying && !isBuffering && playbackError == null,
                        enter = scaleIn(
                            initialScale = 0.6f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                        ) + fadeIn(),
                        exit = scaleOut(targetScale = 0.6f) + fadeOut(),
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(MaterialShapes.Cookie12Sided.toShape())
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = "Play",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }

                    if (playbackError != null) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(horizontal = 32.dp),
                            shape = RoundedCornerShape(28.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(MaterialShapes.Cookie9Sided.toShape())
                                        .background(MaterialTheme.colorScheme.errorContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ErrorOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = playbackError?.message ?: "Playback failed",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                FilledTonalButton(onClick = { viewModel.retryCurrent() }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Retry")
                                }
                            }
                        }
                    }
                }

                // Bottom scrim for metadata legibility
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))
                            )
                        )
                )
            }
        }

        // Top scrim + bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent)
                    )
                )
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { viewModel.close() },
                shapes = IconButtonDefaults.shapes(),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Close Shorts"
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Shorts",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        // Metadata (bottom-left) + floating action pill (bottom-right)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 16.dp, end = 10.dp, bottom = 18.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val video = currentVideo
                if (video != null && video.channelName.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Expressive avatar, cookie-clipped like the design
                        // guide's shaped avatars
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(MaterialShapes.Cookie9Sided.toShape())
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.8f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!video.channelIconUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = video.channelIconUrl,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = video.channelName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(
                            onClick = { requireSubscribeLogin { viewModel.toggleSubscribe() } },
                            colors = if (isSubscribed) {
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.92f),
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            } else {
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 16.dp, vertical = 6.dp
                            ),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text(
                                text = if (isSubscribed) "Subscribed" else "Subscribe",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
                if (video != null && video.title.isNotBlank()) {
                    Text(
                        text = video.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Standalone round action buttons; each can be hidden in Settings
            val likeStatus = engagement?.likeStatus ?: LikeStatus.INDIFFERENT
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (ThemePreferences.SHORTS_ACTION_LIKE !in hiddenActions) {
                    ShortsAction(
                        icon = if (likeStatus == LikeStatus.LIKE) Icons.Rounded.ThumbUp
                            else Icons.Outlined.ThumbUp,
                        label = engagement?.likeCount ?: "Like",
                        active = likeStatus == LikeStatus.LIKE,
                        burstOnActivate = true,
                        contentDescription = "Like",
                        onClick = { requireLogin { viewModel.toggleLike() } }
                    )
                }
                if (ThemePreferences.SHORTS_ACTION_DISLIKE !in hiddenActions) {
                    ShortsAction(
                        icon = if (likeStatus == LikeStatus.DISLIKE) Icons.Rounded.ThumbDown
                            else Icons.Outlined.ThumbDown,
                        label = "Dislike",
                        active = likeStatus == LikeStatus.DISLIKE,
                        contentDescription = "Dislike",
                        onClick = { requireLogin { viewModel.toggleDislike() } }
                    )
                }
                if (ThemePreferences.SHORTS_ACTION_COMMENTS !in hiddenActions) {
                    ShortsAction(
                        icon = Icons.Rounded.ChatBubble,
                        label = "Comments",
                        contentDescription = "Comments",
                        onClick = {
                            viewModel.ensureCommentsLoaded()
                            showCommentsSheet = true
                        }
                    )
                }
                if (ThemePreferences.SHORTS_ACTION_SHARE !in hiddenActions) {
                    ShortsAction(
                        icon = Icons.Rounded.Share,
                        label = "Share",
                        contentDescription = "Share",
                        onClick = {
                            val videoId = currentVideo?.videoId ?: return@ShortsAction
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "https://youtube.com/shorts/$videoId")
                            }
                            context.startActivity(Intent.createChooser(send, "Share Short"))
                        }
                    )
                }
                if (ThemePreferences.SHORTS_ACTION_NOT_INTERESTED !in hiddenActions) {
                    ShortsAction(
                        icon = Icons.Rounded.NotInterested,
                        label = "Not interested",
                        contentDescription = "Not interested",
                        // Opens a chooser rather than acting straight away: this
                        // button sits in a rail the thumb rests on while
                        // swiping, and a one-tap irreversible-looking dismissal
                        // there would go off by accident constantly.
                        onClick = { showDismissSheet = true }
                    )
                }
            }
        }

        // Wavy playback progress, Koda's player signature; the wave settles
        // flat while paused
        val waveAmplitude by animateFloatAsState(
            targetValue = if (isPlaying) 1f else 0f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy),
            label = "waveAmplitude"
        )
        LinearWavyProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .align(Alignment.BottomCenter),
            color = MaterialTheme.colorScheme.primary,
            trackColor = Color.White.copy(alpha = 0.25f),
            amplitude = { waveAmplitude }
        )
    }

    if (showCommentsSheet) {
        CommentsSheet(
            comments = comments,
            replies = commentReplies,
            loadingReplyIds = loadingReplyIds,
            isLoading = isCommentsLoading,
            isLoadingMore = isLoadingMoreComments,
            commentsAvailable = engagement?.commentsToken != null,
            canComment = canComment,
            isPosting = isPostingComment,
            onLoadMore = { viewModel.loadMoreComments() },
            onLoadReplies = { viewModel.loadReplies(it) },
            onPostComment = { viewModel.postComment(it) },
            onPostReply = { target, threadParent, text ->
                viewModel.postReply(target, threadParent, text)
            },
            onLikeComment = { comment -> requireLogin { viewModel.toggleCommentLike(comment) } },
            onDeleteComment = { comment -> viewModel.deleteComment(comment) },
            onDismiss = { showCommentsSheet = false }
        )
    }

    if (showDismissSheet) {
        ShortsDismissSheet(
            channelName = currentVideo?.channelName.orEmpty(),
            onNotInterested = {
                showDismissSheet = false
                viewModel.markCurrentNotInterested()
            },
            onBlockChannel = {
                showDismissSheet = false
                viewModel.blockChannelForCurrent()
            },
            onDismiss = { showDismissSheet = false }
        )
    }

    if (showSignInDialog) {
        com.ivor.ivormusic.ui.auth.YouTubeAuthDialog(
            onDismiss = { showSignInDialog = false },
            onAuthSuccess = {
                showSignInDialog = false
                viewModel.onLoginStateChanged()
            }
        )
    }
}

/**
 * One standalone action button of the Shorts rail: a round tonal button
 * that morphs to a MaterialShapes cookie and fills with secondaryContainer
 * while active, a springy icon pop, and — for the like action — a one-shot
 * Burst-shape flash borrowed from LikeBurstIcon. The label floats below
 * the button, over the video.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShortsAction(
    icon: ImageVector,
    label: String,
    contentDescription: String,
    active: Boolean = false,
    burstOnActivate: Boolean = false,
    onClick: () -> Unit
) {
    val iconScale = remember { Animatable(1f) }
    val burstProgress = remember { Animatable(0f) }
    var isInitial by remember { mutableStateOf(true) }

    LaunchedEffect(active) {
        if (isInitial) {
            isInitial = false
            return@LaunchedEffect
        }
        if (active) {
            launch {
                iconScale.snapTo(0.5f)
                iconScale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
            }
            if (burstOnActivate) {
                burstProgress.snapTo(0.01f)
                burstProgress.animateTo(1f, tween(durationMillis = 450, easing = FastOutSlowInEasing))
                burstProgress.snapTo(0f)
            }
        }
    }

    val containerColor by animateColorAsState(
        targetValue = if (active) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.85f),
        label = "shortsActionContainer"
    )
    val buttonShape = if (active) MaterialShapes.Cookie9Sided.toShape() else CircleShape

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(buttonShape)
                .background(containerColor)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            val p = burstProgress.value
            if (p > 0f) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .graphicsLayer {
                            scaleX = 0.3f + p * 1.2f
                            scaleY = 0.3f + p * 1.2f
                            alpha = 1f - p
                        }
                        .clip(MaterialShapes.Burst.toShape())
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                )
            }
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (active) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer {
                        scaleX = iconScale.value
                        scaleY = iconScale.value
                    }
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.95f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 64.dp)
        )
    }
}

/**
 * The two "stop recommending" choices for the Short on screen.
 *
 * A sheet rather than a direct action on the rail button: that rail is where
 * the thumb rests between swipes, and a single tap that made a video vanish
 * would fire by accident often enough to be the thing people remember about
 * the feature. Both choices are still undoable from the app-wide snackbar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShortsDismissSheet(
    channelName: String,
    onNotInterested: () -> Unit,
    onBlockChannel: () -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(
            skipPartiallyExpanded = true
        ),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Stop recommending",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))

            ShortsDismissRow(
                icon = Icons.Rounded.NotInterested,
                title = "Not interested",
                subtitle = "Hide this Short and move on",
                onClick = onNotInterested
            )
            Spacer(modifier = Modifier.height(8.dp))
            ShortsDismissRow(
                icon = Icons.Rounded.RemoveCircleOutline,
                title = "Don't recommend channel",
                subtitle = channelName.takeIf { it.isNotBlank() }
                    ?.let { "Hide everything from $it" }
                    ?: "Hide everything from this channel",
                onClick = onBlockChannel
            )
        }
    }
}

@Composable
private fun ShortsDismissRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
