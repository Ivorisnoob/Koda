<p align="center">
  <img src="icon.svg" width="120" alt="Koda Logo"/>
</p>

<h1 align="center">Koda</h1>

<p align="center">
  <b>A modern Android music and video player powered by YouTube Music</b>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-green?style=for-the-badge&logo=android" alt="Platform"/>
  <img src="https://img.shields.io/badge/Min%20SDK-31-blue?style=for-the-badge" alt="Min SDK"/>
  <img src="https://img.shields.io/badge/Kotlin-100%25-purple?style=for-the-badge&logo=kotlin" alt="Kotlin"/>
  <img src="https://img.shields.io/badge/Jetpack%20Compose-UI-blue?style=for-the-badge&logo=jetpackcompose" alt="Compose"/>
</p>

<p align="center">
  <img src="https://img.shields.io/github/stars/Ivorisnoob/Koda?style=social" alt="Stars"/>
  <img src="https://img.shields.io/github/forks/Ivorisnoob/Koda?style=social" alt="Forks"/>
</p>

---

Koda is built entirely with Kotlin and Jetpack Compose, and it is built **ground-up on Material 3 Expressive** — not themed with it, constructed from it. Shapes, motion, color, and typography all resolve through a single `MaterialExpressiveTheme` at the root of the app, and every screen is composed from Expressive primitives. See [DESIGN.md](DESIGN.md) for how that works and why the design is not a swappable layer.

It streams music and video from YouTube, learns what you like, and does it without any official API keys. Everything runs through NewPipe Extractor and direct InnerTube calls.

There is one app with two modes. Leave the video toggle off and Koda is a full music player with a queue, downloads, playlists, statistics, and a library. Turn it on and the Home, Search, and Library tabs reshape into a proper video experience with a personalized feed, a real player, chapters, captions, comments, subscriptions, and notifications. No Google API key is ever required, and the whole app works — search, streaming, downloads, a local taste profile — even without signing in.

## Features

### Music

- **Full YouTube Music search** for songs, albums, artists, and playlists, with results grouped into tabs.
- **Search history** kept locally, with per-item removal and a clear-all action.
- **Personalized quick picks and recommendations**. A local taste profile is built from your play history, liked songs, and recent searches, so suggestions work even when you are logged out. Signed in, they blend in your real YouTube Music home feed.
- **Recently played** rail on Home that stays fresh across the app.
- **Your playlists**, including Liked Songs, plus artist and album pages with play, shuffle, and start-radio actions.
- **Start radio** from any track to spin up an endless, related-song queue.
- **Local playlists** you own: create, rename, edit, drag to reorder, and delete, with auto-generated cover art.
- **Add to playlist** and **add to queue** sheets reachable from the player and lists.
- **Like tracks straight from the player.** Liked songs appear immediately and are stored locally, no login required.
- **Sleep timer** with a live countdown, available from both the mini player and the full player sheet.
- **Synced lyrics** fetched from LRCLIB, scrolling in time with playback.
- **Listening statistics**: songs played, artists explored, liked-song count, top artist, top songs and artists, daily / weekly / monthly play charts, a listening streak, and recent searches.
- **Local audio playback** with MediaStore scanning, per-folder exclusion, and a high-compatibility scan mode for devices where MediaStore misses files.
- **Last-played song restoration** so the player comes back where you left it.

### Player styles

Eight distinct, fully animated player UIs, switchable from a spinnable style wheel:

- **Classic** — familiar transport with play / pause / next / previous.
- **Gesture** — swipeable carousel you flick between tracks.
- **Editorial** — two-tone magazine layout with die-cut art and a word-pill transport.
- **Poster** — kinetic type where the title itself is the progress display.
- **Bento** — a squish grid of flat tonal tiles with press physics.
- **Sticker** — a die-cut sticker with drag, peel, and squash-and-stretch physics.
- **Morph** — a living hero shape that cycles organic cuts while playing.
- **Dial** — a rotary tick-ring instrument you spin to scrub.

