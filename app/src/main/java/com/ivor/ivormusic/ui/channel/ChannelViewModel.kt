package com.ivor.ivormusic.ui.channel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ivor.ivormusic.data.ChannelAbout
import com.ivor.ivormusic.data.ChannelHeader
import com.ivor.ivormusic.data.ChannelSortOption
import com.ivor.ivormusic.data.ChannelTab
import com.ivor.ivormusic.data.ChannelTabKind
import com.ivor.ivormusic.data.ChannelTabPage
import com.ivor.ivormusic.data.LocalSubscription
import com.ivor.ivormusic.data.LocalSubscriptionsRepository
import com.ivor.ivormusic.data.NotInterestedActions
import com.ivor.ivormusic.data.NotInterestedRepository
import com.ivor.ivormusic.data.ProfileManager
import com.ivor.ivormusic.data.SessionManager
import com.ivor.ivormusic.data.SubscriptionActions
import com.ivor.ivormusic.data.SubscriptionStore
import com.ivor.ivormusic.data.VideoItem
import com.ivor.ivormusic.data.YouTubeRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch

/**
 * State for one creator's page.
 *
 * Scoped to the navigation entry rather than to the app, so opening a channel
 * from inside another channel gives the second one its own instance and going
 * back finds the first exactly as it was left: same tab, same scroll depth,
 * same pages already loaded. A single app-wide instance would have made the
 * second visit overwrite the first, which is precisely the case a channel
 * screen hits constantly through "Featured channels" and collaborations.
 *
 * **Tabs are cached per tab and never refetched on a switch.** Flipping between
 * Videos and Shorts is free after the first visit, which is what makes the tab
 * row feel like part of one page instead of six separate screens.
 */
class ChannelViewModel(application: Application) : AndroidViewModel(application) {

    private val context get() = getApplication<Application>()
    private val youtubeRepository = YouTubeRepository(context)
    private val sessionManager = SessionManager(context)
    private val subscriptionActions = SubscriptionActions(context, youtubeRepository)
    private val notInterestedRepository = NotInterestedRepository(context)
    private val notInterestedActions =
        NotInterestedActions(notInterestedRepository, youtubeRepository)
    private val localSubscriptions = LocalSubscriptionsRepository(context)

    private val _header = MutableStateFlow<ChannelHeader?>(null)
    val header: StateFlow<ChannelHeader?> = _header.asStateFlow()

    private val _tabs = MutableStateFlow<List<ChannelTab>>(emptyList())
    val tabs: StateFlow<List<ChannelTab>> = _tabs.asStateFlow()

    private val _selectedTab = MutableStateFlow(ChannelTabKind.HOME)
    val selectedTab: StateFlow<ChannelTabKind> = _selectedTab.asStateFlow()

    /** Loaded content per tab. A tab absent from the map has never been opened. */
    private val _pages = MutableStateFlow<Map<ChannelTabKind, ChannelTabPage>>(emptyMap())
    val pages: StateFlow<Map<ChannelTabKind, ChannelTabPage>> = _pages.asStateFlow()

    private val _loadingTabs = MutableStateFlow<Set<ChannelTabKind>>(emptySet())
    val loadingTabs: StateFlow<Set<ChannelTabKind>> = _loadingTabs.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _isLoadingPage = MutableStateFlow(true)
    val isLoadingPage: StateFlow<Boolean> = _isLoadingPage.asStateFlow()

    /** Set only when the channel itself could not be loaded at all. */
    private val _loadFailed = MutableStateFlow(false)
    val loadFailed: StateFlow<Boolean> = _loadFailed.asStateFlow()

    private val _about = MutableStateFlow<ChannelAbout?>(null)
    val about: StateFlow<ChannelAbout?> = _about.asStateFlow()

    private val _isAboutLoading = MutableStateFlow(false)
    val isAboutLoading: StateFlow<Boolean> = _isAboutLoading.asStateFlow()

