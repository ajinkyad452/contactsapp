# Architecture

## Technology
- Native Android application
- Java source
- Android Gradle Plugin 8.7.3
- Java 17
- compileSdk 35, targetSdk 35, minSdk 26
- AndroidX Core dependency

## Package
`com.voicedeck.app`

## Main source files
- `app/src/main/java/com/voicedeck/app/MainActivity.java` — main UI, permissions, speech flow, contact lookup, card display and actions.
- `app/src/main/java/com/voicedeck/app/ContactItem.java` — contact data model.
- `app/src/main/AndroidManifest.xml` — app permissions, package visibility declarations, and launcher activity.
- `app/src/main/res/values/styles.xml` — application theme.

## Runtime flow
1. Android launches `MainActivity`.
2. VoiceDeck requests the Android runtime permissions needed for contacts and microphone features.
3. Speech recognition is delegated to the recognition service configured on the device.
4. The recognized phrase is matched against contacts read from the Android Contacts Provider.
5. The UI displays the selected contact and available details.
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


## Planned local data architecture (not implemented)

The current v1.4.0 app does not include a local application database. Contacts continue to come from Android's Contacts Provider, and the current `ContactNameMatcher` accepts an optional in-memory alias map. It checks complete contact-name/alias phrases against the original recognized phrase, then uses a hardcoded command/greeting filter only for partial-token matching. The filter remains temporary until the planned Room-backed speech dictionary is implemented.

For future app-managed persistent data, the accepted direction is AndroidX Room (SQLite underneath), with database entities and DAOs under `data/local/`, repositories under `data/repository/`, and speech processing under `speech/`. The first database increment is limited to a `speech_dictionary` table. Later candidates include `contact_aliases` (provider IDs only), `contact_recordings`, and `voice_preferences`; `speech_patterns` and `recognition_history` remain conditional on a clear need.

The speech layer must not access Room directly. Database operations must run off the main thread, schema changes must use versioned migrations, and all app-managed data remains local. Full legitimate contact names must be checked before dictionary filtering so that a greeting word which is also part of a contact name (for example, “Hello Tunes”) is not stripped before matching.

This is a future architecture plan, not a description of existing classes or dependencies. No Room dependency or database implementation is included in the current source. See [ADR-004](decisions/ADR-004-room-local-data-layer.md) and the [development roadmap](ROADMAP.md) for the decision and milestones.
