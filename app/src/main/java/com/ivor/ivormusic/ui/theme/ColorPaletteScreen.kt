package com.ivor.ivormusic.ui.theme
import androidx.compose.ui.res.stringResource
import com.ivor.ivormusic.R

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * Color palette picker. Each palette is shown, without a name, as a tiny
 * theme preview built from its real Material 3 accent roles: a primary "button"
 * bar carrying an onPrimary label, a secondaryContainer chip with an
 * onSecondaryContainer mark, a tertiary accent, and a primaryContainer track
 * with a primary fill. The composition of roles is the palette's identity and
 * doubles as a live demo of how the colors sit in real UI. Tapping applies the
 * palette instantly. Dynamic is a full-width, icon-only hero.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorPaletteScreen(
    currentPalette: String,
    onPaletteSelected: (String) -> Unit,
    isDarkMode: Boolean,
    onBack: () -> Unit,
    contentPadding: PaddingValues = PaddingValues()
) {
    val textColor = MaterialTheme.colorScheme.onBackground
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(contentPadding)
    ) {
        TopAppBar(
            title = {
                Text(
                    text = stringResource(R.string.sp_color_palette),
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.headlineMedium
                )
            },
            navigationIcon = {
                IconButton(
                    onClick = onBack,
                    shapes = IconButtonDefaults.shapes(),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = textColor
                    ),
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back)
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 160.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                AnimatedVisibility(
                    visible = isVisible,
                    enter = fadeIn(tween(350)) + slideInVertically(
                        initialOffsetY = { it / 4 },
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                    )
                ) {
                    DynamicPaletteCard(
                        selected = currentPalette == DYNAMIC_PALETTE_ID,
                        onClick = { onPaletteSelected(DYNAMIC_PALETTE_ID) }
                    )
                }
            }

            PALETTE_CATEGORIES.forEach { category ->
                item(span = { GridItemSpan(maxLineSpan) }, key = "divider_$category") {
                    SquigglyDivider(
                        color = secondaryTextColor.copy(alpha = 0.4f),
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }
                items(
                    items = APP_PALETTES.filter { it.category == category },
                    key = { it.id }
                ) { palette ->
                    PaletteCard(
                        roles = remember(palette.id, isDarkMode) { palette.roleColors(isDarkMode) },
                        selected = currentPalette == palette.id,
                        onClick = { onPaletteSelected(palette.id) }
                    )
                }
            }
        }
    }
}

/**
 * A wavy hairline used to separate palette groups — the M3 Expressive squiggle
 * motif rather than a label. Sampled sine wave, rounded stroke caps.
 */
@Composable
private fun SquigglyDivider(
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(18.dp)
            .padding(horizontal = 12.dp)
    ) {
        val amplitude = 4.dp.toPx()
        val waveLength = 22.dp.toPx()
        val midY = size.height / 2f
        val step = 2.dp.toPx()
        val path = Path().apply {
            moveTo(0f, midY)
            var x = 0f
            while (x <= size.width) {
                val y = midY + amplitude * sin(x / waveLength * 2f * PI.toFloat())
                lineTo(x, y)
                x += step
            }
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

/** A soft rounded "bar" filled with [fill], carrying a small on-color mark. */
@Composable
private fun RoleBar(
    fill: Color,
    mark: Color,
    modifier: Modifier = Modifier,
    height: Int = 30,
    corner: Int = 12,
    markWidth: Int = 30
) {
    Box(
        modifier = modifier
            .height(height.dp)
            .clip(RoundedCornerShape(corner.dp))
            .background(fill),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .padding(start = 10.dp)
                .width(markWidth.dp)
                .height(7.dp)
                .clip(CircleShape)
                .background(mark)
        )
    }
}

@Composable
private fun PaletteCard(
    roles: PaletteRoles,
    selected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.95f
            selected -> 1.03f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "cardScale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(148.dp)
            .scale(scale)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .then(
                if (selected) Modifier.border(3.dp, roles.primary, RoundedCornerShape(28.dp)) else Modifier
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            // primary "button" + onPrimary label
            RoleBar(fill = roles.primary, mark = roles.onPrimary, modifier = Modifier.fillMaxWidth())

            // secondaryContainer chip + tertiary accent circle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RoleBar(
                    fill = roles.secondaryContainer,
                    mark = roles.onSecondaryContainer,
                    modifier = Modifier.weight(1f),
                    height = 26,
                    corner = 13,
                    markWidth = 22
                )
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(roles.tertiary)
                )
            }

            // primaryContainer track with a primary fill (like a slider)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(CircleShape)
                    .background(roles.primaryContainer),
                contentAlignment = Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.55f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(roles.primary)
                )
            }
        }

        // Selected check chip using the palette's own primary/onPrimary pairing
        AnimatedVisibility(
            visible = selected,
            enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(roles.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = stringResource(R.string.cd_selected),
                    tint = roles.onPrimary,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}

@Composable
private fun DynamicPaletteCard(
    selected: Boolean,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "dynScale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp)
            .scale(scale)
            .clip(RoundedCornerShape(26.dp))
            .background(cs.surfaceContainerHigh)
            .then(
                if (selected) Modifier.border(3.dp, cs.primary, RoundedCornerShape(26.dp)) else Modifier
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Sparkle mark in a primary circle — icon signals "auto", no name
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(cs.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = stringResource(R.string.cp_dynamic_cd),
                    tint = cs.onPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Live role composition of the wallpaper scheme
            RoleBar(fill = cs.primary, mark = cs.onPrimary, modifier = Modifier.weight(1f), height = 28)
            Box(Modifier.size(28.dp).clip(CircleShape).background(cs.secondaryContainer))
            Box(Modifier.size(22.dp).clip(CircleShape).background(cs.tertiary))
        }

        AnimatedVisibility(
            visible = selected,
            enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(cs.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = stringResource(R.string.cd_selected),
                    tint = cs.onPrimary,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}
