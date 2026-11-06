# FocusApp (Regain-style focus and digital wellbeing app)

Native Android (Kotlin), minSdk 29, built from the SRD. Personal sideload use.
Light, minimalist UI. Offline-first (Room). Firebase is intentionally not included yet (see "Not included").

## What is new in v2

- **Four tabs**: Home, Schedule, Streak, Setup. Light, soft theme (edit colours in `res/values/colors.xml`).
- **Weekly schedule**: add time slots for any day (Mon to Sun). Labels: many built-in ones plus your own custom label.
  Choose apps to block (with icons and search) plus Reels/Shorts blocking.
- **Schedule lock**: "Lock schedule" lets you pick the exact days on a calendar (up to 31 days, change `MAX_LOCK_DAYS`),
  or use the quick choices: rest of this week / next week.
  Every slot then starts by itself in Strict Mode (exact alarm + boot + app-open + accessibility fallbacks).
  While locked, the accessibility service sends you home if you open a Settings / installer page that
  mentions FocusApp. When the week ends, open Setup, re-check the permissions and lock the new week.
- **Streak**: a day counts after 10 minutes of completed focus. Current streak, longest streak,
  active days, 17 week heatmap and this week's row.
- **Emergency unlock** (safety hatch): 10 minute wait plus typing UNLOCK, only between slots.
  Turn it off with `ALLOW_EMERGENCY_UNLOCK = false` in `ScheduleStore.kt` for a truly unbreakable week.

## Get the APK (no Android Studio needed)

1. Create a new empty GitHub repository.
2. Upload everything in this folder to it (or `git init`, `git add .`, `git commit`, `git push`).
   Make sure the `.github` folder is included.
3. Open the repository's **Actions** tab. The "Build APK" workflow starts by itself on push
   (or press **Run workflow**). It takes about 4 to 8 minutes.
4. Open the finished run, scroll to **Artifacts**, download **FocusApp-debug-apk**, unzip it.
5. Copy `app-debug.apk` to your phone and install it (allow "Install unknown apps" when asked).

## Or build in Android Studio

Open this folder, wait for Gradle sync, then **Build > Build APK(s)**.
The APK is at `app/build/outputs/apk/debug/app-debug.apk`.
Command line: `./gradlew assembleDebug`.

## First run on the phone (important order)

1. Open FocusApp. Allow notifications.
2. **Android 13 and newer: allow restricted settings first.** Long-press the FocusApp icon,
   App info, tap the three dots (top right), choose **Allow restricted settings**.
   Without this, Android will not let you enable Accessibility or Notification access.
3. On the dashboard, grant each row: Usage access, Draw over other apps, Accessibility service,
   Notification access, Device admin, Ignore battery optimization.
4. Xiaomi, Vivo, Oppo, Realme, OnePlus: tap "Open phone auto-start settings" and enable auto-start,
   and lock the app in the recents screen.
5. Tap **Start Session**, pick a duration, tag and apps, and go.

## What each SRD feature maps to

| SRD | Where |
| --- | --- |
| 3.1 Interceptor engine | `TrackerService` (UsageEvents poll about every 400 ms) and `BlockActivity` |
| 3.2 / 7.4 Strict Mode guard | `FocusAccessibilityService.guardScreen` (Settings, Play Store, installer) |
| 3.3 Reels / Shorts blocking | `FocusAccessibilityService.isReelsScreen / isShortsScreen` |
| 3.4 Timers, tags | `ConfigActivity`, `ActiveFocusActivity`, `SessionStore` |
| 4.x Permissions | `AndroidManifest.xml`, `Perms.kt` |
| 5 Database | `Db.kt` (Room) |
| 7.1 Reboot survival | `BootReceiver` |
| 7.2 Clock tampering | `SessionStore` (uses `SystemClock.elapsedRealtime()`) |
| 7.3 Penalty | rebooting during a Strict session adds 5 minutes |
| 4.4 Uninstall protection | `FocusAdminReceiver` (Device Admin) |
| Backgrounding | foreground service, wake lock, battery exemption, 60 s exact-alarm watchdog |

## Known limitations

- Reels/Shorts detection depends on Instagram and YouTube screen structure, which changes with
  app updates (SRD Appendix C). If a block stops working, adjust `isReelsScreen` / `isShortsScreen`.
- Strict Mode cannot stop Safe Mode, a factory reset, or ADB (`adb uninstall`) without root.
- No guarantee a service is never killed on aggressive OEM skins; the watchdog and auto-start
  settings reduce this but cannot remove it.
- Debug-signed APK: fine for your own phone. Not for the Play Store.

## Not included yet

- Firebase (Auth, Realtime Database routine sync, analytics): needs your `google-services.json`.
- App icon blob in `App_Directory` and Crashlytics (both optional in the SRD).
