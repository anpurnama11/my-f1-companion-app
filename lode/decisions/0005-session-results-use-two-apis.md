# 0005 — Session results use two free APIs (consolidated)

**Status:** accepted (amended 2026-07-26; this amendment **supersedes 0006** and
partially supersedes the original 0005 framing)

## History

The original 0005 (2026) locked "f1api.dev for Race/Qualifying/FP, Jolpica alpha
for Sprint/Sprint Quali." ADR 0006 then layered a **hybrid** race-result source
(f1api.dev metadata + Jolpica standard `status`/`grid`, merged by driver number).
A later sweep (steps 1–6 of the Jolpica migration) found that Jolpica standard's
`/results.json` and `/qualifying.json` carry the full Ergast richness we needed
(circuit block, per-row `Constructor`, `status`, `grid`, `fastestLap`, time/gap)
for every non-practice session — making the hybrid merge and the f1api.dev
Race/Qualifying endpoints redundant. This amendment records that final state and
retires the hybrid merge (0006) entirely. The original bodies of 0005 and 0006
are retained in their files for the point-in-time record; this file is the
**current** decision.

## Context

`RoundDetail` past mode and `SessionResult` need results for all five sessions in
a GP: Race, Qualifying, Sprint, Sprint Qualifying, and FP1/FP2/FP3.

Live probing established the final source map:

- Jolpica standard `/ergast/f1/{year}/{round}/results.json` — full Ergast race
  richness: circuit block, per-driver `status` (`Finished`, `Lapped`, `Retired`,
  `Did not start`, …), numeric `grid`, `fastestLap`, time/gap strings, points.
  The `grid: "0"` value means a pit-lane start.
- Jolpica standard `/ergast/f1/{year}/{round}/qualifying.json` — full Ergast
  qualifying richness: per-segment `Q1`/`Q2`/`Q3` lap-time strings (null when the
  driver was knocked out), and a `Constructor` object on **every** row (including
  Q1 knockouts — disproving an earlier note that Jolpica quali lacks Constructor
  on knocked-out rows). `position` is the qualifying ordinal (grid-earned).
- Jolpica alpha `/f1/alpha/results/{round_id}/{SR|SQ|FP1|FP2|FP3}/` — the only
  source for Sprint, Sprint Qualifying, and the three Free Practice sessions.
  Carries an **opaque** id namespace (`driver_cAPSXDn9`, `team_LjEBz7Xq`),
  distinct from Ergast canonical.
- f1api.dev has **no** Sprint / Sprint Qualifying / Free Practice result
  endpoints (404); it never housed them.

Only **two** identity namespaces are in play across the whole app — Ergast
canonical (`max_verstappen`, `red_bull`; used by f1api.dev catalogs, Jolpica
standard results, Jolpica pit-stops, favorites, routes) and Jolpica alpha
opaque (FP/SQ/SR rows only, translated at the data seam). The earlier premise of
a third `maxverstappen` (no-underscore) namespace was disproved by live probes —
f1api.dev catalogs use Ergast ids. See
[../architecture/id-namespaces.md](../architecture/id-namespaces.md).

## Decision

Fetch results from Jolpica for **all** sessions; keep f1api.dev only for schedule
and catalogs:

| Session | Source | Endpoint |
|---|---|---|
| Race | Jolpica standard | `/ergast/f1/{year}/{round}/results.json` |
| Qualifying | Jolpica standard | `/ergast/f1/{year}/{round}/qualifying.json` |
| Sprint | Jolpica alpha | `/f1/alpha/results/{round_id}/SR/` |
| Sprint Qualifying | Jolpica alpha | `/f1/alpha/results/{round_id}/SQ/` |
| FP1 / FP2 / FP3 | Jolpica alpha | `/f1/alpha/results/{round_id}/{FP1|FP2|FP3}/` |

Race and Qualifying render straight through (no merge). Sprint, Sprint
Qualifying, and Free Practice rows are translated from the alpha opaque
namespace to Ergast canonical ids via a **car-number bridge**: alpha exposes a
stable, season-unique `car_number` that matches `CurrentDriverDto.number` in the
season-matched f1api `getDrivers(year)` catalog. A `CarNumberTranslator` is
built once per `loadAlpha` call and threads `driverId`/`teamId` through the
per-row mappers, with an opaque-id fallback when the catalog misses.

f1api.dev keeps its role for **schedule + catalogs only**: `getSeason` /
`getCurrent`, `getDrivers(year)` / `getCurrentDrivers` /
`getCurrentTeams`, `getCircuit` / `getCircuitWinners`.
Championship standings moved to Jolpica in
[ADR 0016](0016-standings-source-move-to-jolpica.md).

