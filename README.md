# The Music App

The Music App is an Android-based music player application built using Kotlin and Jetpack Compose. It serves as a unified interface for streaming music from YouTube Music without requiring official API keys, utilizing the NewPipe Extractor and internal YouTube Music APIs. The application is designed with a focus on modern aesthetics, leveraging Material 3 Expressive design principles to offer a seamless and visually appealing user experience.

*Note: The project repository is named `IvorMusic`.*

## key Features

### Core Functionality
- **YouTube Music Integration**: Search for songs, albums, and playlists directly from YouTube Music.
- **Streaming**: High-quality audio streaming using optimized stream, bypassing standard restrictions.
- **Recommendations**: Personalized quick picks and recommendations based on user history (requires session cookies).
- **Library Management**: Access to user playlists, including "Your Likes" and "Supermix".
- **Local Playback**: Option to load and play locally stored music files.

### User Interface
- **Material 3 Design**: Implements the latest Material Design 3 guidelines with dynamic theming.
- **Dark Mode**: Fully supported dark theme for low-light environments.
- **Responsive Layouts**: Adaptive UI components that look great on various screen sizes.

## Technical Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Architecture**: MVVM (Model-View-ViewModel)
- **Networking**: OkHttp
- **Data Extraction**: NewPipe Extractor
- **Concurrency**: Kotlin Coroutines & Flow

## Setup and Installation

1. Clone the repository to your local machine.
2. Open the project in Android Studio (Ladybug or newer recommended).
3. Sync the Gradle project to ensure all dependencies are downloaded.
4. Connect an Android device or start an emulator.
5. Run the `app` configuration.

## Future Goals

The development roadmap for IvorMusic is focused on expanding capabilities and refining reliability. Upcoming milestones include:

- **Offline Support**: Implementing a robust download manager to allow users to save tracks for offline listening.
- **Enhanced Playlist Management**: Adding functionality to create, edit, and reorganize playlists directly within the app.
- **Advanced Audio Features**: Integrating an equalizer, gapless playback, and crossfade support.
- **Improved Authentication**: streamlining the login process to make session management more user-friendly and secure.
- **Cross-Platform Availability**: Exploring Kotlin Multiplatform (KMP) to bring the IvorMusic experience to desktop and iOS environments.
