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

Koda is built entirely with Kotlin and Jetpack Compose, with a Material 3 Expressive design throughout. It streams music and video from YouTube, learns what you like, and does it without any official API keys. Everything runs through NewPipe Extractor and direct InnerTube calls.

There is one app with two modes. Leave the video toggle off and Koda is a full music player with a queue, downloads, and a library. Turn it on and the Home, Search, and Library tabs reshape into a proper video experience with a personalized feed, a real player, comments, subscriptions, and notifications.

## Features

### Music

- Search across the YouTube Music catalog for songs, albums, artists, and playlists.
- Personalized quick picks and recommendations, with a local taste profile built from your play history, liked songs, and searches so suggestions work even when you are logged out.
- Access your playlists, including Liked Songs, and browse artist and album pages with play, shuffle, and start-radio actions.
- Heart tracks straight from the player. Liked songs appear immediately and are kept locally, no login required.
- Sleep timer with a live countdown, available from both the mini player and the full player sheet.
- Synced lyrics.

### Video mode

- A personalized video home feed. It shows your real feed when you are signed in, and falls back to a taste based feed built from your watch history when you are not.
- A video player with the full quality ladder up to 2160p60 and a separate audio track, so it no longer gets stuck at a low muxed quality.
- Double tap either side to skip forward or back.
- Picture in Picture that captures just the video, not the whole app.
- Optional timed comments that fade in over the video as playback reaches the moment they mention.
- A Subscriptions tab with the latest uploads from channels you follow, a channel avatar rail, a full channel list, and drill in to any channel's uploads.
- A dedicated watch history tab that syncs your viewing back to YouTube.
- A notification inbox on the video home screen.

### Comments and engagement

- Read and write comments and replies right from the player. Yours appear instantly.
- Like, dislike, and subscribe from the player using your account.

### Playback

- High quality audio streaming via NewPipe Extractor and direct InnerTube resolution.
- Full queue control with drag to reorder, shuffle, and repeat modes.
- Background playback with system controls on the notification and lock screen through a Media3 media session.
- Optional local audio file playback.

### Downloads

- Download individual songs for offline listening.
- Batch download entire playlists.
- A download manager to track progress and manage saved files.

### Interface

- Material 3 Expressive design with shape morphing, spring physics, and dynamic color.
- Dynamic theming that pulls its palette from album artwork.
- Light, dark, and system themes.
- Gesture based navigation and physics based transitions.
- A rebuilt onboarding flow with a morphing shape hero, wavy progress indicator, and focused steps.

### Authentication

- Sign in through an embedded WebView. No password ever leaves the browser.
- Cookies are stored with EncryptedSharedPreferences.

---

## Technical Stack

| Component | Technology |
|-----------|------------|
| Language | Kotlin |
| UI Framework | Jetpack Compose |
| Design | Material 3 Expressive |
| Architecture | MVVM with StateFlow |
| Playback | Media3 ExoPlayer |
| Networking | OkHttp |
| Data Extraction | NewPipe Extractor and direct InnerTube |
| Image Loading | Coil |
| Concurrency | Kotlin Coroutines and Flow |
| Min SDK | 31 (Android 12) |
| Target SDK | 36 |

---

## Project Structure

```
app/src/main/java/com/ivor/ivormusic/
├── data/                    # Data layer
│   ├── YouTubeRepository    # YouTube Music and video via NewPipe and InnerTube
│   ├── DownloadRepository   # Download management
│   ├── SessionManager       # Auth and cookies
│   └── Models               # Song, Playlist, VideoItem, SubscriptionItem
├── service/                 # Background services
│   └── MusicService         # MediaLibraryService
└── ui/                      # Presentation layer
    ├── home/                # Home with recommendations and video feed
    ├── library/             # Playlists and liked songs
    ├── player/              # Music player and queue
    ├── video/               # Video player, comments, subscriptions, notifications
    ├── search/              # Search and video explore
    ├── downloads/           # Download manager
    ├── settings/            # App preferences
    ├── auth/                # YouTube sign in
    ├── onboarding/          # First run flow
    ├── components/          # Reusable UI components
    └── theme/               # Material 3 theming
```

---

## Technical Documentation

For a deep dive into the app's internals, data flows, and architecture, see the [Documentation Hub](docs/README.md).

## Getting Started

### Prerequisites

- Android Studio Ladybug or newer
- An Android device or emulator running API 31 or higher

### Installation

1. Clone the repository
   ```bash
   git clone https://github.com/Ivorisnoob/Koda.git
   ```
2. Open it in Android Studio.
3. Sync Gradle to download dependencies.
4. Run it on your device.

### YouTube account (optional)

Personalized feeds, watch history, comments, and engagement need a signed in account. To connect one, open Settings, tap Connect YouTube Account, and sign in with Google. Koda works without an account too, using a local taste profile for recommendations.

---

## Building

### Debug APK

```bash
./gradlew assembleDebug
```

### Release APK

Configure the keystore in `app/build.gradle.kts`, then:

```bash
./gradlew assembleRelease
```

APKs are split by ABI for smaller downloads:

- `armeabi-v7a` for 32-bit ARM
- `arm64-v8a` for 64-bit ARM
- a universal APK containing both

---

## Roadmap

- [x] Playlist management, create and edit playlists
- [x] Lyrics support with synced display
- [x] In app video with a personalized feed
- [ ] Advanced audio: equalizer, gapless playback, crossfade
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
