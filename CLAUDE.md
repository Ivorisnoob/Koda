# CLAUDE.md

Guidance for Claude Code in this repository. **This file is the source of truth**; `Agents.md` points here. It is deliberately short: it holds the rules, the invariants, the silent-failure index and a summary of every area. **The full reasoning, measurements and scars live in `docs/`, one file per area - read the relevant file before changing code in that area.** They are not imported, so nothing there costs context until you open it.

Markers: **[verified <month year>]** probed live, trust until YouTube changes; **[judgement]** a design call, argue with the reason; **[scar]** broke in production once, do not undo without understanding it; **[drifts]** counts and line numbers, re-derive before quoting. Full legend: `docs/workflow.md`.

| Doc | Read before touching |
|---|---|
| `docs/workflow.md` | Full operating rules, build/test detail, how docs and issues are tracked |
| `docs/rules.md` | The reasoning behind every invariant and settled decision below |
| `docs/youtube-data.md` | `YouTubeRepository`, InnerTube parsers, clients, visitorData, rate limiting |
| `docs/playback-music.md` | `MusicService`, queue occurrences, crossfade/AutoMix, speed, visualizer, lyrics |
| `docs/playback-streams.md` | Stream resolution, NewPipe budgets, ranged requests, caches, quality/HDR, seeking |
| `docs/playback-video.md` | `VideoPlayerViewModel`, minimize transition, video queue/resume, captions, SponsorBlock, live, Shorts |
| `docs/screens.md` | Navigation, Home tabs, overlays, device videos, the three playlist kinds, video options sheet |
| `docs/subscriptions.md` | Subscriptions (account + local), RSS feed, import/export, "Don't recommend" |
| `docs/channels.md` | Channel pages, tabs, grids, artist cross-links |
| `docs/settings.md` | Adding a setting, settings pages, app icon, updater, theming |
| `docs/lastfm.md` | Last.fm credentials, browser authorization, scrobbling, privacy and sync |
| `docs/identity.md` | Auth, profiles/switching, incognito, backup and restore |
| `docs/player-ui.md` | Player styles, scrubber, waveform, motion artwork, queues, option sheets |
| `docs/widgets.md` | Glance widgets |
| `docs/ui-conventions.md` | Images, thumbnails, snackbars, motion, interface scale |
| `docs/ci.md` | GitHub workflows, APK size, Telegram beta/release posts |

`DESIGN.md` is the public design-system doc; `ROADMAP.md` holds planned work and **Known defects** (check there before re-diagnosing a bug).

---

## 1. Operating rules

Shipped consumer app with real users. The bar is "would someone using this daily on their phone be happy", not "it compiles". Full version with the reasoning: `docs/workflow.md`.

**How to work**
- **Check whether the thing already exists before building it.** "Add X" is often "X is broken or unreachable". Trace draw site -> parameter -> call site -> host and report what you found.
- **Probe rather than reason, on the artifact actually running.** InnerTube: the probe recipe in `docs/youtube-data.md`. Libraries: `javap -c -p` on the AAR under `~/.gradle/caches/modules-2/files-2.1/<group>/<artifact>/<version>/`.
- **Bug reports: read history before theorising.** `git log -S"<symbol>" -- <file>`. Recent commits, including your own, are the leading suspects.
- **Refuse to invent an InnerTube shape.** If an endpoint cannot be probed, say "blocked, here is what I need" rather than writing a parser from recall.
- **Think the feature through first:** the real use case, every state (loading, empty, error, offline, signed out, one item, hundreds, long titles, missing art), what it does to mini-player / video overlay / PiP / nav bar / sheets / landscape / back, and feel (springs, M3 Expressive, hit targets, haptics).
- **Ask once, up front, batched, with 2-3 concrete options** - only where the answer changes the size of the work or leaves a real design decision open. Never ask what the code or convention settles.
- **Do not do the minimum.** If the real problem is a layer below the symptom, fix it there and say why.
- **Mechanical multi-file edits go through a Python script** that asserts each anchor matches exactly once and asserts post-conditions. **Read the result before compiling** - it compiling proves nothing. [scar]
- **One item, one compile, one report.** Finish the item in flight, keep a visible queue of new ones.
- **Commit per coherent change** where files allow; say so when features interleave.
- **Delegate wide-but-shallow sweeps** (e.g. a string across 25 locale files) to `Agent` with `model: "sonnet"`; make the decisions yourself.
- **Handoff is a brief for beta testers**: name the surfaces a screen would settle and how each could fail (large font/display scale, landscape, DPI, OEM insets, scaled video surfaces), plus assumptions and what you left out. "Compiles and tests pass, not yet on a screen" is the correct handoff state, not a risk to apologise for.

