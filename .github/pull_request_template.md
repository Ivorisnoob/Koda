<!--
External contributions are temporarily paused until the project author has
completed the planned work in ROADMAP.md in full. Please do not open a new
external pull request during this pause. Bug reports and feature requests remain
welcome through the issue templates.
-->

## What this changes

<!-- A short description of the change and the problem it solves. Link the issue if there is one. -->

Fixes #

## Why

<!-- What was wrong, or what the feature is for. If the real problem turned out to be a layer below the reported symptom, say so here. -->

## States handled

<!--
For anything user-facing, which of these were considered and what happens in each.
Delete the ones that genuinely do not apply rather than leaving them blank.
-->

- Loading:
- Empty:
- Error / offline:
- Signed out:
- Long titles, missing thumbnail, no artwork colors:

## Deliberately not done

<!-- Anything left out on purpose, and why. This is useful information, not an admission. -->

## Screenshots or recording

<!-- Required for any UI change. Both light and dark if the change touches color or elevation. -->

## Checklist

- [ ] Builds with `./gradlew assembleDebug`
- [ ] Run on a device or emulator, not just compiled
- [ ] No hardcoded colors; everything resolves through `ColorScheme`
- [ ] Material 3 Expressive components used before standard M3, springs for touch-driven motion
- [ ] The signed-out path still works

### If this touches one of these areas

- [ ] **New setting** threaded through all five files (`ThemePreferences`, `ThemeViewModel`, `MainActivity`, `SettingsScreen`, `SettingsPages`) **and added to `buildSettingsSearchIndex`** in `SettingsSearch.kt`. A setting missing from the index is unfindable and there is no compile error to catch it
- [ ] **New player style** added in all four places: the `PlayerStyle` enum, the `<Name>PlayerContent.kt` composable, the `when` in `ExpandablePlayer.kt`, and `playerStyleCatalog`
- [ ] **InnerTube parser change** probed against a live response, not written from memory, with the month and year noted in the KDoc
- [ ] No cookie dumps, response JSON, or `.probe/` contents committed
