package com.ivor.ivormusic.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import com.ivor.ivormusic.data.LocalVideo
import com.ivor.ivormusic.data.LocalVideoThumbnail
import com.ivor.ivormusic.data.VideoItem
import kotlinx.coroutines.delay

/** Attempts after the first before the frame gives up and shows its failed state. */
private const val THUMBNAIL_RETRIES = 2

/** Backoff before each retry. Short enough that a card recovers while still on screen. */
private val THUMBNAIL_RETRY_DELAYS_MS = longArrayOf(600L, 1800L)

/**
 * Held off briefly so a thumbnail already in Coil's memory cache - the common
 * case when scrolling back through a feed - resolves without ever flashing a
 * spinner. A list flickering an indicator per row on every fling reads as
 * broken, not as loading.
 */
private const val THUMBNAIL_SPINNER_DELAY_MS = 180L

/**
 * A video's thumbnail, with the two ways YouTube thumbnails actually fail.
 *
 * **maxresdefault does not exist for every video.** [VideoItem.highResThumbnailUrl]
 * rewrites the mq/hq/sd URL a feed gave us into `maxresdefault`, which YouTube
 * only generates when the source was uploaded at 720p or better - so it 404s
 * for a large minority of videos, and a card that requested only that one drew
 * an empty plate with nothing logged. The URL the feed actually returned is
 * drawn underneath and the high-res version fades in on top of it, the same
 * layering [SongArtwork] uses for covers: a 404 upstairs now costs sharpness
 * instead of the image.
 *
 * **A transient fetch failure is never retried by Coil.** One dropped request
 * on a flaky connection leaves that card blank for as long as it stays in the
 * list, because the error is terminal for that request and scrolling past does
 * not re-issue it. [THUMBNAIL_RETRIES] delayed attempts cover the case that
 * actually happens - a moment of no signal - without hammering a URL that is
 * genuinely gone, since a 404 exhausts the budget in about two seconds and
 * settles on the failed state.
 *
 * Only the frames big enough to notice - feed cards, channel grids, the
 * history row - are worth this; a 100x56 sheet row draws the feed URL directly.
 */
@Composable
fun VideoThumbnail(
    thumbnailUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    /**
     * Sharper version to layer on top, when one exists. Null collapses this to
     * a plain single-source image.
     */
    highResThumbnailUrl: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
    /**
     * False for frames too small to hold an indicator, where the plate colour
     * is the whole loading state.
     */
    showProgress: Boolean = true,
    indicatorSize: Dp = 36.dp,
    /**
     * Drawn when there is no URL at all, as opposed to one that failed. Null
     * leaves the bare plate, which is right for a video frame and wrong for an
     * avatar - a circle with a person in it reads as "no picture", an empty
     * circle reads as "still loading" forever.
     */
    emptyIcon: ImageVector? = null,
    placeholderColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
) {
    val context = LocalContext.current
    // A blank URL is the same state as no URL, and treating it as one keeps a
    // request that could only ever fail off Coil's queue.
    val url = thumbnailUrl?.takeIf { it.isNotBlank() }
    // Only worth a second layer when it is genuinely a different URL - a
    // non-ytimg thumbnail is returned unchanged by highResThumbnailUrl.
    val highRes = highResThumbnailUrl?.takeIf { it.isNotBlank() && it != url && url != null }

    var attempt by remember(url) { mutableIntStateOf(0) }
    var state by remember(url) {
        mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty)
    }
    val isError = state is AsyncImagePainter.State.Error
    // No URL is not a load in progress. Nothing is requested in that case, so
    // the painter state never leaves Empty and the spinner below would turn
    // itself on after its delay and stay on for as long as the frame is on
    // screen - which is what a device video did on every surface that reached
    // this overload rather than the VideoItem one, since a device VideoItem
    // carries no thumbnail URL at all.
    val hasNothingToLoad = url == null
    val isSettled = hasNothingToLoad ||
        state is AsyncImagePainter.State.Success ||
        (isError && attempt >= THUMBNAIL_RETRIES)

    LaunchedEffect(isError, attempt, url) {
        if (!isError || attempt >= THUMBNAIL_RETRIES) return@LaunchedEffect
        delay(THUMBNAIL_RETRY_DELAYS_MS[attempt.coerceAtMost(THUMBNAIL_RETRY_DELAYS_MS.lastIndex)])
        attempt++
    }

    // See THUMBNAIL_SPINNER_DELAY_MS.
    var showSpinner by remember(url) { mutableStateOf(false) }
    LaunchedEffect(isSettled, url) {
        if (isSettled) {
            showSpinner = false
        } else {
            delay(THUMBNAIL_SPINNER_DELAY_MS)
            showSpinner = true
        }
    }

    Box(
        modifier = modifier.background(placeholderColor),
        contentAlignment = Alignment.Center
    ) {
        if (url != null) {
            AsyncImage(
                model = remember(url, attempt) {
                    ImageRequest.Builder(context)
                        .data(url)
                        .crossfade(200)
                        .build()
                },
                contentDescription = contentDescription,
                modifier = Modifier.matchParentSize(),
                contentScale = contentScale,
                onState = { state = it }
            )
        }

        if (highRes != null) {
            AsyncImage(
                model = remember(highRes) {
                    ImageRequest.Builder(context)
                        .data(highRes)
                        .crossfade(300)
                        .build()
                },
                // Described by the layer underneath; announcing the same video
                // twice is noise to a screen reader.
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = contentScale
            )
        }

        if (emptyIcon != null && url == null) {
            Icon(
                imageVector = emptyIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(indicatorSize)
            )
        }

        if (showProgress) {
            AnimatedVisibility(
                visible = showSpinner && !isError,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                LoadingIndicator(modifier = Modifier.size(indicatorSize))
            }
            // Distinguished from "still loading" on purpose: a frame that will
            // never fill should stop pretending it is about to.
            AnimatedVisibility(
                visible = isError && attempt >= THUMBNAIL_RETRIES,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Icon(
                    imageVector = Icons.Rounded.BrokenImage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(indicatorSize)
                )
            }
        }
    }
}

/** [VideoThumbnail] for a feed item, taking both URLs off the item itself. */
@Composable
fun VideoThumbnail(
    video: VideoItem,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    showProgress: Boolean = true,
    indicatorSize: Dp = 36.dp,
    placeholderColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
) {
    // A device video carries no thumbnail URL - its frame is drawn by the
    // MediaStore fetcher from the file itself. Watch history stores nothing but
    // the VideoItem, so this is the one place that can tell the two apart for
    // every surface that lists videos.
    val deviceUri = remember(video.videoId) { LocalVideo.uriFor(video.videoId) }
    if (deviceUri != null) {
        Box(
            modifier = modifier.background(placeholderColor),
            contentAlignment = Alignment.Center
        ) {
            // Drawn under the frame rather than instead of it, so a file whose
            // thumbnail could not be decoded still reads as a video.
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.size(indicatorSize * 0.6f)
            )
            AsyncImage(
                model = LocalVideoThumbnail(deviceUri),
                contentDescription = video.title,
                modifier = Modifier.matchParentSize(),
                contentScale = contentScale
            )
        }
        return
    }
    VideoThumbnail(
        thumbnailUrl = video.thumbnailUrl,
        highResThumbnailUrl = video.highResThumbnailUrl,
        contentDescription = video.title,
        modifier = modifier,
        contentScale = contentScale,
        showProgress = showProgress,
        indicatorSize = indicatorSize,
        placeholderColor = placeholderColor
    )
}
