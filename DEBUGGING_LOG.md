# VoiceDeck Device Debugging Log

## 2026-11-01 — Pixel 7 Pro speech-recognition microphone failure

### Observed behaviour
- The same VoiceDeck APK works on a Pixel 4a but speech recognition fails on a Pixel 7 Pro.
- Pixel 7 Pro initially worked once; subsequent voice-search attempts failed.
- VoiceDeck displayed microphone / speech-recognition permission errors.
- Earlier logcat excerpts showed Google's recognition service reporting `MICROPHONE_UNAVAILABLE` (error code 102), followed by no successful `onResults` callback. This points to microphone capture in the recognition provider, rather than contact matching.

### Device comparison

| Check | Pixel 7 Pro | Pixel 4a |
|---|---|---|
| Android release | 17 | 13 |
| SDK level | 37 | 33 |
| VoiceDeck `RECORD_AUDIO` AppOps | `foreground` | `foreground` (recent allow entry) |
| Google Speech Services (`com.google.android.tts`) `RECORD_AUDIO` AppOps | Current mode `ignore` | `foreground` |
| Google Speech Services permission, active user 0 | `granted=false`, flag includes `ONE_TIME` | Reported as allowed / foreground in the permission/AppOps checks |
| VoiceDeck behaviour | Speech recognition fails | Works |

### Important diagnostic details
- Configured recognition service on the Pixel 7 Pro was `com.google.android.tts/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService`.
- Other recognition services were installed, including Google's `GoogleRecognitionService`; switching the selected service did not resolve the problem.
- Temporarily setting `com.google.android.tts` `RECORD_AUDIO` AppOps to `allow` made the UI appear to listen, but the supplied log still showed `MICROPHONE_UNAVAILABLE` / error 102 and no transcript callback. This was not a confirmed fix.
- The active Android user is `0`. `dumpsys package com.google.android.tts` showed multiple package/user records: User 0 had `RECORD_AUDIO: granted=false` with `GRANTED_BY_ROLE|ONE_TIME`; User 10 had a granted record. ADB shell access to user 10 was denied, so the User 10 record is not evidence of permission for the active user.
- In Android Settings, the user found the Google Speech Services microphone permission was set to **Ask every time**.
- Proposed change: set Google Speech Services microphone permission to **Allow only while using the app**, then retest VoiceDeck. The user has not yet confirmed the post-change test result.

### Current conclusion
Strong lead: the Pixel 7 Pro's Google Speech Services microphone permission was configured as **Ask every time**, while the Pixel 4a had foreground microphone access. VoiceDeck's own microphone permission was already granted on both devices. This is currently a device/provider permission diagnosis, not a confirmed app-code defect or a confirmed final fix.

### Next verification
1. Set Google Speech Services microphone permission to **Allow only while using the app** on the Pixel 7 Pro.
2. Test VoiceDeck voice search again without changing app code or AppOps manually.
3. Confirm whether Google app voice search and Recorder work if VoiceDeck still fails.
4. If fixed, record the successful retest and keep the app-code version at v1.4 unless code changes are actually made.

### Safety / privacy note
Allowing microphone access only while the speech service is in use does not itself mean continuous recording. Android's microphone privacy controls and indicator remain available. Avoid using permanent or unrestricted permission settings for this diagnostic.


## 2026-11-01 — Contact matching greeting false positive

### Observed behaviour
- A spoken greeting recognized as `hello` could partially match a multi-word contact such as `Hello Tunes`, even when the user had not said the full contact name.

### Root cause
- `ContactNameMatcher.resolve()` removed command words before checking complete contact-name phrases.
- The greeting `hello` was not in the existing filter list, so it remained eligible as a partial token match.

### Code change
- Preserve the original normalized tokens for complete contact-name and alias phrase matching.
- Apply command/greeting filtering only to the weaker first-name/surname token fallback.
- Add `hello` and common greeting terms to the current hardcoded filter set. The list remains temporary until the planned Room-backed speech dictionary is implemented.
- If filtering leaves no partial-match candidate tokens, only a complete phrase match can succeed; otherwise return `NO_MATCH`.
- Remove Logcat output of full and partial speech-recognition transcripts. The recognized text remains available to the existing in-app flow but is no longer written to logs.

