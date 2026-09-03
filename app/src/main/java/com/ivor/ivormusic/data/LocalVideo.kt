package com.ivor.ivormusic.data

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore

/**
 * A video file on the device, as MediaStore describes it.
 *
 * Deliberately not a [VideoItem]: nothing here has a YouTube id, a channel or
 * an engagement state, and the fields that matter for a gallery file - the
 * folder it sits in, its pixel size, how big it is on disk - have nowhere to
 * live on that model. The two only meet at the playback boundary, where
 * [toVideoItem] produces the shell the player's chrome renders from.
 */
data class LocalVideo(
    /** MediaStore's own row id. Stable for as long as the file is indexed. */
    val id: Long,
    val title: String,
    val uri: Uri,
    val durationMs: Long,
    val sizeBytes: Long,
    /** Epoch millis, or null when the provider had nothing rather than 1970. */
    val dateAddedMs: Long?,
    val width: Int,
    val height: Int,
    val mimeType: String?,
    /** MediaStore bucket id - the grouping key, since paths are not portable. */
    val bucketId: Long?,
    val folderName: String,
) {
    /**
     * The id this file plays under.
     *
     * Prefixed because it travels through fields typed for YouTube ids - the
     * queue, the player's current-video state, the widget snapshot - and a bare
     * MediaStore row id is a plausible-looking string that must never be
     * mistaken for one. Nothing may build a watch URL or an InnerTube request
     * from an id carrying this prefix; [isDeviceVideoId] is how a caller asks.
     */
    val playbackId: String get() = "$ID_PREFIX$id"

    /** "1080p", "2160p", or null when MediaStore did not record the size. */
    val resolutionLabel: String?
        get() = height.takeIf { it > 0 }?.let { "${it}p" }

    /** Longest edge over shortest, for the player's pre-first-frame layout. */
    val aspectRatio: Float?
        get() = if (width > 0 && height > 0) width.toFloat() / height else null

    fun toVideoItem(): VideoItem = VideoItem(
        videoId = playbackId,
        title = title,
        // The folder is the only provenance a device file has, and it is
        // genuinely useful - "Camera" and "WhatsApp Video" say different things
        // about what the file is - so it takes the byline the channel would.
        channelName = folderName,
        thumbnailUrl = null,
        duration = durationMs / 1000L,
        viewCount = "",
        publishedAtMs = dateAddedMs,
    )

    companion object {
        const val ID_PREFIX = "device:"

        /** True for ids minted by [playbackId]. */
        fun isDeviceVideoId(videoId: String?): Boolean =
            videoId?.startsWith(ID_PREFIX) == true

        /**
         * The content URI a [playbackId] addresses, or null if it is not one.
         *
         * The id is deliberately reversible: watch history stores a
         * [VideoItem] and nothing else, so replaying a device entry - and
         * drawing its frame - has to get back to the file from the id alone.
         * The row may no longer exist, which the resolver reports in its own
         * way; this only rebuilds the address.
         */
        fun uriFor(videoId: String?): Uri? {
            if (!isDeviceVideoId(videoId)) return null
            val rowId = videoId!!.removePrefix(ID_PREFIX).toLongOrNull() ?: return null
            return ContentUris.withAppendedId(
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
                rowId
            )
        }

        /**
         * The playback id for a MediaStore video URI, or null when the URI is
         * not one.
         *
         * An "open with" hand-off usually arrives as a `content://media/...`
         * URI, and one that resolves back to a MediaStore row is a device video
         * like any other - recordable in history, replayable later. A URI from
         * some other provider is a one-off grant that dies with the task, so it
         * gets no id and is deliberately not remembered.
         */
        fun playbackIdFor(uri: Uri): String? {
            if (uri.scheme != "content") return null
            if (uri.authority != MediaStore.AUTHORITY) return null
            val rowId = runCatching { ContentUris.parseId(uri) }.getOrNull() ?: return null
            if (rowId < 0) return null
            return "$ID_PREFIX$rowId"
        }
    }
}

/**
 * One MediaStore bucket - a directory holding videos - and enough to draw its
 * card without re-querying.
 */
data class LocalVideoFolder(
    val bucketId: Long?,
    val name: String,
    val videoCount: Int,
    /** Newest video in the folder, used as the card's cover frame. */
    val coverUri: Uri?,
    /** Newest date in the folder, so folders order by recent activity. */
    val newestDateMs: Long?,
)

/**
 * How a device video list is ordered. Persisted by name, so these constants
 * are frozen the same way every other stored enum in the app is.
 */
enum class LocalVideoSort {
    RECENT,
    NAME,
    DURATION,
    SIZE;

    companion object {
        fun fromName(name: String?): LocalVideoSort =
            entries.firstOrNull { it.name == name } ?: RECENT
    }
}
