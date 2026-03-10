// API Configuration
// Switching to YouTube Data API v3 (Search) via an alternative reliable CORS proxy structure.


// State
let tracks = [];
let currentTrackIndex = -1;
let isPlaying = false;
let isAudioOnly = true;
let currentTab = 'trending';

// YouTube Iframe Player
let ytPlayer = null;
let ytReady = false;

// DOM Elements
const trackListContainer = document.getElementById('trackList');
const searchInput = document.getElementById('searchInput');
const searchBtn = document.getElementById('searchBtn');
const qualityToggle = document.getElementById('qualityToggle');

// Player Elements
const videoContainer = document.getElementById('videoContainer');
const closeVideoBtn = document.getElementById('closeVideoBtn');
const playerDock = document.getElementById('playerDock');

// UI Elements
const loader = document.getElementById('loader');
const heroSection = document.getElementById('heroSection');
const heroTitle = document.getElementById('heroTitle');
const heroVinylArt = document.getElementById('heroVinylArt');
const heroPlayBtn = document.getElementById('heroPlayBtn');

// Controls
const dockTitle = document.getElementById('dockTitle');
const dockArtist = document.getElementById('dockArtist');
const dockArt = document.getElementById('dockArt');
const btnPlayPause = document.getElementById('btnPlayPause');
const iconPlay = document.getElementById('iconPlay');
const iconPause = document.getElementById('iconPause');
const btnPrev = document.getElementById('btnPrev');
const btnNext = document.getElementById('btnNext');
const timeCurrent = document.getElementById('timeCurrent');
const timeTotal = document.getElementById('timeTotal');
const progressFill = document.getElementById('progressFill');
const progressHandle = document.getElementById('progressHandle');
const progressBar = document.getElementById('progressBar');

// Format Time (Seconds to MM:SS)
function formatTime(seconds) {
    if (isNaN(seconds) || !isFinite(seconds)) return "0:00";
    const m = Math.floor(seconds / 60);
    const s = Math.floor(seconds % 60);
    return `${m}:${s.toString().padStart(2, '0')}`;
}

// Show/Hide Loader
function setLoader(show) {
    if (show) {
        loader.classList.remove('hidden');
        trackListContainer.innerHTML = '';
        trackListContainer.appendChild(loader);
    } else {
        loader.classList.add('hidden');
    }
}

// Fallback logic for getting YouTube data without auth/CORS
async function fetchApi(query) {
    try {
        // Since many invidious/piped instances block browser CORS
        // We use allorigins.win to proxy the request to inv.nadeko.net as backup
        const proxyUrl = "https://api.allorigins.win/raw?url=";
        const invidiousUrl = encodeURIComponent("https://inv.nadeko.net/api/v1/search?q=" + encodeURIComponent(query));

        let data = [];
        try {
            const res = await fetch(proxyUrl + invidiousUrl);
            if (res.ok) {
                 data = await res.json();
                 return data;
            }
        } catch(err) {
            console.warn("First proxy failed, trying backup...");
        }

        // Final backup using yewtu.be via proxy
        const invidiousUrl2 = encodeURIComponent("https://yewtu.be/api/v1/search?q=" + encodeURIComponent(query));
        const res2 = await fetch(proxyUrl + invidiousUrl2);
        if(!res2.ok) throw new Error("Search API failed entirely");
        return await res2.json();

    } catch (e) {
        console.error("API failed", e);
        return null;
    }
}

// Map Invidious track format to our app format
function mapYoutubeTrack(item) {
    let thumb = '';
    if (item.videoThumbnails && item.videoThumbnails.length > 0) {
        thumb = item.videoThumbnails.find(t => t.quality === 'sddefault') || item.videoThumbnails[0];
        thumb = thumb.url;
        if (thumb.startsWith('/')) thumb = "https://invidious.nerdvpn.de" + thumb;
    }

    return {
        id: item.videoId,
        title: item.title,
        artist: item.author,
        duration: formatTime(item.lengthSeconds),
        durationSec: item.lengthSeconds,
        art: thumb,
        hasVideo: true
    };
}

// Load Trending via Search Heuristic
async function loadTrending(filter = "music") {
    setLoader(true);

    let query = "trending top hits 2026 music video official";
    if (filter === "video") query = "trending viral videos today";

    const data = await fetchApi(query);

    if (!data || data.length === 0) {
        trackListContainer.innerHTML = '<div style="padding: 2rem;"><h3>Failed to load from API. Please try searching instead.</h3></div>';
        setLoader(false);
        return;
    }

    tracks = data.filter(t => t.type === 'video' || t.videoId).map(mapYoutubeTrack).slice(0, 20);

    renderTracks();
    setLoader(false);

    // Update Hero
    if (tracks.length > 0) {
        heroTitle.innerHTML = tracks[0].title.split(' ').slice(0,3).join('<br>');
        heroVinylArt.style.backgroundImage = `url('${tracks[0].art}')`;
        heroVinylArt.style.backgroundSize = 'cover';
    }
}

