# Product Brief

## Product
VoiceDeck is a native Android, voice-first contact companion. It presents device contacts as a simple, readable contact card rather than requiring users to navigate a conventional contact list.

## Intended audience
The primary audience is older adults, including people around 60–70 years old. The product should also remain useful to anyone who prefers voice-led contact lookup.

## Problem
Finding a person in a long contact list and deciding which action to take can be difficult when the interface is dense or text-heavy.

## Product principles
- Voice search is the primary entry point.
- Keep the interface calm, legible, and predictable.
- Use familiar actions and clear labels.
- Provide manual search when voice recognition is unavailable or unsuccessful.
- Keep contact information on-device and read it only to deliver the requested contact experience.
- Avoid hidden gestures and unnecessary choices.

## Current MVP scope
- Read contacts from the Android Contacts Provider.
- Search by speech, with English (India) as the default locale and Marathi/Hindi selectable.
- Match exact names before partial-name matches.
- Ask the user to choose when multiple contacts match.
- Provide manual search fallback.
- Show a front contact card and a details side.
- Launch phone calling and WhatsApp chat actions.

## Out of scope for this MVP
- Cloud contact storage or contact synchronization.
- Uploading contact data or voice transcripts to a VoiceDeck server.
- Advanced phonetic/fuzzy matching and robust code-switching.
- A Play Store production release without a separate privacy, policy, accessibility, and device-validation review.
