# Third-party source notices

## PipePipeExtractor SABR wire implementation

The following files under
`app/src/main/java/com/ivor/ivormusic/data/youtube/sabr/` are adapted from
[PipePipeExtractor](https://github.com/InfinityLoop1308/PipePipeExtractor),
commit `c0cd0d61863f430af86475aaac968fbef245f507`:

- `protocol/SabrProto.java`
- `protocol/UmpReader.java`
- `exception/SabrProtocolException.java`

Original paths: `extractor/src/main/java/org/schabi/newpipe/extractor/services/youtube/sabr/`
with the same relative names. Copyright belongs to the upstream contributors.
The upstream repository distributes this source under the GNU General Public
License version 3; the license text is included in Koda's root `LICENSE`.
These files had no individual copyright header at the pinned revision.

Koda's September 2026 modifications isolate the packages, remove the fork-specific
model/exception dependencies, use AndroidX annotations, bound input sizes and
field/part counts, reject integer overflow and invalid tags before narrowing,
preserve InputStream zero-length read behavior, and redact protobuf diagnostic
values. The associated synthetic tests are Koda additions.

This notice covers adapted source, not a dependency on the PipePipe app or its
extractor artifact. Koda continues to use stock NewPipe Extractor independently.
