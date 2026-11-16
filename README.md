# OpenCleaner for Android

Free and open-source storage cleaner for Android, from the same project as
[OpenCleaner for Windows](https://github.com/flavioricardo/OpenCleaner): no telemetry, no ads,
no paid tier, and **analyzing never deletes anything**.

One tap analyzes the phone and shows where the space went. A second tap resolves what is safe.

## What it does

- **WhatsApp** (and WhatsApp Business): duplicate media (the same file received in several chats
  or also saved in the gallery), old local backups (the current one always stays), statuses
  already seen, and videos, photos, audio and documents older than a few months. A bar shows
  how the WhatsApp media splits by type.
- **Space by folder**: where the space goes, folder by folder, plus the largest files, and how
  much belongs to apps and the system, out of any app's reach.
- **App cache**: external caches through the Android system dialog; apps with a big internal
  cache are listed with a shortcut, since only they can clear it.
- **Apps you have not opened in 30+ days**, sorted by size, with an uninstall shortcut.
- **Junk and old files**: APKs in `Download`, gallery thumbnails, empty folders, exact
  duplicates, large videos, old downloads, screenshots and screen recordings.
- **Android trash**: optionally send photos, videos and audio there instead of deleting them
  (30 days to undo), and empty it when you choose.
- **Sensitive access**: apps that can use location all the time, the camera or the microphone.
- **Only you can do**: apps holding over 1 GB of their own data (offline downloads, for example),
  with a shortcut to their settings page.
- **Options** like the desktop app: ask before cleaning, remember choices, thresholds, folders
  that are never deleted, app language.

Every item opens a list of exactly which files will go, and you can uncheck any of them. Large
videos, old files and apps always start unchecked.

Removing a duplicate keeps one identical copy (byte for byte). If the copy removed was inside
WhatsApp, that chat shows the media as unavailable, but the file is still on the phone.

## What it does not do

- Nothing runs in the background: no service, no schedule, no notification.
- No network access: the app does not even declare the `INTERNET` permission.
- No "RAM booster", "CPU cooler" or process killing. They do not help on Android.
- No root or Shizuku (planned as an optional module later).

## Permissions

| Permission | Why |
|---|---|
| All files access | Find and delete junk, duplicates and WhatsApp leftovers in shared storage |
| Usage access | Size, cache and last use of each app |
| Query all packages | List installed apps |
| Request delete packages | Open the system uninstall dialog |

## Install

Android 11 or newer. Download the APK from the latest
[release](https://github.com/flavioricardo/OpenCleanerForAndroid/releases/latest) and open it on
the phone, allowing installs from that source. Until releases have a stable signing key,
uninstall the previous version before installing a new one.

## Build

JDK 17, Android SDK 35 and Gradle 8.11.1:

```bash
gradle testDebugUnitTest assembleRelease
```

Release signing reads `OC_KEYSTORE`, `OC_KEYSTORE_PASSWORD`, `OC_KEY_ALIAS` and `OC_KEY_PASSWORD`
from the environment. Without them, the release APK is signed with the debug key.

## License

MIT
