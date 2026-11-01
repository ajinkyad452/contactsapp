# ADR-004: Use Room for the future local data layer

- Status: Accepted — implementation planned, not yet started
- Date: 2026-11-01

## Context

VoiceDeck currently reads contact records from Android's Contacts Provider and keeps its current contact-matching data in memory. `ContactNameMatcher` accepts an optional alias map, but alias persistence and management are not implemented. Speech command words are currently maintained as a hardcoded set in the matcher.

The product roadmap calls for locally managed speech vocabulary and, later, additional structured data such as contact aliases, custom contact recordings, and voice preferences. Introducing unrelated persistence code in individual features would make future expansion harder to maintain.

## Decision

When persistent app-managed data is introduced, use **AndroidX Room**, backed by SQLite, as VoiceDeck's local database abstraction. Do not build the new data layer directly on `SQLiteOpenHelper`.

Keep database concerns modular and separate from the activity and speech-matching implementation:

- `data/local/VoiceDeckDatabase.java` — Room database declaration and singleton access.
- `data/local/entity/` — Room entity classes.
- `data/local/dao/` — data access objects.
- `data/repository/` — repositories that expose app-facing data operations.
- `speech/` — speech processing and command-filtering logic, independent of Room APIs.

Implement this incrementally. The first persisted feature will be a `speech_dictionary` table containing dictionary terms/phrases, their language, their category (for example command, greeting, or filler), and an active flag. A repository will provide the active entries to the speech layer. The speech layer must not query Room directly.

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

- Room will add a dependency and generated-code/build requirements when the first database increment is implemented; it is not added by this documentation decision alone.
- Entities, DAOs, repositories, migrations, and database tests will be introduced only as each feature needs them.
- Existing contact loading, matching outcomes, voice flow, UI, and call/WhatsApp actions must remain intact during the migration.
- The first implementation milestone is the Room foundation plus `speech_dictionary`; future tables must not be pre-created without an implemented requirement.

## Implementation status

No Room dependency, database class, entity, DAO, repository, or migration is present in the current v1.4.0 source snapshot. Track implementation in [the project roadmap](../ROADMAP.md).
