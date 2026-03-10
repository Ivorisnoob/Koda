// API Configuration
// Switching to iTunes API for CORS-friendly, reliable search and previews.
// YouTube APIs (Piped/Invidious) block localhost and have CORS restrictions on browser.

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

// Fetch helper using iTunes API
async function fetchApi(query, entity = 'song') {
    try {
        const res = await fetch(`https://itunes.apple.com/search?term=${encodeURIComponent(query)}&limit=25&entity=${entity}`);
        if (!res.ok) throw new Error("API failed");
        return await res.json();
    } catch (e) {
        console.error("API failed", e);
        return null;
    }
}

// Map iTunes track format to our app format
function mapItunesTrack(item) {
    return {
        id: item.trackId,
        title: item.trackName,
        artist: item.artistName,
        duration: formatTime(item.trackTimeMillis / 1000),
        durationSec: item.trackTimeMillis / 1000,
        art: item.artworkUrl100 ? item.artworkUrl100.replace('100x100', '300x300') : '',
        streamUrl: item.previewUrl,
        hasVideo: item.kind === 'music-video'
    };
}

// Load Trending (Mocking trending with popular search terms)
async function loadTrending(filter = "music") {
    setLoader(true);

    // Use some generic popular terms to simulate trending
    const terms = ["hits", "pop", "top", "trending", "dance"];
    const randomTerm = terms[Math.floor(Math.random() * terms.length)];

    const entity = filter === "video" ? "musicVideo" : "song";
    const data = await fetchApi(randomTerm, entity);

    if (!data || !data.results) {
        trackListContainer.innerHTML = '<div style="padding: 2rem;"><h3>Failed to load from API. Please try searching instead.</h3></div>';
        setLoader(false);
        return;
    }

    tracks = data.results.map(mapItunesTrack).filter(t => t.streamUrl);

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

    const entity = currentTab === 'video' ? 'musicVideo' : 'song';
    const data = await fetchApi(query, entity);

    if (!data || !data.results || data.results.length === 0) {
        trackListContainer.innerHTML = '<h3>No results found</h3>';
        setLoader(false);
        return;
    }

    tracks = data.results.map(mapItunesTrack).filter(t => t.streamUrl);

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
            <div class="track-duration">${track.hasVideo ? '🎬 ' : ''}${track.duration}</div>
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
async function playTrack(index) {
    if (index < 0 || index >= tracks.length) return;

    currentTrackIndex = index;
    const track = tracks[currentTrackIndex];

    updateUI(track, true); // Set loading state UI

    // Pause both players
    audioPlayer.pause();
    videoPlayer.pause();

    try {
        if (!track.streamUrl) {
            alert("Stream not available for this track right now.");
            updateUI(track, false);
            return;
        }

        // We use iTunes preview URLs which are typically .m4a or .m4v
        if (track.hasVideo && !isAudioOnly) {
            videoPlayer.src = track.streamUrl;
            videoPlayer.play();
            videoContainer.classList.remove('hidden');
        } else {
            audioPlayer.src = track.streamUrl;
            audioPlayer.play();
            videoContainer.classList.add('hidden');
        }

        isPlaying = true;
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

    const track = tracks[currentTrackIndex];
    const activePlayer = (track.hasVideo && !isAudioOnly) ? videoPlayer : audioPlayer;

    if (activePlayer.paused) {
        activePlayer.play();
        isPlaying = true;
    } else {
        activePlayer.pause();
        isPlaying = false;
    }
    updateUI(track, false);
}

// Media Event Listeners for sync
const getActivePlayerObj = () => {
    if (currentTrackIndex === -1) return audioPlayer;
    const track = tracks[currentTrackIndex];
    return (track.hasVideo && !isAudioOnly) ? videoPlayer : audioPlayer;
};

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

    // Only update total time if it was missing from metadata
    if (timeTotal.textContent === "0:00" || timeTotal.textContent.includes("NaN")) {
        timeTotal.textContent = formatTime(player.duration);
    }
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

    dockTitle.textContent = isLoading ? "Loading Stream..." : track.title;
    dockArtist.textContent = track.artist;
    dockArt.style.backgroundImage = `url('${track.art}')`;

    // iTunes preview URLs are usually 30 seconds
    timeTotal.textContent = "0:30"; // track.duration is the full song time

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
        const currentTime = getActivePlayerObj().currentTime;
        audioPlayer.pause();
        videoPlayer.pause();

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
            loadTrending('video'); // Video generic
        }
    });
});

// Init
loadTrending('all');