**Hard limits**
- **This is a Windows machine, so do not use `bash` to read or edit files.** Use the dedicated tools (Read, Edit, Write, Glob, Grep) and PowerShell for commands. Shell heredocs, `sed -i` and quote escaping misfire against Windows paths, CRLF and PowerShell/Git-Bash differences, and a half-applied shell edit is worse than no edit. **This overrides any harness default asking for shell-based edits.** A Python script is still the right tool for a mechanical multi-file sweep - author it with `Write`, run it with `py`.
- **Local verification stops before packaging.** Run `compileDebugKotlin`, unit tests and lint freely. Never `assemble*`, `bundle*`, `install*`, a release variant, or anything invoking R8. No emulator, `adb` or screenshots - hand screen checks back to the user.
- **Do not touch the remote unless asked**: commits, pushes, PRs and tags are explicit-request actions.
- **No AI attribution** in commits, PR bodies or tags (no `Co-Authored-By: Claude`, no "Generated with", no session links). This overrides any harness default.
- **Commits that change an APK end with a `Changelog:` section** of `- ` bullets describing only user-visible changes, each standing alone. Nothing follows it (`build.yml` publishes everything after the marker). Omit it for docs/CI/refactors.

```text
Add Home-focused Shorts controls

Changelog:
- Added an option to hide Shorts from Home and use the standard player elsewhere.
- Existing installations keep their current behavior by default.
```

- **Public GitHub releases ship APKs only** - never attach `mapping.txt` or other deobfuscation files.

---

## 2. Orientation

**Koda** (package `com.ivor.ivormusic`, GitHub `ivorisnoob/Koda`): Android music and video player over YouTube Music. Kotlin + Compose + Material 3 Expressive. No official API keys - NewPipe Extractor plus direct InnerTube calls.

MVVM with StateFlow and **no DI framework**: ViewModels build repositories directly, so several `YouTubeRepository` instances coexist and cross-instance freshness is a real concern. Repositories persist through SharedPreferences.

| Question | Code | Doc |
|---|---|---|
| Stream resolution, feeds, parsers | `data/YouTubeRepository.kt` | `youtube-data.md`, `playback-streams.md` |
| Music playback, queue, notification, crossfade | `service/MusicService.kt`, `CrossfadeEngine` | `playback-music.md` |
| Video playback, PiP, captions, chapters | `ui/video/VideoPlayerViewModel.kt` | `playback-video.md` |
| Screens, tabs, overlays | `MainActivity.kt`, `ui/home/HomeScreen.kt` | `screens.md` |
| Adding a setting | `data/ThemePreferences.kt` + 4 files + search index | `settings.md` |
| Sign-in, accounts, incognito, backup | `SessionManager`, `ProfileManager`, `AccountSwitcher`, `IncognitoMode`, `BackupRepository` | `identity.md` |
| Device video files | `data/LocalVideoRepository.kt`, `ui/video/DeviceVideosPage.kt` | `screens.md` |
| Subscriptions, blocklist | `SubscriptionActions`, `NotInterestedRepository` | `subscriptions.md` |
| Channels | `ui/channel/` | `channels.md` |
| Player look-and-feel, queue rows | `ui/player/`, `ui/components/QueueReorder.kt` | `player-ui.md` |
| Widgets | `widget/` | `widgets.md` |
| Colors, shapes, motion | `ui/theme/` | `ui-conventions.md`, `DESIGN.md` |

---

## 3. Invariants

