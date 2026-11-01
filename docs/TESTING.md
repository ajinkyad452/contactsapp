# Testing Guide

## Test devices
The project history references:
- Pixel 4a — Android 13 / API 33
- Pixel 7 Pro — Android 17 / API 37

Record exact device, Android version/API, app version, steps, and result for each test. Device availability is not assumed.

## Local JVM unit tests
Run the contact-matching regression suite from the project root:

```bash
./gradlew :app:testDebugUnitTest
```

The JVM suites are in `app/src/test/java/com/voicedeck/app/` and cover complete-name-before-filtering behavior, greeting collisions, partial matches, ambiguity, aliases, phrase/token filtering, English/Hindi/Marathi dictionary defaults, disabled entries, empty input, and Latin/Devanagari normalization.

## Room database instrumentation tests
Run on an emulator or connected Android device:

```bash
./gradlew :app:connectedDebugAndroidTest
```

`SpeechDictionaryDaoTest` checks language filtering, enabled/disabled entries, and duplicate normalized-term protection using an in-memory Room database. The test database permits main-thread queries only inside the test; production database operations must use the repository's background executor.

## Functional checklist
- [ ] First launch and permission explanation.
- [ ] Contacts permission granted; contacts load.
- [ ] Contacts permission denied; app remains usable and explains the limitation.
- [ ] Start voice search and grant microphone permission.
- [ ] English (`en-IN`) recognition and dictionary filtering.
- [ ] Hindi (`hi-IN`) recognition and dictionary filtering.
- [ ] Marathi (`mr-IN`) recognition and dictionary filtering.
- [ ] Marathi (`mr-IN`) recognition.
- [ ] Hindi (`hi-IN`) recognition.
- [ ] Silence, timeout, and speech-service error messages.
- [ ] Exact-name match, including names containing command/greeting words (for example, “Hello Tunes”).
- [ ] Partial-name match.
- [ ] Greeting-only input does not match a multi-word contact just because one token overlaps.
- [ ] Multiple-match disambiguation.
- [ ] Manual search fallback.
- [ ] Contact without a photo.
- [ ] Contact with missing email/company/birthday/address.
- [ ] Previous / Next navigation.
- [ ] Details-side return button and Android system Back.
- [ ] Call permission granted and direct call handoff.
- [ ] Call permission denied and dialler fallback.
- [ ] WhatsApp handoff with valid number and WhatsApp installed.
- [ ] WhatsApp unavailable / invalid number handling.
- [ ] Large text / display scaling and touch target usability.

## Speech provider troubleshooting matrix
Test VoiceDeck's microphone permission separately from the configured recognition provider's microphone permission. See `DEBUGGING_LOG.md` and [Troubleshooting](TROUBLESHOOTING.md).

## Privacy checks
- [ ] No contact names, numbers, transcripts, or contact fields appear in logs.
- [ ] No background microphone capture.
- [ ] No contact data is sent to a VoiceDeck server.
- [ ] Permission denial does not cause a crash.


## Milestone 5 — Dictionary management checks

On a device with a v1 VoiceDeck database, install the updated build over the existing app (do not uninstall) and verify the Room 1-to-2 migration preserves all existing dictionary rows. In Settings > Manage vocabulary, verify each supported language can be selected; ordinary terms can be enabled/disabled; essential `call` / `कॉल` entries remain enabled; custom terms can be added and removed; duplicate terms in the same language/category are rejected; and Restore defaults re-enables bundled terms without removing custom terms. Confirm changes to the currently selected recognition language affect the next voice search.
