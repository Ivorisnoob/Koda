package com.ivor.ivormusic.ui.player

import android.graphics.Bitmap
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.ivor.ivormusic.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The ground the Canvas cover stands on.
 *
 * The hard part of this is not the colour, it is the seam. A blurred copy of the
 * cover cannot hide one however softly it is masked, because it still carries the
 * cover's own edges and composition and the eye finds where the two line up. Nor
 * can a palette-derived cloud: it is a good colour, but it is not *this* colour,
 * so the join shows as a step.
 *
 * So the field is built from the cover's own bottom edge. The pixels along the
 * bottom of the artwork become the colour at the top of the field, which makes the
 * join continuous by construction rather than by blurring - at the boundary the
 * field is, exactly, the colour the art ends on. From there it sinks into one deep
 * tone drawn from the same pixels, so the foot of the screen is calm enough for
 * white type to sit on.
 *
 * Three columns are sampled rather than one average, because a cover whose bottom
 * runs dark on the left and bright on the right would otherwise meet a flat band
 * and show a seam at both ends. The horizontal detail is only held for the first
 * stretch below the join and is gone by the time the controls start.
 */
@Composable
internal fun CanvasField(
    song: Song?,
    /** Screen fraction where the cover ends and this has to match it. */
    joinAt: Float,
    /** The style's own black; also the honest answer when there is no artwork. */
    scrim: Color,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // The cover as SongArtwork resolves it. ChromaticMistBackground is fed
    // highResThumbnailUrl instead, which is null for every device-library song, and
    // it answers a null model with a flat rectangle - that is the white background.
    val model: Any? = song?.thumbnailUrl ?: song?.albumArtUri

    var sampled by remember { mutableStateOf<FieldColors?>(null) }
    LaunchedEffect(model) {
        sampled = model?.let { sampleFieldColors(context, it) }
    }

    val colors = sampled ?: FieldColors(scrim, scrim, scrim, scrim)
    // Songs change under a running player, so the ground moves rather than cuts.
    val spec = tween<Color>(durationMillis = 900, easing = FastOutSlowInEasing)
    val left by animateColorAsState(colors.left, spec, label = "CanvasFieldLeft")
    val middle by animateColorAsState(colors.middle, spec, label = "CanvasFieldMiddle")
    val right by animateColorAsState(colors.right, spec, label = "CanvasFieldRight")
    val deep by animateColorAsState(colors.deep, spec, label = "CanvasFieldDeep")

    Box(modifier = modifier.fillMaxSize()) {
        // The vertical body. Stops are screen fractions, so the edge colour is still
        // the edge colour exactly where the cover stops - everything above the join
        // is behind the artwork and never seen.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(
                        Brush.verticalGradient(
                            0f to middle,
                            joinAt to middle,
                            (joinAt + (1f - joinAt) * 0.55f) to lerpColor(middle, deep, 0.7f),
                            1f to deep,
                        )
                    )
                }
        )
        // The horizontal detail, held across the join and released below it.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // DstIn has to resolve against this tint alone, not the body under it.
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawBehind {
                    drawRect(Brush.horizontalGradient(0f to left, 0.5f to middle, 1f to right))
                    drawRect(
                        Brush.verticalGradient(
                            joinAt to Color.Black,
                            (joinAt + (1f - joinAt) * 0.45f) to Color.Transparent,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                }
        )
    }
}

internal data class FieldColors(
    val left: Color,
    val middle: Color,
    val right: Color,
    val deep: Color,
)

/**
 * Averages the bottom band of the cover in three columns.
 *
 * Deliberately not Palette: Palette answers "what colours is this artwork made of",
 * and the question here is "what colour is the artwork at the pixel where it stops",
 * which is the only answer that makes the join invisible.
 */
private suspend fun sampleFieldColors(context: android.content.Context, model: Any): FieldColors? =
    withContext(Dispatchers.IO) {
        try {
            // The shared loader, so a cover already on screen is already in memory.
            val request = ImageRequest.Builder(context)
                .data(model)
                .size(72)
                // Pixels have to be readable; a hardware bitmap cannot be sampled.
                .allowHardware(false)
                .build()
            val bitmap = (context.imageLoader.execute(request) as? SuccessResult)
                ?.drawable?.let { (it as? android.graphics.drawable.BitmapDrawable)?.bitmap }
                ?: return@withContext null
            if (bitmap.width < 3 || bitmap.height < 3) return@withContext null

            val safe = if (bitmap.config == Bitmap.Config.HARDWARE) {
                bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return@withContext null
            } else {
                bitmap
            }
            val width = safe.width
            val height = safe.height
            // The band the cover actually ends on, not the whole picture.
            val top = (height * 0.86f).toInt().coerceIn(0, height - 1)
            val third = width / 3

            fun band(fromX: Int, toX: Int): Color {
                var r = 0L
                var g = 0L
                var b = 0L
                var n = 0
                for (y in top until height) {
                    for (x in fromX until toX.coerceAtMost(width)) {
                        val pixel = safe.getPixel(x, y)
                        r += (pixel shr 16) and 0xFF
                        g += (pixel shr 8) and 0xFF
                        b += pixel and 0xFF
                        n++
                    }
                }
                if (n == 0) return Color.Black
                return Color(
                    red = (r / n) / 255f,
                    green = (g / n) / 255f,
                    blue = (b / n) / 255f,
                )
            }

            val left = band(0, third)
            val middle = band(third, third * 2)
            val right = band(third * 2, width)
            val average = Color(
                red = (left.red + middle.red + right.red) / 3f,
                green = (left.green + middle.green + right.green) / 3f,
                blue = (left.blue + middle.blue + right.blue) / 3f,
            )
            FieldColors(left, middle, right, deepen(average))
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: java.io.IOException) {
            null
        }
    }

/**
 * The foot of the screen carries the controls in white, so it is taken down to a
 * dark version of the cover's own colour rather than to a neutral: keeping the hue
 * is what stops the bottom of the screen looking like a different app's background.
 */
private fun deepen(color: Color): Color = Color(
    red = color.red * 0.22f,
    green = color.green * 0.22f,
    blue = color.blue * 0.24f,
)

private fun lerpColor(from: Color, to: Color, fraction: Float): Color = Color(
    red = from.red + (to.red - from.red) * fraction,
    green = from.green + (to.green - from.green) * fraction,
    blue = from.blue + (to.blue - from.blue) * fraction,
)
