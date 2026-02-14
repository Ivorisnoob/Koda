# Material 3 Expressive Motion

Material 3 Expressive replaces the standard easing/duration-based motion system with a physics-based `MotionScheme`. This makes interactions feel more natural and responsive.

## MotionScheme

The `MotionScheme` class defines a set of motion parameters (spatial, effects, etc.) that components use.

### Using Expressive Motion

To enable expressive motion globally or for a subtree, use the `MaterialExpressiveTheme` (or `MaterialTheme` with the `motionScheme` parameter).

```kotlin
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme

@Composable
fun App() {
    MaterialTheme(
        motionScheme = MotionScheme.expressive()
    ) {
        // All M3 components here will use expressive motion
    }
}
```

### Standard Motion

If you want to revert to the standard (less bouncy/expressive) motion in specific areas:

```kotlin
MaterialTheme(
    motionScheme = MotionScheme.standard()
) {
    // Content using standard motion
}
```

## How it works

Components like `Button`, `Card`, `Dialog`, and others query the `LocalMotionScheme` to determine how to animate state changes (e.g., press states, appearance animations).

*   **Spatial Motion**: Governs how elements move in space (e.g., a sheet expanding). Expressive spatial motion often uses spring physics for a "bouncy" feel.
*   **Effects**: Governs visual effects like ripples or shape morphing.

By setting `MotionScheme.expressive()`, you opt-in to these livelier defaults without needing to manually configure animation specs for every component.
