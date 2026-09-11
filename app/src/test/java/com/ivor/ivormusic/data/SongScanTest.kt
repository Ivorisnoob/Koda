package com.ivor.ivormusic.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The manual filesystem scan's root selection and folder exclusion.
 *
 * Both were reported as user-visible bugs on the OEM manual-scan path: every
 * song listed twice, and excluded folders still playing.
 */
class SongScanTest {

    /** What a real device answers: /sdcard is a symlink onto the real tree. */
    private val deviceLayout: (String) -> String? = { path ->
        when (path) {
            "/sdcard", "/storage/self/primary", "/storage/emulated/0" -> "/storage/emulated/0"
            else -> path
        }
    }

    @Test
    fun `the three storage aliases collapse to one root`() {
        // EXTERNAL_STORAGE is /sdcard on nearly every device, so the old
        // distinct() on raw strings left /sdcard and /storage/emulated/0 -
        // two names for one tree, and every song was scanned through both.
        val roots = resolveScanRoots(
            listOf("/sdcard", "/storage/emulated/0", "/sdcard"),
            deviceLayout
        )
        assertEquals(listOf("/storage/emulated/0"), roots)
    }

    @Test
    fun `an alias-free candidate list still collapses`() {
        val roots = resolveScanRoots(
            listOf("/storage/emulated/0", "/storage/emulated/0", "/sdcard"),
            deviceLayout
        )
        assertEquals(listOf("/storage/emulated/0"), roots)
    }

    @Test
    fun `a genuinely separate volume survives`() {
        // An SD card is a different tree and must still be walked.
        val roots = resolveScanRoots(
            listOf("/sdcard", "/storage/emulated/0", "/storage/1A2B-3C4D"),
            deviceLayout
        )
        assertEquals(listOf("/storage/emulated/0", "/storage/1A2B-3C4D"), roots)
    }

    @Test
    fun `an unresolvable path falls back to itself rather than vanishing`() {
        val roots = resolveScanRoots(listOf("/sdcard", "/storage/emulated/0")) { null }
        // Nothing resolved, so both stand - dropping a root that might hold
        // the user's music is worse than scanning one twice.
        assertEquals(listOf("/sdcard", "/storage/emulated/0"), roots)
    }

    @Test
    fun `null and blank candidates are skipped`() {
        // System.getenv returns null when EXTERNAL_STORAGE is unset.
        val roots = resolveScanRoots(listOf(null, "", "   ", "/storage/emulated/0"), deviceLayout)
        assertEquals(listOf("/storage/emulated/0"), roots)
    }

    @Test
    fun `candidate order is preserved`() {
        val roots = resolveScanRoots(listOf("/b", "/a", "/c"), { it })
        assertEquals(listOf("/b", "/a", "/c"), roots)
    }

    // ---- exclusions ---------------------------------------------------

    @Test
    fun `excluding a folder excludes everything under it`() {
        val excluded = setOf("/storage/emulated/0/WhatsApp")
        assertTrue(isExcludedFolder("/storage/emulated/0/WhatsApp", excluded))
        // The manual scan used exact equality, so subfolders kept playing.
        assertTrue(isExcludedFolder("/storage/emulated/0/WhatsApp/Media/Audio", excluded))
    }

    @Test
    fun `a folder sharing a name prefix is not excluded`() {
        val excluded = setOf("/storage/emulated/0/Music")
        assertFalse(isExcludedFolder("/storage/emulated/0/MusicVideos", excluded))
        assertFalse(isExcludedFolder("/storage/emulated/0/MusicVideos/Live", excluded))
    }

    @Test
    fun `a parent of an excluded folder is not itself excluded`() {
        assertFalse(
            isExcludedFolder("/storage/emulated/0", setOf("/storage/emulated/0/Recordings"))
        )
    }

    @Test
    fun `no exclusions and blank exclusions exclude nothing`() {
        assertFalse(isExcludedFolder("/storage/emulated/0/Music", emptySet()))
        // A blank entry must not turn into "everything starts with nothing".
        assertFalse(isExcludedFolder("/storage/emulated/0/Music", setOf("", "   ")))
    }
}
