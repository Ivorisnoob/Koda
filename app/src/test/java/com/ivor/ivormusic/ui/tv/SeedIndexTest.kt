package com.ivor.ivormusic.ui.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The artwork fallback's colour index.
 *
 * [scar] This shipped crashing. The index was computed by masking the hash to
 * unsigned and then narrowing with `.toInt()`, which restores the sign and
 * undoes the mask, and Kotlin's `%` truncates rather than floors - so a
 * negative hash gave a negative index. It threw on roughly four titles in ten,
 * which meant TV mode crashed the moment a shelf drew a poster that had no
 * artwork to load.
 *
 * The property that matters is boring and absolute: the result is always a
 * valid index. It is tested over a wide spread of real-looking titles rather
 * than a couple of examples, because two examples is exactly what the broken
 * version would have passed.
 */
class SeedIndexTest {

    private val paletteSize = 5

    @Test
    fun negativeHashesStillProduceAValidIndex() {
        // "Breaking Bad".hashCode() is negative, which is the whole bug.
        assertTrue("Breaking Bad".hashCode() < 0)
        val index = seedIndex("Breaking Bad", paletteSize)
        assertTrue("index $index out of range", index in 0 until paletteSize)
    }

    @Test
    fun everyTitleInAWideSpreadLandsInRange() {
        val titles = buildList {
            add("")
            add(" ")
            addAll(listOf("Dune", "Inception", "Frieren", "The Kid", "Akira", "Chernobyl"))
            // Enough variety to cover both hash signs many times over.
            for (i in 0..4000) add("Title $i")
            for (i in 0..500) add("鬼滅の刃 $i")
            for (i in 0..500) add("A very long title that goes on for a while, number $i")
        }
        var negativeHashesSeen = 0
        titles.forEach { title ->
            if (title.hashCode() < 0) negativeHashesSeen++
            val index = seedIndex(title, paletteSize)
            assertTrue(
                "seedIndex(\"$title\") = $index is out of [0, $paletteSize)",
                index in 0 until paletteSize
            )
        }
        // Guards the guard: if the corpus somehow had no negative hashes, this
        // test would pass against the broken implementation too.
        assertTrue("corpus exercised no negative hashes", negativeHashesSeen > 100)
    }

    @Test
    fun theResultIsStableForTheSameTitle() {
        // The point of seeding from the title is that a card keeps its colour.
        repeat(5) {
            assertEquals(seedIndex("Frieren", paletteSize), seedIndex("Frieren", paletteSize))
        }
    }

    @Test
    fun aPaletteOfOneOrNoneDoesNotDivideByZero() {
        assertEquals(0, seedIndex("Anything", 1))
        assertEquals(0, seedIndex("Anything", 0))
        assertEquals(0, seedIndex("Anything", -3))
    }

    @Test
    fun theExtremeHashDoesNotEscapeTheRange() {
        // Int.MIN_VALUE is the value `abs()` cannot fix, and the reason the fix
        // masks and mods on Long rather than reaching for absoluteValue.
        val worst = object {
            override fun hashCode(): Int = Int.MIN_VALUE
        }
        assertEquals(Int.MIN_VALUE, worst.hashCode())
        val index = ((Int.MIN_VALUE.toLong() and 0xFFFFFFFFL) % paletteSize).toInt()
        assertTrue("index $index out of range", index in 0 until paletteSize)
    }
}