Breaking one is a bug even when it compiles. Reasons: `docs/rules.md`.

1. **Never hardcode a color.** Everything routes through `ColorScheme`. Only two exceptions, where the color is the data: YouTube Super Chat colors and `SponsorCategory.color` swatches.
2. **Never fetch a stream with the WEB client**, and never send a playback request whose User-Agent does not match the URL's `?c=` client (`uaForPlaybackUri()`), or googlevideo answers 403.
3. **Every googlevideo media fetch is a bounded ranged request** (`ChunkedStreamDataSource`). Never a plain `DefaultHttpDataSource` or unbounded GET.
4. **Never route a live stream through the progressive path.** The HLS manifest is the only usable source.
5. **A `MediaController` is touched only on its application thread.** From Glance, go through `withController`.
6. **Process-wide repository state is a closed list of eleven**: `LocalSubscriptionsRepository`, `NotInterestedRepository`, `SavedPlaylistsRepository`, `LocalVideoPlaylistsRepository`, `VideoHistoryRepository`, `HiddenPlaylistsRepository`, `IncognitoMode`, the `visitorData` cache, `YouTubeRateLimit`, the video stream-resolution cache, and `LastFmRepository` (settings/service cancellation and queue coordination). A twelfth needs the same justification (a write on one surface must be visible on another holding its own instance), not convenience. Shared OkHttp transport and transient buses (`VisualizerBus`, `WaveformStore`) are not repository state.
7. **A ViewModel needing a setting at decision time does a fresh pref read.** `ThemePreferences` flows do not cross instances.
8. **`SettingsScreen`'s signature is the contract with `MainActivity`.** Add parameters; never reorder or restructure.
9. **Persisted enum constants and stored ids are frozen** (`PlayerStyle`, `SponsorCategory.apiName`, `MotionArtworkQuality`, `IconShape` ids...). Renaming resets every user's choice.
10. **No emojis in code comments or docs.**

---

## 4. Silent failures

Compile-clean, fail-at-runtime traps. Each is a scar; the doc has the story.

| Trap | Symptom | Doc |
|---|---|---|
| Bottom sheet content that does not scroll | Rows clipped away, nothing logged, identical at 0 and 300 items | `screens.md`, `player-ui.md` |
| Widget content overrunning its size bucket | Silent clipping; `LocalSize.current` reports the bucket, not the cell | `widgets.md` |
| `LazyGridScope.items` member shadows the list extension | Fails on argument names rather than falling through | `channels.md` |
| A setting missing from `buildSettingsSearchIndex` | Unfindable by search, no compile error | `settings.md` |
| A store renamed but not renamed in `BackupRepository` | Silently drops out of every backup taken afterwards | `identity.md` |
| NewPipe's playlist extractor signed out | Returns zero items and throws nothing | `youtube-data.md` |
| `subscription/subscribe` and `youtubei/v1/feedback` signed out | Answer HTTP 200 having done nothing | `youtube-data.md`, `subscriptions.md` |
| Timedtext `&fmt=` appended rather than replaced | Returns srv3 XML under a `text/vtt` MIME type | `youtube-data.md` |
| A per-call-site wiring parameter defaulting to null | Feature ships half-wired, nothing compile-fails | `screens.md`, `player-ui.md` |
| A tab-index hand-off assigned across the video toggle | Tab 2 is two different screens, and `selectedTab`'s `videoMode` key discards the write; use `goToTab` | `screens.md` |
| Queue row keys qualified by index | `animateItem` has nothing to animate | `player-ui.md` |
| A new history/search/stats write not gated on `IncognitoMode` | Records silently while the switch says it is paused | `identity.md` |
| A profile-scoped store left in `BackupRepository`'s raw preference copy | Restores onto whichever profile is that device's legacy one | `identity.md` |
| Cookies captured from the page URL rather than the `music.youtube.com` jar | "Logged in but anonymous" | `identity.md` |
| An adaptive manifest or playlist served from the playback cache | Live stalls at the live edge; seeking behind it still works | `playback-streams.md` |
| Behind-live measured from the window end rather than the target live offset | A live stream reads a permanent -0:15 and never says LIVE | `playback-video.md` |
| Removing core library desugaring | Every search throws `NoSuchMethodError` on API 30-32, compiles fine | section 6 below |
| A queue index held across a suspension point | The queue is replaced under it; playback lands on the wrong track | `playback-music.md` |
| A start song absent from the list it is played from | Clamped to index 0, so a tap on one song plays another | `playback-music.md` |
| Catching `Exception` around a suspend body | Swallows `CancellationException`, breaks cooperative cancellation | `playback-streams.md` |
| A coroutine timeout around a blocking `fetchPage()` | Never fires early; the caller waits for the work it is abandoning | `playback-streams.md` |
| The plain `ANDROID` client used as a resolver | `OK` with a full `adaptiveFormats` and no `url` on any entry | `youtube-data.md` |
| An `<activity-alias>` addressed with `context.packageName` as its class package | Apply is a no-op on any build with an `applicationIdSuffix`, works on release | `settings.md` |
| A preview drawable using a platform-styled widget | Draws in the device's accent, differs on every phone | `widgets.md` |
| `coerceIn(low, high)` where `low` can pass `high` | Throws an empty-range `IllegalArgumentException` on a degenerate input | general |

