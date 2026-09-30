# Smolish for Android

A native Android client for [Smolish](https://smolish.com). This is an independent project.

## Install

Download [smolish-android-debug.apk](smolish-android-debug.apk) from the repository root and install it on Android 8.0 (API 26) or newer. Android may ask you to allow installation from the app you use to open the APK. This is a debug build.

## Build

Install JDK 17 and Android SDK 35, then open [android/](android/) in Android Studio. From a Windows terminal, you can also run:

```powershell
cd android
.\gradlew.bat assembleDebug
```

The build output is `android/app/build/outputs/apk/debug/app-debug.apk`. To refresh the copy at the repository root after a build:

```powershell
Copy-Item app\build\outputs\apk\debug\app-debug.apk ..\smolish-android-debug.apk
```

## Features

- Full-page vertical video feed with looping playback and automatic pagination
- Creator photos, follow controls, likes, bookmarks, sharing, and compact round actions
- Native comments sheet with half and full positions, replies, mentions, reactions, and a keyboard-aware composer
- Creator search, profiles, and two-column video grids with view and like counts
- Web sign-in, encrypted local session storage, manual browser-session fallback, and account switching
- Detailed notifications with unread polling and automatic pagination; opening an item marks it read on this device
- Studio analytics, video management, multipart upload, and publishing
- Icon-only navigation and safe-area spacing

The profile menu contains Share profile, Switch account, and Sign out. The creator menu contains Share profile.

## Project layout

The Android project is in [android/](android/). App code is in `android/app/src/main/java/com/smolish/android/`; resources are in `android/app/src/main/res/`.

The app calls the Smolish website API. Sign-in and account features require a valid Smolish session. Individual notification read status is stored on this device; Mark all read uses the service API.

## Verification

The debug APK builds with Android SDK 35. On an Android 36 emulator, video playback, 16 successive video swipes, automatic feed pagination, creator video grids, comments loading, sheet expansion, keyboard spacing, and navigation were checked. Signed-in notification actions and uploads still need testing with an account.

This client is not affiliated with Smolish. Service changes may require updates to its API integration.
