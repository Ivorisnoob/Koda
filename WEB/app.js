// API Configuration (Using Piped API which tends to be more stable)
// Note: In a real app, you'd rotate instances if one fails
const API_BASE = "https://pipedapi.kavin.rocks";
// Fallback if Piped is down
const API_FALLBACK = "https://api.piped.projectsegfau.lt";

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
const audioPlayer = document.getElementById('audioPlayer');
const videoContainer = document.getElementById('videoContainer');
const videoPlayer = document.getElementById('videoPlayer');
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
    if (isNaN(seconds)) return "0:00";
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

// Fetch helper with fallback
async function fetchApi(endpoint) {
    try {
        const res = await fetch(`${API_BASE}${endpoint}`);
        if (!res.ok) throw new Error("API Base failed");
        return await res.json();
    } catch (e) {
        console.warn("Falling back to secondary API instance");
        try {
            const res = await fetch(`${API_FALLBACK}${endpoint}`);
            if (!res.ok) throw new Error("API Fallback failed");
            return await res.json();
        } catch (e2) {
            console.error("Both APIs failed", e2);
            return null;
        }
    }
}

// Load Trending
async function loadTrending(filter = "music") {
    setLoader(true);
    // Piped trending region
    const data = await fetchApi(`/trending?region=US`);

    if (!data) {
        trackListContainer.innerHTML = '<div style="padding: 2rem;"><h3>Failed to load from API. Please try searching instead.</h3></div>';
        setLoader(false);
        return;
    }

    // Filter data based on music/video
    tracks = data.map(item => ({
        id: item.url.split('v=')[1] || item.url.split('/').pop(),
        title: item.title,
        artist: item.uploaderName,
        duration: formatTime(item.duration),
        durationSec: item.duration,
        art: item.thumbnail
    })).filter(track => track.id); // Ensure ID exists

    if (filter === "music") {
        // Rough heuristic for music if pure music endpoint isn't available
        const musicTracks = tracks.filter(t => t.artist.toLowerCase().includes('vevo') || t.title.toLowerCase().includes('music') || t.title.toLowerCase().includes('official'));
        if (musicTracks.length > 0) tracks = musicTracks;
        else tracks = tracks.slice(0, 20); // fallback
    } else {
        tracks = tracks.slice(0, 20); // Just take top 20
    }

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

    const filterStr = currentTab === 'music' ? "&filter=music_songs" : "";
    const data = await fetchApi(`/search?q=${encodeURIComponent(query)}${filterStr}`);

    if (!data || !data.items) {
        trackListContainer.innerHTML = '<h3>No results found</h3>';
        setLoader(false);
        return;
    }

    tracks = data.items.filter(item => item.type === "stream").map(item => ({
        id: item.url.split('v=')[1],
        title: item.title,
        artist: item.uploaderName,
        duration: formatTime(item.duration),
        durationSec: item.duration,
        art: item.thumbnail
    }));

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

// Media Logic
async function getStreamUrls(videoId) {
    const data = await fetchApi(`/streams/${videoId}`);
    if (!data) return null;

    // Get Audio
    const audioStreams = data.audioStreams || [];
    audioStreams.sort((a, b) => b.bitrate - a.bitrate); // Highest quality first
    const bestAudio = audioStreams.length > 0 ? audioStreams[0].url : null;

    // Get Video (if not audio only)
    let bestVideo = null;
    if (!isAudioOnly) {
        const videoStreams = data.videoStreams || [];
        // Try to find a good quality mp4 with video+audio
        const mixed = videoStreams.find(s => s.videoOnly === false && s.quality === '720p');
        if (mixed) {
            bestVideo = mixed.url;
        } else {
            // fallback to best available
            bestVideo = videoStreams.length > 0 ? videoStreams[0].url : null;
        }
    }

    return { audio: bestAudio, video: bestVideo };
}

async function playTrack(index) {
    if (index < 0 || index >= tracks.length) return;

    currentTrackIndex = index;
    const track = tracks[currentTrackIndex];

    updateUI(track, true); // Set loading state UI

    // Pause both players
    audioPlayer.pause();
    videoPlayer.pause();

    try {
        const streams = await getStreamUrls(track.id);

        if (!streams || (!streams.audio && !streams.video)) {
            alert("Stream not available for this track right now. Trying another server or track.");
            updateUI(track, false);
            return;
        }

        if (isAudioOnly) {
            audioPlayer.src = streams.audio;
            audioPlayer.play();
            videoContainer.classList.add('hidden');
            isPlaying = true;
        } else {
            videoPlayer.src = streams.video || streams.audio; // fallback to audio if no video
            videoPlayer.play();
            videoContainer.classList.remove('hidden');
            isPlaying = true;
        }

        updateUI(track, false); // Clear loading state

    } catch (e) {
        console.error("Playback error", e);
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
const activePlayerObj = () => isAudioOnly ? audioPlayer : videoPlayer;

audioPlayer.addEventListener('timeupdate', updateProgress);
videoPlayer.addEventListener('timeupdate', updateProgress);

audioPlayer.addEventListener('ended', () => playTrack(currentTrackIndex + 1));
videoPlayer.addEventListener('ended', () => playTrack(currentTrackIndex + 1));

function updateProgress() {
    const player = activePlayerObj();
    if (!player.duration || isNaN(player.duration)) return;

    const percent = (player.currentTime / player.duration) * 100;
    progressFill.style.width = `${percent}%`;
    progressHandle.style.left = `${percent}%`;

    timeCurrent.textContent = formatTime(player.currentTime);

    // Only update total time if it was missing from metadata
    if (timeTotal.textContent === "0:00" || timeTotal.textContent.includes("NaN")) {
        timeTotal.textContent = formatTime(player.duration);
    }
}

// Seek
progressBar.addEventListener('click', (e) => {
    const player = activePlayerObj();
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

    dockTitle.textContent = isLoading ? "Loading Stream..." : track.title;
    dockArtist.textContent = track.artist;
    dockArt.style.backgroundImage = `url('${track.art}')`;
    timeTotal.textContent = track.duration;

    if (isPlaying && !isLoading) {
        iconPlay.classList.add('hidden');
        iconPause.classList.remove('hidden');
        btnPlayPause.style.backgroundColor = 'var(--accent-red)';
    } else {
        iconPlay.classList.remove('hidden');
        iconPause.classList.add('hidden');
        btnPlayPause.style.backgroundColor = 'var(--accent-yellow)';
    }

    // List rendering is heavy, so just toggle classes instead of re-rendering
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
    videoContainer.classList.add('hidden');
    videoPlayer.pause();
    isPlaying = false;
    updateUI(tracks[currentTrackIndex], false);
});

qualityToggle.addEventListener('click', () => {
    isAudioOnly = !isAudioOnly;
    qualityToggle.textContent = isAudioOnly ? "Audio Only" : "Video Mode";
    qualityToggle.style.backgroundColor = isAudioOnly ? "" : "var(--accent-purple)";
    qualityToggle.style.color = isAudioOnly ? "" : "white";

    // If playing, switch stream
    if (isPlaying && currentTrackIndex !== -1) {
        const currentTime = activePlayerObj().currentTime;
        const previousPlayer = isAudioOnly ? videoPlayer : audioPlayer;
        previousPlayer.pause();

        playTrack(currentTrackIndex).then(() => {
            activePlayerObj().currentTime = currentTime;
        });
    }
});

searchBtn.addEventListener('click', () => search(searchInput.value));
searchInput.addEventListener('keypress', (e) => {
    if (e.key === 'Enter') search(searchInput.value);
});

// Tabs
document.querySelectorAll('.nav-pills .pill').forEach(pill => {
    // Ignore settings pills
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
            loadTrending('all'); // Video generic
        }
    });
});

// Init
loadTrending('all');
