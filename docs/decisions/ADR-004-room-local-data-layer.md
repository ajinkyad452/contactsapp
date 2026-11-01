# ADR-004: Use Room for the future local data layer

- Status: Accepted — Room foundation implemented; dictionary integration pending
- Date: 2026-11-01

## Context

VoiceDeck currently reads contact records from Android's Contacts Provider and keeps its current contact-matching data in memory. `ContactNameMatcher` accepts an optional alias map, but alias persistence and management are not implemented. Speech command words are currently maintained as a hardcoded set in the matcher.

The product roadmap calls for locally managed speech vocabulary and, later, additional structured data such as contact aliases, custom contact recordings, and voice preferences. Introducing unrelated persistence code in individual features would make future expansion harder to maintain.

## Decision

Use **AndroidX Room**, backed by SQLite, as VoiceDeck's local database abstraction. Do not build the data layer directly on `SQLiteOpenHelper`.

Keep database concerns modular and separate from the activity and speech-matching implementation:

- `data/local/VoiceDeckDatabase.java` — Room database declaration and singleton access.
- `data/local/entity/` — Room entity classes.
- `data/local/dao/` — data access objects.
- `data/repository/` — repositories that expose app-facing data operations.
- `speech/` — speech processing and command-filtering logic, independent of Room APIs.

The first implementation increment is now in place: Room 2.8.5, `VoiceDeckDatabase`, `SpeechDictionaryEntry`, `SpeechDictionaryDao`, and `SpeechDictionaryRepository`. The table stores terms/phrases, normalized terms, language tags, categories (command, greeting, or filler), and an enabled flag. The repository provides asynchronous access through caller-supplied executors. Dictionary seeding and speech-layer integration remain a separate milestone; the speech layer must not query Room directly.

## Planned data scope

| Table / data set | Purpose | Planned timing |
|---|---|---|
| `speech_dictionary` | Locally configurable commands, greetings, fillers and phrases, with language/category and active state | First database increment |
| `contact_aliases` | User-defined aliases keyed to Android Contacts Provider IDs | Later; do not duplicate contact records |
| `contact_recordings` | User-created recordings associated with a contact | Later; align with the custom contact-recording feature |
| `voice_preferences` | Persist voice-related preferences such as selected language | Later, when persistence is needed |
| `speech_patterns` | Optional user-defined vocabulary or phrase patterns beyond the basic dictionary | Later, only with a defined use case |
| `recognition_history` | Optional diagnostics | Not committed; add only if justified by a clear need and privacy review |

These are planned concepts, not tables that exist in the current app. Names and schemas may be refined before implementation.

## Matching and privacy constraints

- Preserve Android's Contacts Provider as the source of truth for contact records. `contact_aliases` must reference provider contact IDs rather than copy names, phone numbers, or other contact fields into a second address book.
- Keep the database local to the device. This decision does not introduce cloud sync, analytics, AI services, or network access.
- Keep database reads/writes off the main thread.
- Use explicit, versioned Room schema migrations for future database upgrades; do not use destructive migration as a routine upgrade strategy.
- Keep the speech-processing layer independent of Room so it can be tested without Android database setup.
- A legitimate complete contact name must be checked before dictionary terms are removed from a recognized sentence. For example, a contact named “Hello Tunes” must not become unmatchable because “hello” is configured as a greeting. If filtering leaves no meaningful candidate tokens, matching must return `NO_MATCH` rather than guess.
- Do not log contact contents, phone numbers, recognized transcripts, recordings, or other personal data.

## Consequences

- Room adds a runtime dependency and annotation-processor-generated code to the Android build.
- The initial version-1 schema contains only `speech_dictionary`; additional entities will be introduced only when their feature needs them.
- The exported schema must be committed, and future schema versions must include explicit migrations and migration tests.
- Existing contact loading, matching outcomes, voice flow, UI, and call/WhatsApp actions must remain intact during the migration.
- The first implementation milestone is the Room foundation plus `speech_dictionary`; future tables must not be pre-created without an implemented requirement.

## Implementation status

- [x] Room 2.8.5 runtime and Java annotation processor configured.
- [x] Version-1 `VoiceDeckDatabase` singleton added; destructive migration fallback is not enabled.
- [x] Initial `speech_dictionary` entity and DAO added.
- [x] Asynchronous repository boundary added with injected executors.
- [x] Instrumented DAO tests added for language/enabled filtering and duplicate prevention.
- [ ] Run Android Gradle build and instrumented tests; commit generated schema JSON under `app/schemas/`.
- [ ] Seed dictionary entries and integrate the speech filter in the next milestone.

Room 3.x was not selected because the current Room 3 toolchain requires Kotlin/KSP and would expand this Java-only project's build toolchain. Room 2.8.5 documents Java `annotationProcessor` support. Track progress in [the project roadmap](../ROADMAP.md).
