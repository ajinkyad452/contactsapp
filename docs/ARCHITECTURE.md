# Architecture

## Technology
- Native Android application
- Java source
- Android Gradle Plugin 8.7.3
- Java 17
- compileSdk 35, targetSdk 35, minSdk 26
- AndroidX Core 1.15.0
- AndroidX Room 2.8.5 (Java annotation processor)

## Package
`com.voicedeck.app`

## Main source files
- `app/src/main/java/com/voicedeck/app/MainActivity.java` — main UI, permissions, speech flow, contact lookup, card display and actions.
- `app/src/main/java/com/voicedeck/app/ContactItem.java` — contact data model.
- `app/src/main/java/com/voicedeck/app/ContactNameMatcher.java` — local phrase/partial-token contact matching.
- `app/src/main/java/com/voicedeck/app/speech/SpeechCommandFilter.java` — Room-independent dictionary-term filtering.
- `app/src/main/java/com/voicedeck/app/speech/SpeechDictionaryDefaults.java` — bundled starter vocabulary for supported languages.
- `app/src/main/AndroidManifest.xml` — app permissions, package visibility declarations, and launcher activity.
- `app/src/main/res/values/styles.xml` — application theme.

## Runtime flow
1. Android launches `MainActivity`.
2. VoiceDeck requests the Android runtime permissions needed for contacts and microphone features.
3. Speech recognition is delegated to the recognition service configured on the device.
4. The selected language's enabled speech dictionary entries are loaded locally through the repository.
5. `ContactNameMatcher` checks complete contact-name/alias phrases against the original recognized phrase; `SpeechCommandFilter` then filters dictionary terms only for partial-token matching.
6. The UI displays the selected contact and available details.
6. Call and WhatsApp actions hand off to Android intents / target applications.

## External dependencies and boundaries
- Speech recognition availability, language packs, network requirements, and microphone access are controlled partly by the device's configured recognition provider.
- VoiceDeck's `RECORD_AUDIO` permission is distinct from the recognition provider's own microphone permission.
- WhatsApp must be installed for its action to work.
- Contact field availability and formatting vary by device, account, and Contacts Provider implementation.

## Security principles
- Do not transmit or log contact contents, phone numbers, recognized transcripts, or other personal contact data.
- Do not add background microphone capture.
- Keep permission requests tied to an explicit user action.


## Local data architecture

VoiceDeck now has a Room database foundation under `data/local/`, with entities under `data/local/entity/`, DAOs under `data/local/dao/`, and repositories under `data/repository/`. The first and only table currently implemented is `speech_dictionary`. Its data is local to the device; contacts remain sourced from Android's Contacts Provider and are not copied into this database.

`VoiceDeckDatabase` is a process-wide singleton using the application context and the database file `voicedeck.db`. The database is version 1 and does not enable destructive migration fallback. Room schema export is configured to `app/schemas/`; generated schema JSON must be committed after the first successful Gradle build. Future schema changes must increment the database version and add explicit migrations.

`SpeechDictionaryRepository` exposes asynchronous operations and accepts caller-provided database and callback executors. Database work must remain off the main thread. Activities and speech-matching classes must not access DAOs directly.

`SpeechDictionaryDefaults` supplies the initial English (`en-IN`), Hindi (`hi-IN`), and Marathi (`mr-IN`) command, greeting, and filler vocabulary. On app startup, defaults are inserted with conflict-ignore semantics so existing user edits and disabled entries are preserved. The active language's enabled entries are then loaded asynchronously through the repository. If database initialization or loading fails, VoiceDeck uses the bundled terms in memory for that session; it does not block the UI or silently switch language.

`SpeechCommandFilter` is a pure-Java component. It accepts terms from the repository and removes exact token/phrase matches from recognized text. `ContactNameMatcher` checks complete contact names and aliases against the original phrase first; filtering is only used for weaker partial-token matching. This ordering protects names such as “Hello Tunes”. Normalization preserves Indic combining marks while continuing to fold Latin accents. The matcher has no direct Room dependency.

Room 2.8.5 is used with Java annotation processing. Room 3.x is not selected because its current toolchain is Kotlin/KSP-first and would require a Kotlin build-toolchain change for this Java-only app. See [ADR-004](decisions/ADR-004-room-local-data-layer.md) and the [development roadmap](ROADMAP.md).
