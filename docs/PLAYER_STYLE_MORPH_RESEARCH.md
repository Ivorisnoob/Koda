# Player Style Research: "Morph" — A Totally Expressive Third Player UI

**Status:** Implemented (`ui/player/MorphPlayerContent.kt`, selectable in
Settings under Player Style)
**Scope:** A third `PlayerStyle` for the full-screen music player, alongside `CLASSIC` and `GESTURE`
**Grounding:** Codebase analysis of `ui/player/*`, in-repo M3 Expressive guides (`Material_3_expressive/`, `docs/`), and Google's published Material 3 Expressive research

---

## 1. Why a third style — what the codebase already says

Koda currently ships two full-player styles, selected via `PlayerStyle` in
`data/ThemePreferences.kt` and branched in `ExpandablePlayer.kt`:

| | CLASSIC (`PlayerSheetContent.kt`) | GESTURE (`GesturePlayerContent.kt`) |
|---|---|---|
| Core idea | Button-first control deck | Swipe-first album carousel |
| Expressive APIs used | `ButtonGroup` + `animateWidth` squish physics, `LinearWavyProgressIndicator`, `LoadingIndicator` with `MaterialShapes` polygons, connected `ToggleButton` group, spring pill toggle | `HorizontalFloatingToolbar` with `StandardFloatingActionButton`, swipeable carousel with rotation/parallax, `CircularWavyProgressIndicator` |
| Album art | Static rounded-corner square (`RoundedCornerShape(albumSize * 0.15f)`) | Static rounded squares in a carousel |
| Ambient layer | `ChromaticMistBackground` (Palette-extracted drifting color clouds) | Same |
| State transitions | `Crossfade` (queue, lyrics, pill) | `Crossfade` / `AnimatedContent` |

Both styles are *expressive at the edges*: the expressive vocabulary (springs,
wavy indicators, morphing loaders) is applied to the **controls**, while the
**content** — the album art, the typography, the surfaces — stays a static
rectangle with a crossfade. The single most characteristic M3 Expressive
capability, **shape morphing as a first-class state channel**
(`androidx.graphics:graphics-shapes` is already a dependency, and
`MaterialShapes` is already imported for loaders), is only used for a 40dp
loading spinner.

That is the gap the third style fills. Not "more buttons with springs", but a
player where the expressive system *is* the layout, and every playback state
is communicated through shape, motion, and color rather than icons.

---

## 2. The idea: "Morph" — one living shape

**Concept in one sentence:** the album art lives inside a single large
`MaterialShapes` polygon that breathes, rotates, and morphs with playback
state — the shape *is* the play/pause indicator, the progress display, and the
hero of the screen; conventional controls only exist as a summonable floating
toolbar.

### The screen, top to bottom

1. **Ambient layer** — reuse `ChromaticMistBackground` unchanged. It already
   delivers the "color is borrowed from the music" pillar.
2. **Hero shape (the protagonist)** — the album art clipped by an animated
   `Morph` between `MaterialShapes` polygons:
   - **Playing:** the clip slowly cycles through soft organic polygons
     (`Cookie12Sided` → `SoftBurst` → `Sunny` → back), with a slow continuous
     rotation and a subtle "breathing" scale (`infiniteRepeatable`). The art
     visibly *lives* while music plays.
   - **Paused:** the shape settles into a plain `Circle`/squircle and the
     rotation eases to a stop. Silence looks still.
   - **Buffering:** the existing multi-polygon `LoadingIndicator` language,
     but applied to the hero clip itself rather than a small spinner.
   - **Track change:** the outgoing art morphs down/spins out, the incoming
     art morphs in — a shape-morph transition instead of a crossfade.
3. **Progress as a ring** — a `CircularWavyProgressIndicator`-style wavy
   ring wrapped around the hero shape. Wave amplitude is alive while playing
   and flattens when paused (the same semantic the linear wavy indicator
   already carries in CLASSIC, promoted to the hero position). Dragging along
   the ring scrubs; commit a single `seekTo` on release, exactly like the
   scrub-on-release pattern already used in `PlayerSheetContent` (seeking per
   drag frame causes rebuffering storms on streamed tracks).
4. **Hero typography** — title in `displaySmall`/`headlineLarge` weight-bold,
   left-aligned, allowed to wrap to two lines. Both current styles use
   conservative centered `headlineMedium`; Morph adopts the M3 Expressive
   "bold type creates hierarchy" pillar so the text block reads as a poster,
   not a caption. Artist stays a tappable pill (`onArtistClick` exists on both
   current styles and is kept).
5. **Summonable controls** — no permanent button deck. A
   `HorizontalFloatingToolbar` (already proven in GESTURE) floats at the
   bottom with the 4–5 secondary actions (shuffle, previous, next, repeat,
   favorite); it auto-hides after a few seconds of inactivity and springs back
   on any tap, keeping the resting screen almost furniture-free.
   Play/pause has no button at all: **tapping the hero shape toggles playback**,
   and the shape's morph (organic-cycling vs settled circle) is the state
   feedback. Queue, lyrics, add-to-playlist, sleep timer live behind one
   overflow action reusing the existing `ExpressiveQueueView`-style surfaces,
   `SyncedLyricsView`, `AddToPlaylistSheet`, and `SleepTimerDialog`.