### Regression tests
- Added `ContactNameMatcherTest` with 15 cases covering complete names, greeting collisions, single-word greeting names, partial names, ambiguity, command-only and empty input, aliases, and normalization.
- The matcher and test source were compiled and executed with a local JVM test harness: all 15 tests passed. This harness used lightweight Android/JUnit stubs and is not a substitute for the Android Gradle test task.

### Not verified
- The project ZIP does not include `gradlew`, and no system Gradle installation is available in this environment. `./gradlew :app:testDebugUnitTest` and a full Android build were not run.
- Device voice-recognition behavior has not been retested on physical devices.


## 2026-11-02 — Typed aliases and selected-language interface

### Report and root cause
- User reported that manually typed searches did not check saved aliases.
- Confirmed in source: `showManualSearch()` searched only `ContactItem.name` and `phone`; it did not consult the Room-loaded alias index used by voice matching.
- The language preference previously selected recognition/dictionary/alias language only. Screens and most runtime messages contained hardcoded English strings.

### Changes
- Build a `ManualContactSearch` index alongside the voice matcher from the same selected-language alias map, already filtered against current contact IDs.
- Support partial alias/name search, Latin normalization, preserved Devanagari marks, and formatted numeric queries. Keep shared matches visible and deduplicate by contact ID.
- Wait for alias loading and refresh the current typed query on completion. Rebuild after alias CRUD. Clear the search refresh callback when navigating away or destroying the Activity.
- Apply a locale-specific Activity context before resource inflation using the existing Activity language preference, accounting for namespace/application-ID differences. Recreate on language changes and restore Settings without replaying the splash.
- Add complete Hindi/Marathi UI resources, including formatted messages and vocabulary categories. Keep user-entered data and externally opened pages in their original language.

### Validation performed
- Lightweight Java 17 JVM harness: 45 tests passed, including 12 new manual-search tests, 19 voice matcher tests, 3 alias-map tests, 7 speech-filter tests, and 4 dictionary-default tests. The harness uses minimal Android/Room annotation/JUnit stubs and is not the Android Gradle test task.
- Java compiler syntax parsing passed for all 22 Java source/test files. This is syntax validation, not full Android type checking.
- Parsed all resource XML; checked 229 base resources, complete Hindi/Marathi translations for translatable entries, matching format placeholders, and resolution of all string references. `git diff --check` passed.

### Not verified here
- No Gradle executable/wrapper or Android SDK is installed. A probe to the Android SDK download host timed out. `:app:testDebugUnitTest`, `:app:assembleDebug`, and `:app:connectedDebugAndroidTest` were not run.
- New Android resource-selection tests are supplied but were not executed on a device. Activity recreation, language persistence, actual speech-provider behavior, and visual layout at large font sizes need device verification.
- Pixel 4a / Pixel 7 Pro are not connected to this environment. No physical-device test result is claimed.


## 2026-11-02 — Manage aliases clipping and broken initials

### Report and source findings
- User screenshot showed the alias list clipped before the final rows, with scrolling ineffective, and replacement-character diamonds in some badges.
- `showFigmaScreen()` assigned a fixed height to the screen. The alias layout also constrained its content to the parent height, clipping its expanding list before the outer ScrollView could measure the overflow.
- Initials used `substring(0, 1)` on whitespace-separated words. An emoji-only word could contribute an isolated UTF-16 surrogate, displayed as a replacement character.

### Fix
- Let the Manage aliases screen and its content wrap their full natural height. Keep the existing outer ScrollView as the only scroll container; no touch-interception workaround or nested scrolling is introduced.
- Add a shared `ContactInitials` utility for alias, typed-search and contact badges. Extract letter/number words, ignore symbols/emoji, and use character boundaries to preserve surrogate pairs and combining marks. Keep stored/displayed contact names untouched.

### Validation
- Lightweight JVM harness: all 53 checks passed, including 8 new Unicode initials tests and the 45 existing search/dictionary checks. Minimal Android/Room/JUnit stubs are used; this is not an Android Gradle test run.
- Java syntax parsing passed for all 24 Java source/test files. Alias layout XML parses; screen and child have wrap-content height and there is no nested scrolling container. `git diff --check` passed.
- Full Android build and physical-device scrolling/layout tests remain pending: Gradle/Android SDK and attached phones are unavailable here. Retest with many aliases, search/keyboard open, and larger fonts.
