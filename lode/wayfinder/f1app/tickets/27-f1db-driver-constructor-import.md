---
id: 27
title: "F1DB driver + constructor + team-facts import (build-time)"
type: task
status: closed
blocked_by: []
owner: agent
closed_by: "Planned and recorded as build ticket 12. Build-time Python script alongside `tools/f1db/import-circuit-artwork.py`; generates `DriverCatalog.kt` + `ConstructorCatalog.kt` + `TeamSeasonalFacts.kt` with all aggregates pre-computed at build time. F1DB v2026.10.1 pin; CC BY 4.0 attribution header in KDoc. Aggregation rules per ticket 26 — race-only counts via `races-race-results.json` (sprint rounds filtered, NOT the `total*` fields on `f1db-drivers.json` which include sprint); DNF = `positionNumber == null AND reasonRetired != null` (stricter than the ticket 26 quick summary; the api-wrangling R9 Antonelli 2026 'Spin' edge case at position 14 requires this to hit the screenshot's 1 DNF). Implementation contract: build ticket 12. No new fog."
---

## Question

Extend `tools/f1db/import-circuit-artwork.py` to also generate the
Kotlin catalogs that the redesigned DriverDetail/TeamDetail screens
need (ticket 26's data split). Build-time only; generated artifacts
are checked in, no runtime network.

## Scope

- New generated file: `DriverCatalog.kt` with all-time + per-season
  aggregates (podiums, poles, DNFs, top10s, fastest-laps, first
  entry, first win, world championships, races entered — race-only)
  for every F1 driver.
- New generated file: `ConstructorCatalog.kt` with the same shape,
  scoped to constructors.
- New generated file: `TeamSeasonalFacts.kt` with chassis, power
  unit, base country for the current season (2026), sourced from
  `seasons/{year}/entrants.yml` resolved via `chassis.yml` and
  `engine-manufacturers.yml`. 3 fields only — no city, no principal
  (dropped per ticket 26).
- All aggregates pre-computed at build time. The output files are
  generated artifacts, not hand-edited.
- Add F1DB CC BY 4.0 attribution header to each generated file
  (KDoc on the top-level `object`).
- Wire the three catalogs into the existing `Wiring` DI graph
  (Hilt-free manual DI per ADR 0001).

## Inputs

- F1DB v2026.10.1 (or later) `splitted/{drivers,constructors}.json`.
- F1DB v2026.10.1 (or later) `splitted/races-race-results.json`.
- F1DB v2026.10.1 (or later)
  `splitted/seasons-{drivers,constructors}.json`.
- F1DB v2026.10.1 (or later) YAML source:
  `seasons/{year}/entrants.yml`, `chassis.yml`,
  `engine-manufacturers.yml`.
- Existing `tools/f1db/import-circuit-artwork.py` — script pattern
  to follow (Python, F1DB vX.Y.Z tag pinning, attribution header
  convention, output path under the project source tree).

## Aggregation rules (per ticket 26)

- **DNF** = `reasonRetired != null` on `races-race-results.json`.
- **Top10** = `positionNumber <= 10` on `races-race-results.json`.
- **First entry** = first row in `races-race-results.json` sorted
  by (year, round) for the given driver/constructor.
- **First win** = first row where `positionText == "1"` in the same
  sorted list.
- **Races entered (all-time "Grands Prix")** = race-only count
  (sprint rounds filtered).
- **World Championships** = `totalChampionshipWins` on the driver /
  constructor row in `{drivers,constructors}.json` (use this if
  present; else fall back to counting `positionNumber == 1` in
  `seasons-{drivers,constructors}-standings.json`).

## Acceptance

- `DriverCatalog.kt`, `ConstructorCatalog.kt`, and
  `TeamSeasonalFacts.kt` exist under the project source tree
  (path TBD — follow the existing circuit artwork output path).
- Antonelli 2026 stats (computed in this research: 6W / 8P / 6
  poles / 1 DNF / 183 race points vs 204 championship points —
  sprint delta) match the script's output.
- Mercedes 2026 chassis (F1 W17) + power unit (Mercedes) + base
  country (Germany) match the script's output.
- `Wiring` exposes the three catalogs as singletons.
- `Wiring` test (or similar) confirms the catalogs are reachable
  from the composition root.

## Out of scope

- The UI rewrite (ticket 29). This ticket generates the data; the
  UI is a separate concern.
- The Wikipedia REST extension (ticket 28). Independent of this
  ticket.
- Updating F1DB to a new release. The script tags to v2026.10.1;
  bump when the user approves.

## Cross-references

- [26 — GAP-F research](../tickets/26-research-gap-f-detail-redesign.md) —
  the data source split this ticket implements.
- [driver-team-detail.md](../../driver-team-detail.md) — full field
  inventory, rules, implementation outline.
- [driver-team-detail-api-wrangling.md](../../driver-team-detail-api-wrangling.md) —
  per-source payload shapes used by the script.
- [f1db-data.md](../../f1db-data.md) — existing F1DB coverage;
  precedent for the build-time import pattern.
- `tools/f1db/import-circuit-artwork.py` — script pattern to
  follow.
- [ADR 0009](../../decisions/0009-remove-openf1-runtime-dependency.md) —
  F1DB is build-time, not runtime, to keep the runtime source graph
  minimal.

## Resolution

**Planned and recorded as build ticket 12** —
[`lode/plans/f1app-build/tickets/12-f1db-driver-constructor-catalog-import.md`](../../../plans/f1app-build/tickets/12-f1db-driver-constructor-catalog-import.md).
The build ticket is the implementation contract; the work is downstream
of this wayfinder resolution. Key decisions locked here:

### Script structure — sister script, not extension

A new `tools/f1db/import-driver-constructor-catalog.py` runs **alongside**
the existing `tools/f1db/import-circuit-artwork.py`. The artwork script
is SVG→WebP rendering (Cairo/qlmanage + cwebp/ffmpeg); the catalog
script is JSON/YAML→Kotlin generation (Python `json` + `PyYAML`).
Different concerns, different deps, different execution patterns. Sharing
one script would conflate two unrelated workflows and force a single
revision pin to drag both forward on every bump.

### F1DB revision pin — separate file

`tools/f1db/catalog-revision.txt` holds the catalog pin (v2026.10.1, the
research revision). The artwork script keeps its own
`tools/f1db/revision.txt` (v2026.0.1). One pin per script — the artwork
shape (SVG list) evolves much less often than the per-round JSON
schema. The catalog script reads its own pin; never re-reads the
artwork pin.

### Output path

`app/src/main/java/com/anpurnama/f1_app/f1/data/{DriverCatalog,
ConstructorCatalog,TeamSeasonalFacts}.kt` — alongside the existing
`F1Api.kt` / `Dtos.kt` / `DriverImage.kt` / `TeamImage.kt` (the
package already holds build-time-style data files).

### Catalog object shape — three singletons, `forId` accessor

Each generated file is a top-level `object` with a `forId(id): T?`
accessor, mirroring the existing `CircuitArtwork` and `TeamColors`
`object` patterns. `Wiring` exposes the three object references as
`val` properties; use cases take the object as a constructor parameter
and call `catalog.forId(id)`. No DI graph complexity; object references
are singletons by definition.

### Race-only filter — which file, not which field

**All-time aggregates are computed from `f1db-races-race-results.json`
(the main race results file), NOT from the `total*` fields on
`f1db-drivers.json` / `f1db-constructors.json`.** The `total*` fields
include sprint rounds; the screen displays race-only "Grands Prix"
counts to match the screenshot acceptance. Sprint data lives in a
separate `f1db-races-sprint-race-results.json` file; the script does
not read it. This is the implementation contract that gives
"Mercedes all-time: 339 GPs / 130 wins / 211 podiums / 146 poles /
112 fastest laps" (the screenshot numbers, not the F1DB `total*`
upper bounds of 351/139/323/153/121).