---

## 5. Settled - do not re-litigate

One line each; the reasoning is in `docs/rules.md`.

- **The design language is not replaceable.** M3 Expressive is load-bearing (one `MaterialExpressiveTheme`, dozens of files on Expressive-only APIs). Specific radius/animation complaints are fair game; "a different UI" is a rewrite `DESIGN.md` rules out.
- **A music home is mostly artwork shelves.** [scar] Two shelf-less Spotlights were thrown away.
- **Two Homes share one data source** (`HomeViewModel` flows). Neither Home owns data the other lacks.
- **PO tokens are not the fix for stream extraction.** [verified August 2026] NewPipe's BotGuard provider is dead code on v0.26.5; the Android-reel/visionOS chain works without attestation.
- **Backups copy stores, not serialized models**, so new settings ride along free.
- **The rate-limit hold never gates playback.**
- **Search results are never filtered by the blocklist.**
- **Explicit playlist downloads retain an offline snapshot** (`DownloadedPlaylistStore`) - download intent, separate from Save, not automatic sync.
- **Saved playlists are references, never copies.** "Save as editable copy" is a separate, deliberate action and must not be folded into Save.
- **A custom app icon cannot reach the launcher.** Only the nearest preset `<activity-alias>` applies, and the UI must say so.
- **There is no master issue table.** Labels live on the issues.

---

## 6. Build, test, verify

```bash
./gradlew compileDebugKotlin     # .\gradlew on Windows
./gradlew testDebugUnitTest
```

- Non-packaging Gradle tasks need no permission; compile **per item**, not once at the end. No APKs or R8 locally - push an authorized PR branch and `build.yml` builds a signed APK that goes to Telegram beta testers (`docs/ci.md`).
- Tests are a small JVM suite under `app/src/test/` for pure logic. `isReturnDefaultValues = true` is needed for `KLog`, and it stubs `org.json` to parse everything to nothing - `testImplementation(libs.json.unit.test)` supplies a real one, and every parser test depends on it. [scar]
- **minSdk 30 and desugaring is load-bearing** [scar]: keep `isCoreLibraryDesugaringEnabled` with the `_nio` flavour (`desugar.jdk.libs.nio`), or NewPipe's search throws `NoSuchMethodError` on API 30-32. Anything above API 30 needs a `SDK_INT` guard and fallback.
- Versions: `versionCode`/`versionName` in `app/build.gradle.kts`; dependencies only in `gradle/libs.versions.toml`.
- Emulator/`adb` are the user's to run (debug package `com.ivor.ivormusic.debug`, SDK at `E:\Android\Sdk`); details in `docs/workflow.md`.

---

## 7. Area summaries

The rules most often needed in each area. Each is a summary; open the doc before editing.

