package com.ivor.ivormusic.data

import org.json.JSONArray
import org.json.JSONObject

/** YouTube's three notification levels for a subscribed channel, the bell beside Subscribe. */
enum class BellLevel { ALL, PERSONALIZED, NONE }

/**
 * The account bell on one subscribed channel: where it stands and how to move it.
 *
 * [choices] holds the `modifyChannelNotificationPreferenceEndpoint.params` YouTube
 * served for each level. They are opaque and minted per channel, per level and per
 * surface, so they are passed back as given and never built. Posting one to
 * `notification/modify_channel_preference` sets that level (see
 * [YouTubeRepository.setChannelBell]).
 *
 * [level] is null when the response carried the menu but no state, which only a
 * shape change would cause; the bell is then drawn without a current level.
 */
data class ChannelBell(
    val channelId: String,
    val level: BellLevel?,
    val choices: Map<BellLevel, String>,
) {
    fun withLevel(next: BellLevel): ChannelBell = copy(level = next)
}

/** A bell write that landed: the channel's new bell and YouTube's own toast text, when it sent one. */
data class ChannelBellChange(val bell: ChannelBell, val message: String?)

/**
 * Reads the bell out of the two shapes YouTube serves it in. [verified September
 * 2026, signed in, WEB]
 *
 * **View-model shape** - channel page header and watch page (one per channel, so a
 * collaboration carries several):
 * ```
 * subscribeButtonViewModel
 *   channelId
 *   disableNotificationBell
 *   notificationStateEntityStoreKeys.subsNotificationStateKey  -> entity key
 *   onShowSubscriptionOptions.innertubeCommand.showSheetCommand.panelLoadingStrategy
 *     .inlineContent.sheetViewModel.content.listViewModel.listItems[]
 *       listItemViewModel
 *         leadingImage.sources[0].clientResource.imageName    NOTIFICATIONS_ACTIVE | _NONE | _OFF
 *         isSelected
 *         rendererContext.commandContext.onTap.innertubeCommand
 *           .modifyChannelNotificationPreferenceEndpoint.params
 * frameworkUpdates...mutations[].payload.subscriptionNotificationStateEntity
 *   key, state   SUBSCRIPTION_NOTIFICATION_STATE_ALL | _OCCASIONAL | _OFF
 * ```
 * **Toggle shape** - every `channelRenderer` of `FEchannels`, and the reply to a
 * preference write:
 * ```
 * subscriptionNotificationToggleButtonRenderer
 *   currentStateId      2 All, 3 Personalized, 0 None
 *   command.commandExecutorCommand.commands[].openPopupAction.popup.menuPopupRenderer.items[]
 *     menuServiceItemRenderer
 *       icon.iconType   NOTIFICATIONS_ACTIVE | _NONE | _OFF
 *       isSelected
 *       serviceEndpoint.modifyChannelNotificationPreferenceEndpoint.params
 * ```
 * Levels are told apart by icon, never by label: "All", "Personalized" and "None"
 * are localized. Personalized wears NOTIFICATIONS_NONE (the plain bell) and None
 * wears NOTIFICATIONS_OFF.
 *
 * Some watch pages still carry the toggle shape inside a legacy
 * `subscribeButtonRenderer.notificationPreferenceButton` instead of the view
 * model, so callers read both. A channel YouTube has disabled notifications for
 * serves a menu of just "Disabled" (NOTIFICATIONS_OFF) and "Unsubscribe", with no
 * preference params on either: that yields no bell, which is right - there is
 * nothing to change.
 */
internal object ChannelBellParser {

