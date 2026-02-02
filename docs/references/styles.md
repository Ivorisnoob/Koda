# Material 3 Styles

Material 3 Styles define the visual language through color, typography, shape, elevation, icons, and motion.

## Table of Contents

1. [Color](#color)
2. [Typography](#typography)
3. [Elevation](#elevation)
4. [Shape](#shape)
5. [Icons](#icons)
6. [Motion](#motion)

---

## Color

### Color System Overview

The Material 3 color system creates accessible and personalizable color schemes.

URL: https://m3.material.io/styles/color/system/overview

### Color Roles

Roles that map UI elements to specific colors:

#### Primary Colors
- **primary**: The main color of the app (primary buttons, active states).
- **onPrimary**: Text/icons on the primary color.
- **primaryContainer**: Container for primary elements.
- **onPrimaryContainer**: Text on the container.

#### Secondary & Tertiary
- **secondary**: Accent color.
- **tertiary**: Colors for emphasis or balance adjustment.

#### Surface Colors
- **surface**: Background for cards, sheets, and menus.
- **surfaceVariant**: A slightly different background color.
- **surfaceTint**: Used for representing elevation.

#### Semantic Colors
- **error**: Error states.
- **warning**: Warning states (available in some implementations).
- **success**: Success states (available in some implementations).

URL: https://m3.material.io/styles/color/roles

### Color Schemes

#### Dynamic Color
Extracting colors from user wallpapers or selections:
- **User-generated**: From the user's explicit choice.
- **Content-based**: Extracted from images or content.

URL: https://m3.material.io/styles/color/dynamic-color/overview

#### Static Color
Fixed color schemes:
- **Baseline**: The default Material baseline.
- **Custom brand**: Custom brand colors.

URL: https://m3.material.io/styles/color/static/baseline

### Key Colors and Tones

- **Source color**: The starting point for generating a scheme.
- **Tonal palette**: 13 tones (0, 10, 20, ..., 100) generated from each key color.
- Light theme: Typically uses tone 40 for primary.
- Dark theme: Typically uses tone 80 for primary.

URL: https://m3.material.io/styles/color/the-color-system/key-colors-tones

### Tools

**Material Theme Builder**: Tools for generating, customizing, and exporting color schemes.

URL: https://m3.material.io/blog/material-theme-builder-2-color-match

---

## Typography

### Type Scale

Material 3 defines 15 type styles across 5 roles and 3 sizes:

#### Roles
1. **Display**: Large, short text (hero moments, banners).
2. **Headline**: Medium-sized headings.
3. **Title**: Smaller headings (app bars, list items).
4. **Body**: Main body text.
5. **Label**: Buttons, tabs, and small text.

#### Sizes
- **Large**: Maximum size.
- **Medium**: Standard size.
- **Small**: Minimum size.

#### Example Styles
```
displayLarge: 57sp, -0.25 letter spacing
headlineMedium: 28sp, 0 letter spacing
bodyLarge: 16sp, 0.5 letter spacing
labelSmall: 11sp, 0.5 letter spacing
```

URL: https://m3.material.io/styles/typography/overview

### Fonts

- Defaults: **Roboto** (Android), **San Francisco** (iOS), **Roboto** (Web).
- Support for custom fonts.
- Leveraging variable fonts.

URL: https://m3.material.io/styles/typography/fonts

### Applying Typography

- Semantic usage (use headline for headings, body for body text).
- Consistent hierarchy.
- Appropriate line heights and spacing settings.

URL: https://m3.material.io/styles/typography/applying-type

---

## Elevation

### Overview

Elevation represents the distance between surfaces on the Z-axis.

URL: https://m3.material.io/styles/elevation/overview

### Elevation Levels

Material 3 defines 6 elevation levels:

| Level | DP | Use Case |
|-------|-----|----------|
| 0 | 0dp | Default surface |
| 1 | 1dp | Cards, slightly raised elements |
| 2 | 3dp | Search bars |
| 3 | 6dp | FAB (resting state) |
| 4 | 8dp | Navigation drawers |
| 5 | 12dp | Modal bottom sheets, dialogs |

### Elevation Representation

Material 3 represents elevation in two ways:

1. **Shadow**: Elevation via shadows (primarily in Light theme).
2. **Surface tint**: Overlaying a color tint on surfaces (primarily in Dark theme).

URL: https://m3.material.io/styles/elevation/applying-elevation

---

## Shape

### Overview

Shape is used to direct attention, express states, and communicate brand identity.

URL: https://m3.material.io/styles/shape/overview-principles

### Corner Radius Scale

Material 3 defines 5 shape tokens:

| Token | Default Value | Use Case |
|-------|---------------|----------|
| None | 0dp | Full screen, strict layouts |
| Extra Small | 4dp | Checkboxes, small elements |
| Small | 8dp | Chips, small buttons |
| Medium | 12dp | Cards, standard buttons |
| Large | 16dp | FABs, large cards |
| Extra Large | 28dp | Dialogs, bottom sheets |
| Full | 9999dp | Perfect circles |

### Shape Morph

**Key feature of M3 Expressive**: Animations where shapes transform smoothly.

- Visual flow during transitions.
- Enhanced brand expression.
- Attracting user attention.

URL: https://m3.material.io/styles/shape/shape-morph

---

## Icons

### Material Symbols

Material Symbols are variable icon fonts:

#### Styles
- **Outlined**: Outline-only style (default).
- **Filled**: Filled style.
- **Rounded**: Rounded corner style.
- **Sharp**: Sharp corner style.

#### Variable Features
- **Weight**: Stroke thickness (100-700).
- **Grade**: Visual weight (-25 to 200).
- **Optical size**: Optimization for display size (20, 24, 40, 48dp).
- **Fill**: Fill state (0 or 1).

#### Sizes
- 20dp: Dense layouts.
- 24dp: Standard size.
- 40dp: Large touch targets.
- 48dp: Extra-large touch targets.

URL: https://m3.material.io/styles/icons/overview

### Custom Icons

Design guidelines for custom icons:
- 24×24dp grid.
- 2dp stroke width.
- 2dp corner radius.
- Consistent metaphors.

URL: https://m3.material.io/styles/icons/designing-icons

---

## Motion

**Core element of M3 Expressive**: Motion makes the UI expressive and easy to use.

URL: https://m3.material.io/styles/motion/overview

### Motion Principles

1. **Informative**: Communicates information to the user.
2. **Focused**: Appropriately directs attention.
3. **Expressive**: Enhances emotional engagement.

URL: https://m3.material.io/styles/motion/overview/how-it-works

### Easing and Duration

#### Easing Types

Material 3 defines 4 easing curves:

1. **Emphasized**: Dramatic and expressive movement.
   - Decelerate: cubic-bezier(0.05, 0.7, 0.1, 1.0)
   - Accelerate: cubic-bezier(0.3, 0.0, 0.8, 0.15)
   - Standard: cubic-bezier(0.2, 0.0, 0, 1.0)

2. **Standard**: Balanced, standard movement.
   - cubic-bezier(0.2, 0.0, 0, 1.0)

3. **Emphasized Decelerate**: Elements entering the screen.
   - cubic-bezier(0.05, 0.7, 0.1, 1.0)

4. **Emphasized Accelerate**: Elements leaving the screen.
   - cubic-bezier(0.3, 0.0, 0.8, 0.15)

#### Duration Guidelines

| Element Change | Duration |
|----------------|----------|
| Small (icon state) | 50-100ms |
| Medium (component state) | 250-300ms |
| Large (layout change) | 400-500ms |
| Complex transition | 500-700ms |

**Important**: Avoid excessively long animations (>1000ms).

URL: https://m3.material.io/styles/motion/easing-and-duration

### Transitions

Transition patterns during navigation:

#### Transition Types

1. **Container transform**: A container transforms into the next screen.
2. **Shared axis**: Movement along a common axis (X, Y, Z).
3. **Fade through**: Fade out followed by fade in.
4. **Fade**: Simple fade animation.

#### When to Use Each

- **Container transform**: List item to detail screen.
- **Shared axis X**: Tab switching, horizontal navigation.
- **Shared axis Y**: Steppers, vertical navigation.
- **Shared axis Z**: Back/Forward navigation.
- **Fade through**: Content updates (low relationship).
- **Fade**: Overlays, auxiliary changes.

URL: https://m3.material.io/styles/motion/transitions/transition-patterns

### M3 Expressive Motion

**New expressive motion system**:

- Bolder animations.
- Customizable motion themes.
- Enhanced brand expression.

URL: https://m3.material.io/blog/m3-expressive-motion-theming

---

## References

- Material Design 3 Styles: https://m3.material.io/styles/
- Material Theme Builder: https://material-foundation.github.io/material-theme-builder/
- Material Symbols: https://fonts.google.com/icons
