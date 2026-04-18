# Agent Notes

## Start Android Emulator From E Drive Only

Use the SDK from `E:\sdk` and keep the Android user home on `E:\Android\.android`.

PowerShell:

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

Start the working emulator created for this repo:

```powershell
Start-Process -FilePath 'E:\sdk\emulator\emulator.exe' -ArgumentList '-avd musicapp_emulator'
```

Check whether it is visible:

```powershell
adb devices
```

Notes:

- `flutter_emulator` is an `arm` AVD and this emulator build does not run it here.
- `musicapp_emulator` is the safe default because it was created from the `x86_64` system image already installed in `E:\sdk`.
- The old `.ini` entries in `E:\Android\.android\avd` are not enough by themselves. The actual `.avd` folder must exist too.

## Create The E Drive Emulator Again If Needed

If `musicapp_emulator` is missing, recreate it with:

```powershell
$env:ANDROID_SDK_ROOT='E:\sdk'
$env:ANDROID_HOME='E:\sdk'
$env:ANDROID_USER_HOME='E:\Android\.android'
$env:ANDROID_AVD_HOME='E:\Android\.android\avd'
New-Item -ItemType Directory -Force -Path $env:ANDROID_AVD_HOME | Out-Null
'' | & 'E:\sdk\cmdline-tools\latest\bin\avdmanager.bat' create avd -n musicapp_emulator -k 'system-images;android-33;google_apis_playstore;x86_64' -d pixel
```

## What If I Want To Test On Android 16

This project is already configured for Android 16 level APIs:

- `app/build.gradle.kts` uses `compileSdk = 36`
- `app/build.gradle.kts` uses `targetSdk = 36`

That means code changes for Android 16 are not the first blocker. The main requirement is an Android 16 emulator image.

If you want to test specifically on Android 16:

1. Install a system image for API 36 in `E:\sdk\system-images`.
2. Create a new `x86_64` AVD on `E:` with that API 36 image.
3. Start that AVD with the same E-drive environment variables shown above.
4. Run the app with `.\gradlew installDebug` or from Android Studio after selecting that emulator.

Example AVD creation command after the API 36 image is installed:

```powershell
$env:ANDROID_SDK_ROOT='E:\sdk'
$env:ANDROID_HOME='E:\sdk'
$env:ANDROID_USER_HOME='E:\Android\.android'
$env:ANDROID_AVD_HOME='E:\Android\.android\avd'
'' | & 'E:\sdk\cmdline-tools\latest\bin\avdmanager.bat' create avd -n musicapp_android16 -k 'system-images;android-36;google_apis_playstore;x86_64' -d pixel
```

Then start it with:

```powershell
Start-Process -FilePath 'E:\sdk\emulator\emulator.exe' -ArgumentList '-avd musicapp_android16'
```

If API 36 is not installed yet, install it into `E:\sdk` first through Android Studio SDK Manager or `sdkmanager`.
