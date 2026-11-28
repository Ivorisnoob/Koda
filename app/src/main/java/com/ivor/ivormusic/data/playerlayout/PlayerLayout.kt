package com.ivor.ivormusic.data.playerlayout

import java.util.UUID

/**
 * A player screen someone made themselves: a background and a list of blocks,
 * each placed freely.
 *
 * Positions and sizes are fractions of the player's safe area - the window
 * between the status bar and the navigation bar - so a layout made on one phone
 * lands in the same place on another, at another display size or font scale.
 * Blocks draw in list order, so the last one is on top.
 *
 * Every `id` on the enums below is stored and frozen: renaming one resets that
 * choice in every saved layout.
 */
data class PlayerLayout(
    val id: String,
    val name: String,
    val background: LayoutBackground = LayoutBackground.AMBIENT,
    val blocks: List<LayoutBlock> = emptyList(),
) {
    /** True when something on this screen plays and pauses. */
    val canPlayPause: Boolean
        get() = blocks.any {
            it.kind == LayoutBlockKind.TRANSPORT ||
                (it.kind == LayoutBlockKind.BUTTON && it.action == LayoutAction.PLAY_PAUSE) ||
                (it.kind == LayoutBlockKind.COVER && it.action == LayoutAction.PLAY_PAUSE)
        }

    companion object {
        fun newId(): String = UUID.randomUUID().toString()
    }
}

/** What fills the screen behind the blocks. */
enum class LayoutBackground(val id: String) {
    /** The soft colour field drawn from the cover that the built-in styles use. */
    AMBIENT("ambient"),
    /** The plain surface colour. */
    SOLID("solid"),
    /** The cover's own tone, as Hero's deck. */
    TONAL("tonal"),
    /** The cover itself, full screen, fading into the surface at its foot. */
    COVER("cover");

    companion object {
        fun fromId(id: String?): LayoutBackground = entries.firstOrNull { it.id == id } ?: AMBIENT
    }
}

/** The kinds of thing a layout can hold. */
enum class LayoutBlockKind(val id: String) {
    COVER("cover"),
    /** The song's title, with the artist under it. */
    TITLE("title"),
    /** The seek bar, with the times under it. */
    SCRUBBER("scrubber"),
    /** Previous, play and next as one group. */
    TRANSPORT("transport"),
    /** One button, doing whatever it was set to do. */
    BUTTON("button"),
    LYRICS("lyrics"),
    VISUALIZER("visualizer"),
    /** The song that plays next; a tap opens the queue. */
    UP_NEXT("up_next"),
    /** A plain coloured shape to group other blocks on. */
    PANEL("panel");

    /**
     * Whether this kind runs to the screen's edge when it touches the edge of
     * the safe area. A cover or a panel can sit under the status bar; a button
     * under the navigation bar could not be pressed.
     */
    val bleeds: Boolean get() = this == COVER || this == PANEL

    companion object {
        fun fromId(id: String?): LayoutBlockKind? = entries.firstOrNull { it.id == id }
    }
}

/** What a button, or a tap on the cover, does. */
enum class LayoutAction(val id: String) {
    NONE("none"),
    PLAY_PAUSE("play_pause"),
    PREVIOUS("previous"),
    NEXT("next"),
    SEEK_BACK("seek_back"),
    SEEK_FORWARD("seek_forward"),
    LIKE("like"),
    SHUFFLE("shuffle"),
    REPEAT("repeat"),
    QUEUE("queue"),
    LYRICS("lyrics"),
    SLEEP_TIMER("sleep_timer"),
    RADIO("radio"),
    STANDBY("standby"),
    OPTIONS("options"),
    COLLAPSE("collapse");

    companion object {
        fun fromId(id: String?, fallback: LayoutAction): LayoutAction =
            entries.firstOrNull { it.id == id } ?: fallback

        /** The actions a button can be given, in the order the picker shows them. */
        val forButtons: List<LayoutAction> = listOf(
            PLAY_PAUSE, PREVIOUS, NEXT, SEEK_BACK, SEEK_FORWARD,
            LIKE, SHUFFLE, REPEAT, QUEUE, LYRICS,
            SLEEP_TIMER, RADIO, STANDBY, OPTIONS, COLLAPSE
        )

        /** What a tap on the cover can do. Double tap stays the ten-second seek. */
        val forCover: List<LayoutAction> = listOf(NONE, PLAY_PAUSE, LYRICS, QUEUE, OPTIONS)
    }
}

/** How a button is filled. */
enum class ButtonLook(val id: String) {
    FILLED("filled"),
    TONAL("tonal"),
    OUTLINED("outlined"),
    PLAIN("plain");

    companion object {
        fun fromId(id: String?): ButtonLook = entries.firstOrNull { it.id == id } ?: TONAL
    }
}

