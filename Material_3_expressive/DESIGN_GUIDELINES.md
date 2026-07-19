# Koda Design Guidelines — Material 3 Expressive & Frontend UX

This is the umbrella design document for Koda. The component-level guides in this
folder (Buttons, Motion, Shapes, Theming, Toolbars, ...) describe individual APIs;
this document describes the design system as a whole: the philosophy, the rules,
the app-specific conventions, and the review checklist every new screen or
component must pass. When this document and personal taste disagree, this
document wins. When this document and measured usability disagree, fix the
document.

---

## 1. Philosophy: expression is usability

Material 3 Expressive (Google I/O 2025) is the most research-backed Material
release: 46 studies, hundreds of design iterations, 18,000+ participants. Its
central finding:

> Emotion and usability are not in tension — they reinforce each other.

The numbers that justify every expressive decision in this app:

- Key UI elements were found **up to 4x faster** in expressive designs than in
  standard M3 (eye-tracking studies).
- Expressive hierarchy **erased the age gap**: users 45+ located key actions as
  fast as younger users.
- **87% of 18–24 year olds** preferred expressive designs; preference was
  net-positive across all age groups.
- Expressive designs raised brand perception: +32% subculture relevance,
  +34% modernity, +30% boldness.

Expression is not decoration. Every expressive choice must trace back to one of
the five levers below and serve attention, hierarchy, or feedback. If a flourish
does none of those, cut it.

### Calibration: where Koda sits

Expressiveness is a spectrum from calm to vibrant, and the correct level depends
on context. Koda is a music and video app — the canonical high-expressiveness
category. Users are emotionally engaged; playful motion and bold shape are
appropriate almost everywhere.

Two zones deliberately run cooler:

- **Settings and dialogs** — moderate. Structure and clarity first; expression
  through shape (32dp dialog corners, 64dp icon boxes) and gentle press
  animations, not through motion spectacle.
- **Destructive or irreversible flows** (delete download, clear history,
  sign out) — low. No bounce on a confirmation dialog. Trust reads as calm.

The single most important research warning: **never break an established UX
pattern for visual novelty.** A playlist redesigned as scattered album art
tested as modern-looking and unusable. Novel presentation of standard content
must keep the standard interaction model.

---

## 2. The five expressive levers

Every design decision in Koda maps to one or more of: **Color, Shape, Size,
Motion, Containment.** Use them deliberately and name the lever when reviewing.

### 2.1 Color

- Only `MaterialTheme.colorScheme` tokens. Never hardcode hex values in
  screens; the palette system (`ColorPalettes.kt`, dynamic color from
  wallpaper) must be able to re-skin every screen. The one sanctioned
  exception is artwork-derived color (player ambient backgrounds), which is
  computed at runtime, not hardcoded.
- Hierarchy through container roles: `primaryContainer` / `secondaryContainer`
  / `tertiaryContainer` make key actions pop against `surfaceContainer` cards.
  The research case: a large, secondary-colored action was found 4x faster
  than a small icon in an app bar.
- Depth through tonal surfaces, not shadows: `surface` -> `surfaceContainer`
  -> `surfaceContainerHigh` is Koda's elevation story (cards use
  `surfaceContainer`, dialogs and the mini player use `surfaceContainerHigh`).
- Never rely on color alone to signal state; pair it with shape, size, or a
  label (the like button fills *and* changes color; the active nav tab gets a
  pill *and* a tint).

### 2.2 Shape

- Round communicates tappable, soft, friendly. Sharp rectangles are reserved
  for media frames (video surface) where the content is the shape.
- Koda's corner radius scale — use these, don't invent new ones:

  | Radius | Use |
  |---|---|
  | 8–14dp | Small controls: chips, icon boxes inside rows, thumbnails |
  | 16dp | The standard content card (`Surface` + `surfaceContainer`) |
  | 20–24dp | Large feature cards, sheets |
  | 28dp | Pills: mini players, nav bar, hero buttons |
  | 32dp | Dialogs (`AlertDialog` with `surfaceContainerHigh`) |
  | Full/circle | FABs, play buttons, artwork accents |

- `MaterialShapes` polygons (Cookie9Sided, Sunny, SoftBurst, Pill, Clover4Leaf
  are the house favorites) are for **moments**, not layout: loading
  indicators, artwork masks, decorative accents. Layout containment stays on
  `RoundedCornerShape`.
- Shape morphing (graphics-shapes library) is the expressive way to change
  state: corners melt from 28dp pill to 0dp fullscreen as the video player
  expands — never snap a shape between states when the surface itself is
  animating.

### 2.3 Size

- The most important action on a screen must be physically the largest
  tappable thing on it. Play/pause in the expanded player dwarfs the
  secondary controls; that is intentional and must survive redesigns.
- Minimum touch target 48x48dp, no exceptions — including seek bar thumbs and
  chip rows. Expressive sizing usually exceeds it.
