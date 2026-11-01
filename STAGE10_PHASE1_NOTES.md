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
