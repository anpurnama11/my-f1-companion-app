# 0005 — Session results use two APIs

**Status:** accepted

## Context

`RoundDetail` past mode and the new `SessionResult` screen need results for all five sessions in a GP: Race, Qualifying, Sprint, Sprint Qualifying, and FP1/FP2/FP3.

Research showed:

- f1api.dev provides `/{year}/{round}/race`, `/qualy`, `/fp1`, `/fp2`, and `/fp3`.
- f1api.dev does **not** provide Sprint or Sprint Qualifying results endpoints (404).
- Jolpica alpha provides `/f1/alpha/results/{round_id}/{session_filter}/` with filters `R`, `Q`, `SQ`, `SR`, `FP1`, `FP2`, `FP3`, but it requires resolving a `round_id` first.

## Decision

Keep f1api.dev as the primary source for schedule and for Race, Qualifying, and FP1/FP2/FP3 results. Use Jolpica alpha **only** for Sprint and Sprint Qualifying results.

## Why

- Matches the project rule: "free API or not built." Both sources are free.
- Minimizes change to the existing configuration: race/qualy/FP endpoints and DTOs stay on f1api.dev.
- Avoids migrating every session result to Jolpica alpha, which would require a `round_id` lookup and a full schema rewrite.
- The only new integration is the two missing sprint sessions, scoped to a single use case.

## Consequences

- `SessionResult` must branch on `SessionType`: f1api.dev for most sessions, Jolpica alpha for Sprint/Sprint Quali.
- `RoundDetail` past mode needs to know whether a GP has a sprint (from f1api.dev `schedule.sprintQualy.date != null`) so it can render the correct session rows.
- Jolpica alpha is an alpha endpoint; contract changes are possible. We keep the integration narrow so it can be replaced if a better source appears.
