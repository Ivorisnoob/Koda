# NewPipe Extractor & YouTube Music Authentication Guide

This document outlines the standard, recommended implementation patterns for integrating [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor) into an Android application, specifically focusing on handling authentication for YouTube Music data (Liked Songs, Private Playlists).

**Note:** This guide describes best practices based on the official NewPipe Extractor documentation and common community standards for interacting with YouTube's Internal API. It does not reflect the current state of the application's codebase, which may contain bugs or non-standard implementations.

## 1. NewPipe Extractor Integration

The NewPipe Extractor library is designed to be modular. The core component is the `Downloader` interface, which you must implement to handle HTTP requests.

### Recommended dependency
```gradle
implementation 'com.github.teamnewpipe:NewPipeExtractor:latest-release'
```

### Implementing the Downloader
A robust implementation should wrap a networking client like `OkHttpClient` and handle:
1.  **Cookie Injection:** Essential for authenticated requests.
2.  **User-Agent Consistency:** Critical to avoid "Page needs to be reloaded" errors.
3.  **ReCaptcha Handling:** Detecting `429` responses.

**Standard Pattern:**
```kotlin
class NewPipeDownloader(
    private val client: OkHttpClient,
    private val cookieProvider: () -> String?
) : Downloader() {

    override fun execute(request: Request): Response {
        val builder = okhttp3.Request.Builder()
            .url(request.url())
            .method(request.httpMethod(), request.dataToSend()?.toRequestBody())

        // 1. Inject Cookies
        cookieProvider()?.let { cookies ->
            builder.addHeader("Cookie", cookies)
        }

        // 2. Consistent User-Agent (Matches the one used for playback)
        builder.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) ...")

        // 3. Execute & Handle Errors
        val response = client.newCall(builder.build()).execute()
        if (response.code == 429) throw ReCaptchaException("Rate limited", request.url())

        return Response(response.code, response.message, response.headers.toMultimap(), response.body?.string(), request.url())
    }
}
```

### Initialization
Initialize NewPipe once in your Application or Repository singleton.
```kotlin
NewPipe.init(NewPipeDownloader(okHttpClient) { sessionManager.getCookies() })
```

## 2. YouTube Music Authentication

YouTube Music does not offer a public write API. Third-party clients typically authenticate by simulating a web browser session and capturing the cookies.

### Cookie Extraction
The most reliable method is using a `WebView` to log the user in to `https://accounts.google.com/` and intercepting the cookies.

**Key Cookies to Capture:**
*   `SAPISID`: The primary cookie used for generating authorization headers.
*   `__Secure-3PAPISID`: Often required for secure requests.
*   `HSID`, `SSID`, `APISID`: Session identifiers.

**Implementation Tip:** Use `CookieManager.getInstance().getCookie(url)` on the success URL (`https://music.youtube.com`) to get the full cookie string.

### The SAPISIDHASH Authorization Header
For authenticated requests to the internal API (e.g., fetching liked songs), simply sending cookies is often insufficient. You must also include an `Authorization` header derived from the `SAPISID` cookie.

**Algorithm:**
1.  **Timestamp:** Current Unix timestamp in seconds.
2.  **SAPISID:** Value of the cookie.
3.  **Origin:** The request origin, typically `https://music.youtube.com`.

**Hash Generation:**
```
Input = {timestamp} + " " + {SAPISID} + " " + {Origin}
Hash  = SHA1(Input)
Header Value = "SAPISIDHASH " + {timestamp} + "_" + {Hash}
```

**Standard Kotlin Implementation:**
```kotlin
fun getAuthorizationHeader(sapisid: String, origin: String): String {
    val timestamp = System.currentTimeMillis() / 1000
    val input = "$timestamp $sapisid $origin"
    val hash = MessageDigest.getInstance("SHA-1").digest(input.toByteArray())
        .joinToString("") { "%02x".format(it) }
    return "SAPISIDHASH ${timestamp}_${hash}"
}
```

## 3. Data Fetching Strategies

### Profile Picture (PFP)
The most stable way to fetch the user's avatar is via the `account/account_menu` endpoint.

*   **Endpoint:** `https://music.youtube.com/youtubei/v1/account/account_menu`
*   **Method:** POST
*   **Body:** `{"context": { "client": { "clientName": "WEB_REMIX", "clientVersion": "1.2023..." } } }`
*   **Parsing:** Look for `activeAccountHeaderRenderer` -> `avatar` -> `thumbnails`.
*   **High-Res Trick:** The URL often ends in `=s88-c-k-c0x00ffffff-no-rj`. Replace `s88` with `s512` or `s1024` to get the high-resolution image.

### Liked Songs
Fetching the "Liked Music" playlist involves the internal browse endpoint.

*   **Endpoint:** `FEmusic_liked_videos`
*   **API:** `https://music.youtube.com/youtubei/v1/browse`
*   **Payload:**
    ```json
    {
      "browseId": "FEmusic_liked_videos",
      "context": { ... }
    }
    ```
*   **Pagination:** The response contains a `continuationToken`. You must recursively call the `browse` endpoint with this token (wrapped in a `continuation` field) to fetch all songs.

### Private Playlists
To access private playlists, ensure your `NewPipe` requests include the `Cookie` header. NewPipe's `PlaylistExtractor` automatically handles the parsing if the cookies are valid.

**Alternative (Internal API):**
*   **Endpoint:** `FEmusic_liked_playlists` to get the library.
*   **Parsing:** Look for `musicTwoRowItemRenderer` elements in the response.

## 4. Common Pitfalls & Solutions

1.  **"Page needs to be reloaded" / 400 Bad Request:**
    *   **Cause:** Mismatched `User-Agent`. The UA used for the API request must match the one used for playback (stream extraction).
    *   **Fix:** Define a single constant `BROWSER_USER_AGENT` and use it everywhere.

2.  **429 Too Many Requests:**
    *   **Cause:** YouTube detects bot-like traffic.
    *   **Fix:** Implement exponential backoff. Ensure you aren't making parallel requests for stream URLs.

3.  **Cookies Expiring:**
    *   **Cause:** Google sessions eventually expire.
    *   **Fix:** Monitor for `401` or redirect responses. Prompt the user to re-login via the `WebView` when this happens.
