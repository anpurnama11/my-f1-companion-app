---
id: 06
title: Circuit detail (most-wins-at-circuit + top-speed reuse)
type: task
status: ready-for-agent
blocked_by: [02, 03]
owner: ""
---

# 06 — Circuit detail (most-wins-at-circuit + top-speed reuse)

**What to build:** the `CircuitDetail(circuitId)` page — the home for the two circuit-scoped research stats. Renders circuit metadata (f1api.dev `/circuits/{circuitId}`), reuses `GetCircuitTopSpeedUseCase` from slice 02 for the "Top speed" line, and adds `GetCircuitMostWinsUseCase` for the all-time most-winning driver + team at that circuit (jolpica `/circuits/{id}/results/1.json`, ~25KB, one call, client-aggregated top driver + top team; `driverId`/`constructorId` match f1api.dev's namespace; only `circuitId` needs the 5-entry `F1API_TO_JOLPICA_CIRCUIT` translation map). Wires the `CircuitDetail` navigation edges that slices 02 and 03 left dangling: the Homepage §3 circuit card and the RoundDetail circuit block now actually navigate here.

**Blocked by:** 02 — Homepage §3 (circuit card nav edge + `GetCircuitTopSpeedUseCase` already exist); 03 — Round detail (circuit block nav edge exists).

**Status:** ready-for-agent

## Done when

- [ ] `GetCircuit(id)` extension on `F1Api.kt` for `/circuits/{circuitId}` metadata; differs from `/current*` shape (`circuitLength` is `Int` here, not `"7004km"` — use as-is)
- [ ] `GetCircuitMostWinsUseCase(f1apiCircuitId)`: jolpica `GET /circuits/{id}/results/1.json`, client aggregate top driver + top team; 5-entry `F1API_TO_JOLPICA_CIRCUIT` translation
- [ ] `CircuitDetail` page: metadata + "Top speed" (reuse 02 use case) + most-wins driver/team
- [ ] Homepage §3 circuit card and RoundDetail circuit block now resolve to the `CircuitDetail` route
- [ ] Edge cases (circuit not in translation map, jolpica empty) degrade via the shared UX family

Spec cross-ref: `lode/specs/f1app.md` (Circuit metadata, jolpica extension, ID translation maps), `lode/wayfinder/f1app/circuit-most-wins.md`.