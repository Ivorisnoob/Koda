package com.ivor.ivormusic.ui.library

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.AiDjMix
import com.ivor.ivormusic.data.AiDjRepository
import com.ivor.ivormusic.data.DjVoiceRepository
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.data.SongSource
import com.ivor.ivormusic.data.StatsRepository
import com.ivor.ivormusic.ui.home.HomeViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The AI DJ: generates a mix from the user's library via the Google AI API,
 * guided by the custom prompt in Settings. The result can be played right
 * away or saved as a local playlist.
 *
 * With DJ voice enabled, the DJ's intro lines are synthesized with the
 * device's text-to-speech and interleaved with the songs in the queue,
 * Spotify AI DJ style. Intros are skippable queue items and never touch
 * listening history.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiDjScreen(
    viewModel: HomeViewModel,
    onBack: () -> Unit,
    onPlayQueue: (List<Song>, Song?) -> Unit,
    onPlayDjMix: (List<Song>) -> Unit,
    onOpenSettings: () -> Unit,
    contentPadding: PaddingValues,
) {
    val context = LocalContext.current
    val repository = remember { AiDjRepository(context.applicationContext) }
    val voiceRepository = remember { DjVoiceRepository(context.applicationContext) }
    val hasKey by repository.hasApiKey.collectAsState()
    val djVoiceEnabled by repository.djVoiceEnabled.collectAsState()
    val likedSongs by viewModel.likedSongs.collectAsState()
    val downloadedSongs by viewModel.downloadedSongs.collectAsState()

    // The DJ pool: what the user owns and loves, deduplicated.
    val library = remember(likedSongs, downloadedSongs) {
        (likedSongs + downloadedSongs).distinctBy { it.id }
    }

    var generating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // Open on the cached mix when one exists; only the Generate/Regenerate
    // buttons (or the Home emblem) build a fresh one.
    val cachedMix by repository.cachedMix.collectAsState()
    var mix by remember { mutableStateOf<AiDjMix?>(null) }
    LaunchedEffect(cachedMix) {
        if (mix == null) mix = cachedMix
    }
    // Daily auto-refresh: if the cached mix is older than 24h, quietly build
    // a fresh one in the background. The cached mix stays visible meanwhile.
    val autoRefresh by repository.autoRefresh.collectAsState()
    LaunchedEffect(autoRefresh, library) {
        if (autoRefresh && hasKey && repository.isCacheStale() && !generating) {
            generating = true
            repository.generateMix(library).fold(
                onSuccess = {
                    repository.cacheMix(it)
                    mix = it
                },
                onFailure = { /* keep the stale cache; user can retry */ },
            )
            generating = false
        }
    }
    var saving by remember { mutableStateOf(false) }
    var preparingVoice by remember { mutableStateOf(false) }
    var voiceProgress by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()

    fun generate() {
        if (generating) return
        generating = true
        error = null
        mix = null
        scope.launch {
            repository.generateMix(library).fold(
                onSuccess = {
                    repository.cacheMix(it)
                    mix = it
                },
                onFailure = { error = it.message ?: "Generation failed" },
            )
            generating = false
        }
    }

    /**
     * Build the playback queue for [mix]: with DJ voice on, tracks carrying
     * a transition line are preceded by its synthesized spoken intro.
     */
    fun playMix(mix: AiDjMix) {
        if (preparingVoice) return
        if (!djVoiceEnabled) {
            onPlayDjMix(mix.tracks.map { it.song })
            return
        }
        preparingVoice = true
        voiceProgress = 0f
        error = null
        scope.launch {
            try {
                val queue = voiceRepository.buildQueueWithIntros(mix) { progress ->
                    voiceProgress = progress
                }
                onPlayDjMix(queue)
            } finally {
                preparingVoice = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.aidj_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            Icons.Rounded.Settings,
                            contentDescription = stringResource(R.string.aidj_open_settings),
                        )
                    }
                },
            )
        }
    ) { padding ->
        when {
            !hasKey -> {
                AiDjNeedsKey(
                    onOpenSettings = onOpenSettings,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp),
                )
            }
            generating -> {
                AiDjBusy(
                    message = stringResource(R.string.aidj_generating),
                    progress = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                )
            }
            preparingVoice -> {
                AiDjBusy(
                    message = stringResource(R.string.aidj_preparing_voice),
                    progress = voiceProgress,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                )
            }
            else -> {
                val currentMix = mix
                val currentError = error
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = 8.dp,
                        bottom = contentPadding.calculateBottomPadding() + 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    item(key = "dj_header") {
                        Column {
                            Icon(
                                Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(40.dp),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                currentMix?.title ?: stringResource(R.string.aidj_intro),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.aidj_pool_size, library.size),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    stringResource(R.string.aidj_dj_voice_title),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Switch(
                                    checked = djVoiceEnabled,
                                    onCheckedChange = { repository.setDjVoiceEnabled(it) },
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = ::generate,
                                    enabled = library.isNotEmpty(),
                                ) {
                                    Icon(
                                        if (currentMix == null) Icons.Rounded.AutoAwesome
                                        else Icons.Rounded.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.size(8.dp))
                                    Text(
                                        if (currentMix == null) stringResource(R.string.aidj_generate)
                                        else stringResource(R.string.aidj_regenerate)
                                    )
                                }
                                if (currentMix != null) {
                                    OutlinedButton(
                                        onClick = { playMix(currentMix) },
                                    ) {
                                        Icon(
                                            Icons.Rounded.PlayArrow,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(Modifier.size(8.dp))
                                        Text(stringResource(R.string.aidj_play))
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            if (saving) return@OutlinedButton
                                            saving = true
                                            scope.launch {
                                                try {
                                                    val date = SimpleDateFormat(
                                                        "MMM d", Locale.getDefault()
                                                    ).format(Date())
                                                    viewModel.createLocalPlaylistWithSongs(
                                                        name = "${currentMix.title} • $date",
                                                        description = currentMix.tracks.mapNotNull { it.djLine }
                                                            .take(3).joinToString(" / "),
                                                        songs = currentMix.tracks.map { it.song },
                                                    )
                                                } finally {
                                                    saving = false
                                                }
                                            }
                                        },
                                        enabled = !saving,
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Rounded.PlaylistAdd,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(Modifier.size(8.dp))
                                        Text(stringResource(R.string.aidj_save_playlist))
                                    }
                                }
                            }
                            if (currentError != null) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    currentError,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                    if (currentMix != null) {
                        itemsIndexed(
                            currentMix.tracks,
                            key = { _, track -> "dj_${track.song.id}" },
                        ) { index, track ->
                            AiDjTrackRow(
                                index = index + 1,
                                song = track.song,
                                djLine = track.djLine,
                                onClick = {
                                    onPlayQueue(
                                        currentMix.tracks.map { it.song },
                                        track.song,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AiDjBusy(
    message: String,
    progress: Float?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 48.dp),
        ) {
            if (progress == null) {
                CircularProgressIndicator()
            } else {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AiDjNeedsKey(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Rounded.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.aidj_needs_key_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.aidj_needs_key_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onOpenSettings) {
            Text(stringResource(R.string.aidj_open_settings))
        }
    }
}

@Composable
private fun AiDjTrackRow(
    index: Int,
    song: Song,
    djLine: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            index.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 12.dp),
        )
        val art = song.albumArtUri ?: song.highResThumbnailUrl ?: song.thumbnailUrl
        if (art != null) {
            AsyncImage(
                model = art,
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop,
            )
        }
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                song.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                song.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!djLine.isNullOrBlank()) {
                Text(
                    djLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onClick) {
            Icon(
                Icons.Rounded.PlayArrow,
                contentDescription = stringResource(R.string.aidj_play),
            )
        }
    }
}
