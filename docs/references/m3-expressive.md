# Material 3 Expressive Reference

Material 3 Expressive is the 2024-2025 evolution of Material Design, focusing on creating more engaging, emotionally resonant, and brand-expressive interfaces.

## Core Principles

-   **Engaging**: Drawing and holding user attention through motion and shape.
-   **Emotionally Resonant**: Creating visceral connections through visual delight.
-   **User-Friendly**: Maintaining clarity while pushing aesthetic boundaries.
-   **Brand Expression**: Leaving room for unique identity within the system.

## Key Design Tactics

### 1. Emphasized Easing
Uses dramatic curves instead of linear or standard transitions.
-   **Decelerate**: `cubic-bezier(0.05, 0.7, 0.1, 1.0)`
-   **Accelerate**: `cubic-bezier(0.3, 0.0, 0.8, 0.15)`

### 2. Extended Durations
Animations are slightly slower to ensure the user perceives the "story" of the transition.
-   Standard: 200-300ms
-   **Expressive**: 400-700ms

### 3. Exaggerated Scaling
Visual elements "breathe" more during interaction.
-   Standard: 1.0 -> 1.05
-   **Expressive**: 1.0 -> 1.15 (or bounce 1.0 -> 0.9 -> 1.1)

### 4. Shape Morphing
The core of the Expressive identity. Components morph between different polygon states (e.g., Circle -> Squircle -> Pill).

## Motion Tokens

| Token | Duration | Usage |
| :--- | :--- | :--- |
| `motion.duration.short` | 150ms | Simple feedback (ripple, tap) |
| `motion.duration.medium`| 400ms | Intermediate transitions (chips, cards) |
| `motion.duration.long`  | 600ms | Full-screen transitions, Bottom sheets |

## Shape and Form

M3 Expressive utilizes the **`androidx.graphics.shapes`** library to handle complex path interpolation.
-   **`RoundedPolygon`**: The base class for all expressive shapes.
-   **`MaterialShapes` Object**: A repository of predefined organic polygons (`Sunny`, `Clover`, `Burst`, etc.).
-   **`toShape()`**: The bridging function between `RoundedPolygon` and Compose `Shape`.

## Best Practices
-   **80/20 Rule**: Use Standard M3 for 80% of the app (efficiency) and Expressive M3 for 20% (hero moments, transitions) to prevent over-stimulation.
-   **Respect Reduced Motion**: Always throttle or disable expressive animations if the user has enabled accessibility motion restrictions.
