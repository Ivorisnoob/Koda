# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

**Koda** (repo dir `TheMusicApp`, package `com.ivor.ivormusic`) — an Android music & video player powered by YouTube Music, built entirely with Kotlin + Jetpack Compose using Material 3 Expressive. No official YouTube API keys: all data comes from NewPipe Extractor and direct InnerTube API calls.

## How to work here (read before writing any code)

This is a shipped consumer app with real users, not a scratch project. The bar is "would a person using this on their phone every day be happy with it", not "does it compile and satisfy the literal request". Put in the effort up front — the user should not have to come back and ask you to think about the experience.

**Think the feature through before you touch a file.** For anything user-facing — a new screen, a control, a gesture, a setting, a fix that changes behaviour — work out first:

- **The actual use case.** Who taps this, when, and what were they doing right before? A control that is technically correct but three taps deep, or that sits where the thumb cannot reach, has failed.
- **The states.** Loading, empty, error, offline, signed out, single item, hundreds of items, very long titles, missing thumbnail, no artwork colors. Every one of these happens in this app constantly — YouTube fails, sessions expire, feeds come back empty. Handle them in the first pass, do not bolt them on later.
- **What it does to what already exists.** Does it fight the mini-player, the video overlay, PiP, the nav bar, an open sheet, a landscape rotation, back-gesture handling? Does it break the signed-out path? Overlays and the tab system in this app are easy to break from a distance.
- **Feel, not just function.** Motion, hit targets, haptics, whether state changes are legible. See the UI conventions section — springs for touch, M3 Expressive components, never a hardcoded color.

**Ask before you build, not after.** If the request leaves a real design decision open — where something lives, what happens in a case the user did not mention, two reasonable behaviours with different feels — ask. A short question with two or three concrete options is always cheaper than an implementation that gets thrown away. Ask up front, in one batch, not drip-fed mid-implementation. Do not ask about things you can settle yourself by reading the code or following existing convention.

**Say what you decided and what you did not do.** When you finish, briefly note the edge cases you handled, the assumptions you made, and anything you deliberately left out. If you shipped something you think is the wrong UX, say so plainly instead of quietly implementing it.

**Do not do the minimum.** A "quick fix" that leaves the surrounding flow broken is not a fix. If the real problem is one layer below the reported symptom, fix it there and say why.

## Commands

```powershell
.\gradlew assembleDebug          # build debug APK (ABI splits: arm64-v8a, armeabi-v7a + universal)
.\gradlew installDebug           # build + install on the connected device/emulator
.\gradlew compileDebugKotlin     # fast compile check without packaging
.\gradlew assembleRelease        # needs KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD env vars
```

- **Never compile or build (any `gradlew`/`gradle` invocation) unless the user explicitly asks for it.** Finish the code changes and stop — the user decides when to build.
- There is no meaningful test suite (only the template instrumented test). Verification = compile + run on emulator.
- Emulator: `emulator -avd Pixel_8_API36` (tools are on PATH), then `adb wait-for-device`. `Agents.md` documents an older `E:\sdk` setup — the current SDK lives at `E:\Android\Sdk`.
- Version bumps happen in `app/build.gradle.kts` (`versionCode` / `versionName`). Dependency versions live only in `gradle/libs.versions.toml`.
- **minSdk is 30 (Android 11), and `isCoreLibraryDesugaringEnabled` + `coreLibraryDesugaring(libs.desugar.jdk.libs.nio)` are load-bearing for it, not a nice-to-have.** NewPipe Extractor calls Java 10/11 library APIs the platform only shipped in API 33 — `URLEncoder.encode(String, Charset)`, `URLDecoder.decode(String, Charset)`, `Collectors.toUnmodifiableList()` — and D8's built-in backports do not cover those three, so removing desugaring makes every search throw `NoSuchMethodError` on API 30-32 while compiling fine. It must be the `_nio` flavour of the artifact. Anything gated above 30 (dynamic color at API 31, Live Updates at 36) needs a `Build.VERSION.SDK_INT` guard plus a working fallback.

## Architecture

