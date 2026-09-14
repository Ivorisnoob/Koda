package com.ivor.ivormusic.service

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.data.SongSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*

/** The real callback with cached data; no car, Android looper or YouTube requests. */
class MusicServiceBrowserTest {
    private class Fixture(scope: CoroutineScope) : AutoCloseable {
        val service = MusicService()
        private val ownedScope = CoroutineScope(scope.coroutineContext + SupervisorJob())
        val songs = MediaBrowseCache<String, List<Song>>(ownedScope)
        val session = mock(MediaLibrarySession::class.java)
        val browser = mock(MediaSession.ControllerInfo::class.java)
        val callback: MediaLibrarySession.Callback
        init {
            set("serviceScope", ownedScope)
            set("browseSongs", songs)
            set("localBrowseSongs", songs)
            val type = MusicService::class.java.declaredClasses.single { it.simpleName == "LibrarySessionCallback" }
            callback = type.getDeclaredConstructor(MusicService::class.java).apply { isAccessible = true }
                .newInstance(service) as MediaLibrarySession.Callback
        }
        private fun set(name: String, value: Any) {
            MusicService::class.java.getDeclaredField(name).apply { isAccessible = true }.set(service, value)
        }
        override fun close() {
            ownedScope.cancel()
            val field = MusicService::class.java.getDeclaredField("resolveScope").apply { isAccessible = true }
            (field.get(service) as CoroutineScope).cancel()
        }
    }

    @Test fun `connection grants browse and search commands`() = runBlocking {
        // The default-returning Android test jar leaves static constants null.
        // Supply the real platform invariant while testing the actual callback.
        val unsafeField = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
        val unsafe = unsafeField.get(null) as sun.misc.Unsafe
        val emptyField = Bundle::class.java.getField("EMPTY")
        val base = unsafe.staticFieldBase(emptyField)
        val offset = unsafe.staticFieldOffset(emptyField)
        val previous = unsafe.getObject(base, offset)
        if (previous == null) unsafe.putObject(base, offset, Bundle())
        try { Fixture(this).use { f ->
            val result = f.callback.onConnect(f.session, f.browser)
            assertTrue(result.isAccepted)
            for (command in listOf(
                SessionCommand.COMMAND_CODE_LIBRARY_GET_LIBRARY_ROOT,
                SessionCommand.COMMAND_CODE_LIBRARY_GET_CHILDREN,
                SessionCommand.COMMAND_CODE_LIBRARY_GET_ITEM,
                SessionCommand.COMMAND_CODE_LIBRARY_SUBSCRIBE,
                SessionCommand.COMMAND_CODE_LIBRARY_SEARCH,
                SessionCommand.COMMAND_CODE_LIBRARY_GET_SEARCH_RESULT,
            )) assertTrue("missing command $command", result.availableSessionCommands.contains(command))
        } } finally { if (previous == null) unsafe.putObject(base, offset, null) }
    }

    @Test fun `search signals legacy completion and retains results per query`() = runBlocking {
        Fixture(this).use { f ->
            f.songs.get("SEARCH:first") { listOf(Song("a", "A", "Artist", "Album", 1000, source = SongSource.YOUTUBE)) }
            f.songs.get("SEARCH:second") { emptyList() }
            assertEquals(LibraryResult.RESULT_SUCCESS, f.callback.onSearch(f.session, f.browser, "first", null).await().resultCode)
            f.callback.onSearch(f.session, f.browser, "second", null).await()
            verify(f.session).notifySearchResultChanged(f.browser, "first", 1, null)
            verify(f.session).notifySearchResultChanged(f.browser, "second", 0, null)
            val result = f.callback.onGetSearchResult(f.session, f.browser, "first", 0, Int.MAX_VALUE, null).await()
            assertEquals(listOf("a"), result.value!!.map { it.mediaId })
        }
    }

    @Test fun `root fits car tabs and all offline categories remain reachable`() = runBlocking {
        Fixture(this).use { f ->
            val root = f.callback.onGetChildren(f.session, f.browser, "root", 0, Int.MAX_VALUE, null).await().value!!
            assertTrue(root.size <= 4)
            assertEquals(listOf("LIBRARY", "RECOMMENDED", "PLAYLISTS"), root.map { it.mediaId })
            val library = f.callback.onGetChildren(f.session, f.browser, "LIBRARY", 0, Int.MAX_VALUE, null).await().value!!
            assertEquals(listOf("DOWNLOADS", "LIKED", "RECENT", "LOCAL_SONGS"), library.map { it.mediaId })
        }
    }

    @Test fun `ID-only device playback reconstructs its content URI`() = runBlocking {
        Fixture(this).use { f ->
            val uri = mock(Uri::class.java)
            `when`(uri.scheme).thenReturn("content")
            `when`(uri.toString()).thenReturn("content://media/external/audio/media/42")
            f.songs.get("LOCAL_SONGS") { listOf(Song("42", "Local", "Artist", "Album", 1000, uri = uri)) }
            val rows = f.callback.onAddMediaItems(f.session, f.browser,
                mutableListOf(MediaItem.Builder().setMediaId("42").build())).await()
            assertEquals(uri, rows.single().localConfiguration!!.uri)
            assertEquals("Local", rows.single().mediaMetadata.title)
        }
    }
}
