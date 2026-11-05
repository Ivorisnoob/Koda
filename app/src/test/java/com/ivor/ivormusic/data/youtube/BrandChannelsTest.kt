package com.ivor.ivormusic.data.youtube

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrandChannelsTest {

    private fun item(
        name: String,
        handle: String?,
        selected: Boolean,
        pageId: String?,
        datasyncId: String,
    ): JSONObject {
        val tokens = org.json.JSONArray()
        if (pageId != null) tokens.put(JSONObject().put("pageIdToken", JSONObject().put("pageId", pageId)))
        tokens.put(JSONObject().put("datasyncIdToken", JSONObject().put("datasyncIdToken", datasyncId)))
        return JSONObject().put(
            "accountItem",
            JSONObject()
                .put("accountName", JSONObject().put("simpleText", name))
                .apply {
                    if (handle != null) put("channelHandle", JSONObject().put("simpleText", handle))
                }
                .put("accountPhoto", JSONObject().put("thumbnails", org.json.JSONArray()
                    .put(JSONObject().put("url", "https://yt3.ggpht.com/small"))
                    .put(JSONObject().put("url", "https://yt3.ggpht.com/large"))))
                .put("isSelected", selected)
                .put("serviceEndpoint", JSONObject().put("selectActiveIdentityEndpoint",
                    JSONObject().put("supportedTokens", tokens)))
        )
    }

    private fun response(vararg items: JSONObject) = JSONObject().put(
        "contents", org.json.JSONArray().put(
            JSONObject().put("accountSectionListRenderer", JSONObject().put(
                "contents", org.json.JSONArray().put(
                    JSONObject().put("accountItemSectionRenderer", JSONObject().put(
                        "contents", org.json.JSONArray().apply { items.forEach { put(it) } }
                    ))
                )
            ))
        )
    )

    @Test
    fun `reads the personal channel and each brand account`() {
        val channels = parseBrandChannels(
            response(
                item("Ivor", "@ivor", selected = true, pageId = null, datasyncId = "111||"),
                item("Koda Music", "@kodamusic", selected = false, pageId = "222", datasyncId = "222||111"),
            )
        )

        assertEquals(2, channels.size)
        val personal = channels[0]
        assertEquals("Ivor", personal.name)
        assertEquals("@ivor", personal.handle)
        assertNull(personal.pageId)
        assertEquals("111||", personal.datasyncId)
        assertTrue(personal.isSelected)
        assertEquals("https://yt3.ggpht.com/large", personal.avatarUrl)

        val brand = channels[1]
        assertEquals("Koda Music", brand.name)
        assertEquals("222", brand.pageId)
        assertEquals("222||111", brand.datasyncId)
        assertFalse(brand.isSelected)
    }

    @Test
    fun `reads names written as runs`() {
        val runsItem = item("ignored", null, selected = false, pageId = "333", datasyncId = "333||111")
        runsItem.getJSONObject("accountItem").put(
            "accountName",
            JSONObject().put("runs", org.json.JSONArray()
                .put(JSONObject().put("text", "Side "))
                .put(JSONObject().put("text", "Channel")))
        )
        val channels = parseBrandChannels(response(runsItem))
        assertEquals("Side Channel", channels.single().name)
        assertNull(channels.single().handle)
    }

    @Test
    fun `an unexpected response gives no channels rather than failing`() {
        assertTrue(parseBrandChannels(JSONObject().put("responseContext", JSONObject())).isEmpty())
    }
}
