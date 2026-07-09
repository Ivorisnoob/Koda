# Player Style Concepts: The Pure Expressive Gallery

**Status:** Research / concept gallery (no implementation yet)
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

## 5. Comparison and slotting

| Concept | Emotional center | Interaction novelty | Build complexity | Enum value |
|---|---|---|---|---|
| POSTER | Reflective (identity, shareable) | Low — taps and one scrub gesture | Medium (text fill + variable fonts) | `POSTER` |
| BENTO | Behavioral (tactility) | Medium — everything squishes | Medium (weight-animation discipline) | `BENTO` |
| DIAL | Behavioral/visceral (instrument) | High — rotary physics | High (fling/decay tuning) | `DIAL` |
| STICKER | Visceral (playfulness) | Medium — drag toy + peel | Medium-high (particles + drag physics) | `STICKER` |

All four share the Morph doc's integration path: one new content composable
per style with the standard signature (`viewModel, ambientBackground,
onCollapse, onLoadMore, onArtistClick`), a `when` branch in
`ExpandablePlayer`, an enum value, and a new segment in
`ExpressivePlayerStyleSelectItem`. Note that for pure-flat styles the
`ambientBackground` setting should map to the flat Palette color field (or
be ignored) rather than `ChromaticMistBackground`, which is gradient-based
and therefore out of contract here.

**Recommendation:** if only one is built next, build POSTER — it is the
cheapest to implement, the most visually unlike anything shipping in other
players, and it turns the already-strong `SyncedLyricsView` into a
first-class citizen instead of a sub-mode.

---

## 6. Sources

- In-repo: `PLAYER_STYLE_MORPH_RESEARCH.md` (constraint framework and
  integration plan), `Material_3_expressive/Shapes.md` / `Motion.md` /
  `Toolbars.md`, `docs/DesignMindset.md`, `ui/player/PlayerSheetContent.kt`
  (squish physics, scrub-on-release), `GesturePlayerContent.kt` (floating
  toolbar, carousel physics), `ui/components` (`LikeBurstIcon`).
- Google Design, "Expressive Design: Google's UX Research" — shape/motion
  glanceability findings and the usability guardrail.
- Material Design blog, "Start building with Material 3 Expressive" —
  motion scheme and component availability at `material3 1.5.0-alpha13`.