/** Which of the scheme's colour families a block takes. Never a literal colour. */
enum class LayoutColor(val id: String) {
    PRIMARY("primary"),
    SECONDARY("secondary"),
    TERTIARY("tertiary"),
    NEUTRAL("neutral");

    companion object {
        fun fromId(id: String?): LayoutColor = entries.firstOrNull { it.id == id } ?: PRIMARY
    }
}

/**
 * The outline of a cover, a button or a panel. The Material shapes are drawn
 * as squares centred in the block; the rest follow the block's own size.
 */
enum class BlockShape(val id: String) {
    SQUARE("square"),
    ROUNDED("rounded"),
    ROUND("round"),
    COOKIE("cookie"),
    CLOVER("clover"),
    FLOWER("flower"),
    ARCH("arch"),
    SOFT_BURST("soft_burst"),
    GEM("gem"),
    SUNNY("sunny");

    /** A Material shape that only reads as itself in a square. */
    val isPolygon: Boolean get() = this !in setOf(SQUARE, ROUNDED, ROUND)

    companion object {
        fun fromId(id: String?, fallback: BlockShape): BlockShape =
            entries.firstOrNull { it.id == id } ?: fallback
    }
}

enum class LayoutTextAlign(val id: String) {
    START("start"),
    CENTER("center"),
    END("end");

    companion object {
        fun fromId(id: String?): LayoutTextAlign = entries.firstOrNull { it.id == id } ?: START
    }
}

/** How large the title is drawn; it still shrinks to fit its block. */
enum class LayoutTextSize(val id: String) {
    SMALL("small"),
    MEDIUM("medium"),
    LARGE("large"),
    HUGE("huge");

    companion object {
        fun fromId(id: String?): LayoutTextSize = entries.firstOrNull { it.id == id } ?: MEDIUM
    }
}

/**
 * One thing on a custom player, and where it is.
 *
 * Flat rather than one class per kind: each kind reads the fields that mean
 * something to it and ignores the rest, the editor offers only those, and a
 * saved layout keeps reading after a field is added because every field has a
 * default.
 */
data class LayoutBlock(
    val id: String,
    val kind: LayoutBlockKind,
    /** Left edge, as a fraction of the safe area's width. */
    val x: Float,
    /** Top edge, as a fraction of the safe area's height. */
    val y: Float,
    /** Width, as a fraction of the safe area's width. */
    val width: Float,
    /** Height, as a fraction of the safe area's height. */
    val height: Float,
    /** A button's job, or what a tap on the cover does. */
    val action: LayoutAction = defaultAction(kind),
    val look: ButtonLook = ButtonLook.TONAL,
    val color: LayoutColor = LayoutColor.PRIMARY,
    val shape: BlockShape = defaultShape(kind),
    val align: LayoutTextAlign = LayoutTextAlign.START,
    val textSize: LayoutTextSize = LayoutTextSize.MEDIUM,
    /** The artist under a title; the times under a seek bar. */
    val showDetail: Boolean = true,
    /** A cover that turns while playing; a seek bar that waves. */
    val motion: Boolean = kind == LayoutBlockKind.SCRUBBER,
    /** A cover that fades into the background at its foot. */
    val fade: Boolean = false,
    /** Swiping the cover or the title sideways changes the song. */
    val swipeToSkip: Boolean = true,
) {
    val right: Float get() = x + width
    val bottom: Float get() = y + height

    companion object {
        /** The smallest a block may be made, as a fraction of either side. */
        const val MIN_FRACTION = 0.04f

        fun defaultAction(kind: LayoutBlockKind): LayoutAction = when (kind) {
            LayoutBlockKind.BUTTON -> LayoutAction.PLAY_PAUSE
            LayoutBlockKind.COVER -> LayoutAction.PLAY_PAUSE
            LayoutBlockKind.UP_NEXT -> LayoutAction.QUEUE
            else -> LayoutAction.NONE
        }

        fun defaultShape(kind: LayoutBlockKind): BlockShape = when (kind) {
            LayoutBlockKind.COVER -> BlockShape.ROUNDED
            LayoutBlockKind.PANEL -> BlockShape.ROUNDED
            else -> BlockShape.ROUND
        }
    }
}

/** Keeps a block inside the safe area and no smaller than [LayoutBlock.MIN_FRACTION]. */
fun LayoutBlock.clampedToArea(): LayoutBlock {
    val w = width.coerceIn(LayoutBlock.MIN_FRACTION, 1f)
    val h = height.coerceIn(LayoutBlock.MIN_FRACTION, 1f)
    return copy(
        width = w,
        height = h,
        x = x.coerceIn(0f, 1f - w),
        y = y.coerceIn(0f, 1f - h),
    )
}