MVVM with StateFlow, **no DI framework** — ViewModels instantiate repositories directly, so multiple `YouTubeRepository` instances coexist (one per ViewModel). Repositories that persist state do so via SharedPreferences; cross-instance freshness matters (e.g. `VideoHistoryRepository.getHistory()` re-reads prefs because the player VM and home VM hold separate instances).

### Navigation & screens

`MainActivity` hosts a `NavHost` (`onboarding` → `home`, plus `settings`, `downloads`, `stats`, `update`). The `home` route contains its own tab system (`AnimatedContent` + `FloatingPillNavBar`), not nav routes. A global `videoMode` toggle (persisted in `ThemePreferences`) swaps the Home tab between music content and `VideoHomeContent`, and tab 2 between Library and video history. Music player (`ExpandablePlayer`) and video player (`VideoPlayerOverlay`) are overlays living above the NavHost, driven by their ViewModels' `isExpanded` state.

### Playback: two separate pipelines

- **Music**: `MusicService` (Media3 `MediaLibraryService`) + `PlayerViewModel` talking to it through a `MediaController`. Background playback, notifications, queue.
- **Video**: `VideoPlayerViewModel` owns its own `ExoPlayer` directly (no service). DASH or merged video+audio progressive streams, PiP support in `VideoPlayerOverlay`.

Video playback loads in two phases (`VideoPlayerViewModel.playVideo`): Phase 1 calls `getVideoStreamQualities()` (stream URLs only, 15s timeout) and starts playback; Phase 2 runs `getWatchNextData()` in parallel — a single `/next` call parsed into engagement + enriched metadata + related videos (do NOT add per-video NewPipe `fetchPage()` calls or extra `/next` calls here; keeping tap-time network minimal is deliberate, YouTube itself does only `/player` + `/next`). The video ExoPlayer uses a tuned `DefaultLoadControl` (1.5s start buffer) like `MusicService` does. `visitorData` is cached at the `YouTubeRepository` companion level (shared across the per-ViewModel instances), persisted with a mint timestamp so restarts inside the 6h TTL do no network, minted via the cheap `youtubei/v1/visitor_id` endpoint (bootstrap-HTML scrape only as fallback), and prefetched at both `VideoPlayerViewModel` init and `MusicService.onCreate`.

