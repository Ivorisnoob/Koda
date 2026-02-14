# Material 3 Expressive Shapes & Morphing

Material 3 Expressive introduces a powerful new capability for shapes: **Polygon Morphing**. This is enabled by the `androidx.graphics:graphics-shapes` library, which allows for the creation of complex rounded polygons and smooth, physics-based morphing transitions between them.

## Overview

Unlike standard Compose shapes which are typically static (RoundedCornerShape), the Graphics Shapes library treats shapes as mathematical polygons defined by vertices and rounding parameters. This mathematical representation allows the system to interpolate between two different shapes (e.g., a square and a star) by calculating intermediate polygon states, resulting in a fluid "morph" animation.

This technology is fundamental to the "Expressive" feel, used in components like:
- **Buttons:** Morphing from square to round on press.
- **Loading Indicators:** Morphing shapes to indicate activity.
- **Hero Transitions:** Smoothly transforming surface shapes during navigation.

## Setup

To use these features, you must add the graphics-shapes dependency to your `build.gradle` file:

```kotlin
dependencies {
    implementation("androidx.graphics:graphics-shapes:1.0.1")
    implementation("androidx.compose.animation:animation-graphics:1.7.0") // Optional helper
}
```

## Core Concepts

### 1. RoundedPolygon
The foundational class is `RoundedPolygon`. It defines a shape by its number of vertices, radius, and center. Crucially, it supports `CornerRounding` which can be applied to all vertices or specific ones.

**Basic Creation:**
```kotlin
val hexagon = RoundedPolygon(
    numVertices = 6,
    radius = 100f,
    centerX = 100f,
    centerY = 100f
)
```

**Advanced Rounding:**
`CornerRounding` takes a `radius` and a `smoothing` parameter.
- **Radius:** Standard circular rounding radius.
- **Smoothing (0.0 - 1.0):** Determines how "smoothly" the curve transitions from the straight edge to the rounded corner. A value of `0` is a perfect circular arc. Higher values (like `1.0`) create a hyper-elliptical or "squircle"-like continuous curve, which feels more organic and "expressive".

```kotlin
val softStar = RoundedPolygon.star(
    numVerticesPerRadius = 5,
    innerRadius = 50f,
    outerRadius = 100f,
    rounding = CornerRounding(radius = 20f, smoothing = 0.5f)
)
```

### 2. Morph
The `Morph` class represents the interpolation state between two `RoundedPolygon` instances. It handles the complex geometry mapping required to transform one vertex set into another.

```kotlin
val square = RoundedPolygon.rectangle(width = 100f, height = 100f)
val circle = RoundedPolygon.circle(radius = 50f)

// Create a Morph object
val morph = Morph(start = square, end = circle)

// Get the path at 50% morph
val halfWayPath = morph.toPath(progress = 0.5f).asComposePath()
```

## Implementing Shape Morphing in Compose

There are two main ways to render these shapes: using a custom `Shape` implementation for clipping/borders, or drawing directly to a `Canvas`.

### Method 1: Drawing on Canvas (Performance Optimized)

This is ideal for animated backgrounds or standalone visual elements.

```kotlin
@Composable
fun MorphingIcon(
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    // 1. Define Shapes
    val shapeA = remember { RoundedPolygon(numVertices = 6, rounding = CornerRounding(0.2f)) }
    val shapeB = remember { RoundedPolygon.star(numVertices = 6, rounding = CornerRounding(0.1f)) }

    // 2. Create Morph
    val morph = remember(shapeA, shapeB) { Morph(shapeA, shapeB) }

    // 3. Animate Progress
    // Use an Expressive Motion spring for the transition
    val progress by animateFloatAsState(
        targetValue = if (isActive) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.4f, // Lower damping for "bouncy" expressive feel
            stiffness = Spring.StiffnessMedium
        ),
        label = "MorphProgress"
    )

    // 4. Draw
    Canvas(modifier = modifier.fillMaxSize()) {
        // Calculate the matrix to scale the shape to the view size
        // RoundedPolygon default radius is usually 1f, so we scale to fit.
        val matrix = Matrix()
        val bounds = morph.getBounds() // Helper or calculateBounds()

        // Scale to fit the canvas
        val scaleX = size.width / bounds.width
        val scaleY = size.height / bounds.height
        matrix.scale(scaleX, scaleY)
        matrix.translate(-bounds.left, -bounds.top) // Center it

        // Get path for current progress
        val path = morph.toPath(progress).asComposePath()
        path.transform(matrix)

        drawPath(path, color = MaterialTheme.colorScheme.primary)
    }
}
```

### Method 2: Custom Shape Implementation (For Clipping)

To use the morphing shape as a `Modifier.clip()` or inside a Surface, you need to implement the `Shape` interface.

```kotlin
class MorphShape(
    private val morph: Morph,
    private val progress: Float
) : Shape {
    private val matrix = Matrix()

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = morph.toPath(progress).asComposePath()

        // Transform the path to fit the layout size
        // Assuming morph is normalized approx -1..1 or 0..1 range
        matrix.reset()
        matrix.scale(size.width / 2f, size.height / 2f)
        matrix.translate(1f, 1f) // Center
        path.transform(matrix)

        return Outline.Generic(path)
    }
}
```

**Usage:**
```kotlin
Box(
    modifier = Modifier
        .size(100.dp)
        .clip(MorphShape(morph, progress)) // Applies the animated clip
        .background(Color.Red)
)
```

## Expressive Design Usage

In Material 3 Expressive, "Shape Morphing" is often triggered by interaction states:
- **Press:** A "squircle" might morph into a slightly more rounded form.
- **Hover:** Shapes might expand or subtly change vertex counts.
- **Selection:** A `SplitButton` might morph its divider or container shape.

The physics of the morph (controlled by `animationSpec` in Compose) should align with the **Expressive Motion** guidelines (typically spring-based with lower damping ratios) to feel organic and responsive.
