# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

**Koda** (repo dir `TheMusicApp`, package `com.ivor.ivormusic`) — an Android music & video player powered by YouTube Music, built entirely with Kotlin + Jetpack Compose using Material 3 Expressive. No official YouTube API keys: all data comes from NewPipe Extractor and direct InnerTube API calls.

## Commands

```powershell
.\gradlew assembleDebug          # build debug APK (ABI splits: arm64-v8a, armeabi-v7a + universal)
.\gradlew installDebug           # build + install on the connected device/emulator
.\gradlew compileDebugKotlin     # fast compile check without packaging
.\gradlew assembleRelease        # needs KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD env vars
```

- There is no meaningful test suite (only the template instrumented test). Verification = compile + run on emulator.
- Emulator: `emulator -avd Pixel_8_API36` (tools are on PATH), then `adb wait-for-device`. `Agents.md` documents an older `E:\sdk` setup — the current SDK lives at `E:\Android\Sdk`.
- Version bumps happen in `app/build.gradle.kts` (`versionCode` / `versionName`). Dependency versions live only in `gradle/libs.versions.toml`.

## Architecture

MVVM with StateFlow, **no DI framework** — ViewModels instantiate repositories directly, so multiple `YouTubeRepository` instances coexist (one per ViewModel). Repositories that persist state do so via SharedPreferences; cross-instance freshness matters (e.g. `VideoHistoryRepository.getHistory()` re-reads prefs because the player VM and home VM hold separate instances).

### Navigation & screens

`MainActivity` hosts a `NavHost` (`onboarding` → `home`, plus `settings`, `downloads`, `stats`, `update`). The `home` route contains its own tab system (`AnimatedContent` + `FloatingPillNavBar`), not nav routes. A global `videoMode` toggle (persisted in `ThemePreferences`) swaps the Home tab between music content and `VideoHomeContent`, and tab 2 between Library and video history. Music player (`ExpandablePlayer`) and video player (`VideoPlayerOverlay`) are overlays living above the NavHost, driven by their ViewModels' `isExpanded` state.

### Playback: two separate pipelines

- **Music**: `MusicService` (Media3 `MediaLibraryService`) + `PlayerViewModel` talking to it through a `MediaController`. Background playback, notifications, queue.
- **Video**: `VideoPlayerViewModel` owns its own `ExoPlayer` directly (no service). DASH or merged video+audio progressive streams, PiP support in `VideoPlayerOverlay`.

### YouTube data layer (`data/YouTubeRepository.kt`, ~3k lines — the heart of the app)

Two extraction mechanisms, used side by side:
1. **NewPipe Extractor** for search, stream URLs, playlists (initialized once with `NewPipeDownloaderImpl`, which injects session cookies into every request).
2. **Raw InnerTube JSON calls** via OkHttp for everything NewPipe can't do: personalized feeds, watch history, engagement (like/subscribe/comments), account info.

Critical InnerTube knowledge (verified July 2026):
- Client versions (`WEB_REMIX_VERSION`, `WEB_VERSION`) are pinned constants that **must be bumped periodically** — YouTube rejects clients older than a few months.
- Stream resolution uses the `ANDROID_VR` client (no PO token, unciphered URLs), falling back to `IOS`. Resolved googlevideo URLs are bound to the issuing client via the `?c=` param — playback requests must use the matching User-Agent (`uaForPlaybackUri()`), or YouTube 403s.
- The public Trending page is dead (`FEtrending` → HTTP 400 since mid-2025). Video feed order: personalized `FEwhat_to_watch` (logged in) → taste-based mix from local watch history (`getTasteBasedVideos`) → generic search cold-start.
- Comments use the modern `commentEntityPayload` format: entities arrive in `frameworkUpdates.entityBatchUpdate.mutations`, ordering comes from `commentThreadRenderer`s in `onResponseReceivedEndpoints`. The legacy `commentRenderer` no longer exists.
- Feed/related items are `lockupViewModel`s (modern) parsed by `parseLockupViewModel`; `videoRenderer` is legacy but still handled.
- Parsing is manual `org.json` traversal with recursive key-search helpers (`findObjectsByKey`, `findContinuationTokens`). No kotlinx-serialization for InnerTube responses.

**Before changing any InnerTube parser, probe the live API first** with a Python script (`py` launcher on this machine, not `python`) — response shapes drift. Example probes live in past sessions; the pattern is: POST to `https://www.youtube.com/youtubei/v1/<endpoint>?prettyPrint=false` with a WEB client context and inspect renderer types.

### Authentication

- Login = WebView (`YouTubeAuthDialog`) → cookies captured from the **`music.youtube.com` cookie jar specifically** (never the current page URL — mid-login pages are on `accounts.google.com` whose jar also has a SAPISID; saving it produces a broken "logged in but anonymous" state).
- `SessionManager` stores the cookie string in `EncryptedSharedPreferences`; `isLoggedIn()` = cookies exist.
- Authenticated InnerTube calls sign with `YouTubeAuthUtils.getAuthorizationHeader()` (SAPISIDHASH, **per-origin** — `https://music.youtube.com` vs `https://www.youtube.com` produce different hashes) plus `Cookie`, `Origin`, `X-Goog-AuthUser: 0` headers. `postWatchApi()` is the canonical helper for www.youtube.com endpoints.
- Write actions (like/dislike/subscribe) require login; `subscription/subscribe` returns 200 even signed out, so always guard with `isLoggedIn()`.

### UI conventions (from `.agent/rules`)

- **Material 3 Expressive components first** (`LoadingIndicator` with shapes, `FloatingToolbar`, `MaterialShapes`, spring-physics animations), standard M3 second. Do not hand-roll components or animation systems that M3/`androidx.compose.animation` already provide.
- Compiler-level opt-ins for `ExperimentalMaterial3ExpressiveApi` and `ExperimentalMaterial3Api` are global (`app/build.gradle.kts` freeCompilerArgs).
- Screens follow a shared look: `Surface` cards with `RoundedCornerShape(16.dp)`, `surfaceContainer` colors, `ExpressivePullToRefresh`, staggered `AnimatedVisibility` entrances. Match neighboring code.
- No emojis in code comments or docs.

### Reference docs in-repo

`docs/` contains deep dives (ARCHITECTURE, DEEP_DIVE_YOUTUBE, DEEP_DIVE_PLAYBACK, NEWPIPE_INTEGRATION_GUIDE) and `Material_3_expressive/` + `docs/*.md` hold M3 Expressive component guides. GitHub repo for releases/issues: `ivorisnoob/Koda`.
