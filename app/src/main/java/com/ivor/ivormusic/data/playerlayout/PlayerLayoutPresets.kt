package com.ivor.ivormusic.data.playerlayout

import com.ivor.ivormusic.data.PlayerStyle

/**
 * Layouts to start from: one for each built-in style, then two that are not
 * a style. Each is built from the same blocks anyone can place, so a preset is
 * a starting point to edit rather than a style of its own.
 *
 * The ones named after built-in styles are close to those styles, not copies:
 * the built-in styles are drawn by hand and do things a block cannot (Gesture's
 * carousel, Sticker's peel, Dial's ring).
 *
 * The [id]s are not stored; they name the preset in code and in tests.
 */
enum class LayoutPreset(val id: String, val style: PlayerStyle?) {
    CLASSIC("classic", PlayerStyle.CLASSIC),
    GESTURE("gesture", PlayerStyle.GESTURE),
    EDITORIAL("editorial", PlayerStyle.EDITORIAL),
    CANVAS("canvas", PlayerStyle.POSTER),
    BENTO("bento", PlayerStyle.BENTO),
    STICKER("sticker", PlayerStyle.STICKER),
    MORPH("morph", PlayerStyle.MORPH),
    DIAL("dial", PlayerStyle.DIAL),
    HERO("hero", PlayerStyle.HERO),
    LYRICS("lyrics", null),
    BLANK("blank", null);
}

object PlayerLayoutPresets {

    // Presets are drawn on a 360 x 720 safe area and stored as fractions of it.
    // A phone's safe area is a little taller for its width than that; covers
    // and Material shapes draw as squares inside their blocks, so they stay
    // round where a square was meant.
    private const val REF_W = 360f
    private const val REF_H = 720f

    /** The preset for a built-in style, for "start from this player". */
    fun forStyle(style: PlayerStyle): LayoutPreset =
        LayoutPreset.entries.firstOrNull { it.style == style } ?: LayoutPreset.CLASSIC

    /** A fresh layout from [preset], with new ids, called [name]. */
    fun build(preset: LayoutPreset, name: String): PlayerLayout {
        val blocks = when (preset) {
            LayoutPreset.CLASSIC -> classic()
            LayoutPreset.GESTURE -> gesture()
            LayoutPreset.EDITORIAL -> editorial()
            LayoutPreset.CANVAS -> canvas()
            LayoutPreset.BENTO -> bento()
            LayoutPreset.STICKER -> sticker()
            LayoutPreset.MORPH -> morph()
            LayoutPreset.DIAL -> dial()
            LayoutPreset.HERO -> hero()
            LayoutPreset.LYRICS -> lyrics()
            LayoutPreset.BLANK -> blank()
        }
        return PlayerLayout(
            id = PlayerLayout.newId(),
            name = name,
            background = backgroundFor(preset),
            blocks = blocks,
        )
    }

    /** Each player's own ground; the ones that are not a player use the ambient field. */
    fun backgroundFor(preset: LayoutPreset): LayoutBackground = when (preset) {
        LayoutPreset.HERO -> LayoutBackground.TONAL
        LayoutPreset.CANVAS -> LayoutBackground.CANVAS
        LayoutPreset.EDITORIAL -> LayoutBackground.CONTAINER
        LayoutPreset.BENTO, LayoutPreset.DIAL -> LayoutBackground.BOARD
        LayoutPreset.STICKER -> LayoutBackground.SPLIT
        else -> LayoutBackground.AMBIENT
    }

    private fun block(
        kind: LayoutBlockKind,
        x: Float, y: Float, w: Float, h: Float,
        configure: LayoutBlock.() -> LayoutBlock = { this },
    ): LayoutBlock = LayoutBlock(
        id = PlayerLayout.newId(),
        kind = kind,
        x = x / REF_W,
        y = y / REF_H,
        width = w / REF_W,
        height = h / REF_H,
    ).configure().clampedToArea()

    private fun button(
        action: LayoutAction,
        x: Float, y: Float, w: Float = 48f, h: Float = 48f,
        look: ButtonLook = ButtonLook.PLAIN,
        color: LayoutColor = LayoutColor.NEUTRAL,
        shape: BlockShape = BlockShape.ROUND,
    ) = block(LayoutBlockKind.BUTTON, x, y, w, h) {
        copy(action = action, look = look, color = color, shape = shape)
    }

    /** Collapse at the start of the top edge and the menu at its end. */
    private fun topBar(look: ButtonLook = ButtonLook.PLAIN) = listOf(
        button(LayoutAction.COLLAPSE, 12f, 4f, look = look),
        button(LayoutAction.OPTIONS, 300f, 4f, look = look),
    )

    /** Previous, a big play and next, centred on [y]. */
    private fun soloTransport(y: Float, playLook: ButtonLook = ButtonLook.FILLED, playShape: BlockShape = BlockShape.ROUND) =
        listOf(
            button(LayoutAction.PREVIOUS, 54f, y - 28f, 56f, 56f),
            button(
                LayoutAction.PLAY_PAUSE, 130f, y - 50f, 100f, 100f,
                look = playLook, color = LayoutColor.PRIMARY, shape = playShape
            ),
            button(LayoutAction.NEXT, 250f, y - 28f, 56f, 56f),
        )

