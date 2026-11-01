# VoiceDeck Development Roadmap

This roadmap separates the current implemented baseline from planned work. A listed future feature is not implemented until its code and tests are present in the repository.

## Current baseline — v1.4.0

- Native Android app written in Java.
- Android `SpeechRecognizer` integration with English (`en-IN`), Marathi (`mr-IN`) and Hindi (`hi-IN`) language options.
- Contacts loaded from Android's Contacts Provider.
- `ContactNameMatcher` performs local phrase and token matching and returns `MATCH_FOUND`, `MULTIPLE_MATCHES`, or `NO_MATCH`.
- `ContactNameMatcher` checks complete contact-name/alias phrases before applying a supplied speech filter to weaker partial-token matching.
- The Room-backed `speech_dictionary` is seeded with starter command, greeting, and filler terms for English (`en-IN`), Hindi (`hi-IN`), and Marathi (`mr-IN`).
- `SpeechCommandFilter` is a pure-Java boundary; it receives enabled terms from the repository and has no direct Room dependency.
- Contact aliases are stored locally in Room and loaded for the active speech language; the matcher receives only aliases whose Android Contacts Provider IDs are present in the current contact list. Alias creation/management UI is not implemented yet.
- Voice-first contact card, Call / WhatsApp actions, Previous / Next navigation, manual search, and existing permission recovery remain the frozen product baseline.

## Next milestones

### Milestone 1 — Record the local data architecture

**Status:** Completed in this documentation update

- [x] Choose AndroidX Room (SQLite underneath) for future app-managed persistent data.
- [x] Define the separation between local database, repositories, and speech processing.
- [x] Define the first table (`speech_dictionary`) and identify later data sets without creating them prematurely.
- [x] Record privacy, migration, threading, and contact-name matching safeguards.
- [x] Add ADR-004 and document the current implementation gap.

### Milestone 2 — Harden contact matching

**Status:** Implemented in source; Android Gradle/device validation pending

- [x] Check legitimate complete contact names/aliases against the original recognized phrase before filtering command or greeting words.
- [x] Prevent greeting words inside real contact names (for example, “Hello Tunes”) from being stripped before a full-name match.
- [x] Return `NO_MATCH` when filtering leaves no meaningful partial-match candidate tokens and no complete phrase matches.
- [x] Preserve the existing three result states and multiple-match disambiguation behavior.
- [x] Add focused JVM regression tests for command words, greetings, full names, partial names, ambiguity, empty input, aliases, and false-positive cases.
- [x] Remove full and partial speech-recognition transcript logging from Logcat.
- [ ] Run `:app:testDebugUnitTest` and a full Android build on the development machine.
- [ ] Verify voice behavior on Pixel 4a (Android 13 / API 33) and Pixel 7 Pro (Android 17 / API 37).
- [x] Keep speech-recognition provider changes and AI intent understanding out of this milestone.

### Milestone 3 — Introduce the Room foundation

**Status:** Implemented in source; Android build, schema generation, and device test pending

- [x] Add AndroidX Room 2.8.5 and Java annotation processing.
- [x] Add `VoiceDeckDatabase`, entity/DAO packages, and repository package.
- [x] Implement only the initial `speech_dictionary` entity and DAO.
- [x] Configure version 1 and Room schema export; no destructive migration fallback.
- [x] Add asynchronous repository operations with injected executors.
- [x] Add instrumented DAO tests for language/enabled filtering and duplicate prevention.
- [ ] Run Android Gradle build and instrumented tests; commit generated schema JSON under `app/schemas/`.
- [ ] Verify app launch and existing voice/contact flows on supported devices.
- [x] Leave dictionary seeding and speech matching integration for Milestone 4.
- [x] Do not change the existing UI, permissions, contact source, or call/WhatsApp behavior.

### Milestone 4 — Integrate dictionary-driven speech filtering

**Status:** Implemented in source; Android build and device validation pending

- [x] Seed the local Room dictionary with starter English, Hindi, and Marathi commands, greetings, and filler terms.
- [x] Expose enabled language-specific entries through `SpeechDictionaryRepository` and load them asynchronously off the main thread.
- [x] Add `SpeechCommandFilter` as a pure-Java boundary with no Room or Android dependency.
- [x] Replace the matcher's hardcoded command-word set with dictionary terms supplied by `MainActivity`.
- [x] Preserve existing dictionary edits and disabled entries through idempotent `INSERT OR IGNORE` seeding.
- [x] Provide a bundled in-memory fallback if local database initialization/loading fails.
- [x] Queue a completed search while the dictionary is loading; process it after the dictionary is ready.
- [x] Reload enabled terms when the user changes the speech language.
- [x] Preserve full-name-before-filter matching, phrase filtering, and multilingual Devanagari vowel marks.
- [x] Add JVM tests for filter phrases, token boundaries, accents, Devanagari, language defaults, and disabled entries.
- [ ] Run `:app:testDebugUnitTest`, `:app:assembleDebug`, and `:app:connectedDebugAndroidTest` on the development machine.
- [ ] Verify English, Hindi, and Marathi speech on physical devices.

