# Material 3 Expressive Buttons

The Expressive update brings significant enhancements to Buttons, making them more versatile and animated. The key changes involve new size variants, toggle capabilities, and shape morphing animations.

## Key Changes

1.  **New Sizes:** Beyond the standard, we now have Extra Small (XS), Small (S), Medium (M), Large (L), and Extra Large (XL).
2.  **Shape Morphing:** Buttons animate their corner radius (and potentially overall shape) when pressed or toggled.
3.  **Toggle Support:** Built-in selection states for standard buttons.

## Button Sizes

Expressive design encourages using size to denote hierarchy more aggressively.

| Size | Use Case | Implementation Hint |
| :--- | :--- | :--- |
| **XS** | High-density toolbars, compact cards. | `ButtonDefaults.extraSmallContainerSize()` |
| **S** | Secondary actions, dialogs. | `ButtonDefaults.smallContainerSize()` |
| **M** | Default actions. | Standard `Button` |
| **L** | Primary call-to-action (CTA). | `ButtonDefaults.largeContainerSize()` |
| **XL** | Hero actions, splash screens. | `ButtonDefaults.extraLargeContainerSize()` |

## Morphing Animation

Expressive buttons do not just change color on press; they physically react. A common pattern is the **"Squircle to Round"** morph.
- **Rest State:** Slightly squared corners (e.g., `RoundedCornerShape(12.dp)` or a specific smoothing polygon).
- **Press State:** Morphs to fully pill-shaped or circle.

This is often handled internally by the new Expressive Button components, or can be manually applied using `MotionScheme`.

```kotlin
// Example of a Button with Shape Morphing intent
Button(
    onClick = { /* ... */ },
    shape = MaterialTheme.shapes.medium, // Base shape
    // The internal implementation of Expressive Button handles the press-morph
    // provided the experimental APIs are used.
) {
    Text("Expressive Button")
}
```

## Toggle Buttons & Button Groups

Expressive Material introduces dedicated `SplitButton` and `ButtonGroup` components to handle complex selection sets.

### Split Button
A button divided into two touch targets: a main action and a dropdown/menu trigger.

```kotlin
@ExperimentalMaterial3ExpressiveApi
@Composable
fun SplitButtonSample() {
    SplitButton(
        leadingButton = {
            SplitButtonDefaults.LeadingButton(
                onClick = { /* Main Action */ },
            ) {
                Text("Send")
            }
        },
        trailingButton = {
            SplitButtonDefaults.TrailingButton(
                onClick = { /* Open Menu */ },
                checked = false
            ) {
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
        }
    )
}
```

### Button Group
A row of connected buttons, often used for single-select or multi-select filters.

```kotlin
@ExperimentalMaterial3ExpressiveApi
@Composable
fun ButtonGroupSample() {
    val options = listOf("Daily", "Weekly", "Monthly")
    var selectedIndex by remember { mutableStateOf(0) }

    SingleChoiceSegmentedButtonRow {
        options.forEachIndexed { index, label ->
            SegmentedButton(
                selected = index == selectedIndex,
                onClick = { selectedIndex = index },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
            ) {
                Text(label)
            }
        }
    }
}
```

## Styling & Theming

To ensure your buttons feel "Expressive", ensure your `MaterialTheme` is configured with the `expressive` motion scheme. This ensures the press ripples and state change animations use the correct spring physics (low damping, medium stiffness) rather than standard linear eases.

```kotlin
MaterialTheme(
    motionScheme = MotionScheme.expressive()
) {
    // Buttons here will inherit expressive motion
}
```
