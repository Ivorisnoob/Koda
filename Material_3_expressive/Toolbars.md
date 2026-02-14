# Material 3 Expressive Toolbars

Material 3 Expressive introduces updated toolbar patterns, specifically **Floating Toolbars**. These components replace or augment traditional fixed AppBars in specific contexts, providing a more versatile and context-aware set of actions that "float" above the content.

## Overview

There are two primary variants of floating toolbars:
1.  **HorizontalFloatingToolbar:** Ideal for mobile and tablet bottom actions, similar to a simplified, detached BottomAppBar.
2.  **VerticalFloatingToolbar:** Designed for large screens (tablets, foldables, desktop) where vertical real estate is valuable, typically docked to the side.

These toolbars support:
- **Leading/Trailing Content:** Slots for icons or navigation triggers.
- **Content:** The main action area.
- **Expansion/Collapse:** Support for expanding to show more actions or labels.
- **Scroll Behavior:** Can hide/show based on scroll state.

## HorizontalFloatingToolbar

The `HorizontalFloatingToolbar` places content in a row. It is often positioned at the bottom of the screen with a specific offset.

### API Surface

```kotlin
@ExperimentalMaterial3ExpressiveApi
@Composable
fun HorizontalFloatingToolbar(
    expanded: Boolean,
    modifier: Modifier = Modifier,
    containerColor: Color = FloatingToolbarDefaults.containerColor,
    contentPadding: PaddingValues = FloatingToolbarDefaults.ContentPadding,
    scrollBehavior: FloatingToolbarScrollBehavior? = null,
    shape: Shape = FloatingToolbarDefaults.ContainerShape,
    leadingContent: (@Composable RowScope.() -> Unit)? = null,
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit
)
```

### Implementation Example

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MyFloatingToolbar() {
    val listState = rememberLazyListState()
    var expanded by remember { mutableStateOf(false) }
    // Define scroll behavior if you want it to hide on scroll
    val scrollBehavior = FloatingToolbarDefaults.exitAlwaysScrollBehavior()

    Box(modifier = Modifier.fillMaxSize()) {
        // Main Content
        LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 100.dp)) {
            items(50) { Text("Item $it", modifier = Modifier.padding(16.dp)) }
        }

        // Floating Toolbar
        HorizontalFloatingToolbar(
            expanded = expanded,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                // Use the standard screen offset
                .offset(y = -FloatingToolbarDefaults.ScreenOffset),
            leadingContent = {
                IconButton(onClick = { /* Navigate Home */ }) {
                    Icon(Icons.Filled.Home, "Home")
                }
            },
            trailingContent = {
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(Icons.Filled.MoreVert, "More")
                }
            },
            content = {
                // Primary Actions
                FilledIconButton(onClick = { /* Action 1 */ }) {
                    Icon(Icons.Filled.Edit, "Edit")
                }
                IconButton(onClick = { /* Action 2 */ }) {
                    Icon(Icons.Filled.Favorite, "Favorite")
                }
            }
        )
    }
}
```

## VerticalFloatingToolbar

The `VerticalFloatingToolbar` stacks content vertically. This is standard for edge-anchored menus on large screens.

### API Surface

```kotlin
@ExperimentalMaterial3ExpressiveApi
@Composable
fun VerticalFloatingToolbar(
    expanded: Boolean,
    modifier: Modifier = Modifier,
    // ... similar params to Horizontal ...
    leadingContent: (@Composable ColumnScope.() -> Unit)? = null,
    trailingContent: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
)
```

## Key Features & Defaults

### FloatingToolbarDefaults
- **ScreenOffset:** A recommended distance (`24.dp` typically) from the screen edge to position the toolbar.
- **ContainerShape:** Typically a fully rounded shape (Stadium or Circle depending on aspect ratio).
- **Standard vs Vibrant:** You can apply different tonal palettes.

### Scroll Behavior
Like TopAppBar, Floating Toolbars support scroll behaviors:
- `exitAlwaysScrollBehavior()`: Hides the toolbar immediately when scrolling down, shows when scrolling up.
- `enterAlwaysScrollBehavior()`: Hides when scrolling down, shows immediately when scrolling up.

## Best Practices
- **Positioning:** Always float above content. Do not assume it clips content; add padding to your list/scroll container (`contentPadding`) to ensure the last items are visible behind the toolbar.
- **FAB Integration:** Often paired with a Floating Action Button (FAB). The FAB can be docked *within* or *adjacent* to the toolbar using specific `FloatingToolbar` overloads or layouts like `HorizontalFloatingToolbarWithFab`.
