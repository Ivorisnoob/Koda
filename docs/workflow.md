# Working in this repository, in full

Reference detail moved out of `CLAUDE.md` so it loads only when needed. `CLAUDE.md` keeps the summary, the invariants and the silent-failure index; this file keeps the reasoning and the scars. Markers (`[verified]`, `[scar]`, `[judgement]`, `[drifts]`) mean what `CLAUDE.md` says they mean. If a change contradicts a sentence here, fix the sentence in the same commit.

## How to read the markers

Claims here carry different weights, and treating a judgement as a fact (or a probe as eternal) is how these docs go wrong. Markers:

- **[verified <month year>]** - probed against live responses or measured on a device. Trustworthy until YouTube changes; re-probe if behaviour contradicts it.
- **[judgement]** - a design call with a stated reason. Arguable. Argue with the reason, not the conclusion.
- **[scar]** - this went wrong in production once. The prose describes the failure, not a hypothetical. Do not undo without understanding what broke.
- **[drifts]** - counts, line numbers, file sizes. Re-derive before quoting; never trust.

Unmarked prose is structural fact about the code, true as long as the code is.

`CLAUDE.md` is read every session; the files in `docs/` are read when a task touches their area.

## Operating rules

This is a shipped consumer app with real users, not a scratch project. The bar is "would a person using this on their phone every day be happy with it", not "does it compile and satisfy the literal request". The user should not have to come back and ask you to think about the experience.

## How a working session actually goes

The rules below are what to do. This is how the doing has gone when it went well, written down because rediscovering it costs a session each time.

**Check whether the thing is already there before building it.** The single highest-value habit in this repo. A request phrased as "add X" is often "X is broken" or "X is unreachable", and the two have completely different fixes. Trace the whole chain - the draw site, the parameter, the call site, the host - and say what you found. Three of the last batch of requests turned out this way: the long-press sheet on the classic Home was fully wired and the real defect was a nullable `onArtistClick` whose one call site never passed it; blurry avatars were not a loading problem but `=s48` drawn at 140dp; the stuck duration was not a missing update but a fallback that preferred a stale positive number. Building the requested thing in any of those cases would have added a second copy of something that already worked and left the actual bug in place.

