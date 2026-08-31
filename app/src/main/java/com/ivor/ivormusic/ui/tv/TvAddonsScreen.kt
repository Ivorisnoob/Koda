package com.ivor.ivormusic.ui.tv

import android.app.Application
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.tv.AddonRepository
import com.ivor.ivormusic.data.tv.AddonDescriptor
import com.ivor.ivormusic.data.tv.InstalledAddon
import com.ivor.ivormusic.data.tv.StremioClient
import com.ivor.ivormusic.data.tv.StremioUrls
import com.ivor.ivormusic.ui.components.QueueDragHandle
import com.ivor.ivormusic.ui.components.SearchField
import com.ivor.ivormusic.ui.components.rememberQueueReorderState
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class TvAddonsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AddonRepository(application)
    private val client = StremioClient(application)

    val addons: StateFlow<List<InstalledAddon>> = repository.addons

    private val _isInstalling = MutableStateFlow(false)
    val isInstalling: StateFlow<Boolean> = _isInstalling.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _installedName = MutableStateFlow<String?>(null)
    val installedName: StateFlow<String?> = _installedName.asStateFlow()

    private val _directory = MutableStateFlow<List<AddonDescriptor>>(emptyList())
    val directory: StateFlow<List<AddonDescriptor>> = _directory.asStateFlow()

    private val _isLoadingDirectory = MutableStateFlow(false)
    val isLoadingDirectory: StateFlow<Boolean> = _isLoadingDirectory.asStateFlow()

    private val _directoryFailed = MutableStateFlow(false)
    val directoryFailed: StateFlow<Boolean> = _directoryFailed.asStateFlow()

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
        _isInstalling.value = true
        viewModelScope.launch {
            _error.value = null
            _installedName.value = null
            val manifest = client.manifest(url, forceFresh = true)
            if (manifest == null || manifest.id.isBlank()) {
                _error.value = UNREACHABLE
            } else if (!repository.install(url, manifest)) {
                _error.value = STORAGE
            } else {
                _installedName.value = manifest.name
            }
            _isInstalling.value = false
        }
    }

    fun remove(addonId: String) = repository.remove(addonId)

    fun setResourceEnabled(addonId: String, resource: String, enabled: Boolean) =
        repository.setResourceEnabled(addonId, resource, enabled)

    fun reorder(orderedIds: List<String>) = repository.reorder(orderedIds)

    /** Load Cinemeta's live addon directory; no catalog is hardcoded locally. */
    fun loadDirectory(forceFresh: Boolean = false) {
        if (_isLoadingDirectory.value) return
        if (_directory.value.isNotEmpty() && !forceFresh) return
        viewModelScope.launch {
            _isLoadingDirectory.value = true
            _directoryFailed.value = false
            val all = coroutineScope {
                val official = async {
                    client.addonCatalog(
                        AddonRepository.CINEMETA_URL,
                        "all",
                        "official",
                        forceFresh,
                    )
                }
                val community = async {
                    client.addonCatalog(
                        AddonRepository.CINEMETA_URL,
                        "all",
                        "community",
                        forceFresh,
                    )
                }
                official.await() + community.await()
            }
            // The protocol carries an adult bit. Browse keeps it out by
            // default; a settings control can deliberately expose it later.
            _directory.value = all
                .filter { descriptor ->
                    descriptor.transportUrl.isNotBlank() &&
                        descriptor.manifest.id.isNotBlank() &&
                        descriptor.manifest.behaviorHints?.adult != true
                }
                .distinctBy { it.manifest.id }
                .sortedBy { it.manifest.name.lowercase() }
            _directoryFailed.value = _directory.value.isEmpty()
            _isLoadingDirectory.value = false
        }
    }

    fun clearFeedback() {
        _error.value = null
        _installedName.value = null
    }

    companion object {
        const val INVALID = "invalid"
        const val UNREACHABLE = "unreachable"
        const val STORAGE = "storage"

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

/** Installed addons, the live directory, configuration, resource controls and priority. */
@Composable
fun TvAddonsScreen(
    viewModel: TvAddonsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val addons by viewModel.addons.collectAsState()
    val isInstalling by viewModel.isInstalling.collectAsState()
    val error by viewModel.error.collectAsState()
    val installedName by viewModel.installedName.collectAsState()
    val directory by viewModel.directory.collectAsState()
    val isLoadingDirectory by viewModel.isLoadingDirectory.collectAsState()
    val directoryFailed by viewModel.directoryFailed.collectAsState()

    var url by remember { mutableStateOf("") }
    var page by remember { mutableStateOf(AddonPage.INSTALLED) }
    var directoryQuery by remember { mutableStateOf("") }
    var pendingRemoval by remember { mutableStateOf<InstalledAddon?>(null) }
    var configureFor by remember { mutableStateOf<AddonDescriptor?>(null) }

    val listState = rememberLazyListState()
    var orderedAddons by remember { mutableStateOf(addons) }
    LaunchedEffect(addons) { orderedAddons = addons.sortedBy { it.order } }
    LaunchedEffect(installedName) { if (installedName != null) url = "" }
    LaunchedEffect(page) {
        if (page == AddonPage.DISCOVER) viewModel.loadDirectory()
    }

    val reorder = rememberQueueReorderState(
        listState = listState,
        keys = orderedAddons.map { it.id },
        onMove = { from, to ->
            orderedAddons = orderedAddons.toMutableList().apply {
                add(to, removeAt(from))
            }
        },
        onSettle = { viewModel.reorder(orderedAddons.map { it.id }) },
    )

    val installedIds = remember(addons) { addons.mapTo(HashSet()) { it.id } }
    val visibleDirectory = remember(directory, directoryQuery) {
        val query = directoryQuery.trim()
        if (query.isBlank()) directory
        else directory.filter { descriptor ->
            descriptor.manifest.name.contains(query, ignoreCase = true) ||
                descriptor.manifest.description.orEmpty().contains(query, ignoreCase = true)
        }
    }

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
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "pages") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = page == AddonPage.INSTALLED,
                        onClick = { page = AddonPage.INSTALLED },
                        label = {
                            Text(stringResource(R.string.tv_addons_installed, addons.size))
                        },
                    )
                    FilterChip(
                        selected = page == AddonPage.DISCOVER,
                        onClick = { page = AddonPage.DISCOVER },
                        label = { Text(stringResource(R.string.tv_addons_discover)) },
                    )
                }
            }

            if (page == AddonPage.INSTALLED) {
                item(key = "add") {
                    Column {
                        OutlinedTextField(
                            value = url,
                            onValueChange = {
                                url = it
                                viewModel.clearFeedback()
                            },
                            label = { Text(stringResource(R.string.tv_addon_url_label)) },
                            placeholder = { Text(stringResource(R.string.tv_addon_url_hint)) },
                            singleLine = true,
                            isError = error != null,
                            supportingText = when {
                                error != null -> {{
                                    Text(
                                        text = when (error) {
                                            TvAddonsViewModel.UNREACHABLE ->
                                                stringResource(R.string.tv_addon_unreachable)
                                            TvAddonsViewModel.STORAGE ->
                                                stringResource(R.string.tv_addon_secure_storage_failed)
                                            else -> stringResource(R.string.tv_addon_invalid)
                                        },
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }}
                                installedName != null -> {{
                                    Text(
                                        stringResource(R.string.tv_addon_installed, installedName!!),
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }}
                                else -> null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { viewModel.install(url) },
                            enabled = url.isNotBlank() && !isInstalling,
                        ) {
                            if (isInstalling) {
                                LoadingIndicator(modifier = Modifier.size(18.dp))
                            } else {
                                Icon(
                                    Icons.Rounded.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
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

                items(orderedAddons, key = { it.id }) { addon ->
                    val dragging = reorder.isDragging(addon.id)
                    AddonCard(
                        addon = addon,
                        dragHandle = {
                            QueueDragHandle(
                                state = reorder,
                                rowKey = addon.id,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        onToggleResource = { resource, enabled ->
                            viewModel.setResourceEnabled(addon.id, resource, enabled)
                        },
                        onRemove = { pendingRemoval = addon },
                        modifier = Modifier
                            .zIndex(if (dragging) 1f else 0f)
                            .graphicsLayer { translationY = reorder.offsetFor(addon.id) },
                    )
                }
            } else {
                item(key = "directory-search") {
                    SearchField(
                        query = directoryQuery,
                        onQueryChange = { directoryQuery = it },
                        placeholder = stringResource(R.string.tv_addons_search),
                    )
                }

                if (isInstalling || error != null || installedName != null) {
                    item(key = "directory-feedback") {
                        AddonInstallFeedback(
                            isInstalling = isInstalling,
                            error = error,
                            installedName = installedName,
                        )
                    }
                }

                when {
                    isLoadingDirectory && directory.isEmpty() -> item(key = "directory-loading") {
                        DirectoryLoading()
                    }
                    directoryFailed -> item(key = "directory-failed") {
                        TvEmptyState(
                            title = stringResource(R.string.tv_addons_directory_failed_title),
                            body = stringResource(R.string.tv_addons_directory_failed_body),
                            actionLabel = stringResource(R.string.tv_retry),
                            onAction = { viewModel.loadDirectory(forceFresh = true) },
                        )
                    }
                    visibleDirectory.isEmpty() -> item(key = "directory-empty") {
                        TvEmptyState(
                            title = stringResource(R.string.tv_addons_no_match_title),
                            body = stringResource(R.string.tv_addons_no_match_body, directoryQuery),
                        )
                    }
                    else -> items(visibleDirectory, key = { it.manifest.id }) { descriptor ->
                        DirectoryAddonCard(
                            descriptor = descriptor,
                            installed = descriptor.manifest.id in installedIds,
                            installing = isInstalling,
                            onInstall = { viewModel.install(descriptor.transportUrl) },
                            onConfigure = { configureFor = descriptor },
                        )
                    }
                }
            }
        }
    }

    pendingRemoval?.let { addon ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text(stringResource(R.string.tv_addon_remove_title)) },
            text = { Text(stringResource(R.string.tv_addon_remove_body, addon.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.remove(addon.id)
                        pendingRemoval = null
                    }
                ) { Text(stringResource(R.string.tv_addon_remove)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    configureFor?.let { descriptor ->
        AddonConfigureSheet(
            descriptor = descriptor,
            onInstall = viewModel::install,
            onDismiss = { configureFor = null },
        )
    }
}

@Composable
private fun AddonCard(
    addon: InstalledAddon,
    dragHandle: @Composable () -> Unit,
    onToggleResource: (String, Boolean) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth(),
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
                dragHandle()
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
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
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

@Composable
private fun AddonInstallFeedback(
    isInstalling: Boolean,
    error: String?,
    installedName: String?,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            if (isInstalling) LoadingIndicator(modifier = Modifier.size(20.dp))
            Text(
                text = when {
                    isInstalling -> stringResource(R.string.tv_addon_checking)
                    error == TvAddonsViewModel.UNREACHABLE ->
                        stringResource(R.string.tv_addon_unreachable)
                    error == TvAddonsViewModel.STORAGE ->
                        stringResource(R.string.tv_addon_secure_storage_failed)
                    error != null -> stringResource(R.string.tv_addon_invalid)
                    else -> stringResource(R.string.tv_addon_installed, installedName.orEmpty())
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (error != null) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private enum class AddonPage { INSTALLED, DISCOVER }

@Composable
private fun DirectoryLoading() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LoadingIndicator()
        Text(
            stringResource(R.string.tv_addons_loading_directory),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DirectoryAddonCard(
    descriptor: AddonDescriptor,
    installed: Boolean,
    installing: Boolean,
    onInstall: () -> Unit,
    onConfigure: () -> Unit,
) {
    val manifest = descriptor.manifest
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.size(48.dp),
                ) {
                    AsyncImage(
                        model = manifest.logo,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(
                        text = manifest.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    manifest.description?.takeIf { it.isNotBlank() }?.let { description ->
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            if (manifest.resources.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    manifest.resources.joinToString("  ·  ") { it.name },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Button(
                    onClick = onInstall,
                    enabled = !installed && !installing,
                ) {
                    Text(
                        stringResource(
                            if (installed) R.string.tv_addon_already_installed
                            else R.string.tv_addon_install
                        )
                    )
                }
                if (manifest.behaviorHints?.configurable == true) {
                    FilledTonalButton(onClick = onConfigure, enabled = !installing) {
                        Text(stringResource(R.string.tv_addon_configure))
                    }
                }
            }
        }
    }
}

/** The addon's own config page; only the resulting install URL reaches Koda. */
@Composable
private fun AddonConfigureSheet(
    descriptor: AddonDescriptor,
    onInstall: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var pageProgress by remember { mutableStateOf(0) }
    val configureUrl = remember(descriptor.transportUrl) {
        StremioUrls.baseOf(descriptor.transportUrl).trimEnd('/') + "/configure"
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().heightIn(min = 480.dp, max = 680.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.tv_addon_configure),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        descriptor.manifest.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_close))
                }
            }
            Box(Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            settings.mixedContentMode =
                                android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    pageProgress = newProgress
                                }
                            }
                            webViewClient = object : WebViewClient() {
                                private fun intercept(candidate: String): Boolean {
                                    val installUrl = candidate.startsWith("stremio://") ||
                                        android.net.Uri.parse(candidate).path
                                            ?.endsWith("/manifest.json") == true
                                    if (!installUrl) return false
                                    onInstall(candidate)
                                    onDismiss()
                                    return true
                                }

                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                ): Boolean = request?.url?.toString()?.let(::intercept) ?: false

                                @Deprecated("Legacy WebView callback")
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    url: String?,
                                ): Boolean = url?.let(::intercept) ?: false
                            }
                            loadUrl(configureUrl)
                        }
                    },
                    onRelease = { webView ->
                        webView.stopLoading()
                        webView.destroy()
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                if (pageProgress in 0..99) {
                    LoadingIndicator(modifier = Modifier.align(Alignment.Center))
                }
            }
        }
    }
}
