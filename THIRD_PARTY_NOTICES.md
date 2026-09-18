# Third-party source notices

## PipePipeExtractor SABR wire implementation

The following files under
`app/src/main/java/com/ivor/ivormusic/data/youtube/sabr/` are adapted from
[PipePipeExtractor](https://github.com/InfinityLoop1308/PipePipeExtractor),
commit `c0cd0d61863f430af86475aaac968fbef245f507`:

- `protocol/SabrProto.java`
- `protocol/UmpReader.java`
- `exception/SabrProtocolException.java`
- `SabrFormatTimeline.java` (upstream `YoutubeSabrFormatTimeline.java`)
- `media/SabrSegmentIndex.java`
- `media/SabrMp4SegmentIndexParser.java`
- `media/SabrWebmSegmentIndexParser.java`

Original paths: `extractor/src/main/java/org/schabi/newpipe/extractor/services/youtube/sabr/`
with the same relative names. Copyright belongs to the upstream contributors.
The upstream repository distributes this source under the GNU General Public
License version 3; the license text is included in Koda's root `LICENSE`.
These files had no individual copyright header at the pinned revision.

Koda's September 2026 modifications isolate the packages, remove the fork-specific
model/exception dependencies, use AndroidX annotations, bound input sizes and
field/part counts, reject integer overflow and invalid tags before narrowing,
preserve InputStream zero-length read behavior, and redact protobuf diagnostic
values. Timeline adaptations add structural MP4 box traversal, strict leaf-element
bounds, overflow-safe scaling, cue validation and binary search; guessed WebM
final durations are rejected. `SabrTimeScale.java` and the associated synthetic
tests are Koda additions.

This notice covers adapted source, not a dependency on the PipePipe app or its
extractor artifact. Koda continues to use stock NewPipe Extractor independently.

`media/SabrMediaHeader.kt` adapts protocol field mappings from upstream
`generated/SabrMediaHeader.java`. `media/SabrSegmentReader.kt` adapts framing and
compression identifiers from `protocol/SabrStreamingResponseReader.java` and
`media/SabrMediaSegmentCollector.java` at the same revision. Their Kotlin
implementation adds strict field types/widths and bounded file-backed assembly
and decompression. `media/SabrSpool.kt` is Koda's source-scoped storage owner.

`protocol/SabrResponseControls.kt` adapts UMP part ids and control field numbers
from upstream `protocol/SabrResponseDecoder.java` and `YoutubeSabrSession.java`.
`session/SabrRequestEncoder.kt` adapts request field numbers, MWEB client identity
and URL parameters from `YoutubeSabrRequestHelper.java`. `session/SabrSession.kt`
adapts control handling and redirect/backoff/integrity budgets from
`YoutubeSabrSession.java`, and the progress loop from PipePipe (client)
`app/src/main/java/org/schabi/newpipe/youtube/SabrRequestCoordinator.java` at
[PipePipe](https://github.com/InfinityLoop1308/PipePipe) commit
`08b277619ac05a5b227ca53a7fe4cb1958663c4d`, also GPL-3.0. Koda's changes add
audio-only request state, explicit contiguous buffered ranges, strict streaming-URL
host/client checks, typed failures, direct HTTP cancellation, cancellable waits and
descriptor identity/expiry checks. `session/SabrRequest.kt`, `session/SabrTransport.kt`
and `exception/SabrExceptions.kt` are Koda additions.

## PipePipe home-page attestation bootstrap parser (client)

`app/src/main/java/com/ivor/ivormusic/data/youtube/sabr/model/SabrBootstrap.kt`
parses the `ytcfg.set` configs and `window.ytAtN` BotGuard challenge out of a
YouTube home page and is adapted from PipePipe (client)
`app/src/main/java/org/schabi/newpipe/youtube/YoutubePageAttestationBootstrap.kt`
at the commit above, also GPL-3.0. Koda's changes use `org.json` instead of
nanojson, require the WEB home client, keep empty-visitor fallthrough and
`//`-relative interpreter URLs, and redact diagnostics. The home WEB identity
was verified live 2026-09-18; see `docs/playback-sabr.md`.