### DNF rule — the strict version

**`reasonRetired != null AND positionNumber == null`** — the ticket
text's `reasonRetired != null` is the quick summary. The api-wrangling
doc's R9 Antonelli 2026 edge case (a "Spin" retirement that was still
classified at positionNumber 14) shows the loose rule counts
classified retirements as DNFs. The strict rule (also excludes
classified DSQ rows) hits the acceptance of "Antonelli 2026: 1 DNF"
(R7 Collision only; not R9 Spin). The same rule applies to top10s
(`positionNumber != null AND positionNumber <= 10` — classified only).

### Out of scope (locked by ticket 26 + this resolution)

- The Wikipedia REST extension — wayfinder ticket 28, separate
  build ticket. Independent of this work.
- The DriverDetail / TeamDetail UI rewrite — wayfinder ticket 29,
  blocked by this ticket and 28. The use case join (F1DB catalog →
  f1api.dev detail) lands in that build ticket, not here.
- Bumping F1DB to a newer release. The script pins to v2026.10.1;
  bump when the user approves.
- Base city + team principal — dropped from the new screen per ticket
  26. The TeamSeasonalFacts catalog therefore carries 3 fields only
  (chassis, power unit, base country); no city, no principal.

### Cross-references (this resolution)

- [build ticket 12](../../../plans/f1app-build/tickets/12-f1db-driver-constructor-catalog-import.md)
  — the full implementation contract. The ticket is `ready` and
  self-contained; pickable by any future session without re-reading
  this wayfinder ticket.
- [ADR 0012](../../../decisions/0012-gap-f-detail-page-data-sources.md)
  — the source split (F1DB build-time + Wikipedia REST + f1api.dev).
- [driver-team-detail.md](../../driver-team-detail.md) — field
  inventory + rules.
- [driver-team-detail-api-wrangling.md](../../driver-team-detail-api-wrangling.md)
  — per-source payload shapes + computed Antonelli/Mercedes checks +
  the R9 classified-retirement edge case.
- [f1db-data.md](../../f1db-data.md) — existing F1DB coverage + the
  build-time import precedent.
- [`tools/f1db/import-circuit-artwork.py`](../../../tools/f1db/import-circuit-artwork.py)
  — sister-script pattern.
- [ADR 0009](../../../decisions/0009-remove-openf1-runtime-dependency.md)
  — F1DB is build-time, not runtime.
