# UI Specification — Frozen VoiceDeck UI v1.0

This specification records the current design baseline. Do not redesign or add gestures without explicit product approval.

## Flow
1. Launch into the voice-search experience.
2. User starts voice search and says a contact name.
3. Show a matching contact card; if multiple contacts match, let the user disambiguate.
4. If speech is unavailable or unsuccessful, allow manual contact search.
5. User can browse adjacent matches with Previous / Next.

## Contact card front
- Large contact photo when available.
- Prominent contact name.
- Primary mobile number.
- Exactly two primary actions, side by side: **Call** and **WhatsApp**.
- Previous and Next navigation controls.
- No swipe navigation.

## Contact card details side
Show available saved details such as:
- Email
- Company and designation
- Birthday
- Address
- Other supported contact information
- An explicit “Back to contact card” action
- “Open in Google Contacts”

## Accessibility and interaction
- Prefer large, high-contrast text and touch targets.
- Keep labels explicit; do not rely on icons alone.
- Do not make swipe gestures necessary.
- Missing contact fields should be omitted or shown gracefully.
- Preserve a clear route back to the front of the card.
