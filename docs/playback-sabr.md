# SABR implementation and handoff (#259)

Read `CLAUDE.md` first. This document records the implementation sequence and
verified checkpoints for https://github.com/Ivorisnoob/Koda/issues/259. It is not
a claim that SABR playback has shipped.

## Resume here (next agent)

1. `git checkout feature/sabr-playback && git pull`. Last pushed commit is the
   stage 4 session/transport commit; `git log --oneline -8` shows the stage trail.
   The untracked root `plan.md` is the maintainer's; do not commit or delete it.
2. Read `CLAUDE.md`, then this file's **Checkpoint** section (bottom). Its
   **Next action** is the exact starting point; **Outstanding** lists what remains.
3. Re-run the baseline before changing anything:
   `.\gradlew :app:testDebugUnitTest --tests "com.ivor.ivormusic.data.youtube.sabr.*"`
   (expect 36 passing: Wire 10, Timeline 7, SegmentReader 6, Session 12, OkHttp 1).
4. Code map: `data/youtube/sabr/` - `protocol/` (protobuf, UMP, response controls),
   `media/` (timelines, segment header/reader, spool), `session/` (request model,
   encoder, transport, session), `model/` (descriptor/format ids), `exception/`.
   Upstream reference sources are the pinned clones under `.probe/` (below).
5. Stage 5 needs live probes (`.probe/probe.py`). If probing is impossible,
   write down what is blocked and what evidence is needed; never parse from recall.
6. Per stage: implement, focused tests, log to `.probe/sabr-<stage>-check.log`,
   update the Checkpoint (state, checks, next action, outstanding), update
   `THIRD_PARTY_NOTICES.md` for adapted code, commit locally. Commits carry no AI
   attribution and no `Changelog:` until something user-visible ships. Push only
   when the maintainer asks; never open a PR unasked.

## Branch and authorization

- Working branch: `feature/sabr-playback`.
- Branched from the local `feature/bot-check-traffic-playlist-page-shuffle-home`,
  commit `937b669c8dbde3c97c7261cd40eaf19bfc532002`. Do not reset onto `main`:
  the starting branch contains work awaiting merge.
- The user authorized implementation and a local commit after each substantial,
  coherent step. Pushes, PRs and packaging are not part of that authorization.
- An untracked root `plan.md` existed before this work. Leave it untouched.
- Non-packaging compilation/JVM checks follow `CLAUDE.md`; no APK, R8, emulator,
  adb or device screenshots. Hand device checks back to the maintainer.

## Reference snapshots

Local, ignored research clones:

- `.probe/PipePipeExtractor`, `main`, `c0cd0d61863f430af86475aaac968fbef245f507`.
- `.probe/PipePipeClient`, `dev`, `08b277619ac05a5b227ca53a7fe4cb1958663c4d`.
- Older NewPipe/PipePipe checkouts exist under `.probe/reference`; do not confuse
  them with the snapshots above.
- Refreshed 2026-09-18, all `--depth 1`: extractor `main` is still the pinned
  `c0cd0d6`; the client `dev` branch is gone from GitHub (pinned SHA unfetchable
  there) and was recovered from Codeberg `NullPointerException/PipePipeClient`
  at the pinned `08b2776` under `.probe/PipePipeClient/PipePipeClient` (the
  GitHub `PipePipe` repo at that path is only a submodule shell).
  `.probe/NewPipeExtractor` is stock `TeamNewPipe` `dev` HEAD `ab984a8` plus the
  `v0.26.5` tag Koda builds against; extractor `rewrite-sabr` tip `2970d67`
  (Android VR player PO tokens) is fetched as `FETCH_HEAD`.

Source inspection found a reusable protocol/UMP/timeline implementation, a
persistent local-DOM token minter, synthetic DASH playback and checkpointed
downloads. This is source evidence, not a live playback verification.

Important differences from Koda's requirements:

- Playback preparation requires audio and video. Koda needs genuinely audio-only
  preparation and requests, including crossfade/prefetch consumers.
- Source creation selects a singleton video representation; automatic video ABR
  is not implemented by merely exposing the SABR transport.
- Server reload-player-response becomes a protocol exception. Koda needs bounded
  re-resolution and source restoration at the preserved position (8a done
  2026-09-20: `reloadOnce` - music re-resolves fresh exactly once, video
  invalidates and falls through to direct at the preserved position; JVM tests
  in `SabrReloadTest`, log `.probe/sabr-8a-check.log`).
- Bridge stop does not explicitly cancel the active OkHttp call. Koda must wire
  cancellation to network reads and backoff, especially for Shorts.
