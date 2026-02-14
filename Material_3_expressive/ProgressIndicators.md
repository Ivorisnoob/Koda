# Material 3 Expressive Progress Indicators

Material 3 Expressive introduces "Wavy" progress indicators, adding a playful and distinct visual style to loading states.

## LinearWavyProgressIndicator

A linear progress indicator with a wavy visual path.

### Determinate Usage (Lambda API)

Newer versions of Material 3 introduce a lambda-based API for `progress` to improve performance by avoiding recomposition of the indicator when the value changes.

```kotlin
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.runtime.Composable

@Composable
fun WavyLinearExample(currentProgress: () -> Float) {
    // Indeterminate
    LinearWavyProgressIndicator()

    // Determinate (Lambda overload for performance)
    LinearWavyProgressIndicator(progress = currentProgress)
}
```

## CircularWavyProgressIndicator

A circular progress indicator with a wavy path.

```kotlin
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.runtime.Composable

@Composable
fun WavyCircularExample(currentProgress: () -> Float) {
    // Indeterminate
    CircularWavyProgressIndicator()

    // Determinate (Lambda overload)
    CircularWavyProgressIndicator(progress = currentProgress)
}
```

## Customization

You can often customize the wave parameters (amplitude, speed) in the indeterminate variations, as mentioned in the release notes.

```kotlin
// Example with custom wave parameters if API permits (check specific version)
LinearWavyProgressIndicator(
    // amplitude = ...
    // waveSpeed = ...
)
```
