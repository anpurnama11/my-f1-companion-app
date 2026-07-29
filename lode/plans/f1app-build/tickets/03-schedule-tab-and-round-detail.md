---
id: 03
title: Schedule tab + Round detail
type: task
status: shipped
blocked_by: [01]
owner: ""
---

# 03 — Schedule tab + Round detail

> **Post-build supersession note (Jolpica migration, steps 1–6):** the result
> sources described revisions 2–3 (f1api.dev for Race/Qualifying/FP, hybrid
> f1api.dev + Jolpica standard race-merge, Jolpica alpha for Sprint/SQ) were
> replaced by the migration captured in ADR
> `decisions/0005-session-results-use-two-apis.md` (amended, supersedes 0006):
> Race + Qualifying now come from Jolpica standard; FP/Sprint/SprintQuali from
> Jolpica alpha with the car-number id translator; f1api.dev carries schedule
> + catalogs only. The hybrid race-result source and the f1api race/quali/FP
> extensions/DTOs are deleted. The body below records the state as built at
> the time and is retained for history; see ADR 0005 for the current source map.

**What to build:** the Schedule tab becomes real — upcoming rounds show
session times and past rounds show full podiums (P1/P2/P3). Pull-to-refresh
re-fetches the schedule. The planned RoundDetail expansion has two modes:
upcoming shows the full five-session race-weekend schedule + circuit stats;
past shows circuit stats + per-session result rows and a
`Route.SessionResult` destination. The planned SessionResult screen covers
race, qualifying, practice, sprint, hybrid status/grid handling, and fastest
standouts. The circuit block links to `CircuitDetail` (destination page in
slice 06). Schedule and the Round/SessionResult surfaces ship together in
this ticket; CircuitDetail itself remains the next slice.

**Blocked by:** 01 — Foundation (reuses `Wiring`, `F1Api` patterns/Observables, Navigation 3 detail routes, UX family).

**Status:** `shipped` — Schedule, two-mode Round detail, normalized session
results, sprint support, optional pit-stop standout, and the hybrid race
result source are implemented. Weekend session start labels use device-local
time; elapsed result times remain raw durations.

## Done when

- [x] `Schedule` tab renders upcoming rounds with session times + past rounds with P1/P2/P3 podium
- [x] `GetRoundResultsUseCase` over `/{year}/{round}/race` (object-with-results envelope; results `position` kept String; `time` kept String un-parsed)
- [x] `GetRoundQualifyingUseCase` over `/{year}/{round}/qualy`
- [x] `GetRoundPodiumUseCase` reuses `getRoundResults`, slices `[0..2]`; no extra network call
- [x] `ScheduleViewModel` + `ScheduleScreen` (init-less, pull-to-refresh `NO_CACHE`)
- [x] `RoundDetail(year, round)` route + `RoundViewModel`/`RoundScreen`: basic race and qualifying result blocks, independently-failing loading/error states, and a circuit block linking to `CircuitDetail`
- [x] Past-list podium failure degrades to a retry row, never blanks the whole schedule (shared UX family)
- [x] `RoundDetail` upcoming/past modes with the full weekend schedule, session rows, and circuit stats
- [x] `SessionResult(year, round, session)` route + screen with race/qualifying/practice/sprint tables and standout cards
- [x] `GetPracticeResultUseCase`, sprint use cases, `GetSessionResultUseCase`, and `GetFastestPitstopUseCase`
- [x] Hybrid race-result source and authoritative DNF/DNS/grid/PL handling (revision 3)

Spec cross-ref: `lode/specs/data-layer.md` (Use cases table, Schedule contract, envelope diffs, **Schedule surface shape**), `lode/wayfinder/f1app/past-list.md`.

## Revision 1 — tab switcher (replaces the v1 single-list shape)

**Why:** v1 shipped one scrollable list with two section headers (`Upcoming` / `Past`) under a single `OutcomeContent`. The user wanted the two surfaces differentiated: a **tab switcher** (or segmented control) at the top of the screen, so Upcoming and Past are visually separate and you only see one at a time.

**What changed in the shipped code:**
- `ScheduleScreen` now hosts a `TabRow` (Material 3) or a segmented control at the top with two tabs: **Upcoming** and **Past**.
- The shared `OutcomeContent` for the season splits into two list surfaces, one per tab. The tab the user opens is the one that renders its list; the other tab's state is still alive in the VM (so a tab switch is instant, no re-fetch).
- **Upcoming row shape** — closest to the Homepage §3 card: round number, GP name, date, **circuit image** (new — was missing in v1).
- **Past row shape** — same as Upcoming (round / GP name / date / city / circuit image) but **podium winner** replaces any countdown block (Past rows have no countdown by definition).
- The `LaunchedEffect(race.round) { onLoadPodium() }` per-row fetch stays (lazy per-row from ticket 10 / `past-list.md`). Pull-to-refresh on either tab re-fetches the season + every past podium.
- Per-row failure independence preserved: a Past row's podium error still degrades to a retry row inside the Past tab, never blanks the whole schedule.

**Not changed:** data layer, use cases, routes, VM state shape, `RoundPodium` / `RaceSchedule` / `SessionSlot` models, deep link, widget.

**Status:** [REVISION 1 BUILT] — tab switcher landed on top of v1.

## Done when (revision 1)