- Token invalidation ignores initialization still in flight. Koda rejects
  stale completions using its session/profile generation (done 2026-09-20:
  `SabrBridge.staleCheck` wired to descriptor usability in the assembly, JVM
  tests in `SabrBridgeTest`; see `.probe/sabr-5b-check.log`).
- Playback requests currently send speed 1.0; Koda must send actual player speed.
- Segment keys include itag, lastModified and xtags. Offline completeness and
  cold-start metadata still need Koda integration.
- Download remuxing uses FFmpegKit. Do not import that dependency implicitly.

## Implementation sequence and exit criteria

Each stage ends with focused checks, an updated checkpoint below and a local
commit. Split a stage further when that makes a reviewable, independently tested
change. Never mark a stage complete merely because scaffolding compiles.

1. **Branch and plan.** Preserve local HEAD ancestry and existing user work. Save
   this document, link it from the architecture index and roadmap, commit.
2. **Source contracts.** Add URL-independent source and immutable SABR descriptor,
   rendition identity, expiry and ownership models. Preserve existing URL/local/
   HLS behavior while adapting resolution boundaries. No synthetic SABR URL may
   accidentally enter a progressive source or ranged downloader.
3. **Wire/media primitives.** Adapt narrowly scoped protobuf/UMP readers, segment
   assembly, compression and MP4/WebM indices with GPL attribution. Remove
   extractor/downloader/model coupling. Test malformed/truncated input, length
   bounds, interleaving, compression and sequence/time boundaries with sanitized
   or synthetic fixtures. Stream large media to bounded disk storage.
4. **Session/HTTP transport.** Streaming protobuf POST, increasing request number,
   cookies/contexts, honest contiguous buffered ranges, serialized transactions,
   bounded redirects/recovery/backoff and direct HTTP cancellation. Sessions are
   per playback source, never shared mutable state. Test controlled responses.
5. **Attestation and MWEB resolution.** Probe current bootstrap and player shapes
   before parsing them. Restricted local WebView, actual BotGuard challenge,
   reusable expiry-aware minter, content/session binding, generation-safe
   invalidation. Verify signature/n decoder against the installed artifact.
   Missing live evidence is a recorded blocker, not permission to invent shapes.
6. **Media3 bridge.** Synthetic DASH and SABR segment DataSource with cache keys,
   pending-request retry handling, fixed video quality and true audio-only
   startup. Keep rollout disabled until live validation; retain direct fallbacks.
7. **Music/cache integration.** Queue occurrence safety, pre-resolution, crossfade,
   speed, restoration/background lifecycle; music/video cache separation;
   persistent index for complete cached music and cold offline restoration;
   enforce Local Only below every network request and honor cache/preload toggles.
8. **Video/Shorts/recovery.** Position-preserving quality changes, alternate audio
   identity, independent cancellable Shorts sessions, bounded token and descriptor
   refresh. Preserve live HLS, SponsorBlock seeking and overlay/player ownership.
9. **Downloads.** Fresh session on resume, durable rendition/sequence checkpoints,
   ordered writes, cleanup, supported remuxing and classified errors. Explicitly
   document unsupported formats; do not defer rejection until final muxing.
10. **Hardening/handoff.** JVM/HTTP fixtures and non-packaging checks, documentation,
    user-run signed-in/out and WebView/device matrix. Keep true automatic video
    ABR a later stage after fixed-quality transport is stable.

## Invariants

- Stock NewPipe remains; no PipePipe application/extractor dependency.
- Direct streams retain bounded range GETs and matching client User-Agents.
  SABR is a separate streaming POST transport; live/post-live remain HLS.
- Descriptors carry immutable resolution metadata only; session counters,
  playback cookies and contexts belong to a single cancellable session.
- Account identity uses profile plus login generation, not a cookie-string hash.
- Tokens, cookies, challenge output and signed URLs never go in logs, commits,
  exception messages, durable checkpoints or default model `toString()` output.
- Retain attribution/license notices for adapted code. Do not claim the protocol
  is our original work.
- No network in Local Only mode, including token/bootstrap/descriptor preparation.
- No unbounded response buffering, prefetch, retry, decompression or spool growth.
- Do not claim signed-in or Android lifecycle validation from JVM-only checks.

## Checkpoint

