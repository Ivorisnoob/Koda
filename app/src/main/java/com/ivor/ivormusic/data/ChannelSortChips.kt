package com.ivor.ivormusic.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * The Latest / Popular / Oldest chips above a channel's Videos, Shorts and Live
 * grids.
 *
 * **They are plain chips now, not a sheet.** [verified September 2026, signed
 * out, WEB 2.20260903, three channels] The Videos tab puts them in
 * `richGridRenderer.header.chipBarViewModel.chips[].chipViewModel`, each with
 * its label in `text`, `selected`, and its continuation token directly on
 * `tapCommand.innertubeCommand.continuationCommand.token`. The August 2026
 * shape the channel page was written against hid the same tokens inside a
 * `showSheetCommand` list, and against the new shape that parser found
 * nothing: the sort row simply stopped appearing on every channel, with
 * nothing logged. Taking the token is still one browse continuation whose
 * reply is a `reloadContinuationItemsCommand` of 30 videos.
 *
 * Scoped to a grid's own header on purpose. A bare search for `chipViewModel`
 * over a whole tab would also collect chips that are not sort orders.
 *
 * Its own file, like `InnerTubeContinuations.kt`, so a JVM test can reach it.
 */
internal object ChannelSortChips {

    fun parse(scope: JSONObject): List<ChannelSortOption> {
        val grids = mutableListOf<JSONObject>()
        collect(scope, "richGridRenderer", grids)
        val options = mutableListOf<ChannelSortOption>()
        for (grid in grids) {
            val chips = grid.optJSONObject("header")
                ?.optJSONObject("chipBarViewModel")
                ?.optJSONArray("chips") ?: continue
            for (i in 0 until chips.length()) {
                val chip = chips.optJSONObject(i)?.optJSONObject("chipViewModel") ?: continue
                val label = chip.optString("text").takeIf { it.isNotBlank() } ?: continue
                val token = chip.optJSONObject("tapCommand")
                    ?.optJSONObject("innertubeCommand")
                    ?.optJSONObject("continuationCommand")
                    ?.optString("token")
                    ?.takeIf { it.isNotBlank() } ?: continue
                options.add(
                    ChannelSortOption(
                        label = label,
                        selected = chip.optBoolean("selected", false),
                        token = token
                    )
                )
            }
        }
        return options.distinctBy { it.label }
    }

    /**
     * The continuation for the Popular order, which is the one that reaches
     * across a channel's whole history. [verified September 2026] Popular
     * returned uploads from "6 days ago" to "12 years ago" on one page, where
     * Latest stops within months and Oldest clusters at the channel's start.
     *
     * Matched by label first - every request here is sent with `hl=en` - and by
     * position only as a fallback, when the tab offers exactly the three orders
     * YouTube has always listed as Latest, Popular, Oldest.
     */
    fun popularToken(options: List<ChannelSortOption>): String? {
        val withTokens = options.filter { it.token != null }
        withTokens.firstOrNull { it.label.equals("Popular", ignoreCase = true) }
            ?.let { return it.token }
        return if (withTokens.size == 3) withTokens[1].token else null
    }

    private fun collect(node: Any?, key: String, out: MutableList<JSONObject>) {
        when (node) {
            is JSONObject -> {
                node.optJSONObject(key)?.let(out::add)
                val names = node.keys()
                while (names.hasNext()) collect(node.opt(names.next()), key, out)
            }
            is JSONArray -> for (i in 0 until node.length()) collect(node.opt(i), key, out)
        }
    }
}
