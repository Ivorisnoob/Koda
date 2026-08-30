package com.ivor.ivormusic.ui.tv

import android.app.Application
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.tv.AddonRepository
import com.ivor.ivormusic.data.tv.InstalledAddon
import com.ivor.ivormusic.data.tv.StremioClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TvAddonsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AddonRepository(application)
    private val client = StremioClient(application)

    val addons: StateFlow<List<InstalledAddon>> = repository.addons

    private val _isInstalling = MutableStateFlow(false)
    val isInstalling: StateFlow<Boolean> = _isInstalling.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /**
     * Install by URL.
     *
     * Accepts the `stremio://` scheme people copy out of addon pages as well as
     * `https://`, because that is what the ecosystem's install buttons produce
     * and rejecting it would make the obvious paste fail for no reason.
     */
    fun install(rawUrl: String) {
        val url = normalise(rawUrl)
        if (url.isBlank()) {
            _error.value = INVALID
            return
        }
        viewModelScope.launch {
            _isInstalling.value = true
            _error.value = null
            val manifest = client.manifest(url, forceFresh = true)
            if (manifest == null || manifest.id.isBlank()) {
                _error.value = UNREACHABLE
            } else if (!repository.install(url, manifest)) {
                _error.value = INVALID
            }
            _isInstalling.value = false
        }
    }

    fun remove(addonId: String) = repository.remove(addonId)

    fun setResourceEnabled(addonId: String, resource: String, enabled: Boolean) =
        repository.setResourceEnabled(addonId, resource, enabled)

    fun clearError() { _error.value = null }

    companion object {
        const val INVALID = "invalid"
        const val UNREACHABLE = "unreachable"

        /** Pure, so the accepted URL shapes can be tested. */
        fun normalise(raw: String): String {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return ""
            val https = when {
                trimmed.startsWith("stremio://") -> "https://" + trimmed.removePrefix("stremio://")
                trimmed.startsWith("http://") -> "https://" + trimmed.removePrefix("http://")
                trimmed.startsWith("https://") -> trimmed
                // A bare host is what people paste most often.
                !trimmed.contains("://") -> "https://$trimmed"
                else -> return ""
            }
            return if (https.endsWith("/manifest.json")) https
            else https.trimEnd('/') + "/manifest.json"
        }
    }
}

/**
 * The addon manager.
 *
 * Deliberately minimal for now: install by URL, switch resources off, remove.
 * Browsing the community directory and opening an addon's own `/configure` page
 * in a WebView are the two things that make a debrid setup one tap, and they
 * come later - but a "Browse addons" button that opened nothing would be worse
 * than a plain list.
 */
@Composable
fun TvAddonsScreen(
    viewModel: TvAddonsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val addons by viewModel.addons.collectAsState()
    val isInstalling by viewModel.isInstalling.collectAsState()
    val error by viewModel.error.collectAsState()
    var url by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.tv_addons)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                    )
                }
            },
        )

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "add") {
                Column {
                    OutlinedTextField(
                        value = url,
                        onValueChange = {
                            url = it
                            viewModel.clearError()
                        },
                        label = { Text(stringResource(R.string.tv_addon_url_label)) },
                        placeholder = { Text("https://...") },
                        singleLine = true,
                        isError = error != null,
                        supportingText = error?.let { code ->
                            {
                                Text(
                                    when (code) {
                                        TvAddonsViewModel.UNREACHABLE ->
                                            stringResource(R.string.tv_addon_unreachable)
                                        else -> stringResource(R.string.tv_addon_invalid)
                                    }
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            viewModel.install(url)
                            url = ""
                        },
                        enabled = url.isNotBlank() && !isInstalling,
                    ) {
                        if (isInstalling) {
                            LoadingIndicator(modifier = Modifier.size(18.dp))
                        } else {
                            Icon(Icons.Rounded.Add, contentDescription = null,
                                modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(6.dp))
                            Text(stringResource(R.string.tv_addon_install))
                        }
                    }
                }
            }

            item(key = "note") {
                Text(
                    text = stringResource(R.string.tv_addon_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            items(addons, key = { it.id }) { addon ->
                AddonCard(
                    addon = addon,
                    onToggleResource = { resource, enabled ->
                        viewModel.setResourceEnabled(addon.id, resource, enabled)
                    },
                    onRemove = { viewModel.remove(addon.id) },
                )
            }
        }
    }
}

@Composable
private fun AddonCard(
    addon: InstalledAddon,
    onToggleResource: (String, Boolean) -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = addon.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    addon.manifest.description?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (!addon.isPreinstalled) {
                    IconButton(onClick = onRemove) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = stringResource(R.string.tv_addon_remove),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            if (addon.manifest.resources.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    addon.manifest.resources.forEach { resource ->
                        val enabled = resource.name !in addon.disabledResources
                        FilterChip(
                            selected = enabled,
                            onClick = { onToggleResource(resource.name, !enabled) },
                            label = { Text(resource.name) },
                        )
                    }
                }
            }
        }
    }
}