### YouTube data layer -> `docs/youtube-data.md`
- Two mechanisms: **NewPipe** (video/artist/playlist search, stream URLs, fallbacks) and **raw InnerTube JSON over OkHttp** parsed by hand with `org.json` (`findObjectsByKey`, `findContinuationTokens`, `getRunText()`), no kotlinx-serialization.
- **Probe first, never parse from memory**: `py .probe/probe.py <endpoint> '<json>' [--music]` (never commit `.probe/`). Signed-in probes sign SAPISIDHASH **per origin**; confirm a session by `logged_in: 1`, never HTTP 200. Note "verified <month year>" in the parser's KDoc.
- Clients: WEB for browse/next/engagement, WEB_REMIX for music.youtube.com, NewPipe's Android-reel/visionOS chain for VOD streams, ANDROID_VR -> IOS only as direct fallback and caption source. **Plain `ANDROID` is SABR-only - no URLs.** Pinned client versions need periodic bumps (HTTP 400 on browse = too old).
- **`visitorData` rides on every InnerTube call**; a missing one now gets `LOGIN_REQUIRED`, and a googlevideo 403 means remint (`refreshVisitorDataAfterPlaybackFailure`), not a UA problem.
- Music metadata comes from **links and page types** (`MUSIC_PAGE_TYPE_ARTIST/ALBUM`), never subtitle positions; release type/year are data (`Song.albumId`, `releaseType`, `releaseYear`).
- Signed out: public browse ids work anonymously, **account browse ids return a valid empty shell** (gate on `isLoggedIn()`), playlists come back as `lockupViewModel`s. Continuations answer under `appendContinuationItemsAction` (`continuationItemsOrNull`), and token scoping matters.
- **Only HTTP 429 arms `YouTubeRateLimit`**, which gates discretionary fan-out only. One `/next` feeds many features - be frugal per user action.

### Music playback -> `docs/playback-music.md`
- `MusicService` (Media3 `MediaLibraryService`) + `PlayerViewModel` over a `MediaController`. Video is a separate pipeline: the ViewModel owns the player and `VideoPlaybackService` only wraps it in a media session.
- **Anchor on the queue occurrence (`MusicQueueItem.id`), never an index or media id**, for anything planned across a suspension: crossfades, pending skips, prefetch, validation, error retries. Read `MusicServiceOccurrenceTest` and `CrossfadeEngineTest` before touching this.
- `replaceMusicSource` keeps index, position and play intent - replacing a stream is not a restart. Queue replacement is one atomic `setMediaItems`.
- Crossfades cancel on seeks and mode changes; clock checks use a net-displacement budget (1100ms), not a tight absolute cap - two earlier caps abandoned every fade. [scar]
- AutoMix: a failed silence probe is unknown audio (0), not silence; `TransitionPlanner` only does long overlaps with reliable tempo evidence.
- The user's speed is the engine's baseline (`setBaseSpeed`, floor 0.1x from Media3); session extras are replaced, not merged - publish via `publishSessionState`.
- Visualizer reads Koda's own PCM (`VisualizerAudioProcessor`), so no RECORD_AUDIO. Lyrics layout belongs to `SyncedLyricsView`. `notify()` catches `SecurityException`.

### Streams, caching, quality -> `docs/playback-streams.md`
- NewPipe `fetchPage()` blocks; **bound the wait, not the work** (`resolveAudioUrlWithinBudget` detaches on `newPipeScope`). Rethrow `CancellationException` ahead of general catches.
- `DeferredSingleFlight`: no resolution work inside `ConcurrentHashMap.compute*`.
- All googlevideo bytes go through bounded ranged requests (10 MB chunks); downloads use their own ranged loop with resumable checkpoints.
- **Music and video have separate caches.** Music is the user-sized LRU; video/Shorts is an uncapped transient cache. Turning a cache switch off makes it read-only, not bypassed; the preload switch gates work.
- **Adaptive manifests, playlists and live segments never touch a playback cache** (`isUncacheablePlaybackUrl`). [scar]
- Scrubs seek `CLOSEST_SYNC`, precise jumps `EXACT`. Quality ladders are highest-first; HDR is opt-in, merged from a raw visionOS `/player`, and `dynamicRange` is part of a quality's identity. `VideoStreamResolutionCache` shares ladders across surfaces.

