<p align="center">
  <img src="icon.svg" width="120" alt="Koda logo"/>
</p>

<h1 align="center">🎧 Koda</h1>

<p align="center">
  <b>A music and video player for Android, powered by YouTube Music.</b>
</p>

<p align="center">
  <a href="https://github.com/Ivorisnoob/Koda/releases/latest"><img src="https://img.shields.io/github/v/release/Ivorisnoob/Koda?style=for-the-badge&label=Download&color=6750A4" alt="Latest release"/></a>
  <a href="https://github.com/Ivorisnoob/Koda/releases"><img src="https://img.shields.io/github/downloads/Ivorisnoob/Koda/total?style=for-the-badge&color=1B6C3A" alt="Downloads"/></a>
  <a href="LICENSE"><img src="https://img.shields.io/github/license/Ivorisnoob/Koda?style=for-the-badge&color=8B4513" alt="GPL-3.0 license"/></a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-11%2B-green?style=for-the-badge&logo=android" alt="Android 11 and newer"/>
  <img src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-purple?style=for-the-badge&logo=kotlin" alt="Kotlin and Jetpack Compose"/>
  <a href="https://t.me/ivorisnoob_chat"><img src="https://img.shields.io/badge/Telegram-Community-2CA5E0?style=for-the-badge&logo=telegram&logoColor=white" alt="Telegram community"/></a>
</p>

<p align="center">
  <sub>
    <a href="#-download">Download</a> •
    <a href="#-features">Features</a> •
    <a href="#-using-koda-without-an-account">No Account?</a> •
    <a href="#-screenshots">Screenshots</a> •
    <a href="#-building-from-source">Build</a> •
    <a href="#-community-and-contributing">Community</a> •
    <a href="#-security">Security</a> •
    <a href="#-license">License</a>
  </sub>
</p>

<br/>

Koda combines a complete music player and a complete video player in one app. Switch modes from Home and the feed, search, library, and player adapt with it.

It works without an official YouTube API key, and an account is optional. Search, playback, downloads, local playlists, likes, subscriptions, and recommendations can all work on the device; signing in adds your YouTube feeds, library, history, comments, and other account features.

<p align="center">
  <img src="https://raw.githubusercontent.com/Ivorisnoob/Koda/main/Screenshots/01-home-music-your-mix.png" width="24%" alt="Music home"/>
  <img src="https://raw.githubusercontent.com/Ivorisnoob/Koda/main/Screenshots/03-player-editorial.png" width="24%" alt="Editorial music player"/>
  <img src="https://raw.githubusercontent.com/Ivorisnoob/Koda/main/Screenshots/41-home-video-recommended.png" width="24%" alt="Video home"/>
  <img src="https://raw.githubusercontent.com/Ivorisnoob/Koda/main/Screenshots/42-video-watch-page.png" width="24%" alt="Video watch page"/>
</p>

<br/>

## 📥 Download

