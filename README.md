# Hookway Restoration Job Camera — Android starter project

This is an Android Studio/Kotlin starter for an offline-first job camera. It is not yet a finished production app.

## Current starter features
- Reads event instances from the Android Calendar provider, including events from all calendars visible on the tablet. This requires the relevant Google account calendars to be synced to Android and Calendar permission to be granted.
- Loads events from the last 90 days through the next 365 days.
- Saves the most recently retrieved non-empty event list on the tablet, so job selection can still be available if Calendar cannot be reached later.
- Lets you select a Calendar event and creates a local job folder named `Job Title - YYYY-MM-DD`.
- Launches the tablet's camera app for photos and videos, and attempts to save media in app-private local storage.
- Records captured files in a local pending queue and schedules a network-constrained WorkManager task.

## Important limitations
- **Google Drive upload is not implemented.** `UploadWorker` is deliberately a safe placeholder. It does not upload files or mark them as synced.
- This code has not been built or tested on your particular tablet. Camera apps differ; verify that both photo and video files are actually written before using it for customer records.
- The calendar cache is a cached list of events, not a complete offline copy of Google Calendar. It refreshes when Calendar can return events.
- Captured media lives in app-private storage. Uninstalling the app or clearing its data can remove those files. Copy important test media elsewhere until a real backup is operating.
- The pending queue currently stores file paths, not a full database of upload states. A production version needs a persistent database, checksums, duplicate protection, retry/error reporting, and a visible sync status.

## Build on Windows
1. Install Android Studio and open this project folder.
2. Allow Gradle to sync and install the Android SDK version requested by the project.
3. Build the `app` module and test-install it on the tablet.
4. On the tablet, make sure the same Google account is added and Calendar sync is enabled. Grant Calendar permission when asked.
5. Test one photo and one short video while online and offline; confirm the files remain present after closing and reopening the app.

## Google Drive setup still required
The intended destination is `My Drive/Hookway Restoration Jobs/Job Title - YYYY-MM-DD/`. The app needs a proper Google sign-in/OAuth flow and Drive API uploader before automatic upload can work. Configure the Google Cloud project, enable the Drive API, set up the Android OAuth client for package `com.hookway.jobcamera` and the final signing certificate, then implement and test folder lookup/creation and authenticated uploads. Do not put client secrets or Google passwords in the app or share them in chat.

## Safety recommendation
Do not rely on this starter as your only copy of job photos/videos. Test on non-critical sample media first, and keep local files until each upload has been verified in Drive.
