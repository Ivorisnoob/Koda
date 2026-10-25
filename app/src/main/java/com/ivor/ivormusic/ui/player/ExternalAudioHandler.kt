package com.ivor.ivormusic.ui.player

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.data.SongSource
import com.ivor.ivormusic.data.UNKNOWN_ARTIST
import com.ivor.ivormusic.data.UNKNOWN_TITLE
import com.ivor.ivormusic.util.KLog

/**
 * The URI carried by an intent that asked Koda to open an audio file, or null.
 *
 * The audio counterpart of `externalVideoUri`, and checked after it: a file
 * whose sender declared no type, or `application/octet-stream`, is already
 * taken by the video path, which plays audio-only files as well. Here the
 * declared type must say audio, so a link or a video never lands in music.
 */
fun Intent.externalAudioUri(): Uri? {
    if (type?.startsWith("audio/") != true) return null
    val uri = when (action) {
        Intent.ACTION_VIEW -> data
        Intent.ACTION_SEND -> @Suppress("DEPRECATION") getParcelableExtra(Intent.EXTRA_STREAM)
        else -> null
    } ?: return null
    return uri.takeIf { it.scheme == "content" || it.scheme == "file" }
}

/**
 * An audio file another app asked Koda to open, waiting to be played.
 *
 * [token] tells two hand-offs of the same file apart, so opening the same song
 * twice in a row plays it twice.
 */
data class PendingExternalAudio(val uri: Uri, val token: Long)

/**
 * Plays an audio file handed in from outside the app through the music player.
 *
 * The file plays as a device song: [SongSource.LOCAL] with the URI as its
 * source, which `MusicService` already plays without resolving anything. The
 * tags are read first so the player and notification name the song rather than
 * the file; a file without tags plays under its file name.
 *
 * Music lives on one side of the video toggle, so the hand-off switches to
 * music mode first - the same switch the toggle uses, which pauses video
 * rather than dismantling it.
 */
@androidx.compose.runtime.Composable
fun ExternalAudioHandler(
    pending: PendingExternalAudio?,
    enabled: Boolean,
    playerViewModel: PlayerViewModel,
    onEnterMusicMode: () -> Unit,
    onNavigateHome: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lastHandledToken = androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf<Long?>(null)
    }

    androidx.compose.runtime.LaunchedEffect(pending?.token, enabled) {
        val hand = pending ?: return@LaunchedEffect
        if (!enabled || lastHandledToken.value == hand.token) return@LaunchedEffect
        lastHandledToken.value = hand.token

        val song = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            externalSong(context, hand.uri)
        }
        onNavigateHome()
        onEnterMusicMode()
        playerViewModel.playSong(song)
    }
}

/**
 * A [Song] for a file Koda was handed, from its own tags where it has them.
 *
 * A MediaStore audio URI keeps its row id, so a song opened from a file manager
 * is the same song as its row in the device library - likes and history land
 * on one entry. Anything else is keyed by its URI, which cannot collide with a
 * MediaStore id or a YouTube video id.
 */
private fun externalSong(context: Context, uri: Uri): Song {
    val mediaStoreId = uri.takeIf { it.authority == "media" }
        ?.lastPathSegment
        ?.toLongOrNull()
    var title: String? = null
    var artist: String? = null
    var album: String? = null
    var duration = 0L
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(context, uri)
        title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
        artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
        album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
        duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull() ?: 0L
    } catch (e: Exception) {
        // A provider that cannot be read for tags may still be playable; the
        // player reports the file itself if it is not.
        KLog.w("ExternalAudio", "Could not read tags for $uri", e)
    } finally {
        runCatching { retriever.release() }
    }
    return Song(
        id = mediaStoreId?.toString() ?: "external:$uri",
        title = title?.takeIf { it.isNotBlank() }
            ?: displayNameOf(context, uri)
            ?: uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.')
                ?.takeIf { it.isNotBlank() }
            ?: UNKNOWN_TITLE,
        artist = artist?.takeIf { it.isNotBlank() } ?: UNKNOWN_ARTIST,
        album = album.orEmpty(),
        duration = duration,
        uri = uri,
        source = SongSource.LOCAL
    )
}

/** The provider's own name for a file, without its extension, when it reports one. */
private fun displayNameOf(context: Context, uri: Uri): String? = try {
    context.contentResolver.query(
        uri,
        arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null
    )?.use { cursor ->
        if (cursor.moveToFirst()) {
            cursor.getString(0)?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
        } else null
    }
} catch (e: Exception) {
    KLog.w("ExternalAudio", "Could not read a display name for $uri", e)
    null
}