    private fun classic() = topBar() + listOf(
        block(LayoutBlockKind.COVER, 28f, 72f, 304f, 304f),
        block(LayoutBlockKind.TITLE, 28f, 396f, 248f, 72f),
        button(LayoutAction.LIKE, 288f, 408f),
        block(LayoutBlockKind.SCRUBBER, 20f, 478f, 320f, 52f),
        block(LayoutBlockKind.TRANSPORT, 20f, 540f, 320f, 92f),
        button(LayoutAction.SHUFFLE, 28f, 652f),
        button(LayoutAction.LYRICS, 112f, 652f),
        button(LayoutAction.QUEUE, 200f, 652f),
        button(LayoutAction.REPEAT, 284f, 652f),
    )

    /** A big card to swipe, and little else in the way of it. */
    private fun gesture() = topBar() + listOf(
        block(LayoutBlockKind.COVER, 20f, 84f, 320f, 340f) { copy(shape = BlockShape.ROUNDED) },
        block(LayoutBlockKind.TITLE, 24f, 444f, 312f, 76f) { copy(align = LayoutTextAlign.CENTER) },
        block(LayoutBlockKind.SCRUBBER, 24f, 532f, 312f, 52f),
    ) + soloTransport(y = 636f, playLook = ButtonLook.TONAL)

    private fun editorial() = listOf(
        block(LayoutBlockKind.PANEL, 0f, 0f, 360f, 400f) { copy(color = LayoutColor.PRIMARY) },
        block(LayoutBlockKind.COVER, 60f, 64f, 240f, 240f) { copy(shape = BlockShape.FLOWER) },
    ) + topBar() + listOf(
        block(LayoutBlockKind.TITLE, 24f, 420f, 312f, 96f) { copy(textSize = LayoutTextSize.HUGE) },
        block(LayoutBlockKind.SCRUBBER, 20f, 528f, 320f, 52f),
        block(LayoutBlockKind.TRANSPORT, 20f, 592f, 320f, 88f) { copy(look = ButtonLook.FILLED) },
    )

    private fun canvas() = topBar() + listOf(
        block(LayoutBlockKind.TITLE, 24f, 440f, 264f, 96f) { copy(textSize = LayoutTextSize.HUGE) },
        button(LayoutAction.LIKE, 296f, 464f),
        block(LayoutBlockKind.SCRUBBER, 20f, 540f, 320f, 52f),
        block(LayoutBlockKind.TRANSPORT, 20f, 600f, 320f, 88f) {
            copy(look = ButtonLook.FILLED, color = LayoutColor.NEUTRAL)
        },
    )

    private fun bento() = topBar() + listOf(
        block(LayoutBlockKind.COVER, 16f, 64f, 212f, 212f),
        button(
            LayoutAction.LIKE, 236f, 64f, 108f, 102f,
            look = ButtonLook.TONAL, color = LayoutColor.SECONDARY, shape = BlockShape.ROUNDED
        ),
        button(
            LayoutAction.SHUFFLE, 236f, 174f, 108f, 102f,
            look = ButtonLook.TONAL, color = LayoutColor.SECONDARY, shape = BlockShape.ROUNDED
        ),
        block(LayoutBlockKind.TITLE, 16f, 288f, 328f, 72f) { copy(textSize = LayoutTextSize.LARGE) },
        block(LayoutBlockKind.SCRUBBER, 16f, 368f, 328f, 52f),
        button(
            LayoutAction.PREVIOUS, 16f, 432f, 104f, 104f,
            look = ButtonLook.TONAL, color = LayoutColor.PRIMARY, shape = BlockShape.ROUNDED
        ),
        button(
            LayoutAction.PLAY_PAUSE, 128f, 432f, 104f, 104f,
            look = ButtonLook.FILLED, color = LayoutColor.PRIMARY, shape = BlockShape.ROUNDED
        ),
        button(
            LayoutAction.NEXT, 240f, 432f, 104f, 104f,
            look = ButtonLook.TONAL, color = LayoutColor.PRIMARY, shape = BlockShape.ROUNDED
        ),
        button(
            LayoutAction.QUEUE, 16f, 544f, 160f, 72f,
            look = ButtonLook.TONAL, color = LayoutColor.TERTIARY, shape = BlockShape.ROUNDED
        ),
        button(
            LayoutAction.LYRICS, 184f, 544f, 160f, 72f,
            look = ButtonLook.TONAL, color = LayoutColor.TERTIARY, shape = BlockShape.ROUNDED
        ),
    )

