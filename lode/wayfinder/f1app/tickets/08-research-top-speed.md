---
id: 08
title: "Research: top speed per circuit (GAP-A)"
type: research
status: closed
blocked_by: []
owner: ""
closed_by: "Relabel stat to 'Fastest lap' (f1api.dev circuit.lapRecord, no new API). f1api.dev serves no speed (lapRecord is a lap time; race-results fastLap is universally null across 2023-2024); jolpica and Pit Lane F1 also have no speed; OpenF1 /v1/laps st_speed is the only real top-speed source (1+1 calls, ~200 KB, weekend speed-trap reading) — parked for the ticket-04 OpenF1 follow-up, not shipped in the initial build. Brute-force /v1/car_data peak is 21 calls / ~60 MB and is NOT the upgrade path. Research output: lode/wayfinder/f1app/top-speed.md"
---

## Question

The GP Schedule detail screen (section 1) calls for a "top speed" stat per circuit.
f1api.dev serves no speed field — only lap times (`fastLap`, `lapRecord`). Where does
the top-speed figure come from, and is it feasible on a free API?

## Context

- f1api.dev `/circuits/{id}` and the inlined `circuit` block on every race return:
  `circuitName`, `circuitLength`, `numberOfCorners`/`corners`, `firstParticipationYear`,
  `lapRecord`, `fastestLapDriverId`, `fastestLapTeamId`, `fastestLapYear`. No speed.
- f1api.dev race results (`/{y}/{r}/race`) return per-driver `fastLap` (a lap time as
  string), no speed.
- OpenF1 `/v1/car_data?session_key=…&driver_number=…` returns a telemetry stream
  (speed, rpm, gear, drs). Requires the `session_key` join (fetched first from
  `/v1/sessions` or `/v1/meetings`) and a per-driver scan to find the peak.
- Ticket 04 parked OpenF1 wiring as a bounded follow-up — this ticket is one slice.

## Resolution needed

- OpenF1 top-speed: feasible at what effort + rate-limit cost? One peak-speed number
  per circuit requires iterating every driver in the session and scanning their
  `car_data` stream — is that bounded enough to ship, or too heavy for a low-stakes stat?
- Alternative sources: is any free API giving a per-circuit (or per-session) top-speed
  number directly without a telemetry scan?

## Out of scope

- The rest of the OpenF1 enrichment set (driver headshot, weather, race-control flags) —
  those are the ticket-04 follow-up, not this research ticket.
- GAP-B (most wins at circuit) and GAP-C (podium on past list) — separate tickets.

## Default resolution if not investigated

Top-speed stat stays **"Fastest lap"** (relabeled) in the initial build. Re-scoping to
the real top-speed figure is a fresh effort after this ticket closes.