## Why

- **One source per session, no merge.** Jolpica standard carries the circuit
  block, per-row `Constructor`, authoritative `status`/`grid`, `fastestLap`, and
  time/gap in a single `/results.json` response — the f1api.dev race fetch and
  the driver-number merge from 0006 were buying nothing. One call beats two.
- **Qualifying richness, intact.** The standard quali endpoint carries
  `Constructor` on every row including Q1 knockouts (live-verified), so the
  f1api.dev `/qualy` fetch added nothing and is retired.
- **Converges on one id namespace at the data seam.** Translating alpha opaque
  ids to Ergast canonical in `loadAlpha` means favorites keys, routes, pit-stop
  joins, and driver/team detail lookups all key on the same namespace —
  everything aligns in one move. The two-namespace finding (no third
  `maxverstappen` namespace) made this a single translation, not two.
- **Keeps "free API or not built."** Both sources are free; nothing new is paid.
- **f1api.dev stays where it's already canonical and cached.** Schedule + the
  driver/team/circuit catalogs remain on f1api.dev — they were never the cost
  center, and the catalog `getDrivers(year)` is reused as the translator's
  bridge (HttpCache-shared, so the cost is one cached call on top of the alpha
  round-id + results fetches).

## Consequences

- `GetRoundResultsUseCase` makes **one** Jolpica standard call (was two — hybrid
  merge retired). `RoundResult.status` is mapped straight from Jolpica; UI labels
  are unchanged: `Finished`/`Lapped` → time string, `Retired` → **"DNF"**,
  `Did not start` → **"DNS"**, `grid: "0"` → **"PL"** with the change arrow hidden.
- `GetRoundQualifyingUseCase` makes one Jolpica standard call; `Constructor` is
  present on every row. No DNF/DNS labels on quali/SQ rows (no reliable status
  for qualifying sessions).
- `GetPracticeResultUseCase`, `GetSprintResultUseCase`,
  `GetSprintQualifyingResultUseCase` share `loadAlpha`, whose `when(session)`
  is exhaustive over `SR`/`SQ`/`FP1`/`FP2`/`FP3`. An invalid filter throws
  `IllegalArgumentException("Invalid session filter")`, caught and surfaced as
  a user-facing "Session is unavailable" via message-match (other IAEs, e.g.
  `SerializationException`, surface their real message).
- `loadAlpha` fetches the season-matched `getDrivers(year)` catalog (try/catch
  rethrowing only `CancellationException`), builds `CarNumberTranslator`, and
  threads it through `toRoundResult`/`toQualifyingResult`/`toPracticeResult`
  with opaque-id fallback. **Team-id known limitation:** the catalog is a
  season driver list, not a round roster, so the resolved `teamId` is the
  driver's *season* team — wrong for reserve/substitute drives or mid-season
  team changes. Inert today (no UI links to `TeamDetail` from FP/SQ/SR rows);
  tracked for a future team-name → canonical-team translator if team deep links
  land on those rows.
- The dead f1api.dev result code is removed (step 5): the `getRoundResults` /
  `getRoundQualifying` / `getPracticeResults` extensions and the
  `RoundResultsResponseDto` / `RoundQualifyingResponseDto` / `PracticeResponseDto`
  DTOs. `getDrivers(year)` (season-matched catalog) is the new f1api result-
  adjacent call, used as the translator's bridge.
- The pit-stop join (`driverForPitstop` on `SessionResult`) only renders for
  `SessionType.Race`, whose results come from Jolpica standard (Ergast ids) —
  aligned with `/pitstops.json` (also Ergast). Sprint/SQ pit-stops are not
  fetched, so the alpha translator has no pit-stop alignment work.
- Race `SessionResult` still derives Fastest Lap from the Jolpica `fastestLap`
  block and Fastest Pitstop from Jolpica `/pitstops.json` (duration; hidden when
  unavailable, e.g. pre-2024 rounds).

## Reference

- [../architecture/id-namespaces.md](../architecture/id-namespaces.md) — the two
  namespaces, the car-number bridge, the team-id limitation, and the disproof of
  the `maxverstappen` premise.
- [0006-race-results-hybrid-source.md](0006-race-results-hybrid-source.md) —
  superseded. Retained for the point-in-time record of the hybrid merge.
- [../summary.md](../summary.md), [../terminology.md](../terminology.md),
  [../specs/data-layer.md](../specs/data-layer.md) — consume this decision.