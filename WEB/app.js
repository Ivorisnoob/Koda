// API Configuration (Local Node Backend)
const BACKEND_URL = "http://localhost:3000";

// State
let tracks = [];
let currentTrackIndex = -1;
let isPlaying = false;
let isAudioOnly = true;
let currentTab = 'trending';

// DOM Elements
const trackListContainer = document.getElementById('trackList');
const searchInput = document.getElementById('searchInput');
const searchBtn = document.getElementById('searchBtn');
const qualityToggle = document.getElementById('qualityToggle');

// Player Elements
const videoContainer = document.getElementById('videoContainer');
const closeVideoBtn = document.getElementById('closeVideoBtn');
const playerDock = document.getElementById('playerDock');

// HTML5 Media Players (Replacing YouTube IFrame API)
let audioPlayer = document.getElementById('audioPlayer');
let videoPlayer = document.getElementById('videoPlayer');

if (!audioPlayer) {
    audioPlayer = document.createElement('audio');
    audioPlayer.id = 'audioPlayer';
    playerDock.appendChild(audioPlayer);
}

if (!videoPlayer) {
    videoPlayer = document.createElement('video');
    videoPlayer.id = 'videoPlayer';
    videoPlayer.className = 'shadow-brutal';
    videoPlayer.controls = true;

    // Clear out any leftover iframe or divs
    const ytDiv = document.getElementById('ytplayer');
    if (ytDiv) ytDiv.remove();

    videoContainer.insertBefore(videoPlayer, closeVideoBtn);
}

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

// Load Trending
async function loadTrending(filter = "music") {
    setLoader(true);

    try {
        const res = await fetch(`${BACKEND_URL}/trending?filter=${filter}`);
        if (!res.ok) throw new Error("Backend failed");

        const data = await res.json();
        if (data.length === 0) throw new Error("Empty data");

        tracks = data;
        renderTracks();

        // Update Hero
        if (tracks.length > 0) {
            const safeHeroTitle = tracks[0].title.replace(/</g, "&lt;").replace(/>/g, "&gt;");
            heroTitle.innerHTML = safeHeroTitle.split(' ').slice(0,3).join('<br>');
            heroVinylArt.style.backgroundImage = `url('${tracks[0].art}')`;
            heroVinylArt.style.backgroundSize = 'cover';
        }
    } catch (err) {
        console.error("Trending Error", err);
        trackListContainer.innerHTML = '<div style="padding: 2rem;"><h3>Failed to load from Server. Please ensure backend is running.</h3></div>';
    } finally {
        setLoader(false);
    }
}

// Search
async function search(query) {
    if (!query) return;
    setLoader(true);

    try {
        const searchUrl = `${BACKEND_URL}/search?q=${encodeURIComponent(query)}`;
        const res = await fetch(searchUrl);
        if (!res.ok) throw new Error("Search failed");

        const data = await res.json();
        if (data.length === 0) {
            trackListContainer.innerHTML = '<h3>No results found</h3>';
            return;
        }

        tracks = data;
        renderTracks();
    } catch (err) {
        console.error("Search Error", err);
        trackListContainer.innerHTML = '<h3>Error searching</h3>';
    } finally {
        setLoader(false);
    }
}

