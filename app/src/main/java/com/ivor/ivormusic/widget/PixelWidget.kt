package com.ivor.ivormusic.widget

import android.content.Context
import android.graphics.Bitmap
import android.widget.RemoteViews
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.unit.ColorProvider
import com.ivor.ivormusic.MainActivity
import com.ivor.ivormusic.R

/**
 * Pixel: a pixel-art turntable. The record turns while music plays, the arm
 * swings off it when it stops, and the label in the middle is the cover, cut
 * down to eight pixels across.
 *
 * **It is the one widget that moves, and how it moves is the whole design.** A
 * widget is RemoteViews in the launcher's process, so nothing Compose animates
 * exists there. What does exist is a `ViewFlipper` that starts itself, and
 * frames changing on a fixed beat is exactly how pixel art animates anyway -
 * which is why this style got the animation first. The flipper is the glint on
 * the grooves only ([R.layout.widget_pixel_spin]); everything else is still.
 * Paused, it is replaced by one still frame rather than stopped, because a
 * redraw re-inflates the tree and that is the only handle on it there is.
 *
 * **Every layer is a white mask tinted with a theme role**, stacked in one
 * square: deck, its lines, the record, the glint, the label, the arm. Pixel art
 * is usually painted in fixed colours; drawn this way it follows the palette,
 * AMOLED and light or dark like the rest of the family. The record is the one
 * place the theme is asked which way it faces, so that it is dark in both. The
 * label is the exception the palette rule allows: it is the cover.
 *
 * It has no card, like Bloom: the deck is its own ground. Controls are drawn
 * into the deck rather than taken from the kit, because a Material button on a
 * pixel-art turntable is two styles in one cell; the price is Glance's square
 * tap flash, which on this widget is at least in keeping.
 */
class PixelWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(140.dp, 140.dp),
            DpSize(200.dp, 200.dp),
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        PlayerWidgetHost.ensureSeeded(context)
        provideContent {
            val ui by PlayerWidgetHost.state.collectAsState()
            GlanceTheme(colors = KodaWidgetTheme.colors(context)) {
                PixelContent(ui)
            }
        }
    }
}

/** The art is drawn on a grid this many pixels across. */
private const val PIXEL_GRID = 32

/** The cover's label on the record: its width in art pixels, and where it starts. */
private const val LABEL_PIXELS = 8
private const val LABEL_ORIGIN = 10

/** Large enough that each art pixel stays a clean square at any widget size. */
private const val LABEL_BITMAP_PX = 256

private val SPIN_FRAMES = intArrayOf(
    R.id.pixel_spin_0, R.id.pixel_spin_1, R.id.pixel_spin_2, R.id.pixel_spin_3,
    R.id.pixel_spin_4, R.id.pixel_spin_5, R.id.pixel_spin_6, R.id.pixel_spin_7,
)

