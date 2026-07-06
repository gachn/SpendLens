# Building & exporting the APK

## Prerequisites

- JDK 17+ (project is configured to use Android Studio's bundled JBR — see `gradle.properties`)
- Android SDK, with `sdk.dir` set in `local.properties` (created automatically by Android Studio
  on first sync)

## Debug build

```sh
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk` — signed with the auto-generated Android
debug key. Installable on a device/emulator for testing, not for distribution.

## Release build (signed, exportable APK)

```sh
./gradlew assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk` — minified, resource-shrunk, and signed
if a release keystore is configured (see below). Without a keystore, Gradle produces
`app-release-unsigned.apk`, which cannot be installed until it's signed.

Verify signing on the output APK:

```sh
$ANDROID_HOME/build-tools/<version>/apksigner verify --print-certs \
  app/build/outputs/apk/release/app-release.apk
```

## One-time release signing setup

The release `signingConfig` in [`app/build.gradle.kts`](../app/build.gradle.kts) reads its
keystore path and credentials from `local.properties` (gitignored, never committed). If those
keys are absent, the release build type has no `signingConfig` and Gradle emits an unsigned APK
instead of failing.

To set it up:

1. Generate a keystore (PKCS12 requires the store and key password to match):

   ```sh
   keytool -genkeypair -v \
     -keystore keystore/release.keystore.jks \
     -alias spendlens-release \
     -keyalg RSA -keysize 2048 -validity 10950 \
     -storepass <password> -keypass <same password>
   ```

   `keystore/` and `*.jks` are already gitignored — never commit the keystore file.

2. Add these lines to `local.properties`:

   ```properties
   RELEASE_KEYSTORE_PATH=keystore/release.keystore.jks
   RELEASE_KEYSTORE_ALIAS=spendlens-release
   RELEASE_KEYSTORE_PASSWORD=<password>
   RELEASE_KEY_PASSWORD=<same password>
   ```

3. Back up `keystore/release.keystore.jks` and the passwords somewhere safe outside the repo —
   losing them means future release builds can't be updated in-place on a device that already
   has this APK installed (Android requires matching signatures for updates).

This repo already has a keystore generated at `keystore/release.keystore.jks` with credentials
in `local.properties`, so `./gradlew assembleRelease` produces a signed, installable APK out of
the box.
