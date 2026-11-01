# VoiceDeck — Stage 10, Phase 1 implementation

## What changed
- Added `ContactNameMatcher.java`, a local-only matcher over the contact list already loaded by VoiceDeck.
- The matcher normalizes case, accents, punctuation and whitespace.
- It detects a complete contact name/alias appearing as a phrase anywhere in the spoken sentence.
- It also matches individual name tokens (first name or surname).
- It returns exactly one of `MATCH_FOUND`, `MULTIPLE_MATCHES`, or `NO_MATCH`.
- A complete multi-token name/alias has priority over a weaker single-token overlap.
- `MainActivity.loadContacts()` rebuilds the local matcher after reading device contacts.
- `MainActivity.findContact()` now routes the three outcomes into the existing contact card and chooser.
- Existing speech recognition, card UI, call/WhatsApp actions and navigation are otherwise unchanged.

## Privacy and dependencies
- No AI or network request is used by this phase.
- No new dependency or Android permission is introduced.
- Contact names remain on-device.
- `ContactNameMatcher` accepts an optional `Map<Long, List<String>>` of locally configured aliases. The alias management UI/persistence is not included in this first implementation increment; pass an empty map until that is added.

## Test performed
The matcher class was compiled with `javac` against a lightweight `ContactItem` test stub and exercised with:
- first-name match inside a sentence
- full-name match inside a sentence
- surname ambiguity
- first-name ambiguity
- unmatched descriptive request
- configured alias match
- accented-name normalization
- empty input

All of those matcher-level tests passed.

## Not verified
A full Android/Gradle build was not run. The supplied ZIP does not contain `gradlew` and this environment does not have a system `gradle` executable. Please build/install on the development machine before treating the Android integration as verified.

## Files
- `app/src/main/java/com/voicedeck/app/MainActivity.java` — updated
- `app/src/main/java/com/voicedeck/app/ContactNameMatcher.java` — new


## Matching safeguards update — 2026-11-01

- Complete contact-name and alias phrases are now checked against the original normalized recognition tokens before any command/greeting filtering occurs.
- The command/greeting filter is applied only to partial first-name/surname token matching. This preserves complete names such as “Hello Tunes” while preventing a greeting-only “hello” result from selecting that multi-word contact.
- A single-word contact named “Hello” remains matchable as a complete phrase.
- If filtering removes every candidate token and no complete phrase matched, the result is `NO_MATCH`.
- Existing `MATCH_FOUND`, `MULTIPLE_MATCHES`, and `NO_MATCH` outcomes remain unchanged.
- Added 15 JVM regression tests in `app/src/test/java/com/voicedeck/app/ContactNameMatcherTest.java`.
- Removed full and partial recognition transcript writes to Logcat.
- JUnit 4.13.2 is added as a test-only dependency; no runtime dependency or Android permission was added.
- The 15 test methods compiled and passed using a local JVM harness with lightweight Android/JUnit stubs. The Android Gradle test task and full app build remain unverified because this source archive has no Gradle wrapper and no system Gradle is installed in the validation environment.


## Dictionary-driven filtering integration — Milestone 4

- Added `SpeechCommandFilter`, a pure-Java phrase/token filter that receives terms from the local dictionary and has no Room dependency.
- Added `SpeechDictionaryDefaults` with starter command, greeting, and filler entries for English (`en-IN`), Hindi (`hi-IN`), and Marathi (`mr-IN`).
- `MainActivity` seeds defaults using conflict-ignore inserts, loads enabled entries for the selected language through `SpeechDictionaryRepository`, and reloads them when the language changes.
- Dictionary loading and database access run on a background executor. A completed search is held until dictionary loading completes; a bundled in-memory fallback is used if database access fails.
- Removed the hardcoded command-word set from `ContactNameMatcher`. Complete contact-name/alias matching still runs against the original recognized phrase before dictionary filtering is applied to partial token matching.
- Updated normalization to preserve Devanagari combining vowel marks while continuing to fold Latin accents.
- Added JVM tests for multi-word phrases, token boundaries, accent handling, Devanagari, language defaults, and disabled dictionary entries.
- The available local JVM harness passed 25 unit-test methods. Full Android Gradle build, Room instrumentation tests, and physical-device speech testing remain pending because this environment has no Gradle executable or Android SDK.
