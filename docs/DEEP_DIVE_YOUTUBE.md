# Deep Dive: YouTube Extraction & Integration

This document outlines the specialized techniques used to interface with YouTube and YouTube Music without official API keys.

## Data Source Strategy

The application uses a hybrid model of public extraction and private API interaction.

### 1. NewPipeExtractor Integration
Used as the primary engine for public data.
-   **Function**: Parses search results, artist profiles, and playlist metadata.
-   **Implementation**: Utilizes `NewPipeDownloaderImpl` (an OkHttp wrapper) to fetch HTML/JSON from YouTube's desktop frontend.
-   **Parsing**: Leverages the `StreamExtractor` and `SearchExtractor` classes from the NewPipe library.

### 2. InnerTube API (Private)
Used for authenticated and resilient data fetching.
-   **Endpoint**: `https://music.youtube.com/youtubei/v1/`
-   **Client Identifiers**:
    -   `WEB_REMIX`: Used for home recommendations and personalized mixes.
    -   `ANDROID`: Used as a fallback for stream resolution, as it is less prone to "Page needs to be reloaded" bot-detection errors.
-   **API Key**: Uses the standard public InnerTube key used by all YouTube clients.

## Authentication & Cookies

Authentication is essential for features like "Your Supermix" and "Liked Songs".

### 1. Session Persistence
Managed by `SessionManager`. Cookies are captured via a `WebView` during the login process and stored in `EncryptedSharedPreferences`.

### 2. SAPISIDHASH Calculation
To make authenticated requests to InnerTube, the app must generate a time-limited `Authorization` header.
```kotlin
// Simplified Logic
val sapisid = getCookieValue(cookies, "SAPISID")
val timestamp = System.currentTimeMillis() / 1000
val input = "$timestamp $sapisid $origin"
val hash = sha1(input)
val header = "SAPISIDHASH ${timestamp}_${hash}"
```
This header is required by Google servers to verify that the cookies are being used by a legitimate client context.

## Stream URL Resolution

Stream URLs are temporary and tied to IP addresses. The app implements a multi-stage resolution process:

1.  **Cache Check**: Verifies if the video ID is present in `CacheManager` (Media3 SimpleCache).
2.  **NewPipe Extraction**: First attempt at extracting the `bestAudioStream`.
3.  **Fallback (InnerTube Android)**: If NewPipe fails or returns a restricted stream, the app calls the `player` endpoint using the `ANDROID` client signature.
4.  **Error Handling**: If a 403 (Forbidden) is encountered during playback, the `MusicService` triggers a re-resolution event to fetch a fresh URL.

## Recommendation Flow

YouTube Music recommendations are parsed from complex JSON structures (Browse response).
-   **Seed**: The home screen often uses `FEmusic_home` as the browse ID.
-   **Processing**: The app traverses the `sectionListRenderer` to find `musicTwoColumnItemRenderer` elements, extracting song metadata and video IDs.
-   **Pagination**: Handled via `continuationToken` logic, allowing the `HomeViewModel` to load more content as the user scrolls.

## Performance Optimization
-   **Thumbnail Resolution**: The app automatically upgrades low-res thumbnails (e.g., `s90-c`) to high-res versions by modifying the URL parameters before display.
-   **Reporting Playback**: To keep the user's YouTube history accurate, the app calls the `playback/reportPlayback` endpoint after 15 seconds of sustained listening.
