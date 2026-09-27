# Koda Roadmap

The long view: where Koda stands, what is still planned, and the constraints any new feature has to survive. Work items live in [GitHub issues](https://github.com/Ivorisnoob/Koda/issues); the reasoning and the scars behind shipped work live in [`docs/`](docs/), one file per area. How the app is built: [`CLAUDE.md`](CLAUDE.md). The design system: [`DESIGN.md`](DESIGN.md).

**Keep this short and current.** When a planned item lands, it leaves Planned work and gets **one line** under [Shipped](#shipped) in the same change; anything worth carrying to the next problem goes into the area's `docs/` file, not here. A fixed defect leaves [Known defects](#known-defects) the same way. Counts and file names drift; re-derive them.

---

## Where Koda is today

Version **5.0** (`versionCode` 28), targeting Android 16 (API 36) with a floor at Android 11 (API 30). All Compose, all inside one `MaterialExpressiveTheme`.

- **Two modes, one app.** A toggle reshapes Home, Search and Library between a music player and a video client, sharing the tab system, overlays and theme.
- **Two playback pipelines.** Music through `MusicService` (Media3 `MediaLibraryService`, crossfade/AutoMix, Android Auto); video through its own `ExoPlayer` with DASH/HLS, PiP, chapters, captions, SponsorBlock and live. Both fetch through bounded ranged requests.
- **No API keys, no mandatory account.** NewPipe Extractor plus direct InnerTube; one visionOS `/player` per play. Search, streaming, downloads, subscriptions and the blocklist all work signed out; signing in adds the real feeds.
- **Identity is plural.** Several YouTube accounts and device-only profiles, switched with one preference write; incognito; whole-install backup.
- **The interface is the product.** Nine player styles, 28 palettes plus dynamic color and AMOLED, a searchable settings hub, springs on everything touch-driven, 25 translations.

`YouTubeRepository.kt` remains the single point of failure for every network path.

---

## Constraints every item inherits

- **No official API keys, ever.** Parsers are probe-first, dated, and fail into an empty result rather than a crash.
- **Signed out is first-class.** Roughly half the users never sign in; an account-only feature needs a device-local answer or says plainly that it is account-only.
- **Material 3 Expressive is structural.** An alternate design language is a rewrite; specific radius/animation complaints are ordinary work.
- **minSdk 30 and desugaring are load-bearing.** Anything above API 30 needs an `SDK_INT` guard and a working fallback.
- **Frugal on network per user action.** New features parse from responses already fetched before earning a request of their own.

---

## Known defects

- **With captions on, each video still makes one extra `/player`.** `ensureCaptionsLoaded` runs in parallel with stream resolution, so `getCaptionTracks` finds the caption cache empty and asks `ANDROID_VR` (plus a `timedtext`) for tracks the visionOS response is about to deliver. Seen in `YTRequests`, September 2026. Fix by loading tracks after the stream phase or awaiting the in-flight resolution.
- **Classic, Morph and Gesture draw a flat background for device songs.** `ChromaticMistBackground` answers a null model with a flat rectangle, and every caller passes `highResThumbnailUrl`, which is null for local files (their art is a content URI).
- **Video watch history does not reach YouTube for some users ([#287](https://github.com/Ivorisnoob/Koda/issues/287)).** Not reproduced: Koda's exact ping sequence reached FEhistory at 10 s when probed (September 2026), so the failure is device-side; the `Video history:` log lines from `3b38e44` in a current-beta bug report will say which way.
- **`MusicServiceBrowserTest` "root fits car tabs" fails on the JVM.** `AutoArtwork.uriFor` chains `Uri.Builder`, which `isReturnDefaultValues` stubs to null. Present since `8d2d942`, unnoticed because CI does not run unit tests; fix in the test.
- **The music mini pill still flashes briefly after a sideways swipe.** Traced September 2026 (`MiniSkipTrace`, since removed): every skip lands on the right occurrence and the carousel snaps only once the pill draws that song, so the state machine is not the cause. Swapping the cover to a plain `AsyncImage` did not cure it on the emulator. Next suspects: the pill's title/ring recomposing from the old song for a frame while `HomeScreen` recomposes, or the neighbour unfreezing before the snap is drawn.
- **A timestamp link to a different video seeks the one playing.** `RichLinkTarget.Timestamp` carries a `videoId`, but every `onTimestampClick` seeks the current player without comparing it.
- **The in-app notification sheet drops two kinds of tap and stops at 20.** `getNotifications` reads only `watchEndpoint`, so Shorts (`reelWatchEndpoint`) and community posts (`browseEndpoint`) open nothing, and it never follows the menu's continuation (probed September 2026, `docs/youtube-data.md` has the shape).
- **Upload-check state is missing from backups.** `UploadCheckRepository`'s prefs (`upload_check`: profile-scoped mutes, device-wide last-seen marks) are not in `BackupRepository`'s allowlist, so a restore forgets every mute.
- **Music below 1x reportedly hisses ([#290](https://github.com/Ivorisnoob/Koda/issues/290)); a Nothing Glyph visualizer stops after wired USB-C audio ([#270](https://github.com/Ivorisnoob/Koda/issues/270)).** Neither reproduced; both need the device.

---

## Planned work

### Interface

**Notification bell, account half** ([#86](https://github.com/Ivorisnoob/Koda/issues/86)). The device half ships (`work/UploadCheckWorker.kt`, per-channel mutes). Left: writing the bell preference to YouTube for *account* subscriptions. The device check deliberately reads local follows only, so one upload never produces two notifications.

**Comment interaction notifications.** Replies, likes and hearts on the user's comments, in the in-app sheet and as phone notifications. They arrive through the same `get_notification_menu` inbox as uploads (there is no mentions request type on WEB), and `get_unseen_count` is a cheap poll with a 30-minute suggested interval. Blocked on a probe: the probe account's whole inbox (62 items, September 2026) holds no comment interaction, so the item shape is unseen. Needs an account with replies or likes on its comments.

**Community post links.** `youtube.com/post/<id>` still leaves the app: opening a post needs its `FEpost_detail` params, which come from the channel's Posts tab, so a post screen that resolves from the id alone comes first. Post comments themselves ship.

**Spotlight leftovers.** Sort/filter the shelves (play count, duration, date added - all in `Song` and `playCounts`); long-press actions on shortcut tiles and shelf cards; rank the shortcut grid by `playCounts` instead of a fixed interleave. Rule: Spotlight arranges `HomeViewModel` flows and never owns data Classic Home lacks.

**Playlist editing leftovers** ([#109](https://github.com/Ivorisnoob/Koda/issues/109), [#144](https://github.com/Ivorisnoob/Koda/issues/144)). Multi-select from library and search results into a playlist ([#253](https://github.com/Ivorisnoob/Koda/issues/253) is the toolbar for it); editing a description after creation; a generated cover derived from the playlist's contents rather than only the palette. Know the storage first: each local playlist rewrites its whole `Song` list on every add (`docs/screens.md`).

**Respect reduced motion** ([#112](https://github.com/Ivorisnoob/Koda/issues/112)). Lyrics, motion artwork, the visualizer and the connection card read `ANIMATOR_DURATION_SCALE`; player-style springs and screen entrances do not. Fix with one shared motion vocabulary that collapses when the scale is zero. Decide rather than assume: meaningful transitions (player expanding, a sheet arriving) become instant rather than vanish, and motion-defined styles (Morph, Sticker) need a designed still state.

**Predictive back, the panels.** Every screen stack and overlay is done (`docs/screens.md`). Left: the video player's comments and live-chat panels, its fullscreen quality sheet, and the player style wheel - panels over content, which want their own treatment rather than `PredictiveBackStack`. The cancel path must spring back cleanly.

**One heading system** ([#118](https://github.com/Ivorisnoob/Koda/issues/118)). Screen titles still span `displayLarge` (music Home) to `headlineSmall`, and section headers split across three title scales. Define the ladder once in a shared screen-header composable and sweep; fold it into the tablet pass since both touch every screen. Player styles are exempt: their type is the composition.

### Playback

**YouTube Music-first catalogue** ([#182](https://github.com/Ivorisnoob/Koda/issues/182)). Song/release search and playlist browse already read WEB_REMIX with canonical album links and release metadata. Left: a source-aware result model, deterministic ranking and deduplication (official releases, Topic tracks and album-linked recordings first; live/acoustic/remix kept distinct), parser fixtures per entity, an honest unavailable state, and signed-in/out playback tests. Ordinary YouTube stays an explicit fallback, never a silent substitute.

**Listen as music without the gap.** It ships pause-first. The follow-up: disable the video track at once, have `MusicService` resolve and buffer the same id at the current position, and swap only when it reports ready (the warm-then-swap `prefetchUpcomingSongs` already applies to skips). Tear down one media session as the other comes up, or Bluetooth controls point at the wrong player.

**SABR playback** ([#259](https://github.com/Ivorisnoob/Koda/issues/259)) and **dubbed audio / translated captions** ([#201](https://github.com/Ivorisnoob/Koda/issues/201)) are open; PO tokens are settled as not the fix for extraction (`docs/rules.md`).

### Foundations

**NewPipe-format export** ([#154](https://github.com/Ivorisnoob/Koda/issues/154)). m3u/m3u8 and NewPipe/PipePipe import, plus m3u8 export, ship. Left: an export the other YouTube clients can import, and checking the NewPipe playlist-table reader against a real export.

**Surviving process death, one layer deeper.** Home tabs, scroll, search query/filters and both players' sessions survive. Left: `PlaylistDetailScreen`, `ArtistScreen`, `ChannelScreen`'s grid, queue sheets, and open sheets/dialogs. A prerequisite for tablets, where resizing makes recreation routine.

**Image crossfade audit.** Crossfade is set per call site and inconsistently; a global loader default would silently animate everything, so audit what should fade first. A shared placeholder/error treatment belongs in the same place.

**Dependency size.** `material-icons-extended` is one of the largest artifacts; if the referenced icon set is bounded, pulling those icons out may be a size win. See `docs/workflow.md` for the dependencies that look unused and are not.

### Reach

**Android Auto, on a real head unit** ([#126](https://github.com/Ivorisnoob/Koda/issues/126), [#172](https://github.com/Ivorisnoob/Koda/issues/172)). The September 2026 audit fixed command grants, search completion, cold-load deadlines and device-song URIs (`docs/playback-music.md`). Left: validation in a car or the desktop head unit, and the car app quality audit. Sideloaded installs need Auto's "Unknown sources"; Settings, Advanced explains it.

**Tablets and landscape** ([#129](https://github.com/Ivorisnoob/Koda/issues/129), [#165](https://github.com/Ivorisnoob/Koda/issues/165)-[#168](https://github.com/Ivorisnoob/Koda/issues/168), [#282](https://github.com/Ivorisnoob/Koda/issues/282)). Koda is portrait-locked with no `WindowSizeClass`, rail or list-detail, yet Android 16 ignores orientation locks on large screens for SDK 36 apps - confirm on a real tablet first; if it holds, this is correctness, not a feature. Order: `WindowSizeClass` and retire the locks; rail at medium width; real column counts; list-detail for Library and Settings. The expensive part is deciding what each player style means on a tablet; "centred at phone width" is a legitimate answer for some.

**Wear OS** ([#130](https://github.com/Ivorisnoob/Koda/issues/130), [#169](https://github.com/Ivorisnoob/Koda/issues/169), [#170](https://github.com/Ivorisnoob/Koda/issues/170)). The phone stays the player. Tier one: verify on hardware what Wear's system media controls already do. Tier two, only if that falls short: a `:wear` companion over the Data Layer. Wear Material 3, not Expressive; no video.

---

## Not planned

Decided against in September 2026; each issue's closing comment has the full reason. Reopen one only when the reason below has changed.

- **Android TV** ([#131](https://github.com/Ivorisnoob/Koda/issues/131)): a second app that shares only the backend (D-pad focus, distance type scale, lean-back home).
- **Standalone Wear playback** ([#171](https://github.com/Ivorisnoob/Koda/issues/171)) and **the `:core` module split** ([#133](https://github.com/Ivorisnoob/Koda/issues/133)) it would need: the phone's login cannot leave the device, and a watch battery cannot carry the extra YouTube requests.
- **Cast** ([#132](https://github.com/Ivorisnoob/Koda/issues/132)): the first version was removed for not working; the receiver hits YouTube's client restrictions and cannot merge split streams. A rebuild would start from nothing.
- **Discord Rich Presence** ([#243](https://github.com/Ivorisnoob/Koda/issues/243)): on Android it needs the user's Discord account token (against Discord's terms) or a registered Discord app (against the no-API-keys rule).
- **Music-video loops behind Canvas** ([#246](https://github.com/Ivorisnoob/Koda/issues/246)): a second stream per song in data, battery and YouTube requests; motion artwork covers it.
- **Spotify/CSV playlist import** ([#188](https://github.com/Ivorisnoob/Koda/issues/188)): text-only exports mean one search per track and confident wrong matches.
- **ID3 tag editor** ([#238](https://github.com/Ivorisnoob/Koda/issues/238)): a system write prompt per file for a niche of a streaming app.

---

## Shipped

One line each; the reasoning is in `docs/`, and the full write-ups are in this file's git history.

**Awaiting device validation** (built and unit-tested, not yet confirmed on screens): the late-September links/comments/PiP/scrub batch below (posting and liking on a post in particular were never probed); one-request playback and the connection advice card; the September batch below; playlist membership ticks; upload to YouTube Music; music Videos tab; Watch/Listen handovers; swipe-up options; Shorts scrubbing; 8x speed; shuffle order; inline previews; bot-check traffic cuts; video playlist page; Android Auto fixes; Shorts prefetch; watch-history sessions; options control panel and volume; smooth progress; Spin; Hero; artist page; album links; mini-player container transform; live edge fixes; waveform; visualizer; motion artwork; AutoMix listening.

### September 2026

- Smooth motion: opt-in video frame interpolation (a GPU effect in Media3's pipeline doubling 24-30 fps video), behind a warning, pausing on heat, battery saver, speed-up and dropped frames. Awaiting device validation.
- YouTube links stay in Koda (manifest claims channel paths; in-app links route through `LocalUriHandler`; tappable post links, mentions and bio addresses); community post comments; PiP button choice (Next video / Skip 10s); a scrub return point with a magnetic snap on the music and video bars; both mini bars swipe sideways to the next/previous item inside a bar that stays put, and swipe down to dismiss (#254 closed in its favour); artists with device files open their streamed page with an "On this device" section; blocked notifications no longer lose new uploads and ask for the permission; upload mutes follow profile switches.
- Late September feedback ([#298](https://github.com/Ivorisnoob/Koda/issues/298) [#299](https://github.com/Ivorisnoob/Koda/issues/299)): Material 3 alpha29 and Kotlin 2.4.20; segmented lists across the app (`SegmentedColumn`, `segmentedColorsOver`); Home tab flick past a shelf's end and nav-bar scrub; sideways video mini-bar dismiss; Library FAB removed; brightness to the true minimum; watched Shorts skipped; calmer dark Hero; fresh-first shuffle; playing row highlighted on playlist pages; commenter opens channel; PiP previous/next; launcher shortcuts; Artists/Albums sort; video queue saved as a playlist, music queue carried to video, local video playlist tools and import, cross-mode device playlists; open-source licences screen.
- One visionOS `/player` per song, video or Short under Koda's own visitorData (8 requests and 3 new ids down to 1-2 and 0); refused connections explain what to do per network and resume on reconnect.
- Content region (`gl`), fully block Shorts, sleep-timer fade, Return YouTube Dislike, Save also likes into the account library ([#139](https://github.com/Ivorisnoob/Koda/issues/139)), subscription refresh interval, Cards/Compact/Grid video lists, Spotlight Home/Explore/Charts/New tabs ([#291](https://github.com/Ivorisnoob/Koda/issues/291) [#285](https://github.com/Ivorisnoob/Koda/issues/285) [#277](https://github.com/Ivorisnoob/Koda/issues/277) [#240](https://github.com/Ivorisnoob/Koda/issues/240) [#242](https://github.com/Ivorisnoob/Koda/issues/242) [#276](https://github.com/Ivorisnoob/Koda/issues/276) [#266](https://github.com/Ivorisnoob/Koda/issues/266) [#274](https://github.com/Ivorisnoob/Koda/issues/274)).
- Classic Home: Your playlists, Liked tile, top artists, mixes, Ready offline and real Recent albums.
- Local playlists sort (title/artist/album/reverse/shuffle with undo), export m3u8, import m3u/m3u8 and NewPipe/PipePipe backups ([#154](https://github.com/Ivorisnoob/Koda/issues/154), [#296](https://github.com/Ivorisnoob/Koda/issues/296)).
- Add-to-playlist sheets tick playlists already holding the item and toggle in place.
- Upload a local playlist to YouTube Music as a private copy, in batches.
- A Videos tab in music search, played in the music player.
- Watch as video / Listen as music hand the item across and close the player it left.
- Swipe up on the expanded player opens its options.
- Draggable Shorts progress line.
- Uncoloured text follows the theme foreground (dark-mode black text fixed).
- Music and video up to 8x; logarithmic music speed slider.
- Lyrics retry with cleaned titles and failed providers.
- Shuffle opens on the playing song and plays the whole list before recommendations; queue screens draw the play order.
- Inline video-card previews in the feed, off by default.
- Less repeated traffic after a bot-check refusal; Home loads stand for ten minutes.
- Video playlist page matches the music one.
- Video search "Today" filter; channel sort chips restored.
- Home shuffles subscriptions when recommendations are off.
- Android Auto browse/playback fixes; lower speculative Shorts traffic; paged video Library and history; pagination correctness across playlists, history and sorted search.
- Shorts buffer ahead; signed-in Shorts use the account's seedless sequence.
- Sessions are logins, not cookie strings: watch history survives cookie rotation and mid-flight account switches.
- Player overflow as a control panel with a device volume slider.
- Progress bars extrapolate every frame from the 1Hz sample.
- Lyrics in Montserrat where the script allows.
- Mode-qualified tab hand-offs (artist from the player no longer opens Subscriptions).
- Optional Last.fm scrobbling; lyrics provider ordering and switches.
- Spin, a song wheel over the music Home.
- Hero, the ninth player style.
- Redesigned music artist page; "Go to album" from song menus.
- Video mini player uses the music player's container transform.
- Watched-progress bars on video thumbnails and authenticated watch-time reports.
- Live: behind-live measured from the target offset; manifests and playlists kept out of the playback cache.
- Device songs no longer duplicated by the OEM manual scan.
- Whole-song waveform seek bar; permission-free visualizer; motion artwork with quality tiers.
- YouTube Music release metadata (album/artist by link type, release type and year).
- Canvas player stands its cover on an edge-sampled field.
- Settings rows explain themselves on long press; layout cards for Home and navigation style.
- Scheduled backups ([#230](https://github.com/Ivorisnoob/Koda/issues/230)).
- Widgets work on OEM launchers; transparent bars on OEM skins.
- Save the current queue as a playlist ([#229](https://github.com/Ivorisnoob/Koda/issues/229)).
- Alternate launcher icons and an icon studio.
- Widget picker previews repaletted; Bloom without a card.
- Playlist downloads keep an offline snapshot; downloads pause and resume; downloaded playlists expand in place.
- Separate music, video and Shorts caches plus a preload switch; music cache slider to 10 GB.
- HCT preset schemes (Tonal Spot, Vibrant, Expressive, Fruit Salad, Monochrome).
- Album/playlist pages lead with Play all and Shuffle.
- Music playback anchored on queue occurrences across suspensions; stream replacement keeps position and intent.
- AutoMix: tempo/key-aware overlaps, album sequences preserved, drifting handoffs abandoned, failed silence probes no longer cut songs.
- Spotlight "For you" gains albums, artists and playlists; artist discography sort and filter.
- Incognito badge in video mode; local video thumbnails stop spinning.
- Offline video Home from downloads; compact video Home; video Library opens Downloads.

### Earlier

- Slow stream resolution no longer skips songs (bounded wait, not bounded work); `ANDROID` client found SABR-only.
- Swipe-to-dismiss snackbars everywhere.
- Video Home destinations can be hidden and reordered; recommendations can be turned off.
- Video resume after close, per profile, with a "play from start" offer.
- Collab videos: stacked avatars and a collaborators sheet, from cards and the watch page.
- Portrait fullscreen bottom bar; `singleTask` for PiP hand-offs; no keyboard after PiP.
- Tapping a playlist row plays that exact occurrence; tapping the loaded video resumes it.
- HDR and Standard quality tabs.
- Device videos in video mode: folders, decoder-read quality/HDR, embedded audio/subtitle tracks, "Open with" in and out, history per profile.
- Video thumbnails fall back and retry instead of failing silently.
- Per-profile watch history, per-entry removal with undo, Clear.
- Incognito.
- Chromecast removed in full (see Not planned).
- Updater and About pages rebuilt.
- Shared stream-resolution cache and OkHttp transport.
- Lyrics hold their shape at large scales.
- Scroll-aware floating navigation pill.
- Classic Home keeps its three hero artworks with a last-good snapshot.
- Local albums in disc/track order.
- Playlist download counts distinguish entries from files.
- Video recent searches.
- Media3 1.5 to 1.11.
- Transient video cache; lazily built players.
- Near-instant scrubbing (keyframe seeks, 800ms resume, 45s back buffer).
- Playlists past 100 songs ([#218](https://github.com/Ivorisnoob/Koda/issues/218)).
- `DeferredSingleFlight` fixes the recursive-update crash.
- Release footprint audit; locale filters follow `locales_config.xml`.
- README rewrite; Telegram beta builds with commit changelogs.
- i18n: strings in resources, 25 translations, per-app language ([#208](https://github.com/Ivorisnoob/Koda/issues/208)).
- Standing down on HTTP 429 (`YouTubeRateLimit`).
- visitorData on every InnerTube call; shared OkHttp disk cache.
- SponsorBlock, hash-prefix only ([#228](https://github.com/Ivorisnoob/Koda/issues/228)).
- Interface scale (Display size).
- Loudness normalisation from YouTube's own measurement.
- Two-player crossfade and the AutoMix planner, including silence skip.
- Playlist creation studio, import from existing playlists, generated and chosen covers.
- Saving others' playlists and albums as references, in both modes.
- Local-first lyrics with word timing.
- Personalised video feed, chapters, comments, adjustable captions, storyboard scrubbing.
- Offline downloads for music and video, portable tagged music files, whole-playlist downloads.
- Live streams with chat and a vertical live player.
- Account-free subscriptions with imports; "Don't recommend" blocklist.
- Multiple accounts and device-only profiles.
- Spotlight alternative Home.
- Searchable settings hub.
- Restored root tab per mode; saved tab scroll.
- Listening stats and music listening history.
- Editable queues in both modes; video playlists as queues; local video playlists.
- Real channel pages built from the response, community posts included.
- Whole-install backup and restore.
- In-app bug reporting with local crash capture.
- Daily time limit.
- Quick Settings tile; six Glance widgets.
- Voice search and assistant playback; six-node Auto browse root with instant cached answers.
- Haptics on one vocabulary with a four-level dial.
- Background upload check with per-channel mutes.
- Predictive back on every screen stack and overlay.
- Listen as music and Watch video.
- Edge-to-edge feeds.
- One process-wide Coil loader.
