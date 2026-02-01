---
name: material-thinking
description: Comprehensive Material Design 3 (M3) and M3 Expressive guidance for building modern, accessible, and engaging user interfaces. Use when designing or implementing Material Design interfaces, reviewing component designs for M3 compliance, generating design tokens (color schemes, typography, shapes), applying M3 Expressive motion and interactions, or migrating existing UIs to Material 3. Covers all 38 M3 components, foundations (accessibility, layout, interaction), styles (color, typography, elevation, shape, icons, motion), and M3 Expressive tactics for more engaging experiences.
---

# Material Thinking

Apply Material Design 3 and M3 Expressive principles to create accessible, consistent, and engaging user interfaces.

## Overview

This skill provides comprehensive guidance for implementing Material Design 3 (M3) and M3 Expressive across all platforms. Material Design 3 is Google's open-source design system that provides UX guidance, reusable components, and design tools for creating beautiful, accessible interfaces.

**Key capabilities:**
- Design new products with M3 principles
- Review existing designs for M3 compliance
- Generate design tokens (colors, typography, shapes)
- Apply M3 Expressive for engaging, emotionally resonant UIs
- Select appropriate components for specific use cases
- Ensure accessibility and responsive design

## Core Resources

This skill includes four comprehensive reference documents. Read these as needed during your work:

### 1. Foundations (`references/foundations.md`)
Read when working with:
- Accessibility requirements
- Layout and responsive design (window size classes, canonical layouts)
- Interaction patterns (states, gestures, selection)
- Content design and UX writing
- Design tokens and adaptive design

### 2. Styles (`references/styles.md`)
Read when working with:
- Color systems (dynamic color, color roles, tonal palettes)
- Typography (type scale, fonts)
- Elevation and depth
- Shapes and corner radius
- Icons (Material Symbols)
- Motion and transitions

### 3. Components (`references/components.md`)
Read when selecting or implementing:
- Action components (buttons, FAB, segmented buttons)
- Selection and input (checkbox, radio, switch, text fields, chips)
- Navigation (nav bar, drawer, rail, app bars, tabs)
- Containment and layout (cards, lists, carousel, sheets)
- Communication (dialogs, snackbar, badges, progress, tooltips, menus)

### 4. M3 Expressive (`references/m3-expressive.md`)
Read when creating more engaging experiences:
- Expressive motion tactics
- Shape morphing
- Dynamic animations
- Brand expression
- Balancing expressiveness with usability

## Workflows

### Workflow 1: Designing a New Interface

When designing a new product or feature with Material 3:

1. **Define layout structure**
   - Read `references/foundations.md` → Layout section
   - Determine window size classes (compact/medium/expanded)
   - Choose canonical layout if applicable (list-detail, feed, supporting pane)

2. **Select components**
   - Read `references/components.md`
   - Use Component Selection Guide to choose appropriate components
   - Review specific component guidelines for usage and specs

3. **Establish visual style**
   - Read `references/styles.md`
   - Define color scheme (dynamic or static)
   - Set typography scale
   - Choose shape scale (corner radius values)
   - Plan motion and transitions

4. **Apply M3 Expressive (optional)**
   - Read `references/m3-expressive.md`
   - Identify key moments for expressive design
   - Apply emphasized easing and extended durations
   - Consider shape morphing for transitions
   - Follow 80/20 rule (80% standard, 20% expressive)

5. **Validate accessibility**
   - Read `references/foundations.md` → Accessibility section
   - Check color contrast (WCAG compliance)
   - Verify touch targets (minimum 48×48dp)
   - Ensure keyboard navigation and screen reader support

### Workflow 2: Reviewing Existing Designs

When reviewing designs for Material 3 compliance:

1. **Component compliance**
   - Read `references/components.md` for relevant components
   - Check if components follow M3 specifications
   - Verify proper component usage (e.g., not using filled buttons excessively)
   - Validate component states (enabled, hover, focus, pressed, disabled)

