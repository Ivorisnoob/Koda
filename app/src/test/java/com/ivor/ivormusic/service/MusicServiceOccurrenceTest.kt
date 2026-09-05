package com.ivor.ivormusic.service

import android.net.Uri
import android.os.Bundle
import androidx.media3.session.MediaSession
import com.ivor.ivormusic.data.PlaybackSessionRepository
import com.ivor.ivormusic.data.ThemePreferences
import com.ivor.ivormusic.data.Song
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.ExecutionException
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.HttpDataSource
import com.ivor.ivormusic.data.YouTubeRepository
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.resume
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.ivor.ivormusic.data.AudioProfileStore
import java.lang.reflect.Proxy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*

/** Real service methods with controllable resolution and players; no Android looper or network. */
class MusicServiceOccurrenceTest {
    private fun item(id: String, occurrence: String, placeholder: Boolean = false): MediaItem {
        val extras = mock(Bundle::class.java)
        `when`(extras.getString(EXTRA_QUEUE_ITEM_ID)).thenReturn(occurrence)
        val uri = mock(Uri::class.java)
        `when`(uri.toString()).thenReturn(if (placeholder) "https://placeholder.ivormusic/$id" else "https://stream/$id")
        `when`(uri.scheme).thenReturn("https")
        return MediaItem.Builder().setMediaId(id).setUri(uri)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(id).setExtras(extras).build()).build()
    }

    private class TestPlayer {
        val items = mutableListOf<MediaItem>()
        var index = 0
        var position = 0L
        var volume = 1f
        var playing = false
        var parameters = PlaybackParameters.DEFAULT
        var nextIndex: Int? = null
        var previousIndex: Int? = null
        var repeatMode = Player.REPEAT_MODE_OFF
        var onSeek: (() -> Unit)? = null
        val replacements = mutableListOf<Int>()
        val seeks = mutableListOf<Pair<Int, Long>>()
        val player = Proxy.newProxyInstance(ExoPlayer::class.java.classLoader, arrayOf(ExoPlayer::class.java)) { _, method, args ->
            when (method.name) {
                "getMediaItemCount" -> items.size
                "getCurrentMediaItemIndex" -> index
                "getCurrentMediaItem" -> items.getOrNull(index)
                "getMediaItemAt" -> items[args!![0] as Int]
                "getNextMediaItemIndex" -> nextIndex ?: if (index + 1 < items.size) index + 1 else -1
                "getPreviousMediaItemIndex" -> previousIndex ?: index - 1
                "hasNextMediaItem" -> index + 1 < items.size
                "getRepeatMode" -> repeatMode
                "getCurrentPosition" -> position
                "getDuration" -> 10_000L
                "getPlaybackState" -> Player.STATE_READY
                "getPlayerError" -> null
                "getPlayWhenReady", "isPlaying" -> playing
                "getPlaybackParameters" -> parameters
                "setPlaybackParameters" -> { parameters = args!![0] as PlaybackParameters; null }
                "getVolume" -> volume
                "setVolume" -> { volume = args!![0] as Float; null }
                "replaceMediaItem" -> {
                    val at = args!![0] as Int
                    replacements += at
                    items[at] = args[1] as MediaItem
                    if (index == at) position = 0L
                    null
                }
                "seekTo" -> {
                    if (args!!.size == 1) position = args[0] as Long
                    else { index = args[0] as Int; position = args[1] as Long }
                    seeks += index to position
                    onSeek?.invoke()
                    null
                }
                "setMediaItem" -> {
                    items.clear()
                    items += args!![0] as MediaItem
                    index = 0
                    position = args[1] as Long
                    null
                }
                "setPlayWhenReady" -> { playing = args!![0] as Boolean; null }
                "play" -> { playing = true; null }
                "pause", "stop" -> { playing = false; null }
                "clearMediaItems" -> { items.clear(); null }
                "prepare", "release" -> null
                else -> error("Unexpected player call: ${method.name}")
            }
        } as ExoPlayer
    }

    private class Fixture(scope: CoroutineScope) : AutoCloseable {
        private val ownedScope = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]))
        val service = MusicService()
        val outgoing = TestPlayer()
        val incoming = TestPlayer()
        private var built = 0
        val gains = mutableMapOf<String, Float>()
        val engine = CrossfadeEngine(ownedScope, { if (built++ == 0) outgoing.player else incoming.player }, {}, {
            gains[it.currentMediaItem?.mediaId] ?: 1f
        })
        val resolutions = DeferredSingleFlight<String, MediaItem>(ownedScope)
        init {
            set("engine", engine)
            set("serviceScope", ownedScope)
            set("activeResolutions", resolutions)
            set("isNormalizeVolumeEnabled", false)
            set("isAutoMixEnabled", false)
            set("audioProfileStore", mock(AudioProfileStore::class.java))
        }
        fun set(name: String, value: Any) {
            MusicService::class.java.getDeclaredField(name).apply { isAccessible = true }.set(service, value)
        }
        fun get(name: String): Any? = MusicService::class.java.getDeclaredField(name).apply { isAccessible = true }.get(service)
        fun call(name: String, vararg args: Any) {
            MusicService::class.java.declaredMethods.single { it.name == name }.apply { isAccessible = true }.invoke(service, *args)
        }
        fun pending(id: String): CompletableDeferred<MediaItem> = CompletableDeferred<MediaItem>().also { result ->
            resolutions.getOrStart(id) { result.await() }
        }
        override fun close() {
            ownedScope.cancel()
            (get("fadeVolumeJob") as? Job)?.cancel()
            (get("resolveScope") as CoroutineScope).cancel()
            engine.release()
        }
    }

    @Test fun `repeated intercepted Next presses advance while all target resolutions are pending`() = runBlocking {
        Fixture(this).use { f ->
            f.outgoing.items += listOf(item("a", "a1"), item("b", "b1", true), item("c", "c1", true), item("d", "d1", true))
            f.pending("b")
            f.pending("c")
            f.pending("d")
            val callbackClass = MusicService::class.java.declaredClasses.single { it.simpleName == "LibrarySessionCallback" }
            val callback = callbackClass.getDeclaredConstructor(MusicService::class.java)
                .apply { isAccessible = true }.newInstance(f.service) as MediaSession.Callback
            val indices = mutableListOf<Int>()
            repeat(3) {
                assertEquals(androidx.media3.session.SessionResult.RESULT_ERROR_NOT_SUPPORTED,
                    callback.onPlayerCommandRequest(mock(MediaSession::class.java), mock(MediaSession.ControllerInfo::class.java), Player.COMMAND_SEEK_TO_NEXT))
                delay(100L) // Each press arrives well inside the old 1500ms wait.
                indices += f.outgoing.index
            }
            assertEquals(listOf(1, 2, 3), indices)
        }
    }

    @Test fun `repeated Previous presses advance backwards without waiting for extraction`() = runBlocking {
        Fixture(this).use { f ->
            f.outgoing.items += listOf(item("a", "a1", true), item("b", "b1", true), item("c", "c1"))
            f.outgoing.index = 2
            f.pending("a")
            f.pending("b")
            f.call("requestManualSkip", false, false)
            delay(100L)
            f.call("requestManualSkip", false, false)
            delay(100L)
            assertEquals(listOf(1 to 0L, 0 to 0L), f.outgoing.seeks)
        }
    }

    @Test fun `natural advance during the old resolution window cannot erase a Previous request`() = runBlocking {
        Fixture(this).use { f ->
            f.outgoing.items += listOf(item("a", "a1", true), item("b", "b1"), item("c", "c1"))
            f.outgoing.index = 1
            val pending = f.pending("a")
            f.call("requestManualSkip", false, false)
            delay(30L)
            f.outgoing.index = 2 // Outgoing end event during the former wait.
            pending.complete(item("a", "a1"))
            delay(50L)
            assertTrue("the user request must have selected a track", f.outgoing.seeks.isNotEmpty())
        }
    }

    @Test fun `manual fallback restores ducked gain without any media transition callback`() = runBlocking {
        Fixture(this).use { f ->
            f.outgoing.items += listOf(item("a", "a1"), item("b", "b1"))
            f.engine.duckGain = 0.4f
            f.call("requestManualTransition", 1)
            delay(30L)
            assertEquals(1, f.outgoing.index)
            assertTrue(f.outgoing.playing)
            assertFalse(f.engine.isFading)
            assertTrue((f.get("fadeVolumeJob") as? Job)?.isActive != true)
            assertEquals(0.4f, f.outgoing.volume, 0.001f)
        }
    }

    @Test fun `next at the end of a nonempty queue restarts instead of disappearing`() = runBlocking {
        Fixture(this).use { f ->
            f.outgoing.items += item("only", "only1")
            f.outgoing.position = 5_000L
            f.call("requestManualSkip", true, true)
            assertEquals(listOf(0 to 0L), f.outgoing.seeks)
            assertTrue(f.outgoing.playing)
        }
    }

    @Test fun `cancelled fade-in restores live gain when no other writer owns playback`() = runBlocking {
        Fixture(this).use { f ->
            f.outgoing.items += item("a", "a1")
            f.outgoing.playing = true
            f.engine.duckGain = 0.4f
            f.call("performSkipFadeIn")
            delay(35L)
            (f.get("fadeVolumeJob") as Job).cancel()
            delay(30L)
            assertEquals(0.4f, f.outgoing.volume, 0.001f)
        }
    }

    @Test fun `missing current item cannot strand a cancelled fade at partial volume`() = runBlocking {
        Fixture(this).use { f ->
            f.outgoing.playing = true
            f.outgoing.volume = 0f
            f.call("performSkipFadeIn")
            assertEquals(1f, f.outgoing.volume, 0.001f)
        }
    }

    @Test fun `manual selection uses playback order and distinct duplicate rows`() = runBlocking {
        Fixture(this).use { f ->
            f.outgoing.items += listOf(item("a", "a1"), item("b", "b1"), item("a", "a2"))
            f.outgoing.nextIndex = 2 // The player's shuffle successor, not index + 1.
            f.call("requestManualSkip", true, true)
            assertEquals(2, f.outgoing.index)
            assertEquals("a2", f.outgoing.player.currentMediaItem!!.queueItemId)
            f.outgoing.previousIndex = 1
            f.call("requestManualSkip", false, false)
            assertEquals(1, f.outgoing.index)
        }
    }

    @Test fun `manual Next overrides repeat one and Previous restart still takes effect`() = runBlocking {
        Fixture(this).use { f ->
            f.outgoing.items += listOf(item("a", "a1"), item("b", "b1"))
            f.outgoing.repeatMode = Player.REPEAT_MODE_ONE
            f.outgoing.nextIndex = 0
            f.call("requestManualSkip", true, true)
            assertEquals(1, f.outgoing.index)
            f.outgoing.position = 5_000L
            f.call("requestManualSkip", false, true)
            assertEquals(listOf(1 to 0L, 1 to 0L), f.outgoing.seeks)
            assertEquals(1f, f.outgoing.volume, 0.001f)
        }
    }

    @Test fun `placeholder selection and its item callback resolve the exact row at its latest seek`() = runBlocking {
        Fixture(this).use { f ->
            f.outgoing.items += listOf(item("a", "a1"), item("b", "b1", true))
            val pending = f.pending("b")
            val listenerClass = MusicService::class.java.declaredClasses.single { it.simpleName == "PlayerEventListener" }
            val listener = listenerClass.getDeclaredConstructor(MusicService::class.java)
                .apply { isAccessible = true }.newInstance(f.service) as Player.Listener
            f.outgoing.onSeek = {
                listener.onMediaItemTransition(f.outgoing.player.currentMediaItem, Player.MEDIA_ITEM_TRANSITION_REASON_SEEK)
            }
            f.call("requestManualTransition", 1)
            assertEquals(1, f.outgoing.index)
            f.outgoing.onSeek = null
            delay(20L)
            f.outgoing.position = 7_000L
            f.outgoing.playing = false
            pending.complete(item("b", "winning-flight"))
            withTimeout(2_000L) {
                while (f.outgoing.replacements.isEmpty()) delay(1L)
                (f.get("fadeVolumeJob") as? Job)?.join()
            }
            assertEquals("b1", f.outgoing.items[1].queueItemId)
            assertEquals(7_000L, f.outgoing.position)
            assertFalse(f.outgoing.playing)
            assertEquals(1f, f.outgoing.volume, 0.001f)
            assertNull(f.get("fadeVolumeJob"))
        }
    }

    @Test fun `old fade cleanup cannot overwrite a newer volume writer`() = runBlocking {
        Fixture(this).use { f ->
            f.outgoing.items += item("a", "a1")
            f.call("performSkipFadeIn")
            val old = f.get("fadeVolumeJob") as Job
            f.call("performSkipFadeIn")
            val next = f.get("fadeVolumeJob") as Job
            old.join()
            assertSame(next, f.get("fadeVolumeJob"))
            next.join()
            assertNull(f.get("fadeVolumeJob"))
            assertEquals(1f, f.outgoing.volume, 0.001f)
        }
    }

    @Test fun `fade cleanup leaves engine-owned overlap volumes alone`() = runBlocking {
        Fixture(this).use { f ->
            f.outgoing.items += listOf(item("a", "a1"), item("b", "b1"))
            f.outgoing.playing = true
            f.call("performSkipFadeIn")
            val fade = f.get("fadeVolumeJob") as Job
            assertTrue(f.engine.startTransition(f.outgoing.items[1], 500L, targetIndex = 1))
            f.outgoing.volume = 0.37f
            fade.join()
            assertTrue(f.engine.isFading)
            assertEquals(0.37f, f.outgoing.volume, 0.001f)
        }
    }

    @Test fun `manual skip with crossfade off remains immediate and at full gain`() = runBlocking {
        Fixture(this).use { f ->
            f.set("isCrossfadeEnabled", false)
            f.outgoing.items += listOf(item("a", "a1"), item("b", "b1", true))
            f.call("requestManualTransition", 1)
            assertEquals(listOf(1 to 0L), f.outgoing.seeks)
            assertEquals(1f, f.outgoing.volume, 0.001f)
            assertNull(f.get("fadeVolumeJob"))
        }
    }

    @Test fun `external controller duplicate rows and repeated requests acquire distinct occurrences`() = runBlocking {
        Fixture(this).use { f ->
            mockConstruction(Bundle::class.java) { bundle, _ ->
                val strings = mutableMapOf<String, String>()
                doAnswer { call -> strings[call.arguments[0] as String] = call.arguments[1] as String; null }
                    .`when`(bundle).putString(anyString(), anyString())
                doAnswer { call -> strings[call.arguments[0] as String] }
                    .`when`(bundle).getString(anyString())
            }.use {
                val callbackClass = MusicService::class.java.declaredClasses.single { it.simpleName == "LibrarySessionCallback" }
                val callback = callbackClass.getDeclaredConstructor(MusicService::class.java)
                    .apply { isAccessible = true }.newInstance(f.service)
                val prepare = callbackClass.getDeclaredMethod("preparePlaybackItem", MediaItem::class.java)
                    .apply { isAccessible = true }
                val external = MediaItem.Builder().setMediaId("same")
                    .setMediaMetadata(MediaMetadata.Builder().setTitle("same").build()).build()
                val first = prepare.invoke(callback, external) as MediaItem
                val duplicate = prepare.invoke(callback, external) as MediaItem
                val retapped = prepare.invoke(callback, external) as MediaItem
                assertNotNull("external items need occurrence IDs too", first.queueItemId)
                assertFalse(first.isSameQueueItemAs(duplicate))
                assertFalse(first.isSameQueueItemAs(retapped))
            }
        }
    }

    @Test fun `queued playback resumption refuses a player that acquired a new queue`() = runBlocking {
        Fixture(this).use { f ->
            // Run the old IO future on this test dispatcher so the file read is
            // held until another controller has installed its queue.
            val originalScope = f.get("resolveScope")!!
            f.set("resolveScope", this)
            val preferences = mock(ThemePreferences::class.java)
            `when`(preferences.getLastPlayedSong()).thenReturn(Song(id = "saved", title = "Saved", artist = "Artist", album = "", duration = 10_000L))
            f.set("themePreferences", preferences)
            mockConstruction(PlaybackSessionRepository::class.java).use {
                val session = mock(MediaSession::class.java)
                `when`(session.player).thenReturn(f.outgoing.player)
                val callbackClass = MusicService::class.java.declaredClasses.single { it.simpleName == "LibrarySessionCallback" }
                val callback = callbackClass.getDeclaredConstructor(MusicService::class.java)
                    .apply { isAccessible = true }.newInstance(f.service)
                val resume = callbackClass.getDeclaredMethod("onPlaybackResumption", MediaSession::class.java, MediaSession.ControllerInfo::class.java, Boolean::class.javaPrimitiveType)
                    .apply { isAccessible = true }
                val future = resume.invoke(callback, session, mock(MediaSession.ControllerInfo::class.java), true) as ListenableFuture<*>
                f.set("resolveScope", originalScope)
                f.outgoing.items += item("chosen", "chosen1")
                delay(20)
                assertTrue(future.isDone)
                try {
                    future.get()
                    fail("Media3 would apply this stale saved queue over the new selection")
                } catch (_: ExecutionException) {
                    // Media3 only applies a resumption queue on success.
                }
            }
        }
    }

    @Test fun `metadata-only resumption still returns saved metadata with an occupied player`() = runBlocking {
        Fixture(this).use { f ->
            val originalScope = f.get("resolveScope")!!
            val preferences = mock(ThemePreferences::class.java)
            `when`(preferences.getLastPlayedSong()).thenReturn(Song(id = "saved", title = "Saved", artist = "Artist", album = "", duration = 10_000L))
            f.set("themePreferences", preferences)
            mockConstruction(PlaybackSessionRepository::class.java).use {
                val session = mock(MediaSession::class.java)
                `when`(session.player).thenReturn(f.outgoing.player)
                f.outgoing.items += item("chosen", "chosen1")
                val callbackClass = MusicService::class.java.declaredClasses.single { it.simpleName == "LibrarySessionCallback" }
                val callback = callbackClass.getDeclaredConstructor(MusicService::class.java)
                    .apply { isAccessible = true }.newInstance(f.service) as MediaSession.Callback
                f.set("resolveScope", this)
                val future = callback.onPlaybackResumption(session, mock(MediaSession.ControllerInfo::class.java), false)
                f.set("resolveScope", originalScope)
                delay(20)
                assertTrue(future.isDone)
                assertEquals("saved", future.get().mediaItems.single().mediaId)
                assertEquals("chosen1", f.outgoing.items.single().queueItemId)
            }
        }
    }

    @Test fun `403 recovery cannot reset a replacement queue after visitor refresh suspends`() = runBlocking {
        Fixture(this).use { f ->
            val failed = item("a", "a1")
            `when`(failed.localConfiguration!!.uri.getQueryParameter("c")).thenReturn("IOS")
            f.outgoing.items += listOf(failed, item("b", "b1"))
            val repository = mock(YouTubeRepository::class.java)
            f.set("youtubeRepository", repository)
            f.set("activeResolutions", mock(DeferredSingleFlight::class.java) { call ->
                when (call.method.name) {
                    "contains" -> false
                    "getOrStart" -> CompletableDeferred(failed)
                    else -> null
                }
            })
            val refreshing = CompletableDeferred<Continuation<Unit>>()
            doAnswer { call ->
                @Suppress("UNCHECKED_CAST")
                refreshing.complete(call.rawArguments.last() as Continuation<Unit>)
                COROUTINE_SUSPENDED
            }.`when`(repository).refreshVisitorDataAfterPlaybackFailure()
            val cause = mock(HttpDataSource.InvalidResponseCodeException::class.java)
            HttpDataSource.InvalidResponseCodeException::class.java.getField("responseCode").apply { isAccessible = true }.setInt(cause, 403)
            f.call("handlePlayerError", PlaybackException("403", cause, PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS))
            val continuation = refreshing.await()
            f.outgoing.items[0] = item("a", "a2")
            f.outgoing.items[1] = item("b", "b2")
            continuation.resume(Unit)
            delay(50)
            assertTrue("stale recovery reset the new queue's stream", f.outgoing.replacements.isEmpty())
        }
    }

    @Test fun `manual skip commits before replacement and late resolution cannot jump again`() = runBlocking {
        Fixture(this).use { f ->
            f.outgoing.items += listOf(item("a", "a1"), item("b", "b1", true))
            val pending = f.pending("b")
            f.call("requestManualTransition", 1)
            delay(20)
            f.outgoing.items[0] = item("a", "a2")
            pending.complete(item("b", "b1"))
            delay(50)
            assertTrue("stale manual request replaced a source", f.outgoing.replacements.isEmpty())
            assertEquals("selection must happen once, before resolution", listOf(1 to 0L), f.outgoing.seeks)
        }
    }

    @Test fun `selected manual target retains a later seek when extraction finishes`() = runBlocking {
        Fixture(this).use { f ->
            f.outgoing.items += listOf(item("a", "a1"), item("b", "b1", true))
            val pending = f.pending("b")
            f.call("requestManualTransition", 1)
            delay(20)
            f.outgoing.index = 1
            f.outgoing.position = 4_000L
            pending.complete(item("b", "b1"))
            delay(50)
            assertEquals(4_000L, f.outgoing.position)
            assertEquals(listOf(1 to 0L), f.outgoing.seeks)
        }
    }

    @Test fun `a later row tap supersedes a committed skip while its extraction is pending`() = runBlocking {
        Fixture(this).use { f ->
            f.outgoing.items += listOf(item("a", "a1"), item("b", "b1", true))
            val pending = f.pending("b")
            f.call("requestManualTransition", 1)
            delay(20)
            f.call("requestManualTransition", 0)
            pending.complete(item("b", "b1"))
            delay(50)
            assertEquals(0, f.outgoing.index)
            assertTrue(f.outgoing.replacements.isEmpty())
        }
    }

    @Test fun `controller no-op seek cannot revive a completed manual skip after extraction`() = runBlocking {
        Fixture(this).use { f ->
            f.outgoing.items += listOf(item("a", "a1"), item("b", "b1", true))
            val pending = f.pending("b")
            f.call("requestManualTransition", 1)
            delay(20)
            val callbackClass = MusicService::class.java.declaredClasses.single { it.simpleName == "LibrarySessionCallback" }
            val callback = callbackClass.getDeclaredConstructor(MusicService::class.java)
                .apply { isAccessible = true }.newInstance(f.service) as MediaSession.Callback
            callback.onPlayerCommandRequest(mock(MediaSession::class.java), mock(MediaSession.ControllerInfo::class.java), Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
            pending.complete(item("b", "b1"))
            delay(50)
            assertEquals(1, f.outgoing.index)
            assertEquals(listOf(1 to 0L), f.outgoing.seeks)
            assertTrue(f.outgoing.replacements.isEmpty())
        }
    }

    @Test fun `validation ignores a retapped occurrence at the same index`() = runBlocking {
        Fixture(this).use { f ->
            val original = item("a", "a1", true)
            f.outgoing.items += original
            val pending = f.pending("a")
            f.call("validateAndPlayCurrentItem", original)
            delay(20)
            f.outgoing.items[0] = item("a", "a2", true)
            f.outgoing.position = 8_000L
            pending.complete(item("a", "winner"))
            delay(50)
            assertTrue(f.outgoing.replacements.isEmpty())
            assertEquals(8_000L, f.outgoing.position)
        }
    }

    @Test fun `validation follows an occurrence through player handoff and retains its latest seek and pause`() = runBlocking {
        Fixture(this).use { f ->
            val original = item("a", "a1", true)
            f.outgoing.items += original
            val pending = f.pending("a")
            f.call("validateAndPlayCurrentItem", original)
            delay(20)
            f.incoming.items += original
            f.incoming.position = 7_000L
            f.incoming.playing = false
            CrossfadeEngine::class.java.getDeclaredField("active").apply { isAccessible = true }.set(f.engine, f.incoming.player)
            pending.complete(item("a", "winner"))
            delay(50)
            assertTrue(f.outgoing.replacements.isEmpty())
            assertEquals(listOf(0), f.incoming.replacements)
            assertEquals("a1", f.incoming.items[0].queueItemId)
            assertEquals(7_000L, f.incoming.position)
            assertFalse(f.incoming.playing)
        }
    }

    @Test fun `prefetch resolves both duplicate occurrences from one flight`() = runBlocking {
        Fixture(this).use { f ->
            f.outgoing.items += listOf(item("a", "a1"), item("b", "b1", true), item("b", "b2", true))
            val pending = f.pending("b")
            f.call("prefetchUpcomingSongs")
            delay(20)
            pending.complete(item("b", "winner"))
            delay(50)
            assertEquals(listOf(1, 2), f.outgoing.replacements)
            assertEquals("b1", f.outgoing.items[1].queueItemId)
            assertEquals("b2", f.outgoing.items[2].queueItemId)
        }
    }

    @Test fun `old prefetch cannot suppress warming the same track in a replacement queue`() = runBlocking {
        Fixture(this).use { f ->
            f.outgoing.items += listOf(item("a", "a1"), item("b", "b1", true))
            val pending = f.pending("b")
            f.call("prefetchUpcomingSongs")
            delay(20)
            f.outgoing.items[0] = item("a", "a2")
            f.outgoing.items[1] = item("b", "b2", true)
            f.call("prefetchUpcomingSongs")
            delay(20)
            pending.complete(item("b", "winner"))
            delay(50)
            assertEquals(listOf(1), f.outgoing.replacements)
            assertEquals("b2", f.outgoing.items[1].queueItemId)
        }
    }

    @Test fun `a stale fade restores the current occurrence gain without writing the old gain`() = runBlocking {
        Fixture(this).use { f ->
            f.outgoing.items += item("a", "a1")
            f.call("performSkipFadeIn")
            delay(35)
            f.gains["b"] = 0.6f
            f.outgoing.items[0] = item("b", "b1")
            f.outgoing.playing = true
            f.engine.duckGain = 0.4f
            f.outgoing.volume = 0.1f
            delay(350)
            assertEquals(0.24f, f.outgoing.volume, 0.001f)
            assertNull(f.get("fadeVolumeJob"))
        }
    }
}
