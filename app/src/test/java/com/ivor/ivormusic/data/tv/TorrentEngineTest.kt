package com.ivor.ivormusic.data.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pure half of torrent playback: magnet construction, file selection and
 * piece arithmetic.
 *
 * None of this can touch libtorrent - `TorrentInfo` and `TorrentHandle` are JNI
 * objects that cannot exist in a JVM test - which is exactly why the arithmetic
 * was split out from them. A piece range that is one short does not throw; the
 * file simply never finishes buffering, which is the worst kind of bug to find
 * on a device.
 */
class TorrentEngineTest {

    private fun file(offset: Long, size: Long, path: String = "Movie/movie.mkv") =
        TorrentFile(index = 0, path = path, sizeBytes = size, offset = offset)

    // --- Magnets ------------------------------------------------------------

    @Test
    fun buildsAMagnetFromTheHash() {
        val magnet = magnetFor("ABC123DEF456", null, emptyList())
        assertTrue(magnet.startsWith("magnet:?xt=urn:btih:abc123def456"))
    }

    @Test
    fun carriesTheAddonsTrackers() {
        val magnet = magnetFor(
            "abc", "Movie 1080p",
            listOf("tracker:udp://tracker.example.org:1337/announce", "dht:abc"),
        )
        assertTrue(magnet.contains("udp%3A%2F%2Ftracker.example.org%3A1337%2Fannounce"))
        // A dht: entry is not a tracker and must not become a tr= parameter.
        assertFalse(magnet.contains("tr=dht"))
    }

    @Test
    fun addsFallbackTrackersWhenTheAddonSuppliedNone() {
        // A magnet with no trackers relies entirely on DHT, which on a cold
        // routing table can take minutes to find a first peer.
        val magnet = magnetFor("abc", null, emptyList())
        assertTrue(magnet.contains("tracker.opentrackr.org"))
    }

    @Test
    fun theDisplayNameIsEncodedNotInterpolated() {
        val magnet = magnetFor("abc", "Movie & Friends 1080p", emptyList())
        assertTrue(magnet.contains("dn=Movie%20%26%20Friends%201080p"))
        // A raw ampersand here would split the magnet into a bogus parameter.
        assertFalse(magnet.contains("dn=Movie & Friends"))
    }

    // --- Which file to play -------------------------------------------------

    @Test
    fun theAddonsFileIndexWins() {
        // A season pack returned for one episode names which file it is, and
        // that beats any heuristic.
        val files = listOf(
            file(0, 900_000_000, "Show/E01.mkv").copy(index = 0),
            file(900_000_000, 950_000_000, "Show/E02.mkv").copy(index = 1),
        )
        assertEquals(1, TorrentEngine.pickPlayableFile(files, preferredIndex = 1)?.index)
    }

    @Test
    fun withoutAnIndexTheLargestVideoWins() {
        val files = listOf(
            file(0, 50_000_000, "Movie/sample.mkv").copy(index = 0),
            file(50_000_000, 4_000_000_000, "Movie/movie.mkv").copy(index = 1),
            file(4_050_000_000, 40_000, "Movie/movie.srt").copy(index = 2),
        )
        assertEquals(1, TorrentEngine.pickPlayableFile(files, null)?.index)
    }

    @Test
    fun aSampleIsNeverTheFeature() {
        // Scene releases ship a sample beside the film, and it is a video file
        // by every other test.
        assertFalse(file(0, 1, "Movie/sample-movie.mkv").isVideo)
        assertTrue(file(0, 1, "Movie/movie.mkv").isVideo)
    }

    @Test
    fun aTorrentOfOnlyNonVideoFilesStillYieldsSomething() {
        // Better to try the biggest file and fail in the player than to refuse
        // with no explanation.
        val files = listOf(file(0, 100, "x/readme.nfo").copy(index = 0))
        assertEquals(0, TorrentEngine.pickPlayableFile(files, null)?.index)
    }

    @Test
    fun anEmptyTorrentPicksNothing() {
        assertNull(TorrentEngine.pickPlayableFile(emptyList(), null))
    }

    // --- Piece arithmetic ---------------------------------------------------

    @Test
    fun aFileAtTheStartCoversPiecesFromZero() {
        val (first, last) = TorrentEngine.pieceRangeOf(
            pieceLength = 1000, numPieces = 100, file = file(0, 5_000)
        )
        assertEquals(0, first)
        // Bytes 0..4999 with 1000-byte pieces is pieces 0..4, not 0..5.
        assertEquals(4, last)
    }

    @Test
    fun aFileInTheMiddleStartsOnThePieceHoldingItsFirstByte() {
        // Pieces span file boundaries, so a file starting at 2500 begins inside
        // piece 2 rather than at the start of piece 3.
        val (first, last) = TorrentEngine.pieceRangeOf(
            pieceLength = 1000, numPieces = 100, file = file(2_500, 3_000)
        )
        assertEquals(2, first)
        assertEquals(5, last)
    }

    @Test
    fun theRangeNeverRunsPastTheTorrent() {
        val (_, last) = TorrentEngine.pieceRangeOf(
            pieceLength = 1000, numPieces = 3, file = file(0, 100_000)
        )
        assertEquals(2, last)
    }

    @Test
    fun aZeroLengthFileStillYieldsAValidRange() {
        val (first, last) = TorrentEngine.pieceRangeOf(
            pieceLength = 1000, numPieces = 10, file = file(2_000, 0)
        )
        assertEquals(2, first)
        assertTrue(last >= first)
    }

    @Test
    fun seekingMapsToThePieceHoldingThatByte() {
        val f = file(2_500, 10_000)
        // Offset 0 in the file is absolute 2500, inside piece 2.
        assertEquals(2, TorrentEngine.pieceAt(1000, 100, f, 0))
        // Offset 500 is absolute 3000, the first byte of piece 3.
        assertEquals(3, TorrentEngine.pieceAt(1000, 100, f, 500))
        assertEquals(12, TorrentEngine.pieceAt(1000, 100, f, 10_000))
    }

    @Test
    fun aNegativeOffsetIsClampedRatherThanWrapping() {
        // ExoPlayer will not ask for one, but a negative index into a piece
        // array is a crash rather than a wrong frame.
        assertEquals(2, TorrentEngine.pieceAt(1000, 100, file(2_000, 500), -5_000))
    }

    @Test
    fun anOffsetPastTheEndClampsToTheLastPiece() {
        assertEquals(9, TorrentEngine.pieceAt(1000, 10, file(0, 10_000), 999_999))
    }
}
