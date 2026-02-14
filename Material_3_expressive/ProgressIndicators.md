# Material 3 Expressive Progress Indicators

Expressive design reimagines loading states not just as waiting times, but as active, living elements of the UI. The primary introduction here is the **Wavy** style, which feels more organic and fluid than the mechanical circular spinner.

## Wavy Progress Indicators

The `WavyProgressIndicator` (Linear and Circular) replaces the straight lines with sine-wave paths that animate.

### Wavy Linear Progress Indicator

Typically used for indeterminate loading states at the top of surfaces or cards.

```kotlin
@ExperimentalMaterial3ExpressiveApi
@Composable
fun WavyLinearSample() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Indeterminate
        WavyLinearProgressIndicator(
            modifier = Modifier.fillMaxWidth(),
        )

        // Determinate (with progress)
        WavyLinearProgressIndicator(
            progress = { 0.7f },
            modifier = Modifier.fillMaxWidth()
        )
    }
}
```

### Wavy Circular Progress Indicator

Replaces the standard arc spinner.

```kotlin
@ExperimentalMaterial3ExpressiveApi
@Composable
fun WavyCircularSample() {
    WavyCircularProgressIndicator(
        modifier = Modifier.size(48.dp),
        // You can customize the amplitude and frequency of the wave
        amplitude = 1.0f, // Default is usually around 1.0
        wavelength = 20.dp
    )
}
```

## LoadingIndicator (Morphing)

There is also a concept of a `LoadingIndicator` that utilizes **Shape Morphing**. This indicator sits between two states or morphs its shape (e.g., from a star to a polygon) repeatedly to indicate activity.

This is often implemented using `androidx.graphics.shapes` directly (as seen in the [Shapes](./Shapes.md) documentation example) or via new dedicated composables if available in the specific library version.

### Example: Morphing Loading State
A common expressive pattern is a small shape in the center of the screen that breathes or morphs.

```kotlin
// Conceptual example using the Shapes API
@Composable
fun MorphingLoader() {
    val infiniteTransition = rememberInfiniteTransition("Loader")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    // Draw a shape that morphs from Triangle to Circle
    MorphingIcon(progress = progress, ...)
}
```

## Customization

*   **Amplitude:** Controls the height of the wave peaks.
*   **Wavelength:** Controls the distance between peaks.
*   **Speed:** In indeterminate modes, how fast the wave travels.

These indicators respect the `MotionScheme` for their enter/exit transitions if they are shown/hidden via `AnimatedVisibility`.
