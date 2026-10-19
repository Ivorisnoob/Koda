package com.ivor.ivormusic.data

/**
 * A button the mini player can carry beside the song. The pill draws the
 * chosen ones in the order they were picked.
 *
 * The [id]s are stored and frozen.
 */
enum class MiniPlayerButton(val id: String) {
    PREVIOUS("previous"),
    PLAY_PAUSE("play_pause"),
    NEXT("next"),
    LIKE("like"),
    SHUFFLE("shuffle"),
    REPEAT("repeat"),
    CLOSE("close");

    companion object {
        fun fromId(id: String?): MiniPlayerButton? = entries.firstOrNull { it.id == id }
    }
}

/** What a tap on the mini player's cover does. The [id]s are stored and frozen. */
enum class MiniCoverTap(val id: String) {
    PLAY_PAUSE("play_pause"),
    OPEN_PLAYER("open");

    companion object {
        fun fromId(id: String?): MiniCoverTap = entries.firstOrNull { it.id == id } ?: PLAY_PAUSE
    }
}

/** How the playing cover moves in the mini player. The [id]s are stored and frozen. */
enum class MiniCoverMotion(val id: String) {
    SPIN_AND_MORPH("spin_morph"),
    SPIN("spin"),
    MORPH("morph"),
    STILL("still");

    val spins: Boolean get() = this == SPIN_AND_MORPH || this == SPIN
    val morphs: Boolean get() = this == SPIN_AND_MORPH || this == MORPH

    companion object {
        fun fromId(id: String?): MiniCoverMotion = entries.firstOrNull { it.id == id } ?: SPIN_AND_MORPH
    }
}

/**
 * When the mini player closes into the small bubble round its cover: as the
 * page scrolls (the default), never, or always. The [id]s are stored and frozen.
 */
enum class MiniPlayerShrink(val id: String) {
    ON_SCROLL("scroll"),
    NEVER("never"),
    ALWAYS("always");

    companion object {
        fun fromId(id: String?): MiniPlayerShrink = entries.firstOrNull { it.id == id } ?: ON_SCROLL
    }
}

/** What the mini player writes under the title. The [id]s are stored and frozen. */
enum class MiniSecondLine(val id: String) {
    ARTIST("artist"),
    ALBUM("album"),
    TIME_LEFT("time_left");

    companion object {
        fun fromId(id: String?): MiniSecondLine = entries.firstOrNull { it.id == id } ?: ARTIST
    }
}

/** What holding a finger on the mini player does. The [id]s are stored and frozen. */
enum class MiniLongPress(val id: String) {
    NOTHING("nothing"),
    OPTIONS("options"),
    LIKE("like");

    companion object {
        fun fromId(id: String?): MiniLongPress = entries.firstOrNull { it.id == id } ?: NOTHING
    }
}

/** What the mini player is painted in. The [id]s are stored and frozen. */
enum class MiniPlayerColor(val id: String) {
    ACCENT("accent"),
    NEUTRAL("neutral");

    companion object {
        fun fromId(id: String?): MiniPlayerColor = entries.firstOrNull { it.id == id } ?: ACCENT
    }
}

/**
 * Everything about the mini player a user can change, as one snapshot. The
 * defaults are the mini player as it shipped before any of this was a setting.
 */
data class MiniPlayerCustomization(
    /** In the order they are drawn, which is the order they were picked in. */
    val buttons: List<MiniPlayerButton> = DEFAULT_BUTTONS,
    val coverTap: MiniCoverTap = MiniCoverTap.PLAY_PAUSE,
    val coverMotion: MiniCoverMotion = MiniCoverMotion.SPIN_AND_MORPH,
    val swipeToSkip: Boolean = true,
    val swipeToDismiss: Boolean = true,
    val shrink: MiniPlayerShrink = MiniPlayerShrink.ON_SCROLL,
    val color: MiniPlayerColor = MiniPlayerColor.ACCENT,
    val secondLine: MiniSecondLine = MiniSecondLine.ARTIST,
    val longPress: MiniLongPress = MiniLongPress.NOTHING,
) {
    /** The chosen buttons in the order the pill draws them, never more than [MAX_BUTTONS]. */
    val orderedButtons: List<MiniPlayerButton>
        get() = buttons.distinct().take(MAX_BUTTONS)

    companion object {
        /** More than this and the title beside them has no room on a narrow phone. */
        const val MAX_BUTTONS = 3

        val DEFAULT_BUTTONS: List<MiniPlayerButton> =
            listOf(MiniPlayerButton.PLAY_PAUSE, MiniPlayerButton.NEXT)
    }
}
