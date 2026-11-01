# Testing Guide

## Test devices
The project history references:
- Pixel 4a — Android 13 / API 33
- Pixel 7 Pro — Android 17 / API 37

Record exact device, Android version/API, app version, steps, and result for each test. Device availability is not assumed.

## Functional checklist
- [ ] First launch and permission explanation.
- [ ] Contacts permission granted; contacts load.
- [ ] Contacts permission denied; app remains usable and explains the limitation.
- [ ] Start voice search and grant microphone permission.
- [ ] English (`en-IN`) recognition.
- [ ] Marathi (`mr-IN`) recognition.
- [ ] Hindi (`hi-IN`) recognition.
- [ ] Silence, timeout, and speech-service error messages.
- [ ] Exact-name match.
- [ ] Partial-name match.
- [ ] Multiple-match disambiguation.
- [ ] Manual search fallback.
- [ ] Contact without a photo.
- [ ] Contact with missing email/company/birthday/address.
- [ ] Previous / Next navigation.
- [ ] Details-side return button and Android system Back.
- [ ] Call permission granted and direct call handoff.
- [ ] Call permission denied and dialler fallback.
- [ ] WhatsApp handoff with valid number and WhatsApp installed.
- [ ] WhatsApp unavailable / invalid number handling.
- [ ] Large text / display scaling and touch target usability.

## Speech provider troubleshooting matrix
Test VoiceDeck's microphone permission separately from the configured recognition provider's microphone permission. See `DEBUGGING_LOG.md` and [Troubleshooting](TROUBLESHOOTING.md).

## Privacy checks
- [ ] No contact names, numbers, transcripts, or contact fields appear in logs.
- [ ] No background microphone capture.
- [ ] No contact data is sent to a VoiceDeck server.
- [ ] Permission denial does not cause a crash.