**All googlevideo media fetches must go through bounded ranged requests** — googlevideo paces open-ended progressive requests to roughly the media bitrate (measured 32 KB/s vs 5–22 MB/s bounded, July 2026), which reads as "slow loading on a good network". Playback (music, video, Shorts) uses `data/ChunkedStreamDataSource` (10 MB ranged chunks + per-URL UA, wired via `CacheManager.createPerClientHttpFactory()` and the two video VMs' `streamDataSourceFactory`); `DownloadRepository.downloadStream` does its own ranged chunk loop. Never replace these with a plain `DefaultHttpDataSource`/unbounded OkHttp GET for stream URLs. `MusicService.prefetchUpcomingSongs` additionally pre-caches the first 512 KB of the next 3 queue songs (`warmStreamCache`) so skips start from disk. Quality model is `VideoQuality(resolution, url, format, isDASH, audioUrl)` where `resolution` is a YouTube quality label ("1080p60", "720p", …). `parseQualitiesFromStreamingData` returns the list sorted highest-first with 60fps variants before 30fps, and never contains DASH entries — the DASH "Auto (Best)" entry only comes from Phase 2's `getVideoDetails`. The starting quality comes from the per-network video quality settings (`ThemePreferences.getDefaultVideoQuality()`, fresh pref read that picks the Wi-Fi or mobile-data value via `isActiveNetworkMetered`; values are `VIDEO_QUALITY_OPTIONS`: "auto" or a label like "1080p") via `VideoPlayerViewModel.pickDefaultQuality` — best label at or below the target height, else lowest available. Music resolution (`pickAudioStreamUrl` + the NewPipe fallback) honors the per-network music quality the same way (`ThemePreferences.currentMusicQuality`: high/normal/low). HDR formats (label carries " HDR", colorInfo declares PQ/HLG) are dropped by `parseQualitiesFromStreamingData` unless the off-by-default "Prefer HDR Videos" setting is on, in which case they list alongside SDR and sort ahead of it at equal height. Video downloads pick the best MP4 adaptive pair at or below the quality chosen in `VideoDownloadSheet` (opened from the long-press save sheet's Download row; the request carries the label so retries keep it), defaulting to the sheet-persisted `download_video_quality` pref whose "auto" means best available.

Video player extras (in `VideoPlayerContent` / `VideoPlayerScreen` / `VideoPlayerViewModel`): **Captions** are sideloaded as a `SingleSampleMediaSource` (`text/vtt`) merged into the current source. Media3 1.5 parses subtitles at extraction and disables the render-time text path by default, so the ExoPlayer is built with a `DefaultRenderersFactory` subclass whose `buildTextRenderers` calls `TextRenderer.experimentalSetLegacyDecodingEnabled(true)` (the factory-level toggle only exists from Media3 1.6). A player-error listener drops the caption and reloads without it on failure, so a bad/expired subtitle URL never leaves the video stuck buffering (captions are best-effort). CC toggle + language sheet; PlayerView's built-in SubtitleView renders the cues. **Chapters** render as seek-bar ticks + a current-chapter chip + a chapters sheet, all fed by `VideoPlayerViewModel.chapters`. **Hold-to-2x**: a long-press in `PlayerGestureSurface` temporarily sets 2x and restores the prior speed on release. A **Share** action fires `ACTION_SEND` with `youtube.com/watch?v=<id>` (playlists share `youtube.com/playlist?list=` or `music.youtube.com/playlist?list=`, `/browse/` for albums; hidden for local playlists and private WL/LL feeds).

### Live streams (horizontal and vertical)

A live broadcast is not a separate content type anywhere in this app — it is a normal video whose `/player` response happens to carry an HLS manifest. Everything below follows from that.

**Detection and playback.** `streamingData.hlsManifestUrl` is present on live responses and absent on VODs (verified against ANDROID_VR, August 2026), so it *is* the live signal — `parseQualitiesFromStreamingData` returns early on it and stamps `VideoQuality.isLive`, and `VideoPlayerViewModel` reads `_isLive` straight off the quality list rather than paying for a separate call. A live broadcast is the one case where the progressive URLs in `adaptiveFormats` are unusable: they are segment endpoints (`live=1`, `noclen=1`), so an unbounded GET returns one ~2s segment and `ChunkedStreamDataSource`'s ranged GET blocks until it times out — the "live video opens but never plays" failure. The HLS variant playlist is the only sane source. **Never route a live stream through the progressive path.**

**The quality ladder is labels only.** Every rendition lives inside that one manifest, so all entries carry the same URL and picking one calls `applyLiveQualityCap` — `setMaxVideoSize` plus `setForceHighestSupportedBitrate` — instead of swapping the media source. Quality changes are free (no re-prepare, no rebuffer), which matters more on live than anywhere else. The cap is cleared on the next `playVideo`, because track selection outlives media items and a cap picked on a stream would silently limit the next video.

**Live chat** (`getLiveChatSession` → `pollLiveChat`, rendered by `ui/video/LiveChatPanel.kt`). The continuation arrives free with the Phase 2 `/next` response (`parseLiveChatContinuation`); the poll only starts once chat is actually on screen. The server's requested interval is honored but clamped to `LIVE_CHAT_MIN_WINDOW_MS`/`MAX` (a misparse must not become a hot loop), the backlog is capped at `MAX_LIVE_CHAT_MESSAGES` (250, matching YouTube's own `maxItemsToDisplay`), and three consecutive failed polls give up. Sending echoes the accepted message back so it appears without waiting a poll, and rejections (slow mode, word filter, ban) surface through `onFailure` so the composer restores the text. Super Chats carry their own colors from YouTube — they are the documented exception to the never-hardcode-a-color rule. A finished broadcast exposes its *replay* chat under the same key; nothing consumes that today.

**`LiveChatPanel` has three dresses, and the `compact` flag only selects between the first two:**
- default — opaque `surfaceContainer` sheet sliding up over the info area, portrait;
- `compact = true` — translucent column docked right of the video, landscape;
- `LiveChatOverlay` — a *separate* composable in the same file: a read-only ticker on a gradient for the full-bleed vertical player. It reuses the message rows but passes `authorColorOverride`, because the role tints (tertiary/primary/secondary) are not guaranteed to carry contrast against video. Sending, scroll-back and the jump-to-latest pill deliberately stay with the full panel.

**Vertical live.** A vertical live stream is an ordinary broadcast encoded 9:16 — there is no "Shorts live" entity. Probed August 2026: `/shorts/<id>` on one resolves straight back to `/watch?v=`, its `/next` carries no `reelPlayerOverlayRenderer`, and five pages of `reel/reel_watch_sequence` returned zero live items. **Orientation is the only signal there is**, so `VideoQuality.isPortrait` is derived at parse time from the largest dimensioned format (both the InnerTube and NewPipe paths) — reading it from ExoPlayer's `onVideoSizeChanged` instead is too late and makes the layout visibly snap after the first frame. `onVideoSizeChanged` remains the backstop.

`isLive && isPortraitVideo` selects `VerticalLivePlayerContent` (full-bleed, chat ticker over the video, controls on a timer but top chrome always up). It opens immersive automatically; `Icons.Rounded.CloseFullscreen` drops to the standard watch page and `Icons.Rounded.StayCurrentPortrait` on that page returns — the choice resets per video. Fill-vs-fit is conditional: zoom-to-fill matches the Shorts overlay, but past `MAX_ACCEPTABLE_CROP` (25%) it letterboxes, because "vertical" also covers 4:5 and 1:1 streams that would lose their top and bottom.

**Landscape is the good case for a 9:16 stream** — the space beside the video is chat-shaped. Rotating a vertical live stream auto-opens the docked column and `FullscreenPlayerContent`'s `videoEndPadding` slides the video clear of it (portrait sources only; shrinking a 16:9 video to dodge an overlay sitting on letterbox bars would lose more than it gains).

**Comments are hidden entirely on live videos** — the entry row, the timed-comments button in both chromes, the overlay, and the fetch that feeds it. Live chat is the conversation; a running broadcast's comment section is usually empty or disabled, and offering both sent people to the dead one. An open comments sheet closes when a live stream starts, since the sheet outlives the video it was opened on.

**A live item arriving in the Shorts feed is handed off, not approximated** — `ShortsPlayerViewModel` emits `liveHandoff` and closes itself, and `MainActivity` reopens the stream in the video player. Emit *before* `close()`: it cancels the very job the emit runs in.

### Settings plumbing (adding a new setting)

All app settings live in `data/ThemePreferences.kt` (SharedPreferences `ivor_music_theme_prefs`, one `MutableStateFlow` + KEY constant + private getter + public setter per setting). A new setting must be threaded through **four files**:

1. `ThemePreferences` — StateFlow, KEY constant, getter/setter.
2. `ui/theme/ThemeViewModel.kt` — exposes the flow + a `setX()` delegate.
3. `MainActivity.kt` — collect the flow in `setContent`, add a param pair to the `MusicApp` composable, pass through to the `SettingsScreen` call inside the `settings` nav route (the params are threaded twice: setContent → MusicApp → SettingsScreen).
4. `ui/settings/SettingsScreen.kt` — new params + a UI item.

Theming beyond light/dark: a **color palette** setting (`color_palette` pref, default `dynamic`) chooses between wallpaper-based dynamic color and the predefined palettes in `ui/theme/ColorPalettes.kt`, selected through `ColorPaletteScreen` and applied in `ui/theme/Theme.kt`.

Because there is no DI, every consumer news up its own `ThemePreferences` — StateFlow updates do NOT cross instances. ViewModels that need a setting at decision time must do a fresh pref read (pattern: `isSaveVideoHistoryEnabled()` reads straight from prefs; `VideoPlayerViewModel` already holds a `themePreferences` instance).

`SettingsScreen` layout conventions: sections are `SettingsSection(title)` wrapping `ExpressiveSettingsCard`, rows separated by `SettingsDivider()`. Row composables to copy: `ExpressiveSettingsItem` (icon + title/subtitle clickable row, optional chevron), the various `Expressive*ToggleItem` (switch rows with 48dp icon box, `RoundedCornerShape(14.dp)`, press-scale spring animation), and segmented-button groups (`ExpressiveVideoModeToggleItem`, `ExpressivePlayerStyleSelectItem`). Dialogs (`ExpressiveAboutDialog`, `FolderExclusionDialog`): `AlertDialog` with `containerColor = surfaceContainerHigh`, `shape = RoundedCornerShape(32.dp)`, 64dp rounded icon box, spring `scaleIn` entry via `AnimatedVisibility`. Video-related settings belong in the "Content Mode" section. Material icons extended is available (`Icons.Rounded.*` like `FolderOff`, `SwipeRight` are already used).

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
- Video **chapters** live in the watch-next (`/next`) decorated player bar: `multiMarkersPlayerBarRenderer.markersMap` keyed `DESCRIPTION_CHAPTERS` (creator) or `AUTO_CHAPTERS`, each a list of `chapterRenderer { title, timeRangeStartMillis, thumbnail }`. `parseChaptersFromWatchNext` collects `chapterRenderer` by key (document order = chronological) into `WatchNextData.chapters`. Verified July 2026.
- Video **captions** come from the **ANDROID_VR** `/player` call (IOS fallback) — the same client chain as streams, never WEB: `captions.playerCaptionsTracklistRenderer.captionTracks[]` (each `baseUrl`, `languageCode`, `name`, and `kind == "asr"` / `vssId` starting `a.` for auto-generated). A WEB `/player` without account cookies answers `UNPLAYABLE`/"Video unavailable" with **no captions block at all**, so sourcing captions from WEB gives every signed-out user an empty CC menu — that was the long-standing captions bug. The native clients return the full tracklist signed-out. `parseCaptionTracks` runs on every `/player` response and `runPlayerClientChain` caches the result per video id (30 min TTL, LRU 16), so the lazy CC tap normally costs no network at all. Verified July 2026.
- Timedtext `baseUrl`s must have their format **replaced, not appended**: ANDROID_VR hands back URLs already carrying `&fmt=srv3`, and the endpoint honors the *first* `fmt`, so `"$baseUrl&fmt=vtt"` silently returns srv3 XML under a `text/vtt` MIME type and the subtitle load fails. `CaptionTrack.vttUrl` strips `&fmt=`/`&tlang=` then appends `&fmt=vtt` — the same normalization NewPipe's `YoutubeStreamExtractor` applies. Do not "simplify" it back to a plain append.
- Parsing is manual `org.json` traversal with recursive key-search helpers (`findObjectsByKey`, `findContinuationTokens`). No kotlinx-serialization for InnerTube responses.

### Working on InnerTube: the probe-first workflow (follow this recipe exactly)

Never write or modify an InnerTube parser from memory or training data — response shapes drift and renderers get renamed wholesale (`commentRenderer` → `commentEntityPayload`, `videoRenderer` → `lockupViewModel`). The workflow that reliably works:

1. **Probe the live endpoint before touching Kotlin.** A gitignored `.probe/` helper already exists locally for exactly this: `py .probe/probe.py <endpoint> '<json body>'` (add `--music` for music.youtube.com); it reads the pinned client version live out of `YouTubeRepository.kt` and signs with the emulator session cookie, writing the response to `.probe/last_response.json`. `.probe/` (cookies included) is git-ignored — never commit it. Otherwise write a throwaway Python script in the scratchpad (`py` launcher on this machine, not `python`). POST JSON to `https://www.youtube.com/youtubei/v1/<endpoint>?prettyPrint=false` with body `{"context": {"client": {"clientName": "WEB", "clientVersion": <WEB_VERSION>, "hl": "en", "gl": "US"}}, ...}`. Read the pinned `WEB_VERSION` out of `YouTubeRepository.kt` with a regex so the probe matches the app's client. Set `sys.stdout.reconfigure(encoding="utf-8", errors="replace")` or emoji in responses crash the Windows console.

2. **For logged-in endpoints, steal the session from the emulator's WebView jar** (EncryptedSharedPreferences can't be read off-device, but WebView cookies are plaintext SQLite):
   `adb exec-out run-as com.ivor.ivormusic cat app_webview/Default/Cookies > Cookies.db`
   Read `name,value` rows for `%youtube.com%` hosts, send them all as a `Cookie:` header, and sign with `Authorization: SAPISIDHASH {ts}_{sha1("{ts} {SAPISID} https://www.youtube.com")}` plus `Origin`, `X-Origin`, `X-Goog-AuthUser: 0`. The hash is **per-origin** — `music.youtube.com` and `www.youtube.com` need different hashes.