### Video playback -> `docs/playback-video.md`
- `VideoPlayerViewModel` owns its `ExoPlayer` (built lazily); `VideoPlaybackService` borrows it for the media session and never releases it. Load is two phases: stream qualities, then one `/next` for everything else - add no extractions.
- `VideoWatchTracker` owns history qualification, local progress and authenticated watch-time reports, rechecking history-off/incognito/profile before remote writes.
- Mini-to-expanded is the music player's container transform (`PlayerContainerTransform.kt`, shared with `ExpandablePlayer`); portrait uses a TextureView except HDR and vertical live, which keep the curtain (`supportsAnimatedMinimize`). Never read transition progress in composition; only one view holds the surface.
- `VideoQueue` is index-addressed; `playQueue` establishes it and `playVideo` clears it. Resume has two stores (active session vs per-video history). Chromecast is gone; do not reintroduce a `Player` indirection.
- Every `PlayerView` Koda draws captions over calls `disableBuiltInSubtitles()`.
- SponsorBlock: opt-in, sends only a hash prefix, per-category skip/manual/ignore, read-only, not on live.
- Live: detected by formats, not by `hlsManifestUrl`; behind-live measured from `liveTargetOffsetMs`; quality is a track cap; comments hidden on live; a live item in Shorts is handed off (emit before `close()`).

### Navigation and screens -> `docs/screens.md`
- `NavHost` in `MainActivity`; the `home` route has its own tab system (`AnimatedContent` keyed on `HomeTabKey(tab, videoMode)`) and a floating toolbar using M3's scroll behaviour. Read `hiddenFraction()` only in deferred lambdas.
- Both players are **overlays above the NavHost**. Something that owns the whole window must hide the other layer's mini bar (`miniBarHidden` via `isPlayerExpanded`). A mode switch pauses the other player, it does not dismantle it.
- Library sub-screens are opened by hand-off (`initialArtist`/`initialPlaylist` + consumed callbacks).
- Device videos use `device:` ids through the local-playback path; `singleTask` exists because of PiP; quality/HDR come from the decoder.
- **Three playlist kinds**: local, the account's own, saved (references). A `PL` prefix does not mean yours - use `savedPlaylistIds`. Local video playlists use the `localvp_` prefix; routing lives in `addVideoToPlaylist`. Hidden playlists are a filter over the merged list.
- `VideoOptionsSheet` is two panes; every surface uses `VideoOptionsSheetHost`.

### Subscriptions and blocklist -> `docs/subscriptions.md`
- Account and device subscription stores; `SubscriptionActions` alone decides what a tap means. Unsubscribe clears both; the button binds to `isSubscribedToChannel`.
- The local feed is per-channel Atom RSS with a browse fallback (never on 429), 6 at a time, namespace-unaware parser.
- Imports are sniffed by content (NewPipe JSON, PipePipe/NewPipe zip, Takeout CSV, OPML); handles resolve to UC ids.
- `NotInterestedRepository` is the engine, applied as a **derived filter, never a write into the fetch** (Shorts filter on ingestion). Signed-in dismissals also go to YouTube, fire-and-forget. Music shares the store; the device library is never filtered.

### Channels -> `docs/channels.md`
- **Tabs come from the response**, classified by `params` prefix, never title; no hardcoded tab list.
- One `LazyVerticalGrid` on a six-column base; use `spanItems` to avoid the `items` member-shadowing trap.
- Playlists open in-screen; another channel is a real navigation through `openChannel`. `accountSubscribed == null` means unknown.

### Settings -> `docs/settings.md`
- A new setting threads through five files (`ThemePreferences`, `ThemeViewModel`, `MainActivity`, `SettingsScreen`, `SettingsPages`) **plus `buildSettingsSearchIndex`**. Backups need nothing.
- New strings go only in `values/strings.xml` (locales are partial by design).
- Settings is a hub plus `SettingsPage` enum pages, not routes. Hub rows show the live value; dialogs live in `SettingsScreen`.
- The updater hands off to the browser and never installs. Defaults: player style `EDITORIAL`, device library off.

