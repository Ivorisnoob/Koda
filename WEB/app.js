// API Configuration
// Because public Invidious/Piped instances aggressively block CORS from localhost/browsers,
// we must use a proxy (allorigins) to wrap the request. We wrap multiple instances to ensure resilience.

const PROXY_URL = "https://api.allorigins.win/raw?url=";
const INVIDIOUS_INSTANCES = [
    "https://invidious.nerdvpn.de",
    "https://inv.tux.pizza",
    "https://yewtu.be",
    "https://inv.nadeko.net"
];

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

// Robust fallback logic for getting YouTube data without auth/CORS
async function fetchApi(query) {
    for (let instance of INVIDIOUS_INSTANCES) {
        try {
            const encodedQuery = encodeURIComponent(query);
            const targetUrl = encodeURIComponent(`${instance}/api/v1/search?q=${encodedQuery}`);
            const finalUrl = `${PROXY_URL}${targetUrl}`;

            console.log("Trying:", instance);
            const res = await fetch(finalUrl, { cache: 'no-store' });

            if (res.ok) {
                const text = await res.text();
                try {
                    const data = JSON.parse(text);
                    if (Array.isArray(data) && data.length > 0) {
                        return data;
                    }
                } catch (parseError) {
                    console.warn(`JSON Parse failed for ${instance}`, parseError);
                }
            }
        } catch (e) {
            console.warn(`Fetch failed for ${instance}`, e);
        }
    }

    // If all Invidious APIs fail via proxy, fallback to a static known good result
    // to prevent UI breakage and keep the prototype functional.
    console.error("All YouTube proxy APIs failed. Using fallback mock data.");
    return [
        {
            type: "video",
            videoId: "dQw4w9WgXcQ",
            title: "Never Gonna Give You Up (Official Music Video)",
            author: "Rick Astley",
            lengthSeconds: 212,
            videoThumbnails: [{ url: "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg" }]
        },
        {
            type: "video",
            videoId: "kJQP7kiw5Fk",
            title: "Luis Fonsi - Despacito ft. Daddy Yankee",
            author: "Luis Fonsi",
            lengthSeconds: 281,
            videoThumbnails: [{ url: "https://i.ytimg.com/vi/kJQP7kiw5Fk/hqdefault.jpg" }]
        },
        {
            type: "video",
            videoId: "jNQXAC9IVRw",
            title: "Me at the zoo",
            author: "jawed",
            lengthSeconds: 19,
            videoThumbnails: [{ url: "https://i.ytimg.com/vi/jNQXAC9IVRw/hqdefault.jpg" }]
        }
    ];
}

// Map Invidious track format to our app format safely
function mapYoutubeTrack(item) {
    let thumb = '';
    if (item.videoThumbnails && item.videoThumbnails.length > 0) {
        thumb = item.videoThumbnails.find(t => t.quality === 'sddefault') || item.videoThumbnails[0];
        thumb = thumb.url;
        // If relative URL (some invidious instances do this), prepend the host
        if (thumb.startsWith('/')) {
            thumb = "https://invidious.nerdvpn.de" + thumb;
        }
    }

    return {
        id: item.videoId || 'dQw4w9WgXcQ', // fallback ID
        title: item.title || 'Unknown Title',
        artist: item.author || 'Unknown Artist',
        duration: formatTime(item.lengthSeconds),
        durationSec: item.lengthSeconds || 0,
        art: thumb || 'https://images.unsplash.com/photo-1614149162012-d458dfce3787?w=300&h=300&fit=crop',
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
        trackListContainer.innerHTML = '<div style="padding: 2rem;"><h3>No results found</h3></div>';
        setLoader(false);
        return;
    }

    tracks = data.filter(t => t.type === 'video' || t.videoId).map(mapYoutubeTrack).slice(0, 20);

    renderTracks();
    setLoader(false);
}

// Render Tracks (Safely construct HTML)
function renderTracks() {
    trackListContainer.innerHTML = '';
    tracks.forEach((track, index) => {
        const item = document.createElement('article');
        item.className = `track-item shadow-brutal ${index === currentTrackIndex && isPlaying ? 'playing' : ''}`;
        item.dataset.index = index;

        // Escape HTML to prevent injection if title contains weird chars
        const safeTitle = track.title.replace(/</g, "&lt;").replace(/>/g, "&gt;");
        const safeArtist = track.artist.replace(/</g, "&lt;").replace(/>/g, "&gt;");

        item.innerHTML = `
            <div class="track-number">${String(index + 1).padStart(2, '0')}</div>
            <div class="track-art shadow-brutal" style="background-image: url('${track.art}')"></div>
            <div class="track-info">
                <h3 class="track-title">${safeTitle}</h3>
                <p class="track-artist">${safeArtist}</p>
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
    // Replace old HTML5 video if it exists
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

    // Remove old audio tag if exists
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
            'autoplay': 1,
            'rel': 0
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

    dockTitle.textContent = isLoading ? "Loading Stream..." : track.title.replace(/&amp;/g, '&').replace(/&lt;/g, '<').replace(/&gt;/g, '>');
    dockArtist.textContent = track.artist.replace(/&amp;/g, '&');
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
