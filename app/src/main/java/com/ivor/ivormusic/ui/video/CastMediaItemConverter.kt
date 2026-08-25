package com.ivor.ivormusic.ui.video

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.cast.DefaultMediaItemConverter
import androidx.media3.cast.MediaItemConverter
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.MediaTrack
import com.google.android.gms.common.images.WebImage
import org.json.JSONObject

/**
 * Marker set as a [MediaItem]'s tag for a live broadcast. The converter reads
 * it to declare the load to the receiver as [MediaInfo.STREAM_TYPE_LIVE],
 * which the Default Receiver needs for the seek bar, the DVR window and the
 * "live edge" behaviour; [DefaultMediaItemConverter] hardcodes BUFFERED, which
 * makes every live stream present as a finite video on the TV.
 */
const val CAST_LIVE_TAG = "koda_live"

/**
 * Media3 [MediaItem] -> Cast [MediaQueueItem] conversion for Koda's loads.
 *
 * The stock [DefaultMediaItemConverter] covers metadata but hardcodes
 * STREAM_TYPE_BUFFERED and drops subtitle configurations entirely, so casting
 * through it would mean no live streams and no captions on the TV. This class
 * builds the [MediaInfo] itself and keeps two contracts:
 *
 * - **The round trip.** The customData JSON is written in exactly the shape
 *   [DefaultMediaItemConverter.getMediaItem] reads back, because CastPlayer's
 *   timeline tracker re-derives its [androidx.media3.common.Timeline] from the
 *   receiver's queue items through `toMediaItem`. A foreign customData shape
 *   would crash that path with a JSONException on every status update.
 * - **Captions ride the load.** The item's SubtitleConfigurations become
 *   TEXT-type MediaTracks. The Default Receiver renders WebVTT natively, so a
 *   track selected on the phone plays on the TV without any receiver-side work.
 */
@UnstableApi
class KodaCastMediaItemConverter : MediaItemConverter {

    private val fallback = DefaultMediaItemConverter()

    override fun toMediaItem(mediaQueueItem: MediaQueueItem): MediaItem =
        fallback.toMediaItem(mediaQueueItem)

    override fun toMediaQueueItem(mediaItem: MediaItem): MediaQueueItem {
        val local = requireNotNull(mediaItem.localConfiguration) {
            "Cast items must carry a local configuration"
        }
        val mime = requireNotNull(local.mimeType) {
            "Cast items must specify their mimeType"
        }
        val isLive = local.tag == CAST_LIVE_TAG

        val metadata = MediaMetadata(
            if (isLive) MediaMetadata.MEDIA_TYPE_TV_SHOW else MediaMetadata.MEDIA_TYPE_MOVIE
        )
        mediaItem.mediaMetadata.title?.let { metadata.putString(MediaMetadata.KEY_TITLE, it.toString()) }
        mediaItem.mediaMetadata.artist?.let { metadata.putString(MediaMetadata.KEY_ARTIST, it.toString()) }
        mediaItem.mediaMetadata.artworkUri?.let { metadata.addImage(WebImage(it)) }

        val builder = MediaInfo.Builder(
            // contentId doubles as the display id on the receiver side.
            if (mediaItem.mediaId != MediaItem.DEFAULT_MEDIA_ID) mediaItem.mediaId
            else local.uri.toString()
        )
            .setStreamType(
                if (isLive) MediaInfo.STREAM_TYPE_LIVE else MediaInfo.STREAM_TYPE_BUFFERED
            )
            .setContentType(mime)
            .setContentUrl(local.uri.toString())
            .setMetadata(metadata)
            .setCustomData(customData(mediaItem))

        // Captions: one TEXT track per SubtitleConfiguration. Koda only ever
        // sends the selected track (see VideoPlayerViewModel), so this stays a
        // single entry in practice; the list form costs nothing extra.
        val tracks = local.subtitles.mapIndexed { index, subtitle ->
            MediaTrack.Builder((index + 1).toLong(), MediaTrack.TYPE_TEXT)
                .setName(subtitle.label ?: "Subtitles")
                .setSubtype(MediaTrack.SUBTYPE_SUBTITLES)
                .setContentType(subtitle.mimeType ?: MimeTypes.TEXT_VTT)
                .setContentId(subtitle.uri.toString())
                .build()
        }
        if (tracks.isNotEmpty()) {
            @Suppress("VisibleForTests")
            builder.setMediaTracks(tracks)
        }

        return MediaQueueItem.Builder(builder.build()).build()
    }

    /**
     * The exact JSON [DefaultMediaItemConverter] expects in customData, so its
     * `toMediaItem` (which our round-trip delegates to) can rebuild a Media3
     * MediaItem from the receiver's status updates.
     */
    private fun customData(mediaItem: MediaItem): JSONObject {
        val local = checkNotNull(mediaItem.localConfiguration)
        val itemJson = JSONObject()
            .put("mediaId", mediaItem.mediaId)
            .put("uri", local.uri.toString())
            .put("mimeType", local.mimeType)
        return JSONObject().put("mediaItem", itemJson)
    }
}
