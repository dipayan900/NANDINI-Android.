# NANDINI Android starter

A small native Android starter project for a phone-only workflow. GitHub Actions builds the debug APK in the cloud.

## Included
- Native Kotlin Activity (not a WebView)
- Dark purple chat interface
- Demo replies only; real AI is not connected yet
- Application ID: `com.dipayan.nandini`
- Minimum Android version: Android 8.0 (API 26)

## Upload to GitHub from your phone
1. Create a repository named `NANDINI-Android`.
2. Upload every file and folder in this ZIP to the repository root. Keep `.github/workflows/build-apk.yml` in place.
3. Commit the files to the `main` branch.
4. Open the repository's Actions tab.
5. Select `Build NANDINI APK` and tap `Run workflow` if shown.
6. Open the completed run and download the `NANDINI-debug-apk` artifact.
7. Extract the artifact ZIP and install `app-debug.apk`. Android may ask you to permit installs for the app you use to open it.

This is a debug APK for testing, not a Play Store release. GitHub Actions usage is subject to account/repository limits.

## Firebase later
The package name is `com.dipayan.nandini`. Register this exact ID in Firebase only after the native project exists. Do not commit API keys, passwords, service-account files, or other secrets. This starter does not need Firebase credentials yet.

## If build fails
Open the failed workflow run and share the first useful error message, with any private tokens removed.
