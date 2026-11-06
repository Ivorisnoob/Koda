package com.ivor.ivormusic.data.lossless

import org.json.JSONObject
import java.util.Locale

/**
 * What a lossless download holds, kept beside it in the downloads metadata so
 * the Downloads screen can say so without opening the file. The JSON field
 * names are stored, so they are frozen.
 */
data class LosslessDownloadInfo(
    /** `flac` or `m4a`. */
    val container: String,
    val sampleRate: Int = 0,
    val bitsPerSample: Int = 0,
) {
    val isHiRes: Boolean get() = bitsPerSample > 16 || sampleRate > 48_000

    /** "24-bit · 96 kHz", or just the container when the header said nothing. */
    val detail: String
        get() = if (sampleRate > 0 && bitsPerSample > 0) {
            "$bitsPerSample-bit · ${formatKhz(sampleRate)}"
        } else container.uppercase(Locale.US)

    fun toJson(): JSONObject = JSONObject()
        .put("container", container)
        .put("sampleRate", sampleRate)
        .put("bits", bitsPerSample)

    companion object {
        fun of(file: LosslessAudioFile) = LosslessDownloadInfo(
            container = file.extension,
            sampleRate = file.info?.sampleRate ?: 0,
            bitsPerSample = file.info?.bitsPerSample ?: 0,
        )

        fun fromJson(json: JSONObject): LosslessDownloadInfo? {
            val container = json.optString("container").takeIf { it.isNotBlank() } ?: return null
            return LosslessDownloadInfo(container, json.optInt("sampleRate"), json.optInt("bits"))
        }

        /** 44100 -> "44.1 kHz", 96000 -> "96 kHz". */
        fun formatKhz(sampleRate: Int): String {
            val khz = sampleRate / 1000.0
            return if (khz % 1.0 == 0.0) "${khz.toInt()} kHz"
            else String.format(Locale.US, "%.1f kHz", khz)
        }
    }
}
