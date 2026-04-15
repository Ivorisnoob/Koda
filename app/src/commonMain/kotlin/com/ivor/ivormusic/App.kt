package com.ivor.ivormusic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun App() {
    var message by remember { mutableStateOf("Welcome to IvorMusic Desktop") }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            DesktopContent(
                message = message,
                onShowLibrary = { message = "Library coming soon" },
                onShowDownloads = { message = "Downloads coming soon" },
                onShowSettings = { message = "Settings coming soon" }
            )
        }
    }
}

@Composable
private fun DesktopContent(
    message: String,
    onShowLibrary: () -> Unit,
    onShowDownloads: () -> Unit,
    onShowSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "IvorMusic",
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            text = "Desktop preview",
            style = MaterialTheme.typography.titleMedium
        )

        Text(
            text = "The core desktop shell is now wired up and interactive.",
            style = MaterialTheme.typography.bodyLarge
        )

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth()
        )

        Button(onClick = onShowLibrary) {
            Text("Open Library")
        }

        Button(onClick = onShowDownloads) {
            Text("Open Downloads")
        }

        Button(onClick = onShowSettings) {
            Text("Open Settings")
        }
    }
}
