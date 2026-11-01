# ADR-002: Delegate speech recognition to Android

- Status: Accepted
- Date: 2026-11-01

## Decision
Use Android's speech-recognition APIs and the recognition service configured on the device rather than embedding a separate speech engine in the MVP.

## Rationale
This keeps the app implementation lightweight and uses the device's configured recognition provider and language support.

## Consequences
Recognition reliability, supported locales, offline availability, and provider permissions can vary by device. Diagnostics must distinguish VoiceDeck's own microphone permission from the provider's permission. A manual search fallback remains necessary.