    /** A die-cut cover and toy-like controls. */
    private fun sticker() = topBar() + listOf(
        block(LayoutBlockKind.COVER, 40f, 76f, 280f, 280f) { copy(shape = BlockShape.CLOVER) },
        block(LayoutBlockKind.TITLE, 24f, 380f, 312f, 96f) {
            copy(textSize = LayoutTextSize.HUGE, align = LayoutTextAlign.CENTER)
        },
        block(LayoutBlockKind.SCRUBBER, 24f, 488f, 312f, 52f),
        block(LayoutBlockKind.TRANSPORT, 24f, 556f, 312f, 96f) {
            copy(look = ButtonLook.FILLED, shape = BlockShape.COOKIE)
        },
        button(LayoutAction.LIKE, 24f, 668f, look = ButtonLook.TONAL, color = LayoutColor.TERTIARY),
        button(LayoutAction.QUEUE, 288f, 668f, look = ButtonLook.TONAL, color = LayoutColor.TERTIARY),
    )

    /** A living shape that turns while the song plays. */
    private fun morph() = topBar() + listOf(
        block(LayoutBlockKind.COVER, 50f, 96f, 260f, 260f) {
            copy(shape = BlockShape.SOFT_BURST, motion = true)
        },
        block(LayoutBlockKind.TITLE, 24f, 388f, 312f, 80f) { copy(align = LayoutTextAlign.CENTER) },
        block(LayoutBlockKind.SCRUBBER, 40f, 484f, 280f, 52f),
    ) + soloTransport(y = 600f)

    /** A record on a ring, with seek steps either side of play. */
    private fun dial() = topBar() + listOf(
        block(LayoutBlockKind.PANEL, 46f, 68f, 268f, 268f) {
            copy(shape = BlockShape.ROUND, color = LayoutColor.NEUTRAL)
        },
        block(LayoutBlockKind.COVER, 66f, 88f, 228f, 228f) {
            copy(shape = BlockShape.ROUND, motion = true)
        },
        block(LayoutBlockKind.TITLE, 24f, 356f, 312f, 72f) { copy(align = LayoutTextAlign.CENTER) },
        block(LayoutBlockKind.SCRUBBER, 40f, 440f, 280f, 52f),
        button(LayoutAction.SEEK_BACK, 40f, 528f, 64f, 64f),
        button(
            LayoutAction.PLAY_PAUSE, 128f, 508f, 104f, 104f,
            look = ButtonLook.FILLED, color = LayoutColor.PRIMARY
        ),
        button(LayoutAction.SEEK_FORWARD, 256f, 528f, 64f, 64f),
        button(LayoutAction.PREVIOUS, 96f, 636f),
        button(LayoutAction.NEXT, 216f, 636f),
    )

    private fun hero() = listOf(
        block(LayoutBlockKind.COVER, 0f, 0f, 360f, 440f) {
            copy(shape = BlockShape.SQUARE, fade = true)
        },
        button(LayoutAction.COLLAPSE, 12f, 4f, 44f, 44f, look = ButtonLook.TONAL),
        button(LayoutAction.LYRICS, 196f, 4f, 44f, 44f, look = ButtonLook.TONAL),
        button(LayoutAction.SLEEP_TIMER, 248f, 4f, 44f, 44f, look = ButtonLook.TONAL),
        button(LayoutAction.OPTIONS, 300f, 4f, 44f, 44f, look = ButtonLook.TONAL),
        block(LayoutBlockKind.TITLE, 24f, 352f, 252f, 80f) { copy(textSize = LayoutTextSize.LARGE) },
        button(LayoutAction.LIKE, 288f, 372f, look = ButtonLook.OUTLINED),
        block(LayoutBlockKind.SCRUBBER, 20f, 446f, 320f, 52f),
        button(
            LayoutAction.PLAY_PAUSE, 20f, 508f, 164f, 164f,
            look = ButtonLook.FILLED, color = LayoutColor.PRIMARY, shape = BlockShape.ROUNDED
        ),
        button(LayoutAction.PREVIOUS, 196f, 508f, 68f, 100f, look = ButtonLook.OUTLINED),
        button(LayoutAction.NEXT, 272f, 508f, 68f, 100f, look = ButtonLook.OUTLINED),
        button(LayoutAction.SHUFFLE, 196f, 618f, 44f, 54f, look = ButtonLook.TONAL),
        button(LayoutAction.REPEAT, 246f, 618f, 44f, 54f, look = ButtonLook.TONAL),
        button(LayoutAction.QUEUE, 296f, 618f, 44f, 54f, look = ButtonLook.TONAL),
    )

    private fun lyrics() = topBar() + listOf(
        block(LayoutBlockKind.COVER, 20f, 60f, 64f, 64f) { copy(swipeToSkip = false) },
        block(LayoutBlockKind.TITLE, 96f, 60f, 244f, 64f) { copy(textSize = LayoutTextSize.SMALL) },
        block(LayoutBlockKind.LYRICS, 0f, 136f, 360f, 400f),
        block(LayoutBlockKind.SCRUBBER, 20f, 548f, 320f, 52f),
        block(LayoutBlockKind.TRANSPORT, 20f, 608f, 320f, 88f),
    )

    private fun blank() = topBar() + listOf(
        block(LayoutBlockKind.TRANSPORT, 20f, 608f, 320f, 88f),
    )
}
