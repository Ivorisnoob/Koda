package com.ivor.ivormusic.ui.player
import com.ivor.ivormusic.R

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Interests
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.SwipeRight
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.RoundedPolygon
import com.ivor.ivormusic.data.PlayerStyle

/**
 * The one description of a player style.
 *
 * There used to be three hardcoded lists - the settings chips, the onboarding
 * pair and the style wheel - and adding a style meant remembering all of them.
 * They now all read this, so a new style is one entry here plus the branch in
 * [ExpandablePlayer] that renders it.
 *
 * [polygon] is the die-cut identity the wheel blooms into; shape signals which
 * style you are aiming at before the label is readable.
 */
internal data class PlayerStyleInfo(
    val style: PlayerStyle,
    val label: String,
    val subtitle: String,
    val icon: ImageVector,
    val polygon: RoundedPolygon
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal val playerStyleCatalog: List<PlayerStyleInfo> = listOf(
    PlayerStyleInfo(
        PlayerStyle.CLASSIC, "Classic", "Button controls for playback",
        Icons.Rounded.PlayCircle, MaterialShapes.Circle
    ),
    PlayerStyleInfo(
        PlayerStyle.GESTURE, "Gesture", "Swipe album art to navigate",
        Icons.Rounded.SwipeRight, MaterialShapes.Pill
    ),
    PlayerStyleInfo(
        PlayerStyle.EDITORIAL, "Editorial", "Two-tone magazine layout",
        Icons.Rounded.Newspaper, MaterialShapes.Flower
    ),
    // The enum key stays POSTER for SharedPreferences compatibility; every
    // user-facing string says Canvas.
    PlayerStyleInfo(
        PlayerStyle.POSTER, "Canvas", "Full-bleed album art",
        Icons.Rounded.Wallpaper, MaterialShapes.Gem
    ),
    PlayerStyleInfo(
        PlayerStyle.BENTO, "Bento", "Squishy grid of flat tiles",
        Icons.Rounded.GridView, MaterialShapes.Cookie4Sided
    ),
    PlayerStyleInfo(
        PlayerStyle.STICKER, "Sticker", "Die-cut art with toy physics",
        Icons.Rounded.Interests, MaterialShapes.Clover4Leaf
    ),
    PlayerStyleInfo(
        PlayerStyle.MORPH, "Morph", "Living shape that breathes",
        Icons.Rounded.Animation, MaterialShapes.SoftBurst
    ),
    PlayerStyleInfo(
        PlayerStyle.DIAL, "Dial", "Rotary ring, spin to scrub",
        Icons.Rounded.RadioButtonChecked, MaterialShapes.Sunny
    )
)

internal fun playerStyleInfo(style: PlayerStyle): PlayerStyleInfo =
    playerStyleCatalog.firstOrNull { it.style == style } ?: playerStyleCatalog.first()

/**
 * Picks a player style from a grid of tiles that each draw a miniature of the
 * layout they select.
 *
 * A name and an icon do not tell anyone what "Bento" or "Editorial" will look
 * like, and this is a choice about appearance - so each tile sketches the real
 * thing: Classic has its button row, Bento its grid of tiles, Dial its ring.
 *
 * Deliberately not lazy. It renders inside a scrolling settings page and inside
 * onboarding's `verticalScroll` column, and a lazy grid nested in a scrollable
 * parent has unbounded height.
 */
@Composable
internal fun PlayerStylePicker(
    currentStyle: PlayerStyle,
    onStyleSelected: (PlayerStyle) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        playerStyleCatalog.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { info ->
                    PlayerStyleTile(
                        info = info,
                        selected = info.style == currentStyle,
                        onClick = { onStyleSelected(info.style) },
                        modifier = Modifier.weight(1f)
                    )
                }
                // Keeps a trailing odd tile at half width instead of stretching
                // it across the row.
                if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PlayerStyleTile(
    info: PlayerStyleInfo,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "tileScale"
    )

    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val labelColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Column(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(20.dp))
            .background(containerColor)
            .then(
                if (selected) {
                    Modifier.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(20.dp)
                    )
                } else {
                    Modifier
                }
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(10.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.25f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
        ) {
            PlayerStylePreview(
                style = info.style,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            )

            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = playerStyleLabel(info.style),
            color = labelColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = playerStyleSubtitle(info.style),
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontSize = 11.sp,
            lineHeight = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * A wireframe of each player style, abstract enough to stay honest when the
 * real screens change but specific enough to be recognisable.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlayerStylePreview(style: PlayerStyle, modifier: Modifier = Modifier) {
    val art = MaterialTheme.colorScheme.primary
    val soft = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
    val line = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)

    Box(modifier = modifier) {
        when (style) {
            // Square art, a title, and the row of transport buttons.
            PlayerStyle.CLASSIC -> Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(art)
                )
                PreviewLine(color = line, widthFraction = 0.6f)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    repeat(3) { index ->
                        Box(
                            modifier = Modifier
                                .size(if (index == 1) 10.dp else 7.dp)
                                .clip(CircleShape)
                                .background(if (index == 1) art else soft)
                        )
                    }
                }
            }

            // Art card with the neighbouring tracks peeking in from both sides.
            PlayerStyle.GESTURE -> Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(8.dp)
                        .fillMaxHeight(0.55f)
                        .clip(RoundedCornerShape(4.dp))
                        .background(soft)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(0.8f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(art)
                )
                Box(
                    modifier = Modifier
                        .width(8.dp)
                        .fillMaxHeight(0.55f)
                        .clip(RoundedCornerShape(4.dp))
                        .background(soft)
                )
            }

            // Two-tone split with a stack of headline rules.
            PlayerStyle.EDITORIAL -> Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .background(art)
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    PreviewLine(color = line, widthFraction = 0.9f)
                    PreviewLine(color = line, widthFraction = 0.65f)
                    PreviewLine(color = line, widthFraction = 0.4f)
                }
            }

            // Edge-to-edge art with the text scrim across the bottom.
            PlayerStyle.POSTER -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(6.dp))
                    .background(art)
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    PreviewLine(
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                        widthFraction = 0.7f
                    )
                    PreviewLine(
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.55f),
                        widthFraction = 0.45f
                    )
                }
            }

            // Grid of unequal tiles.
            PlayerStyle.BENTO -> Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.4f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1.6f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(6.dp))
                            .background(art)
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(RoundedCornerShape(5.dp))
                                .background(soft)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(RoundedCornerShape(5.dp))
                                .background(soft)
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    repeat(3) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(5.dp))
                                .background(soft)
                        )
                    }
                }
            }

            // Die-cut artwork, tilted like a sticker that has been peeled on.
            PlayerStyle.STICKER -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                PreviewPolygon(
                    polygon = MaterialShapes.Clover4Leaf,
                    color = art,
                    modifier = Modifier
                        .fillMaxSize(0.82f)
                        .rotate(-12f)
                )
            }

            // One soft blob, mid-breath.
            PlayerStyle.MORPH -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                PreviewPolygon(
                    polygon = MaterialShapes.SoftBurst,
                    color = art,
                    modifier = Modifier.fillMaxSize(0.9f)
                )
            }

            // Scrub ring with its handle.
            PlayerStyle.DIAL -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight(0.92f)
                        .aspectRatio(1f)
                        .clip(CircleShape)
                        .border(width = 4.dp, color = soft, shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize(0.42f)
                            .clip(CircleShape)
                            .background(art)
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(art)
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewLine(color: Color, widthFraction: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(color)
    )
}

