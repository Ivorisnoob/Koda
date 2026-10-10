package com.ivor.ivormusic.data.stream

import com.ivor.ivormusic.data.VideoQuality
import com.ivor.ivormusic.data.VideoSeekPreview
import com.ivor.ivormusic.data.VideoStreamResolutionCache
import com.ivor.ivormusic.data.VideoStreamResult
import com.ivor.ivormusic.data.YouTubeAudioTrack
import com.ivor.ivormusic.data.YouTubeAudioTrackKind
import com.ivor.ivormusic.data.deduplicateVideoQualityVariants
import com.ivor.ivormusic.data.parseDirectAudioTracks
import com.ivor.ivormusic.data.parseDirectVideoQualities
import com.ivor.ivormusic.data.parseStoryboardSeekPreview
import com.ivor.ivormusic.data.sortedForMenu
import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.StreamType

/**
 * The quality ladder of a video, a Short or a live stream.
 *
 * Three paths, tried in order and stopping at the first that yields formats:
 * one visionOS `/player` under Koda's own visitorData, NewPipe's own client
 * chain, then the ANDROID_VR and IOS clients as a last resort. The ladder is
 * shared across surfaces through [VideoStreamResolutionCache], so the player,
 * a preview and a download of the same video resolve it once.
 *
 * @param youtubeService NewPipe's YouTube service, for the fallback path.
 * @param onCaptions called with the visionOS response of a resolved video, so
 * its caption tracklist can be kept and a later CC tap costs nothing.
 * @param onResponse called with a last-resort response, which is harvested
 * whole.
 */
