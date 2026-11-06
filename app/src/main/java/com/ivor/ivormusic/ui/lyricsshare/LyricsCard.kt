package com.ivor.ivormusic.ui.lyricsshare

import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.ui.components.SongArtwork
import com.ivor.ivormusic.ui.player.EditorialPolygonShape
import com.ivor.ivormusic.ui.theme.PaletteStyle
import com.ivor.ivormusic.ui.theme.buildSeedColorScheme

/** What a share request carries: the song and its non-blank lines, taken when it opened. */
data class LyricsShareRequest(
    val song: Song,
    val lines: List<String>,
    /** The line to start with picked, if any. */
    val initialLine: Int?,
    /** Tells two openings of the same song apart, so each starts fresh. */
    val openedAt: Long = System.nanoTime(),
)

/** Width of an exported card. Stories and posts both scale from 1080. */
internal const val CARD_EXPORT_WIDTH_PX = 1080

/**
 * The card is designed on a 360-wide grid and laid out at three pixels a unit,
 * so the exported image is 1080 wide whatever the phone's density, font scale
 * or interface scale. Only the preview on screen is scaled, in the draw phase.
 */
private const val PX_PER_UNIT = CARD_EXPORT_WIDTH_PX / 360f

internal enum class CardAspect(val heightUnits: Int, @StringRes val labelRes: Int) {
    STORY(640, R.string.lyrics_share_story),
    SQUARE(360, R.string.lyrics_share_square);

    val heightPx: Int get() = (heightUnits * PX_PER_UNIT).toInt()
}

internal enum class LyricsCardStyle(@StringRes val labelRes: Int) {
    ALBUM(R.string.lyrics_card_album),
    GLOW(R.string.lyrics_card_glow),
    POSTER(R.string.lyrics_card_poster),
    SHAPE(R.string.lyrics_card_shape),
}

/** Converts design units into this density's dp and sp, so a unit is always three pixels. */
@Immutable
internal class CardUnits(private val density: Density) {
    fun dp(units: Float): Dp = with(density) { (units * PX_PER_UNIT).toDp() }
    fun dp(units: Int): Dp = dp(units.toFloat())
    fun sp(units: Float): TextUnit = with(density) { (units * PX_PER_UNIT).toSp() }
}

/**
 * One colourway of a card: the page, the words on it, and an accent for the
 * decoration. All of them roles of the cover's own schemes (see
 * [rememberCardPalettes]), never a literal.
 */
@Immutable
internal data class CardPalette(
    val container: Color,
    val containerEnd: Color,
    val content: Color,
    val accent: Color,
)

/**
 * The colourways offered for [seed], the cover's colour: deep, vivid, soft,
 * the third tone, night and paper. Built from the light, dark and vibrant
 * schemes of the one seed, so every swatch belongs to the album.
 */
@Composable
internal fun rememberCardPalettes(seed: Color): List<CardPalette> = remember(seed) {
    val dark = buildSeedColorScheme(seed, dark = true, style = PaletteStyle.TONAL_SPOT)
    val light = buildSeedColorScheme(seed, dark = false, style = PaletteStyle.TONAL_SPOT)
    val vivid = buildSeedColorScheme(seed, dark = false, style = PaletteStyle.VIBRANT)
    listOf(
        CardPalette(dark.primaryContainer, dark.surfaceContainerLowest, dark.onPrimaryContainer, dark.primary),
        CardPalette(vivid.primary, vivid.tertiary, vivid.onPrimary, vivid.primaryContainer),
        CardPalette(light.primaryContainer, light.tertiaryContainer, light.onPrimaryContainer, light.primary),
        CardPalette(dark.tertiaryContainer, dark.surfaceContainerLowest, dark.onTertiaryContainer, dark.tertiary),
        CardPalette(dark.surfaceContainerLowest, dark.surfaceContainerHigh, dark.onSurface, dark.primary),
        CardPalette(light.surfaceContainerLowest, light.surfaceContainerHigh, light.onSurface, light.primary),
    )
}

/**
 * The card itself, at export size. The caller lays it out at
 * [CARD_EXPORT_WIDTH_PX] by [CardAspect.heightPx] and scales the preview.
 */
