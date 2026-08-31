# TV Mode

A third mode alongside Music and Video: movies, series and anime, with the source, quality,
audio language and subtitle choices that a real streaming client is expected to have.

This document is the plan. It is written to the same rules as `CLAUDE.md` - `[verified <month>]`
means probed against a live endpoint or read out of the actual source, `[judgement]` is a call
someone could reasonably make differently, `[scar]` is something that already went wrong somewhere
and should not be rediscovered here. Counts marked `[drifts]` are true as of writing and should be
re-derived rather than quoted.

---

## 1. How Koda's UI is built today

Read this first, because every recommendation below is shaped by it.

**One theme, no escape hatch.** `MainActivity.setContent` opens `IvorMusicTheme`, which is a single
`MaterialExpressiveTheme` carrying colour scheme, `MotionScheme.expressive()`, the rounder-than-stock
`ExpressiveShapes` scale, and typography. Everything renders inside it, including both player
overlays, which sit outside the navigation graph. There is no second design system to add TV mode to.

**Navigation is a shallow `NavHost` with one deep route.** `MainActivity` hosts routes for
`onboarding`, `home`, `settings`, `downloads`, `stats`, `update`, `channel/{channelId}` and a handful
of leaf screens. Almost all of the app is inside `home`, which runs its **own** tab system: an
`AnimatedContent` over a `selectedTab: Int` with an inline `HorizontalFloatingToolbar` (or a plain
`NavigationBar` when the non-expressive nav-bar setting is on) built in `HomeScreen.kt`. Tabs are not
routes.

**The mode toggle is a boolean, and it reshapes those tabs.** `ThemePreferences.videoMode` (prefs key
`video_mode`) selects between three music tabs (Home, Search, Library) and four video tabs (Home,
Search, Subscriptions, Library). Tab 0 and tab 2 branch on it inside the same `when`. Spotlight, the
opt-in alternative music home, is a third branch in that same `when` rather than a fourth tab - which
is the precedent for how a new surface enters this screen cheaply.

**`videoMode` is threaded by hand.** 117 references across 13 files [drifts]: `ThemePreferences`,
`ThemeViewModel`, `MainActivity` (twice - into `MusicApp` and into `SettingsScreen`), `HomeScreen`,
`HomeViewModel`, `SpotlightHomeContent`, `SearchScreen`, `VideoHomeContent`, `OnboardingScreen`,
`SettingsScreen`, `SettingsPages`, `MusicVideoToggle`, `DiagnosticsCollector`. There is no DI, so this
is the wiring mechanism.

**Both players are overlays above the `NavHost`.** `ExpandablePlayer` (music, eight styles, dispatched
from one `when`) and `VideoPlayerOverlay` (video, its own `ExoPlayer`, PiP, fullscreen). They render on
every route, which is why a `NavHost` destination like the channel screen has to ask them to step
aside. Both minimise to a mini bar rather than closing.

**Settings is a hub plus eleven pages**, not routes: a `SettingsPage` enum driven by `AnimatedContent`,
because `SettingsScreen` already takes 95 parameters [drifts] and a route per category would repeat
that list eleven times. A new setting threads through five files plus `buildSettingsSearchIndex`, which
is the one omission that fails silently.

**Everything is measured in `dp` that the user can rescale.** `IvorMusicTheme` provides a `LocalDensity`
multiplied by `ui_scale`, range `0.85f`..`1.15f`. Any new chrome has to fit at 1.15 on a small phone,
not only at 1.0 on a Pixel.

**Data is per-ViewModel repositories over SharedPreferences**, with a closed list of seven process-wide
singletons and an explicit rule that an eighth needs the same justification. `YouTubeRepository.kt` is
8,451 lines [drifts] and is the single point of failure for every network path.

Shape of the codebase: 211 Kotlin files, 26 locale directories, 889 strings in `values/strings.xml`
[drifts].

---

## 2. What the incumbents actually do

Probed and read rather than recalled.

### Stremio is a protocol, not an app

The whole thing is an HTTP contract. An addon is a server with `/manifest.json` and
`/{resource}/{type}/{id}.json`, optionally `/{resource}/{type}/{id}/{extraArgs}.json`. Four resources
exist: `catalog`, `meta`, `stream`, `subtitles`, plus `addon_catalog` for addons that list other addons.
Content types are `movie`, `series`, `channel`, `tv`, and community types like `anime`.

The important consequence: **catalogues, metadata, streams and subtitles are four independent markets.**
An addon that provides posters knows nothing about where the file comes from, and the client is what
joins them by id. Ids are namespaced by prefix - `tt` for IMDb, `kitsu:`, `mal:`, `anilist:`, `anidb:`
for anime - and the manifest's `idPrefixes` is how a client knows which addons to even ask.

**What is keyless today** [verified August 2026, all probed live with no credentials]:

| Addon | Resources | What it gives |
| --- | --- | --- |
| Cinemeta `v3-cinemeta.strem.io` | catalog, meta, addon_catalog | Movies and series. Six catalogs (Popular / New / Featured, movie and series), each declaring its own genre list. |
| Anime Kitsu `anime-kitsu.strem.fun` | catalog, meta, subtitles | Five anime catalogs (Trending, Top Airing, Most Popular, Highest Rated, searchable list). Emits `kitsu:` ids and cross-maps to `imdb_id`. |
| OpenSubtitles v3 `opensubtitles-v3.strem.io` | subtitles | 90 tracks returned for one movie, with release name, format and encoding per track. |
| Cinemeta `addon_catalog` | - | 7 official and 95 community addons, machine-readable, with transport URLs and full manifests. |

**Cinemeta's catalog response is unusually rich, and that changes the home screen design.** A single
`/catalog/movie/top.json` returns 50 full meta objects - not previews. Field coverage measured on that
response [verified August 2026]: `logo` 50/50, `background` 50/50, `poster` 50/50, `description` 50/50,
`runtime` 50/50, `cast` 50/50, `trailers` 49/50, `imdbRating` 46/50. One request paints a hero, a shelf,
and most of a detail page. `cacheMaxAge` is 14400 (4h). Catalog requests 307-redirect to
`cinemeta-catalogs.strem.io`, so the client must follow redirects and must not treat the redirect target
as canonical.

