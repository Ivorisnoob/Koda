package com.ivor.ivormusic.ui.settings

import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FormatAlignCenter
import androidx.compose.material.icons.rounded.FormatColorText
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.LrcContentSpan
import com.ivor.ivormusic.data.LrcLine
import com.ivor.ivormusic.data.LyricsCustomization
import com.ivor.ivormusic.data.LyricsDim
import com.ivor.ivormusic.data.LyricsFont
import com.ivor.ivormusic.data.LyricsHighlight
import com.ivor.ivormusic.data.LyricsLineSpacing
import com.ivor.ivormusic.data.LyricsRestPosition
import com.ivor.ivormusic.data.LyricsResult
import com.ivor.ivormusic.data.LyricsScroll
import com.ivor.ivormusic.data.LyricsSyncType
import com.ivor.ivormusic.data.LyricsTextSize
import com.ivor.ivormusic.data.LyricsWeight
import com.ivor.ivormusic.data.ThemePreferences
import com.ivor.ivormusic.ui.player.SyncedLyricsView

@Composable
internal fun LyricsAppearancePage(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) { ThemePreferences(context) }
    val look by prefs.lyricsLook.collectAsState()
    val centered by prefs.lyricsCentered.collectAsState()
    val romanized by prefs.lyricsRomanized.collectAsState()
    val update = prefs::setLyricsLook

    SettingsDetailScaffold(title = stringResource(R.string.lyrics_look_title), onBack = onBack) {
        item { LyricsAppearancePreview() }
        item {
            SettingsSection(title = stringResource(R.string.lyrics_look_type)) {
                SettingsCard {
                    LyricChoice(Icons.Rounded.TextFields, R.string.lyrics_look_font, lyricsFonts, look.font) {
                        update(look.copy(font = it))
                    }
                    LyricChoice(Icons.Rounded.FormatColorText, R.string.lyrics_look_weight, lyricsWeights, look.weight) {
                        update(look.copy(weight = it))
                    }
                    LyricChoice(Icons.Rounded.FormatSize, R.string.lyrics_look_size, lyricsSizes, look.size) {
                        update(look.copy(size = it))
                    }
                    LyricChoice(Icons.Rounded.FormatAlignCenter, R.string.lyrics_look_spacing, lyricsSpacing, look.spacing) {
                        update(look.copy(spacing = it))
                    }
                    SettingsChoiceRow(
                        icon = Icons.Rounded.FormatAlignCenter,
                        title = stringResource(R.string.lyrics_alignment),
                        subtitle = stringResource(R.string.lyrics_alignment_sub),
                        labels = listOf(
                            stringResource(R.string.lyrics_alignment_left),
                            stringResource(R.string.lyrics_alignment_centre)
                        ),
                        selectedIndex = if (centered) 1 else 0,
                        onSelect = { prefs.setLyricsCentered(it == 1) }
                    )
                    AppearanceToggle(Icons.Rounded.TextFields, R.string.lyrics_look_caps, look.uppercase) {
                        update(look.copy(uppercase = it))
                    }
                    if (Build.VERSION.SDK_INT >= 29) {
                        SettingsToggleRow(
                            icon = Icons.Rounded.Translate,
                            title = stringResource(R.string.lyrics_romanized),
                            subtitle = stringResource(R.string.lyrics_romanized_sub),
                            enabled = romanized,
                            onToggle = prefs::setLyricsRomanized
                        )
                    }
                }
            }
        }
        item {
            SettingsSection(title = stringResource(R.string.lyrics_look_focus)) {
                SettingsCard {
                    LyricChoice(Icons.Rounded.Palette, R.string.lyrics_look_highlight, lyricsHighlights, look.highlight) {
                        update(look.copy(highlight = it))
                    }
                    LyricChoice(Icons.Rounded.Palette, R.string.lyrics_look_dim, lyricsDimming, look.dim) {
                        update(look.copy(dim = it))
                    }
                    if (Build.VERSION.SDK_INT >= 31) {
                        AppearanceToggle(Icons.Rounded.Palette, R.string.lyrics_look_blur, look.blur) {
                            update(look.copy(blur = it))
                        }
                    }
                    AppearanceToggle(Icons.Rounded.FormatSize, R.string.lyrics_look_depth, look.depthScale) {
                        update(look.copy(depthScale = it))
                    }
                    AppearanceToggle(Icons.Rounded.Palette, R.string.lyrics_look_glow, look.glow) {
                        update(look.copy(glow = it))
                    }
                }
            }
        }
        item {
            SettingsSection(title = stringResource(R.string.lyrics_look_timing)) {
                SettingsCard {
                    LyricChoice(Icons.Rounded.Gesture, R.string.lyrics_look_scroll, lyricsScrollModes, look.scroll) {
                        update(look.copy(scroll = it))
                    }
                    LyricChoice(Icons.Rounded.FormatAlignCenter, R.string.lyrics_look_anchor, lyricsAnchors, look.rest) {
                        update(look.copy(rest = it))
                    }
                    AppearanceToggle(Icons.Rounded.Gesture, R.string.lyrics_look_motion, look.wordMotion) {
                        update(look.copy(wordMotion = it))
                    }
                    AppearanceToggle(Icons.Rounded.MusicNote, R.string.lyrics_look_dots, look.interludeDots) {
                        update(look.copy(interludeDots = it))
                    }
                    AppearanceToggle(Icons.Rounded.MusicNote, R.string.lyrics_look_duet, look.duetSides) {
                        update(look.copy(duetSides = it))
                    }
                }
            }
        }
        item {
            SettingsCard {
                SettingsRow(
                    icon = Icons.Rounded.TextFields,
                    title = stringResource(R.string.lyrics_look_classic),
                    subtitle = "Montserrat",
                    onClick = { update(LyricsCustomization.Classic) }
                )
                SettingsRow(
                    icon = Icons.Rounded.RestartAlt,
                    title = stringResource(R.string.lyrics_look_reset),
                    subtitle = "Inter",
                    onClick = { update(LyricsCustomization.Default) }
                )
            }
        }
    }
}

