# SABR implementation and handoff (#259)

Read `CLAUDE.md` first. This document records the implementation sequence and
verified checkpoints for https://github.com/Ivorisnoob/Koda/issues/259. It is not
a claim that SABR playback has shipped.

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

Source inspection found a reusable protocol/UMP/timeline implementation, a
persistent local-DOM token minter, synthetic DASH playback and checkpointed
downloads. This is source evidence, not a live playback verification.

Important differences from Koda's requirements:

- Playback preparation requires audio and video. Koda needs genuinely audio-only
  preparation and requests, including crossfade/prefetch consumers.
- Source creation selects a singleton video representation; automatic video ABR
  is not implemented by merely exposing the SABR transport.
- Server reload-player-response becomes a protocol exception. Koda needs bounded
  re-resolution and source restoration at the preserved position.
- Bridge stop does not explicitly cancel the active OkHttp call. Koda must wire
  cancellation to network reads and backoff, especially for Shorts.
- Token invalidation ignores initialization still in flight. Koda must reject
  stale completions using its session/profile generation.
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

**Current state:** stages 1, 2a (source/model foundation) and 3a (wire readers) complete. SABR is
not enabled or playable. `PlaybackSource` separates URL-backed and SABR metadata;
legacy `VideoQuality.delivery` uses its URL-backed compatibility projection.
The SABR descriptor copies token bytes/list inputs, redacts diagnostics, checks
profile/login/attestation identity and lifetime, and permits audio-only selection.
Segment identities distinguish rendition revision, opaque tags and audio track.
Resolver/player migration (stage 2b) remains with the bridge integration so an
unsupported source cannot be emitted into a URL-only consumer.

The attributed PipePipe protobuf/UMP readers are now independent of its extractor.
They reject oversized lengths/tags, varint overflow, truncated payloads and
excessive field/part counts. [judgement] Initial limits: 1 MiB buffered metadata,
64 MiB streamed UMP part, 256 MiB response payload, 16,384 parts. Revisit these
against real high-resolution fixtures; never silently remove the limits.

**Checks:** `:app:compileDebugKotlin` and focused `:app:testDebugUnitTest` for
`PlaybackSourceTest`, `VideoQualityVariantsTest`, `VideoStreamResolutionCacheTest`
passed. Log: `.probe/sabr-foundation-check.log`. `SabrWireTest` also passed
(10 tests), including all five UMP widths, malformed protobuf, fragmented reads,
8 MiB generated streaming input and interruption. Log: `.probe/sabr-wire-check.log`.
No packaging/device checks.

**Next action:** stage 3b: adapt/harden MP4/WebM timeline parsers, then the media
collector/control decoder (3c). Upstream MP4 parsing scans for SIDX signatures
and WebM parsing clamps every truncated element; preserve legitimate partial
Segment masters without treating truncated leaf elements as valid.

**Outstanding:** stages 2b, 3b/3c and 4-10. No live probes or device tests performed in this
implementation session yet. Update this section before every implementation
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