### Milestone 5 — Manage local speech vocabulary

**Status:** Implemented in source; Room migration, Android build, and physical-device validation pending

- [x] Add a vocabulary manager entry in Settings without changing the frozen contact-card UI.
- [x] Browse entries by English, Hindi, or Marathi dictionary language, including disabled entries.
- [x] Enable/disable ordinary default and custom terms; reload the active speech filter after changes.
- [x] Add custom words and short phrases with category and duplicate validation.
- [x] Remove custom entries only; bundled defaults cannot be deleted.
- [x] Protect essential `call` / `कॉल` commands from being disabled.
- [x] Restore bundled defaults for a language without deleting custom vocabulary.
- [x] Add Room 1-to-2 migration for default/protected metadata; preserve existing database rows.
- [x] Add DAO tests for protected commands, custom deletion, and restoring defaults.
- [ ] Run `:app:assembleDebug`, `:app:testDebugUnitTest`, and `:app:connectedAndroidTest`.
- [ ] Upgrade an existing v1 database on-device and verify all 56 existing terms survive migration.
- [ ] Verify adding, toggling, removing, and restoring vocabulary on a physical device.

### Milestone 6 — Contact alias foundation and matching

**Status:** 6A storage implemented and device-verified; 6B matching integration implemented in source, build/device validation pending

- [x] Add the `contact_aliases` Room entity, DAO, and asynchronous repository.
- [x] Add the non-destructive Room 2-to-3 migration.
- [x] Support multiple aliases for one contact and duplicate aliases across contacts.
- [x] Load aliases for the currently selected recognition language.
- [x] Build the in-memory matcher alias map from current Android Contacts Provider IDs only; stale IDs are ignored.
- [x] Queue a search until both the speech dictionary and alias-backed matcher are ready.
- [x] Rebuild the matcher when contacts are reloaded or the speech language changes.
- [x] Preserve ambiguity when an alias conflicts with another contact's actual one-word name.
- [x] Add JVM tests for multiple aliases, duplicate alias ambiguity, and stale contact IDs.
- [ ] Run `:app:testDebugUnitTest`, `:app:assembleDebug`, and `:app:connectedDebugAndroidTest`.
- [ ] Verify alias matches and duplicate-alias chooser behavior on a physical device after aliases can be added in Milestone 6C.

### Milestone 6C — Voice-led alias creation

**Status:** Implemented in source; build and physical-device validation pending

- [x] On a voice-search `NO_MATCH`, remove active-language command/greeting/filler terms to derive the proposed alias.
- [x] Ask the user to choose the intended contact from the existing Contacts Provider list.
- [x] Provide an in-dialog contact search to make large address books practical.
- [x] Ask for explicit confirmation before saving the alias.
- [x] Save aliases asynchronously through `ContactAliasRepository` into the existing Room table.
- [x] Store the active recognition language and normalized alias; do not modify Android contact records.
- [x] Reload the in-memory alias matcher after a successful save.
- [x] Keep manual-search no-match behavior unchanged; alias creation is offered only after voice search.
- [ ] Run `:app:testDebugUnitTest`, `:app:assembleDebug`, and `:app:connectedDebugAndroidTest`.
- [ ] On a physical device, test voice no-match → contact selection → confirmation → alias save → second voice search resolves the contact.
- [ ] Verify Cancel / No paths do not write data, duplicate alias for the same contact is handled safely, and the same alias on another contact remains ambiguous.

## Later feature areas

| Feature | Planned data approach | Status |
|---|---|---|
| Contact alias creation/management | Existing `contact_aliases` table; voice-led creation after `NO_MATCH` | Milestone 6C / 6D |
| Custom contact recordings | `contact_recordings`, associated with provider contact IDs | Planned |
| Voice preferences | `voice_preferences` | Planned |
| Advanced user-defined speech vocabulary/patterns | `speech_patterns`, if justified beyond the basic dictionary | Optional future work |
| Recognition history | Separate privacy-reviewed diagnostics design | Not committed |
| AI intent understanding | Separate Phase 2 design, only after Phase 1 limitations are measured | Future phase; no AI in Phase 1 |

## Guardrails for every milestone

- Preserve the frozen VoiceDeck UI and existing user-visible behavior unless explicitly approved.
- Keep contact data and app-managed speech data local; do not introduce a cloud address book.
- Do not duplicate contact records that already live in Android's Contacts Provider.
- Do not log contact details, phone numbers, recognized transcripts, or recordings.
- Keep Java 17 and the repository's current Android toolchain unless a change is explicitly approved.
- Update `CHANGELOG.md` for app behavior changes, `DEBUGGING_LOG.md` for investigations/device findings, and tests/documentation as appropriate.
- Never mark a build or device test as passed unless it was actually run.

## Related decision

See [ADR-004: Use Room for the future local data layer](decisions/ADR-004-room-local-data-layer.md).
