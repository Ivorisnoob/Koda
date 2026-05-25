package com.ivor.ivormusic.platform

import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.image.BufferedImage
import java.net.URL
import javax.imageio.ImageIO

actual suspend fun extractAlbumColors(imageUrl: String?): List<Color> {
    if (imageUrl == null) return emptyList()
    return withContext(Dispatchers.IO) {
        try {
            val image: BufferedImage = ImageIO.read(URL(imageUrl)) ?: return@withContext emptyList()
            val scaled = scaleDown(image, 64)
            sampleDominantColors(scaled)
        } catch (_: Exception) {
            emptyList()
        }
    }
}

private fun scaleDown(src: BufferedImage, maxDim: Int): BufferedImage {
    val scale = maxDim.toDouble() / maxOf(src.width, src.height)
    val w = (src.width * scale).toInt().coerceAtLeast(1)
    val h = (src.height * scale).toInt().coerceAtLeast(1)
    val out = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
    val g = out.createGraphics()
    g.drawImage(src, 0, 0, w, h, null)
    g.dispose()
    return out
}

private fun sampleDominantColors(image: BufferedImage): List<Color> {
    val buckets = HashMap<Int, Int>()
    for (y in 0 until image.height) {
        for (x in 0 until image.width) {
            val rgb = image.getRGB(x, y)
            val quantized = (rgb and 0xE0E0E0.or(0xFF000000.toInt()))
            buckets[quantized] = (buckets[quantized] ?: 0) + 1
        }
    }
    return buckets.entries
        .sortedByDescending { it.value }
        .take(4)
        .map { Color(it.key) }
}