### Identity -> `docs/identity.md`
- Cookies are captured from the **`music.youtube.com` jar**; writes guard on `isLoggedIn()`.
- Switching a profile is one preference write; `SessionManager`'s API stays as it is. `AccountSwitcher` does invalidation (drops `visitorData`); consumers observe `activeProfileId` with `drop(1)`.
- Local subscriptions, blocklist and watch history are profile-scoped; everything else is device-wide.
- Incognito is enforced inside each write store, suppresses recording only, and is persisted before the flag flips.
- Backups copy allowlisted stores (renames silently drop out), carry profile-scoped stores structurally, restore with `commit()` and restart the process.

### Player UI -> `docs/player-ui.md`
- Nine styles; adding one touches the `PlayerStyle` constant, a `<Name>PlayerContent.kt`, `ExpandablePlayer`'s `when`, and `playerStyleCatalog`, plus an overflow button opening `NowPlayingOptionsSheet`. **`POSTER` is the Canvas player - do not rename.**
- Shared contracts: `SwipeToSkip`, `ExpressiveScrubber` (visual only; haptics through `KodaHaptics`), the waveform (decoded through the cache-backed source, frozen once drawn), `rememberSmoothProgress` (the position is extrapolated from the 1Hz sample at `LocalPlaybackSpeed`; **read it only inside a deferred lambda**).
- `NowPlayingOptionsSheet` is a control panel - tiles, then pills, then the speed and volume deck - while `SongOptionsSheet` stays a list. Volume is the device's `STREAM_MUSIC` level (`util/MediaVolume.kt`), never an app-level gain on `player.volume`. It wears the app palette whole (`appColorScheme()`): a surface takes the app scheme **or** the artwork scheme, never roles from both.
- Three queue views share `QueueReorder`/`QueueRowContainer`: drag handle first, swaps per frame, occurrence-qualified keys, guarded auto-scroll.
- Option sheets share `PlayerOptionRows` and must scroll. `SongOptionsSheet` is hosted once in `HomeScreen`.
- Motion artwork is an opt-in hero layer with frozen quality tiers and a fallback chain.

### Widgets -> `docs/widgets.md`
- Commands go through `withController` (main thread). Rendering reads the `PlayerWidgetStore` snapshot written in `MusicService.onEvents`, never a live session.
- Play/pause masks locally, skips settle first. Measure layouts against the size bucket. Colors from `KodaWidgetTheme`; picker previews are fixed literals without platform-styled widgets.

### UI conventions -> `docs/ui-conventions.md`
- Request Google images at the drawn size (`googleImageAtSize`), layered over the original. Every video frame goes through `VideoThumbnail`.
- Every snackbar uses `DismissibleSnackbarHost`; `Dismissed` means the action stands.
- M3 Expressive first; springs for touch, `tween` for crossfades/progress. The interface scale is a `LocalDensity` override - never provide another one.

### CI -> `docs/ci.md`
- Three workflows, all behind an `authorize` job; fork PRs from outsiders deliberately build nothing. A red run with no logs means no runner was acquired - re-run.
- Judge APK size from the minified release. `localeFilters` must match `locales_config.xml`.
- `telegram-notify.yml` never runs on push; test its script with stubs. Free text reaches scripts as env vars, never `${{ }}`.

---

## 8. Keeping this true

- **Finishing work updates `ROADMAP.md` in the same change** (Planned -> Shipped, fixed defects leave Known defects), then fix any prose here or in `docs/` that leaned on the old behaviour.
- **A new fact goes in the topic doc for its area**, marked `[verified]`, `[scar]` or `[judgement]`. It only earns a line in this file if every session needs it. Keep this file a summary - do not paste detail back in.
- Re-derive `[drifts]` numbers before quoting them; `DESIGN.md` carries its own copies.
- GitHub issues are the task list. Every open issue carries `area:` (interface/playback/foundations/reach), `size:` (XS-XL, effort) and `priority:` (P0-P3, user impact). Details in `docs/workflow.md`.
