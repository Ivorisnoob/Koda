package com.ivor.ivormusic.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.toPath
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.HomeNavigationCustomization
import com.ivor.ivormusic.data.ThemePreferences

/**
 * The music/video switch as one button that becomes the mode it is in.
 *
 * [trial October 2026] In music it is a flower holding a note, in the accent;
 * in video it is a soft square holding a play mark, in the tertiary. A tap
 * morphs one into the other on a bouncy spring, with a quarter turn on the way,
 * and the icon pops across. It is the size of the round buttons beside it, so
 * the bar reads as a row of equals with one of them alive, where the two-part
 * switch read as a control twice the width of everything else.
 *
 * The shape and its turn are read in a layer and the outline is rebuilt from
 * the spring's value, so the morph redraws this one button and nothing else.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ModeMorphButton(
    videoMode: Boolean,
    onVideoModeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = com.ivor.ivormusic.util.rememberKodaHaptics()
    val morph = remember { Morph(MaterialShapes.Flower, MaterialShapes.Cookie4Sided) }
    val progress by animateFloatAsState(
        targetValue = if (videoMode) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "modeMorph"
    )
    val container by animateColorAsState(
        targetValue = if (videoMode) MaterialTheme.colorScheme.tertiaryContainer
        else MaterialTheme.colorScheme.primaryContainer,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "modeContainer"
    )
    val content by animateColorAsState(
        targetValue = if (videoMode) MaterialTheme.colorScheme.onTertiaryContainer
        else MaterialTheme.colorScheme.onPrimaryContainer,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "modeContent"
    )
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "modePress"
    )
    val switchLabel = stringResource(
        if (videoMode) R.string.cd_switch_to_music else R.string.cd_switch_to_video
    )

    Box(
        modifier = modifier
            .size(44.dp)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = switchLabel,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onVideoModeChange(!videoMode)
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        // The shape, turning as it changes. The icon above it stays upright.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                    rotationZ = 90f * progress
                    // The spring overshoots; a morph is only defined from 0 to 1.
                    shape = UnitMorph(morph, progress.coerceIn(0f, 1f))
                    clip = true
                }
                .background(container)
        )
        AnimatedContent(
            targetState = videoMode,
            transitionSpec = {
                val pop = spring<Float>(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
                (scaleIn(pop, initialScale = 0.4f) + fadeIn()) togetherWith
                    (scaleOut(targetScale = 0.4f) + fadeOut())
            },
            label = "modeIcon"
        ) { video ->
            Icon(
                imageVector = if (video) Icons.Rounded.PlayArrow else Icons.Rounded.MusicNote,
                contentDescription = switchLabel,
                tint = content,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/** A [Morph] between two unit-square Material shapes, stretched to the composable. */
private class UnitMorph(private val morph: Morph, private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = morph.toPath(progress).asComposePath()
        val matrix = Matrix()
        matrix.scale(size.width, size.height)
        path.transform(matrix)
        return Outline.Generic(path)
    }
}

/**
 * The Home top bar's own options (greeting, downloads button), read where the
 * bar is drawn rather than threaded through every host that draws one.
 */
@Composable
fun rememberHomeTopBarOptions(): HomeNavigationCustomization {
    val context = LocalContext.current
    val preferences = remember(context) { ThemePreferences(context) }
    return preferences.homeNavigation.collectAsState().value
}

/**
 * The downloads button on a Home top bar, growing in and out of the row.
 *
 * It carries its own trailing gap instead of taking one from the row's
 * spacing: a hidden `AnimatedVisibility` is still a child, and a spaced row
 * gives a zero-width child a gap on each side, which showed as a hole between
 * the buttons whenever nothing was downloading. Place it in a row with no
 * spacing of its own, directly before the button that follows it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TopBarDownloadsButton(
    visible: Boolean,
    downloading: Boolean,
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color,
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandHorizontally() +
            scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) +
            fadeIn(),
        exit = shrinkHorizontally() + scaleOut() + fadeOut()
    ) {
        Box(modifier = Modifier.padding(end = 12.dp)) {
            IconButton(
                onClick = onClick,
                shapes = IconButtonDefaults.shapes(),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = containerColor,
                    contentColor = contentColor
                ),
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Download,
                    contentDescription = stringResource(R.string.cd_downloads),
                    modifier = Modifier.size(22.dp)
                )
            }
            // The dot is the status; the button alone only says where downloads are.
            if (downloading) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .align(Alignment.TopEnd)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

/**
 * A short greeting for the Home top bar, by the hour: the bar's one line of
 * text, beside the profile picture.
 *
 * Read once per composition of the bar rather than ticking: a greeting that
 * changed under someone mid-scroll would be noise, and the bar is recomposed
 * often enough (a tab change, a return from another screen) to stay right.
 */
@Composable
fun HomeGreeting(modifier: Modifier = Modifier) {
    val hour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
    Text(
        text = stringResource(
            when (hour) {
                in 5..11 -> R.string.home_greeting_morning
                in 12..16 -> R.string.home_greeting_afternoon
                in 17..21 -> R.string.home_greeting_evening
                else -> R.string.home_greeting_night
            }
        ),
        modifier = modifier,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}