- Prefer labeled actions over bare icons for primary flows (extended FAB over
  icon FAB, "Shuffle" pill over a lone shuffle glyph). Icon-only is acceptable
  in dense secondary rows where the icon is unambiguous (skip, repeat).
- Size hierarchy must be legible at a glance. If a screenshot in grayscale
  doesn't reveal the primary action, the hierarchy has failed.

### 2.4 Motion

- **Springs, not tweens.** The theme already sets
  `MotionScheme.expressive()`; M3 components animate correctly on their own.
  Custom animation must use `spring()` or the theme's motion scheme specs —
  never `tween()` with a hand-picked duration for anything the user perceives
  as physical.
- Consume the scheme in custom components instead of re-inventing constants:
  - `MaterialTheme.motionScheme.fastSpatialSpec()` — small things moving
    (chips, toggles, list items appearing).
  - `defaultSpatialSpec()` — standard layout/navigation movement.
  - `slowSpatialSpec()` — large container transforms (player expansion).
  - `fastEffectsSpec()` / `slowEffectsSpec()` — color, alpha, and other
    non-spatial changes (highlights, crossfades).
- House spring for hand-rolled overlay physics: `spring(stiffness = 300f,
  dampingRatio = 0.8f)` — the video overlay, player transitions, and drag
  settles all use it. Match it rather than introducing new feels per screen.
