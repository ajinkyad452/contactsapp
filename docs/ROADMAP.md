# VoiceDeck Development Roadmap

This roadmap separates the current implemented baseline from planned work. A listed future feature is not implemented until its code and tests are present in the repository.

## Current baseline — v1.4.0

- Native Android app written in Java.
- Android `SpeechRecognizer` integration with English (`en-IN`), Marathi (`mr-IN`) and Hindi (`hi-IN`) language options.
- Contacts loaded from Android's Contacts Provider.
- `ContactNameMatcher` performs local phrase and token matching and returns `MATCH_FOUND`, `MULTIPLE_MATCHES`, or `NO_MATCH`.
- The matcher currently removes a hardcoded set of command words before matching.
- Optional alias input is supported by the matcher constructor, but alias management and persistence are not implemented.
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

**Status:** Planned

- [ ] Add a compatible AndroidX Room dependency and required annotation processing configuration for this Java project.
- [ ] Add `VoiceDeckDatabase`, entity/DAO packages, and repository package.
- [ ] Implement only the initial `speech_dictionary` entity and DAO.
- [ ] Add a versioned schema and migration strategy; test database creation and migration behavior.
- [ ] Keep database operations off the main thread and keep database access out of activities and speech-matching classes.
- [ ] Do not change the existing UI, permissions, contact source, or call/WhatsApp behavior.

### Milestone 4 — Integrate dictionary-driven speech filtering

**Status:** Planned

- [ ] Seed or otherwise provide the initial local dictionary for supported languages.
- [ ] Expose active dictionary entries through `SpeechDictionaryRepository`.
- [ ] Add a `SpeechCommandFilter` / speech-processing boundary that does not depend directly on Room.
- [ ] Replace the hardcoded command-word set only after dictionary loading, empty-dictionary behavior, and fallback behavior are tested.
- [ ] Re-run matching regression tests, including the “Hello Tunes” safeguard.

## Later feature areas

| Feature | Planned data approach | Status |
|---|---|---|
| Contact aliases | `contact_aliases`, referencing Android Contacts Provider IDs | Planned |
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
