package com.ivor.ivormusic.ui.podcast

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ivor.ivormusic.data.EpisodeProgressRepository
import com.ivor.ivormusic.data.PodcastEpisode
import com.ivor.ivormusic.data.PodcastFeed
import com.ivor.ivormusic.data.PodcastRepository
import com.ivor.ivormusic.data.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Backs the Podcasts tab: the subscribed feeds, their episodes, and the
 * add/refresh/remove operations. Episodes play through the normal music
 * player via [episodeSongs].
 */
class PodcastsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PodcastRepository(application)
    private val downloadRepository = com.ivor.ivormusic.data.DownloadRepository.getInstance(application)
    private val progressRepository = EpisodeProgressRepository(application)

    /** Per-episode playback progress, for progress bars and resume. */
    val episodeProgress: StateFlow<Map<String, com.ivor.ivormusic.data.EpisodeProgress>> =
        progressRepository.progress

    /** Ids of downloaded episodes, for the row download state. */
    val downloadedEpisodeIds: StateFlow<Set<String>> =
        downloadRepository.downloadedSongs
            .map { list -> list.map { it.id }.toSet() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Ids of episodes currently downloading. */
    val downloadingEpisodeIds: StateFlow<Set<String>> = downloadRepository.downloadingIds

    val feeds: StateFlow<List<PodcastFeed>> = repository.feeds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val episodesByFeed: StateFlow<Map<String, List<PodcastEpisode>>> =
        repository.episodesByFeed
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** Feed URL currently being added, for the progress UI. Null when idle. */
    private val _addingState = MutableStateFlow<AddFeedState>(AddFeedState.Idle)
    val addingState: StateFlow<AddFeedState> = _addingState.asStateFlow()

    init {
        viewModelScope.launch {
            _isLoading.value = true
            repository.ensureLoaded()
            _isLoading.value = false
        }
    }

    fun episodesFor(feedId: String): List<PodcastEpisode> =
        episodesByFeed.value[feedId].orEmpty()

    fun episodeCount(feedId: String): Int =
        episodesByFeed.value[feedId]?.size ?: 0

    /** Episodes of one feed as player-ready [Song]s, newest first. */
    fun episodeSongs(feedId: String): List<Song> {
        val feed = feeds.value.find { it.id == feedId }
        return episodesFor(feedId)
            .sortedByDescending { it.pubDate }
            .map { repository.episodeToSong(it, feed) }
    }

    /** One episode as a player-ready [Song], for the new-episodes rail. */
    fun songForEpisode(episode: PodcastEpisode): Song {
        val feed = feeds.value.find { it.id == episode.feedId }
        return repository.episodeToSong(episode, feed)
    }

    fun addFeed(url: String) {
        if (_addingState.value is AddFeedState.Working) return
        viewModelScope.launch {
            _addingState.value = AddFeedState.Working
            val result = repository.addFeed(url)
            _addingState.value = result.fold(
                onSuccess = { AddFeedState.Done(it) },
                onFailure = { AddFeedState.Error(it.message ?: "Couldn't load that feed") },
            )
        }
    }

    fun consumeAddFeedState() {
        _addingState.value = AddFeedState.Idle
    }

    fun refreshFeed(feedId: String) {
        viewModelScope.launch { repository.refreshFeed(feedId) }
    }

    fun removeFeed(feedId: String) {
        viewModelScope.launch { repository.removeFeed(feedId) }
    }

    fun downloadEpisode(episode: com.ivor.ivormusic.data.PodcastEpisode, feed: com.ivor.ivormusic.data.PodcastFeed?) {
        viewModelScope.launch { downloadRepository.downloadPodcastEpisode(episode, feed) }
    }

    sealed interface AddFeedState {
        data object Idle : AddFeedState
        data object Working : AddFeedState
        data class Done(val feed: PodcastFeed) : AddFeedState
        data class Error(val message: String) : AddFeedState
    }
}
