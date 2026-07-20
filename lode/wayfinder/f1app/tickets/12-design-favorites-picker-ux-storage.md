---
id: 12
title: "Favorites picker UX + storage (My Team / Homepage §1)"
type: decision
status: closed
blocked_by: [05]
owner: ""
---

> **RE-OPENED** — was prematurely marked closed without a live grilling pass.
> Grilled 2027-01-11. Decisions below are human-locked.

## Question

Homepage §1 is a sliding pager of **two favorite drivers + one favorite
team + the nearest-date GP**. Ticket 03's data layer names the use cases
that *read* the favorites (pick the favorited rows from
`GetDriversStandings` / `GetConstructorsStandings`), but the user has to
*set* them somewhere. Where, and what's the storage contract?

## Decisions (resolved this session)

- **In MVP?** Yes. Favorites are part of the v1 slice.
- **Top-level nav is now 4 tabs:** Homepage, Schedule, Leaderboard,
  **My Team** (rightmost). Amends ticket 05's "3 top-level navs".
- **Homepage §1 stays as-is** — the favorite pager (2 drivers + 1 team +
  nearest GP). My Team is a *second*, dedicated surface over the same
  `FavoritesCache`; Homepage shows the compact view, My Team is the
  management view.
- **My Team tab contents:** 2 favorite drivers + 1 favorite constructor
  team (three slots). Nearest-GP card lives in Homepage §3, not here.
- **First-launch default:** seed `FavoritesCache` with the #1 constructor
  in `GetConstructorsStandings` + that team's two drivers (top two driver
  rows whose `teamId == favorited team`) so neither surface is empty on
  first launch.
- **Driver ↔ team decoupled:** the two favorited drivers need not be from
  the favorited constructor (independent picks).
- **Picker surface = My Team itself.** No separate onboarding route, no
  star/pin on Driver/Team detail screens. Tapping a filled slot opens a
  **ModalBottomSheet** to choose or replace. Full-screen and inline-expand
  variants prototyped and rejected (see below).
- **3rd-pin behavior:** user-driven **replace**, not auto-evict-oldest,
  not block. The user explicitly selects which slot to replace via the
  picker. A driver `id` can occupy at most one of the two driver slots; a
  team `id` is unique in the single team slot.
- **Storage contract:** `FavoritesCache` — `DataStore<Preferences>` with
  typed keys `FAV_DRIVER_1: String`, `FAV_DRIVER_2: String`,
  `FAV_TEAM: String` (no timestamp keys; explicit replace makes them
  unnecessary). One atomic `edit` block, mirroring `NextRaceCache`.
  Backed through `Wiring`, read by HomepageViewModel + MyTeamViewModel.

## UX prototype

Three variants were built as a throwaway `MainActivity` prototype and
evaluated on-device:

| Variant | Mechanism | Verdict |
|---|---|---|
| **A** | `ModalBottomSheet` — tap slot, sheet slides up, pick, dismiss | **Chosen** — compact, no new route, reuses Leaderboard list composables |
| B | Full-screen selection page with back button | Rejected — too heavy for a slot pick |
| C | Inline expand — picker list drops below tapped slot | Rejected — crowded on small screens |

## Context

- Homepage §1 spec: `lode/wayfinder/f1app/homepage.md`.
- Ticket 03's `NextRaceCache` precedent: typed DataStore keys in one atomic
  `edit` block, no JSON blob. Favorites store mirrors it.
- Navigation 7 routes (ticket 05): `Homepage` (start), `Schedule`,
  `Leaderboard`, `DriverDetail(id)`, `TeamDetail(id)`, `RoundDetail(...)`.
  No "settings" or "onboarding" route exists.
- Standings rows link to `DriverDetail` / `TeamDetail` — natural pick surface
  if we ever add a star/pin there as a follow-up.

## Out of scope

- Homepage §2 (season progress) and §3 (nearest GP) — no favorites involved.
- Onboarding flow for any other purpose — this ticket is favorites-only.
- Cloud sync of favorites — local DataStore only. KMP port can revisit.
- Star/pin on Driver/Team detail screens — rejected; My Team is the pick surface.