3. **Inspect shapes with a recursive key search, not by eyeballing.** Save the full response to a JSON file, then use a `find_all(node, key)` helper (dict/list walker) to count candidate renderer keys (`lockupViewModel`, `playlistVideoRenderer`, `richShelfRenderer`, ...). Print one exemplar object's key list and the exact paths to title/thumbnail/id fields. Only then write the parser.

4. **Write parsers the way the file already does:** manual `org.json` traversal with `optJSONObject`/`optString` chains, `findObjectsByKey` for deep renderer collection, `getRunText()` for text (handles both `simpleText` and `runs`), `?.takeIf { it.isNotBlank() }` + fallback for every field, try/catch returning an empty/null result with a `Log.e`. Note "verified <month year>" in the KDoc so staleness is visible.

5. **Client choice cheat sheet:** WEB context (`webContext()`, `postWatchApi()`) for browse//next/engagement on www.youtube.com; WEB_REMIX (`fetchInternalApi()`) for music.youtube.com; ANDROID_VR (fallback IOS) **only** for `/player` stream resolution — it returns unciphered URLs without a PO token. Never fetch streams with WEB. Playback requests must use the UA matching the URL's issuing client (`uaForPlaybackUri()`), or googlevideo answers 403.

6. **When an existing call starts failing:** log HTTP code + first ~200 chars of body and `playabilityStatus.reason`. HTTP 400 on browse usually means the pinned client version constants are too old — bump them. Empty `streamingData` with status OK usually means stale/missing `visitorData`. Don't loop retries; re-probe and compare shapes.

