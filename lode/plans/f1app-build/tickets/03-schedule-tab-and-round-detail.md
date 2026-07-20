---
id: 03
title: Schedule tab + Round detail
type: task
status: ready-for-agent
blocked_by: [01]
owner: ""
---

# 03 — Schedule tab + Round detail

**What to build:** the Schedule tab becomes real — upcoming rounds show session times (FP1/FP2/FP3/qualifying/race) and past rounds show full podiums (P1/P2/P3). Pull-to-refresh re-fetches the schedule. Tapping any round opens a `RoundDetail` page showing the race results and qualifying results, plus a circuit block (the block's link into `CircuitDetail` is routed here but the destination page lands in slice 06). Reuses `GetSeasonUseCase` from 01 for the schedule list, adds `GetRoundResultsUseCase` and `GetRoundQualifyingUseCase` (f1api.dev `/{year}/{round}/race` and `/{year}/{round}/qualy`, using their distinct `races: {...}` object-with-results envelope DTO), and `GetRoundPodiumUseCase` which slices `[0..2]` off `GetRoundResults` for the Past-list podium. The round-detail circuit block renders circuit name/length now and gains its `CircuitDetail` link in slice 06.

**Blocked by:** 01 — Foundation (reuses `Wiring`, `F1Api` patterns/Observables, Navigation 3 detail routes, UX family).

**Status:** ready-for-agent

- [ ] `Schedule` tab renders upcoming rounds with session times + past rounds with P1/P2/P3 podium
- [ ] `GetRoundResultsUseCase` over `/{year}/{round}/race` (object-with-results envelope; results `position` kept String; `time` kept String un-parsed)
- [ ] `GetRoundQualifyingUseCase` over `/{year}/{round}/qualy`
- [ ] `GetRoundPodiumUseCase` reuses `getRoundResults`, slices `[0..2]`; no extra network call
- [ ] `ScheduleViewModel` + `ScheduleScreen` (init-less, pull-to-refresh `NO_CACHE`)
- [ ] `RoundDetail(year, round)` route + `RoundViewModel`/`RoundScreen`: race results, qualifying results, circuit block (link to `CircuitDetail` wired, destination page in 06)
- [ ] Past-list podium failure degrades to a retry row, never blanks the whole schedule (shared UX family)

Spec cross-ref: `lode/specs/f1app.md` (Use cases table, Schedule contract, envelope diffs), `lode/wayfinder/f1app/past-list.md`.