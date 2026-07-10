# Player Style Concepts: The Pure Expressive Gallery

**Status:** All five concepts are implemented and selectable in Settings under
Player Style (`ui/player/EditorialPlayerContent.kt`, `PosterPlayerContent.kt`,
`BentoPlayerContent.kt`, `StickerPlayerContent.kt`, `DialPlayerContent.kt`),
alongside the Morph deep dive (`MorphPlayerContent.kt`)
**Companion to:** `PLAYER_STYLE_MORPH_RESEARCH.md` (the Morph deep dive)
**Hard constraints for every concept here:** no gradients, no drop shadows,
no scrims, no blur. Depth, hierarchy and mood come exclusively from tonal
color roles, shape, typography, and spring motion. This is M3 Expressive
with nothing to hide behind.

---

## 0. The flat-expressive rulebook

These rules apply to all four concepts and are stricter than the current
CLASSIC/GESTURE/Morph styles:

1. **No gradient, anywhere.** The existing album containers use vertical
   gradient overlays for "depth" and `ChromaticMistBackground` is built from
   blurred gradient clouds — both are banned here. Pure-flat styles run on
   **solid tonal fields**. Dynamic mood color still comes from the music:
   Palette (already a dependency) extracts one dominant swatch and the
   background is a single flat fill harmonized against the M3 scheme, or
   simply `surfaceContainerLowest` with dynamic color doing the work.
2. **No elevation.** `shadowElevation = 0` everywhere. Layering is shown by
   overlap, tonal contrast (`surfaceContainerLow` on `surfaceContainerHigh`),
   and motion parallax — objects that move at different rates read as
   different depths without a single shadow.
3. **Hard edges only.** Two flat colors may meet, but never blend. Progress
   fills, selection states and "glow" substitutes are hard-edged color
   boundaries. Where the current styles would crossfade, these styles
   physically morph, slide, or hard-cut on a spring.
4. **Every motion is a sentence.** Same principle as Morph: animation is the
   status display, not garnish. If a spring fires, it is telling the user
   something about playback state.
5. **Color roles only.** No raw hex, no white/black constants. `primary`,
   `primaryContainer`, `secondaryContainer`, `tertiaryContainer` and the
   surface ladder carry all hierarchy — which also keeps every concept
   automatically correct in light, dark, and dynamic-color themes.

All concepts consume the existing `PlayerViewModel` state unchanged, slot
into the `when (playerStyle)` branch of `ExpandablePlayer`, and follow the
same four-file settings threading as any new `PlayerStyle` value.

---

## 1. POSTER — the kinetic type player

**One sentence:** the player is a Swiss-style gig poster where typography is
the entire interface — the song title at display scale is the artwork, the
progress bar, and the play state all at once.

**Why it is cool:** no music app dares to drop the album art. POSTER does.
The title is set huge (display scale, wrapped over multiple stacked lines,
left-aligned, tight leading) on a flat tonal field, like a letterpress print
of the song itself. It is the most reflective-level concept in the gallery:
the screen becomes a shareable poster of whatever is playing.

**Anatomy:**

- **Background:** one flat color field (Palette dominant swatch harmonized to
  the scheme, or `surfaceContainerLowest`).
- **Title block:** `displayLarge`-scale stacked words. While playing, the
  variable font weight slowly breathes (wght 500 to 700 and back) — type
  that is literally alive. Paused type freezes at rest weight.
- **Progress:** a hard-edged "highlighter" fill that sweeps through the
  title text itself — glyphs behind the playhead are `primary`, glyphs ahead
  are `onSurface`. Two flat colors, hard boundary, zero gradient. Dragging
  horizontally anywhere on the title scrubs (single `seekTo` on release,
  same pattern as the existing styles).
- **Play state glyph:** a single oversized punctuation mark next to the
  title — a `MaterialShapes` asterisk-like burst while playing that morphs
  to a full stop (circle) when paused. Punctuation as playback state.
