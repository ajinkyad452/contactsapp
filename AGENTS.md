# VoiceDeck — AI contributor instructions

These instructions apply to the whole repository. Read `README.md`, `CHANGELOG.md`, and `DEBUGGING_LOG.md` before making changes.

## Product guardrails
- VoiceDeck is a voice-first contact companion designed for older adults.
- The VoiceDeck UI v1.0 design is frozen. Do not redesign screens, change the flow, or add gestures unless the user explicitly requests it.
- Preserve the front contact card: photo, name, primary number, and exactly two side-by-side actions (Call and WhatsApp), with Previous/Next navigation.
- Preserve the details side and an explicit way to return to the front.
- Keep the app native Android and Java-first. Do not introduce Kotlin or cross-platform frameworks without explicit approval.
- Preserve supported language choices and defaults unless asked to change them.

## Engineering constraints
- Package / namespace: `com.voicedeck.app`.
- Use Java 17, Android Gradle Plugin 8.7.3, Gradle 8.9, compileSdk 35, minSdk 26, targetSdk 35 unless the user approves a version change.
- Keep dependencies minimal. Explain any new dependency and why it is needed.
- Request only permissions needed for a user-visible feature. Do not weaken Android privacy/security controls or silently change permission behavior.
- Treat device speech-recognition providers as external components; distinguish VoiceDeck permissions from provider permissions in diagnostics.
- Never log contact contents, phone numbers, transcripts, or other personal data.
- Do not claim a build or device test passed unless it was actually run and its result recorded.

## Required change hygiene
- Before editing, inspect the relevant source and current project history.
- Keep changes focused and avoid unrelated refactors.
- Update `CHANGELOG.md` for every user-visible app change. Use an Unreleased section until a release version is assigned.
- Update `DEBUGGING_LOG.md` for bug investigations, device-specific observations, reproduction steps, test results, and unresolved questions. Label hypotheses as hypotheses.
- Update `README.md` when setup, behavior, dependencies, permissions, or testing instructions change.
- Preserve historical entries; append corrections rather than silently rewriting history.
- Do not bump app `versionCode` / `versionName` unless preparing a release or explicitly asked.
- Never include signing keys, passwords, tokens, local SDK paths, or personal contact data in commits or archives.

## Validation
- Prefer building with the project Gradle wrapper once it is available: `./gradlew :app:assembleDebug`.
- If the wrapper or Android SDK is unavailable, say so and provide the exact validation not performed.
- For speech or permission changes, test on both Pixel 4a (Android 13 / API 33) and Pixel 7 Pro (Android 17 / API 37) when devices are available; record device, OS/API, steps, and outcome.