Series meta adds a `videos` array - 67 entries for Breaking Bad, each with `season`, `episode`, `name`,
`overview`, `released`, `rating` and a real `thumbnail` on `episodes.metahub.space`. Episode ids are
`tt0903747:1:1`, which is also the id a stream addon is asked for.

**Streams carry one of `url`, `ytId`, `infoHash`, `externalUrl`.** Plus `name` (conventionally the
quality line), `title`/`description` (the release name plus stats), `subtitles`, and `behaviorHints`
with `bingeGroup`, `proxyHeaders`, `notWebReady`, `filename`, `videoSize`.

`bingeGroup` is the one hint worth building around: addons with the same `bingeGroup` are chosen
automatically for the next episode. It is the protocol's answer to "do not make me pick a source 28
times in a row", and it is free.

### What torrent addons actually return

Torrentio, unconfigured, on `tt1375666` [verified August 2026]: 67 streams, every one an `infoHash` with
`fileIdx`, no `url`. Same for the Public Domain Movies addon, which is the *legal* default in Stremio's
own official catalog - also torrents. Configured with a debrid key in its URL path, Torrentio returns
resolved `url`s instead; the addon talks to Real-Debrid / AllDebrid / TorBox / Premiumize and the client
never sees a credential of its own. Its config surface is `providers` (25 indexers), `sort`, `languages`,
`qualities`, `limit`, `sizeFilter`, `debridProvider` and eight `apiKeys` [verified August 2026].

**So the honest baseline is: with no configuration, TV mode can browse everything and play almost
nothing.** That is not a flaw in the plan, it is the shape of the ecosystem, and section 8 designs for
it rather than hiding it.

### HTTP scraper addons die

`webstreamr.hayd.uk`, the addon most guides pointed at for direct HTTP streams, now answers every stream
request with a single item titled "Webstreamr is deprecated" and an `externalUrl` [verified August 2026].
That is the whole argument against Koda shipping its own scrapers: the scraper layer is the part that
rots, and it rots on someone else's schedule.

### CloudStream is the Android reference, and it disagrees about dub

CloudStream (`recloudstream/cloudstream`, read at HEAD) is the closest analogue - Kotlin, Media3, plugin
providers. Its `MainAPI` is an abstract class with `getMainPage`, `search`, `quickSearch`,
`load(url) -> LoadResponse`, and `loadLinks(data, isCasting, subtitleCallback, callback)` which *streams*
results back through callbacks as each extractor resolves. A resolved link is an
`ExtractorLink(source, name, url, referer, quality: Int, headers, type: VIDEO|M3U8|DASH|TORRENT|MAGNET,
audioTracks: List<AudioFile>)`.

Two things it does that matter here:

**`AnimeLoadResponse.episodes` is a `MutableMap<DubStatus, List<Episode>>`.** Dub is not a track on a
stream - it is a **different episode list**. `ResultViewModel2` indexes by
`EpisodeIndexer(dubStatus, season)` and additionally by `EpisodeRange`, because a 1,000-episode anime
cannot be one list.

**Quality is a per-network profile, not a number.** `QualityDataHelper` stores source priority and
quality priority per profile, where profile types are `WiFi`, `Data`, `Download`. Koda already has
exactly this idea in `ThemePreferences.getDefaultVideoQuality()` picking Wi-Fi or mobile via
`isActiveNetworkMetered`, so the pattern transfers instead of being invented.

### AIOStreams is the state of the art in presenting sources

The 2026 aggregator addon exists because raw stream lists are unusable. It parses each stream into
resolution (240p-2160p), quality (CAM through BluRay REMUX), encode (AVC / HEVC / AV1), HDR and Dolby
Vision tags, audio format (Atmos / TrueHD / DTS) and channels, language, seeder count, age, size,
bitrate, indexer and release group; then deduplicates by filename, infohash, or a hash over chosen
attributes; then sorts by stacked criteria with separate orders for movies, series and anime.

That list is the badge vocabulary. Koda should parse it client-side rather than depending on an
aggregator addon being installed, because the input is just the release name and the parse is a few
hundred lines of regex.

---

## 3. The architecture decision

**Koda's TV mode is a Stremio addon client. It ships no scrapers.**

Three alternatives were considered:

**Built-in scrapers (the CloudStream model).** Rejected. It puts the most volatile code in the project
inside the APK, on a release cadence measured in GitHub Actions runs rather than hours. It also directly
contradicts the constraint that every parser in this project is probe-first and dated: a scraper for a
site that changed yesterday fails silently for everyone until the next release. WebStreamr's tombstone
is the evidence.

**A torrent engine in-app.** Rejected for v1. It is a large native dependency, it is the single worst
thing you can do to a phone's battery and a metered connection, and it changes what the app *is* on
every store and mirror it is distributed through. Debrid, which the ecosystem already treats as the
default, gets the same content over plain HTTPS with none of that.

> **Overruled after phase 3, deliberately and with the cost measured.** Testing seventeen addons found
> no free-and-playable path at all: every route to a file goes through torrents, a debrid subscription
> or a registration, and even Stremio's own Public Domain Movies addon returns torrents. Requiring a
> paid account to use a mode contradicts "the signed-out path is a first-class path" harder than the
> objections above. libtorrent4j is now wired in behind `TorrentEngine`. **The size cost is real and
> larger than this section assumed**: the packaged `libtorrent4j.so` is 15.79 MB on arm64 and 13.20 MB
> on armv7 [measured August 2026 from the APK, not from the artifact - Maven's 6.2 MB jar is the
> compressed form], and native libraries are stored uncompressed and untouched by R8, so that figure
> is identical in a release build. That is what makes the product-flavour split load-bearing rather
> than a nicety. The battery and metered-connection objections stand unchanged and are answered by
> capped connections, an upload ceiling, and tearing the session down when playback stops.

