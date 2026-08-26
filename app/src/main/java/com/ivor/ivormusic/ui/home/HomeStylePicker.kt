package com.ivor.ivormusic.ui.home
import androidx.compose.ui.res.stringResource
import com.ivor.ivormusic.R

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * The Classic-versus-Spotlight choice, as two wireframes rather than two words.
 *
 * A home layout is a shape, and nobody can pick between "classic" and "dense"
 * from the labels alone. The previews are deliberately abstract - blocks, not a
 * screenshot - so they never go stale when either Home changes, the way a
 * bitmap mock would. Same reasoning as PlayerStylePreview's wireframes in the
 * player-style picker.
 *
 * Non-lazy on purpose: this renders inside onboarding's scrolling column, and a
 * lazy container nested in a scrollable parent has unbounded height.
 */
@Composable
fun HomeStylePicker(
    spotlightHome: Boolean,
    onSpotlightHomeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HomeStyleOption(
            title = stringResource(R.string.hs_classic),
            description = stringResource(R.string.hs_classic_desc),
            selected = !spotlightHome,
            onClick = { onSpotlightHomeChange(false) },
            modifier = Modifier.weight(1f),
        ) { ClassicHomeWireframe() }

        HomeStyleOption(
            title = stringResource(R.string.sp_spotlight_home),
            description = stringResource(R.string.hs_spotlight_desc),
            selected = spotlightHome,
            onClick = { onSpotlightHomeChange(true) },
            modifier = Modifier.weight(1f),
        ) { SpotlightHomeWireframe() }
    }
}

@Composable
private fun HomeStyleOption(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    preview: @Composable () -> Unit,
) {
    // The selection ring grows rather than appearing, so switching between the
    // two reads as one control moving instead of two independently blinking.
    val ringWidth by animateDpAsState(
        targetValue = if (selected) 2.dp else 0.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy),
        label = "homeStyleRing",
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        modifier = modifier.border(
            width = ringWidth,
            color = MaterialTheme.colorScheme.primary,
            shape = RoundedCornerShape(20.dp),
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(8.dp),
            ) { preview() }
            Spacer(Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Hero block, then a row of wide cards: the shape of the current Home. */
@Composable
private fun ClassicHomeWireframe() {
    val block = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    val faint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        WireBar(color = block, height = 30.dp, fraction = 1f)
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(3) {
                Box(
                    Modifier
                        .size(width = 22.dp, height = 26.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(faint),
                )
            }
        }
        WireBar(color = faint, height = 6.dp, fraction = 0.5f)
    }
}

/** Chips, a two-column shortcut grid, then a shelf: the shape of Spotlight. */
@Composable
private fun SpotlightHomeWireframe() {
    val block = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    val faint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        // chips
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(3) {
                Box(
                    Modifier
                        .size(width = 14.dp, height = 6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (it == 0) block else faint),
                )
            }
        }
        // shortcut grid, two columns
        repeat(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(2) {
                    Box(
                        Modifier
                            .size(width = 27.dp, height = 12.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(block.copy(alpha = 0.5f)),
                    )
                }
            }
        }
        // a shelf of artwork cards
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(3) {
                Box(
                    Modifier
                        .size(18.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(faint),
                )
            }
        }
    }
}

@Composable
private fun WireBar(color: Color, height: androidx.compose.ui.unit.Dp, fraction: Float) {
    Box(
        Modifier
            .fillMaxWidth(fraction)
            .height(height)
            .clip(RoundedCornerShape(3.dp))
            .background(color),
    )
}
