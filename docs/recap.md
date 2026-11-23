# Listening history and Recap (`data/StatsRepository.kt`, `service/ListeningRecorder.kt`, `data/recap/`, `ui/recap/`)

Reference detail moved out of `CLAUDE.md` so it loads only when needed. `CLAUDE.md` keeps the summary, the invariants and the silent-failure index; this file keeps the reasoning and the scars. Markers (`[verified]`, `[scar]`, `[judgement]`, `[drifts]`) mean what `CLAUDE.md` says they mean. If a change contradicts a sentence here, fix the sentence in the same commit.

Recap (November 2026) is a "year in review" for any week, month, year or all time, built on the device from the active profile's own plays. It needed the history under it fixed first, so the two live here together.

## The history store

**No cap, one play per line, oldest first.** [judgement November 2026] It used to be one JSON array trimmed to the newest 5,000 plays and rewritten whole on every play: a heavy listener's history reached back about two months, so a yearly recap could not exist, and the cost of a play grew with the history. Now a play is one appended line whatever the size, and the file's name did not change (`play_history.json` / `play_history_<id>.json`), so backups, profile copies and the profile switch find it where they always did. `readHistory` reads both formats - an array still arrives from older installs and from backups - and the first write after that converts it. A line that does not parse (a write cut by a crash) is skipped, not allowed to cost the whole history.

**A play's time is amended in place.** The play being heard is the last line almost always, so `updateListened` rewrites that line and nothing else; anything else (the history was edited meanwhile, the file is still an array) falls back to a full rewrite through a `.partial` file and a rename.

**Backups take the array** (`exportForBackup`), so a backup made on this build restores on an older one, which would read the line format as nothing. A restore writes it back as text, and the reader takes it either way.

**Reads are cached per file**, keyed by its size and modification time and updated by every write here, so the screens that read the history (Home's shelves, Stats, History, the profile, Recap, recommendations, Auto's Recent) do not each parse it. It is a cache, not process-wide state: it cannot answer anything the file does not say.

**`listenedMs` is wall time while playing**, measured by the recorder; plays from before it existed carry null. `heardMs` is the measured time, else the song's length - what every total sums, and why a total containing old plays is said as "about".

## The recorder

**Plays are recorded by the service.** [judgement November 2026] They used to be written by `PlayerViewModel` fifteen seconds after a song started. The ViewModel lives with the activity, so music playing with the app's screen gone - swiped from recents, started from a headset, Android Auto - was never recorded (read from the code; not reproduced on a device), and the entry carried the song's length rather than anything heard. `ListeningRecorder` is sampled every 250 ms from the audible player in the same loop as the Last.fm tracker, so it follows a crossfade's engine swap; its rules are the pure `ListeningLedger` (`service/core/`, tested in `ListeningLedgerTest`):

- Per queue occurrence, not media id: a song twice in a queue is two plays. Repeat-one comes back as the same occurrence, so the service calls `restart()` on `MEDIA_ITEM_TRANSITION_REASON_REPEAT`.
- Recorded once heard for 15 s, the rule the history always had: skips and a cold-start restore that never played leave nothing.
- Amended every 30 s of further listening, on pause and when the occurrence changes, so a killed process loses at most that much of one song's time and never the play. `flushFinal()` writes the last amendment synchronously from `onDestroy`, bounded to 400 ms, as session checkpoints do.
- A sample may add at most 1.5 s: a gap longer than that is a frozen process, not listening.
- Live streams are not plays. Incognito is enforced inside `StatsRepository`; the history switch is read fresh at record time.

`PlayerViewModel` still reports the account-side play (`reportPlayback`) and steers its shuffles; it no longer writes the local history.

## The engine

`RecapEngine` (`data/recap/`, tested in `RecapEngineTest`) is pure: history, zone and today in, a `Recap` out. Every story is null when the plays do not support it, and the story then leaves its card out (`cardsFor`), so a quiet week is a short story rather than a padded one. The thresholds, each a judgement:

