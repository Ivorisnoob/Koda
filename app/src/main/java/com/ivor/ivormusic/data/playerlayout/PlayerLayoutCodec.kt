package com.ivor.ivormusic.data.playerlayout

import org.json.JSONArray
import org.json.JSONObject

/**
 * Saved layouts as JSON.
 *
 * The key names are stored and frozen, like the enum ids they carry. Reading
 * is forgiving on purpose: a block of a kind this build does not know is
 * dropped rather than failing the layout, a missing field takes its default,
 * and a position out of range is pulled back inside the screen. A layout that
 * cannot be read at all is skipped, never thrown.
 */
object PlayerLayoutCodec {

    /** Bumped only if a field changes meaning; new fields need no bump. */
    const val VERSION = 1

    fun encodeAll(layouts: List<PlayerLayout>): String =
        JSONArray().apply { layouts.forEach { put(encode(it)) } }.toString()

    fun decodeAll(json: String?): List<PlayerLayout> {
        if (json.isNullOrBlank()) return emptyList()
        val array = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let(::decode)
        }
    }

    fun encodeOne(layout: PlayerLayout): String = encode(layout).toString()

    fun decodeOne(json: String?): PlayerLayout? {
        if (json.isNullOrBlank()) return null
        return runCatching { JSONObject(json) }.getOrNull()?.let(::decode)
    }

    private fun encode(layout: PlayerLayout): JSONObject = JSONObject().apply {
        put("v", VERSION)
        put("id", layout.id)
        put("name", layout.name)
        put("bg", layout.background.id)
        put("blocks", JSONArray().apply { layout.blocks.forEach { put(encodeBlock(it)) } })
    }

    private fun encodeBlock(block: LayoutBlock): JSONObject = JSONObject().apply {
        put("id", block.id)
        put("kind", block.kind.id)
        put("x", block.x.toDouble())
        put("y", block.y.toDouble())
        put("w", block.width.toDouble())
        put("h", block.height.toDouble())
        put("action", block.action.id)
        put("look", block.look.id)
        put("color", block.color.id)
        put("shape", block.shape.id)
        put("align", block.align.id)
        put("size", block.textSize.id)
        put("detail", block.showDetail)
        put("motion", block.motion)
        put("fade", block.fade)
        put("swipe", block.swipeToSkip)
        put("skin", block.skin.id)
    }

    private fun decode(json: JSONObject): PlayerLayout? {
        val id = json.optString("id").takeIf { it.isNotBlank() } ?: return null
        val blocksJson = json.optJSONArray("blocks") ?: JSONArray()
        val blocks = (0 until blocksJson.length()).mapNotNull { index ->
            blocksJson.optJSONObject(index)?.let(::decodeBlock)
        }
        return PlayerLayout(
            id = id,
            name = json.optString("name"),
            background = LayoutBackground.fromId(json.optString("bg")),
            blocks = blocks,
        )
    }

    private fun decodeBlock(json: JSONObject): LayoutBlock? {
        val kind = LayoutBlockKind.fromId(json.optString("kind")) ?: return null
        val defaults = LayoutBlock(id = "", kind = kind, x = 0f, y = 0f, width = 0f, height = 0f)
        return LayoutBlock(
            id = json.optString("id").takeIf { it.isNotBlank() } ?: PlayerLayout.newId(),
            kind = kind,
            x = json.fraction("x", 0f),
            y = json.fraction("y", 0f),
            width = json.fraction("w", 0.25f),
            height = json.fraction("h", 0.1f),
            action = LayoutAction.fromId(json.optString("action"), defaults.action),
            look = ButtonLook.fromId(json.optString("look")),
            color = LayoutColor.fromId(json.optString("color")),
            shape = BlockShape.fromId(json.optString("shape"), defaults.shape),
            align = LayoutTextAlign.fromId(json.optString("align")),
            textSize = LayoutTextSize.fromId(json.optString("size")),
            showDetail = json.optBoolean("detail", defaults.showDetail),
            motion = json.optBoolean("motion", defaults.motion),
            fade = json.optBoolean("fade", defaults.fade),
            swipeToSkip = json.optBoolean("swipe", defaults.swipeToSkip),
            skin = LayoutSkin.fromId(json.optString("skin")),
        ).clampedToArea()
    }

    private fun JSONObject.fraction(key: String, fallback: Float): Float {
        val value = optDouble(key, fallback.toDouble()).toFloat()
        return if (value.isNaN() || value.isInfinite()) fallback else value
    }
}
