package com.ivor.ivormusic.ui.playerlayout

import com.ivor.ivormusic.data.playerlayout.LayoutBlockKind
import com.ivor.ivormusic.data.playerlayout.LayoutSkin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Holds [drawsOwn] to the skins' own code. The style row offers a player for a
 * piece only when that player draws it itself, and the table saying so is
 * written by hand beside the editor; this reads each `XSkinBlock`'s
 * `when (block.kind)` from source, so a piece added to a player without the
 * table fails here instead of silently never being offered.
 */
class SkinOwnPiecesTest {

    private val playerSources = File("src/main/java/com/ivor/ivormusic/ui/player")

    /** Each skin's dispatcher: the file it lives in and its function name. */
    private val dispatchers = mapOf(
        LayoutSkin.CLASSIC to ("PlayerSheetContent.kt" to "ClassicSkinBlock"),
        LayoutSkin.CANVAS to ("PosterPlayerContent.kt" to "CanvasSkinBlock"),
        LayoutSkin.HERO to ("HeroPlayerContent.kt" to "HeroSkinBlock"),
        LayoutSkin.EDITORIAL to ("EditorialPlayerContent.kt" to "EditorialSkinBlock"),
        LayoutSkin.GESTURE to ("GesturePlayerContent.kt" to "GestureSkinBlock"),
        LayoutSkin.BENTO to ("BentoPlayerContent.kt" to "BentoSkinBlock"),
        LayoutSkin.STICKER to ("StickerPlayerContent.kt" to "StickerSkinBlock"),
        LayoutSkin.MORPH to ("MorphPlayerContent.kt" to "MorphSkinBlock"),
        LayoutSkin.DIAL to ("DialPlayerContent.kt" to "DialSkinBlock"),
    )

    /** A branch of the dispatcher's top-level `when`, eight spaces in. */
    private val branch = Regex("""^ {8}((?:LayoutBlockKind\.\w+\s*,\s*)*LayoutBlockKind\.\w+)\s*->""", RegexOption.MULTILINE)

    private fun kindsDrawnBy(file: String, function: String): Set<LayoutBlockKind> {
        val text = File(playerSources, file).readText().replace("\r\n", "\n")
        val start = text.indexOf("internal fun $function(")
        assertTrue("$function not found in $file", start >= 0)
        val end = text.indexOf("\n}\n", start).takeIf { it > 0 } ?: text.length
        return branch.findAll(text.substring(start, end))
            .flatMap { Regex("""LayoutBlockKind\.(\w+)""").findAll(it.groupValues[1]) }
            .map { LayoutBlockKind.valueOf(it.groupValues[1]) }
            .toSet()
    }

    @Test
    fun everySkinIsCheckedSoNoneSlipsPast() {
        assertEquals(LayoutSkin.entries.toSet() - LayoutSkin.KODA, dispatchers.keys)
    }

    @Test
    fun theOwnPieceTableMatchesEachSkinsCode() {
        dispatchers.forEach { (skin, where) ->
            val (file, function) = where
            val inCode = kindsDrawnBy(file, function)
            val inTable = LayoutBlockKind.entries.filter { skin.drawsOwn(it) }.toSet()
            assertEquals("$skin: drawsOwn() against $function", inCode, inTable)
        }
    }

    @Test
    fun kodaDrawsEveryPieceAndClaimsNoneAsAPlayers() {
        LayoutBlockKind.entries.forEach { kind ->
            assertTrue(kind.name, LayoutSkin.KODA in skinsFor(kind, LayoutSkin.KODA))
            assertTrue(kind.name, !LayoutSkin.KODA.drawsOwn(kind))
        }
    }

    @Test
    fun aNewBlockNeverLandsInALookTheRowDoesNotOffer() {
        LayoutBlockKind.entries.forEach { kind ->
            LayoutSkin.entries.forEach { preferred ->
                val picked = offeredSkinFor(kind, preferred)
                assertTrue("$kind from $preferred got $picked", picked in skinsFor(kind, LayoutSkin.KODA))
            }
        }
    }
}
