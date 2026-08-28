# AGENTS.md

**`CLAUDE.md` in the repository root is the source of truth for every coding agent working here. Read it first, and treat it as authoritative over this file.**

It carries the operating rules, the architecture, the invariants, the silent failure modes, the settled decisions that should not be re-litigated, the InnerTube probe-first workflow, and the docs/issue-tracking discipline. Everything an agent needs to work in this repository is there.

This file exists for tools that look for `AGENTS.md` by convention. It is deliberately **not** a second copy of that content. It used to be one - a near-duplicate that drifted out of step, which is the same staleness failure the project already learned from with the master issue table (see `CLAUDE.md` §5) - so it was reduced to this pointer plus the one appendix that lives nowhere else.

If you are about to add project knowledge here, add it to `CLAUDE.md` instead.

## Quick facts

- **Koda**, repo dir `TheMusicApp`, package `com.ivor.ivormusic`. Android music and video player over YouTube Music, Kotlin + Compose + Material 3 Expressive, no official API keys.
- Build commands, the test suite, emulator setup and the desugaring constraint: `CLAUDE.md` §6.
- **Never run a `gradlew` build unless the user explicitly asks.**
- **Never add AI attribution to a git commit** - no `Co-Authored-By: Claude`, no "Generated with" footer, no session link.
- Current SDK is at `E:\Android\Sdk`. `compileSdk = 37`, `targetSdk = 36`, `minSdk = 30`.

---

## Appendix: the older `E:\sdk` emulator setup

The only content unique to this file. It predates the `E:\Android\Sdk` setup described in `CLAUDE.md` §6 but still runs here; `musicapp_emulator` was built from it. Use the current setup unless you specifically need this one.

### Start the emulator from the E drive only

Use the SDK at `E:\sdk` and keep the Android user home on `E:\Android\.android`.

```powershell
$env:ANDROID_SDK_ROOT='E:\sdk'
$env:ANDROID_HOME='E:\sdk'
$env:ANDROID_USER_HOME='E:\Android\.android'
$env:ANDROID_AVD_HOME='E:\Android\.android\avd'
```

List available emulators:

```powershell
& 'E:\sdk\emulator\emulator.exe' -list-avds
```

Start the AVD created for this repo:

```powershell
Start-Process -FilePath 'E:\sdk\emulator\emulator.exe' -ArgumentList '-avd musicapp_emulator'
```

Then check it is visible with `adb devices`.

Notes:

- `flutter_emulator` is an `arm` AVD and this emulator build does not run it here.
- `musicapp_emulator` is the safe default because it was created from the `x86_64` system image already installed in `E:\sdk`.
- The old `.ini` entries in `E:\Android\.android\avd` are not enough on their own. The actual `.avd` folder must exist too.

### Recreate the E drive emulator if it is missing

```powershell
$env:ANDROID_SDK_ROOT='E:\sdk'
$env:ANDROID_HOME='E:\sdk'
$env:ANDROID_USER_HOME='E:\Android\.android'
$env:ANDROID_AVD_HOME='E:\Android\.android\avd'
New-Item -ItemType Directory -Force -Path $env:ANDROID_AVD_HOME | Out-Null
'' | & 'E:\sdk\cmdline-tools\latest\bin\avdmanager.bat' create avd -n musicapp_emulator -k 'system-images;android-33;google_apis_playstore;x86_64' -d pixel
```

### Testing on a newer API level

The project already compiles against a newer platform than it targets (`compileSdk = 37`, `targetSdk = 36`), so code changes are not the first blocker for testing on a recent Android - the requirement is a matching system image.

1. Install the system image for the API level you want into `E:\sdk\system-images`.
2. Create a new `x86_64` AVD on `E:` with that image.
3. Start it with the same E-drive environment variables above.
4. Install with `.\gradlew installDebug` (only when the user has asked for a build).

Example, for API 36:

```powershell
$env:ANDROID_SDK_ROOT='E:\sdk'
$env:ANDROID_HOME='E:\sdk'
$env:ANDROID_USER_HOME='E:\Android\.android'
$env:ANDROID_AVD_HOME='E:\Android\.android\avd'
'' | & 'E:\sdk\cmdline-tools\latest\bin\avdmanager.bat' create avd -n musicapp_android16 -k 'system-images;android-36;google_apis_playstore;x86_64' -d pixel
Start-Process -FilePath 'E:\sdk\emulator\emulator.exe' -ArgumentList '-avd musicapp_android16'
```

If the image is not installed yet, install it into `E:\sdk` first through the Android Studio SDK Manager or `sdkmanager`.
