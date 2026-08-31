package com.ivor.ivormusic.ui.tv

import android.app.Application
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.ivor.ivormusic.data.tv.TvAutoSelectProfile
import com.ivor.ivormusic.data.tv.TvEpisode
import com.ivor.ivormusic.data.tv.TvItem
import com.ivor.ivormusic.data.tv.TvProgressRepository
import com.ivor.ivormusic.data.tv.TvSource
import com.ivor.ivormusic.data.tv.TvSourceKind
import com.ivor.ivormusic.data.tv.TvStreamRepository
import com.ivor.ivormusic.data.tv.TvSubtitleTrack
import com.ivor.ivormusic.data.tv.StremioClient
import com.ivor.ivormusic.data.ThemePreferences
import com.ivor.ivormusic.data.VttCue
import com.ivor.ivormusic.data.WebVttParser
import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What is on screen: a film, or one episode of something. */
data class TvPlayback(
    val item: TvItem,
    val episode: TvEpisode?,
    val source: TvSource,
) {
    /** The addon-facing id, which is also what progress is keyed on. */
    val streamId: String get() = episode?.id ?: item.id

    val title: String get() = item.name

    val subtitle: String?
        get() = episode?.let { ep ->
            val code = if (ep.season != null && ep.episodeNumber != null) {
                "S" + ep.season + "E" + ep.episodeNumber
            } else null
            listOfNotNull(code, ep.displayTitle.takeIf { it.isNotBlank() }).joinToString("  ")
                .takeIf { it.isNotBlank() }
        }
}

/** One selectable audio track in the playing file. */
data class TvAudioTrack(
    val groupIndex: Int,
    val trackIndex: Int,
    val label: String,
    val language: String?,
    val isSelected: Boolean,
    val isSupported: Boolean,
)

/** One subtitle track muxed inside the playing file. */
data class TvEmbeddedSubtitleTrack(
    val groupIndex: Int,
    val trackIndex: Int,
    val label: String,
    val language: String?,
    val isSelected: Boolean,
    val isSupported: Boolean,
)

/** Why playback stopped, in terms a viewer can act on. */
enum class TvPlaybackProblem {
    SOURCE_FAILED,
    NETWORK_FAILED,
    FORMAT_UNSUPPORTED,
    DECODER_FAILED,
    NO_SUPPORTED_AUDIO,
    NO_NEXT_SOURCE,
}

/**
 * TV playback.
 *
 * **Its own ExoPlayer and its own overlay, deliberately, and it deviates from
 * `plan.md` section 3 in doing so.** That section proposed reusing
 * `VideoPlayerOverlay` behind a discriminator; carrying it out means extracting
 * an interface across a 3,375-line ViewModel and the 2,044-line content
 * composable that reads dozens of its members, on the app's most-used surface.
 * The cost the plan wanted to avoid was triplicated z-order and step-aside
 * logic, and that cost is avoided a cheaper way: **this player is strictly
 * full-screen with no mini bar**, so it has no expand animation, no nav-bar
 * interaction and no step-aside path - it is one boolean above the NavHost.
 *
 * The consequence, stated rather than hidden: you cannot browse while a film
 * plays. That is a real loss for a series and a small one for a film, and the
 * mini bar is the obvious phase-4 addition if it turns out to matter.
 *
 * **Nothing here writes to the shared `SimpleCache`.** A single film is larger
 * than the whole cache budget, so caching one would evict every downloaded song
 * and still not fit the film. Music and YouTube video keep the cache to
 * themselves.
 */
