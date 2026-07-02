# Bento Calendar — Implementation Spec (for screen agents)

Native Android port of the design handoff in
`C:\Users\matisse\Documents\projects\productivity app\design_handoff_bento_calendar\`.
Read `README.md` there (full design spec) and consult `Bento Calendar.dc.html`
(the interactive prototype — markup at lines 1–277, all logic in the script at
lines 279–701) for exact behavior. **High fidelity: the prototype's px values
are used 1:1 as dp.** Design width 412dp = S25 viewport; use full width, no
fixed frame. The phone's real status bar replaces the prototype's fake one —
never build a fake status bar.

## Project layout (Kotlin, Jetpack Compose, package `com.bento.calendar`)

Root: `C:\Users\matisse\Documents\projects\productivity app\BentoCalendar\`
Sources: `app\src\main\java\com\bento\calendar\`

Already implemented — READ these, do not modify:
- `data/Models.kt` — AppData/EventItem/TaskItem/NoteItem/Prefs (ISO date "YYYY-MM-DD" + "HH:MM" strings), `Cats` (categories w/ colors), `Accents`, `Recur`, string<->java.time bridges (`toDate()`, `toIso()`, `toTime()`, `toHm()`, `toMins()`, `minsToHm()`)
- `data/Recurrence.kt` — `EventItem.occursOn(LocalDate)`, `occurrencesOn(events, date)` (re-dated, sorted by start)
- `data/Seed.kt`, `data/AppRepository.kt` — persistence (don't touch)
- `ui/AppViewModel.kt` — ALL state + actions (see API below)
- `ui/Format.kt` — `Fmt`: `time(hm/LocalTime, use24h)`, `hourLabel(h, use24h)`, `duration(startHm, endHm)`, `countdown(mins)`, `greeting(hour)`, `todayTitle`, `dayShort`, `monthTitle(YearMonth)`, `weekTitle(weekStart)`, `dayTitle`, `dueLabel(dueIso, today)`, `relEdit(ms, now, use24h)`, `editStamp(ms, today, use24h)`, `dow(LocalDate)` (Sunday=0), month/day name lists MN/MS/WD/WS. Plus top-level `startOfWeek(date, monday)`
- `ui/Derive.kt` — `sortedOpenTasks(tasks, today)`, `taskSections(tasks, today)` (Overdue/Today/Upcoming/Someday, empties dropped), `activeReminder(events, today, nowMin, dismissed): ReminderBanner?`
- `ui/theme/Tokens.kt` — `LocalBento` composition local -> `BentoColors` (`bg, tile, bd, line, tx, sub, faint, inp, cbb, scrim, dng, acc, isLight`, `accTint(pct)`), `CreateBlue` (fixed-blue + buttons), `OnCategory` (#12141B text on category blocks), `Category.color` ext, `hexColor()`
- `ui/theme/Type.kt` — `Sora` FontFamily (weights 400/500/600/700)
- `ui/theme/Theme.kt` — `BentoTheme(prefs)`; M3 MaterialTheme is mapped to tokens so M3 pickers/menus look right
- `ui/components/Common.kt` — `Modifier.pressable(onClick)` (0.92 scale press), `Modifier.tap(onClick)` (no ripple), `Modifier.hairlineBottom(color)`, `GBtn` (38dp circle header button, `primary=true` = fixed blue), `SectionLabel(text, count?)`, `Dot(color, size=7dp)`, `BentoCheckbox(checked, onToggle, size=22dp, corner=8dp)` (pop anim built in; mini = size 17/corner 6), `BentoSwitch(on, onToggle)`, `FieldLabel`, `BentoTextField`, `PrimaryButton`, `DangerTextButton`, `TextLink`, `EmptyText`, `BentoSheet(onDismiss){...}` (scrim+slide-up sheet, grab handle included), `FullOverlay{...}`, `CategoryPills(selected, onSelect, includeNone)`, `FieldButton(text, onClick, compact)`, `BentoDateField(value, display, onPick)`, `BentoTimeField(valueHm, display, use24h, onPick)`, `BentoSelectField(value, options: List<Pair<String,T>>, onSelect, compact)`
- `ui/AppRoot.kt` — shell: tab crossfade, TabBar, overlay stack, back handling. Calls the screens listed below.
- `reminders/*` — system notifications (don't touch)

## Screen composables to implement (exact signatures AppRoot expects)

- `ui/today/TodayScreen.kt` — `@Composable fun TodayScreen(vm: AppViewModel, data: AppData, now: LocalDateTime)`
- `ui/calendar/CalendarScreen.kt` — `fun CalendarScreen(vm, data, now)`
- `ui/notes/NotesScreen.kt` — `fun NotesScreen(vm, data, now)`
- `ui/notes/NoteEditorOverlay.kt` — `fun NoteEditorOverlay(vm, data, now)`
- `ui/notes/PinSheet.kt` — `fun PinSheet(vm: AppViewModel)`
- `ui/tasks/TasksScreen.kt` — `fun TasksScreen(vm, data, now)`
- `ui/settings/SettingsOverlay.kt` — `fun SettingsOverlay(vm, data, now)`
- `ui/search/SearchOverlay.kt` — `fun SearchOverlay(vm, data, now)`
- `ui/sheets/CreateSheet.kt` — `fun CreateSheet(vm: AppViewModel)`
- `ui/sheets/EventEditorSheet.kt` — `fun EventEditorSheet(vm, data, now)`
- `ui/sheets/TaskEditorSheet.kt` — `fun TaskEditorSheet(vm, data, now)`
- `ui/theme/Icons.kt` — `object BentoIcons` with `ImageVector` vals (see icon agent brief)

(`vm: AppViewModel, data: AppData, now: java.time.LocalDateTime` throughout.)

## AppViewModel API (complete)

Observable state (Compose snapshot state / StateFlow):
`data: StateFlow<AppData?>`, `now: StateFlow<LocalDateTime>`, `tab: Tab`,
`calView: CalView`, `selDate: LocalDate`, `cursor: YearMonth`,
`searchOpen: Boolean`, `query: String`, `settingsOpen`, `fabOpen`,
`openNoteId: String?`, `unlocked: Set<String>`, `pinCtx: PinCtx?`,
`pinBuf: String`, `pinErr: Boolean`, `evDraft: EventDraft?`,
`tkDraft: TaskDraft?`, `doneOpen: Boolean`, `dismissed: Set<String>`,
`armed: Set<String>` + `isArmed(key)` with keys in `Arm` (NOTE/EVENT/TASK/CLEAR/RESET).

Actions:
`setTab(Tab)`, `openSettings()`, `closeSheets()`, `backPress()`, `hasOverlay()`,
`setCalView(CalView)`, `calPrev()`, `calNext()`, `goToday()`,
`tapMonthCell(LocalDate)` (select; second tap opens Day), `selectDate(LocalDate)`,
`weekStripTap(LocalDate)`, `openFab()`,
`openEvent(EventItem)`, `newEvent()`, `newEventAt(LocalDate, startMin: Int)`,
`updateEventDraft { it.copy(...) }`, `saveEvent()`, `deleteEvent()` (two-tap),
`toggleTask(id)`, `openTask(TaskItem)`, `newTask()`, `updateTaskDraft {}`,
`saveTask()`, `deleteTask()` (two-tap), `toggleDoneOpen()`, `clearCompleted()` (two-tap),
`openNote(id)` (handles PIN flow), `newNote()`, `setNoteTitle(v)`, `setNoteBody(v)`,
`toggleNotePin()`, `toggleNoteLock()`, `deleteNote()` (two-tap), `closeNote()`,
`pinPress(key)` ("0".."9" or "back"), `pinCancel()`, `startSetPin()`, `removePin()`,
`setTheme("dark"/"light")`, `setAccent(hex)`, `toggle24h()`, `toggleMonday()`,
`setRemindDef(Int?)`, `setDurDef(Int)`, `resetApp()` (two-tap),
`openSearch()`, `closeSearch()`, `setQuery(q)`, `dismissReminder(key)`.

Draft types: `EventDraft(id: String?, title, date: LocalDate, start: "HH:MM", end, cat, recur, remind: Int?, loc)`;
`TaskDraft(id: String?, title, due: LocalDate?, cat)`. `PinCtx(mode: PinMode.Set/Enter, then: PinThen, noteTitle)`.

## Conventions

- Get colors: `val c = LocalBento.current`. NEVER hardcode hex except category/`CreateBlue`/`OnCategory` (already in Tokens).
- Text: use `Text` from material3; base style is Sora already; set `fontSize`/`fontWeight`/`color` per README's type scale. Tabular numerals for timestamps: `style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")`.
- Prototype px == dp. Radii/spacing per README (tiles 19dp, gutter 18–20dp, grid gap 10dp, tile padding 15dp...).
- Screen body: `Column(Modifier.fillMaxSize())` with header (`.ahd`: padding 10dp top, 20dp horizontal) then scrollable body (`Modifier.weight(1f).verticalScroll(...)` padding 2dp top/18dp horizontal/14dp bottom). LazyColumn where lists are long is fine, matching paddings.
- Headers: small label 12sp/500 `c.sub` over 21sp/700 title (-0.01em); right-side `GBtn`s 8dp apart; the create + is `GBtn(primary = true)` and ALWAYS fixed blue.
- Rows separated by 1dp hairline (`Modifier.hairlineBottom(c.line)`).
- Interactions: rows/cells `Modifier.tap {}`; buttons/chips/keys `Modifier.pressable {}`.
- All timestamps respect `data.prefs.use24h` via `Fmt`; all week math respects `data.prefs.monday` via `startOfWeek`.
- `today` = `now.toLocalDate()`, `nowMin` = `now.hour * 60 + now.minute`.
- Icons: `Icon(BentoIcons.X, null, tint = ..., modifier = Modifier.size(Ndp))`.
- Do not create files outside your assignment; do not modify shared files. If something seems missing, build it privately inside your own file.
- Kotlin only, no experimental APIs beyond `@OptIn(ExperimentalLayoutApi::class)` (FlowRow) if needed.