> **Overruled again, and the section as originally written now stands.** `TorrentEngine`,
> `TorrentDataSource` and all four libtorrent4j artifacts were removed before the branch merged, so
> the amendment above describes code that is no longer in the tree and the product-flavour split it
> called load-bearing is moot - there is no native dependency left to split away from. Koda plays a
> resolved `url` and nothing else; a bare `infoHash` needs a debrid service configured into the
> addon's own URL. *The reason for this second reversal is not recorded - fill it in here, because
> the evidence that motivated the first amendment has not changed and the next person reading this
> will otherwise re-derive it and rebuild the engine.*

**Debrid-only.** Rejected as the *only* path, because it makes a paid account mandatory to use a mode,
which the project's own constraint ("the signed-out path is a first-class path") argues against. It
stays the *recommended* path.

**Why the addon protocol wins.** It is the one boundary where the volatile half lives on someone else's
server, the stable half lives in Koda, and both halves are already populated by an ecosystem of 100+
addons. It also matches how this codebase already thinks: `data/SubscriptionActions.kt` and
`data/NotInterestedActions.kt` exist because "one place decides what this means" is already the house
pattern, and an addon registry is the same idea one level up.

**Ship these three preinstalled, and nothing else:** Cinemeta, Anime Kitsu, OpenSubtitles v3. All
keyless, all metadata or subtitles only, none of them a source of files. That makes browsing, searching,
watchlisting and subtitle selection work on first launch with zero configuration, and leaves the one
decision that is genuinely the user's - where files come from - to the user.

### One playback overlay, not two

> **Revised when phase 3 was built.** This section proposed reusing `VideoPlayerOverlay`. Carrying it
> out means extracting an interface across `VideoPlayerViewModel` and the 2,044-line
> `VideoPlayerContent` that reads dozens of its members - a mechanical refactor with real regression
> risk on the app's most-used surface. What shipped instead avoids the cost this section was worried
> about by a different route: `TvPlayerScreen` is **strictly full-screen with no mini bar**, so it
> has no expand animation, no nav-bar arithmetic and nothing to step aside for - one boolean above
> the NavHost rather than a third overlay in the sense meant below. The trade is stated where it is
> paid: you cannot browse while a film plays. The reasoning below stands as the argument the
> deviation had to answer.

[judgement] TV playback reuses `VideoPlayerOverlay` rather than adding a third overlay above the
`NavHost`. Two view models (`VideoPlayerViewModel`, `TvPlayerViewModel`) behind a discriminator, one
overlay, one mini bar, one `VideoPipController`, one "step aside for the channel screen" path.

The alternative - a third overlay - triples the z-order and step-aside logic for no benefit, since only
one of them can hold audio focus anyway. The other alternative - extending `VideoPlayerViewModel` -
makes the app's second-largest and youngest file (3,375 lines [drifts]) worse, and `ROADMAP.md` already
names that file as concentrated risk.

What gets shared as composables rather than duplicated: `PlayerGestureSurface` (hold-to-2x, swipe,
double-tap seek), the captions sheet and its styling controls, `VideoPipController`, `CacheManager`,
`QueueReorder` / `QueueRowContainer`, `PredictiveBackStack`, `ExpressivePullToRefresh`, `Skeleton`.

---

## 4. Data layer

New package `data/tv/`. Nothing in it imports `YouTubeRepository`.

```
data/tv/
  AddonManifest.kt          Manifest, Catalog, ExtraProp, ResourceSpec - parsed with org.json
  AddonRepository.kt        Installed addons, order, per-resource enable/disable, install/remove
  StremioClient.kt          The four resource calls over OkHttp. Follows redirects. No POSTs.
  TvCatalogRepository.kt    Home shelves, genre chips, paging by skip=, search
  TvMetaRepository.kt       meta/{type}/{id}, episode lists, season and range grouping
  TvStreamRepository.kt     Fan-out to every stream addon, merge, dedupe, rank
  ReleaseNameParser.kt      Release name -> StreamTags
  TvSubtitleRepository.kt   Addon subtitles + embedded tracks, merged and deduped
  TvWatchlistRepository.kt  Device-local. Process-wide (see below)
  TvProgressRepository.kt   Resume positions and watched flags, per item and per episode
  TvModels.kt               TvItem, TvMeta, TvEpisode, TvStream, StreamTags, AudioVariant
```

### Models, and the one that matters

```kotlin
data class TvStream(
    val addonId: String,          // which addon produced it, for the source badge
    val url: String?,             // resolved HTTP(S) - the only playable case in v1
    val infoHash: String?,        // present, shown, and NOT playable without debrid
    val externalUrl: String?,     // hand to the system browser
    val ytId: String?,            // hand to Koda's own video player - see section 7.5
    val rawName: String,          // stream.name, conventionally the quality line
    val rawTitle: String,         // stream.title/description, the release name plus stats
    val bingeGroup: String?,      // behaviorHints.bingeGroup
    val proxyHeaders: Map<String, String>?,  // behaviorHints.proxyHeaders.request
    val filename: String?,
    val videoSize: Long?,
    val subtitles: List<TvSubtitle>,
    val tags: StreamTags,         // derived by ReleaseNameParser, never trusted blindly
)

data class StreamTags(
    val resolution: Int?,               // 2160, 1080, 720...
    val sourceQuality: SourceQuality?,  // REMUX, BLURAY, WEB_DL, WEBRIP, HDTV, DVD, CAM
    val codec: String?,                 // avc, hevc, av1
    val hdr: Set<HdrFlag>,              // HDR10, HDR10Plus, DV
    val audioFormat: String?,           // atmos, truehd, dts-hd, eac3, aac, opus
    val audioChannels: String?,         // 5.1, 7.1, 2.0
    val languages: Set<String>,         // ISO 639-1, from flags or words in the title
    val isDualAudio: Boolean,           // "Dual Audio", "Multi-Audio"
    val isDubbed: Boolean,              // "Dubbed", "DUB", "English Dub"
    val releaseGroup: String?,
    val seeders: Int?,
    val sizeBytes: Long?,
    val indexer: String?,
)
```