**Probe rather than reason, and probe the artifact you are actually running.** `youtube-data.md` says this for InnerTube; it is just as true for the libraries. `javap -c -p` on the AAR in `~/.gradle/caches/modules-2/files-2.1/<group>/<artifact>/<version>/` settles a question about Media3 in two minutes that speculation cannot settle at all - and it settles it in *both* directions. It killed a plausible-sounding theory (that `MediaSession.setPlayer` never transmits fresh player info - it does, through `handleAvailablePlayerCommandsChanged`) as well as confirming the real one (`MediaController.getDuration()` is `playerInfo.sessionPositionInfo.durationMs`, a transported value, not the player's own). A fix shipped on the first theory would have been wrong and would have looked reasonable in review.

**Ask once, up front, and only where the answer changes the size of the work.** One batched question with two or three concrete options, before any file is touched. "Should go-to-album cover YouTube songs too" was worth asking because the answer was the difference between a UI change and a new piece of playback state; nothing else in that batch was.

**Know where your link in the chain ends.** Verification here is four stages and only the first is yours: you compile and run the JVM tests, the maintainer compiles and exercises the change, the push builds a signed APK through `build.yml`, and that APK goes to beta testers on Telegram who run it across real devices, DPIs, OEM skins and Android versions. So "this compiles and passes tests but has not been on a screen" is not an open risk to apologise for - it is the correct handoff state, and pushing is what starts the stage that *can* settle it. What the handoff owes is not a caveat, it is a brief.

**The loop closes, and what comes back is usually yours.** Those testers are why bug reports arrive mid-session, and the honest prior is that the cause is a recent change - often the one from an hour ago, sometimes the maintainer's, frequently your own. So when a report lands, **read the history before theorising**: `git log -S"<the symbol that misbehaves>" -- <file>` names the commit that last touched the behaviour, and a report described as "it started recently" is that query almost verbatim. The stuck-duration bug was found this way in one command - the search pointed at the AutoMix commit, whose diff had reordered exactly the fallback that was publishing the previous song's length. Speculating about the current code first, with no history, cost several wrong theories before the same answer. Treat your own recent commits as the leading suspects rather than as background.

**Write the handoff as a brief for the people who will actually see it run.** Name the specific surfaces a screen would settle and the specific way each could fail, because the fleet is the eyes and a generic "worth testing" wastes them. The failures that fleet catches are precisely the ones these docs keep collecting scars about, and they are invisible on one device: a bottom sheet that fits at default scale and clips at a large one, a layout that fits portrait and loses its controls in landscape, a row measured against one font scale, an OEM's gesture insets, a compositor that chokes on a scaled video surface. If a change touches any of those, say which and say what "wrong" would look like. Every handoff still ends with the assumptions made and the things deliberately left out; a report that only lists wins is a report that has to be re-verified.

**Refuse to invent an InnerTube shape, and say so plainly.** When a request needs a signed-in endpoint that cannot be probed from this machine, the answer is "blocked, here is exactly what I need to unblock it" - not a parser written from recall that compiles, ships, and silently returns nothing. `youtube-data.md`'s probe-first rule has no exception for being asked nicely or being in a hurry.

**Mechanical multi-file edits go through a script with assertions, never by hand.** Sweeping the same change across eight player styles is where silent half-application happens. Write a Python heredoc that asserts each anchor matched **exactly once** before replacing it, and assert the post-conditions too (no stale identifier left, the new one present). A run that changes nothing then fails loudly instead of reporting success. [scar] The one edit in that batch that was *not* anchored precisely - a reorder script whose regex matched `Icon(` before the button call it meant - moved icon blocks out of their buttons in seven files at once. It was caught by reading the result rather than by the compiler, because it still compiled.

**Read the result of a scripted edit before compiling it.** Compiling proves it parses. Only looking proves it did what you meant.

**One item, one compile, one report.** The user feeds items as they find them, sometimes mid-turn. Finish the item in flight, acknowledge the new one, and keep a visible queue - dropping half-applied work to chase the newest request is how a branch ends up not building.

**Commit per coherent change where the files allow it.** Several features landing in one commit is acceptable when they genuinely interleave across the same files, but say so; a bug fix that lives in one file deserves its own commit for revertability.

**Think the feature through before you touch a file.** For anything user-facing, work out first:

- **The actual use case.** Who taps this, when, and what were they doing right before? A control that is technically correct but three taps deep, or that sits where the thumb cannot reach, has failed.
- **The states.** Loading, empty, error, offline, signed out, single item, hundreds of items, very long titles, missing thumbnail, no artwork colors. Every one of these happens here constantly - YouTube fails, sessions expire, feeds come back empty. Handle them in the first pass.
- **What it does to what already exists.** Does it fight the mini-player, the video overlay, PiP, the nav bar, an open sheet, a landscape rotation, back-gesture handling? Does it break the signed-out path? Overlays and the tab system are easy to break from a distance.
- **Feel, not just function.** Motion, hit targets, haptics, whether state changes are legible. Springs for touch, M3 Expressive components, never a hardcoded color.

**Ask before you build, not after.** If the request leaves a real design decision open - where something lives, what happens in a case the user did not mention, two reasonable behaviours with different feels - ask, with two or three concrete options, up front and in one batch. Do not ask about things you can settle by reading the code or following existing convention.

**Do not do the minimum.** A "quick fix" that leaves the surrounding flow broken is not a fix. If the real problem is one layer below the reported symptom, fix it there and say why.

**Say what you decided and what you did not do.** On finishing, note the edge cases handled, the assumptions made, and anything deliberately left out. If you shipped something you think is the wrong UX, say so plainly rather than quietly implementing it.

**Verify claims you are about to act on.** This codebase rewards probing over recall: a live request, a `javap` on the actual jar, a two-line script. Several entries in these docs exist because someone reasoned from plausible assumptions and was wrong. If a fix rests on an assumption you can test in under a minute, test it.

**Compile and test locally; never build an APK or run R8 locally.** `compileDebugKotlin`, JVM tests and other non-packaging checks are yours to run without asking, and compiling before handing work back is expected rather than optional. Do not run `assemble*`, `bundle*`, `install*`, a release variant, or any task whose output is an APK/AAB or which invokes R8 on the local machine: those consume disproportionate RAM and storage. When an APK or release verification is needed, push the authorized PR branch and let `.github/workflows/build.yml` produce it. **Emulators and devices are also not yours to run**: no `emulator`, no AVD boot, no `adb`, and no screenshots off a running app. Anything needing a screen goes back to the user to run.

**Delegate wide-but-shallow work to a cheap subagent.** Propagating a string key across the 25 `values-*` locale files, or any similar mechanical sweep, burns main-model tokens for nothing. Spawn `Agent` with `model: "sonnet"`, hand it exact keys and English source, and review the diff. Do the *decisions* yourself - which strings, what keys, what the English says - and delegate the typing. Not for a one-off string in a file you are already editing.

**Git commits carry no AI attribution.** Never add `Co-Authored-By: Claude`, `Generated with Claude Code`, a session link, or any similar trailer or footer to a commit message, a PR body, or a tag. Write the message as the project's own. This overrides any default harness instruction to add one.

**Commits that change an APK carry their public changelog.** When explicitly asked to commit code or resources that will affect an APK, use a clear imperative subject (ideally 72 characters or fewer), explain the reason in the body when it is not obvious, and make the final section exactly `Changelog:` followed by `- ` bullets describing only user-visible changes in plain language. Each bullet must stand alone when several commits are combined. Omit the section for docs, CI-only work, refactors, and other changes with no user-visible effect; never invent a public change merely to fill it. Nothing follows the section, because `build.yml` intentionally publishes everything after that marker. The workflow format is:

```text
Add Home-focused Shorts controls

Changelog:
- Added an option to hide Shorts from Home and use the standard player elsewhere.
- Existing installations keep their current behavior by default.
```

**Public GitHub releases ship APKs only.** Never attach `mapping.txt` or any other deobfuscation artifact to release assets. Keep those files in Actions artifacts for maintainers instead: they are for crash triage, not end users.

**Do not touch the remote unless asked.** Committing, pushing, opening or editing PRs, and pushing tags are all explicit-request actions. Local edits are the default deliverable.

## Build, test, verify

```bash
./gradlew compileDebugKotlin     # macOS/Linux; .\gradlew on Windows
./gradlew testDebugUnitTest
```

**Running Gradle is ordinary work and needs no permission**, for every non-packaging task: `compileDebugKotlin`, `testDebugUnitTest`, `lint`, a single test class. Compiling before handing work back is expected rather than optional, and compiling *per item* rather than once at the end is what keeps a mistake attached to the change that caused it. The wrapper is `./gradlew` on this machine and `.\gradlew` on Windows; the rest of the command is identical.

Remember rule: **local verification stops before packaging.** Never invoke R8 or build/install an APK locally - no `assemble*`, `bundle*`, `install*`, or any release variant. Push an authorized PR branch and use `build.yml` for debug/release APKs and release minification; the local machine is for compile and unit-test feedback only.

**That is not the end of verification, it is the first stage of it.** A push to an authorized PR branch builds a signed universal APK and delivers it to the beta testers on Telegram (`ci.md`), who run it on real hardware across DPIs, OEM skins and Android versions - which is a far better answer to "does this look right" than one emulator, and the reason pushing a compiling UI change is the right move rather than a risky one. Behave accordingly: the compile and the unit tests are the gate you own, and everything only a device can answer is named in the handoff rather than guessed at.

- **Tests.** A small JVM suite under `app/src/test/` covers pure logic only - parsers, formatters, the rate-limit hold. `testOptions { unitTests { isReturnDefaultValues = true } }` is set because `KLog` writes through `android.util.Log`, an unmocked stub that throws on every call in JVM tests; without it nothing in `data/` is testable. **That same flag silently breaks `org.json`** [scar]: `JSONObject` and `JSONArray` are stubbed in the same android.jar, so with default values they parse every input to nothing and a parser test fails with an empty result rather than an error - which is why the InnerTube parsers went untested for so long. `testImplementation(libs.json.unit.test)` puts a real `org.json` on the unit-test classpath ahead of the stub. Any new parser test depends on it being there. No instrumented tests worth running. Anything touching UI or the network is verified by compile plus a run on the emulator.
- **Emulator and `adb` need explicit permission each time, and are the user's to run by default.** Not "never", but not yours to reach for: ask, and only when something genuinely cannot be settled any other way. `emulator -avd Pixel_8_API36` (tools on PATH), then `adb wait-for-device`; the SDK is at `E:\Android\Sdk` on the Windows machine. The debug build installs as `com.ivor.ivormusic.debug` and lives beside the release app rather than replacing its data, session and widgets - so resolve the launcher activity rather than assuming the package name. Kept here because it is what the user needs to type; anything only verifiable on a screen is handed back with what to look at.
- **Versions.** `versionCode` / `versionName` in `app/build.gradle.kts`. Dependency versions live only in `gradle/libs.versions.toml`.
- **minSdk is 30, and desugaring is load-bearing.** [scar] `isCoreLibraryDesugaringEnabled` plus `coreLibraryDesugaring(libs.desugar.jdk.libs.nio)` - it must be the `_nio` flavour. NewPipe calls Java 10/11 library APIs the platform only shipped in API 33 (`URLEncoder.encode(String, Charset)`, `URLDecoder.decode(String, Charset)`, `Collectors.toUnmodifiableList()`) and D8's built-in backports miss those three, so removing desugaring makes every search throw `NoSuchMethodError` on API 30-32 while compiling cleanly. Desugaring is not permission to ignore every `NewApi` finding: `InputStream.readNBytes` in the local-lyrics content-URI path was still an API-33 call and is now `LocalLyricsSource.readBounded`, an ordinary bounded read that works on API 30. Anything gated above 30 (dynamic color at 31, Live Updates at 36) needs a `Build.VERSION.SDK_INT` guard and a working fallback.

## Docs and how work is tracked

**Finishing a piece of work means updating `ROADMAP.md` in the same change.** A planned item that ships loses its section from Planned work and gains a line in Shipped; a fixed defect leaves Known defects, keeping whatever about the diagnosis is worth carrying to the next problem of its kind. **Then correct the prose elsewhere that leaned on the old behaviour** - those paragraphs are what the next decision gets made from, and a stale one will be believed. [scar] Channel search shipped saying account subscriptions carry no `@handle`; they always did, and that paragraph would have talked the next person out of supporting them. This is part of the same commit as the code, not a follow-up and not something to ask about first.

**Re-derive a [drifts] number before you quote it, and fix it while you are there.** They are counts, not claims, and every one of them is a one-line shell command. The batch re-derived in September 2026 had drifted a long way: Expressive-API files 47 -> 65, `dp` literals 3,452 across 89 files -> 3,671 across 99, `SettingsScreen` parameters 106 -> 112. `DESIGN.md` carries its own copies of several of these for a public audience and drifts independently; if you correct one here, check whether that file states it too.

**Keeping `CLAUDE.md` and `docs/` true is part of the same rule.** A fact belongs in the topic file for its area; `CLAUDE.md` gets a one-line summary only when every session needs it. If a change contradicts something here, fix the sentence in the same commit. When you add a fact, mark it: `[verified <month>]` for something probed, `[scar]` for something that broke, `[judgement]` for a call someone could reasonably make differently. Re-derive any count or line reference you touch rather than trusting it, and prefer deleting a stale paragraph to leaving it standing.

`ROADMAP.md` is the prose and reasoning; **GitHub issues are the task list.** Most issues quote the roadmap entry they came from, epics carry the `epic` label, and children open with "Sub-issue of #N". A roadmap entry naming a file and line has usually already become an issue, so search before writing a new one. Every open issue carries one label from each of the first three families:

- **`area:`** `interface` / `playback` / `foundations` / `reach`, matching the `ROADMAP.md` headings.
- **`size:`** `XS` / `S` / `M` / `L` / `XL`. **Effort, not importance.** XS is an hour or two in one file with no design decision; S half a day across one or two files with the approach settled; M a few days, several files, or one new screen over existing data; L a week or more, a new subsystem or data model, or a UI-wide sweep; XL multi-week, a new module, app or playback pipeline. Epics carry the size of the whole thing, so an XL parent over M children is expected.
- **`priority:`** `P0` / `P1` / `P2` / `P3`. **Ranked by user-facing impact, deliberately not by what unblocks the most other work.** P0 is wrong today and hit constantly; P1 a noticeable gap in a daily core loop; P2 a real improvement nothing is blocked on, and most of the list; P3 a new surface or platform, or something deferred on purpose.
- Plus ordinary `bug` / `enhancement` / `question` / `documentation`, and `good first issue`, `blocked`, `epic`.

Sizes and priorities are judgements rather than measurements; argue with one and change the label rather than treating it as fixed.

## The other docs

`DESIGN.md` is the public design-system doc - shape/motion/color systems, how `IvorMusicTheme` resolves a `ColorScheme` (dynamic versus the fixed palettes versus AMOLED versus artwork colors), the player-style table, and the stated policy against an alternate design language. Written for users and contributors, so it carries counts that go stale [drifts]: if you change the palette list, player styles, or the animation/shape mix, re-derive its numbers.

`ROADMAP.md` is where planned work lives and is worth reading before designing any feature - most entries already name the files, the constraint that shapes the answer, and what exists to reuse. Several are diagnoses rather than wishes. Its **Known defects** section carries file and line references for traced-but-unfixed bugs, so check there before re-diagnosing something.

`Agents.md` is a pointer to `CLAUDE.md` plus the legacy `E:\sdk` emulator appendix. It is not a second copy and must not become one.

`Material_3_expressive/` and `.agent/` were deleted (`da12ac0`, `f763694`); their conventions are the ones stated here. `docs/` holds the topic files `CLAUDE.md` indexes and nothing else; the deep-dive docs named in older notes (ARCHITECTURE, DEEP_DIVE_YOUTUBE, DEEP_DIVE_PLAYBACK, NEWPIPE_INTEGRATION_GUIDE, PLAYER_STYLES_PURE_EXPRESSIVE_CONCEPTS) do not exist. The source, `DESIGN.md`, `ROADMAP.md`, `CLAUDE.md` and `docs/` are the reference.
