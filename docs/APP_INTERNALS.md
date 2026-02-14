# Application Internals: NewPipe Extractor, Authentication, and Data Fetching

This document details the internal mechanisms of the application, focusing on the integration of **NewPipe Extractor**, the **Authentication System**, and how data such as **Profile Pictures (PFP)**, **Liked Songs**, and **Playlists** are fetched and managed.

## 1. NewPipe Extractor Integration

The application relies heavily on the [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor) library to interact with YouTube Music without using the official, rate-limited YouTube Data API. This allows for searching, streaming, and fetching metadata anonymously or with user cookies.

### Initialization
The integration is centralized in `YouTubeRepository.kt`. The extractor is initialized once during the repository's instantiation:

```kotlin
// app/src/main/java/com/ivor/ivormusic/data/YouTubeRepository.kt

init {
    initializeNewPipe()
}

private fun initializeNewPipe() {
    if (!isInitialized) {
        try {
            // Initialize NewPipe with a custom Downloader implementation
            NewPipe.init(NewPipeDownloaderImpl(okHttpClient, sessionManager))
            isInitialized = true
        } catch (e: Exception) {
            // Handle re-initialization gracefully
            isInitialized = true
        }
    }
}
```

### Custom Downloader: `NewPipeDownloaderImpl`
To enable authenticated requests (e.g., fetching private playlists or liked songs), the app implements a custom `Downloader` called `NewPipeDownloaderImpl`. This class wraps `OkHttpClient` and injects the user's cookies into every request made by NewPipe.

**Key Features:**
*   **Cookie Injection:** Retrieves cookies from `SessionManager` and adds them to the `Cookie` header.
*   **User-Agent Consistency:** Enforces a consistent `User-Agent` (defined in `YouTubeRepository.BROWSER_USER_AGENT`) to mimic a real browser and avoid "Page needs to be reloaded" errors.
*   **ReCaptcha Handling:** Detects 429 errors and throws `ReCaptchaException`.

```kotlin
// app/src/main/java/com/ivor/ivormusic/data/NewPipeDownloaderImpl.kt

override fun execute(request: ExtractorRequest): Response {
    val requestBuilder = Request.Builder()
        .url(request.url())
        // ... method setup ...

    // Inject Cookies
    sessionManager?.getCookies()?.let { cookies ->
        if (cookies.isNotEmpty()) {
            requestBuilder.addHeader("Cookie", cookies)
        }
    }

    // Enforce User-Agent
    if (!headers.containsKey("User-Agent")) {
        requestBuilder.addHeader("User-Agent", YouTubeRepository.BROWSER_USER_AGENT)
    }

    // ... execution ...
}
```

### Usage Scenarios
*   **Search:** `search(query)` uses `ServiceList.all().find { ... }.getSearchExtractor(...)` to parse search results (Songs, Videos, Albums, Playlists).
*   **Streaming:** `getStreamUrl(videoId)` uses `getStreamExtractor(url)` to extract audio/video stream URLs (DASH, HLS, or progressive).
*   **Playlists:** `getPlaylist(playlistId)` uses `getPlaylistExtractor(url)` to parse public playlists.

## 2. Authentication System

The application mimics a web browser login to authenticate with YouTube Music. It does *not* use OAuth. Instead, it captures the session cookies directly.

### The Login Flow (`YouTubeAuthDialog.kt`)
1.  **User Interface:** A `BasicAlertDialog` containing a `WebView`.
2.  **Navigation:** The `WebView` loads the Google login URL: `https://accounts.google.com/ServiceLogin?service=youtube&continue=https://music.youtube.com`.
3.  **Cookie Interception:** A `WebViewClient` monitors page loads. When the URL indicates a successful login (or simply when cookies are present), it checks for the `SAPISID` cookie.
4.  **Storage:** If `SAPISID` is found, the entire cookie string is saved to `SessionManager`.

```kotlin
// app/src/main/java/com/ivor/ivormusic/ui/auth/YouTubeAuthDialog.kt

webViewClient = object : WebViewClient() {
    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        val cookies = CookieManager.getInstance().getCookie(url)
        // SAPISID is the key cookie for generating auth headers
        if (cookies != null && cookies.contains("SAPISID")) {
            sessionManager.saveCookies(cookies)
            onAuthSuccess()
        }
    }
}
```

### Authorization Header Generation (`YouTubeAuthUtils.kt`)
YouTube's internal API requires a specifically formatted `Authorization` header derived from the `SAPISID` cookie. This is handled in `YouTubeAuthUtils`.

**Algorithm:**
1.  Extract `SAPISID` from the cookie string.
2.  Get the current timestamp (seconds).
3.  Construct the input string: `{timestamp} {SAPISID} {origin}` (Origin is usually `https://music.youtube.com`).
4.  Compute the SHA-1 hash of this input.
5.  Format the header: `SAPISIDHASH {timestamp}_{hash_hex}`.

