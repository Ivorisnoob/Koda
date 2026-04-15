package com.ivor.ivormusic

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "IvorMusic") {
        MaterialTheme {
            Text("IvorMusic Desktop (KMP)")
        }
    }
}
