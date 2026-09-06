package com.ivor.ivormusic.ui.video

/**
 * The inline video box on the portrait watch page, in pixels.
 *
 * @property topPx how far down the window the box starts, which is the status
 *   bar inset the page holds above it
 * @property heightPx the box's laid-out height, which follows the video's shape
 */
data class InlineVideoBox(val topPx: Int, val heightPx: Int)

/**
 * Whether the playing rendition can take the shared-element minimize.
 *
 * HDR needs a SurfaceView, which follows nothing Compose does to its ancestors,
 * so those sessions keep the curtain. Read by both the page that chooses the
 * surface type and the overlay that chooses the transition, because the two
 * disagreeing would animate a frame the video is not in.
 */
internal fun supportsSharedElementMinimize(
    quality: com.ivor.ivormusic.data.VideoQuality?,
): Boolean = quality?.isHdr != true

/**
 * Where the expanded watch page is drawn at each point between full screen and
 * the collapsed bar's thumbnail.
 *
 * The transition is a shared element: the same picture travels from the top of
 * the watch page into the 92dp frame in the mini bar, rather than being covered
 * by a curtain while an unrelated bar appears somewhere else. That only works
 * if the video lands exactly on the thumbnail, so the arithmetic is kept here
 * as a pure function - it is the one part of this animation that can be checked
 * without a screen, and [VideoMinimizeGeometryTest] does check it at both ends.
 *
 * Everything is in pixels and in the overlay's own coordinate space, whose
 * origin is the top left of the window.
 *
 * @property clipLeft left edge of the visible window onto the page
 * @property clipTop top edge of that window
 * @property clipWidth width of that window
 * @property clipHeight height of that window
 * @property pageLeft where the page's own left edge sits, relative to the clip
 * @property pageTop where the page's own top edge sits, relative to the clip
 * @property scale uniform scale applied to the page about its top left corner
 * @property cornerRadius corner rounding of the clip window
 */
internal data class VideoMinimizeGeometry(
    val clipLeft: Float,
    val clipTop: Float,
    val clipWidth: Float,
    val clipHeight: Float,
    val pageLeft: Float,
    val pageTop: Float,
    val scale: Float,
    val cornerRadius: Float,
)

/**
 * Resolve the geometry for one frame of the minimize transition.
 *
 * At [progress] 1 this is the identity: the clip is the whole window, the page
 * sits at the origin unscaled, and the corners are square - pixel for pixel
 * what the expanded player draws when nothing is animating. At 0 the clip is
 * exactly the mini bar's thumbnail and the page is scaled so that its video box
 * fills that thumbnail, with the rest of the page - the title, the related
 * videos - scaled with it and clipped away. So the resting states are not
 * special cases of this function, they are its endpoints, and there is no
 * second layout that has to be kept in agreement with the first.
 *
 * The clip always contains the video: its edges interpolate to the window while
 * the video's interpolate to the video box, and the video box is inside the
 * window, so the difference at any point is a non-negative multiple of
 * [progress]. That is what lets the page below the video appear as the frame
 * opens rather than being revealed in a separate step.
 *
 * **Scale fits rather than fills for a portrait source**, matching what the
 * thumbnail itself does with one: the mini frame is 16:9 and a vertical video
 * is letterboxed into it, so scaling on width would hand over a picture cropped
 * differently from the one the bar then draws, and the swap at the end would
 * show as a jump.
 *
 * @param progress 1 fully expanded, 0 fully collapsed. Values outside are
 *   clamped, so a spring that overshoots cannot invert the layout.
 * @param videoTop top of the video box within the page, which on the portrait
 *   watch page is the status bar inset the page holds above it
 * @param videoHeight height of that box, which follows the video's own shape
 * @param isPortraitVideo whether the source is taller than it is wide
 */
internal fun videoMinimizeGeometry(
    progress: Float,
    windowWidth: Float,
    windowHeight: Float,
    videoTop: Float,
    videoHeight: Float,
    thumbLeft: Float,
    thumbTop: Float,
    thumbWidth: Float,
    thumbHeight: Float,
    thumbCornerRadius: Float,
    isPortraitVideo: Boolean,
): VideoMinimizeGeometry {
    val p = progress.coerceIn(0f, 1f)

    // Scale at the collapsed end. Width for a landscape source, which the
    // thumbnail crops to its own 16:9 the same way; height for a portrait one,
    // which it letterboxes.
    val collapsedScale = when {
        windowWidth <= 0f || videoHeight <= 0f -> 1f
        isPortraitVideo -> thumbHeight / videoHeight
        else -> thumbWidth / windowWidth
    }
    val scale = lerp(collapsedScale, 1f, p)

    // The video box, full width of the page, ends up here. Centred in the
    // thumbnail at the collapsed end, which only moves it when fitting a
    // portrait source has left the frame with bars to either side.
    val videoLeftNow = lerp(thumbLeft + (thumbWidth - collapsedScale * windowWidth) / 2f, 0f, p)
    val videoTopNow = lerp(thumbTop + (thumbHeight - collapsedScale * videoHeight) / 2f, videoTop, p)

    val clipLeft = lerp(thumbLeft, 0f, p)
    val clipTop = lerp(thumbTop, 0f, p)
    val clipRight = lerp(thumbLeft + thumbWidth, windowWidth, p)
    val clipBottom = lerp(thumbTop + thumbHeight, windowHeight, p)

    return VideoMinimizeGeometry(
        clipLeft = clipLeft,
        clipTop = clipTop,
        clipWidth = (clipRight - clipLeft).coerceAtLeast(0f),
        clipHeight = (clipBottom - clipTop).coerceAtLeast(0f),
        // The page's origin, not the video's: the video sits videoTop down from
        // it, and that gap is scaled too.
        pageLeft = videoLeftNow - clipLeft,
        pageTop = videoTopNow - scale * videoTop - clipTop,
        scale = scale,
        cornerRadius = lerp(thumbCornerRadius, 0f, p),
    )
}

private fun lerp(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction
