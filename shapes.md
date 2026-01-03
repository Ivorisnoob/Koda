The "Expressive" update to Material 3 (available in the latest Jetpack Compose alphas) focuses on bolder shapes, "organic" variations, and shape morphing.

The following guide and examples use `androidx.compose.material3:material3:1.4.0-alpha04` (or later), which includes the latest Expressive APIs.

### 1\. Setup & Dependencies

To use these features, you need the latest Material 3 alpha and the **graphics-shapes** library (essential for the new polygon shapes and morphing).

```kotlin
dependencies {
    // Latest Material 3 Alpha (Expressive features)
    implementation("androidx.compose.material3:material3:1.5.0-alpha10")
    
    // Required for Expressive Polygon shapes (Cookie, Burst, etc.)
    implementation("androidx.graphics:graphics-shapes:1.0.1") 
}
```

-----

### 2\. Using the New "Expressive" Standard Shapes

Material Expressive encourages "bolder" styling. You can apply this immediately by using the `MaterialExpressiveTheme` (instead of `MaterialTheme`) or by manually applying the new shape tokens like `MediumComponentShape` and `LargeComponentShape` which use significantly rounder corners (often 20dp+).

```kotlin
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.foundation.shape.RoundedCornerShape

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveThemeExample() {
    // This theme automatically applies "Expressive" defaults (rounder corners, vibrant colors)
    MaterialExpressiveTheme {
        Button(
            onClick = { /* ... */ },
            // Expressive buttons often use fully rounded corners or larger radii
            shape = RoundedCornerShape(24.dp) 
        ) {
            Text("Expressive Button")
        }
    }
}
```

-----

### 3\. Using Built-in "Organic" Shapes (Polygons)

Material 3 Expressive introduces a set of predefined "organic" shapes (like `Cookie`, `SoftBurst`, `Pill`) accessed via `MaterialShapes`. These are **RoundedPolygons**, not standard Compose shapes, so they are primarily used in specific components like `LoadingIndicator` or require a wrapper.

#### A. In `LoadingIndicator` (Built-in Support)

The new `LoadingIndicator` accepts a list of polygons and morphs between them automatically.

```kotlin
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OrganicLoadingIndicator() {
    LoadingIndicator(
        polygons = listOf(
            MaterialShapes.SoftBurst,    // A star-like burst shape
            MaterialShapes.Cookie9Sided, // A scalloped "cookie" shape
            MaterialShapes.Pill,         // A standard pill shape
            MaterialShapes.Sunny         // A sun-like shape
        )
    )
}
```

#### B. On Standard Components (e.g., Buttons, Cards)

To use a shape like `Cookie9Sided` on a standard `Button`, you must wrap the `RoundedPolygon` into a Compose `Shape`.

**Step 1: Create the Helper Class**

```kotlin
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Size
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath

// Helper to convert Expressive Polygons to a Compose Shape
class PolygonShape(private val polygon: RoundedPolygon) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = polygon.toPath().asComposePath()
        // Scale the path to fit the component size
        val matrix = androidx.compose.ui.graphics.Matrix()
        matrix.scale(
            x = size.width / 2f, // Polygons are normalized to radius 1, so we scale by width/2
            y = size.height / 2f
        )
        matrix.translate(size.width / 2f, size.height / 2f)
        path.transform(matrix)
        
        return Outline.Generic(path)
    }
}
```

**Step 2: Apply to a Button**

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CookieButton() {
    Button(
        onClick = { },
        // Use the helper to apply the "Cookie9Sided" shape
        shape = PolygonShape(MaterialShapes.Cookie9Sided), 
        modifier = Modifier.size(100.dp)
    ) {
        Text("Cookie!")
    }
}
```

-----

### 4\. Advanced: Shape Morphing

One of the coolest features of Expressive M3 is morphing one shape into another (e.g., a button turning into a square).

```kotlin
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShapeMorphingDemo() {
    val infiniteTransition = rememberInfiniteTransition(label = "morph")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "progress"
    )

    // Morph between a "SoftBurst" and a "Pill"
    val morph = remember {
        Morph(
            start = MaterialShapes.SoftBurst,
            end = MaterialShapes.Pill
        )
    }

    Box(
        modifier = Modifier
            .size(120.dp)
            .drawWithCache {
                val path = morph.toPath(progress).asComposePath()
                
                // Scale path to fill the box
                val matrix = androidx.compose.ui.graphics.Matrix()
                matrix.scale(size.width / 2f, size.height / 2f)
                matrix.translate(size.width / 2f, size.height / 2f)
                path.transform(matrix)

                onDrawBehind {
                    drawPath(path, Color(0xFF6750A4))
                }
            }
    )
}
```

### 5. Full MaterialShapes Reference

The `MaterialShapes` class (added in `1.5.0-alpha10`) provides a wide range of predefined `RoundedPolygon` shapes:

| Shape Name | Description |
| :--- | :--- |
| **`MaterialShapes.Arch`** | An arch shape. |
| **`MaterialShapes.Arrow`** | An arrow shape. |
| **`MaterialShapes.Boom`** | A boom shape. |
| **`MaterialShapes.Bun`** | A bun shape. |
| **`MaterialShapes.Burst`** | A burst shape. |
| **`MaterialShapes.Circle`** | A circle shape. |
| **`MaterialShapes.ClamShell`** | A clam-shell shape. |
| **`MaterialShapes.Clover4Leaf`** | A 4-leaf clover shape. |
| **`MaterialShapes.Clover8Leaf`** | An 8-leaf clover shape. |
| **`MaterialShapes.Cookie12Sided`** | A 12-sided cookie shape. |
| **`MaterialShapes.Cookie4Sided`** | A 4-sided cookie shape. |
| **`MaterialShapes.Cookie6Sided`** | A 6-sided cookie shape. |
| **`MaterialShapes.Cookie7Sided`** | A 7-sided cookie shape. |
| **`MaterialShapes.Cookie9Sided`** | A 9-sided cookie shape. |
| **`MaterialShapes.Diamond`** | A diamond shape. |
| **`MaterialShapes.Fan`** | A fan shape. |
| **`MaterialShapes.Flower`** | A flower shape. |
| **`MaterialShapes.Gem`** | A gem shape. |
| **`MaterialShapes.Ghostish`** | A ghost-ish shape. |
| **`MaterialShapes.Heart`** | A heart shape. |
| **`MaterialShapes.Oval`** | An oval shape. |
| **`MaterialShapes.Pentagon`** | A pentagon shape. |
| **`MaterialShapes.Pill`** | A pill shape. |
| **`MaterialShapes.PixelCircle`** | A pixel-circle shape. |
| **`MaterialShapes.PixelTriangle`** | A pixel-triangle shape. |
| **`MaterialShapes.Puffy`** | A puffy shape. |
| **`MaterialShapes.PuffyDiamond`** | A puffy-diamond shape. |
| **`MaterialShapes.SemiCircle`** | A semi-circle shape. |
| **`MaterialShapes.Slanted`** | A slanted square shape. |
| **`MaterialShapes.SoftBoom`** | A soft-boom shape. |
| **`MaterialShapes.SoftBurst`** | A soft-burst shape. |
| **`MaterialShapes.Square`** | A rounded square shape. |
| **`MaterialShapes.Sunny`** | A sunny shape. |
| **`MaterialShapes.Triangle`** | A rounded triangle shape. |
| **`MaterialShapes.VerySunny`** | A very-sunny shape. |