@Composable
internal fun LyricsCard(
    song: Song,
    lines: List<String>,
    style: LyricsCardStyle,
    palette: CardPalette,
    aspect: CardAspect,
    lyricsFont: FontFamily?,
    /** The words' entrance, 0 to 1: they rise into place after the card lands. */
    reveal: () -> Float,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val u = remember(density) { CardUnits(density) }
    Box(modifier = modifier.fillMaxSize().background(palette.container)) {
        when (style) {
            LyricsCardStyle.ALBUM -> AlbumCard(song, lines, palette, aspect, lyricsFont, reveal, u)
            LyricsCardStyle.GLOW -> GlowCard(song, lines, palette, aspect, lyricsFont, reveal, u)
            LyricsCardStyle.POSTER -> PosterCard(song, lines, palette, aspect, lyricsFont, reveal, u)
            LyricsCardStyle.SHAPE -> ShapeCard(song, lines, palette, aspect, lyricsFont, reveal, u)
        }
    }
}

/** The width of the Album card's quote rail, in card units. */
private const val RAIL_UNITS = 3.5f

/** The gap between two lyric lines, as a share of the text size. */
private const val LINE_GAP_EM = 0.5f

/** The words, as large as the room allows, rising into place with [reveal]. */
@Composable
private fun CardLyrics(
    lines: List<String>,
    palette: CardPalette,
    lyricsFont: FontFamily?,
    reveal: () -> Float,
    u: CardUnits,
    maxUnits: Float,
    align: TextAlign,
    modifier: Modifier = Modifier,
    /** A rounded bar down the start edge, the height of the words; null for none. */
    rail: Color? = null,
) {
    val style = TextStyle(
        color = palette.content,
        fontFamily = lyricsFont,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.015).em,
        lineHeight = 1.14.em,
        textAlign = align,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
        // A wrapped line splits evenly rather than leaving one word behind.
        lineBreak = LineBreak.Heading,
    )
    // Each lyric line is its own paragraph with a gap after it, so a line that
    // wraps never runs into the next one. The gap is an empty line set at a
    // fraction of the text's size (em, so it shrinks with the auto-size), and
    // its own newline is inside that span or the gap would be a full line.
    val text = remember(lines) {
        buildAnnotatedString {
            lines.forEachIndexed { index, line ->
                if (index > 0) {
                    append("\n")
                    withStyle(SpanStyle(fontSize = LINE_GAP_EM.em)) { append("\u200B\n") }
                }
                append(line)
            }
        }
    }
    BasicText(
        text = text,
        style = style,
        autoSize = TextAutoSize.StepBased(
            minFontSize = u.sp(11f),
            maxFontSize = u.sp(maxUnits),
            stepSize = u.sp(0.5f),
        ),
        modifier = modifier
            .graphicsLayer {
                val r = reveal().coerceIn(0f, 1.2f)
                alpha = r.coerceAtMost(1f)
                translationY = (1f - r) * 48f * PX_PER_UNIT / 3f
            }
            .then(
                if (rail == null) Modifier else Modifier
                    .drawBehind {
                        val w = RAIL_UNITS * PX_PER_UNIT
                        drawRoundRect(rail, size = Size(w, size.height), cornerRadius = CornerRadius(w / 2f))
                    }
                    .padding(start = u.dp(RAIL_UNITS + 14f))
            ),
    )
}

