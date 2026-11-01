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