// Render Tracks (Safely construct HTML)
function renderTracks() {
    trackListContainer.innerHTML = '';
    tracks.forEach((track, index) => {
        const item = document.createElement('article');
        item.className = `track-item shadow-brutal ${index === currentTrackIndex && isPlaying ? 'playing' : ''}`;
        item.dataset.index = index;

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

// Media Logic: Fetch stream from backend
async function playTrack(index) {
    if (index < 0 || index >= tracks.length) return;

    currentTrackIndex = index;
    const track = tracks[currentTrackIndex];

    updateUI(track, true);

    // Pause both players
    audioPlayer.pause();
    videoPlayer.pause();

    try {
        const endpoint = isAudioOnly ? '/audio' : '/video';
        const res = await fetch(`${BACKEND_URL}${endpoint}?id=${track.id}`);

        if (!res.ok) throw new Error("Failed to extract stream");

        const data = await res.json();

        if (isAudioOnly) {
            audioPlayer.src = data.url;
            audioPlayer.play();
            videoContainer.classList.add('hidden');
        } else {
            videoPlayer.src = data.url;
            videoPlayer.play();
            videoContainer.classList.remove('hidden');
        }

        isPlaying = true;
        updateUI(track, false);

    } catch (err) {
        console.error("Playback Error:", err);
        alert("Failed to stream media. It might be age-restricted or blocked.");
        isPlaying = false;
        updateUI(track, false);
    }
}

function togglePlay() {
    if (currentTrackIndex === -1) {
        if (tracks.length > 0) playTrack(0);
        return;
    }

    const activePlayer = isAudioOnly ? audioPlayer : videoPlayer;

    if (activePlayer.paused) {
        activePlayer.play();
        isPlaying = true;
    } else {
        activePlayer.pause();
        isPlaying = false;
    }
    updateUI(tracks[currentTrackIndex], false);
}

// Media Event Listeners for sync
const getActivePlayerObj = () => isAudioOnly ? audioPlayer : videoPlayer;

audioPlayer.addEventListener('timeupdate', updateProgress);
videoPlayer.addEventListener('timeupdate', updateProgress);

audioPlayer.addEventListener('ended', () => playTrack(currentTrackIndex + 1));
videoPlayer.addEventListener('ended', () => playTrack(currentTrackIndex + 1));

function updateProgress() {
    const player = getActivePlayerObj();
    if (!player.duration || isNaN(player.duration)) return;

    const percent = (player.currentTime / player.duration) * 100;
    progressFill.style.width = `${percent}%`;
    progressHandle.style.left = `${percent}%`;

    timeCurrent.textContent = formatTime(player.currentTime);
    timeTotal.textContent = formatTime(player.duration);
}

// Seek
progressBar.addEventListener('click', (e) => {
    const player = getActivePlayerObj();
    if (player.duration) {
        const rect = progressBar.getBoundingClientRect();
        const percent = (e.clientX - rect.left) / rect.width;
        player.currentTime = player.duration * percent;
    }
});

// UI Updates
function updateUI(track, isLoading) {
    if (playerDock.classList.contains('hidden')) {
        playerDock.classList.remove('hidden');
    }

    dockTitle.textContent = isLoading ? "Extracting Stream..." : track.title.replace(/&amp;/g, '&').replace(/&lt;/g, '<').replace(/&gt;/g, '>');
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
    // Hide video container
    videoContainer.classList.add('hidden');
    videoPlayer.pause();

    isAudioOnly = true;
    qualityToggle.textContent = "Audio Only";
    qualityToggle.style.backgroundColor = "";
    qualityToggle.style.color = "";

    // Resume audio
    if (currentTrackIndex !== -1 && isPlaying) {
        const currentTime = videoPlayer.currentTime;
        playTrack(currentTrackIndex).then(() => {
            audioPlayer.currentTime = currentTime;
        });
    }
});

qualityToggle.addEventListener('click', () => {
    isAudioOnly = !isAudioOnly;
    qualityToggle.textContent = isAudioOnly ? "Audio Only" : "Video Mode";
    qualityToggle.style.backgroundColor = isAudioOnly ? "" : "var(--accent-purple)";
    qualityToggle.style.color = isAudioOnly ? "" : "white";

    if (currentTrackIndex !== -1 && isPlaying) {
        const previousPlayer = !isAudioOnly ? audioPlayer : videoPlayer;
        const currentTime = previousPlayer.currentTime;
        previousPlayer.pause();

        playTrack(currentTrackIndex).then(() => {
            getActivePlayerObj().currentTime = currentTime;
        });
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
loadTrending('all');
