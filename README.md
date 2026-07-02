# Bento Calendar

Native Android app (Kotlin + Jetpack Compose) for the Samsung Galaxy S25 —
calendar, notes, and tasks behind a 4-tab bento-style UI. Built from the
design handoff in `../design_handoff_bento_calendar/` (see `SPEC.md` for the
implementation map).

## Features
- **Today** — bento dashboard: up-next event, top tasks, pinned note, week strip, later-today, reminder banner
- **Calendar** — Month / Week / Day views, recurring events, color categories, tap-empty-slot to create
- **Notes** — pinning + 4-digit-PIN-locked private notes
- **Tasks** — Overdue / Today / Upcoming / Someday checklist with collapsible Completed
- Universal search, dark/light theme, 4 accent colors, 24h/12h time, week-start setting
- Real system reminders: exact alarms + notifications, reboot/clock-change/permission-change recovery
- All data on-device (atomic JSON via DataStore, file `bento.calendar.v1.json`)

## Build
Toolchain lives in `C:\Users\matisse\android-toolchain\` (JDK 17, Android SDK 35, Gradle 8.14.2):

```powershell
$env:JAVA_HOME = "C:\Users\matisse\android-toolchain\jdk"
& "C:\Users\matisse\android-toolchain\gradle\bin\gradle.bat" :app:assembleRelease :app:testReleaseUnitTest :app:lintRelease
```

Output: `app\build\outputs\apk\release\app-release.apk` — signed with
`bento-release.keystore` (alias `bento`, password in `app/build.gradle.kts`).
**Keep the keystore**: future updates must be signed with it to install over
the existing app.

- `applicationId` com.bento.calendar · minSdk 27 · target/compile SDK 35
- versionCode/versionName in `app/build.gradle.kts` — bump both for updates
