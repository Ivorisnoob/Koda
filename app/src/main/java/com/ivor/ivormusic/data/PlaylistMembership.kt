package com.ivor.ivormusic.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Which of the account's playlists hold the item an add-to-playlist sheet was
 * opened on, for its check marks.
 *
 * One `playlist/get_add_to_playlist` per sheet open, never a browse per
 * playlist. That answer is eventually consistent [verified September 2026:
 * an add still read NONE straight away and ALL fifteen seconds later], so
 * the app's own recent writes are laid over it until the server has caught up;
 * without that, a tick would undo itself on the next open.
 */
class PlaylistMembership(
    private val youtube: YouTubeRepository,
    private val scope: CoroutineScope,
    private val now: () -> Long = { android.os.SystemClock.elapsedRealtime() },
) {
    private val overlay = PlaylistMembershipOverlay(now)
    private val videoId = MutableStateFlow<String?>(null)
    private val server = MutableStateFlow<Set<String>>(emptySet())
    private val revision = MutableStateFlow(0)
    private val _loading = MutableStateFlow(false)
    private var job: Job? = null

    /** True while the account's answer for the open item is on its way. */
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    /** Account playlist ids holding the open item, own writes included. */
    val containing: StateFlow<Set<String>> =
        combine(videoId, server, revision) { id, fromServer, _ ->
            if (id == null) emptySet() else overlay.apply(id, fromServer)
        }.stateIn(scope, SharingStarted.Eagerly, emptySet())

    /** Ask once for [id]; signed out there is no account half to ask about. */
    fun load(id: String) {
        job?.cancel()
        videoId.value = id
        server.value = emptySet()
        _loading.value = false
        if (!youtube.isLoggedIn()) return
        _loading.value = true
        job = scope.launch {
            try {
                server.value = youtube.getPlaylistsContaining(id).orEmpty()
            } finally {
                if (videoId.value == id) _loading.value = false
            }
        }
    }

    /** A write the app just made, trusted over the server for a minute. */
    fun record(playlistId: String, id: String, contains: Boolean) {
        overlay.record(playlistId, id, contains)
        revision.value++
    }

    /** A write that failed: fall back to what the server said. */
    fun forget(playlistId: String, id: String) {
        overlay.forget(playlistId, id)
        revision.value++
    }
}

/** Recent writes, newest wins, each trusted for [ttlMs]. Pure, so it is tested. */
internal class PlaylistMembershipOverlay(
    private val now: () -> Long,
    private val ttlMs: Long = 60_000L,
) {
    private val writes = HashMap<Pair<String, String>, Pair<Boolean, Long>>()

    fun record(playlistId: String, videoId: String, contains: Boolean) {
        writes[playlistId to videoId] = contains to now()
    }

    fun forget(playlistId: String, videoId: String) {
        writes.remove(playlistId to videoId)
    }

    fun apply(videoId: String, server: Set<String>): Set<String> {
        val cutoff = now() - ttlMs
        writes.entries.removeAll { it.value.second < cutoff }
        val result = server.toMutableSet()
        for ((key, value) in writes) {
            if (key.second != videoId) continue
            if (value.first) result += key.first else result -= key.first
        }
        return result
    }
}

/**
 * Playlist ids the www `playlist/get_add_to_playlist` answer marks as holding
 * the requested video. Only the www host reports it; WEB_REMIX omits the field.
 */
internal fun parsePlaylistsContaining(raw: String): Set<String>? {
    val root = runCatching { JSONObject(raw) }.getOrNull() ?: return null
    val found = LinkedHashSet<String>()
    fun walk(node: Any?) {
        when (node) {
            is JSONObject -> {
                node.optJSONObject("playlistAddToOptionRenderer")?.let { option ->
                    val id = option.optString("playlistId")
                    val contains = option.optString("containsSelectedVideos")
                    if (id.isNotBlank() && (contains == "ALL" || contains == "SOME")) found += id
                }
                node.keys().forEach { walk(node.opt(it)) }
            }
            is JSONArray -> for (i in 0 until node.length()) walk(node.opt(i))
        }
    }
    walk(root)
    return found
}