7. **Be frugal with calls per user action.** One `/next` response contains engagement, metadata, related videos and the comments token at once — parse many features from one response (`getWatchNextData` pattern) instead of adding a request per feature.

8. **Clean up write-probe side effects** (e.g. delete test comments) and never commit cookie dumps or response JSONs — keep them in the scratchpad.

Known browse IDs (verified July 2026): `FElibrary` (library shelves), `FEplaylist_aggregation` (all user playlists as `lockupViewModel`s with `contentType: LOCKUP_CONTENT_TYPE_PLAYLIST`), `VL<playlistId>` (playlist contents as `playlistVideoRenderer`s; `VLWL` = Watch Later, `VLLL` = Liked videos), `FEhistory` (watch history), `FEchannels` (subscriptions). The public `FEtrending` is dead (HTTP 400).

### Authentication

- Login = WebView (`YouTubeAuthDialog`) → cookies captured from the **`music.youtube.com` cookie jar specifically** (never the current page URL — mid-login pages are on `accounts.google.com` whose jar also has a SAPISID; saving it produces a broken "logged in but anonymous" state).
- `SessionManager` stores the cookie string in `EncryptedSharedPreferences`; `isLoggedIn()` = cookies exist.
- Authenticated InnerTube calls sign with `YouTubeAuthUtils.getAuthorizationHeader()` (SAPISIDHASH, **per-origin** — `https://music.youtube.com` vs `https://www.youtube.com` produce different hashes) plus `Cookie`, `Origin`, `X-Goog-AuthUser: 0` headers. `postWatchApi()` is the canonical helper for www.youtube.com endpoints.
- Write actions (like/dislike/subscribe) require login; `subscription/subscribe` returns 200 even signed out, so always guard with `isLoggedIn()`.

