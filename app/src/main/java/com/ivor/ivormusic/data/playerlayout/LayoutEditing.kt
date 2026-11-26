package com.ivor.ivormusic.data.playerlayout

/*
 * The edits the layout editor makes, as pure functions over a layout, plus
 * its undo history. Kept free of Compose so the JVM suite can hold them.
 */

fun PlayerLayout.withBlock(block: LayoutBlock): PlayerLayout =
    copy(blocks = blocks.map { if (it.id == block.id) block else it })

/** Adds [block] on top of everything else. */
fun PlayerLayout.plusBlock(block: LayoutBlock): PlayerLayout = copy(blocks = blocks + block)

fun PlayerLayout.minusBlock(id: String): PlayerLayout = copy(blocks = blocks.filterNot { it.id == id })

/**
 * A copy of the block [id] just above it in the stack, nudged down and right
 * by [nudge] (a fraction of the safe area's width) so it is visibly a second
 * one. Returns the layout and the copy's id, or null when there is no such block.
 */
fun PlayerLayout.duplicating(id: String, nudge: Float = 0.04f): Pair<PlayerLayout, String>? {
    val index = blocks.indexOfFirst { it.id == id }
    if (index < 0) return null
    val source = blocks[index]
    // The safe area is about twice as tall as it is wide, so half the nudge
    // down is about the same distance as the nudge across.
    val twin = source.copy(
        id = PlayerLayout.newId(),
        x = source.x + nudge,
        y = source.y + nudge / 2f,
    ).clampedToArea()
    val updated = blocks.toMutableList().also { it.add(index + 1, twin) }
    return copy(blocks = updated) to twin.id
}

/** Moves a block one step up (towards the viewer) or down the stack. */
fun PlayerLayout.restacked(id: String, up: Boolean): PlayerLayout {
    val index = blocks.indexOfFirst { it.id == id }
    val target = if (up) index + 1 else index - 1
    if (index < 0 || target !in blocks.indices) return this
    val updated = blocks.toMutableList()
    val moving = updated.removeAt(index)
    updated.add(target, moving)
    return copy(blocks = updated)
}

/**
 * Undo and redo for the editor: whole layouts, which are small and immutable,
 * so a step is one reference rather than a description of a change. One entry
 * per finished gesture or choice, never per frame of a drag.
 */
class LayoutHistory(private val limit: Int = 60) {
    private val undo = ArrayDeque<PlayerLayout>()
    private val redo = ArrayDeque<PlayerLayout>()

    val canUndo: Boolean get() = undo.isNotEmpty()
    val canRedo: Boolean get() = redo.isNotEmpty()

    /** Remembers [before] as the step to go back to; a new edit forgets what was undone. */
    fun record(before: PlayerLayout) {
        undo.addLast(before)
        while (undo.size > limit) undo.removeFirst()
        redo.clear()
    }

    fun undo(current: PlayerLayout): PlayerLayout? {
        val previous = undo.removeLastOrNull() ?: return null
        redo.addLast(current)
        return previous
    }

    fun redo(current: PlayerLayout): PlayerLayout? {
        val next = redo.removeLastOrNull() ?: return null
        undo.addLast(current)
        return next
    }
}
