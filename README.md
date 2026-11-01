# VoiceDeck Android MVP

A native Java Android starter implementation for the frozen VoiceDeck UI v1.0.

## Environment
- Android Studio
- JDK 17
- Gradle 8.9
- Android Gradle Plugin 8.7.3
- compileSdk 35 / targetSdk 35 / minSdk 26

## Included in this MVP
- Voice-first launch flow with English (`en-IN`) by default, with Marathi (`mr-IN`) and Hindi (`hi-IN`) selectable in Settings.
- Runtime permission requests for Contacts and Microphone.
- Read-only access to contacts stored on the device.
- Contact matching with exact-name priority and partial-name fallback.
- Disambiguation dialog when multiple contacts match.
- Manual contact search fallback.
- Contact card front with photo, name, mobile number, Call and WhatsApp actions.
- Card back with email, company, birthday, address and Open in Google Contacts.
- Previous / Next navigation (no swipe navigation).
- Language selection in Settings.
- Call places a direct phone call after the user grants the Android Phone permission; if permission is denied, it opens the dialler instead. WhatsApp opens a chat link when the number is valid.

## Open and run
1. Extract the ZIP.
2. Open the `VoiceDeck` folder in Android Studio.
3. Set Gradle JDK to JDK 17 (Settings > Build, Execution, Deployment > Build Tools > Gradle).
4. Allow Android Studio to sync and download the Android Gradle Plugin/dependencies.
5. Connect an Android phone with USB debugging enabled, or use an emulator.
6. Run the `app` configuration.
7. Grant Contacts and Microphone permissions when prompted.

## Important testing notes
- Speech recognition is provided by the Android device's configured recognition service. Language availability and whether processing is online/offline depend on the device and installed speech service.
- Test Marathi and Hindi on the actual phones and accents expected in the beta.
- The Call button starts a direct cellular call after permission is granted. The WhatsApp button opens the chat, not a WhatsApp voice call. The user can start a call from within WhatsApp. WhatsApp must be installed and the phone number must include a correct country code. This first MVP assumes Indian numbers when a stored number has exactly 10 digits.
- Android Contacts Provider data can vary by device and account. Some fields (birthday, company, address) may be absent or stored differently.
- The current contact matching is deliberately conservative and basic. It does not yet implement fuzzy phonetic matching or mixed Marathi/Hindi code-switching.
- The UI is a functional first pass. We will refine dimensions, typography, photo crop and accessibility after device testing.
- This is an MVP project, not a Play Store release candidate. Review current Google Play permission, privacy disclosure and Data safety requirements before publishing.


## v1.2 diagnostic improvement
Speech recognition errors now show a specific message for silence/timeouts, missing microphone permission, network/service errors, busy recognizer, and unsupported/unavailable language. This helps distinguish a normal no-speech timeout from an actual speech-service problem.


## v1.3 reliability and navigation
- Speech recognition sessions are now explicitly released after results or errors, so the next manual attempt starts with a fresh recognizer instance.
- The card-back view now has a visible "Back to contact card" button.
- Android system Back also returns from the details side to the contact-card front.


## v1.4 microphone permission recovery
- If Android's runtime microphone permission is missing, VoiceDeck now requests it again when the user taps Start voice search.
- If permission is denied, VoiceDeck offers a shortcut to its Android App settings.
- If the speech-recognition service itself reports insufficient permissions while VoiceDeck's permission is granted, the app explains that the configured speech-recognition provider may also need microphone access.

## Project documentation and resources
- Product, UI, architecture, privacy, development, testing and release guidance: [`docs/`](docs/README.md).
- Design baseline, branding asset location and synthetic test contacts: [`resources/`](resources/README.md).
- Android app version metadata: [`version.properties`](version.properties).
- Decision records: [`docs/decisions/`](docs/decisions/README.md).

## Project history and device diagnostics
- App-code release history: see [`CHANGELOG.md`](CHANGELOG.md).
- Device-specific tests, logs, findings and pending verification: see [`DEBUGGING_LOG.md`](DEBUGGING_LOG.md).

## Repository conventions
- `AGENTS.md` contains repository-wide instructions for AI coding agents and human contributors.
- `.github/copilot-instructions.md` points GitHub Copilot to the same project guardrails.
- `.gitignore` excludes local Android/Gradle build output, IDE state, and signing material.
- `.editorconfig` keeps formatting consistent.
- `CONTRIBUTING.md` describes the change and validation workflow.
- `SECURITY.md` explains how to report security concerns safely.
- A Gradle Wrapper is not included in this archive because Gradle was not available in the packaging environment. On a machine with Gradle 8.9 installed, generate it from the project root with `gradle wrapper --gradle-version 8.9 --distribution-type bin`, then commit the generated `gradlew`, `gradlew.bat`, and `gradle/wrapper/` files.

## AI-assisted development
AI coding tools must follow `AGENTS.md`. Treat `CHANGELOG.md` and `DEBUGGING_LOG.md` as project history: append new findings and preserve prior entries. Never infer that a build or device test passed without running it.