`StreamTags` is derived, best-effort, and **must never be the only thing shown**. The raw release name
stays visible on every row, because parsers are wrong sometimes and the person choosing a source often
knows the group name better than any tag.

### Process-wide state

Invariant 6 in `CLAUDE.md` closes the list of process-wide repositories at seven and demands the same
justification for an eighth. Two qualify here, and only two:

- **`TvWatchlistRepository`** - added from the detail screen, from a long-press in a shelf, and from
  search, and read by TV Library and TV Home's watchlist row, which hold their own instances. Exactly
  the "a write taken on one surface must be visible on another" test.
- **`TvProgressRepository`** - written by the player (an overlay), read by the Continue Watching row and
  the episode list underneath it, which are a different ViewModel entirely. Same test.

`AddonRepository` does **not** qualify. Addon changes happen on one screen and can be re-read on resume;
making it process-wide buys nothing that a fresh read at decision time does not, which is the existing
rule for settings (invariant 7).

### Network discipline

The project's constraint is frugality per user action. Concretely:

- **Home is N catalog calls, one per shelf, and nothing else.** Because Cinemeta returns full metas, the
  hero, the poster shelves, and the first paint of any detail page all come from those. No per-item
  `/meta` on the home screen.
- **Opening a detail page is one `/meta` call**, and only for `series` / `anime` (a movie already has
  everything). It is skipped entirely if the catalog object carried `videos`.
- **Opening the source sheet is a fan-out**, one request per enabled stream addon, run concurrently with
  a ceiling. Reuse the existing `FEED_CONCURRENCY = 6` value and the reasoning behind it; unbounded
  fan-out over ten addons on a pull-to-refresh is the same mistake `getLocalSubscriptionsFeed` already
  avoided.
- **Addon responses carry `cacheMaxAge`.** These are GETs, so the existing companion-level OkHttp disk
  `Cache` serves them, and an explicit refresh passes `forceFresh`. This is the first genuinely
  cacheable feed in the app - InnerTube calls are POSTs and never were.
- **Nothing here goes through `YouTubeRateLimit`.** That hold exists for a specific 429 from
  googlevideo's neighbours and gates discretionary YouTube fan-out. A third-party addon timing out is a
  different failure with a different answer: drop that addon from this query, keep the rest.

---

## 5. Sub and dub: the thing every app gets wrong

There are **two unrelated mechanisms** that both get called "dub", and conflating them is why this is
usually bad.

**Mechanism A - dub is a different file.** The anime scraper world works this way. CloudStream models it
as `episodes: Map<DubStatus, List<Episode>>`: choosing Dubbed replaces the entire episode list, because
the dubbed release has its own episode numbering, its own air dates, and usually fewer episodes than the
subbed one. In the addon world this shows up as separate streams whose release names say `Dual Audio`,
`[Dub]`, `English Dub`, or carry a set of language flags. Torrentio's anime results are exactly this:
sub/dub is **not a structured field**, it is text in the release name [verified August 2026, probed on
`kitsu:46474`].

**Mechanism B - dub is an audio track inside one file.** A `Dual Audio` MKV has two audio tracks;
ExoPlayer exposes them as separate audio track groups and switching is `TrackSelectionParameters` with a
preferred audio language. Nothing needs re-fetching, nothing rebuffers.

Koda has **no audio track selection today** - `VideoQuality` carries a single `audioUrl`, because a
YouTube video has one audio language per stream. TV mode is where that stops being true.

### The design

**One control, two implementations, and the user never learns the difference.**

- The detail page carries a **Sub / Dub segmented control** *only when the choice changes what is listed*
  - that is, when the metadata source distinguishes them, or when the stream list contains both dubbed
  and non-dubbed releases. Selecting Dub re-ranks and filters the stream list; it does not open a dialog.
- The player carries an **Audio** entry in the same sheet family as Quality and Captions, listing the
  real audio tracks in the playing file by language name plus channel layout ("Japanese 5.1",
  "English 2.0"). When there is one track, the row is not shown at all.
- **`ThemePreferences.preferredAudioLanguage`** (a list, ordered) is applied at both levels: it ranks
  streams in the sheet and it is the `setPreferredAudioLanguage` seed for the player. So someone who
  always wants Japanese sets it once and never touches either control again.

[judgement] The Sub/Dub control is a segmented button rather than a chip row or a menu, because it is
binary-to-ternary (`Sub`, `Dub`, and `Any` when both are wanted), it is chosen before playing rather
than during, and a segmented control is the one M3 component that reads as "this is a mode you are in"
rather than "this is a filter you applied".

**Failure mode to design for now:** DTS, DTS-HD MA and TrueHD are common in high-quality releases and are
frequently not decodable on Android, which produces video with silence and no error. Detect unsupported
audio codecs at track-selection time and (a) prefer a supported track automatically, and (b) if none is
supported, say so in the player as a one-line notice with the option to pick another source - rather
than leaving someone staring at a silent film.

---

## 6. Quality: three ladders, not one

Koda's `VideoQuality` today is a label-and-URL ladder from one provider. TV mode has three different
things people call quality and they need three different controls.

**1. Which file.** 2160p REMUX vs 1080p WEB-DL vs 720p. This is chosen in the **source sheet** before
playback, because it is a different file with a different size, and it is where HDR, codec, audio format
and release group live. This is the ladder that matters most and it is the one every other app presents
worst.

**2. Which rendition inside the file.** If the resolved stream is an HLS or DASH manifest, the existing
quality menu applies unchanged, and `applyLiveQualityCap`'s existing approach - cap via
`setMaxVideoSize` plus `setForceHighestSupportedBitrate` rather than swapping the media source - is the
right one here too. Direct MKV / MP4 files have exactly one rendition, so this menu must **hide itself**
rather than show a one-item list.

**3. What to auto-pick.** A stored preference profile, per network, reusing the shape of
`getDefaultVideoQuality()`'s Wi-Fi / mobile split: max resolution, max file size, allowed source
qualities, HDR preference, preferred audio languages, preferred release groups. This is what makes
`bingeGroup` binge-watching actually work.