@Composable
private fun PreviewPolygon(
    polygon: RoundedPolygon,
    color: Color,
    modifier: Modifier = Modifier
) {
    val shape = remember(polygon) { EditorialPolygonShape(polygon) }
    Box(
        modifier = modifier
            .clip(shape)
            .background(color)
    )
}


@Composable
internal fun playerStyleLabel(style: PlayerStyle): String = when (style) {
    PlayerStyle.CLASSIC -> stringResource(R.string.ps_style_classic)
    PlayerStyle.GESTURE -> stringResource(R.string.ps_style_gesture)
    PlayerStyle.EDITORIAL -> stringResource(R.string.ps_style_editorial)
    PlayerStyle.POSTER -> stringResource(R.string.ps_style_canvas)
    PlayerStyle.BENTO -> stringResource(R.string.ps_style_bento)
    PlayerStyle.STICKER -> stringResource(R.string.ps_style_sticker)
    PlayerStyle.MORPH -> stringResource(R.string.ps_style_morph)
    PlayerStyle.DIAL -> stringResource(R.string.ps_style_dial)
}

@Composable
internal fun playerStyleSubtitle(style: PlayerStyle): String = when (style) {
    PlayerStyle.CLASSIC -> stringResource(R.string.ps_sub_classic)
    PlayerStyle.GESTURE -> stringResource(R.string.ps_sub_gesture)
    PlayerStyle.EDITORIAL -> stringResource(R.string.ps_sub_editorial)
    PlayerStyle.POSTER -> stringResource(R.string.ps_sub_canvas)
    PlayerStyle.BENTO -> stringResource(R.string.ps_sub_bento)
    PlayerStyle.STICKER -> stringResource(R.string.ps_sub_sticker)
    PlayerStyle.MORPH -> stringResource(R.string.ps_sub_morph)
    PlayerStyle.DIAL -> stringResource(R.string.ps_sub_dial)
}
