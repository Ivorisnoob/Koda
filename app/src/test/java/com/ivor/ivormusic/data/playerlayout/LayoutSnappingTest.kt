package com.ivor.ivormusic.data.playerlayout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutSnappingTest {

    private val w = 400f
    private val h = 800f
    private val threshold = 10f

    @Test
    fun aBlockNearTheCentreLineSnapsOntoIt() {
        // centre at 196, four short of the area's centre
        val result = LayoutSnapping.move(SnapRect(146f, 300f, 246f, 400f), emptyList(), w, h, threshold)
        assertEquals(200f, result.rect.centerX, 0.001f)
        assertTrue(200f in result.guidesX)
    }

    @Test
    fun aBlockFarFromEveryLineStaysWhereItWasPut() {
        val proposed = SnapRect(60f, 300f, 130f, 370f)
        val result = LayoutSnapping.move(proposed, emptyList(), w, h, threshold)
        assertEquals(proposed, result.rect)
        assertTrue(result.guidesX.isEmpty() && result.guidesY.isEmpty())
    }

    @Test
    fun itSnapsToAnotherBlocksEdgeOnBothAxes() {
        val other = SnapRect(100f, 100f, 200f, 200f)
        // left 6 past the other's right, top 7 below the other's top
        val result = LayoutSnapping.move(SnapRect(206f, 107f, 256f, 157f), listOf(other), w, h, threshold)
        assertEquals(200f, result.rect.left, 0.001f)
        assertEquals(100f, result.rect.top, 0.001f)
    }

    @Test
    fun theNearestLineWins() {
        val other = SnapRect(0f, 0f, 103f, 50f)
        // left at 98: the other's right (103) is 5 away, nothing else is near
        val result = LayoutSnapping.move(SnapRect(98f, 300f, 148f, 350f), listOf(other), w, h, threshold)
        assertEquals(103f, result.rect.left, 0.001f)
    }

    @Test
    fun aBlockDraggedPastTheEdgeIsKeptInside() {
        val result = LayoutSnapping.move(SnapRect(-80f, 760f, 20f, 860f), emptyList(), w, h, threshold)
        assertEquals(0f, result.rect.left, 0.001f)
        assertEquals(h, result.rect.bottom, 0.001f)
        assertEquals(100f, result.rect.width, 0.001f)
    }

    @Test
    fun resizingSnapsOnlyTheMovingEdges() {
        val other = SnapRect(0f, 0f, 300f, 50f)
        // dragging the right edge to 296: snaps to 300; left stays at 4 even
        // though 0 is within reach
        val result = LayoutSnapping.resize(
            SnapRect(4f, 100f, 296f, 200f), SnapEdges(right = true), listOf(other), w, h, threshold, 24f
        )
        assertEquals(4f, result.rect.left, 0.001f)
        assertEquals(300f, result.rect.right, 0.001f)
    }

    @Test
    fun resizingNeverGoesBelowTheMinimumAndHoldsTheFixedEdge() {
        val result = LayoutSnapping.resize(
            SnapRect(100f, 100f, 90f, 200f), SnapEdges(right = true), emptyList(), w, h, threshold, 24f
        )
        assertEquals(100f, result.rect.left, 0.001f)
        assertEquals(124f, result.rect.right, 0.001f)
    }

    @Test
    fun resizingFromTheTopLeftStaysInsideTheArea() {
        val result = LayoutSnapping.resize(
            SnapRect(-30f, -40f, 100f, 100f), SnapEdges(left = true, top = true), emptyList(), w, h, threshold, 24f
        )
        assertEquals(0f, result.rect.left, 0.001f)
        assertEquals(0f, result.rect.top, 0.001f)
    }
}
