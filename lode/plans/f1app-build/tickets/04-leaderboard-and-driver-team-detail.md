---
id: 04
title: Leaderboard + Driver/Team detail
type: task
status: ready-for-agent
blocked_by: [01]
owner: ""
---

# 04 — Leaderboard + Driver/Team detail

**What to build:** the Leaderboard tab shows current driver and constructor standings with wins and points; tapping a driver row opens `DriverDetail` (headshot slot left empty-text-`team_colour`-swatch for now — imagery lands in 08, team, number, standings snapshot), tapping a team row opens `TeamDetail` (wordmark + standings snapshot; car-imagery hero left as swatch for now — imagery in 08). Adds `GetDriverDetailUseCase` (`/current/drivers` + `/drivers-championship` join) and `GetTeamDetailUseCase` (`/current/teams` + `/constructors-championship` join). Reuses the standings use cases already added in 02 for the Leaderboard rows.

**Blocked by:** 01 — Foundation (reuses `Wiring`, `F1Api`, Navigation detail routes, UX family). Note: 02 also adds the standings use cases, but 04 can be queued after 01 if you prefer — it only depends on the standings endpoints existing, and 02 is not strictly gating the Leaderboard's read path. Kept blocked by 01 for ordering safety.

**Status:** ready-for-agent

- [ ] `Leaderboard` tab renders driver standings + constructor standings (wins, points); rows drill to `DriverDetail`/`TeamDetail`
- [ ] `GetDriverDetailUseCase(id)` joins `/current/drivers` + `/drivers-championship`; `DriverDetail` shows team, number, standings snapshot; headshot slot renders swatch fallback (imagery in 08)
- [ ] `GetTeamDetailUseCase(id)` joins `/current/teams` + `/constructors-championship`; `TeamDetail` shows wordmark + standings snapshot; car-imagery hero renders swatch fallback (imagery in 08)
- [ ] `LeaderboardViewModel`/`Screen` + `DriverViewModel`/`Screen` + `TeamViewModel`/`Screen` (init-less, pull-to-refresh `NO_CACHE`)
- [ ] Empty/error states use the shared UX family from 01

Spec cross-ref: `lode/specs/f1app.md` (Use cases table, Driver/Team detail contracts).