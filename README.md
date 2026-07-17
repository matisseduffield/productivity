# Bento Calendar

Native Android app (Kotlin + Jetpack Compose) for the Samsung Galaxy S25 —
calendar, notes, and tasks behind a 4-tab bento-style UI. Built from the
design handoff in `../design_handoff_bento_calendar/` (see `SPEC.md` for the
implementation map).

## Features
- **Today** — daily-planning command center with capacity, suggested timeline, rollover review and focus timer
- **Calendar** — Month / Week / Day / Agenda views, events plus movable/resizable task time blocks
- **Notes** — pinning + 4-digit-PIN-locked private notes
- **Tasks** — Overdue / Today / Upcoming / Someday checklist with collapsible Completed
- Universal search, dark/light theme, 4 accent colors, 24h/12h time, week-start setting
- Real system reminders: exact alarms + notifications, reboot/clock-change/permission-change recovery
- Calendar interoperability: merge or export standard `.ics` event files
- Smart Quick Add: dates, time ranges, task estimates, relative offsets, recurrence, priorities and #categories
- Multiple work sessions per task, future-safe smart replanning, working hours and planned-versus-actual review
- Seven-day Plan Ahead board with event-aware capacity, future-day planning, week swipes, historical review and review-before-write weekly auto-planning
- Private productivity insights: focus trends, follow-through, streaks and category breakdowns
- Widgets (including Daily Plan), biometric app/note unlock, recurring tasks, custom categories, soft-delete trash
- All data stays on-device: Room for domain records and DataStore for preferences/security settings

The persisted schema and mutation invariants are documented in
[`DATA_MODEL.md`](DATA_MODEL.md).

## Build
Toolchain lives in `C:\Users\matisse\android-toolchain\` (JDK 17, Android SDK 35, Gradle 8.14.2):

```powershell
$env:JAVA_HOME = "C:\Users\matisse\android-toolchain\jdk"
& "C:\Users\matisse\android-toolchain\gradle\bin\gradle.bat" --project-dir . `
  :app:testGithubReleaseUnitTest :app:assembleGithubRelease `
  :app:bundlePlayRelease :app:lintGithubRelease
```

Outputs: the self-updating APK under `app\build\outputs\apk\github\release\`
and the Play bundle under `app\build\outputs\bundle\playRelease\`.

Signing is never committed: local builds read the untracked
`keystore.properties` + `bento-release.keystore` (backup at
`C:\Users\matisse\android-toolchain\bento-release.keystore.backup`); CI
restores them from the repo's encrypted Actions secrets (`KEYSTORE_BASE64`,
`KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`). **Keep the keystore**:
future updates must be signed with it to install over the existing app.

## Updates
The app updates itself: it checks this repo's latest GitHub release on launch,
and offers download + install in-app (Settings → App updates, or the Today
banner). Publishing an update = bump versionCode/versionName, push a `v*` tag.

- `applicationId` com.bento.calendar · minSdk 27 · target/compile SDK 35
- versionCode/versionName in `app/build.gradle.kts` — bump both for updates
- Every release: add an entry to `app/src/main/java/com/bento/calendar/ui/settings/Changelog.kt` (shown in Settings → What's new)
