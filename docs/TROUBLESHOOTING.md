# Troubleshooting

## Speech recognition fails although VoiceDeck has microphone permission
Speech recognition runs through the device's configured recognition service. Check:
1. VoiceDeck has `RECORD_AUDIO` permission.
2. The configured recognition provider has microphone permission while it is in use.
3. Android's microphone privacy toggle is enabled.
4. The selected language is supported and any required language pack/service is available.
5. Another app is not holding the microphone.

Do not assume a provider permission issue is a VoiceDeck code defect. Do not use permanent unrestricted AppOps overrides as a routine fix.

## Pixel 7 Pro finding
The 2026-11-01 debugging log records a Pixel 7 Pro provider-side microphone permission failure (`MICROPHONE_UNAVAILABLE`, error 102) while the same app worked on a Pixel 4a. The provider permission was observed as Ask every time / not granted for the active user. Changing it to “Allow only while using the app” was proposed for a controlled retest; the log does not record a confirmed successful retest.

## Contact not found
- Confirm Contacts permission is granted.
- Confirm the contact exists in the device's Contacts app.
- Try the manual search fallback.
- Check whether the displayed name differs from the spoken phrase.
- Current matching is basic; phonetic and multilingual fuzzy matching are not implemented.

## Call or WhatsApp action does not work
- Confirm the contact has a valid phone number.
- Confirm WhatsApp is installed for the WhatsApp action.
- For Indian numbers, the current MVP assumes a country code may be added for a stored number containing exactly 10 digits.
- If direct call permission is denied, the dialler fallback is expected.
