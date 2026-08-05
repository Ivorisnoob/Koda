# Koda Roadmap

This document is the long view: where Koda stands today, where it is going, and the reasoning behind each direction. It is deliberately not a task list. Individual work items live in [GitHub issues](https://github.com/Ivorisnoob/Koda/issues); what belongs here is the shape of the app a year out and the constraints any new feature has to survive.

For how the app is built, see [`CLAUDE.md`](CLAUDE.md). For the design system and why it is not a swappable layer, see [`DESIGN.md`](DESIGN.md).

---

## Where Koda is today

Version **4.3** (`versionCode` 21), targeting Android 16 (API 36) with a floor at Android 11 (API 30). Roughly **69,000 lines** of Kotlin across **135 files**, all of it Compose, all of it rendered inside a single `MaterialExpressiveTheme`.

The app is past the point of proving itself. The core loops all work end to end:

**Two modes, one app.** A video toggle reshapes Home, Search, and Library between a full music player and a full video client. Both share the tab system, the overlays, and the theme; neither is a stripped-down version of the other.

**Two playback pipelines.** Music runs through `MusicService`, a Media3 `MediaLibraryService` with background playback, notifications, and a queue. Video owns its own `ExoPlayer` with DASH, PiP, chapters, captions, and hold-to-2x. Both fetch media through bounded ranged requests, because googlevideo throttles open-ended reads to roughly the media bitrate.

**No API keys, and no mandatory account.** Everything comes from NewPipe Extractor and direct InnerTube calls. Search, streaming, downloads, a local taste profile, subscriptions, and the "don't recommend" blocklist all work signed out. Signing in adds the real YouTube feeds on top rather than unlocking the app.

**Identity is plural.** Several YouTube accounts and device-only local profiles sit side by side, switchable with one preference write and no re-authentication, no network, and no interruption to playback.

**The interface is the product.** Eight fully animated player styles, 27 color palettes plus wallpaper-based dynamic color and AMOLED, a settings hub of eleven pages with full-text search, and spring physics on anything touch-driven.

Where the weight sits today:

| Area | Lines | What lives there |
| --- | --- | --- |
| `ui/video` | 14,800 | Video player, live streams, live chat, subscriptions |
| `ui/player` | 11,200 | Eight music player styles |
| `data/YouTubeRepository.kt` | 6,500 | The InnerTube and NewPipe layer, single file |
| `ui/settings` | 5,900 | Hub, eleven pages, search index |
| `ui/home` | 3,500 | Both modes' feeds |
| `ui/library` | 3,400 | Playlists, liked songs, local audio |

That table is also a map of the risk. `YouTubeRepository.kt` is the single point of failure for every network path in the app, and the two largest UI areas are the two youngest.

---

## The constraints every item here inherits

These are not goals. They are the walls the roadmap has to fit inside, and any proposal that breaks one is a different app.

**No official API keys, ever.** InnerTube shapes drift and renderers get renamed wholesale. Every parser is probe-first, dated in its KDoc, and written to fail into an empty result rather than a crash. Anything that would require a Google API key, a billing account, or a developer registration is out of scope by definition.

**The signed-out path is a first-class path, not a degraded one.** Roughly half the userbase never signs in. A feature that only works with an account needs a device-local answer as well, or it needs to be honest that it is account-only in the same breath it is offered.

**Material 3 Expressive is structural.** 39 files use Expressive-only APIs. An alternate or "classic" design language is a rewrite of 37,500 lines of UI, and the project has a stated policy against it. Specific complaints — a radius, a nav-bar dimension, one animation — are ordinary work and always welcome.

**Minimum SDK 30, and desugaring is load-bearing.** NewPipe calls Java 10/11 APIs that only landed in the platform at API 33. Anything gated above 30 needs a `Build.VERSION.SDK_INT` guard and a working fallback, not a graceful degradation to nothing.

**Frugal on network per user action.** One `/next` response already yields engagement, metadata, related videos, and the comments token. New features parse from responses the app is already fetching before they earn a request of their own.

---

## Known defects

Not roadmap items. These are things that are wrong today, recorded here because they were raised alongside the planning and because each has a diagnosed cause rather than a suspicion.

**Vertical videos are pillarboxed on the watch page.** Open a Short from watch history — or any 9:16 upload that is not a live broadcast — and it plays inside the standard 16:9 player box with black bars down both sides, using a fraction of the screen.

The cause is a single condition. The app already detects portrait video correctly and in two independent ways: `VideoQuality.isPortrait` is derived at parse time from the largest dimensioned format on both the InnerTube and NewPipe paths, and `onVideoSizeChanged` backstops it from the first frame. Both feed `VideoPlayerViewModel.isPortraitVideo`, which is accurate for every video. But the only consumer is `VideoPlayerContent.kt:152`:

```kotlin
val verticalLiveAvailable = isLive && isPortraitVideo
```

The vertical treatment was built for live streams and gated behind `isLive`, so a portrait signal that is already correct for every video is discarded for all of them except broadcasts. Nothing needs detecting; an existing, working signal is simply being ignored.

What it should do instead is a genuine design decision rather than a one-line flip, which is why it is worth writing down rather than patching blind. Three candidate behaviours, in the order they seem right:

- **Let the player box take the video's aspect ratio** on the standard watch page, capped at some maximum height, with the title, actions, and comments below it as normal. This is what YouTube itself does for vertical uploads that are not Shorts, and it keeps the whole watch page — description, comments, related — reachable.
- **Reuse the vertical chrome** from `VerticalLivePlayerContent` without the chat ticker. Full-bleed and immersive, closest to how the video was meant to be seen, but it hides the watch page behind a gesture.
- **Hand off to the Shorts overlay**, which is wrong: Shorts is opt-in and is a feed, and a video opened deliberately from history is not a feed.

The first is the recommended default, since it degrades gracefully for the awkward middle ratios — 4:5 and 1:1 uploads are common and are not "vertical" in the Shorts sense. The existing `MAX_ACCEPTABLE_CROP` logic in the vertical live player already encodes that judgement and can be borrowed.

**Two loading indicators on first load.** Several screens show the pull-to-refresh spinner and a centered `LoadingIndicator` at the same time while loading into an empty list, so the screen has two spinners running at once.

The fix already exists in the codebase and simply was not applied everywhere. `VideoHomeContent.kt:193` gets it right:

```kotlin
isRefreshing = isLoading && videos.isNotEmpty(),
```

Pairing that with an inner indicator guarded on `isLoading && videos.isEmpty()` makes the two states mutually exclusive: the centered indicator owns the first load, the pull-to-refresh spinner owns every refresh after it. Every screen below already has the inner guard written correctly; only the `isRefreshing` binding was left bare, so the two overlap on exactly the first load.

A sweep of every `isRefreshing` binding in the app found five unguarded sites across four files:

| File | Line | Binding | Inner indicator |
| --- | --- | --- | --- |
| `SubscriptionsContent.kt` | 334 | `isChannelsLoading` | `:387`, guarded on `channels.isEmpty()` |
| `SubscriptionsContent.kt` | 425 | `isFeedLoading` | `:620`, guarded on `feed.isEmpty()` |
| `VideoHistoryScreen.kt` | 137 | `isHistoryLoading` | `:168`, guarded on `historyVideos.isEmpty()` |
| `VideoLibraryScreen.kt` | 211 | `isPlaylistsLoading` | `:314`, guarded on `playlists.isEmpty()` |
| `LibraryScreen.kt` | 405 | `isLoading` | none found — needs checking before changing |

The first four are confirmed doubles with a mechanical fix. `LibraryScreen.kt:405` is unguarded in the same way, but no paired inner indicator turned up, so it may not double in practice and should be looked at rather than patched by pattern. Worth noting the fifth is in music mode, not video — the issue is slightly wider than the video screens where it was noticed.

Two screens already handle this correctly and are the reference: `VideoHomeContent.kt:193`, and `HomeScreen.kt:833`, which solves it a second way with `isRefreshing && !isInitialLoading`.

---

## Planned work

### Interface

#### Proper channel screens

A channel in Koda today is a bottom sheet. `ChannelSheet.kt` is 245 lines, and the model behind it, `ChannelProfile`, holds five fields: an id, a name, an avatar, a handle, and a subscriber count as pre-formatted text. Tapping a creator's name gets you their avatar and a Subscribe button, and `getChannelVideos` returns one flat list of uploads with no tabs, no sorting, and no sense of the channel as a place.

That is a gap you feel most at the moment of deciding whether to follow someone. The question "is this channel worth subscribing to" is answered by a banner, a description, an upload cadence, and what else they make — none of which the sheet can show. Everything about the current surface says "here is a name", when what is wanted is "here is a creator".

The target is the full page: banner art, the about text with links and join date, and real tabs — Videos, Shorts, Live, Playlists, Community, About — each with its own sort order and its own continuation paging, so a channel with two thousand uploads scrolls properly instead of ending after one page. Rendered in Koda's own language rather than YouTube's: Expressive shapes, the app's palette, spring physics on the tab switch, and the staggered entrance every other screen already uses.

**Much of this is already paid for.** The Subscribe button routes through `SubscriptionActions`, which means account subscriptions, device-local subscriptions, and the auto target all work on the new screen for free, signed in or out. "Don't recommend this channel" is already wired through `NotInterestedActions`. Sharing, the video grid components, and the long-press save sheet all exist. The work is genuinely the screen and the data behind it, not a new subsystem.

**Two things make it more than a layout job.** The first is that every tab is a different InnerTube browse `params` value, and those must be probed live rather than written from memory — this is exactly the case the probe-first workflow in `CLAUDE.md` exists for, and the Community tab in particular is a renderer family the app has never parsed. `ChannelProfile` will need to grow considerably; today it cannot even express a banner.

The second is a design question worth settling before any code: **`ArtistScreen.kt` already exists, at 1,458 lines, and it is good.** It is the music-mode view of what is often the same entity — a musician's YouTube channel and their YouTube Music artist page are one creator with two faces. Building a channel screen without deciding how it relates to the artist screen means two large screens that drift apart, each missing half of what the other knows. The likely answer is that they stay separate surfaces because their content genuinely differs, but share a header, a subscribe path, and a navigation entry, so that arriving at a musician from video mode and from music mode does not feel like arriving at two different people.

#### Per-channel notification settings

The bell, as YouTube has it: for each channel you follow, choose whether new uploads notify you for everything, only occasionally, or not at all. Today there is no bell anywhere, and no per-channel state to put behind one.

**The uncomfortable part is that the bell is an output control for something that does not run.** `getNotifications()` exists and reads YouTube's own inbox through `notification/get_notification_menu`, but it is a pull that only happens when the notifications sheet is opened, and it needs an account. Nothing in Koda checks for new uploads in the background, and nothing ever posts an Android notification. Adding a per-channel preference without that is building a volume knob with no speaker attached — the setting would be recorded and never consulted.

So this item really contains two, and the order matters: **a background upload check has to come first**, and the data for it already exists. `getLocalSubscriptionsFeed` merges each followed channel's Atom feed and is cheap by design, roughly 50 KB per channel against about 1 MB for the equivalent browse. A periodic `WorkManager` job over that feed, remembering the last-seen timestamp per channel, is the whole delivery mechanism. The per-channel preference then becomes the filter it consults.

**The two-store split shapes what the bell can honestly offer.** For an account subscription the preference can be written to YouTube, so it also affects notifications on youtube.com and in the official apps. For a device-local subscription there is no account to record it against, so it is a device preference driving a device-side check. YouTube's middle setting, "Personalized", is a server-side judgement about which uploads are worth surfacing — there is no local equivalent, and offering it signed out would be a lie. Signed out, the honest set is All or None; signed in, all three.

`SubscriptionGroup` already exists — user-defined bundles of local channels like "Music" or "Tech" — and it is the obvious place to set this in bulk rather than channel by channel. Someone following two hundred channels will not tap two hundred bells.

Two Android specifics worth deciding early. `POST_NOTIFICATIONS` is already declared but is a runtime permission from Android 13, and the request should come when the user first enables a bell rather than at startup, so it arrives with a reason attached. And notifications should share one Android notification channel for uploads with grouping, not one Android channel per YouTube channel — the latter looks tempting and becomes unusable at any real subscription count.

#### Saving other people's playlists and albums

There is no way to keep a playlist you did not make. You can play someone else's playlist and you can build your own from scratch, but the ordinary move — find a good playlist, save it, come back to it next week — has nowhere to land.

**The signed-in half is close to free.** Saving a playlist to your library on YouTube Music is a like on the playlist id, and `postPlaylistApi` with `like/like` and `like/removelike` is already wired for playlists in `YouTubeRepository`. `getUserPlaylists()` reads `FEplaylist_aggregation`, which already returns saved playlists alongside owned ones, so a saved playlist would appear in Library through the existing read path without a new call. This is mostly a button and an entry in the long-press sheet.

**The signed-out half is the real work, and the existing model is the wrong shape for it.** `UserPlaylist` embeds its full `Song` list, which is right for a playlist you own and wrong for one you saved: the owner keeps editing theirs, and a copy taken today is stale by next month. A saved playlist should be stored as a *reference* — id, title, author, artwork, saved-at — and re-fetched when opened, so it stays live. The temptation to reuse `UserPlaylist` because it is already there should be resisted; these are different things that happen to render similarly.

Offline is the exception that proves it. A reference cannot play without signal, so the answer is the download system that already exists rather than a snapshot baked into the save. Saving and downloading stay separate actions with separate meanings, the way they are everywhere else in the app.

**Albums need the same feature and are not the same object.** They browse by their own id rather than as playlists, so the save path has to handle both rather than assuming everything is a playlist with a `list=` in its URL.

In the Library, saved items want to be visibly distinct from owned ones. They cannot be renamed, reordered, or edited, and a user who cannot tell which is which will try. A separate section, or at minimum a clear marker plus a disabled edit affordance, avoids a whole category of confusion.

#### Discover: a simpler home for music mode

An alternative Home for music mode. Off by default, chosen in Settings, for people who find the current Home busy and want something that gets out of the way — still unmistakably this app, still Expressive, just fewer decisions per screen.

**The pattern already exists, which makes this cheaper than it sounds.** The video toggle already swaps Home's entire content through `AnimatedContent` while keeping the tab system, the overlays, and the nav bar untouched. A third variant of Home is an established move in this codebase rather than a new kind of thing. The setting itself is the usual five-file thread plus the settings search index, and the index is the one that fails silently if forgotten.

**The rule that keeps this from rotting: same data, different composition.** Discover should be a different arrangement of the flows `HomeViewModel` already exposes — recently played, liked songs, quick picks, user playlists, play counts — not a new ViewModel, not new fetches, not its own network path. The moment it owns data the current Home does not, there are two homes to keep working and one of them will quietly fall behind. Everything Discover shows should already be on screen somewhere today.

**What "simpler" should mean is worth deciding rather than discovering during implementation.** The strongest candidate is fewer, larger units and one clear thing to do — the current Home's density comes largely from horizontal rails nested inside a vertical scroll, which asks the user to navigate two axes at once. Removing that is most of the perceived simplification on its own. Bigger artwork, fewer rows, and a single obvious entry point ("pick up where you left off") is a different shape from the current screen without being a different design language.

**Simpler is not plainer.** The Expressive shapes, the palette, the springs, and the artwork colors all stay — this is not a "lite mode" or a flat theme, and it must not become the alternate design language `DESIGN.md` rules out. The reduction is in how many choices are presented at once, not in how the app looks.

Two states deserve more care here than on the main Home, because this screen is meant to be the calm one: signed out, where there is no account feed and the taste profile may be thin, and brand new, where there is no history at all. A simplified home that is mostly empty is worse than the busy one it replaced.

#### Playlists: creation, editing, and covers

Local playlists work, and they are plain. You can create, rename, reorder by drag, and delete. What is missing is everything that makes a playlist feel like yours.

**Custom cover art is closer than it looks.** `UserPlaylist` already carries a `coverUri` field, documented for exactly this — `file://` or `content://` — and `PlaylistRepository.updatePlaylist` already guards it correctly: it only regenerates the auto cover when the stored URI still points at the generated `cover_<id>.png`, so a user-set image would survive edits today. The data model is done. Nothing in the UI ever calls an image picker, so the field is unreachable. This is a picker, a crop, and a copy into app storage away from working, and it is the highest ratio of felt improvement to work anywhere in this list.

**The generated covers themselves deserve a pass.** `generateCoverArt` paints a 1000px linear gradient between two random HSV hues with the playlist's first letter in bold white on top. It is a reasonable Apple Music impression, but it is the one place in the app that hardcodes color and ignores the user's palette entirely — someone running a deliberate palette, or AMOLED, gets random vivid gradients that belong to no theme. Generated covers should be derived from the active `ColorScheme`, and ideally from the playlist's own contents, so a playlist of one artist picks up that artwork's colors the way the player already does.

Beyond covers, the editing experience is where the bump lands: a real creation flow rather than a name prompt, multi-select in lists so a playlist can be built from a selection in one action, duplicate detection, sort and shuffle-into-order, editable descriptions, and a proper empty state that offers a way to fill the playlist rather than reporting that it is empty.

**One structural note.** Playlists serialize to SharedPreferences with the full `Song` list embedded in each record, so every add rewrites the whole playlist. That is invisible at forty songs and will not be at four thousand. It does not need solving now, but a playlist feature set that encourages large playlists should not be built without knowing that is the storage underneath.

#### Hold-to-2x should take longer to trigger

The speed boost fires too eagerly. A press that was meant as a tap on the video — to bring the controls up — crosses the threshold and jumps playback to 2x instead, which is startling in a way a mis-tap should not be.

The reason there is no value to tune is that the app never sets one. `PlayerGestureSurface` boosts from the `onLongPress` callback of `detectTapGestures` (`VideoPlayerScreen.kt:1250`), and that fires on Compose's default long-press timeout, which is the platform's roughly half a second. Every other timing in that file is a named, documented constant — `DOUBLE_TAP_SEEK_SECONDS`, `LEVEL_HIDE_MS`, the pinch thresholds — and this one is the only interaction whose feel is inherited from a system default rather than chosen. It wants a `SPEED_BOOST_HOLD_MS` sitting in the same block as the others, deliberately longer than the platform figure.

One call site covers everything: all three surfaces — the inline player, fullscreen, and the vertical live player — route through the same composable, so this is tuned in one place.

**The implementation choice matters more than the number.** The obvious route is to keep `onLongPress` and start the boost from a delayed job instead, but that opens a dead zone: `detectTapGestures` still consumes the gesture as a long press at the system timeout, so a press landing between the two thresholds would neither toggle the controls nor boost. It would feel like the video stopped responding.

Overriding `LocalViewConfiguration.longPressTimeoutMillis` around the gesture surface avoids that, because it moves the tap and long-press boundaries together — below the threshold is cleanly a tap, above it is cleanly a boost, and there is no gap. The one thing to check is that the override does not leak into anything nested that uses its own long-press. The related-videos row does use `combinedClickable`, but it lives in `VideoInfoSection`, outside the gesture surface, so on current structure this looks clear.

Worth pairing with a look at whether the boost should be interruptible by the volume and brightness drags, which share the same surface.

#### Respect reduced motion

Koda animates more than almost anything in its category — roughly 97 spring animations, eight player styles built on motion, staggered entrances on every screen — and it reads nothing about whether the person using it wants that. There is no read of `Settings.Global.ANIMATOR_DURATION_SCALE` anywhere in the source, so a user who has turned animations off system-wide, whether for vestibular reasons or because they are on a slow device, still gets every spring and every stagger.

This matters more here than in a typical app precisely *because* the motion is so central. The bigger the motion design, the worse the experience for someone who cannot tolerate it, and "turn off animations" is a setting people reach for because something is making them ill.

The fix is a single source of truth — read the animation scale, expose it as a composition local or a theme value, and have the shared animation specs collapse to instant when it is zero. Because springs in this app are used through `spring()` calls scattered across the UI rather than through a shared motion vocabulary, the honest first step is probably introducing that vocabulary, which the design system would benefit from anyway.

Two things worth deciding rather than assuming: transitions that carry meaning — the player expanding, a sheet arriving — should probably become instant rather than disappearing, so the user still understands what happened. And the player styles whose entire identity is motion (Morph's cycling shape, Sticker's squash-and-stretch) need a defined still state rather than a broken one.

#### A listening history for music

Video mode has `VideoHistoryScreen`. Music has no equivalent surface. Play history exists and is tracked — `StatsRepository` builds charts, streaks, and top artists from it, and the taste profile in `RecommendationEngine` is derived from it — but there is nowhere to simply see what you listened to, in order, and play something again.

This is the asymmetry people notice fastest, because it is the same app in two modes and one of them forgot. It is also the cheapest item in this document: the data is already recorded and already aggregated, so this is a screen over an existing store rather than new plumbing.

Worth pairing with the same controls video history has — clear an entry, clear all, and a pause-history toggle, since `isSaveVideoHistoryEnabled()` already establishes that pattern on the video side.

#### Predictive back, which is currently paid for and switched off

The manifest sets `android:enableOnBackInvokedCallback="true"`, so Koda has opted into the modern back API. It then suppresses the result everywhere: there are **17 `BackHandler`s in the app and zero `PredictiveBackHandler`s**. A plain `BackHandler` consumes the gesture and gives the system nothing to preview, so on Android 14 and up the back-swipe animation — the one that peels the current screen away and shows what is behind it — never appears anywhere it would matter.

That is the worst of both arrangements. The opt-in is declared, so the platform stops applying its own compatibility behaviour, and nothing replaces it. Every sheet, the expanded player, the video overlay, the Shorts overlay and the settings page stack all swallow the gesture and then snap.

**The settings stack is the clearest case and the best place to start.** Back there already unwinds in defined steps — open page, then hub, then clear the search query, then leave — so the states are known and the animation has somewhere obvious to go. `PredictiveBackHandler` hands back a `Flow` of gesture progress, which is exactly the input the existing `AnimatedContent` transition wants.

**The overlays are the hard part and the reason to do this deliberately.** `ExpandablePlayer`, `VideoPlayerOverlay` and the Shorts overlay live above the `NavHost` rather than inside it, so they are not screens the system can peel back to reveal something. Their back gesture collapses a thing rather than popping a destination, and the preview has to be driven by hand from the progress flow into the same spring that already animates the collapse.

**The cancel path is what most implementations get wrong, and it is the entire point of the feature.** `PredictiveBackHandler` is a suspending handler that can be cancelled mid-gesture when the user changes their mind and slides back. If that path does not spring the UI cleanly back to where it was, predictive back is worse than no predictive back — the user gets a preview of leaving, decides not to, and lands somewhere broken. Every one of the 17 sites needs that case handled, not just the commit case.

#### Haptics, more than eight files

Haptic feedback appears in 8 of 135 source files, almost all of it in the video gesture surface. Everything else is silent: the player style wheel spins without detents, drag-to-reorder in playlists gives nothing when an item locks into place, likes and toggles have no confirmation, and the tab bar does not respond to the thumb.

For an app built this heavily on touch — roughly 97 springs, gesture-driven players, a spinnable style wheel — that is a whole sensory channel going unused, and it is the channel that makes physical-feeling UI feel physical.

**The existing use already demonstrates the right instinct, and it should be written down as a rule rather than repeated by memory.** The fullscreen swipes tick because they commit while the finger is still down with no dragged preview behind them, so the tick is the only confirmation the gesture took. Generalised: haptics belong on **commits the user cannot yet see** and on **detents and thresholds** — the wheel clicking to the next style, a reorder locking in, a drag passing the point where releasing will do something. They do not belong on every tap, which is how apps end up buzzing constantly and getting the feature switched off.

Worth routing through one small helper rather than calling `performHapticFeedback` ad hoc, so the vocabulary stays consistent and there is a single place to respect the system haptic setting.

#### Use the skeletons that already exist

`ui/components/Skeleton.kt` is written and includes a text-line placeholder sized to the type it replaces. Several screens do not use it and show a bare centered spinner instead: Subscriptions, video history, and video library.

Those are the same screens carrying the double-indicator defect recorded above, which makes this one pass rather than two. Replacing the centered spinner with a skeleton and guarding the `isRefreshing` binding are the same edit in the same block of each file.

The rule worth settling while doing it: a skeleton when the shape of what is coming is known — a list of rows, a grid of cards — because it tells the user what to expect and stops the layout jumping when content lands. A spinner only when the shape genuinely is not known yet.

#### Channel links should open in the app

`YouTubeLinkParser` handles `youtu.be`, watch links across the www, m and music subdomains, and `shorts`, `live`, `embed`, `v` and playlist forms. It does not handle channels — not `/@handle`, not `/channel/UC...`, and not the legacy `/c/` or `/user/` paths.

**This is worse than simply not supporting them, because the manifest already claims them.** The intent filters match every `youtube.com` host, so Koda appears in the share sheet and as a link handler for a channel URL, accepts the tap, and then does nothing with it. Offering to handle something and then dropping it is a worse experience than not being offered.

**The expensive half is already built.** `resolveChannelId` turns handles, vanity URLs and legacy user paths into canonical `UC` ids through `navigation/resolve_url`, works signed out, and exists today because the subscription importer needed it. The parser only has to recognise the channel shapes and hand them over.

Naturally this lands on the proper channel screen once that exists; until then the existing channel sheet is a reasonable destination, and shipping it early means the share path is fixed rather than waiting on a larger piece of work.

#### One heading system, applied everywhere

Screen titles are set at four different type scales depending on which screen you are on. Home and Library use `displayLarge`; Search and the artist page use `headlineLarge`; Settings, video history, Subscriptions, and the palette picker use `headlineMedium`; Stats uses `displaySmall`. All four are doing the same job — naming the screen you are looking at — and they do not agree on how big that job is.

The cause is structural rather than careless. There are only nine `TopAppBar` usages in the entire app, so nearly every screen hand-rolls its own header, and a hand-rolled header picks its own scale. Each one looked right in isolation and the set drifted. The same is true one level down, where section headers inside screens split across `titleLarge`, `titleMedium`, and `titleSmall` without a rule for which means what.

The fix is a decision followed by a sweep: define the ladder once — screen title, section header, subsection, supporting text — and state which scale each maps to, then apply it. In practice this probably means a shared screen-header composable in `ui/components` that owns the scale and the padding, so the next screen inherits the answer rather than choosing again. `SettingsComponents.kt` already proves the pattern works, with `SettingsRow` and friends owning their own typography and colors so the eleven settings pages cannot drift from each other.

**The player styles are exempt, deliberately.** Editorial's `displayLarge` / `displayMedium` / `displaySmall` progression, Sticker's `headlineLarge` in Black weight, and Morph's Bold headline are compositions, not headings — the type *is* the design in those layouts, which is the whole point of having eight distinct styles. A uniformity pass that flattens them has broken something rather than fixed it. The rule applies to screen chrome, not to the player canvas.

This is low-risk, high-legibility work with no architectural weight, which makes it a good candidate to fold into the tablet pass — both touch every screen, and doing them in one sweep is much less disruptive than doing them in two.

### Playback

#### Switching modes without stopping the audio

Watching a video and deciding you only want to listen should not mean stopping, switching mode, finding the thing again, and starting over. The toggle should carry the playback across.

**The mechanism already exists and already ships.** `VideoPlayerViewModel.onEnterBackground` disables the video track when the app goes to the background — `setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)` — and audio keeps playing, uninterrupted, which is how screen-off listening works today. `onEnterForeground` turns it back on and rendering resumes at the live position. Dropping the picture while the sound continues is a solved problem in this codebase, with zero gap, because nothing is torn down and nothing re-buffers: the same player keeps decoding the same audio stream.

So the honest question is not "how do we avoid a gap", it is **what "moved to music mode" should mean**, because there are two readings and they cost very different amounts.

**The cheap reading is a view change.** The UI flips to music mode, the video player collapses, and playback stays exactly where it is — in `VideoPlayerViewModel`'s ExoPlayer with the video track disabled. This is genuinely gapless because nothing moves, and it is close to free given the code above. What you get is a player that looks like music mode but is not in the music queue, will not autoplay into a radio, and does not appear in the eight player styles.

**The expensive reading is a real migration** into `MusicService`, so the track joins the queue, gets the music notification, and behaves like everything else in music mode. Playback has to actually move between two ExoPlayers, and that is where the seam appears. The pieces are mostly there: the `/player` response is already cached per video id for 30 minutes, so resolving the audio URL usually costs no network, and `PlayerViewModel` already knows how to start at an offset — `setMediaItems(items, index, positionMs)` is exactly what session restore uses. What is missing is an overlap. Handing the position over and calling play means a buffer stall at the switch, which is precisely the interruption being complained about.

**The recommended shape is both, staged.** Disable the video track the instant the toggle is hit, so the audio never breaks. In parallel, have `MusicService` resolve and buffer the same id at the current position, and only when it reports ready, swap: start the music player, stop the video one. Done properly the user hears nothing, because the handoff waits for the second player to be genuinely prepared rather than optimistically seeking. This is the same warm-then-swap idea `prefetchUpcomingSongs` and `warmStreamCache` already apply to queue skips.

**Two things that will bite.** Video has its own `MediaSessionService` now — `VideoPlaybackService` — so during a migration two sessions exist at once, and unless one is torn down as the other comes up the user gets two media notifications and Bluetooth controls pointed at the wrong player. And the queue semantics differ in kind: video autoplays into related videos, music autoplays into radio. A video handed to music mode needs a decided answer for what plays next, and inheriting the related-videos list into a music queue is probably wrong.

The reverse direction — music to video mode, promoting a song to its music video — is the same machinery with one extra unknown: not every track has a watchable video, so the control has to be conditional or it will fail more often than it works. Worth treating as a follow-up rather than assuming symmetry.

#### A crossfade worth the name

There is a crossfade setting today, with a duration preference defaulting to three seconds, and it does not do what it says. It is worth stating plainly, because the setting existing makes this look like a tuning job when it is a rebuild.

`MusicService` runs **one** ExoPlayer, and one player renders one item at a time. So the current implementation fades the outgoing track to silence, lets it end, then fades the incoming one up from zero in `performFadeIn`. The two never overlap. What the listener hears is a dip to silence in the middle — a fade-out followed by a fade-in, which is the one thing a crossfade is defined by not being.

Three further problems, each audible on its own:

**The fade-out is driven by the progress loop, which ticks on `delay(1000)`.** A three-second fade therefore gets about three volume updates. That is not a ramp, it is a staircase, and it is clearly audible as stepping. The fade-in is smoother at 20 steps but is still a coroutine ramp rather than anything tied to the audio clock.

**Both ramps are linear on `player.volume`,** which is a linear amplitude scalar. Hearing is roughly logarithmic, so a linear ramp sounds like it collapses early and then crawls. Even if the two tracks did overlap, summing two linear ramps dips about 6 dB at the midpoint — the hole in the middle that makes amateur crossfades recognisable. Apple Music and every other implementation people rate use an equal-power curve, sine and cosine against each other, so the summed energy stays constant across the transition.

**Nothing distinguishes tracks that must not be crossfaded.** Album transitions that are continuous by design — a live record, a DJ mix, anything that segues — get three seconds of destruction where they need sample-accurate gapless. Apple Music's crossfade is good partly because it knows when not to fire.

So the real work: two players (or one player with a mixing `AudioProcessor`) so the tails genuinely overlap; an equal-power curve; a ramp resolution fine enough to be inaudible, driven off something better than a one-second UI tick; gapless detection so continuous albums are left alone; and a decision about whether a manual skip crossfades or cuts, which are both defensible and should be deliberate.

**One piece is already built.** `prefetchUpcomingSongs` pre-caches the first 512 KB of the next three songs through `warmStreamCache`, so the incoming track's audio is normally on disk before it is needed. That is exactly the prerequisite an overlapping crossfade depends on, and it is the difference between this being feasible and it stuttering on every transition.

**One thing to check early:** loudness. Crossfading two tracks mastered at different levels lurches, and no curve fixes that. If this is going to stand next to Apple Music, track loudness normalisation probably has to come with it rather than after it.

### Foundations

#### Backup, restore, and getting playlists in and out

Subscriptions can leave the app. Nothing else can. `data/SubscriptionTransfer.kt` reads four formats and writes one, sniffed by content rather than extension, and it is the best-built import path in the project. Every other thing a user accumulates — local playlists, liked songs, listening stats, watch history, the not-recommended blocklist, and every setting including their palette and player style — exists only inside this install and dies with it.

That is a bad trade for an app distributed as an APK from GitHub. People sideload, they reinstall, they move phones, they clear data by accident. Losing a hand-built playlist to a reinstall is the kind of thing that loses a user permanently, and right now nothing prevents it.

**Two separate features share a heading here, and they should not be conflated.**

The first is **backup and restore**: one file containing everything, written and read by the app, for moving an install or recovering one. It does not need to be interoperable, which makes it much simpler — the app's own models serialize with kotlinx.serialization already. It should be explicit and user-triggered rather than silent, and it needs a version field from day one, because a backup taken today has to restore into next year's build.

The second is **playlist import and export in formats other apps understand** — m3u/m3u8 for local files, and something that survives a round trip with the other YouTube clients. This is the one that has to be interoperable, and `SubscriptionTransfer` is the model to copy: sniff the content, accept more shapes than you emit, count and explain what could not be brought across rather than dropping it silently.

Note that Android's own auto-backup is already declared via `backup_rules.xml`, which is not the same thing and should not be mistaken for it — it is opaque, tied to a Google account, and cannot move data to a device the user is holding.

**One thing to settle before writing it:** what a backup does about the profile split. Local subscriptions and the blocklist are keyed per profile, while playlists, liked songs, stats and theme are device-wide. A restore that flattens that distinction would put one account's blocklist onto another, so the file has to carry the profile structure, not just the data.

#### Surviving process death

`rememberSaveable` appears in exactly one file, and no ViewModel takes a `SavedStateHandle`. Almost nothing in Koda is restored when the process is killed and rebuilt.

**This hides well, which is why it has lasted.** `MainActivity` declares `configChanges` for orientation, screen size and layout, so rotation never recreates anything and the usual way people notice missing state never fires. What does fire is ordinary Android behaviour: the app goes to the background, the system reclaims it, and returning to it rebuilds from nothing. Scroll positions, expanded sections, the search query and its results, and open sheets are all gone. On phones with aggressive memory management this happens several times a day, and it reads as the app having forgotten what you were doing.

**It also gets worse exactly when the tablet work lands.** Large-screen resizing and multi-window produce real recreation that `configChanges` will not absorb, so a gap that is currently intermittent becomes routine on the devices that item is meant to serve. Worth treating as a prerequisite for that work rather than a separate cleanup.

Not everything needs saving. The highest-value targets, roughly in order: which tab was open, feed and library scroll positions, the search query with its results, and the video position for a player that was open when the process died — the last of which matters most, because losing your place in a long video is the most annoying version of this bug.

#### One image loader

There is no central Coil `ImageLoader`. Crossfade is configured per call site and inconsistently — `crossfade(300)` in one component, `crossfade(true)` in others, nothing at all elsewhere — and there is no shared memory or disk cache policy in an app that is almost entirely images.

`IvorMusicApplication` already exists and is the natural place for a single configured loader: one crossfade duration, one cache policy, one shared placeholder and error treatment, so a thumbnail that fails to load looks deliberate everywhere instead of looking different on each screen.

The practical win beyond consistency is memory. Artwork is loaded on Home, in search results, in the queue, in the notification, in the widget when that exists, and behind three of the player styles, and today none of those share a tuned cache. It is also the single place any future image-sizing policy would live.

#### Loose ends the author already flagged

Three small things marked in shipped code, none of them urgent and all of them the kind of thing that only gets fixed if it is written down.

`HomeViewModel.kt:941` carries `// Handle error silently for now` around a swallowed failure. Silent is a real choice in some places, but here nothing tells the user and nothing logs for a bug report, so a failure in that path is invisible from both directions.

`VideoPlayerContent.kt:169` notes `// Let's poll in UI for now as we have the ExoPlayer instance in VM` — a polling loop sitting in the composable because that was the shortest route to the player. It belongs in the ViewModel, where the rest of the player state already lives.

`UserPlaylist.kt:15` has an informal comment about not wanting to do the URI work yet. It is harmless in effect and this is a public repository that people read to learn how the app is built, so it is worth a rewrite into what it actually means: the playlist id is carried in the display item's `url` field rather than a real URI.

#### Dependency audit

The dependency list has accumulated things nothing uses. The clearest case is **Ktor: three declared artifacts, zero imports.**

`ktor-client-okhttp`, `ktor-client-content-negotiation`, and `ktor-serialization-kotlinx-json` are all declared in the version catalog and in `app/build.gradle.kts`, and `import io.ktor` appears nowhere in the 135 Kotlin source files. The app talks to the network through OkHttp directly and parses InnerTube with manual `org.json` traversal, which is a deliberate architectural choice documented in `CLAUDE.md`. Ktor was presumably an early direction that was not taken. It carries a real transitive graph — client core, http, io, utils, serialization — for nothing at all, and removing all three is safe.

Two smaller items in the same pass:

**`androidx.palette:palette-ktx:1.0.0` is declared inline with a hardcoded version** in `app/build.gradle.kts`, and it is the only dependency that bypasses the version catalog. The rule that dependency versions live only in `gradle/libs.versions.toml` has exactly one exception, and there is no reason for it. The library itself is genuinely used — `ArtworkColorScheme` and `ChromaticMistBackground` both extract colors from artwork through it — so this is a move into the catalog, not a removal.

**Accompanist Permissions is a migration candidate rather than a removal.** It is used in two files, `HomeScreen` and `OnboardingScreen`, and the library is in maintenance upstream now that the platform covers the same ground. Two call sites is a small enough surface to move deliberately rather than urgently.

**One warning for whoever does this pass.** `media3-exoplayer-dash` and `media3-exoplayer-hls` will look unused to any tool and to any grep, because `DefaultMediaSourceFactory` loads them reflectively. They are load-bearing: without them the player dead-ends on "Source error" for every live stream and for the videos where the NewPipe fallback returns a DASH manifest. The build file already carries a comment saying so. Do not let an automated unused-dependency sweep take them out. `kotlinx-coroutines-guava` is similarly easy to misjudge — one import, in `MusicService`, where the `ListenableFuture` API of `MediaLibraryService` requires it.

While the file is open, `material-icons-extended` is worth a look for a different reason. It is not unused, but it is one of the largest artifacts in the graph, and if only a bounded set of icons is actually referenced there may be a size win in pulling them out.

### Reach

#### Android Auto, properly

Auto is declared correctly and still does not work in practice. The manifest carries the `com.google.android.gms.car.application` meta-data, `automotive_app_desc.xml` declares `<uses name="media"/>`, and `MusicService` is an exported `MediaLibraryService` with both browser intent filters. On paper it is wired. In a car it is not usable, and there are four separate reasons stacked on top of each other.

**The first is distribution, and it is probably the whole story for most users.** Android Auto refuses to list media apps that were not installed from the Play Store unless the user has switched on "Unknown sources" inside Auto's own developer settings. Koda ships as an APK from GitHub releases, so essentially every install is sideloaded, which means essentially every user has an Auto that will never show the app no matter how good the browse tree is. No amount of code fixes this. What can be done is documenting it plainly and putting the steps in the app — a row in Settings that explains the developer-settings toggle, near the Auto-related settings, would convert a silent failure into a solvable one.

**The second is that there is no search.** `MediaLibrarySession.Callback.onSearch` and `onGetSearchResult` are not overridden anywhere in `MusicService`. Auto's search button and every voice query routed to the app land on those callbacks, so both currently do nothing at all. In a car, browsing is the fallback and search is the primary interaction — this is not a missing extra, it is the main road being closed.

**The third is that the browse tree is two nodes wide and both need the network.** The root offers only "Recommended For You" and "Your Playlists", which resolve through `getRecommendations()` and `getUserPlaylists()`. Signed out, or on a cold start before the five-minute cache is warm, both can come back empty and Auto shows an app with nothing in it. Worse, the categories that would work best in a car are exactly the ones missing: downloads, liked songs, recently played, and the local audio library all play with no signal, and none of them are reachable. A car is the environment most likely to have bad connectivity and the browse tree is built entirely on things that need good connectivity.

**The fourth is timing.** Auto expects browse responses quickly, and `onGetChildren` goes to the network on a cold cache. A slow InnerTube call can exceed what Auto will wait for, which surfaces as an error or an empty list rather than as loading. Serving something instantly from local data and refreshing behind it would be more robust than making the car wait.

One small bug while in there: playlist items call `setArtworkUri(Uri.parse(playlist.thumbnailUrl ?: ""))`, which turns a missing thumbnail into an empty `Uri` rather than omitting artwork, and that can fail Auto's image loading on the whole item.

Because Auto and Wear both consume this same browse tree, widening it pays twice — it is listed as the first step of the Wear work for the same reason.

#### Voice search

There is no search entry point outside the app's own UI. `onSearch` and `onGetSearchResult` are unimplemented in `MusicService`, and the manifest declares no search or voice intents, so "Hey Google, play something on Koda" has nothing to bind to, on a phone or in a car.

Implementing the two session callbacks is the same work that unblocks Auto's search box, which is why these two items belong next to each other — one implementation, two surfaces, and a third if the Wear companion happens. `YouTubeRepository` already has search; this is wiring an existing capability to an entry point that is currently missing rather than building anything new.

The in-app half is separate and smaller: a microphone in the search bar using the platform speech recogniser, which is worth having on its own for anyone typing one-handed.

#### A home screen widget and a Quick Settings tile

There is no `AppWidgetProvider`, no Glance widget, and no `TileService` in the project. Playback can only be controlled from the app, the notification, or the lock screen.

**The widget** is the one already on the README's list: current artwork, title and artist, and transport controls, resizable, with the artwork colors it already extracts driving the widget's own theme so it does not look pasted on. Glance is the right tool since it is Compose-shaped, though it is a genuinely different rendering model with its own constraints, and the app's Expressive components do not carry over — the widget will need designing rather than porting.

**The Quick Settings tile** is the cheaper half almost nobody builds, and it is arguably the better fit for this app: a one-tap toggle in the shade to start or pause what was last playing, without unlocking or finding an icon. `PlaybackSessionRepository` already restores the last-played song, which is most of what a cold tile tap needs to do.

Both are read-mostly surfaces over a `MediaController`, so neither needs changes to the playback pipeline. The main design question is what they show when nothing is playing and nothing has ever played — the empty state is the state a new user sees first.

#### Tablet optimisation, on every screen

Koda is portrait-only, twice over: `android:screenOrientation="portrait"` in the manifest with the lint warning explicitly suppressed, and `requestedOrientation` set again at runtime in `MainActivity`. Across roughly 69,000 lines there is no `WindowSizeClass`, no `NavigationRail`, no list-detail pane, and no `sw600dp` resource qualifier. The app assumes one hand and one column everywhere except the video player, which overrides orientation itself to go fullscreen.

**This is more urgent than a nice-to-have, because the platform has already taken the decision away.** Koda targets SDK 36, and on Android 16 large-screen devices the system ignores orientation and resizability restrictions for apps at that target. The manifest sets no opt-out property. Which means on an Android 16 tablet the app is *already* being shown rotated and resized right now, with a UI built on the assumption that cannot happen. The choice is not whether to support landscape; it is whether landscape looks designed or looks like a stretched phone. This should be confirmed on a real Android 16 tablet before planning around it, but if it holds, tablet work stops being a feature and becomes a correctness issue.

The good news is that the app is not starting from zero conceptually. `FullscreenPlayerContent` already reflows for landscape and slides the video clear of the docked live chat; `VideoPlayerContent` already computes a chat width from `screenWidthDp`. The patterns exist, they are just applied in one place and reached by a manual orientation override rather than by measuring the window.

The work, roughly in the order it pays off: adopt `WindowSizeClass` as the single source of truth and retire the manual orientation locks; move the `FloatingPillNavBar` to a navigation rail at medium width and up, since a bottom bar on a 12-inch screen is a long reach to a small target; give the grid-shaped screens — Home, Search, Library, Subscriptions — real column counts instead of a stretched single column; and adopt list-detail where the content is genuinely two-panel, which is most of Library and all of Settings, whose eleven-page hub is close to a list-detail layout already.

**The expensive part is the player styles.** There are eight of them across 11,200 lines, each a deliberate composition, and several — the sticker's drag physics, the morph's hero shape, the rotary dial — are designed around a thumb reaching a specific part of a phone-sized screen. They do not become correct by widening. Each needs a decision about what it means on a tablet, and "centered at phone width in the middle of a large screen" is a legitimate answer for some of them rather than a failure to do the work.

#### A Wear OS app

Music is the one thing people genuinely want off their phone. A run, a commute, a gym set — the phone is in a pocket or a locker, and the only thing needed is skip, like, and a different playlist. Koda has no answer there today, and the shape of the answer is less obvious than it looks, because a watch app is not one project. It is three, stacked, and each one is a different amount of work for a different amount of payoff.

**The groundwork is already laid, and partly already paid for.** `MusicService` is a Media3 `MediaLibraryService`, exported, declaring both the modern `androidx.media3.session.MediaLibraryService` action and the legacy `android.media.browse.MediaBrowserService` one. That is exactly the surface Wear OS connects to. The browse callbacks are written and working — `onGetLibraryRoot`, `onGetChildren`, an async fetch path, and a five-minute cache of the account's recommendations and playlists. It was built for Android Auto, but Auto and Wear speak the same protocol, so Wear inherits it for free.

What Wear inherits is also thin. The browse root is two nodes — "Recommended For You" and "Your Playlists" — which means the watch cannot reach Liked Songs, downloads, recently played, or the local audio library, all of which are the things most worth having when the phone is out of reach. Downloads especially: the one category that works with no signal at all is the one currently invisible to every remote surface.

**So the first step is not a watch app at all.** Widening the browse tree is a change to one file, it costs no new module and no new UI, and it improves Android Auto, Bluetooth head units, and Wear's built-in media controls at the same time. Whatever is decided about the tiers below, this is worth doing on its own merits, and doing it first means the watch app is built against a library worth browsing.

**Tier one — the system controls, which may already work.** Wear OS mirrors the phone's active media session to the watch automatically. Because Koda publishes a real session with real metadata and artwork, transport controls and now-playing on the wrist plausibly work today with no code written. This needs verifying on hardware rather than asserting, and if it works it is worth saying so in the README, because a good share of the people asking for a watch app are asking for precisely this and do not know they have it.

**Tier two — a companion app, and the recommended target.** A watch app with Koda's own UI: browse the library, pick a playlist, control the queue, like the current track, set the sleep timer. Playback stays on the phone; the watch is a remote. The appeal is that everything hard is already solved — streams resolve on the phone, the session lives on the phone, the profile is whatever the phone is on, and the watch needs no network of its own and no account of its own.

The cost is a `:wear` module and a message protocol. A custom watch app cannot point a `MediaController` at another device's session, so the link is the Wearable Data Layer: a `WearableListenerService` on the phone translating messages into calls on the existing `MediaController`, and the browse tree serialized across the same channel. That is real work, but it is bounded, self-contained, and touches almost nothing that already exists — which for a codebase where `YouTubeRepository.kt` is 6,500 lines in a single module is worth a great deal.

**Tier three — standalone playback, which is a different project wearing the same name.** The watch streams on its own over Wi-Fi or LTE, with downloads synced to it, and works with the phone switched off. This is what people mean when they say a watch app should be "real", and it runs straight into three walls.

The first is the module boundary. Standalone means the watch needs search, stream resolution, `ChunkedStreamDataSource`, and the InnerTube layer — all of which live in `:app` today. That is an extraction of the app's largest and most volatile file into a shared `:core` module, and it makes every future InnerTube fix a two-target change.

The second is the session, and it is the sharper one. `SessionManager` keeps cookies in `EncryptedSharedPreferences`, which is keystore-bound and by design does not leave the device. The watch cannot inherit the phone's login. It would have to sign in itself, and signing into Google on a watch-sized screen through a WebView is close to unusable. The app's existing paste-a-session-cookie path is the only plausible escape hatch — pushing the cookie string over the data layer — and pushing a credential across a Bluetooth channel deserves a deliberate decision rather than a convenient one. Profiles inherit the same question: tier two is on whatever profile the phone is on, and tier three has to answer it independently.

The third is simply that a watch is a small battery attached to a bad antenna. Every design rule in this project about being frugal with network per user action gets stricter, not looser, and a standalone client doubles the request volume of an already-throttled data source.

**The plan is therefore tiers one and two, and tier three stays an open question** until there is evidence people want it enough to pay the extraction cost. That ordering also happens to be the one where each step is useful on its own: the browse tree helps Auto today, the system controls need only verifying, and the companion app is a finished product whether or not standalone ever follows.

**Two things this deliberately is not.** It is not a video app — the video half of Koda has no watch story and should not be given one. And it is not Material 3 Expressive. Wear OS has its own Material 3, sized and shaped for a round screen and a rotating bezel, and `MaterialExpressiveTheme` does not run there. This is not the alternate design language `DESIGN.md` rules out: that policy exists to stop the phone app being built twice, and a watch is a different device with different ergonomics, not a reskin. The watch app should look like it belongs to Koda through color, palette, and typography, and like it belongs on a watch through everything else.

---

#### Android TV

There is no leanback support in the project — no TV launcher intent, no leanback feature declaration, no D-pad navigation model. Koda cannot appear on a TV today.

This is the largest single item in this document and it should be entered with clear eyes. A TV app is not a tablet layout at greater distance. It is a different input device — a D-pad with no pointer, where every interactive element needs explicit focus handling and a focus order, and where the app's entire gesture vocabulary (swipe to change track, drag to scrub, long-press to boost, pinch to zoom, the Gesture and Sticker player styles) has no equivalent. It is a different viewing distance, which invalidates the type scale and the hit targets. And it wants a different home screen, because a TV feed is browsed leaning back, not scrolled.

Realistically it shares the data layer and almost nothing else. That makes it the item most dependent on the `:core` extraction described under the Wear OS entry — if standalone Wear ever happens, the module boundary it forces is the same one a TV app needs, and doing either alone pays a cost the other would reuse.

**The honest framing is that this is a second app that shares a backend, and it should only start when there is evidence of demand**, because it will not get half-built and remain useful the way the tablet or widget work does. The one thing worth doing cheaply in the meantime is Cast support, which puts Koda's audio and video on a TV without a TV app at all, and would satisfy a good share of the people who ask for this.

---

## Shipped

The milestones behind us, kept here so the direction of travel is visible.

- Playlist management: create, rename, reorder, and delete, with generated cover art
- Synced lyrics from LRCLIB, scrolling in time with playback
- In-app video with a personalized feed, chapters, captions, and comments
- Eight player styles and a 27-palette color system with dynamic color and AMOLED
- Listening statistics with play charts, streaks, and top artists
- Offline downloads for music and video, written to the system Downloads folder
- Live streams with live chat, including a full-screen player for vertical broadcasts
- Account-free subscriptions, with import from NewPipe, PipePipe, Tubular, Takeout, and OPML
- "Don't recommend this" with a local blocklist, account propagation, and app-wide undo
- Multiple accounts and device-only profiles, switchable without signing in again
- Settings split into a searchable hub of eleven pages