- **Top song**: most distinct days, then plays, then time - the song you kept coming back to, not the one looped for an afternoon.
- **The overtake**: the last day the lead in plays changed hands for good; a tie keeps the old leader. Only in periods of 14 days or more, with the passed song having led for at least 3 days and reached 5 plays.
- **Came back**: the longest gap (21 days or more) before a song was played again inside the period, gaps measured across the whole history.
- **Long sitting**: plays with less than 30 minutes between one's end and the next one's start; at least 4 plays and 40 minutes.
- **New this time**: an artist first heard inside the period and played on 3 or more of its days. Never when the history starts inside the period, where everything would be "new".
- **Odd hour**: a play between 01:00 and 04:59 at an hour with 3 plays or fewer, in a period of 20 plays or more.
- **Day parts**: the middle of an artist's busiest three hours, 05-11 morning, 12-16 afternoon, 17-20 evening, else night.
- **Catalog number**: FNV-1a over the period's plays, so the same listening always gets the same number.

**No genres, moods or personalities.** [judgement November 2026] The history holds none. The only honest genre source probed was MusicBrainz's community tags (good data, but rate-limited to about one request a second, answering 503 past it), and the owner declined sending listeners' artists to an outside service. The day-part ridgeline ("Nights were Cigarettes After Sex") is the substitute: it describes what was done, not a label.

## The story

The shelf is the `recap` route; the story is `RecapStoryOverlay`, hosted above everything in `MainActivity` like Standby (always dark, a scheme built from the period's top cover, light bar icons while open). One activity-scoped `RecapViewModel` serves both. Stats and the profile open the shelf through `LocalRecapLauncher`, which throws without its provider rather than letting a call site ship half-wired.

**One travelling window.** A card does not draw its own hero cover: it marks where the window goes with `RecapStoryScope.slot(...)` on a laid-out box, and the window springs there, changing shape on the way (`Morph` between Material shapes fitted to a circle, the mini player's scar). `follow = true` snaps it to a moving slot every frame - the race's head, the calendar's scrub. The slot lives in its own state, read only by the window and the room, so a slot moving every frame recomposes those two and not the story.

[scar] `onGloballyPositioned` fires when layout changes, not when its callback does: a slot that changes its cover in place (the door's cycling covers) never re-reported until `slot()` became a composable that also reports on any change of what it asks for.

**Every card's choreography is a function of its own clock** - seconds since it appeared - through `springAt` (Standby's `appleSpring` in closed form) and `rise`. Any frame can be drawn on its own, a card re-entered replays from the start, and the system animator scale stops it at the end.

**Gestures are one handler** on the story, reading the Final pass so anything a card consumed (a button, the calendar's drag, the plays list) is left alone. A tap is a press that never left touch slop: a separate tap detector let every swipe also count as a tap on release.

**Cover colours** are worked out once per song from a 64 px cover through Coil (`RecapCoverColours`): the vibrant swatch when there is a real amount of it, else what the cover mostly is, so a black sleeve is black. They are kept in `recap_cover_colours.json`, deliberately out of backups (they rebuild themselves), fetched most-visible first, and drawn through `onRoom()` (lightness 0.34-0.74) or `deep()` for spines. They are the data, the fourth exception to invariant 1.

## Sharing

Three designs (the pressing, top songs, top artists) in three formats (Story 1080x1920, Feed 1080x1350, Square 1080x1080), laid out on a 360-unit grid at three pixels a unit and captured from their `GraphicsLayer` as the lyrics card is; only the preview is scaled. Export is `LyricsShareExport.shareImage` / `saveImage`. "Show numbers" takes the play counts and time off, for sharing taste without volume. The pressing's key is printed on it so a stranger can read it.

## Open

- Video history stays a preference string capped at 1,000; unlimited needs a file store like this one.
- A year of a heavy listener's history is a few thousand arcs drawn per frame while the pressing cuts; grooves under 2 px are skipped. Not yet measured on a low-end phone.
