package com.ivor.ivormusic.ui.video

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.ivor.ivormusic.R
import com.ivor.ivormusic.util.KLog

/**
 * Hand a device video to another app.
 *
 * **Koda does not delete files, and this is the reason it does not need to.**
 * Deleting from a media app means a scoped-storage write request, a system
 * confirmation, and a permanent loss the app is then responsible for; a file
 * manager or gallery already does that job, with the user's own trash and undo
 * behind it. So the one action offered here is the hand-off: the system chooser
 * over `ACTION_VIEW`, which lists every app that can open a video - the gallery,
 * the file manager, another player - and from there the file can be renamed,
 * moved or deleted where those operations belong.
 *
 * The read grant is passed along explicitly. A MediaStore URI is readable by any
 * app holding the media permission, but a URI Koda itself received through an
 * "open with" is not, and forwarding one without the flag hands the next app an
 * address it cannot open.
 *
 * Returns false when nothing on the device took the intent, so the caller can
 * say so rather than leaving a control that looks broken - the same contract
 * the updater's `openExternal` uses.
 */
fun openVideoWithExternalApp(
    context: Context,
    uri: Uri,
    mimeType: String? = null,
): Boolean {
    val view = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType?.takeIf { it.isNotBlank() } ?: "video/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(view, context.getString(R.string.dv_open_with))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    return try {
        context.startActivity(chooser)
        true
    } catch (e: ActivityNotFoundException) {
        KLog.w("DeviceVideoActions", "No app took the video view intent", e)
        Toast.makeText(context, R.string.dv_open_with_none, Toast.LENGTH_LONG).show()
        false
    }
}

/**
 * The URI carried by an intent that asked Koda to open a video, or null.
 *
 * Both shapes the manifest claims arrive here: an "open with" puts the file in
 * `data`, while a share puts it in `EXTRA_STREAM`. The mime type is checked
 * where the intent stated one, because the filters also match by extension and
 * a chooser can hand over something that is not video at all.
 */
fun Intent.externalVideoUri(): Uri? {
    val uri = when (action) {
        Intent.ACTION_VIEW -> data
        Intent.ACTION_SEND -> @Suppress("DEPRECATION") getParcelableExtra(Intent.EXTRA_STREAM)
        else -> null
    } ?: return null
    val declared = type
    if (declared != null && !declared.startsWith("video/") && declared != "application/octet-stream") {
        return null
    }
    return uri.takeIf { it.scheme == "content" || it.scheme == "file" }
}

/**
 * A video file another app asked Koda to open, waiting to be played.
 *
 * [token] distinguishes two hand-offs of the same file, which are otherwise
 * indistinguishable to the effect acting on them - opening the same clip twice
 * in a row must play it twice.
 */
data class PendingExternalVideo(val uri: android.net.Uri, val token: Long)

/**
 * Plays a video file handed in from outside the app.
 *
 * Separate from `SharedLinkHandler` because nothing here is resolved: there is
 * no id, no metadata and no network step, so the file starts immediately and
 * the only work is looking up a name to put on the player's chrome. That lookup
 * is allowed to fail - a provider is not obliged to report a display name, and
 * a video playing under its file name is better than one that waited for a
 * title it was never going to get.
 */
@androidx.compose.runtime.Composable
fun ExternalVideoHandler(
    pending: PendingExternalVideo?,
    enabled: Boolean,
    videoPlayerViewModel: com.ivor.ivormusic.ui.video.VideoPlayerViewModel,
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

        val title = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            displayNameOf(context, hand.uri)
        }
        // The video player is an overlay above the NavHost, so it can open over
        // anything - but a hand-off arriving while Settings is on top would
        // leave the user behind the player on a screen they never chose.
        onNavigateHome()
        videoPlayerViewModel.playExternalVideo(hand.uri, title)
    }
}

/** The provider's own name for a file, when it reports one. */
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
    KLog.w("DeviceVideoActions", "Could not read a display name for $uri", e)
    null
}
