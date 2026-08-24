# Watchio Native Test Isolation

Phase 14.2C separates automated connected tests from the real manually configured debug app.

## Package Ids

- Real/manual debug app: `com.watchioiptv.nativeapp.debug`
- Isolated connected-test app: `com.watchioiptv.nativeapp.uitest`
- Isolated instrumentation package: `com.watchioiptv.nativeapp.uitest.test`

The real debug app keeps the normal APK path:

```powershell
app\build\outputs\apk\debug\app-debug.apk
```

The isolated test target uses:

```powershell
app\build\outputs\apk\uitest\app-uitest.apk
app\build\outputs\apk\androidTest\uitest\app-uitest-androidTest.apk
```

## Gradle Variant

`app/build.gradle.kts` defines a `uitest` build type cloned from `debug` with:

- `applicationIdSuffix = ".uitest"`
- `versionNameSuffix = "-uitest"`
- app label override `Watchio Test`
- `testBuildType = "uitest"`

Connected instrumentation now runs against `com.watchioiptv.nativeapp.uitest`, not the real `com.watchioiptv.nativeapp.debug` package.

## Commands

Use S22 scoped ADB before device work:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" devices -l
```

Run isolated connected tests:

```powershell
$env:ANDROID_SERIAL = "<S22_SERIAL>"
$env:WATCHIO_ANDROID_TEST_RUN_ID = "phase142c_uitest_s22_1"
.\gradlew.bat connectedUitestAndroidTest
```

Run twice for seal validation by changing `WATCHIO_ANDROID_TEST_RUN_ID`.

## Real Data Protection

Do not run uninstall or `pm clear` against the real debug package on S22. Those actions destroy the manually entered provider, DataStore settings, SecretStore credentials, Room cache, favourites, history, and EPG cache.

Manual installs of the real app must use replace-only:

```powershell
adb -s <S22_SERIAL> install -r app\build\outputs\apk\debug\app-debug.apk
```

The isolated `uitest` app has its own Android package sandbox, so Room, DataStore, SecretStore, and WorkManager state are separate from the real debug app.

Use the isolated app for fresh first-run/onboarding tests. Do not clear the real debug package to manufacture first-run state.

## Phase 14.2D.2
Strict Xtream-gate tests continue to run against com.watchioiptv.nativeapp.uitest through connectedUitestAndroidTest only. The real debug package and user provider data must not be cleared for onboarding tests.

