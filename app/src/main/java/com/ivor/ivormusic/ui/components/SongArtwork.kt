package com.ivor.ivormusic.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ivor.ivormusic.data.Song

/**
 * Album artwork that layers the high-res cover over the low-res thumbnail.
 *
 * The low-res thumbnail (usually already in Coil's memory cache from the
 * list or mini player) renders immediately, and the high-res version fades
 * in on top only once it has actually loaded. If the high-res URL fails
 * (e.g. ytimg maxresdefault returns 404 for many tracks) the low-res image
 * simply stays visible instead of leaving a blank frame.
 */
@Composable
fun SongArtwork(
    song: Song,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val lowRes: Any? = song.thumbnailUrl ?: song.albumArtUri
    val highRes = song.highResThumbnailUrl?.takeIf { it != song.thumbnailUrl }
    Box(modifier = modifier) {
        AsyncImage(
            model = lowRes,
            contentDescription = contentDescription,
            modifier = Modifier.matchParentSize(),
            contentScale = contentScale
        )
        if (highRes != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(highRes)
                    .crossfade(300)
                    .build(),
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = contentScale
            )
        }
    }
}
