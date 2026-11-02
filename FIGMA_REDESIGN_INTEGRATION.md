# VoiceDeck Figma redesign integration — based on contactsapp-develop(7)

This update wires the supplied Figma-native Android XML layouts into the existing Java application.

## Integrated screens

- Home (`screen_home.xml`) — Speak and manual search actions
- Listening (`screen_listening.xml`) — live transcript text, cancel and stop actions
- Contact card (`screen_contact.xml`) — dynamic contact photo/name/number/details, Call, WhatsApp, flip, previous/next and return-to-search actions
- Multiple matches (`screen_matches.xml`) — real match list and manual-search fallback
- Manual search (`screen_search.xml`) — live filtering of the device contacts by name or phone
- Settings (`screen_settings.xml`) — language, vocabulary, aliases, permissions and legal navigation
- Alias list (`screen_aliases.xml`) — real Room-backed aliases, search, edit and delete
- Vocabulary list (`screen_vocabulary.xml`) — real Room-backed vocabulary, search, enable/disable, add and restore
- Permissions (`screen_permissions.xml`) — current Android permission state and shortcut to app settings
- Legal (`screen_legal.xml`) — privacy policy and terms links

## Compatibility

The Figma XML used MaterialButton and MaterialSwitch class names, but the app uses a platform Material theme and does not include Material Components. To avoid a new dependency/theme migration, these XML widgets have been converted to native Android Button and Switch controls. The existing Figma styles and drawable backgrounds remain in use.

ConstraintLayout remains supplied by the project's existing AndroidX dependency.

## Files changed

- `app/src/main/java/com/ajinkyadube/voicedeck/MainActivity.java`
- `app/src/main/res/layout/screen_alias_form.xml`
- `app/src/main/res/layout/screen_aliases.xml`
- `app/src/main/res/layout/screen_contact.xml`
- `app/src/main/res/layout/screen_home.xml`
- `app/src/main/res/layout/screen_listening.xml`
- `app/src/main/res/layout/screen_matches.xml`
- `app/src/main/res/layout/screen_nickname.xml`
- `app/src/main/res/layout/screen_permissions.xml`
- `app/src/main/res/layout/screen_vocabulary.xml`
- `app/src/main/res/layout/screen_vocabulary_form.xml`

## Preserved

- Application ID and namespace
- Java speech-recognition and contact-matching logic
- Room database, entities, DAOs and repositories
- Contact aliases and speech dictionary data
- Calling and WhatsApp actions
- Existing permissions and manifest
- Existing contact photo fallback resource
- Existing app data; no uninstall or database migration required

## Apply and verify

Extract this project and build from the project root:

```bash
gradle :app:assembleDebug
```

Install over the existing app without uninstalling:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Test Home, voice listening, manual search, multiple matches, contact card front/back, Settings, Alias management, Vocabulary, Permissions and Legal.

## Validation note

The updated XML files were parsed successfully and checked for missing `R.id` references and leftover Material Components view tags. A full Android Gradle build and physical-device test were not available in the generation environment; run the build above before installing.

Alias and vocabulary *list* screens are wired to the live Room repositories. Their add/edit forms still use the existing tested Java dialogs; the Figma form layouts are included and converted to native controls, but have not yet been wired to those dialog flows.

## UI button styling hotfix
Custom primary and outline button drawables now explicitly suppress platform backgroundTint in XML styles and button views. Dynamic alias action buttons also use the app button styling. This fixes the visual regression caused by converting Figma MaterialButtons to native Buttons without disabling platform tint. To see the previously integrated forms, open Settings → Aliases → Add alias or Settings → Vocabulary → Add.