@Composable
private fun PixelContent(ui: PlayerWidgetUi) {
    if (!ui.snapshot.hasMedia) {
        WidgetSurface { WidgetEmptyState() }
        return
    }

    val context = LocalContext.current
    val size = LocalSize.current
    // LocalSize reports the matched bucket rather than the real cell, so the
    // deck is sized from it explicitly and everything inside is measured
    // against that square - the art and the tap targets cannot drift apart.
    val side = minOf(size.width, size.height) - 8.dp
    val unit = side / PIXEL_GRID
    val playing = ui.snapshot.isPlaying
    val dark = KodaWidgetTheme.isDark(context)
    val colors = GlanceTheme.colors
    // A record is dark whichever way the theme faces.
    val record = if (dark) colors.surface else colors.onSurface
    val glint = colors.outline

    WidgetSurface(background = null, openAppOnTap = false) {
        Box(
            modifier = GlanceModifier
                .size(side)
                .semantics {
                    contentDescription = context.getString(
                        if (playing) R.string.widget_action_pause else R.string.widget_action_play
                    )
                }
                .clickable(actionRunCallback<TogglePlaybackAction>()),
            contentAlignment = Alignment.Center,
        ) {
            PixelLayer(R.drawable.px_deck, colors.surfaceVariant)
            PixelLayer(R.drawable.px_deck_lines, colors.onSurfaceVariant)
            PixelLayer(R.drawable.px_record, record)
            if (playing) {
                val tint = glint.read().toArgb()
                val spin = RemoteViews(context.packageName, R.layout.widget_pixel_spin).apply {
                    SPIN_FRAMES.forEach { setInt(it, "setColorFilter", tint) }
                }
                AndroidRemoteViews(remoteViews = spin, modifier = GlanceModifier.fillMaxSize())
            } else {
                PixelLayer(R.drawable.px_shine_0, glint)
            }
            val label = remember(ui.artwork) { ui.artwork?.let(::pixelLabel) }
            if (label != null) {
                Image(
                    provider = ImageProvider(label),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = GlanceModifier.fillMaxSize(),
                )
            } else {
                PixelLayer(
                    R.drawable.px_label,
                    if (dark) colors.tertiary else colors.tertiaryContainer,
                )
            }
            // The arm crosses the deck and the record, and no one colour stands
            // out from both, so it is drawn over its own outline.
            PixelLayer(
                if (playing) R.drawable.px_arm_play_halo else R.drawable.px_arm_rest_halo,
                colors.surface,
            )
            PixelLayer(
                if (playing) R.drawable.px_arm_play else R.drawable.px_arm_rest,
                colors.primary,
            )

            // Tap targets over the glyphs drawn along the deck's foot. Taller
            // than the glyphs, reaching up over the record's edge, because the
            // strip itself is a fifth of the deck.
            Box(
                modifier = GlanceModifier.fillMaxSize(),
                contentAlignment = Alignment.BottomStart,
            ) {
                Row {
                    PixelTapTarget(
                        width = unit * 11,
                        height = unit * 9,
                        description = widgetString(R.string.widget_action_previous),
                        action = actionRunCallback<SkipPreviousAction>(),
                    )
                    PixelTapTarget(
                        width = unit * 10,
                        height = unit * 9,
                        description = widgetString(R.string.widget_action_next),
                        action = actionRunCallback<SkipNextAction>(),
                    )
                }
            }
            Box(
                modifier = GlanceModifier.fillMaxSize(),
                contentAlignment = Alignment.BottomEnd,
            ) {
                PixelTapTarget(
                    width = unit * 10,
                    height = unit * 9,
                    description = widgetString(R.string.widget_action_open_app),
                    action = actionStartActivity<MainActivity>(),
                )
            }
        }
    }
}

/** One layer of the art: a mask the full size of the deck, tinted. */
@Composable
private fun PixelLayer(@DrawableRes mask: Int, color: ColorProvider) {
    Image(
        provider = ImageProvider(mask),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        colorFilter = ColorFilter.tint(color),
        modifier = GlanceModifier.fillMaxSize(),
    )
}

@Composable
private fun PixelTapTarget(width: Dp, height: Dp, description: String, action: Action) {
    Box(
        modifier = GlanceModifier
            .width(width)
            .height(height)
            .semantics { contentDescription = description }
            .clickable(action),
    ) {}
}

/**
 * The cover as the record's label: averaged down to [LABEL_PIXELS] across, cut
 * to a pixel circle, and set where the label sits in an otherwise empty frame
 * the size of the whole deck, so it stacks like every other layer. Scaled back
 * up without filtering, which is what keeps the pixels square.
 */
private fun pixelLabel(cover: Bitmap): Bitmap {
    val tiny = Bitmap.createScaledBitmap(cover, LABEL_PIXELS, LABEL_PIXELS, true)
    val frame = Bitmap.createBitmap(PIXEL_GRID, PIXEL_GRID, Bitmap.Config.ARGB_8888)
    val centre = (LABEL_PIXELS - 1) / 2f
    for (y in 0 until LABEL_PIXELS) {
        for (x in 0 until LABEL_PIXELS) {
            val dx = x - centre
            val dy = y - centre
            if (dx * dx + dy * dy <= 4.2f * 4.2f) {
                // Opaque: a cover with transparency would let the record through.
                frame.setPixel(LABEL_ORIGIN + x, LABEL_ORIGIN + y, tiny.getPixel(x, y) or (0xFF shl 24))
            }
        }
    }
    return Bitmap.createScaledBitmap(frame, LABEL_BITMAP_PX, LABEL_BITMAP_PX, false)
}

class PixelWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PixelWidget()
}