Plus an **ambient artwork background**, an optional **chromatic-mist** effect, and an option to color the expanded player's controls straight from the current cover art.

### Video mode

- **Personalized video home feed**. It shows your real YouTube feed when you are signed in, and falls back to a taste-based feed built from your watch history when you are not.
- **A real video player** with the full quality ladder up to 2160p60 and a separate audio track, so it never gets stuck on a low muxed quality. A **Default Video Quality** setting picks the starting resolution.
- **Chapters** rendered as seek-bar ticks, a current-chapter chip, and a chapters sheet.
- **Captions / subtitles** with a CC toggle and a language picker, sideloaded as WebVTT and rendered over the video.
- **Double-tap** either side to skip forward or back, and **hold to play at 2x**, with an adjustable playback speed.
- **Picture-in-Picture** that captures just the video, not the whole app.
- **Optional timed comments** that fade in over the video as playback reaches the moment they mention.
- **Share** a video, playlist, or album out to any app.
- **A Subscriptions tab** with the latest uploads from channels you follow, a channel-avatar rail, a full channel list, and drill-in to any channel's uploads.
- **A dedicated watch-history tab** that syncs your viewing back to YouTube.
- **A notification inbox** on the video home screen.
- **Video playlists** and a save-to-playlist sheet, including Watch Later and Liked videos.
- **A mini video player** so a clip keeps playing while you browse.

### Shorts (opt-in)

- A short-form vertical feed, off by default and only shown once you deliberately enable it.
- Swipe-through pager player with prefetch for instant next-video playback.
- An action rail for like, dislike, comments, and share — and you can hide any of those buttons you do not want.

### Comments and engagement

- Read and write comments and replies right from the player. Yours appear instantly.
- Like a comment, and delete your own.
- Like, dislike, and subscribe from the player using your account.

### Playback engine

- High-quality audio streaming via NewPipe Extractor and direct InnerTube resolution (ANDROID_VR / IOS clients for unciphered stream URLs).
- Background playback with system controls on the notification and lock screen through a Media3 media session, including a live progress bar in the notification.
- Full queue control with drag to reorder, shuffle, and repeat modes.
- Auto-load queue that appends recommended songs when the queue runs low.
- Crossfade between songs with an adjustable duration.
- Stream prefetch and an on-device cache with a configurable size limit and a one-tap clear.
- OEM and HyperOS fixes: high-compatibility scanning and a battery-optimization opt-out to keep background playback alive on aggressive devices.

### Downloads

- Download individual songs for offline listening.
- A download manager to track progress, cancel, delete, and clear failed downloads, with progress notifications.

### Interface and theming

Koda's interface is the product, not a wrapper around one. The whole app is constructed from Material 3 Expressive: 39 source files use Expressive-only APIs directly, `MaterialShapes` is referenced 131 times across 14 shapes, and spring physics drives 97 animation specs. There is no alternate design language and no fallback path — details and rationale in [DESIGN.md](DESIGN.md).

- Material 3 Expressive design with shape morphing, spring physics, and dynamic color, applied through one root `MaterialExpressiveTheme`.
- Dynamic theming that pulls its palette from your wallpaper (Android 12+) or from album artwork.
- Over two dozen curated color palettes across six families — Vibrant, Pastel, Aesthetic, Earthy, Moody, and Jewel & Mono.
- Light, dark, and system themes, plus an AMOLED true-black mode.
- Gesture-based navigation and physics-based transitions with a floating pill nav bar.
- A one-tap Home mode toggle to switch between music and video.
- A rebuilt onboarding flow with a morphing shape hero, wavy progress indicator, and focused first-run steps.
- A built-in updater that checks GitHub Releases, picks the right APK for your device's ABI, and installs it.

### Authentication

- Sign in through an embedded WebView. No password ever leaves the browser.
- Cookies are stored with EncryptedSharedPreferences and signed per-origin (SAPISIDHASH).
- Everything except personalized feeds, watch history, comments, and engagement works fully signed out.

---

## Technical Stack