- **Artist line:** `headlineSmall`, tappable (existing `onArtistClick`).
- **Controls:** a one-row `HorizontalFloatingToolbar` that only appears on
  tap, then hides. The resting screen is pure typography.
- **Lyrics mode:** the natural home of `SyncedLyricsView` — the poster
  simply becomes the lyrics, same type system, zero visual mode switch cost.

**Expressive semantics:**

| Element | Meaning |
|---|---|
| Breathing font weight | Music is playing |
| Weight frozen | Paused |
| Highlighter fill position | Progress |
| Asterisk/full-stop morph | Play/pause state |
| Word-by-word spring re-stack on track change | New song is a new poster |

**Key APIs:** variable font animation via `fontVariationSettings` +
`animateFloatAsState(motionScheme.slowEffectsSpec())`; two-layer text with a
hard `clipRect` for the fill sweep; `Morph` for the punctuation glyph;
`AnimatedContent` with spatial springs for the re-stack.

**Risk:** long titles at display scale need an auto-fit step (measure, then
step down through display/headline sizes). Non-Latin scripts must be tested
for variable-weight support; fall back to scale breathing where wght axes
are unavailable.

---

## 2. BENTO — the squish grid player

**One sentence:** the whole screen is a bento box of flat tonal tiles with
physical mass — pressing any tile squishes the entire grid, and the tiles'
sizes themselves are the interface hierarchy.

**Why it is cool:** CLASSIC already proves the `ButtonGroup` + `animateWidth`
squish physics feel great on one row. BENTO generalizes that physics to the
entire screen: a living layout where every element pushes back. It is the
most behavioral-level concept — maximal tactility, everything is a target.

**Anatomy (weight-based grid, no fixed sizes):**

- **Art tile** (largest, ~55% height): album art clipped to a big squircle,
  flat, no overlay. Tap = play/pause; while playing the tile's corner radius
  slowly oscillates between squircle and rounder squircle — the grid
  breathes at the layout level, not via scale hacks.
- **Transport row:** previous / play state / next as three tiles with
  CLASSIC's exact press physics — the pressed tile expands, neighbors
  compress, spring back on release.
- **Progress tile:** a full-width flat tile whose fill is a hard-edged
  two-tone split (`primaryContainer` played / `surfaceContainerHigh`
  remaining). The boundary line is the scrubber. `LinearWavyProgressIndicator`
  is allowed as a stroke on top since it is a flat single-color wave.
- **Status tiles:** favorite (with the existing `LikeBurstIcon`), shuffle,
  repeat, queue-peek — small square tiles in `secondaryContainer` /
  `tertiaryContainer` when checked, surface roles when not. Checked state =
  color role + corner morph, never elevation.
- **Queue peek tile:** shows the next song title; tapping expands it — the
  tile physically grows to swallow the grid and becomes the queue view, then
  shrinks back (container-transform feel done purely with weight animation).

**Expressive semantics:**

| Element | Meaning |
|---|---|
| Grid gap and corner oscillation | Music is playing |
| Grid perfectly settled | Paused |
| Tile expand + neighbor compress | Press feedback (mass) |
| Checked tile color role + rounder corners | Toggle state |
| Queue tile swallowing the grid | Navigation is a physical expansion |

**Key APIs:** `Modifier.weight` animated via `animateFloatAsState` with
`fastSpatialSpec` (the `animateWidth` pattern, applied to both axes);
`animateDpAsState` for corner radii; `ToggleButton` colors; no new
dependencies.

**Risk:** whole-grid springs must stay layout-cheap — animate weights and
radii only (no per-frame text relayout inside tiles); keep the grid to a
fixed tile count.

---

## 3. DIAL — the rotary instrument player

**One sentence:** the player is a precision instrument: one huge flat rotary
dial that the user physically spins to scrub, flicks to skip, and reads like
a gauge.

**Why it is cool:** it replaces the most generic element in every music app
(the horizontal seek bar) with a mechanical object with real fling physics,
friction, and detents. Strokes, dots, and ticks only — the flattest possible
aesthetic, and the strongest "this is a device, not a screen" feel.