@Composable
private fun SongCredit(
    song: Song,
    palette: CardPalette,
    u: CardUnits,
    coverUnits: Int,
    align: Alignment.Horizontal = Alignment.Start,
    showCover: Boolean = true,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (showCover) {
            SongArtwork(
                song = song,
                contentDescription = null,
                modifier = Modifier
                    .size(u.dp(coverUnits))
                    .clip(RoundedCornerShape(u.dp(coverUnits * 0.22f))),
            )
            Spacer(Modifier.width(u.dp(12)))
        }
        Column(horizontalAlignment = align) {
            Text(
                text = song.title,
                color = palette.content,
                fontSize = u.sp(15f),
                lineHeight = 1.25.em,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (song.artist.isNotBlank()) {
                Text(
                    text = song.artist,
                    color = palette.content.copy(alpha = 0.72f),
                    fontSize = u.sp(12.5f),
                    lineHeight = 1.25.em,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The app's mark, small, so a shared card says where it was made without shouting. */
@Composable
private fun Wordmark(palette: CardPalette, u: CardUnits) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Rounded.GraphicEq,
            contentDescription = null,
            tint = palette.content.copy(alpha = 0.62f),
            modifier = Modifier.size(u.dp(13)),
        )
        Spacer(Modifier.width(u.dp(4)))
        Text(
            text = "Koda",
            color = palette.content.copy(alpha = 0.62f),
            fontSize = u.sp(11.5f),
            lineHeight = 1.2.em,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.04.em,
        )
    }
}

/**
 * The album's own light: the page lit from two corners in the cover's colours
 * over a faint wash of the cover itself, the cover large with its colour
 * glowing out behind it, and the words set against a rail in the accent, the
 * way a pulled quote is.
 */
@Composable
private fun AlbumCard(
    song: Song, lines: List<String>, palette: CardPalette, aspect: CardAspect,
    font: FontFamily?, reveal: () -> Float, u: CardUnits,
) {
    val story = aspect == CardAspect.STORY
    val blurs = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    Box(Modifier.fillMaxSize().background(palette.container))
    if (blurs) {
        SongArtwork(
            song = song,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = 1.6f
                    scaleY = 1.6f
                    alpha = 0.28f
                }
                .blur(u.dp(48)),
        )
    }
    // Light from the top end in the accent, depth from the bottom start: a
    // soft mesh rather than a flat fill.
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                val w = size.width
                val h = size.height
                drawRect(
                    Brush.radialGradient(
                        listOf(palette.accent.copy(alpha = 0.5f), palette.accent.copy(alpha = 0f)),
                        center = Offset(w * 0.98f, h * 0.02f),
                        radius = w * 0.95f,
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(palette.containerEnd.copy(alpha = 0.95f), palette.containerEnd.copy(alpha = 0f)),
                        center = Offset(w * 0.02f, h * 1.02f),
                        radius = w * 1.15f,
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(palette.accent.copy(alpha = 0.16f), palette.accent.copy(alpha = 0f)),
                        center = Offset(w * 0.15f, h * 0.42f),
                        radius = w * 0.6f,
                    )
                )
            }
    )
    Column(Modifier.fillMaxSize().padding(u.dp(if (story) 30 else 26))) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val cover = if (story) 88 else 66
            Box(contentAlignment = Alignment.Center) {
                // The cover's own colour, glowing out from behind it.
                if (blurs) {
                    SongArtwork(
                        song = song,
                        contentDescription = null,
                        modifier = Modifier
                            .size(u.dp(cover))
                            .graphicsLayer {
                                scaleX = 1.12f
                                scaleY = 1.12f
                                translationY = 6f * PX_PER_UNIT
                                alpha = 0.85f
                            }
                            .blur(u.dp(16))
                    )
                }
                SongArtwork(
                    song = song,
                    contentDescription = null,
                    modifier = Modifier
                        .size(u.dp(cover))
                        .clip(RoundedCornerShape(u.dp(cover * 0.2f))),
                )
            }
            Spacer(Modifier.width(u.dp(16)))
            Column(Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    color = palette.content,
                    fontSize = u.sp(if (story) 19f else 16.5f),
                    lineHeight = 1.2.em,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.01).em,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (song.artist.isNotBlank()) {
                    Spacer(Modifier.height(u.dp(2)))
                    Text(
                        text = song.artist,
                        color = palette.content.copy(alpha = 0.72f),
                        fontSize = u.sp(if (story) 14f else 12.5f),
                        lineHeight = 1.25.em,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(Modifier.height(u.dp(if (story) 30 else 20)))
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
            CardLyrics(
                lines, palette, font, reveal, u,
                maxUnits = if (story) 25f else 19f,
                align = TextAlign.Start,
                rail = palette.accent,
            )
        }
        Spacer(Modifier.height(u.dp(16)))
        Wordmark(palette, u)
    }
}

