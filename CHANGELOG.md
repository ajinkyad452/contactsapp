# VoiceDeck Changelog

## Unreleased
- Fix Manage aliases scrolling by allowing its full row content to determine the height inside the existing outer ScrollView.
- Generate contact initials from Unicode character boundaries and ignore emoji/symbol-only words, preventing broken-character badges; share this behavior across contact/search/alias badges.
- Fix typed search ignoring saved aliases; use a language-scoped local search index for partial names, aliases, and formatted phone numbers, preserving all matching contacts.
- Refresh typed results after asynchronous alias loading and after alias changes; report alias-load failures while keeping ordinary contact search available.
- Apply the selected English, Hindi, or Marathi language to the app interface as well as speech recognition, immediately and across restarts.
- Move screen labels, dialogs, errors, form validation, vocabulary categories, and accessibility descriptions into translated Android resources; allow Settings labels to wrap.
- Replace design-only contact placeholders with runtime content and show the installed version on the localized Legal screen.
- Add typed-search regression tests and Android resource-selection tests for localization.
- Add `SpeechCommandFilter` as a Room-independent pure-Java component and replace the matcher's hardcoded command-word list with enabled dictionary terms.
- Seed the local speech dictionary with starter English, Hindi, and Marathi commands, greetings, and filler phrases using conflict-ignore inserts that preserve user edits.
- Load the selected language's enabled dictionary terms asynchronously through `SpeechDictionaryRepository`; queue a search until loading completes and use bundled in-memory defaults if database loading fails.
- Reload the speech dictionary when the selected recognition language changes.
- Preserve Devanagari vowel marks during speech/name tokenization while retaining Latin accent normalization.
- Add JVM tests for phrase filtering, token boundaries, multilingual normalization, dictionary defaults, and disabled entries.
- Add the Room 2.8.5 database foundation with a version-1 `speech_dictionary` table, DAO, and asynchronous repository boundary.
- Configure Java annotation processing and Room schema export; add instrumented DAO tests for language filtering and duplicate prevention.
- Check complete contact names and aliases against the original recognized phrase before filtering command/greeting words for partial-token matching.
- Add greeting terms to the partial-match filter and return `NO_MATCH` when filtering leaves no candidate tokens.
- Add JVM regression tests for full names, greeting collisions, partial matches, ambiguity, aliases, and normalization.
- Stop writing full and partial speech-recognition transcripts to Logcat.

This file tracks app-code versions and project-level changes. Device-specific findings that do not change app code are recorded in `DEBUGGING_LOG.md`.

## v1.4 — Microphone permission recovery
- Re-request the VoiceDeck microphone runtime permission when the user starts voice search and permission is missing.
- Offer an app-settings shortcut after denial.
- Explain when the configured speech-recognition provider reports insufficient microphone permissions even though VoiceDeck itself has permission.

## v1.3 — Recognition lifecycle and card navigation
- Explicitly cancel and destroy each speech recognizer session after results or errors; create a fresh recognizer for the next session.
- Add a visible “Back to contact card” control on the details side.
- Android system Back returns from the details side to the contact-card front.

## v1.2 — Speech error diagnostics
- Map speech-recognition error codes to more specific user-facing explanations.

## v1.1 — Language default and calling behavior
- Set English (`en-IN`) as the default speech language; retain Marathi (`mr-IN`) and Hindi (`hi-IN`) as selectable options.
- Use direct calling after `CALL_PHONE` permission is granted, with dialler fallback if permission is denied.

## Initial MVP
- Native Java Android implementation of the frozen VoiceDeck UI v1.0 and voice-first contact lookup flow.


## Unreleased — Milestone 5

- Added a Settings vocabulary manager for English, Hindi, and Marathi.
- Added local custom words/phrases, category selection, enable/disable controls, and custom-entry removal.
- Protected essential built-in `call` / `कॉल` commands from accidental disabling and prevented deletion of bundled defaults.
- Added restore-defaults behavior that preserves custom entries.
- Added a non-destructive Room migration from database version 1 to 2 for default/protected metadata.

## Unreleased — Milestone 6A: contact alias storage

- Add the `contact_aliases` Room entity, DAO, and asynchronous repository.
- Store aliases against Android Contacts Provider contact IDs; do not duplicate contact records.
- Support multiple aliases per contact and allow the same alias to belong to multiple contacts so matching can report ambiguity rather than choosing arbitrarily.
- Scope alias uniqueness by contact ID, normalized alias, and language tag; the same normalized alias may be used by different contacts or in different recognition languages.
- Add a non-destructive Room migration from database version 2 to 3. Existing speech dictionary rows and user settings are untouched.
- Add instrumented DAO tests for multiple aliases, duplicate handling, language scoping, ambiguity, deletion, and migration preservation.
