# Material 3 Expressive Lists

Material 3 Expressive introduces updates to Lists, enabling more rich and interactive layouts. This includes segmented styling and enhanced interactions.

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
        modifier = Modifier.clip(RoundedCornerShape(16.dp)), // Example of shape application
        colors = ListItemDefaults.colors(
            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh
        )
    )
}
```

### Segmented Styling

Expressive lists often use "segmented" items, where each item has its own container shape and elevation, separating it from others. This is different from the continuous "flat" list style.

To achieve this:
1.  Apply a Shape to the `ListItem` (or its container).
2.  Add padding between items.
3.  Use a distinct container color.

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