**HDR is a live conflict with an existing decision.** `CLAUDE.md` states HDR is intentionally unsupported
because the previous path was not reliable. The TV catalogue is full of HDR10 and Dolby Vision releases,
and playing a DV Profile 5 file on a device that cannot handle it produces green-and-purple video, not a
graceful fallback. [judgement] The v1 answer is: **HDR and DV releases are parsed, labelled, and
de-prioritised by default in auto-select**, with an explicit "Allow HDR / Dolby Vision sources" toggle in
TV playback settings that is off by default and carries a one-line honest warning. That keeps the
existing decision intact while not pretending 40% of the catalogue does not exist.

---

## 7. The interface

### 7.1 The mode switcher

`MusicVideoToggle` is a 44dp pill with two 38dp segments and a thumb that stretches between them by
running two `Animatable`s at different spring stiffnesses (`fastEdge`, `slowEdge`). The thumb's position
is `segmentWidth * start`, its width `segmentWidth * (1 + end - start)`.

**Generalising to three is nearly free.** The two `Animatable`s become `0f..2f` instead of `0f..1f` and
the same min/max clamp produces the same liquid stretch across any number of segments. The motion
identity survives intact.

**Width is the real constraint, and it does not survive naively.** Today's top bar is avatar (44) +
downloads (44) + settings (44) + toggle (84), plus gaps - around 240dp. A 320dp phone at
`UI_SCALE_MAX = 1.15f` has roughly 278dp of usable width. A third 38dp segment lands at 278dp needed
against 278dp available: zero headroom, which means it clips on the smallest supported device at the
largest supported scale.

[judgement] **Segments shrink from 38dp to 32dp when three modes are shown** (a 96dp pill instead of
84dp, net +20dp), which restores roughly 18dp of headroom in the worst case. Verify this by rendering at
320dp x 1.15 before writing anything else in this section; if it still does not fit, the fallback is a
single 44dp mode button opening a small mode sheet - one tap worse, but scalable, and it frees 60dp.

**Do not** put TV behind a long-press on the existing toggle. A mode that is only reachable by a gesture
nobody is told about is a mode most users never find, which is the same mistake the curated onboarding
player-style pair made and that was already reversed once.

### 7.2 `videoMode: Boolean` becomes `AppMode`

```kotlin
enum class AppMode { MUSIC, VIDEO, TV }
```

This is the largest mechanical change in the project and it touches 117 call sites across 13 files
[drifts]. It is also unavoidable: a boolean cannot express three states, and adding a second boolean
gives four states, one of which is meaningless.

Specific things that break, and how:

- **Persisted value.** Invariant 9 freezes persisted enum constants: `AppMode` is stored by `name`, so
  `MUSIC` / `VIDEO` / `TV` are chosen once and never renamed. Migration reads the legacy `video_mode`
  boolean once, writes `app_mode`, and leaves the old key alone - a downgrade should not strand anyone.
- **`getLastHomeTab(videoMode: Boolean)` / `setLastHomeTab`** currently branch on a boolean into two keys
  with two different "last valid tab" ceilings. They become mode-keyed with three.
- **`SettingsScreen`'s signature** is a stated contract with `MainActivity` (invariant 8): parameters are
  added, never reordered. `videoMode: Boolean` cannot be changed in place; add `appMode: AppMode` at the
  end of the list and remove the boolean in the same commit.
- **`HomeScreen`'s tab `when`** gains a third branch per tab, in the shape Spotlight already established.
- **`MusicVideoToggle`** is renamed `AppModeToggle` (a file rename, not a behaviour change), and
  `MusicVideoToggleState` with it.
- **`DiagnosticsCollector`** reports the mode; it should print the enum name rather than "video: true".
- **Onboarding** currently offers a `videoMode` switch on the Look and feel page. It becomes a three-way
  choice, and TV needs a one-line honest description there ("Movies and series from sources you add") so
  nobody turns it on expecting a catalogue that is not there.

This sweep is exactly the "wide but shallow" work `CLAUDE.md` says to delegate: the *decisions* above are
made here, the 117 mechanical edits are a `sonnet` subagent's job, and the diff is reviewed.

### 7.3 TV Home (tab 0)

Three tabs, matching music: **Home, Search, Library.** [judgement] A nav tab is for something visited
daily; an addon manager is visited twice a year, so it lives in Settings.

Home, top to bottom:

**Hero.** One full-bleed card at the top: `background` art, the item's `logo` PNG over it (not its name
set in type - the logo is what makes this read as a movie surface, and Cinemeta supplies one for 100% of
catalog items), a one-line meta row (year, runtime, rating), and two actions: Play and Add to watchlist.
It rotates through the first few items of the leading catalog. Auto-advance is off by default: a hero
that moves while you are reading it is the single most complained-about pattern in this shape of screen.
[judgement] Swipe to change, with a small page indicator.

The one thing to get right: the logo PNG is a transparent asset of unpredictable aspect ratio and
luminance. Constrain it by height, cap its width, and put a bottom-up scrim under it, because a white
logo on a bright still is unreadable and that is not hypothetical - it is most animated films.

