# Material 3 Expressive Shapes

The Music App utilizes the latest **Material 3 Expressive** shape system, which introduces dynamic shapes, organic polygons, and sophisticated shape-morphing animations.

## The `MaterialShapes` Object

Instead of relying on standard geometric primitives, the app uses the `MaterialShapes` object (provided by `androidx.compose.material3`). This object contains a collection of `RoundedPolygon` definitions that evoke a more "organic" and "alive" feel.

### Available Polygons
Common polygons used in the app include:
- `MaterialShapes.Square`: A soft-edged squircle.
- `MaterialShapes.Pill`: A classic stadium shape.
- `MaterialShapes.Sunny`: A decorative burst shape.
- `MaterialShapes.SoftBurst`: A star-like organic form.
- `MaterialShapes.Cookie9Sided`: A playful scalloped edge.

## Implementation: `toShape()`

To use these polygons with standard Jetpack Compose modifiers (like `.clip()` or `Surface(shape = ...)`), they must be converted to a `Shape` using the `.toShape()` extension function.

```kotlin
// Example: Clipping an image to a Material Expressive Square
val albumShape = MaterialShapes.Square.toShape()

AsyncImage(
    model = imageUrl,
    modifier = Modifier.clip(albumShape)
)
```

## Expressive Loading Indicators

The `LoadingIndicator` component in M3 Expressive can morph between multiple polygons to create a fluid, engaging animation.

```kotlin
LoadingIndicator(
    polygons = listOf(
        MaterialShapes.SoftBurst,
        MaterialShapes.Cookie9Sided,
        MaterialShapes.Pill,
        MaterialShapes.Sunny
    )
)
```
*Used in: `VideoPlayerScreen.kt` during video buffering.*

## Motion-Driven Morphing

The app implements manual shape morphing for interactive elements (like the Play/Pause button) to provide tactile feedback.

```kotlin
// Animating from a circle to a squircle on press
val cornerRadius by animateDpAsState(
    targetValue = if (isPressed) size / 3 else size / 2,
    animationSpec = spring(dampingRatio = 0.5f)
)

Surface(
    shape = RoundedCornerShape(cornerRadius)
) { ... }
```

## Consistent Shape Tokens

App-wide shape tokens are defined in `Theme.kt` within the `ExpressiveShapes` object:

| Token | Radius | Representative Use Case |
| :--- | :--- | :--- |
| `extraSmall` | 8.dp | Mini-player controls |
| `small` | 12.dp | Chip elements |
| `medium` | 20.dp | Search list items, Album cards |
| `large` | 28.dp | Floating toolbars, Bottom sheets |
| `extraLarge`| 36.dp | Main player background, Hero sections |