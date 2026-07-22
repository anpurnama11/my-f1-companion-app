---
id: 03
title: Schedule tab + Round detail
type: task
status: built
blocked_by: [01]
owner: ""
---

# 03 — Schedule tab + Round detail

**What to build:** the Schedule tab becomes real — upcoming rounds show session times (FP1/FP2/FP3/qualifying/race) and past rounds show full podiums (P1/P2/P3). Pull-to-refresh re-fetches the schedule. Tapping any round opens a `RoundDetail` page showing the race results and qualifying results, plus a circuit block (the block's link into `CircuitDetail` is routed here but the destination page lands in slice 06). Reuses `GetSeasonUseCase` from 01 for the schedule list, adds `GetRoundResultsUseCase` and `GetRoundQualifyingUseCase` (f1api.dev `/{year}/{round}/race` and `/{year}/{round}/qualy`, using their distinct `races: {...}` object-with-results envelope DTO), and `GetRoundPodiumUseCase` which slices `[0..2]` off `GetRoundResults` for the Past-list podium. The round-detail circuit block renders circuit name/length now and gains its `CircuitDetail` link in slice 06.

**Blocked by:** 01 — Foundation (reuses `Wiring`, `F1Api` patterns/Observables, Navigation 3 detail routes, UX family).

**Status:** `[BUILT]` (ticket 03) — Schedule tab + Round detail landed.

## Done when

- [x] `Schedule` tab renders upcoming rounds with session times + past rounds with P1/P2/P3 podium
- [x] `GetRoundResultsUseCase` over `/{year}/{round}/race` (object-with-results envelope; results `position` kept String; `time` kept String un-parsed)
- [x] `GetRoundQualifyingUseCase` over `/{year}/{round}/qualy`
- [x] `GetRoundPodiumUseCase` reuses `getRoundResults`, slices `[0..2]`; no extra network call
- [x] `ScheduleViewModel` + `ScheduleScreen` (init-less, pull-to-refresh `NO_CACHE`)
- [x] `RoundDetail(year, round)` route + `RoundViewModel`/`RoundScreen`: race results, qualifying results, circuit block (link to `CircuitDetail` wired, destination page in 06)
- [x] Past-list podium failure degrades to a retry row, never blanks the whole schedule (shared UX family)

Spec cross-ref: `lode/specs/f1app.md` (Use cases table, Schedule contract, envelope diffs, **Schedule surface shape**), `lode/wayfinder/f1app/past-list.md`.

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