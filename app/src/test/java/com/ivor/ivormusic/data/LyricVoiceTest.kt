package com.ivor.ivormusic.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricVoiceTest {

    @Test fun `a solo song is all lead, whatever its agent is called`() {
        val voices = assignLyricVoices(listOf("v1", "v1", null, "v1"), groupAgents = emptySet())
        assertEquals(List(4) { LyricVoice.LEAD }, voices)
    }

    @Test fun `the second singer answers from the other side and a group stays with the lead`() {
        // The shape of "No One Knows" from YouLyPlus: a group opening, then v1 and v2 trading lines.
        val voices = assignLyricVoices(
            listOf("v1000", "v1", "v2", "v1", "v2", "v1000"),
            groupAgents = setOf("v1000"),
        )
        assertEquals(
            listOf(
                LyricVoice.TOGETHER, LyricVoice.LEAD, LyricVoice.DUET,
                LyricVoice.LEAD, LyricVoice.DUET, LyricVoice.TOGETHER,
            ),
            voices,
        )
    }

    @Test fun `singers are numbered by first appearance and a third goes back to the start`() {
        val voices = assignLyricVoices(listOf("v2", "v1", "v3", null), groupAgents = emptySet())
        assertEquals(listOf(LyricVoice.LEAD, LyricVoice.DUET, LyricVoice.LEAD, LyricVoice.LEAD), voices)
    }

    @Test fun `a group alone is not a duet`() {
        val voices = assignLyricVoices(listOf("v1000", "v1", "v1000"), groupAgents = setOf("v1000"))
        assertEquals(List(3) { LyricVoice.LEAD }, voices)
    }
}
