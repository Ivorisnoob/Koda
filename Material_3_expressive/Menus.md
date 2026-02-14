# Material 3 Expressive Menus

Material 3 Expressive updates Menus with new interactive capabilities and visual grouping.

## New Menu Items

The expressive update introduces specific composables for different interaction types within menus.

### Toggleable Menu Item

Used for items that switch a state on or off (like a Switch or Checkbox).

```kotlin
// Conceptual example - exact API name to be confirmed in 1.5.0-alpha
// DropdownMenuItem with specific trailing icon for toggle
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

Expressive menus support visual grouping of items. This can be achieved using dividers or specific layout containers to group related actions.

## Visual Updates

Expressive menus may feature:
*   Rounded corners for the menu popup (e.g., Medium or Large shapes).
*   Dynamic spacing between items.
*   "Expressive" motion when opening/closing (handled by `MotionScheme`).
