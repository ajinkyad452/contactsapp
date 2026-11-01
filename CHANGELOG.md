# VoiceDeck Changelog

## Unreleased
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
