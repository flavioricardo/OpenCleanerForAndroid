# OpenCleaner for Android

Free and open-source storage cleaner for Android, from the same project as
[OpenCleaner for Windows](https://github.com/flavioricardo/OpenCleaner): no telemetry, no ads,
no paid tier, and **analyzing never deletes anything**.

One tap analyzes the phone and shows where the space went. A second tap resolves what is safe.

## What it does

- **WhatsApp** (and WhatsApp Business): duplicate media (the same file received in several chats
  or also saved in the gallery), old local backups (the current one always stays) and statuses
  already seen. Large videos are listed, never checked by default.
- **Cache of all apps**, through the Android system dialog.
- **Apps you have not opened in 30+ days**, sorted by size, with an uninstall shortcut.
- **Junk**: APKs in `Download`, gallery thumbnails, empty folders, exact duplicates.
- **Only you can do**: apps holding over 1 GB of their own data (offline downloads, for example),
  with a shortcut to their settings page.

Every item opens a list of exactly which files will go, and you can uncheck any of them.

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

Android 11 or newer. Download `app-release.apk` from the latest
[CI run](https://github.com/flavioricardo/OpenCleanerForAndroid/actions/workflows/ci.yml)
(artifact `OpenCleaner-apk`) and open it on the phone, allowing installs from that source.

## Build

JDK 17, Android SDK 35 and Gradle 8.11.1:

```bash
gradle testDebugUnitTest assembleRelease
```

Release signing reads `OC_KEYSTORE`, `OC_KEYSTORE_PASSWORD`, `OC_KEY_ALIAS` and `OC_KEY_PASSWORD`
from the environment. Without them, the release APK is signed with the debug key.

## License

MIT
