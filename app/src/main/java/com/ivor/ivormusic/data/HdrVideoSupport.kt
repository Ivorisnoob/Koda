package com.ivor.ivormusic.data

import android.content.Context
import android.hardware.display.DisplayManager
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import android.view.Display
import java.util.concurrent.ConcurrentHashMap

/**
 * Device-side half of HDR selection.
 *
 * A display advertising HDR is not enough: YouTube's direct HDR renditions
 * are 10-bit VP9 Profile 2 (with room here for AV1/HEVC), and a panel can
 * advertise HDR while its decoder cannot sustain a particular size/rate.
 * Both checks are therefore applied to every rendition before it reaches a
 * player or quality sheet.
 */
class HdrVideoSupport(context: Context) {
    private val appContext = context.applicationContext
    private val decoderSupport = ConcurrentHashMap<DecoderKey, Boolean>()

    val supportedDynamicRanges: Set<VideoDynamicRange>
        get() {
            val display = appContext.getSystemService(DisplayManager::class.java)
                ?.getDisplay(Display.DEFAULT_DISPLAY)
                ?: return emptySet()
            val types = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                display.mode.supportedHdrTypes.toSet()
            } else {
                @Suppress("DEPRECATION")
                display.hdrCapabilities?.supportedHdrTypes?.toSet() ?: emptySet()
            }
            return buildSet {
                if (Display.HdrCapabilities.HDR_TYPE_HDR10 in types) {
                    add(VideoDynamicRange.HDR10)
                }
                if (Display.HdrCapabilities.HDR_TYPE_HLG in types) {
                    add(VideoDynamicRange.HLG)
                }
            }
        }

    val hasHdrDisplay: Boolean get() = supportedDynamicRanges.isNotEmpty()

    fun filterSupported(qualities: List<VideoQuality>): List<VideoQuality> {
        val displayRanges = supportedDynamicRanges
        if (displayRanges.isEmpty()) return qualities.filterNot(VideoQuality::isHdr)
        return qualities.filter { quality ->
            !quality.isHdr ||
                (quality.dynamicRange in displayRanges && decoderSupports(quality))
        }
    }

    private fun decoderSupports(quality: VideoQuality): Boolean {
        val mime = codecMimeType(quality.codec) ?: return false
        val width = quality.width.takeIf { it > 0 }
            ?: inferredWidth(quality.height.takeIf { it > 0 } ?: quality.resolutionHeight)
        val height = quality.height.takeIf { it > 0 } ?: quality.resolutionHeight
        if (width <= 0 || height <= 0) return false
        val frameRate = quality.frameRate.takeIf { it > 0 } ?: quality.resolutionFrameRate
        val key = DecoderKey(mime, width, height, frameRate, quality.dynamicRange)
        return decoderSupport.getOrPut(key) {
            val mediaFormat = MediaFormat.createVideoFormat(mime, width, height).apply {
                setInteger(MediaFormat.KEY_PROFILE, codecProfile(mime, quality.dynamicRange))
                setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT2020)
                setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_LIMITED)
                setInteger(
                    MediaFormat.KEY_COLOR_TRANSFER,
                    if (quality.dynamicRange == VideoDynamicRange.HLG) {
                        MediaFormat.COLOR_TRANSFER_HLG
                    } else {
                        MediaFormat.COLOR_TRANSFER_ST2084
                    }
                )
                if (frameRate > 0) setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            }

            runCatching {
                MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { codec ->
                    !codec.isEncoder && codec.isHardwareAccelerated &&
                        codec.supportedTypes.any { it.equals(mime, ignoreCase = true) } &&
                        runCatching {
                            codec.getCapabilitiesForType(mime).isFormatSupported(mediaFormat)
                        }.getOrDefault(false)
                }
            }.getOrDefault(false)
        }
    }

    private fun codecProfile(mime: String, range: VideoDynamicRange): Int = when (mime) {
        MediaFormat.MIMETYPE_VIDEO_VP9 -> MediaCodecInfo.CodecProfileLevel.VP9Profile2HDR
        MediaFormat.MIMETYPE_VIDEO_AV1 -> MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10
        MediaFormat.MIMETYPE_VIDEO_HEVC -> if (range == VideoDynamicRange.HDR10) {
            MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10
        } else {
            MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10
        }
        else -> error("Unsupported HDR codec $mime")
    }

    private fun codecMimeType(codec: String?): String? = when {
        codec == null -> null
        codec.startsWith("vp9", ignoreCase = true) ||
            codec.startsWith("vp09", ignoreCase = true) -> MediaFormat.MIMETYPE_VIDEO_VP9
        codec.startsWith("av01", ignoreCase = true) -> MediaFormat.MIMETYPE_VIDEO_AV1
        codec.startsWith("hev1", ignoreCase = true) ||
            codec.startsWith("hvc1", ignoreCase = true) -> MediaFormat.MIMETYPE_VIDEO_HEVC
        else -> null
    }

    private fun inferredWidth(height: Int): Int = if (height > 0) height * 16 / 9 else 0

    private data class DecoderKey(
        val mime: String,
        val width: Int,
        val height: Int,
        val frameRate: Int,
        val dynamicRange: VideoDynamicRange,
    )
}

internal fun youtubeVideoDynamicRange(
    qualityLabel: String,
    transferCharacteristics: String?,
): VideoDynamicRange {
    val transfer = transferCharacteristics.orEmpty().uppercase()
    return when {
        transfer.contains("ARIB_STD_B67") || transfer.contains("HLG") ->
            VideoDynamicRange.HLG
        transfer.contains("2084") || qualityLabel.contains("HDR", ignoreCase = true) ->
            VideoDynamicRange.HDR10
        else -> VideoDynamicRange.SDR
    }
}

internal fun normalizedVideoQualityLabel(label: String): String = label
    .replace(Regex("""\s+HDR(?:10\+?)?\b.*$""", RegexOption.IGNORE_CASE), "")
    .trim()

internal val VideoQuality.resolutionHeight: Int
    get() = resolution.takeWhile(Char::isDigit).toIntOrNull() ?: 0

internal val VideoQuality.resolutionFrameRate: Int
    get() = resolution.substringAfter('p', "").takeWhile(Char::isDigit).toIntOrNull() ?: 30

/** Closest safe rendition after an HDR source or decoder failure. */
internal fun bestSdrFallback(
    qualities: List<VideoQuality>,
    failed: VideoQuality,
): VideoQuality? {
    val sdr = qualities.filter { !it.isHdr && !it.isLive }
    return sdr.firstOrNull { it.resolution == failed.resolution }
        ?: sdr.firstOrNull { it.resolutionHeight == failed.resolutionHeight }
        ?: sdr.filter { it.resolutionHeight in 1..failed.resolutionHeight }
            .maxByOrNull(VideoQuality::resolutionHeight)
        ?: sdr.filter { it.resolutionHeight > 0 }.minByOrNull(VideoQuality::resolutionHeight)
        ?: sdr.firstOrNull()
}
