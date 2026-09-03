package com.ivor.ivormusic.data

import java.util.Locale

/**
 * One selectable track inside the media the player is already reading.
 *
 * Distinct from [CaptionTrack], which addresses a subtitle file fetched over
 * the network and rendered by Koda's own overlay. These are tracks the
 * container carries, so selecting one is a track-selection override rather
 * than a fetch, and the id is only meaningful against the Tracks snapshot it
 * was built from.
 */
data class PlayerTrackOption(
    /** "groupIndex:trackIndex" in the snapshot that produced this option. */
    val id: String,
    val label: String,
    /** Codec, channel count, bitrate - the second line of the row. */
    val detail: String? = null,
    /** The track's declared language, kept so a choice can carry to the next file. */
    val language: String? = null,
    val isSelected: Boolean = false,
)

/**
 * The human name for a BCP-47 or ISO-639 language code.
 *
 * Returns null rather than the code itself when the platform does not know it,
 * so the caller can fall back to a positional name ("Audio track 2") instead of
 * showing a row labelled "qaa".
 */
fun languageDisplayName(code: String?): String? {
    val normalized = code?.trim()?.takeIf { it.isNotBlank() } ?: return null
    // Media containers use these for "no particular language". Naming them
    // after their code would be worse than falling through to a positional
    // label, which at least tells the user which row they are on.
    if (normalized.equals("und", true) ||
        normalized.equals("mis", true) ||
        normalized.equals("mul", true) ||
        normalized.equals("zxx", true) ||
        normalized.equals("qaa", true)
    ) {
        return null
    }
    val locale = Locale.forLanguageTag(normalized.replace('_', '-'))
    val display = locale.getDisplayLanguage(Locale.getDefault()).takeIf { it.isNotBlank() }
        ?: return null
    // getDisplayLanguage echoes the input when it does not recognise it, which
    // would put "qbc" in the menu as though it were a language name.
    if (display.equals(normalized, ignoreCase = true) && normalized.length <= 3) return null

    val region = locale.getDisplayCountry(Locale.getDefault()).takeIf { it.isNotBlank() }
    return if (region != null) "$display ($region)" else display
}

/**
 * The name for one audio track.
 *
 * A container's own track title wins when it has one - "Director's commentary"
 * is worth more than "English" - then the language, then the position. The
 * fallback matters: multi-audio rips routinely tag nothing at all, and three
 * rows all reading "Audio" would make the menu useless.
 */
fun audioTrackLabel(
    trackTitle: String?,
    language: String?,
    index: Int,
    isDefault: Boolean = false,
): String {
    val named = trackTitle?.trim()?.takeIf { it.isNotBlank() }
    val languageName = languageDisplayName(language)
    val base = when {
        named != null && languageName != null && !named.contains(languageName, true) ->
            "$languageName - $named"
        named != null -> named
        languageName != null -> languageName
        isDefault -> "Default"
        else -> "Audio track ${index + 1}"
    }
    return base
}

/**
 * The second line of an audio row: what actually differs between two tracks of
 * the same language, which is the case people are choosing between.
 */
fun audioTrackDetail(codec: String?, channelCount: Int, bitrate: Int): String? {
    val parts = buildList {
        codecDisplayName(codec)?.let { add(it) }
        channelLayoutName(channelCount)?.let { add(it) }
        if (bitrate > 0) add("${bitrate / 1000} kbps")
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" - ")
}

/**
 * The name for one embedded subtitle track. Forced and hearing-impaired tracks
 * are marked, because a forced track shows only foreign-dialogue lines and
 * picking it expecting full subtitles looks like the file is broken.
 */
fun textTrackLabel(
    trackTitle: String?,
    language: String?,
    index: Int,
    isForced: Boolean = false,
    isHearingImpaired: Boolean = false,
): String {
    val named = trackTitle?.trim()?.takeIf { it.isNotBlank() }
    val languageName = languageDisplayName(language)
    val base = when {
        named != null && languageName != null && !named.contains(languageName, true) ->
            "$languageName - $named"
        named != null -> named
        languageName != null -> languageName
        else -> "Subtitle track ${index + 1}"
    }
    val suffixes = buildList {
        if (isForced) add("forced")
        if (isHearingImpaired) add("SDH")
    }
    return if (suffixes.isEmpty()) base else "$base (${suffixes.joinToString(", ")})"
}

/** "2160p60", "1080p" - the label a device file's video track plays under. */
fun localVideoQualityLabel(height: Int, frameRate: Float): String {
    val resolution = if (height > 0) "${height}p" else "Video"
    // Only frame rates meaningfully above 30 are worth naming; 29.97 and 30
    // both mean "normal" to a viewer, and rounding 59.94 up to 60 is what every
    // other player shows.
    val rounded = Math.round(frameRate)
    return if (height > 0 && rounded >= 50) "$resolution$rounded" else resolution
}

/** "5.1", "Stereo", "Mono" - null when the container did not say. */
private fun channelLayoutName(channelCount: Int): String? = when {
    channelCount <= 0 -> null
    channelCount == 1 -> "Mono"
    channelCount == 2 -> "Stereo"
    channelCount == 6 -> "5.1"
    channelCount == 8 -> "7.1"
    else -> "$channelCount ch"
}

/**
 * A codec MIME type as people recognise it. Unknown types return their subtype
 * rather than null, since "audio/x-whatever" still tells the user these two
 * rows differ.
 */
private fun codecDisplayName(mimeType: String?): String? {
    val mime = mimeType?.lowercase()?.takeIf { it.isNotBlank() } ?: return null
    return when {
        mime.contains("eac3") || mime.contains("e-ac3") -> "E-AC-3"
        mime.contains("ac3") -> "AC-3"
        mime.contains("dts") -> "DTS"
        mime.contains("truehd") -> "TrueHD"
        mime.contains("opus") -> "Opus"
        mime.contains("vorbis") -> "Vorbis"
        mime.contains("flac") -> "FLAC"
        mime.contains("mp4a") || mime.contains("aac") -> "AAC"
        mime.contains("mpeg") -> "MP3"
        mime.contains("raw") || mime.contains("pcm") -> "PCM"
        else -> mime.substringAfter('/').takeIf { it.isNotBlank() }?.uppercase()
    }
}