    /** Every channel's bell in a view-model response, keyed by channel id. */
    fun fromSubscribeButtons(root: JSONObject): Map<String, ChannelBell> {
        val states = HashMap<String, BellLevel>()
        walk(root) { node ->
            val entity = node.optJSONObject("subscriptionNotificationStateEntity") ?: return@walk
            val key = entity.optString("key").takeIf { it.isNotBlank() } ?: return@walk
            entityLevel(entity.optString("state"))?.let { states[key] = it }
        }

        val bells = LinkedHashMap<String, ChannelBell>()
        walk(root) { node ->
            val button = node.optJSONObject("subscribeButtonViewModel") ?: return@walk
            val channelId = button.optString("channelId").takeIf { it.isNotBlank() } ?: return@walk
            if (bells.containsKey(channelId)) return@walk
            if (button.optBoolean("disableNotificationBell", false)) return@walk
            val items = button.optJSONObject("onShowSubscriptionOptions")
                ?.optJSONObject("innertubeCommand")
                ?.optJSONObject("showSheetCommand")
                ?.optJSONObject("panelLoadingStrategy")
                ?.optJSONObject("inlineContent")
                ?.optJSONObject("sheetViewModel")
                ?.optJSONObject("content")
                ?.optJSONObject("listViewModel")
                ?.optJSONArray("listItems") ?: return@walk

            val choices = LinkedHashMap<BellLevel, String>()
            var selected: BellLevel? = null
            items.objects().forEach { entry ->
                val item = entry.optJSONObject("listItemViewModel") ?: return@forEach
                val level = iconLevel(
                    item.optJSONObject("leadingImage")?.optJSONArray("sources")
                        ?.optJSONObject(0)?.optJSONObject("clientResource")?.optString("imageName")
                ) ?: return@forEach
                val params = item.optJSONObject("rendererContext")
                    ?.optJSONObject("commandContext")
                    ?.optJSONObject("onTap")
                    ?.optJSONObject("innertubeCommand")
                    ?.optJSONObject("modifyChannelNotificationPreferenceEndpoint")
                    ?.optString("params")
                    ?.takeIf { it.isNotBlank() } ?: return@forEach
                choices[level] = params
                if (item.optBoolean("isSelected", false)) selected = level
            }
            if (choices.isEmpty()) return@walk

            val stateKey = button.optJSONObject("notificationStateEntityStoreKeys")
                ?.optString("subsNotificationStateKey")
            bells[channelId] = ChannelBell(channelId, states[stateKey] ?: selected, choices)
        }
        return bells
    }

    /**
     * The bell out of a `subscriptionNotificationToggleButtonRenderer`, or the
     * object holding one. [channelId] comes from the caller: the renderer itself
     * does not name its channel.
     */
    fun fromToggle(host: JSONObject?, channelId: String): ChannelBell? {
        val toggle = host?.optJSONObject("subscriptionNotificationToggleButtonRenderer") ?: host
            ?: return null
        if (!toggle.has("states") && !toggle.has("command")) return null

        val choices = LinkedHashMap<BellLevel, String>()
        var selected: BellLevel? = null
        toggle.optJSONObject("command")
            ?.optJSONObject("commandExecutorCommand")
            ?.optJSONArray("commands")
            .objects()
            .mapNotNull {
                it.optJSONObject("openPopupAction")?.optJSONObject("popup")
                    ?.optJSONObject("menuPopupRenderer")?.optJSONArray("items")
            }
            .forEach { items ->
                items.objects().forEach { entry ->
                    val item = entry.optJSONObject("menuServiceItemRenderer") ?: return@forEach
                    val level = iconLevel(item.optJSONObject("icon")?.optString("iconType"))
                        ?: return@forEach
                    val params = item.optJSONObject("serviceEndpoint")
                        ?.optJSONObject("modifyChannelNotificationPreferenceEndpoint")
                        ?.optString("params")
                        ?.takeIf { it.isNotBlank() } ?: return@forEach
                    choices[level] = params
                    if (item.optBoolean("isSelected", false)) selected = level
                }
            }
        if (choices.isEmpty()) return null

        val current = when (toggle.optInt("currentStateId", -1)) {
            2 -> BellLevel.ALL
            3 -> BellLevel.PERSONALIZED
            0 -> BellLevel.NONE
            else -> null
        }
        return ChannelBell(channelId, current ?: selected, choices)
    }

    private fun iconLevel(icon: String?): BellLevel? = when (icon) {
        "NOTIFICATIONS_ACTIVE" -> BellLevel.ALL
        "NOTIFICATIONS_NONE" -> BellLevel.PERSONALIZED
        "NOTIFICATIONS_OFF" -> BellLevel.NONE
        else -> null
    }

    private fun entityLevel(state: String?): BellLevel? = when (state) {
        "SUBSCRIPTION_NOTIFICATION_STATE_ALL" -> BellLevel.ALL
        "SUBSCRIPTION_NOTIFICATION_STATE_OCCASIONAL" -> BellLevel.PERSONALIZED
        "SUBSCRIPTION_NOTIFICATION_STATE_OFF" -> BellLevel.NONE
        else -> null
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

    private fun walk(node: Any?, visit: (JSONObject) -> Unit) {
        when (node) {
            is JSONObject -> {
                visit(node)
                val keys = node.keys()
                while (keys.hasNext()) walk(node.opt(keys.next()), visit)
            }
            is JSONArray -> for (i in 0 until node.length()) walk(node.opt(i), visit)
        }
    }
}