### UI conventions (from `.agent/rules`)

- **Material 3 Expressive components first** (`LoadingIndicator` with shapes, `FloatingToolbar`, `MaterialShapes`, spring-physics animations), standard M3 second. Do not hand-roll components or animation systems that M3/`androidx.compose.animation` already provide.
- Compiler-level opt-ins for `ExperimentalMaterial3ExpressiveApi` and `ExperimentalMaterial3Api` are global (`app/build.gradle.kts` freeCompilerArgs).
- Screens follow a shared look: `Surface` cards with `RoundedCornerShape(16.dp)`, `surfaceContainer` colors, `ExpressivePullToRefresh`, staggered `AnimatedVisibility` entrances. Match neighboring code.
- Springs for anything touch-driven (`spring()` is used ~97 times; `DampingRatioMediumBouncy` is the house default). Reserve `tween()` for crossfades and time-tracking progress.
- Never hardcode a color — everything routes through `ColorScheme`, or palettes/AMOLED/dynamic color silently break.
- No emojis in code comments or docs.
- **The design language is not up for replacement.** M3 Expressive is load-bearing here, not a theme layer: 39 files use Expressive-only APIs and the whole app renders inside one `MaterialExpressiveTheme`. If a user or issue asks for "a different UI", an alternate/classic/flat design language, or a non-Material look, do not start building it — that is a rewrite of ~37.5k lines of UI, and the project has an explicit stated policy against it in `DESIGN.md`. Specific complaints (a radius, a nav-bar dimension, one animation) are fair game and should be treated as normal UI work.