- Motion must communicate: state change (expand/collapse), causality (press ->
  squish), or continuity (drag hand-off into settle — the video player's
  `expandProgress` pattern, where the release animation continues from the
  finger's position instead of jumping). Decorative-only motion is cut.
- Interruptibility is non-negotiable: a spring interrupted mid-flight must
  retarget smoothly (springs do this natively; another reason tweens are
  banned for spatial work).
- Staggered entrances (`AnimatedVisibility` with per-index delay) are the
  house pattern for list/screen appearance. Keep the stagger subtle
  (tens of ms per item), never so long that content feels withheld.
- Respect reduced-motion preferences for large or repeated animations; ambient
  backgrounds and loaders should degrade gracefully.

### 2.5 Containment

- No naked content: lists, shelves, and settings rows live inside `Surface`
  cards (16dp, `surfaceContainer`). Containment groups related things and
  creates safe visual structure without borders.
- Settings follow a fixed grammar: `SettingsSection(title)` wrapping an
  `ExpressiveSettingsCard`, rows separated by `SettingsDivider()`, each row an
  `ExpressiveSettingsItem` or `Expressive*ToggleItem` (48dp icon box,
  `RoundedCornerShape(14.dp)`, press-scale spring). New settings copy these
  composables; they do not invent row layouts.
- Dialogs follow one recipe: `AlertDialog`, `containerColor =
  surfaceContainerHigh`, `shape = RoundedCornerShape(32.dp)`, 64dp rounded
  icon box, spring `scaleIn` entrance via `AnimatedVisibility`.
- Overlays (music player, video player) float above the NavHost as
  independent surfaces — containment communicates that they are a layer, with
  their own shape (pill when mini, full-bleed when expanded).

---

## 3. Typography

M3 Expressive ships two parallel scales of 15 styles each — baseline and
**emphasized** — across display, headline, title, body, label in
large/medium/small. Rules for Koda:

- Vary size dramatically between roles. A screen where everything is
  `bodyMedium` has no hierarchy. Hero moments (now playing title, greeting
  headers) earn `headlineMedium`+; metadata stays `bodySmall`/`labelMedium`.
- Use the emphasized styles (or `FontWeight.ExtraBold` where the emphasized
  scale isn't exposed yet) for the single current focus — the active lyric
  line pattern: current line `headlineMedium` ExtraBold + primary color,
  inactive lines `titleLarge` Medium at reduced alpha.
- One line of hierarchy per level: title says what it is, subtitle says the
  secondary fact, label row says the metadata. Don't stack three subtitles.
- Never shrink text to fit layout problems; fix the layout. Ellipsize with
  `TextOverflow.Ellipsis` and give marquee treatment only to the now-playing
  title.

---

## 4. Component selection rules

Strict priority, per repo rules:

1. **M3 Expressive component** if one exists: `LoadingIndicator` (with
   `MaterialShapes` polygons) over `CircularProgressIndicator`, wavy progress
   for playback, `FloatingToolbar`, `SplitButton`, `ButtonGroup`, segmented
   buttons, FAB menu over speed-dial, docked toolbar over bottom app bar.
2. **Standard M3 component** when no expressive variant exists.
3. **Hand-rolled** only when neither fits, built from `androidx.compose.
   animation` and `graphics-shapes` primitives, consuming theme tokens and the
   motion scheme — never a parallel design system.

Do not invent components, parameters, or library features. Verify against the
pinned Material3 version before using anything experimental; the
`ExperimentalMaterial3ExpressiveApi`/`ExperimentalMaterial3Api` opt-ins are
already global in `app/build.gradle.kts`.

---

## 5. Frontend UX rules

### Feedback and state

- Every tap answers within 100ms with something visual (press scale, ripple,
  optimistic state flip). Network truth can arrive later and correct it —
  the like button flips instantly, the request follows.
- Every async surface has all four states designed: loading (expressive
  `LoadingIndicator`, or skeletons for content shelves), success, empty
  (icon + one-line explanation + optional action — the "No lyrics available"
  pattern), and error (what happened + what the user can do; a retry where
  retrying can help). Silent failure is a defect: surface errors like the
  player's one-shot toast pattern.
- Loading states that can exceed a few seconds need a watchdog so a spinner
  can never outlive the possibility of success (buffering watchdog pattern).

### Gestures

- Gestures are accelerators, never the only path. Long-press saves a video,
  but the overflow menu offers the same action. Drag minimizes the player,
  but the collapse button exists.
- Drag gestures drive progress directly (finger-attached, `snapTo`), and
  release settles with momentum: fast fling commits regardless of position,
  slow release decides by threshold. This is the shared player-minimize
  grammar; reuse it for any new draggable surface.
- Hold-to-2x, swipe-to-skip and similar power gestures must not collide with
  system gestures or scrolling; test on-device at screen edges.

### Navigation and structure

- One primary action per screen; one focus per moment. Screens are built
  around what the user most likely came to do.
- Preserve scroll position and state across tab switches and process
  recreation where feasible. Losing a user's place is a bug, not a redesign
  opportunity.
- Bottom-of-screen content must clear the floating nav bar and any active
  mini players — use the dynamic bottom padding pattern (`listBottomPadding`)
  rather than fixed magic values, since up to two mini players can stack.

### Accessibility

- Contrast: 4.5:1 body text, 3:1 large text and UI components — in every
  palette, both themes. New palettes are validated before shipping.
- 48dp minimum targets, labels on primary actions, `contentDescription` on
  meaningful icons (null for purely decorative ones), `semantics {}` where
  the visual grouping isn't obvious to TalkBack.
- The expressive claim is that these guidelines help older and
  motor/vision-impaired users most — the 4x-faster and age-gap findings came
  from exactly the patterns above. Accessibility review is part of design
  review, not a post-hoc pass.

---

## 6. Anti-patterns

| Don't | Do instead |
|---|---|
| Hardcode colors (`Color(0xFF...)`) in screens | `MaterialTheme.colorScheme` tokens; palettes must re-skin everything |
| `tween(300)` for movement | `spring()` / motion scheme specs |
| New spring constants per screen | House spring (300f / 0.8) or scheme specs |
| Same corner radius everywhere | The radius scale in section 2.2 |
| Icon-only primary actions | Icon + label; size to importance |
| Naked `Column` of rows | Contained card with the settings/list grammar |
| Snap shape or size between states | Morph/animate through the transition |
| Scattered-art novelty layouts | Expressive styling on standard patterns |
| Spinner with no timeout or error path | Watchdog + explicit error/empty states |
| Bounce on destructive confirmations | Calm, standard-motion dialogs |
| Blocking the UI on network truth | Optimistic updates, corrected on failure |
| Custom component duplicating an M3 one | The M3/Expressive component |

---

## 7. Design review checklist

Run this list on every new screen, component, or redesign PR:

- [ ] Primary action identifiable at a glance (size + color, grayscale test)
- [ ] Every color from theme tokens; verified in light, dark, and at least one
      non-dynamic palette
- [ ] Corner radii from the house scale; shapes morph rather than snap where
      the surface animates
- [ ] All motion spring-based, interruptible, and communicating state — no
      decorative-only animation
- [ ] Related content contained; settings/dialog grammar followed where
      applicable
- [ ] Typography hierarchy: distinct roles, emphasized style on the single
      current focus
- [ ] Loading, empty, and error states all designed and reachable
- [ ] Tap targets >= 48dp; icons labeled or described; contrast checked
- [ ] Bottom padding clears nav bar + possible mini player stack
- [ ] Established UX patterns preserved; gestures have button equivalents
- [ ] Expressiveness level matches the flow (playful for media moments, calm
      for settings and destructive actions)

---

## 8. Sources

- Material 3 Expressive announcement and research summary (Google I/O 2025):
  46 studies, 18k+ participants, 4x attention finding, age-gap finding.
- M3 Expressive component updates: button groups, FAB menu, loading
  indicator, split button, docked/floating toolbars; 15 baseline + 15
  emphasized type styles; 35-shape library with morphing; physics-based
  motion scheme replacing duration/easing.
- Repo component guides in this folder (Buttons, Motion, Shapes, Theming,
  Toolbars, ProgressIndicators, Lists, Menus, Carousel, Overview).
- Koda codebase conventions: `ui/settings/SettingsScreen.kt` (settings
  grammar), `ui/video/VideoPlayerOverlay.kt` (drag physics, shape morph),
  `ui/player/SyncedLyricsView.kt` (typographic focus), `ui/theme/Theme.kt`
  (expressive motion scheme, palettes).