**Anatomy:**

- **The dial:** a large circle drawn as a flat stroke ring with tick marks.
  Progress is shown by ticks behind the playhead filling solid `primary`,
  ticks ahead staying `outlineVariant` — a discrete, hard-edged progress
  gauge. The whole tick ring slowly rotates while playing (one revolution
  per track) and halts on pause.
- **Center puck:** album art clipped to a small `MaterialShapes` polygon at
  dial center; morphs on play/pause exactly like Morph's hero, but at puck
  scale. Tap = play/pause.
- **Rotary scrub:** dragging along the ring rotates it with 1:1 finger
  tracking plus detent haptics every 30 ticks; release commits one `seekTo`.
  A hard flick spins the dial with decay physics — spinning past the end
  detent skips to the next track, past the start detent restarts/previous.
- **Readout:** current time / duration in `labelLarge` monospaced numerals
  under the dial, flipping digit-by-digit (hard cuts, no fades).
- **Controls:** shuffle / repeat / favorite as three small outlined chips
  below the readout; queue and lyrics behind a long-press on the puck.

**Expressive semantics:**

| Element | Meaning |
|---|---|
| Ring rotating | Music is playing |
| Ring halted | Paused |
| Filled vs hollow ticks | Progress |
| Detent haptic cadence | Scrub granularity |
| Fling past detent | Skip intent (with friction as the cancel affordance) |
| Puck shape morph | Play/pause state |

**Key APIs:** single `Canvas` for ring + ticks (rotation via
`graphicsLayer.rotationZ`, no relayout); `Animatable` +
`splineBasedDecay` for fling; `LocalHapticFeedback` detents; `Morph` for
the puck. Everything already available at the pinned versions.

**Risk:** rotary scrubbing has a learning curve — first-run hint required,
and the readout must live-update during rotation so the mapping is obvious.
Decay tuning decides whether the dial feels like a flywheel or a wet knob;
budget iteration time for the physics constants.

---

## 4. STICKER — the die-cut playful player

**One sentence:** the album art is die-cut into a `MaterialShapes` sticker
slapped at a slight angle onto a flat poster board, with a thick flat outline
ring instead of a shadow — and it behaves like a real sticker: squash on tap,
fling with spring return, peel on skip.

**Why it is cool:** it is the most visceral-level, personality-forward
concept — closest to the "Vibrant, Personal, Alive" voice in
`DesignMindset.md`. The no-shadow constraint becomes the signature look:
the sticker's "lift" is a thick `secondaryContainer` outline ring plus a
resting 2-3 degree rotation, which reads as a physical object on a board
without any elevation.

**Anatomy:**

- **Board:** flat `surfaceContainerLowest` field, optionally split into two
  hard-edged tonal blocks (top `surfaceContainerLow`, bottom
  `surfaceContainerLowest`) for composition — a color-block layout, not a
  gradient.
- **Sticker:** album art clipped to a rotating pick of `MaterialShapes`
  polygons (a new shape per track — the shape is part of the song's
  identity), with a thick flat contrasting border ring. Tap = play/pause
  with cartoon squash-and-stretch (scale X up while Y down, spring back).
  While playing the sticker sways almost imperceptibly around its resting
  angle; paused it sits perfectly straight.
- **Drag physics:** the sticker is grabbable. Small drags rubber-band back
  with an underdamped spring (pure toy value — and a pet-the-app moment).
  A committed horizontal drag "peels" it off screen and the next track's
  sticker slaps on with an overshooting entrance.
- **Like burst:** double-tap fires a confetti burst of tiny flat
  `MaterialShapes` polygons in `primary` / `tertiary` fills (an upscaled
  sibling of the existing `LikeBurstIcon` pattern) — flat fills, no alpha
  glow.
- **Type and controls:** title in `headlineLarge` on the board below the
  sticker; transport as three flat chips; everything else behind the
  floating toolbar pattern.

