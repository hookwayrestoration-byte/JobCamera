# Google setup checklist — Hookway Restoration Job Camera

## Confirmed app choices
- [x] Calendar and Drive are intended to use the same Google account.
- [x] Include events from all Google Calendars that are synced and visible on the tablet, not only a dedicated jobs calendar.
- [x] Planned Drive folder format: `Job Title - YYYY-MM-DD`.
- [ ] Confirm the Drive destination parent folder; planned default is `My Drive/Hookway Restoration Jobs/`.
- [ ] Keep local media after upload (recommended) until you choose to delete it.

## Intended Drive structure
`My Drive/Hookway Restoration Jobs/Job Title - YYYY-MM-DD/`

The completed app should reuse an existing matching folder and track each file as Pending, Uploading, Synced, or Failed. It should retry failures without creating duplicate files.

## Things you can do without the Windows computer
1. Check that job appointments are in Google Calendar and have clear titles.
2. On the tablet, open Android Settings → Passwords & accounts (wording varies) → your Google account → Account sync, and check that Calendar sync is enabled.
3. Open Google Drive and decide whether to use an existing `Hookway Restoration Jobs` folder or create one later.
4. Keep customer data safe: do not send your Google password, verification codes, recovery codes, or OAuth client secrets in this chat.

## When the computer and charger are available
1. Install Android Studio and open the project.
2. Create/select a Google Cloud project and enable Google Drive API.
3. Configure OAuth consent and an Android OAuth client for package `com.hookway.jobcamera` using the final build's SHA-1 signing certificate.
4. Implement authenticated sign-in, Drive folder lookup/creation, uploads, retry handling, duplicate protection, and visible sync status.
5. Build and install the app, grant Calendar permission, and test photo/video capture on the actual tablet.
6. Test offline capture, reconnect to Wi-Fi/mobile data, and verify every file in Drive before relying on the app.

## Current status
The project now caches the last non-empty Calendar event list for offline job selection. It can launch the camera and save media locally when the camera app supports the supplied output URI. **Automatic Google Drive upload is still not implemented**, and this project has not yet been built/tested on the target tablet. Do not rely on it as a completed backup solution yet.
