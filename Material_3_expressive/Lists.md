# Material 3 Expressive Lists

Material 3 Expressive introduces updates to Lists, enabling more rich and interactive layouts. This moves beyond simple flat lists to segmented, shape-aware, and physics-driven collections.

## Expressive List Items

Expressive list items allow for more flexible content arrangements and visual styles. They often move beyond the flat list look to include segmented containers and more distinct selection states.

### Usage

You can use the standard `ListItem` composable but apply specific configurations to achieve the expressive look.

```kotlin
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

@Composable
fun ExpressiveListExample() {
    // Segmented style example
    ListItem(
        headlineContent = { Text("Expressive Item") },
        supportingContent = { Text("With segmented styling") },
        leadingContent = {
            Icon(
                Icons.Filled.Favorite,
                contentDescription = null
            )
        },
        // Apply an expressive shape (e.g., Medium Rounded or Squircle)
        modifier = Modifier.clip(RoundedCornerShape(16.dp)),
        colors = ListItemDefaults.colors(
            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh
        )
    )
}
```

### Segmented Styling

Expressive lists often use "segmented" items, where each item has its own container shape and elevation, separating it from others. This is different from the continuous "flat" list style.

To achieve this:
1.  **Shape:** Apply a Shape to the `ListItem`. You can use standard shapes or custom `RoundedPolygon` shapes from the [Shapes](./Shapes.md) library for a more organic feel.
2.  **Spacing:** Add padding between items (e.g., `Arrangement.spacedBy(8.dp)` in your `LazyColumn`).
3.  **Color:** Use a distinct container color (e.g., `surfaceContainer` or `surfaceContainerHigh`) to differentiate the item from the background.

### Interactivity & Motion

When using `MotionScheme.expressive()` in your theme:
*   **Press States:** List items will scale or "squish" slightly on press rather than just showing a ripple.
*   **Reordering:** If implementing drag-and-drop, the item movement will follow the spring physics defined in the motion scheme.

### Multi-line Support

Expressive lists support rich multi-line content. `ListItem` can handle:
*   `headlineContent` (Primary text)
*   `overlineContent` (Text above headline)
*   `supportingContent` (Secondary/Tertiary text below headline)

```kotlin
ListItem(
    headlineContent = { Text("Three line list item") },
    overlineContent = { Text("OVERLINE") },
    supportingContent = { Text("Secondary text that spans multiple lines to provide more context.") },
    leadingContent = { Icon(Icons.Filled.Favorite, contentDescription = null) },
    trailingContent = { Text("Meta") }
)
```
