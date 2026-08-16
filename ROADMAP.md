# Koda Roadmap

This document is the long view: where Koda stands today, where it is going, and the reasoning behind each direction. It is deliberately not a task list. Individual work items live in [GitHub issues](https://github.com/Ivorisnoob/Koda/issues); what belongs here is the shape of the app a year out and the constraints any new feature has to survive.

For how the app is built, see [`CLAUDE.md`](CLAUDE.md). For the design system and why it is not a swappable layer, see [`DESIGN.md`](DESIGN.md).

**Keeping this current is part of shipping, not a follow-up.** When a planned item lands, its section moves to [Shipped](#shipped) in the same change that lands the work: the entry leaves Planned work rather than staying put with a note on it, and any prose elsewhere that leaned on the old behaviour is corrected in the same pass. A fixed defect leaves [Known defects](#known-defects) the same way, keeping whatever about the diagnosis is worth carrying to the next problem of its kind. This matters more here than in a changelog, because what is written down is reasoning, and the reasoning is what the next decision gets made from. A section describing the app as it was last month will be believed. Counts, file names and line references drift constantly; re-derive them rather than trusting them.

---

## Where Koda is today

Version **4.3** (`versionCode` 21), targeting Android 16 (API 36) with a floor at Android 11 (API 30). Roughly **71,000 lines** of Kotlin across **139 files**, all of it Compose, all of it rendered inside a single `MaterialExpressiveTheme`.

The app is past the point of proving itself. The core loops all work end to end:

**Two modes, one app.** A video toggle reshapes Home, Search, and Library between a full music player and a full video client. Both share the tab system, the overlays, and the theme; neither is a stripped-down version of the other.

**Two playback pipelines.** Music runs through `MusicService`, a Media3 `MediaLibraryService` with background playback, notifications, and a queue. Video owns its own `ExoPlayer` with DASH, PiP, chapters, captions, hold-to-2x, and a playlist queue when one was opened from a playlist. Both fetch media through bounded ranged requests, because googlevideo throttles open-ended reads to roughly the media bitrate.

**No API keys, and no mandatory account.** Everything comes from NewPipe Extractor and direct InnerTube calls. Search, streaming, downloads, a local taste profile, subscriptions, and the "don't recommend" blocklist all work signed out. Signing in adds the real YouTube feeds on top rather than unlocking the app.

**Identity is plural.** Several YouTube accounts and device-only local profiles sit side by side, switchable with one preference write and no re-authentication, no network, and no interruption to playback.

**The interface is the product.** Eight fully animated player styles, 27 color palettes plus wallpaper-based dynamic color and AMOLED, a settings hub of eleven pages with full-text search, and spring physics on anything touch-driven.

Where the weight sits today:

| Area | Lines | What lives there |
| --- | --- | --- |
| `ui/video` | 15,300 | Video player, live streams, live chat, subscriptions |
| `ui/player` | 11,200 | Eight music player styles |
| `data/YouTubeRepository.kt` | 6,500 | The InnerTube and NewPipe layer, single file |
| `ui/settings` | 5,800 | Hub, eleven pages, search index |
| `ui/library` | 4,700 | Playlists, liked songs, local audio, listening history |
| `ui/home` | 3,600 | Both modes' feeds |

That table is also a map of the risk. `YouTubeRepository.kt` is the single point of failure for every network path in the app, and the two largest UI areas are the two youngest.

---

## The constraints every item here inherits

These are not goals. They are the walls the roadmap has to fit inside, and any proposal that breaks one is a different app.

**No official API keys, ever.** InnerTube shapes drift and renderers get renamed wholesale. Every parser is probe-first, dated in its KDoc, and written to fail into an empty result rather than a crash. Anything that would require a Google API key, a billing account, or a developer registration is out of scope by definition.

**The signed-out path is a first-class path, not a degraded one.** Roughly half the userbase never signs in. A feature that only works with an account needs a device-local answer as well, or it needs to be honest that it is account-only in the same breath it is offered.

**Material 3 Expressive is structural.** 39 files use Expressive-only APIs. An alternate or "classic" design language is a rewrite of 37,500 lines of UI, and the project has a stated policy against it. Specific complaints (a radius, a nav-bar dimension, one animation), are ordinary work and always welcome.

**Minimum SDK 30, and desugaring is load-bearing.** NewPipe calls Java 10/11 APIs that only landed in the platform at API 33. Anything gated above 30 needs a `Build.VERSION.SDK_INT` guard and a working fallback, not a graceful degradation to nothing.

**Frugal on network per user action.** One `/next` response already yields engagement, metadata, related videos, and the comments token. New features parse from responses the app is already fetching before they earn a request of their own.

---

## Known defects

Not roadmap items. These are things that are wrong today, recorded here because they were raised alongside the planning and because each has a diagnosed cause rather than a suspicion.

The entry that stood here, **Shorts and downloads cannot recover from a flagged `visitorData`**, is fixed: `ShortsPlayerViewModel` now registers an `onPlayerError` that splits renderer failures from source failures the way the video player does, and `DownloadRepository` re-mints between attempts when a media fetch came back 403. Two things about it generalise. **A surface that plays googlevideo streams and does not handle player errors has no way out of the bad bucket**, and it will look like a hang rather than a failure, because a fatal error drives the player to `STATE_IDLE` and the buffering flag goes *false* on the way. And **re-resolving under the refused token is not a retry**: the downloader already re-resolved on each of its three attempts and rebuilt an equally dead URL every time, which is why it failed three times in a row and looked like a broken video rather than a flagged session. Anything holding pre-resolved URLs has to drop them at the same moment, which for Shorts meant the five-deep prefetch cache and an epoch counter so a fetch already in flight cannot write its dead URLs back in behind the purge.

The last entry, **vertical videos pillarboxed on the watch page**, is fixed: the video box now takes the source aspect ratio when the source is portrait, capped so the watch page underneath survives. What made that one worth writing down rather than patching blind is worth keeping in mind for the next layout of its kind. The portrait signal was already correct on both parse paths and simply discarded for everything but live streams, so the work was never detection; and the fix is fit rather than zoom, because the box is never narrower than the video and filling it would have cropped the top and bottom, which is exactly where a vertical upload puts faces and captions.

---

## Planned work

### Interface

#### Proper channel screens

A channel in Koda today is a bottom sheet. `ChannelSheet.kt` is 245 lines, and the model behind it, `ChannelProfile`, holds five fields: an id, a name, an avatar, a handle, and a subscriber count as pre-formatted text. Tapping a creator's name gets you their avatar and a Subscribe button, and `getChannelVideos` returns one flat list of uploads with no tabs, no sorting, and no sense of the channel as a place.

That is a gap you feel most at the moment of deciding whether to follow someone. The question "is this channel worth subscribing to" is answered by a banner, a description, an upload cadence, and what else they make. None of which the sheet can show. Everything about the current surface says "here is a name", when what is wanted is "here is a creator".

The target is the full page: banner art, the about text with links and join date, and real tabs (Videos, Shorts, Live, Playlists, Community, About), each with its own sort order and its own continuation paging, so a channel with two thousand uploads scrolls properly instead of ending after one page. Rendered in Koda's own language rather than YouTube's: Expressive shapes, the app's palette, spring physics on the tab switch, and the staggered entrance every other screen already uses.

**Much of this is already paid for.** The Subscribe button routes through `SubscriptionActions`, which means account subscriptions, device-local subscriptions, and the auto target all work on the new screen for free, signed in or out. "Don't recommend this channel" is already wired through `NotInterestedActions`. Sharing, the video grid components, and the long-press save sheet all exist. The work is genuinely the screen and the data behind it, not a new subsystem.

**Two things make it more than a layout job.** The first is that every tab is a different InnerTube browse `params` value, and those must be probed live rather than written from memory. This is exactly the case the probe-first workflow in `CLAUDE.md` exists for, and the Community tab in particular is a renderer family the app has never parsed. `ChannelProfile` will need to grow considerably; today it cannot even express a banner.

The second is a design question worth settling before any code: **`ArtistScreen.kt` already exists, at 1,458 lines, and it is good.** It is the music-mode view of what is often the same entity. A musician's YouTube channel and their YouTube Music artist page are one creator with two faces. Building a channel screen without deciding how it relates to the artist screen means two large screens that drift apart, each missing half of what the other knows. The likely answer is that they stay separate surfaces because their content genuinely differs, but share a header, a subscribe path, and a navigation entry, so that arriving at a musician from video mode and from music mode does not feel like arriving at two different people.

#### Per-channel notification settings

The bell, as YouTube has it: for each channel you follow, choose whether new uploads notify you for everything, only occasionally, or not at all. Today there is no bell anywhere, and no per-channel state to put behind one.

**The uncomfortable part is that the bell is an output control for something that does not run.** `getNotifications()` exists and reads YouTube's own inbox through `notification/get_notification_menu`, but it is a pull that only happens when the notifications sheet is opened, and it needs an account. Nothing in Koda checks for new uploads in the background, and nothing ever posts an Android notification. Adding a per-channel preference without that is building a volume knob with no speaker attached. The setting would be recorded and never consulted.

So this item really contains two, and the order matters: **a background upload check has to come first**, and the data for it already exists. `getLocalSubscriptionsFeed` merges each followed channel's Atom feed and is cheap by design, roughly 50 KB per channel against about 1 MB for the equivalent browse. A periodic `WorkManager` job over that feed, remembering the last-seen timestamp per channel, is the whole delivery mechanism. The per-channel preference then becomes the filter it consults.

**The two-store split shapes what the bell can honestly offer.** For an account subscription the preference can be written to YouTube, so it also affects notifications on youtube.com and in the official apps. For a device-local subscription there is no account to record it against, so it is a device preference driving a device-side check. YouTube's middle setting, "Personalized", is a server-side judgement about which uploads are worth surfacing. There is no local equivalent, and offering it signed out would be a lie. Signed out, the honest set is All or None; signed in, all three.

`SubscriptionGroup` already exists (user-defined bundles of local channels like "Music" or "Tech"), and it is the obvious place to set this in bulk rather than channel by channel. Someone following two hundred channels will not tap two hundred bells.

Two Android specifics worth deciding early. `POST_NOTIFICATIONS` is already declared but is a runtime permission from Android 13, and the request should come when the user first enables a bell rather than at startup, so it arrives with a reason attached. And notifications should share one Android notification channel for uploads with grouping, not one Android channel per YouTube channel. The latter looks tempting and becomes unusable at any real subscription count.

#### Saving playlists to the YouTube account

Keeping a playlist you did not make now works on the device (`data/SavedPlaylistsRepository.kt`), in both modes and into one store, and what is left is the half that syncs. **The signed-in path is close to free**: saving a playlist to your library on YouTube Music is a like on the playlist id, and `postPlaylistApi` with `like/like` and `like/removelike` is already wired for playlists in `YouTubeRepository`. `getUserPlaylists()` reads `FEplaylist_aggregation`, which already returns saved playlists alongside owned ones, so an account-saved playlist appears in Library through the existing read path without a new call.

Because both modes write into the one store, that question only has to be answered once, but it has to be answered for a playlist saved on either side: the video half is saved through the same reference and the same id, so an account sync that only understands music-mode saves would leave half the library unsynced and invisible on youtube.com.

The open question is not how to send it but **what the button means when both stores exist**. Subscriptions already answered the same question and the answer is `data/SubscriptionActions.kt`: one place decides where a save goes, a target preference picks the store, and *un*-saving clears both, because a toggle that turns off in one place and leaves the thing saved in the other is the UI lying. Saving playlists should follow that shape rather than inventing a second one. The signed-out path must keep working untouched, which is why the local store was built first rather than as a fallback.

**Offline is the exception that proves the reference model.** A reference cannot play without signal, so the answer is the download system that already exists rather than a snapshot baked into the save. Saving and downloading stay separate actions with separate meanings, the way they are everywhere else in the app.

#### Spotlight: finishing the alternative home

Spotlight (`ui/home/SpotlightHomeContent.kt`) is the alternative music Home, off by default and chosen either in onboarding or from Appearance. It replaced a planned entry called *Discover*, which aimed at a different user again: fewer decisions per screen for people who find the current Home busy. Building both would have meant three Homes to keep working.

**It took two rejected attempts to land, and why they failed is the useful part.** The first was a dense metadata list; the second added a transport deck, a queue and ranked stats. Both were wrong for the same reason: they were built on a rule that said horizontal shelves are what makes a home feel busy, so they had no shelves at all. Every real music app - Spotify, Apple Music, YouTube Music - is mostly artwork shelves, and a music home without them does not read as a music app. Do not re-derive that rule.

**What Spotlight actually is** is the two ideas that make those homes work, in one screen: Spotify's two-column **shortcut grid** at the top, above the fold and needing no scroll, and YouTube Music's **quick picks** as a *paged* block of four song rows. Paging is the one horizontal gesture that earns its place, because it snaps instead of leaving the reader between two positions and never steals a vertical drag. Then artwork shelves.

**The rule that keeps this from rotting: same data, different composition.** Spotlight is an arrangement of flows `HomeViewModel` already exposes (songs, recently played, liked songs, user playlists), not a new ViewModel, not new fetches, not its own network path. The moment it owns data the classic Home does not, there are two Homes to maintain and one quietly falls behind. The filter chips follow from this: they scope what is genuinely on the device rather than imitating YouTube Music's mood chips, which are a browse call this screen deliberately does not make.

**Auto-generated mixes are filtered out** (`isAutoMix`). Nothing on a playlist marks it as machine-made, so the only signal is the name, and the patterns are anchored on the whole title - a user's own "Late night mixtape" must survive. They are dropped at the source rather than per-section, or they reappear one shelf down.

What is left:

- **Sorting and filtering the shelves**, by play count, duration or date added. All of it is already in `Song` and `playCounts`.
- **Long-press actions on a shortcut tile and a shelf card**, so queueing and adding to a playlist do not require opening the player first.
- **The shortcut grid is ranked by a fixed interleave** (liked first, then playlists and recents alternating) rather than by what is actually reached for most. Real use data is in `playCounts` and would make the grid earn its position.
- **Nothing surfaces albums.** Spotlight shows songs, playlists and liked songs; albums are a shape music homes usually carry and Koda has the data for.

#### Playlists: creation and editing

Local playlists work, and they are plain. You can create, rename, reorder by drag, delete, and now set your own cover. What is missing is everything else that makes a playlist feel like yours: a real creation flow rather than a name prompt, multi-select in lists so a playlist can be built from a selection in one action, duplicate detection, sort and shuffle-into-order, editable descriptions, and a proper empty state that offers a way to fill the playlist rather than reporting that it is empty.

**Two things the cover work settled that the rest of this should not re-derive.** Cover files are written with a timestamp in the name and the previous one deleted, because Coil caches by URL and writing a new image to the path the old one used shows the old artwork until the cache happens to evict it. That applies to any artwork this app generates, not just covers. And **the generator takes its colors as an argument rather than reading them**: `PlaylistRepository` is data layer, the palette lives in `ui/theme`, and the resolution is one function (`playlistCoverSeeds`) that every caller passes down. There are two callers, because a playlist can be created from the Library or from any player style's add-to-playlist sheet, and only one of them being themed is exactly the kind of split that goes unnoticed.

Still open on covers: deriving a generated cover from the playlist's *contents* rather than only the palette, so a playlist of one artist picks up that artwork's colors the way the player already does.

**One structural note.** Playlists serialize to SharedPreferences with the full `Song` list embedded in each record, so every add rewrites the whole playlist. That is invisible at forty songs and will not be at four thousand. It does not need solving now, but a playlist feature set that encourages large playlists should not be built without knowing that is the storage underneath.

#### Black and white custom palettes

The custom color palette picker should offer two deliberate monochrome presets: **Black** and **White**. This is related to the earlier pure-black AMOLED request in [#51](https://github.com/Ivorisnoob/Koda/issues/51), but it is a smaller, better-scoped feature: the presets belong inside the existing palette system rather than becoming a separate theme mode.

Both palettes need to flow through the shared theme everywhere it is used — surfaces, text, controls, navigation, dialogs, settings, and player styles — with readable contrast in every state. They should persist like the other palette choices, work signed out, and keep the project's rule that colors live in the theme rather than being hardcoded at call sites.

Tracked in [#179](https://github.com/Ivorisnoob/Koda/issues/179).

#### Swipe song information to change tracks

The player already uses the album-cover area for the previous/next-song swipe. The same gesture should also work on the **song title and artist-name area**, so the visible song information is an equally natural navigation surface.

This should be one gesture contract, not a second interaction model: identical direction, threshold, animation, and playback behavior across every player style and every compact, expanded, or full-screen surface where song navigation exists. Taps on the text, controls, menus, links, scrolling titles, larger text, and accessibility must continue to work, and a gesture crossing from the artwork into the text must not trigger navigation twice.

Tracked in [#180](https://github.com/Ivorisnoob/Koda/issues/180).

#### A choice between the floating nav pill and a short bottom bar

Koda's current navigation is the inline `HorizontalFloatingToolbar` in `HomeScreen.kt`, an expressive floating pill shared by music and video mode. Some users will prefer a familiar, compact bottom navigation bar that stays anchored to the bottom of the screen.

Add a local preference under **Settings → Appearance** with the floating pill as the default and a short standard bottom navigation bar as the alternative. Both surfaces should use the same tab model and selection handler, including selected and unselected icons, labels, haptics, accessibility, and the existing re-tap-to-scroll behavior. Insets must leave room for the system navigation bar and the mini-player, and the choice must persist across restarts and work signed out. The setting also belongs in the settings search index.

This is an appearance choice, not a second navigation architecture. The tablet work may still move navigation to a rail at medium width and up; this preference describes the phone-sized bottom-navigation alternatives, so those decisions should be coordinated rather than allowed to drift.

Tracked in [#181](https://github.com/Ivorisnoob/Koda/issues/181).

#### Respect reduced motion

Koda animates more than almost anything in its category (roughly 97 spring animations, eight player styles built on motion, staggered entrances on every screen), and it reads nothing about whether the person using it wants that. There is no read of `Settings.Global.ANIMATOR_DURATION_SCALE` anywhere in the source, so a user who has turned animations off system-wide, whether for vestibular reasons or because they are on a slow device, still gets every spring and every stagger.

This matters more here than in a typical app precisely *because* the motion is so central. The bigger the motion design, the worse the experience for someone who cannot tolerate it, and "turn off animations" is a setting people reach for because something is making them ill.

The fix is a single source of truth. Read the animation scale, expose it as a composition local or a theme value, and have the shared animation specs collapse to instant when it is zero. Because springs in this app are used through `spring()` calls scattered across the UI rather than through a shared motion vocabulary, the honest first step is probably introducing that vocabulary, which the design system would benefit from anyway.

Two things worth deciding rather than assuming: transitions that carry meaning (the player expanding, a sheet arriving), should probably become instant rather than disappearing, so the user still understands what happened. And the player styles whose entire identity is motion (Morph's cycling shape, Sticker's squash-and-stretch) need a defined still state rather than a broken one.

#### Predictive back, which is currently paid for and switched off

The manifest sets `android:enableOnBackInvokedCallback="true"`, so Koda has opted into the modern back API and then spent a long time suppressing the result everywhere. **19 `BackHandler`s against zero `PredictiveBackHandler`s** was the worst of both arrangements: the opt-in is declared, so the platform stops applying its own compatibility behaviour, and nothing replaced it.

It now stands at **six against eight**, and the eight are every screen stack and every overlay: settings, Library, the video library, Home's search drill-ins, the Subscriptions channel list, the expanded music player, the video player and the Shorts overlay.

**A note for the next inventory of this.** The Shorts overlay was missed by the first sweep because it was written `BackHandler { ... }` with no argument list, and a grep for `BackHandler(` does not find it. Count both forms.

**The two players turned out to be the easy case, for the same reason.** Both already keep a single progress value from mini pill to full screen, with every height, padding, corner and alpha derived from it, so a preview is that value scrubbed rather than a second animation running alongside the first. What the finger reveals is the real destination, because neither has a separate "leaving" state to draw. The video one also has a drag channel built for its swipe-down minimize, and back rides that rather than a parallel one, so the two ways of dismissing it cannot drift apart. Expect this shape wherever a surface already animates itself open.

The music player also lost eight identical `BackHandler(enabled = true) { onCollapse() }` blocks, one per style, replaced by one handler on `ExpandablePlayer`. Back handlers resolve most recent first, so eight children each claiming the gesture made "which one answers" a question about composition order.

**The settings page stack is done, and three things it turned out to need are worth carrying to the rest.**

The first is that **a preview needs its destination already composed.** Settings pages are an `AnimatedContent` over a `SettingsPage` enum, and `AnimatedContent` renders one state at a time, so there was literally nothing behind the open page to reveal. The hub had to be lifted out of it and composed permanently underneath, with the pages layered on top. That happens to fix a smaller thing on the way past (the hub's scroll position used to be discarded whenever a page opened), and it costs a modifier that makes the covered layer inert, because a composed hub behind a page is one whose rows still take taps falling through and still read out to TalkBack.

The second is that **the commit has to continue the gesture rather than restart it.** The peel is one continuous value spanning both the drag and the flight off screen, and the `AnimatedContent` exit is suppressed for that case specifically; handing back to the normal transition snaps the page to full size to replay the move the finger just made.

The third is the trap in the cancel path, below.

**The in-screen stacks were the settings problem four more times, so that shape is a component now.** Library's route, the video library's page, the Subscriptions channel list and drill-in, and Home's search drill-ins were each one `AnimatedContent` over a route enum, which composes one state at a time and therefore had nothing behind the child to reveal. `ui/components/PredictiveBackStack.kt` owns the answer: parent in `background`, child in `foreground`, the peel, the cancel spring, and the modifier that makes the covered parent stop taking taps and stop talking to TalkBack. Each call site is now the lift plus three lines.

Two details it carries that are easy to get wrong alone. The child's own exit has to be suppressed when a gesture committed, or it snaps back to full size to replay the move the finger just made. And the parent state of the child layer must stay full size (an empty `Spacer`, not nothing), because the default `SizeTransform` will otherwise animate the container between nothing and full screen and clip the child to it, which reads as a page unfolding out of a growing rectangle.

**A step that does not close the child must not be previewed**, which is what `previewable` is for. Clearing the Subscriptions channel filter widens the list in place, and popping an album back to the artist page reveals another child rather than the screen underneath. Previewing either animates a departure that is not happening.

**The Shorts overlay is the odd one out among the overlays**, and worth knowing before the next one like it. The two players collapse into a mini pill and already own a value describing that journey, so their preview is a scrub of it. Shorts has no smaller resting state - it closes, and what is behind it is the app it was opened from - so its peel is its own value shrinking the whole overlay inward, which is the shape the system uses for leaving with no parent to reveal.

**What is left is small and mostly panels.** The video player's comments and live-chat panels and its fullscreen quality sheet, and the player style wheel. These slide up over content rather than sitting on a parent, so they want their own treatment rather than [PredictiveBackStack].

**Two sites should keep a plain `BackHandler`, and it is the same rule as the settings search query.** Back on a non-default Home tab returns to the first tab, and back in Library leaves search or reorder mode. Nothing leaves the screen in either case, so there is nothing to draw behind, and a peel would describe a departure that is not happening.

**The cancel path is what most implementations get wrong, and it is the entire point of the feature.** `PredictiveBackHandler` is a suspending handler that can be cancelled mid-gesture when the user changes their mind and slides back. If that path does not spring the UI cleanly back to where it was, predictive back is worse than no predictive back. The user gets a preview of leaving, decides not to, and lands somewhere broken. Every remaining site needs that case handled, not just the commit case.

**And it carries a trap that is easy to walk into, because the obvious code compiles and does nothing.** Cancellation arrives as a `CancellationException` thrown out of the flow, so the natural place to spring the UI back is the `catch` block. That coroutine is the one being cancelled: an animation started there never runs, and the screen stays stranded mid-gesture. The spring has to be launched from a scope that outlives the gesture (the screen's `rememberCoroutineScope`), which is what the settings implementation does.

#### Haptics, more than eight files

Haptic feedback is still thin on the ground, almost all of it in the video gesture surface and the tab bar. Everything else is silent: the player style wheel spins without detents, drag-to-reorder in playlists gives nothing when an item locks into place, and likes and toggles have no confirmation.

For an app built this heavily on touch (roughly 97 springs, gesture-driven players, a spinnable style wheel), that is a whole sensory channel going unused, and it is the channel that makes physical-feeling UI feel physical.

**The existing use already demonstrates the right instinct, and it should be written down as a rule rather than repeated by memory.** The fullscreen swipes tick because they commit while the finger is still down with no dragged preview behind them, so the tick is the only confirmation the gesture took. Generalised: haptics belong on **commits the user cannot yet see** and on **detents and thresholds:** the wheel clicking to the next style, a reorder locking in, a drag passing the point where releasing will do something. They do not belong on every tap, which is how apps end up buzzing constantly and getting the feature switched off.

Worth routing through one small helper rather than calling `performHapticFeedback` ad hoc, so the vocabulary stays consistent and there is a single place to respect the system haptic setting.

#### Channel links should open in the app

`YouTubeLinkParser` handles `youtu.be`, watch links across the www, m and music subdomains, and `shorts`, `live`, `embed`, `v` and playlist forms. It does not handle channels, not `/@handle`, not `/channel/UC...`, and not the legacy `/c/` or `/user/` paths.

**This is worse than simply not supporting them, because the manifest already claims them.** The intent filters match every `youtube.com` host, so Koda appears in the share sheet and as a link handler for a channel URL, accepts the tap, and then does nothing with it. Offering to handle something and then dropping it is a worse experience than not being offered.

**The expensive half is already built.** `resolveChannelId` turns handles, vanity URLs and legacy user paths into canonical `UC` ids through `navigation/resolve_url`, works signed out, and exists today because the subscription importer needed it. The parser only has to recognise the channel shapes and hand them over.

Naturally this lands on the proper channel screen once that exists; until then the existing channel sheet is a reasonable destination, and shipping it early means the share path is fixed rather than waiting on a larger piece of work.

#### One heading system, applied everywhere

Screen titles are set at four different type scales depending on which screen you are on. Home and Library use `displayLarge`; Search and the artist page use `headlineLarge`; Settings, video history, Subscriptions, and the palette picker use `headlineMedium`; Stats uses `displaySmall`. All four are doing the same job (naming the screen you are looking at), and they do not agree on how big that job is.

The cause is structural rather than careless. There are only nine `TopAppBar` usages in the entire app, so nearly every screen hand-rolls its own header, and a hand-rolled header picks its own scale. Each one looked right in isolation and the set drifted. The same is true one level down, where section headers inside screens split across `titleLarge`, `titleMedium`, and `titleSmall` without a rule for which means what.

The fix is a decision followed by a sweep: define the ladder once (screen title, section header, subsection, supporting text), and state which scale each maps to, then apply it. In practice this probably means a shared screen-header composable in `ui/components` that owns the scale and the padding, so the next screen inherits the answer rather than choosing again. `SettingsComponents.kt` already proves the pattern works, with `SettingsRow` and friends owning their own typography and colors so the eleven settings pages cannot drift from each other.

**The player styles are exempt, deliberately.** Editorial's `displayLarge` / `displayMedium` / `displaySmall` progression, Sticker's `headlineLarge` in Black weight, and Morph's Bold headline are compositions, not headings. The type *is* the design in those layouts, which is the whole point of having eight distinct styles. A uniformity pass that flattens them has broken something rather than fixed it. The rule applies to screen chrome, not to the player canvas.

This is low-risk, high-legibility work with no architectural weight, which makes it a good candidate to fold into the tablet pass, both touch every screen, and doing them in one sweep is much less disruptive than doing them in two.

#### Lyrics that fill as they are sung

Lyrics come from LRCLIB and scroll a line at a time. The line that is playing turns the accent color all at once. The ask is the thing every music app does now: words filling in as they are sung, letter by letter rather than line by line.

**Most of this is already written and it never runs.** `data/LrcLine.kt` carries `contentSpans`, a list of `LrcContentSpan(timeMs, text, durationMs)`. `LyricsRepository.parseLrc` parses Enhanced LRC's `<mm:ss.xx>` word tags into those spans in the same pass as the standard `[mm:ss.xx]` line tags, and back-fills the final span's duration from the next line's start. `ui/player/SyncedLyricsView.kt` branches on `line.contentSpans.isNotEmpty()` and colors each span independently. The word-level path exists end to end. What is missing is a source: LRCLIB's `syncedLyrics` is line-level standard LRC, so `contentSpans` is empty for effectively every song the app fetches, and that branch is dead code.

**So this is a data problem before it is a rendering one**, and there are two honest answers. Either find real word timings - a source that serves Enhanced LRC, or a service carrying a word-level sync - or interpolate them from the line by spreading its duration across its characters. `SyncedLyricsView` refuses the second on purpose and says so in a comment: no "fake" gradient filling, on the grounds that it is more honest to the user. That decision is worth re-taking rather than inheriting, because a sweep that is approximately right reads better than a line that pops, and the players that do this at scale interpolate inside the word anyway. What it must not do is interpolate blindly across a line: one whose last word lands two seconds before the next line begins will finish its sweep early and then sit there, which looks worse than not sweeping at all.

**Even with real spans the current renderer pops rather than fills.** It compares `currentPositionMs >= span.timeMs` and switches a whole word, so the smallest true version of this is a fraction within the active span, not a boolean. Compose takes a `brush` on `SpanStyle`, so the fill can be a gradient with a moving stop across one `Text` rather than a composable per character, which is what keeps it cheap every frame. Worth knowing that the annotated string is already rebuilt on every position tick for the current line, and a per-character version keeping that shape will be rebuilding it per frame.

Two small things to fix while in there: the span style has a dead conditional, where `fontSize` is identical on both branches of `if (isWordPassed)`, and spans are rejoined with a plain `append(" ")`, which does not reproduce the original spacing around punctuation.

### Playback

#### A genuinely YouTube Music-first catalogue

Music mode should be a YouTube Music-first catalogue and playback experience, not ordinary YouTube search presented inside a music-shaped UI. The current repository already asks NewPipe for `music_songs`, `music_artists`, `music_albums`, and `music_playlists`, and Koda already has the artist, album, playlist, authentication, and audio-service pieces around those results. What is missing is an enforced identity contract from source selection through metadata, browse, queue, and playback.

The target is to prefer official releases, Topic-channel tracks, and album-linked recordings; preserve stable track, album, artist, and playlist IDs; carry canonical artwork and structured metadata; and keep music results separate from generic uploads. Ordinary YouTube remains available as an explicit fallback when a music result cannot be resolved, but it should not silently become the primary result or substitute an unrelated video. Live, acoustic, remix, and remastered versions must remain distinct rather than being over-aggressively deduplicated.

This is an architectural push rather than a search-screen tweak. It needs a probe-first audit of the current InnerTube/NewPipe renderer families and continuation paths, parser fixtures for songs/albums/artists/playlists and missing fields, a source-aware result model, deterministic ranking and deduplication, and playback tests covering signed-out and signed-in states. Album and artist relationships should survive movement through search, browse, queue, history, and restored playback; unavailable tracks need an honest unavailable state or deterministic fallback.

Tracked in [#182](https://github.com/Ivorisnoob/Koda/issues/182). Related work includes [#84](https://github.com/Ivorisnoob/Koda/issues/84), [#106](https://github.com/Ivorisnoob/Koda/issues/106), [#119](https://github.com/Ivorisnoob/Koda/issues/119), [#136](https://github.com/Ivorisnoob/Koda/issues/136), [#139](https://github.com/Ivorisnoob/Koda/issues/139), and [#178](https://github.com/Ivorisnoob/Koda/issues/178).

#### Switching modes without stopping the audio

Watching a video and deciding you only want to listen should not mean stopping, switching mode, finding the thing again, and starting over. The toggle should carry the playback across.

**The mechanism already exists and already ships.** `VideoPlayerViewModel.onEnterBackground` disables the video track when the app goes to the background (`setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)`), and audio keeps playing, uninterrupted, which is how screen-off listening works today. `onEnterForeground` turns it back on and rendering resumes at the live position. Dropping the picture while the sound continues is a solved problem in this codebase, with zero gap, because nothing is torn down and nothing re-buffers: the same player keeps decoding the same audio stream.

So the honest question is not "how do we avoid a gap", it is **what "moved to music mode" should mean**, because there are two readings and they cost very different amounts.

**The cheap reading is a view change.** The UI flips to music mode, the video player collapses, and playback stays exactly where it is. In `VideoPlayerViewModel`'s ExoPlayer with the video track disabled. This is genuinely gapless because nothing moves, and it is close to free given the code above. What you get is a player that looks like music mode but is not in the music queue, will not autoplay into a radio, and does not appear in the eight player styles.

**The expensive reading is a real migration** into `MusicService`, so the track joins the queue, gets the music notification, and behaves like everything else in music mode. Playback has to actually move between two ExoPlayers, and that is where the seam appears. The pieces are mostly there: the `/player` response is already cached per video id for 30 minutes, so resolving the audio URL usually costs no network, and `PlayerViewModel` already knows how to start at an offset. `setMediaItems(items, index, positionMs)` is exactly what session restore uses. What is missing is an overlap. Handing the position over and calling play means a buffer stall at the switch, which is precisely the interruption being complained about.

**The recommended shape is both, staged.** Disable the video track the instant the toggle is hit, so the audio never breaks. In parallel, have `MusicService` resolve and buffer the same id at the current position, and only when it reports ready, swap: start the music player, stop the video one. Done properly the user hears nothing, because the handoff waits for the second player to be genuinely prepared rather than optimistically seeking. This is the same warm-then-swap idea `prefetchUpcomingSongs` and `warmStreamCache` already apply to queue skips.

**Two things that will bite.** Video has its own `MediaSessionService` now (`VideoPlaybackService`), so during a migration two sessions exist at once, and unless one is torn down as the other comes up the user gets two media notifications and Bluetooth controls pointed at the wrong player. And the queue semantics differ in kind: video autoplays into the rest of the playlist when it was opened from one and into related videos otherwise, music autoplays into radio. A video handed to music mode needs a decided answer for what plays next. Inheriting the related-videos list into a music queue is probably wrong; inheriting an actual `VideoQueue` is the one case where the answer is obvious, since it is already an explicit ordered list the user chose.

The reverse direction (music to video mode, promoting a song to its music video), is the same machinery with one extra unknown: not every track has a watchable video, so the control has to be conditional or it will fail more often than it works. Worth treating as a follow-up rather than assuming symmetry.

#### A crossfade worth the name

There is a crossfade setting today, with a duration preference defaulting to three seconds, and it does not do what it says. It is worth stating plainly, because the setting existing makes this look like a tuning job when it is a rebuild.

`MusicService` runs **one** ExoPlayer, and one player renders one item at a time. So the current implementation fades the outgoing track to silence, lets it end, then fades the incoming one up from zero in `performFadeIn`. The two never overlap. What the listener hears is a dip to silence in the middle. A fade-out followed by a fade-in, which is the one thing a crossfade is defined by not being.

Three further problems, each audible on its own:

**The fade-out is driven by the progress loop, which ticks on `delay(1000)`.** A three-second fade therefore gets about three volume updates. That is not a ramp, it is a staircase, and it is clearly audible as stepping. The fade-in is smoother at 20 steps but is still a coroutine ramp rather than anything tied to the audio clock.

**Both ramps are linear on `player.volume`,** which is a linear amplitude scalar. Hearing is roughly logarithmic, so a linear ramp sounds like it collapses early and then crawls. Even if the two tracks did overlap, summing two linear ramps dips about 6 dB at the midpoint. The hole in the middle that makes amateur crossfades recognisable. Apple Music and every other implementation people rate use an equal-power curve, sine and cosine against each other, so the summed energy stays constant across the transition.

**Nothing distinguishes tracks that must not be crossfaded.** Album transitions that are continuous by design (a live record, a DJ mix, anything that segues), get three seconds of destruction where they need sample-accurate gapless. Apple Music's crossfade is good partly because it knows when not to fire.

So the real work: two players (or one player with a mixing `AudioProcessor`) so the tails genuinely overlap; an equal-power curve; a ramp resolution fine enough to be inaudible, driven off something better than a one-second UI tick; gapless detection so continuous albums are left alone; and a decision about whether a manual skip crossfades or cuts, which are both defensible and should be deliberate.

**One piece is already built.** `prefetchUpcomingSongs` pre-caches the first 512 KB of the next three songs through `warmStreamCache`, so the incoming track's audio is normally on disk before it is needed. That is exactly the prerequisite an overlapping crossfade depends on, and it is the difference between this being feasible and it stuttering on every transition.

**One thing to check early:** loudness. Crossfading two tracks mastered at different levels lurches, and no curve fixes that. If this is going to stand next to Apple Music, track loudness normalisation probably has to come with it rather than after it.

### Foundations

#### Backup, restore, and getting playlists in and out

Subscriptions can leave the app. Nothing else can. `data/SubscriptionTransfer.kt` reads four formats and writes one, sniffed by content rather than extension, and it is the best-built import path in the project. Every other thing a user accumulates, from local playlists, liked songs, listening stats and watch history to the not-recommended blocklist and every setting including their palette and player style, exists only inside this install and dies with it.

That is a bad trade for an app distributed as an APK from GitHub. People sideload, they reinstall, they move phones, they clear data by accident. Losing a hand-built playlist to a reinstall is the kind of thing that loses a user permanently, and right now nothing prevents it.

**Two separate features share a heading here, and they should not be conflated.**

The first is **backup and restore**: one file containing everything, written and read by the app, for moving an install or recovering one. It does not need to be interoperable, which makes it much simpler. The app's own models serialize with kotlinx.serialization already. It should be explicit and user-triggered rather than silent, and it needs a version field from day one, because a backup taken today has to restore into next year's build.

The second is **playlist import and export in formats other apps understand:** m3u/m3u8 for local files, and something that survives a round trip with the other YouTube clients. This is the one that has to be interoperable, and `SubscriptionTransfer` is the model to copy: sniff the content, accept more shapes than you emit, count and explain what could not be brought across rather than dropping it silently. The import half of it is large enough to have its own heading below, because bringing a playlist in from an app that is not a YouTube client is a different problem from writing a file back out.

Note that Android's own auto-backup is already declared via `backup_rules.xml`, which is not the same thing and should not be mistaken for it. It is opaque, tied to a Google account, and cannot move data to a device the user is holding.

**One thing to settle before writing it:** what a backup does about the profile split. Local subscriptions and the blocklist are keyed per profile, while playlists, liked songs, stats and theme are device-wide. A restore that flattens that distinction would put one account's blocklist onto another, so the file has to carry the profile structure, not just the data.

#### Importing playlists from other apps

**Explicitly optional, and listed because it keeps being asked for rather than because it is committed.** The heading above covers getting playlists in and out in formats other apps understand, framed around m3u and a round trip with the other YouTube clients. This is the speculative half: bringing in a playlist somebody built on a service Koda cannot see, which for almost everyone means Spotify. It is a real reason people open a new music app and then close it, because everything they listen to is still in the old one.

**From YouTube Music there is nothing to import, and that is worth stating before anyone builds it.** A signed-in account's own playlists are already listed through `FEplaylist_aggregation` and merged into `HomeViewModel.userPlaylists`, and somebody else's playlist is already savable from a link as a live reference through `data/SavedPlaylistsRepository.kt`, in both modes. The YouTube half of this request is shipped. What is left is the services that hold nothing Koda can address.

**Spotify will not be an API integration, and that is the constraint that shapes the whole thing.** This app has no official API keys anywhere by design; the Web API needs a client id and a user OAuth round trip, which would be the first such dependency in the project and the first thing to break when a key is revoked. The realistic path is the export file people already produce with Exportify, TuneMyMusic or Soundiiz, which costs nothing to support and works for Apple Music and every other service in the same move.

**`data/SubscriptionTransfer.kt` is most of the machinery and the right model to copy.** It sniffs by content rather than by extension or MIME type, both of which providers lie about; `read()` recognises a zip by its magic bytes, unpacks it into a scratch `File`, and opens the Room database inside it with SQLite. The NewPipe/PipePipe/Tubular backup archive it already reads for subscriptions is the same archive that holds those apps' playlists, so that path is close to free - though those tables have not been probed the way `subscriptions` and `feed_group` were, and must be before anything is written against them. `ui/video/SubscriptionsManagerScreen.kt` and `HomeViewModel.importSubscriptions` are the shape of the flow, down to handing back a summary of what came across and what did not.

**The line that decides the whole design is whether an export carries YouTube ids or only text.** A NewPipe archive, a Tubular export or a Takeout playlist CSV carries ids or urls, so the import is exact, offline and instant. A Spotify export - Exportify, TuneMyMusic, Soundiiz, all CSV - carries a title, an artist, an album and a duration and nothing else. Turning that into something playable is one search per track, and **a wrong match is worse than a missing one**: search a well-known song on YouTube and the top result is as likely to be a live cut, a cover, a sped-up edit or an hour-long loop as the recording the person meant. Duration is the strongest signal available, the way `LyricsRepository` already leans on it against LRCLIB, and on its own it is not enough.

So this cannot be a one-tap import that writes straight into the library. It resolves, then shows what it found and what it could not, and lets the user drop or correct before a playlist is created. That is also the only sane place to put the cost: a 200-track export is 200 searches, and they have to be bounded the way `getLocalSubscriptionsFeed` bounds its fetches at `FEED_CONCURRENCY` rather than fired off at once.

The write side already exists: `PlaylistRepository.createPlaylist` and `replacePlaylistSongs`. Local playlists embed `Song` objects rather than referencing ids, so an imported playlist is a real local playlist that works signed out, which is the right outcome. m3u and m3u8 are a third resolver and the easiest of them: file paths resolve against the local library through `SongRepository` with no network at all.

**One thing to settle before building it:** where it is offered. Subscriptions import lives on its own manager screen because managing subscriptions needs a screen. Playlists have no equivalent, and the natural home is the Library's create-playlist action, beside the thing it is a variant of, rather than a second import page in Settings that nobody finds.

#### Surviving process death

`rememberSaveable` appears in exactly one file, and no ViewModel takes a `SavedStateHandle`. Almost nothing in Koda is restored when the process is killed and rebuilt.

**This hides well, which is why it has lasted.** `MainActivity` declares `configChanges` for orientation, screen size and layout, so rotation never recreates anything and the usual way people notice missing state never fires. What does fire is ordinary Android behaviour: the app goes to the background, the system reclaims it, and returning to it rebuilds from nothing. Scroll positions, expanded sections, the search query and its results, and open sheets are all gone. On phones with aggressive memory management this happens several times a day, and it reads as the app having forgotten what you were doing.

**It also gets worse exactly when the tablet work lands.** Large-screen resizing and multi-window produce real recreation that `configChanges` will not absorb, so a gap that is currently intermittent becomes routine on the devices that item is meant to serve. Worth treating as a prerequisite for that work rather than a separate cleanup.

Not everything needs saving. The highest-value targets, roughly in order: which tab was open, feed and library scroll positions, the search query with its results, and the video position for a player that was open when the process died. The last of which matters most, because losing your place in a long video is the most annoying version of this bug.

**One prerequisite is already paid for.** Each Home tab's `LazyListState` is now hoisted above the tab `AnimatedContent` in `HomeScreen`, so the positions survive a tab switch and, more to the point here, there is finally a single owner to save them from. They are still plain `remember`, and so is `selectedTab`, so this entry is unchanged in substance. What has gone away is having to do the hoisting first.

#### One image loader

There is no central Coil `ImageLoader`. Crossfade is configured per call site and inconsistently (`crossfade(300)` in one component, `crossfade(true)` in others, nothing at all elsewhere), and there is no shared memory or disk cache policy in an app that is almost entirely images.

`IvorMusicApplication` already exists and is the natural place for a single configured loader: one crossfade duration, one cache policy, one shared placeholder and error treatment, so a thumbnail that fails to load looks deliberate everywhere instead of looking different on each screen.

The practical win beyond consistency is memory. Artwork is loaded on Home, in search results, in the queue, in the notification, in the widget when that exists, and behind three of the player styles, and today none of those share a tuned cache. It is also the single place any future image-sizing policy would live.

#### Dependency audit

The dependency list has accumulated things nothing uses. Ktor (three artifacts, zero imports), the hardcoded `palette-ktx` version, and Accompanist Permissions have all been dealt with; **the trap that pass uncovered is worth keeping.**

`kotlinx-serialization-json` was never declared. Only the `kotlin-serialization` *plugin* was, and a plugin ships the compiler side, not the runtime. The whole data layer's `Json.decode` calls were compiling against a runtime that arrived transitively through `ktor-serialization-kotlinx-json`, so removing Ktor as an "unused" dependency broke the build rather than shrinking it. It is a direct dependency now. **Any future dependency removal should check what else was riding on the artifact before trusting an import count.**

Still open: **`androidx.compose.ui.tooling.preview` is declared at `implementation` scope and there is not a single `@Preview` in the project.** It is small, and removing it means the next preview someone writes fails to compile with a confusing error, so it is a judgement call rather than an obvious win.

**One warning for whoever does the next pass.** `media3-exoplayer-dash` and `media3-exoplayer-hls` will look unused to any tool and to any grep, because `DefaultMediaSourceFactory` loads them reflectively. They are load-bearing: without them the player dead-ends on "Source error" for every live stream and for the videos where the NewPipe fallback returns a DASH manifest. The build file already carries a comment saying so. Do not let an automated unused-dependency sweep take them out. `kotlinx-coroutines-guava` is similarly easy to misjudge. One import, in `MusicService`, where the `ListenableFuture` API of `MediaLibraryService` requires it.

While the file is open, `material-icons-extended` is worth a look for a different reason. It is not unused, but it is one of the largest artifacts in the graph, and if only a bounded set of icons is actually referenced there may be a size win in pulling them out.

### Reach

#### Android Auto, properly

Auto is declared correctly and still does not work in practice. The manifest carries the `com.google.android.gms.car.application` meta-data, `automotive_app_desc.xml` declares `<uses name="media"/>`, and `MusicService` is an exported `MediaLibraryService` with both browser intent filters. On paper it is wired. In a car it is not usable, and there are four separate reasons stacked on top of each other.

**The first is distribution, and it is probably the whole story for most users.** Android Auto refuses to list media apps that were not installed from the Play Store unless the user has switched on "Unknown sources" inside Auto's own developer settings. Koda ships as an APK from GitHub releases, so essentially every install is sideloaded, which means essentially every user has an Auto that will never show the app no matter how good the browse tree is. No amount of code fixes this. What can be done is documenting it plainly and putting the steps in the app. A row in Settings that explains the developer-settings toggle, near the Auto-related settings, would convert a silent failure into a solvable one.

**The second is that there is no search.** `MediaLibrarySession.Callback.onSearch` and `onGetSearchResult` are not overridden anywhere in `MusicService`. Auto's search button and every voice query routed to the app land on those callbacks, so both currently do nothing at all. In a car, browsing is the fallback and search is the primary interaction. This is not a missing extra, it is the main road being closed.

**The third is that the browse tree is two nodes wide and both need the network.** The root offers only "Recommended For You" and "Your Playlists", which resolve through `getRecommendations()` and `getUserPlaylists()`. Signed out, or on a cold start before the five-minute cache is warm, both can come back empty and Auto shows an app with nothing in it. Worse, the categories that would work best in a car are exactly the ones missing: downloads, liked songs, recently played, and the local audio library all play with no signal, and none of them are reachable. A car is the environment most likely to have bad connectivity and the browse tree is built entirely on things that need good connectivity.

**The fourth is timing.** Auto expects browse responses quickly, and `onGetChildren` goes to the network on a cold cache. A slow InnerTube call can exceed what Auto will wait for, which surfaces as an error or an empty list rather than as loading. Serving something instantly from local data and refreshing behind it would be more robust than making the car wait.

One small bug that used to sit under this heading is now fixed: playlist items called `setArtworkUri(Uri.parse(playlist.thumbnailUrl ?: ""))`, which turned a missing thumbnail into an empty `Uri` rather than omitting artwork and could fail Auto's image loading for the whole item. Every media item now goes through a `toArtworkUri()` helper that returns null instead. Worth knowing because it is the shape of bug this area produces: Auto fails an entire item on a malformed extra rather than degrading to no artwork, so anything fed into `MediaMetadata` for a remote surface should be null rather than empty.

Because Auto and Wear both consume this same browse tree, widening it pays twice. It is listed as the first step of the Wear work for the same reason.

#### Voice search

There is no search entry point outside the app's own UI. `onSearch` and `onGetSearchResult` are unimplemented in `MusicService`, and the manifest declares no search or voice intents, so "Hey Google, play something on Koda" has nothing to bind to, on a phone or in a car.

Implementing the two session callbacks is the same work that unblocks Auto's search box, which is why these two items belong next to each other. One implementation, two surfaces, and a third if the Wear companion happens. `YouTubeRepository` already has search; this is wiring an existing capability to an entry point that is currently missing rather than building anything new.

The in-app half is separate and smaller: a microphone in the search bar using the platform speech recogniser, which is worth having on its own for anyone typing one-handed.

#### A home screen widget and a Quick Settings tile

There is no `AppWidgetProvider`, no Glance widget, and no `TileService` in the project. Playback can only be controlled from the app, the notification, or the lock screen.

**The widget** is the long-standing request: current artwork, title and artist, and transport controls, resizable, with the artwork colors it already extracts driving the widget's own theme so it does not look pasted on. Glance is the right tool since it is Compose-shaped, though it is a genuinely different rendering model with its own constraints, and the app's Expressive components do not carry over. The widget will need designing rather than porting.

**The Quick Settings tile** is the cheaper half almost nobody builds, and it is arguably the better fit for this app: a one-tap toggle in the shade to start or pause what was last playing, without unlocking or finding an icon. `PlaybackSessionRepository` already restores the last-played song, which is most of what a cold tile tap needs to do.

Both are read-mostly surfaces over a `MediaController`, so neither needs changes to the playback pipeline. The main design question is what they show when nothing is playing and nothing has ever played. The empty state is the state a new user sees first.

#### Tablet optimisation, on every screen

Koda is portrait-only, twice over: `android:screenOrientation="portrait"` in the manifest with the lint warning explicitly suppressed, and `requestedOrientation` set again at runtime in `MainActivity`. Across roughly 71,000 lines there is no `WindowSizeClass`, no `NavigationRail`, no list-detail pane, and no `sw600dp` resource qualifier. The app assumes one hand and one column everywhere except the video player, which overrides orientation itself to go fullscreen.

**This is more urgent than a nice-to-have, because the platform has already taken the decision away.** Koda targets SDK 36, and on Android 16 large-screen devices the system ignores orientation and resizability restrictions for apps at that target. The manifest sets no opt-out property, which means on an Android 16 tablet the app is *already* being shown rotated and resized right now, with a UI built on the assumption that cannot happen. The choice is not whether to support landscape; it is whether landscape looks designed or looks like a stretched phone. This should be confirmed on a real Android 16 tablet before planning around it, but if it holds, tablet work stops being a feature and becomes a correctness issue.

The good news is that the app is not starting from zero conceptually. `FullscreenPlayerContent` already reflows for landscape and slides the video clear of the docked live chat; `VideoPlayerContent` already computes a chat width from `screenWidthDp`. The patterns exist, they are just applied in one place and reached by a manual orientation override rather than by measuring the window.

The work, roughly in the order it pays off: adopt `WindowSizeClass` as the single source of truth and retire the manual orientation locks; move the bottom tab bar to a navigation rail at medium width and up, since a bottom bar on a 12-inch screen is a long reach to a small target; give the grid-shaped screens (Home, Search, Library, Subscriptions), real column counts instead of a stretched single column; and adopt list-detail where the content is genuinely two-panel, which is most of Library and all of Settings, whose eleven-page hub is close to a list-detail layout already.

**The expensive part is the player styles.** There are eight of them across 11,200 lines, each a deliberate composition, and several (the sticker's drag physics, the morph's hero shape, the rotary dial), are designed around a thumb reaching a specific part of a phone-sized screen. They do not become correct by widening. Each needs a decision about what it means on a tablet, and "centered at phone width in the middle of a large screen" is a legitimate answer for some of them rather than a failure to do the work.

#### A Wear OS app

Music is the one thing people genuinely want off their phone. A run, a commute, a gym set. The phone is in a pocket or a locker, and the only thing needed is skip, like, and a different playlist. Koda has no answer there today, and the shape of the answer is less obvious than it looks, because a watch app is not one project. It is three, stacked, and each one is a different amount of work for a different amount of payoff.

**The groundwork is already laid, and partly already paid for.** `MusicService` is a Media3 `MediaLibraryService`, exported, declaring both the modern `androidx.media3.session.MediaLibraryService` action and the legacy `android.media.browse.MediaBrowserService` one. That is exactly the surface Wear OS connects to. The browse callbacks are written and working. `onGetLibraryRoot`, `onGetChildren`, an async fetch path, and a five-minute cache of the account's recommendations and playlists. It was built for Android Auto, but Auto and Wear speak the same protocol, so Wear inherits it for free.

What Wear inherits is also thin. The browse root is two nodes ("Recommended For You" and "Your Playlists"), which means the watch cannot reach Liked Songs, downloads, recently played, or the local audio library, all of which are the things most worth having when the phone is out of reach. Downloads especially: the one category that works with no signal at all is the one currently invisible to every remote surface.

**So the first step is not a watch app at all.** Widening the browse tree is a change to one file, it costs no new module and no new UI, and it improves Android Auto, Bluetooth head units, and Wear's built-in media controls at the same time. Whatever is decided about the tiers below, this is worth doing on its own merits, and doing it first means the watch app is built against a library worth browsing.

**Tier one. The system controls, which may already work.** Wear OS mirrors the phone's active media session to the watch automatically. Because Koda publishes a real session with real metadata and artwork, transport controls and now-playing on the wrist plausibly work today with no code written. This needs verifying on hardware rather than asserting, and if it works it is worth saying so in the README, because a good share of the people asking for a watch app are asking for precisely this and do not know they have it.

**Tier two. A companion app, and the recommended target.** A watch app with Koda's own UI: browse the library, pick a playlist, control the queue, like the current track, set the sleep timer. Playback stays on the phone; the watch is a remote. The appeal is that everything hard is already solved: streams resolve on the phone, the session lives on the phone, the profile is whatever the phone is on, and the watch needs no network of its own and no account of its own.

The cost is a `:wear` module and a message protocol. A custom watch app cannot point a `MediaController` at another device's session, so the link is the Wearable Data Layer: a `WearableListenerService` on the phone translating messages into calls on the existing `MediaController`, and the browse tree serialized across the same channel. That is real work, but it is bounded, self-contained, and touches almost nothing that already exists, which for a codebase where `YouTubeRepository.kt` is 6,500 lines in a single module is worth a great deal.

**Tier three. Standalone playback, which is a different project wearing the same name.** The watch streams on its own over Wi-Fi or LTE, with downloads synced to it, and works with the phone switched off. This is what people mean when they say a watch app should be "real", and it runs straight into three walls.

The first is the module boundary. Standalone means the watch needs search, stream resolution, `ChunkedStreamDataSource`, and the InnerTube layer, all of which live in `:app` today. That is an extraction of the app's largest and most volatile file into a shared `:core` module, and it makes every future InnerTube fix a two-target change.

The second is the session, and it is the sharper one. `SessionManager` keeps cookies in `EncryptedSharedPreferences`, which is keystore-bound and by design does not leave the device. The watch cannot inherit the phone's login. It would have to sign in itself, and signing into Google on a watch-sized screen through a WebView is close to unusable. The app's existing paste-a-session-cookie path is the only plausible escape hatch (pushing the cookie string over the data layer), and pushing a credential across a Bluetooth channel deserves a deliberate decision rather than a convenient one. Profiles inherit the same question: tier two is on whatever profile the phone is on, and tier three has to answer it independently.

The third is simply that a watch is a small battery attached to a bad antenna. Every design rule in this project about being frugal with network per user action gets stricter, not looser, and a standalone client doubles the request volume of an already-throttled data source.

**The plan is therefore tiers one and two, and tier three stays an open question** until there is evidence people want it enough to pay the extraction cost. That ordering also happens to be the one where each step is useful on its own: the browse tree helps Auto today, the system controls need only verifying, and the companion app is a finished product whether or not standalone ever follows.

**Two things this deliberately is not.** It is not a video app. The video half of Koda has no watch story and should not be given one. And it is not Material 3 Expressive. Wear OS has its own Material 3, sized and shaped for a round screen and a rotating bezel, and `MaterialExpressiveTheme` does not run there. This is not the alternate design language `DESIGN.md` rules out: that policy exists to stop the phone app being built twice, and a watch is a different device with different ergonomics, not a reskin. The watch app should look like it belongs to Koda through color, palette, and typography, and like it belongs on a watch through everything else.

---

#### Android TV

There is no leanback support in the project. No TV launcher intent, no leanback feature declaration, no D-pad navigation model. Koda cannot appear on a TV today.

This is the largest single item in this document and it should be entered with clear eyes. A TV app is not a tablet layout at greater distance. It is a different input device. A D-pad with no pointer, where every interactive element needs explicit focus handling and a focus order, and where the app's entire gesture vocabulary (swipe to change track, drag to scrub, long-press to boost, pinch to zoom, the Gesture and Sticker player styles) has no equivalent. It is a different viewing distance, which invalidates the type scale and the hit targets. And it wants a different home screen, because a TV feed is browsed leaning back, not scrolled.

Realistically it shares the data layer and almost nothing else. That makes it the item most dependent on the `:core` extraction described under the Wear OS entry. If standalone Wear ever happens, the module boundary it forces is the same one a TV app needs, and doing either alone pays a cost the other would reuse.

**The honest framing is that this is a second app that shares a backend, and it should only start when there is evidence of demand**, because it will not get half-built and remain useful the way the tablet or widget work does. The one thing worth doing cheaply in the meantime is Cast support, which puts Koda's audio and video on a TV without a TV app at all, and would satisfy a good share of the people who ask for this.

---

## Shipped

The milestones behind us, kept here so the direction of travel is visible.

- Playlist management: create, rename, reorder, and delete, with cover art you pick or the app generates from your palette
- Saving other people's playlists and albums to the library in both music and video mode, stored as live references rather than copies
- Synced lyrics from LRCLIB, scrolling in time with playback
- In-app video with a personalized feed, chapters, captions, and comments
- Eight player styles and a 27-palette color system with dynamic color and AMOLED
- Listening statistics with play charts, streaks, and top artists
- Offline downloads for music and video, written to the system Downloads folder
- Live streams with live chat, including a full-screen player for vertical broadcasts
- Account-free subscriptions, with import from NewPipe, PipePipe, Tubular, Takeout, and OPML
- "Don't recommend this" with a local blocklist, account propagation, and app-wide undo
- Multiple accounts and device-only profiles, switchable without signing in again
- Spotlight, an alternative music Home with a shortcut grid, paged quick picks and artwork shelves, chosen in onboarding or Appearance
- Settings split into a searchable hub of eleven pages
- Tab scroll positions that survive a switch, and a re-tap that returns the list to the top
- Skeleton placeholders on every feed's first load, replacing the doubled spinners
- A listening history for music, grouped by day, searchable, with a pause toggle
- Vertical videos given a player box their own shape instead of a pillarboxed 16:9 frame
- Search over the channels you follow, by name or @handle, on every list long enough to need it
- Playlists that play through in video mode: a real queue with next/previous, a browsable queue sheet, and a "Playing from" card on the watch page
- Feeds that actually run edge to edge, scrolling under the status bar instead of stopping at it
- Video playlists held on the device, so saving a video no longer needs a YouTube account
- A music queue you can actually edit: hold-to-drag reordering in every player style, swipe-to-remove with undo, and Play next / Add to queue from a long press on any song

The music queue is the other kind of gap worth recording: **the model was finished and the UI was not.** `addToQueue`, `moveQueueItem` and `removeQueueItem` had all existed on `PlayerViewModel` for a long time. `addToQueue` was reachable from nothing at all - only the auto-queue called it - so adding one track to what was already playing was impossible without restarting playback from a new list. `moveQueueItem` was wired into one of the three queue views, and `removeQueueItem` into two. **A ViewModel method with no caller is not a feature, and nothing in the type system says so**, which is why this survived so long. The reorder that did exist measured the drag against a hardcoded 80dp while its rows were a different height, so a long drag walked out from under the finger, and it committed every crossing to both the player and the session file.

Three things generalise from the fix. **Index-qualified `LazyColumn` keys and reordering are incompatible**: `"$id_$index"` was chosen because a queue can hold the same song twice and duplicate keys crash, but moving one row then re-keys every row it passed, so `animateItem` sees a screenful of destroyed items rather than one moving. Qualifying by *occurrence* (`queueRowKeys`) is unique and survives a move. **Drag geometry must be read from `LazyListState.layoutInfo`, never assumed** - that is what makes `QueueReorderState` correct for three views with three different row heights. And **`rememberSwipeToDismissBoxState` keeps the `confirmValueChange` it was first given**, so a callback closing over a row index removes the wrong song the moment anything above it moves; it has to be read through `rememberUpdatedState`. That last one is the general hazard with any `remember`ed state object that takes a lambda.


The device video playlists are worth a note, because the shape of the bug is one this app can grow again. Video mode had every piece of a save flow except somewhere to save to: `videoPlaylists` was the account's list alone, so signed out the sheet listed nothing, the five surfaces that open it routed to a sign-in wall instead, and the pinned Watch Later row posted to `addToYouTubePlaylist`, which - like `subscription/subscribe` - answers 200 without a session and does nothing. The sheet then showed its success check. **A write path that reports success it did not verify is worse than one that fails**, and this is the second endpoint in this codebase with that property, so treat a 200 from an account write as meaning nothing until something in the response confirms it. The fix is `data/LocalVideoPlaylistsRepository.kt`, deliberately shaped like `PlaylistRepository` rather than like `SavedPlaylistsRepository`: a local playlist is a copy with the videos embedded, not a live reference, because there is no upstream list to re-fetch. The routing between the three targets lives in one method per ViewModel instead of at the call sites, the `localvp_` id prefix is what lets every downstream consumer branch, and the queue came free - `VideoQueue` and `playQueue` never looked at where a playlist came from, so serving local videos through the same `_playlistVideos` state was the whole integration.

Worth carrying from the edge-to-edge work, because it is the kind of bug that hides in plain sight. The window was edge to edge the whole time: `enableEdgeToEdge`, transparent bars, the right icon appearance. What was wrong sat one layer down, in a line copied into every scrolling surface. `Modifier.windowInsetsPadding(WindowInsets.statusBars)` on a `LazyColumn` shrinks the list's *viewport*, so content is clipped at the status bar and can never pass beneath it, leaving a flat band that reads as a system title bar. The same inset belongs in `contentPadding`, where it spaces the first item without shrinking anything. The bottom of those same screens had always done it that way, which is why the bug was only ever visible at the top. It is now one `listContentPadding` in `HomeScreen`, handed to all seven tab surfaces, so a new screen inherits it. Two smaller things travelled with it: contrast enforcement is now off for both bars (`enableEdgeToEdge` leaves the navigation bar's on, which is a translucent system band under the floating toolbar on three-button navigation, API 30-34 only), and the portrait watch page takes the top inset alone so its info list scrolls under the navigation bar rather than stopping above it.