Download the latest APK from **[GitHub Releases](https://github.com/Ivorisnoob/Koda/releases/latest)**. Koda requires Android 11 or newer.

| APK | Use it for |
|---|---|
| `arm64-v8a` | Almost every modern Android phone. Start here. |
| `armeabi-v7a` | Older 32-bit devices. |
| `universal` | A larger build containing both architectures. |

Android will ask for permission to install an app from outside the Play Store. After installation, Koda's updater can check GitHub Releases and select the correct APK automatically.

> **Official builds are published only on GitHub and in the [Telegram community](https://t.me/ivorisnoob_chat).** See [SECURITY.md](SECURITY.md) before installing a build from anywhere else.

<br/>

## ✨ Features

### 🎵 Music and library

- Search YouTube Music for songs, albums, artists, and playlists, with paged results and local search history.
- Search by voice from inside Koda.
- Choose between the Your Mix and Spotlight home layouts.
- Get recommendations from local listening activity, with YouTube Music recommendations added when signed in.
- Start a radio from any song and automatically extend the queue with related music.
- Browse recently played music, albums, artists, playlists, and liked songs.
- Create, rename, reorder, edit, and delete device playlists, with generated or custom cover art.
- Edit playlists you own on YouTube Music and save other people's playlists or albums to your library.
- Like songs without an account or sync likes to YouTube Music when signed in.
- Play music stored on the device, with folder exclusions and compatibility scanning.
- Sort the library by title, artist, date added, or play count.
- View listening history, charts, streaks, top songs, top artists, and other statistics.
- Open Ready offline to see fully cached songs that can play without a connection.

### 🎤 Lyrics

- Synced, word-timed, line-timed, and plain-text lyrics from multiple providers.
- Local `.lrc` and `.ttml` files, embedded lyrics, and offline lyric playback for device music.
- Adjustable lyric timing and smooth word-by-word highlighting across every player style.

### ▶️ Music playback

- Eight player styles: Classic, Gesture, Editorial, Canvas, Bento, Sticker, Morph, and Dial.
- A shared editable queue with Play next, Add to queue, drag-to-reorder, swipe-to-remove, and undo.
- Shuffle and repeat that survive app restarts.
- AutoMix transitions, manual crossfade durations, and short overlaps on manual skips.
- Optional volume normalisation between tracks.
- A sleep timer with timed and end-of-track modes.
- Per-network music quality settings for Wi-Fi and mobile data.
- Background playback with notification, lock-screen, headset, Bluetooth, and Android Auto controls.
- Session restoration, stream prefetching, and a configurable on-device cache.

### 📺 Video

- A personalized video home feed, plus local recommendations when signed out.
- Search videos, channels, and playlists with date, duration, and sorting filters.
- Video quality controls with available resolutions up to 2160p60.
- Captions with remembered language and on/off state, plus adjustable size, color, and background.
- Chapters, storyboard seek previews, playback speed, double-tap seeking, and hold-to-2x.
- Fullscreen brightness and volume gestures, with an option to remember fullscreen brightness.
- Picture-in-Picture with playback controls.
- A mini player that keeps playing while you browse.
- Video queues with next, previous, reordering, removal, and playlist continuation.
- Device-local video playlists, Watch Later, liked videos, and saved YouTube playlists.
- Timed comments that can appear over the video at the moment they reference.
- Tappable links and timestamps in descriptions and comments.
- Video watch history, a notification inbox, sharing, and autoplay controls.
- Open shared YouTube watch, Shorts, live, playlist, music, and channel links directly in Koda.

### 📡 Channels and subscriptions

- Full channel pages with the creator's available videos, Shorts, live streams, playlists, posts, releases, and other tabs.
- Search and sort a channel's uploads.
- Follow channels on the device, through a YouTube account, or both.
- A subscriptions feed with channel filters, groups, upload-age filters, and date ordering.
- Import subscriptions from NewPipe, PipePipe, Tubular, Google Takeout, or OPML, and export a NewPipe-compatible file.
- Optional background notifications for new uploads, with per-channel controls.
- Hide individual recommendations or block channels, with undo and a screen for managing hidden items.

### 🔴 Live streams and Shorts

- Live video playback with adaptive quality, viewer counts, DVR controls, and a jump-to-live action.
- Live chat with messages, Super Chats, stickers, memberships, pinned messages, badges, and channel emoji.
- Send live-chat messages when signed in.
- A dedicated full-screen layout for vertical live streams and a side-by-side video and chat layout in landscape.
- An optional swipe-through Shorts player with prefetching, comments, likes, dislikes, and sharing.
- One app-wide switch removes Shorts surfaces when you do not want them.

### 💬 Comments and account actions

- Read, write, reply to, like, and delete comments from the video player.
- Like, dislike, subscribe, and manage account playlists when signed in.
- Changes to account-owned playlists, likes, subscriptions, comments, and history sync back to YouTube.

### ⬇️ Downloads and offline playback

- Download songs, videos, albums, and playlists for offline playback.
- Choose video quality and see an estimated size before downloading.
- Store portable music and video files under `Downloads/Koda` so other apps can open them.
- Keep downloads running in the background with progress notifications.
- Manage music and video downloads with progress, cancel, delete, and retry controls.
- Skip files already downloaded when restarting an album or playlist download.
- Optional Android 16 Live Updates for download and music-playback progress.

### 🎨 Appearance and controls

- Material 3 Expressive design with dynamic color, fixed palettes, artwork colors, and AMOLED black.
- Light, dark, and system themes.
- A choice between an expressive floating navigation pill and a standard bottom navigation bar.
- Ambient artwork and chromatic-mist player backgrounds.
- Four haptic levels: Off, Subtle, Balanced, and Rich.
- Searchable settings grouped into focused pages.
- A guided onboarding flow and a built-in update checker.
- English plus 25 translations, with per-app language selection on supported Android versions.

### 🔗 System integration

- Chromecast playback for music and video, including queues, captions, and live streams.
- Six home-screen widgets covering artwork, transport controls, progress, shuffle, repeat, and Up Next.
- A Quick Settings tile for music playback.
- Android Auto browsing, search, playback, and voice requests.
- Assistant playback requests such as asking Google to play a song on Koda.
- System media controls for both music and video.
- An in-app bug report screen with logs and device details that you review before sharing.
- An optional daily time limit with separate budgets for each day of the week.

### 👤 Accounts, profiles, and backup

- Use Koda without a Google account through a device-local profile.
- Add several YouTube accounts and switch between them without signing in again.
- Keep subscriptions and hidden recommendations separate per profile while sharing device libraries and downloads.
- Sign in through an embedded browser or paste session cookies when the browser flow is unavailable.
- Store account sessions encrypted on the device.
- Back up and restore playlists, likes, saved items, statistics, history, subscriptions, hidden recommendations, profiles, artwork, and settings.
- Backups exclude account credentials and downloaded media.

<br/>

## 🔓 Using Koda without an account

| Without an account | Signing in adds |
|---|---|
| Search and stream music or video | Your YouTube and YouTube Music home feeds |
| Download media and play device music | Account playlists, likes, subscriptions, and history |
| Create music and video playlists | Comments, replies, live-chat sending, likes, and dislikes |
| Like songs and follow channels on the device | Changes synchronized with YouTube |
| Use local recommendations, history, statistics, and the blocklist | Multiple stored YouTube profiles |

> Account actions affect the connected YouTube account. Koda asks for confirmation before destructive actions such as deleting an account-owned playlist.

<br/>

## 🖼️ Screenshots

> The current gallery was captured on Koda 4.4 and may differ slightly from the latest release.

<details>
<summary><b>Open the screenshot gallery</b></summary>
<br/>

### Music

<p align="center">
  <img src="https://raw.githubusercontent.com/Ivorisnoob/Koda/main/Screenshots/01-home-music-your-mix.png" width="30%" alt="Music home"/>
  <img src="https://raw.githubusercontent.com/Ivorisnoob/Koda/main/Screenshots/05-library-music.png" width="30%" alt="Music library"/>
  <img src="https://raw.githubusercontent.com/Ivorisnoob/Koda/main/Screenshots/07-search-music-songs.png" width="30%" alt="Music search"/>
</p>

### Player styles

<p align="center">
  <img src="https://raw.githubusercontent.com/Ivorisnoob/Koda/main/Screenshots/10-player-classic.png" width="23%" alt="Classic player"/>
  <img src="https://raw.githubusercontent.com/Ivorisnoob/Koda/main/Screenshots/03-player-editorial.png" width="23%" alt="Editorial player"/>
  <img src="https://raw.githubusercontent.com/Ivorisnoob/Koda/main/Screenshots/12-player-canvas.png" width="23%" alt="Canvas player"/>
  <img src="https://raw.githubusercontent.com/Ivorisnoob/Koda/main/Screenshots/17-player-dial.png" width="23%" alt="Dial player"/>
</p>

### Video and subscriptions

<p align="center">
  <img src="https://raw.githubusercontent.com/Ivorisnoob/Koda/main/Screenshots/41-home-video-recommended.png" width="30%" alt="Video home"/>
  <img src="https://raw.githubusercontent.com/Ivorisnoob/Koda/main/Screenshots/42-video-watch-page.png" width="30%" alt="Video player"/>
  <img src="https://raw.githubusercontent.com/Ivorisnoob/Koda/main/Screenshots/46-subscriptions-feed.png" width="30%" alt="Subscriptions feed"/>
</p>

### Settings and profiles

<p align="center">
  <img src="https://raw.githubusercontent.com/Ivorisnoob/Koda/main/Screenshots/22-settings-hub.png" width="30%" alt="Settings"/>
  <img src="https://raw.githubusercontent.com/Ivorisnoob/Koda/main/Screenshots/23-settings-color-palette.png" width="30%" alt="Color palette picker"/>
  <img src="https://raw.githubusercontent.com/Ivorisnoob/Koda/main/Screenshots/49-profiles-sheet-signed-in.png" width="30%" alt="Account profiles"/>
</p>

</details>

<br/>

## 🛠️ Building from source

Use the latest stable Android Studio and a device or emulator running Android 11 or newer.

```bash
git clone https://github.com/Ivorisnoob/Koda.git
cd Koda
./gradlew assembleDebug
```

Release builds expect `keystore/ivormusic.jks`. Signing values can be placed in the gitignored `local.properties` file as `keystore.storePassword`, `keystore.keyAlias`, and `keystore.keyPassword`, or supplied through `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD` environment variables.

**Contributor and maintainer documentation:**

| Document | Purpose |
|---|---|
| [CLAUDE.md](CLAUDE.md) | Architecture, invariants, data flows, and development rules |
| [DESIGN.md](DESIGN.md) | The design system and UI conventions |
| [ROADMAP.md](ROADMAP.md) | Planned work, known defects, and shipped milestones |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Current contribution policy and setup |
| [SECURITY.md](SECURITY.md) | Security scope and private reporting |

<br/>

## 🤝 Community and contributing

Join the [Telegram community](https://t.me/ivorisnoob_chat) for help, beta builds, and discussion. Use [GitHub Issues](https://github.com/Ivorisnoob/Koda/issues) for bug reports and feature requests so they remain trackable.

> External pull requests are temporarily paused while the planned work in [ROADMAP.md](ROADMAP.md) is completed. Discussion and issue reports remain welcome; see [CONTRIBUTING.md](CONTRIBUTING.md) for the current policy and [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) for community expectations.

<br/>

## 🔒 Security

Please report vulnerabilities privately through [GitHub Security Advisories](https://github.com/Ivorisnoob/Koda/security/advisories), not through a public issue or chat. See [SECURITY.md](SECURITY.md) for details.

<br/>

## 📄 License

Koda is licensed under the [GNU General Public License v3.0](LICENSE).

---

<p align="center">
  Made by <b>ivorisnoob</b><br/>
  Copyright 2026
</p>
