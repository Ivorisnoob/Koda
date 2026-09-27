# Notes

## Material 3 Expressive segmented lists

The grouped, highlighted lists in OpenStream's player settings (captions, quality, sources) are
Material 3 Expressive's **segmented list**, from `androidx.compose.material3` `1.5.0-alpha29`.
They need `@OptIn(ExperimentalMaterial3ExpressiveApi::class)`.

### The three pieces

- **`SegmentedListItem(...)`**: the row. There's a plain overload with `onClick` and a selectable
  overload with `selected` + `onClick`. The selectable one gives the highlighted look on the chosen
  row. Slots: `leadingContent`, `supportingContent`, `trailingContent`, and the headline as the
  trailing lambda.
- **`ListItemDefaults.segmentedShapes(index, count)`**: shapes each row by its position. The first
  row gets big rounded top corners, the last gets big rounded bottom corners, and middle rows get
  small corners, so separate rows read as one grouped card.
- **`ListItemDefaults.segmentedColors()`** and **`ListItemDefaults.SegmentedGap`**: the matching
  container colors and the small gap between rows. Use the gap as the list spacing.

### Minimal example

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LanguagePicker(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        options.forEachIndexed { index, option ->
            SegmentedListItem(
                selected = option == selected,
                onClick = { onSelect(option) },
                shapes = ListItemDefaults.segmentedShapes(index = index, count = options.size),
                colors = ListItemDefaults.segmentedColors(),
                trailingContent = {
                    if (option == selected) Icon(Icons.Default.Check, contentDescription = null)
                }
            ) {
                Text(option)
            }
        }
    }
}
```

In a `LazyColumn`, use `itemsIndexed` so each row knows its index, and set
`verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)` on the list.

### Related: expressive toggle buttons

The speed buttons on OpenStream's Speed page are the expressive `ToggleButton` from the same
library:

```kotlin
ToggleButton(checked = isSelected, onCheckedChange = { onSelect() }) {
    Text("1.5×")
}
```

### Version requirements (checked 2026-09-26)

- `material3` `1.5.0-alpha29` needs **AGP 9.1+** and **compileSdk 37**. Its AAR metadata enforces
  both.
- It pulls in Kotlin stdlib 2.2.20, so the Kotlin compiler has to be 2.1 or newer to read it.
- The newest alpha that still works on AGP 8.x / compileSdk 36 is `1.5.0-alpha18`. From alpha19
  onward, AGP 9.1 is required.
- OpenStream's working combination: Gradle 9.8, AGP 9.4.1 (built-in Kotlin, so no
  `org.jetbrains.kotlin.android` plugin in the app module and no `kotlinOptions`), Kotlin 2.3.21,
  KSP 2.3.12, Hilt 2.60.1, Room 2.8.5, kotlinx-serialization 1.10.0, Compose BOM 2026.09.00.
- These are alpha APIs, so parameter names can change between alphas. Between alpha13 and alpha29,
  for example, the `Slider` overload taking `value` plus custom `thumb`/`track` was removed. Use the
  state-based `Slider(state = rememberSliderState(), onValueChange = ..., thumb = ..., track = ...)`
  instead.

## Where Koda should use segmented lists

### Status (2026-09-27)

Done. Koda is on `material3` `1.5.0-alpha29`, and all six steps below are in, using
`SegmentedGroup`, `SegmentedColumn`, `segmentedRowShape` and `animatedSegmentedShapes` in
`ui/components/`. The how-to now lives in `docs/ui-conventions.md`.

### Availability in Koda (checked 2026-09-26)

Koda is on `material3` `1.5.0-alpha24` (AGP 9.3.1, compileSdk 37, Kotlin 2.2.21). `javap` on the
alpha24 AAR shows `SegmentedListItem` (four overloads), `ListItemDefaults.segmentedShapes`,
`segmentedColors` and `SegmentedGap` are all already there, so **no upgrade is needed** to adopt
them. Koda already meets alpha29's requirements too if a bump is wanted later; watch the `Slider`
overload change above, since Koda has custom scrubbers. The Expressive opt-in is already
module-wide (`app/build.gradle.kts`, `-opt-in=...ExperimentalMaterial3ExpressiveApi`).

### Plan, in order

1. **Statistics (`ui/library/StatsScreen.kt`) - do first, a like-for-like swap.**
   The screen already imitates segmented lists by hand: `segmentShape()` (big outer corners,
   small inner ones) plus a 2dp bottom padding as the gap. Three lists use it: Top songs,
   Top artists, Recent searches. Replace each with `SegmentedListItem` +
   `segmentedShapes(index, count)` + `SegmentedGap`, and delete `segmentShape()`. Rank badge and
   artwork go in `leadingContent`, the play count / remove button in `trailingContent`. Gains the
   press shape-morph and the proper colours; behaviour unchanged.

2. **Listening history (`ui/library/ListeningHistoryScreen.kt`) - most visible change.**
   Today every play is its own 20dp card with 12dp between them. Make **each day's runs one
   segmented group under its sticky `DayHeader`**, so a day reads as one unit. Watch for:
   - `HistoryRunRow`'s `SwipeToDismissBox` background is a fixed `RoundedCornerShape(20.dp)`; it
     must take the row's segment shape or square corners show mid-swipe.
   - The `LazyColumn` uses one `spacedBy(12.dp)` for everything; rows inside a day need
     `SegmentedGap` while headers, hero card, range selector and banner keep their spacing.
   - Removing a run changes its neighbours' index/count, so their corners change. Expected, but
     the change at a day's first/last row should animate, not jump.
   - Keep the long-press `DropdownMenu`, the `×N` repeat pill, the "not on device" error line and
     the stable row keys (`HistoryRun.key`, needed by `animateItem`) as they are.

3. **Deduplicate the hand-rolled helpers.** Same corner logic copied in
   `DownloadsScreen.kt` (`segmentedShape`), `SearchScreen.kt` (`getSegmentedShape`) and
   `ArtistScreen.kt` (`getSegmentedShape`). Move them to the library API.

4. **Single-choice pickers -> the selectable overload** (`selected` + `onClick`, check in
   `trailingContent`). `RadioButton` lists in `SettingsScreen.kt` and `BackupScreen.kt`
   (and one in `PlaylistImportScreen.kt`). This is what OpenStream uses for quality/captions.

5. **Settings pages - biggest, most on-brand.** `SettingsCard` wraps rows in one surface with
   `SettingsDivider` between them; segmented rows are the Android 16 Settings look. Costly because
   `SettingsCard` takes arbitrary content, so every page would need each row's index/count.
   `SettingsRow` / `SettingsToggleRow` / `SettingsHubRow` are shared, which helps. Keep the
   long-press explanation (`LocalSettingsInfoSink`) and the settings search index untouched.

6. **Player option sheets.** `OptionGroup` + `OptionRowDivider` in `ui/player/PlayerOptionRows.kt`
   is the same card-with-dividers shape as Settings. Sheets must still scroll.

### Where not to use them

- **Video history (`ui/video/VideoHistoryScreen.kt`)** and other video lists: rows are large
  thumbnail `VideoCard`s in the user's chosen list/grid layout. Segmented items are for text-first
  rows.
- Artwork shelves on Home and queue rows (`QueueReorder` has its own drag/key contract).
