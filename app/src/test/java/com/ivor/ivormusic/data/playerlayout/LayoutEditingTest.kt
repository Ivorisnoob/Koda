package com.ivor.ivormusic.data.playerlayout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutEditingTest {

    private fun block(id: String, x: Float = 0.1f) =
        LayoutBlock(id = id, kind = LayoutBlockKind.BUTTON, x = x, y = 0.1f, width = 0.2f, height = 0.1f)

    private val layout = PlayerLayout(id = "l", name = "L", blocks = listOf(block("a"), block("b"), block("c")))

    @Test
    fun duplicateSitsJustAboveItsSourceAndIsNudged() {
        val (updated, newId) = layout.duplicating("a")!!
        assertEquals(listOf("a", newId, "b", "c"), updated.blocks.map { it.id })
        val copy = updated.blocks[1]
        assertNotEquals("a", newId)
        assertTrue(copy.x > layout.blocks[0].x)
        assertEquals(LayoutBlockKind.BUTTON, copy.kind)
    }

    @Test
    fun duplicatingAMissingBlockIsNothing() {
        assertNull(layout.duplicating("zzz"))
    }

    @Test
    fun aDuplicateAtTheEdgeStaysOnScreen() {
        val edge = PlayerLayout(id = "l", name = "L", blocks = listOf(block("a", x = 0.8f)))
        val copy = edge.duplicating("a")!!.first.blocks[1]
        assertTrue(copy.right <= 1.0001f)
    }

    @Test
    fun restackingMovesOneStepAndStopsAtTheEnds() {
        assertEquals(listOf("b", "a", "c"), layout.restacked("a", up = true).blocks.map { it.id })
        assertEquals(listOf("a", "c", "b"), layout.restacked("c", up = false).blocks.map { it.id })
        assertSame(layout, layout.restacked("c", up = true))
        assertSame(layout, layout.restacked("a", up = false))
    }

    @Test
    fun removingAndReplacingKeepTheOrder() {
        assertEquals(listOf("a", "c"), layout.minusBlock("b").blocks.map { it.id })
        val moved = layout.withBlock(block("b", x = 0.5f))
        assertEquals(listOf("a", "b", "c"), moved.blocks.map { it.id })
        assertEquals(0.5f, moved.blocks[1].x, 0.0001f)
    }

    @Test
    fun historyUndoesAndRedoesWholeSteps() {
        val history = LayoutHistory()
        val one = layout
        val two = layout.minusBlock("a")
        val three = two.minusBlock("b")
        history.record(one)
        history.record(two)
        assertEquals(two, history.undo(three))
        assertEquals(one, history.undo(two))
        assertFalse(history.canUndo)
        assertEquals(two, history.redo(one))
        assertEquals(three, history.redo(two))
        assertFalse(history.canRedo)
    }

    @Test
    fun aNewEditForgetsWhatWasUndone() {
        val history = LayoutHistory()
        history.record(layout)
        history.undo(layout.minusBlock("a"))
        assertTrue(history.canRedo)
        history.record(layout)
        assertFalse(history.canRedo)
    }

    @Test
    fun historyKeepsOnlyItsLimit() {
        val history = LayoutHistory(limit = 2)
        repeat(5) { history.record(layout) }
        var steps = 0
        var current = layout
        while (true) {
            current = history.undo(current) ?: break
            steps++
        }
        assertEquals(2, steps)
    }
}
