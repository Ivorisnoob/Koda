The "Expressive" update to Material 3 (available in the latest Jetpack Compose alphas) focuses on bolder shapes, "organic" variations, and shape morphing.

The following guide and examples use `androidx.compose.material3:material3:1.4.0-alpha04` (or later), which includes the latest Expressive APIs.

### 1\. Setup & Dependencies

To use these features, you need the latest Material 3 alpha and the **graphics-shapes** library (essential for the new polygon shapes and morphing).

```kotlin
dependencies {
    // Latest Material 3 Alpha (Expressive features)
    implementation("androidx.compose.material3:material3:1.4.0-alpha04")
    
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

### Summary of New Shapes

| Shape Name | Description | Best Use Case |
| :--- | :--- | :--- |
| **`RoundedCornerShape`** | Standard corners, but M3 Expressive uses larger radii (e.g., 20dp+). | Buttons, Cards, Dialogs |
| **`MaterialShapes.SoftBurst`** | A soft, star-like shape with rounded points. | Stickers, badged icons, loading |
| **`MaterialShapes.Cookie9Sided`** | A wavy/scalloped circle. | Avatars, decorative containers |
| **`MaterialShapes.Pill`** | A classic stadium/lozenge shape. | Chips, Toggles, Indicators |
| **`MaterialShapes.Sunny`** | Sharp, sun-like spikes. | Attention-grabbing icons, alerts |