**Expressive semantics:**

| Element | Meaning |
|---|---|
| Sticker swaying | Music is playing |
| Sticker straight and still | Paused |
| Squash-and-stretch | Play/pause feedback |
| Peel-off / slap-on | Track change |
| Per-track die-cut shape | Song identity |
| Confetti burst | Liked |

**Key APIs:** `Morph` + custom `Shape` for the die-cut; border ring via
`Modifier.border` with the morph shape; `Animatable(Offset)` with
`spring(dampingRatio = 0.35f)` for the rubber-band; particle burst via a
short-lived `Canvas` with flat polygon fills.

**Risk:** the toy factor must never delay function — the peel gesture and
squash feedback must commit actions immediately and animate after
(optimistic UI), and the sway must be disabled under reduced motion.

---

## 5. EDITORIAL — the two-tone magazine player

**One sentence:** the player is a full-bleed magazine spread in exactly two
colors — album art die-cut into a scalloped flower shape up top, the title
set in a huge editorial italic serif below it, and chunky asymmetric pill
controls where the word "PLAY" itself is the icon.

**Provenance:** this concept is modeled directly on Google's own Material 3
Expressive announcement mock (the "Serafina" player screen), which makes it
the most canonical concept in this gallery — it is what the M3 Expressive
team themselves picked to show the system's ceiling. Every element in that
mock maps one-to-one onto APIs already pinned in this repo.

**The two-tone discipline (the whole trick):** the entire screen uses
exactly two colors plus the artwork — a flat mid-tone field (Palette
dominant swatch harmonized to the scheme, or `primaryContainer`) and one
high-contrast accent (`primaryFixed`/`secondaryContainer` family) used for
type, buttons, and the progress line alike. No third color, no tone ladder,
no outline strokes. Hierarchy comes from size and shape only. This is the
strictest palette in the gallery and the reason the style reads as print.

**Anatomy (top to bottom, matching the reference for the first ~70%):**

- **Die-cut art (~40% of height):** album art clipped to
  `MaterialShapes.Flower` / `Puffy` (the scalloped clover of the mock),
  centered, flat against the field — no ring, no border, the hard clip edge
  is the composition. On track change the scallop count morphs (Flower to
  Clover8Leaf to Puffy), so each song gets its own die-cut like Sticker's
  per-track identity. Tap = play/pause with a single soft morph pulse.
- **Title (~20%):** one full-bleed line in a display italic serif, accent
  color, edge-to-edge with tight side margins, auto-fit stepping down for
  long titles (marquee only as last resort). This is the style's voice and
  justifies a player-only display typeface layered over the app's type
  scale; the rest of the screen stays on the standard scale so the serif
  reads as a deliberate headline, not a theme change.
- **Control cluster (~15%, asymmetric bento):** the mock's exact trio —
  a wide **PLAY pill where the label is the word**, morphing between "PLAY"
  and "PAUSE" (width springs to fit via `animateContentSize`, letters swap
  with a hard cut); previous and next as large accent circles with
  field-colored glyphs. The asymmetry (wide pill + two circles, offset rows)
  is the layout's expressive move — deliberately not a centered symmetric
  transport row.
- **Progress line (~10%):** the mock is literally
  `LinearWavyProgressIndicator` semantics — played portion drawn as a wavy
  line, remaining portion flat, separated by a tall bar playhead; the wave
  flattens when paused. Time labels at both ends in `labelLarge` numerals.
  Scrub by dragging the line, single `seekTo` on release.
- **The missing 30% (below the reference crop):** artist name as a tappable
  accent-outlined pill (existing `onArtistClick`); one row of small flat
  accent chips — favorite (`LikeBurstIcon`), shuffle, repeat, queue — and a
  lyrics chip that flips the whole spread into `SyncedLyricsView` set in the
  same serif, turning lyrics into the magazine's body copy.

**Expressive semantics:**

