# Material 3 Foundations

Material 3 Foundations define the design principles and patterns that serve as the base for all Material interfaces.

## Table of Contents

1. [Accessibility](#accessibility)
2. [Layout](#layout)
3. [Interaction](#interaction)
4. [Content Design](#content-design)
5. [Design Tokens](#design-tokens)
6. [Adaptive Design](#adaptive-design)

---

## Accessibility

### Core Principles
- Design for users with diverse abilities.
- Integration with assistive technologies like screen readers.
- WCAG-compliant contrast ratios.

### Key Areas

#### Structure and Elements
- Intuitive layout hierarchy.
- Design of accessible UI elements.
- Focus management and navigation.

URL: https://m3.material.io/foundations/designing/structure

#### Color Contrast
- WCAG-compliant color contrast.
- Visual clarity for text and UI controls.
- 4.5:1 (Normal text), 3:1 (Large text, UI components).

URL: https://m3.material.io/foundations/designing/color-contrast

#### Text Accessibility
- Support for text resizing (up to 200%).
- Accessible text truncation.
- Clear and adaptable writing.

URL: https://m3.material.io/foundations/writing/text-resizing

---

## Layout

### Understanding Layout

#### Core Components
- **Regions**: Major screen areas (header, body, navigation).
- **Columns**: Fundamental units of the grid system.
- **Gutters**: Spaces between columns.
- **Spacing**: 4dp-based consistent spacing system.

URL: https://m3.material.io/foundations/layout/understanding-layout/overview

### Window Size Classes

Responsive design based on screen size:

| Size Class | Width | Typical Device | Key Patterns |
|-----------|-------|---------------|--------------|
| Compact | <600dp | Phone | Single pane, bottom nav |
| Medium | 600-840dp | Tablet (portrait) | Dual pane optional, nav rail |
| Expanded | >840dp | Tablet (landscape), Desktop | Dual/multi pane, nav drawer |
| Large/XL | >1240dp | Large screens, TV | Multi-pane, extensive nav |

URL: https://m3.material.io/foundations/layout/applying-layout/window-size-classes

### Canonical Layouts

Commonly used layout patterns:

1. **List-detail**: Master-detail navigation.
2. **Feed**: Content feed.
3. **Supporting pane**: Auxiliary content panel.

URL: https://m3.material.io/foundations/layout/canonical-layouts/overview

---

## Interaction

### States

#### Visual States
- **Enabled**: Default state.
- **Hover**: Pointer hovering (Desktop).
- **Focused**: Keyboard focus.
- **Pressed**: Actively being pressed.
- **Dragged**: During a drag operation.
- **Disabled**: Disabled state.

#### State Layers
Semi-transparent overlays visually indicating states:
- Hover: 8% opacity
- Focus: 12% opacity
- Press: 12% opacity

URL: https://m3.material.io/foundations/interaction/states/state-layers

### Gestures

Touch gestures for mobile interfaces:
- Tap: Basic selection.
- Long press: Context menu.
- Drag: Move, rearrange.
- Swipe: Navigation, deletion.
- Pinch: Zoom.

URL: https://m3.material.io/foundations/interaction/gestures

### Selection

Selection interaction patterns:
- **Single selection**: Radio buttons, list items.
- **Multi selection**: Checkboxes, selectable lists.

URL: https://m3.material.io/foundations/interaction/selection

---

## Content Design

### UX Writing Principles

1. **Clear**: Explicit and easy to understand.
2. **Concise**: Brief and to the point.
3. **Useful**: Meets user needs.
4. **Consistent**: Uniformity in terminology and tone.

### Notifications

Effective notification content:
- Actionable information.
- Clear next steps.
- Understanding of user context.

URL: https://m3.material.io/foundations/content-design/notifications

### Alt Text

Accessible image descriptions:
- Decorative images: Empty alt attribute.
- Functional images: Describe the action.
- Informational images: Briefly describe the content.

URL: https://m3.material.io/foundations/content-design/alt-text

### Global Writing

Writing for global audiences:
- Word choices considering localization.
- Culturally neutral expressions.
- Grammar structures easy to translate.

URL: https://m3.material.io/foundations/content-design/global-writing/overview

---

## Design Tokens

### What are Design Tokens?

Design tokens are the smallest units of design decisions used across design, tools, and code:

- **Color tokens**: primary, secondary, surface, error, etc.
- **Typography tokens**: displayLarge, bodyMedium, etc.
- **Shape tokens**: cornerRadius, roundedCorner, etc.
- **Motion tokens**: duration, easing curves.

### Benefits

- Consistency between design and code.
- Ease of theme customization.
- Unity across platforms.

URL: https://m3.material.io/foundations/design-tokens/overview

---

## Adaptive Design

### Principles

- **Responsive**: Adjusts to window size.
- **Adaptive**: Optimizes for device characteristics.
- **Contextual**: Considers usage context.

### Key Strategies

1. Layout adjustments based on Window size classes.
2. Support for input methods (touch, mouse, keyboard).
3. Leveraging device features (camera, location, etc.).
4. Handling offline and online scenarios.

URL: https://m3.material.io/foundations/adaptive-design

---

## References

- Material Design 3 Foundations: https://m3.material.io/foundations/
- Glossary: https://m3.material.io/foundations/glossary
