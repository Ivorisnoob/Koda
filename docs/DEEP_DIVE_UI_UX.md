# Deep Dive: UI, UX & Design System

This document details the implementation of the app's visual identity, focusing on Material 3 Expressive and dynamic theming logic.

## Design Philosophy: Material 3 Expressive

The app is built on the latest iterations of Material 3, emphasizing **expressive motion** and **organic shapes**.

### 1. Shape System
The app leverages the **Material 3 Expressive** shape system, which moves beyond static radii to dynamic, organic forms.

-   **Composition Shapes**: Defined in `Theme.kt` via `ExpressiveShapes`.
-   **Organic Polygons**: Uses the `MaterialShapes` object for non-geometric forms like `Cookie`, `Sunny`, and `SoftBurst`.
    -   **Conversion**: Polygons are converted to usable Compose shapes via the `.toShape()` extension.
    -   **Morphing**: Integrated into `LoadingIndicator` cycles and interactive button states.
-   **Tokens**:
    -   `extraLarge`: 36.dp (Main Player, Hero sections).
    -   `large`: 28.dp (Floating toolbars, Bottom bars).
    -   `medium`: 20.dp (Search result segments, Playlists).

### 2. Motion & Transitions
The app utilizes a custom **Motion Scheme** that favors organic, spring-based easing.
-   **Expandable Player**: Implemented using a custom bottom sheet-like logic in `ExpandablePlayer.kt`. It utilizes a `Swipeable` (or similar gesture) state to transition between the collapsed Mini-Player and the Full-screen Player.
-   **Navigation**: Uses the `MaterialExpressiveTheme` motion scheme to provide fluid entries and exits between screens.

## Dynamic Theming Engine

The application's color palette is not static; it responds to the content being played.

### 1. Palette Extraction
When a new song starts:
1.  The album artwork is loaded.
2.  The `ThemeViewModel` (or a helper) extracts the dominant colors using the **Palette API**.
3.  A custom `ColorScheme` is generated based on the primary color extracted.

### 2. Chromatic Mist (Ambient Background)
The Player screen features a high-performance "Chromatic Mist" background.
-   **Implementation**: `ChromaticMistBackground.kt`.
-   **Logic**: Uses a series of overlapping, blurred `Canvas` circles (or shaders) that slowly drift and rotate.
-   **Synchronization**: The colors of these "mists" are derived from the current album art's secondary and tertiary palette colors.

## Screen Architectures

### 1. Home Screen
-   **Structure**: Uses a `LazyColumn` with nested `LazyRow` components for discovery sections.
-   **Performance**: Each horizontal row uses `rememberLazyListState` to preserve scroll positions across recompositions.

### 2. Player Screen
-   **Synced Lyrics**: Implementation in `SyncedLyricsView.kt`. It uses a scroll-driven animation system that tracks the `player.currentPosition`.
-   **Gesture Layer**: `GesturePlayerContent.kt` handles swipe-to-skip and double-tap gestures overlaying the album art.

### 3. Settings & Stats
-   **Visualizations**: `StatsScreen.kt` uses custom drawing or simple bar charts to represent listening habits stored in `StatsRepository`.
-   **Persistence**: All user preferences (Video Mode, Cache Size, Crossfade) are stored in `ThemePreferences` and exposed via `StateFlow` to ensure immediate UI updates upon change.
