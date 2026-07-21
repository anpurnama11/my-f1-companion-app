---
id: 02
title: Homepage §1 (favorites nearest-GP) + §3 (top speed)
type: task
status: ready-for-agent
blocked_by: [01]
owner: ""
---

# 02 — Homepage §1 (favorites nearest-GP) + §3 (top speed)

**What to build:** the Homepage fills out its other two sections. §1 is a compact pager of the two favorite drivers + the favorite constructor team + the nearest-date GP; §3 is the nearest-GP circuit card showing the circuit name/brand accent (`Circuits.forId(circuitId)`) and the weekend's top speed. Adds `GetNextRaceUseCase` (`/current/next`), `GetDriversStandingsUseCase`, `GetConstructorsStandingsUseCase`, and `GetCircuitTopSpeedUseCase` (OpenF1 `/v1/sessions` then `/v1/laps` for `max(st_speed)` over the Qualifying session, joined by `country_name + year + race-date match`, with the 1-entry `F1API_TO_OPENF1_COUNTRY` fallback applied only when the literal country returns 0). Stands up `FavoritesCache` (DataStore typed keys `FAV_DRIVER_1/2`, `FAV_TEAM`) and the first-launch default seed (#1 constructor from `GetConstructorsStandings` + that team's two drivers) so §1 is never empty before the user picks. The Homepage ViewModel now combines five use cases with each section failing independently — no composite use case, no blank screen on one source failing. Pull-to-refresh forces `NO_CACHE`. Tapping the §3 circuit card navigates to `CircuitDetail` (that page lands in slice 06; the nav edge is what matters here).

**Blocked by:** 01 — Foundation + Homepage §2 (shares the `Wiring`/HttpClient/Navigation shell/UX family this slice builds on).

**Status:** built (2027-01-15)

- [x] `GetNextRaceUseCase` over `/current/next` (`race: [...]` envelope) → `NextRace` model
- [x] `GetDriversStandingsUseCase` + `GetConstructorsStandingsUseCase` over the two `/current/*-championship` endpoints
- [x] `FavoritesCache` DataStore (typed keys, one atomic `edit`); `HomepageViewModel` reads it for §1
- [x] First-launch default seed: `GetConstructorsStandings` #1 constructor + that team's two drivers written into `FavoritesCache` (empty-only, partial-fill safe)
- [x] `GetCircuitTopSpeedUseCase`: OpenF1 `getOpenF1Sessions(year, countryName, "Qualifying")` joined by `country_name + year + qualyDate match` (**deviation from ticket text: use `schedule.qualy.date`, not `schedule.race.date` — see invariant note below**), then `getOpenF1Laps(sessionKey)` `max(st_speed)`; `F1API_TO_OPENF1_COUNTRY` 1-entry fallback only on literal-0
- [x] §1 pager renders 2 fav drivers + fav team + nearest GP from the cache + `GetNextRace`; §3 renders circuit name/`Circuits.forId` accent + "Top speed" label (not "record")
- [x] Pre-2023 rounds show an empty top-speed cell — no placeholder, no fake dash
- [x] Homepage combines five use cases; each section fails independently (per-section failure surfaces via the shared UX family from 01, never blanks the whole screen)
- [x] Pull-to-refresh on §2/§3 uses `CacheControl.NO_CACHE`
- [x] §3 circuit card navigation edge to `CircuitDetail` route (page itself in slice 06)

**Invariant (deviation from ticket text):** the OpenF1 join uses
`schedule.qualy.date` (Qualifying day, "YYYY-MM-DD"), NOT
`schedule.race.date` (race day). Ticket 11 research claimed
`date_start` matched the race day — live probes show OpenF1's Qualifying
is on the day before the race (or two days before for sprint weekends).
The fix: `NextRace` carries `qualyDate: String?`; the use case matches
`OpenF1.dateStart.dateOnly == qualyDate`. See
`f1/GetCircuitTopSpeedUseCase.kt` doc comment for the live data probes.

Spec cross-ref: `lode/specs/f1app.md` (OpenF1 top-speed specifics, Favorites, Homepage composition), `lode/wayfinder/f1app/top-speed.md`.