**Current state:** stages 1, 2a (source/model foundation), 2b (source/boundary
hardening: synthetic `sabr://`/`sabrseg://` URIs fail fast at every URL-only
boundary - `isSabrUri`, uncacheable playback URLs, chunked source, UA match -
plus JVM boundary tests, 2026-09-20), 3a (wire readers),
3b (timeline parsers), 3c (segment assembly) and 4 (session/HTTP transport)
complete, plus stage 5a (home bootstrap parser, `model/SabrBootstrap.kt` with
`SabrBootstrapTest`; token-bound request shape, `model/SabrPlayerRequest.kt` with
`SabrPlayerRequestTest`; MWEB envelope parser, `model/SabrResolution.kt` with
`SabrResolutionTest` - implemented and JVM-verified 2026-09-18: 30 tests green
(`SabrBootstrapTest`, `SabrPlayerRequestTest`, `SabrResolutionTest`,
`SabrSessionTest`; one fixture bug fixed in `82b8617`), plus the minter
orchestration (`session/SabrMinter.kt` with `SabrMinterTest`, 9 tests green),
headless page runtime (`session/SabrWebViewRuntime.kt`,
`assets/koda_sabr_po_token.js`) and Android owner (`session/SabrAttestation.kt`
with identity producer), plus the resolution pipeline (`SabrResolver.kt`: mint,
token-bound MWEB fetch through `fetchPlayerResponse`, parse, construct -
JVM-verified with the suites above, 70 tests green 2026-09-18), plus the stage
6 bridge (`bridge/SabrManifest.kt`, `SabrPlaybackSpec.kt`, `SabrBridge.kt`,
`SabrSegmentDataSource.kt`, fixed selection, gated assembly - 83 tests green
2026-09-18), plus stage 7 integration (music waterfall branch with
occurrence-keyed sources, video resolve/loadQuality branch with position
restore intact, cache-toggle wiring, Local Only refusal by construction -
compile-verified, rollout gate closed), plus stage 2b boundary hardening and
the code-only half of 5b (stale completion rejection via identity generation,
JVM-verified 2026-09-20 - logs `.probe/sabr-2b-check.log`,
`.probe/sabr-5b-check.log`), plus stage 8a bounded reload recovery
(`reloadOnce`, JVM-verified 2026-09-20 - log `.probe/sabr-8a-check.log`), plus
stage 9a download checkpoints and support policy (durable rendition/sequence
progress format, upfront remux check, error classification, JVM-verified
2026-09-20 - log `.probe/sabr-9a-check.log`). SABR is
not enabled or playable. The anonymous MWEB /player envelope is verified live
(`c=MWEB`, 25 adaptive formats, ~6h expiry, ciphered URLs, no ustreamer leaf):
`.probe/stage5-mweb-player-anon-2026-09-18.log`. `PlaybackSource` separates URL-backed and SABR metadata;
legacy `VideoQuality.delivery` uses its URL-backed compatibility projection.
`PlaybackSource.Sabr` is not `UrlBacked` by construction, and the compat
projection provably carries no SABR-scheme URL (stage 2b, `PlaybackSourceTest`).
The SABR descriptor copies token bytes/list inputs, redacts diagnostics, checks
profile/login/attestation identity and lifetime, and permits audio-only selection.
Segment identities distinguish rendition revision, opaque tags and audio track.
Resolver/player migration (stage 2b) is done at the boundary layer: an
unsupported source cannot be emitted into a URL-only consumer because the
synthetic schemes fail fast there. What remains with the bridge integration is
only enabling the SABR route itself after live validation.

The attributed PipePipe protobuf/UMP readers are now independent of its extractor.
They reject oversized lengths/tags, varint overflow, truncated payloads and
excessive field/part counts. [judgement] Initial limits: 1 MiB buffered metadata,
64 MiB streamed UMP part, 256 MiB response payload, 16,384 parts. Revisit these
against real high-resolution fixtures; never silently remove the limits.

MP4/WebM timeline parsers are adapted with attribution. MP4 walks actual box
boundaries (including extended sizes), validates SIDX headers and rounds absolute
time boundaries without cumulative gaps. WebM permits partial/unknown-length
Segment masters but rejects truncated leaves, unordered/missing cues and unknown
final duration. Time arithmetic rejects overflow. Initialization is bounded to
4 MiB and timelines to 65,536 entries. Seek lookup is binary search, with an
end-of-stream sequence of entry count + 1. These limits and stricter rejection
need validation against live initialization data before rollout.

Segment assembly (`media/SabrSegmentReader`, `SabrMediaHeader`, `SabrSpool`)
demultiplexes MEDIA_HEADER (20) / MEDIA (21) / MEDIA_END (22) straight to
per-source temporary files; every other part type is handed to a control callback
as bytes. Field numbers and compression codes (1 gzip, 2 Brotli via
`org.brotli:dec`) match upstream at the snapshot revision. Headers enforce wire
types, a one-byte header id, positive itag and 0/1 init flag. [judgement] Stricter
than upstream: a non-init segment without a sequence number is rejected; revisit
if live responses omit it. Limits: 16 open segments, 512 KiB / 512 control parts
per response, 4 MiB init and 64 MiB media segment (also caps decompressed size),
256 MiB spool per source. A declared length must match the bytes received. Any
failure, consumer exception or spool close deletes the pending/delivered files;
closing the spool also invalidates open readers. `SabrSpool` is not Media3's
cache - durable caching is stage 7.

Session/transport (`session/`, `protocol/SabrResponseControls`,
`exception/SabrExceptions`). One `SabrSession` per playback source owns the
request number, playback cookie, SABR contexts, redirect target, backoff and a
bandwidth estimate; the descriptor stays immutable. Transactions are serialized
by a lock; `close()` from any thread cancels the active `SabrCall` (OkHttp
`call.cancel()`) and wakes any backoff/retry wait, and the caller then closes the
spool. Consumers own each delivered `SabrSegment` and must close it.
- Requests: `SabrRequest.preparation` (formats preferred, not selected) and
  `.playback` (actual player speed, one audio and/or one video track). Audio-only
  sends track-type 1 and no viewport. `SabrTrack.buffered` is an explicit
  contiguous sequence range translated through the timeline; upstream always
  claimed "from sequence 1", which is not honest after a seek.
- Before every request: descriptor `isUsable(now, currentIdentity())`, else
  `SabrStaleDescriptorException` with no network. The streaming URL and every
  redirect must be https `*.googlevideo.com`, and a `c=` parameter must be MWEB
  to match the fixed MWEB User-Agent (invariant 2). [judgement] Unverified live:
  stage 5 must confirm the player response's URL is MWEB-minted.
- `rn` increments for every request that got an HTTP response, including
  truncated ones, so a retry never reuses a number. Upstream reused it on a
  streaming failure. [judgement]
- Failures are typed: `SabrIncompleteMediaException`/EOF (retried, 3 consecutive
  max), `SabrHttpException` (non-200), `SabrServerErrorException` (SABR_ERROR),
  `SabrAttestationException` (status 3, or status 2 with no media 3 times),
  `SabrReloadException` (reload player response - stage 8 re-resolves),
  `SabrCancelledException`. LIVE_METADATA on a session is a protocol error:
  live stays HLS (invariant 4). Malformed optional controls (policy, protection,
  context) are skipped, max 16, as upstream observed transient invalid wire types.
- Budgets [judgement, upstream values]: 3 redirects without media, a backoff
  over 30s rejected, 30s continuous backoff and 30s continuous no-progress in
  `request()`, 250 ms between empty responses, 64 contexts.
- Not yet handled: rotating a session-bound PO token without a new descriptor
  (stage 5 decides whether the session takes a token supplier); format
  initialization metadata (part 42) is ignored because timelines come from the
  initialization ranges; `SABR_SEEK` is ignored until the bridge (stage 6) shows
  a need.

**Checks:** `:app:compileDebugKotlin` and focused `:app:testDebugUnitTest` for
`PlaybackSourceTest`, `VideoQualityVariantsTest`, `VideoStreamResolutionCacheTest`
passed. Log: `.probe/sabr-foundation-check.log`. `SabrWireTest` also passed
(10 tests), including all five UMP widths, malformed protobuf, fragmented reads,
8 MiB generated streaming input and interruption. Log: `.probe/sabr-wire-check.log`.
`SabrTimelineTest` passed (7 tests), including MP4 v0/v1/extended boxes, every
truncated prefix, fractional boundaries, embedded fake SIDX, WebM Segment forms,
scales and invalid cue/duration cases. Log: `.probe/sabr-timeline-check.log`.
`SabrSegmentReaderTest` passed (6 tests): interleaving, gzip/Brotli, eight
malformed/incomplete cases leaving zero files, spool budget and decompression
expansion, consumer failure and release while reading. Log:
`.probe/sabr-segment-check.log`. `SabrSessionTest` passed (12 tests): encoded
audio-only body (speed, DRC, audio track id, token, cookie, active/unsent
contexts, client id/version, URL `alr`/`cpn`/`rn`), buffered ranges in timeline
time, URL/redirect host and client validation, redirect bound, incomplete retry
bound with unique request numbers, the backoff/truncation loop with no extra
calls, no-progress budget, typed controls including pending attestation, a
malformed policy skipped next to valid media, context stop/discard, stale
identity/expiry with no network, close during a blocked body read (call
cancelled, zero spool files) and close waking a 20 s backoff.
`OkHttpSabrTransportTest` (1 test) posts over loopback and proves `cancel()`
aborts a stalled body read. Log: `.probe/sabr-session-check.log`. Stage 2b
boundary tests passed (`PlaybackSourceTest` 6, `VideoPlaybackCacheTest` 8),
including sabr-scheme rejection at the chunked source, UA match and playback
cache. Log: `.probe/sabr-2b-check.log`. Device evidence 2026-09-20: a local
`installDebug` from this branch played video with frames flowing, no crash,
and none of the 2b guards fired - no leak reached them and no guard
false-positives on direct playback. `SabrBridgeTest` passed (7 tests),
including stale-prepare with zero calls and mid-preparation invalidation
discarding late init. Log: `.probe/sabr-5b-check.log`. `SabrReloadTest` passed
(6 tests): single bounded retry, fallback after the second failure, no reload
on other failures, cancellation propagated. Log: `.probe/sabr-8a-check.log`.
The same 2026-09-20 device run covered the 8a caller changes (`trySabrMusic`,
`loadSabrQuality` fallbacks) with no behavior change behind the closed gate.
`SabrDownloadCheckpointTest` passed (6 tests) and `SabrDownloadSupportTest`
passed (4 tests): checkpoint round-trips, ordered advance, support matrix,
error classification. Log: `.probe/sabr-9a-check.log`. All controlled
responses are synthetic; no live googlevideo request has been made.
No packaging/device checks.

**Next action:** live validation before any rollout: on-device minter run
(real BotGuard + GenerateIT through the app WebView), signature/n decoder
check for the ciphered ladder URLs, token-bound ustreamer evidence, then flip
`SABR_PLAYBACK_ENABLED` with fallbacks watching. Re-probed 2026-09-20 from
this machine (5 requests): anon MWEB /player bare and with fresh visitorData,
plus cookie MWEB, all UNPLAYABLE with 0 formats on dQw4w9WgXcQ - the 09-16
session no longer yields formats either, and nothing in production calls
`warmUp()` while the gate is closed, so the mint needs a gated build on a
honored network. Anon-envelope claim left standing but unconfirmed on
re-probe (its stage5 log is absent here). On-device mint SUCCEEDED 2026-09-20
(emulator, WebView 145, temp DEBUG trigger): real BotGuard + GenerateIT,
clientVersion 2.20260918.00.00, generation 0; token-bound resolve then OK on
the emulator (29 formats, ~5h expiry, MWEB URL, ustreamer present) while the
first streaming POST 403'd in prepareTimelines with an empty UMP-typed body.
Wire headers/params/body verified identical to pinned upstream, and audio+video
preparation 403s identically, so this is an edge attestation refusal on this
network, not a shape bug - next attempt needs a honored egress IP. Probe
captures non-200 streaming bodies. See `.probe/sabr-5b-live-check.log`.
Everything else is
implemented and JVM/compile-verified (logs: `.probe/sabr-*-test.log`,
`.probe/sabr-*-compile.log`). Still needs
device/signed-in probes: token-bound ustreamer leaf, SESSION binding, actual
BotGuard run. MWEB envelope (minus ustreamer) is verified live; do not invent
beyond it.

**Outstanding:** stage 5b-live (on-device minter run, signature/n decoder,
token-bound ustreamer, token-supplier decision), stages 8b-10 (quality changes
across transports, alternate audio identity, Shorts sessions, download
pipeline with persistence and remux wiring, hardening). (Stages 2b,
5b-code, 8a and 9a landed 2026-09-20.) Anonymous live probes done (home bootstrap,
MWEB envelope - see `.probe/stage5-*-2026-09-18.log`); signed-in probes done
2026-09-18 from the app WebView jar - home honors the session (LOGGED_IN,
DATASYNC_ID) but /player answers logged_in:0 on this IP/visitor, so
token-bound deltas still need a honored session or the on-device minter.
Update this section before every implementation
commit so a replacement agent can resume without chat history.

## Device acceptance matrix (maintainer-run)

- Signed out/in, content/session token binding, login/logout/profile switch during
  cold initialization and while warm; missing/outdated/crashed WebView.
- Audio past 90 seconds and long continuous playback; seek near start/middle/end;
  actual playback speed; screen off, Bluetooth, notifications and Android Auto.
- Duplicate queue occurrences, rapid skips, crossfade, restore after process death.
- Video quality/audio switch, SponsorBlock, Shorts swipe-away and watch handoff;
  direct fallback and live HLS regression checks.
- Full/partial/evicted cache, disabled cache/preload, Local Only including cold
  startup; ensure no network is opened for a cache miss in Local Only.
- Download pause/resume/process death, cancel, disk full, destination failures and
  every offered codec/container combination.
