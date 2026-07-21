---
id: 1
title: OpenF1 join uses schedule.qualy.date, not schedule.race.date
type: correction
status: accepted
date: 2027-01-15
supersedes: wayfinder/f1app/tickets/11-research-openf1-join-all-time-top-speed.md
---

# OpenF1 join: `qualyDate` not `raceDate`

## Context

Ticket 11 (closed) researched the OpenF1 `session_key` join and
concluded the join key was `country_name + year + race-date match`
— i.e. f1api.dev's `schedule.race.date` (race day, typically Sunday)
matches OpenF1's Qualifying `date_start` date portion.

That research was wrong. Live probes during ticket 02 build (2027-01-15)
showed:

| f1api.dev field | Sample 2024 Imola | OpenF1 Qualifying `date_start` |
|---|---|---|
| `schedule.qualy.date` (Saturday for normal weekends) | `2024-05-18` | `2024-05-18T14:00:00+00:00` |
| `schedule.race.date` (Sunday) | `2024-05-19` | (no match — race day) |

OpenF1's Qualifying `date_start` is the Qualifying day, which is 1 day
before the race (or 2 days before for sprint weekends). The race day
never matches.

For Bahrain 2025: f1api.dev `qualy.date=2025-04-12`, race `2025-04-13`;
OpenF1 Qualifying `date_start=2025-04-12T16:00:00+00:00`.

For sprint weekends (e.g. Miami 2026, British 2026): Qualifying moves
to Friday — race day is Sunday, so the gap is 2 days.

## Decision

`NextRace` carries `qualyDate: String?` in addition to `raceDate`. The
OpenF1 join in `GetCircuitTopSpeedUseCase` matches on
`OpenF1.dateStart.dateOnly == qualyDate`. The
`f1api.dev.schedule.qualy.date` field is always populated; if the
schedule is empty, `qualyDate` is null and the cell renders empty.

## Why not just match `raceDate - 1 day` or `raceDate - 2 days`?

The sprint/normal distinction lives inside f1api.dev's `schedule` (the
`sprintQualy` / `sprintRace` blocks). `qualyDate` is the right
field — it's the literal Qualifying day for this race. The use case
already has it on hand (inlined in `NextRace`); using a date offset
would re-derive a value the source already gives us.

## Why not a `circuit_short_name` translation map?

The other documented alternative is mapping f1api.dev's `circuitId` to
OpenF1's `circuit_short_name` (24-entry map). It's correct but
maintenance-heavy — every new circuit requires a map update. The
date match is one field, already on hand, and unique per
(year, country).

## Invariant captured

- OpenF1 join date = `schedule.qualy.date`, not `schedule.race.date`.
- The 1-entry `F1API_TO_OPENF1_COUNTRY` fallback (Silverstone
  "Great Britain" → "United Kingdom") still applies — orthogonal
  concern.
- This correction is captured in `lode/wayfinder/f1app/tickets/11-...md`
  (closed_by line) and in the `GetCircuitTopSpeedUseCase` doc comment.

## Cross-references

- [../wayfinder/f1app/tickets/11-research-openf1-join-all-time-top-speed.md](../wayfinder/f1app/tickets/11-research-openf1-join-all-time-top-speed.md) — the research whose conclusion this corrects.
- [../wayfinder/f1app/top-speed.md](../wayfinder/f1app/top-speed.md) — the broader top-speed decision (which still stands).
- [../plans/f1app-build/tickets/02-homepage-section-1-and-section-3.md](../plans/f1app-build/tickets/02-homepage-section-1-and-section-3.md) — the ticket that surfaced the bug.
- `f1/GetCircuitTopSpeedUseCase.kt` — implementation.
- `f1/model/NextRace.kt` — the `qualyDate` field.
