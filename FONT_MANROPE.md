# Manrope applied app-wide

- All Figma XML screens inherit Manrope via AppTheme.
- Dynamically created TextViews/buttons in MainActivity use Manrope.
- The official Google Fonts variable TTF is downloaded **once** by Gradle into `app/src/main/res/font/manrope.ttf` and packaged with the APK.
- First build requires internet access to GitHub. Later builds and app runtime work offline.
- Run `gradle :app:clean :app:assembleDebug` from project root.
- Font source: https://github.com/google/fonts/tree/main/ofl/manrope (SIL Open Font License).
- If network access blocks automatic installation, manually download `Manrope[wght].ttf` from the source and put it at `app/src/main/res/font/manrope.ttf`.