### Player styles (`ui/player/`, 8 styles, ~10.3k lines)

**Naming trap: the `POSTER` enum key is the "Canvas" player.** The old kinetic-type Poster style was rewritten in place as full-bleed album art; the enum constant and the file name (`PosterPlayerContent.kt`, `PosterPlayerSheetContent`) kept the old key for SharedPreferences compatibility, while every user-facing string says "Canvas". Searching for "Canvas" will not find the enum, and searching for "Poster" lands on a file that no longer does anything poster-like. Do not "fix" this mismatch by renaming the enum constant — the stored pref value is the string `"POSTER"` and renaming it resets every existing user's player style.

Adding or renaming a style means touching **four** places (an omission compiles but silently misbehaves):

1. `data/ThemePreferences.kt` — the `PlayerStyle` enum constant (persisted by `name`, so treat existing constants as frozen).
2. `ui/player/<Name>PlayerContent.kt` — a `<Name>PlayerSheetContent(viewModel, ...)` composable, the convention every style follows.
3. `ui/player/ExpandablePlayer.kt` — a branch in the `when (playerStyle)` around line 305 that calls it. This `when` is the only dispatch point.
4. Both pickers, which are separate hardcoded lists that must stay in sync: `rememberPlayerStyleWheelEntries()` in `ui/player/PlayerStyleWheel.kt` (label + `Icons.Rounded.*` + a `MaterialShapes` shape) and `playerStyleOptions` in `ui/settings/SettingsScreen.kt` (label + subtitle + icon).

Onboarding (`ui/onboarding/OnboardingScreen.kt`) deliberately offers only `CLASSIC` and `GESTURE` — it is a curated first-run pair, not a list to keep in sync with the other four.

### Reference docs in-repo

`DESIGN.md` (repo root) is the public design-system doc: the shape/motion/color systems, how `IvorMusicTheme` resolves a `ColorScheme` (dynamic vs the 27 fixed palettes vs AMOLED vs artwork colors), the player-style table, and the stated policy against an alternate design language. It is written for users and contributors, so it carries counts that go stale — if you change the palette list, player styles, or the animation/shape mix, re-derive its numbers rather than trusting them.

`Material_3_expressive/` holds the M3 Expressive component guides (Buttons, Carousel, Lists, Menus, Motion, Overview, ProgressIndicators, Shapes, Theming, Toolbars). `.agent/rules/` and `.agent/skills/` (Kotlin skill, code-style guide) carry the coding conventions. There is no `docs/` directory in the repo today — the deep-dive docs (ARCHITECTURE, DEEP_DIVE_YOUTUBE, DEEP_DIVE_PLAYBACK, NEWPIPE_INTEGRATION_GUIDE) referenced in older notes do not exist; the source files, `DESIGN.md`, and this file are the reference. GitHub repo for releases/issues: `ivorisnoob/Koda`.
