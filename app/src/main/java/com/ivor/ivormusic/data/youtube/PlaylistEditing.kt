package com.ivor.ivormusic.data.youtube

import com.ivor.ivormusic.data.SessionManager
import com.ivor.ivormusic.data.parsePlaylistsContaining
import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** How an upload went: [uploaded] of [total] songs reached [playlistId]. */
data class PlaylistUpload(val playlistId: String, val uploaded: Int, val total: Int)

/**
 * Writes to the account's playlists: create, rename, delete, add, remove,
 * reorder, save to library.
 *
 * The same InnerTube write endpoints exist on both hosts. `music = true` goes
 * through music.youtube.com (WEB_REMIX) so the edit lands in the YouTube
 * Music library, `music = false` through www.youtube.com (WEB) for video
 * playlists and Watch Later. These are the long-stable action-based writes;
 * a response is only checked for `STATUS_SUCCEEDED` or a playlist id, never
 * deep-parsed.
 */
internal class PlaylistEditing(
    private val webApi: WebApi,
    private val musicApi: MusicApi,
    private val sessionManager: SessionManager,
) {
    /**
     * Create a playlist, optionally with initial videos. Returns the new
     * playlist id or null. Requires login.
     */
    suspend fun createYouTubePlaylist(
        title: String,
        music: Boolean,
        videoIds: List<String> = emptyList()
    ): String? = withContext(Dispatchers.IO) {
        if (!sessionManager.isLoggedIn()) return@withContext null
        val body = JSONObject()
            .put("context", playlistContext(music))
            .put("title", title)
        if (videoIds.isNotEmpty()) {
            body.put("videoIds", JSONArray(videoIds))
        }
        val raw = postPlaylistApi(music, "playlist/create", body) ?: return@withContext null
        try {
            JSONObject(raw).optString("playlistId").takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Copy a playlist onto the YouTube Music account as a new private playlist.
     *
     * Batched so a long playlist costs a handful of writes, not one per song:
     * `playlist/create` carries the first [UPLOAD_CREATE_BATCH] ids and each
     * `edit_playlist` carries [UPLOAD_ADD_BATCH] add actions [verified September
     * 2026: a 100-id create and a 50-action edit both landed every row]. A
     * failed batch stops the upload and reports how far it got, rather than
     * retrying writes against an account. Null when nothing was created.
     */
    suspend fun uploadPlaylist(title: String, description: String?, videoIds: List<String>): PlaylistUpload? =
        withContext(Dispatchers.IO) {
            if (!sessionManager.isLoggedIn() || videoIds.isEmpty()) return@withContext null
            val first = videoIds.take(UPLOAD_CREATE_BATCH)
            val createBody = JSONObject()
                .put("context", musicApi.musicContext())
                .put("title", title)
                .put("privacyStatus", "PRIVATE")
                .put("videoIds", JSONArray(first))
            val playlistId = musicApi.postMusicApi("playlist/create", createBody)
                ?.let { runCatching { JSONObject(it).optString("playlistId") }.getOrNull() }
                ?.takeIf { it.isNotBlank() }
                ?: return@withContext null
            var uploaded = first.size
            for (batch in videoIds.drop(UPLOAD_CREATE_BATCH).chunked(UPLOAD_ADD_BATCH)) {
                kotlinx.coroutines.delay(UPLOAD_BATCH_PAUSE_MS)
                val actions = JSONArray()
                batch.forEach {
                    actions.put(JSONObject().put("action", "ACTION_ADD_VIDEO").put("addedVideoId", it))
                }
                val body = JSONObject()
                    .put("context", musicApi.musicContext())
                    .put("playlistId", playlistId)
                    .put("actions", actions)
                if (!editStatusOk(musicApi.postMusicApi("browse/edit_playlist", body))) break
                uploaded += batch.size
            }
            if (!description.isNullOrBlank()) {
                renameYouTubePlaylist(playlistId, title, music = true, description = description)
            }
            PlaylistUpload(playlistId, uploaded, videoIds.size)
        }

    /**
     * Rename a playlist (and optionally replace its description). Only works
     * on playlists the user owns. Requires login.
     */
    suspend fun renameYouTubePlaylist(
        playlistId: String,
        title: String,
        music: Boolean,
        description: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        if (!sessionManager.isLoggedIn()) return@withContext false
        val actions = JSONArray().put(
            JSONObject()
                .put("action", "ACTION_SET_PLAYLIST_NAME")
                .put("playlistName", title)
        )
        if (description != null) {
            actions.put(
                JSONObject()
                    .put("action", "ACTION_SET_PLAYLIST_DESCRIPTION")
                    .put("playlistDescription", description)
            )
        }
        val body = JSONObject()
            .put("context", playlistContext(music))
            .put("playlistId", normalizePlaylistId(playlistId))
            .put("actions", actions)
        editStatusOk(postPlaylistApi(music, "browse/edit_playlist", body))
    }

    suspend fun deleteYouTubePlaylist(playlistId: String, music: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            if (!sessionManager.isLoggedIn()) return@withContext false
            val id = normalizePlaylistId(playlistId)
            val body = JSONObject()
                .put("context", playlistContext(music))
                .put("playlistId", id)
            if (postPlaylistApi(music, "playlist/delete", body) != null) return@withContext true
            val unlikeBody = JSONObject()
                .put("context", playlistContext(music))
                .put("target", JSONObject().put("playlistId", id))
            postPlaylistApi(music, "like/removelike", unlikeBody) != null
        }

    /**
     * Delete a playlist. playlist/delete only works on playlists the user
     * owns; for saved (someone else's) playlists it fails, so fall back to
     * removing the playlist from the library instead. Requires login.
     */
    /** Add or remove someone else's playlist from the account's YouTube Music library. */
    suspend fun setPlaylistInLibrary(playlistId: String, saved: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            if (!sessionManager.isLoggedIn()) return@withContext false
            val body = JSONObject()
                .put("context", playlistContext(true))
                .put("target", JSONObject().put("playlistId", normalizePlaylistId(playlistId)))
            postPlaylistApi(true, if (saved) "like/like" else "like/removelike", body) != null
        }

    /**
     * Add a video/song to a playlist ("WL" adds to Watch Later). Requires login.
     */
    suspend fun addToYouTubePlaylist(
        playlistId: String,
        videoId: String,
        music: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        if (!sessionManager.isLoggedIn()) return@withContext false
        val body = JSONObject()
            .put("context", playlistContext(music))
            .put("playlistId", normalizePlaylistId(playlistId))
            .put(
                "actions",
                JSONArray().put(
                    JSONObject()
                        .put("action", "ACTION_ADD_VIDEO")
                        .put("addedVideoId", videoId)
                )
            )
        editStatusOk(postPlaylistApi(music, "browse/edit_playlist", body))
    }

    /**
     * Remove a video/song from a playlist. Works for Watch Later ("WL");
     * the liked lists ("LL" videos, "LM" music) are not editable playlists —
     * removing from them means removing the like. Requires login.
     */
    suspend fun removeFromYouTubePlaylist(
        playlistId: String,
        videoId: String,
        music: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        if (!sessionManager.isLoggedIn()) return@withContext false
        val id = normalizePlaylistId(playlistId)
        if (id == "LL" || id == "LM") {
            val body = JSONObject()
                .put("context", playlistContext(music))
                .put("target", JSONObject().put("videoId", videoId))
            return@withContext postPlaylistApi(music, "like/removelike", body) != null
        }
        val body = JSONObject()
            .put("context", playlistContext(music))
            .put("playlistId", id)
            .put(
                "actions",
                JSONArray().put(
                    JSONObject()
                        .put("action", "ACTION_REMOVE_VIDEO_BY_VIDEO_ID")
                        .put("removedVideoId", videoId)
                )
            )
        editStatusOk(postPlaylistApi(music, "browse/edit_playlist", body))
    }

    /**
     * Move a playlist row before another row (or to the end when
     * successorSetVideoId is null). Rows are addressed by their setVideoId
     * from getPlaylistSetVideoIds. The anchor field is
     * "movedSetVideoIdSuccessor" — the "movedSetVideoId" name some client
     * libraries document is silently ignored and drops the row to the end
     * with STATUS_SUCCEEDED. Requires login. Verified July 2026.
     */
    suspend fun moveInYouTubePlaylist(
        playlistId: String,
        setVideoId: String,
        successorSetVideoId: String?,
        music: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        if (!sessionManager.isLoggedIn()) return@withContext false
        val action = JSONObject()
            .put("action", "ACTION_MOVE_VIDEO_BEFORE")
            .put("setVideoId", setVideoId)
        if (successorSetVideoId != null) {
            action.put("movedSetVideoIdSuccessor", successorSetVideoId)
        }
        val body = JSONObject()
            .put("context", playlistContext(music))
            .put("playlistId", normalizePlaylistId(playlistId))
            .put("actions", JSONArray().put(action))
        editStatusOk(postPlaylistApi(music, "browse/edit_playlist", body))
    }

    /**
     * The account playlists holding [videoId], from one www
     * `playlist/get_add_to_playlist` (only www reports membership). Eventually
     * consistent - see [PlaylistMembership]. Null signed out or on failure.
     */
    suspend fun getPlaylistsContaining(videoId: String): Set<String>? = withContext(Dispatchers.IO) {
        if (!sessionManager.isLoggedIn()) return@withContext null
        val body = JSONObject()
            .put("context", webApi.webContext())
            .put("videoIds", JSONArray().put(videoId))
            .put("excludeWatchLater", false)
        webApi.postWatchApi("playlist/get_add_to_playlist", body)?.let(::parsePlaylistsContaining)
    }

    /**
     * Fetch the per-row playlist item ids ("setVideoId") for a playlist the
     * user can edit. Reordering via edit_playlist identifies rows by these,
     * not by videoId. Values stay occurrence-ordered because duplicate videos
     * are separate rows with separate setVideoIds. Browses VL<id> on music.youtube.com and reads
     * musicResponsiveListItemRenderer.playlistItemData across every playlist
     * continuation. An incomplete map cannot safely address duplicate rows,
     * so a failed/repeated continuation returns no map. Verified September 2026.
     */
    suspend fun getPlaylistSetVideoIds(playlistId: String): Map<String, List<String>> =
        withContext(Dispatchers.IO) {
            if (!sessionManager.isLoggedIn()) return@withContext emptyMap()
            val browseId = if (playlistId.startsWith("VL")) playlistId else "VL$playlistId"
            var raw = musicApi.browseMusic(browseId) ?: return@withContext emptyMap()
            try {
                val idsByVideo = linkedMapOf<String, MutableList<String>>()
                val seenTokens = mutableSetOf<String>()
                while (true) {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    if (raw.isBlank()) return@withContext emptyMap()
                    val root = JSONObject(raw)
                    if (root.has("error")) return@withContext emptyMap()
                    val rows = mutableListOf<JSONObject>()
                    findObjectsByKey(root, "musicResponsiveListItemRenderer", rows)
                    for (row in rows) {
                        val itemData = row.optJSONObject("playlistItemData") ?: continue
                        val videoId = itemData.optString("videoId").takeIf { it.isNotBlank() } ?: continue
                        val setVideoId = itemData.optString("playlistSetVideoId")
                            .takeIf { it.isNotBlank() } ?: continue
                        idsByVideo.getOrPut(videoId) { mutableListOf() }.add(setVideoId)
                    }
                    val token = extractPlaylistContinuationToken(raw) ?: break
                    if (!seenTokens.add(token)) {
                        KLog.w(YOUTUBE_TAG, "Repeated playlist row-id continuation for $playlistId")
                        return@withContext emptyMap()
                    }
                    raw = musicApi.fetchContinuation(token)
                }
                idsByVideo.mapValues { (_, ids) -> ids.toList() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                KLog.e(YOUTUBE_TAG, "getPlaylistSetVideoIds failed", e)
                emptyMap()
            }
        }

    private fun postPlaylistApi(music: Boolean, endpoint: String, body: JSONObject): String? =
        if (music) musicApi.postMusicApi(endpoint, body) else webApi.postWatchApi(endpoint, body)

    private fun playlistContext(music: Boolean): JSONObject =
        if (music) musicApi.musicContext() else webApi.webContext()

    /** Playlist ids sometimes carry the VL browse prefix — edit calls need it stripped. */
    private fun normalizePlaylistId(playlistId: String): String = playlistId.removePrefix("VL")

    /** edit_playlist responses report success in a top-level status field. */
    private fun editStatusOk(raw: String?): Boolean {
        if (raw == null) return false
        return try {
            JSONObject(raw).optString("status") == "STATUS_SUCCEEDED"
        } catch (e: Exception) {
            false
        }
    }

    private companion object {
        private const val UPLOAD_CREATE_BATCH = 100

        private const val UPLOAD_ADD_BATCH = 50

        private const val UPLOAD_BATCH_PAUSE_MS = 400L
    }
}
