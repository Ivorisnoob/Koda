package com.ivor.ivormusic.ui.video

import android.net.Uri
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

/** Which of Koda's two independent playback pipelines owns the Cast session. */
enum class CastPlaybackKind(val wireValue: String) {
    MUSIC("music"),
    VIDEO("video");

    companion object {
        fun fromWireValue(value: String?): CastPlaybackKind? =
            entries.firstOrNull { it.wireValue == value }
    }
}

const val CAST_PLAYBACK_KIND_KEY = "kodaPlaybackKind"

/**
 * Media3 [MediaItem] -> Cast [MediaQueueItem] conversion for Koda's loads.
 *
 * The stock [DefaultMediaItemConverter] covers metadata but hardcodes
 * STREAM_TYPE_BUFFERED and drops subtitle configurations entirely, so casting
 * through it would mean no live streams and no captions on the TV. This class
 * builds the [MediaInfo] itself and keeps two contracts:
 *
 * - **The round trip.** The customData JSON is written in exactly the shape
 *   [DefaultMediaItemConverter] reads back, because CastPlayer's timeline
 *   tracker re-derives its [androidx.media3.common.Timeline] from the
 *   receiver's queue items through `toMediaItem`. Incoming queue entries are
 *   guarded as well: the stock converter assumes they all came from itself,
 *   while real receivers can publish transient or foreign entries without
 *   MediaInfo/customData.
 * - **Captions ride the load.** The item's SubtitleConfigurations become
 *   TEXT-type MediaTracks. The Default Receiver renders WebVTT natively, so a
 *   track selected on the phone plays on the TV without any receiver-side work.
 */
@UnstableApi
class KodaCastMediaItemConverter(
    private val playbackKind: CastPlaybackKind = CastPlaybackKind.VIDEO
) : MediaItemConverter {

    private val fallback = DefaultMediaItemConverter()

    override fun toMediaItem(mediaQueueItem: MediaQueueItem): MediaItem {
        val mediaInfo = mediaQueueItem.media ?: return MediaItem.EMPTY

        // DefaultMediaItemConverter asserts that both MediaInfo
        // and its customData are non-null. Those assumptions do not hold for a
        // real Cast session: a receiver may briefly publish a queue entry with
        // no MediaInfo while replacing a load, and an item left by another
        // sender usually has no Media3 customData at all. CastPlayer calls this
        // method from its main-thread status callback, so either assertion
        // takes the whole app down instead of merely producing an incomplete
        // timeline item.
        //
        // Keep the stock round-trip for entries Koda produced, but only after
        // validating its envelope. A malformed/stale envelope and every
        // foreign item fall back to the public MediaInfo fields below.
        val itemJson = mediaInfo.customData?.optJSONObject("mediaItem")
        if (itemJson != null &&
            itemJson.nonBlankString("mediaId") != null &&
            itemJson.nonBlankString("uri") != null
        ) {
            runCatching { fallback.toMediaItem(mediaQueueItem) }
                .getOrNull()
                ?.let { return it }
        }

        return mediaInfo.toFallbackMediaItem()
    }

    /**
     * Rebuild enough of a foreign or partially-published queue item for
     * CastPlayer's timeline. This item is never sent back to the receiver as a
     * new load; it exists so status updates, media controls and disconnect can
     * remain alive until Koda installs its own fully-described item.
     */
    private fun MediaInfo.toFallbackMediaItem(): MediaItem {
        val castMetadata = metadata
        val metadataBuilder = androidx.media3.common.MediaMetadata.Builder()
        castMetadata?.getString(MediaMetadata.KEY_TITLE)
            ?.takeIf(String::isNotBlank)
            ?.let(metadataBuilder::setTitle)
        castMetadata?.getString(MediaMetadata.KEY_SUBTITLE)
            ?.takeIf(String::isNotBlank)
            ?.let(metadataBuilder::setSubtitle)
        castMetadata?.getString(MediaMetadata.KEY_ARTIST)
            ?.takeIf(String::isNotBlank)
            ?.let(metadataBuilder::setArtist)
        castMetadata?.getString(MediaMetadata.KEY_ALBUM_TITLE)
            ?.takeIf(String::isNotBlank)
            ?.let(metadataBuilder::setAlbumTitle)
        castMetadata?.images?.firstOrNull()?.url
            ?.let(metadataBuilder::setArtworkUri)

        val itemJson = customData?.optJSONObject("mediaItem")
        val uri = itemJson?.nonBlankString("uri")
            ?: contentUrl?.takeIf(String::isNotBlank)
        val id = itemJson?.nonBlankString("mediaId")
            ?: contentId?.takeIf(String::isNotBlank)
            ?: uri
            ?: MediaItem.DEFAULT_MEDIA_ID
        val mime = itemJson?.nonBlankString("mimeType")
            ?: contentType?.takeIf(String::isNotBlank)

        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(metadataBuilder.build())
            .apply {
                uri?.let { setUri(Uri.parse(it)) }
                mime?.let(::setMimeType)
            }
            .build()
    }

    private fun JSONObject.nonBlankString(key: String): String? =
        takeUnless { isNull(key) }
            ?.optString(key)
            ?.takeIf { it.isNotBlank() && it != "null" }

    override fun toMediaQueueItem(mediaItem: MediaItem): MediaQueueItem {
        val local = requireNotNull(mediaItem.localConfiguration) {
            "Cast items must carry a local configuration"
        }
        val mime = requireNotNull(local.mimeType) {
            "Cast items must specify their mimeType"
        }
        val isLive = local.tag == CAST_LIVE_TAG

        val metadata = MediaMetadata(
            when {
                playbackKind == CastPlaybackKind.MUSIC -> MediaMetadata.MEDIA_TYPE_MUSIC_TRACK
                isLive -> MediaMetadata.MEDIA_TYPE_TV_SHOW
                else -> MediaMetadata.MEDIA_TYPE_MOVIE
            }
        )
        mediaItem.mediaMetadata.title?.let { metadata.putString(MediaMetadata.KEY_TITLE, it.toString()) }
        mediaItem.mediaMetadata.artist?.let { metadata.putString(MediaMetadata.KEY_ARTIST, it.toString()) }
        mediaItem.mediaMetadata.albumTitle?.let {
            metadata.putString(MediaMetadata.KEY_ALBUM_TITLE, it.toString())
        }
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
                .apply { subtitle.language?.let(::setLanguage) }
                .build()
        }
        if (tracks.isNotEmpty()) {
            @Suppress("VisibleForTests")
            builder.setMediaTracks(tracks)
        }

        return MediaQueueItem.Builder(builder.build())
            // Declaring a text track and selecting it are separate Cast SDK
            // operations. Without activeTrackIds the Default Receiver may show
            // the track on one firmware revision and leave it disabled on
            // another, even though Koda's CC button says it is on.
            .apply {
                if (tracks.isNotEmpty()) {
                    setActiveTrackIds(tracks.map(MediaTrack::getId).toLongArray())
                }
            }
            .build()
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
            .put(CAST_PLAYBACK_KIND_KEY, playbackKind.wireValue)
    }
}
