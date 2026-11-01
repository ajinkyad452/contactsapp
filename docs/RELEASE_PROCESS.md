# Release Process

## Before release
- [ ] Confirm the product/UI baseline and approved scope.
- [ ] Complete the functional checklist on supported devices.
- [ ] Verify speech recognition on Pixel 4a and Pixel 7 Pro where available.
- [ ] Review permission prompts and denial paths.
- [ ] Review privacy disclosures, Play policy, and Data safety information.
- [ ] Check that logs and test data contain no personal contact information.
- [ ] Run `./gradlew :app:assembleDebug` and record the result.
- [ ] Test the generated APK on physical devices.
- [ ] Review `git diff` and `git diff --check`.

## Versioning
- `version.properties` is the single source for Android `versionName` and `versionCode`.
- Increase `VERSION_CODE` for every Play Store release.
- Use a human-readable semantic version in `VERSION_NAME`.
- Update `CHANGELOG.md` with the release date and user-visible changes.
- Keep device-only observations in `DEBUGGING_LOG.md`; they do not automatically require a version bump.

## Signing and publishing
Keep upload keys, signing passwords, service credentials, and private configuration outside the repository. Use a secure local or CI secret store. Never place signing material in project ZIPs or Git history.

## Archive contents
Include source, documentation, resources, and repository support files. Exclude `.gradle/`, build outputs, IDE state, local SDK paths, secrets, and real user contact data.
