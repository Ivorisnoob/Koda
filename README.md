<p align="center">
  <img src="icon.svg" width="120" alt="Koda Logo"/>
</p>

<h1 align="center">Koda</h1>

<p align="center">
  <b>A modern Android music and video player powered by YouTube Music</b>
</p>

<p align="center">
  <a href="https://github.com/Ivorisnoob/Koda/releases/latest"><img src="https://img.shields.io/github/v/release/Ivorisnoob/Koda?style=for-the-badge&label=Download&color=6750A4" alt="Latest release"/></a>
  <a href="https://github.com/Ivorisnoob/Koda/releases"><img src="https://img.shields.io/github/downloads/Ivorisnoob/Koda/total?style=for-the-badge&color=1B6C3A" alt="Downloads"/></a>
  <a href="LICENSE"><img src="https://img.shields.io/github/license/Ivorisnoob/Koda?style=for-the-badge&color=8B4513" alt="License"/></a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-11%2B-green?style=for-the-badge&logo=android" alt="Android 11 and newer"/>
  <img src="https://img.shields.io/badge/Min%20SDK-30-blue?style=for-the-badge" alt="Minimum SDK 30"/>
  <img src="https://img.shields.io/badge/Kotlin-100%25-purple?style=for-the-badge&logo=kotlin" alt="Kotlin"/>
  <img src="https://img.shields.io/badge/Jetpack%20Compose-UI-blue?style=for-the-badge&logo=jetpackcompose" alt="Compose"/>
</p>

<p align="center">
  <a href="https://telegram.me/ivorisnoob_chat"><img src="https://img.shields.io/badge/Telegram-Join%20the%20chat-2CA5E0?style=for-the-badge&logo=telegram&logoColor=white" alt="Telegram"/></a>
</p>

<p align="center">
  <img src="https://img.shields.io/github/stars/Ivorisnoob/Koda?style=social" alt="Stars"/>
  <img src="https://img.shields.io/github/forks/Ivorisnoob/Koda?style=social" alt="Forks"/>
</p>

---

Koda is built entirely with Kotlin and Jetpack Compose, and it is built **ground-up on Material 3 Expressive**, not themed with it but constructed from it. Shapes, motion, color, and typography all resolve through a single `MaterialExpressiveTheme` at the root of the app, and every screen is composed from Expressive primitives. See [DESIGN.md](DESIGN.md) for how that works and why the design is not a swappable layer.

It streams music and video from YouTube, learns what you like, and does it without any official API keys. Everything runs through NewPipe Extractor and direct InnerTube calls.

There is one app with two modes. Leave the video toggle off and Koda is a full music player with a queue, downloads, playlists, statistics, and a library. Turn it on and the Home, Search, and Library tabs reshape into a proper video experience with a personalized feed, a real player, chapters, captions, comments, subscriptions, and notifications. No Google API key is ever required, and the whole app works even without signing in, including search, streaming, downloads, and a local taste profile.

<p align="center">
  <img src="https://raw.githubusercontent.com/Ivorisnoob/Koda/main/Screenshots/01-home-music-your-mix.png" width="24%" alt="Music home"/>
  <img src="https://raw.githubusercontent.com/Ivorisnoob/Koda/main/Screenshots/03-player-editorial.png" width="24%" alt="Editorial player style"/>
  <img src="https://raw.githubusercontent.com/Ivorisnoob/Koda/main/Screenshots/41-home-video-recommended.png" width="24%" alt="Video home"/>
  <img src="https://raw.githubusercontent.com/Ivorisnoob/Koda/main/Screenshots/42-video-watch-page.png" width="24%" alt="Video watch page"/>
</p>

<p align="center"><sub><a href="#screenshots">More screenshots</a> &middot; music mode, all eight player styles, video mode, and every settings page</sub></p>

## Download

