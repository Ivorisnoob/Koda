package com.ivor.ivormusic.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The per-profile file names behind listening history and like metadata.
 * A restore deletes every file [ProfileManager.isHistoryScopedFileName]
 * matches, so the matcher has to take every name the writer can produce and
 * nothing else in `filesDir`.
 */
class ProfileScopedFileNameTest {

    private val owner = "0f8fad5b-d9cb-469f-a165-70867728950e"
    private val other = "7c9e6679-7425-40de-944b-e07fc1f90ae7"

    @Test
    fun `the owner keeps the name the device-wide file always had`() {
        assertEquals(
            "play_history.json",
            ProfileManager.historyScopedFileName("play_history", "json", owner, owner)
        )
    }

    @Test
    fun `every other profile gets its own file`() {
        assertEquals(
            "play_history_$other.json",
            ProfileManager.historyScopedFileName("play_history", "json", other, owner)
        )
    }

    @Test
    fun `an id from a backup file cannot climb out of filesDir`() {
        val name = ProfileManager.historyScopedFileName("play_history", "json", "../../shared_prefs/x", owner)
        assertFalse(name.contains('/'))
        assertFalse(name.contains(".."))
        assertTrue(ProfileManager.isHistoryScopedFileName(name, "play_history", "json"))
    }

    @Test
    fun `the matcher takes what the writer produces`() {
        listOf(owner, other).forEach { id ->
            listOf("play_history", "liked_songs_meta").forEach { base ->
                val name = ProfileManager.historyScopedFileName(base, "json", id, owner)
                assertTrue(name, ProfileManager.isHistoryScopedFileName(name, base, "json"))
            }
        }
    }

    @Test
    fun `the matcher leaves every other file alone`() {
        listOf(
            "audio_profiles.json",
            "playback_session.json",
            "play_history.json.partial",
            "play_history_$other.json.partial",
            "play_history_.json",
            "play_history_$other.txt",
            "liked_songs_meta_extra/file.json",
        ).forEach { name ->
            assertFalse(name, ProfileManager.isHistoryScopedFileName(name, "play_history", "json"))
            assertFalse(name, ProfileManager.isHistoryScopedFileName(name, "liked_songs_meta", "json"))
        }
    }
}
