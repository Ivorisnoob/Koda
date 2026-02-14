# Material 3 Expressive Theming

Material 3 Expressive introduces new theming capabilities to allow for more personalized and vibrant app designs.

## Applying Expressive Motion

The core of the expressive update is the `MotionScheme`. You can apply it to your theme using the `MaterialTheme` composable.

```kotlin
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable

@Composable
fun MyApp() {
    MaterialTheme(
        // Apply the Expressive Motion Scheme
        motionScheme = MotionScheme.expressive()
    ) {
        // App Content
    }
}
```

### MaterialExpressiveTheme

In some versions (specifically targeted for `1.5.0-alpha`), a convenience wrapper `MaterialExpressiveTheme` might be available. This wrapper sets up the expressive defaults for you.

```kotlin
// Check availability in your specific library version
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MyExpressiveApp() {
    MaterialExpressiveTheme {
        // Content
    }
}
```

If `MaterialExpressiveTheme` is not available, use `MaterialTheme(motionScheme = MotionScheme.expressive())`.

## Expressive Shapes

Material 3 Expressive encourages the use of more varied shapes. While you can still use `Shapes`, the new design language often employs "Medium", "Large", and "Extra Large" shapes that are more rounded or have distinct geometry.

## Expressive Color

While the foundation remains the Material 3 Color System (Dynamic Color), Expressive designs often use more vibrant or specific color roles to enhance emotion.
