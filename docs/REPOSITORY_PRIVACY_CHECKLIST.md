# Repository Privacy and Secret-Prevention Checklist

Use this checklist before every commit, pull request, release, or ZIP shared publicly. A public repository is visible to anyone, and removing a file later does not remove it from Git history.

## Never commit

- Real contact names, phone numbers, email addresses, birthdays, postal addresses, photos, or contact exports.
- Real speech transcripts, audio recordings, screenshots of contacts, notification contents, or logcat output containing personal data.
- Account identifiers, device serial numbers, IMEI/MEID values, Android IDs, precise location, Wi-Fi names/passwords, IP addresses tied to a person, or home-directory paths containing a real username.
- Passwords, API keys, access tokens, OAuth client secrets, private keys, signing keys, keystores, signing passwords, service-account files, or production credentials.
- `local.properties`, local IDE state, local databases, backups, crash dumps, or unredacted diagnostic bundles.

## Safe test data

- Use invented names and clearly non-routable placeholders only.
- Use the reserved `.test` domain for example email addresses.
- Do not use plausible real phone numbers in fixtures. Use explicit non-dialable labels such as `SAMPLE-NUMBER-001`.
- Keep synthetic fixtures under `resources/test-data/` and label them as synthetic.
- Never copy a real address-book entry into a fixture, even temporarily.

## Before committing or pushing

1. Inspect changed and untracked files:
   ```bash
   git status --short
   git diff --check
   git diff --stat
   git diff
   git diff --cached
   ```
2. Search tracked files for likely personal data and credentials. Review every match; searches can produce false positives and can miss encoded or unusual secrets:
   ```bash
   git grep -nEi '(@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}|password|passwd|secret|api[_-]?key|access[_-]?token|private[_-]?key|BEGIN .*PRIVATE KEY|[+]?[0-9][0-9 ()-]{8,}[0-9])' -- .
   ```
3. Check staged files specifically and inspect binary additions. Do not assume `.gitignore` protects files that are already tracked.
4. Confirm every URL, email address, identifier, device detail, and test fixture is intentionally public and synthetic or explicitly approved.
5. If a secret or personal record was committed, treat it as exposed: revoke/rotate credentials and follow the repository owner's private incident process. Deleting it in a later commit is not sufficient; Git history must also be addressed.

## GitHub safeguards

- Enable secret scanning and push protection when available for the repository/plan.
- Require review of changes to permissions, data collection, logging, networking, and release/signing configuration.
- Keep CI secrets in GitHub Actions secrets or another secret manager; never in workflow files or repository variables when they are confidential.
- Do not paste raw logs, contact exports, or screenshots into public issues. Redact first and use a private channel for sensitive reports.

## Intentional public project information

The app currently links to the project's published privacy policy and terms pages at `ajinkyadube.com`. These are intentional public legal-document URLs, not credentials. Keep them only while the project owner approves those public endpoints. Do not add personal email addresses, phone numbers, or home/contact details as a substitute for a private vulnerability-reporting channel.

## Scope and limitations of a snapshot audit

A source ZIP audit checks only the files present in that archive. It does not inspect earlier Git commits, deleted files, branches, GitHub Actions logs, release assets, issues, discussions, or repository settings. Audit Git history and repository configuration separately before treating the repository as fully cleared.