- [x] `ScheduleScreen` has a tab/segmented control at the top: **Upcoming** | **Past**
- [x] Upcoming tab renders rows that look like the Homepage §3 card: round, GP name, date, city, circuit image
- [x] Past tab renders rows with the same shape plus a podium winner cell (P1/P2/P3, or retry on per-row failure)
- [x] Switching tabs is instant (state is alive in the VM; no re-fetch)
- [x] Pull-to-refresh re-fetches season + all past podiums
- [x] No countdown block anywhere on Schedule (countdown lives on Homepage §1 / widget)

## Revision 2 — RoundDetail two-mode + SessionResult (design update after screenshots)

**Why:** Screenshots showed a richer RoundDetail (circuit stats, full weekend schedule in upcoming mode, per-session result rows in past mode) and a separate full-session result screen (`SessionResult`) reached by tapping **Results** on a session row.

**What changed in the design:**
- `RoundDetail` is one route with two modes driven by the Race session start time.
  - **Upcoming mode** shows circuit stats (length, laps, turns, top speed) + the five-session race-weekend schedule.
  - **Past mode** shows circuit stats + a **Results** tab with per-session rows (Race, Qualifying, Sprint, Sprint Quali, FP1). Each row has a **Results** action that pushes `Route.SessionResult`.
  - The **Highlights** tab and Driver of the Day are out of scope for v1.
- `Route.SessionResult(year, round, session)` is added.
  - **Race** shows podium chips, Fastest Lap (derived from f1api.dev `fastLap`), Fastest Pitstop (OpenF1 `stop_duration`), and the full grid table with position change arrows (hidden for pit-lane starts), time/status, and points. `Retired` rows show **"DNF"**; `Did not start` rows show **"DNS"**; pit-lane starts (`grid: "0"`) display **"PL"**.
  - **Sprint** uses the same shape as Race.
  - **Qualifying / Sprint Quali** show position + Q1/Q2/Q3 times.
  - **FP1/2/3** show time-ordered fastest-lap times.
- Session results use two APIs: f1api.dev for Race/Qualifying/FP1/2/3, Jolpica alpha for Sprint/Sprint Quali (f1api.dev has no sprint endpoints). See ADR `lode/decisions/0005-session-results-use-two-apis.md`.
- Fastest Pitstop comes from OpenF1 and is hidden when unavailable (pre-2024 US GP).

**Not changed:** Schedule tab shape, deep link, pull-to-refresh behavior, per-row podium retry UX, `CircuitDetail` destination page still in slice 06.

**Status:** [REVISION 2 BUILT] — implemented in `feature/round/` and
`feature/sessionresult/`.

## Revision 3 — Race result status, grid handling, and circuit stats (domain-model lock)

**Why:** Live API data revealed that f1api.dev mislabels Did-Not-Start rows as DNF, and neither f1api.dev nor Jolpica expose elevation. The screenshots and domain interview resolved the remaining status/grid labels.

**What changed in the design:**
- **Race results** now use a **hybrid source**: f1api.dev `/{year}/{round}/race` provides circuit metadata, per-driver `fastLap`, and time/gap strings; Jolpica standard `/ergast/f1/{year}/{round}/results.json` provides the authoritative `status` and `grid`. The two responses are merged by driver number. See ADR `lode/decisions/0006-race-results-hybrid-source.md`.
- **Status labels:**
  - `Retired` → **"DNF"**.
  - `Did not start` → **"DNS"**.
  - `Finished` / `Lapped` → show the f1api.dev `time` string (gap / `+N laps`).
- **Pit-lane starts:** Jolpica `grid: "0"` displays as **"PL"** and the position-change arrow is hidden.
- **DNF/DNS rows still compute the arrow** from `grid` vs `position`.
- **Sprint** uses the same DNF/DNS/PL label logic as Race.
- **Qualifying** and **Sprint Qualifying** skip DNF/DNS labels — neither source exposes a reliable status for qualifying sessions; only Q1/Q2/Q3 times and grid position are shown.
- **Circuit stats card:** elevation is dropped from v1; top speed uses the same OpenF1 all-time max `stSpeed` logic as Homepage §3.

**Not changed:** Schedule tab shape, deep link, pull-to-refresh behavior, per-row podium retry UX, `CircuitDetail` destination page still in slice 06, Jolpica alpha still used for Sprint/Sprint Quali raw data.

**Status:** [REVISION 3 BUILT] — hybrid mapping and result-row presentation are
implemented; Jolpica failures degrade to the f1api.dev result table.

**Done when (revision 3):**
- [x] `RoundDetail` circuit stats card shows length, laps, turns, top speed (no elevation)
- [x] `GetRoundResultsUseCase` is updated to the hybrid f1api.dev + Jolpica standard source
- [x] `RoundResult` domain model carries a `status` field mapped from Jolpica
- [x] Race `SessionResult` renders DNF/DNS labels and "PL" for pit-lane starts
- [x] Race `SessionResult` hides the grid-change arrow for `grid: "0"`
- [x] DNF/DNS rows still compute and display the position-change arrow when a numeric finish position exists
- [x] Sprint `SessionResult` applies the same DNF/DNS/PL logic
- [x] Qualifying and Sprint Qualifying `SessionResult` show only Q1/Q2/Q3 times and grid position
- [x] ADR `0006-race-results-hybrid-source.md` is referenced from the spec and ticket
