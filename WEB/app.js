// Mock Data for Tracks
const tracks = [
    {
        id: '1',
        title: 'Neon Nights',
        artist: 'Synthwave Dreamer',
        duration: '3:45',
        durationSec: 225,
        art: 'https://images.unsplash.com/photo-1614149162012-d458dfce3787?w=300&h=300&fit=crop'
    },
    {
        id: '2',
        title: 'Bass Drop Panic',
        artist: 'DJ Wobbly',
        duration: '4:12',
        durationSec: 252,
        art: 'https://images.unsplash.com/photo-1557672172-298e090bd0f1?w=300&h=300&fit=crop'
    },
    {
        id: '3',
        title: 'Chill Vibes Lofi',
        artist: 'Study Girl',
        duration: '2:50',
        durationSec: 170,
        art: 'https://images.unsplash.com/photo-1511379938547-c1f69419868d?w=300&h=300&fit=crop'
    },
    {
        id: '4',
        title: 'Retro Funk Blast',
        artist: 'The Groovers',
        duration: '5:01',
        durationSec: 301,
        art: 'https://images.unsplash.com/photo-1493225457124-a1a2a5f5f9af?w=300&h=300&fit=crop'
    },
    {
        id: '5',
        title: 'Space Cowboy',
        artist: 'Galactic Outlaws',
        duration: '3:20',
        durationSec: 200,
        art: 'https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=300&h=300&fit=crop'
    }
];

// State
let currentTrackIndex = -1;
let isPlaying = false;
let currentProgress = 0; // in seconds
let intervalId = null;

// DOM Elements
const trackListContainer = document.querySelector('.track-list');
const playerDock = document.getElementById('playerDock');
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
const heroPlayBtn = document.getElementById('heroPlayBtn');

// Initialize Track List
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

// Update UI state
function updateUI() {
    // Show dock if hidden
    if (playerDock.classList.contains('hidden') && currentTrackIndex !== -1) {
        playerDock.classList.remove('hidden');
    }

    if (currentTrackIndex === -1) return;

    const track = tracks[currentTrackIndex];

    // Update Dock Info
    dockTitle.textContent = track.title;
    dockArtist.textContent = track.artist;
    dockArt.style.backgroundImage = `url('${track.art}')`;
    timeTotal.textContent = track.duration;

    // Toggle Icons
    if (isPlaying) {
        iconPlay.classList.add('hidden');
        iconPause.classList.remove('hidden');
        btnPlayPause.style.backgroundColor = 'var(--accent-red)';
    } else {
        iconPlay.classList.remove('hidden');
        iconPause.classList.add('hidden');
        btnPlayPause.style.backgroundColor = 'var(--accent-yellow)';
    }

    // Update List styling
    document.querySelectorAll('.track-item').forEach((el, idx) => {
        if (idx === currentTrackIndex && isPlaying) {
            el.classList.add('playing');
        } else {
            el.classList.remove('playing');
        }
    });
}

// Player Logic
function playTrack(index) {
    currentTrackIndex = index;
    isPlaying = true;
    currentProgress = 0;
    updateUI();
    startProgress();
}

function togglePlay() {
    if (currentTrackIndex === -1) {
        playTrack(0);
        return;
    }

    isPlaying = !isPlaying;
    updateUI();

    if (isPlaying) {
        startProgress();
    } else {
        stopProgress();
    }
}

function nextTrack() {
    if (currentTrackIndex === -1) return;
    let next = currentTrackIndex + 1;
    if (next >= tracks.length) next = 0;
    playTrack(next);
}

function prevTrack() {
    if (currentTrackIndex === -1) return;
    let prev = currentTrackIndex - 1;
    if (prev < 0) prev = tracks.length - 1;
    playTrack(prev);
}

// Progress Bar Simulation
function startProgress() {
    stopProgress();
    intervalId = setInterval(() => {
        const track = tracks[currentTrackIndex];
        currentProgress += 1; // 1 sec tick

        if (currentProgress >= track.durationSec) {
            nextTrack();
        } else {
            updateProgressUI();
        }
    }, 1000);
}

function stopProgress() {
    if (intervalId) {
        clearInterval(intervalId);
        intervalId = null;
    }
}

function formatTime(seconds) {
    const m = Math.floor(seconds / 60);
    const s = Math.floor(seconds % 60);
    return `${m}:${s.toString().padStart(2, '0')}`;
}

function updateProgressUI() {
    if (currentTrackIndex === -1) return;

    const track = tracks[currentTrackIndex];
    const percent = (currentProgress / track.durationSec) * 100;

    timeCurrent.textContent = formatTime(currentProgress);
    progressFill.style.width = `${percent}%`;
    progressHandle.style.left = `${percent}%`;
}

// Seek functionality
progressBar.addEventListener('click', (e) => {
    if (currentTrackIndex === -1) return;

    const rect = progressBar.getBoundingClientRect();
    const clickX = e.clientX - rect.left;
    const percent = clickX / rect.width;

    const track = tracks[currentTrackIndex];
    currentProgress = track.durationSec * percent;

    updateProgressUI();
});

// Event Listeners
btnPlayPause.addEventListener('click', togglePlay);
btnNext.addEventListener('click', nextTrack);
btnPrev.addEventListener('click', prevTrack);
heroPlayBtn.addEventListener('click', () => {
    if (currentTrackIndex === -1) {
        playTrack(0);
    } else {
        togglePlay();
    }
});

// Nav Pills Interaction
document.querySelectorAll('.pill').forEach(pill => {
    pill.addEventListener('click', (e) => {
        document.querySelectorAll('.pill').forEach(p => p.classList.remove('active'));
        e.target.classList.add('active');

        // Fun visual change
        const color = e.target.dataset.color;
        document.documentElement.style.setProperty('--accent-blue', color);
    });
});

// Initialize
renderTracks();