// Search
async function search(query) {
    if (!query) return;
    setLoader(true);

    if (currentTab === 'music') query += " song official";

    const data = await fetchApi(query);

    if (!data || data.length === 0) {
        trackListContainer.innerHTML = '<h3>No results found</h3>';
        setLoader(false);
        return;
    }

    tracks = data.filter(t => t.type === 'video' || t.videoId).map(mapYoutubeTrack).slice(0, 20);

    renderTracks();
    setLoader(false);
}

// Render Tracks
function renderTracks() {
    trackListContainer.innerHTML = '';
    tracks.forEach((track, index) => {
        const item = document.createElement('article');
        item.className = `track-item shadow-brutal ${index === currentTrackIndex && isPlaying ? 'playing' : ''}`;
        item.dataset.index = index;

        item.innerHTML = `
            <div class="track-number">${String(index + 1).padStart(2, '0')}</div>
            <div class="track-art shadow-brutal" style="background-image: url('${track.art}')"></div>
            <div class="track-info">
                <h3 class="track-title">${track.title}</h3>
                <p class="track-artist">${track.artist}</p>
            </div>
            <div class="track-duration">${track.duration}</div>
            <div class="playing-bars">
                <div class="bar"></div>
                <div class="bar"></div>
                <div class="bar"></div>
            </div>
        `;

        item.addEventListener('click', () => {
            if (currentTrackIndex === index) {
                togglePlay();
            } else {
                playTrack(index);
            }
        });

        trackListContainer.appendChild(item);
    });
}

// --- YOUTUBE IFRAME API INTEGRATION ---
function initYouTubePlayer() {
    const oldVideo = document.getElementById('videoPlayer');
    if (oldVideo) {
        const div = document.createElement('div');
        div.id = 'ytplayer';
        div.className = 'shadow-brutal';
        div.style.width = '100%';
        div.style.aspectRatio = '16/9';
        div.style.borderRadius = '8px';
        oldVideo.parentNode.replaceChild(div, oldVideo);
    }

    const oldAudio = document.getElementById('audioPlayer');
    if (oldAudio) oldAudio.remove();

    var tag = document.createElement('script');
    tag.src = "https://www.youtube.com/iframe_api";
    var firstScriptTag = document.getElementsByTagName('script')[0];
    firstScriptTag.parentNode.insertBefore(tag, firstScriptTag);
}

window.onYouTubeIframeAPIReady = function() {
    ytPlayer = new YT.Player('ytplayer', {
        height: '100%',
        width: '100%',
        playerVars: {
            'playsinline': 1,
            'controls': 1,
            'disablekb': 0,
            'fs': 1,
            'modestbranding': 1,
            'autoplay': 1
        },
        events: {
            'onReady': onPlayerReady,
            'onStateChange': onPlayerStateChange,
            'onError': onPlayerError
        }
    });
};

function onPlayerReady(event) {
    ytReady = true;
    console.log("YT Player Ready");
    setInterval(updateProgress, 1000);
}

function onPlayerStateChange(event) {
    if (event.data === YT.PlayerState.PLAYING) {
        isPlaying = true;
        updateUI(tracks[currentTrackIndex], false);
    } else if (event.data === YT.PlayerState.PAUSED || event.data === YT.PlayerState.UNSTARTED) {
        isPlaying = false;
        updateUI(tracks[currentTrackIndex], false);
    } else if (event.data === YT.PlayerState.ENDED) {
        playTrack(currentTrackIndex + 1);
    }
}

function onPlayerError(event) {
    console.error("YT Error", event.data);
    alert("Video blocked by copyright or unavailable in iframe. Skipping to next.");
    playTrack(currentTrackIndex + 1);
}

// Media Logic
function playTrack(index) {
    if (!ytReady) {
        console.warn("Player not ready yet");
        return;
    }
    if (index < 0 || index >= tracks.length) return;

    currentTrackIndex = index;
    const track = tracks[currentTrackIndex];

    updateUI(track, true);

    ytPlayer.loadVideoById(track.id);
    isPlaying = true;

    // Manage UI for audio-only vs video mode
    if (isAudioOnly) {
        videoContainer.classList.add('hidden');
    } else {
        videoContainer.classList.remove('hidden');
    }

    updateUI(track, false);
}

