package com.ivor.ivormusic.widget

import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.components.CircleIconButton
import androidx.glance.appwidget.components.SquareIconButton
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.ivor.ivormusic.MainActivity
import com.ivor.ivormusic.R

/**
 * The widget family's design kit. Every widget shares it; only Pixel draws a
 * control of its own, because a Material button on pixel art is two styles in
 * one cell.
 *
 * **Controls are Material components, decoration is expressive.** Glance draws
 * a ripple for every click target as a rectangle over the view bounds, and 1.1
 * exposes no way to override it - so a hand-rolled button with a shaped
 * background flashes a hard square on every tap, which is exactly the "tap
 * ripples are so bad" complaint. [CircleIconButton] and [SquareIconButton] ship
 * their own matching ripple drawables, so they are the only two button shapes
 * used here and the ripple always follows the silhouette. The expressive shape
 * language lives where it costs nothing - on the album art, which M3 itself
 * names as the right place for adventurous shapes ("photography cropping,
 * personalized avatar masking, and other non-interactive elements").
 *
 * **The hero is a circle, everything else is a squircle.** With no motion
 * available, that round-against-square contrast is the whole hierarchy: one
 * round primary-filled control per widget, everything else tonal and square.
 *
 * **Color is Koda's palette**, resolved by [KodaWidgetTheme], not raw system
 * dynamic color and not sampled out of the artwork. There is no literal color
 * in widget content: nothing is drawn over the cover, so nothing needs a scrim.
 *
 * Container radii come from tinted shape drawables because
 * `GlanceModifier.cornerRadius` is a no-op below API 31 and minSdk here is 30;
 * where a ripple has to be clipped, both are applied.
 */

// ---------------------------------------------------------------- shape scale

/** M3 corner-radius scale, as the drawables that back it. */
internal object WidgetShape {
    @DrawableRes val Full = R.drawable.widget_shape_full
    @DrawableRes val ExtraLarge = R.drawable.widget_shape_xlarge
    @DrawableRes val LargeIncreased = R.drawable.widget_shape_large_increased
    @DrawableRes val Large = R.drawable.widget_shape_large
    @DrawableRes val Medium = R.drawable.widget_shape_medium
    @DrawableRes val Small = R.drawable.widget_shape_small
}

/** A tinted shape as a background, valid on every supported API level. */
internal fun GlanceModifier.shaped(
    @DrawableRes shape: Int,
    color: ColorProvider,
): GlanceModifier = this.background(
    imageProvider = ImageProvider(shape),
    contentScale = ContentScale.FillBounds,
    colorFilter = ColorFilter.tint(color),
)

@Composable
internal fun ColorProvider.read(): Color = getColor(LocalContext.current)

/**
 * Glance has no stringResource: Compose's reads LocalConfiguration, which this
 * composition does not provide. LocalContext is the one Glance does.
 */
@Composable
internal fun widgetString(@StringRes id: Int): String = LocalContext.current.getString(id)

// ------------------------------------------------------------------- surfaces

/**
 * The card every widget sits in. `cornerRadius` is applied alongside the shaped
 * background purely so the whole-card tap ripple is clipped to the corner
 * instead of flashing a full rectangle past it.
 *
 * A null [background] draws no card at all, which is how a widget sits directly
 * on the wallpaper - the cover and its controls float, with nothing behind
 * them. The corner clip stays either way, because it is the ripple's shape
 * rather than the card's: without a card that flash is the only feedback a tap
 * registered, so it is worth keeping and worth keeping inside the cell.
 */
@Composable
internal fun WidgetSurface(
    modifier: GlanceModifier = GlanceModifier,
    background: ColorProvider? = GlanceTheme.colors.widgetBackground,
    openAppOnTap: Boolean = true,
    content: @Composable () -> Unit,
) {
    var surface = modifier.fillMaxSize()
    if (background != null) {
        surface = surface.shaped(WidgetShape.ExtraLarge, background)
    }
    if (openAppOnTap) {
        surface = surface.cornerRadius(28.dp).clickable(actionStartActivity<MainActivity>())
    }
    Box(modifier = surface, contentAlignment = Alignment.Center) { content() }
}

// ------------------------------------------------------------------- controls