@Composable
private fun LyricsAppearancePreview() {
    val first = stringResource(R.string.lyrics_look_preview_one)
    val second = stringResource(R.string.lyrics_look_preview_two)
    val third = stringResource(R.string.lyrics_look_preview_three)
    val lyrics = remember(first, second, third) {
        LyricsResult.Success(
            lines = listOf(
                sampleLine(0L, first),
                sampleLine(3_000L, second),
                sampleLine(12_000L, third)
            ),
            provider = "",
            syncType = LyricsSyncType.WORD
        )
    }
    var positionMs by remember { mutableLongStateOf(3_950L) }
    var previewPlaying by remember { mutableStateOf(false) }
    LaunchedEffect(previewPlaying) {
        if (!previewPlaying) return@LaunchedEffect
        val startPosition = positionMs
        val startedAt = withFrameNanos { it }
        while (positionMs < 15_000L) {
            positionMs = withFrameNanos { startPosition + (it - startedAt) / 1_000_000L }
        }
        previewPlaying = false
    }
    val colors = MaterialTheme.colorScheme
    Surface(
        color = colors.surfaceContainerHigh,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth().height(216.dp)
    ) {
        Box {
            SyncedLyricsView(
                lyricsResult = lyrics,
                currentPositionMs = positionMs,
                isPlaying = false,
                onSeekTo = {},
                onSurfaceColor = colors.onSurface,
                interactive = false,
                backgroundColor = colors.surfaceContainerHigh
            )
            FilledTonalIconButton(
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).size(48.dp),
                onClick = {
                    if (positionMs >= 15_000L) positionMs = 0L
                    previewPlaying = !previewPlaying
                }
            ) {
                Icon(
                    imageVector = if (previewPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(
                        if (previewPlaying) R.string.lyrics_look_pause_preview else R.string.lyrics_look_play_preview
                    )
                )
            }
        }
    }
}

private fun sampleLine(startMs: Long, text: String): LrcLine {
    val words = text.split(' ').filter(String::isNotBlank)
    val spans = words.mapIndexed { index, word -> LrcContentSpan(startMs + index * 420L, word, 420L) }
    return LrcLine(startMs, text, spans)
}

@Composable
internal fun lyricsAppearanceSummary(): String {
    val context = LocalContext.current
    val look by remember(context) { ThemePreferences(context) }.lyricsLook.collectAsState()
    return lyricsFonts.getValue(look.font).label()
}