**Continue watching.** Landscape cards with a determinate progress bar, an episode label ("S2 E4 -
Title") for series, and long-press to remove. This row is first because it is the row people actually
came for, and it is empty on a fresh install, which is why the hero sits above it rather than below.

**Catalog shelves, one per catalog per installed addon**, in addon order. Poster cards at the aspect
`posterShape` declares (`poster` 1:0.675 by default, `square`, `landscape`), horizontally scrolling, with
the shelf title from the catalog's `name` and the addon's name as a small trailing label when more than
one addon is installed.

**Genre chips come from the manifest, not from a hardcoded list.** Cinemeta's `top` catalogs declare
19-22 genre options each; Kitsu declares its own. This is the same principle as the channel page in
`ui/channel/`, where the tab row is built from the response because a fixed list draws empty tabs for
some creators and hides real ones for others. Do not hardcode genres.

**Paging** is `skip=` in `extraArgs`, 50 items per page for Cinemeta [verified August 2026]. Load-more on
horizontal scroll-end, the same trigger shape the video feed already uses.

Empty and unconfigured states are in section 8, and they are not an afterthought.

### 7.4 TV Search (tab 1)

Search reuses `SearchField` and the existing search-history plumbing. The differences:

- The query fans out to every installed catalog addon whose catalogs declare `search` in `extra`
  (Cinemeta's `top` and Kitsu's `kitsu-anime-list` both do). Results group by **type** (Movies, Series,
  Anime) rather than by addon, because nobody searching for "Frieren" cares which addon answered.
- **Deduplicate across addons by id, then by normalised title plus year.** Kitsu returns `imdb_id`
  alongside `kitsu:` ids [verified August 2026], which makes the first pass exact for anime that also
  exists on IMDb. Prefer the anime-native id when both exist, because it is the one the anime stream
  addons are keyed on.
- An empty query shows recent searches and, [judgement], a compact "Browse by genre" grid built from the
  manifests' declared genres - the one thing this surface can offer that costs no network.
- Following the existing house rule: **search results are never filtered by a blocklist**, and the adult
  filter suppresses adult *catalogs* from browse rather than hiding typed results. Typing a title is
  explicit intent.

### 7.5 The detail screen

A `NavHost` route (`tv/{type}/{id}`), not in-tab state, because it is entered from Home, Search, Library,
the player's "more like this", and from a deep link, and because back must unwind through several of
them. Both overlays step aside the way they do for `channel/{channelId}` - the video player drops to its
mini bar, Shorts closes.

One `LazyColumn`. Sections in order:

1. **Backdrop and identity.** `background` art with a scrim, `logo` over it, falling back to the name in
   `displayLarge` when no logo exists. Poster inset at the leading edge.
2. **Meta row.** Year, runtime, IMDb rating, content rating, and genres as non-interactive chips.
3. **Primary action.** One button: **Play** if nothing is watched, **Resume** with the position and a
   progress bar if something is, **Play S1 E1** for an unstarted series. Beside it: Watchlist (toggle),
   Download, Share, Trailer.
4. **Trailer, and this is Koda's unfair advantage.** Cinemeta hands back
   `trailers: [{source: "<youtubeId>", type: "Trailer"}]` for 49 of 50 catalog items [verified August
   2026]. Koda already resolves and plays YouTube video. **A trailer plays in Koda's own player, inline,
   with no addon and no external app** - something Stremio itself needs a separate addon for. This is
   nearly free (`playVideo(id)` on the existing video ViewModel) and it is the most visible thing that
   says this mode belongs in *this* app rather than being a worse copy of another one.
5. **Synopsis**, expandable, three lines collapsed.
6. **For series and anime: the episode block.** Season selector, then **episode range chips** ("1-50",
   "51-100") whenever the season exceeds a threshold - CloudStream needs these for One Piece and so will
   this. Then the episode list: thumbnail (`episodes.metahub.space` supplies real ones), number and
   title, air date, overview truncated to two lines, a watched checkmark and a resume bar. Long-press for
   mark-watched / mark-unwatched-from-here / download.
7. **Sub / Dub segmented control**, above the episode list, only when it changes what is listed
   (section 5).
8. **Cast.** Cinemeta gives names, not photos. [judgement] Render as a chip row of names rather than a
   fake avatar row with initials - a row of grey circles is worse than no row. If a TMDB key is ever
   added as an optional user setting, this becomes the real cast row and nothing else changes.
9. **More like this.** From `meta.links` where `category` is a genre, or from the catalog, whichever the
   addon provides.

**Failure to design for:** a series `meta` can be large (Breaking Bad is 67 videos; a long-running anime
is 1,000+). Parse into seasons once, key the lazy list by episode id, and never build the whole flat list
inside a composable.

### 7.6 The source sheet

**This is where TV mode is won or lost.** Every competing app dumps 67 rows of raw release names into a
scroll and calls it a feature. Koda already knows what happens to a bottom sheet that gets this wrong -
`ui/video/VideoOptionsSheet.kt` exists in its two-pane form precisely because a single-column sheet laid
1,100dp of content into 890dp of space and clipped it silently, identically at zero items and at three
hundred [scar]. **The source list must be a `LazyColumn` from the first commit**, with the header and the
auto-play action pinned outside it.

Structure:

**Pinned top: the auto pick.** One card, visually the hero of the sheet: "1080p WEB-DL - HEVC - English
5.1 - 2.1 GB", the addon name as a small label, and a single line saying *why* it was picked ("Best match
for your Wi-Fi profile"). Tapping it plays. This is the only interaction 90% of people should ever need.

**Then a filter row**, horizontally scrolling chips: resolution, HDR, audio language, source quality.
Chips reflect what is actually present in *this* result set, not a fixed vocabulary - a set of chips that
filters to zero results is a broken control.

**Then the list**, grouped by resolution with sticky section headers (4K, 1080p, 720p, Other). Each row:

```
[2160p] [REMUX] [DV | HDR10] [Atmos 7.1]            38.4 GB
Movie.Title.2010.2160p.UHD.BluRay.REMUX.DV.HDR10.TrueHD.7.1-GROUP
Torrentio - 1337x - 284 seeders                       [cached]
```

Rules that make this readable rather than dense:

- **Badges are derived; the release name is raw and always present.** The parser is best-effort, the name
  is ground truth, and experienced users read it directly.
- **`[cached]`** or an equivalent instant-availability marker when the addon signals it, because on a
  debrid setup that is the difference between playing now and waiting.
- **A row with only an `infoHash` is shown, dimmed, and not playable in v1.** Tapping it explains in one
  sentence that a debrid service or a torrent-capable player is needed, and offers to open TV settings.
  Hiding them would make TV mode look empty on a working addon, which is worse.
- **Long-press a row** to make its `bingeGroup` sticky for this series, or to copy the release name.
- **The sheet remembers the last-used `bingeGroup` per series** and, on the next episode, auto-plays from
  it without opening at all. That is the entire point of binge-watching and the protocol hands it to us.

### 7.7 Player changes

The player is mostly already right. What TV mode adds:

- **An Audio track row** in the settings sheet, beside Quality and Captions (section 5). Hidden when
  there is one track.
- **Subtitles from three places, merged into one list**: tracks embedded in the file, `stream.subtitles`
  from the chosen stream, and a `/subtitles` fan-out to installed subtitle addons (OpenSubtitles v3
  returned 90 tracks for one film [verified August 2026]). Group by language, collapse duplicates by
  release name, and mark the source. Koda's existing caption sideloading path (`SingleSampleMediaSource`
  merged in, plus the `TextRenderer.experimentalSetLegacyDecodingEnabled` renderers factory) works
  unchanged for `.srt` and `.vtt`.