/**
 * The hero: the only circle on the widget, filled with primary. Sizes follow
 * the M3 icon-button scale - 48 small, 56 medium, 64 where there is room.
 */
@Composable
internal fun HeroPlayButton(
    isPlaying: Boolean,
    size: Dp = 56.dp,
    background: ColorProvider = GlanceTheme.colors.primary,
    content: ColorProvider = GlanceTheme.colors.onPrimary,
) {
    CircleIconButton(
        imageProvider = ImageProvider(
            if (isPlaying) R.drawable.ic_media_pause else R.drawable.ic_media_play
        ),
        contentDescription = widgetString(
            if (isPlaying) R.string.widget_action_pause else R.string.widget_action_play
        ),
        onClick = actionRunCallback<TogglePlaybackAction>(),
        modifier = GlanceModifier.size(size),
        backgroundColor = background,
        contentColor = content,
    )
}

/** Secondary transport: a tonal squircle against the hero's circle. */
@Composable
internal fun WidgetSquareButton(
    @DrawableRes iconRes: Int,
    description: String,
    action: Action,
    size: Dp = 48.dp,
    background: ColorProvider = GlanceTheme.colors.surfaceVariant,
    content: ColorProvider = GlanceTheme.colors.onSurfaceVariant,
) {
    SquareIconButton(
        imageProvider = ImageProvider(iconRes),
        contentDescription = description,
        onClick = action,
        modifier = GlanceModifier.size(size),
        backgroundColor = background,
        contentColor = content,
    )
}

/**
 * A secondary control on a card, one tonal step off the card itself.
 *
 * [WidgetSquareButton]'s surfaceVariant default is for controls standing on
 * the wallpaper (Bloom, Vinyl). On a card it is the wrong family:
 * `widgetBackground` is secondaryContainer moved five tones lighter in light
 * and ten darker in dark (Glance's own adjustment, read off the AAR), so the
 * unadjusted secondaryContainer is exactly one visible step from the card in
 * both themes and stays in its hue, where surfaceVariant read as a grey patch.
 */
@Composable
internal fun CardSquareButton(
    @DrawableRes iconRes: Int,
    description: String,
    action: Action,
    size: Dp = 44.dp,
) {
    WidgetSquareButton(
        iconRes = iconRes,
        description = description,
        action = action,
        size = size,
        background = GlanceTheme.colors.secondaryContainer,
        content = GlanceTheme.colors.onSecondaryContainer,
    )
}

/**
 * Previous, hero, next on a card - five children, which matters: a Glance
 * container silently truncates past ten, so groups are kept small and composed
 * rather than flattened into their parent. [withPrevious] false drops to hero
 * and next, for a column too narrow to hold all three at real touch sizes.
 */
@Composable
internal fun CardTransportRow(
    snapshot: PlayerWidgetSnapshot,
    heroSize: Dp = 48.dp,
    sideSize: Dp = 40.dp,
    gap: Dp = 6.dp,
    withPrevious: Boolean = true,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (withPrevious) {
            CardSquareButton(
                iconRes = R.drawable.ic_media_previous,
                description = widgetString(R.string.widget_action_previous),
                action = actionRunCallback<SkipPreviousAction>(),
                size = sideSize,
            )
            Spacer(modifier = GlanceModifier.width(gap))
        }
        HeroPlayButton(isPlaying = snapshot.isPlaying, size = heroSize)
        Spacer(modifier = GlanceModifier.width(gap))
        CardSquareButton(
            iconRes = R.drawable.ic_media_next,
            description = widgetString(R.string.widget_action_next),
            action = actionRunCallback<SkipNextAction>(),
            size = sideSize,
        )
    }
}

/** The width [CardTransportRow] needs with all three controls. */
internal fun cardTransportWidth(heroSize: Dp, sideSize: Dp, gap: Dp): Dp =
    sideSize * 2 + heroSize + gap * 2

// -------------------------------------------------------------------- artwork

/**
 * The cover, cut to [shape] - this is where the widget family gets its
 * character. Falls back to the same silhouette in a flat tonal color with the
 * launcher mark on it, so a missing cover is never a hole or a square.
 */