    // ---------------- Search within the channel ----------------

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<VideoItem>>(emptyList())
    val searchResults: StateFlow<List<VideoItem>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    /** True once a search has actually run, so "no results" is distinct from "not searched yet". */
    private val _searchRan = MutableStateFlow(false)
    val searchRan: StateFlow<Boolean> = _searchRan.asStateFlow()

    // ---------------- Subscription state ----------------

    /**
     * YouTube's own view of the subscription, which only the account knows.
     * Kept apart from the button's state for the same reason the player does:
     * the button is account-OR-device, and an unsubscribe needs to know whether
     * a network call is owed.
     */
    private val _remotelySubscribed = MutableStateFlow(false)

    private val _accountSubscribed = MutableStateFlow(false)

    /**
     * What the Subscribe button binds to: subscribed on the account **or** on
     * the device. Never [_accountSubscribed] alone, which would show
     * "Subscribe" for a channel followed locally.
     */
    val isSubscribed: StateFlow<Boolean> = combine(
        localSubscriptions.subscriptions,
        _accountSubscribed,
        _header
    ) { local, account, header ->
        val id = header?.channelId
        account || (id != null && local.any { it.channelId == id })
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val isBlocked: StateFlow<Boolean> = combine(
        notInterestedRepository.blockedChannels,
        _header
    ) { blocked, header ->
        val id = header?.channelId ?: return@combine false
        blocked.any { it.channelId == id }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _isLoggedIn = MutableStateFlow(sessionManager.isLoggedIn())
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private var loadedChannelId: String? = null
    private var searchJob: Job? = null

    init {
        observeProfileSwitches()
    }

    /**
     * Loads the channel, once per id.
     *
     * [idOrHandleOrUrl] may be a canonical `UC…` id, an `@handle`, a full
     * channel URL, or a `video:<id>` fallback from a feed card whose modern
     * lockup omitted the creator endpoint. The last form resolves the owner
     * from watch metadata and never starts or mutates playback.
     */
    fun load(idOrHandleOrUrl: String, force: Boolean = false) {
        if (!force && loadedChannelId == idOrHandleOrUrl) return
        loadedChannelId = idOrHandleOrUrl
        _isLoadingPage.value = true
        _loadFailed.value = false
        viewModelScope.launch {
            val channelId = when {
                idOrHandleOrUrl.startsWith(VideoItem.CHANNEL_REFERENCE_VIDEO_PREFIX) -> {
                    val videoId = idOrHandleOrUrl
                        .removePrefix(VideoItem.CHANNEL_REFERENCE_VIDEO_PREFIX)
                    youtubeRepository.getVideoChannelId(videoId)
                }
                idOrHandleOrUrl.startsWith("UC") && idOrHandleOrUrl.length >= 24 -> {
                    idOrHandleOrUrl
                }
                else -> youtubeRepository.resolveChannelId(idOrHandleOrUrl)
            }
            if (channelId == null) {
                _isLoadingPage.value = false
                _loadFailed.value = true
                return@launch
            }
            val page = youtubeRepository.getChannelPage(channelId)
            if (page == null) {
                _isLoadingPage.value = false
                _loadFailed.value = true
                return@launch
            }
            _header.value = page.header
            _tabs.value = page.tabs
            _selectedTab.value = page.selectedTab
            _pages.value = mapOf(page.selectedTab to page.selectedContent)
            _isLoadingPage.value = false
            refreshRemoteSubscription(channelId)
        }
    }

    /**
     * Whether YouTube thinks the account follows this channel.
     *
     * The channel response normally answers this for free - it has to, to draw
     * its own Subscribe button - so the happy path costs nothing. The fallback
     * exists because "the response did not say" and "no" are different answers
     * and only one of them is safe to show: it walks the account's subscription
     * list, which is the only endpoint that can answer it, and is why it is a
     * fallback rather than the primary. Signed out neither runs, and the device
     * store is the only truth, which is already driving the button.
     */
    private suspend fun refreshRemoteSubscription(channelId: String) {
        if (!sessionManager.isLoggedIn()) {
            _accountSubscribed.value = false
            _remotelySubscribed.value = false
            return
        }
        val fromResponse = _header.value?.accountSubscribed
        val subscribed = fromResponse ?: runCatching {
            youtubeRepository.getSubscribedChannels().any { it.channelId == channelId }
        }.getOrDefault(false)
        _accountSubscribed.value = subscribed
        _remotelySubscribed.value = subscribed
    }

    /**
     * Opens a tab, fetching it only the first time.
     *
     * About is the exception: it is not a real tab on YouTube's side but an
     * engagement panel behind its own token, so it is fetched separately and
     * kept separately.
     */
    fun selectTab(kind: ChannelTabKind) {
        _selectedTab.value = kind
        if (_pages.value.containsKey(kind) || kind in _loadingTabs.value) return
        val tab = _tabs.value.firstOrNull { it.kind == kind } ?: return
        val channelId = _header.value?.channelId ?: return
        _loadingTabs.value = _loadingTabs.value + kind
        viewModelScope.launch {
            val page = youtubeRepository.getChannelTab(channelId, tab.params, _header.value)
            _pages.value = _pages.value + (kind to page)
            _loadingTabs.value = _loadingTabs.value - kind
        }
    }

    /** Fetches the About panel, once. */
    fun loadAbout() {
        if (_about.value != null || _isAboutLoading.value) return
        val token = _header.value?.aboutToken ?: return
        _isAboutLoading.value = true
        viewModelScope.launch {
            _about.value = youtubeRepository.getChannelAbout(token)
            _isAboutLoading.value = false
        }
    }

    /**
     * Next page of the tab on screen.
     *
     * Guarded on the tab it was asked for, not just on a global flag: a fast
     * tab switch while a page is in flight would otherwise append one tab's
     * videos to another tab's list.
     */
    fun loadMore(kind: ChannelTabKind = _selectedTab.value) {
        if (_isLoadingMore.value) return
        val current = _pages.value[kind] ?: return
        val token = current.continuation ?: return
        _isLoadingMore.value = true
        viewModelScope.launch {
            val next = youtubeRepository.getChannelContinuation(token, _header.value)
            val existing = _pages.value[kind]
            if (existing != null && existing.continuation == token) {
                _pages.value = _pages.value + (kind to existing.plus(next))
            }
            _isLoadingMore.value = false
        }
    }

    /**
     * Re-sorts the tab on screen.
     *
     * Replaces the tab's contents rather than appending, because that is what
     * the user asked for, and keeps the sort list itself with the new selection
     * marked - the response's own chip state describes the sheet that produced
     * it, not the order that was just chosen.
     */
    fun selectSort(option: ChannelSortOption, kind: ChannelTabKind = _selectedTab.value) {
        val current = _pages.value[kind] ?: return
        val channelId = _header.value?.channelId ?: return
        _loadingTabs.value = _loadingTabs.value + kind
        viewModelScope.launch {
            val page = when {
                option.token != null ->
                    youtubeRepository.getChannelContinuation(option.token, _header.value)
                option.params != null ->
                    youtubeRepository.getChannelTab(channelId, option.params, _header.value)
                else -> null
            }
            if (page != null) {
                _pages.value = _pages.value + (
                    kind to page.copy(
                        sortOptions = current.sortOptions.map {
                            it.copy(selected = it.label == option.label)
                        }
                    )
                    )
            }
            _loadingTabs.value = _loadingTabs.value - kind
        }
    }

    // ---------------- Search ----------------

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _isSearching.value = false
            _searchRan.value = false
            return
        }
        val channelId = _header.value?.channelId ?: return
        val tab = _tabs.value.firstOrNull { it.kind == ChannelTabKind.SEARCH } ?: return
        _isSearching.value = true
        searchJob = viewModelScope.launch {
            // Typing a channel search is typing a phrase, and firing a browse
            // per keystroke would spend a request on every prefix of it.
            kotlinx.coroutines.delay(350)
            val page = youtubeRepository.searchWithinChannel(
                channelId = channelId,
                params = tab.params,
                query = query,
                header = _header.value
            )
            if (_searchQuery.value == query) {
                _searchResults.value = page.videos
                _searchRan.value = true
                _isSearching.value = false
            }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _searchQuery.value = ""
        _searchResults.value = emptyList()
        _isSearching.value = false
        _searchRan.value = false
    }

    // ---------------- Actions ----------------

    /**
     * Subscribe or unsubscribe, routed by the subscribe-target setting.
     *
     * Optimistic on the account half only. The device half is a synchronous
     * write whose process-wide flow already drives the button, so marking it
     * optimistically would be marking it twice - and a local-only subscribe
     * must leave the account flag alone or the next unsubscribe fires a
     * pointless network call. Same shape as the player's, deliberately.
     */
    fun toggleSubscribe() {
        val header = _header.value ?: return
        val subscribe = !isSubscribed.value
        val wasRemote = _remotelySubscribed.value
        val writesRemote = subscriptionActions.resolveTarget() != SubscriptionStore.LOCAL

        _accountSubscribed.value = when {
            !subscribe -> false
            writesRemote -> true
            else -> _accountSubscribed.value
        }

        viewModelScope.launch {
            val ok = subscriptionActions.setSubscribed(
                channel = LocalSubscription(
                    channelId = header.channelId,
                    name = header.name,
                    avatarUrl = header.avatarUrl,
                    handle = header.handle
                ),
                subscribe = subscribe,
                remotelySubscribed = wasRemote
            )
            if (ok) {
                _remotelySubscribed.value = subscribe && writesRemote
            } else {
                _accountSubscribed.value = wasRemote
            }
        }
    }

    /** True when Subscribe cannot do anything without a sign-in. */
    fun subscribeNeedsLogin(): Boolean = subscriptionActions.subscribeNeedsLogin()

    /**
     * Stop recommending this channel.
     *
     * The account-side token rides on a feed item, not on a channel, so a block
     * taken from the channel page itself is local-only. That is the honest
     * behaviour rather than a gap: the local blocklist is the engine of this
     * feature everywhere (YouTube's own feedback is advisory and takes days),
     * and it is the half that works signed out.
     */
    fun toggleBlocked() {
        val header = _header.value ?: return
        if (isBlocked.value) {
            notInterestedRepository.unblockChannel(header.channelId, header.name)
        } else {
            notInterestedRepository.blockChannel(
                channelId = header.channelId,
                name = header.name,
                avatarUrl = header.avatarUrl
            )
        }
    }

    fun hideVideo(video: VideoItem) = notInterestedActions.hideVideo(video, viewModelScope)

    fun onLoginStateChanged() {
        _isLoggedIn.value = sessionManager.isLoggedIn()
        val channelId = _header.value?.channelId ?: return
        viewModelScope.launch { refreshRemoteSubscription(channelId) }
    }

    /**
     * Everything account-derived on this screen resets when the active profile
     * changes: the subscription state belongs to the identity, and the page
     * itself is re-fetched because a channel browse is personalized (members-only
     * shelves, and the notification state on the header).
     *
     * `drop(1)` because the flow replays its current value on subscribe, and
     * that is not a switch.
     */
    private fun observeProfileSwitches() {
        viewModelScope.launch {
            ProfileManager(context)
                .activeProfileId
                .drop(1)
                .distinctUntilChanged()
                .collect {
                    _isLoggedIn.value = sessionManager.isLoggedIn()
                    _accountSubscribed.value = false
                    _remotelySubscribed.value = false
                    _about.value = null
                    val id = loadedChannelId ?: return@collect
                    load(id, force = true)
                }
        }
    }
}
