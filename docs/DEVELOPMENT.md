# Development Guide

## Requirements
- JDK 17
- Android SDK with platform 35 and build tools installed
- Android Gradle Plugin 8.7.3 (downloaded by Gradle during sync)
- Gradle 8.9

## Open in Android Studio
1. Extract the project.
2. Open the `VoiceDeck` directory (the directory containing `settings.gradle`).
3. Set the Gradle JDK to JDK 17.
4. Allow project sync to resolve Android Gradle Plugin and dependencies.
5. Connect a device with USB debugging enabled or start an emulator.
6. Run the `app` configuration.

## Command-line build
Once the Gradle Wrapper has been generated and committed:
```bash
./gradlew :app:assembleDebug
```

The supplied archive does not contain Gradle Wrapper scripts or wrapper JAR. On a machine with Gradle 8.9 installed, generate them from the project root:
```bash
gradle wrapper --gradle-version 8.9 --distribution-type bin
```
Then commit `gradlew`, `gradlew.bat`, and `gradle/wrapper/`.

## Version metadata
The Android version is read from the root `version.properties` file. Update `VERSION_NAME` and increment `VERSION_CODE` when preparing a release. Do not increment the version code for ordinary documentation-only changes.

## Room database validation
Room uses Java annotation processing in this Java-only project. The processor exports versioned schemas to `app/schemas/`; commit generated schema JSON after the first successful build and after every schema change.

Run the Room DAO instrumentation tests on an emulator or connected Android device:
```bash
./gradlew :app:connectedDebugAndroidTest
```

Do not use destructive migration fallback for routine upgrades. Add and test an explicit migration whenever the Room database version changes.

## Useful checks
```bash
git status --short
git diff --check
```
Do not report a successful Android build unless `:app:assembleDebug` was actually run successfully.