### Gestures (consistent with the container)

`ExpandablePlayer` already owns vertical-drag collapse, so Morph only adds:

- **Tap hero:** play/pause (shape morph is the feedback).
- **Horizontal drag on hero:** skip next/previous with the shape stretching
  in the drag direction before release (spring-back if under threshold) —
  the same threshold-plus-spring pattern `ExpandablePlayer` uses for dismiss.
- **Drag on progress ring:** scrub.
- **Long-press hero:** peek the queue.

---

## 3. Philosophy — why this is the *totally* expressive style

### 3.1 The M3 Expressive thesis

Material 3 Expressive's core claim (see `Material_3_expressive/Overview.md`)
is that UI elements are not rigid pixels but "physical objects with mass,
elasticity, and personality", and that emotion is a design function, not
decoration. Google's research program behind the system — 46 studies with
over 18,000 participants, using eye-tracking, surveys, and usability testing —
reported that well-applied expressive design let participants spot key UI
elements up to four times faster, raised perceived modernity (+34%),
subcultural relevance (+32%) and innovation (+30%), and was strongly
preferred across age groups (87% of 18–24-year-olds). Crucially, the same
research warns that expressiveness applied *against* usability (hiding or
displacing a key action) degrades the experience — expression must encode
meaning, not obscure it.

Morph is designed directly on that finding: every expressive element carries
a semantic.

| Expressive element | Meaning it encodes |
|---|---|
| Hero shape cycling organically | "Music is playing" |
| Shape settled to a circle | "Paused / at rest" |
| Wavy ring amplitude | Playback activity + progress position |
| Shape stretch under horizontal drag | "You are about to skip" (with spring-back = cancel) |
| Morph-through transition on track change | "This is a new object, not a mutation of the old one" |
| Toolbar auto-hide | "The music is the interface; controls are guests" |

Nothing animates *because it can*; the animation is the status display. This
is the M2 → M3E shift the repo's own `docs/DesignMindset.md` frames as
"Does it work?" → "Does it *feel* right?", and Norman's three levels mapped
concretely:

- **Visceral** — a living, breathing artwork shape on first open; color mist
  from the track itself.
- **Behavioral** — fewer chrome elements than CLASSIC, bigger touch targets
  (the hero is the play button — effectively the largest touch target
  possible), interruptible spring physics everywhere so mid-animation input
  is never ignored.
- **Reflective** — a player that looks like nothing else; shareable,
  identity-forming ("my player breathes with the song"). This is the
  "Differentiation" argument from `Overview.md` and the app's stated voice:
  *Vibrant, Personal, Alive*.

### 3.2 Design principles for the style

1. **Shape is the protagonist.** One hero shape carries identity and state.
   No competing decorated containers — everything else is quiet
   (`surfaceContainer` tones, standard 16dp radii) so the morph reads.
2. **Motion is a language, not a garnish.** All springs come from
   `MaterialTheme.motionScheme` (`slowSpatialSpec` for the hero,
   `fastSpatialSpec`/`fastEffectsSpec` for toolbar and micro-feedback), never
   hand-tuned tweens — consistent with `Motion.md` and with how
   `ExpandablePlayer` already animates its expansion.
3. **Color is borrowed from the music.** Palette-extracted mist +
   `primaryContainer` accents; the style adds no new color system.
4. **Type goes hero.** Display-scale title, bold, poster-like block.
5. **Controls are summoned, never resident.** Floating toolbar with
   auto-hide; the resting state is art + ring + type only.
6. **Everything is interruptible.** Springs allow catch-up and retargeting;
   a user who taps mid-morph gets an immediate retarget, not a queued
   animation.
7. **Expression never hides the primary action.** The primary action
   (play/pause) is the biggest target on screen; skip/scrub have visible,
   reversible gestures with spring-back cancel. This is the usability guard
   Google's research flags.

---

## 4. Component mapping (all available at current versions)

`material3 = 1.5.0-alpha13` and `graphics-shapes = 1.0.1` (from
`gradle/libs.versions.toml`) cover everything below; compiler-level opt-ins
for `ExperimentalMaterial3ExpressiveApi` are already global.

