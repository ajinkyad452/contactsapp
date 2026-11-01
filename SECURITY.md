# Security policy

## Reporting a vulnerability

Do not publish exploit details or personal contact data in a public issue. Contact the project owner privately through a communication channel they have explicitly provided.

## Privacy-sensitive data

VoiceDeck handles contact information and microphone input. Avoid collecting, storing, or logging more personal data than the feature requires. Do not include real names, phone numbers, addresses, transcripts, credentials, or signing material in bug reports or repository history.

## Repository hygiene

Before every push, follow [`docs/REPOSITORY_PRIVACY_CHECKLIST.md`](docs/REPOSITORY_PRIVACY_CHECKLIST.md). In particular, never publish real contact records, transcripts, recordings, device/account identifiers, local configuration, credentials, or signing material. Synthetic fixtures must use non-dialable phone placeholders and reserved `.test` email addresses.

If a credential is accidentally committed, revoke or rotate it immediately. Removing it in a later commit does not remove it from Git history. Treat any real personal data committed publicly as exposed and contact the project owner privately to coordinate removal from repository history and any copies.
