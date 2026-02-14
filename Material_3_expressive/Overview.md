# Material 3 Expressive Design Overview

Material 3 Expressive is an evolution of the Material Design system that focuses on creating user experiences that are not just functional, but **visceral, human, and distinct**.

## Philosophy

While standard Material Design focuses on utility and predictability, Expressive Design aims to bridge the gap between software and the natural world. It treats UI elements not as rigid pixels, but as physical objects with mass, elasticity, and personality.

### Key Pillars

1.  **Shape Morphing:** Objects shouldn't just "snap" between states. A square button becoming a round circle should physically *morph*, smoothing its corners dynamically. This connects states seamlessly.
2.  **Physics-Based Motion:** Animations are driven by springs, not timelines. This allows for interruption, "catch-up" behavior, and natural oscillation (bounce) that mimics real-world physics.
3.  **Bold Typography & Hierarchy:** Expressive layouts often feature larger, bolder type (Hero styles) to guide attention immediately.
4.  **Flexible Layouts:** Components like `FloatingToolbar` break the grid, allowing controls to exist closer to the user's thumb or in context with the content, rather than docked to rigid screen edges.

## Why use it?

*   **Engagement:** The fluid animations (morphing, bouncing) create a "delight" factor that keeps users engaged.
*   **Feedback:** Physics-based responses provide immediate, visceral confirmation of user actions (e.g., a button squishing when pressed).
*   **Differentiation:** It allows an app to feel "native" and premium compared to standard, static interfaces.

## Artifacts & Versioning

To access these features in Jetpack Compose, you typically need the following (or newer):

*   **Compose Material 3:** `1.3.0` (stable) provides some, but `1.4.0+` or `1.5.0-alpha` series contains the full `ExperimentalMaterial3ExpressiveApi` surface.
*   **Graphics Shapes:** `androidx.graphics:graphics-shapes:1.0.1` (essential for polygon morphing).

## Usage

Most expressive features are opt-in via:
1.  **Theming:** Applying `MotionScheme.expressive()` to your `MaterialTheme`.
2.  **Components:** Using specific expressive components (`HorizontalFloatingToolbar`, `SplitButton`) or their expressive variants (`WavyProgressIndicator`).
3.  **Annotations:** Many APIs are marked with `@ExperimentalMaterial3ExpressiveApi`.

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MyApp() {
    MaterialTheme(motionScheme = MotionScheme.expressive()) {
        // App Content
    }
}
```
