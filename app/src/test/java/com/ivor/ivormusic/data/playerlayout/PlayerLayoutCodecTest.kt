package com.ivor.ivormusic.data.playerlayout

import com.ivor.ivormusic.data.PlayerStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerLayoutCodecTest {

    @Test
    fun everyPresetSurvivesARoundTrip() {
        LayoutPreset.entries.forEach { preset ->
            val layout = PlayerLayoutPresets.build(preset, preset.id)
            val decoded = PlayerLayoutCodec.decodeOne(PlayerLayoutCodec.encodeOne(layout))
            assertEquals(preset.id, layout, decoded)
        }
    }

    @Test
    fun severalLayoutsKeepTheirOrder() {
        val layouts = listOf(
            PlayerLayoutPresets.build(LayoutPreset.HERO, "One"),
            PlayerLayoutPresets.build(LayoutPreset.LYRICS, "Two"),
        )
        assertEquals(layouts, PlayerLayoutCodec.decodeAll(PlayerLayoutCodec.encodeAll(layouts)))
    }

    @Test
    fun everyPresetFitsInsideTheSafeArea() {
        LayoutPreset.entries.forEach { preset ->
            PlayerLayoutPresets.build(preset, preset.id).blocks.forEach { block ->
                assertTrue("${preset.id} ${block.kind}", block.x >= 0f && block.right <= 1.0001f)
                assertTrue("${preset.id} ${block.kind}", block.y >= 0f && block.bottom <= 1.0001f)
            }
        }
    }

    @Test
    fun everyPresetCanPlayAndPause() {
        LayoutPreset.entries.forEach { preset ->
            assertTrue(preset.id, PlayerLayoutPresets.build(preset, preset.id).canPlayPause)
        }
    }

    @Test
    fun everyBuiltInStyleHasAPresetOfItsOwnToStartFrom() {
        val builtIn = PlayerStyle.entries.filter { it != PlayerStyle.CUSTOM }
        val presets = builtIn.map { PlayerLayoutPresets.forStyle(it) }
        assertEquals(builtIn, presets.map { it.style })
        assertEquals(builtIn.size, presets.distinct().size)
    }

    @Test
    fun anUnknownBlockKindIsDroppedAndTheRestKept() {
        val json = """
            [{"v":1,"id":"a","name":"Mine","bg":"solid","blocks":[
              {"id":"1","kind":"hologram","x":0.1,"y":0.1,"w":0.2,"h":0.2},
              {"id":"2","kind":"cover","x":0.1,"y":0.1,"w":0.5,"h":0.3}
            ]}]
        """.trimIndent()
        val layout = PlayerLayoutCodec.decodeAll(json).single()
        assertEquals(LayoutBackground.SOLID, layout.background)
        assertEquals(listOf(LayoutBlockKind.COVER), layout.blocks.map { it.kind })
    }

    @Test
    fun missingFieldsTakeTheirDefaultsAndUnknownIdsFallBack() {
        val json = """
            {"id":"a","name":"Mine","bg":"plasma","blocks":[
              {"id":"1","kind":"button","x":0.1,"y":0.1,"w":0.2,"h":0.2,"look":"neon"}
            ]}
        """.trimIndent()
        val layout = PlayerLayoutCodec.decodeOne(json)!!
        val button = layout.blocks.single()
        assertEquals(LayoutBackground.AMBIENT, layout.background)
        assertEquals(LayoutAction.PLAY_PAUSE, button.action)
        assertEquals(ButtonLook.TONAL, button.look)
        assertEquals(BlockShape.ROUND, button.shape)
        assertTrue(button.swipeToSkip)
    }

    @Test
    fun aBlockOutsideTheScreenIsPulledBackIn() {
        val json = """
            {"id":"a","name":"Mine","blocks":[
              {"id":"1","kind":"title","x":0.9,"y":-0.5,"w":0.5,"h":2.0}
            ]}
        """.trimIndent()
        val block = PlayerLayoutCodec.decodeOne(json)!!.blocks.single()
        assertEquals(0.5f, block.x, 0.0001f)
        assertEquals(0f, block.y, 0.0001f)
        assertEquals(1f, block.height, 0.0001f)
    }

    @Test
    fun unreadableTextIsNothingRatherThanACrash() {
        assertTrue(PlayerLayoutCodec.decodeAll("not json").isEmpty())
        assertTrue(PlayerLayoutCodec.decodeAll(null).isEmpty())
        assertNull(PlayerLayoutCodec.decodeOne("{\"name\":\"no id\"}"))
    }
}
