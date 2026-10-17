package com.ivor.ivormusic.ui.settings

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.AiDjRepository
import com.ivor.ivormusic.data.GeminiTtsRepository

class AiDjSettingsViewModel(application: Application) : AndroidViewModel(application) {
    val repository = AiDjRepository(application)
    val ttsRepository = GeminiTtsRepository(repository)
    val hasApiKey = repository.hasApiKey
    val prompt = repository.prompt
    val trackCount = repository.trackCount
    val model = repository.model
    val safeTokenMode = repository.safeTokenMode
    val djVoiceEnabled = repository.djVoiceEnabled
    val autoRefresh = repository.autoRefresh
    val tokenUsage = repository.tokenUsage
    var ttsVoice by mutableStateOf(ttsRepository.voice)
        private set

    fun updateTtsVoice(voice: String) {
        ttsRepository.voice = voice
        ttsVoice = voice
    }
}

@Composable
internal fun AiDjHubRow(
    onClick: () -> Unit,
    selected: Boolean = false,
    model: AiDjSettingsViewModel = viewModel(),
) {
    val hasKey by model.hasApiKey.collectAsState()
    SettingsHubRow(
        icon = Icons.Rounded.AutoAwesome,
        title = stringResource(R.string.aidj_title),
        value = if (hasKey) stringResource(R.string.aidj_configured)
        else stringResource(R.string.aidj_not_configured),
        onClick = onClick,
        tint = MaterialTheme.colorScheme.tertiary,
        explanation = stringResource(R.string.aidj_intro),
        selected = selected,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AiDjSettingsPage(
    onBack: () -> Unit,
    model: AiDjSettingsViewModel = viewModel(),
) {
    val repository = model.repository
    val context = LocalContext.current
    val hasKey by model.hasApiKey.collectAsState()
    val prompt by model.prompt.collectAsState()
    val trackCount by model.trackCount.collectAsState()
    val aiModel by model.model.collectAsState()
    val safeTokenMode by model.safeTokenMode.collectAsState()
    val djVoiceEnabled by model.djVoiceEnabled.collectAsState()
    val autoRefresh by model.autoRefresh.collectAsState()
    val tokenUsage by model.tokenUsage.collectAsState()

    // Credentials never enter saved-instance-state or navigation arguments.
    var keyInput by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    var promptInput by remember { mutableStateOf(prompt) }
    var promptDirty by remember { mutableStateOf(false) }
    var showModelSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.aidj_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                stringResource(R.string.aidj_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Provider.
            AiDjSectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Science,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(Modifier.size(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.aidj_provider),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            stringResource(R.string.aidj_provider_sub),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        FilterChip(
                            selected = true,
                            onClick = {},
                            label = { Text(stringResource(R.string.aidj_provider_gemini)) },
                        )
                    }
                }
            }

            // Safe token mode + DJ voice.
            AiDjSectionCard {
                AiDjToggleRow(
                    title = stringResource(R.string.aidj_safe_token_title),
                    subtitle = stringResource(R.string.aidj_safe_token_sub),
                    checked = safeTokenMode,
                    onCheckedChange = { repository.setSafeTokenMode(it) },
                )
                Spacer(Modifier.height(12.dp))
                AiDjToggleRow(
                    title = stringResource(R.string.aidj_dj_voice_title),
                    subtitle = stringResource(R.string.aidj_dj_voice_sub),
                    checked = djVoiceEnabled,
                    onCheckedChange = { repository.setDjVoiceEnabled(it) },
                )
                Spacer(Modifier.height(12.dp))
                AiDjToggleRow(
                    title = stringResource(R.string.aidj_auto_refresh_title),
                    subtitle = stringResource(R.string.aidj_auto_refresh_sub),
                    checked = autoRefresh,
                    onCheckedChange = { repository.setAutoRefresh(it) },
                )
                if (djVoiceEnabled) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.aidj_voice_label),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    GeminiTtsRepository.VOICES.forEach { (voiceName, description) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { model.updateTtsVoice(voiceName) }
                                .padding(vertical = 8.dp),
                        ) {
                            RadioButton(
                                selected = model.ttsVoice == voiceName,
                                onClick = { model.updateTtsVoice(voiceName) },
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    voiceName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    Text(
                        stringResource(R.string.aidj_voice_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.aidj_voice_test_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.aidj_voice_test_sub),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    // Diagnostic: shows what prefs/key the tester sees (no key value).
                    Text(
                        "Key check: " + model.ttsRepository.keyDiagnostic(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    val testScope = rememberCoroutineScope()
                    var testResults by remember {
                        mutableStateOf<Map<String, GeminiTtsRepository.TtsTestResult>>(emptyMap())
                    }
                    var testingModel by remember { mutableStateOf<String?>(null) }
                    model.ttsRepository.allModels().forEach { ttsModel ->
                        val result = testResults[ttsModel]
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    ttsModel,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                result?.let {
                                    Text(
                                        if (it.success) "✓ ${it.audioBytes / 1024} KB audio"
                                        else "✗ ${it.detail}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (it.success)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            if (testingModel == ttsModel) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            } else {
                                TextButton(onClick = {
                                    testingModel = ttsModel
                                    testScope.launch {
                                        val sampleFile = java.io.File(
                                            context.cacheDir,
                                            "tts-test-${ttsModel.hashCode()}.wav",
                                        )
                                        val r = model.ttsRepository.testModel(
                                            ttsModel,
                                            model.ttsVoice,
                                            sampleFile,
                                        )
                                        testResults = testResults + (ttsModel to r)
                                        testingModel = null
                                    }
                                }) {
                                    Text(stringResource(R.string.aidj_voice_test_button))
                                }
                            }
                        }
                    }
                }
            }

            // Credentials.
            Text(
                stringResource(R.string.aidj_section_credentials),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            AiDjSectionCard {
                Text(
                    stringResource(R.string.aidj_api_key_label),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(R.string.aidj_api_key_where),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            if (hasKey) stringResource(R.string.aidj_api_key_set_placeholder)
                            else stringResource(R.string.aidj_api_key_hint)
                        )
                    },
                    visualTransformation = if (showKey) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    singleLine = true,
                    trailingIcon = {
                        TextButton(onClick = { showKey = !showKey }) {
                            Text(
                                if (showKey) stringResource(R.string.aidj_hide)
                                else stringResource(R.string.aidj_show)
                            )
                        }
                    },
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = {
                            repository.setApiKey(keyInput)
                            keyInput = ""
                        },
                        enabled = keyInput.isNotBlank(),
                    ) {
                        Text(stringResource(R.string.aidj_save_key))
                    }
                    if (hasKey) {
                        OutlinedButton(onClick = {
                            repository.clearApiKey()
                            keyInput = ""
                        }) {
                            Text(stringResource(R.string.aidj_remove_key))
                        }
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                TextButton(
                    onClick = {
                        try {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://aistudio.google.com/apikey"),
                                )
                            )
                        } catch (_: Exception) { }
                    }
                ) {
                    Text(stringResource(R.string.aidj_get_key))
                    Icon(
                        Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            // Model selection.
            Text(
                stringResource(R.string.aidj_section_model),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            AiDjSectionCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showModelSheet = true }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.Science,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(Modifier.size(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.aidj_model_label),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            stringResource(R.string.aidj_model_sub),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        FilterChip(
                            selected = true,
                            onClick = { showModelSheet = true },
                            label = { Text(aiModel.prettyModelName()) },
                        )
                    }
                }
            }

            // Prompt behavior.
            Text(
                stringResource(R.string.aidj_section_prompt),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            AiDjSectionCard {
                Text(
                    stringResource(R.string.aidj_prompt_label),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(R.string.aidj_prompt_sub),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.aidj_preset_prompts),
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AiDjRepository.PROMPT_PRESETS.forEach { (name, preset) ->
                        FilterChip(
                            selected = false,
                            onClick = {
                                repository.setPrompt(preset)
                                promptInput = preset
                                promptDirty = false
                            },
                            label = { Text(name) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = if (promptDirty) promptInput else prompt,
                    onValueChange = {
                        promptInput = it
                        promptDirty = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    minLines = 4,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            repository.setPrompt(promptInput)
                            promptDirty = false
                        },
                        enabled = promptDirty,
                    ) {
                        Text(stringResource(R.string.aidj_save_prompt))
                    }
                    TextButton(onClick = {
                        repository.resetPrompt()
                        promptInput = AiDjRepository.DEFAULT_PROMPT
                        promptDirty = false
                    }) {
                        Text(stringResource(R.string.aidj_reset_prompt))
                    }
                }
            }

            // Track count.
            Text(
                stringResource(R.string.aidj_track_count_label),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AiDjRepository.TRACK_COUNT_OPTIONS.forEach { option ->
                    FilterChip(
                        selected = trackCount == option,
                        onClick = { repository.setTrackCount(option) },
                        label = { Text(option.toString()) },
                    )
                }
            }

            // AI usage report.
            Text(
                stringResource(R.string.aidj_section_usage),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            AiDjSectionCard {
                Text(
                    stringResource(R.string.aidj_usage_total),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(
                        R.string.aidj_usage_detail,
                        tokenUsage.promptTokens + tokenUsage.outputTokens,
                        tokenUsage.promptTokens,
                        tokenUsage.outputTokens,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = { repository.clearTokenUsage() }) {
                    Text(stringResource(R.string.aidj_usage_clear))
                }
            }

            Text(
                stringResource(R.string.aidj_privacy_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showModelSheet) {
        ModalBottomSheet(onDismissRequest = { showModelSheet = false }) {
            Text(
                stringResource(R.string.aidj_model_label),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            LazyColumn(
                modifier = Modifier.padding(bottom = 32.dp),
            ) {
                items(AiDjRepository.AI_MODELS) { modelName ->
                    val selected = modelName == aiModel
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .clickable {
                                repository.setModel(modelName)
                                showModelSheet = false
                            }
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            modelName.prettyModelName(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        if (selected) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** "gemini-2.5-flash" -> "Gemini 2.5 Flash". */
private fun String.prettyModelName(): String =
    split("-").joinToString(" ") { part ->
        part.replaceFirstChar { it.uppercase() }
    }

@Composable
private fun AiDjSectionCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            content()
        }
    }
}

@Composable
private fun AiDjToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
