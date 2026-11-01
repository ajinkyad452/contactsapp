# ADR-003: Read contacts from the device

- Status: Accepted
- Date: 2026-11-01

## Decision
Read contact information through Android's Contacts Provider. Do not add a VoiceDeck cloud address book in the MVP.

## Rationale
The app is a companion interface for contacts already stored on the user's phone. A cloud contact service would add unnecessary data handling and privacy obligations.

## Consequences
Available fields depend on the device and account. Contact access requires runtime permission. Test fixtures must be synthetic and logs must not contain personal contact data.