| Component | Technology |
|-----------|------------|
| Language | Kotlin |
| UI Framework | Jetpack Compose |
| Design | Material 3 Expressive |
| Architecture | MVVM with StateFlow (no DI framework) |
| Playback | Media3 ExoPlayer / MediaLibraryService |
| Networking | OkHttp |
| Data Extraction | NewPipe Extractor and direct InnerTube |
| Image Loading | Coil |
| Concurrency | Kotlin Coroutines and Flow |
| Min SDK | 31 (Android 12) |
| Target SDK | 36 |
| Version | 4.2 |

---

## Project Structure

```
app/src/main/java/com/ivor/ivormusic/
├── data/                    # Data layer
│   ├── YouTubeRepository    # YouTube Music and video via NewPipe and InnerTube
│   ├── DownloadRepository   # Download management
│   ├── PlaylistRepository   # Local user playlists
│   ├── LikedSongsRepository # Local liked songs
│   ├── RecommendationEngine # Local taste profile and queue continuation
│   ├── StatsRepository      # Listening statistics
│   ├── LyricsRepository     # Synced lyrics (LRCLIB)
│   ├── UpdateRepository     # In-app updates from GitHub Releases
│   ├── SessionManager       # Auth and cookies
│   ├── ThemePreferences     # All app settings
│   └── Models               # Song, Playlist, VideoItem, SubscriptionItem, ...
├── service/                 # Background services
│   └── MusicService         # MediaLibraryService with live-progress notification
└── ui/                      # Presentation layer
    ├── home/                # Home with recommendations and video feed
    ├── library/             # Playlists, liked songs, statistics
    ├── player/              # Music player, queue, and the eight player styles
    ├── video/               # Video player, comments, subscriptions, notifications
    ├── shorts/              # Opt-in Shorts player
    ├── search/              # Search and video explore
    ├── artist/              # Artist and album pages
    ├── downloads/           # Download manager
    ├── settings/            # App preferences and updates
    ├── auth/                # YouTube sign in
    ├── onboarding/          # First run flow
    ├── components/          # Reusable UI components
    └── theme/               # Material 3 theming and color palettes
```

---

## Getting Started

### Prerequisites

- Android Studio Ladybug or newer
- An Android device or emulator running API 31 (Android 12) or higher

### Installation

1. Clone the repository
   ```bash
   git clone https://github.com/Ivorisnoob/Koda.git
   ```
2. Open it in Android Studio.
3. Sync Gradle to download dependencies.
4. Run it on your device.

### YouTube account (optional)

Personalized feeds, watch history, comments, and engagement need a signed-in account. To connect one, open Settings, tap Connect YouTube Account, and sign in with Google. Koda works without an account too, using a local taste profile for recommendations.

---

## Building

### Debug APK

```bash
./gradlew assembleDebug
```

### Release APK

Configure the keystore in `app/build.gradle.kts` (or the `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD` env vars), then:

```bash
./gradlew assembleRelease
```

APKs are split by ABI for smaller downloads:

- `armeabi-v7a` for 32-bit ARM
- `arm64-v8a` for 64-bit ARM
- a universal APK containing both

For a deeper dive into the architecture, data flows, and the InnerTube layer, see [`CLAUDE.md`](CLAUDE.md). For the design system — shape, motion, color, and the rules for contributing UI code — see [`DESIGN.md`](DESIGN.md).

---

## Roadmap

- [x] Playlist management, create and edit playlists
- [x] Lyrics support with synced display
- [x] In-app video with a personalized feed, chapters, and captions
- [x] Multiple player styles and a full color-palette system
- [x] Listening statistics
- [ ] Advanced audio: equalizer and gapless playback
- [ ] Home screen widget for playback controls
- [ ] Kotlin Multiplatform for desktop and iOS

---

## Contributing

Contributions are welcome, whether it is a bug report, a feature idea, or a pull request. See [CONTRIBUTING.md](CONTRIBUTING.md) to get started.

---

## License

GNU GPL V3

See the [LICENSE](LICENSE) file.

---

<p align="center">
  Made by <b>ivorisnoob</b>
  <br/>
  Copyright 2026
</p>
