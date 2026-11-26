# FitStreak 🔥 — Earn your screen time

A daily morning-workout tracker for Android that keeps you disciplined:

- **Today's workout**: 40 push-ups, 40 squats, 20 lunges (each side), 50 jumping jacks,
  50 mountain climbers, 1 min plank, 1 min side plank (each side)
- **Guided mode**: one exercise at a time, tap-to-count reps, plank timer with beeps, rest timer between exercises
- **Streaks** 🔥, best streak, calendar (done / partial / missed), lifetime totals
- **Badges** at 1, 3, 7, 14, 21, 30, 50, 100 and 365 days in a row
- **Reminders**: a morning notification at your chosen time, plus a 9 PM "streak in danger" alert
  (both skipped if you've already finished the day)
- Malayalam + English motivation quotes
- Editable targets and a "Level up (+10%)" button
- Backup / restore (copy-paste text)

## Getting the APK

Every push that changes `fitstreak/` builds the APK with GitHub Actions and publishes it as a
**GitHub Release** (`fitstreak-v1.0.N`). Open the repo's *Releases* page on your phone, download
`FitStreak.apk`, and allow "Install unknown apps" for your browser when Android asks.

## Project layout

- `app/src/main/assets/` — the app UI (HTML/CSS/JS). Open `index.html` in a browser to try it.
- `app/src/main/java/com/fitstreak/app/` — the Android wrapper: WebView, reminder alarms, notifications.
- `app/fitstreak.keystore` — a fixed signing key so each new APK installs over the old one without
  losing data. It is not secret; it only exists for personal sideloading.

Build locally (with the Android SDK installed): `./gradlew assembleRelease`
