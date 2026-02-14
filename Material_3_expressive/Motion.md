# Material 3 Expressive Motion

Motion is not just an embellishment in Material 3 Expressive; it is a core semantic layer. It conveys state, hierarchy, and surface texture. The Expressive system moves away from rigid durations (ms) towards physics-based modeling (springs).

## MotionScheme

The central API for this is `MotionScheme`. A `MotionScheme` provides a set of `FiniteAnimationSpec`s (specifically springs) for various UI transition types.

### Standard vs. Expressive

Material 3 defines two distinct schemes:

1.  **Standard (`MotionScheme.standard()`)**:
    *   **Feel:** Reliable, linear, utilitarian.
    *   **Physics:** Higher damping ratios (less bounce), standard stiffness.
    *   **Use Case:** Productivity apps, data-heavy interfaces, recurring tasks.

2.  **Expressive (`MotionScheme.expressive()`)**:
    *   **Feel:** Visceral, fluid, playful, organic.
    *   **Physics:** Lower damping ratios (more "overshoot" or bounce), tuned stiffness to feel "lighter".
    *   **Use Case:** Consumer apps, media, hero moments, prominent interactions.

### Implementing MotionScheme

You apply a motion scheme at the Theme level. All standard Material 3 components (Buttons, Dialogs, etc.) read from this scheme to determine their internal animation specs.

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        // Switch to the expressive scheme
        motionScheme = MotionScheme.expressive(),
        colorScheme = /* ... */,
        typography = /* ... */,
        content = content
    )
}
```

### Using MotionSpecs Manually

If you are building custom components, you should consume the values from the `MotionScheme` to ensure consistency with the rest of the app.

The `MotionScheme` interface exposes explicit specs:

| Spec Function | Description | Typical Use |
| :--- | :--- | :--- |
| `fastSpatialSpec()` | Fast movement, changes bounds/shape. | Small items appearing, checkboxes, toggles. |
| `slowSpatialSpec()` | Slower movement, changes bounds/shape. | Large container transforms, shared element transitions. |
| `defaultSpatialSpec()` | Baseline spatial movement. | Standard navigation, list item addition. |
| `fastEffectsSpec()` | Fast non-spatial changes (color/alpha). | Ripples, highlights, micro-interactions. |
| `slowEffectsSpec()` | Slow non-spatial changes. | Crossfades, mood changes. |

**Example Usage:**

```kotlin
val animatedColor by animateColorAsState(
    targetValue = if (selected) Color.Red else Color.Gray,
    // Use the theme's expressive spec for consistency
    animationSpec = MaterialTheme.motionScheme.fastEffectsSpec()
)

val animatedSize by animateDpAsState(
    targetValue = if (expanded) 200.dp else 50.dp,
    // Spatial spec for layout changes
    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()
)
```

## Physics & "Springs"

The "Expressive" feel is mathematically defined by Spring animations. Unlike Tweens (duration + easing curve), Springs are defined by:
*   **Stiffness:** How strong the spring is (how fast it tries to return to rest).
*   **Damping Ratio:** How much friction exists (how much it oscillates/bounces).

**Expressive Characteristics:**
*   **Damping:** Often `< 1.0` (Underdamped), creating a visible bounce or overshoot.
*   **Stiffness:** Varied. High stiffness for small/fast items, lower stiffness for large surfaces to give them "weight".

By using `MotionScheme.expressive()`, you don't need to manually tune these values (`Spring.DampingRatioMediumBouncy`, etc.); the system provides the tuned constants for you via the `...Spec()` functions.
