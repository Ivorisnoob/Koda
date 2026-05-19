package com.ivor.ivormusic.platform

import androidx.compose.ui.graphics.Color

/**
 * Extract dominant colors from an image URL for the ambient background effect.
 * Android: uses Coil + Palette API.
 * iOS: stub returning empty (can be implemented with UIKit color sampling).
 */
expect suspend fun extractAlbumColors(imageUrl: String?): List<Color>
