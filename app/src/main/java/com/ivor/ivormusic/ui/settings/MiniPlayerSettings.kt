package com.ivor.ivormusic.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhotoSizeSelectSmall
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SwipeDown
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.MiniCoverMotion
import com.ivor.ivormusic.data.MiniCoverTap
import com.ivor.ivormusic.data.MiniLongPress
import com.ivor.ivormusic.data.MiniSecondLine
import com.ivor.ivormusic.data.MiniPlayerButton
import com.ivor.ivormusic.data.MiniPlayerColor
import com.ivor.ivormusic.data.MiniPlayerCustomization
import com.ivor.ivormusic.data.MiniPlayerProgress
import com.ivor.ivormusic.data.MiniPlayerShrink
import com.ivor.ivormusic.data.ThemePreferences

/**
 * Everything about the mini player a user can change: its buttons, what the
 * cover does, its gestures and its look.
 *
 * Read and written through the page's own `ThemePreferences` rather than
 * threaded through `SettingsScreen`: the pill and Home each hold an instance
 * of their own, and their flows follow the stored value, so a choice made here
 * reaches the mini player at once without another dozen parameters on the
 * screen's signature.
 */
@Composable
internal fun MiniPlayerSettingsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) { ThemePreferences(context) }
    val customization by prefs.miniPlayerCustomization.collectAsState()
    val progress by prefs.miniPlayerProgress.collectAsState()
    val update: (MiniPlayerCustomization) -> Unit = { prefs.setMiniPlayerCustomization(it) }

    SettingsDetailScaffold(
        title = stringResource(R.string.sp_mini_player),
        onBack = onBack,
        // Pinned, so the pill stays in view while the choices under it change:
        // the real one is on Home, behind this screen.
        header = { MiniPlayerPreview(customization, progress) }
    ) {
        item {
            SettingsSection(title = stringResource(R.string.sp_mini_buttons)) {
                SettingsCard {
                    val full = customization.buttons.size >= MiniPlayerCustomization.MAX_BUTTONS
                    MiniPlayerButton.entries.forEach { button ->
                        val chosen = button in customization.buttons
                        SettingsToggleRow(
                            icon = button.icon(),
                            title = stringResource(button.titleRes()),
                            subtitle = stringResource(button.subtitleRes()),
                            enabled = chosen,
                            onToggle = { on ->
                                update(
                                    customization.copy(
                                        buttons = if (on) customization.buttons + button
                                        else customization.buttons - button
                                    )
                                )
                            },
                            // A fourth has no room; the chosen ones stay
                            // tappable so one can be swapped out.
                            available = chosen || !full
                        )
                    }
                }
            }
        }
        item {
            SettingsNotice(
                icon = Icons.Rounded.Info,
                text = stringResource(R.string.sp_mini_buttons_note)
            )
        }

        item {
            SettingsSection(title = stringResource(R.string.sp_mini_song_and_cover)) {
                SettingsCard {
                    val lines = MiniSecondLine.entries
                    SettingsChoiceRow(
                        icon = Icons.Rounded.TextFields,
                        title = stringResource(R.string.sp_mini_second_line),
                        subtitle = stringResource(R.string.sp_mini_second_line_sub),
                        labels = listOf(
                            stringResource(R.string.sp_mini_second_line_artist),
                            stringResource(R.string.sp_mini_second_line_album),
                            stringResource(R.string.sp_mini_second_line_time)
                        ),
                        selectedIndex = lines.indexOf(customization.secondLine),
                        onSelect = { update(customization.copy(secondLine = lines[it])) }
                    )
                    val taps = MiniCoverTap.entries
                    SettingsChoiceRow(
                        icon = Icons.Rounded.TouchApp,
                        title = stringResource(R.string.sp_mini_cover_tap),
                        subtitle = stringResource(R.string.sp_mini_cover_tap_sub),
                        labels = listOf(
                            stringResource(R.string.sp_mini_cover_tap_play),
                            stringResource(R.string.sp_mini_cover_tap_open)
                        ),
                        selectedIndex = taps.indexOf(customization.coverTap),
                        onSelect = { update(customization.copy(coverTap = taps[it])) }
                    )
                    val motions = MiniCoverMotion.entries
                    SettingsChoiceRow(
                        icon = Icons.Rounded.Autorenew,
                        title = stringResource(R.string.sp_mini_cover_motion),
                        subtitle = stringResource(R.string.sp_mini_cover_motion_sub),
                        labels = listOf(
                            stringResource(R.string.sp_mini_cover_motion_both),
                            stringResource(R.string.sp_mini_cover_motion_spin),
                            stringResource(R.string.sp_mini_cover_motion_morph),
                            stringResource(R.string.sp_mini_cover_motion_still)
                        ),
                        selectedIndex = motions.indexOf(customization.coverMotion),
                        onSelect = { update(customization.copy(coverMotion = motions[it])) }
                    )
                }
            }
        }

        item {
            SettingsSection(title = stringResource(R.string.sp_mini_gestures)) {
                SettingsCard {
                    val presses = MiniLongPress.entries
                    SettingsChoiceRow(
                        icon = Icons.Rounded.TouchApp,
                        title = stringResource(R.string.sp_mini_long_press),
                        subtitle = stringResource(R.string.sp_mini_long_press_sub),
                        labels = listOf(
                            stringResource(R.string.sp_mini_long_press_nothing),
                            stringResource(R.string.sp_mini_long_press_options),
                            stringResource(R.string.sp_mini_button_like)
                        ),
                        selectedIndex = presses.indexOf(customization.longPress),
                        onSelect = { update(customization.copy(longPress = presses[it])) }
                    )
                    SettingsToggleRow(
                        icon = Icons.Rounded.Swipe,
                        title = stringResource(R.string.sp_mini_swipe_skip),
                        subtitle = stringResource(R.string.sp_mini_swipe_skip_sub),
                        enabled = customization.swipeToSkip,
                        onToggle = { update(customization.copy(swipeToSkip = it)) }
                    )
                    SettingsToggleRow(
                        icon = Icons.Rounded.SwipeDown,
                        title = stringResource(R.string.sp_mini_swipe_dismiss),
                        subtitle = stringResource(R.string.sp_mini_swipe_dismiss_sub),
                        enabled = customization.swipeToDismiss,
                        onToggle = { update(customization.copy(swipeToDismiss = it)) }
                    )
                }
            }
        }

        item {
            SettingsSection(title = stringResource(R.string.sp_mini_look)) {
                SettingsCard {
                    val colors = MiniPlayerColor.entries
                    SettingsChoiceRow(
                        icon = Icons.Rounded.Palette,
                        title = stringResource(R.string.sp_mini_color),
                        subtitle = stringResource(R.string.sp_mini_color_sub),
                        labels = listOf(
                            stringResource(R.string.sp_mini_color_accent),
                            stringResource(R.string.sp_mini_color_neutral)
                        ),
                        selectedIndex = colors.indexOf(customization.color),
                        onSelect = { update(customization.copy(color = colors[it])) }
                    )
                    val shrinks = MiniPlayerShrink.entries
                    SettingsChoiceRow(
                        icon = Icons.Rounded.PhotoSizeSelectSmall,
                        title = stringResource(R.string.sp_mini_shrink),
                        subtitle = stringResource(R.string.sp_mini_shrink_sub),
                        labels = listOf(
                            stringResource(R.string.sp_mini_shrink_scroll),
                            stringResource(R.string.sp_mini_shrink_never),
                            stringResource(R.string.sp_mini_shrink_always)
                        ),
                        selectedIndex = shrinks.indexOf(customization.shrink),
                        onSelect = { update(customization.copy(shrink = shrinks[it])) }
                    )
                    val styles = listOf(
                        MiniPlayerProgress.BOTH,
                        MiniPlayerProgress.FILL,
                        MiniPlayerProgress.OUTLINE
                    )
                    SettingsChoiceRow(
                        icon = Icons.Rounded.PlayCircle,
                        title = stringResource(R.string.sp_mini_progress),
                        subtitle = stringResource(R.string.sp_mini_progress_sub),
                        labels = listOf(
                            stringResource(R.string.sp_mini_progress_both),
                            stringResource(R.string.sp_mini_progress_fill),
                            stringResource(R.string.sp_mini_progress_outline)
                        ),
                        selectedIndex = styles.indexOf(progress),
                        onSelect = { prefs.setMiniPlayerProgress(styles[it]) }
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

/**
 * The mini player as the choices above would draw it: its colour, the cover,
 * the song beside it and the chosen buttons in their order.
 *
 * A drawing of the pill rather than the pill itself. The real one needs a
 * song, a queue and a player behind it; this needs only the choices, so it is
 * the same whether or not anything is playing. Buttons spring in and out as
 * they are switched, which is the one piece of motion on the page.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MiniPlayerPreview(customization: MiniPlayerCustomization, progress: MiniPlayerProgress) {
    val neutral = customization.color == MiniPlayerColor.NEUTRAL
    val container by animateColorAsState(
        targetValue = if (neutral) MaterialTheme.colorScheme.surfaceContainerHighest
        else MaterialTheme.colorScheme.primaryContainer,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "miniPreviewContainer"
    )
    val content by animateColorAsState(
        targetValue = if (neutral) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onPrimaryContainer,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "miniPreviewContent"
    )
    val fill = MaterialTheme.colorScheme.primary.copy(alpha = 0.26f)
    val outline = MaterialTheme.colorScheme.primary
    val coverShape = MaterialShapes.Cookie9Sided.toShape()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(68.dp)
            // A picture of a control, not a control: nothing here for a
            // screen reader to land on.
            .clearAndSetSemantics { },
        shape = CircleShape,
        color = container
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    // About a third played, so both progress styles show.
                    if (progress.showsFill) {
                        drawRect(color = fill, size = Size(size.width * 0.36f, size.height))
                    }
                }
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                if (progress.showsOutline) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .border(3.dp, outline, coverShape)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(coverShape)
                        .background(MaterialTheme.colorScheme.tertiaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.sp_mini_preview_title),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(
                        when (customization.secondLine) {
                            MiniSecondLine.ARTIST -> R.string.sp_mini_preview_artist
                            MiniSecondLine.ALBUM -> R.string.sp_mini_preview_album
                            MiniSecondLine.TIME_LEFT -> R.string.sp_mini_preview_time
                        }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = content.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // Keyed by button and drawn in the chosen order, so a button
            // that is still on keeps its place while another springs in or
            // out beside it.
            val shown = customization.orderedButtons
            val drawn = shown + MiniPlayerButton.entries.filter { it !in shown }
            drawn.forEach { button -> androidx.compose.runtime.key(button) {
                AnimatedVisibility(
                    visible = button in shown,
                    enter = fadeIn() + scaleIn(
                        initialScale = 0.6f,
                        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()
                    ) + expandHorizontally(MaterialTheme.motionScheme.fastSpatialSpec()),
                    exit = fadeOut() + scaleOut(targetScale = 0.6f) +
                        shrinkHorizontally(MaterialTheme.motionScheme.fastSpatialSpec())
                ) {
                    val main = button == MiniPlayerButton.PLAY_PAUSE
                    Box(
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (main) MaterialTheme.colorScheme.primary else Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = button.icon(),
                            contentDescription = null,
                            tint = if (main) MaterialTheme.colorScheme.onPrimary else content,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            } }
        }
    }
}

private fun MiniPlayerButton.icon() = when (this) {
    MiniPlayerButton.PREVIOUS -> Icons.Rounded.SkipPrevious
    MiniPlayerButton.PLAY_PAUSE -> Icons.Rounded.PlayArrow
    MiniPlayerButton.NEXT -> Icons.Rounded.SkipNext
    MiniPlayerButton.LIKE -> Icons.Rounded.Favorite
    MiniPlayerButton.SHUFFLE -> Icons.Rounded.Shuffle
    MiniPlayerButton.REPEAT -> Icons.Rounded.Repeat
    MiniPlayerButton.CLOSE -> Icons.Rounded.Close
}

private fun MiniPlayerButton.titleRes() = when (this) {
    MiniPlayerButton.PREVIOUS -> R.string.sp_mini_button_previous
    MiniPlayerButton.PLAY_PAUSE -> R.string.sp_mini_button_play
    MiniPlayerButton.NEXT -> R.string.sp_mini_button_next
    MiniPlayerButton.LIKE -> R.string.sp_mini_button_like
    MiniPlayerButton.SHUFFLE -> R.string.sp_mini_button_shuffle
    MiniPlayerButton.REPEAT -> R.string.sp_mini_button_repeat
    MiniPlayerButton.CLOSE -> R.string.sp_mini_button_close
}

private fun MiniPlayerButton.subtitleRes() = when (this) {
    MiniPlayerButton.PREVIOUS -> R.string.sp_mini_button_previous_sub
    MiniPlayerButton.PLAY_PAUSE -> R.string.sp_mini_button_play_sub
    MiniPlayerButton.NEXT -> R.string.sp_mini_button_next_sub
    MiniPlayerButton.LIKE -> R.string.sp_mini_button_like_sub
    MiniPlayerButton.SHUFFLE -> R.string.sp_mini_button_shuffle_sub
    MiniPlayerButton.REPEAT -> R.string.sp_mini_button_repeat_sub
    MiniPlayerButton.CLOSE -> R.string.sp_mini_button_close_sub
}

/** The mini player in a few words, for the row that opens this page. */
@Composable
internal fun miniPlayerSummary(): String {
    val context = LocalContext.current
    val customization by remember(context) { ThemePreferences(context) }
        .miniPlayerCustomization.collectAsState()
    val count = customization.orderedButtons.size
    val buttons = if (count == 0) {
        stringResource(R.string.sp_mini_summary_no_buttons)
    } else {
        androidx.compose.ui.res.pluralStringResource(R.plurals.sp_mini_summary_buttons, count, count)
    }
    val color = stringResource(
        if (customization.color == MiniPlayerColor.NEUTRAL) R.string.sp_mini_color_neutral
        else R.string.sp_mini_color_accent
    )
    return "$buttons, $color"
}
