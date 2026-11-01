# Contributing to VoiceDeck

1. Read `README.md`, `AGENTS.md`, `CHANGELOG.md`, and `DEBUGGING_LOG.md` before changing the project.
2. Keep pull requests focused and explain the user-visible behavior change.
3. Preserve the frozen UI and Java-first architecture unless the product owner explicitly approves a change.
4. Update the changelog, debugging log, and README where applicable.
5. Run `./gradlew :app:assembleDebug` when the Gradle wrapper and Android SDK are available. Report any validation you could not run.
6. Never commit signing keys, credentials, local machine configuration, or real contact data.


## Documentation and version metadata
- Keep `docs/` aligned with actual app behaviour.
- Keep synthetic fixtures only in `resources/test-data/`.
- Update `CHANGELOG.md` for user-visible app changes and `DEBUGGING_LOG.md` for device-specific findings.
- `version.properties` is the source of Android version metadata; do not hardcode a second version in Gradle.
- Do not claim a build/device test passed unless it was run.