2. **Visual consistency**
   - Read `references/styles.md`
   - Verify color roles are used correctly
   - Check typography matches type scale
   - Validate elevation levels
   - Review shape consistency (corner radius)

3. **Accessibility audit**
   - Read `references/foundations.md` → Accessibility section
   - Test color contrast ratios
   - Check touch target sizes
   - Verify text resizing support
   - Review focus indicators

4. **Interaction patterns**
   - Read `references/foundations.md` → Interaction section
   - Verify state layers are present
   - Check gesture support for mobile
   - Validate selection patterns

### Workflow 3: Generating Design Tokens

When creating design tokens for a new theme:

1. **Color tokens**
   - Read `references/styles.md` → Color section
   - Choose color scheme type (dynamic or static)
   - Define source color(s)
   - Generate tonal palette (13 tones per key color)
   - Map color roles (primary, secondary, tertiary, surface, error)
   - Create light and dark theme variants

2. **Typography tokens**
   - Read `references/styles.md` → Typography section
   - Select font family
   - Define type scale (display, headline, title, body, label × small/medium/large)
   - Set letter spacing and line height

3. **Shape tokens**
   - Read `references/styles.md` → Shape section
   - Define corner radius scale (none, extra-small, small, medium, large, extra-large)
   - Map shapes to component categories

4. **Motion tokens**
   - Read `references/styles.md` → Motion section
   - Define duration values (short, medium, long)
   - Set easing curves (emphasized, standard)
   - For M3 Expressive, read `references/m3-expressive.md` → Expressive Motion

### Workflow 4: Implementing M3 Expressive

When adding expressive elements to enhance engagement:

1. **Identify key moments**
   - Onboarding flows
   - Primary user actions (FAB, main CTAs)
   - Screen transitions
   - Success/completion states

2. **Apply expressive tactics**
   - Read `references/m3-expressive.md` → Design Tactics
   - Use emphasized easing for important transitions
   - Extend animation durations (400-700ms)
   - Add exaggerated scale changes
   - Implement layered/staggered animations
   - Consider shape morphing

3. **Balance with usability**
   - Follow 80/20 rule (most interactions remain standard)
   - Respect `prefers-reduced-motion`
   - Avoid excessive motion in productivity contexts
   - Test on lower-end devices

## Quick Reference

### When to Read Each Reference

| Your Question                                  | Read This                                                  |
| ---------------------------------------------- | ---------------------------------------------------------- |
| "What components should I use for navigation?" | `references/components.md` → Navigation Components         |
| "How do I create a color scheme?"              | `references/styles.md` → Color                             |
| "What are the responsive breakpoints?"         | `references/foundations.md` → Layout → Window Size Classes |
| "How do I make my design more engaging?"       | `references/m3-expressive.md`                              |
| "What's the correct button hierarchy?"         | `references/components.md` → Action Components → Buttons   |
| "How do I ensure accessibility?"               | `references/foundations.md` → Accessibility                |
| "What motion timing should I use?"             | `references/styles.md` → Motion                            |
| "How do I implement shape morphing?"           | `references/m3-expressive.md` → Shape and Form             |

### Component Quick Selector

**Actions:**
- Primary screen action → FAB or Filled Button
- Secondary action → Tonal/Outlined Button
- Tertiary action → Text Button
- Compact action → Icon Button
- Toggle options (2-5) → Segmented Button

**Input:**
- Single choice → Radio Button
- Multiple choices → Checkbox
- On/Off toggle → Switch
- Text input → Text Field
- Date/time → Date/Time Picker
- Range value → Slider
- Tags → Input Chips

**Navigation:**
- Compact screens (<600dp) → Navigation Bar
- Medium screens (600-840dp) → Navigation Rail
- Large screens (>840dp) → Navigation Drawer
- Secondary nav → Tabs or Top App Bar

