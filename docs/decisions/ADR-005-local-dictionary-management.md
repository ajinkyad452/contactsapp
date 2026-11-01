# ADR-005: Manage speech vocabulary locally

- Status: Accepted
- Date: 2026-11-01

## Context

VoiceDeck's Room-backed `speech_dictionary` was seeded with bundled vocabulary, but users could not adapt it to their own phrasing. The app needs a safe local way to customize words without introducing cloud storage or changing contact matching responsibilities.

## Decision

Add a Settings vocabulary manager backed by `SpeechDictionaryRepository`. Support language-specific browsing, enabling/disabling ordinary entries, adding custom words and short phrases, removing custom entries, and restoring bundled defaults. Keep all data on-device.

Bundled entries are marked `isDefault`; essential built-in commands are additionally marked `isProtected`. Default entries cannot be deleted, and protected commands cannot be disabled. Restore defaults re-seeds missing bundled entries and re-enables defaults for the selected language while retaining custom entries.

Room schema version 2 adds this metadata through a non-destructive 1-to-2 migration. Existing v1 rows are treated as bundled defaults because v1 only contained the shipped starter vocabulary.

## Consequences

- Users can personalize command, greeting, and filler vocabulary without editing source code.
- The speech filter remains a pure-Java component and consumes enabled terms from the repository.
- The Settings screen gains a vocabulary-management section; the frozen contact card remains unchanged.
- Future changes to the dictionary schema require explicit Room migrations and updated schema exports.
