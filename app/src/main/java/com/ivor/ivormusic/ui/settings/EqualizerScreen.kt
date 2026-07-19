package com.ivor.ivormusic.ui.settings

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.SpeakerGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.data.AudioEffects

/**
 * Equalizer settings screen: master toggle, system presets, per-band gain
 * sliders, and bass boost. Talks directly to [AudioEffects], which owns the
 * platform effect instances on MusicService's audio session — changes are
 * audible immediately and persisted for future sessions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerScreen(
    onBack: () -> Unit
) {
    val isAvailable by AudioEffects.isAvailable.collectAsState()
    val enabled by AudioEffects.enabled.collectAsState()
    val bandLevels by AudioEffects.bandLevels.collectAsState()
    val currentPreset by AudioEffects.currentPreset.collectAsState()
    val bassBoost by AudioEffects.bassBoostStrength.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Equalizer",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!isAvailable) {
                UnavailableCard()
            } else {
                EnableCard(enabled = enabled, onToggle = { AudioEffects.setEnabled(it) })

                if (AudioEffects.presetNames.isNotEmpty()) {
                    PresetRow(
                        presets = AudioEffects.presetNames,
                        currentPreset = currentPreset,
                        enabled = enabled,
                        onPresetSelected = { AudioEffects.applyPreset(it) }
                    )
                }

                BandsCard(
                    bandLevels = bandLevels,
                    centerFreqsHz = AudioEffects.centerFreqsHz,
                    minLevel = AudioEffects.minBandLevel,
                    maxLevel = AudioEffects.maxBandLevel,
                    enabled = enabled,
                    onBandChange = { band, level -> AudioEffects.setBandLevel(band, level) }
                )

                if (AudioEffects.isBassBoostSupported) {
                    BassBoostCard(
                        strength = bassBoost,
                        enabled = enabled,
                        onStrengthChange = { AudioEffects.setBassBoostStrength(it) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun UnavailableCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Equalizer not ready",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Start the music player once, then come back here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun EnableCard(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(14.dp)
                ) {}
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Enable equalizer",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Shape the sound of music playback",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

@Composable
private fun PresetRow(
    presets: List<String>,
    currentPreset: Int,
    enabled: Boolean,
    onPresetSelected: (Int) -> Unit
) {
    Column {
        Text(
            text = "Presets",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(
                    selected = currentPreset == AudioEffects.PRESET_CUSTOM,
                    onClick = {},
                    enabled = enabled,
                    label = { Text("Custom") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
            items(presets.size) { index ->
                FilterChip(
                    selected = currentPreset == index,
                    onClick = { onPresetSelected(index) },
                    enabled = enabled,
                    label = { Text(presets[index]) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        }
    }
}

@Composable
private fun BandsCard(
    bandLevels: List<Short>,
    centerFreqsHz: List<Int>,
    minLevel: Short,
    maxLevel: Short,
    enabled: Boolean,
    onBandChange: (Int, Short) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Bands",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            bandLevels.forEachIndexed { band, level ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formatFrequency(centerFreqsHz.getOrElse(band) { 0 }),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(56.dp)
                    )
                    Slider(
                        value = level.toFloat(),
                        onValueChange = { onBandChange(band, it.toInt().toShort()) },
                        valueRange = minLevel.toFloat()..maxLevel.toFloat(),
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    Text(
                        text = formatDecibels(level),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(48.dp),
                        textAlign = TextAlign.End
                    )
                }
            }
        }
    }
}

@Composable
private fun BassBoostCard(
    strength: Int,
    enabled: Boolean,
    onStrengthChange: (Int) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.SpeakerGroup,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Bass boost",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${strength * 100 / AudioEffects.BASS_BOOST_MAX}%",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Slider(
                value = strength.toFloat(),
                onValueChange = { onStrengthChange(it.toInt()) },
                valueRange = 0f..AudioEffects.BASS_BOOST_MAX.toFloat(),
                enabled = enabled,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

private fun formatFrequency(hz: Int): String = when {
    hz >= 1000 -> {
        val kHz = hz / 1000f
        if (kHz % 1f == 0f) "${kHz.toInt()} kHz" else "%.1f kHz".format(kHz)
    }
    else -> "$hz Hz"
}

private fun formatDecibels(millibels: Short): String {
    val db = millibels / 100f
    val sign = if (db > 0) "+" else ""
    return if (db % 1f == 0f) "$sign${db.toInt()} dB" else "$sign%.1f dB".format(db)
}