@OptIn(UnstableApi::class)
class TvPlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val streamRepository = TvStreamRepository(application)
    private val stremioClient = StremioClient(application)
    private val progressRepository = TvProgressRepository(application)
    private val themePreferences = ThemePreferences(application)

    private val _playback = MutableStateFlow<TvPlayback?>(null)
    val playback: StateFlow<TvPlayback?> = _playback.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _bufferedMs = MutableStateFlow(0L)
    val bufferedMs: StateFlow<Long> = _bufferedMs.asStateFlow()

    private val _audioTracks = MutableStateFlow<List<TvAudioTrack>>(emptyList())
    val audioTracks: StateFlow<List<TvAudioTrack>> = _audioTracks.asStateFlow()

    private val _subtitleTracks = MutableStateFlow<List<TvSubtitleTrack>>(emptyList())
    val subtitleTracks: StateFlow<List<TvSubtitleTrack>> = _subtitleTracks.asStateFlow()

    private val _selectedSubtitle = MutableStateFlow<TvSubtitleTrack?>(null)
    val selectedSubtitle: StateFlow<TvSubtitleTrack?> = _selectedSubtitle.asStateFlow()

    private val _embeddedSubtitleTracks =
        MutableStateFlow<List<TvEmbeddedSubtitleTrack>>(emptyList())
    val embeddedSubtitleTracks: StateFlow<List<TvEmbeddedSubtitleTrack>> =
        _embeddedSubtitleTracks.asStateFlow()

    private val _selectedEmbeddedSubtitle = MutableStateFlow<TvEmbeddedSubtitleTrack?>(null)
    val selectedEmbeddedSubtitle: StateFlow<TvEmbeddedSubtitleTrack?> =
        _selectedEmbeddedSubtitle.asStateFlow()

    private val _subtitleCues = MutableStateFlow<List<VttCue>>(emptyList())
    val subtitleCues: StateFlow<List<VttCue>> = _subtitleCues.asStateFlow()

    private val _isSubtitlesLoading = MutableStateFlow(false)
    val isSubtitlesLoading: StateFlow<Boolean> = _isSubtitlesLoading.asStateFlow()

    private val _subtitleLoadFailed = MutableStateFlow(false)
    val subtitleLoadFailed: StateFlow<Boolean> = _subtitleLoadFailed.asStateFlow()

    val captionTextSize = themePreferences.captionTextSize
    val captionTextColor = themePreferences.captionTextColor
    val captionBackground = themePreferences.captionBackground

    private val _problem = MutableStateFlow<TvPlaybackProblem?>(null)
    val problem: StateFlow<TvPlaybackProblem?> = _problem.asStateFlow()

    private val _nextEpisode = MutableStateFlow<TvEpisode?>(null)
    val nextEpisode: StateFlow<TvEpisode?> = _nextEpisode.asStateFlow()

    /** Seconds left on the up-next card, or null when it is not showing. */
    private val _autoplayCountdown = MutableStateFlow<Int?>(null)
    val autoplayCountdown: StateFlow<Int?> = _autoplayCountdown.asStateFlow()

    private val _isAdvancing = MutableStateFlow(false)
    val isAdvancing: StateFlow<Boolean> = _isAdvancing.asStateFlow()

    /**
     * The release the current episode came from.
     *
     * Kept so the next episode can be played from the same one without asking:
     * two streams sharing a `bingeGroup` are the same release of the same show,
     * and that is the entire point of binge-watching handed over by the
     * protocol for free.
     */
    private var bingeGroup: String? = null

    private var ticker: Job? = null
    private var countdown: Job? = null
    private var advanceJob: Job? = null
    private var subtitleDiscoveryJob: Job? = null
    private var subtitleLoadJob: Job? = null

    /** Set while the media source is being swapped, so an expected stop is not an error. */
    private var isSwitchingSource = false

    /**
     * The episode the up-next card has already been shown for, and whether the
     * viewer turned it down.
     *
     * Both exist because the offer is driven from a twice-a-second tick, and a
     * tick has no memory. Without the first, dismissing the card brings it
     * straight back on the next tick; without the second, saying "not now" and
     * then letting the episode finish advances anyway - the app overruling a
     * decision the viewer just made. Reset on every [play].
     */
    private var offeredNextFor: String? = null
    private var autoAdvanceDeclined = false

    /**
     * Built on the first [play], not at construction.
     *
     * This ViewModel is activity-scoped so that playback survives leaving the
     * detail page, which means it exists for everyone - including the majority
     * who never open TV mode. An ExoPlayer allocated for them is a codec pool
     * and a wake-lock holder doing nothing, on top of the one the video player
     * already builds eagerly.
     */
    private val playerLazy = lazy { buildPlayer() }
    private val player: ExoPlayer get() = playerLazy.value

    private fun buildPlayer(): ExoPlayer = ExoPlayer.Builder(getApplication())
        .setMediaSourceFactory(DefaultMediaSourceFactory(defaultDataSourceFactory(getApplication())))
        .setLoadControl(
            // Larger than the YouTube player's, because these are single large
            // files from one server rather than adaptive segments: a longer
            // read-ahead is the difference between riding out a dip and
            // rebuffering, and there is no ladder to drop down to.
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(30_000, 120_000, 1_500, 3_000)
                .setTargetBufferBytes(96 * 1024 * 1024)
                .setBackBuffer(30_000, true)
                .setPrioritizeTimeOverSizeThresholds(false)
                .build()
        )
        .setWakeMode(C.WAKE_MODE_NETWORK)
        // Focus handling is what pauses the music player rather than playing
        // over it, and is why nothing here has to know MusicService exists.
        .setAudioAttributes(AudioAttributes.DEFAULT, true)
        .setHandleAudioBecomingNoisy(true)
        .setSeekBackIncrementMs(SEEK_STEP_MS)
        .setSeekForwardIncrementMs(SEEK_STEP_MS)
        .build()
        .also { built ->
            // "Off" has to mean off. Text tracks are enabled only after the
            // saved preference or an explicit picker choice selects one.
            built.trackSelectionParameters = built.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
            built.addListener(playerListener)
        }

    /** Exposed for the `PlayerView` that renders it. Nothing else may command it. */
    fun exoPlayer(): ExoPlayer = player

    private val playerListener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                _isPlaying.value = playing
                if (!playing) checkpoint()
            }

            override fun onPlaybackStateChanged(state: Int) {
                _isBuffering.value = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    _durationMs.value = player.duration.coerceAtLeast(0)
                    _problem.value = null
                }
                if (state == Player.STATE_ENDED) onReachedEnd()
            }

            override fun onTracksChanged(tracks: Tracks) {
                _audioTracks.value = readAudioTracks(tracks)
                _embeddedSubtitleTracks.value = readEmbeddedSubtitleTracks(tracks)
                // Video with silence and no error is the worst failure this
                // player has, and it is common: DTS-HD MA and TrueHD are all
                // over high-quality releases and frequently undecodable here.
                // Say so rather than letting someone watch a silent film.
                val audio = _audioTracks.value
                if (audio.isNotEmpty() && audio.none { it.isSupported }) {
                    _problem.value = TvPlaybackProblem.NO_SUPPORTED_AUDIO
                }
                restorePreferredEmbeddedSubtitle()
            }

            override fun onPlayerError(error: PlaybackException) {
                if (isSwitchingSource) return
                KLog.w(TAG, "Playback failed: " + error.errorCodeName)
                _isBuffering.value = false
                _problem.value = playbackProblemFor(error)
            }
    }

    /**
     * Start playing [source] for [item] (and [episode], for a series).
     *
     * [resumeFrom] is honoured when it is a genuine resume point; a position
     * inside the first minute is treated as "started by accident" by the same
     * rule the progress store uses, so it seeks to the start instead.
     */
    fun play(
        item: TvItem,
        episode: TvEpisode?,
        source: TvSource,
        resumeFrom: Long? = null,
    ) {
        cancelCountdown()
        advanceJob?.cancel()
        subtitleDiscoveryJob?.cancel()
        subtitleLoadJob?.cancel()

        _isAdvancing.value = false
        _problem.value = null
        _audioTracks.value = emptyList()
        _subtitleTracks.value = emptyList()
        _selectedSubtitle.value = null
        _embeddedSubtitleTracks.value = emptyList()
        _selectedEmbeddedSubtitle.value = null
        _subtitleCues.value = emptyList()
        _subtitleLoadFailed.value = false
        _isSubtitlesLoading.value = false
        offeredNextFor = null
        autoAdvanceDeclined = false

        _playback.value = TvPlayback(item, episode, source)
        bingeGroup = source.bingeGroup
        _nextEpisode.value = findNextEpisode(item, episode)
        discoverSubtitles(item, episode, source)

        val streamId = episode?.id ?: item.id
        val stored = progressRepository.forEpisode(streamId)
        val startAt = resumeFrom ?: stored?.takeIf { it.isResumable }?.positionMs ?: 0L

        val url = source.stream.url
        if (url.isNullOrBlank()) {
            // A torrent or an external link reaches here only if a caller
            // ignored its kind; the sheet routes both to their own notice.
            _problem.value = TvPlaybackProblem.SOURCE_FAILED
            return
        }

        _isBuffering.value = true
        isSwitchingSource = true
        try {
            // A per-playback factory, because the headers belong to this stream:
            // behaviorHints.proxyHeaders.request is how an addon says its host
            // checks a Referer or a token, and without them the fetch is a 403
            // with no explanation anywhere.
            player.setMediaSource(
                DefaultMediaSourceFactory(
                    dataSourceFactory(source.requestHeaders)
                ).createMediaSource(MediaItem.fromUri(url))
            )
            player.prepare()
            if (startAt > 0) player.seekTo(startAt)
            player.playWhenReady = true
            startTicker()
        } catch (error: Exception) {
            KLog.w(TAG, "Could not prepare source: " + error.javaClass.simpleName)
            _isBuffering.value = false
            _problem.value = TvPlaybackProblem.SOURCE_FAILED
        } finally {
            isSwitchingSource = false
        }
    }

    private fun playbackProblemFor(error: PlaybackException): TvPlaybackProblem {
        return when (error.errorCode) {
            in 2000..2999 -> TvPlaybackProblem.NETWORK_FAILED
            in 3000..3999 -> TvPlaybackProblem.FORMAT_UNSUPPORTED
            in 4000..4999 -> TvPlaybackProblem.DECODER_FAILED
            else -> TvPlaybackProblem.SOURCE_FAILED
        }
    }

    /** Swap the file without losing the position. Used by "change source". */
    fun switchSource(source: TvSource) {
        val current = _playback.value ?: return
        play(current.item, current.episode, source, resumeFrom = player.currentPosition)
    }

    fun togglePlayPause() {
        if (player.playWhenReady) player.pause() else player.play()
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs.coerceIn(0, player.duration.coerceAtLeast(0)))
        _positionMs.value = player.currentPosition
    }

    fun seekBy(deltaMs: Long) = seekTo(player.currentPosition + deltaMs)

    fun setSpeed(speed: Float) {
        player.setPlaybackSpeed(speed.coerceIn(0.25f, 4f))
    }

    fun currentSpeed(): Float = player.playbackParameters.speed

    /**
     * Choose an audio track by index.
     *
     * This is mechanism B of the two things called "dub" - a dual-audio file
     * carrying both languages, where switching costs nothing and rebuffers
     * nothing. Mechanism A, where the dub is a different file entirely, is
     * handled a layer up in the source sheet.
     */
    fun selectAudioTrack(track: TvAudioTrack) {
        val tracks = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        val group = tracks.getOrNull(track.groupIndex) ?: return
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, track.trackIndex))
            .build()
        _audioTracks.value = readAudioTracks(player.currentTracks)
    }

    /** Select an external subtitle without rebuilding or discarding the video buffer. */
    fun selectSubtitle(track: TvSubtitleTrack?) {
        if (track == null) {
            disableSubtitles()
            return
        }
        if (_selectedSubtitle.value == track) return
        _selectedSubtitle.value = track
        _selectedEmbeddedSubtitle.value = null
        _subtitleLoadFailed.value = false
        themePreferences.setCaptionsEnabled(true)
        themePreferences.setCaptionLanguageCode(track?.lang?.takeIf { it.isNotBlank() })
        subtitleLoadJob?.cancel()
        if (playerLazy.isInitialized()) {
            playerLazy.value.trackSelectionParameters = playerLazy.value.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .build()
        }
        _isSubtitlesLoading.value = true
        _subtitleCues.value = emptyList()
        subtitleLoadJob = viewModelScope.launch {
            val body = stremioClient.subtitleBody(track.url)
            val cues = body?.let(WebVttParser::parse).orEmpty()
            if (_selectedSubtitle.value != track) return@launch
            _subtitleCues.value = cues
            _subtitleLoadFailed.value = cues.isEmpty()
            _isSubtitlesLoading.value = false
        }
    }

    /** Select a text track already muxed into the playing file. */
    fun selectEmbeddedSubtitle(track: TvEmbeddedSubtitleTrack) {
        if (!playerLazy.isInitialized() || !track.isSupported) return
        val groups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
        val group = groups.getOrNull(track.groupIndex) ?: return
        subtitleLoadJob?.cancel()
        _selectedSubtitle.value = null
        _subtitleCues.value = emptyList()
        _subtitleLoadFailed.value = false
        _isSubtitlesLoading.value = false
        themePreferences.setCaptionsEnabled(true)
        themePreferences.setCaptionLanguageCode(track.language)
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, track.trackIndex))
            .build()
        _embeddedSubtitleTracks.value = readEmbeddedSubtitleTracks(player.currentTracks)
        _selectedEmbeddedSubtitle.value = _embeddedSubtitleTracks.value
            .firstOrNull { it.groupIndex == track.groupIndex && it.trackIndex == track.trackIndex }
            ?: track
    }

    fun disableSubtitles() {
        subtitleLoadJob?.cancel()
        _selectedSubtitle.value = null
        _selectedEmbeddedSubtitle.value = null
        _subtitleCues.value = emptyList()
        _subtitleLoadFailed.value = false
        _isSubtitlesLoading.value = false
        themePreferences.setCaptionsEnabled(false)
        themePreferences.setCaptionLanguageCode(null)
        if (playerLazy.isInitialized()) {
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .build()
        }
    }

    fun dismissProblem() {
        _problem.value = null
    }

    /** Retry the current release without losing the viewer's position. */
    fun retryPlayback() {
        val current = _playback.value ?: return
        val resumeFrom = if (playerLazy.isInitialized()) {
            playerLazy.value.currentPosition.coerceAtLeast(0)
        } else {
            _positionMs.value.coerceAtLeast(0)
        }
        play(current.item, current.episode, current.source, resumeFrom)
    }

    /** Close, and ask whoever is underneath to reopen the source list. */
    /** Internal reset. Does not count as the viewer declining. */
    private fun cancelCountdown() {
        countdown?.cancel()
        countdown = null
        _autoplayCountdown.value = null
    }

    /**
     * The viewer turning down the up-next card.
     *
     * Distinct from [cancelCountdown] because it has to be remembered: the card
     * is offered from a tick and the auto-advance also fires on `STATE_ENDED`,
     * so "not now" has to survive both or it means nothing.
     */
    fun declineNextEpisode() {
        autoAdvanceDeclined = true
        cancelCountdown()
    }

    /**
     * Play the next episode, from the same release when the addon offers one.
     *
     * The `bingeGroup` match is what makes this silent: without it every
     * episode boundary is a fan-out, a sheet and a decision, which is the exact
     * opposite of binge-watching. When nothing matches it falls back to the
     * ordinary auto-pick, and when nothing at all is playable it says so rather
     * than sitting on a black screen.
     */
    fun playNextEpisode() {
        val current = _playback.value ?: return
        val next = _nextEpisode.value ?: return
        // The countdown finishing and the player reaching STATE_ENDED are two
        // signals for one event and they arrive within a frame of each other,
        // so without this guard every episode boundary runs the fan-out twice
        // and the second cancels the first mid-flight.
        if (advanceJob?.isActive == true) return
        cancelCountdown()
        advanceJob = viewModelScope.launch {
            _isAdvancing.value = true
            val profile = TvAutoSelectProfile.forCurrentNetwork(getApplication())
            val sources = streamRepository.sources(current.item.type, next.id).sources
            val playable = sources.filter { it.isPlayable }
            val sameRelease = bingeGroup?.let { group ->
                playable.firstOrNull { it.bingeGroup == group }
            }
            val chosen = sameRelease
                ?: TvStreamRepository.autoPick(playable, profile)?.source
            _isAdvancing.value = false
            if (chosen == null) {
                _problem.value = TvPlaybackProblem.NO_NEXT_SOURCE
                return@launch
            }
            play(current.item, next, chosen, resumeFrom = 0L)
        }
    }

    fun close() {
        checkpoint()
        cancelCountdown()
        advanceJob?.cancel()
        subtitleDiscoveryJob?.cancel()
        subtitleLoadJob?.cancel()
        ticker?.cancel()
        ticker = null
        if (playerLazy.isInitialized()) {
            player.stop()
            player.clearMediaItems()
        }
        _playback.value = null
        _isPlaying.value = false
        _isBuffering.value = false
        _isAdvancing.value = false
        _positionMs.value = 0
        _durationMs.value = 0
        _bufferedMs.value = 0
        _audioTracks.value = emptyList()
        _subtitleTracks.value = emptyList()
        _selectedSubtitle.value = null
        _embeddedSubtitleTracks.value = emptyList()
        _selectedEmbeddedSubtitle.value = null
        _subtitleCues.value = emptyList()
        _subtitleLoadFailed.value = false
        _isSubtitlesLoading.value = false
        _problem.value = null
        _nextEpisode.value = null
        bingeGroup = null
    }

    /** Called when the app stops being visible, matching the video player's rule. */
    fun onEnterBackground() {
        if (!playerLazy.isInitialized()) return
        checkpoint()
        player.pause()
    }

    override fun onCleared() {
        checkpoint()
        ticker?.cancel()
        countdown?.cancel()
        advanceJob?.cancel()
        subtitleDiscoveryJob?.cancel()
        subtitleLoadJob?.cancel()
        // Never through the accessor: releasing a player that was never built
        // would construct one purely to tear it down.
        if (playerLazy.isInitialized()) playerLazy.value.release()
        super.onCleared()
    }

    // --- Internals ----------------------------------------------------------

    /** Merge the chosen stream's tracks with every installed subtitle addon. */
    private fun discoverSubtitles(item: TvItem, episode: TvEpisode?, source: TvSource) {
        val inline = source.stream.subtitles
            .filter { it.isUsable }
            .map { track ->
                if (track.addonName.isNullOrBlank()) track.copy(addonName = source.addonName)
                else track
            }
        _subtitleTracks.value = mergeSubtitleTracks(inline, emptyList())
        restorePreferredSubtitle(_subtitleTracks.value)
        subtitleDiscoveryJob = viewModelScope.launch {
            val streamId = episode?.id ?: item.id
            val addonTracks = streamRepository.subtitles(item.type, streamId)
            if (_playback.value?.streamId != streamId || _playback.value?.source?.id != source.id) {
                return@launch
            }
            val merged = mergeSubtitleTracks(inline, addonTracks)
            _subtitleTracks.value = merged
            restorePreferredSubtitle(merged)
        }
    }

    private fun restorePreferredSubtitle(tracks: List<TvSubtitleTrack>) {
        if (
            !themePreferences.isCaptionsEnabled() ||
            _selectedSubtitle.value != null ||
            _selectedEmbeddedSubtitle.value != null
        ) return
        val preferred = themePreferences.getCaptionLanguageCode()
        val match = preferred?.let { wanted ->
            tracks.firstOrNull { languageMatches(it.lang, wanted) }
        } ?: tracks.firstOrNull()
        match?.let(::selectSubtitle)
    }

    private fun restorePreferredEmbeddedSubtitle() {
        if (
            !themePreferences.isCaptionsEnabled() ||
            _selectedSubtitle.value != null ||
            _selectedEmbeddedSubtitle.value != null
        ) return
        val tracks = _embeddedSubtitleTracks.value.filter { it.isSupported }
        val preferred = themePreferences.getCaptionLanguageCode()
        val match = preferred?.let { wanted ->
            tracks.firstOrNull { languageMatches(it.language.orEmpty(), wanted) }
        } ?: tracks.firstOrNull()
        match?.let(::selectEmbeddedSubtitle)
    }

    /**
     * Position, and a progress checkpoint on the same 15-second cadence music
     * sessions already use.
     *
     * One loop for both, because a separate checkpoint timer would be a second
     * thing to cancel on every transition and the failure - a resume point
     * frozen at the last episode - is silent.
     */
    private fun startTicker() {
        ticker?.cancel()
        ticker = viewModelScope.launch {
            var sinceCheckpoint = 0L
            // Bounded by playback rather than by the ViewModel: this one is
            // activity-scoped, so an unconditional loop would be a twice-a-
            // second wakeup for the whole session of someone who only ever
            // listens to music.
            while (_playback.value != null) {
                delay(TICK_MS)
                if (_playback.value == null) break
                _positionMs.value = player.currentPosition.coerceAtLeast(0)
                _bufferedMs.value = player.bufferedPosition.coerceAtLeast(0)
                if (player.duration > 0) _durationMs.value = player.duration
                maybeOfferNextEpisode()
                if (!player.isPlaying) {
                    sinceCheckpoint = 0
                    continue
                }
                sinceCheckpoint += TICK_MS
                if (sinceCheckpoint >= CHECKPOINT_MS) {
                    sinceCheckpoint = 0
                    checkpoint()
                }
            }
        }
    }

    /** Write where the viewer got to. Cheap, and safe to call at any time. */
    private fun checkpoint() {
        val current = _playback.value ?: return
        if (!playerLazy.isInitialized()) return
        val duration = player.duration
        if (duration <= 0) return
        progressRepository.record(
            item = current.item,
            episodeId = current.streamId,
            positionMs = player.currentPosition.coerceAtLeast(0),
            durationMs = duration,
            season = current.episode?.season,
            episode = current.episode?.episodeNumber,
        )
    }

    /**
     * Show the up-next card over the last stretch of an episode.
     *
     * Only for episodes, only once, and never while paused - a countdown that
     * runs while someone has deliberately stopped to read something is the app
     * taking the remote out of their hand.
     */
    private fun maybeOfferNextEpisode() {
        if (_nextEpisode.value == null || autoAdvanceDeclined) return
        if (countdown != null || _autoplayCountdown.value != null) return
        val streamId = _playback.value?.streamId ?: return
        if (offeredNextFor == streamId) return
        if (!player.isPlaying) return
        val duration = player.duration
        if (duration <= 0) return
        val remaining = duration - player.currentPosition
        if (remaining in 1..UP_NEXT_WINDOW_MS) {
            offeredNextFor = streamId
            startCountdown((remaining / 1000).toInt())
        }
    }

    private fun startCountdown(seconds: Int) {
        countdown?.cancel()
        countdown = viewModelScope.launch {
            var left = seconds.coerceIn(1, (UP_NEXT_WINDOW_MS / 1000).toInt())
            _autoplayCountdown.value = left
            while (left > 0) {
                delay(1_000)
                // A pause during the countdown holds it rather than cancelling
                // it, so stepping away and coming back does not lose the card.
                if (!player.isPlaying && player.playbackState != Player.STATE_ENDED) continue
                left--
                _autoplayCountdown.value = left
            }
            _autoplayCountdown.value = null
            countdown = null
            playNextEpisode()
        }
    }

    private fun onReachedEnd() {
        val current = _playback.value ?: return
        // Reaching the end is what marks something watched, and it has to be
        // written before anything advances - the next episode overwrites
        // _playback and the checkpoint would then land on the wrong row.
        val duration = player.duration
        if (duration > 0) {
            progressRepository.markWatched(current.item, current.streamId, duration)
        }
        if (autoAdvanceDeclined) return
        if (_nextEpisode.value != null && _autoplayCountdown.value == null && countdown == null) {
            playNextEpisode()
        }
    }

    /**
     * Audio tracks, labelled the way a viewer thinks of them.
     *
     * `isSupported` comes from the renderer rather than from the codec string,
     * so it reflects what this device can actually decode rather than what the
     * container claims.
     */
    private fun readAudioTracks(tracks: Tracks): List<TvAudioTrack> {
        val groups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        return buildList {
            groups.forEachIndexed { groupIndex, group ->
                for (trackIndex in 0 until group.length) {
                    val format = group.getTrackFormat(trackIndex)
                    add(
                        TvAudioTrack(
                            groupIndex = groupIndex,
                            trackIndex = trackIndex,
                            label = audioLabel(
                                format.language,
                                format.label,
                                format.channelCount,
                            ),
                            language = format.language,
                            isSelected = group.isTrackSelected(trackIndex),
                            isSupported = group.isTrackSupported(trackIndex),
                        )
                    )
                }
            }
        }
    }

    private fun readEmbeddedSubtitleTracks(tracks: Tracks): List<TvEmbeddedSubtitleTrack> {
        val groups = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
        return buildList {
            groups.forEachIndexed { groupIndex, group ->
                for (trackIndex in 0 until group.length) {
                    val format = group.getTrackFormat(trackIndex)
                    val language = format.language?.takeIf { it.isNotBlank() && it != "und" }
                    val label = format.label?.takeIf { it.isNotBlank() }
                        ?: language?.let(::languageLabel)
                        ?: "Subtitle " + (size + 1)
                    add(
                        TvEmbeddedSubtitleTrack(
                            groupIndex = groupIndex,
                            trackIndex = trackIndex,
                            label = label,
                            language = language,
                            isSelected = group.isTrackSelected(trackIndex),
                            isSupported = group.isTrackSupported(trackIndex),
                        )
                    )
                }
            }
        }
    }

    companion object {
        private const val TAG = "TvPlayerViewModel"

        /**
         * The episode after this one: the next in the season, else the first of
         * the next season.
         *
         * **Season 0 is specials and never leads**, and `TvItem.seasons` already
         * filters it out, so a season finale advances into the next real season
         * rather than into the OVAs - the same rule the detail page's initial
         * season uses. Addressed by id rather than by number because an episode
         * list can be missing numbers entirely, and by position within the
         * season so that a gap in numbering does not end the run.
         */
        fun findNextEpisode(item: TvItem, episode: TvEpisode?): TvEpisode? {
            if (episode == null || !item.hasEpisodes) return null
            val season = episode.season ?: return null
            val inSeason = item.episodesInSeason(season)
            val index = inSeason.indexOfFirst { it.id == episode.id }
            if (index >= 0 && index < inSeason.size - 1) return inSeason[index + 1]
            if (index < 0) return null
            val nextSeason = item.seasons.firstOrNull { it > season } ?: return null
            return item.episodesInSeason(nextSeason).firstOrNull()
        }

        /** Matches the video player's double-tap seek, so the two agree. */
        const val SEEK_STEP_MS = 10_000L

        private const val TICK_MS = 500L
        private const val CHECKPOINT_MS = 15_000L

        /** How long before the end the up-next card appears. */
        private const val UP_NEXT_WINDOW_MS = 25_000L

        /**
         * "Japanese 5.1", not "und / eac3". Falls back to whatever the file
         * offers rather than to an index, because "Track 2" tells nobody which
         * one is the dub.
         */
        fun audioLabel(language: String?, label: String?, channelCount: Int): String {
            val name = language
                ?.takeIf { it.isNotBlank() && !it.equals("und", ignoreCase = true) }
                ?.let { languageLabel(it) }
                ?: label?.takeIf { it.isNotBlank() }
            val channels = when (channelCount) {
                8 -> "7.1"
                6 -> "5.1"
                2 -> "2.0"
                1 -> "Mono"
                else -> null
            }
            return listOfNotNull(name, channels).joinToString(" ").ifBlank { "Audio" }
        }

        internal fun mergeSubtitleTracks(
            streamTracks: List<TvSubtitleTrack>,
            addonTracks: List<TvSubtitleTrack>,
        ): List<TvSubtitleTrack> {
            val seenUrls = HashSet<String>()
            val seenLabels = HashSet<String>()
            return (streamTracks + addonTracks).filter { track ->
                if (!track.isUsable || !seenUrls.add(track.url)) return@filter false
                val labelKey = track.lang.trim().lowercase() + "|" +
                    (track.name ?: track.id).trim().lowercase()
                labelKey == "|" || seenLabels.add(labelKey)
            }
        }

        private fun languageMatches(first: String, second: String): Boolean =
            first.equals(second, ignoreCase = true) ||
                languageLabel(first).equals(languageLabel(second), ignoreCase = true)
    }
}

/**
 * The default factory, used only for the player's construction.
 *
 * Real playback always replaces it with one carrying the chosen stream's
 * headers; this exists because ExoPlayer.Builder needs a factory before any
 * source is known.
 */
@OptIn(UnstableApi::class)
private fun defaultDataSourceFactory(application: Application) =
    DefaultDataSource.Factory(application, DefaultHttpDataSource.Factory())

/**
 * An HTTP factory carrying one stream's headers.
 *
 * **Not `ChunkedStreamDataSource`.** That exists because googlevideo paces
 * open-ended progressive requests to roughly the media bitrate, and it keys its
 * per-request User-Agent off the URL's `?c=` client tag. Neither applies to an
 * addon's host, and a YouTube-shaped User-Agent aimed at a debrid server is a
 * request that identifies the app as something it is not.
 *
 * Cross-protocol redirects are allowed because debrid links redirect freely,
 * including across schemes, and refusing that is a dead stream with no message.
 */
@OptIn(UnstableApi::class)
private fun dataSourceFactory(headers: Map<String, String>) =
    DefaultHttpDataSource.Factory()
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(20_000)
        .setReadTimeoutMs(20_000)
        .apply { if (headers.isNotEmpty()) setDefaultRequestProperties(headers) }