function togglePlay() {
    if (!ytReady) return;
    if (currentTrackIndex === -1) {
        if (tracks.length > 0) playTrack(0);
        return;
    }

    const state = ytPlayer.getPlayerState();
    if (state === YT.PlayerState.PLAYING) {
        ytPlayer.pauseVideo();
        isPlaying = false;
    } else {
        ytPlayer.playVideo();
        isPlaying = true;
    }
    updateUI(tracks[currentTrackIndex], false);
}

function updateProgress() {
    if (!ytReady || currentTrackIndex === -1) return;

    // Only update if playing or explicitly moved
    const state = ytPlayer.getPlayerState();
    if (state !== YT.PlayerState.PLAYING && state !== YT.PlayerState.PAUSED) return;

    const current = ytPlayer.getCurrentTime();
    const duration = ytPlayer.getDuration();

    if (!duration || isNaN(duration) || duration === 0) return;

    const percent = (current / duration) * 100;
    progressFill.style.width = `${percent}%`;
    progressHandle.style.left = `${percent}%`;

    timeCurrent.textContent = formatTime(current);
    timeTotal.textContent = formatTime(duration);
}

// Seek
progressBar.addEventListener('click', (e) => {
    if (!ytReady || currentTrackIndex === -1) return;
    const duration = ytPlayer.getDuration();
    if (duration) {
        const rect = progressBar.getBoundingClientRect();
        const percent = (e.clientX - rect.left) / rect.width;
        ytPlayer.seekTo(duration * percent, true);
        progressFill.style.width = `${percent * 100}%`;
        progressHandle.style.left = `${percent * 100}%`;
    }
});

// UI Updates
function updateUI(track, isLoading) {
    if (playerDock.classList.contains('hidden')) {
        playerDock.classList.remove('hidden');
    }

    dockTitle.textContent = isLoading ? "Loading Stream..." : track.title;
    dockArtist.textContent = track.artist;
    dockArt.style.backgroundImage = `url('${track.art}')`;

    if (isPlaying && !isLoading) {
        iconPlay.classList.add('hidden');
        iconPause.classList.remove('hidden');
        btnPlayPause.style.backgroundColor = 'var(--accent-red)';
    } else {
        iconPlay.classList.remove('hidden');
        iconPause.classList.add('hidden');
        btnPlayPause.style.backgroundColor = 'var(--accent-yellow)';
    }

    document.querySelectorAll('.track-item').forEach((el, idx) => {
        if (idx === currentTrackIndex && isPlaying && !isLoading) {
            el.classList.add('playing');
        } else {
            el.classList.remove('playing');
        }
    });
}

// Button Listeners
btnPlayPause.addEventListener('click', togglePlay);
btnNext.addEventListener('click', () => playTrack(currentTrackIndex + 1));
btnPrev.addEventListener('click', () => playTrack(currentTrackIndex - 1));
heroPlayBtn.addEventListener('click', () => {
    if(tracks.length > 0) playTrack(0);
});

closeVideoBtn.addEventListener('click', () => {
    // Hide video container but don't pause audio
    videoContainer.classList.add('hidden');
    isAudioOnly = true;
    qualityToggle.textContent = "Audio Only";
    qualityToggle.style.backgroundColor = "";
    qualityToggle.style.color = "";
});

qualityToggle.addEventListener('click', () => {
    isAudioOnly = !isAudioOnly;
    qualityToggle.textContent = isAudioOnly ? "Audio Only" : "Video Mode";
    qualityToggle.style.backgroundColor = isAudioOnly ? "" : "var(--accent-purple)";
    qualityToggle.style.color = isAudioOnly ? "" : "white";

    if (currentTrackIndex !== -1) {
        if (!isAudioOnly) {
            videoContainer.classList.remove('hidden');
        } else {
            videoContainer.classList.add('hidden');
        }
    }
});

searchBtn.addEventListener('click', () => search(searchInput.value));
searchInput.addEventListener('keypress', (e) => {
    if (e.key === 'Enter') search(searchInput.value);
});

// Tabs
document.querySelectorAll('.nav-pills .pill').forEach(pill => {
    if (pill.id === 'qualityToggle') return;

    pill.addEventListener('click', (e) => {
        document.querySelectorAll('.nav-pills .pill').forEach(p => p.classList.remove('active'));
        e.target.classList.add('active');

        const color = e.target.dataset.color;
        document.documentElement.style.setProperty('--accent-blue', color);

        currentTab = e.target.id.replace('tab', '').toLowerCase();

        if (currentTab === 'trending') {
            loadTrending('all');
        } else if (currentTab === 'music') {
            loadTrending('music');
        } else {
            loadTrending('video');
        }
    });
});

// Init
initYouTubePlayer();
loadTrending('all');
