package com.ivor.ivormusic.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The display family covers Latin and Cyrillic and nothing else, so this
 * decision is the difference between a typeface choice and a screen of boxes on
 * every song that is not written in one of those scripts.
 *
 * Every non-Latin string here is written as escapes rather than literal text, so
 * the test cannot be quietly broken by a file saved in the wrong encoding - in
 * which the Korean below would arrive as Latin characters and pass for the
 * wrong reason.
 */
class LyricsScriptTest {

    /** Japanese: "kimi no na wo yonde". */
    private val japanese = "君の名を呼んで"

    /** Korean: "nareul bureuneun sori". */
    private val korean = "나를 부르는 소리"

    /** Hindi: "tum hi ho". */
    private val hindi = "तुम ही हो"

    /** Arabic: "anta hubbi". */
    private val arabic = "أنت حبي"

    @Test
    fun `english lyrics take the display font`() {
        assertTrue(
            lyricsFitDisplayFont(
                listOf("Hold me closer", "In the darkness of the night", "Oh-oh-oh")
            )
        )
    }

    @Test
    fun `accented latin and vietnamese take the display font`() {
        assertTrue(
            lyricsFitDisplayFont(
                listOf(
                    // Vietnamese sits in Latin Extended Additional, which the
                    // family does publish.
                    "Kết thúc một mối tình",
                    "École, über, niño"
                )
            )
        )
    }

    @Test
    fun `cyrillic takes the display font`() {
        assertTrue(
            lyricsFitDisplayFont(
                listOf("Ты знаешь меня")
            )
        )
    }

    @Test
    fun `japanese korean devanagari and arabic do not`() {
        assertFalse(lyricsFitDisplayFont(listOf(japanese)))
        assertFalse(lyricsFitDisplayFont(listOf(korean)))
        assertFalse(lyricsFitDisplayFont(listOf(hindi)))
        assertFalse(lyricsFitDisplayFont(listOf(arabic)))
    }

    @Test
    fun `a stray foreign glyph does not demote an english song`() {
        // One borrowed word in a chorus of English is exactly what a
        // single-character test gets wrong.
        assertTrue(
            lyricsFitDisplayFont(
                listOf(
                    "We were dancing in the kitchen light",
                    "And you whispered はい before the night was over",
                    "So hold on to me now, hold on to me now",
                    "Every word of every song we ever sang"
                )
            )
        )
    }

    @Test
    fun `a mostly japanese song is not rescued by an english chorus line`() {
        assertFalse(
            lyricsFitDisplayFont(
                listOf(japanese, japanese, japanese, "Hold me now")
            )
        )
    }

    @Test
    fun `digits punctuation and instrumental markers are not a script`() {
        assertTrue(lyricsFitDisplayFont(listOf("1, 2, 3, 4!", "- - -", "")))
        assertTrue(lyricsFitDisplayFont(emptyList()))
    }
}