**Communication:**
- Important decision → Dialog
- Quick feedback → Snackbar
- Notification count → Badge
- Loading status → Progress Indicator
- Contextual help → Tooltip
- Action list → Menu

### Design Token Defaults

**Color (Light Theme):**
- Primary: tone 40
- On-primary: tone 100 (white)
- Primary container: tone 90
- Surface: tone 98

**Typography:**
- Display Large: 57sp
- Headline Large: 32sp
- Title Large: 22sp
- Body Large: 16sp
- Label Large: 14sp

**Shape:**
- Extra Small: 4dp (chips, checkboxes)
- Small: 8dp (small buttons)
- Medium: 12dp (cards, standard buttons)
- Large: 16dp (FAB)
- Extra Large: 28dp (dialogs, sheets)

**Elevation:**
- Level 0: 0dp (standard surface)
- Level 1: 1dp (cards)
- Level 2: 3dp (search bars)
- Level 3: 6dp (FAB)
- Level 5: 12dp (modals, dialogs)

## Best Practices

### General Principles

1. **Consistency**: Use design tokens consistently across the product
2. **Hierarchy**: Establish clear visual hierarchy through size, color, and spacing
3. **Accessibility**: Always meet WCAG 2.1 Level AA standards (minimum)
4. **Responsiveness**: Design for all window size classes
5. **Platform conventions**: Respect platform-specific patterns when appropriate

### Common Mistakes to Avoid

- **Too many filled buttons**: Use only one filled button per screen for primary action
- **Ignoring window size classes**: Design must adapt to different screen sizes
- **Poor color contrast**: Always validate contrast ratios
- **Inconsistent spacing**: Use 4dp grid system throughout
- **Overusing M3 Expressive**: Keep 80% standard, 20% expressive
- **Small touch targets**: Minimum 48×48dp for all interactive elements
- **Unclear component states**: All components must show hover, focus, pressed states

### Platform-Specific Notes

**Flutter:**
- Use Material 3 theme in `ThemeData(useMaterial3: true)`
- Access design tokens via `Theme.of(context)`
- Official documentation: https://m3.material.io/develop/flutter

**Android (Jetpack Compose):**
- Use Material3 package
- MaterialTheme provides M3 components and tokens
- Official documentation: https://m3.material.io/develop/android/jetpack-compose

**Web:**
- Use Material Web Components library
- CSS custom properties for design tokens
- Official documentation: https://m3.material.io/develop/web

**Platform-agnostic:**
- Export design tokens from Material Theme Builder
- Apply M3 principles manually to any framework

## Tools and Resources

### Material Theme Builder
Web-based tool for creating M3 color schemes and design tokens:
- Generate color schemes from source colors
- Create light and dark themes
- Export tokens for various platforms
- URL: https://material-foundation.github.io/material-theme-builder/

### Material Symbols
Variable icon font with 2,500+ icons:
- Styles: Outlined, Filled, Rounded, Sharp
- Variable features: weight, grade, optical size, fill
- URL: https://fonts.google.com/icons

### Official Documentation
- Material Design 3: https://m3.material.io/
- Get Started: https://m3.material.io/get-started
- Blog (updates and announcements): https://m3.material.io/blog

## Summary

This skill enables comprehensive Material Design 3 implementation:

1. **Read references as needed**: Don't try to memorize everything—reference files exist to be consulted during work
2. **Follow workflows**: Use structured workflows for common tasks (designing, reviewing, generating tokens)
3. **Start with foundations**: Layout, accessibility, and interaction patterns form the base
4. **Build with components**: Use the 38 documented M3 components appropriately
5. **Apply styles consistently**: Color, typography, shape, elevation, icons, motion
6. **Enhance with M3 Expressive**: Add engaging, emotionally resonant elements where appropriate
7. **Validate accessibility**: Always check contrast, touch targets, and keyboard navigation

Material Design 3 is a complete design system—this skill helps you apply it effectively across all contexts.
