package com.ivor.ivormusic.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Shapes trimmed from signed-in WEB probes, September 2026 (see ChannelBellParser). */
class ChannelBellTest {

    private fun sheetItem(icon: String, params: String, selected: Boolean) = """
        {"listItemViewModel":{
          "title":{"content":"localized"},
          "leadingImage":{"sources":[{"clientResource":{"imageName":"$icon"}}]},
          "isSelected":$selected,
          "rendererContext":{"commandContext":{"onTap":{"innertubeCommand":{
            "modifyChannelNotificationPreferenceEndpoint":{"params":"$params"}}}}}}}
    """

    private fun subscribeButton(channel: String, stateKey: String, disabled: Boolean = false) = """
        {"subscribeButtonViewModel":{
          "channelId":"$channel",
          "disableNotificationBell":$disabled,
          "notificationStateEntityStoreKeys":{"subsNotificationStateKey":"$stateKey"},
          "onShowSubscriptionOptions":{"innertubeCommand":{"showSheetCommand":{"panelLoadingStrategy":
            {"inlineContent":{"sheetViewModel":{"content":{"listViewModel":{"listItems":[
              ${sheetItem("NOTIFICATIONS_ACTIVE", "all-$channel", false)},
              ${sheetItem("NOTIFICATIONS_NONE", "personalized-$channel", true)},
              ${sheetItem("NOTIFICATIONS_OFF", "none-$channel", false)}
            ]}}}}}}}}}}
    """

    private fun stateEntity(key: String, state: String) = """
        {"payload":{"subscriptionNotificationStateEntity":{"key":"$key","state":"$state"}}}
    """

    @Test fun `view-model bell reads each channel's state entity and its own menu`() {
        val root = JSONObject("""
            {"contents":[${subscribeButton("UCa", "keyA")}, ${subscribeButton("UCb", "keyB")}],
             "frameworkUpdates":{"entityBatchUpdate":{"mutations":[
               ${stateEntity("keyA", "SUBSCRIPTION_NOTIFICATION_STATE_ALL")},
               ${stateEntity("keyB", "SUBSCRIPTION_NOTIFICATION_STATE_OFF")}
             ]}}}
        """)
        val bells = ChannelBellParser.fromSubscribeButtons(root)

        assertEquals(setOf("UCa", "UCb"), bells.keys)
        assertEquals(BellLevel.ALL, bells.getValue("UCa").level)
        assertEquals(BellLevel.NONE, bells.getValue("UCb").level)
        assertEquals("all-UCb", bells.getValue("UCb").choices[BellLevel.ALL])
        assertEquals("personalized-UCb", bells.getValue("UCb").choices[BellLevel.PERSONALIZED])
        assertEquals("none-UCb", bells.getValue("UCb").choices[BellLevel.NONE])
    }

    @Test fun `occasional is personalized, and a missing entity falls back to the selected item`() {
        val withEntity = JSONObject("""
            {"a":${subscribeButton("UCa", "keyA")},
             "m":[${stateEntity("keyA", "SUBSCRIPTION_NOTIFICATION_STATE_OCCASIONAL")}]}
        """)
        assertEquals(BellLevel.PERSONALIZED, ChannelBellParser.fromSubscribeButtons(withEntity)["UCa"]?.level)

        val withoutEntity = JSONObject("""{"a":${subscribeButton("UCa", "keyA")}}""")
        assertEquals(BellLevel.PERSONALIZED, ChannelBellParser.fromSubscribeButtons(withoutEntity)["UCa"]?.level)
    }

    @Test fun `a channel with notifications disabled yields no bell`() {
        // Probed September 2026: "Disabled" and "Unsubscribe", neither with preference params.
        val root = JSONObject("""
            {"subscribeButtonViewModel":{"channelId":"UCa","disableNotificationBell":false,
              "onShowSubscriptionOptions":{"innertubeCommand":{"showSheetCommand":{"panelLoadingStrategy":
                {"inlineContent":{"sheetViewModel":{"content":{"listViewModel":{"listItems":[
                  {"listItemViewModel":{"title":{"content":"Disabled"},
                    "leadingImage":{"sources":[{"clientResource":{"imageName":"NOTIFICATIONS_OFF"}}]},
                    "rendererContext":{"commandContext":{"onTap":{"innertubeCommand":{"signalServiceEndpoint":{}}}}}}},
                  {"listItemViewModel":{"title":{"content":"Unsubscribe"},
                    "leadingImage":{"sources":[{"clientResource":{"imageName":"PERSON_MINUS"}}]},
                    "rendererContext":{"commandContext":{"onTap":{"innertubeCommand":{"signalServiceEndpoint":{}}}}}}}
                ]}}}}}}}}}}
        """)
        assertTrue(ChannelBellParser.fromSubscribeButtons(root).isEmpty())
    }

    @Test fun `a disabled bell is not offered`() {
        val root = JSONObject("""{"a":${subscribeButton("UCa", "keyA", disabled = true)}}""")
        assertTrue(ChannelBellParser.fromSubscribeButtons(root).isEmpty())
    }

    private fun menuItem(icon: String, params: String, selected: Boolean) = """
        {"menuServiceItemRenderer":{
          "text":{"simpleText":"localized"},
          "icon":{"iconType":"$icon"},
          "serviceEndpoint":{"modifyChannelNotificationPreferenceEndpoint":{"params":"$params"}},
          "isSelected":$selected}}
    """

    private fun toggle(currentStateId: Int) = JSONObject("""
        {"subscriptionNotificationToggleButtonRenderer":{
          "states":[{"stateId":2},{"stateId":3},{"stateId":0}],
          "currentStateId":$currentStateId,
          "command":{"commandExecutorCommand":{"commands":[{"openPopupAction":{"popup":{
            "menuPopupRenderer":{"items":[
              ${menuItem("NOTIFICATIONS_ACTIVE", "p-all", currentStateId == 2)},
              ${menuItem("NOTIFICATIONS_NONE", "p-personalized", currentStateId == 3)},
              ${menuItem("NOTIFICATIONS_OFF", "p-none", currentStateId == 0)},
              {"menuServiceItemRenderer":{"icon":{"iconType":"PERSON_MINUS"}}}
            ]}}}}]}}}}
    """)

    @Test fun `toggle bell maps state ids and ignores the unsubscribe item`() {
        assertEquals(BellLevel.ALL, ChannelBellParser.fromToggle(toggle(2), "UCa")?.level)
        assertEquals(BellLevel.PERSONALIZED, ChannelBellParser.fromToggle(toggle(3), "UCa")?.level)
        val none = ChannelBellParser.fromToggle(toggle(0), "UCa")!!
        assertEquals(BellLevel.NONE, none.level)
        assertEquals("UCa", none.channelId)
        assertEquals(
            mapOf(BellLevel.ALL to "p-all", BellLevel.PERSONALIZED to "p-personalized", BellLevel.NONE to "p-none"),
            none.choices
        )
    }

    @Test fun `toggle accepts the renderer itself as well as its host`() {
        val renderer = toggle(2).getJSONObject("subscriptionNotificationToggleButtonRenderer")
        assertEquals(BellLevel.ALL, ChannelBellParser.fromToggle(renderer, "UCa")?.level)
    }

    @Test fun `no menu means no bell`() {
        assertNull(ChannelBellParser.fromToggle(JSONObject("""{"states":[]}"""), "UCa"))
        assertNull(ChannelBellParser.fromToggle(null, "UCa"))
    }
}
