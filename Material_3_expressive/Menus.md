# Material 3 Expressive Menus

Material 3 Expressive updates Menus with new interactive capabilities, visual grouping, and physics-based entrance animations.

## Interaction & Motion

The most significant change in Expressive Menus is how they appear.

*   **Standard:** Menus expand linearly or with a simple ease-out.
*   **Expressive:** When `MotionScheme.expressive()` is active, menus "spring" open. They may slightly overshoot their final size or position before settling, giving a visceral, physical feel to the action of opening a menu.

## New Menu Items

The expressive update introduces specific composables for different interaction types within menus.

### Toggleable Menu Item

Used for items that switch a state on or off (like a Switch or Checkbox).

```kotlin
// Conceptual example
DropdownMenuItem(
    text = { Text("Show Notifications") },
    onClick = { /* toggle state */ },
    trailingIcon = {
        Switch(
            checked = true,
            onCheckedChange = null // Handled by menu item click
        )
    }
)
```

### Selectable Menu Item

Used for items that are part of a selection group (like Radio Buttons).

```kotlin
// Conceptual example
DropdownMenuItem(
    text = { Text("Option A") },
    onClick = { /* select option */ },
    leadingIcon = {
        RadioButton(
            selected = true,
            onClick = null
        )
    }
)
```

## Menu Groups

Expressive menus support visual grouping of items. This typically involves using `HorizontalDivider` between logical groups or using distinct background containers for sub-sections of a menu.

## Visual Updates

Expressive menus often feature:
*   **Larger Corner Radius:** Often `RoundedCornerShape(16.dp)` or larger.
*   **Dynamic Spacing:** More breathing room between items compared to dense desktop-style menus.
*   **Iconography:** Heavy use of leading icons to improve scannability.