@Composable
internal fun WidgetArtwork(
    artwork: Bitmap?,
    size: Dp,
    shape: ArtworkShape,
    modifier: GlanceModifier = GlanceModifier,
) {
    val density = LocalContext.current.resources.displayMetrics.density
    val px = (size.value * density).toInt().coerceIn(1, 1024)
    if (artwork != null) {
        Image(
            provider = ImageProvider(maskedArtwork(artwork, shape, px)),
            contentDescription = null,
            modifier = modifier.size(size),
        )
    } else {
        val tint = GlanceTheme.colors.secondaryContainer.read().toArgb()
        Box(
            modifier = modifier.size(size),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(shapeTile(shape, px, tint)),
                contentDescription = null,
                modifier = GlanceModifier.size(size),
            )
            Image(
                provider = ImageProvider(R.drawable.ic_playback_notification),
                contentDescription = null,
                modifier = GlanceModifier.size(size / 2.6f),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onSecondaryContainer),
            )
        }
    }
}

// ----------------------------------------------------------------------- text

@Composable
internal fun WidgetTitle(
    text: String,
    color: ColorProvider = GlanceTheme.colors.onSurface,
    fontSize: TextUnit = 16.sp,
    fontWeight: FontWeight = FontWeight.Bold,
    maxLines: Int = 1,
) {
    Text(
        text = text,
        style = TextStyle(color = color, fontSize = fontSize, fontWeight = fontWeight),
        maxLines = maxLines,
    )
}

@Composable
internal fun WidgetSubtitle(
    text: String,
    color: ColorProvider = GlanceTheme.colors.onSurfaceVariant,
    fontSize: TextUnit = 12.sp,
) {
    Text(
        text = text,
        style = TextStyle(color = color, fontSize = fontSize, fontWeight = FontWeight.Medium),
        maxLines = 1,
    )
}

/**
 * Nothing has ever played. Centred rather than left-aligned - the left-aligned
 * version left two thirds of the card empty and read as a widget that had
 * failed to load rather than one waiting to be used.
 */
@Composable
internal fun WidgetEmptyState() {
    // The stacked version needs about 130dp; below that it would be clipped
    // away with nothing logged, so short cells get the row instead.
    val compact = androidx.glance.LocalSize.current.height < 130.dp
    if (compact) {
        Row(
            modifier = GlanceModifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EmptyMark(38.dp)
            Spacer(modifier = GlanceModifier.width(12.dp))
            Column {
                WidgetTitle(widgetString(R.string.app_name), fontSize = 14.sp)
                WidgetSubtitle(widgetString(R.string.widget_tap_to_listen), fontSize = 11.sp)
            }
        }
        return
    }
    Column(
        modifier = GlanceModifier.fillMaxSize().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EmptyMark(56.dp)
        Spacer(modifier = GlanceModifier.height(10.dp))
        WidgetTitle(widgetString(R.string.app_name))
        Spacer(modifier = GlanceModifier.height(2.dp))
        WidgetSubtitle(widgetString(R.string.widget_tap_to_listen))
    }
}

/** The brand mark on a clover, so even the empty state carries the shape language. */
@Composable
private fun EmptyMark(size: Dp) {
    val density = LocalContext.current.resources.displayMetrics.density
    val px = (size.value * density).toInt().coerceAtLeast(1)
    val tint = GlanceTheme.colors.primaryContainer.read().toArgb()
    Box(modifier = GlanceModifier.size(size), contentAlignment = Alignment.Center) {
        Image(
            provider = ImageProvider(shapeTile(ArtworkShape.CLOVER, px, tint)),
            contentDescription = null,
            modifier = GlanceModifier.size(size),
        )
        Image(
            provider = ImageProvider(R.mipmap.ic_launcher_round),
            contentDescription = null,
            modifier = GlanceModifier.size(size * 0.6f),
        )
    }
}

/*
 * There is no progress strip in the kit on purpose. The snapshot's position is
 * the one read at the last playback event, and nothing ticks it forward in a
 * RemoteViews tree, so a bar drawn from it sat at wherever the song was when it
 * started or was last paused - a number that was wrong almost all the time it
 * was on screen. Duration and position stay in the snapshot for anything that
 * can be honest about them.
 */
