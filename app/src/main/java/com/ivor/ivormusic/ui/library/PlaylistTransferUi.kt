package com.ivor.ivormusic.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.R
import com.ivor.ivormusic.ui.home.HomeViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlaylistImportButton(viewModel: HomeViewModel) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<HomeViewModel.PlaylistImportResult?>(null) }
    var failed by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val r = viewModel.importPlaylists(uri)
            busy = false
            if (r == null) failed = true else result = r
        }
    }
    TextButton(onClick = { picker.launch(arrayOf("*/*")) }, enabled = !busy) {
        if (busy) LoadingIndicator(Modifier.size(18.dp))
        else Icon(Icons.Rounded.FileDownload, null, Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.pl_import))
    }
    result?.let { r ->
        AlertDialog(
            onDismissRequest = { result = null },
            confirmButton = { TextButton(onClick = { result = null }) { Text(stringResource(R.string.action_done)) } },
            title = { Text(stringResource(R.string.pl_import_done_title)) },
            text = {
                val lines = buildList {
                    if (r.playlists > 0) add(pluralStringResource(R.plurals.pl_import_playlists, r.playlists, r.playlists, r.songs))
                    if (r.saved > 0) add(pluralStringResource(R.plurals.pl_import_saved, r.saved, r.saved))
                    if (r.missing > 0) add(pluralStringResource(R.plurals.pl_import_missing, r.missing, r.missing))
                    if (r.foreign > 0) add(pluralStringResource(R.plurals.pl_import_foreign, r.foreign, r.foreign))
                    if (isEmpty()) add(stringResource(R.string.pl_import_nothing_new))
                }
                Text(lines.joinToString("\n\n"))
            }
        )
    }
    if (failed) {
        AlertDialog(
            onDismissRequest = { failed = false },
            confirmButton = { TextButton(onClick = { failed = false }) { Text(stringResource(R.string.action_done)) } },
            title = { Text(stringResource(R.string.pl_import_failed_title)) },
            text = { Text(stringResource(R.string.pl_import_failed_body)) }
        )
    }
}