- **Subtitle styling needs to grow.** Koda has a caption text-size slider. TV audiences expect size,
  colour, background or outline, and vertical position, because half these subtitles are for a 20-year-old
  release with hardcoded styling. This is also the honest place to note that Media3's ASS/SSA support is
  styling-free: complex typeset anime subs render as plain text, and that should be said in the picker
  rather than discovered.
- **Next-episode autoplay** with a countdown card in the last 20 seconds, a "Play now" and a "Cancel",
  reusing the queue advance path `VideoQueue` already has. The queue for a series is the episode list
  from the current position; the same "addressed by index, never by id" rule applies, for the same reason.
- **Resume is written per episode**, checkpointed on the same 15-second cadence music sessions already
  use, and on backgrounding.
- **No seek preview.** There are no storyboards here. `VideoSeekPreview` degrades to the plain scrubber
  rather than showing an empty frame box.
- **Per-stream headers.** `behaviorHints.proxyHeaders.request` must be attached to the media requests.
  `ChunkedStreamDataSource` correctly no-ops for non-googlevideo hosts (`shouldChunk` returns false unless
  the host ends in `.googlevideo.com`), so TV streams fall through to the default HTTP path - which today
  sets a User-Agent derived from the URL's `?c=` query param and nothing else. TV playback needs a data
  source factory built per playback carrying that stream's headers, the way CloudStream does it.

### 7.8 TV Library (tab 2)

Four sections, all device-local, all working with no account and no addon:

- **Continue watching**, the full list rather than the home row's slice.
- **Watchlist**, a poster grid, sortable and filterable by type.
- **Downloads**, filtered to TV items, reusing `DownloadRepository`. [judgement] v1 downloads handle only
  a resolved single-file HTTP `url`, which `downloadStream`'s existing ranged loop already does; anything
  else is not offered, rather than offered and failing.
- **Watched history**, with a clear action.

Trakt or Simkl sync is deliberately out of v1: both need a registered client id, which the project's
constraints push against, and neither is worth blocking a first release on.

### 7.9 Settings

One new hub row, `TV`, opening a `SettingsPage.TV` with four sections, plus a dedicated
`ui/settings/AddonSettings.kt` for the manager - the same precedent `DisplaySizeSettings.kt` set for "a
page big enough to own a file gets one".

**Addons** (its own page, reached from the TV page):

- Installed list, drag to reorder. **Order is priority**: it decides shelf order on Home and result order
  in the source sheet. Reuse `QueueReorder`, which lives in `ui/components/` precisely so non-player
  screens can use it.
- Per-addon toggles for each resource it provides, so a metadata addon can be kept for catalogs while its
  streams are ignored.
- **Add by URL**, accepting `https://.../manifest.json` and `stremio://.../manifest.json`.
- **Browse**, listing the 7 official and 95 community addons from Cinemeta's own `addon_catalog`
  [verified August 2026] - no hardcoded directory, and it stays current on its own.
- **Configure**, which opens the addon's `/configure` page in a WebView and intercepts the resulting
  install URL. Koda already has this exact pattern in `YouTubeAuthDialog`. This is how a debrid key
  reaches Torrentio without Koda ever handling the credential itself.

**Playback**: the auto-select profile per network (max resolution, max size, preferred source qualities,
preferred audio languages, allow HDR/DV), and whether to auto-play the next episode.

**Subtitles**: preferred languages in order, auto-enable, and the styling controls.

**Content**: the adult-catalog filter (on by default), and whether to hide unplayable torrent-only
sources.

Every one of these threads through the five files in section 12 of `CLAUDE.md` **and**
`buildSettingsSearchIndex`. That last one has no compile error attached to it and is the one omission in
this whole plan that will be discovered by a user rather than by the compiler.

Every hub row shows its live value: "TV" shows "3 addons, 1 source" or "No sources yet".

---

## 8. States

The house rule is that these are handled in the first pass, not retrofitted. For TV mode the
unconfigured state is not an edge case - it is what **every** user sees on day one.

| State | What it must do |
| --- | --- |
| **No stream addons installed** (the default) | Home is fully populated from the three preinstalled metadata addons and looks alive. One dismissible card sits under the hero: "You can browse everything. To play, add a source." with **Browse addons** and **I have a debrid account**. The detail page's Play button is present and opens the source sheet, which explains the same thing once. Nothing anywhere says "empty". |
| **Addons installed, zero streams for this title** | "No sources found for this. Try another addon, or check your filters." Name which addons were asked and which failed - "no results" and "three addons timed out" are different problems with different fixes. |
| **Some addons failed** | Show the results that arrived. A quiet inline line names the failures with a retry. Never block good results on a bad addon. |
| **All results are torrent-only** | Show them dimmed with the one-sentence debrid explanation, not an empty list. |
| **Offline** | Continue watching, watchlist and downloads all render from disk. Shelves show the last cached page with a stale marker rather than a spinner. |
| **Loading** | `Skeleton` poster shelves, matching the existing skeleton vocabulary. Never a full-screen spinner over a screen that has cached content. |
| **Long titles** | Two lines then ellipsis on cards, one line in the source sheet's badge row. Anime titles are routinely 60+ characters and the romaji / English pair doubles that. |
| **No artwork** | Poster and background both fail often on community addons. Fall back to a generated tonal card seeded from the title, the way `playlistCoverSeeds` already generates covers. Do not draw a broken-image glyph. |
| **1,000+ episodes** | Range chips (section 7.5). A picker with 20 seasons is a scrollable menu, not a row. |
| **A single episode, or a movie** | No season picker, no range chips, no Sub/Dub control unless it applies. Every optional control hides itself. |
| **RTL and 26 locales** | The source sheet's badge row is the risk: it mixes latin release names with localised labels. Badges are localised; the release name is not and should be forced LTR. |
| **Rotation and PiP mid-source-sheet** | The sheet's state survives rotation; entering PiP closes it. |

