package com.ivor.ivormusic.data

/**
 * How synced lyrics look and move: the snapshot the Customization -> Lyrics page
 * writes and `SyncedLyricsView` reads, through `ThemePreferences`.
 *
 * The defaults are Apple Music's lyrics: a heavy grotesque in plain white, the
 * lines either side dimmed and softly blurred with distance, a word-by-word fill
 * where the timing allows, three breathing dots through a long instrumental, and
 * the sung line resting a third of the way down.
 *
 * Every enum's [id] is stored, so ids are frozen once shipped (invariant 9);
 * the constant names and their order may change, the ids may not. An unknown or
 * missing id reads as the default.
 *
 * Alignment and romanized lines stay where they were stored before this page
 * existed (`lyricsCentered`, `lyricsRomanized`), so nobody's choice resets; the
 * page edits them alongside these.
 */
data class LyricsCustomization(
    val font: LyricsFont = LyricsFont.INTER,
    val weight: LyricsWeight = LyricsWeight.BOLD,
    val size: LyricsTextSize = LyricsTextSize.LARGE,
    val spacing: LyricsLineSpacing = LyricsLineSpacing.NORMAL,
    val highlight: LyricsHighlight = LyricsHighlight.PLAIN,
    val dim: LyricsDim = LyricsDim.MEDIUM,
    /** Lines away from the sung one go softly out of focus (Android 12 and newer). */
    val blur: Boolean = true,
    /** Letters rise as they are sung and long notes swell; off keeps the words still. */
    val wordMotion: Boolean = true,
    /** A soft light behind the sung line and held notes. */
    val glow: Boolean = true,
    val scroll: LyricsScroll = LyricsScroll.CASCADE,
    val rest: LyricsRestPosition = LyricsRestPosition.THIRD,
    /** Three dots that fill through a long instrumental gap. */
    val interludeDots: Boolean = true,
    /** Resting lines shrink a touch so the sung one stands forward. */
    val depthScale: Boolean = true,
    val uppercase: Boolean = false,
    /** A duet's second singer answers from the other side of the screen. */
    val duetSides: Boolean = true,
) {
    companion object {
        val Default = LyricsCustomization()

        /** What the lyrics looked like before this page: kept as a one-tap choice. */
        val Classic = LyricsCustomization(
            font = LyricsFont.MONTSERRAT,
            weight = LyricsWeight.HEAVY,
            size = LyricsTextSize.SMALL,
            highlight = LyricsHighlight.ACCENT,
            blur = false,
            depthScale = false,
            interludeDots = false,
        )
    }
}

enum class LyricsFont(val id: String) {
    /** Inter: the open grotesque closest to Apple's SF Pro. */
    INTER("inter"),
    /** Koda's own display face, what lyrics used before. */
    MONTSERRAT("montserrat"),
    /** The phone's own font. */
    SYSTEM("system"),
    /** Rounded and friendly: Nunito. */
    ROUNDED("rounded"),
    /** A book serif: Lora. */
    SERIF("serif");

    companion object {
        fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: INTER
    }
}

enum class LyricsWeight(val id: String, val value: Int) {
    MEDIUM("medium", 500),
    SEMIBOLD("semibold", 600),
    BOLD("bold", 700),
    HEAVY("heavy", 800);

    companion object {
        fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: BOLD
    }
}

/** Type size in sp, so it still follows the phone's font scale. */
enum class LyricsTextSize(val id: String, val sp: Float) {
    /** The size lyrics had before this page. */
    SMALL("small", 24f),
    MEDIUM("medium", 27f),
    LARGE("large", 30f),
    HUGE("huge", 35f);

    companion object {
        fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: LARGE
    }
}

/** The gap between one lyric line and the next, in dp. */
enum class LyricsLineSpacing(val id: String, val dp: Float) {
    TIGHT("tight", 14f),
    NORMAL("normal", 24f),
    AIRY("airy", 36f);

    companion object {
        fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: NORMAL
    }
}

enum class LyricsHighlight(val id: String) {
    /** The sung line in full white (or black on a light page), Apple's way. */
    PLAIN("plain"),
    /** The sung line in the theme's or the album's accent colour. */
    ACCENT("accent");

    companion object {
        fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: PLAIN
    }
}

/** How far the lines that are not being sung fade back. */
enum class LyricsDim(val id: String, val near: Float, val far: Float) {
    SOFT("soft", 0.6f, 0.45f),
    MEDIUM("medium", 0.42f, 0.3f),
    STRONG("strong", 0.28f, 0.16f);

    companion object {
        fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: MEDIUM
    }
}

enum class LyricsScroll(val id: String) {
    /** Each line follows a beat after the one above, as Apple Music moves. */
    CASCADE("cascade"),
    /** The whole list glides as one. */
    SMOOTH("smooth"),
    /** Jumps straight to the line. */
    INSTANT("instant");

    companion object {
        fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: CASCADE
    }
}

/** Where the sung line rests, as a fraction of the panel's height. */
enum class LyricsRestPosition(val id: String, val fraction: Float) {
    HIGH("high", 0.2f),
    THIRD("third", 0.32f),
    CENTRE("centre", 0.5f);

    companion object {
        fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: THIRD
    }
}
