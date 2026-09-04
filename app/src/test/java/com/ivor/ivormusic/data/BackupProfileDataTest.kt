package com.ivor.ivormusic.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The profile-scoped section is the half of a backup no compiler checks: the
 * store names are restated here and in [BackupRepository], and a field left out
 * of the round trip is dropped silently by every backup taken afterwards.
 */
class BackupProfileDataTest {

    private fun manifest() = BackupManifest(
        formatVersion = BackupTransfer.FORMAT_VERSION,
        appVersionName = "test",
        appVersionCode = 1,
        createdAt = 0L,
        device = null,
    )

    private fun roundTrip(snapshot: BackupSnapshot): BackupSnapshot? {
        val out = ByteArrayOutputStream()
        BackupTransfer.write(snapshot, out)
        return BackupTransfer.read(ByteArrayInputStream(out.toByteArray()))
    }

    @Test
    fun `every profile-scoped store survives a round trip`() {
        val data = BackupProfileData(
            subscriptions = """[{"id":"UC1"}]""",
            subscriptionGroups = """[{"name":"Music"}]""",
            hiddenVideos = """[{"videoId":"abc"}]""",
            blockedChannels = """[{"channelId":"UC2"}]""",
            watchHistory = """[{"videoId":"def"}]""",
            removedFromHistory = setOf("ghi"),
            resumePositions = """[{"videoId":"def","positionMs":120000,"durationMs":600000}]""",
        )

        val restored = roundTrip(
            BackupSnapshot(manifest = manifest(), profileData = mapOf("p1" to data))
        )

        assertNotNull(restored)
        assertEquals(data, restored!!.profileData["p1"])
    }

    @Test
    fun `a backup written before resume positions existed reads back without them`() {
        val legacy = BackupProfileData(
            watchHistory = """[{"videoId":"def"}]""",
            removedFromHistory = setOf("ghi"),
        )

        val restored = roundTrip(
            BackupSnapshot(manifest = manifest(), profileData = mapOf("p1" to legacy))
        )

        assertNotNull(restored)
        assertNull(restored!!.profileData["p1"]?.resumePositions)
        assertEquals(legacy, restored.profileData["p1"])
    }

    @Test
    fun `resume positions alone are enough to make a profile worth carrying`() {
        val onlyResume = BackupProfileData(resumePositions = """[{"videoId":"def"}]""")
        assertEquals(false, onlyResume.isEmpty)
        assertEquals(true, BackupProfileData().isEmpty)
    }
}