/** The cover itself as light: blurred full bleed, the words centred over it. */
@Composable
private fun GlowCard(
    song: Song, lines: List<String>, palette: CardPalette, aspect: CardAspect,
    font: FontFamily?, reveal: () -> Float, u: CardUnits,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        SongArtwork(
            song = song,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = 1.5f
                    scaleY = 1.5f
                }
                .blur(u.dp(36)),
        )
    }
    // The palette washes over the cover, deepest where the words sit, so any
    // cover reads under any of the six colourways.
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to palette.container.copy(alpha = 0.55f),
                    0.5f to palette.container.copy(alpha = 0.72f),
                    1f to palette.containerEnd.copy(alpha = 0.9f),
                )
            )
    )
    Column(
        Modifier.fillMaxSize().padding(u.dp(30)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Wordmark(palette, u)
        Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = u.dp(18)), contentAlignment = Alignment.Center) {
            CardLyrics(
                lines, palette, font, reveal, u,
                maxUnits = if (aspect == CardAspect.STORY) 25f else 19f,
                align = TextAlign.Center,
            )
        }
        SongCredit(song, palette, u, coverUnits = 44)
    }
}

/** An editorial page: the cover large, an oversized quote mark, the words beneath. */
@Composable
private fun PosterCard(
    song: Song, lines: List<String>, palette: CardPalette, aspect: CardAspect,
    font: FontFamily?, reveal: () -> Float, u: CardUnits,
) {
    val story = aspect == CardAspect.STORY
    Column(Modifier.fillMaxSize().padding(u.dp(24))) {
        if (story) {
            SongArtwork(
                song = song,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(u.dp(18))),
            )
            Spacer(Modifier.height(u.dp(6)))
        } else {
            SongCredit(song, palette, u, coverUnits = 54)
        }
        Text(
            text = "“",
            color = palette.accent,
            fontSize = u.sp(if (story) 64f else 52f),
            fontWeight = FontWeight.Black,
            lineHeight = u.sp(if (story) 52f else 40f),
            modifier = Modifier.offset(x = u.dp(-2), y = u.dp(4)),
        )
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopStart) {
            CardLyrics(
                lines, palette, font, reveal, u,
                maxUnits = if (story) 22f else 18f,
                align = TextAlign.Start,
            )
        }
        Spacer(Modifier.height(u.dp(14)))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (story) {
                Box(Modifier.weight(1f)) { SongCredit(song, palette, u, coverUnits = 0, showCover = false) }
            } else {
                Spacer(Modifier.weight(1f))
            }
            Wordmark(palette, u)
        }
    }
}

/** Material's own shapes as the page's art: a burst behind, a clover for the cover. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BoxScope.ShapeCard(
    song: Song, lines: List<String>, palette: CardPalette, aspect: CardAspect,
    font: FontFamily?, reveal: () -> Float, u: CardUnits,
) {
    val story = aspect == CardAspect.STORY
    val burst = remember { EditorialPolygonShape(MaterialShapes.Cookie12Sided) }
    val clover = remember { EditorialPolygonShape(MaterialShapes.Clover4Leaf) }
    val flower = remember { EditorialPolygonShape(MaterialShapes.Flower) }
    Box(
        Modifier
            .align(Alignment.TopEnd)
            .offset(x = u.dp(120), y = u.dp(-110))
            .requiredSize(u.dp(380))
            .graphicsLayer { rotationZ = 14f }
            .clip(burst)
            .background(palette.accent.copy(alpha = 0.2f))
    )
    Box(
        Modifier
            .align(Alignment.BottomStart)
            .offset(x = u.dp(-70), y = u.dp(70))
            .requiredSize(u.dp(220))
            .graphicsLayer { rotationZ = -18f }
            .clip(flower)
            .background(palette.accent.copy(alpha = 0.14f))
    )
    Column(
        Modifier.fillMaxSize().padding(u.dp(30)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        SongArtwork(
            song = song,
            contentDescription = null,
            modifier = Modifier
                .size(u.dp(if (story) 132 else 84))
                .clip(clover),
        )
        Spacer(Modifier.height(u.dp(if (story) 26 else 14)))
        Box(
            Modifier.weight(1f, fill = false).fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            CardLyrics(
                lines, palette, font, reveal, u,
                maxUnits = if (story) 22f else 17f,
                align = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(u.dp(if (story) 26 else 14)))
        Text(
            text = listOf(song.title, song.artist).filter { it.isNotBlank() }.joinToString("  ·  "),
            color = palette.content.copy(alpha = 0.78f),
            fontSize = u.sp(12.5f),
            lineHeight = 1.25.em,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(u.dp(10)))
        Wordmark(palette, u)
    }
}
