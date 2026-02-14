# Material 3 Expressive Theming

Theming is the entry point for Expressive Design. It ensures that the entire application adheres to the physics-based motion and updated color/shape roles defined by the system.

## MaterialTheme Setup

The core change in Expressive Design theming is the introduction of the `motionScheme` parameter in `MaterialTheme`.

```kotlin
// In your Theme.kt
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (useDarkTheme) DarkColorScheme else LightColorScheme
    val shapes = Shapes // Your shapes

    MaterialTheme(
        colorScheme = colors,
        shapes = shapes,
        typography = Typography,
        // ENABLE EXPRESSIVE MOTION HERE
        motionScheme = MotionScheme.expressive(),
        content = content
    )
}
```

## Motion Schemes

### `MotionScheme.expressive()`
This is the "magic switch". By passing this to `MaterialTheme`, all downstream Material 3 components that support animations will attempt to use spring-based, bouncy, or fluid animations instead of standard linear transitions.

*   **Button Presses:** Will feel tactile (squish/bounce).
*   **Dialogs:** Will enter with a slight overshoot/settle effect.
*   **Navigation:** (If configured) will use fluid morphs.

### `MotionScheme.standard()`
The default if nothing is provided. Uses standard Easing curves (Cubic Bezier) and fixed durations.

## Shape Theming

While `motionScheme` handles the *movement*, the *shapes* themselves can also be tuned for expression.

Material 3 Expressive encourages "organic" shapes. While standard `RoundedCornerShape` is supported, you might consider using the **Graphics Shapes** library to create `RoundedPolygon` shapes (like Squircles or smoothed stars) and applying them to your theme's `Shapes` object (though `Shapes` usually expects standard Compose `Shape` objects, you can create adapters as shown in the [Shapes documentation](./Shapes.md)).

## Color Configurations

Expressive buttons and controls often use "Configurations" for color rather than fixed slots.
For example, instead of just `ButtonDefaults.buttonColors()`, you might see references to `ButtonDefaults.filledTonalButtonColors()` or distinct tonal palettes that have higher contrast in their "Expressive" variants.

Ensure your `ColorScheme` has rich definitions for `tertiary` and `tertiaryContainer`, as expressive designs often leverage these "accent" colors more heavily than standard utility apps.
