package com.ivor.ivormusic.data.youtube.sabr

import com.ivor.ivormusic.data.youtube.sabr.download.SabrDownloadCheckpoint
import com.ivor.ivormusic.data.youtube.sabr.download.SabrTrackProgress
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SabrDownloadCheckpointTest {
    private fun audio(completed: Int = 0) = SabrTrackProgress(
        itag = 140, lastModified = 7, xtags = null, audioTrackId = "original",
        completedThrough = completed, endSequence = 3)

    @Test fun `round trip keeps identities and ignores future fields`() {
        val checkpoint = SabrDownloadCheckpoint(
            videoId = "abc", audio = audio(1),
            video = SabrTrackProgress(137, 9, "x:y", null, 0, 2), updatedAtMs = 42)
        val raw = checkpoint.toJson().put("future", 1).toString()
        assertEquals(checkpoint, SabrDownloadCheckpoint.parse(raw))
    }

    @Test fun `null video and null tags survive the round trip`() {
        val checkpoint = SabrDownloadCheckpoint("abc", audio(), null, 0)
        assertEquals(checkpoint, SabrDownloadCheckpoint.parse(checkpoint.toJson().toString()))
    }

    @Test fun `only the next expected sequence advances`() {
        val progress = audio(1)
        assertEquals(2, progress.advance(2).completedThrough)
        assertSame(progress, progress.advance(3))
        assertSame(progress, progress.advance(1))
        assertSame(progress, progress.advance(0))
    }

    @Test fun `a finished track never advances past its timeline`() {
        val done = audio(3)
        assertTrue(done.isComplete)
        assertSame(done, done.advance(4))
    }

    @Test fun `completion needs every present track`() {
        assertFalse(SabrDownloadCheckpoint("a", audio(3),
            SabrTrackProgress(137, 9, null, null, 1, 2), 0).isComplete)
        assertTrue(SabrDownloadCheckpoint("a", audio(3),
            SabrTrackProgress(137, 9, null, null, 2, 2), 0).isComplete)
        assertTrue(SabrDownloadCheckpoint("a", audio(3), null, 0).isComplete)
        assertFalse(SabrDownloadCheckpoint("a", audio(2), null, 0).isComplete)
    }

    @Test fun `invalid checkpoints are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            SabrDownloadCheckpoint("", audio(), null, 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SabrTrackProgress(140, 7, null, null, 4, 3)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SabrTrackProgress(140, 7, null, null, 0, 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SabrTrackProgress(0, 7, null, null, 0, 1)
        }
        assertThrows(Exception::class.java) {
            SabrDownloadCheckpoint.parse(JSONObject().put("videoId", "a").toString())
        }
    }
}
