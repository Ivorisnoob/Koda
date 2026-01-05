# The Music App

A modern, feature-rich Android music player that seamlessly integrates with YouTube Music. Built entirely with Kotlin and Jetpack Compose, IvorMusic delivers a premium listening experience with Material 3 Expressive design, bringing personalized recommendations, playlist management, and high-quality streaming without requiring official API keys.

## Screenshots

### Home Screen

| Light Mode | Dark Mode |
|:----------:|:---------:|
| ![Home Screen - Light Mode](screenshots/Home_Light.png) | ![Home Screen - Dark Mode](screenshots/Home_Dark.png) |

### Player Screen

| Light Mode | Dark Mode |
|:----------:|:---------:|
| ![Player Screen - Light Mode](screenshots/Player_Light.png) | ![Player Screen - Dark Mode](screenshots/Player_Dark.png) |

## Features

### YouTube Music Integration
- **Search**: Find songs, albums, artists, and playlists directly from the YouTube Music catalog
- **Personalized Recommendations**: Quick picks and curated suggestions based on your listening history
- **Playlist Access**: Browse and play your YouTube Music playlists including "Liked Songs" and "Supermix"
- **History Sync**: Tracks you play are synchronized with your YouTube Music history
- **Like/Unlike**: Heart your favorite songs directly from the player

### Playback
- **High-Quality Streaming**: Optimized audio streams using NewPipe Extractor for reliable playback
- **Queue Management**: Full queue control with add, reorder, and infinite recommendations loading
- **Shuffle and Repeat**: Standard shuffle and repeat modes (off, one, all)
- **Media Session**: System-wide playback controls via notification and lock screen
- **Local Music Support**: Optional playback of locally stored audio files

### User Interface
- **Material 3 Expressive**: Implements the latest Material Design 3 with expressive shape morphing, spring physics animations, and dynamic color schemes
- **Dynamic Theming**: Album artwork automatically influences the player color palette
- **Light, Dark, and System Themes**: Choose your preferred appearance
- **Full-bleed Album Art**: Immersive player experience with large artwork display
- **Animated Transitions**: Smooth, physics-based animations throughout the UI
- **Bottom Sheet Player**: Expandable player with gesture support

### Library
- **YouTube Playlists**: Access all your saved playlists with cover art and track counts
- **Liked Songs**: Dedicated section for your hearted tracks
- **Quick Access Cards**: Fast navigation to frequently used sections

### Authentication
- **Cookie-based Authentication**: Simple sign-in via embedded WebView to access personalized content
- **Secure Storage**: Credentials stored securely using Android EncryptedSharedPreferences

## Technical Stack

| Component | Technology |
|-----------|------------|
| Language | Kotlin |
| UI Framework | Jetpack Compose |
| Architecture | MVVM with StateFlow |
| Playback | Media3 ExoPlayer |
| Networking | OkHttp |
| Data Extraction | NewPipe Extractor |
| Image Loading | Coil |
| Concurrency | Kotlin Coroutines & Flow |
| Min SDK | 31 (Android 12) |
| Target SDK | 36 |

## Project Structure

```
app/src/main/java/com/ivor/ivormusic/
├── data/                    # Data layer (repositories, models, session management)
│   ├── YouTubeRepository    # YouTube Music API integration via NewPipe
│   ├── SessionManager       # Cookie and authentication management
│   └── Song, Playlist       # Data models
├── service/                 # Background services
│   └── MusicService         # MediaLibraryService for playback
└── ui/                      # Presentation layer
    ├── home/                # Home screen with recommendations
    ├── library/             # Library with playlists and liked songs
    ├── player/              # Expandable player with queue
    ├── search/              # Search functionality
    ├── settings/            # App preferences
    ├── auth/                # YouTube sign-in dialog
    ├── components/          # Reusable UI components
    └── theme/               # Material 3 theming
```

## Setup and Installation

1. Clone the repository to your local machine
2. Open the project in Android Studio (Ladybug or newer recommended)
3. Sync the Gradle project to download all dependencies
4. Connect an Android device (API 31+) or start an emulator
5. Run the `app` configuration

### YouTube Music Features
To access personalized features (recommendations, playlists, liked songs, history sync):
1. Launch the app and navigate to Settings
2. Tap "Connect YouTube Account"
3. Sign in with your Google account through the embedded browser
4. Once authenticated, personalized content will be available

## Building Release APK

The project supports ABI splits for optimized APK sizes:
- `armeabi-v7a` for 32-bit ARM devices
- `arm64-v8a` for 64-bit ARM devices

To build a release APK, configure the keystore in `app/build.gradle.kts` and run:
```bash
./gradlew assembleRelease
```

## Future Goals

Development continues with a focus on expanding capabilities and improving reliability:

- **Offline Support**: Download manager for saving tracks for offline listening
- **Enhanced Playlist Management**: Create, edit, and reorganize playlists within the app
- **Advanced Audio Features**: Equalizer, gapless playback, and crossfade support
- **Improved Authentication**: Streamlined login process with better session management
- **Cross-Platform Availability**: Exploring Kotlin Multiplatform (KMP) for desktop and iOS

## Contributing

Contributions are welcome. Please open an issue first to discuss proposed changes before submitting a pull request.

## License

This project is licensed under the **Creative Commons Attribution-NonCommercial 4.0 International License (CC BY-NC 4.0)**.

You are free to:
- **Share**: Copy and redistribute the material in any medium or format
- **Adapt**: Remix, transform, and build upon the material

Under the following terms:
- **Attribution**: You must give appropriate credit, provide a link to the license, and indicate if changes were made
- **NonCommercial**: You may not use the material for commercial purposes

For the full license text, see the [LICENSE](LICENSE) file or visit [creativecommons.org/licenses/by-nc/4.0](https://creativecommons.org/licenses/by-nc/4.0/)

Copyright 2026 Harsh Nandha
