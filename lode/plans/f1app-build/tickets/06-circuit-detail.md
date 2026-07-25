---
id: 06
title: Circuit detail (most-wins-at-circuit)
type: task
status: shipped
blocked_by: [02, 03]
owner: ""
shipped_at: 2026-07-25
---

# 06 — Circuit detail (most-wins-at-circuit)

**What to build:** the `CircuitDetail(circuitId)` page — the home for the
circuit-scoped research stat. Renders circuit metadata (f1api.dev
`/circuits/{circuitId}`: length, corners, first-GP year, all-time lap
record with attribution) and adds `GetCircuitMostWinsUseCase` for the
all-time most-winning driver + team at that circuit (jolpica
`/circuits/{id}/results/1.json`, ~25KB, one call, client-aggregated top
driver + top team; `driverId`/`constructorId` match f1api.dev's
namespace; only `circuitId` needs the 5-entry `F1API_TO_JOLPICA_CIRCUIT`
translation map). Wires the `CircuitDetail` navigation edge that slice 03
left dangling: the RoundDetail circuit block now resolves to the
`CircuitDetail` page (the Homepage §3 nearest-GP card that the original
ticket text referenced was removed by shipped ticket 10 alongside
OpenF1).

**Blocked by:** 02 — Homepage §3 (favorites + nearest-GP cards, the
nearest-GP card later cut by ticket 10); 03 — Round detail (circuit
block nav edge).

**Status:** shipped (2026-07-25)

## Done when

- [x] `GetCircuit(f1apiCircuitId)` extension on `F1Api.kt` for
      `/circuits/{circuitId}` metadata; differs from `/current*` shape
      (`circuitLength` is `Int` here, not `"7004km"` — use as-is, but
      convert to `Double` km at the seam)
- [x] `GetCircuitMostWinsUseCase(f1apiCircuitId)`: jolpica
      `GET /circuits/{id}/results/1.json`, client aggregate top driver +
      top team; 5-entry `F1API_TO_JOLPICA_CIRCUIT` translation
- [x] `CircuitDetail` page: metadata (length, corners, first-GP year,
      lap record + attribution) + most-wins driver/team
- [x] RoundDetail circuit block now resolves to the `CircuitDetail` route
      (the `Route.CircuitDetail` entry in `NavShell` replaces the
      placeholder)
- [x] Edge cases (circuit not in translation map, jolpica empty, missing
      lap-record attribution) degrade via the shared UX family — no
      composite use case, two independent `SectionUiState` atoms per
      ADR 0002

## Scope fix vs the original ticket text

The ticket text and the original lode references to a "Top speed" line
on this page are **stale**: ticket 10 (Remove OpenF1 runtime
dependency) deliberately removed the `GetCircuitTopSpeedUseCase` and
made "Top speed is absent from v1 rather than replaced by an
unsupported metric" an explicit invariant. This implementation does
not add a Top speed cell, and does not re-introduce any OpenF1
dependency. The terminology entry for `CircuitDetail` is updated
alongside this ticket (see `lode/terminology.md`).

The original ticket's "Homepage §3 circuit card" navigation edge
referenced the §3 nearest-GP card that ticket 10 also removed; §3
today is favorites only, with no circuit nav edge to add. The
RoundDetail circuit block (ticket 03) is the only remaining entry
point to `CircuitDetail`; that edge was already wired and just
needed the placeholder page to be replaced by the shipped one.

Spec cross-ref: `lode/specs/f1app.md` (Circuit metadata, jolpica
extension, ID translation maps), `lode/wayfinder/f1app/circuit-most-wins.md`.