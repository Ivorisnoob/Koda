# Last.fm

[judgement September 2026] Last.fm is an optional, device-wide music integration, independent of YouTube profiles. It is off on fresh installs. Settings > Last.fm and settings search are its entry points. Videos are not scrobbled, and Last.fm history is displayed separately rather than imported into Koda's library or used to alter recommendations.

## Authentication and setup

[judgement September 2026] Koda ships no Last.fm application credentials. Each user registers their own application at https://www.last.fm/api/account/create and enters the **API key and shared secret** in Settings > Last.fm. An API key by itself cannot scrobble. Koda never collects a Last.fm password. There is no build-time credential and no `BuildConfig` field: nothing in the repository or in a distributed APK carries a Last.fm secret.

The registration form's callback URL is unused - Koda uses the desktop/token flow and never passes `cb`, so the field can be left blank.

The flow uses `auth.getToken`, opens `https://www.last.fm/api/auth/` with the key/token, then exchanges the authorized token using `auth.getSession` when the user taps Finish sign-in. This is Last.fm's documented desktop/token flow and does not require a custom callback scheme. Tokens expire after one hour. The pending token survives process recreation; unsaved credential fields deliberately do not enter saved-instance state.

## Storage, privacy, and sync

`LastFmRepository` is a process-wide coordinator because Settings and `MusicService` must share connection status, serialize queue draining, and cancel the same active network calls when disabled. Its scope uses the main dispatcher; OkHttp performs cancellable asynchronous network calls. Player snapshots stay on the player's application thread.

`lastfm_private` uses the existing AndroidX encrypted-preference mechanism for credentials, enabled state, pending authorization, cached account data, and the offline queue. It is excluded from Android cloud backup and device transfer and is not in Koda's manual backup allowlist. This account/session store intentionally does not use `ThemePreferences`: restoring a general preference must not turn on a third-party integration on another device.

[scar September 2026] **Opening the encrypted store must never be able to take the app down.** The 5.0 betas opened it with no recovery, from the music service's first player tick on the main thread, for every user whether Last.fm was on or not. Any failure to open it - a keystore that refuses, a keyset sealed under a key that no longer exists, app data copied to a new phone by an OEM transfer tool that ignores the backup rules - was an uncaught exception on the main thread and a crash on every launch, because the unreadable file stayed. Two rules now:

