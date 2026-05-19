package com.ivor.ivormusic.ui.theme

import androidx.compose.runtime.Composable

@Composable
actual fun KodaTheme(
    darkTheme: Boolean,
    dynamicColor: Boolean,
    content: @Composable () -> Unit
) {
    KodaThemeBase(darkTheme = darkTheme, content = content)
}