| Element | API |
|---|---|
| Hero clip | `MaterialShapes.*` polygons + `Morph(start, end)` + custom `Shape` (`Outline.Generic(morph.toPath(progress))`) per `Material_3_expressive/Shapes.md` Method 2 |
| Morph progress / settle | `animateFloatAsState` with `motionScheme.slowSpatialSpec()`; continuous cycle via `rememberInfiniteTransition` |
| Breathing scale | `rememberInfiniteTransition` + `graphicsLayer { scaleX/scaleY }` (layer-only, no relayout) |
| Progress ring | `CircularWavyProgressIndicator` (determinate, stroke ~6dp) around the hero, or custom wavy arc via `drawScope` if the component's radius cannot wrap the hero size |
| Scrub input | Invisible drag surface, local scrub state, single `seekTo` on release (pattern from `PlayerSheetContent.kt`) |
| Toolbar | `HorizontalFloatingToolbar` + `FloatingToolbarDefaults` (pattern from `GesturePlayerContent.kt`), `AnimatedVisibility` slide+fade with `fastSpatialSpec` |
| Favorite | existing `LikeBurstIcon` |
| Buffering | `LoadingIndicator` polygon list applied at hero scale |
| Queue / lyrics / playlist / timer | reuse `ExpressiveQueueView` pattern, `SyncedLyricsView`, `AddToPlaylistSheet`, `SleepTimerDialog` |
| Ambient | `ChromaticMistBackground` unchanged |

---

## 5. Integration plan (follows existing conventions exactly)

1. **`data/ThemePreferences.kt`** — add `MORPH` to the `PlayerStyle` enum.
   Persistence already round-trips by enum name with a `CLASSIC` fallback, so
   old prefs are safe.
2. **`ui/player/MorphPlayerContent.kt`** — new file exposing
   `MorphPlayerSheetContent(viewModel, ambientBackground, onCollapse,
   onLoadMore, onArtistClick)` — the same signature as
   `PlayerSheetContent`/`GesturePlayerSheetContent`, consuming only existing
   `PlayerViewModel` state (no ViewModel changes needed).
3. **`ExpandablePlayer.kt`** — third branch in the `when (playerStyle)`.
   Respect the existing performance contract documented there: the expanded
   layer is measured once at fixed full-screen height; the hero's breathing
   and morphing must live in `graphicsLayer`/`Canvas` so no per-frame
   relayout occurs.
4. **`ui/settings/SettingsScreen.kt`** — extend
   `ExpressivePlayerStyleSelectItem` from two to three segments
   (Classic / Gesture / Morph). No new preference plumbing: the
   `playerStyle` flow, `ThemeViewModel` delegate and `MainActivity`
   threading already exist.

No data-layer, service, or navigation changes. The style is purely a
presentation-layer sibling.

---

## 6. Performance and accessibility guardrails

- **Morph cost:** `Morph.toPath()` allocates; compute per frame into a
  reused `Path` inside a `Canvas`/`drawWithCache`, cache `Morph` pairs with
  `remember`, and keep the polygon cycle to a small fixed set. The clip shape
  itself changes via `graphicsLayer` clip, not layout.
- **Battery:** pause all infinite transitions when `!isPlaying` and when the
  player is collapsed (`expandProgress == 0`), mirroring how the mist layer
  is already gated by `enabled`.
- **Reduced motion:** if system animator scale is 0 (or a future in-app
  reduce-motion setting), freeze the cycle at a fixed pleasant polygon and
  express play/pause as a single settle morph only.
- **Contrast:** hero typography sits on the mist; keep the existing
  scrim-gradient trick from the current album containers under the text
  block, and use `onSurface`/`onSurfaceVariant` roles, never raw white.
- **Touch:** ring scrub band at least 48dp tall; toolbar buttons keep the
  standard FAB sizing from GESTURE; hero tap target is the whole shape.
- **Discoverability of hidden controls:** first-run one-time hint chip
  ("Tap the art to pause, swipe to skip"), consistent with the app's
  onboarding tone; the toolbar is summoned by any tap, so there is no
  dead-end state.

---

## 7. Risks and open questions

- **Wavy ring around a non-circular, rotating shape** — wrapping progress
  around the morphing outline is visually rich but geometrically fiddly;
  the pragmatic v1 is a circular ring circumscribing the shape's bounds.
- **Legibility with busy artwork** inside star-like polygons (SoftBurst
  crops aggressively). Constrain the cycle to high-area polygons
  (Cookie/Sunny family), keep `ContentScale.Crop` centered.
- **Auto-hiding the toolbar** trades resting beauty for one extra tap on
  secondary actions. Mitigation: generous timeout (~5s), always visible
  while user is interacting, optional "keep controls visible" toggle later
  if feedback demands it (would follow the standard four-file settings
  threading).
- **Tap-to-pause vs tap-to-expand conflict** — none in practice: the hero
  only exists in the expanded player; the mini player keeps its own
  play/pause button.

---

## 8. Sources

- In-repo: `Material_3_expressive/Overview.md`, `Motion.md`, `Shapes.md`,
  `Toolbars.md`; `docs/DesignMindset.md`,
  `docs/Material3ExpressiveDesignGuide.md`; `ui/player/PlayerSheetContent.kt`,
  `GesturePlayerContent.kt`, `ExpandablePlayer.kt`,
  `ChromaticMistBackground.kt`; `data/ThemePreferences.kt`.
- Google Design, "Expressive Design: Google's UX Research"
  (design.google/library/expressive-material-design-google-research) — 46
  studies, 18,000+ participants; glanceability, preference and perception
  findings.
- Material Design blog, "Start building with Material 3 Expressive"
  (m3.material.io/blog/building-with-m3-expressive) — component and
  motion-scheme availability.
