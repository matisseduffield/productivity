# Bento Calendar data model

Bento stores its private data as one atomic, typed document using Android
DataStore. The on-device file is `bento.calendar.v1.json`; the name stays
stable so releases upgrade the same store. Kotlin serialization supplies
defaults for new fields and ignores unknown fields, making app upgrades and
backup downgrades tolerant without a manual SQL migration layer.

## Root document

`AppData` owns every persisted collection and preference:

- `events`: Bento-created calendar events.
- `tasks`: tasks, recurrence, priorities, checklist steps and due reminders.
- `notes`: note text, pin/lock state and optional tile colour.
- `prefs`: appearance, calendar, privacy and integration settings.
- `pin`: the notes PIN. It remains on device and is stripped from JSON exports.
- `categories`: user-editable category ids, labels and colours.
- `trash`: restorable event/task/note snapshots, capped at 200 entries and
  retained for 30 days.

The device-calendar overlay is deliberately not stored here: only the user's
enabled calendar ids are persisted. Device events remain read-only and are
queried from Android's Calendar Provider when needed.

The last Calendar mode is stored as a small preference (`month`, `week`, `day`
or `agenda`) so the user's working view survives a process restart.

## Event invariants

- Dates are ISO `YYYY-MM-DD`; times are `HH:MM`.
- A single-day timed event has `end > start`.
- An all-day event uses `00:00` to `23:59`.
- `endDate` is the inclusive final day of a multi-day event. Multi-day events
  cannot recur.
- Recurrence is expanded at read time. Daily, weekly and monthly series keep
  one base event; `exDates` removes individual occurrences.
- Editing one occurrence adds its date to the series' `exDates` and creates a
  standalone event containing the edit.
- `.ics` import is additive. Imported ids are stable (Bento id or calendar
  UID-derived), so importing the same file again does not duplicate events.

## Task invariants

- `due == null` means Someday; `remindAt` is meaningful only with a due date.
- Priority is an integer from 0 (none) through 3 (high).
- Checklist step ids are stable inside the task.
- Completing a recurring task advances its due date and resets its checklist
  instead of marking it permanently complete.

## Notes and privacy

Locked notes use the app's four-digit PIN gate, optionally substituted by
Android biometric/device authentication. This is access control, not database
encryption. JSON backup exports intentionally omit the PIN but include note
content (including locked notes), which Settings states explicitly.

## Mutation pipeline

All writes go through `AppRepository.update`, which uses DataStore's serialized
`updateData` transaction. `AppViewModel.mut` then compares the resulting data
with its prior projections:

1. Event/task changes reschedule the single alarm chain.
2. Event/task or widget-visible preference changes refresh Glance widgets.
3. Note-editor keystrokes and unrelated preference writes avoid those costly
   side effects.

Deletes first snapshot the item into `trash`; Undo removes that trash snapshot
while restoring the original list position. Importing a full JSON backup is
the exceptional destructive operation: it validates semantic date/time values,
normalizes unsafe fields, replaces the root document, and resets transient UI
state. Calendar `.ics` import only merges events and never replaces the root.

## Compatibility rules

- Add persisted fields with safe defaults.
- Keep parsing of date/time strings defensive before writing imported data.
- Keep category lookup total: orphaned ids must render through a fallback.
- If a new field affects reminders or widgets, include it in the relevant
  `AppViewModel` change projection.
- Update JSON import normalization and tests whenever a new persisted invariant
  is introduced.
