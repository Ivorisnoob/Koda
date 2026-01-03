# Material 3 Expressive Components Reference

An AI reference of Material Design 3 Expressive components available in Jetpack Compose.

> **Note:** Components marked with 🌟 have expressive features like shape morphing or enhanced motion.

---

## Table of Contents

- [Action Components](#action-components)
  - [Buttons](#buttons)
  - [Floating Action Buttons](#floating-action-buttons)
  - [Icon Buttons](#icon-buttons)
  - [Toggle Buttons](#toggle-buttons)
  - [Split Buttons](#split-buttons)
- [Navigation Components](#navigation-components)
  - [App Bars](#app-bars)
  - [Navigation Bars](#navigation-bars)
  - [Navigation Drawers](#navigation-drawers)
  - [Navigation Rails](#navigation-rails)
  - [Tabs](#tabs)
  - [Toolbars](#toolbars)
- [Containment Components](#containment-components)
  - [Cards](#cards)
  - [Sheets](#sheets)
  - [Dialogs](#dialogs)
- [Selection Components](#selection-components)
  - [Chips](#chips)
  - [Checkbox](#checkbox)
  - [Radio Button](#radio-button)
  - [Switch](#switch)
  - [Sliders](#sliders)
  - [Segmented Buttons](#segmented-buttons)
  - [Button Groups](#button-groups)
- [Text Input Components](#text-input-components)
  - [Text Fields](#text-fields)
  - [Search Bars](#search-bars)
  - [Dropdown Menus](#dropdown-menus)
- [Date & Time Components](#date--time-components)
  - [Date Pickers](#date-pickers)
  - [Time Pickers](#time-pickers)
- [Progress & Loading Components](#progress--loading-components)
  - [Progress Indicators](#progress-indicators)
  - [Loading Indicators](#loading-indicators)
- [Communication Components](#communication-components)
  - [Badges](#badges)
  - [Snackbar](#snackbar)
  - [Tooltips](#tooltips)
- [Other Components](#other-components)
  - [Lists](#lists)
  - [Dividers](#dividers)
  - [Surfaces](#surfaces)
  - [Swipe to Dismiss](#swipe-to-dismiss)

---

## Action Components

### Buttons

#### Button 🌟

The standard filled button with optional expressive shape morphing.

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveButton() {
    Button(
        onClick = { /* action */ },
        shapes = ButtonDefaults.shapes()  // Enables shape morphing
    ) {
        Text("Click Me")
    }
}
```

**Parameters:**
- `onClick`: Lambda called when button is clicked
- `modifier`: Modifier for the button
- `enabled`: Whether the button is enabled
- `shapes`: 🌟 Shape configuration for morphing animations
- `colors`: Button colors
- `elevation`: Button elevation
- `border`: Optional border stroke
- `contentPadding`: Padding for button content
- `interactionSource`: Interaction source for handling interactions

---

#### ElevatedButton 🌟

Button with elevation and optional shape morphing.

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveElevatedButton() {
    ElevatedButton(
        onClick = { /* action */ },
        shapes = ButtonDefaults.shapes()
    ) {
        Text("Elevated")
    }
}
```

---

#### OutlinedButton 🌟

Button with outline border and optional shape morphing.

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveOutlinedButton() {
    OutlinedButton(
        onClick = { /* action */ },
        shapes = ButtonDefaults.shapes()
    ) {
        Text("Outlined")
    }
}
```

---

#### TextButton 🌟

Low-emphasis button with optional shape morphing.

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveTextButton() {
    TextButton(
        onClick = { /* action */ },
        shapes = ButtonDefaults.shapes()
    ) {
        Text("Text Button")
    }
}
```

---

### Floating Action Buttons

#### FloatingActionButton

Standard FAB for primary actions.

```kotlin
FloatingActionButton(onClick = { /* action */ }) {
    Icon(Icons.Default.Add, contentDescription = "Add")
}
```

---

#### MediumFloatingActionButton 🌟

Medium-sized FAB for expressive layouts.

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MediumFAB() {
    MediumFloatingActionButton(onClick = { /* action */ }) {
        Icon(Icons.Default.Edit, contentDescription = "Edit")
    }
}
```

---

#### LargeFloatingActionButton 🌟

Large FAB for prominent primary actions.

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LargeFAB() {
    LargeFloatingActionButton(onClick = { /* action */ }) {
        Icon(
            Icons.Default.Add,
            contentDescription = "Add",
            modifier = Modifier.size(36.dp)
        )
    }
}
```

---

#### ExtendedFloatingActionButton

FAB with extended width for text + icon.

```kotlin
ExtendedFloatingActionButton(
    onClick = { /* action */ },
    icon = { Icon(Icons.Default.Add, contentDescription = null) },
    text = { Text("Create") }
)
```

---

#### MediumExtendedFloatingActionButton 🌟

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MediumExtendedFAB() {
    MediumExtendedFloatingActionButton(
        onClick = { /* action */ },
        icon = { Icon(Icons.Default.Edit, contentDescription = null) },
        text = { Text("Edit") }
    )
}
```

---

#### LargeExtendedFloatingActionButton 🌟

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LargeExtendedFAB() {
    LargeExtendedFloatingActionButton(
        onClick = { /* action */ },
        icon = { Icon(Icons.Default.Add, contentDescription = null) },
        text = { Text("Create New") }
    )
}
```

---

### Icon Buttons

#### IconButton 🌟

Standard icon button with optional shape morphing.

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveIconButton() {
    IconButton(
        onClick = { /* action */ },
        shapes = IconButtonDefaults.shapes()
    ) {
        Icon(Icons.Default.Favorite, contentDescription = "Favorite")
    }
}
```

---

#### FilledTonalIconButton 🌟

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveFilledTonalIconButton() {
    FilledTonalIconButton(
        onClick = { /* action */ },
        shapes = IconButtonDefaults.shapes()
    ) {
        Icon(Icons.Default.Star, contentDescription = "Star")
    }
}
```

---

#### OutlinedIconButton 🌟

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveOutlinedIconButton() {
    OutlinedIconButton(
        onClick = { /* action */ },
        shapes = IconButtonDefaults.shapes()
    ) {
        Icon(Icons.Default.Share, contentDescription = "Share")
    }
}
```

---

### Toggle Buttons

#### ToggleButton 🌟

Toggle button with shape morphing between checked/unchecked states.

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveToggleButton() {
    var checked by remember { mutableStateOf(false) }
    
    ToggleButton(
        checked = checked,
        onCheckedChange = { checked = it },
        shapes = ToggleButtonDefaults.shapes()
    ) {
        Icon(
            if (checked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = null
        )
        Spacer(Modifier.size(ToggleButtonDefaults.IconSpacing))
        Text("Like")
    }
}
```

**Parameters:**
- `checked`: Whether the button is checked
- `onCheckedChange`: Lambda called on state change
- `shapes`: 🌟 Shape configuration for morphing between states
- `colors`: Button colors for different states

---

#### ElevatedToggleButton 🌟

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveElevatedToggleButton() {
    var checked by remember { mutableStateOf(false) }
    
    ElevatedToggleButton(
        checked = checked,
        onCheckedChange = { checked = it }
    ) {
        Text(if (checked) "Selected" else "Select")
    }
}
```

---

#### OutlinedToggleButton 🌟

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveOutlinedToggleButton() {
    var checked by remember { mutableStateOf(false) }
    
    OutlinedToggleButton(
        checked = checked,
        onCheckedChange = { checked = it }
    ) {
        Text(if (checked) "On" else "Off")
    }
}
```

---

#### IconToggleButton 🌟

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveIconToggleButton() {
    var checked by remember { mutableStateOf(false) }
    
    IconToggleButton(
        checked = checked,
        onCheckedChange = { checked = it },
        shapes = IconButtonDefaults.shapes()
    ) {
        Icon(
            if (checked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
            contentDescription = "Bookmark"
        )
    }
}
```

---

#### FilledTonalIconToggleButton 🌟

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveFilledTonalIconToggleButton() {
    var checked by remember { mutableStateOf(false) }
    
    FilledTonalIconToggleButton(
        checked = checked,
        onCheckedChange = { checked = it },
        shapes = IconButtonDefaults.shapes()
    ) {
        Icon(
            if (checked) Icons.Filled.Star else Icons.Outlined.StarBorder,
            contentDescription = "Star"
        )
    }
}
```

---

#### OutlinedIconToggleButton 🌟

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveOutlinedIconToggleButton() {
    var checked by remember { mutableStateOf(false) }
    
    OutlinedIconToggleButton(
        checked = checked,
        onCheckedChange = { checked = it },
        shapes = IconButtonDefaults.shapes()
    ) {
        Icon(
            if (checked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = "Favorite"
        )
    }
}
```

---

### Split Buttons

#### SplitButton 🌟

A button split into a main action and a secondary dropdown action.

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SplitButtonExample() {
    var expanded by remember { mutableStateOf(false) }
    
    SplitButton(
        leadingButton = {
            SplitButtonDefaults.LeadingButton(
                onClick = { /* Main action */ }
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add")
            }
        },
        trailingButton = {
            SplitButtonDefaults.TrailingButton(
                onClick = { expanded = !expanded },
                checked = expanded
            )
        }
    )
}
```

---

## Navigation Components

### App Bars

#### TopAppBar 🌟

Standard top app bar with optional subtitle support.

```kotlin
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveTopAppBar() {
    TopAppBar(
        title = { Text("Title") },
        subtitle = { Text("Subtitle") },  // 🌟 Expressive feature
        navigationIcon = {
            IconButton(onClick = { /* back */ }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        }
    )
}
```

---

#### MediumTopAppBar

Medium-sized top app bar with collapsing behavior.

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediumAppBar() {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    
    MediumTopAppBar(
        title = { Text("Medium Title") },
        scrollBehavior = scrollBehavior
    )
}
```

---

#### LargeTopAppBar

Large top app bar for prominent titles.

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LargeAppBar() {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    
    LargeTopAppBar(
        title = { Text("Large Title") },
        scrollBehavior = scrollBehavior
    )
}
```

---

#### MediumFlexibleTopAppBar 🌟

Medium app bar with flexible content and subtitle.

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MediumFlexibleAppBar() {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    
    MediumFlexibleTopAppBar(
        title = { Text("Flexible Medium") },
        subtitle = { Text("With subtitle") },
        scrollBehavior = scrollBehavior
    )
}
```

---

#### LargeFlexibleTopAppBar 🌟

Large app bar with flexible content and subtitle.

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LargeFlexibleAppBar() {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    
    LargeFlexibleTopAppBar(
        title = { Text("Flexible Large") },
        subtitle = { Text("Descriptive subtitle") },
        titleHorizontalAlignment = Alignment.Start,
        scrollBehavior = scrollBehavior
    )
}
```

---

#### CenterAlignedTopAppBar

Top app bar with centered title.

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CenteredAppBar() {
    CenterAlignedTopAppBar(
        title = { Text("Centered Title") }
    )
}
```

---

#### BottomAppBar

Bottom app bar with optional FAB cutout.

```kotlin
@Composable
fun BottomBar() {
    BottomAppBar(
        actions = {
            IconButton(onClick = { /* action */ }) {
                Icon(Icons.Default.Menu, contentDescription = "Menu")
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { /* action */ }) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        }
    )
}
```

---

#### FlexibleBottomAppBar 🌟

Expressive bottom app bar with flexible arrangements.

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FlexibleBottomBar() {
    FlexibleBottomAppBar(
        horizontalArrangement = Arrangement.SpaceEvenly,
        content = {
            IconButton(onClick = { }) { Icon(Icons.Default.Home, null) }
            IconButton(onClick = { }) { Icon(Icons.Default.Search, null) }
            IconButton(onClick = { }) { Icon(Icons.Default.Settings, null) }
        }
    )
}
```

---

### Navigation Bars

#### NavigationBar

Bottom navigation bar for primary destinations.

```kotlin
@Composable
fun BottomNavigation() {
    var selectedItem by remember { mutableIntStateOf(0) }
    val items = listOf("Home", "Search", "Profile")
    
    NavigationBar {
        items.forEachIndexed { index, item ->
            NavigationBarItem(
                icon = { Icon(Icons.Default.Home, contentDescription = item) },
                label = { Text(item) },
                selected = selectedItem == index,
                onClick = { selectedItem = index }
            )
        }
    }
}
```

---

#### ShortNavigationBar 🌟

Compact navigation bar for limited space.

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShortNavBar() {
    var selectedItem by remember { mutableIntStateOf(0) }
    
    ShortNavigationBar(
        arrangement = ShortNavigationBarArrangement.EqualWeight
    ) {
        ShortNavigationBarItem(
            selected = selectedItem == 0,
            onClick = { selectedItem = 0 },
            icon = { Icon(Icons.Default.Home, null) },
            label = { Text("Home") }
        )
        ShortNavigationBarItem(
            selected = selectedItem == 1,
            onClick = { selectedItem = 1 },
            icon = { Icon(Icons.Default.Search, null) },
            label = { Text("Search") }
        )
    }
}
```

---

### Navigation Drawers

#### ModalNavigationDrawer

Modal drawer that slides in from the edge.

```kotlin
@Composable
fun ModalDrawer() {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                NavigationDrawerItem(
                    label = { Text("Home") },
                    selected = true,
                    onClick = { }
                )
            }
        }
    ) {
        // Main content
    }
}
```

---

#### DismissibleNavigationDrawer

Drawer that can be dismissed with gesture.

```kotlin
@Composable
fun DismissibleDrawer() {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    
    DismissibleNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            DismissibleDrawerSheet {
                NavigationDrawerItem(
                    label = { Text("Settings") },
                    selected = false,
                    onClick = { }
                )
            }
        }
    ) {
        // Main content
    }
}
```

---

#### PermanentNavigationDrawer

Always-visible drawer for large screens.

```kotlin
@Composable
fun PermanentDrawer() {
    PermanentNavigationDrawer(
        drawerContent = {
            PermanentDrawerSheet {
                NavigationDrawerItem(
                    label = { Text("Dashboard") },
                    selected = true,
                    onClick = { }
                )
            }
        }
    ) {
        // Main content
    }
}
```

---

### Navigation Rails

#### NavigationRail

Vertical navigation for tablets and large screens.

```kotlin
@Composable
fun NavRail() {
    var selectedItem by remember { mutableIntStateOf(0) }
    
    NavigationRail {
        NavigationRailItem(
            icon = { Icon(Icons.Default.Home, null) },
            label = { Text("Home") },
            selected = selectedItem == 0,
            onClick = { selectedItem = 0 }
        )
        NavigationRailItem(
            icon = { Icon(Icons.Default.Search, null) },
            label = { Text("Search") },
            selected = selectedItem == 1,
            onClick = { selectedItem = 1 }
        )
    }
}
```

---

#### WideNavigationRail 🌟

Expanded navigation rail with more content.

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WideNavRail() {
    val railState = rememberWideNavigationRailState()
    
    WideNavigationRail(state = railState) {
        WideNavigationRailItem(
            railExpanded = railState.isExpanded,
            icon = { Icon(Icons.Default.Home, null) },
            label = { Text("Home") },
            selected = true,
            onClick = { }
        )
    }
}
```

---

#### ModalWideNavigationRail 🌟

Modal version of wide navigation rail.

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ModalWideNavRail() {
    ModalWideNavigationRail(
        expanded = true,
        onCollapse = { /* handle collapse */ }
    ) {
        WideNavigationRailItem(
            railExpanded = true,
            icon = { Icon(Icons.Default.Settings, null) },
            label = { Text("Settings") },
            selected = false,
            onClick = { }
        )
    }
}
```

---

### Tabs

#### PrimaryTabRow

Primary tabs for main content sections.

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrimaryTabs() {
    var selectedTab by remember { mutableIntStateOf(0) }
    
    PrimaryTabRow(selectedTabIndex = selectedTab) {
        Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
            Text("Tab 1", modifier = Modifier.padding(16.dp))
        }
        Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
            Text("Tab 2", modifier = Modifier.padding(16.dp))
        }
    }
}
```

---

#### SecondaryTabRow

Secondary tabs for sub-sections.

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecondaryTabs() {
    var selectedTab by remember { mutableIntStateOf(0) }
    
    SecondaryTabRow(selectedTabIndex = selectedTab) {
        Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
            Text("Details")
        }
        Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
            Text("Reviews")
        }
    }
}
```

---

#### PrimaryScrollableTabRow

Scrollable primary tabs for many items.

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScrollablePrimaryTabs() {
    var selectedTab by remember { mutableIntStateOf(0) }
    
    PrimaryScrollableTabRow(selectedTabIndex = selectedTab) {
        repeat(10) { index ->
            Tab(
                selected = selectedTab == index,
                onClick = { selectedTab = index }
            ) {
                Text("Tab $index", modifier = Modifier.padding(16.dp))
            }
        }
    }
}
```

---

#### SecondaryScrollableTabRow

Scrollable secondary tabs.

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScrollableSecondaryTabs() {
    var selectedTab by remember { mutableIntStateOf(0) }
    
    SecondaryScrollableTabRow(selectedTabIndex = selectedTab) {
        repeat(10) { index ->
            Tab(
                selected = selectedTab == index,
                onClick = { selectedTab = index }
            ) {
                Text("Item $index")
            }
        }
    }
}
```

---

### Toolbars

#### HorizontalFloatingToolbar 🌟

Floating toolbar for contextual actions.

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FloatingToolbar() {
    val state = rememberFloatingToolbarState()
    
    HorizontalFloatingToolbar(
        expanded = state.isExpanded,
        floatingActionButton = {
            FloatingActionButton(onClick = { }) {
                Icon(Icons.Default.Add, null)
            }
        },
        content = {
            IconButton(onClick = { }) { Icon(Icons.Default.Edit, null) }
            IconButton(onClick = { }) { Icon(Icons.Default.Share, null) }
            IconButton(onClick = { }) { Icon(Icons.Default.Delete, null) }
        }
    )
}
```

---

#### VerticalFloatingToolbar 🌟

Vertical floating toolbar.

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VerticalToolbar() {
    val state = rememberFloatingToolbarState()
    
    VerticalFloatingToolbar(
        expanded = state.isExpanded,
        floatingActionButton = {
            FloatingActionButton(onClick = { }) {
                Icon(Icons.Default.Add, null)
            }
        },
        content = {
            IconButton(onClick = { }) { Icon(Icons.Default.Edit, null) }
            IconButton(onClick = { }) { Icon(Icons.Default.Share, null) }
        }
    )
}
```

---

## Containment Components

### Cards

#### Card

Basic card container.

```kotlin
@Composable
fun BasicCard() {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Card Title", style = MaterialTheme.typography.titleLarge)
            Text("Card content goes here")
        }
    }
}
```

---

#### ElevatedCard

Card with elevation.

```kotlin
@Composable
fun ElevatedCardExample() {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Elevated Card")
        }
    }
}
```

---

#### OutlinedCard

Card with outline border.

```kotlin
@Composable
fun OutlinedCardExample() {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Outlined Card")
        }
    }
}
```

---

### Sheets

#### ModalBottomSheet

Bottom sheet that appears modally.

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomSheetExample() {
    var showSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    
    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState
        ) {
            // Sheet content
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Bottom Sheet Content")
            }
        }
    }
}
```

---

#### BottomSheetScaffold

Scaffold with integrated bottom sheet.

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomSheetScaffoldExample() {
    val scaffoldState = rememberBottomSheetScaffoldState()
    
    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetContent = {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Sheet Content")
            }
        }
    ) { innerPadding ->
        // Main content
    }
}
```

---

### Dialogs

#### AlertDialog

Standard alert dialog.

```kotlin
@Composable
fun AlertDialogExample() {
    var showDialog by remember { mutableStateOf(false) }
    
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Dialog Title") },
            text = { Text("Dialog message content") },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
```

---

#### BasicAlertDialog

Basic customizable dialog.

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BasicDialogExample() {
    var showDialog by remember { mutableStateOf(false) }
    
    if (showDialog) {
        BasicAlertDialog(
            onDismissRequest = { showDialog = false }
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                tonalElevation = 6.dp
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("Custom Dialog Content")
                }
            }
        }
    }
}
```

---

## Selection Components

### Chips

#### AssistChip

Chip for smart suggestions.

```kotlin
@Composable
fun AssistChipExample() {
    AssistChip(
        onClick = { /* action */ },
        label = { Text("Assist") },
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = null)
        }
    )
}
```

---

#### FilterChip

Chip for filtering content.

```kotlin
@Composable
fun FilterChipExample() {
    var selected by remember { mutableStateOf(false) }
    
    FilterChip(
        selected = selected,
        onClick = { selected = !selected },
        label = { Text("Filter") },
        leadingIcon = if (selected) {
            { Icon(Icons.Default.Done, null) }
        } else null
    )
}
```

---

#### InputChip

Chip representing user input.

```kotlin
@Composable
fun InputChipExample() {
    var enabled by remember { mutableStateOf(true) }
    
    if (enabled) {
        InputChip(
            selected = false,
            onClick = { enabled = false },
            label = { Text("Input Tag") },
            trailingIcon = {
                Icon(Icons.Default.Close, null)
            }
        )
    }
}
```

---

#### SuggestionChip

Chip for suggestions.

```kotlin
@Composable
fun SuggestionChipExample() {
    SuggestionChip(
        onClick = { /* action */ },
        label = { Text("Suggestion") }
    )
}
```

---

#### ElevatedAssistChip

Elevated assist chip.

```kotlin
@Composable
fun ElevatedAssistChipExample() {
    ElevatedAssistChip(
        onClick = { /* action */ },
        label = { Text("Elevated Assist") }
    )
}
```

---

#### ElevatedFilterChip

Elevated filter chip.

```kotlin
@Composable
fun ElevatedFilterChipExample() {
    var selected by remember { mutableStateOf(false) }
    
    ElevatedFilterChip(
        selected = selected,
        onClick = { selected = !selected },
        label = { Text("Elevated Filter") }
    )
}
```

---

#### ElevatedSuggestionChip

Elevated suggestion chip.

```kotlin
@Composable
fun ElevatedSuggestionChipExample() {
    ElevatedSuggestionChip(
        onClick = { /* action */ },
        label = { Text("Elevated Suggestion") }
    )
}
```

---

### Checkbox

#### Checkbox

Standard checkbox.

```kotlin
@Composable
fun CheckboxExample() {
    var checked by remember { mutableStateOf(false) }
    
    Checkbox(
        checked = checked,
        onCheckedChange = { checked = it }
    )
}
```

---

#### TriStateCheckbox

Checkbox with three states.

```kotlin
@Composable
fun TriStateCheckboxExample() {
    var state by remember { mutableStateOf(ToggleableState.Off) }
    
    TriStateCheckbox(
        state = state,
        onClick = {
            state = when (state) {
                ToggleableState.Off -> ToggleableState.Indeterminate
                ToggleableState.Indeterminate -> ToggleableState.On
                ToggleableState.On -> ToggleableState.Off
            }
        }
    )
}
```

---

### Radio Button

#### RadioButton

Standard radio button.

```kotlin
@Composable
fun RadioButtonExample() {
    val options = listOf("Option 1", "Option 2", "Option 3")
    var selected by remember { mutableStateOf(options[0]) }
    
    Column {
        options.forEach { option ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { selected = option }
            ) {
                RadioButton(
                    selected = selected == option,
                    onClick = { selected = option }
                )
                Text(option)
            }
        }
    }
}
```

---

### Switch

#### Switch

Toggle switch.

```kotlin
@Composable
fun SwitchExample() {
    var checked by remember { mutableStateOf(false) }
    
    Switch(
        checked = checked,
        onCheckedChange = { checked = it },
        thumbContent = if (checked) {
            { Icon(Icons.Default.Check, null, Modifier.size(SwitchDefaults.IconSize)) }
        } else null
    )
}
```

---

### Sliders

#### Slider

Continuous value slider.

```kotlin
@Composable
fun SliderExample() {
    var value by remember { mutableFloatStateOf(0.5f) }
    
    Slider(
        value = value,
        onValueChange = { value = it },
        valueRange = 0f..1f
    )
}
```

---

#### RangeSlider

Slider with range selection.

```kotlin
@Composable
fun RangeSliderExample() {
    var range by remember { mutableStateOf(0.2f..0.8f) }
    
    RangeSlider(
        value = range,
        onValueChange = { range = it },
        valueRange = 0f..1f
    )
}
```

---

#### VerticalSlider 🌟

Vertically oriented slider.

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VerticalSliderExample() {
    var value by remember { mutableFloatStateOf(0.5f) }
    
    VerticalSlider(
        value = value,
        onValueChange = { value = it },
        modifier = Modifier.height(200.dp)
    )
}
```

---

### Segmented Buttons

#### SingleChoiceSegmentedButtonRow

Row of segmented buttons for single selection.

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SingleChoiceSegmentedButtons() {
    var selectedIndex by remember { mutableIntStateOf(0) }
    val options = listOf("Day", "Week", "Month")
    
    SingleChoiceSegmentedButtonRow {
        options.forEachIndexed { index, label ->
            SegmentedButton(
                selected = selectedIndex == index,
                onClick = { selectedIndex = index },
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = options.size
                )
            ) {
                Text(label)
            }
        }
    }
}
```

---

#### MultiChoiceSegmentedButtonRow

Row of segmented buttons for multiple selection.

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiChoiceSegmentedButtons() {
    var selectedOptions by remember { mutableStateOf(setOf<Int>()) }
    val options = listOf("S", "M", "L", "XL")
    
    MultiChoiceSegmentedButtonRow {
        options.forEachIndexed { index, label ->
            SegmentedButton(
                checked = index in selectedOptions,
                onCheckedChange = {
                    selectedOptions = if (index in selectedOptions) {
                        selectedOptions - index
                    } else {
                        selectedOptions + index
                    }
                },
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = options.size
                )
            ) {
                Text(label)
            }
        }
    }
}
```

---

### Button Groups 🌟

#### ButtonGroup with Connected ToggleButtons

Connected button group for segmented selection.

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ConnectedButtonGroup() {
    var selectedIndex by remember { mutableIntStateOf(0) }
    val options = listOf("Week", "Month", "Year")
    
    Row(
        horizontalArrangement = Arrangement.spacedBy(
            ButtonGroupDefaults.ConnectedSpaceBetween
        )
    ) {
        options.forEachIndexed { index, label ->
            ToggleButton(
                checked = selectedIndex == index,
                onCheckedChange = { selectedIndex = index },
                modifier = Modifier
                    .weight(1f)
                    .semantics { role = Role.RadioButton },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                }
            ) {
                Text(label)
            }
        }
    }
}
```

**Parameters for ButtonGroupDefaults:**
- `ConnectedSpaceBetween`: Default spacing between connected buttons
- `connectedLeadingButtonShapes()`: Shapes for the first button
- `connectedMiddleButtonShapes()`: Shapes for middle buttons
- `connectedTrailingButtonShapes()`: Shapes for the last button
- `connectedButtonCheckedShape`: Shape when a button is checked

---

#### ButtonGroup with Overflow

Button group with overflow indicator for many items.

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ButtonGroupWithOverflow() {
    ButtonGroup(
        overflowIndicator = { menuState ->
            ButtonGroupDefaults.OverflowIndicator(menuState = menuState)
        }
    ) {
        for (i in 0 until 10) {
            clickableItem(onClick = {}, label = "$i")
        }
    }
}
```

---

## Text Input Components

### Text Fields

#### TextField

Standard filled text field.

```kotlin
@Composable
fun TextFieldExample() {
    var text by remember { mutableStateOf("") }
    
    TextField(
        value = text,
        onValueChange = { text = it },
        label = { Text("Label") },
        placeholder = { Text("Enter text") }
    )
}
```

---

#### OutlinedTextField

Text field with outline.

```kotlin
@Composable
fun OutlinedTextFieldExample() {
    var text by remember { mutableStateOf("") }
    
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text("Label") },
        placeholder = { Text("Enter text") }
    )
}
```

---

#### SecureTextField 🌟

Text field for passwords with visibility toggle.

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SecureTextFieldExample() {
    var password by remember { mutableStateOf("") }
    
    SecureTextField(
        state = rememberTextFieldState(),
        textObfuscationMode = TextObfuscationMode.Hidden,
        label = { Text("Password") }
    )
}
```

---

#### OutlinedSecureTextField 🌟

Outlined secure text field.

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OutlinedSecureTextFieldExample() {
    OutlinedSecureTextField(
        state = rememberTextFieldState(),
        textObfuscationMode = TextObfuscationMode.Hidden,
        label = { Text("Password") }
    )
}
```

---

### Search Bars

#### SearchBar

Expandable search bar.

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBarExample() {
    var query by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    
    SearchBar(
        inputField = {
            SearchBarDefaults.InputField(
                query = query,
                onQueryChange = { query = it },
                onSearch = { expanded = false },
                expanded = expanded,
                onExpandedChange = { expanded = it },
                placeholder = { Text("Search") }
            )
        },
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        // Search suggestions
    }
}
```

---

#### DockedSearchBar

Search bar that stays docked.

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DockedSearchBarExample() {
    var query by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    
    DockedSearchBar(
        inputField = {
            SearchBarDefaults.InputField(
                query = query,
                onQueryChange = { query = it },
                onSearch = { expanded = false },
                expanded = expanded,
                onExpandedChange = { expanded = it }
            )
        },
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        // Search results
    }
}
```

---

#### ExpandedDockedSearchBar 🌟

Always-expanded docked search bar.

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpandedDockedSearchBarExample() {
    var query by remember { mutableStateOf("") }
    
    ExpandedDockedSearchBar(
        inputField = {
            SearchBarDefaults.InputField(
                query = query,
                onQueryChange = { query = it },
                onSearch = { /* handle search */ },
                expanded = true,
                onExpandedChange = { }
            )
        }
    ) {
        // Search content
    }
}
```

---

#### ExpandedFullScreenSearchBar 🌟

Full-screen search experience.

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FullScreenSearchBarExample() {
    var query by remember { mutableStateOf("") }
    
    ExpandedFullScreenSearchBar(
        inputField = {
            SearchBarDefaults.InputField(
                query = query,
                onQueryChange = { query = it },
                onSearch = { /* handle */ },
                expanded = true,
                onExpandedChange = { }
            )
        }
    ) {
        // Full-screen search content
    }
}
```

---

### Dropdown Menus

#### DropdownMenu

Standard dropdown menu.

```kotlin
@Composable
fun DropdownMenuExample() {
    var expanded by remember { mutableStateOf(false) }
    
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, null)
        }
        
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Option 1") },
                onClick = { expanded = false }
            )
            DropdownMenuItem(
                text = { Text("Option 2") },
                onClick = { expanded = false }
            )
        }
    }
}
```

---

#### ExposedDropdownMenuBox

Dropdown attached to a text field.

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExposedDropdownExample() {
    var expanded by remember { mutableStateOf(false) }
    var selectedOption by remember { mutableStateOf("") }
    val options = listOf("Option 1", "Option 2", "Option 3")
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedOption,
            onValueChange = { },
            readOnly = true,
            label = { Text("Select") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        selectedOption = option
                        expanded = false
                    }
                )
            }
        }
    }
}
```

---

## Date & Time Components

### Date Pickers

#### DatePicker

Date selection component.

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerExample() {
    val state = rememberDatePickerState()
    
    DatePicker(state = state)
}
```

---

#### DatePickerDialog

Modal dialog with date picker.

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerDialogExample() {
    var showDialog by remember { mutableStateOf(false) }
    val state = rememberDatePickerState()
    
    if (showDialog) {
        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("OK")
                }
            }
        ) {
            DatePicker(state = state)
        }
    }
}
```

---

#### DateRangePicker

Range selection for dates.

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangePickerExample() {
    val state = rememberDateRangePickerState()
    
    DateRangePicker(
        state = state,
        modifier = Modifier.height(500.dp)
    )
}
```

---

### Time Pickers

#### TimePicker

Time selection component.

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerExample() {
    val state = rememberTimePickerState()
    
    TimePicker(state = state)
}
```

---

#### TimeInput

Keyboard-based time input.

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeInputExample() {
    val state = rememberTimePickerState()
    
    TimeInput(state = state)
}
```

---

## Progress & Loading Components

### Progress Indicators

#### CircularProgressIndicator

Circular progress indicator.

```kotlin
// Indeterminate
@Composable
fun IndeterminateProgress() {
    CircularProgressIndicator()
}

// Determinate
@Composable
fun DeterminateProgress(progress: Float) {
    CircularProgressIndicator(progress = { progress })
}
```

---

#### LinearProgressIndicator

Linear progress indicator.

```kotlin
// Indeterminate
@Composable
fun IndeterminateLinearProgress() {
    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
}

// Determinate
@Composable
fun DeterminateLinearProgress(progress: Float) {
    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier.fillMaxWidth()
    )
}
```

---

#### CircularWavyProgressIndicator 🌟

Wavy circular progress with expressive motion.

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CircularWavyProgressExample() {
    CircularWavyProgressIndicator()
}
```

---

#### LinearWavyProgressIndicator 🌟

Wavy linear progress indicator.

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LinearWavyProgressExample() {
    LinearWavyProgressIndicator(
        progress = { 0.7f },
        modifier = Modifier.fillMaxWidth()
    )
}
```

---

### Loading Indicators

#### LoadingIndicator 🌟

General-purpose loading indicator.

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoadingIndicatorExample() {
    LoadingIndicator()
}
```

---

#### ContainedLoadingIndicator 🌟

Loading indicator with container.

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ContainedLoadingExample() {
    ContainedLoadingIndicator()
}
```

---

## Communication Components

### Badges

#### Badge

Small badge for notifications.

```kotlin
@Composable
fun BadgeExample() {
    Badge { Text("3") }
}
```

---

#### BadgedBox

Box with badge overlay.

```kotlin
@Composable
fun BadgedBoxExample() {
    BadgedBox(
        badge = { Badge { Text("99+") } }
    ) {
        Icon(Icons.Default.Notifications, null)
    }
}
```

---

### Snackbar

#### Snackbar

Transient message at bottom of screen.

```kotlin
@Composable
fun SnackbarExample() {
    val snackbarHostState = remember { SnackbarHostState() }
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        // Content
        LaunchedEffect(Unit) {
            snackbarHostState.showSnackbar(
                message = "Message",
                actionLabel = "Undo"
            )
        }
    }
}
```

---

### Tooltips

#### TooltipBox

Container for tooltip display.

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TooltipExample() {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text("Tooltip text") } },
        state = rememberTooltipState()
    ) {
        IconButton(onClick = { }) {
            Icon(Icons.Default.Info, null)
        }
    }
}
```

---

#### RichTooltip

Tooltip with rich content.

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RichTooltipExample() {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
        tooltip = {
            RichTooltip(
                title = { Text("Title") },
                action = { TextButton(onClick = { }) { Text("Action") } }
            ) {
                Text("Extended tooltip content")
            }
        },
        state = rememberTooltipState()
    ) {
        IconButton(onClick = { }) {
            Icon(Icons.Default.Help, null)
        }
    }
}
```

---

## Other Components

### Lists

#### ListItem

Standard list item.

```kotlin
@Composable
fun ListItemExample() {
    ListItem(
        headlineContent = { Text("Headline") },
        supportingContent = { Text("Supporting text") },
        leadingContent = {
            Icon(Icons.Default.Person, null)
        },
        trailingContent = {
            Icon(Icons.Default.ChevronRight, null)
        }
    )
}
```

---

### Dividers

#### HorizontalDivider

Horizontal divider line.

```kotlin
@Composable
fun HorizontalDividerExample() {
    HorizontalDivider()
}
```

---

#### VerticalDivider

Vertical divider line.

```kotlin
@Composable
fun VerticalDividerExample() {
    Row {
        Text("Left")
        VerticalDivider(modifier = Modifier.height(20.dp))
        Text("Right")
    }
}
```

---

### Surfaces

#### Surface

Basic container surface.

```kotlin
@Composable
fun SurfaceExample() {
    Surface(
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Surface content")
        }
    }
}
```

---

### Swipe to Dismiss

#### SwipeToDismissBox

Swipeable container for dismissible content.

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToDismissExample() {
    val dismissState = rememberSwipeToDismissBoxState()
    
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Red)
            )
        }
    ) {
        ListItem(
            headlineContent = { Text("Swipe to dismiss") }
        )
    }
}
```

---

## Theming

### MaterialExpressiveTheme 🌟

Complete expressive theme wrapper.

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveTheme(content: @Composable () -> Unit) {
    MaterialExpressiveTheme(
        colorScheme = if (isSystemInDarkTheme()) {
            dynamicDarkColorScheme(LocalContext.current)
        } else {
            dynamicLightColorScheme(LocalContext.current)
        },
        motionScheme = MotionScheme.expressive()
    ) {
        content()
    }
}
```

---

### Motion Schemes 🌟

#### Expressive Motion

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val expressiveMotion = MotionScheme.expressive()
```

#### Standard Motion

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val standardMotion = MotionScheme.standard()
```

---

## Key Annotations

- `@ExperimentalMaterial3ExpressiveApi` - Required for expressive components
- `@ExperimentalMaterial3Api` - Required for some Material 3 components
- `@OptIn` - Used to opt-in to experimental APIs

---

## Dependencies

```kotlin
dependencies {
    implementation("androidx.compose.material3:material3:1.5.0-alpha10")
}
```

---

*This reference covers the main Material 3 Expressive components. For complete documentation, refer to the [official Material 3 documentation](https://m3.material.io/).*
