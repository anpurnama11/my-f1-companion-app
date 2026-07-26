---
id: 26
title: "GAP-F research — DriverDetail/TeamDetail redesign data sources"
type: research
status: closed
blocked_by: []
owner: ""
closed_by: "Free sources cover every new field. F1DB build-time for stats + team facts (chassis/PU/base country); Wikipedia REST for About (CC BY-SA); bar chart + base city + team principal dropped; all-time = race-only (sprint rounds filtered). Implementation: 3 follow-up tickets 27/28/29. Research output: driver-team-detail.md + driver-team-detail-api-wrangling.md"
---

## Question

What data sources can serve the new fields on the redesigned
DriverDetail/TeamDetail screens (first entry, first win, world
championships, chassis, power unit, team base, "About" biography,
per-season + all-time aggregates) without violating the project's
"if not covered by a free API, it's not built" rule or ADR 0009 (no
new OpenF1 runtime dependency)?

## Context

Current `feature/driver/DriverScreen.kt` and
`feature/team/TeamScreen.kt` are join-only minimal surfaces. The
redesign adds ~12 new fields per screen across two tabs (current
season + all-time). 5 reference screenshots are the design spec
(Antonelli DriverDetail + Mercedes TeamDetail, both tabs). f1api.dev
supplies the already-wired fields. ADR 0009 forbids adding OpenF1.

## What was probed

- f1api.dev (`/current/{drivers,teams}` + `/current/{drivers,constructors}-championship`) — no stats, no biography, no chassis/PU/base.
- Jolpica ergast (`/drivers/{id}/`, `/constructors/{id}/`, `/results.json`, etc.) — no biography, no chassis/PU/base.
- Jolpica alpha (`/f1/alpha/core/{teams,drivers}/`) — same shape; alpha tree still stabilizing per issue #304.
- F1DB v2026.10.1 `splitted/*.json` — per-round + per-season + career aggregates. Missing per-season chassis/PU mapping.
- F1DB v2026.10.1 YAML (`seasons/{year}/entrants.yml` + `chassis.yml` + `engine-manufacturers.yml`) — per-season chassisId, engineManufacturerId, countryId for all 11 2026 teams.
- Wikipedia REST (`/api/rest_v1/page/summary/{title}`) — About summary + canonical title. CC BY-SA 4.0. URL from f1api.dev `url` auto-redirects.
- TheSportsDB, Sportmonks (paid), F1 Fandom Wiki (Cloudflare), Liquipedia (HTML) — all ruled out.

## Resolution (closed)

### Source split

| Need | Source | Cost |
|---|---|---|
| 2026 season — pos/points/wins | f1api.dev (already wired) | 0 |
| 2026 season — podiums/poles/DNFs/top10s/fastest-laps | F1DB build-time | 0 |
| All-time — GPs/points/wins/podiums/poles/fastest-laps | F1DB build-time | 0 |
| All-time — DNFs/top10s | F1DB build-time aggregation | 0 |
| First entry / first win (driver) | F1DB build-time (first row in `races-race-results.json` sorted by year, round) | 0 |
| First entry / first win (team) | F1DB build-time (same shape, filtered by `constructorId`) | 0 |
| World Championships (driver / team) | F1DB build-time (`totalChampionshipWins` on the row) | 0 |
| DOB / country / current team / current drivers / 3-letter / car # | f1api.dev (already wired) | 0 |
| First appearance / constructors' titles / drivers' titles (team) | f1api.dev (already wired) | 0 |
| "About" / biography | **Wikipedia REST** — slug from f1api.dev `url` (auto-redirects). CC BY-SA 4.0 attribution required. | 1 call per detail open (HttpCache covers re-opens) |
| Headshots / car render | formula1.com Cloudinary (already wired) | 0 |
| Team accent | `TeamColors.forId()` (already wired) | 0 |
| **Chassis (F1 W17)** | **F1DB YAML** `seasons/{year}/entrants.yml` → `chassisId` → `chassis.yml` | 0 (build-time) |
| **Power unit** | **F1DB YAML** `seasons/{year}/entrants.yml` → `engineManufacturerId` → `engine-manufacturers.yml` | 0 (build-time) |
| **Base country** | **F1DB YAML** `seasons/{year}/entrants.yml` → `countryId` | 0 (build-time) |

### Rules locked

- **Bar chart dropped.** Per user decision. Removes a data-definition
  question (race points vs championship points) and an F1DB aggregation.
- **Base city + team principal dropped.** Per user decision. No free
  JSON source; the remaining team-facts fields all come from F1DB YAML.
- **All-time "Grands Prix" = race-only.** F1DB includes sprint rounds;
  the screen excludes them to match the screenshot and the casual F1
  fan mental model.
- **No new paid API.** Sportmonks (€79/mo) ruled out despite a
  near-perfect data shape. ADR 0009 spirit preserved.
- **Use case architecture unchanged.** Only the use case seam changes
  (DriverDetail/TeamDetail models gain ~12 new fields each). The route
  + ViewModel + screen shape stays. F1DB catalog + Wikipedia REST
  extension join existing f1api.dev detail in the use case.

### License

- **F1DB is CC BY 4.0.** Generated catalog files carry the attribution
  header in the KDoc.
- **Wikipedia is CC BY-SA 4.0.** The "About" UI section surfaces the
  attribution line. `User-Agent: F1app/1.0 (+contact URL)` per
  Wikipedia's API etiquette.
- **No F1DB runtime network.** F1DB is build-time only; generated
  artifacts are checked in, not fetched at runtime (same precedent as
  the existing `tools/f1db/import-circuit-artwork.py` script).

## Follow-up tickets

- [27 — F1DB driver + constructor + team-facts import](27-f1db-driver-constructor-import.md) (open)
- [28 — Wikipedia REST extension](28-wikipedia-rest-extension.md) (open)
- [29 — DriverDetail / TeamDetail UI rewrite](29-driver-team-detail-ui-rewrite.md) (open, blocked by 27, 28)

## Cross-references

- [driver-team-detail.md](../driver-team-detail.md) — full field
  inventory, rules, implementation outline.
- [driver-team-detail-api-wrangling.md](../driver-team-detail-api-wrangling.md) —
  per-source probes, payload shapes, live counts.
- [f1db-data.md](../f1db-data.md) — existing F1DB coverage; build-time
  import precedent.
- [ADR 0009](../../decisions/0009-remove-openf1-runtime-dependency.md) —
  no new OpenF1 runtime dependency; this resolution respects it.
- [ADR 0012](../../decisions/0012-gap-f-detail-page-data-sources.md) —
  records the source split + rejected alternatives.