- `openPrivatePrefs` recovers the way `ProfileManager.sharedPrefs` does: delete the file, rebuild, and fall back to `VolatileSharedPreferences` if that fails too. **It deletes only its own file, never the master key.** `MasterKey.DEFAULT_MASTER_KEY_ALIAS` is shared with the YouTube session store; deleting it from here would make that store unreadable. (`ProfileManager`'s recovery does delete it, which is one way `lastfm_private` becomes unreadable, and why this recovery has to exist.)
- The service does not build the repository unless Last.fm might be on. `lastfm_hint` is a plain preference mirroring `enabled`, written by a collector over `state` so every path that changes it is covered. `LastFmRepository.mayBeEnabled` reads it; with no hint recorded yet (a store written by a build before the hint existed) an existing `lastfm_private.xml` counts as possibly enabled, so the repository is built once and writes the hint. When it may be on, `MusicService` builds the repository on `Dispatchers.IO` and hands the tracker back to the main thread. The hint is only an optimization - the encrypted store stays authoritative - and it is excluded from Android backup and device transfer like the store itself.

The Settings hub row still builds the repository on the main thread when Settings opens; that is user-initiated and now cannot crash.

- Turning Last.fm off cancels current calls, stops future requests and recording, and clears unsent plays. Already received requests cannot be retracted from Last.fm. Saved credentials and the cached account snapshot remain until Disconnect and forget account.
- Incognito and Local Only cancel calls and pause recording/sync. Previously qualified queued plays are retained for later; listening during the pause never becomes a queued play. A generation change invalidates partially accumulated listening even if the user quickly toggles back.
- Reauthorizing the same Last.fm account preserves queued plays. Authorizing a different account discards the previous account's queue and cache. No play is submitted under a different account.
- Only non-live music with a title and artist qualifies. `LastFmListeningClock` counts audible wall time, not playback position; seeks do not grant listening time, buffering/pauses do not count, and delayed scheduler ticks are capped. Duration must exceed 30 seconds, and the threshold is half the duration or four minutes, whichever is earlier.
- `LastFmPlaybackTracker` follows queue occurrence ids and the active crossfade player. Media3 repeat transitions explicitly reset the clock. It follows the active player only; the incoming overlap before handoff is not counted.
- Now-playing updates are best effort. Qualified plays are persisted with their original UTC start timestamp. The queue holds at most 1,000 plays and reports when full. It drains one acknowledged play at a time; ignored results are surfaced, while transient failures and daily-limit responses retain the play. Last.fm may deduplicate retries after an ambiguous network failure using the original timestamp.
- Background retry runs every five minutes while the music service exists and its tracker has been built (see the hint above). There is no scheduled worker after the service stops: the queue resumes with service activity or Sync now. Network errors and API transient/rate-limit errors impose a five-minute cooldown.
- The page refreshes public profile totals and the latest 20 plays on connection, on entry, through Sync now, and every minute while the page is STARTED. The cached snapshot and its update time survive offline restarts. A successful read does not imply that a queued write succeeded.

## API evidence and verification limits

[verified September 2026] Read the current official [API index](https://www.last.fm/api), [authentication specification](https://www.last.fm/api/authspec), [token authorization guide](https://www.last.fm/api/desktopauth), [Scrobbling 2.0 guide](https://www.last.fm/api/scrobbling), [track.scrobble](https://www.last.fm/api/show/track.scrobble), [user.getInfo](https://www.last.fm/api/show/user.getInfo), and [user.getRecentTracks](https://www.last.fm/api/show/user.getRecentTracks). HTTPS `ws.audioscrobbler.com/2.0/` was probed locally with an intentionally invalid key: HTTP 403, JSON error 10. The scratch probe lives under ignored `.probe/`.

No application credentials were available during implementation, so the JVM fixtures are contract examples rather than captured authenticated responses. [verified September 2026] The maintainer has since tested the integration end to end with a registered application and a real account: browser authorization, the session exchange and live scrobbling work.

## UI intent and screen checks

[judgement September 2026] Foundational Expressive intensity, a profile hero with avatar, display name and formatted lifetime scrobble count; the recent-listening view also highlights a fresh now-playing entry. Connected tabs separate listening history from account management. History is grouped by local date with album artwork, artist, album and play time. The account tab shows optional country and registration date, keeps the master switch easy to find, and explains scrobbling and privacy. Missing images use Koda's existing artwork fallback; image requests stop when disabled or paused. Cached now-playing data older than two minutes is not labeled live. Browser authorization has a direct restart action, and credentials are only cleared from the form after a pending token exists. The page uses Koda's existing detail scaffold, cards, toggle row, and hub row; theme-derived container/text roles, expressive button shapes and LoadingIndicator; wrapping login choices and scrollable content with IME padding. No new theme, custom animation, or density override is introduced. Material component motion inherits the system animation scale.

Relevant skill references: `material-3-expressive/references/m3-buttons-specs-tokens.md` (`md.comp.button.filled`, `md.comp.button.tonal`, disabled and pressed groups), and Koda's `DESIGN.md`. Runtime state is lifecycle-collected from the ViewModel; credentials are transient Compose state.

Device checks remain with the maintainer: compact/landscape and tablet windows, large font/interface scale, keyboard visibility for both credential fields, screen-reader button/switch labels, dynamic colors and AMOLED, settings back navigation with the mini-player present, and browser return after process recreation. No emulator or device screenshots were used during implementation.

Local verification: `compileDebugKotlin` and `testDebugUnitTest` passed (410 tests, including 22 Last.fm tests). `lintDebug` completed with repository-wide errors and the expected MissingTranslation findings for the new base-locale strings; it is not a passing gate. The new Kotlin files had no lint errors. No packaging tasks, emulator, remote writes, or commits were performed.

**Uploads from music searchs Videos tab are never scrobbled.** [judgement September 2026] Their artist field is a channel name, so a scrobble would file a fan edit under an uploader in the users Last.fm history. Song.isUpload rides the playback item as EXTRA_MUSIC_IS_UPLOAD and LastFmPlaybackTracker skips it; radio songs queued after an upload are ordinary and still scrobble.
