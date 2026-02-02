# Material 3 Components

Material 3 provides 38 documented components. Each component includes sub-pages for overview, guidelines, specifications, and accessibility.

## Table of Contents

1. [Action Components](#action-components)
2. [Selection and Input Components](#selection-and-input-components)
3. [Navigation Components](#navigation-components)
4. [Containment and Layout Components](#containment-and-layout-components)
5. [Communication Components](#communication-components)

---

## Action Components

Components that allow users to perform actions.

### Buttons

#### Common Buttons
Standard buttons for primary and secondary actions.

**Variants:**
- **Filled**: Highest emphasis, for primary actions.
- **Filled Tonal**: Medium emphasis, for secondary actions.
- **Outlined**: Outline-only, medium emphasis.
- **Elevated**: With shadow, used when emphasis is needed but less than Filled.
- **Text**: Lowest emphasis, for auxiliary actions.

**Usage Guidelines:**
- Recommended to have only one Filled button per screen.
- Maintain a clear hierarchy of buttons (Filled > Tonal > Outlined > Text).
- Minimum touch target: 48×48dp.
- Labels should start with a verb (e.g., "Save", "Send", "Delete").

URL: https://m3.material.io/components/buttons/overview

#### Icon Buttons
Compact buttons for auxiliary actions.

**Variants:**
- Standard
- Filled
- Filled Tonal
- Outlined

**Usage:**
- For repeating actions (Favorite, Share, Delete).
- Limited space.
- When the meaning is clear from the icon alone.

URL: https://m3.material.io/components/icon-buttons/overview

#### Floating Action Button (FAB)
A floating button for the screen's primary action.

**Types:**
- **FAB**: Standard FAB.
- **Small FAB**: Smaller FAB.
- **Large FAB**: Larger FAB.
- **Extended FAB**: FAB with a text label.

**Guidelines:**
- Recommend one FAB per screen.
- Use only for the most important action.
- Placement: Typically bottom-right.
- Consider behavior during scrolling (hide or shrink).

URL: https://m3.material.io/components/floating-action-button/overview

#### Segmented Buttons
A group of related options for single or multiple selection.

**Usage:**
- Switching views (List/Grid).
- Filtering (Category selection).
- Configuration options.

**Guidelines:**
- Recommended 2-5 options.
- Labels should be concise (1-2 words).
- Icon+text or text-only.

URL: https://m3.material.io/components/segmented-buttons/overview

---

## Selection and Input Components

Components that allow users to make selections or enter input.

### Checkbox
Select multiple items from a list.

**States:**
- Unchecked
- Checked
- Indeterminate (partial selection)

**Usage:**
- Multiple selection.
- On/Off settings (though a Switch might be better in some cases).
- Selecting list items.

URL: https://m3.material.io/components/checkbox/guidelines

### Radio Button
Select a single option from a set.

**Usage:**
- Mutually exclusive options (only one selection possible).
- When all options need to be visible.
- Recommended 2-7 options.

**Guidelines:**
- Provide a default choice.
- Recommended to arrange options vertically.
- Labels should be clickable.

URL: https://m3.material.io/components/radio-button/overview

### Switch
Binary On/Off toggle.

**Usage:**
- Settings where the effect is applied immediately.
- Enabling/disabling a single item.
- Toggling individual items within a list.

**vs Checkbox:**
- Switch: Immediate effect, state change.
- Checkbox: Requires saving, multiple selection.

URL: https://m3.material.io/components/switch/guidelines

### Text Fields
Form fields for text input.

**Types:**
- **Filled**: Default, with a filled background.
- **Outlined**: Outline-only, recommended for forms.

**Elements:**
- Label: Description of input.
- Input text: User-entered text.
- Helper text: Auxiliary instructions.
- Error text: Error messages.
- Leading/Trailing icons: Associated icons.

**Guidelines:**
- Labels should be concise.
- Use placeholders as auxiliary examples.
- Errors should be specific (e.g., "Please enter a valid email address" instead of "Invalid input").

URL: https://m3.material.io/components/text-fields/overview

### Chips
Compact informational elements.

**Types:**
- **Assist**: Action or help suggestions.
- **Filter**: Content filtering.
- **Input**: User input (tags, contacts).
- **Suggestion**: Dynamic suggestions.

**Usage:**
- Displaying tags or attributes.
- Filtering options.
- Displaying selected items.

URL: https://m3.material.io/components/chips/guidelines

### Sliders
Select a value from a range.

**Types:**
- Continuous: For a continuous range of values.
- Discrete: For discrete values (with steps).

**Usage:**
- Volume, brightness adjustment.
- Price range selection.
- Numerical settings.

URL: https://m3.material.io/components/sliders/specs

### Date Pickers / Time Pickers
Selecting dates and times.

**Date Picker Modes:**
- Modal: Dialog format.
- Docked: Inline display.

**Time Picker Types:**
- Dial: Dial format.
- Input: Text input format.

URL: https://m3.material.io/components/date-pickers

---

## Navigation Components

Components that provide navigation within the app.

### Navigation Bar
Bottom navigation for mobile devices.

**Guidelines:**
- 3-5 primary destinations.
- Icon + Label (avoid icon-only).
- Always visible (fixed during scrolling).
- For Compact window size class.

URL: https://m3.material.io/components/navigation-bar/overview

### Navigation Drawer
Side navigation menus.

**Types:**
- **Standard**: Opens/closes from the screen edge.
- **Modal**: Overlay format.

**Usage:**
- 5 or more destinations.
- Medium/Expanded window size classes.
- Primary sections of the app.

URL: https://m3.material.io/components/navigation-drawer/overview

### Navigation Rail
Vertical navigation for medium-sized screens.

**Usage:**
- Medium window size class (tablet portrait).
- 3-7 destinations.
- Fixed to the left edge of the screen.

URL: https://m3.material.io/components/navigation-rail/overview

### Top App Bar
Title and actions at the top of the screen.

**Types:**
- **Small**: Standard app bar.
- **Medium**: Medium size (shrinks on scroll).
- **Large**: Large size (shrinks on scroll).

**Elements:**
- Navigation icon: Back, menu.
- Title: Screen title.
- Action icons: Primary actions (recommended max 3).

URL: https://m3.material.io/components/app-bars/overview

### Tabs
Organize content into multiple views.

**Types:**
- Primary tabs: Switch between main content areas.
- Secondary tabs: Switch between sub-sections.

**Guidelines:**
- Recommended 2-6 tabs.
- Labels should be concise (1-2 words).
- Support switching via swipe gestures.

URL: https://m3.material.io/components/tabs/guidelines

---

## Containment and Layout Components

Components for organizing and displaying content.

### Cards
Containers that group related information.

**Types:**
- **Elevated**: With shadow.
- **Filled**: With a filled background.
- **Outlined**: Outline-only.

**Usage:**
- Collections of different content types.
- Actionable content.
- Entry points for detailed info.

**Guidelines:**
- Do not overuse (a list might suffice).
- Provide clear actions.
- Maintain information hierarchy.

URL: https://m3.material.io/components/cards/guidelines

### Lists
Vertical indexes of text and images.

**Types:**
- Single-line
- Two-line
- Three-line

**Elements:**
- Leading element: Icon, image, checkbox.
- Primary text: Main text.
- Secondary text: Supporting text.
- Trailing element: Meta info, actions.

**Usage:**
- Collections of homogeneous content.
- Scannable information.
- Entry points to details.

URL: https://m3.material.io/components/lists/overview

### Carousel
A collection of scrollable visual items.

**Types:**
- Hero: Large, focused items.
- Multi-browse: Multiple items displayed at once.
- Uncontained: Full-bleed edges.

**Usage:**
- Image galleries.
- Product showcases.
- Onboarding screens.

URL: https://m3.material.io/components/carousel/overview

### Bottom Sheets / Side Sheets
Surfaces that display supplemental content.

**Types:**
- **Standard**: Persistent, part of the screen.
- **Modal**: Temporary, requires focus.

**Bottom Sheet Usage:**
- Contextual actions.
- Additional options.
- For mobile devices.

**Side Sheet Usage:**
- Detailed information, filters.
- For tablets/desktops.

URL: https://m3.material.io/components/bottom-sheets/overview

---

## Communication Components

Components that communicate feedback or info to the user.

### Dialogs
Urgent prompts that require user action.

**Types:**
- **Basic**: Title, body text, actions.
- **Full-screen**: Full-screen dialogs (mobile).

**Usage:**
- Important decisions (e.g., delete confirmation).
- Required information input.
- Errors or warnings.

**Guidelines:**
- Recommended to use a question for the title.
- Actions should be clear (e.g., "Delete", "Cancel").
- Do not place destructive actions on the right without consideration.

URL: https://m3.material.io/components/dialogs/guidelines

### Snackbar
Brief updates about a process displayed at the bottom of the screen.

**Usage:**
- Confirmation of finished operations ("Message sent").
- Minor error notifications.
- Providing optional actions (e.g., "Undo").

**Guidelines:**
- Display duration: 4-10 seconds.
- Recommend single-line messages.
- Maximum of one action.
- Do not use for critical information (use a Dialog instead).

URL: https://m3.material.io/components/snackbar/overview

### Badges
Notifications and counts on navigation items.

**Types:**
- Numeric: Displaying a count (1-999).
- Dot: Simple dot indicator for something new.

**Usage:**
- Number of unread notifications.
- Indicator for new content.

URL: https://m3.material.io/components/badges/overview

### Progress Indicators
Status of an ongoing process.

**Types:**
- **Circular**: Round, indeterminate or determinate.
- **Linear**: Bar, determinate progress.

**Usage:**
- Circular: Loading, processing.
- Linear: File uploads, downloads.

**Guidelines:**
- Display for processes taking longer than 2 seconds.
- Use determinate progress whenever possible.
- Use indeterminate if the progress duration is unknown.

URL: https://m3.material.io/components/progress-indicators/overview

### Tooltips
Contextual labels and messages.

**Types:**
- Plain: Text-only.
- Rich: Text + icons/images.

**Usage:**
- Explaining icon buttons.
- Full version of truncated text.
- Auxiliary information.

**Guidelines:**
- Concise (single line recommended).
- Do not use for critical information.
- Long press on touch devices.

URL: https://m3.material.io/components/tooltips/guidelines

### Menus
List of choices on temporary surfaces.

**Types:**
- Standard menu.
- Dropdown menu.
- Exposed dropdown menu (displays selection state).

**Usage:**
- Context menus.
- Selection options.
- List of actions.

**Guidelines:**
- Recommended 2-7 items.
- Icons are optional.
- Separate destructive actions.

URL: https://m3.material.io/components/menus/overview

### Search
Search bar and suggestions.

**Elements:**
- Search bar: Input field for search.
- Search view: Full-screen search interface.

**Usage:**
- In-app search.
- Filtering.
- Displaying suggestions.

URL: https://m3.material.io/components/search/overview

---

## Component Selection Guide

### Action Selection

| Need | Component |
|------|-----------|
| Primary screen action | FAB or Filled Button |
| Secondary action | Tonal/Outlined Button |
| Tertiary action | Text Button |
| Compact action | Icon Button |
| Toggle between 2-5 options | Segmented Button |

### Input Selection

| Need | Component |
|------|-----------|
| Single choice from list | Radio Button |
| Multiple choices | Checkbox |
| On/Off toggle | Switch |
| Text input | Text Field |
| Date selection | Date Picker |
| Value from range | Slider |
| Tags or attributes | Input Chips |

### Navigation Selection

| Window Size | Primary Nav | Secondary Nav |
|-------------|-------------|---------------|
| Compact (<600dp) | Navigation Bar | Tabs |
| Medium (600-840dp) | Navigation Rail | Tabs |
| Expanded (>840dp) | Navigation Drawer | Tabs, Top App Bar |

---

## References

- Material Design 3 Components: https://m3.material.io/components/
- All Components List: https://m3.material.io/components/all-buttons
