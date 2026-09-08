package com.ivor.ivormusic.ui.settings

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.AppIconStyleStore
import com.ivor.ivormusic.data.SavedIconStyle
import com.ivor.ivormusic.data.ThemePreferences
import com.ivor.ivormusic.util.rememberKodaHaptics

/**
 * The App icon page: pick a launcher icon, or mix and keep a look of your own.
 *
 * **The preview does not scroll.** That is the whole shape of this screen. The
 * controls run to more than a screenful - nine presets, six shapes, four colour
 * rows - and every one of them is judged by looking at the icon, so a preview
 * that sat at the top of the list was gone by the time anyone reached the
 * colours and the page became "pick a swatch, scroll up, look, scroll down".
 * `SettingsDetailScaffold` grew a pinned header slot for it.
 *
 * **Selecting is not applying.** A preset tap loads the preview and nothing
 * else; the one action in the header applies it. The previous version applied
 * on tap, so browsing nine presets changed the home-screen icon nine times, and
 * the Apply button it also had could never be reached in a state where it did
 * anything. One primary action, in one place, whose label says which of the
 * three things it currently is: Apply, Save this style, or nothing at all
 * because this is already the active one.
 *
 * **A custom style cannot be a launcher icon, and the page says so.** The home
 * screen draws a static resource behind an `<activity-alias>` - there is no
 * runtime path from arbitrary colours to it. So a saved style becomes Koda's
 * mark inside the app and puts the closest real preset on the launcher, and the
 * header names that preset rather than letting someone discover the
 * substitution on their home screen.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AppIconSettingsPage(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val haptics = rememberKodaHaptics()
    val themePrefs = remember { ThemePreferences(context) }
    val styleStore = remember { AppIconStyleStore(context) }

    val launcherIconId by themePrefs.appIcon.collectAsState()
    val savedStyles by styleStore.styles.collectAsState()
    val activeStyleId by styleStore.activeStyleId.collectAsState()

    val launcherPreset = remember(launcherIconId) { AppIcon.fromId(launcherIconId) }

    // What the preview is showing right now. Saved across configuration
    // changes, because a mix somebody spent a minute on should not be lost to
    // a rotation - that is the one piece of state here that cannot be rebuilt.
    var draft by rememberSaveable(stateSaver = IconStyleSaver) {
        mutableStateOf(
            styleStore.activeStyle()?.toIconStyle() ?: launcherPreset.style()
        )
    }
    var selectedPresetId by rememberSaveable {
        mutableStateOf(if (styleStore.activeStyle() == null) launcherPreset.id else null)
    }
    var selectedSavedId by rememberSaveable { mutableStateOf(styleStore.activeStyle()?.id) }

    var namingStyle by rememberSaveable { mutableStateOf(false) }
    var deleteTarget by rememberSaveable { mutableStateOf<String?>(null) }

    val selectedPreset = remember(selectedPresetId) {
        AppIcon.entries.firstOrNull { it.id == selectedPresetId }
    }
    val selectedSaved = remember(selectedSavedId, savedStyles) {
        savedStyles.firstOrNull { it.id == selectedSavedId }
    }

    fun applyLauncher(preset: AppIcon): Boolean {
        val applied = AppIconManager.applyIcon(context, preset)
        // The preference is only written when the system actually took the
        // change. Writing it either way is what let a silent PackageManager
        // failure show up in Settings as an icon that had been applied.
        if (applied) themePrefs.setAppIcon(preset.id)
        Toast.makeText(
            context,
            context.getString(
                if (applied) {
                    // Several launchers redraw their icon cache lazily, so
                    // without this the home screen looks unchanged and a
                    // successful tap looks like it failed.
                    R.string.app_icon_applied_notice
                } else {
                    R.string.app_icon_apply_failed
                }
            ),
            Toast.LENGTH_SHORT
        ).show()
        return applied
    }

    fun choosePreset(preset: AppIcon) {
        selectedPresetId = preset.id
        selectedSavedId = null
        // The shape travels: it is how the preview is drawn, not part of the
        // preset, so browsing presets should not keep resetting it.
        draft = preset.style(draft.shape)
    }

    fun chooseSaved(saved: SavedIconStyle) {
        selectedSavedId = saved.id
        selectedPresetId = null
        draft = saved.toIconStyle()
    }

    /** Any colour edit turns the preview into a custom mix owned by nobody. */
    fun edit(update: (IconStyle) -> IconStyle) {
        draft = update(draft)
        selectedPresetId = null
        selectedSavedId = null
    }

    val nearest = remember(draft) { draft.nearestPreset() }
    val previewShape = draft.shape.containerShape()

    SettingsDetailScaffold(
        title = stringResource(R.string.sp_app_icon),
        onBack = onBack,
        header = {
            AppIconPreviewHeader(
                style = draft,
                shape = previewShape,
                title = when {
                    selectedPreset != null -> stringResource(selectedPreset.titleRes)
                    selectedSaved != null -> selectedSaved.name
                    else -> stringResource(R.string.app_icon_custom_title)
                },
                subtitle = when {
                    selectedPreset != null -> stringResource(selectedPreset.descRes)
                    else -> stringResource(
                        R.string.app_icon_launcher_note,
                        stringResource(nearest.titleRes)
                    )
                },
                action = when {
                    selectedPreset != null && selectedPreset.id == launcherIconId &&
                        activeStyleId == null -> HeaderAction.Active

                    selectedPreset != null -> HeaderAction.Apply

                    selectedSaved != null && selectedSaved.id == activeStyleId ->
                        HeaderAction.Active

                    selectedSaved != null -> HeaderAction.Apply
                    else -> HeaderAction.Save
                },
                onAction = {
                    when {
                        selectedPreset != null -> {
                            haptics.confirm()
                            applyLauncher(selectedPreset)
                            styleStore.setActive(null)
                        }

                        selectedSaved != null -> {
                            haptics.confirm()
                            styleStore.setActive(selectedSaved.id)
                            applyLauncher(selectedSaved.toIconStyle().nearestPreset())
                        }

                        else -> {
                            haptics.tick()
                            namingStyle = true
                        }
                    }
                }
            )
        }
    ) {
        // ------------------------------------------------------------- shape
        item {
            SettingsSection(title = stringResource(R.string.app_icon_shape_preview)) {
                IconShapeSelector(
                    selected = draft.shape,
                    onSelect = { shape ->
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        draft = draft.copy(shape = shape)
                    }
                )
            }
        }

        // ----------------------------------------------------------- presets
        item {
            SettingsSection(title = stringResource(R.string.app_icon_section_presets)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.app_icon_section_presets_sub),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                    AppIcon.entries.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            pair.forEach { preset ->
                                IconTile(
                                    style = preset.style(draft.shape),
                                    label = stringResource(preset.titleRes),
                                    selected = selectedPresetId == preset.id,
                                    onLauncher = preset.id == launcherIconId &&
                                        activeStyleId == null,
                                    onClick = {
                                        haptics.tick()
                                        choosePreset(preset)
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // ------------------------------------------------------------ studio
        item {
            SettingsSection(title = stringResource(R.string.app_icon_section_studio)) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 2.dp
                ) {
                    Column(modifier = Modifier.padding(vertical = 16.dp)) {
                        Text(
                            text = stringResource(R.string.app_icon_section_studio_sub),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        SwatchRow(
                            title = stringResource(R.string.app_icon_color_blob),
                            colors = AURA_COLORS,
                            selected = draft.blob,
                            onSelect = { color ->
                                haptics.tick()
                                edit { it.copy(blob = color) }
                            }
                        )
                        SwatchRow(
                            title = stringResource(R.string.app_icon_color_note),
                            colors = NOTE_COLORS,
                            selected = draft.note,
                            onSelect = { color ->
                                haptics.tick()
                                // The flag is the note seen edge-on, so it
                                // follows it rather than being a fifth control
                                // nobody would think to touch.
                                edit { it.copy(note = color, flag = color.copy(alpha = 0.9f)) }
                            }
                        )
                        SwatchRow(
                            title = stringResource(R.string.app_icon_color_dot),
                            colors = SPARK_COLORS,
                            selected = draft.dot,
                            onSelect = { color ->
                                haptics.tick()
                                edit { it.copy(dot = color) }
                            }
                        )
                        SwatchRow(
                            title = stringResource(R.string.app_icon_color_bg),
                            colors = GROUND_COLORS,
                            selected = draft.background,
                            onSelect = { color ->
                                haptics.tick()
                                edit { it.copy(background = color) }
                            }
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TextButton(
                                onClick = {
                                    haptics.subtle()
                                    choosePreset(AppIcon.DEFAULT)
                                }
                            ) {
                                Text(stringResource(R.string.app_icon_reset))
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            Button(
                                onClick = {
                                    haptics.tick()
                                    namingStyle = true
                                },
                                shape = RoundedCornerShape(100)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Save,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.app_icon_save_style),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // ------------------------------------------------------ saved styles
        if (savedStyles.isNotEmpty()) {
            item {
                SettingsSection(title = stringResource(R.string.app_icon_section_saved)) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = stringResource(R.string.app_icon_section_saved_sub),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                        savedStyles.chunked(2).forEach { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                pair.forEach { saved ->
                                    IconTile(
                                        style = saved.toIconStyle(),
                                        label = saved.name,
                                        selected = selectedSavedId == saved.id,
                                        onLauncher = saved.id == activeStyleId,
                                        onClick = {
                                            haptics.tick()
                                            chooseSaved(saved)
                                        },
                                        onDelete = { deleteTarget = saved.id },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }

    if (namingStyle) {
        SaveStyleDialog(
            onDismiss = { namingStyle = false },
            onSave = { name ->
                val stored = styleStore.save(draft.toSaved(name))
                styleStore.setActive(stored.id)
                applyLauncher(nearest)
                selectedSavedId = stored.id
                selectedPresetId = null
                namingStyle = false
                haptics.confirm()
            }
        )
    }

    deleteTarget?.let { id ->
        val style = savedStyles.firstOrNull { it.id == id }
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.app_icon_delete_style)) },
            text = {
                Text(
                    stringResource(
                        R.string.app_icon_delete_style_body,
                        style?.name.orEmpty()
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        styleStore.delete(id)
                        if (selectedSavedId == id) {
                            selectedSavedId = null
                            selectedPresetId = launcherPreset.id
                            draft = launcherPreset.style(draft.shape)
                        }
                        deleteTarget = null
                        haptics.confirm()
                    }
                ) {
                    Text(
                        text = stringResource(R.string.action_delete),
                        color = SettingsRowDefaults.destructiveTint
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(32.dp)
        )
    }
}

/** What the header's single action currently means. */
private enum class HeaderAction { Apply, Save, Active }

/**
 * The pinned preview.
 *
 * Laid out as a row rather than a column so it costs about 120dp of the screen
 * instead of 300: it is on screen for the whole visit, and every dp it takes is
 * a dp of presets and swatches that is not.
 *
 * No drop shadow on the icon, which the stacked version had. Compose renders an
 * elevation shadow from the shape's outline, and a concave outline - the
 * Cookie, the Clover, the Burst, three of the six shapes offered here - cannot
 * produce one, so it degraded to a hard rectangular smear behind the die-cut
 * shapes and nothing at all on others. The radial glow does the same job for
 * every shape, and takes its colour from the icon being previewed.
 */
@Composable
private fun AppIconPreviewHeader(
    style: IconStyle,
    shape: Shape,
    title: String,
    subtitle: String,
    action: HeaderAction,
    onAction: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(112.dp),
                contentAlignment = Alignment.Center
            ) {
                val glow by animateColorAsState(
                    targetValue = style.blob.copy(alpha = 0.4f),
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "iconGlow"
                )
                Box(
                    modifier = Modifier
                        .size(112.dp)
                        .clip(CircleShape)
                        .background(Brush.radialGradient(listOf(glow, Color.Transparent)))
                )
                KodaAppIcon(
                    style = style,
                    shape = shape,
                    modifier = Modifier.size(88.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Crossfaded rather than swapped, because the three states share
                // a slot and a hard cut here reads as the layout jumping.
                AnimatedContent(
                    targetState = action,
                    transitionSpec = {
                        fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) togetherWith
                            fadeOut(spring(stiffness = Spring.StiffnessMediumLow))
                    },
                    label = "iconHeaderAction"
                ) { state ->
                    when (state) {
                        HeaderAction.Active -> Surface(
                            shape = RoundedCornerShape(100),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(
                                    horizontal = 14.dp,
                                    vertical = 8.dp
                                ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.app_icon_current),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        else -> Button(
                            onClick = onAction,
                            shape = RoundedCornerShape(100)
                        ) {
                            Icon(
                                imageVector = if (state == HeaderAction.Save) {
                                    Icons.Rounded.Save
                                } else {
                                    Icons.Rounded.Check
                                },
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(
                                    if (state == HeaderAction.Save) {
                                        R.string.app_icon_save_style
                                    } else {
                                        R.string.app_icon_apply_button
                                    }
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The shape row, as one connected group rather than loose chips.
 *
 * Six mutually exclusive ways of drawing the same thing is a segmented control,
 * which M3 Expressive draws as connected [ToggleButton]s with a shape morph on
 * selection - the same component Spotlight's filters, the SponsorBlock category
 * rows and the video quality tabs already use. It scrolls rather than dividing
 * the width six ways, because "Squircle" and "Circle" are not the same length.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun IconShapeSelector(
    selected: IconShape,
    onSelect: (IconShape) -> Unit
) {
    val entries = IconShape.entries
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
    ) {
        entries.forEachIndexed { index, shape ->
            ToggleButton(
                checked = shape == selected,
                onCheckedChange = { if (shape != selected) onSelect(shape) },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                colors = ToggleButtonDefaults.toggleButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    checkedContainerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    checkedContentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(stringResource(shape.labelRes))
            }
        }
    }
}

/**
 * One selectable icon - a preset or a saved style, which are the same card
 * because they are the same choice.
 *
 * The launcher badge sits on the *card's* corner rather than on the icon's, so
 * it stops covering the artwork it is describing. Presses spring rather than
 * ripple, matching the settings rows this page sits among.
 */
@Composable
private fun IconTile(
    style: IconStyle,
    label: String,
    selected: Boolean,
    onLauncher: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onDelete: (() -> Unit)? = null
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "tilePress"
    )

    Box(modifier = modifier) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .scale(press)
                .clip(RoundedCornerShape(20.dp))
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onClick
                ),
            shape = RoundedCornerShape(20.dp),
            color = if (selected) {
                MaterialTheme.colorScheme.surfaceContainerHighest
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
            border = if (selected) {
                BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            } else {
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                KodaAppIcon(
                    style = style,
                    shape = style.shape.containerShape(),
                    animateColors = false,
                    modifier = Modifier.size(62.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf(style.blob, style.note, style.dot).forEach { swatch ->
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(swatch)
                        )
                    }
                }
            }
        }

        if (onLauncher) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = stringResource(R.string.app_icon_current),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        if (onDelete != null) {
            IconButton(
                onClick = onDelete,
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(2.dp)
                    .size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.app_icon_delete_style),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * One row of colour swatches.
 *
 * The scroll padding is inside the scroller rather than on the card, so the row
 * runs to both edges instead of being clipped at a fixed inset - the previous
 * version put the whole studio inside a 24dp-padded card and the last swatch in
 * every row was cut in half by it. Targets are 44dp, which clears the 48dp
 * minimum with the 8dp gap between them.
 */
@Composable
private fun SwatchRow(
    title: String,
    colors: List<Color>,
    selected: Color,
    onSelect: (Color) -> Unit
) {
    Column(modifier = Modifier.padding(top = 18.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            colors.forEach { color ->
                val isSelected = color.toArgb() == selected.toArgb()
                val scale by animateFloatAsState(
                    targetValue = if (isSelected) 1.1f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "swatchScale"
                )
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .scale(scale)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                            shape = CircleShape
                        )
                        .clickable { onSelect(color) },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            // Against the swatch itself, which is an arbitrary
                            // colour rather than a theme role - the one thing
                            // no ColorScheme entry can be right about here.
                            tint = if (color.perceivedLuminance() > 0.55f) {
                                Color.Black
                            } else {
                                Color.White
                            },
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SaveStyleDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    val trimmed = name.trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.Palette,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text(stringResource(R.string.app_icon_save_style_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(40) },
                label = { Text(stringResource(R.string.app_icon_save_style_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = { if (trimmed.isNotEmpty()) onSave(trimmed) }
                ),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                // An unnamed style is findable only by its colours, and this
                // list is a grid of small icons - so the name is required
                // rather than defaulted to something nobody chose.
                enabled = trimmed.isNotEmpty(),
                onClick = { onSave(trimmed) }
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(32.dp)
    )
}

/** The container a style is drawn in. Three of the six are concave. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun IconShape.containerShape(): Shape = when (this) {
    IconShape.SQUIRCLE -> RoundedCornerShape(26.dp)
    IconShape.CIRCLE -> CircleShape
    IconShape.SQUARE -> RoundedCornerShape(14.dp)
    IconShape.BURST -> MaterialShapes.SoftBurst.toShape()
    IconShape.COOKIE -> MaterialShapes.Cookie9Sided.toShape()
    IconShape.CLOVER -> MaterialShapes.Clover4Leaf.toShape()
}

/**
 * Rec. 601 luma, which is the right measure for "is a check mark going to read
 * on this" - it weights green the way the eye does, unlike a flat average.
 */
private fun Color.perceivedLuminance(): Float = 0.299f * red + 0.587f * green + 0.114f * blue

private val IconStyleSaver = listSaver<IconStyle, Any>(
    save = {
        listOf(
            it.blob.toArgb(), it.note.toArgb(), it.flag.toArgb(), it.dot.toArgb(),
            it.dark1.toArgb(), it.dark2.toArgb(), it.dark3.toArgb(),
            it.background.toArgb(), it.shape.id
        )
    },
    restore = {
        IconStyle(
            blob = Color(it[0] as Int),
            note = Color(it[1] as Int),
            flag = Color(it[2] as Int),
            dot = Color(it[3] as Int),
            dark1 = Color(it[4] as Int),
            dark2 = Color(it[5] as Int),
            dark3 = Color(it[6] as Int),
            background = Color(it[7] as Int),
            shape = IconShape.fromId(it[8] as String)
        )
    }
)

// The swatch sets. Every preset's own colour is in the matching row, so a mix
// can always be walked back to something that exists as a launcher icon.
private val AURA_COLORS = listOf(
    Color(0xFF372DA9), Color(0xFFC62828), Color(0xFF1E1F2B), Color(0xFF1B5E20),
    Color(0xFFE65100), Color(0xFF0D47A1), Color(0xFF7E57C2), Color(0xFFF57F17),
    Color(0xFF212121), Color(0xFF00897B), Color(0xFFC2185B), Color(0xFF4A148C)
)

private val NOTE_COLORS = listOf(
    Color(0xFFDAD8CD), Color(0xFFFFFFFF), Color(0xFF00E5FF), Color(0xFFFFEBEE),
    Color(0xFFE8F5E9), Color(0xFFFFFDE7), Color(0xFFF3E5F5), Color(0xFFB0BEC5)
)

private val SPARK_COLORS = listOf(
    Color(0xFF98EB41), Color(0xFFFFD700), Color(0xFFFF007F), Color(0xFFFFEB3B),
    Color(0xFF7C4DFF), Color(0xFFFF5252), Color(0xFF00E5FF), Color(0xFFB0BEC5)
)

private val GROUND_COLORS = listOf(
    Color(0xFF16133D), Color(0xFF000000), Color(0xFF0B0C10), Color(0xFF1F0606),
    Color(0xFF051A08), Color(0xFF041630), Color(0xFF190E2E), Color(0xFF241200),
    Color(0xFF0D0D0D)
)