```kotlin
// app/src/main/java/com/ivor/ivormusic/data/YouTubeAuthUtils.kt

fun getAuthorizationHeader(cookieString: String, origin: String): String? {
    val sapisid = getCookieValue(cookieString, "SAPISID") ?: return null
    val timestamp = System.currentTimeMillis() / 1000
    val input = "$timestamp $sapisid $origin"
    val hash = sha1(input) // Simplified
    return "SAPISIDHASH ${timestamp}_${hash}"
}
```

## 3. Data Fetching & Internals

### Profile Picture (PFP)
Fetching the user's profile picture is part of the account info synchronization process.

*   **Trigger:** `HomeViewModel.checkYouTubeConnection()` calls `YouTubeRepository.fetchAccountInfo()`.
*   **Endpoint:** The app calls the internal `account/account_menu` endpoint.
*   **Parsing:** The response JSON is parsed to find the active account header (`activeAccountHeaderRenderer`).
*   **Resolution Upgrade:** The extracted URL usually has a low resolution (e.g., `s88`). The code replaces `=s88` with `=s512` to get a high-quality image.

```kotlin
// app/src/main/java/com/ivor/ivormusic/data/YouTubeRepository.kt

// Inside fetchAccountInfo()
val header = popup?.optJSONObject("header")?.optJSONObject("activeAccountHeaderRenderer")
val thumbnails = header.optJSONObject("avatar")?.optJSONArray("thumbnails")
val avatarUrl = thumbnails?.optJSONObject(thumbnails.length() - 1)?.optString("url")

// Upgrade resolution
val highResUrl = avatarUrl?.replace("=s88", "=s512")
sessionManager.saveUserAvatar(highResUrl)
```

### Liked Songs
The "Liked Songs" list is a combination of songs liked on YouTube and songs liked locally within the app.

1.  **YouTube Source (`YouTubeRepository`):**
    *   Uses the internal API endpoint `FEmusic_liked_videos` (Auto-playlist for Liked Music).
    *   Handles **Pagination**: The API returns a continuation token. The code loops, fetching subsequent pages until no token remains or a limit is reached.
    *   **Fallback:** If the API fails, it falls back to parsing the "LM" playlist using NewPipe.

2.  **Local Source (`LikedSongsRepository`):**
    *   Stores a set of Liked Song IDs in `SharedPreferences` (`ivor_music_liked_songs`).
    *   This allows users to "like" local files or YouTube songs even when offline (though syncing back to YouTube might not be immediate/implemented).

3.  **Merging (`HomeViewModel`):**
    *   The `likedSongs` StateFlow combines the YouTube list and the local ID set.
    *   It filters the global list of loaded songs to find those matching the local liked IDs and merges them with the YouTube results.

```kotlin
// app/src/main/java/com/ivor/ivormusic/data/YouTubeRepository.kt

suspend fun getLikedMusic(): List<Song> {
    // ... loop with continuation token ...
    val json = fetchInternalApi("FEmusic_liked_videos")
    val songs = parseSongsFromInternalJson(json)
    // ...
}
```

### Playlists
User playlists are fetched from the YouTube Music library.

*   **Endpoint:** `FEmusic_liked_playlists` (Liked Playlists).
*   **Virtual Playlists:** The app manually injects two "virtual" playlists at the top:
    1.  **"My Supermix"**: `https://music.youtube.com/playlist?list=RTM`
    2.  **"Your Likes"**: `https://music.youtube.com/playlist?list=LM`
*   **Parsing:** The JSON response (`sectionListRenderer` -> `musicShelfRenderer` -> `musicTwoRowItemRenderer`) is parsed to extract:
    *   `browseId` (Playlist ID, often starting with `VL` or `PL`).
    *   `title` and `subtitle` (e.g., "50 songs").
    *   `thumbnail` URL.

To fetch the **songs** within a playlist, `getPlaylist(id)` is used. It first attempts to use NewPipe's `PlaylistExtractor` (which handles public/unlisted playlists well) and falls back to internal API calls if NewPipe fails.

## Summary of Data Flow

1.  **Auth:** User logs in -> Cookies (SAPISID) saved -> Auth Header generated.
2.  **NewPipe:** `NewPipeDownloaderImpl` initializes with cookies -> NewPipe requests include cookies -> Access to restricted content.
3.  **Home Screen:**
    *   `checkYouTubeConnection` -> `fetchAccountInfo` (Avatar).
    *   `loadLibraryData` -> `getLikedMusic` (Recursive API fetch) & `getUserPlaylists`.
    *   `getRecommendations` -> `FEmusic_home` (Personalized Mixes).
