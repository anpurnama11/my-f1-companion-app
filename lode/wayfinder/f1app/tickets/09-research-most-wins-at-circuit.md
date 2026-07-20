---
id: 09
title: "Research: most wins (team + driver) at circuit (GAP-B)"
type: research
status: closed
blocked_by: []
owner: ""
closed_by: "Ship via jolpica /circuits/{id}/results/1.json (1 call, ~25KB, 1h server cache; client-aggregated top driver + top team). Canonical Driver.driverId/Constructor.constructorId match f1api.dev → free routing to DriverDetail/TeamDetail. 5-entry f1api.dev→jolpica circuitId translation map (austin→americas, gilles_villeneuve→villeneuve, hermanos_rodriguez→rodriguez, lusail→losail, montmelo→catalunya); the other 19 inlined IDs match. Pit Lane F1 documented as recovery fallback (same shape, names only — fails retired-driver link). OpenF1 out (2023+ only). Multi-source confirmed (ticket 04 reopen): JOLPICA_BASE joins F1API_BASE in f1/data/F1Api.kt. Research output: lode/wayfinder/f1app/circuit-most-wins.md"
---

## Question

The GP Schedule detail's circuit-stats block calls for "who wins the most from team,
then driver" at that circuit. f1api.dev has no historical aggregation endpoint. What
free source provides this, and at what effort?

## Context

- Ticket 04 **reopened to multi-source** (2026-07-16). OpenF1 + jolpica are now
  wiring-allowed in the initial build, alongside f1api.dev, for stats f1api.dev
  cannot serve. This ticket is no longer gated on "adds a 3rd API" — that's the
  new design. One `HttpClient`, per-request base URLs (`JOLPICA_BASE` etc.), no
  `jolpica/` package (repository methods in `f1/`).
- Verified against f1api.dev: `/circuits/{id}` returns the same fields already inlined
  in every race's `circuit` block (name, length, corners, first-year, lap record).
  No winner aggregation.
- OpenF1 `/v1/results?session_key=…&location=…` gives per-session results, not
  historical aggregation; building "most wins at circuit X" from it means fetching
  every historical session at that circuit and tallying — high call count, low free-tier
  safety.
- jolpica (API, the Ergast successor) exposes historical results and standings and is the
  likely clean source. Now in-scope; no longer "outside the ticket-04 single-source
  decision."
- Ticket 08's research surfaced Pit Lane F1 (`pitlanef1.com/circuits/{slug}`)
  serves per-circuit `most_wins` + `winners_timeline` free (CC-BY-4.0). A
  candidate alongside jolpica — compare call cost + schema cleanliness.
- This is the one gap a second API (OpenF1) does NOT solve on its own; it
  needs either jolpica directly, Pit Lane F1, or a heavy OpenF1 aggregation.

## Resolution needed

- jolpica feasibility: does it expose "most wins at circuit X" directly, or does it still
  require fetching every historical round at that circuit and tallying client-side?
- If aggregation is client-side regardless of source, what is the lazily-correct call:
  precompute per circuit on demand + cache, or drop the stat?
- Does this belong as a 3rd API alongside f1api.dev, or stay parked until justified?

## Out of scope

- GAP-A (top speed) — ticket 08.
- GAP-C (podium on past list) — ticket 10.
- General OpenF1 enrichment (headshot/weather/flags) — ticket-04 follow-up.

## Default resolution if not investigated

Stat stays **dropped** from the initial Schedule-detail circuit-stats block. Re-scoping
to add it requires sourcing and a caching strategy.