Get the latest APK from **[Releases](https://github.com/Ivorisnoob/Koda/releases/latest)**. Builds are also posted in the [Telegram chat](https://t.me/ivorisnoob_chat) as they are cut.

Each release publishes three APKs. Pick the one that matches your device:

| APK | Who it is for |
|-----|---------------|
| `arm64-v8a` | Essentially every phone made since around 2017. **Start here.** |
| `armeabi-v7a` | Older 32-bit devices. |
| `universal` | Both of the above in one larger file. Use it if you are not sure. |

Android will warn you before installing an app from outside the Play Store. That is expected for a sideloaded app, not a sign of a problem. You only have to do this once: Koda's built-in updater watches Releases, picks the right APK for your device's ABI automatically, and installs it from then on.

Koda is published here and in the Telegram chat, nowhere else. See [SECURITY.md](SECURITY.md) for why a build from anywhere else cannot be vouched for.

**Requirements:** Android 11 (API 30) or newer. No Google account is required. See [Authentication](#authentication) for what changes if you sign in.

---

## Signing in is optional

Personalized feeds, watch history, comments, and engagement need a signed-in account. To connect one, open Settings and tap **Connect YouTube Account**.

Everything else works signed out: search, streaming, downloads, local playlists, liked songs, subscriptions, and recommendations built from a local taste profile of your own listening. Signing in adds to that rather than replacing it, so the feeds merge. See [Authentication](#authentication) for the full split.

On Android 11 specifically, wallpaper-based dynamic color is unavailable, since that is an Android 12 platform feature. Koda falls back to its bundled palettes; everything else behaves the same.

---

## Features

### Music

- **Full YouTube Music search** for songs, albums, artists, and playlists, with results grouped into tabs.
- **Search history** kept locally, with per-item removal and a clear-all action.
- **Personalized quick picks and recommendations**. A local taste profile is built from your play history, liked songs, and recent searches, so suggestions work even when you are logged out. Signed in, they blend in your real YouTube Music home feed.
- **Recently played** rail on Home that stays fresh across the app.
- **Your playlists**, including Liked Songs, plus artist and album pages with play, shuffle, and start-radio actions.
- **Start radio** from any track to spin up an endless, related-song queue.
- **Local playlists** you own: create, rename, edit, drag to reorder, and delete, with auto-generated cover art. The playlist screen has a manage mode for selecting, removing and reordering, plus total duration.
- **Reordering YouTube Music playlists** writes back to your account, not just the local view.
- **Tapping a search result starts a radio** around that song rather than queueing every other upload of it. Playlists, Your Mix and Library still queue their list, because those are real playlists.
- **Search pages in as you scroll**, for both songs and videos, instead of a Load More button.
- **Add to playlist** and **add to queue** sheets reachable from the player and lists.
- **Like tracks straight from the player.** Liked songs appear immediately and are stored locally, no login required.
- **Sleep timer** in all eight player styles, each in its own idiom, with a live countdown and a visible armed state. It lives with playback rather than the screen, so leaving the app does not cancel it. There is a duration mode that fades out over five seconds rather than cutting the audio dead, and an **end of this track** mode.
- **Library sort that persists** across launches, including **Most played** and **Recently added**, each showing what it sorted on ("12 plays", "3d ago") so the order never reads as arbitrary.
- **Synced lyrics** fetched from LRCLIB, scrolling in time with playback.
- **Listening statistics**: songs played, artists explored, liked-song count, top artist, top songs and artists, daily / weekly / monthly play charts, a listening streak, and recent searches.
- **Local audio playback** with MediaStore scanning, per-folder exclusion, and a high-compatibility scan mode for devices where MediaStore misses files.
- **Last-played song restoration** so the player comes back where you left it.

### Player styles

Eight distinct, fully animated player UIs, switchable from a spinnable style wheel:

- **Classic:** familiar transport with play / pause / next / previous.
- **Gesture:** swipeable carousel you flick between tracks.
- **Editorial:** two-tone magazine layout with die-cut art and a word-pill transport.
- **Canvas:** full-bleed album art as the whole screen, with chrome that fades away while playing and swipe-to-skip that follows your finger.
- **Bento:** a squish grid of flat tonal tiles with press physics.
- **Sticker:** a die-cut sticker with drag, peel, and squash-and-stretch physics.
- **Morph:** a living hero shape that cycles organic cuts while playing.
- **Dial:** a rotary tick-ring instrument you spin to scrub.

Plus an **ambient artwork background**, an optional **chromatic-mist** effect, and an option to color the expanded player's controls straight from the current cover art.

### Video mode

- **Personalized video home feed**. It shows your real YouTube feed when you are signed in, and falls back to a taste-based feed built from your watch history when you are not.
- **A real video player** with the full quality ladder up to 2160p60 and a separate audio track, so it never gets stuck on a low muxed quality.
- **Chapters** rendered as seek-bar ticks, a current-chapter chip, and a chapters sheet.
- **Captions / subtitles** with a CC toggle and a language picker, drawn as an overlay so toggling them costs nothing and they survive a quality switch. They work signed out.
- **Double-tap** either side to skip forward or back, and **hold to play at 2x**, with an adjustable playback speed.
- **Swipe up on the video to go fullscreen, and down the middle to come back:** the corner button is a small target on a phone held in both hands. Pull the whole watch page down to minimize.
- **Drag the left and right edges for brightness and volume**, with a ladder of slats that lights from the bottom, one slat per real system volume notch, so the haptic tick and the light are the same event. It takes its colors from your palette.
- **Picture-in-Picture** that captures just the video, not the whole app, with play, pause and 10-second skip controls in the window.
- **Video appears in the system media controls** too: the lock screen, the shade's media area, the output switcher, and headset and Bluetooth buttons.
- **Open YouTube links in Koda.** Share a link to it, or open one with it, and it plays: watch, `youtu.be`, shorts, live, embed and playlist URLs. Music links open in the music player, everything else in the video player.
- **Links in descriptions and comments are tappable**, including timestamps, which seek the player and close the panel.
- **Optional timed comments** that fade in over the video as playback reaches the moment they mention.
- **Share** a video, playlist, or album out to any app.
- **A Subscriptions tab** with the latest uploads from channels you follow, a channel-avatar rail, a full channel list, and drill-in to any channel's uploads.
- **Follow channels without a Google account.** Subscriptions can be saved to the device instead of (or as well as) your YouTube account, and the feed merges both. A **Subscribe saves to** setting picks which.
- **Import your subscriptions** from NewPipe, PipePipe or Tubular (either their subscriptions file or their whole backup zip), or from a Google Takeout CSV or OPML. The file type is detected for you, and channels Koda cannot play are reported rather than dropped silently. A backup also brings across channel pictures and your feed groups. Export writes the NewPipe-compatible format so it round-trips back.
- **Channel groups** to filter the subscriptions feed.
- **"Don't recommend this"** on any recommendation: hide a single video, or stop a whole channel being suggested. It works signed out, takes effect immediately, and every dismissal has one app-wide Undo. Signed in, the choice is also sent to your YouTube account so it cleans up recommendations everywhere else too. A **Not recommended** screen in Settings lists everything you have hidden, so nothing is buried forever by a mis-tap.
- **A dedicated watch-history tab** that syncs your viewing back to YouTube.
- **A notification inbox** on the video home screen.
- **Video playlists** and a save-to-playlist sheet, including Watch Later and Liked videos.
- **A mini video player** so a clip keeps playing while you browse.

### Live streams

- **Live streams play like any other video**, with a LIVE badge and a viewer count that keeps updating while you watch.
- **Live chat**, with Super Chats and Super Stickers in their real colors, membership and gift events, pinned messages, channel emoji, and owner / moderator / member badges. Scroll back to read and it holds position instead of yanking you forward; a pill tells you how many you missed.
- **Send messages** when you are signed in. Yours appears instantly, and if the stream rejects it for slow mode, a word filter or a ban, you get told and your text is handed back rather than silently swallowed.
- **Jump back to the live edge** with one tap on the LIVE chip whenever you have fallen behind on a stream with a DVR window.
- **Switching quality is instant.** Every resolution lives in one manifest, so changing it never re-buffers or drops the stream.
- **Vertical live streams get a player built for them.** A 9:16 broadcast fills the screen instead of sitting as a thin strip between two black bars, with chat over the bottom of the video on a soft gradient so it stays readable without hiding the stream. One tap drops to the normal page for the description and related videos, and one tap goes back.
- **Rotate a vertical stream to landscape** and the video moves aside for a full-height chat column. The space next to a 9:16 video is exactly chat-shaped, so nothing is wasted.
- **Comments step aside on live videos**, because live chat is where the conversation actually is.

### Shorts (opt-in)

- A short-form vertical feed, off by default and only shown once you deliberately enable it.
- Swipe-through pager player with prefetch for instant next-video playback.
- An action rail for like, dislike, comments, and share, and you can hide any of those buttons you do not want.

### Comments and engagement

- Read and write comments and replies right from the player. Yours appear instantly.
- Like a comment, and delete your own.
- Like, dislike, and subscribe from the player using your account.

### Playback engine

- High-quality audio streaming via NewPipe Extractor and direct InnerTube resolution (ANDROID_VR / IOS clients for unciphered stream URLs).
- Background playback with system controls on the notification and lock screen through a Media3 media session, including a live progress bar in the notification.
- On Android 16, an optional **Live Update** that puts the current song in the status bar as a chip with a progress bar and the time remaining. Off by default, switchable on in Settings.
- Full queue control with drag to reorder, shuffle, and repeat modes.
- Auto-load queue that appends recommended songs when the queue runs low.
- Crossfade between songs with an adjustable duration.
- **Chunked streaming.** YouTube paces open-ended requests to roughly the bitrate of the media, measured at 32 KB/s on a stream that served 5-22 MB/s over the same connection when asked properly. Every fetch is ranged, which is what makes tap-to-play, seeking and downloads fast.
- **Skips start from disk.** The head of the next three songs in the queue is pre-cached, so a skip does not pay a round trip before sound starts.
- **A quicker first play each session.** The identity token needed before any stream resolves is minted from a small endpoint and reused across restarts, so a warm start does no network at all.
- Stream prefetch and an on-device cache with a configurable size limit and a one-tap clear.
- OEM and HyperOS fixes: high-compatibility scanning and a battery-optimization opt-out to keep background playback alive on aggressive devices.

### Downloads

- **Download songs and videos** for offline playback. Video is stitched back together from YouTube's separate high-quality video and audio streams into a single MP4, so downloads are not stuck at the 360p that comes as a ready-made file.
- **Download a whole album or playlist** in one tap. Anything you already have is skipped, so re-running it only fetches what is missing.
- **Files go where you can find them:** `Downloads/Koda/Music` and `Downloads/Koda/Video`, named after the track rather than the video id. They show up in the Files app and play in anything else on your device.
- **Downloads survive leaving the screen.** They run in the background through a foreground service instead of dying when you navigate away.
- **A download manager** with separate Music and Video tabs, a live queue, progress, cancel, delete, and one-tap retry on anything that failed.
- **Progress notifications with album art**, and on Android 16 an optional **Live Update** that puts download progress in the status bar as a chip with a progress bar. On by default there, switchable off in Settings.
- Failures retry automatically with a freshly resolved stream URL, and a partial download is never left behind as a broken file.

### Interface and theming

Koda's interface is the product, not a wrapper around one. The whole app is constructed from Material 3 Expressive: 47 source files use Expressive-only APIs directly, `MaterialShapes` is referenced 141 times across 14 shapes, and spring physics drives 120 animation specs. There is no alternate design language and no fallback path. Details and rationale are in [DESIGN.md](DESIGN.md).

- Material 3 Expressive design with shape morphing, spring physics, and dynamic color, applied through one root `MaterialExpressiveTheme`.
- Dynamic theming that pulls its palette from your wallpaper (Android 12+) or from album artwork.
- Over two dozen curated color palettes across six families: Vibrant, Pastel, Aesthetic, Earthy, Moody, and Jewel & Mono.
- Light, dark, and system themes, plus an AMOLED true-black mode.
- Gesture-based navigation and physics-based transitions with a floating pill nav bar.
- A one-tap Home mode toggle to switch between music and video.
- **Two music home layouts.** The classic Home leads with a hero and carousels; **Spotlight** opens instead with a grid of the things you reach for most, then paged quick picks and artwork shelves. Pick one during onboarding or in Settings, and change it whenever.
- A rebuilt onboarding flow with a morphing shape hero, wavy progress indicator, and focused first-run steps.
- A built-in updater that checks GitHub Releases, picks the right APK for your device's ABI, and installs it.

### Settings

- **A hub of eleven categories** rather than one long scroll, and every category row shows the live value of what is inside it, so "what is this set to?" is answered without opening anything.
- **Settings search.** There are around fifty settings, and people know what they want rather than what it is called, so the index carries synonyms and tolerates typos. "offline" finds Local Only, "battery" finds the OEM fix, "bitrate" finds music quality, "songs not showing" finds compatibility scanning, and "amol" and "crossfde" both land where you meant.
- **Quality is per-network.** Separate Wi-Fi and mobile-data settings for video and for music, applied automatically based on what you are connected to.
- **Music streaming quality** of its own: High, Normal, or Data Saver.
- **Prefer HDR Videos**, off by default. Turned on, HDR variants are listed alongside SDR and preferred at equal resolution, with a clean fallback for videos that have none.
- **Destructive actions use the theme's error color**, so they read correctly across all palettes and AMOLED rather than being a hardcoded red.

### Authentication

- **Several accounts at once, and profiles without one.** Add more than one YouTube account and switch between them from the home avatar - instantly, with no network and no signing in again, since each session is stored encrypted on the device. Long-press the avatar to flip straight back to the last one.
- **Profiles that are not accounts.** A profile can be device-only, with no Google account at all, so you can keep separate sets of subscriptions and hidden recommendations without signing into anything.
- Subscriptions and hidden recommendations are kept per profile. Playlists, liked songs, downloads and statistics are shared across all of them.
- **Music keeps playing across a switch.** Everything account-derived resets, including both feeds, subscriptions and the player's like and subscribe state, but killing your music because you checked another account is a bad trade.
- Signing out of your last profile disconnects the account and keeps the profile, so its device-local subscriptions and blocklist survive.
- An account whose session has expired says so on its own row, instead of quietly showing empty screens.
- Sign in through an embedded WebView. No password ever leaves the browser.
- **Or paste a session cookie** from a desktop browser, for when the in-app sign-in fails or a session has gone stale. The sheet walks through getting it and checks what you paste before saving.
- Cookies are stored with EncryptedSharedPreferences and signed per-origin (SAPISIDHASH).
- Everything except personalized feeds, watch history, comments, and engagement works fully signed out.
- Subscriptions and "don't recommend this" both work without an account, stored on the device. Signing in does not replace them, it adds to them: the feeds merge, and dismissals are forwarded to your YouTube account as well.

---

## Screenshots

Captured on a Pixel 8 running Koda 4.4, in light theme with wallpaper-based dynamic color. Every palette, AMOLED, and dark mode change what these look like.

### Music mode

<table>
  <tr>
    <td width="33%" align="center"><img src="https://raw.githubusercontent.com/Ivorisnoob/Koda/main/Screenshots/01-home-music-your-mix.png" alt="Music home"/><br/><sub><b>Home</b><br/>Your Mix hero and carousels</sub></td>
    <td width="33%" align="center"><img src="https://raw.githubusercontent.com/Ivorisnoob/Koda/main/Screenshots/05-library-music.png" alt="Library"/><br/><sub><b>Library</b><br/>Playlists, artists, albums</sub></td>
    <td width="33%" align="center"><img src="https://raw.githubusercontent.com/Ivorisnoob/Koda/main/Screenshots/19-library-music-with-downloads.png" alt="Library with downloads"/><br/><sub><b>Library</b><br/>With offline downloads</sub></td>
  </tr>
  <tr>
    <td width="33%" align="center"><img src="https://raw.githubusercontent.com/Ivorisnoob/Koda/main/Screenshots/07-search-music-songs.png" alt="Song search"/><br/><sub><b>Search</b><br/>Songs</sub></td>
    <td width="33%" align="center"><img src="https://raw.githubusercontent.com/Ivorisnoob/Koda/main/Screenshots/08-search-music-artists.png" alt="Artist search"/><br/><sub><b>Search</b><br/>Artists</sub></td>
    <td width="33%" align="center"><img src="https://raw.githubusercontent.com/Ivorisnoob/Koda/main/Screenshots/18-downloads-music.png" alt="Downloads"/><br/><sub><b>Downloads</b><br/>Music and video tabs</sub></td>
  </tr>
  <tr>
    <td width="33%" align="center"><img src="https://raw.githubusercontent.com/Ivorisnoob/Koda/main/Screenshots/20-listening-history.png" alt="Listening history"/><br/><sub><b>Listening history</b><br/>Today, this week, all time</sub></td>
    <td width="33%" align="center"><img src="https://raw.githubusercontent.com/Ivorisnoob/Koda/main/Screenshots/06-listening-statistics.png" alt="Listening statistics"/><br/><sub><b>Statistics</b><br/>Plays, top artist, streak</sub></td>
    <td width="33%"></td>
  </tr>
</table>

### Player styles

Eight layouts over the same playback engine, switchable from a spinnable wheel by long-pressing the artwork in any of them.

<table>
  <tr>
    <td width="33%" align="center"><img src="https://raw.githubusercontent.com/Ivorisnoob/Koda/main/Screenshots/09-player-style-wheel.png" alt="Player style wheel"/><br/><sub><b>The style wheel</b><br/>Long-press the artwork</sub></td>
    <td width="33%" align="center"><img src="https://raw.githubusercontent.com/Ivorisnoob/Koda/main/Screenshots/10-player-classic.png" alt="Classic player style"/><br/><sub><b>Classic</b><br/>Familiar transport</sub></td>
    <td width="33%" align="center"><img src="https://raw.githubusercontent.com/Ivorisnoob/Koda/main/Screenshots/11-player-gesture.png" alt="Gesture player style"/><br/><sub><b>Gesture</b><br/>Swipe the carousel</sub></td>
  </tr>
  <tr>
    <td width="33%" align="center"><img src="https://raw.githubusercontent.com/Ivorisnoob/Koda/main/Screenshots/03-player-editorial.png" alt="Editorial player style"/><br/><sub><b>Editorial</b><br/>Two-tone magazine layout</sub></td>
    <td width="33%" align="center"><img src="https://raw.githubusercontent.com/Ivorisnoob/Koda/main/Screenshots/04-player-editorial-lyrics.png" alt="Editorial player with synced lyrics"/><br/><sub><b>Editorial</b><br/>Synced lyrics</sub></td>
    <td width="33%" align="center"><img src="https://raw.githubusercontent.com/Ivorisnoob/Koda/main/Screenshots/12-player-canvas.png" alt="Canvas player style"/><br/><sub><b>Canvas</b><br/>Full-bleed album art</sub></td>
  </tr>
  <tr>
    <td width="33%" align="center"><img src="https://raw.githubusercontent.com/Ivorisnoob/Koda/main/Screenshots/13-player-bento.png" alt="Bento player style"/><br/><sub><b>Bento</b><br/>Squishy grid of tiles</sub></td>
    <td width="33%" align="center"><img src="https://raw.githubusercontent.com/Ivorisnoob/Koda/main/Screenshots/14-player-sticker.png" alt="Sticker player style"/><br/><sub><b>Sticker</b><br/>Die-cut art, toy physics</sub></td>
    <td width="33%" align="center"><img src="https://raw.githubusercontent.com/Ivorisnoob/Koda/main/Screenshots/17-player-dial.png" alt="Dial player style"/><br/><sub><b>Dial</b><br/>Rotary ring, spin to scrub</sub></td>
  </tr>
  <tr>
    <td width="33%" align="center"><img src="https://raw.githubusercontent.com/Ivorisnoob/Koda/main/Screenshots/15-player-morph.png" alt="Morph player style"/><br/><sub><b>Morph</b><br/>A living hero shape...</sub></td>
    <td width="33%" align="center"><img src="https://raw.githubusercontent.com/Ivorisnoob/Koda/main/Screenshots/16-player-morph-shape-change.png" alt="Morph player style, seconds later"/><br/><sub><b>Morph</b><br/>...two seconds later</sub></td>
    <td width="33%"></td>
  </tr>
</table>

### Video mode

<table>
  <tr>
    <td width="33%" align="center"><img src="https://raw.githubusercontent.com/Ivorisnoob/Koda/main/Screenshots/41-home-video-recommended.png" alt="Video home"/><br/><sub><b>Home</b><br/>Personalized feed</sub></td>
    <td width="33%" align="center"><img src="https://raw.githubusercontent.com/Ivorisnoob/Koda/main/Screenshots/42-video-watch-page.png" alt="Video watch page"/><br/><sub><b>Watch page</b><br/>Engagement and up next</sub></td>
    <td width="33%" align="center"><img src="https://raw.githubusercontent.com/Ivorisnoob/Koda/main/Screenshots/43-video-playback-settings-sheet.png" alt="Playback settings"/><br/><sub><b>Playback settings</b><br/>Quality up to 2160p, speed</sub></td>
  </tr>
  <tr>
    <td width="33%" align="center"><img src="https://raw.githubusercontent.com/Ivorisnoob/Koda/main/Screenshots/44-video-comments-sheet.png" alt="Comments"/><br/><sub><b>Comments</b><br/>Read, reply, and post</sub></td>
    <td width="33%" align="center"><img src="https://raw.githubusercontent.com/Ivorisnoob/Koda/main/Screenshots/45-video-save-sheet.png" alt="Save video sheet"/><br/><sub><b>Save</b><br/>Playlists, download, dismiss</sub></td>
    <td width="33%" align="center"><img src="https://raw.githubusercontent.com/Ivorisnoob/Koda/main/Screenshots/48-library-video.png" alt="Video library"/><br/><sub><b>Library</b><br/>History and video playlists</sub></td>
  </tr>
  <tr>
    <td width="33%" align="center"><img src="https://raw.githubusercontent.com/Ivorisnoob/Koda/main/Screenshots/36-search-video-videos.png" alt="Video search"/><br/><sub><b>Search</b><br/>Videos, with date and sort</sub></td>
    <td width="33%" align="center"><img src="https://raw.githubusercontent.com/Ivorisnoob/Koda/main/Screenshots/37-search-video-playlists.png" alt="Video playlist search"/><br/><sub><b>Search</b><br/>Playlists</sub></td>
    <td width="33%"></td>
  </tr>
</table>

### Subscriptions

<table>
  <tr>
    <td width="33%" align="center"><img src="https://raw.githubusercontent.com/Ivorisnoob/Koda/main/Screenshots/46-subscriptions-feed.png" alt="Subscriptions feed"/><br/><sub><b>Feed</b><br/>Latest from everything you follow</sub></td>
    <td width="33%" align="center"><img src="https://raw.githubusercontent.com/Ivorisnoob/Koda/main/Screenshots/47-manage-subscriptions-empty.png" alt="Manage subscriptions"/><br/><sub><b>Manage</b><br/>Import, export, and groups</sub></td>
    <td width="33%"></td>
  </tr>
</table>

### Accounts and profiles

<table>
  <tr>
    <td width="33%" align="center"><img src="https://raw.githubusercontent.com/Ivorisnoob/Koda/main/Screenshots/21-profiles-sheet-signed-out.png" alt="Profiles, no account"/><br/><sub><b>Profiles</b><br/>Signed out is just a profile</sub></td>
    <td width="33%" align="center"><img src="https://raw.githubusercontent.com/Ivorisnoob/Koda/main/Screenshots/49-profiles-sheet-signed-in.png" alt="Profiles with an account"/><br/><sub><b>Profiles</b><br/>Switch without re-authenticating</sub></td>
    <td width="33%" align="center"><img src="https://raw.githubusercontent.com/Ivorisnoob/Koda/main/Screenshots/38-settings-account-signed-out.png" alt="Account settings, signed out"/><br/><sub><b>Account</b><br/>Signed out</sub></td>
  </tr>
  <tr>
    <td width="33%" align="center"><img src="https://raw.githubusercontent.com/Ivorisnoob/Koda/main/Screenshots/39-session-cookies-sign-in-sheet.png" alt="Session cookie sign-in"/><br/><sub><b>Session cookies</b><br/>The fallback sign-in path</sub></td>
    <td width="33%" align="center"><img src="https://raw.githubusercontent.com/Ivorisnoob/Koda/main/Screenshots/40-settings-account-signed-in.png" alt="Account settings, signed in"/><br/><sub><b>Account</b><br/>Connected</sub></td>
    <td width="33%"></td>
  </tr>
</table>

### Settings

<details>
<summary>All eleven categories</summary>

<table>
  <tr>
    <td width="33%" align="center"><img src="https://raw.githubusercontent.com/Ivorisnoob/Koda/main/Screenshots/22-settings-hub.png" alt="Settings hub"/><br/><sub><b>Hub</b><br/>Every row shows its live value</sub></td>
    <td width="33%" align="center"><img src="https://raw.githubusercontent.com/Ivorisnoob/Koda/main/Screenshots/31-settings-hub-system-and-about.png" alt="Settings hub, system and about"/><br/><sub><b>Hub</b><br/>System and About</sub></td>
    <td width="33%" align="center"><img src="https://raw.githubusercontent.com/Ivorisnoob/Koda/main/Screenshots/24-settings-appearance.png" alt="Appearance settings"/><br/><sub><b>Appearance</b><br/>Theme, palette, AMOLED</sub></td>
  </tr>
  <tr>
    <td width="33%" align="center"><img src="https://raw.githubusercontent.com/Ivorisnoob/Koda/main/Screenshots/23-settings-color-palette.png" alt="Color palette picker"/><br/><sub><b>Color palette</b><br/>Dynamic, or two dozen fixed</sub></td>
    <td width="33%" align="center"><img src="https://raw.githubusercontent.com/Ivorisnoob/Koda/main/Screenshots/27-settings-player-styles.png" alt="Player style picker"/><br/><sub><b>Player</b><br/>Style picker with wireframes</sub></td>
    <td width="33%" align="center"><img src="https://raw.githubusercontent.com/Ivorisnoob/Koda/main/Screenshots/28-settings-player-styles-scrolled.png" alt="Player style picker, scrolled"/><br/><sub><b>Player</b><br/>The rest, plus artwork colors</sub></td>
  </tr>
  <tr>
    <td width="33%" align="center"><img src="https://raw.githubusercontent.com/Ivorisnoob/Koda/main/Screenshots/25-settings-playback-and-quality.png" alt="Playback settings"/><br/><sub><b>Playback</b><br/>Crossfade, queue, history</sub></td>
    <td width="33%" align="center"><img src="https://raw.githubusercontent.com/Ivorisnoob/Koda/main/Screenshots/26-settings-streaming-quality.png" alt="Streaming quality settings"/><br/><sub><b>Quality</b><br/>Per network, music and video</sub></td>
    <td width="33%" align="center"><img src="https://raw.githubusercontent.com/Ivorisnoob/Koda/main/Screenshots/29-settings-content-and-feeds.png" alt="Content and feeds settings"/><br/><sub><b>Content and feeds</b><br/>Mode, Shorts, recommendations</sub></td>
  </tr>
  <tr>
    <td width="33%" align="center"><img src="https://raw.githubusercontent.com/Ivorisnoob/Koda/main/Screenshots/30-settings-subscriptions.png" alt="Subscription settings"/><br/><sub><b>Subscriptions</b><br/>Where they live, how they refresh</sub></td>
    <td width="33%" align="center"><img src="https://raw.githubusercontent.com/Ivorisnoob/Koda/main/Screenshots/32-settings-storage-and-cache.png" alt="Storage and cache settings"/><br/><sub><b>Storage and cache</b><br/>Size limit and one-tap clear</sub></td>
    <td width="33%" align="center"><img src="https://raw.githubusercontent.com/Ivorisnoob/Koda/main/Screenshots/33-settings-notifications.png" alt="Notification settings"/><br/><sub><b>Notifications</b><br/>Android 16 Live Updates</sub></td>
  </tr>
  <tr>
    <td width="33%" align="center"><img src="https://raw.githubusercontent.com/Ivorisnoob/Koda/main/Screenshots/34-settings-local-library.png" alt="Local library settings"/><br/><sub><b>Local library</b><br/>Device music scanning</sub></td>
    <td width="33%" align="center"><img src="https://raw.githubusercontent.com/Ivorisnoob/Koda/main/Screenshots/35-settings-advanced.png" alt="Advanced settings"/><br/><sub><b>Advanced</b><br/>OEM and battery fixes</sub></td>
    <td width="33%"></td>
  </tr>
</table>

</details>

---

## Roadmap

Where Koda is going lives in **[`ROADMAP.md`](ROADMAP.md)**. It is not a checklist. Each item explains what it is, who it is for, what already exists in the codebase that it can reuse, and the constraints it has to fit inside. Several entries are diagnoses rather than wishes, with the cause already traced.

The current tracks:

- **Interface:** proper channel screens, per-channel notification settings, saving other people's playlists and albums, a simpler "Discover" home for music mode, a playlist upgrade with custom cover art, predictive back, haptics, and one consistent heading system.
- **Playback:** carrying audio across a mode switch without a gap, and a crossfade that genuinely overlaps.
- **Foundations:** backup and restore, playlist import and export, surviving process death, and a dependency audit.
- **Reach:** Android Auto done properly, voice search, a home screen widget and Quick Settings tile, tablet layouts on every screen, Wear OS, and Android TV.

It also carries a **Known defects** section for bugs that have been diagnosed but not yet fixed, and a **Shipped** section listing everything already done.

If you are about to file a feature request, that document is the fastest way to find out whether it is already planned, and for a few things, why it deliberately is not.

---

## Technical Stack

| Component | Technology |
|-----------|------------|
| Language | Kotlin |
| UI Framework | Jetpack Compose |
| Design | Material 3 Expressive |
| Architecture | MVVM with StateFlow (no DI framework) |
| Playback | Media3 ExoPlayer / MediaLibraryService |
| Networking | OkHttp |
| Data Extraction | NewPipe Extractor and direct InnerTube |
| Image Loading | Coil |
| Concurrency | Kotlin Coroutines and Flow |
| Min SDK | 30 (Android 11) |
| Target SDK | 36 (Android 16) |

---

## Project Structure

<details>
<summary>Expand the source tree</summary>

```
app/src/main/java/com/ivor/ivormusic/
├── data/                    # Data layer
│   ├── YouTubeRepository    # YouTube Music and video via NewPipe and InnerTube
│   ├── DownloadRepository   # Download queue, state, and progress
│   ├── DownloadStorage      # Writes downloads into Downloads/Koda via MediaStore
│   ├── DownloadMuxer        # Joins separate video and audio tracks into one MP4
│   ├── DownloadMigration    # One-time move of older downloads to shared storage
│   ├── PlaylistRepository   # Local user playlists
│   ├── LikedSongsRepository # Local liked songs
│   ├── LocalSubscriptions.. # Device-only channel subscriptions
│   ├── SubscriptionActions  # Decides whether Subscribe writes to device or account
│   ├── SubscriptionTransfer # Import/export: NewPipe json/backup, CSV, OPML
│   ├── NotInterested..      # Hidden videos and blocked channels
│   ├── NotInterestedActions # Local hide plus best-effort account feedback
│   ├── RecommendationEngine # Local taste profile and queue continuation
│   ├── StatsRepository      # Listening statistics
│   ├── LyricsRepository     # Synced lyrics (LRCLIB)
│   ├── UpdateRepository     # In-app updates from GitHub Releases
│   ├── SessionManager       # The active profile's session cookies
│   ├── ProfileManager       # Profile roster: YouTube accounts and local ones
│   ├── AccountSwitcher      # Switching, and everything it has to invalidate
│   ├── ThemePreferences     # All app settings
│   └── Models               # Song, Playlist, VideoItem, SubscriptionItem, ...
├── service/                 # Background services
│   ├── MusicService         # MediaLibraryService with live-progress notification
│   └── DownloadService      # Keeps downloads running in the background
└── ui/                      # Presentation layer
    ├── home/                # Two music homes (classic and Spotlight), video feed
    ├── library/             # Playlists, liked songs, statistics
    ├── player/              # Music player, queue, and the eight player styles
    ├── video/               # Video player, live streams and chat, comments, subscriptions
    ├── shorts/              # Opt-in Shorts player
    ├── search/              # Search and video explore
    ├── artist/              # Artist and album pages
    ├── downloads/           # Download manager
    ├── settings/            # App preferences and updates
    ├── auth/                # YouTube sign in
    ├── onboarding/          # First run flow
    ├── components/          # Reusable UI components
    └── theme/               # Material 3 theming and color palettes
```

</details>

---

## Building from source

### Prerequisites

- Android Studio Ladybug or newer
- A device or emulator running API 30 (Android 11) or higher

### Debug build

```bash
git clone https://github.com/Ivorisnoob/Koda.git
cd Koda
./gradlew assembleDebug
```

Or open the project in Android Studio, sync Gradle, and hit run.

### Release build

Configure the keystore in `app/build.gradle.kts`, or set the `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD` environment variables, then:

```bash
./gradlew assembleRelease
```

Both build types split by ABI: `arm64-v8a`, `armeabi-v7a`, and a universal APK containing both.

### Where to read next

| Document | What is in it |
|----------|---------------|
| [`CLAUDE.md`](CLAUDE.md) | Architecture, data flows, and the InnerTube layer in depth |
| [`DESIGN.md`](DESIGN.md) | The design system: shape, motion, color, and the rules for UI code |
| [`ROADMAP.md`](ROADMAP.md) | Where the app is going, and the defects already diagnosed |
| [`CONTRIBUTING.md`](CONTRIBUTING.md) | How to open a pull request |
| [`SECURITY.md`](SECURITY.md) | Reporting a vulnerability, and what is in scope |

---

## Community

Join the Telegram chat at **[t.me/ivorisnoob_chat](https://t.me/ivorisnoob_chat)** for beta builds, help with a problem, and feature discussion. Bug reports and feature requests are still best filed as [GitHub issues](https://github.com/Ivorisnoob/Koda/issues) so they do not get lost.

---

## Contributing

Contributions are welcome, whether it is a bug report, a feature idea, or a pull request. See [CONTRIBUTING.md](CONTRIBUTING.md) to get started, and [ROADMAP.md](ROADMAP.md) if you are looking for something to pick up. The **Known defects** section lists bugs that have already been traced to a file and a line.

Everyone taking part is expected to follow the [Code of Conduct](CODE_OF_CONDUCT.md).

---

## Security

Found a vulnerability? Please do not open a public issue or post it in the Telegram chat. Report it privately through the [Security tab](https://github.com/Ivorisnoob/Koda/security/advisories), and see [SECURITY.md](SECURITY.md) for what is in scope.

---

## License

Koda is licensed under the **GNU General Public License v3.0**. See the [LICENSE](LICENSE) file for the full text.

---

<p align="center">
  Made by <b>ivorisnoob</b>
  <br/>
  Copyright 2026
</p>