internal class VideoStreamResolver(
    private val playerSession: PlayerSession,
    private val youtubeService: StreamingService,
    private val onCaptions: (videoId: String, root: JSONObject) -> Unit,
    private val onResponse: (videoId: String, root: JSONObject) -> Unit,
) {
    /**
     * The quality ladder of a video, and nothing else: no related videos or
     * channel details, which come from one `/next` afterwards.
     */
    suspend fun getVideoStreamQualities(
        videoId: String,
        includeHdr: Boolean = false,
    ): List<VideoQuality> = getVideoStreamResult(videoId, includeHdr).qualities

    /**
     * Resolve the quality ladder and the storyboard harvested by that exact
     * extraction as one value. Callers that render a scrub preview must use
     * this API instead of trying to coordinate two independently mutable reads.
     */
    suspend fun getVideoStreamResult(
        videoId: String,
        includeHdr: Boolean = false,
    ): VideoStreamResult =
        VideoStreamResolutionCache.getOrResolve(videoId, includeHdr) {
            resolveVideoStreamResult(videoId, includeHdr)
        }

    /** Forget a failed ladder before retrying the same video. */
    fun invalidateVideoStreamResult(videoId: String) {
        VideoStreamResolutionCache.invalidate(videoId)
    }

    private suspend fun resolveVideoStreamResult(
        videoId: String,
        includeHdr: Boolean = false,
    ): VideoStreamResult = withContext(Dispatchers.IO) {
        // Primary: one visionOS /player under Koda's own visitorData. See
        // PlayerSession.visionOs for why this is not NewPipe any more. The
        // direct parser keeps HDR itags 330-337 that NewPipe v0.26.5's ItagItem
        // table drops, so HDR comes from this same response rather than from a
        // second request merged into NewPipe's ladder as it used to.
        val direct = playerSession.visionOs(videoId)
        direct.answer?.let { answer ->
            val qualities = parseQualitiesFromStreamingData(answer.streamingData, includeHdr)
            if (qualities.isNotEmpty()) {
                BotCheckVerdict.clearOnSuccess()
                // Makes a CC tap free: getCaptionTracks reads this cache first.
                onCaptions(videoId, answer.root)
                KLog.i(
                    "YouTubeRepo",
                    "Video qualities via visionOS: ${qualities.size} for $videoId" +
                        qualities.count(VideoQuality::isHdr).let { if (it > 0) " (HDR=$it)" else "" },
                )
                // Dubs ride the same response. Live has one soundtrack in its
                // HLS master, so only a VOD ladder offers a choice.
                val audioTracks = if (qualities.any(VideoQuality::isLive)) {
                    emptyList()
                } else {
                    parseDirectAudioTracks(answer.streamingData)
                }
                // Best-effort: a malformed spec costs the scrub preview, never
                // the stream.
                val seekPreview =
                    runCatching { parseStoryboardSeekPreview(answer.root) }.getOrNull()
                return@withContext VideoStreamResult(qualities, seekPreview, audioTracks)
            }
            KLog.w("YouTubeRepo", "visionOS answered with no usable formats for $videoId")
        }

        // Fallback: NewPipe's maintained Android reel + visionOS chain, with
        // identities of its own. It no longer carries the HDR augmentation - a
        // video that reaches this far is already a failure being covered, and
        // the augmentation was the same visionOS call that just failed.
        var newPipeBotChecked = false
        try {
            val extracted = getVideoStreamsFromNewPipe(videoId)
            if (extracted.qualities.isNotEmpty()) {
                BotCheckVerdict.clearOnSuccess()
                KLog.i(
                    "YouTubeRepo",
                    "Video qualities via NewPipe fallback: ${extracted.qualities.size} for $videoId",
                )
                return@withContext extracted
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            newPipeBotChecked = e.isNewPipeBotCheck()
            KLog.w(
                "YouTubeRepo",
                "NewPipe quality resolution failed, falling back to direct InnerTube",
                e,
            )
        }

        // Last resort: the ANDROID_VR -> IOS chain. Still useful for a
        // client-specific edge case, but never the normal VOD path: ANDROID_VR
        // URLs hit googlevideo's progressive byte ceiling on long videos even
        // though /player succeeds, so the source starts and then dies part-way.
        try {
            VideoStreamResult(
                getVideoQualitiesFromInnerTube(
                    videoId,
                    includeHdr,
                    newPipeBotChecked || direct.botChecked,
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            KLog.e("YouTubeRepo", "Error getting video stream qualities", e)
            VideoStreamResult(emptyList())
        }
    }

    /**
     * Resolve the full video quality ladder via InnerTube: ANDROID_VR first
     * (no PO token, unciphered URLs), IOS as fallback, with a one-shot
     * visitorData remint when the bot check flags the current token. Returns
     * an empty list when neither client yields usable streamingData.
     */
    private suspend fun getVideoQualitiesFromInnerTube(
        videoId: String,
        includeHdr: Boolean = false,
        newPipeBotChecked: Boolean = false,
    ): List<VideoQuality> {
        val streamingData = playerSession.nativeFallback(videoId, newPipeBotChecked) {
            onResponse(videoId, it)
        }?.streamingData ?: return emptyList()
        return parseQualitiesFromStreamingData(streamingData, includeHdr)
    }

    private fun parseQualitiesFromStreamingData(
        streamingData: org.json.JSONObject,
        includeHdr: Boolean = false,
    ): List<VideoQuality> = parseDirectVideoQualities(streamingData, includeHdr)

    /**
     * Resolve playable URLs through the maintained NewPipe client chain.
     *
     * Only actual URL streams are admitted. NewPipe can also expose generated
     * DASH manifest text through the same Stream model (`isUrl == false`); that
     * content is not a URI and handing it to Media3's progressive source fails
     * before the first frame.
     */
    private fun getVideoStreamsFromNewPipe(videoId: String): VideoStreamResult {
        val extractor = youtubeService.getStreamExtractor("https://www.youtube.com/watch?v=$videoId")
        extractor.fetchPage()

        // Storyboards ride the same extraction as the stream URLs. Prefer the
        // largest usable frameset so a fullscreen scrub preview stays sharp;
        // failure is best-effort and must never hold playback resolution up.
        val seekPreview = runCatching {
            extractor.frames
                .asSequence()
                .filter {
                    it.urls.isNotEmpty() && it.frameWidth > 0 && it.frameHeight > 0 &&
                        it.framesPerPageX > 0 && it.framesPerPageY > 0 &&
                        it.totalCount > 0 && it.durationPerFrame > 0
                }
                .maxByOrNull { it.frameWidth * it.frameHeight }
                ?.let {
                    VideoSeekPreview(
                        pageUrls = it.urls,
                        frameWidthPx = it.frameWidth,
                        frameHeightPx = it.frameHeight,
                        framesPerPageX = it.framesPerPageX,
                        framesPerPageY = it.framesPerPageY,
                        totalFrameCount = it.totalCount,
                        durationPerFrameMs = it.durationPerFrame,
                    )
                }
        }.getOrNull()

        val videoOnlyStreams = extractor.videoOnlyStreams
        val muxedStreams = extractor.videoStreams
        val isLiveStream = extractor.streamType == StreamType.LIVE_STREAM ||
            extractor.streamType == StreamType.AUDIO_LIVE_STREAM
        val sourceAspect = (videoOnlyStreams + muxedStreams)
            .filter { it.width > 0 && it.height > 0 }
            .maxByOrNull { it.height }
            ?.let { it.width.toFloat() / it.height.toFloat() }

        val extractedAudioStreams = extractor.audioStreams
        val hasAlternateAudioTracks = extractedAudioStreams.any {
            it.audioTrackType != null && it.audioTrackType != AudioTrackType.ORIGINAL
        }
        val qualities = mutableListOf<VideoQuality>()
        // Live progressive endpoints are unusable: a live broadcast is only
        // playable through its HLS master playlist, including its audio
        // rendition. Never let a live DASH URL win merely because NewPipe
        // happened to expose both manifest fields.
        val manifest = if (isLiveStream) {
            extractor.hlsUrl?.takeIf { it.isNotBlank() }?.let { "HLS" to it }
        } else {
            extractor.dashMpdUrl?.takeIf { it.isNotBlank() }?.let { "DASH" to it }
                ?: extractor.hlsUrl?.takeIf { it.isNotBlank() }?.let { "HLS" to it }
        }
        manifest?.let { (format, url) ->
            qualities.add(
                VideoQuality(
                    resolution = if (format == "HLS") "Auto (HLS)" else "Auto (Best)",
                    url = url,
                    format = format,
                    isDASH = true,
                    isLive = isLiveStream,
                    sourceAspectRatio = sourceAspect,
                )
            )
        }

        // Progressive live entries are segment endpoints, not complete files.
        if (isLiveStream) return VideoStreamResult(qualities)

        val bestAudio = originalAudioStreams(extractedAudioStreams)
            .asSequence()
            .filter { it.isUrl }
            // MP4 downloads are remuxed on-device. Prefer AAC/M4A over the
            // usually-higher-bitrate Opus stream, which MediaMuxer cannot put
            // into an MP4 container reliably.
            .maxWithOrNull(
                compareBy<AudioStream>(
                    {
                        if (it.format?.suffix.equals("m4a", ignoreCase = true) ||
                            it.codec?.contains("mp4a", ignoreCase = true) == true
                        ) 1 else 0
                    },
                    { it.averageBitrate },
                )
            )
        val hasOriginalAdaptivePair = bestAudio != null && videoOnlyStreams.any { it.isUrl }
        if (hasAlternateAudioTracks && hasOriginalAdaptivePair) {
            // A manifest or muxed stream lets its issuing YouTube client pick
            // the default language again. When alternate tracks exist and we
            // have a known-original separate stream, expose only that
            // deterministic path—even for the "Auto" quality choice.
            qualities.removeAll { it.isDASH }
        }
        if (bestAudio != null) {
            videoOnlyStreams.asSequence()
                .filter { it.isUrl }
                .mapNotNull { stream ->
                    stream.resolution?.takeIf { it.isNotBlank() }?.let { resolution ->
                        VideoQuality(
                            resolution = resolution,
                            url = stream.content,
                            format = stream.format?.suffix,
                            isDASH = false,
                            audioUrl = bestAudio.content,
                            sourceAspectRatio = sourceAspect,
                            codec = stream.codec,
                        )
                    }
                }
                .forEach(qualities::add)
        }

        if (!hasAlternateAudioTracks || !hasOriginalAdaptivePair) {
            muxedStreams.asSequence()
                .filter { it.isUrl }
                .mapNotNull { stream ->
                    stream.resolution?.takeIf { it.isNotBlank() }?.let { resolution ->
                        VideoQuality(
                            resolution = resolution,
                            url = stream.content,
                            format = stream.format?.suffix,
                            isDASH = false,
                            sourceAspectRatio = sourceAspect,
                            codec = stream.codec,
                        )
                    }
                }
                .forEach(qualities::add)
        }

        // NewPipe exposes several codecs and delivery types for the same
        // visible label. Collapse codec alternatives, but retain both a split
        // local-playback entry and a muxed download entry when both exist.
        return VideoStreamResult(
            deduplicateVideoQualityVariants(qualities),
            seekPreview,
            newPipeAudioTracks(extractedAudioStreams, hasOriginalAdaptivePair),
        )
    }

    /**
     * The same soundtrack menu as [parseDirectAudioTracks], built from NewPipe's
     * streams when the direct call failed. Offered only when the qualities are
     * split pairs, because the dub replaces a pair's audio half; a muxed or
     * manifest ladder has no half to replace. NewPipe folds machine dubs into
     * DUBBED, so this path cannot label them.
     */
    private fun newPipeAudioTracks(
        streams: List<AudioStream>,
        hasOriginalAdaptivePair: Boolean,
    ): List<YouTubeAudioTrack> {
        if (!hasOriginalAdaptivePair) return emptyList()
        val byTrack = streams
            .filter { it.isUrl && !it.audioTrackId.isNullOrBlank() }
            .groupBy { it.audioTrackId!! }
        if (byTrack.size < 2) return emptyList()
        return byTrack.mapNotNull { (id, group) ->
            val best = group.maxWithOrNull(
                compareBy<AudioStream>(
                    { if (it.codec?.contains("mp4a", ignoreCase = true) == true) 1 else 0 },
                    { it.averageBitrate },
                )
            ) ?: return@mapNotNull null
            YouTubeAudioTrack(
                id = id,
                displayName = best.audioTrackName?.takeIf { it.isNotBlank() } ?: id,
                languageTag = best.audioLocale?.toLanguageTag()
                    ?: id.substringBefore('.').takeIf { it.isNotBlank() },
                kind = when (best.audioTrackType) {
                    AudioTrackType.ORIGINAL -> YouTubeAudioTrackKind.ORIGINAL
                    AudioTrackType.DUBBED -> YouTubeAudioTrackKind.DUBBED
                    AudioTrackType.DESCRIPTIVE -> YouTubeAudioTrackKind.DESCRIPTIVE
                    AudioTrackType.SECONDARY -> YouTubeAudioTrackKind.SECONDARY
                    null -> YouTubeAudioTrackKind.UNKNOWN
                },
                url = best.content,
            )
        }.sortedForMenu()
    }
}
