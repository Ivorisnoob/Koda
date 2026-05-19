package com.ivor.ivormusic.platform

import android.graphics.drawable.BitmapDrawable
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.ivor.ivormusic.platform.applicationContext

actual suspend fun extractAlbumColors(imageUrl: String?): List<Color> {
    if (imageUrl == null) return emptyList()
    return withContext(Dispatchers.IO) {
        try {
            val context = applicationContext
            val loader = ImageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(imageUrl)
                .allowHardware(false)
                .size(Size(128, 128))
                .build()
            val result = loader.execute(request)
            if (result is SuccessResult) {
                val bitmap = (result.image as? BitmapDrawable)?.bitmap ?: return@withContext emptyList()
                val palette = Palette.from(bitmap).generate()
                listOfNotNull(
                    palette.darkVibrantSwatch?.rgb?.let { Color(it) },
                    palette.vibrantSwatch?.rgb?.let { Color(it) },
                    palette.darkMutedSwatch?.rgb?.let { Color(it) },
                    palette.mutedSwatch?.rgb?.let { Color(it) },
                    palette.dominantSwatch?.rgb?.let { Color(it) }
                ).take(4)
            } else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