---

## 9. What this costs, and in what order

Rough shape, not a schedule. Sizes use the `ROADMAP.md` scale.

**Phase 1 - the sweep.** `AppMode` enum, the 117-site migration, the three-segment toggle, a TV tab set
that renders an honest placeholder. Nothing about content. This lands on its own and breaks nothing, and
it is the only phase that touches existing files at scale. `L`.

**Phase 2 - browse.** `data/tv/`, the addon client, the three preinstalled addons, TV Home with hero and
shelves, TV Search, the detail screen without playback, the watchlist. **At the end of this phase the
mode is genuinely useful with zero configuration**: browse, search, watchlist, and trailers that play in
Koda's own player. `XL`.

**Phase 3 - playback.** `TvPlayerViewModel` behind the shared overlay, the source sheet, the release
parser, per-stream headers, resume and progress, next-episode autoplay. `XL`. **Landed, with one
deviation:** the player is its own full-screen composable rather than a discriminator inside
`VideoPlayerOverlay` - see the revision note under section 3. Audio-track selection was pulled
forward from phase 4, because it is half of what "dub" means and it is sixty lines once the player
exists.

**Phase 4 - the choices.** Merged subtitles, subtitle styling, auto-select profiles of their own,
downloads. `L`. (Audio-track selection and `bingeGroup` stickiness landed in phase 3.)

**Phase 5 - the manager.** Addon browse / configure / reorder UI, per-resource toggles, adult filter.
`M`.

Deliberately later: Trakt / Simkl sync; IPTV and live TV (the protocol's `tv` type is designed for it and
the models here should not exclude it, but nothing in v1 should be shaped around it); a torrent engine;
Android TV leanback.

**Strings.** Every phase adds strings across 26 locale directories [drifts]. Decide the keys and the
English here; delegate the 25-file propagation to a `sonnet` subagent, per the house rule.

---

## 10. Risk, stated plainly

**Naming collides with an existing roadmap item.** `ROADMAP.md` has an "Android TV" entry meaning
leanback support on a television. This mode means movies and series on a phone. They are unrelated and
both are called TV. Recommendation: keep "TV" as the user-facing name the request asked for, use `ui/tv/`
and `data/tv/` in code, and rename the roadmap entry to "Android TV (leanback)" in the same commit that
lands phase 1. Two things called TV in one repository will cost someone an afternoon otherwise.

**Distribution.** Under this architecture Koda ships no scrapers, no indexers and no default source - the
same posture as Stremio's own client, or a browser. That is a real and defensible distinction. It is also
not a guarantee: apps in this category have been targeted on GitHub regardless of architecture, and that
is worth knowing before phase 3 rather than after. It is a judgement about the project's exposure, and it
is the maintainer's to make, not this document's.

**Credentials.** A debrid key arrives inside an addon URL. That makes the addon list a credential store:
`EncryptedSharedPreferences`, excluded from `BackupRepository`'s allowlists under exactly the rule that
already excludes session cookies, and never logged - including in `DiagnosticsCollector`, which currently
prints preference state and would happily print a Real-Debrid key into a bug report.

**Adult content.** A meaningful share of community addons carry NSFW catalogs. The filter is on by
default and lives in TV settings, not buried.

**The largest technical risk is not the addon layer, it is the player.** Arbitrary MKVs with DTS audio,
ASS subtitles and Dolby Vision are a fundamentally harder playback problem than YouTube's uniform,
pre-transcoded streams, and Media3's gaps there are real and known. Sections 5, 6 and 7.7 each name a
specific failure that produces silence, wrong colours, or unstyled text rather than an error. Building
phase 3 without those three answers already designed is how this ships as "sometimes the video is silent".

---

## 11. Decisions this document made, that you may want to make differently

1. **Addon client, not scrapers.** Section 3. The strongest call here, and the one everything else rests
   on.
2. **One playback overlay, two view models.** Section 3.
3. **Three tabs; addons live in Settings.** Section 7.3.
4. **A rotating hero at the top of Home**, rather than straight into shelves. Section 7.3.
5. **32dp toggle segments** rather than a mode sheet. Section 7.1 - verify against a 320dp screen at 1.15
   scale before committing.
6. **HDR sources parsed and de-prioritised behind an off-by-default toggle**, rather than filtered out
   entirely or allowed freely. Section 6.
7. **Torrent-only results shown dimmed** rather than hidden. Section 7.6.
8. **Cast as a name chip row**, not avatars. Section 7.5.
9. **No Trakt / Simkl in v1.** Section 7.8.

---

## 12. Invariants this adds

To be folded into `CLAUDE.md` when phase 1 lands, not after.

1. **`AppMode` constants are persisted by `name` and are frozen.** Renaming one resets every user's mode.
2. **Nothing in `data/tv/` imports `YouTubeRepository`.** The two data layers share an OkHttp client and
   nothing else. The one deliberate crossing is trailers, which go through the player, not the repository.
3. **A stream's parsed tags are never the only thing displayed.** The raw release name is always present.
4. **The source list is a `LazyColumn`.** A bottom sheet whose content does not scroll has a silent hard
   ceiling, and this list is unbounded by construction.
5. **Addon URLs are credentials.** Encrypted at rest, excluded from backup, never logged.
6. **Genres, catalogs and content types come from the manifest.** Do not hardcode a list an addon already
   declares.
7. **A failing addon never blocks the addons that answered.**