| Element | Meaning |
|---|---|
| Wavy vs flat progress line | Playing vs paused, plus position |
| PLAY/PAUSE word pill | State as language, not iconography |
| Die-cut scallop morph on track change | New song, new cut |
| Soft morph pulse on art tap | Play/pause feedback |
| Serif headline re-typesetting per track | Each song is a new spread |

**Key APIs:** `MaterialShapes.Flower` / `Clover8Leaf` / `Puffy` +
`Morph` for the die-cut; `LinearWavyProgressIndicator` (stroke ~4dp, its
default wavy-active/flat-track behavior is exactly the mock);
`animateContentSize` with `fastSpatialSpec` for the word pill; a bundled
display serif via `FontFamily` for the headline. Nothing beyond the pinned
`material3 1.5.0-alpha13` plus one font asset.

**Risk:** the two-tone contract depends on the accent having sufficient
contrast against the Palette-derived field in both themes — derive the pair
through the M3 scheme (e.g. container + onContainer-adjacent fixed roles)
rather than raw Palette output, and fall back to `primaryContainer` /
`onPrimaryContainer` when the artwork swatch cannot produce a compliant
pair. Long titles and non-Latin scripts need the same auto-fit and
font-fallback care as POSTER.

---

## 6. Comparison and slotting

| Concept | Emotional center | Interaction novelty | Build complexity | Enum value |
|---|---|---|---|---|
| POSTER | Reflective (identity, shareable) | Low — taps and one scrub gesture | Medium (text fill + variable fonts) | `POSTER` |
| BENTO | Behavioral (tactility) | Medium — everything squishes | Medium (weight-animation discipline) | `BENTO` |
| DIAL | Behavioral/visceral (instrument) | High — rotary physics | High (fling/decay tuning) | `DIAL` |
| STICKER | Visceral (playfulness) | Medium — drag toy + peel | Medium-high (particles + drag physics) | `STICKER` |
| EDITORIAL | Reflective/visceral (print identity) | Low — taps, one scrub | Low-medium (die-cut + word pill + wavy line) | `EDITORIAL` |

All five share the Morph doc's integration path: one new content composable
per style with the standard signature (`viewModel, ambientBackground,
onCollapse, onLoadMore, onArtistClick`), a `when` branch in
`ExpandablePlayer`, an enum value, and a new segment in
`ExpressivePlayerStyleSelectItem`. Note that for pure-flat styles the
`ambientBackground` setting should map to the flat Palette color field (or
be ignored) rather than `ChromaticMistBackground`, which is gradient-based
and therefore out of contract here.

**Recommendation:** if only one is built next, build EDITORIAL — it is the
canonical Google reference realized with components the repo already ships
(`MaterialShapes` clip, `LinearWavyProgressIndicator`, word-pill button),
the lowest-risk to execute, and instantly recognizable as "true" M3
Expressive. POSTER is the strongest second: it shares EDITORIAL's type-first
philosophy and turns `SyncedLyricsView` into a first-class citizen, so the
two styles can share the auto-fit headline machinery.

---

## 7. Sources

- In-repo: `PLAYER_STYLE_MORPH_RESEARCH.md` (constraint framework and
  integration plan), `Material_3_expressive/Shapes.md` / `Motion.md` /
  `Toolbars.md`, `docs/DesignMindset.md`, `ui/player/PlayerSheetContent.kt`
  (squish physics, scrub-on-release), `GesturePlayerContent.kt` (floating
  toolbar, carousel physics), `ui/components` (`LikeBurstIcon`).
- Google Design, "Expressive Design: Google's UX Research" — shape/motion
  glanceability findings and the usability guardrail.
- Material Design blog, "Start building with Material 3 Expressive" —
  motion scheme and component availability at `material3 1.5.0-alpha13`.
- Google's Material 3 Expressive announcement mock (the "Serafina" player
  screen) — direct visual reference for the EDITORIAL concept: flat
  two-tone field, flower die-cut artwork, editorial serif headline, word
  pill transport, wavy-played/flat-remaining progress line.
