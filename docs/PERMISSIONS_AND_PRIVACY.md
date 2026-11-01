# Permissions and Privacy

## Android permissions declared by the current manifest
| Permission | Purpose |
|---|---|
| `READ_CONTACTS` | Read device contacts for lookup and card display. |
| `RECORD_AUDIO` | Capture speech during an explicitly started voice-search session. |
| `CALL_PHONE` | Place a direct call after the user grants permission; the app can fall back to the dialler if permission is denied. |

## Recognition-provider permission
Android speech recognition is provided by a separate service selected/configured on the device. That provider may need its own microphone permission. Granting VoiceDeck microphone permission does not automatically grant microphone access to the provider.

A microphone permission does not mean VoiceDeck continuously records in the background. Voice capture should occur only during an active, user-initiated recognition session and remain subject to Android privacy indicators and controls.

## Data handling
- Contacts are read from the device for the requested feature.
- This source archive contains no VoiceDeck backend or cloud contact-sync implementation.
- Do not add analytics, telemetry, network uploads, or contact logging without an explicit product and privacy review.
- Do not include real contact data in test fixtures, screenshots, logs, issue reports, or repository commits.

## Before publishing
Review current Google Play permissions policy, Data safety declarations, privacy policy, and any applicable disclosure/consent requirements. The current MVP documentation is not a legal compliance certification.