@Composable
private fun AppearanceToggle(icon: ImageVector, @StringRes title: Int, enabled: Boolean, onToggle: (Boolean) -> Unit) {
    SettingsToggleRow(icon = icon, title = stringResource(title), subtitle = "", enabled = enabled, onToggle = onToggle)
}

@Composable
private fun <T> LyricChoice(
    icon: ImageVector,
    @StringRes title: Int,
    choices: Map<T, LyricLabel>,
    selectedChoice: T,
    onSelect: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        SettingsRow(
            icon = icon,
            title = stringResource(title),
            subtitle = choices.getValue(selectedChoice).label(),
            showChevron = true,
            onClick = { expanded = true }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { (choice, label) ->
                DropdownMenuItem(
                    text = { Text(label.label()) },
                    trailingIcon = if (choice == selectedChoice) {
                        { Icon(Icons.Rounded.Check, contentDescription = null) }
                    } else null,
                    modifier = Modifier.semantics { selected = choice == selectedChoice },
                    onClick = {
                        expanded = false
                        onSelect(choice)
                    }
                )
            }
        }
    }
}

private class LyricLabel(@StringRes val resource: Int = 0, val name: String = "") {
    @Composable
    fun label() = if (resource == 0) name else stringResource(resource)
}

private val lyricsFonts = mapOf(
    LyricsFont.INTER to LyricLabel(name = "Inter"),
    LyricsFont.MONTSERRAT to LyricLabel(name = "Montserrat"),
    LyricsFont.SYSTEM to LyricLabel(R.string.lyrics_look_system),
    LyricsFont.ROUNDED to LyricLabel(name = "Nunito"),
    LyricsFont.SERIF to LyricLabel(name = "Lora")
)
private val lyricsWeights = mapOf(
    LyricsWeight.MEDIUM to LyricLabel(R.string.lyrics_look_medium),
    LyricsWeight.SEMIBOLD to LyricLabel(R.string.lyrics_look_semibold),
    LyricsWeight.BOLD to LyricLabel(R.string.lyrics_look_bold),
    LyricsWeight.HEAVY to LyricLabel(R.string.lyrics_look_heavy)
)
private val lyricsSizes = mapOf(
    LyricsTextSize.SMALL to LyricLabel(R.string.lyrics_look_small),
    LyricsTextSize.MEDIUM to LyricLabel(R.string.lyrics_look_medium),
    LyricsTextSize.LARGE to LyricLabel(R.string.lyrics_look_large),
    LyricsTextSize.HUGE to LyricLabel(R.string.lyrics_look_huge)
)
private val lyricsSpacing = mapOf(
    LyricsLineSpacing.TIGHT to LyricLabel(R.string.lyrics_look_tight),
    LyricsLineSpacing.NORMAL to LyricLabel(R.string.lyrics_look_normal),
    LyricsLineSpacing.AIRY to LyricLabel(R.string.lyrics_look_airy)
)
private val lyricsHighlights = mapOf(
    LyricsHighlight.PLAIN to LyricLabel(R.string.lyrics_look_plain),
    LyricsHighlight.ACCENT to LyricLabel(R.string.lyrics_look_accent)
)
private val lyricsDimming = mapOf(
    LyricsDim.SOFT to LyricLabel(R.string.lyrics_look_soft),
    LyricsDim.MEDIUM to LyricLabel(R.string.lyrics_look_medium),
    LyricsDim.STRONG to LyricLabel(R.string.lyrics_look_strong)
)
private val lyricsScrollModes = mapOf(
    LyricsScroll.CASCADE to LyricLabel(R.string.lyrics_look_cascade),
    LyricsScroll.SMOOTH to LyricLabel(R.string.lyrics_look_smooth),
    LyricsScroll.INSTANT to LyricLabel(R.string.lyrics_look_instant)
)
private val lyricsAnchors = mapOf(
    LyricsRestPosition.HIGH to LyricLabel(R.string.lyrics_look_high),
    LyricsRestPosition.THIRD to LyricLabel(R.string.lyrics_look_third),
    LyricsRestPosition.CENTRE to LyricLabel(R.string.lyrics_look_center)
)
